import Foundation

/// Local daily counter for **creating** diary uploads (not server-side quota).
/// Gates new text entries now; multimedia paths are ready for DA-15 voice/video create.
///
/// Day key uses the same local-calendar stamp style as ``MirrorUsageGuard``
/// (`yyyy-MM-dd` in `TimeZone.current` — Asia/Shanghai when the device is set that way).
///
/// Separate from Mirror request counting (own UserDefaults keys). Mirror keeps its own caps
/// in ``MirrorUsageGuard`` / ``AIConfig`` (Free 3→6, Plus 15, Pro 40) — do not couple them.
enum DiaryUploadUsageGuard {
    enum UploadError: Error, Equatable {
        /// Hard daily cap reached for this kind + tier.
        case rateLimited(kind: UploadQuotaConfig.Kind, limit: Int, tier: BillingConfig.Tier)
    }

    private static let textDayKey = "diaryUpload.text.usageDay"
    private static let textCountKey = "diaryUpload.text.usageCount"
    private static let mediaDayKey = "diaryUpload.media.usageDay"
    private static let mediaCountKey = "diaryUpload.media.usageCount"

    static func effectiveLimit(
        kind: UploadQuotaConfig.Kind,
        tier: BillingConfig.Tier
    ) -> Int {
        UploadQuotaConfig.hardLimit(kind: kind, tier: tier)
    }

    static func usageCountToday(kind: UploadQuotaConfig.Kind) -> Int {
        let defaults = UserDefaults.standard
        let today = dayStamp()
        let (dayKey, countKey) = keys(for: kind)
        guard defaults.string(forKey: dayKey) == today else { return 0 }
        return max(0, defaults.integer(forKey: countKey))
    }

    static func remaining(kind: UploadQuotaConfig.Kind, tier: BillingConfig.Tier) -> Int {
        max(0, effectiveLimit(kind: kind, tier: tier) - usageCountToday(kind: kind))
    }

    static func isExhausted(kind: UploadQuotaConfig.Kind, tier: BillingConfig.Tier) -> Bool {
        usageCountToday(kind: kind) >= effectiveLimit(kind: kind, tier: tier)
    }

    /// Free users who already used ≥ soft threshold but are not hard-capped should see dismissible Plus.
    static func shouldNudgePaywall(kind: UploadQuotaConfig.Kind, tier: BillingConfig.Tier) -> Bool {
        guard tier == .free else { return false }
        guard !isExhausted(kind: kind, tier: .free) else { return false }
        let threshold = UploadQuotaConfig.softThreshold(kind: kind)
        guard threshold > 0 else { return false }
        return usageCountToday(kind: kind) >= threshold
    }

    /// Call before persisting a **new** diary create. Throws when the local daily cap is hit.
    /// Editing an existing entry must not call this.
    static func checkAndIncrement(
        kind: UploadQuotaConfig.Kind,
        tier: BillingConfig.Tier = BillingConfig.currentTier
    ) throws {
        let limit = effectiveLimit(kind: kind, tier: tier)
        let defaults = UserDefaults.standard
        let today = dayStamp()
        let (dayKey, countKey) = keys(for: kind)
        let storedDay = defaults.string(forKey: dayKey)
        var count = defaults.integer(forKey: countKey)
        if storedDay != today {
            count = 0
            defaults.set(today, forKey: dayKey)
        }
        if count >= limit {
            throw UploadError.rateLimited(kind: kind, limit: limit, tier: tier)
        }
        defaults.set(count + 1, forKey: countKey)
    }

    /// DA-15 entry point — same semantics as text; no UI caller yet.
    static func checkAndIncrementMultimedia(
        tier: BillingConfig.Tier = BillingConfig.currentTier
    ) throws {
        try checkAndIncrement(kind: .multimedia, tier: tier)
    }

    private static func keys(for kind: UploadQuotaConfig.Kind) -> (String, String) {
        switch kind {
        case .text: return (textDayKey, textCountKey)
        case .multimedia: return (mediaDayKey, mediaCountKey)
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
