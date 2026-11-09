package com.silasha.diaryvault.auth

import com.silasha.diaryvault.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugPhoneTestCodesTest {
    @Test
    fun debugDefaultsIncludeHkAndCnTestPairs() {
        // Unit tests run against the debug variant BuildConfig in this module.
        if (!BuildConfig.DEBUG) return
        val pairs = DebugPhoneTestCodes.pairs()
        assertTrue(pairs.isNotEmpty())
        assertEquals("123456", pairs["+85284028001"])
        assertEquals("123456", pairs["+8615014531587"])
        assertEquals("123456", DebugPhoneTestCodes.codeFor("+85284028001"))
        assertEquals("123456", DebugPhoneTestCodes.codeFor("＋８５２８４０２８００１"))
        assertNull(DebugPhoneTestCodes.codeFor("+886912345678"))
    }
}
