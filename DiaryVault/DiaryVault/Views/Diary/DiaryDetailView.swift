import SwiftUI

struct DiaryDetailView: View {
    @EnvironmentObject private var diaryStore: DiaryStore
    @EnvironmentObject private var localization: LocalizationManager
    @Environment(\.dismiss) private var dismiss

    let entryID: UUID

    @State private var isEditing = false
    @State private var draftTitle = ""
    @State private var draftBody = ""
    @State private var draftMood = ""
    @State private var showDeleteConfirm = false

    private var entry: DiaryEntry? { diaryStore.entry(id: entryID) }

    var body: some View {
        ZStack {
            VaultBackground()

            if let entry {
                entryScroll(entry)
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
                ToolbarItem(placement: .topBarTrailing) {
                    Button(role: .destructive) {
                        showDeleteConfirm = true
                    } label: {
                        Image(systemName: "trash")
                    }
                    .accessibilityLabel(localization.text("common.delete"))
                }
            }
        }
        .confirmationDialog(
            localization.text("diary.delete.title"),
            isPresented: $showDeleteConfirm,
            titleVisibility: .visible
        ) {
            Button(localization.text("common.delete"), role: .destructive) {
                diaryStore.delete(id: entryID)
                dismiss()
            }
            Button(localization.text("common.cancel"), role: .cancel) {}
        } message: {
            Text(localization.text("diary.delete.message"))
        }
    }

    private func entryScroll(_ entry: DiaryEntry) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text(formattedDetailDate(entry.createdAt))
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(VaultTheme.seal)

                if isEditing {
                    editFields
                } else {
                    readOnlyFields(entry)
                }

                Text(localization.text("diary.detail.owned"))
                    .font(.system(size: 12))
                    .foregroundStyle(VaultTheme.inkMuted)
                    .padding(.top, 24)
            }
            .padding(24)
        }
    }

    private var editFields: some View {
        Group {
            TextField(localization.text("diary.field.title"), text: $draftTitle)
                .font(.system(size: 26, weight: .semibold, design: .serif))
            TextField(localization.text("diary.field.mood"), text: $draftMood)
                .font(.system(size: 14))
                .foregroundStyle(VaultTheme.inkMuted)
            TextEditor(text: $draftBody)
                .font(.system(size: 17, design: .serif))
                .frame(minHeight: 220)
                .scrollContentBackground(.hidden)
        }
    }

    private func readOnlyFields(_ entry: DiaryEntry) -> some View {
        Group {
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
    }

    private func formattedDetailDate(_ date: Date) -> String {
        let f = DateFormatter()
        f.locale = localization.locale
        f.dateStyle = .long
        f.timeStyle = .short
        return f.string(from: date)
    }

    private func saveEdits() {
        // Do NOT `guard var entry` on the computed `entry` property — that triggers a
        // Swift compiler assertion (Querying VarDecl's type before type-checking parent stmt).
        guard let existing = diaryStore.entry(id: entryID) else { return }
        var updated = existing
        updated.title = draftTitle
        updated.body = draftBody
        let moodTrimmed = draftMood.trimmingCharacters(in: .whitespacesAndNewlines)
        updated.moodTag = moodTrimmed.isEmpty ? nil : moodTrimmed
        diaryStore.update(updated)
        isEditing = false
    }
}
