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
        assertEquals(ImportError(ImportErrorKind.BadValue, "", 1), handMadeError("2026-11-01,A,0,\n"))
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
        val yoga = Practice("p1", "Hatha Yoga", Measure.Minutes, 30, 0, false, 0)
        val c = merge(handMade("2026-11-01,  hatha YOGA ,45,\n"), LogState(listOf(yoga), emptyList()))
        assertEquals(emptyList<String>(), c.unknownNames)
        assertEquals(emptyList<Practice>(), c.practices)
        assertEquals("p1", c.entries.single().practiceId)
    }

    @Test fun newPracticesGoAfterTheExistingOnes() {
        val yoga = Practice("p1", "Hatha Yoga", Measure.Minutes, 30, 0, false, 4)
        val c = merge(handMade("2026-11-01,Kriya,10,\n"), LogState(listOf(yoga), emptyList()))
        assertEquals(5, c.practices.single().position)
    }

    @Test fun anIdLessRowThatMatchesPracticeDayAndValueIsSkipped() {
        val yoga = Practice("p1", "Hatha Yoga", Measure.Minutes, null, 0, false, 0)
        val existing = Entry("e1", "p1", day("2026-11-01"), 45, 7, null, 0)
        val file = handMade("2026-11-01,Hatha Yoga,45,\n2026-11-01,Hatha Yoga,30,\n2026-11-02,Hatha Yoga,45,\n")
        val c = merge(file, LogState(listOf(yoga), listOf(existing)))
        assertEquals(1, c.entriesSkipped)
        assertEquals(listOf(30, 45), c.entries.map { it.value })
    }

    @Test fun twoIdenticalRowsInOneFileAreBothAddedOnce() {
        val file = handMade("2026-11-01,Walk,20,\n2026-11-01,Walk,20,\n")
        val first = merge(file, empty, mapOf("Walk" to Measure.Minutes))
        assertEquals(2, first.entries.size)
        val second = merge(file, apply(empty, first))
        assertEquals(0, second.entries.size)
        assertEquals(2, second.entriesSkipped)
    }

    @Test fun importingTheSameHandMadeFileTwiceChangesNothing() {
        val file = handMade("2026-11-01,Hatha Yoga,45,85\n2026-11-02,Kriya,108,\n2026-11-02,Kriya,20,40\n")
        val first = merge(file, empty, mapOf("Hatha Yoga" to Measure.Minutes, "Kriya" to Measure.Count))
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
        val mine = Practice("mine", "Hatha Yoga", Measure.Minutes, null, 0, false, 0)
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
        val mine = yoga.copy(measure = Measure.Minutes)
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
        val practices = names.mapIndexed { i, n -> Practice("p$i", n, Measure.Minutes, null, day("2026-10-01"), false, i) }
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
        val composed = Practice("p1", "Caf\u00E9", Measure.Minutes, null, 0, false, 0)
        val c = merge(handMade("2026-11-01,Cafe\u0301,5,\n"), LogState(listOf(composed), emptyList()))
        assertEquals(emptyList<String>(), c.unknownNames)
        assertEquals("p1", c.entries.single().practiceId)
        val fresh = merge(handMade("2026-11-01,Cafe\u0301,5,\n"), empty)
        assertEquals(listOf("Caf\u00E9"), fresh.practices.map { it.name })
    }
}
