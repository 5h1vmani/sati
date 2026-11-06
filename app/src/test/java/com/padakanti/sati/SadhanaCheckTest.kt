package com.padakanti.sati

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SadhanaCheckTest {
    private val good = Sadhana("s-1", "Morning", listOf(Stage(20, "Breath")))

    private fun json(vararg sadhanas: String) = "[" + sadhanas.joinToString(",") + "]"

    private fun raw(id: String = "s-1", name: String = "Morning", minutes: String = "20", label: String = "", settle: Int = 0) =
        """{"id":"$id","name":"$name","settleSec":$settle,"vibrateOnly":false,"stages":[{"minutes":$minutes,"label":"$label"}]}"""

    @Test fun aNormalSadhanaHasNoFlaw() = assertNull(SadhanaCheck.flaw(good))

    @Test fun theStartersAreValid() = Sadhana.STARTERS.forEach { assertNull(SadhanaCheck.flaw(it)) }

    @Test fun negativeMinutesAreFlawed() = assertEquals(SadhanaFlaw.Basic, SadhanaCheck.flaw(good.copy(stages = listOf(Stage(-5)))))

    @Test fun hugeMinutesAreFlawed() = assertEquals(SadhanaFlaw.Basic, SadhanaCheck.flaw(good.copy(stages = listOf(Stage(Int.MAX_VALUE)))))

    @Test fun noStagesAreFlawed() = assertEquals(SadhanaFlaw.Basic, SadhanaCheck.flaw(good.copy(stages = emptyList())))

    @Test fun badSettleIsFlawed() = assertEquals(SadhanaFlaw.Basic, SadhanaCheck.flaw(good.copy(settleSec = 7)))

    @Test fun blankAndLongIdsAreFlawed() {
        assertEquals(SadhanaFlaw.BadId, SadhanaCheck.flaw(good.copy(id = " ")))
        assertEquals(SadhanaFlaw.BadId, SadhanaCheck.flaw(good.copy(id = "x".repeat(65))))
        assertNull(SadhanaCheck.flaw(good.copy(id = "x".repeat(64))))
    }

    @Test fun longNamesAndLabelsAreFlawed() {
        assertEquals(SadhanaFlaw.TooLong, SadhanaCheck.flaw(good.copy(name = "n".repeat(61))))
        assertEquals(SadhanaFlaw.TooLong, SadhanaCheck.flaw(good.copy(stages = listOf(Stage(5, "l".repeat(61))))))
        assertNull(SadhanaCheck.flaw(good.copy(name = "n".repeat(60))))
    }

    @Test fun hiddenCharactersAreFlawed() {
        assertEquals(SadhanaFlaw.HiddenCharacters, SadhanaCheck.flaw(good.copy(name = "Mor​ning")))
        assertEquals(SadhanaFlaw.HiddenCharacters, SadhanaCheck.flaw(good.copy(name = "Mor‮ning")))
        assertEquals(SadhanaFlaw.HiddenCharacters, SadhanaCheck.flaw(good.copy(stages = listOf(Stage(5, "a\nb")))))
    }

    @Test fun normalizedUsesNfc() {
        val composed = SadhanaCheck.normalized(good.copy(name = "Café"))
        assertEquals("Café", composed.name)
    }

    // ---------- reading from storage ----------

    @Test fun readsTheFormatOfVersion120() {
        val v120 = """[{"id":"s-1700000000000","name":"Sukha Kriya","settleSec":60,"vibrateOnly":true,""" +
            """"stages":[{"minutes":30,"label":"Breathing"},{"minutes":30,"label":""}]}]"""
        val list = SadhanaJson.decodeList(v120)!!
        assertEquals(1, list.size)
        assertEquals("Sukha Kriya", list[0].name)
        assertEquals(listOf(Stage(30, "Breathing"), Stage(30, "")), list[0].stages)
        assertEquals(60, list[0].settleSec)
    }

    @Test fun optionalFieldsMayBeMissing() {
        val list = SadhanaJson.decodeList("""[{"id":"a","name":"A","stages":[{"minutes":5}]}]""")!!
        assertEquals(Sadhana("a", "A", listOf(Stage(5, "")), 0, false), list.single())
    }

    @Test fun theOwnEncodingReadsBack() = assertEquals(listOf(good), SadhanaJson.decodeList(SadhanaJson.encodeList(listOf(good))))

    @Test fun decodeListDropsASadhanaWithMinusFiveMinutes() {
        val list = SadhanaJson.decodeList(json(raw(id = "bad", minutes = "-5"), raw(id = "ok")))!!
        assertEquals(listOf("ok"), list.map { it.id })
    }

    @Test fun decodeListDropsBrokenAndEmptyStageEntries() {
        val broken = """{"id":"x","name":"X"}"""
        val noStages = """{"id":"y","name":"Y","stages":[]}"""
        assertEquals(listOf("ok"), SadhanaJson.decodeList(json(broken, noStages, raw(id = "ok")))!!.map { it.id })
    }

    @Test fun decodeListOfNonsenseIsNull() = assertNull(SadhanaJson.decodeList("not json"))

    @Test fun aSessionWithMinusFiveMinutesIsNull() {
        val session = """{"sadhana":${raw(minutes = "-5")},"startedAt":1,"pausedAt":0,"pausedMs":0,"nextCue":0}"""
        assertNull(SadhanaJson.decodeSession(session))
    }

    @Test fun aGoodSessionReadsBack() {
        val session = Session(good, 1000)
        assertEquals(session, SadhanaJson.decodeSession(SadhanaJson.encodeSession(session)))
    }

    @Test fun aHugeStageListIsRefusedBeforeItIsBuilt() {
        val stages = List(11) { """{"minutes":1}""" }.joinToString(",")
        assertTrue(SadhanaJson.decodeList("""[{"id":"a","name":"A","stages":[$stages]}]""")!!.isEmpty())
    }

    // ---------- reading from an import ----------

    @Test fun importKeepsGoodSadhanasAndSkipsBadOnes() {
        val result = SadhanaJson.decodeImport(json(raw(id = "ok"), raw(id = "bad", minutes = "-5"), """{"id":"x"}""", raw(id = "ok2", settle = 7)))!!
        assertEquals(listOf("ok"), result.sadhanas.map { it.id })
        assertEquals(3, result.skipped)
    }

    @Test fun importSkipsTextRuleBreakers() {
        val result = SadhanaJson.decodeImport(json(raw(id = "a", name = "Mor\\u200Bning"), raw(id = "b", name = "n".repeat(61)), raw(id = "c")))!!
        assertEquals(listOf("c"), result.sadhanas.map { it.id })
        assertEquals(2, result.skipped)
    }

    @Test fun importSkipsARepeatedIdInTheFile() {
        val result = SadhanaJson.decodeImport(json(raw(id = "a"), raw(id = "a", name = "Second")))!!
        assertEquals(listOf("Morning"), result.sadhanas.map { it.name })
        assertEquals(1, result.skipped)
    }

    @Test fun importOfMoreThanFiftyIsNotRefusedHere() =
        assertEquals(60, SadhanaJson.decodeImport(json(*Array(60) { raw(id = "s$it") }))!!.sadhanas.size)

    @Test fun importOfTextThatIsNotAListFails() {
        assertNull(SadhanaJson.decodeImport("not json"))
        assertNull(SadhanaJson.decodeImport("""{"id":"a"}"""))
    }

    @Test fun importNormalizesNames() {
        assertEquals("Caf\u00E9", SadhanaJson.decodeImport(json(raw(name = "Cafe\u0301")))!!.sadhanas.single().name)
    }

    @Test fun aNewDraftWithNoNameStillReadsBackAsADraft() {
        val draft = Sadhana("s-1", "", listOf(Stage(20)))
        assertEquals(draft, SadhanaJson.decodeDraft(SadhanaJson.encodeList(listOf(draft))))
        assertTrue(SadhanaJson.decodeList(SadhanaJson.encodeList(listOf(draft)))!!.isEmpty())
    }

    // ---------- two levels ----------

    @Test fun safeKeepsAnOldNameWithTextRulesBroken() {
        val old = good.copy(name = "n".repeat(100), stages = listOf(Stage(5, "a\u200Bb")), id = "x".repeat(100))
        assertTrue(SadhanaCheck.isSafe(old))
        assertEquals(false, SadhanaCheck.isValid(old))
    }

    @Test fun safeRejectsWhatCanCrashTheTimer() {
        assertEquals(false, SadhanaCheck.isSafe(good.copy(stages = listOf(Stage(-5)))))
        assertEquals(false, SadhanaCheck.isSafe(good.copy(stages = emptyList())))
        assertEquals(false, SadhanaCheck.isSafe(good.copy(settleSec = 7)))
        assertEquals(false, SadhanaCheck.isSafe(good.copy(id = " ")))
        assertEquals(false, SadhanaCheck.isSafe(good.copy(stages = List(Sadhana.MAX_STAGES + 1) { Stage(1) })))
        assertTrue(SadhanaCheck.isSafe(good))
    }

    @Test fun validImpliesSafe() = Sadhana.STARTERS.forEach { assertTrue(SadhanaCheck.isValid(it) && SadhanaCheck.isSafe(it)) }

    @Test fun storedSadhanaWithHiddenCharactersOrLongTextIsKept() {
        val stored = json(raw(id = "a", name = "Mor\\u200Bning"), raw(id = "b", name = "n".repeat(100)), raw(id = "c", minutes = "-5"))
        assertEquals(listOf("a", "b"), SadhanaJson.decodeList(stored)!!.map { it.id })
    }

    @Test fun aStoredSessionWithHiddenCharactersIsKept() {
        val session = """{"sadhana":${raw(name = "Mor\\u200Bning")},"startedAt":1,"pausedAt":0,"pausedMs":0,"nextCue":0}"""
        assertNotNull(SadhanaJson.decodeSession(session))
    }

    @Test fun importAcceptsAnIndicNameWithJoiners() {
        val name = "\\u0915\\u094D\\u200D\\u0937 \\u0C16\\u200C\\u0C15"
        assertEquals(1, SadhanaJson.decodeImport(json(raw(name = name)))!!.sadhanas.size)
    }

    @Test fun theEditorCheckAllowsJoinersToo() =
        assertNull(SadhanaCheck.flaw(good.copy(name = "\u0915\u094D\u200D\u0937", stages = listOf(Stage(5, "\u0C16\u200C\u0C15")))))

    @Test fun theLimitIsFifty() = assertEquals(50, SadhanaCheck.MAX_SADHANAS)
}
