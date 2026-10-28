import Foundation

/// StoreKit product IDs for Author Plus. Override via scheme env / Info.plist — never hardcode secrets.
///
/// - `DIARY_IAP_MONTHLY_PRODUCT_ID` — default `com.diaryvault.author.plus.monthly`
/// - `DIARY_IAP_YEARLY_PRODUCT_ID` — default `com.diaryvault.author.plus.yearly`
enum BillingConfig {
    static let defaultMonthlyProductID = "com.diaryvault.author.plus.monthly"
    static let defaultYearlyProductID = "com.diaryvault.author.plus.yearly"

    static var monthlyProductID: String {
        value(for: "DIARY_IAP_MONTHLY_PRODUCT_ID") ?? defaultMonthlyProductID
    }

    static var yearlyProductID: String {
        value(for: "DIARY_IAP_YEARLY_PRODUCT_ID") ?? defaultYearlyProductID
    }

    static var productIDs: [String] {
        [monthlyProductID, yearlyProductID]
    }

    /// Human label for Settings / paywall when products are not yet loaded from the store.
    static var planName: String { "Author Plus" }

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
