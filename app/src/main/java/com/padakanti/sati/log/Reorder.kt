package com.padakanti.sati.log

/** The arithmetic of dragging a row to a new place in a list. No Android code, so it is unit-tested. */
object Reorder {
    /** The dragged row is now at [index], and its drag offset from that place is [offset]. */
    data class Step(val index: Int, val offset: Float)

    /**
     * Whether the dragged row at [index] has moved far enough to swap with a neighbour.
     * [heights] are the row heights in the list order, and [offset] is how far the row has been dragged from its
     * place, down being positive. The row swaps when its centre passes the middle of the neighbour.
     * After a swap the row has a new place, so the offset changes by the neighbour's height and the row stays
     * where the finger is. Returns null when there is no swap. Call again until it returns null.
     */
    fun step(heights: List<Int>, index: Int, offset: Float): Step? {
        if (index !in heights.indices) return null
        val top = heights.take(index).sum()
        val centre = top + offset + heights[index] / 2f
        if (index > 0) {
            val above = heights[index - 1]
            if (centre < top - above + above / 2f) return Step(index - 1, offset + above)
        }
        if (index < heights.lastIndex) {
            val below = heights[index + 1]
            if (centre > top + heights[index] + below / 2f) return Step(index + 1, offset - below)
        }
        return null
    }
}
