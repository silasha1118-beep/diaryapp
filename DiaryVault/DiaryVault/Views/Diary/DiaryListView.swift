import SwiftUI

struct DiaryListView: View {
    @EnvironmentObject private var diaryStore: DiaryStore
    @EnvironmentObject private var localization: LocalizationManager
    @EnvironmentObject private var appState: AppState

    @State private var showComposer = false

    private var dateFormatter: DateFormatter {
        let f = DateFormatter()
        f.locale = localization.locale
        f.dateStyle = .medium
        // Show time so same-day entries read as chronological, not unordered.
        f.timeStyle = .short
        return f
    }

    var body: some View {
        NavigationStack {
            ZStack {
                VaultBackground()

                if diaryStore.sortedEntries.isEmpty {
                    emptyState
                } else {
                    ScrollView {
                        LazyVStack(alignment: .leading, spacing: 0) {
                            header
                                .padding(.horizontal, 24)
                                .padding(.top, 8)
                                .padding(.bottom, 20)

                            ForEach(diaryStore.sortedEntries) { entry in
                                NavigationLink(value: entry) {
                                    entryRow(entry)
                                }
                                .buttonStyle(.plain)

                                Rectangle()
                                    .fill(VaultTheme.line.opacity(0.6))
                                    .frame(height: 1)
                                    .padding(.horizontal, 24)
                            }
                        }
                        .padding(.bottom, 80)
                    }
                }
            }
            .navigationDestination(for: DiaryEntry.self) { entry in
                DiaryDetailView(entryID: entry.id)
            }
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        showComposer = true
                    } label: {
                        Image(systemName: "square.and.pencil")
                            .foregroundStyle(VaultTheme.ink)
                    }
                    .accessibilityLabel(localization.text("diary.new"))
                }
            }
            .sheet(isPresented: $showComposer) {
                DiaryComposerView()
            }
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(localization.text("brand.name"))
                .font(.system(size: 32, weight: .semibold, design: .serif))
                .foregroundStyle(VaultTheme.ink)

            Text(
                appState.displayName.isEmpty
                    ? localization.text("diary.subtitle")
                    : String(format: localization.text("diary.subtitle.named"), appState.displayName)
            )
            .font(.system(size: 15))
            .foregroundStyle(VaultTheme.inkMuted)
        }
    }

    private func entryRow(_ entry: DiaryEntry) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(dateFormatter.string(from: entry.createdAt))
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(VaultTheme.seal)
                if let mood = entry.moodTag, !mood.isEmpty {
                    Text("· \(mood)")
                        .font(.system(size: 12))
                        .foregroundStyle(VaultTheme.inkMuted)
                }
                Spacer()
            }

            if !entry.title.isEmpty {
                Text(entry.title)
                    .font(.system(size: 18, weight: .semibold, design: .serif))
                    .foregroundStyle(VaultTheme.ink)
                    .lineLimit(1)
            }

            Text(entry.preview.isEmpty ? localization.text("diary.emptyBody") : entry.preview)
                .font(.system(size: 15))
                .foregroundStyle(VaultTheme.inkMuted)
                .lineLimit(3)
                .multilineTextAlignment(.leading)
        }
        .padding(.horizontal, 24)
        .padding(.vertical, 18)
        .contentShape(Rectangle())
    }

    private var emptyState: some View {
        VStack(spacing: 16) {
            Text(localization.text("diary.empty.title"))
                .font(.system(size: 22, weight: .semibold, design: .serif))
                .foregroundStyle(VaultTheme.ink)
            Text(localization.text("diary.empty.body"))
                .font(.system(size: 15))
                .foregroundStyle(VaultTheme.inkMuted)
                .multilineTextAlignment(.center)
            Button {
                showComposer = true
            } label: {
                Text(localization.text("diary.new"))
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(VaultTheme.paper)
                    .padding(.horizontal, 20)
                    .padding(.vertical, 10)
                    .background(VaultTheme.ink)
                    .clipShape(RoundedRectangle(cornerRadius: 4))
            }
            .buttonStyle(.plain)
        }
        .padding(32)
    }
}
