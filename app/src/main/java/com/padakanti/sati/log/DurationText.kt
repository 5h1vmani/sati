package com.padakanti.sati.log

/** Reading and splitting the seconds of a Duration entry or target. No Android code, so it is unit-tested. */
object DurationText {
    /** The seconds field of a form holds 0 to 59. */
    const val MAX_SECONDS_FIELD = 59

    private val PLAIN = Regex("""\d{1,6}""")
    private val MINUTES_SECONDS = Regex("""(\d{1,6}):(\d{1,2})""")
    private val SECONDS_ONLY = Regex("""(\d{1,6})\s*[sS]""")

    /** Splits [total] seconds into whole minutes and the seconds left over. */
    fun split(total: Int): Pair<Int, Int> = total / 60 to total % 60

    /**
     * The text of the two fields of a form for [total] seconds. Minutes are empty when
     * there are only seconds, and seconds are empty when there are none.
     */
    fun fieldsOf(total: Int): Pair<String, String> {
        val (minutes, seconds) = split(total)
        return (if (minutes == 0 && seconds > 0) "" else minutes.toString()) to (if (seconds == 0) "" else seconds.toString())
    }

    /**
     * The seconds of the two fields of a form. An empty field means 0, so
     * "30" in Minutes alone is 30 minutes. Returns null when both are empty,
     * when a field is not a whole number, when seconds are over 59, or when
     * the total is over [EntryRules.MAX_SECONDS].
     */
    fun fromFields(minutes: String, seconds: String): Int? {
        val m = minutes.trim()
        val s = seconds.trim()
        if (m.isEmpty() && s.isEmpty()) return null
        val whole = if (m.isEmpty()) 0 else m.toIntOrNull() ?: return null
        val rest = if (s.isEmpty()) 0 else s.toIntOrNull() ?: return null
        if (whole < 0 || rest !in 0..MAX_SECONDS_FIELD) return null
        return withinMax(whole.toLong() * 60 + rest)
    }

    /**
     * The seconds in the value of a hand-made CSV row for a Duration practice.
     * A plain number is minutes, "1:30" is minutes and seconds, and "45s" is
     * seconds. Returns null for anything else, or a total over the maximum.
     */
    fun parseHandMade(text: String): Int? {
        val t = text.trim()
        if (PLAIN.matches(t)) return withinMax(t.toLong() * 60)
        MINUTES_SECONDS.matchEntire(t)?.let { m ->
            val seconds = m.groupValues[2].toInt()
            return if (seconds > MAX_SECONDS_FIELD) null else withinMax(m.groupValues[1].toLong() * 60 + seconds)
        }
        SECONDS_ONLY.matchEntire(t)?.let { return withinMax(it.groupValues[1].toLong()) }
        return null
    }

    private fun withinMax(total: Long): Int? = if (total <= EntryRules.MAX_SECONDS) total.toInt() else null
}
