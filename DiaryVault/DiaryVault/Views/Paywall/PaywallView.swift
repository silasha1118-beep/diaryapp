import SwiftUI
import StoreKit

struct PaywallView: View {
    @EnvironmentObject private var localization: LocalizationManager
    @EnvironmentObject private var subscriptions: SubscriptionStore
    @Environment(\.dismiss) private var dismiss

    @State private var plusPeriod: BillingPeriod = .monthly
    @State private var proPeriod: BillingPeriod = .monthly
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
    }

    var body: some View {
        NavigationStack {
            ZStack {
                VaultBackground()

                ScrollView {
                    VStack(alignment: .leading, spacing: 22) {
                        header

                        if subscriptions.isSubscribed {
                            statusBanner(
                                localization.text(
                                    subscriptions.isAuthorPro ? "paywall.active.pro" : "paywall.active"
                                ),
                                detail: activeDetail
                            )
                        }

                        freeCard
                            .opacity(appeared ? 1 : 0)
                            .offset(y: appeared ? 0 : 10)

                        paidTierCard(
                            title: localization.text("paywall.plus.title"),
                            benefits: [
                                localization.text("paywall.plus.benefit1"),
                                localization.text("paywall.plus.benefit2"),
                                localization.text("paywall.plus.benefit3"),
                            ],
                            products: subscriptions.plusProducts,
                            period: $plusPeriod,
                            accentPro: false,
                            isCurrent: subscriptions.tier == .authorPlus
                        )
                        .opacity(appeared ? 1 : 0)
                        .offset(y: appeared ? 0 : 14)

                        paidTierCard(
                            title: localization.text("paywall.pro.title"),
                            benefits: [
                                localization.text("paywall.pro.benefit1"),
                                localization.text("paywall.pro.benefit2"),
                                localization.text("paywall.pro.benefit3"),
                            ],
                            products: subscriptions.proProducts,
                            period: $proPeriod,
                            accentPro: true,
                            isCurrent: subscriptions.tier == .authorPro
                        )
                        .opacity(appeared ? 1 : 0)
                        .offset(y: appeared ? 0 : 18)

                        if subscriptions.isLoading && subscriptions.products.isEmpty {
                            ProgressView()
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 8)
                        }

                        if subscriptions.products.isEmpty && !subscriptions.isLoading {
                            missingProductsBanner
                        }

                        actionsRow

                        if let key = subscriptions.statusMessage {
                            Text(statusText(for: key))
                                .font(.system(size: 13))
                                .foregroundStyle(VaultTheme.inkMuted)
                        }

                        #if DEBUG
                        debugDiagnostics
                        #endif

                        Text(localization.text("paywall.disclosure"))
                            .font(.system(size: 12))
                            .foregroundStyle(VaultTheme.inkMuted)
                            .fixedSize(horizontal: false, vertical: true)
                            .padding(.top, 4)

                        legalLinks

                        Text(localization.text("paywall.footer"))
                            .font(.system(size: 12))
                            .foregroundStyle(VaultTheme.inkMuted)
                            .padding(.top, 4)
                    }
                    .padding(24)
                }
            }
            .navigationTitle(localization.text("paywall.title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(localization.text("common.cancel")) { dismiss() }
                }
            }
            .task {
                await subscriptions.refresh()
            }
            .onAppear {
                withAnimation(.easeOut(duration: 0.55)) {
                    appeared = true
                }
            }
        }
    }

    // MARK: - Sections

    private var header: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(localization.text("paywall.headline"))
                .font(.system(size: 28, weight: .semibold, design: .serif))
                .foregroundStyle(VaultTheme.ink)
            Text(localization.text("paywall.body"))
                .font(.system(size: 16))
                .foregroundStyle(VaultTheme.inkMuted)
                .lineSpacing(4)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private var freeCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .firstTextBaseline) {
                Text(localization.text("paywall.free.title"))
                    .font(.system(size: 20, weight: .semibold, design: .serif))
                    .foregroundStyle(VaultTheme.ink)
                Spacer(minLength: 8)
                Text(localization.text("paywall.free.price"))
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(VaultTheme.inkMuted)
            }

            benefitList([
                localization.text("paywall.free.benefit1"),
                localization.text("paywall.free.benefit2"),
                localization.text("paywall.free.benefit3"),
            ])

            if subscriptions.tier == .free {
                Text(localization.text("paywall.free.current"))
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(VaultTheme.seal)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(18)
        .overlay(
            RoundedRectangle(cornerRadius: 6, style: .continuous)
                .stroke(VaultTheme.line, lineWidth: 1)
        )
    }

    private func paidTierCard(
        title: String,
        benefits: [String],
        products: [Product],
        period: Binding<BillingPeriod>,
        accentPro: Bool,
        isCurrent: Bool
    ) -> some View {
        let selected = product(in: products, period: period.wrappedValue)
        let isActiveProduct = selected.map { subscriptions.activeProductID == $0.id } ?? false

        return VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .firstTextBaseline) {
                Text(title)
                    .font(.system(size: 20, weight: .semibold, design: .serif))
                    .foregroundStyle(VaultTheme.ink)
                Spacer(minLength: 8)
                if isCurrent {
                    Text(localization.text("paywall.currentPlan"))
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(VaultTheme.seal)
                }
            }

            benefitList(benefits)

            periodPicker(period)

            if let selected {
                purchaseButton(for: selected, isActive: isActiveProduct, accentPro: accentPro)
            } else if subscriptions.isLoading {
                ProgressView()
                    .frame(maxWidth: .infinity, alignment: .leading)
            } else {
                unavailablePurchasePlaceholder(period: period.wrappedValue)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(18)
        .overlay(
            RoundedRectangle(cornerRadius: 6, style: .continuous)
                .stroke(
                    accentPro ? VaultTheme.seal.opacity(0.45) : VaultTheme.line,
                    lineWidth: accentPro ? 1.5 : 1
                )
        )
    }

    private func benefitList(_ items: [String]) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            ForEach(items, id: \.self) { item in
                HStack(alignment: .top, spacing: 8) {
                    Text("·")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(VaultTheme.seal)
                    Text(item)
                        .font(.system(size: 14))
                        .foregroundStyle(VaultTheme.inkMuted)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }

    private func periodPicker(_ period: Binding<BillingPeriod>) -> some View {
        HStack(spacing: 8) {
            ForEach(BillingPeriod.allCases) { option in
                let selected = period.wrappedValue == option
                Button {
                    period.wrappedValue = option
                } label: {
                    Text(localization.text(option.localizationKey))
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(selected ? VaultTheme.paper : VaultTheme.ink)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 9)
                        .background(selected ? VaultTheme.ink : VaultTheme.paperDeep.opacity(0.55))
                        .clipShape(RoundedRectangle(cornerRadius: 5, style: .continuous))
                }
                .buttonStyle(.plain)
            }
        }
    }

    private func purchaseButton(for product: Product, isActive: Bool, accentPro: Bool) -> some View {
        Button {
            Task { await subscriptions.purchase(product) }
        } label: {
            HStack {
                Text(isActive ? localization.text("paywall.owned") : localization.text("paywall.buy"))
                    .font(.system(size: 15, weight: .semibold))
                Spacer(minLength: 8)
                Text(product.displayPrice)
                    .font(.system(size: 16, weight: .semibold, design: .serif))
            }
            .foregroundStyle(isActive ? VaultTheme.ink : VaultTheme.paper)
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .background(isActive ? VaultTheme.seal.opacity(0.16) : VaultTheme.ink)
            .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 6, style: .continuous)
                    .stroke(accentPro && !isActive ? VaultTheme.seal.opacity(0.35) : .clear, lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
        .disabled(subscriptions.isLoading || isActive)
        .accessibilityLabel(displayName(for: product))
    }

    private func unavailablePurchasePlaceholder(period: BillingPeriod) -> some View {
        HStack {
            Text(localization.text("paywall.unavailable"))
                .font(.system(size: 14))
                .foregroundStyle(VaultTheme.inkMuted)
            Spacer()
            Text(localization.text(period.localizationKey))
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(VaultTheme.inkMuted)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .overlay(
            RoundedRectangle(cornerRadius: 6, style: .continuous)
                .stroke(VaultTheme.line, lineWidth: 1)
        )
    }

    private var missingProductsBanner: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(localization.text("paywall.productsMissing"))
                .font(.system(size: 13))
                .foregroundStyle(VaultTheme.inkMuted)
                .fixedSize(horizontal: false, vertical: true)
            #if DEBUG
            Text(localization.text("paywall.productsMissing.debug"))
                .font(.system(size: 12))
                .foregroundStyle(VaultTheme.inkMuted)
                .fixedSize(horizontal: false, vertical: true)
            #endif
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(VaultTheme.paperDeep.opacity(0.55))
        .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
    }

    private var actionsRow: some View {
        VStack(spacing: 10) {
            Button {
                Task { await subscriptions.restore() }
            } label: {
                Text(localization.text("paywall.restore"))
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(VaultTheme.ink)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .overlay(
                        RoundedRectangle(cornerRadius: 6, style: .continuous)
                            .stroke(VaultTheme.line, lineWidth: 1)
                    )
            }
            .buttonStyle(.plain)
            .disabled(subscriptions.isLoading)

            Button {
                Task { await subscriptions.refresh() }
            } label: {
                Text(localization.text("paywall.reload"))
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(VaultTheme.seal)
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.plain)
            .disabled(subscriptions.isLoading)
        }
    }

    private var legalLinks: some View {
        HStack(spacing: 16) {
            if let privacy = LegalConfig.privacyPolicyURL {
                Link(localization.text("settings.legal.privacy"), destination: privacy)
                    .font(.system(size: 13, weight: .medium))
            } else {
                Text(localization.text("paywall.privacy.pending"))
                    .font(.system(size: 12))
                    .foregroundStyle(VaultTheme.inkMuted)
            }
            Link(
                localization.text("settings.legal.terms"),
                destination: LegalConfig.effectiveTermsURL
            )
            .font(.system(size: 13, weight: .medium))
        }
    }

    #if DEBUG
    private var debugDiagnostics: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("DEBUG StoreKit")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(VaultTheme.inkMuted)
            if let detail = subscriptions.lastErrorDetail, !detail.isEmpty {
                Text(detail)
                    .font(.system(size: 11, design: .monospaced))
                    .foregroundStyle(VaultTheme.inkMuted)
                    .textSelection(.enabled)
            }
            if !subscriptions.missingProductIDs.isEmpty {
                Text(subscriptions.missingProductIDs.joined(separator: "\n"))
                    .font(.system(size: 11, design: .monospaced))
                    .foregroundStyle(VaultTheme.inkMuted)
                    .textSelection(.enabled)
            }
            if let id = subscriptions.activeProductID {
                Text(id)
                    .font(.system(size: 11, design: .monospaced))
                    .foregroundStyle(VaultTheme.inkMuted)
                    .textSelection(.enabled)
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .overlay(
            RoundedRectangle(cornerRadius: 6, style: .continuous)
                .stroke(VaultTheme.line.opacity(0.7), lineWidth: 1)
        )
    }
    #endif

    // MARK: - Helpers

    private var activeDetail: String? {
        var parts: [String] = []
        #if DEBUG
        if let id = subscriptions.activeProductID {
            parts.append(id)
        }
        #endif
        if let exp = subscriptions.expirationDate {
            let formatter = DateFormatter()
            formatter.dateStyle = .medium
            formatter.timeStyle = .none
            parts.append(localization.text("paywall.expires") + " " + formatter.string(from: exp))
        }
        if subscriptions.willAutoRenew {
            parts.append(localization.text("paywall.autoRenewOn"))
        }
        return parts.isEmpty ? nil : parts.joined(separator: " · ")
    }

    private func product(in products: [Product], period: BillingPeriod) -> Product? {
        let ids: [String]
        switch period {
        case .monthly:
            ids = products.compactMap { p in
                BillingConfig.periodRank(for: p.id) == 0 ? p.id : nil
            }
        case .quarterly:
            ids = products.compactMap { p in
                BillingConfig.periodRank(for: p.id) == 1 ? p.id : nil
            }
        case .yearly:
            ids = products.compactMap { p in
                BillingConfig.periodRank(for: p.id) == 2 ? p.id : nil
            }
        }
        return products.first { ids.contains($0.id) }
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
            return product.displayName
        }
    }

    private func statusBanner(_ title: String, detail: String?) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(VaultTheme.ink)
            if let detail {
                Text(detail)
                    .font(.system(size: 12))
                    .foregroundStyle(VaultTheme.inkMuted)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(VaultTheme.seal.opacity(0.12))
        .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
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
