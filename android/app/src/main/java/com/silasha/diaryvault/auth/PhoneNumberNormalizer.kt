package com.silasha.diaryvault.auth

/**
 * First-market dial codes + E.164 helpers — mirrors iOS `PhoneNumberNormalizer`.
 *
 * Kotlin [Char.isDigit] is true for full-width / other Unicode digits (common on MIUI IMEs).
 * Firebase Phone Auth and Console test lists expect ASCII `0-9` in E.164 and OTP codes.
 */
object PhoneNumberNormalizer {
    enum class RegionDial(val code: String, val labelResHint: String) {
        TW("+886", "tw"),
        HK("+852", "hk"),
        CN("+86", "cn"),
        MY("+60", "my"),
        SG("+65", "sg"),
        TH("+66", "th"),
        JP("+81", "jp"),
        ;

        companion object {
            fun fromCode(raw: String): RegionDial? {
                val digits = "+" + asciiDigitsOnly(raw)
                return entries.firstOrNull { it.code == digits || raw.startsWith(it.code) }
            }
        }
    }

    /**
     * Map ASCII + full-width digits to ASCII `0-9`; drop everything else
     * (spaces, ZWSP, dashes, non-digit Unicode).
     */
    fun asciiDigitsOnly(raw: String): String =
        buildString(raw.length) {
            for (ch in raw) {
                when (ch) {
                    in '0'..'9' -> append(ch)
                    in '\uFF10'..'\uFF19' -> append('0' + (ch - '\uFF10'))
                }
            }
        }

    /**
     * OTP entry may use full-width digits (MIUI / CN IMEs). Firebase and CF gateways
     * expect ASCII `0-9` — map both so Console test codes like `123456` verify.
     */
    fun normalizeOtpDigits(raw: String): String = asciiDigitsOnly(raw)

    /** Phone field input: keep a leading `+`, ASCII-normalize digits, drop other chars. */
    fun normalizePhoneInput(raw: String): String {
        val hasPlus = raw.trimStart().startsWith("+") || raw.any { it == '+' || it == '\uFF0B' }
        val digits = asciiDigitsOnly(raw)
        return if (hasPlus) "+$digits" else digits
    }

    fun normalizeToE164(raw: String): String {
        val digits = asciiDigitsOnly(raw.trim())
        return "+$digits"
    }

    fun composeE164(dial: RegionDial, nationalNumber: String): String {
        val digits = asciiDigitsOnly(nationalNumber)
        val national = if (digits.startsWith("0")) digits.drop(1) else digits
        return dial.code + national
    }

    fun nationalDigits(nationalNumber: String): String {
        val digits = asciiDigitsOnly(nationalNumber)
        return if (digits.startsWith("0")) digits.drop(1) else digits
    }

    fun isPlausibleNationalNumber(dial: RegionDial, nationalNumber: String): Boolean {
        val national = nationalDigits(nationalNumber)
        return when (dial) {
            RegionDial.TW -> national.length == 9
            RegionDial.HK, RegionDial.SG -> national.length == 8
            RegionDial.CN -> national.length == 11
            RegionDial.MY -> national.length in 9..10
            RegionDial.TH -> national.length == 9
            RegionDial.JP -> national.length == 10
        }
    }

    fun isPlausibleE164(e164: String): Boolean {
        if (!e164.startsWith("+")) return false
        val digits = asciiDigitsOnly(e164)
        return digits.length in 8..15
    }

    fun maskedDisplay(e164: String): String {
        if (e164.length <= 6) return e164
        val prefix = e164.take(4)
        val suffix = e164.takeLast(2)
        val middle = "•".repeat((e164.length - 6).coerceAtLeast(0))
        return prefix + middle + suffix
    }

    /** Parse dial label like "+886 台灣" / "+886 Taiwan" into RegionDial. */
    fun dialFromLabel(label: String): RegionDial {
        val digits = asciiDigitsOnly(label)
        val code = when {
            digits.startsWith("886") -> "886"
            digits.startsWith("852") -> "852"
            digits.startsWith("86") -> "86"
            digits.startsWith("60") -> "60"
            digits.startsWith("65") -> "65"
            digits.startsWith("66") -> "66"
            digits.startsWith("81") -> "81"
            else -> digits.take(3)
        }
        return RegionDial.fromCode("+$code") ?: RegionDial.TW
    }
}
