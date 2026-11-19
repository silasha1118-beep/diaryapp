import Foundation

/// Local JSON persistence for Mirror chat bubbles (device-only; no cloud Mirror transcript).
/// Scoped per account key so switching accounts does not leak prior threads.
/// Android parity: `MirrorChatStore` under `filesDir/mirror_chat_*.json`.
enum MirrorChatStore {
    private static let maxMessages = 200
    private static let anonymousKey = "anonymous"

    private static let encoder: JSONEncoder = {
        let e = JSONEncoder()
        e.dateEncodingStrategy = .iso8601
        return e
    }()

    private static let decoder: JSONDecoder = {
        let d = JSONDecoder()
        d.dateDecodingStrategy = .iso8601
        return d
    }()

    static func accountKey(userID: String?) -> String {
        guard let userID, !userID.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return anonymousKey
        }
        return userID
    }

    static func load(accountKey: String) -> [MirrorMessage] {
        let url = fileURL(for: accountKey)
        guard FileManager.default.fileExists(atPath: url.path) else { return [] }
        do {
            let data = try Data(contentsOf: url)
            // Drop legacy cited flags so a stale binary / reintroduced caption cannot reappear.
            return try decoder.decode([MirrorMessage].self, from: data).map { message in
                guard !message.citedEntryIDs.isEmpty else { return message }
                return MirrorMessage(
                    id: message.id,
                    role: message.role,
                    text: message.text,
                    citedEntryIDs: [],
                    createdAt: message.createdAt
                )
            }
        } catch {
            return []
        }
    }

    static func save(accountKey: String, messages: [MirrorMessage]) {
        let cleared = messages.map { message -> MirrorMessage in
            guard !message.citedEntryIDs.isEmpty else { return message }
            return MirrorMessage(
                id: message.id,
                role: message.role,
                text: message.text,
                citedEntryIDs: [],
                createdAt: message.createdAt
            )
        }
        let trimmed = cleared.count > maxMessages
            ? Array(cleared.suffix(maxMessages))
            : cleared
        let url = fileURL(for: accountKey)
        do {
            try FileManager.default.createDirectory(
                at: url.deletingLastPathComponent(),
                withIntermediateDirectories: true
            )
            let data = try encoder.encode(trimmed)
            try data.write(to: url, options: [.atomic])
        } catch {
            // Best-effort local cache; chat still works in memory.
        }
    }

    static func clear(accountKey: String) {
        let url = fileURL(for: accountKey)
        try? FileManager.default.removeItem(at: url)
    }

    private static func fileURL(for accountKey: String) -> URL {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
        let safe = accountKey.replacingOccurrences(
            of: "[^A-Za-z0-9._-]",
            with: "_",
            options: .regularExpression
        )
        let key = safe.isEmpty ? anonymousKey : safe
        return docs.appendingPathComponent("mirror_chat_\(key).json")
    }
}
