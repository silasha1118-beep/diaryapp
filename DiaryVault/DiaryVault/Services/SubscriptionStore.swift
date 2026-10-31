import Foundation
import Combine
import StoreKit

/// StoreKit 2 purchase / restore / entitlement for Author Plus and Author Pro.
/// Uses the scheme's local `DiaryVault.storekit` in Simulator/Xcode, or App Store Sandbox / production when that file is cleared.
/// Entitlement rank: Author Pro > Author Plus > Free.
@MainActor
final class SubscriptionStore: ObservableObject {
    @Published private(set) var products: [Product] = []
    @Published private(set) var tier: BillingConfig.Tier = .free
    @Published private(set) var isSubscribed: Bool = false
    @Published private(set) var isAuthorPro: Bool = false
    @Published private(set) var activeProductID: String?
    @Published private(set) var expirationDate: Date?
    @Published private(set) var willAutoRenew: Bool = false
    @Published private(set) var statusMessage: String?
    @Published private(set) var lastErrorDetail: String?
    @Published private(set) var isLoading: Bool = false

    /// Retained for app lifetime (StoreKit `Transaction.updates` is endless). No `deinit` cancel —
    /// `@MainActor` + `nonisolated(unsafe)` deinit broke some Xcode / Swift version combos.
    private var updatesTask: Task<Void, Never>?

    init() {
        updatesTask = Task { await listenForTransactions() }
        Task { await refresh() }
    }

    var plusProducts: [Product] {
        products.filter { BillingConfig.isPlusProduct($0.id) }
    }

    var proProducts: [Product] {
        products.filter { BillingConfig.isProProduct($0.id) }
    }

    /// Load Plus + Pro products and refresh local entitlement from `Transaction.currentEntitlements`.
    func refresh() async {
        isLoading = true
        statusMessage = nil
        lastErrorDetail = nil
        defer { isLoading = false }

        do {
            let ids = BillingConfig.productIDs
            let loaded = try await Product.products(for: ids)
            products = sortProducts(loaded)
            await updateEntitlement()
            if products.isEmpty {
                statusMessage = "products.empty"
            }
        } catch {
            statusMessage = "products.error"
            lastErrorDetail = error.localizedDescription
            products = []
        }
    }

    func purchase(_ product: Product) async {
        guard BillingConfig.isCatalogProduct(product.id) else {
            statusMessage = "purchase.error"
            lastErrorDetail = "Product not in Author Plus / Author Pro catalog."
            return
        }

        isLoading = true
        statusMessage = nil
        lastErrorDetail = nil
        defer { isLoading = false }

        do {
            let result = try await product.purchase()
            switch result {
            case .success(let verification):
                let transaction = try checkVerified(verification)
                await transaction.finish()
                await updateEntitlement()
                statusMessage = "purchase.success"
            case .userCancelled:
                statusMessage = "purchase.cancelled"
            case .pending:
                statusMessage = "purchase.pending"
            @unknown default:
                statusMessage = "purchase.unknown"
            }
        } catch {
            statusMessage = "purchase.error"
            lastErrorDetail = error.localizedDescription
        }
    }

    /// Sync with App Store (or local StoreKit config) and re-read entitlements.
    func restore() async {
        isLoading = true
        statusMessage = nil
        lastErrorDetail = nil
        defer { isLoading = false }

        do {
            try await AppStore.sync()
            await updateEntitlement()
            statusMessage = isSubscribed ? "restore.success" : "restore.none"
        } catch {
            statusMessage = "restore.error"
            lastErrorDetail = error.localizedDescription
        }
    }

    func product(for id: String) -> Product? {
        products.first { $0.id == id }
    }

    // MARK: - Entitlements

    private func updateEntitlement() async {
        var best: Transaction?
        var bestTier: BillingConfig.Tier = .free

        for await result in Transaction.currentEntitlements {
            guard let transaction = try? checkVerified(result) else { continue }
            guard let candidateTier = BillingConfig.tier(for: transaction.productID) else { continue }

            if let existing = best, let existingTier = BillingConfig.tier(for: existing.productID) {
                if candidateTier > existingTier {
                    best = transaction
                    bestTier = candidateTier
                } else if candidateTier == existingTier {
                    let existingPeriod = BillingConfig.periodRank(for: existing.productID)
                    let candidatePeriod = BillingConfig.periodRank(for: transaction.productID)
                    if candidatePeriod > existingPeriod {
                        best = transaction
                        bestTier = candidateTier
                    } else if candidatePeriod == existingPeriod {
                        let existingExp = existing.expirationDate ?? .distantPast
                        let candidateExp = transaction.expirationDate ?? .distantPast
                        if candidateExp > existingExp {
                            best = transaction
                            bestTier = candidateTier
                        }
                    }
                }
            } else {
                best = transaction
                bestTier = candidateTier
            }
        }

        if let best {
            activeProductID = best.productID
            expirationDate = best.expirationDate
            tier = bestTier
            isSubscribed = bestTier >= .authorPlus
            isAuthorPro = bestTier == .authorPro
            willAutoRenew = await subscriptionWillAutoRenew(for: best.productID)
        } else {
            activeProductID = nil
            expirationDate = nil
            tier = .free
            isSubscribed = false
            isAuthorPro = false
            willAutoRenew = false
        }

        // Sync for MirrorUsageGuard (cloud clients read this synchronously).
        UserDefaults.standard.set(isSubscribed, forKey: BillingConfig.authorPlusEntitlementDefaultsKey)
        UserDefaults.standard.set(isAuthorPro, forKey: BillingConfig.authorProEntitlementDefaultsKey)
        UserDefaults.standard.set(tier.rawValue, forKey: BillingConfig.mirrorTierDefaultsKey)
    }

    private func subscriptionWillAutoRenew(for productID: String) async -> Bool {
        guard let product = products.first(where: { $0.id == productID }),
              let subscription = product.subscription else {
            return false
        }
        guard let statuses = try? await subscription.status else { return false }
        for status in statuses {
            guard case .verified(let renewal) = status.renewalInfo else { continue }
            if renewal.currentProductID == productID {
                return renewal.willAutoRenew
            }
        }
        return false
    }

    private func listenForTransactions() async {
        for await result in Transaction.updates {
            guard let transaction = try? checkVerified(result) else { continue }
            await transaction.finish()
            await updateEntitlement()
        }
    }

    private func checkVerified<T>(_ result: VerificationResult<T>) throws -> T {
        switch result {
        case .unverified(_, let error):
            throw error
        case .verified(let value):
            return value
        }
    }

    private func sortProducts(_ products: [Product]) -> [Product] {
        products.sorted { lhs, rhs in
            let leftTier = BillingConfig.tier(for: lhs.id) ?? .free
            let rightTier = BillingConfig.tier(for: rhs.id) ?? .free
            if leftTier != rightTier {
                // Plus before Pro in the list (upsell path Free → Plus → Pro).
                return leftTier < rightTier
            }
            let lo = BillingConfig.periodRank(for: lhs.id)
            let ro = BillingConfig.periodRank(for: rhs.id)
            if lo != ro { return lo < ro }
            return lhs.id < rhs.id
        }
    }
}
