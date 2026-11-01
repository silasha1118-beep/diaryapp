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
    @State private var attachments: [DiaryMediaAttachment] = []
    /// Stable ID so draft media files land under the eventual entry folder.
    @State private var draftEntryID = UUID()
    @State private var hardGate: HardQuotaGate?
    /// Compact Plus/Pro + period checkout (owned by this sheet — nested parent cover is a no-op).
    @State private var showUpgradeCheckout = false
    @State private var didLoadEditDraft = false
    @State private var mediaAlertMessage: String?
    @State private var didSave = false
    @FocusState private var focusedField: ComposerField?

    private enum ComposerField: Hashable {
        case title
        case mood
        case body
    }

    private var isEditing: Bool { editingEntry != nil }

    private var billingTier: BillingConfig.Tier { subscriptions.tier }

    private var canSave: Bool {
        let hasText = !bodyText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        return hasText || !attachments.isEmpty
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
                    Button(localization.text("common.cancel")) {
                        cancelComposer()
                    }
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
                if let hardGate {
                    hardQuotaBanner(hardGate)
                        // Keep the purchase chrome above the TextEditor hit target.
                        .zIndex(2)
                }
            }
            .onAppear {
                loadEditDraftIfNeeded()
            }
            .onDisappear {
                cleanupDraftMediaIfNeeded()
            }
            .onChange(of: subscriptions.tier) { _, _ in
                clearQuotaMessagesIfUnderCap()
            }
            .alert(
                localization.text("diary.media.alertTitle"),
                isPresented: Binding(
                    get: { mediaAlertMessage != nil },
                    set: { if !$0 { mediaAlertMessage = nil } }
                )
            ) {
                Button(localization.text("common.ok"), role: .cancel) {
                    mediaAlertMessage = nil
                }
            } message: {
                Text(mediaAlertMessage ?? "")
            }
        }
        // Owned by the composer sheet (parent cover while this sheet is up is a silent no-op).
        .sheet(isPresented: $showUpgradeCheckout) {
            UpgradeCheckoutSheet(
                preferredTier: billingTier == .authorPlus ? .authorPro : .authorPlus
            )
            .environmentObject(localization)
            .environmentObject(subscriptions)
        }
    }

    // MARK: - Split views (keeps the type checker happy)

    private var formContent: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                TextField(localization.text("diary.field.title"), text: $title)
                    .font(.system(size: 22, weight: .semibold, design: .serif))
                    .foregroundStyle(VaultTheme.ink)
                    .focused($focusedField, equals: .title)

                TextField(localization.text("diary.field.mood"), text: $mood)
                    .font(.system(size: 14))
                    .foregroundStyle(VaultTheme.inkMuted)
                    .focused($focusedField, equals: .mood)

                DiaryComposerMediaBar(
                    attachments: $attachments,
                    entryID: draftEntryID,
                    localization: localization,
                    onPermissionDenied: { mediaAlertMessage = $0 },
                    onError: { mediaAlertMessage = $0 }
                )
                .allowsHitTesting(hardGate == nil)

                Rectangle()
                    .fill(VaultTheme.line)
                    .frame(height: 1)

                TextEditor(text: $bodyText)
                    .font(.system(size: 17, design: .serif))
                    .foregroundStyle(VaultTheme.ink)
                    .scrollContentBackground(.hidden)
                    .background(Color.clear)
                    .frame(minHeight: 220)
                    .focused($focusedField, equals: .body)
                    // TextEditor often extends its hit-testing under bottom insets and steals
                    // the upgrade CTA tap (keyboard stays up, Button never fires).
                    .allowsHitTesting(hardGate == nil)

                Text(localization.text("diary.media.hint"))
                    .font(.system(size: 12))
                    .foregroundStyle(VaultTheme.inkMuted.opacity(0.85))
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(20)
        }
        // When hard-gated, disable the form (banner owns taps via safeAreaInset).
        .disabled(hardGate != nil)
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
                    presentUpgradeCheckout()
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
                        .contentShape(Rectangle())
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
        .contentShape(Rectangle())
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
        guard !didLoadEditDraft else { return }
        didLoadEditDraft = true
        if let editingEntry {
            draftEntryID = editingEntry.id
            title = editingEntry.title
            bodyText = editingEntry.body
            mood = editingEntry.moodTag ?? ""
            attachments = editingEntry.attachments
        } else {
            draftEntryID = UUID()
        }
    }

    private func cancelComposer() {
        dismiss()
    }

    private func cleanupDraftMediaIfNeeded() {
        guard !didSave, !isEditing else { return }
        // New entry abandoned — drop draft media folder.
        DiaryMediaFileStore.deleteAll(for: draftEntryID)
    }

    private func clearQuotaMessagesIfUnderCap() {
        if !DiaryUploadUsageGuard.isExhausted(kind: .text, tier: billingTier) {
            hardGate = nil
        }
    }

    /// Compact sheet: Plus + Pro, each with 月／季／年, then 訂閱 → Apple sheet.
    private func presentUpgradeCheckout() {
        focusedField = nil
        KeyboardDismiss.resign()
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 80_000_000)
            showUpgradeCheckout = true
        }
    }

    private func applyHardGate(tier: BillingConfig.Tier, limit: Int) {
        let showUpgrade = tier != .authorPro
        hardGate = HardQuotaGate(
            tier: tier,
            limit: limit,
            showUpgrade: showUpgrade
        )
        // Show the purchase banner; do not auto-fire StoreKit (requires an explicit CTA tap).
        focusedField = nil
        KeyboardDismiss.resign()
    }

    private func attemptSave() {
        guard canSave else { return }

        if isEditing {
            saveEdit()
            return
        }

        hardGate = nil

        // One vault upload per create (text and/or media share the same bucket).
        let quotaKind: UploadQuotaConfig.Kind = attachments.isEmpty ? .text : .multimedia

        // Hard stop only — Free soft ladders removed (lifetime vault uploads incl. media).
        if DiaryUploadUsageGuard.isExhausted(kind: quotaKind, tier: billingTier) {
            applyHardGate(
                tier: billingTier,
                limit: DiaryUploadUsageGuard.effectiveLimit(kind: quotaKind, tier: billingTier)
            )
            return
        }

        do {
            try DiaryUploadUsageGuard.checkAndIncrement(kind: quotaKind, tier: billingTier)
        } catch {
            if case let DiaryUploadUsageGuard.UploadError.rateLimited(_, limit, tier) = error {
                applyHardGate(tier: tier, limit: limit)
                return
            }
            applyHardGate(
                tier: billingTier,
                limit: DiaryUploadUsageGuard.effectiveLimit(kind: quotaKind, tier: billingTier)
            )
            return
        }

        didSave = true
        diaryStore.add(
            id: draftEntryID,
            title: title,
            body: bodyText,
            moodTag: mood.isEmpty ? nil : mood,
            attachments: attachments
        )
        dismiss()
    }

    private func saveEdit() {
        guard var updated = editingEntry else { return }
        updated.title = title.trimmingCharacters(in: .whitespacesAndNewlines)
        updated.body = bodyText.trimmingCharacters(in: .whitespacesAndNewlines)
        let moodTrimmed = mood.trimmingCharacters(in: .whitespacesAndNewlines)
        updated.moodTag = moodTrimmed.isEmpty ? nil : moodTrimmed
        updated.attachments = attachments
        didSave = true
        diaryStore.update(updated)
        dismiss()
    }
}

// MARK: - Quota banner models

private struct HardQuotaGate: Equatable {
    let tier: BillingConfig.Tier
    let limit: Int
    let showUpgrade: Bool
}
