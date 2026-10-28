import SwiftUI
import StoreKit

struct PaywallView: View {
    @EnvironmentObject private var localization: LocalizationManager
    @EnvironmentObject private var subscriptions: SubscriptionStore
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ZStack {
                VaultBackground()

                ScrollView {
                    VStack(alignment: .leading, spacing: 20) {
                        Text(localization.text("paywall.headline"))
                            .font(.system(size: 28, weight: .semibold, design: .serif))
                            .foregroundStyle(VaultTheme.ink)

                        Text(localization.text("paywall.body"))
                            .font(.system(size: 16))
                            .foregroundStyle(VaultTheme.inkMuted)
                            .lineSpacing(4)
                            .fixedSize(horizontal: false, vertical: true)

                        freeTierCard

                        if subscriptions.isSubscribed {
                            statusBanner(
                                localization.text("paywall.active"),
                                detail: activeDetail
                            )
                        }

                        if subscriptions.isLoading && subscriptions.products.isEmpty {
                            ProgressView()
                                .padding(.vertical, 12)
                        }

                        ForEach(subscriptions.products, id: \.id) { product in
                            productRow(product)
                        }

                        if subscriptions.products.isEmpty && !subscriptions.isLoading {
                            placeholderProducts
                        }

                        Button {
                            Task { await subscriptions.restore() }
                        } label: {
                            Text(localization.text("paywall.restore"))
                                .font(.system(size: 15, weight: .medium))
                                .foregroundStyle(VaultTheme.ink)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 12)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 4, style: .continuous)
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

                        if let key = subscriptions.statusMessage {
                            Text(statusText(for: key))
                                .font(.system(size: 13))
                                .foregroundStyle(VaultTheme.inkMuted)
                        }

                        if let detail = subscriptions.lastErrorDetail, !detail.isEmpty {
                            Text(detail)
                                .font(.system(size: 12, design: .monospaced))
                                .foregroundStyle(VaultTheme.inkMuted)
                        }

                        Text(localization.text("paywall.footer"))
                            .font(.system(size: 12))
                            .foregroundStyle(VaultTheme.inkMuted)
                            .padding(.top, 8)
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
        }
    }

    private var activeDetail: String? {
        var parts: [String] = []
        if let id = subscriptions.activeProductID {
            parts.append(id)
        }
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

    private var freeTierCard: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(localization.text("paywall.free.title"))
                .font(.system(size: 17, weight: .semibold, design: .serif))
                .foregroundStyle(VaultTheme.ink)
            Text(localization.text("paywall.free.body"))
                .font(.system(size: 14))
                .foregroundStyle(VaultTheme.inkMuted)
                .fixedSize(horizontal: false, vertical: true)
            if !subscriptions.isSubscribed {
                Text(localization.text("paywall.free.current"))
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(VaultTheme.seal)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .overlay(
            RoundedRectangle(cornerRadius: 4, style: .continuous)
                .stroke(VaultTheme.line, lineWidth: 1)
        )
    }

    private var placeholderProducts: some View {
        VStack(alignment: .leading, spacing: 12) {
            placeholderCard(
                title: localization.text("paywall.monthly"),
                subtitle: BillingConfig.monthlyProductID
            )
            placeholderCard(
                title: localization.text("paywall.yearly"),
                subtitle: BillingConfig.yearlyProductID
            )
            Text(localization.text("paywall.productsMissing"))
                .font(.system(size: 13))
                .foregroundStyle(VaultTheme.inkMuted)
        }
    }

    private func placeholderCard(title: String, subtitle: String) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.system(size: 17, weight: .semibold, design: .serif))
                .foregroundStyle(VaultTheme.ink)
            Text(subtitle)
                .font(.system(size: 12, design: .monospaced))
                .foregroundStyle(VaultTheme.inkMuted)
            Text(localization.text("paywall.unavailable"))
                .font(.system(size: 14))
                .foregroundStyle(VaultTheme.inkMuted)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .overlay(
            RoundedRectangle(cornerRadius: 4, style: .continuous)
                .stroke(VaultTheme.line, lineWidth: 1)
        )
    }

    private func productRow(_ product: Product) -> some View {
        let isActive = subscriptions.activeProductID == product.id
        return Button {
            Task { await subscriptions.purchase(product) }
        } label: {
            HStack(alignment: .center, spacing: 12) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(displayName(for: product))
                        .font(.system(size: 17, weight: .semibold, design: .serif))
                        .foregroundStyle(VaultTheme.ink)
                    Text(product.description.isEmpty
                         ? localization.text("paywall.plus.blurb")
                         : product.description)
                        .font(.system(size: 13))
                        .foregroundStyle(VaultTheme.inkMuted)
                        .lineLimit(3)
                    if isActive {
                        Text(localization.text("paywall.currentPlan"))
                            .font(.system(size: 12, weight: .medium))
                            .foregroundStyle(VaultTheme.seal)
                    }
                }
                Spacer(minLength: 8)
                Text(isActive ? localization.text("paywall.owned") : product.displayPrice)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(isActive ? VaultTheme.ink : VaultTheme.paper)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .background(isActive ? VaultTheme.seal.opacity(0.18) : VaultTheme.ink)
                    .clipShape(RoundedRectangle(cornerRadius: 4, style: .continuous))
            }
            .padding(16)
            .overlay(
                RoundedRectangle(cornerRadius: 4, style: .continuous)
                    .stroke(isActive ? VaultTheme.seal.opacity(0.55) : VaultTheme.line, lineWidth: isActive ? 1.5 : 1)
            )
        }
        .buttonStyle(.plain)
        .disabled(subscriptions.isLoading || isActive)
        .accessibilityLabel(displayName(for: product))
    }

    private func displayName(for product: Product) -> String {
        if !product.displayName.isEmpty {
            return product.displayName
        }
        if product.id == BillingConfig.monthlyProductID {
            return localization.text("paywall.monthly")
        }
        if product.id == BillingConfig.yearlyProductID {
            return localization.text("paywall.yearly")
        }
        return product.id
    }

    private func statusBanner(_ title: String, detail: String?) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(VaultTheme.ink)
            if let detail {
                Text(detail)
                    .font(.system(size: 12, design: .monospaced))
                    .foregroundStyle(VaultTheme.inkMuted)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(VaultTheme.seal.opacity(0.12))
        .clipShape(RoundedRectangle(cornerRadius: 4, style: .continuous))
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
