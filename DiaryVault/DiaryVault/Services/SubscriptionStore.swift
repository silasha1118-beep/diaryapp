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
    /// Product IDs requested but not returned by StoreKit (DEBUG paywall diagnostics).
    @Published private(set) var missingProductIDs: [String] = []
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

        let ids = BillingConfig.productIDs
        do {
            var loaded = try await Product.products(for: Set(ids))
            // Local `.storekit` / Sandbox can briefly return empty on first cold query.
            if loaded.isEmpty {
                try? await Task.sleep(nanoseconds: 350_000_000)
                loaded = try await Product.products(for: Set(ids))
            }

            products = sortProducts(loaded)
            let returned = Set(loaded.map(\.id))
            missingProductIDs = ids.filter { !returned.contains($0) }
            await updateEntitlement()

            if products.isEmpty {
                statusMessage = "products.empty"
                lastErrorDetail = emptyCatalogHint(requested: ids)
            }
        } catch {
            statusMessage = "products.error"
            lastErrorDetail = error.localizedDescription
            missingProductIDs = ids
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

    /// Default monthly SKU for the next tier (Free → Plus monthly, Plus → Pro monthly).
    /// Used by vault / Mirror hard-cap CTAs so Apple’s purchase sheet opens without the Plans picker.
    /// - Returns `false` when the product catalog could not be loaded (caller may open PaywallView).
    @discardableResult
    func purchaseRecommendedUpgrade(from currentTier: BillingConfig.Tier) async -> Bool {
        let productID: String?
        switch currentTier {
        case .free:
            productID = BillingConfig.plusMonthlyProductID
        case .authorPlus:
            productID = BillingConfig.proMonthlyProductID
        case .authorPro:
            productID = nil
        }
        guard let productID else { return false }

        if product(for: productID) == nil {
            await refresh()
        }
        guard let product = product(for: productID) else {
            statusMessage = "products.empty"
            lastErrorDetail = "Missing recommended upgrade SKU: \(productID)"
            return false
        }
        await purchase(product)
        return true
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

    private func emptyCatalogHint(requested: [String]) -> String {
        """
        StoreKit returned 0 products for \(requested.count) IDs.
        Local: Scheme → Run → Options → StoreKit Configuration = DiaryVault.storekit.
        Sandbox: clear StoreKit Configuration, create matching Connect SKUs, sign in with Sandbox Apple ID.
        Requested: \(requested.joined(separator: ", "))
        """
    }
}
