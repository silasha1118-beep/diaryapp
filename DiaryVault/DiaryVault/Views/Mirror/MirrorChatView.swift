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

    private var billingTier: BillingConfig.Tier { subscriptions.tier }

    var body: some View {
        NavigationStack {
            ZStack {
                VaultBackground()

                VStack(spacing: 0) {
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
            .onChange(of: subscriptions.tier) { _, _ in
                quotaTick &+= 1
                if !MirrorUsageGuard.isExhausted(tier: billingTier) {
                    showQuotaUpgrade = false
                    if errorText != nil {
                        errorText = nil
                    }
                }
            }
            // Match Settings / Vault: full-screen Plans so prices + purchase are immediate.
            .fullScreenCover(isPresented: $showPaywall) {
                PaywallView()
                    .environmentObject(localization)
                    .environmentObject(subscriptions)
            }
        }
    }

    /// Show exhausted banner only (soft Free ladders removed — hard stop → paywall).
    private var showQuotaBanner: Bool {
        guard AIConfig.useCloudClient else { return false }
        return MirrorUsageGuard.isExhausted(tier: billingTier)
    }

    private var quotaBanner: some View {
        let _ = quotaTick
        let exhausted = MirrorUsageGuard.isExhausted(tier: billingTier)
        return HStack(alignment: .center, spacing: 10) {
            Image(systemName: exhausted ? "exclamationmark.circle" : "sparkles")
                .font(.system(size: 13))
                .foregroundStyle(exhausted ? VaultTheme.seal : VaultTheme.inkMuted)

            Text(quotaStatusText(exhausted: exhausted))
                .font(.system(size: 13))
                .foregroundStyle(exhausted ? VaultTheme.seal : VaultTheme.inkMuted)
                .fixedSize(horizontal: false, vertical: true)

            Spacer(minLength: 8)

            if shouldShowQuotaUpgradeCTA(exhausted: exhausted) {
                Button {
                    presentPaywall()
                } label: {
                    Text(localization.text(quotaUpgradeCTAKey))
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(VaultTheme.seal)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 12)
        .background(
            exhausted
                ? VaultTheme.seal.opacity(0.08)
                : VaultTheme.paperDeep.opacity(0.45)
        )
    }

    private func quotaStatusText(exhausted: Bool) -> String {
        if let remaining = MirrorUsageGuard.remaining(tier: billingTier) {
            let limit = MirrorUsageGuard.effectiveLimit(tier: billingTier)
            if exhausted {
                return String(
                    format: localization.text(exhaustedKey(for: billingTier)),
                    limit
                )
            }
            return String(
                format: localization.text(
                    billingTier == .free ? "mirror.quota.remaining.lifetime" : "mirror.quota.remaining"
                ),
                remaining,
                limit
            )
        }
        return localization.text("mirror.quota.unlimited")
    }

    private var messageList: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 18) {
                    ForEach(messages) { message in
                        bubble(message)
                            .id(message.id)
                    }
                    if isThinking {
                        Text(localization.text("mirror.thinking"))
                            .font(.system(size: 14, design: .serif))
                            .foregroundStyle(VaultTheme.inkMuted)
                            .italic()
                            .padding(.horizontal, 6)
                            .id("thinking")
                    }
                    if let errorText {
                        VStack(alignment: .leading, spacing: 10) {
                            Text(errorText)
                                .font(.system(size: 14))
                                .foregroundStyle(VaultTheme.seal)
                            if showQuotaUpgrade {
                                Button {
                                    presentPaywall()
                                } label: {
                                    Text(localization.text(quotaUpgradeCTAKey))
                                        .font(.system(size: 14, weight: .semibold))
                                        .foregroundStyle(VaultTheme.seal)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .id("error")
                    }
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 24)
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
            if message.role == .user { Spacer(minLength: 48) }

            VStack(alignment: message.role == .user ? .trailing : .leading, spacing: 8) {
                Text(message.text)
                    .font(.system(
                        size: message.role == .system ? 15 : 16,
                        design: message.role == .system ? .default : .serif
                    ))
                    .foregroundStyle(
                        message.role == .system ? VaultTheme.inkMuted : VaultTheme.ink
                    )
                    .lineSpacing(message.role == .system ? 4 : 5)
                    .padding(message.role == .system ? 4 : 16)
                    .background(background(for: message.role))
                    .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))

                if !message.citedEntryIDs.isEmpty {
                    Text(localization.text("mirror.cited"))
                        .font(.system(size: 12))
                        .foregroundStyle(VaultTheme.inkMuted)
                }
            }

            if message.role != .user { Spacer(minLength: 48) }
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
        HStack(alignment: .bottom, spacing: 12) {
            TextField(localization.text("mirror.placeholder"), text: $draft, axis: .vertical)
                .font(.system(size: 16, design: .serif))
                .lineLimit(1...5)
                .textFieldStyle(.plain)
                .padding(.horizontal, 14)
                .padding(.vertical, 12)
                .background(VaultTheme.paper)
                .overlay(
                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                        .stroke(VaultTheme.line.opacity(0.85), lineWidth: 1)
                )

            Button {
                Task { await send() }
            } label: {
                Image(systemName: "arrow.up.circle.fill")
                    .font(.system(size: 32))
                    .foregroundStyle(canSend ? VaultTheme.seal : VaultTheme.line)
            }
            .disabled(!canSend || isThinking)
            .accessibilityLabel(localization.text("mirror.send"))
        }
        .padding(.horizontal, 16)
        .padding(.top, 12)
        .padding(.bottom, 14)
        .background(VaultTheme.paperDeep.opacity(0.85))
    }

    private var canSend: Bool {
        !draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    @MainActor
    private func send() async {
        let text = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }

        // Hard-capped (Free lifetime / paid daily) — Plus also offers Author Pro CTA.
        if MirrorUsageGuard.isExhausted(tier: billingTier) {
            applyHardQuotaGate(tier: billingTier)
            return
        }

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
            case .rateLimited(let limit, let tier):
                applyHardQuotaGate(tier: tier, limit: limit)
            default:
                errorText = error.localizedDescription
            }
        } catch {
            errorText = error.localizedDescription
        }

        isThinking = false
        quotaTick &+= 1
    }

    private func presentPaywall() {
        showPaywall = true
    }

    /// Hard stop → show copy + open Plans immediately (same PaywallView as Settings).
    private func applyHardQuotaGate(tier: BillingConfig.Tier, limit: Int? = nil) {
        let resolvedLimit = limit ?? MirrorUsageGuard.effectiveLimit(tier: tier)
        errorText = String(
            format: localization.text(exhaustedKey(for: tier)),
            resolvedLimit
        )
        let canUpgrade = tier != .authorPro
        showQuotaUpgrade = canUpgrade
        if canUpgrade {
            presentPaywall()
        }
    }

    private func exhaustedKey(for tier: BillingConfig.Tier) -> String {
        switch tier {
        case .free: return "mirror.quota.exhausted.free"
        case .authorPlus: return "mirror.quota.exhausted.plus"
        case .authorPro: return "mirror.quota.exhausted.pro"
        }
    }

    private var quotaUpgradeCTAKey: String {
        switch billingTier {
        case .authorPlus:
            return "mirror.quota.upgrade.pro"
        case .free, .authorPro:
            return "mirror.quota.upgrade"
        }
    }

    private func shouldShowQuotaUpgradeCTA(exhausted: Bool) -> Bool {
        switch billingTier {
        case .authorPro:
            return false
        case .authorPlus, .free:
            return exhausted
        }
    }
}
