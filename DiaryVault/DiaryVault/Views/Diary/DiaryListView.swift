import SwiftUI

struct DiaryListView: View {
    @EnvironmentObject private var diaryStore: DiaryStore
    @EnvironmentObject private var localization: LocalizationManager
    @EnvironmentObject private var appState: AppState
    @EnvironmentObject private var subscriptions: SubscriptionStore

    /// Item-based sheet so create/edit always receive a fresh session.
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
                DiaryComposerView(editingEntry: session.editingEntry)
                    .environmentObject(diaryStore)
                    .environmentObject(localization)
                    .environmentObject(subscriptions)
            }
        }
    }

    private var entryList: some View {
        List {
            Section {
                VaultBrandHeader(
                    title: localization.text("brand.name"),
                    subtitle: vaultSubtitle
                )
                .listRowInsets(EdgeInsets(top: 10, leading: 20, bottom: 14, trailing: 20))
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
                    .listRowSeparatorTint(VaultTheme.line.opacity(0.55))
                    .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                        // Soft-delete only on swipe — Edit lives on the detail screen.
                        Button(role: .destructive) {
                            diaryStore.delete(id: entry.id)
                        } label: {
                            Label(localization.text("common.delete"), systemImage: "trash")
                        }
                        .tint(VaultTheme.seal)
                    }
                    .accessibilityHint(localization.text("diary.swipe.hint"))
                }
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .background(Color.clear)
        .padding(.bottom, 24)
    }

    private func formattedEntryDate(_ date: Date) -> String {
        let f = DateFormatter()
        f.locale = localization.locale
        f.dateStyle = .medium
        f.timeStyle = .short
        return f.string(from: date)
    }

    /// Dense feed row: date·mood → title → preview → quiet media icons (no label clutter).
    private func entryRow(_ entry: DiaryEntry) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            HStack(spacing: 5) {
                Text(formattedEntryDate(entry.createdAt))
                    .font(.system(size: 11, weight: .medium))
                    .foregroundStyle(VaultTheme.seal)
                if let mood = entry.moodTag, !mood.isEmpty {
                    Text("·")
                        .font(.system(size: 11))
                        .foregroundStyle(VaultTheme.inkMuted.opacity(0.65))
                    Text(mood)
                        .font(.system(size: 11))
                        .foregroundStyle(VaultTheme.inkMuted)
                        .lineLimit(1)
                }
                if entry.hasMedia {
                    Spacer(minLength: 8)
                    HStack(spacing: 6) {
                        ForEach(entry.mediaKindsPresent, id: \.self) { kind in
                            Image(systemName: kind.systemImage)
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundStyle(VaultTheme.inkMuted.opacity(0.85))
                                .accessibilityLabel(localization.text(kind.localizationKey))
                        }
                    }
                } else {
                    Spacer(minLength: 0)
                }
            }

            if !entry.title.isEmpty {
                Text(entry.title)
                    .font(.system(size: 17, weight: .semibold, design: .serif))
                    .foregroundStyle(VaultTheme.ink)
                    .lineLimit(1)
            }

            if !entry.preview.isEmpty {
                Text(entry.preview)
                    .font(.system(size: 14))
                    .foregroundStyle(VaultTheme.inkMuted)
                    .lineLimit(2)
                    .lineSpacing(2)
                    .multilineTextAlignment(.leading)
            } else if entry.hasMedia && entry.title.isEmpty {
                Text(localization.text("diary.media.previewOnly"))
                    .font(.system(size: 14))
                    .foregroundStyle(VaultTheme.inkMuted)
                    .lineLimit(1)
            }
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 12)
        .contentShape(Rectangle())
    }

    private var emptyState: some View {
        VStack(alignment: .leading, spacing: 0) {
            VaultBrandHeader(
                title: localization.text("brand.name"),
                subtitle: vaultSubtitle
            )
            .padding(.horizontal, 20)
            .padding(.top, 16)

            Spacer(minLength: 20)

            VStack(spacing: 12) {
                Text(localization.text("diary.empty.title"))
                    .font(.system(size: 20, weight: .semibold, design: .serif))
                    .foregroundStyle(VaultTheme.ink)
                    .multilineTextAlignment(.center)
                Text(localization.text("diary.empty.body"))
                    .font(.system(size: 14))
                    .foregroundStyle(VaultTheme.inkMuted)
                    .multilineTextAlignment(.center)
                    .lineSpacing(3)
                Button {
                    composerSession = .create
                } label: {
                    Text(localization.text("diary.new"))
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(VaultTheme.paper)
                        .padding(.horizontal, 20)
                        .padding(.vertical, 10)
                        .background(VaultTheme.ink)
                        .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
                }
                .buttonStyle(.plain)
                .padding(.top, 2)
            }
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 32)

            Spacer(minLength: 32)
        }
    }
}
