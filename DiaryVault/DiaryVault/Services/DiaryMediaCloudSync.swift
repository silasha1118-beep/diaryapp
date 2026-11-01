import Foundation
import FirebaseCore
import FirebaseAuth
import FirebaseStorage

/// Firebase Storage helpers for diary media under the signed-in user's uid path.
///
/// Object path: `users/{uid}/entries/{entryId}/{localFileName}`
/// Metadata stays on the Firestore entry doc (`attachments` array). Soft-delete
/// hides the entry; Storage objects are retained with the archive.
enum DiaryMediaCloudSync {
    static var isAvailable: Bool {
        FirebaseBootstrap.configureIfNeeded()
        return FirebaseApp.app() != nil && Auth.auth().currentUser != nil
    }

    static func storagePath(uid: String, entryID: UUID, fileName: String) -> String {
        "users/\(uid)/entries/\(entryID.uuidString)/\(fileName)"
    }

    /// Uploads any local attachment that is missing `remotePath`.
    /// Returns the attachments array with `remotePath` filled in.
    @discardableResult
    static func uploadPending(
        entry: DiaryEntry,
        uid: String
    ) async throws -> [DiaryMediaAttachment] {
        guard !entry.attachments.isEmpty else { return entry.attachments }
        FirebaseBootstrap.configureIfNeeded()
        let storage = Storage.storage()
        var updated = entry.attachments
        for index in updated.indices {
            var item = updated[index]
            let localURL = DiaryMediaFileStore.absoluteURL(entryID: entry.id, attachment: item)
            guard FileManager.default.fileExists(atPath: localURL.path) else { continue }
            let path = storagePath(uid: uid, entryID: entry.id, fileName: item.localFileName)
            if item.remotePath == path {
                continue
            }
            let ref = storage.reference().child(path)
            let metadata = StorageMetadata()
            metadata.contentType = item.mimeType
            try await putFile(ref: ref, from: localURL, metadata: metadata)
            item.remotePath = path
            if item.byteSize == nil {
                item.byteSize = (try? FileManager.default.attributesOfItem(atPath: localURL.path)[.size] as? NSNumber)?.intValue
            }
            updated[index] = item
        }
        return updated
    }

    /// Downloads a missing local file from Storage when `remotePath` is present.
    static func ensureLocalFile(entryID: UUID, attachment: DiaryMediaAttachment) async throws -> URL {
        let local = DiaryMediaFileStore.absoluteURL(entryID: entryID, attachment: attachment)
        if FileManager.default.fileExists(atPath: local.path) {
            return local
        }
        guard let remotePath = attachment.remotePath, !remotePath.isEmpty else {
            throw MediaFileError.missingFile
        }
        FirebaseBootstrap.configureIfNeeded()
        guard FirebaseApp.app() != nil else { throw MediaFileError.missingFile }
        let ref = Storage.storage().reference().child(remotePath)
        try DiaryMediaFileStore.ensureEntryDirectory(entryID: entryID)
        try await downloadFile(ref: ref, to: local)
        return local
    }

    private static func putFile(
        ref: StorageReference,
        from localURL: URL,
        metadata: StorageMetadata
    ) async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            ref.putFile(from: localURL, metadata: metadata) { _, error in
                if let error {
                    continuation.resume(throwing: error)
                } else {
                    continuation.resume()
                }
            }
        }
    }

    private static func downloadFile(ref: StorageReference, to localURL: URL) async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            ref.write(toFile: localURL) { _, error in
                if let error {
                    continuation.resume(throwing: error)
                } else {
                    continuation.resume()
                }
            }
        }
    }
}
