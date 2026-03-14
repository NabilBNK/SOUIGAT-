package com.souigat.mobile.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CurrencyFormatterTest {

    @Test
    fun parseCurrencyInput_parsesWholeUnits() {
        assertEquals(120_000L, parseCurrencyInput("1200"))
    }

    @Test
    fun parseCurrencyInput_parsesDecimalInput() {
        assertEquals(123_456L, parseCurrencyInput("1 234,56"))
    }

    @Test
    fun parseCurrencyInput_returnsNullForInvalidInput() {
        assertNull(parseCurrencyInput("12..3"))
    }
}
