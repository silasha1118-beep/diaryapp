import Foundation

/// Daily diary **upload** quotas (Silas 2026-10-31): text create + multimedia (DA-15 stub).
/// Day boundary matches ``DiaryUploadUsageGuard`` / ``MirrorUsageGuard`` (device local calendar day).
///
/// Independent of Mirror AI quotas in ``AIConfig`` / ``MirrorUsageGuard`` (Free 3→6, Plus 15, Pro 40).
enum UploadQuotaConfig {
    enum Kind: String {
        case text
        /// Voice / video diary create — API ready; UI deferred (DA-15).
        case multimedia
    }

    // MARK: - Text text (create entry)

    /// Soft free through this many text creates; further Free creates until hard cap show dismissible Plus.
    static let freeTextSoftThreshold = 4
    /// Hard stop after this many Free text creates (“done for today”).
    static let freeTextHardLimit = 8

    // MARK: - Free multimedia (DA-15)

    static let freeMediaSoftThreshold = 3
    static let freeMediaHardLimit = 5

    // MARK: - Author Plus

    static let plusTextHardLimit = 20
    static let plusMediaHardLimit = 11

    // MARK: - Author Pro

    static let proTextHardLimit = 40
    static let proMediaHardLimit = 22

    static func softThreshold(kind: Kind) -> Int {
        switch kind {
        case .text: return freeTextSoftThreshold
        case .multimedia: return freeMediaSoftThreshold
        }
    }

    static func hardLimit(kind: Kind, tier: BillingConfig.Tier) -> Int {
        switch (kind, tier) {
        case (.text, .free): return freeTextHardLimit
        case (.text, .authorPlus): return plusTextHardLimit
        case (.text, .authorPro): return proTextHardLimit
        case (.multimedia, .free): return freeMediaHardLimit
        case (.multimedia, .authorPlus): return plusMediaHardLimit
        case (.multimedia, .authorPro): return proMediaHardLimit
        }
    }
}
