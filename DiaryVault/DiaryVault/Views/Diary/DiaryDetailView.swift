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
            VStack(alignment: .leading, spacing: 0) {
                // Meta band: date, then mood — clearly above title/body.
                Text(formattedDetailDate(entry.createdAt))
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(VaultTheme.seal)
                    .textCase(nil)

                if isEditing {
                    editFields
                } else {
                    readOnlyFields(entry)
                }

                Text(localization.text("diary.detail.owned"))
                    .font(.system(size: 12))
                    .foregroundStyle(VaultTheme.inkMuted)
                    .padding(.top, 40)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 24)
            .padding(.top, 20)
            .padding(.bottom, 48)
        }
    }

    private var editFields: some View {
        VStack(alignment: .leading, spacing: 0) {
            TextField(localization.text("diary.field.mood"), text: $draftMood)
                .font(.system(size: 14, weight: .regular))
                .foregroundStyle(VaultTheme.inkMuted)
                .padding(.top, 10)

            TextField(localization.text("diary.field.title"), text: $draftTitle)
                .font(.system(size: 28, weight: .semibold, design: .serif))
                .foregroundStyle(VaultTheme.ink)
                .padding(.top, 20)

            Rectangle()
                .fill(VaultTheme.line.opacity(0.7))
                .frame(height: 1)
                .padding(.top, 20)
                .padding(.bottom, 20)

            TextEditor(text: $draftBody)
                .font(.system(size: 17, design: .serif))
                .foregroundStyle(VaultTheme.ink)
                .frame(minHeight: 220)
                .scrollContentBackground(.hidden)
                .padding(.horizontal, -4)
        }
    }

    private func readOnlyFields(_ entry: DiaryEntry) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            if let mood = entry.moodTag, !mood.isEmpty {
                Text(mood)
                    .font(.system(size: 14, weight: .regular))
                    .foregroundStyle(VaultTheme.inkMuted)
                    .padding(.top, 10)
            }

            if !entry.title.isEmpty {
                Text(entry.title)
                    .font(.system(size: 28, weight: .semibold, design: .serif))
                    .foregroundStyle(VaultTheme.ink)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, entry.moodTag?.isEmpty == false ? 16 : 20)
            }

            // Hairline separates title/meta from body so identical short strings don't stack as peers.
            Rectangle()
                .fill(VaultTheme.line.opacity(0.7))
                .frame(height: 1)
                .padding(.top, entry.title.isEmpty && (entry.moodTag == nil || entry.moodTag?.isEmpty == true) ? 16 : 20)
                .padding(.bottom, 20)

            Text(entry.body.isEmpty ? localization.text("diary.emptyBody") : entry.body)
                .font(.system(size: 17, design: .serif))
                .foregroundStyle(VaultTheme.ink)
                .lineSpacing(8)
                .multilineTextAlignment(.leading)
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
