package com.diaryvault.app.billing

/**
 * Google Play product IDs for Author Plus / Author Pro — aligned with iOS [BillingConfig].
 *
 * Plus:
 * - `DIARY_IAP_PLUS_MONTHLY_PRODUCT_ID` (alias `DIARY_IAP_MONTHLY_PRODUCT_ID`)
 * - `DIARY_IAP_PLUS_QUARTERLY_PRODUCT_ID`
 * - `DIARY_IAP_PLUS_YEARLY_PRODUCT_ID` (alias `DIARY_IAP_YEARLY_PRODUCT_ID`)
 *
 * Pro:
 * - `DIARY_IAP_PRO_MONTHLY_PRODUCT_ID`
 * - `DIARY_IAP_PRO_QUARTERLY_PRODUCT_ID`
 * - `DIARY_IAP_PRO_YEARLY_PRODUCT_ID`
 *
 * Entitlement rank: Author Pro > Author Plus > Free.
 * Vault / Mirror quotas live in UploadQuotaConfig / AIConfig.
 *
 * Override via process env or Gradle `local.properties` → BuildConfig later if needed.
 * Product IDs are public identifiers — never secrets.
 */
object BillingConfig {
    enum class Tier(val rawValue: String) {
        FREE("free"),
        AUTHOR_PLUS("authorPlus"),
        AUTHOR_PRO("authorPro");

        val displayName: String
            get() = when (this) {
                FREE -> "Free"
                AUTHOR_PLUS -> "Author Plus"
                AUTHOR_PRO -> "Author Pro"
            }

        val rank: Int
            get() = when (this) {
                FREE -> 0
                AUTHOR_PLUS -> 1
                AUTHOR_PRO -> 2
            }

        fun isAtLeast(other: Tier): Boolean = rank >= other.rank
    }

    enum class Period {
        MONTHLY,
        QUARTERLY,
        YEARLY;

        val rank: Int
            get() = when (this) {
                MONTHLY -> 0
                QUARTERLY -> 1
                YEARLY -> 2
            }
    }

    /** Snapshot keys for quota guards (SharedPreferences). */
    const val AUTHOR_PLUS_ENTITLEMENT_PREFS_KEY = "billing.authorPlusActive"
    const val AUTHOR_PRO_ENTITLEMENT_PREFS_KEY = "billing.authorProActive"
    const val TIER_PREFS_KEY = "billing.mirrorTier"
    const val ACTIVE_PRODUCT_PREFS_KEY = "billing.activeProductId"
    const val EXPIRATION_PREFS_KEY = "billing.expirationMs"
    const val BILLING_PREFS_NAME = "diary_vault_billing"

    const val DEFAULT_PLUS_MONTHLY_PRODUCT_ID = "com.diaryvault.author.plus.monthly"
    const val DEFAULT_PLUS_QUARTERLY_PRODUCT_ID = "com.diaryvault.author.plus.quarterly"
    const val DEFAULT_PLUS_YEARLY_PRODUCT_ID = "com.diaryvault.author.plus.yearly"

    const val DEFAULT_PRO_MONTHLY_PRODUCT_ID = "com.diaryvault.author.pro.monthly"
    const val DEFAULT_PRO_QUARTERLY_PRODUCT_ID = "com.diaryvault.author.pro.quarterly"
    const val DEFAULT_PRO_YEARLY_PRODUCT_ID = "com.diaryvault.author.pro.yearly"

    /**
     * Recommended Play Console **base plan** IDs (one auto-renewing base plan per subscription).
     * Client accepts any base-plan offerToken; these IDs keep Console setup unambiguous.
     */
    const val BASE_PLAN_MONTHLY = "monthly"
    const val BASE_PLAN_QUARTERLY = "quarterly"
    const val BASE_PLAN_YEARLY = "yearly"

    fun recommendedBasePlanId(period: Period): String = when (period) {
        Period.MONTHLY -> BASE_PLAN_MONTHLY
        Period.QUARTERLY -> BASE_PLAN_QUARTERLY
        Period.YEARLY -> BASE_PLAN_YEARLY
    }

    val plusMonthlyProductId: String
        get() = env("DIARY_IAP_PLUS_MONTHLY_PRODUCT_ID")
            ?: env("DIARY_IAP_MONTHLY_PRODUCT_ID")
            ?: DEFAULT_PLUS_MONTHLY_PRODUCT_ID

    val plusQuarterlyProductId: String
        get() = env("DIARY_IAP_PLUS_QUARTERLY_PRODUCT_ID")
            ?: DEFAULT_PLUS_QUARTERLY_PRODUCT_ID

    val plusYearlyProductId: String
        get() = env("DIARY_IAP_PLUS_YEARLY_PRODUCT_ID")
            ?: env("DIARY_IAP_YEARLY_PRODUCT_ID")
            ?: DEFAULT_PLUS_YEARLY_PRODUCT_ID

    val proMonthlyProductId: String
        get() = env("DIARY_IAP_PRO_MONTHLY_PRODUCT_ID") ?: DEFAULT_PRO_MONTHLY_PRODUCT_ID

    val proQuarterlyProductId: String
        get() = env("DIARY_IAP_PRO_QUARTERLY_PRODUCT_ID") ?: DEFAULT_PRO_QUARTERLY_PRODUCT_ID

    val proYearlyProductId: String
        get() = env("DIARY_IAP_PRO_YEARLY_PRODUCT_ID") ?: DEFAULT_PRO_YEARLY_PRODUCT_ID

    /** Legacy aliases. */
    val monthlyProductId: String get() = plusMonthlyProductId
    val yearlyProductId: String get() = plusYearlyProductId

    val plusProductIds: List<String>
        get() = listOf(plusMonthlyProductId, plusQuarterlyProductId, plusYearlyProductId)

    val proProductIds: List<String>
        get() = listOf(proMonthlyProductId, proQuarterlyProductId, proYearlyProductId)

    val productIds: List<String>
        get() = plusProductIds + proProductIds

    const val PLAN_NAME = "Author Plus"
    const val HIGHER_PLAN_NAME = "Author Pro"

    fun isPlusProduct(productId: String): Boolean = productId in plusProductIds

    fun isProProduct(productId: String): Boolean = productId in proProductIds

    fun isCatalogProduct(productId: String): Boolean = productId in productIds

    /** Prefer yearly > quarterly > monthly. */
    fun periodRank(productId: String): Int = when {
        productId.endsWith(".yearly") -> Period.YEARLY.rank
        productId.endsWith(".quarterly") -> Period.QUARTERLY.rank
        productId.endsWith(".monthly") -> Period.MONTHLY.rank
        else -> -1
    }

    fun period(productId: String): Period? = when (periodRank(productId)) {
        Period.YEARLY.rank -> Period.YEARLY
        Period.QUARTERLY.rank -> Period.QUARTERLY
        Period.MONTHLY.rank -> Period.MONTHLY
        else -> null
    }

    fun tier(productId: String): Tier? = when {
        isProProduct(productId) -> Tier.AUTHOR_PRO
        isPlusProduct(productId) -> Tier.AUTHOR_PLUS
        else -> null
    }

    private fun env(key: String): String? =
        System.getenv(key)?.trim()?.takeIf { it.isNotEmpty() }
}

/**
 * UI-facing catalog row from Play Billing [ProductDetails].
 * [offerToken] is required to launch a subscription purchase flow.
 */
data class BillingProductOffer(
    val productId: String,
    val title: String,
    val description: String,
    val formattedPrice: String,
    val offerToken: String,
    val basePlanId: String?,
)
