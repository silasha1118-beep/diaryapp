package com.silasha.diaryvault.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumberNormalizerTest {
    @Test
    fun normalizeOtpDigits_mapsFullWidthAndStripsNoise() {
        assertEquals("123456", PhoneNumberNormalizer.normalizeOtpDigits("123456"))
        assertEquals("123456", PhoneNumberNormalizer.normalizeOtpDigits("１２３４５６"))
        assertEquals("123456", PhoneNumberNormalizer.normalizeOtpDigits(" 1 2 3 4 5 6 "))
        assertEquals("123456", PhoneNumberNormalizer.normalizeOtpDigits("12\u200B3456"))
    }

    @Test
    fun composeE164_asciiNormalizesNationalNumber() {
        val dial = PhoneNumberNormalizer.RegionDial.CN
        assertEquals("+8615014531587", PhoneNumberNormalizer.composeE164(dial, "15014531587"))
        assertEquals("+8615014531587", PhoneNumberNormalizer.composeE164(dial, "１５０１４５３１５８７"))
        assertEquals("+8615014531587", PhoneNumberNormalizer.composeE164(dial, "150 1453 1587"))
    }

    @Test
    fun isChinaMainland_excludesTaiwanAndNonMainland() {
        assertTrue(RoutingSMSAuthProvider.isChinaMainland("+8615014531587"))
        assertFalse(RoutingSMSAuthProvider.isChinaMainland("+886912345678"))
        assertFalse(RoutingSMSAuthProvider.isChinaMainland("+85284028001"))
        assertFalse(RoutingSMSAuthProvider.isChinaMainland("+85328"))
    }

    @Test
    fun composeE164_hkEightDigits() {
        assertEquals(
            "+85284028001",
            PhoneNumberNormalizer.composeE164(
                PhoneNumberNormalizer.RegionDial.HK,
                "84028001",
            ),
        )
        assertEquals(
            "+85284028001",
            PhoneNumberNormalizer.composeE164(
                PhoneNumberNormalizer.RegionDial.HK,
                "8402 8001",
            ),
        )
    }
}
