import Foundation
import Combine

/// Local JSON persistence for diary entries. Cloud sync is out of scope for this scaffold.
@MainActor
final class DiaryStore: ObservableObject {
    @Published private(set) var entries: [DiaryEntry] = []

    private let fileURL: URL
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

    init(filename: String = "diary_entries.json") {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
        fileURL = docs.appendingPathComponent(filename)
        load()
        if entries.isEmpty {
            seedSampleIfNeeded()
        }
    }

    var sortedEntries: [DiaryEntry] {
        entries.sorted { $0.createdAt > $1.createdAt }
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
        save()
    }

    func update(_ entry: DiaryEntry) {
        guard let index = entries.firstIndex(where: { $0.id == entry.id }) else { return }
        var updated = entry
        updated.updatedAt = Date()
        entries[index] = updated
        save()
    }

    func delete(id: UUID) {
        entries.removeAll { $0.id == id }
        save()
    }

    /// Wipe every entry (account-deletion / leave-vault path). Does not touch auth session.
    func deleteAllEntries() {
        entries = []
        save()
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

    // MARK: - Persistence

    private func load() {
        guard FileManager.default.fileExists(atPath: fileURL.path) else { return }
        do {
            let data = try Data(contentsOf: fileURL)
            entries = try decoder.decode([DiaryEntry].self, from: data)
        } catch {
            entries = []
        }
    }

    private func save() {
        do {
            let data = try encoder.encode(entries)
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
