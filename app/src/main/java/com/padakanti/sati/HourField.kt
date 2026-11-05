package com.padakanti.sati

/** One of the four waking-hour times the user can change. Pure logic, so it is unit tested. */
enum class HourField(val title: String, val weekend: Boolean, val start: Boolean) {
    WeekdayStart("Weekdays start", weekend = false, start = true),
    WeekdayEnd("Weekdays end", weekend = false, start = false),
    WeekendStart("Weekends start", weekend = true, start = true),
    WeekendEnd("Weekends end", weekend = true, start = false);

    /** This field's current value, in minutes after midnight. */
    fun valueIn(weekday: Hours, weekend: Hours): Int {
        val hours = if (this.weekend) weekend else weekday
        return if (start) hours.startMin else hours.endMin
    }

    /** The hours with this field set to [minute], or null when the start would not be before the end. */
    fun apply(hours: Hours, minute: Int): Hours? =
        if (start) {
            if (minute < hours.endMin) hours.copy(startMin = minute) else null
        } else {
            if (minute > hours.startMin) hours.copy(endMin = minute) else null
        }

    /** Why [apply] refused the change, in words for the user. */
    val refusal: String
        get() = if (start) "The start must be before the end." else "The end must be after the start."
}
