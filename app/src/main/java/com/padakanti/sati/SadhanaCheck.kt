package com.padakanti.sati

/** What is wrong with a sadhana that [Sadhana.problem] does not catch, or that it does. */
enum class SadhanaFlaw { Basic, BadId, TooLong, HiddenCharacters }

/**
 * Two levels of check for a sadhana that did not come from the editor on this
 * screen.
 *
 * [isSafe] is for data already on the phone. It fails only for a problem that
 * can crash or break the timer, such as minutes below 1 or no stages. Text
 * rules do not apply, so a name that was fine in an older version is kept.
 *
 * [isValid] is for an import and for the editor. It adds the id and text rules.
 */
object SadhanaCheck {
    /** The most sadhanas the app lets the user save, and the most an import accepts. */
    const val MAX_SADHANAS = 50
    const val MAX_ID = 64
    const val MAX_TEXT = 60

    /** True when [s] cannot crash or break the timer: [Sadhana.problem] finds nothing and the id is not blank. */
    fun isSafe(s: Sadhana): Boolean = Sadhana.problem(s) == null && s.id.isNotBlank()

    /** The first flaw of [s], or null when it passes every check. */
    fun flaw(s: Sadhana): SadhanaFlaw? = when {
        Sadhana.problem(s) != null -> SadhanaFlaw.Basic
        s.id.isBlank() || s.id.length > MAX_ID -> SadhanaFlaw.BadId
        s.name.length > MAX_TEXT || s.stages.any { it.label.length > MAX_TEXT } -> SadhanaFlaw.TooLong
        TextRules.hasHidden(s.name) || s.stages.any { TextRules.hasHidden(it.label) } -> SadhanaFlaw.HiddenCharacters
        else -> null
    }

    /** True when [s] passes every check, including the id and text rules. */
    fun isValid(s: Sadhana): Boolean = flaw(s) == null

    /** The name and labels in NFC form, so equal names compare equal. */
    fun normalized(s: Sadhana): Sadhana =
        s.copy(name = TextRules.nfc(s.name), stages = s.stages.map { it.copy(label = TextRules.nfc(it.label)) })
}
