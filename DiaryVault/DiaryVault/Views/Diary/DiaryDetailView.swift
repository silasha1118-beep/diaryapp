import SwiftUI

struct DiaryDetailView: View {
    @EnvironmentObject private var diaryStore: DiaryStore
    @EnvironmentObject private var localization: LocalizationManager

    let entryID: UUID

    @State private var isEditing = false
    @State private var draftTitle = ""
    @State private var draftBody = ""
    @State private var draftMood = ""

    private var entry: DiaryEntry? { diaryStore.entry(id: entryID) }

    private var dateFormatter: DateFormatter {
        let f = DateFormatter()
        f.locale = localization.locale
        f.dateStyle = .long
        f.timeStyle = .short
        return f
    }

    var body: some View {
        ZStack {
            VaultBackground()

            if let entry {
                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        Text(dateFormatter.string(from: entry.createdAt))
                            .font(.system(size: 13, weight: .medium))
                            .foregroundStyle(VaultTheme.seal)

                        if isEditing {
                            TextField(localization.text("diary.field.title"), text: $draftTitle)
                                .font(.system(size: 26, weight: .semibold, design: .serif))
                            TextField(localization.text("diary.field.mood"), text: $draftMood)
                                .font(.system(size: 14))
                                .foregroundStyle(VaultTheme.inkMuted)
                            TextEditor(text: $draftBody)
                                .font(.system(size: 17, design: .serif))
                                .frame(minHeight: 220)
                                .scrollContentBackground(.hidden)
                        } else {
                            if !entry.title.isEmpty {
                                Text(entry.title)
                                    .font(.system(size: 26, weight: .semibold, design: .serif))
                                    .foregroundStyle(VaultTheme.ink)
                            }
                            if let mood = entry.moodTag, !mood.isEmpty {
                                Text(mood)
                                    .font(.system(size: 14))
                                    .foregroundStyle(VaultTheme.inkMuted)
                            }
                            Text(entry.body)
                                .font(.system(size: 17, design: .serif))
                                .foregroundStyle(VaultTheme.ink)
                                .lineSpacing(6)
                                .fixedSize(horizontal: false, vertical: true)
                        }

                        Text(localization.text("diary.detail.owned"))
                            .font(.system(size: 12))
                            .foregroundStyle(VaultTheme.inkMuted)
                            .padding(.top, 24)
                    }
                    .padding(24)
                }
            } else {
                Text(localization.text("diary.missing"))
                    .foregroundStyle(VaultTheme.inkMuted)
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if entry != nil {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(isEditing ? localization.text("common.save") : localization.text("common.edit")) {
                        if isEditing {
                            saveEdits()
                        } else if let entry {
                            draftTitle = entry.title
                            draftBody = entry.body
                            draftMood = entry.moodTag ?? ""
                            isEditing = true
                        }
                    }
                    .foregroundStyle(VaultTheme.ink)
                }
            }
        }
    }

    private func saveEdits() {
        guard var entry else { return }
        entry.title = draftTitle
        entry.body = draftBody
        entry.moodTag = draftMood.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? nil
            : draftMood
        diaryStore.update(entry)
        isEditing = false
    }
}
