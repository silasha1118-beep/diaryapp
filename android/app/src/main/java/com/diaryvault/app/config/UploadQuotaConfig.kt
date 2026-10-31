package com.diaryvault.app.config

/**
 * Daily diary **upload** quotas (Silas 2026-10-31): text create + multimedia (DA-15 stub).
 * Day boundary matches [DiaryUploadUsageGuard] / [com.diaryvault.app.service.MirrorUsageGuard]
 * (device local calendar day).
 *
 * Independent of Mirror AI quotas in [AIConfig] (Free 3→6, Plus 15, Pro 40).
 */
object UploadQuotaConfig {
    enum class Kind {
        TEXT,
        /** Voice / video diary create — API ready; UI deferred (DA-15). */
        MULTIMEDIA,
    }

    const val FREE_TEXT_SOFT_THRESHOLD = 4
    const val FREE_TEXT_HARD_LIMIT = 8

    const val FREE_MEDIA_SOFT_THRESHOLD = 3
    const val FREE_MEDIA_HARD_LIMIT = 5

    const val PLUS_TEXT_HARD_LIMIT = 20
    const val PLUS_MEDIA_HARD_LIMIT = 11

    const val PRO_TEXT_HARD_LIMIT = 40
    const val PRO_MEDIA_HARD_LIMIT = 22

    fun softThreshold(kind: Kind): Int = when (kind) {
        Kind.TEXT -> FREE_TEXT_SOFT_THRESHOLD
        Kind.MULTIMEDIA -> FREE_MEDIA_SOFT_THRESHOLD
    }

    /**
     * @param isAuthorPro Prefer over Plus when both are somehow true.
     * @param isAuthorPlus Plus entitlement when not Pro.
     */
    fun hardLimit(kind: Kind, isAuthorPlus: Boolean, isAuthorPro: Boolean = false): Int {
        return when {
            isAuthorPro -> when (kind) {
                Kind.TEXT -> PRO_TEXT_HARD_LIMIT
                Kind.MULTIMEDIA -> PRO_MEDIA_HARD_LIMIT
            }
            isAuthorPlus -> when (kind) {
                Kind.TEXT -> PLUS_TEXT_HARD_LIMIT
                Kind.MULTIMEDIA -> PLUS_MEDIA_HARD_LIMIT
            }
            else -> when (kind) {
                Kind.TEXT -> FREE_TEXT_HARD_LIMIT
                Kind.MULTIMEDIA -> FREE_MEDIA_HARD_LIMIT
            }
        }
    }
}
