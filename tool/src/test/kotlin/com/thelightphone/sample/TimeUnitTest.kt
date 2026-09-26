package com.thelightphone.sample

import kotlin.test.Test
import kotlin.test.assertEquals

class TimeUnitTest {
    private fun hms(h: Int, m: Int, s: Int) = h * 3600 + m * 60 + s

    @Test
    fun secondsStepBy15() {
        assertEquals(hms(0, 0, 15), TimeUnit.Seconds.stepUp(0))
        assertEquals(hms(0, 0, 30), TimeUnit.Seconds.stepUp(hms(0, 0, 15)))
    }

    @Test
    fun goingUpPastMaxCarriesIntoNextUnit() {
        assertEquals(hms(0, 1, 0), TimeUnit.Seconds.stepUp(hms(0, 0, 45)))
        assertEquals(hms(1, 0, 30), TimeUnit.Minutes.stepUp(hms(0, 59, 30)))
        assertEquals(hms(2, 0, 0), TimeUnit.Seconds.stepUp(hms(1, 59, 45)))
    }

    @Test
    fun goingDownBorrowsFromNextUnit() {
        assertEquals(hms(0, 4, 45), TimeUnit.Seconds.stepDown(hms(0, 5, 0)))
        assertEquals(hms(0, 59, 0), TimeUnit.Minutes.stepDown(hms(1, 0, 0)))
        assertEquals(hms(1, 59, 45), TimeUnit.Seconds.stepDown(hms(2, 0, 0)))
    }

    @Test
    fun goingDownFromZeroWrapsThatRowToItsMax() {
        assertEquals(hms(0, 0, 45), TimeUnit.Seconds.stepDown(0))
        assertEquals(hms(0, 59, 0), TimeUnit.Minutes.stepDown(0))
        assertEquals(hms(23, 0, 0), TimeUnit.Hours.stepDown(0))
        // lower units are kept when a higher row wraps
        assertEquals(hms(0, 59, 30), TimeUnit.Minutes.stepDown(hms(0, 0, 30)))
        assertEquals(hms(23, 30, 0), TimeUnit.Hours.stepDown(hms(0, 30, 0)))
    }

    @Test
    fun goingUpPast23HoursWrapsToZero() {
        assertEquals(hms(0, 30, 0), TimeUnit.Hours.stepUp(hms(23, 30, 0)))
        assertEquals(0, TimeUnit.Seconds.stepUp(hms(23, 59, 45)))
    }

    @Test
    fun valueInSplitsTotalIntoRows() {
        val total = hms(2, 7, 45)
        assertEquals(2, TimeUnit.Hours.valueIn(total))
        assertEquals(7, TimeUnit.Minutes.valueIn(total))
        assertEquals(45, TimeUnit.Seconds.valueIn(total))
    }
}
