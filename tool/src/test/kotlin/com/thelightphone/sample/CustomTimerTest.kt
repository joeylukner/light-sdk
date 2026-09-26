package com.thelightphone.sample

import kotlin.test.Test
import kotlin.test.assertEquals

class CustomTimerTest {
    @Test
    fun digitsFillFromTheRight() {
        assertEquals("000000", padCustomDigits(""))
        assertEquals("000130", padCustomDigits("130"))
        assertEquals("123456", padCustomDigits("123456"))
    }

    @Test
    fun digitsConvertToSeconds() {
        assertEquals(0, customDigitsToSeconds(""))
        assertEquals(90, customDigitsToSeconds("130"))
        assertEquals(3600 + 23 * 60 + 45, customDigitsToSeconds("12345"))
    }

    @Test
    fun overflowingFieldsCarryLikeAMicrowave() {
        assertEquals(90, customDigitsToSeconds("90"))
        assertEquals(99 * 60 + 99, customDigitsToSeconds("9999"))
    }

    @Test
    fun cappedAt23h59m59s() {
        assertEquals(23 * 3600 + 59 * 60 + 59, customDigitsToSeconds("995959"))
    }
}
