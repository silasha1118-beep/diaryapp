import Foundation

/// StoreKit product IDs for Author Plus / Author Pro (living-author plans).
/// Override via scheme env / Info.plist — product IDs are public identifiers, never secrets.
///
/// Plus:
/// - `DIARY_IAP_PLUS_MONTHLY_PRODUCT_ID` (alias `DIARY_IAP_MONTHLY_PRODUCT_ID`)
/// - `DIARY_IAP_PLUS_QUARTERLY_PRODUCT_ID`
/// - `DIARY_IAP_PLUS_YEARLY_PRODUCT_ID` (alias `DIARY_IAP_YEARLY_PRODUCT_ID`)
///
/// Pro:
/// - `DIARY_IAP_PRO_MONTHLY_PRODUCT_ID`
/// - `DIARY_IAP_PRO_QUARTERLY_PRODUCT_ID`
/// - `DIARY_IAP_PRO_YEARLY_PRODUCT_ID`
///
/// Entitlement rank: Author Pro > Author Plus > Free.
/// Voice / video diary duration caps are deferred (DA-15).
enum BillingConfig {
    enum Tier: String, CaseIterable, Comparable {
        case free
        case authorPlus
        case authorPro

        private var rank: Int {
            switch self {
            case .free: return 0
            case .authorPlus: return 1
            case .authorPro: return 2
            }
        }

        static func < (lhs: Tier, rhs: Tier) -> Bool {
            lhs.rank < rhs.rank
        }

        var displayName: String {
            switch self {
            case .free: return "Free"
            case .authorPlus: return "Author Plus"
            case .authorPro: return "Author Pro"
            }
        }
    }

    /// Snapshot keys for ``MirrorUsageGuard`` (cloud clients read synchronously).
    static let authorPlusEntitlementDefaultsKey = "billing.authorPlusActive"
    static let authorProEntitlementDefaultsKey = "billing.authorProActive"
    static let mirrorTierDefaultsKey = "billing.mirrorTier"

    static var isAuthorPlusEntitled: Bool {
        UserDefaults.standard.bool(forKey: authorPlusEntitlementDefaultsKey)
    }

    static var isAuthorProEntitled: Bool {
        UserDefaults.standard.bool(forKey: authorProEntitlementDefaultsKey)
    }

    /// Highest entitled Mirror billing tier (Pro > Plus > Free).
    static var currentTier: Tier {
        if isAuthorProEntitled { return .authorPro }
        if isAuthorPlusEntitled { return .authorPlus }
        if let raw = UserDefaults.standard.string(forKey: mirrorTierDefaultsKey),
           let tier = Tier(rawValue: raw) {
            return tier
        }
        return .free
    }

    // MARK: - Plus product IDs

    static let defaultPlusMonthlyProductID = "com.diaryvault.author.plus.monthly"
    static let defaultPlusQuarterlyProductID = "com.diaryvault.author.plus.quarterly"
    static let defaultPlusYearlyProductID = "com.diaryvault.author.plus.yearly"

    // MARK: - Pro product IDs

    static let defaultProMonthlyProductID = "com.diaryvault.author.pro.monthly"
    static let defaultProQuarterlyProductID = "com.diaryvault.author.pro.quarterly"
    static let defaultProYearlyProductID = "com.diaryvault.author.pro.yearly"

    static var plusMonthlyProductID: String {
        value(for: "DIARY_IAP_PLUS_MONTHLY_PRODUCT_ID")
            ?? value(for: "DIARY_IAP_MONTHLY_PRODUCT_ID")
            ?? defaultPlusMonthlyProductID
    }

    static var plusQuarterlyProductID: String {
        value(for: "DIARY_IAP_PLUS_QUARTERLY_PRODUCT_ID") ?? defaultPlusQuarterlyProductID
    }

    static var plusYearlyProductID: String {
        value(for: "DIARY_IAP_PLUS_YEARLY_PRODUCT_ID")
            ?? value(for: "DIARY_IAP_YEARLY_PRODUCT_ID")
            ?? defaultPlusYearlyProductID
    }

    static var proMonthlyProductID: String {
        value(for: "DIARY_IAP_PRO_MONTHLY_PRODUCT_ID") ?? defaultProMonthlyProductID
    }

    static var proQuarterlyProductID: String {
        value(for: "DIARY_IAP_PRO_QUARTERLY_PRODUCT_ID") ?? defaultProQuarterlyProductID
    }

    static var proYearlyProductID: String {
        value(for: "DIARY_IAP_PRO_YEARLY_PRODUCT_ID") ?? defaultProYearlyProductID
    }

    /// Legacy aliases used by older call sites / README.
    static var monthlyProductID: String { plusMonthlyProductID }
    static var yearlyProductID: String { plusYearlyProductID }

    static var plusProductIDs: [String] {
        [plusMonthlyProductID, plusQuarterlyProductID, plusYearlyProductID]
    }

    static var proProductIDs: [String] {
        [proMonthlyProductID, proQuarterlyProductID, proYearlyProductID]
    }

    /// All StoreKit product IDs loaded in this build.
    static var productIDs: [String] {
        plusProductIDs + proProductIDs
    }

    static var planName: String { Tier.authorPlus.displayName }
    static var higherPlanName: String { Tier.authorPro.displayName }

    static func isPlusProduct(_ productID: String) -> Bool {
        plusProductIDs.contains(productID)
    }

    static func isProProduct(_ productID: String) -> Bool {
        proProductIDs.contains(productID)
    }

    static func isCatalogProduct(_ productID: String) -> Bool {
        productIDs.contains(productID)
    }

    /// Prefer Pro over Plus; within a tier prefer yearly > quarterly > monthly.
    static func periodRank(for productID: String) -> Int {
        if productID.hasSuffix(".yearly") { return 2 }
        if productID.hasSuffix(".quarterly") { return 1 }
        if productID.hasSuffix(".monthly") { return 0 }
        return -1
    }

    static func tier(for productID: String) -> Tier? {
        if isProProduct(productID) { return .authorPro }
        if isPlusProduct(productID) { return .authorPlus }
        return nil
    }

    /// Backward-compatible name.
    static func isAuthorPlusProduct(_ productID: String) -> Bool {
        isPlusProduct(productID)
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
