import SwiftUI

struct DiaryDetailView: View {
    @EnvironmentObject private var diaryStore: DiaryStore
    @EnvironmentObject private var localization: LocalizationManager

    let entryID: UUID

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
        // Edit / Delete live on list swipe only — keep the system back button.
    }

    private func entryScroll(_ entry: DiaryEntry) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                // Meta band: date, then mood — clearly above title/body.
                Text(formattedDetailDate(entry.createdAt))
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(VaultTheme.seal)
                    .textCase(nil)

                readOnlyFields(entry)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 24)
            .padding(.top, 24)
            .padding(.bottom, 56)
        }
    }

    private func readOnlyFields(_ entry: DiaryEntry) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            if let mood = entry.moodTag, !mood.isEmpty {
                Text(mood)
                    .font(.system(size: 14, weight: .regular))
                    .foregroundStyle(VaultTheme.inkMuted)
                    .padding(.top, 12)
            }

            if !entry.title.isEmpty {
                Text(entry.title)
                    .font(.system(size: 30, weight: .semibold, design: .serif))
                    .foregroundStyle(VaultTheme.ink)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, entry.moodTag?.isEmpty == false ? 14 : 18)
            }

            // Hairline separates title/meta from body so identical short strings don't stack as peers.
            Rectangle()
                .fill(VaultTheme.line.opacity(0.65))
                .frame(height: 1)
                .padding(.top, entry.title.isEmpty && (entry.moodTag == nil || entry.moodTag?.isEmpty == true) ? 18 : 22)
                .padding(.bottom, 22)

            Text(entry.body.isEmpty ? localization.text("diary.emptyBody") : entry.body)
                .font(.system(size: 17, design: .serif))
                .foregroundStyle(entry.body.isEmpty ? VaultTheme.inkMuted : VaultTheme.ink)
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
}
