import SwiftUI

struct MirrorChatView: View {
    @EnvironmentObject private var diaryStore: DiaryStore
    @EnvironmentObject private var appState: AppState
    @EnvironmentObject private var localization: LocalizationManager
    @EnvironmentObject private var subscriptions: SubscriptionStore

    @State private var messages: [MirrorMessage] = []
    @State private var draft = ""
    @State private var isThinking = false
    @State private var errorText: String?
    @State private var showQuotaUpgrade = false
    @State private var showPaywall = false
    @State private var client: (any MirrorAIService) = MockMirrorAIClient()
    @State private var quotaTick = 0

    private var isAuthorPlus: Bool { subscriptions.isSubscribed }

    var body: some View {
        NavigationStack {
            ZStack {
                VaultBackground()

                VStack(spacing: 0) {
                    banner
                    if showQuotaBanner {
                        quotaBanner
                    }
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
                quotaTick &+= 1
            }
            .onChange(of: localization.language) { _, _ in
                if let first = messages.first, first.role == .system {
                    messages[0] = MirrorMessage(role: .system, text: localization.text("mirror.intro"))
                }
            }
            .onChange(of: subscriptions.isSubscribed) { _, _ in
                quotaTick &+= 1
                if !MirrorUsageGuard.isExhausted(isAuthorPlus: isAuthorPlus) {
                    showQuotaUpgrade = false
                    if errorText != nil {
                        errorText = nil
                    }
                }
            }
            .sheet(isPresented: $showPaywall) {
                PaywallView()
                    .environmentObject(localization)
                    .environmentObject(subscriptions)
            }
        }
    }

    private var showQuotaBanner: Bool {
        AIConfig.useCloudClient
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

    private var quotaBanner: some View {
        let _ = quotaTick
        let exhausted = MirrorUsageGuard.isExhausted(isAuthorPlus: isAuthorPlus)
        return HStack(alignment: .center, spacing: 10) {
            Image(systemName: exhausted ? "exclamationmark.circle" : "chart.bar")
                .font(.system(size: 13))
                .foregroundStyle(exhausted ? VaultTheme.seal : VaultTheme.inkMuted)

            Text(quotaStatusText(exhausted: exhausted))
                .font(.system(size: 12))
                .foregroundStyle(exhausted ? VaultTheme.seal : VaultTheme.inkMuted)
                .fixedSize(horizontal: false, vertical: true)

            Spacer(minLength: 8)

            if exhausted && !isAuthorPlus {
                Button {
                    showPaywall = true
                } label: {
                    Text(localization.text("mirror.quota.upgrade"))
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(VaultTheme.seal)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 8)
        .background(
            exhausted
                ? VaultTheme.seal.opacity(0.08)
                : VaultTheme.paper.opacity(0.35)
        )
    }

    private func quotaStatusText(exhausted: Bool) -> String {
        if let remaining = MirrorUsageGuard.remaining(isAuthorPlus: isAuthorPlus) {
            let limit = MirrorUsageGuard.effectiveLimit(isAuthorPlus: isAuthorPlus)
            if exhausted {
                return String(
                    format: localization.text(
                        isAuthorPlus ? "mirror.quota.exhausted.plus" : "mirror.quota.exhausted.free"
                    ),
                    limit
                )
            }
            return String(
                format: localization.text("mirror.quota.remaining"),
                remaining,
                limit
            )
        }
        return localization.text("mirror.quota.unlimited")
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
                        VStack(alignment: .leading, spacing: 8) {
                            Text(errorText)
                                .font(.system(size: 13))
                                .foregroundStyle(VaultTheme.seal)
                            if showQuotaUpgrade {
                                Button {
                                    showPaywall = true
                                } label: {
                                    Text(localization.text("mirror.quota.upgrade"))
                                        .font(.system(size: 13, weight: .semibold))
                                        .foregroundStyle(VaultTheme.seal)
                                }
                                .buttonStyle(.plain)
                            }
                        }
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
        showQuotaUpgrade = false
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
        } catch let error as MirrorAIError {
            switch error {
            case .rateLimited(let limit, let plus):
                errorText = String(
                    format: localization.text(plus ? "mirror.quota.exhausted.plus" : "mirror.quota.exhausted.free"),
                    limit
                )
                showQuotaUpgrade = !plus
            default:
                errorText = error.localizedDescription
            }
        } catch {
            errorText = error.localizedDescription
        }

        isThinking = false
        quotaTick &+= 1
    }
}
