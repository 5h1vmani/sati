package com.padakanti.sati

/** One timed part of a sadhana. [label] is optional, for example "Breathing". */
data class Stage(val minutes: Int, val label: String = "")

/**
 * A saved practice: a list of stages, an optional settling time before the
 * start bell, and whether the bells vibrate instead of ringing.
 * Pure data, no Android types, so it runs in plain unit tests.
 */
data class Sadhana(
    val id: String,
    val name: String,
    val stages: List<Stage>,
    val settleSec: Int = 0,
    val vibrateOnly: Boolean = false,
) {
    val totalMin: Int get() = stages.sumOf { it.minutes }

    companion object {
        const val MAX_STAGES = 10
        const val MAX_STAGE_MIN = 240
        val SETTLE_CHOICES = listOf(0, 10, 30, 60, 120)

        /** Shown the first time the Sadhana tab opens. The user can edit or delete them. */
        val STARTERS = listOf(
            Sadhana("starter-20", "20 minutes", listOf(Stage(20))),
            Sadhana("starter-60", "30 + 30", listOf(Stage(30), Stage(30))),
            Sadhana("starter-45", "Bell every 15", listOf(Stage(15), Stage(15), Stage(15))),
        )

        /** What is wrong with [s], in words for the user, or null when it can be saved. */
        fun problem(s: Sadhana): String? = when {
            s.name.isBlank() -> "Give the sadhana a name."
            s.stages.isEmpty() -> "Add at least one stage."
            s.stages.size > MAX_STAGES -> "A sadhana can have up to $MAX_STAGES stages."
            s.stages.any { it.minutes < 1 } -> "Each stage needs at least 1 minute."
            s.stages.any { it.minutes > MAX_STAGE_MIN } -> "A stage can be up to $MAX_STAGE_MIN minutes."
            s.settleSec !in SETTLE_CHOICES -> "Choose a settling time from the list."
            else -> null
        }

        /**
         * [totalMin] split into [parts] stages as equal as whole minutes allow.
         * The first stages get the extra minutes. Null when a stage would be
         * shorter than one minute.
         */
        fun splitEvenly(totalMin: Int, parts: Int): List<Stage>? {
            if (parts < 1 || totalMin < parts) return null
            val base = totalMin / parts
            val extra = totalMin % parts
            return List(parts) { Stage(base + if (it < extra) 1 else 0) }
        }
    }
}

/** The bell played at a cue. [times] is the number of strikes, so the user can tell them apart with eyes closed. */
enum class Strike(val times: Int) { Start(1), StageChange(2), End(3) }

/** One bell in a sadhana: [offsetMs] after the start button, beginning stage [stage]. End keeps the last stage. */
data class Cue(val offsetMs: Long, val strike: Strike, val stage: Int)

/** Where a running sadhana stands at one moment. */
data class Position(
    /** True during the settling time, before the start bell. */
    val settling: Boolean,
    val stage: Int,
    /** Until the next bell: the start bell while settling, else the end of this stage. */
    val toNextBellMs: Long,
    val totalLeftMs: Long,
    /** Share of the practice done, 0 to 1. Settling time does not count. */
    val progress: Float,
)

/** What the screen shows after a sadhana ends. */
data class Summary(val name: String, val satMs: Long, val completed: Boolean)

/**
 * A sadhana in progress. All times are epoch milliseconds. A pause moves
 * every later cue back by its length, so [pausedMs] adds up all pauses.
 * [nextCue] is the index of the first cue that has not rung yet.
 */
data class Session(
    val sadhana: Sadhana,
    val startedAt: Long,
    val pausedAt: Long = 0,
    val pausedMs: Long = 0,
    val nextCue: Int = 0,
) {
    val paused: Boolean get() = pausedAt > 0

    val cues: List<Cue> get() = cuesOf(sadhana)

    /** Time spent in the session so far, leaving out pauses. */
    fun elapsed(now: Long): Long = ((if (paused) pausedAt else now) - startedAt - pausedMs).coerceAtLeast(0)

    /** When cue [index] rings. While paused, this is where it would be if the pause ended now. */
    fun timeOf(index: Int): Long = startedAt + pausedMs + cues[index].offsetMs

    /** The next cue's time, or null when every cue has rung. Meaningless while paused. */
    fun nextTime(): Long? = if (nextCue < cues.size) timeOf(nextCue) else null

    /**
     * The cue to ring now: the latest one that is due and has not rung.
     * Earlier missed cues are skipped, for example after the phone was off,
     * so the user never hears a burst of old bells. Null when none is due.
     */
    fun due(now: Long): Int? {
        if (paused) return null
        return (nextCue until cues.size).lastOrNull { timeOf(it) <= now + DUE_TOLERANCE_MS }
    }

    /** Practice time so far, from the start bell, at most the full length. */
    fun satMs(now: Long): Long =
        (elapsed(now) - sadhana.settleSec * 1000L).coerceIn(0, sadhana.totalMin * 60_000L)

    fun position(now: Long): Position {
        val settleMs = sadhana.settleSec * 1000L
        val totalMs = sadhana.totalMin * 60_000L
        val e = elapsed(now)
        if (e < settleMs) return Position(true, 0, settleMs - e, settleMs + totalMs - e, 0f)
        val t = (e - settleMs).coerceAtMost(totalMs)
        var stageEnd = 0L
        sadhana.stages.forEachIndexed { i, stage ->
            stageEnd += stage.minutes * 60_000L
            if (t < stageEnd || i == sadhana.stages.lastIndex) {
                return Position(false, i, stageEnd - t, totalMs - t, if (totalMs == 0L) 1f else t.toFloat() / totalMs)
            }
        }
        error("A sadhana always has at least one stage.")
    }

    companion object {
        /** An alarm may fire a little early. A cue this close counts as due. */
        const val DUE_TOLERANCE_MS = 1_000L

        fun cuesOf(s: Sadhana): List<Cue> {
            val settleMs = s.settleSec * 1000L
            val cues = mutableListOf(Cue(settleMs, Strike.Start, 0))
            var t = settleMs
            s.stages.forEachIndexed { i, stage ->
                t += stage.minutes * 60_000L
                cues += if (i < s.stages.lastIndex) Cue(t, Strike.StageChange, i + 1) else Cue(t, Strike.End, i)
            }
            return cues
        }
    }
}
