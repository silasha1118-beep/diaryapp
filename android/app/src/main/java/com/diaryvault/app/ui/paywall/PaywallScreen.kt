package com.diaryvault.app.ui.paywall

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.diaryvault.app.BuildConfig
import com.diaryvault.app.R
import com.diaryvault.app.VaultViewModel
import com.diaryvault.app.billing.BillingConfig
import com.diaryvault.app.billing.BillingProductOffer
import com.diaryvault.app.ui.components.localizedString
import com.diaryvault.app.ui.theme.VaultColors
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private enum class BillingPeriod {
    MONTHLY,
    QUARTERLY,
    YEARLY;

    val rank: Int
        get() = when (this) {
            MONTHLY -> 0
            QUARTERLY -> 1
            YEARLY -> 2
        }

    val labelRes: Int
        get() = when (this) {
            MONTHLY -> R.string.paywall_period_monthly
            QUARTERLY -> R.string.paywall_period_quarterly
            YEARLY -> R.string.paywall_period_yearly
        }
}

/**
 * Full plans page (Settings → View plans / restore).
 * Mirrors iOS PaywallView: Plus + Pro cards, period chips, restore.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(
    viewModel: VaultViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val store = viewModel.subscriptionStore
    val products by store.products.collectAsState()
    val tier by store.tier.collectAsState()
    val isSubscribed by store.isSubscribed.collectAsState()
    val isAuthorPro by store.isAuthorPro.collectAsState()
    val activeProductId by store.activeProductId.collectAsState()
    val expirationMs by store.expirationMs.collectAsState()
    val statusMessage by store.statusMessage.collectAsState()
    val lastErrorDetail by store.lastErrorDetail.collectAsState()
    val missingProductIds by store.missingProductIds.collectAsState()
    val isLoading by store.isLoading.collectAsState()

    var plusPeriod by remember { mutableStateOf(BillingPeriod.MONTHLY) }
    var proPeriod by remember { mutableStateOf(BillingPeriod.MONTHLY) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = context.findActivity()

    LaunchedEffect(Unit) {
        store.refresh()
    }

    val plusProducts = remember(products) { products.filter { BillingConfig.isPlusProduct(it.productId) } }
    val proProducts = remember(products) { products.filter { BillingConfig.isProProduct(it.productId) } }

    Scaffold(
        modifier = modifier,
        containerColor = VaultColors.paper,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        localizedString(R.string.paywall_title),
                        color = VaultColors.ink,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onClose) {
                        Text(localizedString(R.string.common_cancel), color = VaultColors.inkMuted)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultColors.paper),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    localizedString(R.string.paywall_headline),
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 26.sp,
                    color = VaultColors.ink,
                )
                Text(
                    localizedString(R.string.paywall_body_short),
                    style = MaterialTheme.typography.bodyMedium,
                    color = VaultColors.inkMuted,
                )
            }

            if (isSubscribed) {
                StatusBanner(
                    title = localizedString(
                        if (isAuthorPro) R.string.paywall_active_pro else R.string.paywall_active,
                    ),
                    detail = activeDetail(expirationMs),
                )
            }

            PaidTierCard(
                title = localizedString(R.string.paywall_plus_title),
                summary = localizedString(R.string.paywall_plus_summary),
                products = plusProducts,
                period = plusPeriod,
                onPeriodChange = { plusPeriod = it },
                accentPro = false,
                isCurrent = tier == BillingConfig.Tier.AUTHOR_PLUS,
                activeProductId = activeProductId,
                isLoading = isLoading,
                onPurchase = { offer ->
                    val act = activity ?: return@PaidTierCard
                    scope.launch { store.purchase(act, offer.productId) }
                },
            )

            PaidTierCard(
                title = localizedString(R.string.paywall_pro_title),
                summary = localizedString(R.string.paywall_pro_summary),
                products = proProducts,
                period = proPeriod,
                onPeriodChange = { proPeriod = it },
                accentPro = true,
                isCurrent = tier == BillingConfig.Tier.AUTHOR_PRO,
                activeProductId = activeProductId,
                isLoading = isLoading,
                onPurchase = { offer ->
                    val act = activity ?: return@PaidTierCard
                    scope.launch { store.purchase(act, offer.productId) }
                },
            )

            if (isLoading && products.isEmpty()) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = VaultColors.seal,
                )
            }

            if (products.isEmpty() && !isLoading) {
                Text(
                    localizedString(R.string.paywall_products_missing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = VaultColors.inkMuted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(VaultColors.paperDeep.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                        .padding(14.dp),
                )
            }

            Text(
                localizedString(R.string.paywall_restore),
                style = MaterialTheme.typography.labelLarge,
                color = VaultColors.inkMuted,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isLoading) {
                        scope.launch { store.restore() }
                    }
                    .padding(vertical = 8.dp),
                textAlign = TextAlign.Center,
            )

            statusMessage?.let { key ->
                Text(
                    statusText(key),
                    style = MaterialTheme.typography.bodySmall,
                    color = VaultColors.inkMuted,
                )
            }

            if (BuildConfig.DEBUG) {
                DebugBillingBlock(
                    lastErrorDetail = lastErrorDetail,
                    missingProductIds = missingProductIds,
                    onSimulatePlus = { store.debugSimulateTier(BillingConfig.Tier.AUTHOR_PLUS) },
                    onSimulatePro = { store.debugSimulateTier(BillingConfig.Tier.AUTHOR_PRO) },
                    onClear = { store.clearDebugOverride() },
                )
            }

            Text(
                localizedString(R.string.paywall_disclosure_short),
                style = MaterialTheme.typography.labelSmall,
                color = VaultColors.inkMuted,
            )
        }
    }
}

@Composable
private fun PaidTierCard(
    title: String,
    summary: String,
    products: List<BillingProductOffer>,
    period: BillingPeriod,
    onPeriodChange: (BillingPeriod) -> Unit,
    accentPro: Boolean,
    isCurrent: Boolean,
    activeProductId: String?,
    isLoading: Boolean,
    onPurchase: (BillingProductOffer) -> Unit,
) {
    val selected = products.firstOrNull { BillingConfig.periodRank(it.productId) == period.rank }
    val isActiveProduct = selected?.productId == activeProductId

    Column(
        Modifier
            .fillMaxWidth()
            .border(
                width = if (accentPro) 1.5.dp else 1.dp,
                color = if (accentPro) VaultColors.seal.copy(alpha = 0.45f) else VaultColors.line,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                color = VaultColors.ink,
                modifier = Modifier.weight(1f),
            )
            if (isCurrent) {
                Text(
                    localizedString(R.string.paywall_current_plan),
                    style = MaterialTheme.typography.labelMedium,
                    color = VaultColors.seal,
                )
            }
        }
        Text(summary, style = MaterialTheme.typography.bodyMedium, color = VaultColors.inkMuted)
        PeriodPicker(period = period, onPeriodChange = onPeriodChange)
        when {
            selected != null -> {
                PurchaseButton(
                    offer = selected,
                    isActive = isActiveProduct,
                    enabled = !isLoading && !isActiveProduct,
                    onClick = { onPurchase(selected) },
                )
            }
            isLoading -> {
                CircularProgressIndicator(color = VaultColors.seal, modifier = Modifier.size(28.dp))
            }
            else -> {
                Text(
                    localizedString(R.string.paywall_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = VaultColors.inkMuted,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun PeriodPicker(
    period: BillingPeriod,
    onPeriodChange: (BillingPeriod) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        BillingPeriod.entries.forEach { option ->
            val selected = period == option
                Text(
                localizedString(option.labelRes),
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) VaultColors.paper else VaultColors.ink,
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (selected) VaultColors.ink else VaultColors.paperDeep.copy(alpha = 0.55f),
                        RoundedCornerShape(5.dp),
                    )
                    .clickable { onPeriodChange(option) }
                    .padding(vertical = 9.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PurchaseButton(
    offer: BillingProductOffer,
    isActive: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                if (isActive) VaultColors.seal.copy(alpha = 0.16f) else VaultColors.ink,
                RoundedCornerShape(6.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            localizedString(if (isActive) R.string.paywall_owned else R.string.paywall_buy),
            fontWeight = FontWeight.SemiBold,
            color = if (isActive) VaultColors.ink else VaultColors.paper,
            modifier = Modifier.weight(1f),
        )
        Text(
            offer.formattedPrice,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            color = if (isActive) VaultColors.ink else VaultColors.paper,
        )
    }
}

@Composable
private fun StatusBanner(title: String, detail: String?) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(VaultColors.seal.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, fontWeight = FontWeight.SemiBold, color = VaultColors.ink)
        if (!detail.isNullOrBlank()) {
            Text(detail, style = MaterialTheme.typography.labelMedium, color = VaultColors.inkMuted)
        }
    }
}

@Composable
private fun DebugBillingBlock(
    lastErrorDetail: String?,
    missingProductIds: List<String>,
    onSimulatePlus: () -> Unit,
    onSimulatePro: () -> Unit,
    onClear: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, VaultColors.line.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "DEBUG Play Billing",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = VaultColors.inkMuted,
        )
        if (!lastErrorDetail.isNullOrBlank()) {
            Text(
                lastErrorDetail,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = VaultColors.inkMuted,
            )
        }
        if (missingProductIds.isNotEmpty()) {
            Text(
                missingProductIds.joinToString("\n"),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = VaultColors.inkMuted,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                localizedString(R.string.paywall_debug_simulate_plus),
                color = VaultColors.seal,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clickable(onClick = onSimulatePlus),
            )
            Text(
                localizedString(R.string.paywall_debug_simulate_pro),
                color = VaultColors.seal,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clickable(onClick = onSimulatePro),
            )
            Text(
                localizedString(R.string.paywall_debug_clear),
                color = VaultColors.inkMuted,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clickable(onClick = onClear),
            )
        }
    }
}

@Composable
private fun activeDetail(expirationMs: Long?): String? {
    if (expirationMs == null || expirationMs <= 0L) return null
    val formatted = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(expirationMs))
    return localizedString(R.string.paywall_expires) + " " + formatted
}

@Composable
private fun statusText(key: String): String {
    val res = when (key) {
        "products.empty" -> R.string.paywall_status_products_empty
        "products.error" -> R.string.paywall_status_products_error
        "purchase.success" -> R.string.paywall_status_purchase_success
        "purchase.cancelled" -> R.string.paywall_status_purchase_cancelled
        "purchase.pending" -> R.string.paywall_status_purchase_pending
        "purchase.unknown" -> R.string.paywall_status_purchase_unknown
        "purchase.error" -> R.string.paywall_status_purchase_error
        "restore.success" -> R.string.paywall_status_restore_success
        "restore.none" -> R.string.paywall_status_restore_none
        "restore.error" -> R.string.paywall_status_restore_error
        "debug.simulate" -> R.string.paywall_status_debug_simulate
        else -> null
    }
    return if (res != null) localizedString(res) else key
}

internal fun android.content.Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
