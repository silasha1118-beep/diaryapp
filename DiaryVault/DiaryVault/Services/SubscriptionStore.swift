import Foundation
import Combine
import StoreKit

/// StoreKit 2 stub for Author Plus subscriptions.
/// Loads configurable product IDs, purchases, restores, and tracks a simple local entitlement.
@MainActor
final class SubscriptionStore: ObservableObject {
    @Published private(set) var products: [Product] = []
    @Published private(set) var isSubscribed: Bool = false
    @Published private(set) var activeProductID: String?
    @Published private(set) var statusMessage: String?
    @Published private(set) var isLoading: Bool = false

    private var updatesTask: Task<Void, Never>?

    init() {
        updatesTask = Task { await listenForTransactions() }
        Task { await refresh() }
    }

    func refresh() async {
        isLoading = true
        statusMessage = nil
        defer { isLoading = false }

        do {
            let ids = BillingConfig.productIDs
            products = try await Product.products(for: ids).sorted { $0.id < $1.id }
            await updateEntitlement()
            if products.isEmpty {
                statusMessage = "products.empty"
            }
        } catch {
            statusMessage = "products.error"
            products = []
        }
    }

    func purchase(_ product: Product) async {
        isLoading = true
        statusMessage = nil
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
        }
    }

    /// Restores previous purchases (App Store sync).
    func restore() async {
        isLoading = true
        statusMessage = nil
        defer { isLoading = false }

        do {
            try await AppStore.sync()
            await updateEntitlement()
            statusMessage = isSubscribed ? "restore.success" : "restore.none"
        } catch {
            statusMessage = "restore.error"
        }
    }

    private func updateEntitlement() async {
        var foundID: String?
        for await result in Transaction.currentEntitlements {
            guard let transaction = try? checkVerified(result) else { continue }
            if BillingConfig.productIDs.contains(transaction.productID) {
                foundID = transaction.productID
                break
            }
        }
        activeProductID = foundID
        isSubscribed = foundID != nil
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
}
