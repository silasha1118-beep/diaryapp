import SwiftUI

struct DiaryComposerView: View {
    @EnvironmentObject private var diaryStore: DiaryStore
    @EnvironmentObject private var localization: LocalizationManager
    @EnvironmentObject private var subscriptions: SubscriptionStore
    @Environment(\.dismiss) private var dismiss

    /// When non-nil, composer updates this entry instead of creating a new one (no upload quota).
    var editingEntry: DiaryEntry? = nil

    @State private var title = ""
    @State private var bodyText = ""
    @State private var mood = ""
    @State private var softNudge: SoftQuotaNudge?
    @State private var hardGate: HardQuotaGate?
    @State private var showPaywall = false
    /// After the user taps Continue on the inline soft reminder, allow one save without re-nudge.
    @State private var suppressPaywallNagOnce = false
    @State private var didLoadEditDraft = false

    private var isEditing: Bool { editingEntry != nil }

    private var billingTier: BillingConfig.Tier { subscriptions.tier }

    private var canSave: Bool {
        !bodyText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    var body: some View {
        NavigationStack {
            ZStack {
                VaultTheme.paper.ignoresSafeArea()
                formContent
            }
            .navigationTitle(
                localization.text(isEditing ? "diary.edit" : "diary.new")
            )
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(localization.text("common.cancel")) { dismiss() }
                        .foregroundStyle(VaultTheme.inkMuted)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(localization.text("common.save")) {
                        attemptSave()
                    }
                    .disabled(!canSave)
                    .fontWeight(.semibold)
                    .foregroundStyle(canSave ? VaultTheme.seal : VaultTheme.inkMuted)
                }
            }
            .safeAreaInset(edge: .bottom, spacing: 0) {
                if let softNudge {
                    softNudgeBanner(softNudge)
                } else if let hardGate {
                    hardQuotaBanner(hardGate)
                }
            }
            .onAppear {
                loadEditDraftIfNeeded()
            }
            .onChange(of: subscriptions.tier) { _, _ in
                clearQuotaMessagesIfUnderCap()
            }
            // fullScreenCover — not nested `.sheet` — so Free soft paywall is visible from the composer sheet.
            .fullScreenCover(isPresented: $showPaywall) {
                PaywallView()
                    .environmentObject(localization)
                    .environmentObject(subscriptions)
            }
        }
    }

    // MARK: - Split views (keeps the type checker happy)

    private var formContent: some View {
        VStack(alignment: .leading, spacing: 16) {
            TextField(localization.text("diary.field.title"), text: $title)
                .font(.system(size: 22, weight: .semibold, design: .serif))
                .foregroundStyle(VaultTheme.ink)

            TextField(localization.text("diary.field.mood"), text: $mood)
                .font(.system(size: 14))
                .foregroundStyle(VaultTheme.inkMuted)

            Rectangle()
                .fill(VaultTheme.line)
                .frame(height: 1)

            TextEditor(text: $bodyText)
                .font(.system(size: 17, design: .serif))
                .foregroundStyle(VaultTheme.ink)
                .scrollContentBackground(.hidden)
                .background(Color.clear)

            Spacer(minLength: 0)
        }
        .padding(20)
    }

    // MARK: - Soft nudge (purchase-first, benefit copy)

    private func softNudgeBanner(_ nudge: SoftQuotaNudge) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(alignment: .top, spacing: 12) {
                quotaAccentIcon(systemName: "book.closed.fill")

                VStack(alignment: .leading, spacing: 6) {
                    Text(localization.text("diary.quota.soft.title"))
                        .font(.system(size: 17, weight: .semibold, design: .serif))
                        .foregroundStyle(VaultTheme.ink)
                        .fixedSize(horizontal: false, vertical: true)

                    Text(localization.text("diary.quota.soft.benefit"))
                        .font(.system(size: 13))
                        .foregroundStyle(VaultTheme.inkMuted)
                        .lineSpacing(3)
                        .fixedSize(horizontal: false, vertical: true)

                    Text(
                        String(
                            format: localization.text("diary.quota.soft.meta"),
                            nudge.remaining,
                            nudge.limit
                        )
                    )
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(VaultTheme.inkMuted.opacity(0.9))
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }

            VStack(spacing: 8) {
                Button {
                    showPaywall = true
                } label: {
                    Text(localization.text(quotaUpgradeCTAKey))
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(VaultTheme.paper)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 13)
                        .background(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .fill(VaultTheme.seal)
                        )
                }
                .buttonStyle(.plain)
                .accessibilityLabel(localization.text(quotaUpgradeCTAKey))

                Button {
                    suppressPaywallNagOnce = true
                    softNudge = nil
                    attemptSave()
                } label: {
                    Text(localization.text("diary.quota.soft.continue"))
                        .font(.system(size: 14, weight: .medium))
                        .foregroundStyle(VaultTheme.inkMuted)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 11)
                        .background(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .fill(VaultTheme.paper.opacity(0.55))
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .stroke(VaultTheme.line.opacity(0.9), lineWidth: 1)
                        )
                }
                .buttonStyle(.plain)
                .accessibilityLabel(localization.text("diary.quota.soft.continue"))
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 18)
        .padding(.bottom, 14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background { quotaBannerChrome }
    }

    // MARK: - Hard gate (premium stop, still purchase-oriented)

    private func hardQuotaBanner(_ gate: HardQuotaGate) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(alignment: .top, spacing: 12) {
                quotaAccentIcon(systemName: gate.showUpgrade ? "lock.open.fill" : "moon.stars.fill")

                VStack(alignment: .leading, spacing: 6) {
                    Text(localization.text(hardTitleKey(for: gate.tier)))
                        .font(.system(size: 17, weight: .semibold, design: .serif))
                        .foregroundStyle(VaultTheme.ink)
                        .fixedSize(horizontal: false, vertical: true)

                    Text(localization.text(hardBenefitKey(for: gate.tier)))
                        .font(.system(size: 13))
                        .foregroundStyle(VaultTheme.inkMuted)
                        .lineSpacing(3)
                        .fixedSize(horizontal: false, vertical: true)

                    Text(
                        String(
                            format: localization.text(hardMetaKey(for: gate.tier)),
                            gate.limit
                        )
                    )
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(VaultTheme.inkMuted.opacity(0.9))
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }

            if gate.showUpgrade {
                Button {
                    showPaywall = true
                } label: {
                    Text(localization.text(quotaUpgradeCTAKey))
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(VaultTheme.paper)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 13)
                        .background(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .fill(VaultTheme.seal)
                        )
                }
                .buttonStyle(.plain)
                .accessibilityLabel(localization.text(quotaUpgradeCTAKey))
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 18)
        .padding(.bottom, 14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background { quotaBannerChrome }
    }

    private func quotaAccentIcon(systemName: String) -> some View {
        ZStack {
            Circle()
                .fill(VaultTheme.seal.opacity(0.12))
                .frame(width: 36, height: 36)
            Image(systemName: systemName)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(VaultTheme.seal)
                .accessibilityHidden(true)
        }
        .padding(.top, 1)
    }

    private var quotaBannerChrome: some View {
        VStack(spacing: 0) {
            Rectangle()
                .fill(VaultTheme.line.opacity(0.7))
                .frame(height: 1)
            ZStack {
                VaultTheme.paperDeep
                // Quiet warm wash — purchase cue without glow / purple.
                LinearGradient(
                    colors: [
                        VaultTheme.seal.opacity(0.07),
                        VaultTheme.paperDeep.opacity(0),
                    ],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
            }
        }
        .ignoresSafeArea(edges: .bottom)
    }

    private var quotaUpgradeCTAKey: String {
        switch billingTier {
        case .authorPlus:
            return "diary.quota.upgrade.pro"
        case .free, .authorPro:
            return "diary.quota.upgrade"
        }
    }

    private func hardTitleKey(for tier: BillingConfig.Tier) -> String {
        switch tier {
        case .free: return "diary.quota.hard.title.free"
        case .authorPlus: return "diary.quota.hard.title.plus"
        case .authorPro: return "diary.quota.hard.title.pro"
        }
    }

    private func hardBenefitKey(for tier: BillingConfig.Tier) -> String {
        switch tier {
        case .free: return "diary.quota.hard.benefit.free"
        case .authorPlus: return "diary.quota.hard.benefit.plus"
        case .authorPro: return "diary.quota.hard.benefit.pro"
        }
    }

    private func hardMetaKey(for tier: BillingConfig.Tier) -> String {
        switch tier {
        case .free: return "diary.quota.hard.meta.free"
        case .authorPlus: return "diary.quota.hard.meta.plus"
        case .authorPro: return "diary.quota.hard.meta.pro"
        }
    }

    private func loadEditDraftIfNeeded() {
        guard !didLoadEditDraft, let editingEntry else { return }
        didLoadEditDraft = true
        title = editingEntry.title
        bodyText = editingEntry.body
        mood = editingEntry.moodTag ?? ""
    }

    private func clearQuotaMessagesIfUnderCap() {
        if !DiaryUploadUsageGuard.isExhausted(kind: .text, tier: billingTier) {
            softNudge = nil
            hardGate = nil
        }
    }

    private func attemptSave() {
        guard canSave else { return }

        if isEditing {
            saveEdit()
            return
        }

        softNudge = nil
        hardGate = nil

        if DiaryUploadUsageGuard.isExhausted(kind: .text, tier: billingTier) {
            let limit = DiaryUploadUsageGuard.effectiveLimit(kind: .text, tier: billingTier)
            hardGate = HardQuotaGate(
                tier: billingTier,
                limit: limit,
                showUpgrade: billingTier != .authorPro
            )
            return
        }

        // Inline soft reminder (Free uses 5-8). Nested paywall sheets from this sheet
        // auto-dismissed on device and silently saved.
        if DiaryUploadUsageGuard.shouldNudgePaywall(kind: .text, tier: billingTier),
           !suppressPaywallNagOnce {
            let remaining = DiaryUploadUsageGuard.remaining(kind: .text, tier: billingTier)
            let limit = DiaryUploadUsageGuard.effectiveLimit(kind: .text, tier: billingTier)
            softNudge = SoftQuotaNudge(remaining: remaining, limit: limit)
            return
        }
        suppressPaywallNagOnce = false

        do {
            try DiaryUploadUsageGuard.checkAndIncrement(kind: .text, tier: billingTier)
        } catch {
            if case let DiaryUploadUsageGuard.UploadError.rateLimited(_, limit, tier) = error {
                hardGate = HardQuotaGate(
                    tier: tier,
                    limit: limit,
                    showUpgrade: tier != .authorPro
                )
                return
            }
            hardGate = HardQuotaGate(
                tier: billingTier,
                limit: DiaryUploadUsageGuard.effectiveLimit(kind: .text, tier: billingTier),
                showUpgrade: billingTier != .authorPro
            )
            return
        }

        diaryStore.add(
            title: title,
            body: bodyText,
            moodTag: mood.isEmpty ? nil : mood
        )
        dismiss()
    }

    private func saveEdit() {
        guard var updated = editingEntry else { return }
        updated.title = title.trimmingCharacters(in: .whitespacesAndNewlines)
        updated.body = bodyText.trimmingCharacters(in: .whitespacesAndNewlines)
        let moodTrimmed = mood.trimmingCharacters(in: .whitespacesAndNewlines)
        updated.moodTag = moodTrimmed.isEmpty ? nil : moodTrimmed
        diaryStore.update(updated)
        dismiss()
    }
}

// MARK: - Quota banner models

private struct SoftQuotaNudge: Equatable {
    let remaining: Int
    let limit: Int
}

private struct HardQuotaGate: Equatable {
    let tier: BillingConfig.Tier
    let limit: Int
    let showUpgrade: Bool
}
