package com.diaryvault.app.config

/**
 * Diary **vault upload** quotas (Silas 2026-11-01): one shared counter for text + multimedia.
 *
 * - Free: **lifetime** hard cap (not daily) — hard stop → paywall. Multimedia is included in
 *   the same lifetime 40 (Free is not media-blocked before that cap).
 * - Author Plus / Pro: **daily** hard caps (device local calendar day).
 *
 * Independent of Mirror AI quotas in [AIConfig].
 */
object UploadQuotaConfig {
    enum class Kind {
        TEXT,
        /** Voice / video diary create — counts toward the same vault bucket for all tiers incl. Free. */
        MULTIMEDIA,
    }

    const val FREE_LIFETIME_VAULT_LIMIT = 40
    const val PLUS_DAILY_VAULT_LIMIT = 8
    const val PRO_DAILY_VAULT_LIMIT = 16

    /** Soft ladders removed (2026-11-01). */
    fun softThreshold(kind: Kind): Int {
        @Suppress("UNUSED_PARAMETER")
        kind
        return 0
    }

    /**
     * Effective hard cap for the vault upload bucket (kind is ignored — unified counter).
     * @param isAuthorPro Prefer over Plus when both are somehow true.
     * @param isAuthorPlus Plus entitlement when not Pro.
     */
    fun hardLimit(kind: Kind, isAuthorPlus: Boolean, isAuthorPro: Boolean = false): Int {
        @Suppress("UNUSED_PARAMETER")
        kind
        return when {
            isAuthorPro -> PRO_DAILY_VAULT_LIMIT
            isAuthorPlus -> PLUS_DAILY_VAULT_LIMIT
            else -> FREE_LIFETIME_VAULT_LIMIT
        }
    }
}
