package com.padakanti.sati.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReorderTest {
    private val heights = listOf(100, 100, 100)

    @Test fun aSmallDragDoesNotSwap() {
        assertNull(Reorder.step(heights, 1, 10f))
        assertNull(Reorder.step(heights, 1, -10f))
        assertNull(Reorder.step(heights, 1, 0f))
    }

    @Test fun draggingDownPastTheMiddleOfTheNextRowSwapsWithIt() {
        // The row at index 0 has its centre at 50 plus the offset. The next row has its middle at 150, so 100 is the limit.
        assertNull(Reorder.step(heights, 0, 100f))
        val step = Reorder.step(heights, 0, 101f)!!
        assertEquals(1, step.index)
        assertEquals(1f, step.offset, 0.001f)
    }

    @Test fun theOffsetShrinksByTheHeightOfTheRowItPassed() {
        val step = Reorder.step(heights, 0, 130f)!!
        assertEquals(1, step.index)
        assertEquals(30f, step.offset, 0.001f)
    }

    @Test fun draggingUpPastTheMiddleOfThePreviousRowSwapsWithIt() {
        assertNull(Reorder.step(heights, 1, -100f))
        val step = Reorder.step(heights, 1, -130f)!!
        assertEquals(0, step.index)
        assertEquals(-30f, step.offset, 0.001f)
    }

    @Test fun theFirstRowCannotGoUpAndTheLastCannotGoDown() {
        assertNull(Reorder.step(heights, 0, -500f))
        assertNull(Reorder.step(heights, 2, 500f))
    }

    @Test fun rowsOfOtherHeightsUseTheirOwnMiddles() {
        // A tall row (200) is above a short one (50). The short one's middle is at 200 + 25.
        val mixed = listOf(200, 50, 80)
        assertNull(Reorder.step(mixed, 1, -112f))
        val step = Reorder.step(mixed, 1, -126f)!!
        assertEquals(0, step.index)
        assertEquals(74f, step.offset, 0.001f)
    }

    @Test fun anUnknownIndexNeverSwaps() {
        assertNull(Reorder.step(heights, -1, 50f))
        assertNull(Reorder.step(heights, 3, 50f))
        assertNull(Reorder.step(emptyList(), 0, 50f))
    }

    @Test fun aLongDragSwapsStepByStepUntilItSettles() {
        var index = 0
        var offset = 250f
        while (true) {
            val step = Reorder.step(heights, index, offset) ?: break
            index = step.index
            offset = step.offset
        }
        assertEquals(2, index)
        assertEquals(50f, offset, 0.001f)
    }
}
