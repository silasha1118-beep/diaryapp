import Foundation

/// Local daily Mirror AI request counter (not a server-side quota).
/// Free vs Author Plus limits come from ``AIConfig`` + StoreKit entitlement snapshot.
enum MirrorUsageGuard {
    private static let dayKey = "mirrorAI.usageDay"
    private static let countKey = "mirrorAI.usageCount"

    /// Effective daily cap for the current entitlement.
    /// - Free: ``AIConfig/freeDailyRequestLimit`` (always ≥ 1)
    /// - Author Plus: ``AIConfig/dailyRequestLimit`` (`0` = unlimited)
    static func effectiveLimit(isAuthorPlus: Bool) -> Int {
        if isAuthorPlus {
            return AIConfig.dailyRequestLimit
        }
        return AIConfig.freeDailyRequestLimit
    }

    /// Requests already counted today (resets on calendar day change).
    static func usageCountToday() -> Int {
        let defaults = UserDefaults.standard
        let today = dayStamp()
        guard defaults.string(forKey: dayKey) == today else { return 0 }
        return max(0, defaults.integer(forKey: countKey))
    }

    /// Remaining requests today, or `nil` when unlimited (Plus with limit `0`).
    static func remaining(isAuthorPlus: Bool) -> Int? {
        let limit = effectiveLimit(isAuthorPlus: isAuthorPlus)
        guard limit > 0 else { return nil }
        return max(0, limit - usageCountToday())
    }

    static func isExhausted(isAuthorPlus: Bool) -> Bool {
        let limit = effectiveLimit(isAuthorPlus: isAuthorPlus)
        guard limit > 0 else { return false }
        return usageCountToday() >= limit
    }

    /// Call before a cloud request. Throws ``MirrorAIError/rateLimited`` when the local daily cap is hit.
    static func checkAndIncrement(isAuthorPlus: Bool = BillingConfig.isAuthorPlusEntitled) throws {
        let limit = effectiveLimit(isAuthorPlus: isAuthorPlus)
        guard limit > 0 else { return }

        let defaults = UserDefaults.standard
        let today = dayStamp()
        let storedDay = defaults.string(forKey: dayKey)
        var count = defaults.integer(forKey: countKey)
        if storedDay != today {
            count = 0
            defaults.set(today, forKey: dayKey)
        }
        if count >= limit {
            throw MirrorAIError.rateLimited(limit: limit, isAuthorPlus: isAuthorPlus)
        }
        defaults.set(count + 1, forKey: countKey)
    }

    private static func dayStamp() -> String {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone.current
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: Date())
    }
}
