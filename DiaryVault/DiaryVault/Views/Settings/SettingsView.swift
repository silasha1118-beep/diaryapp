import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var localization: LocalizationManager
    @EnvironmentObject private var appState: AppState
    @EnvironmentObject private var account: AccountSession
    @EnvironmentObject private var subscriptions: SubscriptionStore
    @EnvironmentObject private var diaryStore: DiaryStore

    @State private var showPaywall = false
    @State private var showDeleteConfirm = false
    @State private var showPrivacySheet = false
    @State private var exportURL: URL?
    @State private var exportError: String?
    @State private var deleteOutcomeMessage: String?
    @State private var isDeleting = false

    var body: some View {
        NavigationStack {
            ZStack {
                VaultBackground()

                Form {
                    Section {
                        if account.isSignedIn {
                            LabeledContent(localization.text("settings.account.status")) {
                                Text(localization.text("settings.account.signedIn"))
                                    .foregroundStyle(VaultTheme.inkMuted)
                            }
                            if !account.displayLabel.isEmpty {
                                LabeledContent(localization.text("settings.account.identity")) {
                                    Text(account.displayLabel)
                                        .foregroundStyle(VaultTheme.inkMuted)
                                        .lineLimit(1)
                                }
                            }
                            LabeledContent(localization.text("settings.account.provider")) {
                                Text(providerLabel)
                                    .foregroundStyle(VaultTheme.inkMuted)
                            }
                            if account.hasLiveFirebaseAuth {
                                LabeledContent(localization.text("settings.account.linked.phone")) {
                                    Text(
                                        account.hasLinkedPhone
                                            ? localization.text("settings.account.linked.yes")
                                            : localization.text("settings.account.linked.no")
                                    )
                                    .foregroundStyle(
                                        account.hasLinkedPhone ? VaultTheme.inkMuted : VaultTheme.seal
                                    )
                                }
                                LabeledContent(localization.text("settings.account.linked.apple")) {
                                    Text(
                                        account.hasLinkedApple
                                            ? localization.text("settings.account.linked.yes")
                                            : localization.text("settings.account.linked.no")
                                    )
                                    .foregroundStyle(
                                        account.hasLinkedApple ? VaultTheme.inkMuted : VaultTheme.seal
                                    )
                                }
                            }
                            Button(role: .destructive) {
                                account.signOut()
                            } label: {
                                Text(localization.text("settings.account.signOut"))
                            }
                        } else {
                            Text(localization.text("settings.account.signedOut"))
                                .foregroundStyle(VaultTheme.inkMuted)
                        }
                    } header: {
                        Text(localization.text("settings.section.account"))
                    }

                    if !account.isSignedIn {
                        Section {
                            PhoneOTPLoginForm(chrome: .settings)
                                .listRowInsets(EdgeInsets(top: 12, leading: 16, bottom: 12, trailing: 16))

                            AppleSignInBackupView(chrome: .settings)
                                .listRowInsets(EdgeInsets(top: 8, leading: 16, bottom: 12, trailing: 16))
                        } header: {
                            Text(localization.text("settings.section.accountPhone"))
                        } footer: {
                            Text(localization.text("settings.account.phone.footer"))
                        }
                    } else if account.hasLiveFirebaseAuth {
                        if account.needsPhoneLink {
                            Section {
                                Text(localization.text("settings.account.link.phone.banner"))
                                    .font(.system(size: 14, weight: .medium))
                                    .foregroundStyle(VaultTheme.seal)
                                    .fixedSize(horizontal: false, vertical: true)

                                PhoneOTPLoginForm(chrome: .settings, mode: .link)
                                    .listRowInsets(EdgeInsets(top: 12, leading: 16, bottom: 12, trailing: 16))
                            } header: {
                                Text(localization.text("settings.account.link.phone.title"))
                            } footer: {
                                Text(localization.text("settings.account.link.phone.note"))
                            }
                        }
                        if account.needsAppleLink {
                            Section {
                                Text(localization.text("settings.account.link.apple.banner"))
                                    .font(.system(size: 14, weight: .medium))
                                    .foregroundStyle(VaultTheme.seal)
                                    .fixedSize(horizontal: false, vertical: true)

                                AppleSignInBackupView(chrome: .settings, mode: .link)
                                    .listRowInsets(EdgeInsets(top: 8, leading: 16, bottom: 12, trailing: 16))
                            } header: {
                                Text(localization.text("settings.account.link.apple.title"))
                            } footer: {
                                Text(localization.text("settings.account.link.apple.note"))
                            }
                        }
                        if account.hasLinkedPhone && account.hasLinkedApple {
                            Section {
                                Text(localization.text("settings.account.link.bothDone"))
                                    .font(.system(size: 13))
                                    .foregroundStyle(VaultTheme.inkMuted)
                            } header: {
                                Text(localization.text("settings.section.accountLink"))
                            } footer: {
                                Text(localization.text("settings.account.link.footer"))
                            }
                        }
                    }

                    Section {
                        LabeledContent(localization.text("settings.cloud.status")) {
                            Text(cloudSyncStatusLabel)
                                .foregroundStyle(VaultTheme.inkMuted)
                                .multilineTextAlignment(.trailing)
                        }
                        if let projectID = cloudProjectID {
                            LabeledContent(localization.text("settings.cloud.project")) {
                                Text(projectID)
                                    .font(.system(size: 12, design: .monospaced))
                                    .foregroundStyle(VaultTheme.inkMuted)
                                    .textSelection(.enabled)
                            }
                        }
                        LabeledContent(localization.text("settings.cloud.database")) {
                            Text(DiaryCloudSync.firestoreDatabaseID)
                                .font(.system(size: 12, design: .monospaced))
                                .foregroundStyle(VaultTheme.inkMuted)
                        }
                        if let uid = account.firebaseAuthUID, !uid.isEmpty {
                            LabeledContent(localization.text("settings.cloud.uid")) {
                                Text(uid)
                                    .font(.system(size: 11, design: .monospaced))
                                    .foregroundStyle(VaultTheme.inkMuted)
                                    .lineLimit(2)
                                    .textSelection(.enabled)
                            }
                        }
                        if let synced = diaryStore.lastCloudSyncAt {
                            LabeledContent(localization.text("settings.cloud.lastSync")) {
                                Text(synced, style: .relative)
                                    .foregroundStyle(VaultTheme.inkMuted)
                            }
                        }
                        if let serverCount = diaryStore.lastCloudSyncServerCount {
                            LabeledContent(localization.text("settings.cloud.serverCount")) {
                                Text("\(serverCount)")
                                    .foregroundStyle(VaultTheme.inkMuted)
                            }
                        }
                        if let err = diaryStore.lastCloudSyncError, !err.isEmpty {
                            Text(err)
                                .font(.system(size: 13))
                                .foregroundStyle(VaultTheme.seal)
                                .textSelection(.enabled)
                        }
                        Button {
                            diaryStore.scheduleSynchronize()
                        } label: {
                            Text(localization.text("settings.cloud.syncNow"))
                                .foregroundStyle(VaultTheme.seal)
                        }
                        .disabled(!account.hasLiveFirebaseAuth)
                    } header: {
                        Text(localization.text("settings.section.cloud"))
                    } footer: {
                        Text(localization.text("settings.cloud.footer"))
                    }

                    Section {
                        LabeledContent(localization.text("settings.billing.plan")) {
                            Text(billingPlanLabel)
                                .foregroundStyle(VaultTheme.inkMuted)
                        }
                        if let productID = subscriptions.activeProductID {
                            LabeledContent(localization.text("settings.billing.product")) {
                                Text(productID)
                                    .font(.system(size: 12, design: .monospaced))
                                    .foregroundStyle(VaultTheme.inkMuted)
                                    .lineLimit(1)
                            }
                        }
                        if let exp = subscriptions.expirationDate {
                            LabeledContent(localization.text("settings.billing.expires")) {
                                Text(exp, style: .date)
                                    .foregroundStyle(VaultTheme.inkMuted)
                            }
                        }
                        Button {
                            showPaywall = true
                        } label: {
                            Text(localization.text("settings.billing.manage"))
                                .foregroundStyle(VaultTheme.seal)
                        }
                    } header: {
                        Text(localization.text("settings.section.billing"))
                    } footer: {
                        Text(localization.text("settings.billing.footer"))
                    }

                    Section {
                        Button {
                            prepareExport()
                        } label: {
                            Text(localization.text("settings.data.export"))
                                .foregroundStyle(VaultTheme.seal)
                        }
                        if let exportURL {
                            ShareLink(
                                item: exportURL,
                                subject: Text(localization.text("settings.data.export.shareSubject")),
                                message: Text(localization.text("settings.data.export.shareMessage"))
                            ) {
                                Text(localization.text("settings.data.export.share"))
                                    .foregroundStyle(VaultTheme.seal)
                            }
                        }
                        if let exportError {
                            Text(exportError)
                                .font(.system(size: 13))
                                .foregroundStyle(VaultTheme.seal)
                        }
                        if account.isSignedIn {
                            Button(role: .destructive) {
                                showDeleteConfirm = true
                            } label: {
                                Text(localization.text("settings.data.deleteAccount"))
                            }
                            .disabled(isDeleting)
                        }
                        if let deleteOutcomeMessage {
                            Text(deleteOutcomeMessage)
                                .font(.system(size: 13))
                                .foregroundStyle(VaultTheme.inkMuted)
                        }
                    } header: {
                        Text(localization.text("settings.section.data"))
                    } footer: {
                        Text(localization.text("settings.data.footer"))
                    }

                    Section {
                        if let privacy = LegalConfig.privacyPolicyURL {
                            Link(localization.text("settings.legal.privacy"), destination: privacy)
                        } else {
                            Button {
                                showPrivacySheet = true
                            } label: {
                                Text(localization.text("settings.legal.privacy"))
                                    .foregroundStyle(VaultTheme.seal)
                            }
                        }
                        Link(
                            localization.text("settings.legal.terms"),
                            destination: LegalConfig.effectiveTermsURL
                        )
                    } header: {
                        Text(localization.text("settings.section.legal"))
                    } footer: {
                        Text(localization.text("settings.legal.footer"))
                    }

                    Section {
                        Picker(localization.text("settings.language"), selection: $localization.language) {
                            ForEach(AppLanguage.allCases) { lang in
                                Text(lang.displayName).tag(lang)
                            }
                        }
                    } header: {
                        Text(localization.text("settings.section.language"))
                    } footer: {
                        Text(localization.text("settings.language.footer"))
                    }

                    Section {
                        Toggle(isOn: $appState.gentleRemindersEnabled) {
                            Text(localization.text("settings.reminders"))
                        }
                        .tint(VaultTheme.seal)
                    } header: {
                        Text(localization.text("settings.section.advice"))
                    } footer: {
                        Text(localization.text("settings.reminders.footer"))
                    }

                    Section {
                        LabeledContent(localization.text("settings.ai.mode")) {
                            Text(
                                AIConfig.useCloudClient
                                    ? localization.text("settings.ai.cloud")
                                    : localization.text("settings.ai.mock")
                            )
                            .foregroundStyle(VaultTheme.inkMuted)
                        }
                        if AIConfig.useCloudClient {
                            LabeledContent(localization.text("settings.ai.provider")) {
                                Text(
                                    AIConfig.provider == .anthropic
                                        ? localization.text("settings.ai.provider.anthropic")
                                        : localization.text("settings.ai.provider.openai")
                                )
                                .foregroundStyle(VaultTheme.inkMuted)
                            }
                            LabeledContent(localization.text("settings.ai.quota")) {
                                Text(mirrorQuotaLabel)
                                    .foregroundStyle(
                                        MirrorUsageGuard.isExhausted(tier: subscriptions.tier)
                                            ? VaultTheme.seal
                                            : VaultTheme.inkMuted
                                    )
                            }
                            if MirrorUsageGuard.isExhausted(tier: subscriptions.tier) {
                                Text(localization.text(settingsExhaustedKey))
                                .font(.system(size: 13))
                                .foregroundStyle(VaultTheme.seal)
                                if subscriptions.tier != .authorPro {
                                    Button {
                                        showPaywall = true
                                    } label: {
                                        Text(
                                            localization.text(
                                                subscriptions.tier == .authorPlus
                                                    ? "mirror.quota.upgrade.pro"
                                                    : "mirror.quota.upgrade"
                                            )
                                        )
                                        .foregroundStyle(VaultTheme.seal)
                                    }
                                }
                            }
                        }
                        LabeledContent(localization.text("settings.ai.residency")) {
                            Text(AIConfig.dataResidencyLabel)
                                .foregroundStyle(VaultTheme.inkMuted)
                        }
                    } header: {
                        Text(localization.text("settings.section.ai"))
                    } footer: {
                        Text(localization.text("settings.ai.footer"))
                    }

                    Section {
                        Text(localization.text("settings.legacy.stub"))
                            .foregroundStyle(VaultTheme.inkMuted)
                    } header: {
                        Text(localization.text("settings.section.legacy"))
                    } footer: {
                        Text(localization.text("settings.legacy.footer"))
                    }

                    Section {
                        if !appState.displayName.isEmpty {
                            LabeledContent(localization.text("settings.name")) {
                                Text(appState.displayName)
                            }
                        }
                        Button(localization.text("settings.resetOnboarding")) {
                            appState.resetOnboardingForDemo()
                        }
                        .foregroundStyle(VaultTheme.seal)
                    } header: {
                        Text(localization.text("settings.section.vault"))
                    }
                }
                .scrollContentBackground(.hidden)
                // numberPad has no Return — drag or any Form tap must resign first responder.
                .scrollDismissesKeyboard(.immediately)
                .resignKeyboardOnBackgroundTap()
                .resignKeyboardOnAnyTap()
            }
            .navigationTitle(localization.text("settings.title"))
            .onDisappear {
                KeyboardDismiss.resign()
            }
            .sheet(isPresented: $showPaywall) {
                PaywallView()
                    .environmentObject(localization)
                    .environmentObject(subscriptions)
            }
            .sheet(isPresented: $showPrivacySheet) {
                PrivacyPolicySheet()
                    .environmentObject(localization)
            }
            .confirmationDialog(
                localization.text("settings.data.deleteAccount.title"),
                isPresented: $showDeleteConfirm,
                titleVisibility: .visible
            ) {
                Button(localization.text("settings.data.deleteAccount.confirm"), role: .destructive) {
                    Task { await performDeleteAccount() }
                }
                Button(localization.text("common.cancel"), role: .cancel) {}
            } message: {
                Text(localization.text("settings.data.deleteAccount.message"))
            }
        }
    }

    private var providerLabel: String {
        if account.hasLinkedPhone && account.hasLinkedApple {
            return localization.text("settings.account.provider.both")
        }
        switch account.provider {
        case .phone:
            return localization.text("settings.account.provider.phone")
        case .apple:
            return localization.text("settings.account.provider.apple")
        case .local:
            return localization.text("settings.account.provider.local")
        case .none:
            return "—"
        }
    }

    private var cloudProjectID: String? {
        diaryStore.lastCloudSyncProjectID
            ?? DiaryCloudSync.firebaseProjectID
            ?? AuthConfig.firebaseProjectID
    }

    private var cloudSyncStatusLabel: String {
        if account.hasLiveFirebaseAuth {
            if diaryStore.lastCloudSyncError != nil {
                return localization.text("settings.cloud.status.error")
            }
            if diaryStore.lastCloudSyncAt != nil {
                return localization.text("settings.cloud.status.synced")
            }
            return localization.text("settings.cloud.status.pending")
        }
        if account.isSignedIn {
            return localization.text("settings.cloud.status.localSession")
        }
        return localization.text("settings.cloud.status.signedOut")
    }

    private var billingPlanLabel: String {
        switch subscriptions.tier {
        case .free:
            return localization.text("settings.billing.free")
        case .authorPlus:
            return localization.text("settings.billing.active")
        case .authorPro:
            return localization.text("settings.billing.pro")
        }
    }

    private var settingsExhaustedKey: String {
        switch subscriptions.tier {
        case .free: return "settings.ai.quota.exhausted.free"
        case .authorPlus: return "settings.ai.quota.exhausted.plus"
        case .authorPro: return "settings.ai.quota.exhausted.pro"
        }
    }

    private var mirrorQuotaLabel: String {
        let used = MirrorUsageGuard.usageCountToday()
        let limit = MirrorUsageGuard.effectiveLimit(tier: subscriptions.tier)
        if limit <= 0 {
            return localization.text("settings.ai.quota.unlimited")
        }
        return String(format: localization.text("settings.ai.quota.value"), used, limit)
    }

    private func prepareExport() {
        exportError = nil
        do {
            exportURL = try diaryStore.makeExportFileURL()
        } catch {
            exportURL = nil
            exportError = localization.text("settings.data.export.error")
        }
    }

    @MainActor
    private func performDeleteAccount() async {
        isDeleting = true
        defer { isDeleting = false }
        deleteOutcomeMessage = nil
        // Recommend export first is in the dialog copy; purge cloud while Auth is live, then wipe local + session.
        await diaryStore.deleteAllEntries(purgeCloud: true)
        let outcome = await account.deleteAccount()
        appState.clearIdentityAfterAccountDeletion()
        switch outcome {
        case .deletedRemote, .localOnly:
            deleteOutcomeMessage = localization.text("settings.data.deleteAccount.done")
        case .requiresReauthentication:
            deleteOutcomeMessage = localization.text("settings.data.deleteAccount.reauth")
        case .remoteFailed:
            deleteOutcomeMessage = localization.text("settings.data.deleteAccount.remoteFailed")
        }
    }
}

/// In-app privacy summary when `DIARY_PRIVACY_POLICY_URL` is not yet hosted.
private struct PrivacyPolicySheet: View {
    @EnvironmentObject private var localization: LocalizationManager
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text(localization.text("onboarding.privacy.title"))
                        .font(.system(size: 22, weight: .semibold, design: .serif))
                        .foregroundStyle(VaultTheme.ink)
                    Text(localization.text("onboarding.privacy.point1"))
                        .foregroundStyle(VaultTheme.inkMuted)
                    Text(localization.text("onboarding.privacy.point2"))
                        .foregroundStyle(VaultTheme.inkMuted)
                    Text(localization.text("onboarding.privacy.point3"))
                        .foregroundStyle(VaultTheme.inkMuted)
                    Text(localization.text("onboarding.privacy.residency"))
                        .foregroundStyle(VaultTheme.inkMuted)
                    Text(localization.text("settings.legal.privacy.stubNote"))
                        .font(.system(size: 13))
                        .foregroundStyle(VaultTheme.inkMuted)
                }
                .padding(24)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .background(VaultBackground())
            .navigationTitle(localization.text("settings.legal.privacy"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(localization.text("common.done")) { dismiss() }
                }
            }
        }
    }
}
