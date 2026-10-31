import Foundation

/// Local daily Mirror AI request counter (not a server-side quota).
/// Limits come from ``AIConfig`` + StoreKit entitlement snapshot (``BillingConfig/currentTier``).
///
/// - Free: soft Author Plus reminder after ``AIConfig/freeSoftPaywallThreshold`` (default 3);
///   hard stop at ``AIConfig/freeDailyRequestLimit`` (default 6).
/// - Author Plus: hard stop at ``AIConfig/dailyRequestLimit`` (default 15), then upsell Author Pro.
/// - Author Pro: hard stop at ``AIConfig/proDailyRequestLimit`` (default 40) — “done for today”.
///
/// Diary **upload** quotas are separate — see ``DiaryUploadUsageGuard`` / ``UploadQuotaConfig``.
/// Day stamp = local calendar day (`TimeZone.current`), same helper style as the upload guard.
enum MirrorUsageGuard {
    private static let dayKey = "mirrorAI.usageDay"
    private static let countKey = "mirrorAI.usageCount"

    /// Effective daily cap for a billing tier.
    /// - Free: ``AIConfig/freeDailyRequestLimit`` (always ≥ 1)
    /// - Author Plus: ``AIConfig/dailyRequestLimit`` (`0` = unlimited)
    /// - Author Pro: ``AIConfig/proDailyRequestLimit`` (`0` = unlimited)
    static func effectiveLimit(tier: BillingConfig.Tier) -> Int {
        switch tier {
        case .free:
            return AIConfig.freeDailyRequestLimit
        case .authorPlus:
            return AIConfig.dailyRequestLimit
        case .authorPro:
            return AIConfig.proDailyRequestLimit
        }
    }

    /// Convenience for call sites that still pass Plus vs Free (Pro callers should use ``effectiveLimit(tier:)``).
    static func effectiveLimit(isAuthorPlus: Bool) -> Int {
        effectiveLimit(tier: isAuthorPlus ? .authorPlus : .free)
    }

    /// Requests already counted today (resets on calendar day change).
    static func usageCountToday() -> Int {
        let defaults = UserDefaults.standard
        let today = dayStamp()
        guard defaults.string(forKey: dayKey) == today else { return 0 }
        return max(0, defaults.integer(forKey: countKey))
    }

    /// Remaining requests today, or `nil` when unlimited (tier limit `0`).
    static func remaining(tier: BillingConfig.Tier) -> Int? {
        let limit = effectiveLimit(tier: tier)
        guard limit > 0 else { return nil }
        return max(0, limit - usageCountToday())
    }

    static func remaining(isAuthorPlus: Bool) -> Int? {
        remaining(tier: isAuthorPlus ? .authorPlus : .free)
    }

    static func isExhausted(tier: BillingConfig.Tier) -> Bool {
        let limit = effectiveLimit(tier: tier)
        guard limit > 0 else { return false }
        return usageCountToday() >= limit
    }

    static func isExhausted(isAuthorPlus: Bool) -> Bool {
        isExhausted(tier: isAuthorPlus ? .authorPlus : .free)
    }

    /// Free users who have already used ≥ soft threshold but are not yet hard-capped should see Author Plus before the next Mirror call (dismissible).
    static func shouldNudgePaywall(tier: BillingConfig.Tier) -> Bool {
        guard tier == .free else { return false }
        guard !isExhausted(tier: .free) else { return false }
        let threshold = AIConfig.freeSoftPaywallThreshold
        guard threshold > 0 else { return false }
        return usageCountToday() >= threshold
    }

    static func shouldNudgePaywall(isAuthorPlus: Bool) -> Bool {
        shouldNudgePaywall(tier: isAuthorPlus ? .authorPlus : .free)
    }

    /// Call before a cloud request. Throws ``MirrorAIError/rateLimited`` when the local daily cap is hit.
    static func checkAndIncrement(tier: BillingConfig.Tier = BillingConfig.currentTier) throws {
        let limit = effectiveLimit(tier: tier)
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
            throw MirrorAIError.rateLimited(limit: limit, tier: tier)
        }
        defaults.set(count + 1, forKey: countKey)
    }

    /// Legacy entry used by cloud clients; resolves tier from entitlement snapshot.
    static func checkAndIncrement(isAuthorPlus: Bool) throws {
        try checkAndIncrement(tier: isAuthorPlus ? .authorPlus : .free)
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
