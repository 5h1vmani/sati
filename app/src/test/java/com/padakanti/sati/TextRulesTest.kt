package com.padakanti.sati

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextRulesTest {
    @Test fun plainTextIsFine() = assertFalse(TextRules.hasHidden("Hatha Yoga, ॐ नमः शिवाय 🙏"))

    @Test fun zeroWidthAndBidiMarksAreHidden() {
        assertTrue(TextRules.hasHidden("a​b"))
        assertTrue(TextRules.hasHidden("a‎b"))
        assertTrue(TextRules.hasHidden("a‮b"))
        assertTrue(TextRules.hasHidden("a⁦b"))
        assertTrue(TextRules.hasHidden("﻿a"))
    }

    @Test fun controlCharactersAreHidden() {
        assertTrue(TextRules.hasHidden("a\u0000b"))
        assertTrue(TextRules.hasHidden("a\tb"))
        assertTrue(TextRules.hasHidden("a\u0085b"))
    }

    @Test fun newLinesAreRefusedUnlessAllowed() {
        assertTrue(TextRules.hasHidden("a\nb"))
        assertTrue(TextRules.hasHidden("a\r\nb"))
        assertFalse(TextRules.hasHidden("a\nb\r\nc", allowNewlines = true))
        assertTrue(TextRules.hasHidden("a\tb\nc", allowNewlines = true))
    }

    @Test fun nfcJoinsALetterAndItsAccent() = assertEquals("é", TextRules.nfc("é"))

    @Test fun joinersAreAllowedInIndicNames() {
        // Telugu with ZWNJ, Devanagari with ZWJ and ZWNJ, and an emoji joined with ZWJ.
        assertFalse(TextRules.hasHidden("\u0C38\u0C41\u0C16\u200C\u0C15\u0C4D\u0C30\u0C3F\u0C2F"))
        assertFalse(TextRules.hasHidden("\u0915\u094D\u200D\u0937 \u0915\u094D\u200C\u0937"))
        assertFalse(TextRules.hasHidden("\uD83D\uDC69\u200D\uD83D\uDCBB"))
        assertFalse(TextRules.hasHidden("a\u200Cb\u200Dc", allowNewlines = true))
    }

    @Test fun otherFormatCharactersStayRefusedNextToJoiners() {
        assertTrue(TextRules.hasHidden("\u0915\u094D\u200D\u0937\u200B"))
        for (cp in listOf(0x200B, 0x200E, 0x200F, 0x202A, 0x202B, 0x202C, 0x202D, 0x202E, 0x2066, 0x2067, 0x2068, 0x2069, 0xFEFF)) {
            assertTrue("U+%04X".format(cp), TextRules.hasHidden("a${String(Character.toChars(cp))}b"))
        }
    }

    @Test fun lineAndParagraphSeparatorsAreRefused() {
        assertTrue(TextRules.hasHidden("a\u2028b"))
        assertTrue(TextRules.hasHidden("a\u2029b"))
        assertTrue(TextRules.hasHidden("a\u2028b", allowNewlines = true))
        assertTrue(TextRules.hasHidden("a\u2029b", allowNewlines = true))
    }
}
