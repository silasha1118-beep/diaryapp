import SwiftUI
import StoreKit

/// Compact upgrade sheet from vault / Mirror hard-cap CTAs:
/// pick **Plus or Pro** → pick **月／季／年** → 訂閱 → Apple purchase sheet.
/// Includes Guideline 3.1.2 title / length / price / benefits / legal / restore.
struct UpgradeCheckoutSheet: View {
    @EnvironmentObject private var localization: LocalizationManager
    @EnvironmentObject private var subscriptions: SubscriptionStore
    @Environment(\.dismiss) private var dismiss

    /// Prefer Plus when Free; Pro when already Plus.
    var preferredTier: BillingConfig.Tier = .authorPlus

    @State private var selectedTier: BillingConfig.Tier = .authorPlus
    @State private var period: BillingPeriod = .monthly
    @State private var appeared = false

    private enum BillingPeriod: String, CaseIterable, Identifiable {
        case monthly
        case quarterly
        case yearly

        var id: String { rawValue }

        var localizationKey: String {
            switch self {
            case .monthly: return "paywall.period.monthly"
            case .quarterly: return "paywall.period.quarterly"
            case .yearly: return "paywall.period.yearly"
            }
        }

        var lengthKey: String {
            switch self {
            case .monthly: return "paywall.length.monthly"
            case .quarterly: return "paywall.length.quarterly"
            case .yearly: return "paywall.length.yearly"
            }
        }

        var rank: Int {
            switch self {
            case .monthly: return 0
            case .quarterly: return 1
            case .yearly: return 2
            }
        }
    }

    private var products: [Product] {
        switch selectedTier {
        case .authorPlus: return subscriptions.plusProducts
        case .authorPro: return subscriptions.proProducts
        case .free: return []
        }
    }

    private var selectedProduct: Product? {
        products.first { BillingConfig.periodRank(for: $0.id) == period.rank }
    }

    private var tierTitle: String {
        selectedTier == .authorPro
            ? localization.text("paywall.pro.title")
            : localization.text("paywall.plus.title")
    }

    var body: some View {
        NavigationStack {
            ZStack {
                VaultBackground()

                ScrollView {
                    VStack(alignment: .leading, spacing: 18) {
                        Text(localization.text("upgrade.checkout.headline"))
                            .font(.system(size: 24, weight: .semibold, design: .serif))
                            .foregroundStyle(VaultTheme.ink)
                            .opacity(appeared ? 1 : 0)

                        Text(localization.text("upgrade.checkout.body"))
                            .font(.system(size: 14))
                            .foregroundStyle(VaultTheme.inkMuted)
                            .fixedSize(horizontal: false, vertical: true)
                            .opacity(appeared ? 1 : 0)

                        tierPicker
                            .opacity(appeared ? 1 : 0)

                        Text(selectedTier == .authorPro
                             ? localization.text("paywall.pro.summary")
                             : localization.text("paywall.plus.summary"))
                            .font(.system(size: 13))
                            .foregroundStyle(VaultTheme.inkMuted)
                            .fixedSize(horizontal: false, vertical: true)

                        SubscriptionBenefitsList(tier: selectedTier)

                        periodPicker
                            .opacity(appeared ? 1 : 0)

                        if let product = selectedProduct {
                            offerSummary(for: product)
                        }

                        subscribeButton
                            .opacity(appeared ? 1 : 0)

                        if subscriptions.isLoading && subscriptions.products.isEmpty {
                            ProgressView()
                                .frame(maxWidth: .infinity)
                        } else if selectedProduct == nil && !subscriptions.isLoading {
                            Text(localization.text("paywall.unavailable"))
                                .font(.system(size: 13))
                                .foregroundStyle(VaultTheme.inkMuted)
                        }

                        if let key = subscriptions.statusMessage {
                            Text(statusText(for: key))
                                .font(.system(size: 13))
                                .foregroundStyle(VaultTheme.inkMuted)
                        }

                        SubscriptionDisclosureFooter()
                            .padding(.top, 4)
                    }
                    .padding(24)
                }
            }
            .navigationTitle(localization.text("upgrade.checkout.title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(localization.text("common.cancel")) { dismiss() }
                }
            }
            .task {
                selectedTier = preferredTier == .authorPro ? .authorPro : .authorPlus
                await subscriptions.refresh()
            }
            .onAppear {
                withAnimation(.easeOut(duration: 0.4)) {
                    appeared = true
                }
            }
            .onChange(of: subscriptions.tier) { _, newTier in
                if newTier == .authorPro || (newTier == .authorPlus && selectedTier == .authorPlus) {
                    dismiss()
                }
            }
        }
    }

    private var tierPicker: some View {
        HStack(spacing: 8) {
            tierChip(
                title: localization.text("paywall.plus.title"),
                tier: .authorPlus,
                selected: selectedTier == .authorPlus
            )
            tierChip(
                title: localization.text("paywall.pro.title"),
                tier: .authorPro,
                selected: selectedTier == .authorPro
            )
        }
    }

    private func tierChip(title: String, tier: BillingConfig.Tier, selected: Bool) -> some View {
        Button {
            selectedTier = tier
        } label: {
            Text(title)
                .font(.system(size: 15, weight: .semibold, design: .serif))
                .foregroundStyle(selected ? VaultTheme.paper : VaultTheme.ink)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .background(selected ? VaultTheme.seal : VaultTheme.paperDeep.opacity(0.55))
                .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                        .stroke(selected ? Color.clear : VaultTheme.line, lineWidth: 1)
                )
        }
        .buttonStyle(.plain)
    }

    private var periodPicker: some View {
        HStack(spacing: 8) {
            ForEach(BillingPeriod.allCases) { option in
                let selected = period == option
                let price = products
                    .first { BillingConfig.periodRank(for: $0.id) == option.rank }?
                    .displayPrice
                Button {
                    period = option
                } label: {
                    VStack(spacing: 4) {
                        Text(localization.text(option.localizationKey))
                            .font(.system(size: 13, weight: .semibold))
                        Text(price ?? "—")
                            .font(.system(size: 12, design: .serif))
                            .opacity(0.85)
                    }
                    .foregroundStyle(selected ? VaultTheme.paper : VaultTheme.ink)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(selected ? VaultTheme.ink : VaultTheme.paperDeep.opacity(0.55))
                    .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
                }
                .buttonStyle(.plain)
            }
        }
    }

    /// Explicit title + length + price (Guideline 3.1.2).
    private func offerSummary(for product: Product) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(displayName(for: product))
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(VaultTheme.ink)
            Text(
                localization.text(period.lengthKey)
                    + " · "
                    + product.displayPrice
            )
            .font(.system(size: 14))
            .foregroundStyle(VaultTheme.inkMuted)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }

    private var subscribeButton: some View {
        let product = selectedProduct
        let isActive = product.map { subscriptions.activeProductID == $0.id } ?? false
        return Button {
            guard let product else { return }
            Task { await subscriptions.purchase(product) }
        } label: {
            HStack {
                if subscriptions.isLoading {
                    ProgressView()
                        .tint(VaultTheme.paper)
                }
                Text(isActive ? localization.text("paywall.owned") : localization.text("paywall.buy"))
                    .font(.system(size: 16, weight: .semibold))
                Spacer(minLength: 8)
                if let product {
                    Text(product.displayPrice)
                        .font(.system(size: 17, weight: .semibold, design: .serif))
                }
            }
            .foregroundStyle(isActive ? VaultTheme.ink : VaultTheme.paper)
            .padding(.horizontal, 16)
            .padding(.vertical, 15)
            .background(isActive ? VaultTheme.seal.opacity(0.16) : VaultTheme.seal)
            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
        }
        .buttonStyle(.plain)
        .disabled(subscriptions.isLoading || product == nil || isActive)
    }

    private func displayName(for product: Product) -> String {
        if !product.displayName.isEmpty {
            return product.displayName
        }
        switch product.id {
        case BillingConfig.plusMonthlyProductID:
            return localization.text("paywall.plus.monthly")
        case BillingConfig.plusQuarterlyProductID:
            return localization.text("paywall.plus.quarterly")
        case BillingConfig.plusYearlyProductID:
            return localization.text("paywall.plus.yearly")
        case BillingConfig.proMonthlyProductID:
            return localization.text("paywall.pro.monthly")
        case BillingConfig.proQuarterlyProductID:
            return localization.text("paywall.pro.quarterly")
        case BillingConfig.proYearlyProductID:
            return localization.text("paywall.pro.yearly")
        default:
            return tierTitle
        }
    }

    private func statusText(for key: String) -> String {
        let map = [
            "products.empty": "paywall.status.productsEmpty",
            "products.error": "paywall.status.productsError",
            "purchase.success": "paywall.status.purchaseSuccess",
            "purchase.cancelled": "paywall.status.purchaseCancelled",
            "purchase.pending": "paywall.status.purchasePending",
            "purchase.unknown": "paywall.status.purchaseUnknown",
            "purchase.error": "paywall.status.purchaseError",
            "restore.success": "paywall.status.restoreSuccess",
            "restore.none": "paywall.status.restoreNone",
            "restore.error": "paywall.status.restoreError",
        ]
        return localization.text(map[key] ?? key)
    }
}
