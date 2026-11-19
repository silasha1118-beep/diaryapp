package com.silasha.diaryvault.billing

import android.app.Activity
import android.app.Application
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.silasha.diaryvault.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Google Play Billing purchase / restore / entitlement for Author Plus and Author Pro.
 *
 * Compiles and runs without Play Console products — empty catalog surfaces a clear
 * "products unavailable" status. Real purchases need the six subscription SKUs +
 * license testers (see android/README.md).
 *
 * Entitlement rank: Author Pro > Author Plus > Free.
 * Snapshots tier into SharedPreferences for quota guards.
 */
class SubscriptionStore(
    private val app: Application,
) : PurchasesUpdatedListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs = app.getSharedPreferences(BillingConfig.BILLING_PREFS_NAME, Context.MODE_PRIVATE)
    private val connectMutex = Mutex()

    private val billingClient: BillingClient = BillingClient.newBuilder(app)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build(),
        )
        // PBL 8+: reconnect automatically after Play Billing service drops.
        .enableAutoServiceReconnection()
        .build()

    private val _products = MutableStateFlow<List<BillingProductOffer>>(emptyList())
    val products: StateFlow<List<BillingProductOffer>> = _products.asStateFlow()

    private val _tier = MutableStateFlow(readCachedTier())
    val tier: StateFlow<BillingConfig.Tier> = _tier.asStateFlow()

    private val _isSubscribed = MutableStateFlow(_tier.value.isAtLeast(BillingConfig.Tier.AUTHOR_PLUS))
    val isSubscribed: StateFlow<Boolean> = _isSubscribed.asStateFlow()

    private val _isAuthorPro = MutableStateFlow(_tier.value == BillingConfig.Tier.AUTHOR_PRO)
    val isAuthorPro: StateFlow<Boolean> = _isAuthorPro.asStateFlow()

    private val _activeProductId = MutableStateFlow(prefs.getString(BillingConfig.ACTIVE_PRODUCT_PREFS_KEY, null))
    val activeProductId: StateFlow<String?> = _activeProductId.asStateFlow()

    private val _expirationMs = MutableStateFlow(
        prefs.getLong(BillingConfig.EXPIRATION_PREFS_KEY, 0L).takeIf { it > 0L },
    )
    val expirationMs: StateFlow<Long?> = _expirationMs.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _lastErrorDetail = MutableStateFlow<String?>(null)
    val lastErrorDetail: StateFlow<String?> = _lastErrorDetail.asStateFlow()

    private val _missingProductIds = MutableStateFlow<List<String>>(emptyList())
    val missingProductIds: StateFlow<List<String>> = _missingProductIds.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _billingReady = MutableStateFlow(false)
    val billingReady: StateFlow<Boolean> = _billingReady.asStateFlow()

    /** DEBUG-only local override so Redmi can exercise paywall → quota without Console SKUs. */
    private var debugOverrideTier: BillingConfig.Tier? = null

    private var productDetailsById: Map<String, ProductDetails> = emptyMap()

    /** Purchase token for the currently entitled catalog subscription (needed for Play upgrades). */
    private var activePurchaseToken: String? = null

    val plusProducts: List<BillingProductOffer>
        get() = _products.value.filter { BillingConfig.isPlusProduct(it.productId) }

    val proProducts: List<BillingProductOffer>
        get() = _products.value.filter { BillingConfig.isProProduct(it.productId) }

    init {
        scope.launch { refresh() }
    }

    fun product(forId: String): BillingProductOffer? =
        _products.value.firstOrNull { it.productId == forId }

    /**
     * Load catalog + refresh entitlement from Play.
     * Safe when Play Billing is unavailable (sideload / no Console products).
     */
    suspend fun refresh() {
        _isLoading.value = true
        _statusMessage.value = null
        _lastErrorDetail.value = null
        try {
            val connected = ensureConnected()
            if (!connected) {
                _products.value = emptyList()
                productDetailsById = emptyMap()
                _missingProductIds.value = BillingConfig.productIds
                _statusMessage.value = "products.error"
                _lastErrorDetail.value = emptyCatalogHint(
                    reason = "BillingClient not ready (Play Store missing, or service unavailable).",
                    requested = BillingConfig.productIds,
                )
                applyDebugOverrideOrKeepCache()
                return
            }

            val ids = BillingConfig.productIds
            val productList = ids.map { id ->
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(id)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            }
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(productList)
                .build()

            val result = withContext(Dispatchers.IO) {
                billingClient.queryProductDetails(params)
            }
            if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                _products.value = emptyList()
                productDetailsById = emptyMap()
                _missingProductIds.value = ids
                _statusMessage.value = "products.error"
                _lastErrorDetail.value =
                    "queryProductDetails code=${result.billingResult.responseCode} " +
                        result.billingResult.debugMessage.orEmpty()
                applyDebugOverrideOrKeepCache()
                return
            }

            val details = result.productDetailsList.orEmpty()
            productDetailsById = details.associateBy { it.productId }
            val offers = details.mapNotNull { toOffer(it) }.sortedWith(catalogComparator())
            _products.value = offers
            val returned = details.map { it.productId }.toSet()
            _missingProductIds.value = ids.filter { it !in returned }

            if (offers.isEmpty()) {
                _statusMessage.value = "products.empty"
                _lastErrorDetail.value = emptyCatalogHint(
                    reason = "Play returned 0 subscription products.",
                    requested = ids,
                )
            }

            refreshEntitlementFromPurchases()
        } catch (e: Exception) {
            Log.w(TAG, "refresh failed", e)
            _statusMessage.value = "products.error"
            _lastErrorDetail.value = e.message
            _missingProductIds.value = BillingConfig.productIds
            _products.value = emptyList()
            applyDebugOverrideOrKeepCache()
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun purchase(activity: Activity, productId: String) {
        if (!BillingConfig.isCatalogProduct(productId)) {
            _statusMessage.value = "purchase.error"
            _lastErrorDetail.value = "Product not in Author Plus / Author Pro catalog."
            return
        }

        _isLoading.value = true
        _statusMessage.value = null
        _lastErrorDetail.value = null
        try {
            if (!ensureConnected()) {
                _statusMessage.value = "purchase.error"
                _lastErrorDetail.value = "Play Billing is not available on this device."
                return
            }

            var details = productDetailsById[productId]
            if (details == null) {
                refresh()
                details = productDetailsById[productId]
            }
            val offer = _products.value.firstOrNull { it.productId == productId }
            if (details == null || offer == null) {
                _statusMessage.value = "purchase.error"
                _lastErrorDetail.value =
                    "Product unavailable. Create SKU `$productId` in Play Console, or wait for catalog sync."
                return
            }

            val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .setOfferToken(offer.offerToken)
                .build()
            val flowBuilder = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productDetailsParams))

            // Play requires a replacement token when changing plan while already subscribed
            // (Plus → Pro, or period change). Fresh Free → paid buys skip this.
            val oldToken = activePurchaseToken
            val currentProduct = _activeProductId.value
            if (!oldToken.isNullOrBlank() &&
                !currentProduct.isNullOrBlank() &&
                currentProduct != productId
            ) {
                val updateParams = BillingFlowParams.SubscriptionUpdateParams.newBuilder()
                    .setOldPurchaseToken(oldToken)
                    .setSubscriptionReplacementMode(
                        BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.CHARGE_FULL_PRICE,
                    )
                    .build()
                flowBuilder.setSubscriptionUpdateParams(updateParams)
            }

            val launchResult = billingClient.launchBillingFlow(activity, flowBuilder.build())
            if (launchResult.responseCode != BillingClient.BillingResponseCode.OK) {
                _statusMessage.value = "purchase.error"
                _lastErrorDetail.value =
                    "launchBillingFlow code=${launchResult.responseCode} ${launchResult.debugMessage}"
            }
            // Success / cancel land in onPurchasesUpdated.
        } catch (e: Exception) {
            _statusMessage.value = "purchase.error"
            _lastErrorDetail.value = e.message
        } finally {
            _isLoading.value = false
        }
    }

    /** Re-query active subscriptions for this Google account (restore). */
    suspend fun restore() {
        _isLoading.value = true
        _statusMessage.value = null
        _lastErrorDetail.value = null
        try {
            if (!ensureConnected()) {
                _statusMessage.value = "restore.error"
                _lastErrorDetail.value = "Play Billing is not available on this device."
                return
            }
            refreshEntitlementFromPurchases()
            _statusMessage.value = if (_isSubscribed.value) "restore.success" else "restore.none"
        } catch (e: Exception) {
            _statusMessage.value = "restore.error"
            _lastErrorDetail.value = e.message
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * DEBUG: simulate Plus / Pro / Free so quota + paywall UI can be tested without Console SKUs.
     * Cleared by [clearDebugOverride] or process death (also persisted lightly for Redmi smoke).
     */
    fun debugSimulateTier(tier: BillingConfig.Tier) {
        if (!BuildConfig.DEBUG) return
        debugOverrideTier = tier
        prefs.edit().putString(DEBUG_OVERRIDE_KEY, tier.rawValue).apply()
        applyEntitlement(
            productId = when (tier) {
                BillingConfig.Tier.AUTHOR_PRO -> BillingConfig.proMonthlyProductId
                BillingConfig.Tier.AUTHOR_PLUS -> BillingConfig.plusMonthlyProductId
                BillingConfig.Tier.FREE -> null
            },
            tier = tier,
            expirationMs = if (tier == BillingConfig.Tier.FREE) null else System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000,
        )
        _statusMessage.value = "debug.simulate"
        _lastErrorDetail.value = "DEBUG simulate ${tier.displayName}"
    }

    fun clearDebugOverride() {
        if (!BuildConfig.DEBUG) return
        debugOverrideTier = null
        prefs.edit().remove(DEBUG_OVERRIDE_KEY).apply()
        scope.launch { refresh() }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        scope.launch {
            when (billingResult.responseCode) {
                BillingClient.BillingResponseCode.OK -> {
                    val list = purchases.orEmpty()
                    for (purchase in list) {
                        handlePurchase(purchase)
                    }
                    refreshEntitlementFromPurchases()
                    _statusMessage.value = "purchase.success"
                }
                BillingClient.BillingResponseCode.USER_CANCELED -> {
                    _statusMessage.value = "purchase.cancelled"
                }
                BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                    refreshEntitlementFromPurchases()
                    _statusMessage.value = "purchase.success"
                }
                else -> {
                    _statusMessage.value = "purchase.error"
                    _lastErrorDetail.value =
                        "onPurchasesUpdated code=${billingResult.responseCode} ${billingResult.debugMessage}"
                }
            }
        }
    }

    private suspend fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        // PBL 8.1+: suspended (dispute/abuse hold) must not grant entitlement.
        if (purchase.isSuspended) return
        val catalogHit = purchase.products.any { BillingConfig.isCatalogProduct(it) }
        if (!catalogHit) return
        if (!purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            val result = withContext(Dispatchers.IO) {
                billingClient.acknowledgePurchase(params)
            }
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "acknowledge failed code=${result.responseCode} ${result.debugMessage}")
            }
        }
    }

    private suspend fun refreshEntitlementFromPurchases() {
        val debug = debugOverrideTier ?: readDebugOverride()
        if (BuildConfig.DEBUG && debug != null && debug != BillingConfig.Tier.FREE) {
            debugOverrideTier = debug
            applyEntitlement(
                productId = when (debug) {
                    BillingConfig.Tier.AUTHOR_PRO -> BillingConfig.proMonthlyProductId
                    BillingConfig.Tier.AUTHOR_PLUS -> BillingConfig.plusMonthlyProductId
                    BillingConfig.Tier.FREE -> null
                },
                tier = debug,
                expirationMs = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000,
            )
            return
        }

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        val result = withContext(Dispatchers.IO) {
            billingClient.queryPurchasesAsync(params)
        }
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.w(
                TAG,
                "queryPurchases failed code=${result.billingResult.responseCode} " +
                    result.billingResult.debugMessage,
            )
            applyDebugOverrideOrKeepCache()
            return
        }

        var bestProductId: String? = null
        var bestTier: BillingConfig.Tier = BillingConfig.Tier.FREE
        var bestPeriod = -1
        var bestToken: String? = null

        for (purchase in result.purchasesList) {
            if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) continue
            if (purchase.isSuspended) continue
            for (productId in purchase.products) {
                val candidate = BillingConfig.tier(productId) ?: continue
                val period = BillingConfig.periodRank(productId)
                val better = when {
                    candidate.rank > bestTier.rank -> true
                    candidate.rank == bestTier.rank && period > bestPeriod -> true
                    else -> false
                }
                if (better) {
                    bestTier = candidate
                    bestProductId = productId
                    bestPeriod = period
                    bestToken = purchase.purchaseToken
                }
            }
            // Acknowledge any unacked while scanning.
            handlePurchase(purchase)
        }

        activePurchaseToken = bestToken
        applyEntitlement(
            productId = bestProductId,
            tier = bestTier,
            // Play Billing client does not expose expiry on Purchase without RTDN / Play Developer API.
            expirationMs = null,
        )
    }

    private fun applyEntitlement(
        productId: String?,
        tier: BillingConfig.Tier,
        expirationMs: Long?,
    ) {
        _activeProductId.value = productId
        _tier.value = tier
        _isSubscribed.value = tier.isAtLeast(BillingConfig.Tier.AUTHOR_PLUS)
        _isAuthorPro.value = tier == BillingConfig.Tier.AUTHOR_PRO
        _expirationMs.value = expirationMs
        // Free / DEBUG simulate have no Play purchase token — clear so upgrades never send a stale one.
        if (tier == BillingConfig.Tier.FREE || debugOverrideTier != null) {
            activePurchaseToken = null
        }

        prefs.edit()
            .putBoolean(BillingConfig.AUTHOR_PLUS_ENTITLEMENT_PREFS_KEY, _isSubscribed.value)
            .putBoolean(BillingConfig.AUTHOR_PRO_ENTITLEMENT_PREFS_KEY, _isAuthorPro.value)
            .putString(BillingConfig.TIER_PREFS_KEY, tier.rawValue)
            .putString(BillingConfig.ACTIVE_PRODUCT_PREFS_KEY, productId)
            .apply {
                if (expirationMs != null) {
                    putLong(BillingConfig.EXPIRATION_PREFS_KEY, expirationMs)
                } else {
                    remove(BillingConfig.EXPIRATION_PREFS_KEY)
                }
            }
            .apply()
    }

    private fun applyDebugOverrideOrKeepCache() {
        val debug = debugOverrideTier ?: readDebugOverride()
        if (BuildConfig.DEBUG && debug != null) {
            debugOverrideTier = debug
            applyEntitlement(
                productId = when (debug) {
                    BillingConfig.Tier.AUTHOR_PRO -> BillingConfig.proMonthlyProductId
                    BillingConfig.Tier.AUTHOR_PLUS -> BillingConfig.plusMonthlyProductId
                    BillingConfig.Tier.FREE -> null
                },
                tier = debug,
                expirationMs = if (debug == BillingConfig.Tier.FREE) {
                    null
                } else {
                    System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000
                },
            )
        }
        // else keep cached prefs snapshot already loaded in init
    }

    private fun readCachedTier(): BillingConfig.Tier {
        if (BuildConfig.DEBUG) {
            readDebugOverride()?.let { return it }
        }
        if (prefs.getBoolean(BillingConfig.AUTHOR_PRO_ENTITLEMENT_PREFS_KEY, false)) {
            return BillingConfig.Tier.AUTHOR_PRO
        }
        if (prefs.getBoolean(BillingConfig.AUTHOR_PLUS_ENTITLEMENT_PREFS_KEY, false)) {
            return BillingConfig.Tier.AUTHOR_PLUS
        }
        val raw = prefs.getString(BillingConfig.TIER_PREFS_KEY, null)
        return BillingConfig.Tier.entries.firstOrNull { it.rawValue == raw } ?: BillingConfig.Tier.FREE
    }

    private fun readDebugOverride(): BillingConfig.Tier? {
        if (!BuildConfig.DEBUG) return null
        val raw = prefs.getString(DEBUG_OVERRIDE_KEY, null) ?: return null
        return BillingConfig.Tier.entries.firstOrNull { it.rawValue == raw }
    }

    private suspend fun ensureConnected(): Boolean = connectMutex.withLock {
        if (billingClient.isReady) {
            _billingReady.value = true
            return true
        }
        return suspendCancellableCoroutine { cont ->
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    val ok = billingResult.responseCode == BillingClient.BillingResponseCode.OK
                    _billingReady.value = ok
                    if (!ok) {
                        Log.w(
                            TAG,
                            "Billing setup failed code=${billingResult.responseCode} ${billingResult.debugMessage}",
                        )
                    }
                    if (cont.isActive) cont.resume(ok)
                }

                override fun onBillingServiceDisconnected() {
                    _billingReady.value = false
                }
            })
        }
    }

    private fun toOffer(details: ProductDetails): BillingProductOffer? {
        val offer = details.subscriptionOfferDetails
            ?.firstOrNull { it.offerId == null } // base plan (no promo offer id)
            ?: details.subscriptionOfferDetails?.firstOrNull()
            ?: return null
        val price = offer.pricingPhases.pricingPhaseList
            .lastOrNull()
            ?.formattedPrice
            ?: return null
        return BillingProductOffer(
            productId = details.productId,
            title = details.title,
            description = details.description,
            formattedPrice = price,
            offerToken = offer.offerToken,
            basePlanId = offer.basePlanId,
        )
    }

    private fun catalogComparator(): Comparator<BillingProductOffer> =
        Comparator { lhs, rhs ->
            val leftTier = BillingConfig.tier(lhs.productId) ?: BillingConfig.Tier.FREE
            val rightTier = BillingConfig.tier(rhs.productId) ?: BillingConfig.Tier.FREE
            if (leftTier != rightTier) return@Comparator leftTier.rank - rightTier.rank
            val lo = BillingConfig.periodRank(lhs.productId)
            val ro = BillingConfig.periodRank(rhs.productId)
            if (lo != ro) return@Comparator lo - ro
            lhs.productId.compareTo(rhs.productId)
        }

    private fun emptyCatalogHint(reason: String, requested: List<String>): String = buildString {
        append(reason)
        append(' ')
        append("Create 6 subscriptions in Play Console (same IDs as iOS), add a license tester, ")
        append("install from an internal testing track (or sideload with the same package + signing). ")
        append("Requested: ")
        append(requested.joinToString(", "))
        if (BuildConfig.DEBUG) {
            append(" DEBUG: use Paywall → Simulate Plus/Pro to test quotas without Console.")
        }
    }

    companion object {
        private const val TAG = "DiaryBilling"
        private const val DEBUG_OVERRIDE_KEY = "billing.debugOverrideTier"
    }
}
