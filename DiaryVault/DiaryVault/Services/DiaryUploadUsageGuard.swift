import Foundation
import FirebaseAuth

/// Local counter for **creating** diary vault uploads (not server-side quota).
/// Text + multimedia share one vault bucket.
///
/// - Free: **lifetime** per account (UserDefaults keyed by uid; mirrored to Firestore `users/{uid}`).
/// - Author Plus / Pro: **daily** (local calendar day, same stamp style as ``MirrorUsageGuard``).
///
/// Separate from Mirror request counting. Soft Free ladders removed (2026-11-01) — hard stop → paywall.
enum DiaryUploadUsageGuard {
    enum UploadError: Error, Equatable {
        /// Hard cap reached for this kind + tier (lifetime for Free, daily for paid).
        case rateLimited(kind: UploadQuotaConfig.Kind, limit: Int, tier: BillingConfig.Tier)
    }

    static let firestoreLifetimeField = "vaultUploadLifetimeCount"

    private static let vaultDayKey = "diaryUpload.vault.usageDay"
    private static let vaultCountKey = "diaryUpload.vault.usageCount"
    private static let lifetimeKeyPrefix = "diaryUpload.vault.lifetimeCount."
    private static let legacyTextDayKey = "diaryUpload.text.usageDay"
    private static let legacyTextCountKey = "diaryUpload.text.usageCount"
    private static let legacyMediaDayKey = "diaryUpload.media.usageDay"
    private static let legacyMediaCountKey = "diaryUpload.media.usageCount"
    private static let accountUserIDKey = "account.userID"
    private static let anonymousAccountKey = "anonymous"

    static func effectiveLimit(
        kind: UploadQuotaConfig.Kind,
        tier: BillingConfig.Tier
    ) -> Int {
        UploadQuotaConfig.hardLimit(kind: kind, tier: tier)
    }

    /// Usage toward the active window: lifetime for Free, today for Plus/Pro.
    static func usageCount(kind: UploadQuotaConfig.Kind, tier: BillingConfig.Tier) -> Int {
        _ = kind
        switch tier {
        case .free:
            return lifetimeCount(for: accountKey())
        case .authorPlus, .authorPro:
            return usageCountToday()
        }
    }

    /// Legacy name kept for call sites — Free returns lifetime usage, not “today”.
    static func usageCountToday(kind: UploadQuotaConfig.Kind) -> Int {
        usageCount(kind: kind, tier: BillingConfig.currentTier)
    }

    static func remaining(kind: UploadQuotaConfig.Kind, tier: BillingConfig.Tier) -> Int {
        max(0, effectiveLimit(kind: kind, tier: tier) - usageCount(kind: kind, tier: tier))
    }

    static func isExhausted(kind: UploadQuotaConfig.Kind, tier: BillingConfig.Tier) -> Bool {
        usageCount(kind: kind, tier: tier) >= effectiveLimit(kind: kind, tier: tier)
    }

    /// Soft ladders removed — always `false` (hard stop → paywall only).
    static func shouldNudgePaywall(kind: UploadQuotaConfig.Kind, tier: BillingConfig.Tier) -> Bool {
        _ = kind
        _ = tier
        return false
    }

    /// Call before persisting a **new** diary create. Throws when the local cap is hit.
    /// Editing an existing entry must not call this.
    static func checkAndIncrement(
        kind: UploadQuotaConfig.Kind,
        tier: BillingConfig.Tier = BillingConfig.currentTier
    ) throws {
        let limit = effectiveLimit(kind: kind, tier: tier)
        switch tier {
        case .free:
            try checkAndIncrementLifetime(kind: kind, limit: limit)
        case .authorPlus, .authorPro:
            try checkAndIncrementDaily(kind: kind, limit: limit, tier: tier)
        }
    }

    /// Multimedia create entry — same vault bucket as text for **all** tiers (Free lifetime
    /// includes media; Plus/Pro daily vault includes media). Call before persisting a new
    /// media diary. Full media UI/Storage polish remains separate (DA-15).
    static func checkAndIncrementMultimedia(
        tier: BillingConfig.Tier = BillingConfig.currentTier
    ) throws {
        try checkAndIncrement(kind: .multimedia, tier: tier)
    }

    /// Merge remote lifetime count with local (and migrate anonymous) after Auth is live.
    /// If local is ahead of remote, pushes the higher value to Firestore.
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

    private static func checkAndIncrementLifetime(kind: UploadQuotaConfig.Kind, limit: Int) throws {
        let key = accountKey()
        let count = lifetimeCount(for: key)
        if count >= limit {
            throw UploadError.rateLimited(kind: kind, limit: limit, tier: .free)
        }
        let next = count + 1
        setLifetimeCount(next, for: key)
        if let uid = Auth.auth().currentUser?.uid, !uid.isEmpty, uid == key {
            scheduleFirestoreBump(uid: uid)
        }
    }

    private static func checkAndIncrementDaily(
        kind: UploadQuotaConfig.Kind,
        limit: Int,
        tier: BillingConfig.Tier
    ) throws {
        let defaults = UserDefaults.standard
        let today = dayStamp()
        let storedDay = defaults.string(forKey: vaultDayKey)
        var count = defaults.integer(forKey: vaultCountKey)
        if storedDay != today {
            // One-time migration: fold legacy split text/media day counts into the unified vault day.
            count = migratedDailyCountIfNeeded(defaults: defaults, today: today, storedDay: storedDay)
            defaults.set(today, forKey: vaultDayKey)
        }
        if count >= limit {
            throw UploadError.rateLimited(kind: kind, limit: limit, tier: tier)
        }
        defaults.set(count + 1, forKey: vaultCountKey)
    }

    private static func usageCountToday() -> Int {
        let defaults = UserDefaults.standard
        let today = dayStamp()
        let storedDay = defaults.string(forKey: vaultDayKey)
        if storedDay == today {
            return max(0, defaults.integer(forKey: vaultCountKey))
        }
        return migratedDailyCountIfNeeded(defaults: defaults, today: today, storedDay: storedDay)
    }

    /// If unified day key is missing/stale, sum legacy text+media for today (then clear legacy).
    private static func migratedDailyCountIfNeeded(
        defaults: UserDefaults,
        today: String,
        storedDay: String?
    ) -> Int {
        if storedDay == today {
            return max(0, defaults.integer(forKey: vaultCountKey))
        }
        var migrated = 0
        if defaults.string(forKey: legacyTextDayKey) == today {
            migrated += max(0, defaults.integer(forKey: legacyTextCountKey))
        }
        if defaults.string(forKey: legacyMediaDayKey) == today {
            migrated += max(0, defaults.integer(forKey: legacyMediaCountKey))
        }
        defaults.removeObject(forKey: legacyTextDayKey)
        defaults.removeObject(forKey: legacyTextCountKey)
        defaults.removeObject(forKey: legacyMediaDayKey)
        defaults.removeObject(forKey: legacyMediaCountKey)
        if migrated > 0 {
            defaults.set(today, forKey: vaultDayKey)
            defaults.set(migrated, forKey: vaultCountKey)
        }
        return migrated
    }

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

    /// Same day-stamp rules as ``MirrorUsageGuard`` (local calendar day).
    private static func dayStamp() -> String {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone.current
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: Date())
    }
}
