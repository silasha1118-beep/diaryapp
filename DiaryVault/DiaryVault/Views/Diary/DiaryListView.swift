import SwiftUI

struct DiaryListView: View {
    @EnvironmentObject private var diaryStore: DiaryStore
    @EnvironmentObject private var localization: LocalizationManager
    @EnvironmentObject private var appState: AppState
    @EnvironmentObject private var subscriptions: SubscriptionStore

    /// Item-based sheet so Edit always receives the tapped entry (not a stale nil capture).
    @State private var composerSession: ComposerSession?

    private var vaultSubtitle: String {
        appState.displayName.isEmpty
            ? localization.text("diary.subtitle")
            : String(format: localization.text("diary.subtitle.named"), appState.displayName)
    }

    private enum ComposerSession: Identifiable {
        case create
        case edit(DiaryEntry)

        var id: String {
            switch self {
            case .create: return "create"
            case .edit(let entry): return entry.id.uuidString
            }
        }

        var editingEntry: DiaryEntry? {
            if case .edit(let entry) = self { return entry }
            return nil
        }
    }

    var body: some View {
        NavigationStack {
            ZStack {
                VaultBackground()

                if diaryStore.sortedEntries.isEmpty {
                    emptyState
                } else {
                    entryList
                }
            }
            .navigationDestination(for: DiaryEntry.self) { entry in
                DiaryDetailView(entryID: entry.id)
            }
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        composerSession = .create
                    } label: {
                        Image(systemName: "square.and.pencil")
                            .foregroundStyle(VaultTheme.ink)
                    }
                    .accessibilityLabel(localization.text("diary.new"))
                }
            }
            .sheet(item: $composerSession) { session in
                // Re-attach env objects — sheet inheritance is unreliable on some iOS builds.
                DiaryComposerView(editingEntry: session.editingEntry)
                    .environmentObject(diaryStore)
                    .environmentObject(localization)
                    .environmentObject(subscriptions)
            }
        }
    }

    private var entryList: some View {
        // List (not ScrollView) so `.swipeActions` works on rows.
        List {
            Section {
                VaultBrandHeader(
                    title: localization.text("brand.name"),
                    subtitle: vaultSubtitle
                )
                .listRowInsets(EdgeInsets(top: 14, leading: 24, bottom: 28, trailing: 24))
                .listRowBackground(Color.clear)
                .listRowSeparator(.hidden)
            }

            Section {
                ForEach(diaryStore.sortedEntries) { entry in
                    NavigationLink(value: entry) {
                        entryRow(entry)
                    }
                    .buttonStyle(.plain)
                    .navigationLinkIndicatorVisibility(.hidden)
                    .listRowInsets(EdgeInsets(top: 0, leading: 0, bottom: 0, trailing: 0))
                    .listRowBackground(Color.clear)
                    .listRowSeparatorTint(VaultTheme.line.opacity(0.6))
                    .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                        // Soft-delete immediately — no confirm dialog; entry stays archived forever.
                        Button(role: .destructive) {
                            diaryStore.delete(id: entry.id)
                        } label: {
                            Label(localization.text("common.delete"), systemImage: "trash")
                        }
                        .tint(VaultTheme.seal)

                        Button {
                            composerSession = .edit(entry)
                        } label: {
                            Label(localization.text("common.edit"), systemImage: "pencil")
                        }
                        .tint(VaultTheme.inkMuted)
                    }
                    .accessibilityHint(localization.text("diary.swipe.hint"))
                }
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .background(Color.clear)
        .padding(.bottom, 40)
    }

    private func formattedEntryDate(_ date: Date) -> String {
        let f = DateFormatter()
        f.locale = localization.locale
        f.dateStyle = .medium
        // Show time so same-day entries read as chronological, not unordered.
        f.timeStyle = .short
        return f.string(from: date)
    }

    private func entryRow(_ entry: DiaryEntry) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 6) {
                Text(formattedEntryDate(entry.createdAt))
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(VaultTheme.seal)
                if let mood = entry.moodTag, !mood.isEmpty {
                    Text("·")
                        .font(.system(size: 12))
                        .foregroundStyle(VaultTheme.inkMuted.opacity(0.7))
                    Text(mood)
                        .font(.system(size: 12))
                        .foregroundStyle(VaultTheme.inkMuted)
                }
                Spacer(minLength: 0)
            }

            if !entry.title.isEmpty {
                Text(entry.title)
                    .font(.system(size: 19, weight: .semibold, design: .serif))
                    .foregroundStyle(VaultTheme.ink)
                    .lineLimit(1)
            }

            if !entry.preview.isEmpty {
                Text(entry.preview)
                    .font(.system(size: 15))
                    .foregroundStyle(VaultTheme.inkMuted)
                    .lineLimit(3)
                    .lineSpacing(3)
                    .multilineTextAlignment(.leading)
            }
        }
        .padding(.horizontal, 24)
        .padding(.vertical, 20)
        .contentShape(Rectangle())
    }

    private var emptyState: some View {
        VStack(alignment: .leading, spacing: 0) {
            VaultBrandHeader(
                title: localization.text("brand.name"),
                subtitle: vaultSubtitle
            )
            .padding(.horizontal, 28)
            .padding(.top, 20)

            Spacer(minLength: 24)

            VStack(spacing: 16) {
                Text(localization.text("diary.empty.title"))
                    .font(.system(size: 22, weight: .semibold, design: .serif))
                    .foregroundStyle(VaultTheme.ink)
                    .multilineTextAlignment(.center)
                Text(localization.text("diary.empty.body"))
                    .font(.system(size: 15))
                    .foregroundStyle(VaultTheme.inkMuted)
                    .multilineTextAlignment(.center)
                    .lineSpacing(3)
                Button {
                    composerSession = .create
                } label: {
                    Text(localization.text("diary.new"))
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(VaultTheme.paper)
                        .padding(.horizontal, 22)
                        .padding(.vertical, 12)
                        .background(VaultTheme.ink)
                        .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
                }
                .buttonStyle(.plain)
                .padding(.top, 4)
            }
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 36)

            Spacer(minLength: 40)
        }
    }
}
