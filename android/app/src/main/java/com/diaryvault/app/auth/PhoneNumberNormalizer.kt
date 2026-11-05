package com.diaryvault.app.auth

/**
 * First-market dial codes + E.164 helpers — mirrors iOS `PhoneNumberNormalizer`.
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
                val digits = "+" + raw.filter { it.isDigit() }
                return entries.firstOrNull { it.code == digits || raw.startsWith(it.code) }
            }
        }
    }

    fun normalizeToE164(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("+")) {
            return "+" + trimmed.drop(1).filter { it.isDigit() }
        }
        return "+" + trimmed.filter { it.isDigit() }
    }

    fun composeE164(dial: RegionDial, nationalNumber: String): String {
        val digits = nationalNumber.filter { it.isDigit() }
        val national = if (digits.startsWith("0")) digits.drop(1) else digits
        return dial.code + national
    }

    fun nationalDigits(nationalNumber: String): String {
        val digits = nationalNumber.filter { it.isDigit() }
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
        val digits = e164.drop(1).filter { it.isDigit() }
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
        val code = "+" + label.filter { it.isDigit() }.take(3).let { digits ->
            when {
                digits.startsWith("886") -> "886"
                digits.startsWith("852") -> "852"
                digits.startsWith("86") -> "86"
                digits.startsWith("60") -> "60"
                digits.startsWith("65") -> "65"
                digits.startsWith("66") -> "66"
                digits.startsWith("81") -> "81"
                else -> digits
            }
        }
        return RegionDial.fromCode("+$code") ?: RegionDial.TW
    }
}
