import CoreGraphics
import Foundation

/// Soft caps for diary multimedia (Silas 2026-11-01).
/// Upload **counts** stay in ``UploadQuotaConfig`` (same vault bucket as text).
enum DiaryMediaConfig {
    /// Suggested voice recording cap (~60–90s). Hard stop at the high end.
    static let voiceMaxDurationSeconds: TimeInterval = 90

    /// Suggested video recording / import cap (~30–60s). Hard stop at the high end.
    static let videoMaxDurationSeconds: TimeInterval = 60

    /// Max attachments on a single entry (voice + images + video).
    static let maxAttachmentsPerEntry = 6

    /// JPEG compression quality for still photos.
    static let imageJPEGQuality: CGFloat = 0.72

    /// Longest edge for still photos before JPEG encode.
    static let imageMaxPixelLength: CGFloat = 1920

    /// Rough per-file size soft guidance (bytes) — oversized imports are still accepted after compression.
    static let imageSoftMaxBytes = 4 * 1024 * 1024
}
