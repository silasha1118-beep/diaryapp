import SwiftUI

struct DiaryComposerView: View {
    @EnvironmentObject private var diaryStore: DiaryStore
    @EnvironmentObject private var localization: LocalizationManager
    @EnvironmentObject private var subscriptions: SubscriptionStore
    @Environment(\.dismiss) private var dismiss

    @State private var title = ""
    @State private var bodyText = ""
    @State private var mood = ""
    @State private var quotaError: String?
    @State private var softReminder: String?
    @State private var showQuotaUpgrade = false
    @State private var showPaywall = false
    /// After the user taps Continue on the inline soft reminder, allow one save without re-nudge.
    @State private var suppressPaywallNagOnce = false

    private var billingTier: BillingConfig.Tier { subscriptions.tier }

    private var canSave: Bool {
        !bodyText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    var body: some View {
        NavigationStack {
            ZStack {
                VaultTheme.paper.ignoresSafeArea()

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

                    if let softReminder {
                        VStack(alignment: .leading, spacing: 10) {
                            Text(softReminder)
                                .font(.system(size: 13))
                                .foregroundStyle(VaultTheme.seal)
                                .fixedSize(horizontal: false, vertical: true)
                            HStack(spacing: 16) {
                                Button {
                                    showPaywall = true
                                } label: {
                                    Text(localization.text(quotaUpgradeCTAKey))
                                        .font(.system(size: 13, weight: .semibold))
                                        .foregroundStyle(VaultTheme.seal)
                                }
                                .buttonStyle(.plain)

                                Button {
                                    suppressPaywallNagOnce = true
                                    softReminder = nil
                                    attemptSave()
                                } label: {
                                    Text(localization.text("diary.quota.soft.continue"))
                                        .font(.system(size: 13, weight: .semibold))
                                        .foregroundStyle(VaultTheme.ink)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }

                    if let quotaError {
                        VStack(alignment: .leading, spacing: 8) {
                            Text(quotaError)
                                .font(.system(size: 13))
                                .foregroundStyle(VaultTheme.seal)
                            if showQuotaUpgrade {
                                Button {
                                    showPaywall = true
                                } label: {
                                    Text(localization.text(quotaUpgradeCTAKey))
                                        .font(.system(size: 13, weight: .semibold))
                                        .foregroundStyle(VaultTheme.seal)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }

                    Spacer(minLength: 0)
                }
                .padding(20)
            }
            .navigationTitle(localization.text("diary.new"))
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
            .onChange(of: subscriptions.tier) { _, _ in
                if !DiaryUploadUsageGuard.isExhausted(kind: .text, tier: billingTier) {
                    quotaError = nil
                    showQuotaUpgrade = false
                    softReminder = nil
                }
            }
            // fullScreenCover — not nested `.sheet` — so Free soft paywall is visible from the composer sheet.
            .fullScreenCover(isPresented: $showPaywall) {
                NavigationStack {
                    PaywallView()
                        .environmentObject(localization)
                        .environmentObject(subscriptions)
                        .toolbar {
                            ToolbarItem(placement: .cancellationAction) {
                                Button(localization.text("common.cancel")) {
                                    showPaywall = false
                                }
                            }
                        }
                }
            }
        }
    }

    private var quotaUpgradeCTAKey: String {
        switch billingTier {
        case .authorPlus:
            return "diary.quota.upgrade.pro"
        case .free, .authorPro:
            return "diary.quota.upgrade"
        }
    }

    private func exhaustedKey(for tier: BillingConfig.Tier) -> String {
        switch tier {
        case .free: return "diary.quota.exhausted.free"
        case .authorPlus: return "diary.quota.exhausted.plus"
        case .authorPro: return "diary.quota.exhausted.pro"
        }
    }

    private func attemptSave() {
        guard canSave else { return }
        softReminder = nil
        quotaError = nil
        showQuotaUpgrade = false

        if DiaryUploadUsageGuard.isExhausted(kind: .text, tier: billingTier) {
            let limit = DiaryUploadUsageGuard.effectiveLimit(kind: .text, tier: billingTier)
            quotaError = String(format: localization.text(exhaustedKey(for: billingTier)), limit)
            showQuotaUpgrade = billingTier != .authorPro
            return
        }

        // Inline soft reminder (Free uses 5–8). Do not present a nested sheet from this sheet —
        // that auto-dismissed and silently saved on device.
        if DiaryUploadUsageGuard.shouldNudgePaywall(kind: .text, tier: billingTier),
           !suppressPaywallNagOnce {
            let remaining = DiaryUploadUsageGuard.remaining(kind: .text, tier: billingTier)
            let limit = DiaryUploadUsageGuard.effectiveLimit(kind: .text, tier: billingTier)
            softReminder = String(
                format: localization.text("diary.quota.softReminder"),
                remaining,
                limit
            )
            return
        }
        suppressPaywallNagOnce = false

        do {
            try DiaryUploadUsageGuard.checkAndIncrement(kind: .text, tier: billingTier)
        } catch let DiaryUploadUsageGuard.UploadError.rateLimited(_, limit, tier) {
            quotaError = String(format: localization.text(exhaustedKey(for: tier)), limit)
            showQuotaUpgrade = tier != .authorPro
            return
        } catch {
            quotaError = error.localizedDescription
            return
        }

        diaryStore.add(
            title: title,
            body: bodyText,
            moodTag: mood.isEmpty ? nil : mood
        )
        dismiss()
    }
}
