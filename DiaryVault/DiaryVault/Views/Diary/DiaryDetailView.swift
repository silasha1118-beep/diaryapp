import SwiftUI

struct DiaryDetailView: View {
    @EnvironmentObject private var diaryStore: DiaryStore
    @EnvironmentObject private var localization: LocalizationManager
    @EnvironmentObject private var subscriptions: SubscriptionStore

    let entryID: UUID

    @State private var showEditor = false

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
            ToolbarItem(placement: .topBarTrailing) {
                if entry != nil {
                    Button(localization.text("common.edit")) {
                        showEditor = true
                    }
                    .foregroundStyle(VaultTheme.ink)
                }
            }
        }
        .sheet(isPresented: $showEditor) {
            if let entry = diaryStore.entry(id: entryID) {
                DiaryComposerView(editingEntry: entry)
                    .environmentObject(diaryStore)
                    .environmentObject(localization)
                    .environmentObject(subscriptions)
            }
        }
    }

    private func entryScroll(_ entry: DiaryEntry) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                // Compact meta: date · mood on one line (feed-style).
                HStack(spacing: 5) {
                    Text(formattedDetailDate(entry.createdAt))
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(VaultTheme.seal)
                    if let mood = entry.moodTag, !mood.isEmpty {
                        Text("·")
                            .font(.system(size: 12))
                            .foregroundStyle(VaultTheme.inkMuted.opacity(0.65))
                        Text(mood)
                            .font(.system(size: 12))
                            .foregroundStyle(VaultTheme.inkMuted)
                            .lineLimit(1)
                    }
                    Spacer(minLength: 0)
                }

                if !entry.title.isEmpty {
                    Text(entry.title)
                        .font(.system(size: 24, weight: .semibold, design: .serif))
                        .foregroundStyle(VaultTheme.ink)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.top, 10)
                }

                if entry.body.isEmpty && entry.hasMedia {
                    // Media-only — skip empty-body placeholder.
                } else {
                    Text(entry.body.isEmpty ? localization.text("diary.emptyBody") : entry.body)
                        .font(.system(size: 16, design: .serif))
                        .foregroundStyle(entry.body.isEmpty ? VaultTheme.inkMuted : VaultTheme.ink)
                        .lineSpacing(6)
                        .multilineTextAlignment(.leading)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.top, entry.title.isEmpty ? 10 : 12)
                }

                DiaryEntryMediaGallery(entry: entry, localization: localization)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 20)
            .padding(.top, 16)
            .padding(.bottom, 40)
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
