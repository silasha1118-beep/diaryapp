package com.diaryvault.app.billing

/**
 * Product ID placeholders aligned with iOS [BillingConfig].
 * Play Billing Library is **not** wired in this PR (costly vs iOS-first StoreKit stub).
 *
 * Override via Gradle/env later if needed — no secrets belong in source.
 */
object BillingConfig {
    const val DEFAULT_MONTHLY_PRODUCT_ID = "com.diaryvault.author.plus.monthly"
    const val DEFAULT_YEARLY_PRODUCT_ID = "com.diaryvault.author.plus.yearly"

    val monthlyProductId: String
        get() = System.getenv("DIARY_IAP_MONTHLY_PRODUCT_ID")
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_MONTHLY_PRODUCT_ID

    val yearlyProductId: String
        get() = System.getenv("DIARY_IAP_YEARLY_PRODUCT_ID")
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_YEARLY_PRODUCT_ID

    val productIds: List<String>
        get() = listOf(monthlyProductId, yearlyProductId)

    const val PLAN_NAME = "Author Plus"
}

/**
 * Play Billing hook stub — no BillingClient calls yet.
 */
class BillingStub {
    var isSubscribed: Boolean = false
        private set

    var activeProductId: String? = null
        private set

    /** Placeholder for a future BillingClient.queryProductDetails / purchase flow. */
    fun refreshEntitlementStub() {
        // No-op until Play Billing is implemented.
    }

    fun markSubscribedForDev(productId: String?) {
        activeProductId = productId
        isSubscribed = !productId.isNullOrBlank()
    }

    fun clear() {
        activeProductId = null
        isSubscribed = false
    }
}
