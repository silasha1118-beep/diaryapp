import SwiftUI

struct MirrorChatView: View {
    @EnvironmentObject private var diaryStore: DiaryStore
    @EnvironmentObject private var appState: AppState
    @EnvironmentObject private var localization: LocalizationManager
    @EnvironmentObject private var subscriptions: SubscriptionStore
    @EnvironmentObject private var account: AccountSession

    @State private var messages: [MirrorMessage] = []
    @State private var draft = ""
    @State private var isThinking = false
    @State private var errorText: String?
    @State private var showQuotaUpgrade = false
    @State private var showUpgradeCheckout = false
    @State private var client: (any MirrorAIService) = MockMirrorAIClient()
    @State private var quotaTick = 0
    @State private var didLoadPersisted = false
    @State private var mirrorAccountKey = MirrorChatStore.accountKey(userID: nil)

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
                reloadMessagesIfNeeded(force: !didLoadPersisted)
                quotaTick &+= 1
            }
            .onChange(of: account.userID) { _, _ in
                reloadMessagesIfNeeded(force: true)
            }
            .onChange(of: localization.language) { _, _ in
                if let first = messages.first, first.role == .system {
                    messages[0] = MirrorMessage(
                        id: first.id,
                        role: .system,
                        text: localization.text("mirror.intro"),
                        citedEntryIDs: first.citedEntryIDs,
                        createdAt: first.createdAt
                    )
                    persistMessages()
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
            .sheet(isPresented: $showUpgradeCheckout) {
                UpgradeCheckoutSheet(
                    preferredTier: billingTier == .authorPlus ? .authorPro : .authorPlus
                )
                .environmentObject(localization)
                .environmentObject(subscriptions)
            }
        }
    }

    /// Needs-key banner (Android parity) or exhausted quota (hard stop → paywall).
    private var showQuotaBanner: Bool {
        if !AIConfig.useCloudClient { return true }
        return MirrorUsageGuard.isExhausted(tier: billingTier)
    }

    private var quotaBanner: some View {
        let _ = quotaTick
        let cloudReady = AIConfig.useCloudClient
        let exhausted = cloudReady && MirrorUsageGuard.isExhausted(tier: billingTier)
        let alert = !cloudReady || exhausted
        return HStack(alignment: .center, spacing: 10) {
            Image(systemName: alert ? "exclamationmark.circle" : "sparkles")
                .font(.system(size: 13))
                .foregroundStyle(alert ? VaultTheme.seal : VaultTheme.inkMuted)

            Text(quotaStatusText(cloudReady: cloudReady, exhausted: exhausted))
                .font(.system(size: 13))
                .foregroundStyle(alert ? VaultTheme.seal : VaultTheme.inkMuted)
                .fixedSize(horizontal: false, vertical: true)

            Spacer(minLength: 8)

            if shouldShowQuotaUpgradeCTA(exhausted: exhausted) {
                Button {
                    presentUpgradeCheckout()
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
            alert
                ? VaultTheme.seal.opacity(0.08)
                : VaultTheme.paperDeep.opacity(0.45)
        )
    }

    private func quotaStatusText(cloudReady: Bool, exhausted: Bool) -> String {
        guard cloudReady else {
            return localization.text("mirror.mode.needsKey")
        }
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
            GeometryReader { geo in
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
                                        presentUpgradeCheckout()
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
                    // Fill viewport so blank chat chrome is hit-testable for dismiss.
                    .frame(maxWidth: .infinity, minHeight: geo.size.height, alignment: .top)
                    .contentShape(Rectangle())
                    // UIKit any-tap (cancelsTouchesInView = false) — keeps send / upgrade CTAs tappable.
                    .resignKeyboardOnAnyTap()
                }
                .scrollDismissesKeyboard(.immediately)
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
                // Citation caption removed (DA-170) — diary grounding stays in the model prompt only.
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

        // Android parity: never invent mock Mirror replies when cloud AI is not configured.
        guard AIConfig.useCloudClient else {
            errorText = localization.text("mirror.error.notConfigured")
            showQuotaUpgrade = false
            return
        }

        draft = ""
        errorText = nil
        showQuotaUpgrade = false
        // Free tab bar (首頁 / Vault) while Mirror awaits a reply.
        KeyboardDismiss.resign()
        messages.append(MirrorMessage(role: .user, text: text))
        persistMessages()
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
            persistMessages()
        } catch let error as MirrorAIError {
            switch error {
            case .rateLimited(let limit, let tier):
                applyHardQuotaGate(tier: tier, limit: limit)
            case .providerBusy:
                errorText = localization.text("mirror.error.busy")
                showQuotaUpgrade = false
            case .notConfigured:
                errorText = localization.text("mirror.error.notConfigured")
                showQuotaUpgrade = false
            case .emptyInput, .network, .decoding:
                // Never surface raw Cloud AI JSON / HTTP bodies in the chat error line.
                errorText = localization.text("mirror.error.generic")
                showQuotaUpgrade = false
            }
        } catch {
            errorText = localization.text("mirror.error.generic")
            showQuotaUpgrade = false
        }

        isThinking = false
        quotaTick &+= 1
    }

    private func reloadMessagesIfNeeded(force: Bool) {
        let key = MirrorChatStore.accountKey(userID: account.userID)
        if !force, didLoadPersisted, key == mirrorAccountKey { return }
        mirrorAccountKey = key
        let loaded = MirrorChatStore.load(accountKey: key)
        if loaded.isEmpty {
            messages = [
                MirrorMessage(role: .system, text: localization.text("mirror.intro"))
            ]
        } else {
            messages = loaded
            if let first = messages.first, first.role == .system {
                messages[0] = MirrorMessage(
                    id: first.id,
                    role: .system,
                    text: localization.text("mirror.intro"),
                    citedEntryIDs: first.citedEntryIDs,
                    createdAt: first.createdAt
                )
            }
        }
        didLoadPersisted = true
    }

    private func persistMessages() {
        MirrorChatStore.save(accountKey: mirrorAccountKey, messages: messages)
    }

    private func presentUpgradeCheckout() {
        KeyboardDismiss.resign()
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 80_000_000)
            showUpgradeCheckout = true
        }
    }

    /// Hard stop → show copy + upgrade CTA (compact Plus/Pro period sheet on tap).
    private func applyHardQuotaGate(tier: BillingConfig.Tier, limit: Int? = nil) {
        let resolvedLimit = limit ?? MirrorUsageGuard.effectiveLimit(tier: tier)
        errorText = String(
            format: localization.text(exhaustedKey(for: tier)),
            resolvedLimit
        )
        showQuotaUpgrade = tier != .authorPro
        KeyboardDismiss.resign()
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
