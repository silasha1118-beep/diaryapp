import SwiftUI

struct MirrorChatView: View {
    @EnvironmentObject private var diaryStore: DiaryStore
    @EnvironmentObject private var appState: AppState
    @EnvironmentObject private var localization: LocalizationManager

    @State private var messages: [MirrorMessage] = []
    @State private var draft = ""
    @State private var isThinking = false
    @State private var errorText: String?
    @State private var client: (any MirrorAIService) = MockMirrorAIClient()

    var body: some View {
        NavigationStack {
            ZStack {
                VaultBackground()

                VStack(spacing: 0) {
                    banner
                    messageList
                    composer
                }
            }
            .navigationTitle(localization.text("mirror.title"))
            .navigationBarTitleDisplayMode(.inline)
            .onAppear {
                client = MirrorAIClientFactory.make()
                if messages.isEmpty {
                    messages = [
                        MirrorMessage(
                            role: .system,
                            text: localization.text("mirror.intro")
                        )
                    ]
                }
            }
            .onChange(of: localization.language) { _, _ in
                if let first = messages.first, first.role == .system {
                    messages[0] = MirrorMessage(role: .system, text: localization.text("mirror.intro"))
                }
            }
        }
    }

    private var banner: some View {
        HStack(spacing: 8) {
            Image(systemName: AIConfig.useCloudClient ? "cloud" : "circle.lefthalf.filled")
                .font(.system(size: 12))
            Text(
                AIConfig.useCloudClient
                    ? localization.text("mirror.mode.cloud")
                    : localization.text("mirror.mode.mock")
            )
            .font(.system(size: 12))
            Spacer()
            Text(localization.text("mirror.style.hint"))
                .font(.system(size: 11))
                .foregroundStyle(VaultTheme.inkMuted)
        }
        .foregroundStyle(VaultTheme.inkMuted)
        .padding(.horizontal, 20)
        .padding(.vertical, 10)
        .background(VaultTheme.paperDeep.opacity(0.55))
    }

    private var messageList: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 14) {
                    ForEach(messages) { message in
                        bubble(message)
                            .id(message.id)
                    }
                    if isThinking {
                        Text(localization.text("mirror.thinking"))
                            .font(.system(size: 13, design: .serif))
                            .foregroundStyle(VaultTheme.inkMuted)
                            .italic()
                            .padding(.horizontal, 4)
                            .id("thinking")
                    }
                    if let errorText {
                        Text(errorText)
                            .font(.system(size: 13))
                            .foregroundStyle(VaultTheme.seal)
                            .id("error")
                    }
                }
                .padding(20)
            }
            .onChange(of: messages.count) { _, _ in
                if let last = messages.last {
                    withAnimation { proxy.scrollTo(last.id, anchor: .bottom) }
                }
            }
        }
    }

    private func bubble(_ message: MirrorMessage) -> some View {
        HStack {
            if message.role == .user { Spacer(minLength: 40) }

            VStack(alignment: message.role == .user ? .trailing : .leading, spacing: 6) {
                Text(message.text)
                    .font(.system(size: 15, design: message.role == .system ? .default : .serif))
                    .foregroundStyle(VaultTheme.ink)
                    .lineSpacing(3)
                    .padding(14)
                    .background(background(for: message.role))
                    .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))

                if !message.citedEntryIDs.isEmpty {
                    Text(localization.text("mirror.cited"))
                        .font(.system(size: 11))
                        .foregroundStyle(VaultTheme.inkMuted)
                }
            }

            if message.role != .user { Spacer(minLength: 40) }
        }
    }

    private func background(for role: MirrorRole) -> Color {
        switch role {
        case .user: return VaultTheme.userBubble
        case .mirror: return VaultTheme.mirrorBubble
        case .system: return Color.clear
        }
    }

    private var composer: some View {
        HStack(alignment: .bottom, spacing: 10) {
            TextField(localization.text("mirror.placeholder"), text: $draft, axis: .vertical)
                .lineLimit(1...5)
                .textFieldStyle(.plain)
                .padding(12)
                .background(VaultTheme.paper)
                .overlay(
                    RoundedRectangle(cornerRadius: 6)
                        .stroke(VaultTheme.line, lineWidth: 1)
                )

            Button {
                Task { await send() }
            } label: {
                Image(systemName: "arrow.up.circle.fill")
                    .font(.system(size: 30))
                    .foregroundStyle(canSend ? VaultTheme.seal : VaultTheme.line)
            }
            .disabled(!canSend || isThinking)
            .accessibilityLabel(localization.text("mirror.send"))
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(VaultTheme.paperDeep.opacity(0.8))
    }

    private var canSend: Bool {
        !draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    @MainActor
    private func send() async {
        let text = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }

        draft = ""
        errorText = nil
        messages.append(MirrorMessage(role: .user, text: text))
        isThinking = true

        let context = MirrorContext(
            userDisplayName: appState.displayName,
            recentEntries: diaryStore.sortedEntries,
            conversation: messages,
            localeIdentifier: localization.language.rawValue
        )

        do {
            let reply = try await client.reply(to: text, context: context)
            messages.append(reply)
        } catch {
            errorText = error.localizedDescription
        }

        isThinking = false
    }
}
