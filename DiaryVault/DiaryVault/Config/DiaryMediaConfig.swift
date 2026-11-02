import CoreGraphics
import Foundation

/// Soft caps for diary multimedia (Silas 2026-11-01 / 2026-11-02).
/// Upload **counts** stay in ``UploadQuotaConfig`` (same vault bucket as text).
/// Per-entry attachment caps are independent of Free lifetime / Plus·Pro daily vault quotas.
enum DiaryMediaConfig {
    /// Suggested voice recording cap (~60–90s). Hard stop at the high end.
    static let voiceMaxDurationSeconds: TimeInterval = 90

    /// Suggested video recording / import cap (~30–60s). Hard stop at the high end.
    static let videoMaxDurationSeconds: TimeInterval = 60

    // MARK: - Per-entry attachment caps (not vault upload quota)

    /// Overall safety ceiling across all kinds on one entry.
    static let maxAttachmentsPerEntry = 24

    /// Several voice clips per diary page.
    static let maxVoicePerEntry = 6

    /// Several still photos per diary page.
    static let maxImagesPerEntry = 12

    /// Several short videos per diary page.
    static let maxVideosPerEntry = 6

    /// Hard limit for a given kind on one entry.
    static func maxCount(for kind: DiaryMediaKind) -> Int {
        switch kind {
        case .voice: return maxVoicePerEntry
        case .image: return maxImagesPerEntry
        case .video: return maxVideosPerEntry
        }
    }

    /// Whether another attachment of `kind` may be added to `attachments`.
    static func canAdd(kind: DiaryMediaKind, to attachments: [DiaryMediaAttachment]) -> Bool {
        remainingSlots(for: kind, in: attachments) > 0
    }

    /// How many more of `kind` can still be added (respects kind + total ceilings).
    static func remainingSlots(for kind: DiaryMediaKind, in attachments: [DiaryMediaAttachment]) -> Int {
        let kindRemaining = maxCount(for: kind) - attachments.filter { $0.kind == kind }.count
        let totalRemaining = maxAttachmentsPerEntry - attachments.count
        return max(0, min(kindRemaining, totalRemaining))
    }

    /// JPEG compression quality for still photos.
    static let imageJPEGQuality: CGFloat = 0.72

    /// Longest edge for still photos before JPEG encode.
    static let imageMaxPixelLength: CGFloat = 1920

    /// Rough per-file size soft guidance (bytes) — oversized imports are still accepted after compression.
    static let imageSoftMaxBytes = 4 * 1024 * 1024
}
