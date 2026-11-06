package com.diaryvault.app.ui.paywall

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.diaryvault.app.R
import com.diaryvault.app.VaultViewModel
import com.diaryvault.app.billing.BillingConfig
import com.diaryvault.app.ui.components.localizedString
import com.diaryvault.app.ui.theme.VaultColors
import kotlinx.coroutines.launch

private enum class CheckoutPeriod {
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
 * Compact upgrade sheet from Vault / Mirror hard-cap CTAs.
 * Prefer Plus when Free; Pro when already Plus. No restore (full paywall has restore).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpgradeCheckoutSheet(
    viewModel: VaultViewModel,
    preferredTier: BillingConfig.Tier = BillingConfig.Tier.AUTHOR_PLUS,
    onDismiss: () -> Unit,
) {
    val store = viewModel.subscriptionStore
    val products by store.products.collectAsState()
    val tier by store.tier.collectAsState()
    val activeProductId by store.activeProductId.collectAsState()
    val statusMessage by store.statusMessage.collectAsState()
    val isLoading by store.isLoading.collectAsState()

    var selectedTier by remember {
        mutableStateOf(
            if (preferredTier == BillingConfig.Tier.AUTHOR_PRO) {
                BillingConfig.Tier.AUTHOR_PRO
            } else {
                BillingConfig.Tier.AUTHOR_PLUS
            },
        )
    }
    var period by remember { mutableStateOf(CheckoutPeriod.MONTHLY) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = context.findActivity()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(Unit) {
        store.refresh()
    }

    LaunchedEffect(tier) {
        if (tier == BillingConfig.Tier.AUTHOR_PRO ||
            (tier == BillingConfig.Tier.AUTHOR_PLUS && selectedTier == BillingConfig.Tier.AUTHOR_PLUS)
        ) {
            onDismiss()
        }
    }

    val offerProducts = remember(products, selectedTier) {
        when (selectedTier) {
            BillingConfig.Tier.AUTHOR_PLUS -> products.filter { BillingConfig.isPlusProduct(it.productId) }
            BillingConfig.Tier.AUTHOR_PRO -> products.filter { BillingConfig.isProProduct(it.productId) }
            BillingConfig.Tier.FREE -> emptyList()
        }
    }
    val selectedProduct = offerProducts.firstOrNull { BillingConfig.periodRank(it.productId) == period.rank }
    val isActive = selectedProduct?.productId == activeProductId

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = VaultColors.paper,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    localizedString(R.string.upgrade_checkout_title),
                    fontWeight = FontWeight.SemiBold,
                    color = VaultColors.ink,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismiss) {
                    Text(localizedString(R.string.common_cancel), color = VaultColors.inkMuted)
                }
            }

            Text(
                localizedString(R.string.upgrade_checkout_headline),
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                color = VaultColors.ink,
            )
            Text(
                localizedString(R.string.upgrade_checkout_body),
                style = MaterialTheme.typography.bodyMedium,
                color = VaultColors.inkMuted,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                TierChip(
                    title = localizedString(R.string.paywall_plus_title),
                    selected = selectedTier == BillingConfig.Tier.AUTHOR_PLUS,
                    accent = false,
                    onClick = { selectedTier = BillingConfig.Tier.AUTHOR_PLUS },
                    modifier = Modifier.weight(1f),
                )
                TierChip(
                    title = localizedString(R.string.paywall_pro_title),
                    selected = selectedTier == BillingConfig.Tier.AUTHOR_PRO,
                    accent = true,
                    onClick = { selectedTier = BillingConfig.Tier.AUTHOR_PRO },
                    modifier = Modifier.weight(1f),
                )
            }

            Text(
                localizedString(
                    if (selectedTier == BillingConfig.Tier.AUTHOR_PRO) {
                        R.string.paywall_pro_summary
                    } else {
                        R.string.paywall_plus_summary
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = VaultColors.inkMuted,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                CheckoutPeriod.entries.forEach { option ->
                    val selected = period == option
                    val price = offerProducts
                        .firstOrNull { BillingConfig.periodRank(it.productId) == option.rank }
                        ?.formattedPrice
                        ?: "—"
                    Column(
                        Modifier
                            .weight(1f)
                            .background(
                                if (selected) VaultColors.ink else VaultColors.paperDeep.copy(alpha = 0.55f),
                                RoundedCornerShape(8.dp),
                            )
                            .clickable { period = option }
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            localizedString(option.labelRes),
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) VaultColors.paper else VaultColors.ink,
                        )
                        Text(
                            price,
                            fontFamily = FontFamily.Serif,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) VaultColors.paper.copy(alpha = 0.85f) else VaultColors.ink,
                        )
                    }
                }
            }

            val canBuy = selectedProduct != null && !isLoading && !isActive
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(
                        if (isActive) VaultColors.seal.copy(alpha = 0.16f) else VaultColors.seal,
                        RoundedCornerShape(10.dp),
                    )
                    .clickable(enabled = canBuy) {
                        val offer = selectedProduct ?: return@clickable
                        val act = activity ?: return@clickable
                        scope.launch { store.purchase(act, offer.productId) }
                    }
                    .padding(horizontal = 16.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = VaultColors.paper,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(18.dp),
                        strokeWidth = 2.dp,
                    )
                }
                Text(
                    localizedString(if (isActive) R.string.paywall_owned else R.string.paywall_buy),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = if (isActive) VaultColors.ink else VaultColors.paper,
                    modifier = Modifier.weight(1f),
                )
                selectedProduct?.let {
                    Text(
                        it.formattedPrice,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                        color = if (isActive) VaultColors.ink else VaultColors.paper,
                    )
                }
            }

            when {
                isLoading && products.isEmpty() -> {
                    CircularProgressIndicator(
                        color = VaultColors.seal,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
                selectedProduct == null && !isLoading -> {
                    Text(
                        localizedString(R.string.paywall_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = VaultColors.inkMuted,
                    )
                }
            }

            statusMessage?.let { key ->
                Text(
                    checkoutStatusText(key),
                    style = MaterialTheme.typography.bodySmall,
                    color = VaultColors.inkMuted,
                )
            }

            Text(
                localizedString(R.string.upgrade_checkout_legal),
                style = MaterialTheme.typography.labelSmall,
                color = VaultColors.inkMuted,
            )
        }
    }
}

@Composable
private fun TierChip(
    title: String,
    selected: Boolean,
    accent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        title,
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        color = if (selected) VaultColors.paper else VaultColors.ink,
        textAlign = TextAlign.Center,
        modifier = modifier
            .background(
                when {
                    selected && accent -> VaultColors.seal
                    selected -> VaultColors.seal
                    else -> VaultColors.paperDeep.copy(alpha = 0.55f)
                },
                RoundedCornerShape(8.dp),
            )
            .then(
                if (!selected) {
                    Modifier.border(1.dp, VaultColors.line, RoundedCornerShape(8.dp))
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
    )
}

@Composable
private fun checkoutStatusText(key: String): String {
    val res = when (key) {
        "products.empty" -> R.string.paywall_status_products_empty
        "products.error" -> R.string.paywall_status_products_error
        "purchase.success" -> R.string.paywall_status_purchase_success
        "purchase.cancelled" -> R.string.paywall_status_purchase_cancelled
        "purchase.pending" -> R.string.paywall_status_purchase_pending
        "purchase.unknown" -> R.string.paywall_status_purchase_unknown
        "purchase.error" -> R.string.paywall_status_purchase_error
        "debug.simulate" -> R.string.paywall_status_debug_simulate
        else -> null
    }
    return if (res != null) localizedString(res) else key
}
