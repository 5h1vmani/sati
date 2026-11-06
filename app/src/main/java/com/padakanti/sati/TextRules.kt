package com.padakanti.sati

import java.text.Normalizer

/** Checks for text that comes from the user or from an imported file. */
object TextRules {
    private const val ZWNJ = 0x200C
    private const val ZWJ = 0x200D
    private const val LINE_SEPARATOR = 0x2028
    private const val PARAGRAPH_SEPARATOR = 0x2029

    /** The same letters can be written in more than one way. NFC picks one, so two names compare equal. */
    fun nfc(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFC)

    /**
     * True when [text] has a control character (Unicode category Cc) or a
     * format character (Cf). Format characters include zero-width marks and
     * the bidi overrides that can make a name look like another name. The line and paragraph separators (U+2028, U+2029) are refused too. Two
     * are allowed, because Hindi, Telugu and other scripts, and emoji
     * sequences, need them: the zero-width joiner and non-joiner. A new
     * line is a control character; [allowNewlines] lets LF and CR through, for notes.
     */
    fun hasHidden(text: String, allowNewlines: Boolean = false): Boolean {
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            if (cp == ZWNJ || cp == ZWJ) continue
            // The line and paragraph separators are not Cc or Cf, but they break a line like a new line does.
            if (cp == LINE_SEPARATOR || cp == PARAGRAPH_SEPARATOR) return true
            if (allowNewlines && (cp == '\n'.code || cp == '\r'.code)) continue
            val type = Character.getType(cp)
            if (type == Character.CONTROL.toInt() || type == Character.FORMAT.toInt()) return true
        }
        return false
    }
}
