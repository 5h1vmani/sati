package com.padakanti.sati.log

import com.padakanti.sati.Sadhana
import com.padakanti.sati.SadhanaJson
import com.padakanti.sati.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LogImportTest {
    private val today = LocalDate.of(2026, 11, 5).toEpochDay()
    private val now = 1_800_000_000_000L
    private var counter = 0
    private fun newId() = "new-${counter++}"

    private fun day(s: String) = LocalDate.parse(s).toEpochDay()

    private fun handMade(text: String): ImportFile = (LogImport.parseHandMade(text, today) as Parsed.Ok).value

    private fun handMadeError(text: String): ImportError =
        (LogImport.parseHandMade(text, today) as Parsed.Failed).error

    private fun merge(file: ImportFile, state: LogState, measures: Map<String, Measure> = emptyMap()) =
        (LogImport.merge(file, state, measures, now, ::newId) as Parsed.Ok).value

    /** The log after the changes are written. */
    private fun apply(state: LogState, c: ImportChanges) = LogState(state.practices + c.practices, state.entries + c.entries)

    private val empty = LogState(emptyList(), emptyList())

    // ---------- hand-made files ----------

    @Test fun readsFourColumns() {
        val file = handMade("2026-11-01,Hatha Yoga,45,85\n2026-11-02,Kriya,108,\n")
        assertEquals(2, file.entries.size)
        val first = file.entries[0]
        assertEquals("Hatha Yoga", first.practiceName)
        assertEquals(day("2026-11-01"), first.day)
        assertEquals(45, first.value)
        assertEquals(9, first.focus)
        assertEquals(null, file.entries[1].focus)
        assertEquals(null, first.id)
    }

    @Test fun aHeaderRowIsSkipped() {
        val file = handMade("Date,Practice,Value,Focus %\n2026-11-01,Kriya,10,50\n")
        assertEquals(1, file.entries.size)
        assertEquals(5, file.entries[0].focus)
    }

    @Test fun focusPercentSignAndLowScoresWork() {
        val file = handMade("2026-11-01,A,10,85%\n2026-11-01,B,10,3\n")
        assertEquals(9, file.entries[0].focus)
        assertEquals(1, file.entries[1].focus)
    }

    @Test fun quotedNamesWithCommasWork() {
        val file = handMade("2026-11-01,\"Yoga, morning\",30,\n")
        assertEquals("Yoga, morning", file.entries[0].practiceName)
    }

    @Test fun rowsMustHaveExactlyFourColumns() {
        assertEquals(ImportError(ImportErrorKind.WrongColumns, "", 2), handMadeError("2026-11-01,A,10,50\n2026-11-02,A,10\n"))
        assertEquals(ImportError(ImportErrorKind.WrongColumns, "", 1), handMadeError("2026-11-01,A,10,50,extra\n"))
    }

    @Test fun errorsNameTheLine() {
        assertEquals(ImportError(ImportErrorKind.BadDate, "", 3), handMadeError("2026-11-01,A,10,\n2026-11-02,A,10,\n02/11/2026,A,10,\n"))
        assertEquals(ImportError(ImportErrorKind.BadDate, "", 1), handMadeError("2026-13-40,A,10,\n"))
        assertEquals(ImportError(ImportErrorKind.FutureDay, "", 1), handMadeError("2026-11-06,A,10,\n"))
        assertEquals(ImportError(ImportErrorKind.BadName, "", 1), handMadeError("2026-11-01,  ,10,\n"))
        assertEquals(ImportError(ImportErrorKind.BadValue, "", 1), handMadeError("2026-11-01,A,ten,\n"))
        assertEquals(ImportError(ImportErrorKind.BadValue, "", 1), handMadeError("2026-11-01,A,-1,\n"))
        assertEquals(ImportError(ImportErrorKind.BadValue, "", 1), handMadeError("2026-11-01,A,100000,\n"))
        assertEquals(ImportError(ImportErrorKind.BadFocus, "", 1), handMadeError("2026-11-01,A,5,150\n"))
        assertEquals(ImportError(ImportErrorKind.BadFocus, "", 1), handMadeError("2026-11-01,A,5,high\n"))
    }

    @Test fun aLineAfterAMultiLineFieldIsCountedCorrectly() {
        assertEquals(ImportError(ImportErrorKind.BadDate, "", 3), handMadeError("2026-11-01,A,5,\"\n\"\nbad,A,5,\n"))
    }

    @Test fun anUnclosedQuoteIsAnError() =
        assertEquals(ImportError(ImportErrorKind.QuoteProblem, "", 2), handMadeError("2026-11-01,A,5,\n2026-11-02,\"B,5,\n"))

    @Test fun anEmptyFileIsAnError() {
        assertEquals(ImportErrorKind.Empty, handMadeError("").kind)
        assertEquals(ImportErrorKind.Empty, handMadeError("date,practice,value,focus\n").kind)
    }

    @Test fun aBadRowAfterGoodRowsFailsTheWholeFile() {
        // Good rows come first. The result still holds no file at all, so nothing can be written.
        val result = LogImport.parseHandMade("2026-11-01,A,10,\n2026-11-02,A,10,\nbad,A,10,\n", today)
        assertEquals(Parsed.Failed(ImportError(ImportErrorKind.BadDate, "", 3)), result)
    }

    @Test fun rowsOfOnlyCommasAreSkipped() {
        val file = handMade("2026-11-01,A,10,\n,,,\n , , ,\n2026-11-02,A,10,\n")
        assertEquals(listOf(1, 4), file.entries.map { it.line })
    }

    @Test fun aFileOfOnlyBlankRowsIsEmpty() = assertEquals(ImportErrorKind.Empty, handMadeError(",,,\n,,,\n").kind)

    // ---------- merging hand-made rows ----------

    @Test fun unknownNamesAreReturnedForTheScreenToAsk() {
        val file = handMade("2026-11-01,Hatha Yoga,45,\n2026-11-02,hatha yoga,30,\n2026-11-02,Kriya,108,\n")
        val changes = merge(file, empty)
        assertEquals(listOf("Hatha Yoga", "Kriya"), changes.unknownNames)
        assertEquals(2, changes.practices.size)
        assertEquals(3, changes.entries.size)
    }

    @Test fun chosenMeasuresAreUsedAndNewPracticesStartOnTheirFirstDay() {
        val file = handMade("2026-11-03,Kriya,108,\n2026-11-01,Kriya,50,\n2026-11-02,Walk,1,\n")
        val c = merge(file, empty, mapOf("Kriya" to Measure.Count, "Walk" to Measure.Done))
        val kriya = c.practices.single { it.name == "Kriya" }
        assertEquals(Measure.Count, kriya.measure)
        assertEquals(day("2026-11-01"), kriya.createdDay)
        assertEquals(Measure.Done, c.practices.single { it.name == "Walk" }.measure)
        assertEquals(listOf(0, 1), c.practices.map { it.position })
    }

    @Test fun aDonePracticeAlwaysGetsValueOne() {
        val file = handMade("2026-11-01,Walk,45,\n")
        assertEquals(1, merge(file, empty, mapOf("Walk" to Measure.Done)).entries.single().value)
    }

    @Test fun knownNamesMatchWithCaseIgnoredAndAskNothing() {
        val yoga = Practice("p1", "Hatha Yoga", Measure.Duration, 30, 0, false, 0)
        val c = merge(handMade("2026-11-01,  hatha YOGA ,45,\n"), LogState(listOf(yoga), emptyList()))
        assertEquals(emptyList<String>(), c.unknownNames)
        assertEquals(emptyList<Practice>(), c.practices)
        assertEquals("p1", c.entries.single().practiceId)
    }

    @Test fun newPracticesGoAfterTheExistingOnes() {
        val yoga = Practice("p1", "Hatha Yoga", Measure.Duration, 30, 0, false, 4)
        val c = merge(handMade("2026-11-01,Kriya,10,\n"), LogState(listOf(yoga), emptyList()))
        assertEquals(5, c.practices.single().position)
    }

    @Test fun anIdLessRowThatMatchesPracticeDayAndValueIsSkipped() {
        val yoga = Practice("p1", "Hatha Yoga", Measure.Count, null, 0, false, 0)
        val existing = Entry("e1", "p1", day("2026-11-01"), 45, 7, null, 0)
        val file = handMade("2026-11-01,Hatha Yoga,45,\n2026-11-01,Hatha Yoga,30,\n2026-11-02,Hatha Yoga,45,\n")
        val c = merge(file, LogState(listOf(yoga), listOf(existing)))
        assertEquals(1, c.entriesSkipped)
        assertEquals(listOf(30, 45), c.entries.map { it.value })
    }

    @Test fun twoIdenticalRowsInOneFileAreBothAddedOnce() {
        val file = handMade("2026-11-01,Walk,20,\n2026-11-01,Walk,20,\n")
        val first = merge(file, empty, mapOf("Walk" to Measure.Duration))
        assertEquals(2, first.entries.size)
        val second = merge(file, apply(empty, first))
        assertEquals(0, second.entries.size)
        assertEquals(2, second.entriesSkipped)
    }

    @Test fun importingTheSameHandMadeFileTwiceChangesNothing() {
        val file = handMade("2026-11-01,Hatha Yoga,45,85\n2026-11-02,Kriya,108,\n2026-11-02,Kriya,20,40\n")
        val first = merge(file, empty, mapOf("Hatha Yoga" to Measure.Duration, "Kriya" to Measure.Count))
        val after = apply(empty, first)
        val second = merge(file, after)
        assertEquals(emptyList<Practice>(), second.practices)
        assertEquals(emptyList<Entry>(), second.entries)
        assertEquals(emptyList<String>(), second.unknownNames)
        assertEquals(3, second.entriesSkipped)
    }

    // ---------- ZIP files ----------

    private val yoga = Practice("p-yoga", "Hatha Yoga", Measure.Done, null, day("2026-10-01"), archived = true, position = 0)
    private val aum = Practice("p-aum", "AUM, \"Chanting\"", Measure.Count, 108, day("2026-10-02"), archived = false, position = 1)
    private val periods = listOf(
        ArchivePeriod(1, "p-yoga", day("2026-10-10"), day("2026-10-20")),
        ArchivePeriod(2, "p-yoga", day("2026-11-01"), null),
    )
    private val entries = listOf(
        Entry("e1", "p-aum", day("2026-11-01"), 108, 8, "calm,\nquiet", 111),
        Entry("e2", "p-yoga", day("2026-11-02"), 1, null, null, 222),
    )
    private val sadhanas = listOf(Sadhana("s-1", "Morning", listOf(Stage(20, "Breath"))))

    private fun zip(
        practicesCsv: String = LogExport.practicesCsv(listOf(yoga, aum), periods),
        entriesCsv: String = LogExport.entriesCsv(entries, listOf(yoga, aum)),
        json: String? = SadhanaJson.encodeList(sadhanas),
    ) = ZipContents(practicesCsv, entriesCsv, json)

    private fun zipFile(): ImportFile = (LogImport.parseZip(zip(), today) as Parsed.Ok).value

    private fun zipError(z: ZipContents) = (LogImport.parseZip(z, today) as Parsed.Failed).error

    @Test fun readsAnExport() {
        val file = zipFile()
        assertEquals(listOf("p-yoga", "p-aum"), file.practices.map { it.id })
        val readYoga = file.practices[0]
        assertEquals(Measure.Done, readYoga.measure)
        assertTrue(readYoga.archived)
        assertEquals(listOf(day("2026-10-10") to day("2026-10-20"), day("2026-11-01") to null), readYoga.periods)
        assertEquals("AUM, \"Chanting\"", file.practices[1].name)
        assertEquals(108, file.practices[1].target)
        val e = file.entries[0]
        assertEquals("e1", e.id)
        assertEquals("p-aum", e.practiceId)
        assertEquals("calm,\nquiet", e.note)
        assertEquals(8, e.focus)
        assertEquals(111L, e.createdAt)
        assertEquals(sadhanas, file.sadhanas)
    }

    @Test fun anExportImportsIntoAnEmptyLog() {
        val c = merge(zipFile(), empty)
        assertEquals(listOf("p-yoga", "p-aum"), c.practices.map { it.id })
        assertEquals(listOf(0, 1), c.practices.map { it.position })
        assertEquals(entries, c.entries)
        assertEquals(periods.map { it.copy(id = 0) }, c.periods)
        assertEquals(0, c.entriesSkipped)
        assertEquals(emptyList<String>(), c.unknownNames)
        assertEquals(sadhanas, c.sadhanas)
        assertEquals(yoga, c.practices[0])
    }

    @Test fun importingTheSameZipTwiceChangesNothing() {
        val file = zipFile()
        val after = apply(empty, merge(file, empty))
        val second = merge(file, after)
        assertEquals(emptyList<Practice>(), second.practices)
        assertEquals(emptyList<ArchivePeriod>(), second.periods)
        assertEquals(emptyList<Entry>(), second.entries)
        assertEquals(2, second.entriesSkipped)
    }

    @Test fun anEntryWhoseIdExistsIsSkippedAndTheRestAreAdded() {
        val have = LogState(listOf(yoga, aum), listOf(entries[0]))
        val c = merge(zipFile(), have)
        assertEquals(listOf("e2"), c.entries.map { it.id })
        assertEquals(1, c.entriesSkipped)
        assertEquals(emptyList<Practice>(), c.practices)
    }

    @Test fun aPracticeWithAnExistingNameJoinsTheExistingOne() {
        val mine = Practice("mine", "hatha yoga", Measure.Done, null, 0, false, 0)
        val c = merge(zipFile(), LogState(listOf(mine), emptyList()))
        assertEquals(listOf("p-aum"), c.practices.map { it.id })
        assertEquals("mine", c.entries.single { it.id == "e2" }.practiceId)
    }

    @Test fun aSameNamePracticeWithAnotherMeasureIsAnError() {
        val mine = Practice("mine", "Hatha Yoga", Measure.Duration, null, 0, false, 0)
        val result = LogImport.merge(zipFile(), LogState(listOf(mine), emptyList()), emptyMap(), now, ::newId)
        assertEquals(ImportError(ImportErrorKind.MeasureConflict, "practices.csv", 2), (result as Parsed.Failed).error)
    }

    @Test fun anEntryOfAnUnknownPracticeIdNamesItsLine() {
        val file = zipFile()
        val onlyAum = file.copy(practices = file.practices.filter { it.id == "p-aum" })
        val result = LogImport.merge(onlyAum, empty, emptyMap(), now, ::newId)
        assertEquals(ImportError(ImportErrorKind.UnknownPractice, "entries.csv", 4), (result as Parsed.Failed).error)
    }

    @Test fun aPracticeWithAnExistingIdAndAnotherMeasureIsAnError() {
        val mine = yoga.copy(measure = Measure.Duration)
        val result = LogImport.merge(zipFile(), LogState(listOf(mine), emptyList()), emptyMap(), now, ::newId)
        assertEquals(ImportError(ImportErrorKind.MeasureConflict, "practices.csv", 2), (result as Parsed.Failed).error)
    }

    @Test fun aPracticeWithAnExistingIdAndTheSameMeasureIsSkipped() {
        val c = merge(zipFile(), LogState(listOf(yoga, aum), emptyList()))
        assertEquals(emptyList<Practice>(), c.practices)
        assertEquals(2, c.entries.size)
    }

    @Test fun rowsWithoutAnIdMatchByNameInsideAZip() {
        val csv = LogExport.ENTRY_HEADER.joinToString(",") + "\r\n,,Hatha Yoga,2026-11-03,1,,,\r\n"
        val file = (LogImport.parseZip(zip(entriesCsv = csv), today) as Parsed.Ok).value
        val c = merge(file, empty)
        assertEquals("p-yoga", c.entries.single().practiceId)
        assertEquals(now, c.entries.single().createdAt)
        val again = merge(file, apply(empty, c))
        assertEquals(1, again.entriesSkipped)
    }

    @Test fun zipErrorsNameTheFileAndLine() {
        assertEquals(ImportError(ImportErrorKind.MissingFile, "entries.csv"), zipError(ZipContents("x", null, null)))
        assertEquals(ImportError(ImportErrorKind.MissingFile, "practices.csv"), zipError(ZipContents(null, "x", null)))
        assertEquals(ImportError(ImportErrorKind.WrongColumns, "practices.csv", 1), zipError(zip(practicesCsv = "id,name\r\n")))
        val bad = LogExport.entriesCsv(entries, listOf(yoga, aum)).replace("2026-11-02", "2026-02-30")
        assertEquals(ImportError(ImportErrorKind.BadDate, "entries.csv", 4), zipError(zip(entriesCsv = bad)))
        val future = LogExport.entriesCsv(entries, listOf(yoga, aum)).replace("2026-11-02", "2026-12-25")
        assertEquals(ImportError(ImportErrorKind.FutureDay, "entries.csv", 4), zipError(zip(entriesCsv = future)))
        val focus = LogExport.entriesCsv(entries, listOf(yoga, aum)).replace(",8,", ",11,")
        assertEquals(ImportError(ImportErrorKind.BadFocus, "entries.csv", 2), zipError(zip(entriesCsv = focus)))
        val measure = LogExport.practicesCsv(listOf(yoga, aum), periods).replace(",count,", ",tens,")
        assertEquals(ImportError(ImportErrorKind.BadMeasure, "practices.csv", 3), zipError(zip(practicesCsv = measure)))
        assertEquals(ImportError(ImportErrorKind.BadSadhanas, "sadhanas.json", 0, 0), zipError(zip(json = "not json")))
    }

    @Test fun aDuplicatePracticeIdIsAnError() {
        val csv = LogExport.practicesCsv(listOf(yoga, aum.copy(id = "p-yoga")), emptyList())
        assertEquals(ImportError(ImportErrorKind.BadId, "practices.csv", 3), zipError(zip(practicesCsv = csv)))
    }

    @Test fun sadhanaJsonIsOptional() {
        val file = (LogImport.parseZip(zip(json = null), today) as Parsed.Ok).value
        assertEquals(null, file.sadhanas)
        assertEquals(emptyList<Sadhana>(), merge(file, empty).sadhanas)
    }

    @Test fun sadhanasMergeByIdKeepingTheExistingOne() {
        val mine = Sadhana("s-1", "Mine", listOf(Stage(5)))
        val other = Sadhana("s-2", "Other", listOf(Stage(10)))
        val merged = LogImport.mergeSadhanas(listOf(mine), sadhanas + other)
        assertEquals(listOf(mine, other), merged.list)
        assertEquals(merged.list, LogImport.mergeSadhanas(merged.list, sadhanas + other).list)
    }

    // ---------- hostile files ----------

    private fun sadhanaJson(vararg ids: String, name: String = "X", minutes: Int = 5) =
        "[" + ids.joinToString(",") { """{"id":"$it","name":"$name","stages":[{"minutes":$minutes}]}""" } + "]"

    private fun sadhana(id: String) = Sadhana(id, "X", listOf(Stage(5)))

    @Test fun anUnsafeSadhanaIsSkippedAndTheRestOfTheFileImports() {
        val file = (LogImport.parseZip(zip(json = sadhanaJson("a", "b", minutes = -5)), today) as Parsed.Ok).value
        assertEquals(emptyList<Sadhana>(), file.sadhanas)
        assertEquals(2, file.sadhanasBad)
        val c = merge(file, empty)
        assertEquals(entries, c.entries)
        assertEquals(2, c.sadhanasBad)
    }

    @Test fun aLongNamedSadhanaIsSkippedWhileEntriesImport() {
        val json = "[" + """{"id":"long","name":"${"n".repeat(61)}","stages":[{"minutes":5}]},""" +
            """{"id":"ok","name":"Fine","stages":[{"minutes":5}]}]"""
        val file = (LogImport.parseZip(zip(json = json), today) as Parsed.Ok).value
        assertEquals(listOf("ok"), file.sadhanas!!.map { it.id })
        assertEquals(1, file.sadhanasBad)
        assertEquals(2, merge(file, empty).entries.size)
    }

    @Test fun moreThanFiftySadhanasInTheFileDoNotBlockTheRestore() {
        val file = (LogImport.parseZip(zip(json = sadhanaJson(*Array(60) { "s$it" })), today) as Parsed.Ok).value
        assertEquals(60, file.sadhanas!!.size)
        assertEquals(2, merge(file, empty).entries.size)
    }

    @Test fun aSadhanaFileThatIsNotAListFailsTheImport() {
        assertEquals(ImportError(ImportErrorKind.BadSadhanas, "sadhanas.json", 0, 0), zipError(zip(json = "not json")))
        assertEquals(ImportErrorKind.BadSadhanas, zipError(zip(json = """{"id":"a"}""")).kind)
    }

    @Test fun fiftyExistingPlusFiftyNewAddsNone() {
        val existing = List(50) { sadhana("old$it") }
        val result = LogImport.mergeSadhanas(existing, List(50) { sadhana("new$it") })
        assertEquals(existing, result.list)
        assertEquals(0, result.added)
        assertEquals(50, result.skipped)
    }

    @Test fun newSadhanasFillOnlyTheRoomLeft() {
        val existing = List(48) { sadhana("old$it") }
        val result = LogImport.mergeSadhanas(existing, List(5) { sadhana("new$it") })
        assertEquals(50, result.list.size)
        assertEquals(listOf("new0", "new1"), result.list.drop(48).map { it.id })
        assertEquals(2, result.added)
        assertEquals(3, result.skipped)
    }

    @Test fun mergeSkipsExistingIdsAndUnsafeSadhanas() {
        val mine = Sadhana("s-1", "Mine", listOf(Stage(5)))
        val unsafe = Sadhana("bad", "Bad", listOf(Stage(-5)))
        val result = LogImport.mergeSadhanas(listOf(mine), listOf(sadhana("s-1"), unsafe, sadhana("s-2")))
        assertEquals(listOf("s-1", "s-2"), result.list.map { it.id })
        assertEquals("Mine", result.list[0].name)
        assertEquals(1, result.added)
        assertEquals(2, result.skipped)
    }

    @Test fun importingTheSameSadhanasTwiceAddsNothingTheSecondTime() {
        val first = LogImport.mergeSadhanas(emptyList(), listOf(sadhana("a"), sadhana("b")))
        val second = LogImport.mergeSadhanas(first.list, listOf(sadhana("a"), sadhana("b")))
        assertEquals(0, second.added)
        assertEquals(first.list, second.list)
    }

    @Test fun practicesAboveTheTotalCapRefuseTheImport() {
        val existing = List(PracticeRules.MAX_PRACTICES - 1) { Practice("e$it", "Existing $it", Measure.Done, null, 0, false, it) }
        val result = LogImport.merge(zipFile(), LogState(existing, emptyList()), emptyMap(), now, ::newId)
        assertEquals(ImportError(ImportErrorKind.PracticeLimit, "", 0, PracticeRules.MAX_PRACTICES), (result as Parsed.Failed).error)
    }

    @Test fun practicesUpToTheTotalCapAreAccepted() {
        val existing = List(PracticeRules.MAX_PRACTICES - 2) { Practice("e$it", "Existing $it", Measure.Done, null, 0, false, it) }
        assertEquals(2, merge(zipFile(), LogState(existing, emptyList())).practices.size)
    }

    @Test fun newNamesFromAHandMadeFileCountTowardTheCapToo() {
        val existing = List(PracticeRules.MAX_PRACTICES) { Practice("e$it", "Existing $it", Measure.Done, null, 0, false, it) }
        val result = LogImport.merge(handMade("2026-11-01,Brand new,5,\n"), LogState(existing, emptyList()), emptyMap(), now, ::newId)
        assertEquals(ImportErrorKind.PracticeLimit, (result as Parsed.Failed).error.kind)
    }

    @Test fun idsMustBeShortAndPlain() {
        val long = "x".repeat(65)
        assertEquals(ImportErrorKind.BadId, zipError(zip(practicesCsv = LogExport.practicesCsv(listOf(yoga.copy(id = long)), emptyList()))).kind)
        assertEquals(ImportErrorKind.BadId, zipError(zip(practicesCsv = LogExport.practicesCsv(listOf(yoga.copy(id = "a b")), emptyList()))).kind)
        val e = LogExport.entriesCsv(listOf(entries[0].copy(id = "e/1")), listOf(aum))
        assertEquals(ImportError(ImportErrorKind.BadId, "entries.csv", 2, 0), zipError(zip(entriesCsv = e)))
        val pid = LogExport.entriesCsv(listOf(entries[0].copy(practiceId = long)), listOf(aum))
        assertEquals(ImportErrorKind.BadId, zipError(zip(entriesCsv = pid)).kind)
    }

    @Test fun idsOfExactlySixtyFourPlainCharactersAreFine() {
        val id = "a-Z_9".repeat(12) + "abcd"
        assertEquals(64, id.length)
        val csv = LogExport.practicesCsv(listOf(yoga.copy(id = id)), emptyList())
        assertTrue(LogImport.parseZip(zip(practicesCsv = csv, entriesCsv = LogExport.entriesCsv(emptyList(), emptyList())), today) is Parsed.Ok)
    }

    @Test fun moreThanTwoHundredPracticesAreRefused() {
        val many = (0..200).map { yoga.copy(id = "p$it", name = "P$it") }
        val error = zipError(zip(practicesCsv = LogExport.practicesCsv(many, emptyList())))
        assertEquals(ImportErrorKind.TooMany, error.kind)
        assertEquals(200, error.limit)
        assertEquals("practices.csv", error.file)
        val exactly = (0 until 200).map { yoga.copy(id = "p$it", name = "P$it") }
        assertTrue(LogImport.parseZip(zip(practicesCsv = LogExport.practicesCsv(exactly, emptyList()), entriesCsv = LogExport.entriesCsv(emptyList(), emptyList())), today) is Parsed.Ok)
    }

    @Test fun moreThanOneHundredThousandEntriesAreRefused() {
        val rows = StringBuilder()
        repeat(LogImport.MAX_ENTRIES + 1) { rows.append("2026-11-01,A,1,\n") }
        val error = handMadeError(rows.toString())
        assertEquals(ImportErrorKind.TooMany, error.kind)
        assertEquals(LogImport.MAX_ENTRIES, error.limit)
        assertEquals(LogImport.MAX_ENTRIES + 1, error.line)
    }

    @Test fun exactlyTheEntryCapIsAccepted() {
        val rows = StringBuilder()
        repeat(LogImport.MAX_ENTRIES) { rows.append("2026-11-01,A,1,\n") }
        assertEquals(LogImport.MAX_ENTRIES, handMade(rows.toString()).entries.size)
    }

    @Test fun moreThanFiftyNewNamesAreRefused() {
        val rows = (0..50).joinToString("") { "2026-11-01,Name$it,1,\n" }
        val file = handMade(rows)
        val result = LogImport.merge(file, empty, emptyMap(), now, ::newId) as Parsed.Failed
        assertEquals(ImportError(ImportErrorKind.TooMany, "", 51, 50), result.error)
        val fifty = handMade((0 until 50).joinToString("") { "2026-11-01,Name$it,1,\n" })
        assertEquals(50, merge(fifty, empty).unknownNames.size)
    }

    // ---------- formulas ----------

    @Test fun fieldsThatStartLikeAFormulaGetAnApostropheOnExport() {
        val p = Practice("p1", "=SUM(A1)", Measure.Done, null, 0, false, 0)
        val e = Entry("e1", "p1", 0, 1, null, "@cmd", 0)
        assertTrue(LogExport.practicesCsv(listOf(p), emptyList()).contains("p1,'=SUM(A1),done"))
        assertTrue(LogExport.entriesCsv(listOf(e), listOf(p)).contains("e1,p1,'=SUM(A1),1970-01-01,1,,'@cmd,0"))
    }

    @Test fun everyTriggerIsGuardedAndPlainTextIsNot() {
        listOf("=a", "+a", "-a", "@a", "\ta", "\ra", "'a").forEach { assertEquals("'$it", CsvGuard.guard(it)) }
        listOf("a", "a=b", " =a", "").forEach { assertEquals(it, CsvGuard.guard(it)) }
    }

    @Test fun unguardRemovesExactlyOneApostrophe() {
        assertEquals("=a", CsvGuard.unguard("'=a"))
        assertEquals("'a", CsvGuard.unguard("''a"))
        assertEquals("a", CsvGuard.unguard("a"))
    }

    @Test fun formulaLookingTextRoundTripsExactly() {
        val names = listOf("=1+1", "+x", "-x", "@x", "'quoted", "''two", "plain")
        val practices = names.mapIndexed { i, n -> Practice("p$i", n, Measure.Duration, null, day("2026-10-01"), false, i) }
        val notes = listOf("=x", "'starts with apostrophe", "''", "'", "@", "-", "ordinary", "line one\nline two", "=first\nsecond")
        val es = notes.mapIndexed { i, n -> Entry("e$i", "p${i % names.size}", day("2026-11-01"), 5, null, n, i.toLong()) }
        val z = ZipContents(LogExport.practicesCsv(practices, emptyList()), LogExport.entriesCsv(es, practices), null)
        val file = (LogImport.parseZip(z, today) as Parsed.Ok).value
        assertEquals(names, file.practices.map { it.name })
        assertEquals(notes, file.entries.map { it.note })
        val c = merge(file, empty)
        assertEquals(names, c.practices.map { it.name })
        assertEquals(es, c.entries)
    }

    // ---------- names and notes ----------

    @Test fun hiddenCharactersInNamesAreRefused() {
        assertEquals(ImportError(ImportErrorKind.BadText, "", 1), handMadeError("2026-11-01,Yo\u200Bga,5,\n"))
        assertEquals(ImportError(ImportErrorKind.BadText, "", 1), handMadeError("2026-11-01,Yo\u202Ega,5,\n"))
        assertEquals(ImportError(ImportErrorKind.BadText, "", 1), handMadeError("2026-11-01,\"Yo\nga\",5,\n"))
        val p = LogExport.practicesCsv(listOf(yoga.copy(name = "Ha\u200Btha")), emptyList())
        assertEquals(ImportError(ImportErrorKind.BadText, "practices.csv", 2), zipError(zip(practicesCsv = p)))
    }

    @Test fun hiddenCharactersInNotesAreRefusedButNewLinesAreKept() {
        val bad = LogExport.entriesCsv(listOf(entries[0].copy(note = "a\u202Eb")), listOf(aum))
        assertEquals(ImportError(ImportErrorKind.BadText, "entries.csv", 2), zipError(zip(entriesCsv = bad)))
        val tab = LogExport.entriesCsv(listOf(entries[0].copy(note = "a\tb")), listOf(aum))
        assertEquals(ImportErrorKind.BadText, zipError(zip(entriesCsv = tab)).kind)
        assertEquals("calm,\nquiet", zipFile().entries[0].note)
    }

    @Test fun namesAreNormalizedAndMatchAcrossSpellings() {
        val composed = Practice("p1", "Caf\u00E9", Measure.Duration, null, 0, false, 0)
        val c = merge(handMade("2026-11-01,Cafe\u0301,5,\n"), LogState(listOf(composed), emptyList()))
        assertEquals(emptyList<String>(), c.unknownNames)
        assertEquals("p1", c.entries.single().practiceId)
        val fresh = merge(handMade("2026-11-01,Cafe\u0301,5,\n"), empty)
        assertEquals(listOf("Caf\u00E9"), fresh.practices.map { it.name })
    }

    // ---------- values and focus checked against the measure ----------

    private fun mergeResult(text: String, measure: Measure, name: String = "X") =
        LogImport.merge(handMade(text), empty, mapOf(name to measure), now, ::newId)

    private fun mergeError(text: String, measure: Measure, name: String = "X") = (mergeResult(text, measure, name) as Parsed.Failed).error

    @Test fun aHandMadeValueMayEndInAPercentSign() {
        val file = handMade("2026-11-01,X,72%,\n2026-11-02,X, 85 % ,\n")
        assertEquals(listOf(72, 85), file.entries.map { it.value })
        val c = merge(file, empty, mapOf("X" to Measure.Percent))
        assertEquals(listOf(72, 85), c.entries.map { it.value })
        assertEquals(Measure.Percent, c.practices.single().measure)
    }

    @Test fun aHandMadeValueOfZeroIsNotDoneForEveryMeasure() {
        assertEquals(0, handMade("2026-11-01,X,0,\n").entries.single().value)
        Measure.entries.forEach {
            assertEquals("$it", 0, merge(handMade("2026-11-01,X,0,\n"), empty, mapOf("X" to it)).entries.single().value)
        }
    }

    @Test fun aNegativeHandMadeValueIsBadValue() {
        assertEquals(ImportError(ImportErrorKind.BadValue, "", 1), handMadeError("2026-11-01,X,-1,\n"))
    }

    @Test fun aHandMadeDonePracticeReadsZeroAsNotDoneAndOneOrMoreAsDone() {
        val c = merge(handMade("2026-11-01,X,0,\n2026-11-02,X,1,\n2026-11-03,X,45,\n"), empty, mapOf("X" to Measure.Done))
        assertEquals(listOf(0, 1, 1), c.entries.map { it.value })
    }

    @Test fun aScaleValueOutOfRangeStopsTheImportWithItsLine() {
        assertEquals(ImportError(ImportErrorKind.BadValue, "", 2), mergeError("2026-11-01,X,5,\n2026-11-02,X,11,\n", Measure.Scale))
        assertEquals(10, merge(handMade("2026-11-01,X,10,\n"), empty, mapOf("X" to Measure.Scale)).entries.single().value)
    }

    @Test fun aPercentValueAboveOneHundredStopsTheImport() {
        assertEquals(ImportError(ImportErrorKind.BadValue, "", 1), mergeError("2026-11-01,X,101,\n", Measure.Percent))
        assertEquals(100, merge(handMade("2026-11-01,X,100%,\n"), empty, mapOf("X" to Measure.Percent)).entries.single().value)
    }

    @Test fun oneBadRowStopsTheWholeImportEvenAfterGoodRows() {
        val result = mergeResult("2026-11-01,X,5,\n2026-11-02,X,6,\n2026-11-03,X,60,\n", Measure.Scale)
        assertTrue(result is Parsed.Failed)
    }

    @Test fun aFocusOnAPercentOrScaleRowIsKeptAsItIs() {
        val percent = merge(handMade("2026-11-01,X,50,85\n"), empty, mapOf("X" to Measure.Percent))
        assertEquals(9, percent.entries.single().focus)
        val scale = merge(handMade("2026-11-01,X,5,40\n"), empty, mapOf("X" to Measure.Scale))
        assertEquals(4, scale.entries.single().focus)
    }

    @Test fun aNotDoneRowWithAFocusIsBadFocus() {
        assertEquals(ImportError(ImportErrorKind.BadFocus, "", 1), mergeError("2026-11-01,X,0,80\n", Measure.Done))
        assertEquals(9, merge(handMade("2026-11-01,X,1,85\n"), empty, mapOf("X" to Measure.Done)).entries.single().focus)
    }

    @Test fun beforeTheUserChoosesAMeasureTheValuesAreNotCheckedAgainstAGuess() {
        // 101 is fine as minutes of a guessed Duration, but wrong for Percent. The check waits for the choice.
        val first = LogImport.merge(handMade("2026-11-01,X,101,\n"), empty, emptyMap(), now, ::newId)
        assertEquals(listOf("X"), (first as Parsed.Ok).value.unknownNames)
        val chosen = LogImport.merge(handMade("2026-11-01,X,101,\n"), empty, mapOf("X" to Measure.Percent), now, ::newId)
        assertEquals(ImportErrorKind.BadValue, (chosen as Parsed.Failed).error.kind)
    }

    @Test fun theFinalRunChecksAPracticeThatIsStillAGuess() {
        // A name nobody chose a measure for, for example because its practice was deleted meanwhile.
        // 90000 minutes is more than a day, so it is wrong for the guessed Duration.
        val result = LogImport.merge(handMade("2026-11-01,X,90000,\n"), empty, emptyMap(), now, ::newId, final = true)
        assertEquals(ImportError(ImportErrorKind.BadValue, "", 1), (result as Parsed.Failed).error)
    }

    @Test fun aPercentSignIsAllowedOnlyForAPercentPractice() {
        assertEquals(ImportError(ImportErrorKind.BadValue, "", 1), mergeError("2026-11-01,X,30%,\n", Measure.Duration))
        assertEquals(1800, merge(handMade("2026-11-01,X,30,\n"), empty, mapOf("X" to Measure.Duration)).entries.single().value)
    }

    @Test fun aKnownPracticeIsCheckedAgainstItsOwnMeasureAtOnce() {
        val mood = Practice("m", "Mood", Measure.Scale, null, 0, false, 0, askFocus = false)
        val result = LogImport.merge(handMade("2026-11-01,Mood,50,\n"), LogState(listOf(mood), emptyList()), emptyMap(), now, ::newId)
        assertEquals(ImportError(ImportErrorKind.BadValue, "", 1), (result as Parsed.Failed).error)
    }

    @Test fun aZipDoneRowMustAlreadyBeZeroOrOne() {
        val bad = entries[1].copy(value = 2)
        val z = zip(entriesCsv = LogExport.entriesCsv(listOf(entries[0], bad), listOf(yoga, aum)))
        val file = (LogImport.parseZip(z, today) as Parsed.Ok).value
        val result = LogImport.merge(file, empty, emptyMap(), now, ::newId)
        assertEquals(ImportError(ImportErrorKind.BadValue, "entries.csv", 4), (result as Parsed.Failed).error)
    }

    @Test fun aZipDoneRowOfZeroIsKeptAsNotDone() {
        val notDone = entries[1].copy(value = 0)
        val z = zip(entriesCsv = LogExport.entriesCsv(listOf(entries[0], notDone), listOf(yoga, aum)))
        val c = merge((LogImport.parseZip(z, today) as Parsed.Ok).value, empty)
        assertEquals(0, c.entries.single { it.id == "e2" }.value)
    }

    @Test fun aZipDoneRowOfZeroWithAFocusIsBadFocus() {
        val bad = entries[1].copy(value = 0, focus = 6)
        val z = zip(entriesCsv = LogExport.entriesCsv(listOf(entries[0], bad), listOf(yoga, aum)))
        val result = LogImport.merge((LogImport.parseZip(z, today) as Parsed.Ok).value, empty, emptyMap(), now, ::newId)
        assertEquals(ImportError(ImportErrorKind.BadFocus, "entries.csv", 4), (result as Parsed.Failed).error)
    }

    @Test fun aZipNotDoneRowOfACountPracticeWithAFocusIsBadFocus() {
        val bad = entries[0].copy(value = 0)
        val z = zip(entriesCsv = LogExport.entriesCsv(listOf(bad, entries[1]), listOf(yoga, aum)))
        val result = LogImport.merge((LogImport.parseZip(z, today) as Parsed.Ok).value, empty, emptyMap(), now, ::newId)
        assertEquals(ImportError(ImportErrorKind.BadFocus, "entries.csv", 2), (result as Parsed.Failed).error)
    }

    @Test fun percentAndScalePracticesRoundTripThroughExportAndImport() {
        val energy = Practice("p-energy", "Energy", Measure.Percent, null, day("2026-10-01"), false, 0, askFocus = true)
        val mood = Practice("p-mood", "Mood", Measure.Scale, null, day("2026-10-01"), false, 1, askFocus = false)
        val es = listOf(
            Entry("x1", "p-energy", day("2026-11-01"), 72, 8, null, 1),
            Entry("x2", "p-mood", day("2026-11-01"), 7, null, null, 2),
        )
        val csv = LogExport.practicesCsv(listOf(energy, mood), emptyList())
        assertTrue(csv.contains("percent"))
        assertTrue(csv.contains("scale"))
        val z = ZipContents(csv, LogExport.entriesCsv(es, listOf(energy, mood)), null)
        val c = merge((LogImport.parseZip(z, today) as Parsed.Ok).value, empty)
        assertEquals(listOf(energy, mood), c.practices)
        assertEquals(es, c.entries)
    }

    @Test fun theMeasureNamesPercentAndScaleAreReadFromACsv() {
        assertEquals(Measure.Percent, LogExport.measureOf("percent"))
        assertEquals(Measure.Scale, LogExport.measureOf(" Scale "))
        assertEquals("percent", LogExport.measureText(Measure.Percent))
    }

    // ---------- the ask_focus column ----------

    private val oldHeaderRows = listOf(
        listOf("p-yoga", "Hatha Yoga", "done", "", "2026-10-01", "false", ""),
        listOf("p-aum", "AUM", "count", "108", "2026-10-02", "false", ""),
        listOf("p-mood", "Mood", "scale", "", "2026-10-03", "false", ""),
    )

    private fun practiceFile(csv: String): Parsed<ImportFile> =
        LogImport.parseZip(ZipContents(csv, LogExport.entriesCsv(emptyList(), emptyList()), null), today)

    @Test fun theExportWritesAskFocusAndTheScheduleAsTheLastColumns() {
        val off = aum.copy(askFocus = false, everyDays = 3, scheduleStart = day("2026-11-02"))
        val csv = LogExport.practicesCsv(listOf(yoga.copy(scheduleStart = day("2026-10-01")), off), emptyList())
        assertEquals(listOf("ask_focus", "every_days", "schedule_start"), LogExport.PRACTICE_HEADER.takeLast(3))
        assertTrue(csv.lineSequence().first().endsWith(",ask_focus,every_days,schedule_start"))
        val lines = csv.trim().lines()
        assertTrue(lines[1], lines[1].endsWith(",true,1,2026-10-01"))
        assertTrue(lines[2], lines[2].endsWith(",false,3,2026-11-02"))
    }

    @Test fun aFileWithAskFocusKeepsEachPracticesSwitch() {
        val off = aum.copy(askFocus = false)
        val on = yoga.copy(askFocus = true)
        val file = (practiceFile(LogExport.practicesCsv(listOf(on, off), emptyList())) as Parsed.Ok).value
        assertEquals(listOf(true, false), file.practices.map { it.askFocus })
        val c = merge(file, empty)
        assertEquals(listOf(true, false), c.practices.map { it.askFocus })
    }

    @Test fun aFileWithoutAskFocusGivesEachMeasureItsDefault() {
        val csv = Csv.write(listOf(LogExport.PRACTICE_HEADER_OLD) + oldHeaderRows)
        val file = (practiceFile(csv) as Parsed.Ok).value
        assertEquals(listOf<Boolean?>(null, null, null), file.practices.map { it.askFocus })
        val c = merge(file, empty)
        assertEquals(listOf(false, true, false), c.practices.map { it.askFocus })
    }

    @Test fun aBadAskFocusValueNamesTheLine() {
        val rows = oldHeaderRows.mapIndexed { i, r -> r + (if (i == 1) "maybe" else "true") }
        val csv = Csv.write(listOf(LogExport.PRACTICE_HEADER_ASK_FOCUS) + rows)
        assertEquals(ImportError(ImportErrorKind.BadFlag, "practices.csv", 3), (practiceFile(csv) as Parsed.Failed).error)
    }

    @Test fun aRowShorterThanItsHeaderIsStillAColumnError() {
        val csv = Csv.write(listOf(LogExport.PRACTICE_HEADER, oldHeaderRows[0]))
        assertEquals(ImportError(ImportErrorKind.WrongColumns, "practices.csv", 2), (practiceFile(csv) as Parsed.Failed).error)
        val other = Csv.write(listOf(LogExport.PRACTICE_HEADER.dropLast(1)))
        assertEquals(ImportError(ImportErrorKind.WrongColumns, "practices.csv", 1), (practiceFile(other) as Parsed.Failed).error)
    }

    @Test fun anExistingPracticeKeepsItsOwnSwitchWhenTheFileHasAnother() {
        val mine = aum.copy(askFocus = false)
        val file = (practiceFile(LogExport.practicesCsv(listOf(aum.copy(askFocus = true)), emptyList())) as Parsed.Ok).value
        assertEquals(emptyList<Practice>(), merge(file, LogState(listOf(mine), emptyList())).practices)
    }

    @Test fun aHandMadePracticeAsksForFocusWhenAnyRowHasOne() {
        val c = merge(
            handMade("2026-11-01,A,50,\n2026-11-02,A,60,85\n2026-11-01,B,50,\n2026-11-01,C,5,\n2026-11-01,D,1,70\n"),
            empty,
            mapOf("A" to Measure.Percent, "B" to Measure.Percent, "C" to Measure.Scale, "D" to Measure.Done),
        )
        assertEquals(true, c.practices.single { it.name == "A" }.askFocus)
        assertEquals(false, c.practices.single { it.name == "B" }.askFocus)
        assertEquals(false, c.practices.single { it.name == "C" }.askFocus)
        assertEquals(true, c.practices.single { it.name == "D" }.askFocus)
    }

    @Test fun aHandMadePracticeWithoutFocusRowsGetsTheMeasureDefault() {
        val c = merge(handMade("2026-11-01,A,10,\n2026-11-01,B,10,\n"), empty, mapOf("A" to Measure.Duration, "B" to Measure.Done))
        assertEquals(true, c.practices.single { it.name == "A" }.askFocus)
        assertEquals(false, c.practices.single { it.name == "B" }.askFocus)
    }

    // ---------- durations in seconds ----------

    private fun durationValues(text: String) = merge(handMade(text), empty, mapOf("X" to Measure.Duration)).entries.map { it.value }

    @Test fun aHandMadeDurationValueIsMinutesMinutesAndSecondsOrSeconds() {
        assertEquals(listOf(1800, 90, 45), durationValues("2026-11-01,X,30,\n2026-11-02,X,1:30,\n2026-11-03,X,45s,\n"))
    }

    @Test fun aHandMadeDurationOverOneDayStopsTheImportWithItsLine() {
        val result = LogImport.merge(handMade("2026-11-01,X,30,\n2026-11-02,X,1441,\n"), empty, mapOf("X" to Measure.Duration), now, ::newId)
        assertEquals(ImportError(ImportErrorKind.BadValue, "", 2), (result as Parsed.Failed).error)
        // Seconds over a day make no sense for any measure, so reading the file already stops.
        assertEquals(ImportError(ImportErrorKind.BadValue, "", 1), handMadeError("2026-11-01,X,86401s,\n"))
    }

    @Test fun aHandMadeColonValueIsBadValueForOtherMeasures() {
        assertEquals(ImportError(ImportErrorKind.BadValue, "", 1), mergeError("2026-11-01,X,1:30,\n", Measure.Count))
        assertEquals(ImportError(ImportErrorKind.BadValue, "", 1), mergeError("2026-11-01,X,45s,\n", Measure.Scale))
    }

    @Test fun aHandMadeValueThatIsNothingAtAllFailsWhileReading() {
        assertEquals(ImportError(ImportErrorKind.BadValue, "", 1), handMadeError("2026-11-01,X,1:75,\n"))
        assertEquals(ImportError(ImportErrorKind.BadValue, "", 1), handMadeError("2026-11-01,X,fast,\n"))
    }

    @Test fun aNewHandMadePracticeIsDueEveryDayFromItsFirstDay() {
        val c = merge(handMade("2026-11-03,X,5,\n2026-11-01,X,5,\n"), empty, mapOf("X" to Measure.Count))
        val p = c.practices.single()
        assertEquals(1, p.everyDays)
        assertEquals(day("2026-11-01"), p.scheduleStart)
    }

    // ---------- older files and the schedule columns ----------

    private val sevenColumns = listOf(
        listOf("p-walk", "Walk", "minutes", "30", "2026-10-01", "false", ""),
        listOf("p-yoga", "Hatha Yoga", "done", "", "2026-10-02", "false", ""),
    )

    private fun entriesFor(vararg rows: Entry) = LogExport.entriesCsv(rows.toList(), listOf(Practice("p-walk", "Walk", Measure.Duration, null, 0, false, 0)))

    private fun zipOf(practices: String, entriesCsv: String) = (LogImport.parseZip(ZipContents(practices, entriesCsv, null), today) as Parsed.Ok).value

    @Test fun aSevenColumnFileGetsEveryDayAndItsCreatedDayAsStart() {
        val file = zipOf(Csv.write(listOf(LogExport.PRACTICE_HEADER_OLD) + sevenColumns), LogExport.entriesCsv(emptyList(), emptyList()))
        assertEquals(listOf(1, 1), file.practices.map { it.everyDays })
        val c = merge(file, empty)
        assertEquals(listOf(day("2026-10-01"), day("2026-10-02")), c.practices.map { it.scheduleStart })
        assertEquals(listOf(1, 1), c.practices.map { it.everyDays })
    }

    @Test fun anEightColumnFileGetsEveryDayAndItsCreatedDayAsStart() {
        val rows = sevenColumns.map { it + "true" }
        val c = merge(zipOf(Csv.write(listOf(LogExport.PRACTICE_HEADER_ASK_FOCUS) + rows), LogExport.entriesCsv(emptyList(), emptyList())), empty)
        assertEquals(listOf(1, 1), c.practices.map { it.everyDays })
        assertEquals(listOf(day("2026-10-01"), day("2026-10-02")), c.practices.map { it.scheduleStart })
        assertEquals(listOf(true, true), c.practices.map { it.askFocus })
    }

    @Test fun aTenColumnFileKeepsItsSchedule() {
        val rows = listOf(listOf("p-yoga", "Hatha Yoga", "done", "", "2026-10-02", "false", "", "false", "3", "2026-10-20"))
        val c = merge(zipOf(Csv.write(listOf(LogExport.PRACTICE_HEADER) + rows), LogExport.entriesCsv(emptyList(), emptyList())), empty)
        assertEquals(3, c.practices.single().everyDays)
        assertEquals(day("2026-10-20"), c.practices.single().scheduleStart)
    }

    @Test fun aBadEveryDaysOrScheduleStartNamesTheLine() {
        fun file(every: String, start: String) = Csv.write(
            listOf(LogExport.PRACTICE_HEADER, listOf("p-yoga", "Yoga", "done", "", "2026-10-02", "false", "", "true", every, start)),
        )
        val bad = { csv: String -> (LogImport.parseZip(ZipContents(csv, LogExport.entriesCsv(emptyList(), emptyList()), null), today) as Parsed.Failed).error }
        assertEquals(ImportError(ImportErrorKind.BadNumber, "practices.csv", 2), bad(file("0", "2026-10-02")))
        assertEquals(ImportError(ImportErrorKind.BadNumber, "practices.csv", 2), bad(file("8", "2026-10-02")))
        assertEquals(ImportError(ImportErrorKind.BadNumber, "practices.csv", 2), bad(file("x", "2026-10-02")))
        assertEquals(ImportError(ImportErrorKind.BadDate, "practices.csv", 2), bad(file("2", "soon")))
    }

    @Test fun aMinutesPracticeInAnOldZipBecomesADurationInSeconds() {
        val file = zipOf(
            Csv.write(listOf(LogExport.PRACTICE_HEADER_OLD) + sevenColumns),
            entriesFor(Entry("x1", "p-walk", day("2026-11-01"), 20, 6, null, 1)),
        )
        val c = merge(file, empty)
        val walk = c.practices.single { it.id == "p-walk" }
        assertEquals(Measure.Duration, walk.measure)
        assertEquals(1800, walk.target)
        assertEquals(1200, c.entries.single().value)
        assertEquals(6, c.entries.single().focus)
    }

    @Test fun aMinutesPracticeRowWithoutAnIdIsMultipliedToo() {
        val csv = LogExport.ENTRY_HEADER.joinToString(",") + "\r\n,,Walk,2026-11-03,5,,,\r\n"
        val c = merge(zipOf(Csv.write(listOf(LogExport.PRACTICE_HEADER_OLD) + sevenColumns), csv), empty)
        assertEquals(300, c.entries.single().value)
    }

    @Test fun onlyMinutesPracticesAreMultipliedInAnOldZip() {
        val csv = LogExport.entriesCsv(
            listOf(Entry("x1", "p-yoga", day("2026-11-01"), 1, null, null, 1), Entry("x2", "p-walk", day("2026-11-01"), 2, null, null, 2)),
            listOf(Practice("p-yoga", "Hatha Yoga", Measure.Done, null, 0, false, 0), Practice("p-walk", "Walk", Measure.Duration, null, 0, false, 1)),
        )
        val c = merge(zipOf(Csv.write(listOf(LogExport.PRACTICE_HEADER_OLD) + sevenColumns), csv), empty)
        assertEquals(mapOf("x1" to 1, "x2" to 120), c.entries.associate { it.id to it.value })
    }

    @Test fun anOldMinutesTargetThatIsMoreThanADayInSecondsIsBadTarget() {
        val rows = listOf(listOf("p-walk", "Walk", "minutes", "1441", "2026-10-01", "false", ""))
        val error = (LogImport.parseZip(ZipContents(Csv.write(listOf(LogExport.PRACTICE_HEADER_OLD) + rows), LogExport.entriesCsv(emptyList(), emptyList()), null), today) as Parsed.Failed).error
        assertEquals(ImportError(ImportErrorKind.BadTarget, "practices.csv", 2), error)
    }

    @Test fun aNewExportWritesDurationAndKeepsSecondsAsTheyAre() {
        val walk = Practice("p-walk", "Walk", Measure.Duration, 1800, day("2026-10-01"), false, 0, true, 2, day("2026-10-05"))
        val e = Entry("x1", "p-walk", day("2026-11-01"), 90, null, null, 1)
        val csv = LogExport.practicesCsv(listOf(walk), emptyList())
        assertTrue(csv, csv.contains(",duration,1800,"))
        val c = merge(zipOf(csv, LogExport.entriesCsv(listOf(e), listOf(walk))), empty)
        assertEquals(walk, c.practices.single())
        assertEquals(90, c.entries.single().value)
    }
}
