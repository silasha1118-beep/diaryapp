import Foundation

/// StoreKit product IDs for Author Plus (living-author plan).
/// Override via scheme env / Info.plist — product IDs are public identifiers, never secrets.
///
/// - `DIARY_IAP_MONTHLY_PRODUCT_ID` — default `com.diaryvault.author.plus.monthly`
/// - `DIARY_IAP_YEARLY_PRODUCT_ID` — default `com.diaryvault.author.plus.yearly`
///
/// Voice / video diary and any higher multimedia tier are **deferred** — not configured here.
enum BillingConfig {
    /// Free = local vault + limited mirror (quota wiring later).
    /// Author Plus = unlimited diary narrative + mirror advice quota story.
    enum Tier: String, CaseIterable {
        case free
        case authorPlus
    }

    static let defaultMonthlyProductID = "com.diaryvault.author.plus.monthly"
    static let defaultYearlyProductID = "com.diaryvault.author.plus.yearly"

    static var monthlyProductID: String {
        value(for: "DIARY_IAP_MONTHLY_PRODUCT_ID") ?? defaultMonthlyProductID
    }

    static var yearlyProductID: String {
        value(for: "DIARY_IAP_YEARLY_PRODUCT_ID") ?? defaultYearlyProductID
    }

    /// Product IDs loaded / purchasable in this build (Author Plus monthly + yearly).
    static var productIDs: [String] {
        [monthlyProductID, yearlyProductID]
    }

    static var planName: String { "Author Plus" }

    static func isAuthorPlusProduct(_ productID: String) -> Bool {
        productIDs.contains(productID)
    }

    private static func value(for key: String) -> String? {
        if let env = ProcessInfo.processInfo.environment[key], !env.isEmpty {
            return env
        }
        if let plist = Bundle.main.object(forInfoDictionaryKey: key) as? String, !plist.isEmpty {
            return plist
        }
        return nil
    }
}
