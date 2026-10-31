import Foundation
import FirebaseCore
import FirebaseAuth
import FirebaseFirestore

/// Firestore paths and field mapping for text diary sync (phase 1).
///
/// Schema (admin-readable later via Admin SDK; client rules restrict to own uid):
/// ```
/// users/{uid}                          // path key = Firebase Auth UID (never phone/Apple id)
///   ownerUid, updatedAt, schemaVersion, hasTextEntries,
///   phoneNumber?, appleUserId?, displayName?, authProviders?
/// users/{uid}/entries/{entryId}        // entryId = stable DiaryEntry UUID (never phone+datetime)
///   id, title, body, moodTag?, createdAt, updatedAt,
///   deleted, deletedAt?,                 // soft-delete; docs retained forever
///   ownerUid, schemaVersion, contentType ("text"),
///   label?, bodyPreview?               // Console browsing aids — not path keys
/// ```
/// Identity fields live on the user doc only (Console lookup by phoneNumber).
/// Entry document IDs stay UUIDs (local sync / privacy / phone changes). Use `label` in Console.
/// User deletes never call Firestore `delete()` — set `deleted` + `deletedAt` instead.
/// Multimedia (DA-15) can add Storage paths later without changing this text shape.
enum DiaryFirestoreSchema {
    static let schemaVersion = 1
    static let contentTypeText = "text"
    /// Always the Firebase `(default)` database — never a named DB.
    static let databaseID = "(default)"

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
    /// Prefer observing `AccountSession.firebaseAuthUID` for launch timing — Auth can restore
    /// asynchronously, so a one-shot `currentUser` read at startup may still be nil.
    static var activeFirebaseUID: String? {
        FirebaseBootstrap.configureIfNeeded()
        guard FirebaseApp.app() != nil,
              let uid = Auth.auth().currentUser?.uid,
              !uid.isEmpty
        else { return nil }
        return uid
    }

    /// Live `PROJECT_ID` from the bundled `GoogleService-Info.plist` (source of truth for Console).
    static var firebaseProjectID: String? {
        FirebaseBootstrap.configureIfNeeded()
        return FirebaseApp.app()?.options.projectID
    }

    static var firestoreDatabaseID: String { DiaryFirestoreSchema.databaseID }

    static func upsert(entry: DiaryEntry, uid: String) async throws {
        do {
            let ref = DiaryFirestoreSchema.entryDocument(uid: uid, entryID: entry.id)
            try await ref.setData(Self.encode(entry: entry, ownerUid: uid), merge: true)
            try await upsertUserProfile(uid: uid, hasTextEntries: true)
        } catch {
            throw mapFirestoreError(error)
        }
    }

    /// Soft-delete when the local row is gone but a legacy pending delete remains.
    /// Never calls Firestore `delete()` — retention is forever under `users/{uid}/entries`.
    /// Merge-only patch; existing title/body/createdAt satisfy rules `hasAll` on update.
    static func markDeleted(entryID: UUID, uid: String, at date: Date) async throws {
        do {
            let ref = DiaryFirestoreSchema.entryDocument(uid: uid, entryID: entryID)
            try await ref.setData([
                "deleted": true,
                "deletedAt": Timestamp(date: date),
                "updatedAt": Timestamp(date: date)
            ], merge: true)
            try await upsertUserProfile(uid: uid, hasTextEntries: true)
        } catch {
            throw mapFirestoreError(error)
        }
    }

    /// Creates/updates `users/{uid}` with Auth identity fields for Console lookup.
    /// Path and `ownerUid` always remain the Firebase Auth UID — never phone or Apple id.
    /// Call after Apple/SMS sign-in and on every cloud sync.
    static func upsertUserProfile(
        uid: String,
        displayName: String? = nil,
        hasTextEntries: Bool? = nil
    ) async throws {
        do {
            var data = Self.encodeUserProfile(
                uid: uid,
                authUser: Auth.auth().currentUser,
                displayNameOverride: displayName,
                hasTextEntries: hasTextEntries
            )
            // Always refresh updatedAt on upsert.
            data["updatedAt"] = Timestamp(date: Date())
            try await DiaryFirestoreSchema.userDocument(uid).setData(data, merge: true)
        } catch {
            throw mapFirestoreError(error)
        }
    }

    /// Builds the `users/{uid}` payload. Omits identity keys that are unavailable (merge keeps prior values).
    static func encodeUserProfile(
        uid: String,
        authUser: User?,
        displayNameOverride: String? = nil,
        hasTextEntries: Bool? = nil
    ) -> [String: Any] {
        var data: [String: Any] = [
            "ownerUid": uid,
            "schemaVersion": Int(DiaryFirestoreSchema.schemaVersion)
        ]
        if let hasTextEntries {
            data["hasTextEntries"] = hasTextEntries
        }

        guard let authUser, authUser.uid == uid else {
            // Still write ownerUid so Console can list the doc; identity fills in once Auth is live.
            if let name = displayNameOverride?.trimmingCharacters(in: .whitespacesAndNewlines), !name.isEmpty {
                data["displayName"] = name
            }
            return data
        }

        if let phone = authUser.phoneNumber?.trimmingCharacters(in: .whitespacesAndNewlines), !phone.isEmpty {
            data["phoneNumber"] = phone
        }

        if let apple = authUser.providerData.first(where: { $0.providerID == "apple.com" }) {
            let appleUID = apple.uid.trimmingCharacters(in: .whitespacesAndNewlines)
            if !appleUID.isEmpty {
                data["appleUserId"] = appleUID
            }
        }

        let overrideName = displayNameOverride?
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let authName = authUser.displayName?
            .trimmingCharacters(in: .whitespacesAndNewlines)
        if let overrideName, !overrideName.isEmpty {
            data["displayName"] = overrideName
        } else if let authName, !authName.isEmpty {
            data["displayName"] = authName
        }

        var providers: [String] = []
        for info in authUser.providerData {
            switch info.providerID {
            case "phone":
                if !providers.contains("phone") { providers.append("phone") }
            case "apple.com":
                if !providers.contains("apple") { providers.append("apple") }
            default:
                break
            }
        }
        if !providers.isEmpty {
            data["authProviders"] = providers
        }

        return data
    }

    /// Soft-deletes every entry under the user (account wipe). Docs stay forever.
    /// Does not remove the `users/{uid}` profile doc (entries remain addressable for admin).
    static func softDeleteAllEntries(uid: String) async throws {
        do {
            let snapshot = try await DiaryFirestoreSchema.entriesCollection(uid).getDocuments()
            let docs = snapshot.documents
            let now = Date()
            let db = Firestore.firestore()
            for chunk in docs.chunked(into: 400) {
                let batch = db.batch()
                for doc in chunk {
                    batch.setData([
                        "deleted": true,
                        "deletedAt": Timestamp(date: now),
                        "updatedAt": Timestamp(date: now)
                    ], forDocument: doc.reference, merge: true)
                }
                try await batch.commit()
            }
            try await upsertUserProfile(uid: uid, hasTextEntries: false)
        } catch {
            throw mapFirestoreError(error)
        }
    }

    /// Pull entries. Prefer `.server` so a stale local cache cannot pretend Console has data.
    /// Documents are returned newest-`createdAt`-first; never rely on document ID order (UUIDs).
    static func fetchAllEntries(uid: String, source: FirestoreSource = .server) async throws -> [DiaryEntry] {
        do {
            // Fetch the full collection (no orderBy) so docs missing/odd dates are not dropped,
            // then sort client-side by createdAt. Console default list is also by doc ID — sort there by createdAt.
            let snapshot = try await DiaryFirestoreSchema.entriesCollection(uid).getDocuments(source: source)
            return snapshot.documents
                .compactMap { Self.decode(document: $0) }
                .sorted(by: Self.chronologicalDescending)
        } catch {
            throw mapFirestoreError(error)
        }
    }

    /// Newest `createdAt` first; stable tie-break on `updatedAt` then id (never document-ID order).
    static func chronologicalDescending(_ lhs: DiaryEntry, _ rhs: DiaryEntry) -> Bool {
        if lhs.createdAt != rhs.createdAt {
            return lhs.createdAt > rhs.createdAt
        }
        if lhs.updatedAt != rhs.updatedAt {
            return lhs.updatedAt > rhs.updatedAt
        }
        return lhs.id.uuidString > rhs.id.uuidString
    }

    /// Document IDs currently acknowledged on the server (ignores local cache). Includes soft-deleted.
    static func fetchServerEntryIDs(uid: String) async throws -> Set<UUID> {
        let remote = try await fetchAllEntries(uid: uid, source: .server)
        return Set(remote.map(\.id))
    }

    /// Active (non-deleted) entry IDs on the server — used for Settings count / Vault parity.
    static func fetchActiveEntryIDs(uid: String) async throws -> Set<UUID> {
        let remote = try await fetchAllEntries(uid: uid, source: .server)
        return Set(remote.filter { !$0.deleted }.map(\.id))
    }

    /// Blocks until Firestore has acknowledged outstanding writes for this user (or fails).
    static func waitForPendingWrites() async throws {
        do {
            try await Firestore.firestore().waitForPendingWrites()
        } catch {
            throw mapFirestoreError(error)
        }
    }

    // MARK: - Encode / decode

    static func encode(entry: DiaryEntry, ownerUid: String) -> [String: Any] {
        // Always write Firebase Timestamp (not ISO strings / numbers) so Console can sort by createdAt.
        // Document ID remains the entry UUID — do not use phone+datetime as the path key.
        var data: [String: Any] = [
            "id": entry.id.uuidString,
            "title": entry.title,
            "body": entry.body,
            "createdAt": Timestamp(date: entry.createdAt),
            "updatedAt": Timestamp(date: entry.updatedAt),
            "ownerUid": ownerUid,
            // Explicit Int so rules `schemaVersion is int` never sees a Double bridge.
            "schemaVersion": Int(DiaryFirestoreSchema.schemaVersion),
            "contentType": DiaryFirestoreSchema.contentTypeText,
            // Non-key Console aids (UUID doc IDs are opaque in the list view).
            "label": consoleLabel(for: entry),
            "bodyPreview": bodyPreview(for: entry)
        ]
        // Omit nil moodTag (do not write NSNull — merge deletes the field and confuses rules debugging).
        if let mood = entry.moodTag {
            data["moodTag"] = mood
        }
        data["deleted"] = entry.deleted
        if entry.deleted, let deletedAt = entry.deletedAt {
            data["deletedAt"] = Timestamp(date: deletedAt)
        } else if entry.deleted {
            data["deletedAt"] = Timestamp(date: entry.updatedAt)
        }
        return data
    }

    /// Human-readable Console scan line: `yyyy-MM-dd HH:mm · title` (falls back to body snippet).
    static func consoleLabel(for entry: DiaryEntry) -> String {
        let stamp = ConsoleLabelDateFormatter.string(from: entry.createdAt)
        let title = entry.title.trimmingCharacters(in: .whitespacesAndNewlines)
        if !title.isEmpty {
            return "\(stamp) · \(title)"
        }
        let snippet = bodyPreview(for: entry, maxChars: 40)
        if !snippet.isEmpty {
            return "\(stamp) · \(snippet)"
        }
        return stamp
    }

    /// Short body excerpt for Console list scanning (full `body` remains on the doc).
    static func bodyPreview(for entry: DiaryEntry, maxChars: Int = 120) -> String {
        let trimmed = entry.body.trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "\n", with: " ")
        guard !trimmed.isEmpty else { return "" }
        if trimmed.count <= maxChars { return trimmed }
        return String(trimmed.prefix(maxChars)) + "…"
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
        // Prefer Timestamp; also accept Date / ISO8601 / epoch so a bad round-trip does not
        // collapse every entry to "now" and scramble Vault chronological order.
        let createdAt = dateValue(data["createdAt"]) ?? Date.distantPast
        let updatedAt = dateValue(data["updatedAt"]) ?? createdAt
        let deleted: Bool
        if let flag = data["deleted"] as? Bool {
            deleted = flag
        } else if let number = data["deleted"] as? NSNumber {
            deleted = number.boolValue
        } else {
            deleted = false
        }
        let deletedAt = dateValue(data["deletedAt"])
        return DiaryEntry(
            id: id,
            title: title,
            body: body,
            moodTag: moodTag,
            createdAt: createdAt,
            updatedAt: updatedAt,
            deleted: deleted,
            deletedAt: deletedAt
        )
    }

    /// Coerce Firestore / legacy field values into `Date`.
    static func dateValue(_ raw: Any?) -> Date? {
        guard let raw, !(raw is NSNull) else { return nil }
        if let ts = raw as? Timestamp {
            return ts.dateValue()
        }
        if let date = raw as? Date {
            return date
        }
        if let string = raw as? String {
            if let iso = ISO8601DateFormatter.diaryFractional.date(from: string)
                ?? ISO8601DateFormatter.diary.date(from: string) {
                return iso
            }
            return nil
        }
        if let number = raw as? NSNumber {
            let value = number.doubleValue
            // Heuristic: ≥ 1e12 → milliseconds since epoch; else seconds.
            if value > 1_000_000_000_000 {
                return Date(timeIntervalSince1970: value / 1000)
            }
            if value > 1_000_000_000 {
                return Date(timeIntervalSince1970: value)
            }
        }
        return nil
    }

    /// Maps Firestore / Auth failures into actionable copy (especially permission-denied).
    static func mapFirestoreError(_ error: Error) -> Error {
        let ns = error as NSError
        if ns.domain == FirestoreErrorDomain,
           ns.code == FirestoreErrorCode.permissionDenied.rawValue {
            return CloudSyncFailure.permissionDenied
        }
        if let mapped = error as? CloudSyncFailure {
            return mapped
        }
        return error
    }

}

enum CloudSyncFailure: LocalizedError, Equatable {
    case permissionDenied
    case serverMissingEntries(localCount: Int, serverCount: Int)
    case notSignedIn

    var errorDescription: String? {
        switch self {
        case .permissionDenied:
            return "Firestore permission denied. In Firebase Console → project matching Settings → Cloud sync → Project ID → Firestore → Rules, paste firebase/firestore.rules and Publish."
        case .serverMissingEntries(let localCount, let serverCount):
            return "Upload reported OK but server still has \(serverCount) entr\(serverCount == 1 ? "y" : "ies") while this device has \(localCount). Confirm Console project ID matches Settings, database is (default), then Sync now. Path: users/{uid}/entries."
        case .notSignedIn:
            return "No live Firebase Auth user — sign in with phone SMS or Apple first."
        }
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

private enum ConsoleLabelDateFormatter {
    static func string(from date: Date) -> String {
        formatter.string(from: date)
    }

    private static let formatter: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = TimeZone.current
        f.dateFormat = "yyyy-MM-dd HH:mm"
        return f
    }()
}

private extension ISO8601DateFormatter {
    static let diary: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime]
        return f
    }()

    static let diaryFractional: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return f
    }()
}
