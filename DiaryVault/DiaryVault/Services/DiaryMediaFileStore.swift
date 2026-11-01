import Foundation
import UIKit
import AVFoundation

/// On-device media files for diary attachments.
/// Layout: Documents/diary_media/{entryId}/{attachmentId}.{ext}
enum DiaryMediaFileStore {
    static var rootDirectory: URL {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
        return docs.appendingPathComponent("diary_media", isDirectory: true)
    }

    static func entryDirectory(entryID: UUID) -> URL {
        rootDirectory.appendingPathComponent(entryID.uuidString, isDirectory: true)
    }

    static func fileURL(entryID: UUID, fileName: String) -> URL {
        entryDirectory(entryID: entryID).appendingPathComponent(fileName)
    }

    static func ensureEntryDirectory(entryID: UUID) throws {
        let dir = entryDirectory(entryID: entryID)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
    }

    static func absoluteURL(entryID: UUID, attachment: DiaryMediaAttachment) -> URL {
        fileURL(entryID: entryID, fileName: attachment.localFileName)
    }

    static func fileExists(entryID: UUID, attachment: DiaryMediaAttachment) -> Bool {
        FileManager.default.fileExists(atPath: absoluteURL(entryID: entryID, attachment: attachment).path)
    }

    /// Removes every file for an entry (cancel composer / wipe).
    static func deleteAll(for entryID: UUID) {
        let dir = entryDirectory(entryID: entryID)
        try? FileManager.default.removeItem(at: dir)
    }

    static func delete(entryID: UUID, attachment: DiaryMediaAttachment) {
        let url = absoluteURL(entryID: entryID, attachment: attachment)
        try? FileManager.default.removeItem(at: url)
    }

    // MARK: - Write helpers

    static func saveImageJPEG(
        entryID: UUID,
        image: UIImage,
        attachmentID: UUID = UUID()
    ) throws -> DiaryMediaAttachment {
        try ensureEntryDirectory(entryID: entryID)
        let resized = resizedImage(image, maxPixel: DiaryMediaConfig.imageMaxPixelLength)
        guard let data = resized.jpegData(compressionQuality: DiaryMediaConfig.imageJPEGQuality) else {
            throw MediaFileError.encodeFailed
        }
        let fileName = "\(attachmentID.uuidString).\(DiaryMediaKind.image.defaultFileExtension)"
        let url = fileURL(entryID: entryID, fileName: fileName)
        try data.write(to: url, options: [.atomic])
        return DiaryMediaAttachment(
            id: attachmentID,
            kind: .image,
            localFileName: fileName,
            mimeType: DiaryMediaKind.image.defaultMIMEType,
            byteSize: data.count
        )
    }

    static func importVoiceRecording(
        entryID: UUID,
        from sourceURL: URL,
        durationSeconds: Double?,
        attachmentID: UUID = UUID()
    ) throws -> DiaryMediaAttachment {
        try ensureEntryDirectory(entryID: entryID)
        let fileName = "\(attachmentID.uuidString).\(DiaryMediaKind.voice.defaultFileExtension)"
        let dest = fileURL(entryID: entryID, fileName: fileName)
        if FileManager.default.fileExists(atPath: dest.path) {
            try FileManager.default.removeItem(at: dest)
        }
        try FileManager.default.copyItem(at: sourceURL, to: dest)
        let size = (try? FileManager.default.attributesOfItem(atPath: dest.path)[.size] as? NSNumber)?.intValue
        return DiaryMediaAttachment(
            id: attachmentID,
            kind: .voice,
            localFileName: fileName,
            mimeType: DiaryMediaKind.voice.defaultMIMEType,
            durationSeconds: durationSeconds,
            byteSize: size
        )
    }

    static func importVideo(
        entryID: UUID,
        from sourceURL: URL,
        attachmentID: UUID = UUID()
    ) async throws -> DiaryMediaAttachment {
        try ensureEntryDirectory(entryID: entryID)
        let fileName = "\(attachmentID.uuidString).\(DiaryMediaKind.video.defaultFileExtension)"
        let dest = fileURL(entryID: entryID, fileName: fileName)
        if FileManager.default.fileExists(atPath: dest.path) {
            try FileManager.default.removeItem(at: dest)
        }

        // Prefer a compressed MP4 under the soft duration cap when possible.
        let asset = AVURLAsset(url: sourceURL)
        let duration = try await asset.load(.duration).seconds
        if duration > DiaryMediaConfig.videoMaxDurationSeconds + 0.5 {
            throw MediaFileError.videoTooLong
        }

        if let exported = try await exportTrimmedMP4(asset: asset, to: dest) {
            let size = (try? FileManager.default.attributesOfItem(atPath: exported.path)[.size] as? NSNumber)?.intValue
            return DiaryMediaAttachment(
                id: attachmentID,
                kind: .video,
                localFileName: fileName,
                mimeType: DiaryMediaKind.video.defaultMIMEType,
                durationSeconds: min(duration, DiaryMediaConfig.videoMaxDurationSeconds),
                byteSize: size
            )
        }

        try FileManager.default.copyItem(at: sourceURL, to: dest)
        let size = (try? FileManager.default.attributesOfItem(atPath: dest.path)[.size] as? NSNumber)?.intValue
        return DiaryMediaAttachment(
            id: attachmentID,
            kind: .video,
            localFileName: fileName,
            mimeType: DiaryMediaKind.video.defaultMIMEType,
            durationSeconds: duration.isFinite ? duration : nil,
            byteSize: size
        )
    }

    static func writeDownloadedData(
        entryID: UUID,
        attachment: DiaryMediaAttachment,
        data: Data
    ) throws {
        try ensureEntryDirectory(entryID: entryID)
        let url = absoluteURL(entryID: entryID, attachment: attachment)
        try data.write(to: url, options: [.atomic])
    }

    // MARK: - Private

    private static func resizedImage(_ image: UIImage, maxPixel: CGFloat) -> UIImage {
        let size = image.size
        let longest = max(size.width, size.height)
        guard longest > maxPixel, longest > 0 else { return image }
        let scale = maxPixel / longest
        let newSize = CGSize(width: floor(size.width * scale), height: floor(size.height * scale))
        let renderer = UIGraphicsImageRenderer(size: newSize)
        return renderer.image { _ in
            image.draw(in: CGRect(origin: .zero, size: newSize))
        }
    }

    private static func exportTrimmedMP4(asset: AVURLAsset, to dest: URL) async throws -> URL? {
        guard let session = AVAssetExportSession(asset: asset, presetName: AVAssetExportPresetMediumQuality) else {
            return nil
        }
        let duration = try await asset.load(.duration)
        let end = min(duration.seconds, DiaryMediaConfig.videoMaxDurationSeconds)
        session.outputURL = dest
        session.outputFileType = .mp4
        session.shouldOptimizeForNetworkUse = true
        session.timeRange = CMTimeRange(
            start: .zero,
            duration: CMTime(seconds: end, preferredTimescale: 600)
        )
        await session.export()
        guard session.status == .completed else { return nil }
        return dest
    }
}

enum MediaFileError: LocalizedError {
    case encodeFailed
    case videoTooLong
    case missingFile

    var errorDescription: String? {
        switch self {
        case .encodeFailed: return "Could not encode the image."
        case .videoTooLong: return "Video is longer than the suggested limit."
        case .missingFile: return "Media file is missing on this device."
        }
    }
}
