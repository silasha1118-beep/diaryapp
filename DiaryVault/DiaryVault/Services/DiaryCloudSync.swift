import Foundation
import FirebaseCore
import FirebaseAuth
import FirebaseFirestore

/// Firestore paths and field mapping for text diary sync (phase 1).
///
/// Schema (admin-readable later via Admin SDK; client rules restrict to own uid):
/// ```
/// users/{uid}                          // lightweight profile for admin listing
///   ownerUid, updatedAt, schemaVersion, hasTextEntries
/// users/{uid}/entries/{entryId}        // entryId = DiaryEntry.id.uuidString
///   id, title, body, moodTag?, createdAt, updatedAt,
///   ownerUid, schemaVersion, contentType ("text")
/// ```
/// Multimedia (DA-15) can add Storage paths later without changing this text shape.
enum DiaryFirestoreSchema {
    static let schemaVersion = 1
    static let contentTypeText = "text"

    static func userDocument(_ uid: String) -> DocumentReference {
        Firestore.firestore().collection("users").document(uid)
    }

    static func entriesCollection(_ uid: String) -> CollectionReference {
        userDocument(uid).collection("entries")
    }

    static func entryDocument(uid: String, entryID: UUID) -> DocumentReference {
        entriesCollection(uid).document(entryID.uuidString)
    }
}

/// Pushes / pulls text diary entries when Firebase Auth has a live user.
/// Local JSON remains source of truth offline; this layer best-effort syncs when online.
enum DiaryCloudSync {
    /// True only for real Firebase Auth sessions (not DEBUG local / stub synthetic ids).
    static var activeFirebaseUID: String? {
        FirebaseBootstrap.configureIfNeeded()
        guard FirebaseApp.app() != nil,
              let uid = Auth.auth().currentUser?.uid,
              !uid.isEmpty
        else { return nil }
        return uid
    }

    static func upsert(entry: DiaryEntry, uid: String) async throws {
        let ref = DiaryFirestoreSchema.entryDocument(uid: uid, entryID: entry.id)
        try await ref.setData(Self.encode(entry: entry, ownerUid: uid), merge: true)
        try await touchUserProfile(uid: uid)
    }

    static func delete(entryID: UUID, uid: String) async throws {
        try await DiaryFirestoreSchema.entryDocument(uid: uid, entryID: entryID).delete()
        try await touchUserProfile(uid: uid)
    }

    /// Deletes every entry under the user. Batch in chunks of 400.
    static func deleteAllEntries(uid: String) async throws {
        let snapshot = try await DiaryFirestoreSchema.entriesCollection(uid).getDocuments()
        let docs = snapshot.documents
        guard !docs.isEmpty else {
            try await DiaryFirestoreSchema.userDocument(uid).delete()
            return
        }
        let db = Firestore.firestore()
        for chunk in docs.chunked(into: 400) {
            let batch = db.batch()
            for doc in chunk {
                batch.deleteDocument(doc.reference)
            }
            try await batch.commit()
        }
        try await DiaryFirestoreSchema.userDocument(uid).delete()
    }

    static func fetchAllEntries(uid: String) async throws -> [DiaryEntry] {
        let snapshot = try await DiaryFirestoreSchema.entriesCollection(uid).getDocuments()
        return snapshot.documents.compactMap { Self.decode(document: $0) }
    }

    // MARK: - Encode / decode

    static func encode(entry: DiaryEntry, ownerUid: String) -> [String: Any] {
        var data: [String: Any] = [
            "id": entry.id.uuidString,
            "title": entry.title,
            "body": entry.body,
            "createdAt": Timestamp(date: entry.createdAt),
            "updatedAt": Timestamp(date: entry.updatedAt),
            "ownerUid": ownerUid,
            "schemaVersion": DiaryFirestoreSchema.schemaVersion,
            "contentType": DiaryFirestoreSchema.contentTypeText
        ]
        if let mood = entry.moodTag {
            data["moodTag"] = mood
        } else {
            data["moodTag"] = NSNull()
        }
        return data
    }

    static func decode(document: QueryDocumentSnapshot) -> DiaryEntry? {
        decode(data: document.data(), fallbackID: document.documentID)
    }

    static func decode(data: [String: Any], fallbackID: String) -> DiaryEntry? {
        let idString = (data["id"] as? String) ?? fallbackID
        guard let id = UUID(uuidString: idString) else { return nil }
        let title = data["title"] as? String ?? ""
        let body = data["body"] as? String ?? ""
        let moodTag: String?
        if data["moodTag"] is NSNull {
            moodTag = nil
        } else {
            moodTag = data["moodTag"] as? String
        }
        let createdAt = (data["createdAt"] as? Timestamp)?.dateValue() ?? Date()
        let updatedAt = (data["updatedAt"] as? Timestamp)?.dateValue() ?? createdAt
        return DiaryEntry(
            id: id,
            title: title,
            body: body,
            moodTag: moodTag,
            createdAt: createdAt,
            updatedAt: updatedAt
        )
    }

    private static func touchUserProfile(uid: String) async throws {
        try await DiaryFirestoreSchema.userDocument(uid).setData([
            "ownerUid": uid,
            "updatedAt": Timestamp(date: Date()),
            "schemaVersion": DiaryFirestoreSchema.schemaVersion,
            "hasTextEntries": true
        ], merge: true)
    }
}

private extension Array {
    func chunked(into size: Int) -> [[Element]] {
        guard size > 0 else { return [self] }
        var result: [[Element]] = []
        var index = startIndex
        while index < endIndex {
            let end = self.index(index, offsetBy: size, limitedBy: endIndex) ?? endIndex
            result.append(Array(self[index..<end]))
            index = end
        }
        return result
    }
}
