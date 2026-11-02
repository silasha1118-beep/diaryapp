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
                // Meta stays above all content sections.
                Text(formattedDetailDate(entry.createdAt))
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(VaultTheme.seal)

                if let mood = entry.moodTag, !mood.isEmpty {
                    Text(mood)
                        .font(.system(size: 14))
                        .foregroundStyle(VaultTheme.inkMuted)
                        .padding(.top, 8)
                }

                // Separated sections — text, then voice, photo, video (never mixed).
                textSection(entry)

                let voices = entry.attachments.filter { $0.kind == .voice }
                let photos = entry.attachments.filter { $0.kind == .image }
                let videos = entry.attachments.filter { $0.kind == .video }

                if !voices.isEmpty {
                    mediaSection(
                        titleKey: "diary.section.voice",
                        systemImage: DiaryMediaKind.voice.systemImage
                    ) {
                        VStack(alignment: .leading, spacing: 10) {
                            ForEach(voices) { item in
                                DiaryAudioAttachmentView(
                                    entryID: entry.id,
                                    attachment: item,
                                    localization: localization
                                )
                            }
                        }
                    }
                }

                if !photos.isEmpty {
                    mediaSection(
                        titleKey: "diary.section.photo",
                        systemImage: DiaryMediaKind.image.systemImage
                    ) {
                        VStack(alignment: .leading, spacing: 12) {
                            ForEach(photos) { item in
                                DiaryImageAttachmentView(entryID: entry.id, attachment: item)
                            }
                        }
                    }
                }

                if !videos.isEmpty {
                    mediaSection(
                        titleKey: "diary.section.video",
                        systemImage: DiaryMediaKind.video.systemImage
                    ) {
                        VStack(alignment: .leading, spacing: 12) {
                            ForEach(videos) { item in
                                DiaryVideoAttachmentView(
                                    entryID: entry.id,
                                    attachment: item,
                                    localization: localization
                                )
                            }
                        }
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 24)
            .padding(.top, 24)
            .padding(.bottom, 56)
        }
    }

    @ViewBuilder
    private func textSection(_ entry: DiaryEntry) -> some View {
        let hasTitle = !entry.title.isEmpty
        let hasBody = !entry.body.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        let showTextBlock = hasTitle || hasBody || !entry.hasMedia

        if showTextBlock {
            sectionChrome(titleKey: "diary.section.text", systemImage: "text.alignleft") {
                VStack(alignment: .leading, spacing: 12) {
                    if hasTitle {
                        Text(entry.title)
                            .font(.system(size: 26, weight: .semibold, design: .serif))
                            .foregroundStyle(VaultTheme.ink)
                            .fixedSize(horizontal: false, vertical: true)
                    }

                    if hasBody {
                        Text(entry.body)
                            .font(.system(size: 17, design: .serif))
                            .foregroundStyle(VaultTheme.ink)
                            .lineSpacing(7)
                            .multilineTextAlignment(.leading)
                            .fixedSize(horizontal: false, vertical: true)
                    } else if !entry.hasMedia {
                        Text(localization.text("diary.emptyBody"))
                            .font(.system(size: 17, design: .serif))
                            .foregroundStyle(VaultTheme.inkMuted)
                    }
                }
            }
        }
    }

    private func mediaSection<Content: View>(
        titleKey: String,
        systemImage: String,
        @ViewBuilder content: () -> Content
    ) -> some View {
        sectionChrome(titleKey: titleKey, systemImage: systemImage, content: content)
    }

    private func sectionChrome<Content: View>(
        titleKey: String,
        systemImage: String,
        @ViewBuilder content: () -> Content
    ) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Rectangle()
                .fill(VaultTheme.line.opacity(0.7))
                .frame(height: 1)
                .padding(.top, 22)

            HStack(spacing: 6) {
                Image(systemName: systemImage)
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(VaultTheme.seal)
                Text(localization.text(titleKey))
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(VaultTheme.seal)
                    .textCase(.uppercase)
                    .tracking(0.6)
                Spacer(minLength: 0)
            }

            content()
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
