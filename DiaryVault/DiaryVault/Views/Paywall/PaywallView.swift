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

                        if subscriptions.isSubscribed {
                            statusBanner(localization.text("paywall.active"), detail: subscriptions.activeProductID)
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

                        if let key = subscriptions.statusMessage {
                            Text(statusText(for: key))
                                .font(.system(size: 13))
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
        Button {
            Task { await subscriptions.purchase(product) }
        } label: {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text(product.displayName.isEmpty ? product.id : product.displayName)
                        .font(.system(size: 17, weight: .semibold, design: .serif))
                        .foregroundStyle(VaultTheme.ink)
                    Text(product.description)
                        .font(.system(size: 13))
                        .foregroundStyle(VaultTheme.inkMuted)
                        .lineLimit(2)
                }
                Spacer()
                Text(product.displayPrice)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(VaultTheme.paper)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .background(VaultTheme.ink)
                    .clipShape(RoundedRectangle(cornerRadius: 4, style: .continuous))
            }
            .padding(16)
            .overlay(
                RoundedRectangle(cornerRadius: 4, style: .continuous)
                    .stroke(VaultTheme.line, lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
        .disabled(subscriptions.isLoading || subscriptions.isSubscribed)
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
