import Foundation
import Combine

/// Local JSON persistence for diary entries (source of truth offline).
/// When Firebase Auth is signed in, text entries sync to Firestore in the background.
@MainActor
final class DiaryStore: ObservableObject {
    @Published private(set) var entries: [DiaryEntry] = []
    @Published private(set) var lastCloudSyncAt: Date?
    @Published private(set) var lastCloudSyncError: String?
    /// Entries confirmed present on the Firestore server after the last successful sync.
    @Published private(set) var lastCloudSyncServerCount: Int?
    /// `FirebaseApp.options.projectID` captured at last sync attempt (for Console matching).
    @Published private(set) var lastCloudSyncProjectID: String?

    private let fileURL: URL
    private let pendingURL: URL
    private let encoder: JSONEncoder = {
        let e = JSONEncoder()
        e.dateEncodingStrategy = .iso8601
        e.outputFormatting = [.prettyPrinted, .sortedKeys]
        return e
    }()
    private let decoder: JSONDecoder = {
        let d = JSONDecoder()
        d.dateDecodingStrategy = .iso8601
        return d
    }()

    private var pendingOps: [PendingCloudOp] = []
    private var debounceTask: Task<Void, Never>?
    private var isSyncing = false
    private var needsResync = false

    init(filename: String = "diary_entries.json") {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
        fileURL = docs.appendingPathComponent(filename)
        pendingURL = docs.appendingPathComponent("diary_cloud_pending.json")
        load()
        loadPending()
        if entries.isEmpty {
            seedSampleIfNeeded()
        }
    }

    /// Vault list order: newest `createdAt` first (never document-ID / insertion order).
    var sortedEntries: [DiaryEntry] {
        entries.sorted(by: DiaryCloudSync.chronologicalDescending)
    }

    /// Call once from the app root after `AccountSession` exists. Triggers pull+push when signed in.
    func bindCloudSync() {
        scheduleSynchronize()
    }

    /// Re-run sync after sign-in / Auth restore / returning online.
    /// Debounces rapid calls; never cancels an in-flight Firestore round-trip (uses `needsResync`).
    func scheduleSynchronize() {
        debounceTask?.cancel()
        debounceTask = Task { [weak self] in
            // Brief defer so Auth.currentUser is settled after OTP / Apple / keychain restore.
            try? await Task.sleep(nanoseconds: 250_000_000)
            guard !Task.isCancelled else { return }
            await self?.synchronizeWithCloud()
        }
    }

    func entry(id: UUID) -> DiaryEntry? {
        entries.first { $0.id == id }
    }

    func add(title: String, body: String, moodTag: String?) {
        let now = Date()
        let entry = DiaryEntry(
            title: title.trimmingCharacters(in: .whitespacesAndNewlines),
            body: body.trimmingCharacters(in: .whitespacesAndNewlines),
            moodTag: moodTag?.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty,
            createdAt: now,
            updatedAt: now
        )
        entries.insert(entry, at: 0)
        resortEntries()
        save()
        enqueue(.upsert(entry.id))
        scheduleSynchronize()
    }

    func update(_ entry: DiaryEntry) {
        guard let index = entries.firstIndex(where: { $0.id == entry.id }) else { return }
        var updated = entry
        updated.updatedAt = Date()
        entries[index] = updated
        resortEntries()
        save()
        enqueue(.upsert(updated.id))
        scheduleSynchronize()
    }

    func delete(id: UUID) {
        entries.removeAll { $0.id == id }
        save()
        enqueue(.delete(id))
        scheduleSynchronize()
    }

    /// Wipe every local entry. Optionally purge the signed-in user's Firestore entries first
    /// (account-deletion path — must run while Auth session is still live).
    func deleteAllEntries(purgeCloud: Bool = true) async {
        let uid = DiaryCloudSync.activeFirebaseUID
        entries = []
        save()
        pendingOps = []
        persistPending()

        if purgeCloud, let uid {
            do {
                try await DiaryCloudSync.deleteAllEntries(uid: uid)
                lastCloudSyncAt = Date()
                lastCloudSyncError = nil
            } catch {
                lastCloudSyncError = error.localizedDescription
                #if DEBUG
                print("DiaryCloudSync deleteAll failed: \(error)")
                #endif
            }
        }
    }

    /// JSON export for Share Sheet / Files — fulfills onboarding “export and leave” promise.
    func exportJSONData() throws -> Data {
        try encoder.encode(sortedEntries)
    }

    /// Writes a temp `.json` suitable for `ShareLink` / `UIActivityViewController`.
    func makeExportFileURL() throws -> URL {
        let data = try exportJSONData()
        let name = "DiaryVault-export-\(Self.exportStamp()).json"
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(name)
        try data.write(to: url, options: [.atomic])
        return url
    }

    private static func exportStamp() -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "yyyyMMdd-HHmmss"
        return f.string(from: Date())
    }

    // MARK: - Cloud sync

    /// Merge remote → local (LWW by `updatedAt`), then flush pending upserts/deletes.
    /// Push still runs if pull fails (so a rules/read glitch does not block uploads).
    /// Success requires a **server** read confirming local entry IDs exist (avoids “synced” when
    /// Console is empty because cache lied or writes never landed).
    func synchronizeWithCloud() async {
        if isSyncing {
            needsResync = true
            return
        }
        guard let uid = DiaryCloudSync.activeFirebaseUID else {
            // Signed-out / stub / local DEBUG / Auth not restored yet: keep vault local-only.
            return
        }

        isSyncing = true
        lastCloudSyncProjectID = DiaryCloudSync.firebaseProjectID
        defer {
            isSyncing = false
            if needsResync {
                needsResync = false
                scheduleSynchronize()
            }
        }

        // Upsert identity on every sync so Console can show phone / Apple even with zero entries.
        do {
            try await DiaryCloudSync.upsertUserProfile(
                uid: uid,
                hasTextEntries: !entries.isEmpty
            )
        } catch {
            #if DEBUG
            print("DiaryCloudSync user profile upsert failed: \(error)")
            #endif
        }

        var remote: [DiaryEntry] = []

        do {
            // Force server so we never skip uploads based on a stale local cache.
            remote = try await DiaryCloudSync.fetchAllEntries(uid: uid, source: .server)
            mergeRemote(remote)
        } catch is CancellationError {
            return
        } catch {
            #if DEBUG
            print("DiaryCloudSync pull failed: \(error)")
            #endif
            // Continue to push — verifyLocalsOnServer is the success gate.
        }

        do {
            try await flushPending(uid: uid)
            // Push any local-only entries that were never queued (e.g. pre-sign-in writing,
            // or creates that happened while Auth was still restoring).
            try await pushMissingLocals(uid: uid, remoteIDs: Set(remote.map(\.id)))
            try await DiaryCloudSync.waitForPendingWrites()

            let serverIDs = try await verifyLocalsOnServer(uid: uid)
            lastCloudSyncServerCount = serverIDs.count
            lastCloudSyncAt = Date()
            // Server verification is authoritative — never claim “synced” without it.
            lastCloudSyncError = nil
            #if DEBUG
            let project = DiaryCloudSync.firebaseProjectID ?? "?"
            print("DiaryCloudSync OK project=\(project) uid=\(uid) local=\(entries.count) server=\(serverIDs.count)")
            #endif
        } catch is CancellationError {
            return
        } catch {
            lastCloudSyncError = error.localizedDescription
            #if DEBUG
            print("DiaryCloudSync push/verify failed: \(error)")
            #endif
        }
    }

    /// Re-push any locals missing on the server, then confirm. Throws if Console would still look empty.
    @discardableResult
    private func verifyLocalsOnServer(uid: String) async throws -> Set<UUID> {
        let localIDs = Set(entries.map(\.id))
        guard !localIDs.isEmpty else {
            return try await DiaryCloudSync.fetchServerEntryIDs(uid: uid)
        }

        var serverIDs = try await DiaryCloudSync.fetchServerEntryIDs(uid: uid)
        let missing = localIDs.subtracting(serverIDs)
        if !missing.isEmpty {
            for entry in entries where missing.contains(entry.id) {
                try await DiaryCloudSync.upsert(entry: entry, uid: uid)
            }
            try await DiaryCloudSync.waitForPendingWrites()
            serverIDs = try await DiaryCloudSync.fetchServerEntryIDs(uid: uid)
        }

        let stillMissing = localIDs.subtracting(serverIDs)
        if !stillMissing.isEmpty {
            throw CloudSyncFailure.serverMissingEntries(
                localCount: localIDs.count,
                serverCount: serverIDs.count
            )
        }
        return serverIDs
    }

    private func mergeRemote(_ remote: [DiaryEntry]) {
        var changed = false
        for remoteEntry in remote {
            if let index = entries.firstIndex(where: { $0.id == remoteEntry.id }) {
                if remoteEntry.updatedAt > entries[index].updatedAt {
                    entries[index] = remoteEntry
                    changed = true
                }
            } else if !pendingOps.contains(where: {
                if case .delete(let id) = $0 { return id == remoteEntry.id }
                return false
            }) {
                entries.append(remoteEntry)
                changed = true
            }
        }
        if changed {
            resortEntries()
            save()
        }
    }

    /// Keep the in-memory array chronological so any consumer (and JSON on disk) matches Vault order.
    private func resortEntries() {
        entries.sort(by: DiaryCloudSync.chronologicalDescending)
    }

    private func flushPending(uid: String) async throws {
        guard !pendingOps.isEmpty else { return }
        var remaining: [PendingCloudOp] = []
        // Collapse by id: last op wins.
        var latest: [UUID: PendingCloudOp] = [:]
        for op in pendingOps {
            latest[op.entryID] = op
        }
        var firstError: Error?
        for op in latest.values {
            do {
                switch op {
                case .upsert(let id):
                    if let entry = entry(id: id) {
                        try await DiaryCloudSync.upsert(entry: entry, uid: uid)
                    }
                    // If already deleted locally, skip upsert.
                case .delete(let id):
                    try await DiaryCloudSync.delete(entryID: id, uid: uid)
                }
            } catch {
                remaining.append(op)
                if firstError == nil { firstError = error }
            }
        }
        pendingOps = remaining
        persistPending()
        if let firstError {
            throw firstError
        }
    }

    private func pushMissingLocals(uid: String, remoteIDs: Set<UUID>) async throws {
        for entry in entries where !remoteIDs.contains(entry.id) {
            try await DiaryCloudSync.upsert(entry: entry, uid: uid)
        }
    }

    // MARK: - Pending queue

    private enum PendingCloudOp: Codable, Equatable {
        case upsert(UUID)
        case delete(UUID)

        var entryID: UUID {
            switch self {
            case .upsert(let id), .delete(let id): return id
            }
        }
    }

    private func enqueue(_ op: PendingCloudOp) {
        // Signed-out / stub / DEBUG local / Auth not restored yet: local vault only.
        // Entries created before Auth restores are uploaded via `pushMissingLocals`
        // once `AccountSession.firebaseAuthUID` becomes available.
        guard DiaryCloudSync.activeFirebaseUID != nil else { return }
        // Drop earlier ops for the same id.
        pendingOps.removeAll { $0.entryID == op.entryID }
        pendingOps.append(op)
        persistPending()
    }

    private func loadPending() {
        guard FileManager.default.fileExists(atPath: pendingURL.path) else { return }
        do {
            let data = try Data(contentsOf: pendingURL)
            pendingOps = try decoder.decode([PendingCloudOp].self, from: data)
        } catch {
            pendingOps = []
        }
    }

    private func persistPending() {
        do {
            let data = try encoder.encode(pendingOps)
            try data.write(to: pendingURL, options: [.atomic])
        } catch {
            // Best-effort queue; local vault already saved.
        }
    }

    // MARK: - Persistence

    private func load() {
        guard FileManager.default.fileExists(atPath: fileURL.path) else { return }
        do {
            let data = try Data(contentsOf: fileURL)
            entries = try decoder.decode([DiaryEntry].self, from: data)
            resortEntries()
        } catch {
            entries = []
        }
    }

    private func save() {
        do {
            // Persist in Vault display order (createdAt newest-first), not Firestore doc-ID order.
            let data = try encoder.encode(sortedEntries)
            try data.write(to: fileURL, options: [.atomic])
        } catch {
            // MVP: silent fail; production would surface error UI.
        }
    }

    private func seedSampleIfNeeded() {
        let sample = DiaryEntry(
            title: "",
            body: "今天把舊筆記本翻出來，發現幾年前寫過的一句話還在。想把這裡當成自己的金庫，慢慢寫，慢慢被理解。",
            moodTag: "平靜",
            createdAt: Date().addingTimeInterval(-86_400),
            updatedAt: Date().addingTimeInterval(-86_400)
        )
        entries = [sample]
        save()
    }
}

private extension String {
    var nilIfEmpty: String? {
        let t = trimmingCharacters(in: .whitespacesAndNewlines)
        return t.isEmpty ? nil : t
    }
}
