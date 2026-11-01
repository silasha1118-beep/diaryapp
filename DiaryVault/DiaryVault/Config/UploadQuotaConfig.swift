import Foundation

/// Diary **vault upload** quotas (Silas 2026-11-01): one shared counter for text + multimedia.
///
/// - Free: **lifetime** hard cap (not daily) — hard stop → paywall. **Multimedia is included** in
///   the same lifetime 40 (Free is not media-blocked before that cap).
/// - Author Plus / Pro: **daily** hard caps (local calendar day; same stamp as ``MirrorUsageGuard``).
///   Text + media share that daily vault bucket too.
///
/// Independent of Mirror AI quotas in ``AIConfig`` / ``MirrorUsageGuard``.
enum UploadQuotaConfig {
    enum Kind: String {
        case text
        /// Voice / image / video diary create. Counts toward the same vault bucket for **all**
        /// tiers including Free (lifetime 40). Duration soft caps: ``DiaryMediaConfig``.
        case multimedia
    }

    // MARK: - Free (lifetime, unified vault — text + media)

    /// Account lifetime vault uploads (text + multimedia one bucket). Hard stop → paywall.
    static let freeLifetimeVaultLimit = 40

    // MARK: - Author Plus / Pro (daily, unified vault)

    static let plusDailyVaultLimit = 8
    static let proDailyVaultLimit = 16

    /// Soft ladders removed (2026-11-01). Always `0` so call sites that still ask never nudge.
    static func softThreshold(kind: Kind) -> Int {
        _ = kind
        return 0
    }

    /// Effective hard cap for the vault upload bucket (kind is ignored — unified counter).
    static func hardLimit(kind: Kind, tier: BillingConfig.Tier) -> Int {
        _ = kind
        switch tier {
        case .free: return freeLifetimeVaultLimit
        case .authorPlus: return plusDailyVaultLimit
        case .authorPro: return proDailyVaultLimit
        }
    }
}
