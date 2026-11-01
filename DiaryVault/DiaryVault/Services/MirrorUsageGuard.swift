import Foundation
import FirebaseAuth

/// Local Mirror AI request counter (not a server-side quota).
/// Limits come from ``AIConfig`` + StoreKit entitlement snapshot (``BillingConfig/currentTier``).
///
/// - Free: **lifetime** hard cap (``AIConfig/freeLifetimeRequestLimit``, default 40) — hard stop → paywall.
/// - Author Plus: daily hard stop at ``AIConfig/dailyRequestLimit`` (default 8), then upsell Author Pro.
/// - Author Pro: daily hard stop at ``AIConfig/proDailyRequestLimit`` (default 16) — “done for today”.
///
/// Soft Free ladders removed (2026-11-01). Diary **upload** quotas are separate —
/// see ``DiaryUploadUsageGuard`` / ``UploadQuotaConfig``.
/// Day stamp = local calendar day (`TimeZone.current`), same helper style as the upload guard.
enum MirrorUsageGuard {
    static let firestoreLifetimeField = "mirrorLifetimeCount"

    private static let dayKey = "mirrorAI.usageDay"
    private static let countKey = "mirrorAI.usageCount"
    private static let lifetimeKeyPrefix = "mirrorAI.lifetimeCount."
    private static let accountUserIDKey = "account.userID"
    private static let anonymousAccountKey = "anonymous"

    /// Effective cap for a billing tier (lifetime for Free, daily for paid).
    /// - Free: ``AIConfig/freeLifetimeRequestLimit`` (always ≥ 1)
    /// - Author Plus: ``AIConfig/dailyRequestLimit`` (`0` = unlimited)
    /// - Author Pro: ``AIConfig/proDailyRequestLimit`` (`0` = unlimited)
    static func effectiveLimit(tier: BillingConfig.Tier) -> Int {
        switch tier {
        case .free:
            return AIConfig.freeLifetimeRequestLimit
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

    /// Usage toward the active window: lifetime for Free, today for Plus/Pro.
    static func usageCount(tier: BillingConfig.Tier) -> Int {
        switch tier {
        case .free:
            return lifetimeCount(for: accountKey())
        case .authorPlus, .authorPro:
            return usageCountToday()
        }
    }

    /// Requests already counted today (resets on calendar day change). Free should use ``usageCount(tier:)``.
    static func usageCountToday() -> Int {
        let defaults = UserDefaults.standard
        let today = dayStamp()
        guard defaults.string(forKey: dayKey) == today else { return 0 }
        return max(0, defaults.integer(forKey: countKey))
    }

    /// Remaining requests in the active window, or `nil` when unlimited (paid tier limit `0`).
    static func remaining(tier: BillingConfig.Tier) -> Int? {
        let limit = effectiveLimit(tier: tier)
        guard limit > 0 else { return nil }
        return max(0, limit - usageCount(tier: tier))
    }

    static func remaining(isAuthorPlus: Bool) -> Int? {
        remaining(tier: isAuthorPlus ? .authorPlus : .free)
    }

    static func isExhausted(tier: BillingConfig.Tier) -> Bool {
        let limit = effectiveLimit(tier: tier)
        guard limit > 0 else { return false }
        return usageCount(tier: tier) >= limit
    }

    static func isExhausted(isAuthorPlus: Bool) -> Bool {
        isExhausted(tier: isAuthorPlus ? .authorPlus : .free)
    }

    /// Soft ladders removed — always `false` (hard stop → paywall only).
    static func shouldNudgePaywall(tier: BillingConfig.Tier) -> Bool {
        _ = tier
        return false
    }

    static func shouldNudgePaywall(isAuthorPlus: Bool) -> Bool {
        _ = isAuthorPlus
        return false
    }

    /// Call before a cloud request. Throws ``MirrorAIError/rateLimited`` when the local cap is hit.
    static func checkAndIncrement(tier: BillingConfig.Tier = BillingConfig.currentTier) throws {
        let limit = effectiveLimit(tier: tier)
        guard limit > 0 else { return }

        switch tier {
        case .free:
            let key = accountKey()
            let count = lifetimeCount(for: key)
            if count >= limit {
                throw MirrorAIError.rateLimited(limit: limit, tier: .free)
            }
            let next = count + 1
            setLifetimeCount(next, for: key)
            if let uid = Auth.auth().currentUser?.uid, !uid.isEmpty, uid == key {
                scheduleFirestoreBump(uid: uid)
            }
        case .authorPlus, .authorPro:
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
    }

    /// Legacy entry used by cloud clients; resolves tier from entitlement snapshot.
    static func checkAndIncrement(isAuthorPlus: Bool) throws {
        try checkAndIncrement(tier: isAuthorPlus ? .authorPlus : .free)
    }

    /// Merge remote lifetime count with local (and migrate anonymous) after Auth is live.
    static func hydrateFromCloud(uid: String, remoteCount: Int) {
        guard !uid.isEmpty else { return }
        let local = lifetimeCount(for: uid)
        let anon = lifetimeCount(for: anonymousAccountKey)
        let merged = max(0, local, remoteCount, anon)
        setLifetimeCount(merged, for: uid)
        if anon > 0 {
            UserDefaults.standard.removeObject(forKey: lifetimeKey(for: anonymousAccountKey))
        }
        if merged > remoteCount {
            scheduleFirestoreSet(uid: uid, count: merged)
        }
    }

    // MARK: - Private

    private static func accountKey() -> String {
        if let uid = Auth.auth().currentUser?.uid, !uid.isEmpty {
            return uid
        }
        if let id = UserDefaults.standard.string(forKey: accountUserIDKey), !id.isEmpty {
            return id
        }
        return anonymousAccountKey
    }

    private static func lifetimeKey(for accountKey: String) -> String {
        lifetimeKeyPrefix + accountKey
    }

    private static func lifetimeCount(for accountKey: String) -> Int {
        max(0, UserDefaults.standard.integer(forKey: lifetimeKey(for: accountKey)))
    }

    private static func setLifetimeCount(_ count: Int, for accountKey: String) {
        UserDefaults.standard.set(max(0, count), forKey: lifetimeKey(for: accountKey))
    }

    private static func scheduleFirestoreBump(uid: String) {
        Task {
            try? await DiaryCloudSync.incrementLifetimeQuota(
                uid: uid,
                field: firestoreLifetimeField
            )
        }
    }

    private static func scheduleFirestoreSet(uid: String, count: Int) {
        Task {
            try? await DiaryCloudSync.setLifetimeQuota(
                uid: uid,
                field: firestoreLifetimeField,
                count: count
            )
        }
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
