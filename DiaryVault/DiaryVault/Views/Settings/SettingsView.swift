import SwiftUI

/// Internal Settings dumps (Firebase project / Auth UID / product IDs / reset onboarding).
/// Keep `false` until brand launch — DEBUG alone must not surface this to testers or customers.
private enum SettingsDeveloperTools {
    static let isVisible = false
}

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
                    accountSection

                    if !account.isSignedIn {
                        signInSection
                    } else if account.hasLiveFirebaseAuth {
                        linkSections
                    }

                    cloudSection
                    billingSection
                    dataSection
                    languageSection
                    adviceSection
                    if !appState.displayName.isEmpty {
                        vaultSection
                    }
                    // Legal last so Privacy / EULA clear the tab bar with bottom inset below.
                    legalSection
                    #if DEBUG
                    if SettingsDeveloperTools.isVisible {
                        developerSection
                    }
                    #endif
                }
                .scrollContentBackground(.hidden)
                // numberPad has no Return — drag/scroll resigns; PhoneOTPLoginForm owns tap-to-dismiss.
                // Do NOT attach SwiftUI simultaneousGesture TapGesture on this Form — it steals
                // Button / Link hits (Sync / Export / Delete / Paywall / Legal all went dead).
                .scrollDismissesKeyboard(.immediately)
                .resignKeyboardOnAnyTap()
                .contentMargins(.bottom, 36, for: .scrollContent)
                .safeAreaInset(edge: .bottom, spacing: 0) {
                    Color.clear.frame(height: 12)
                        .accessibilityHidden(true)
                }
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

    // MARK: - Sections

    private var accountSection: some View {
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
                settingsActionButton(titleKey: "settings.account.signOut", role: .destructive) {
                    account.signOut()
                }
            } else {
                Text(localization.text("settings.account.signedOut"))
                    .foregroundStyle(VaultTheme.inkMuted)
            }
        } header: {
            Text(localization.text("settings.section.account"))
        }
    }

    private var signInSection: some View {
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
    }

    @ViewBuilder
    private var linkSections: some View {
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

    private var cloudSection: some View {
        Section {
            LabeledContent(localization.text("settings.cloud.status")) {
                Text(cloudSyncStatusLabel)
                    .foregroundStyle(VaultTheme.inkMuted)
                    .multilineTextAlignment(.trailing)
            }
            if let err = diaryStore.lastCloudSyncError, !err.isEmpty {
                Text(customerFacingSyncError(err))
                    .font(.system(size: 13))
                    .foregroundStyle(VaultTheme.seal)
            }
            settingsActionButton(titleKey: "settings.cloud.syncNow") {
                diaryStore.scheduleSynchronize()
            }
            .disabled(!account.hasLiveFirebaseAuth)
        } header: {
            Text(localization.text("settings.section.cloud"))
        } footer: {
            Text(localization.text("settings.cloud.footer"))
        }
    }

    private var billingSection: some View {
        Section {
            LabeledContent(localization.text("settings.billing.plan")) {
                Text(billingPlanLabel)
                    .foregroundStyle(VaultTheme.inkMuted)
            }
            if let exp = subscriptions.expirationDate {
                LabeledContent(localization.text("settings.billing.expires")) {
                    Text(exp, style: .date)
                        .foregroundStyle(VaultTheme.inkMuted)
                }
            }
            settingsActionButton(titleKey: "settings.billing.manage") {
                showPaywall = true
            }
        } header: {
            Text(localization.text("settings.section.billing"))
        } footer: {
            Text(localization.text("settings.billing.footer"))
        }
    }

    private var dataSection: some View {
        Section {
            settingsActionButton(titleKey: "settings.data.export") {
                prepareExport()
            }
            if let exportURL {
                ShareLink(
                    item: exportURL,
                    subject: Text(localization.text("settings.data.export.shareSubject")),
                    message: Text(localization.text("settings.data.export.shareMessage"))
                ) {
                    Text(localization.text("settings.data.export.share"))
                        .foregroundStyle(VaultTheme.seal)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.borderless)
            }
            if let exportError {
                Text(exportError)
                    .font(.system(size: 13))
                    .foregroundStyle(VaultTheme.seal)
            }
            if account.isSignedIn {
                settingsActionButton(titleKey: "settings.data.deleteAccount", role: .destructive) {
                    showDeleteConfirm = true
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
    }

    private var legalSection: some View {
        Section {
            if let privacy = LegalConfig.privacyPolicyURL {
                settingsLinkRow(
                    titleKey: "settings.legal.privacy",
                    destination: privacy
                )
            } else {
                settingsActionButton(titleKey: "settings.legal.privacy") {
                    showPrivacySheet = true
                }
            }
            settingsLinkRow(
                titleKey: "settings.legal.terms",
                destination: LegalConfig.effectiveTermsURL
            )
        } header: {
            Text(localization.text("settings.section.legal"))
        } footer: {
            Text(localization.text("settings.legal.footer"))
        }
    }

    private var languageSection: some View {
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
    }

    private var adviceSection: some View {
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
    }

    private var vaultSection: some View {
        Section {
            if !appState.displayName.isEmpty {
                LabeledContent(localization.text("settings.name")) {
                    Text(appState.displayName)
                }
            }
            Text(localization.text("settings.legacy.stub"))
                .font(.system(size: 13))
                .foregroundStyle(VaultTheme.inkMuted)
        } header: {
            Text(localization.text("settings.section.vault"))
        }
    }

    #if DEBUG
    /// Builder-only dumps — gated by ``SettingsDeveloperTools/isVisible`` (off until brand launch).
    /// Never compile into Release; never show in DEBUG until the flag is flipped.
    private var developerCloudProjectID: String? {
        diaryStore.lastCloudSyncProjectID
            ?? DiaryCloudSync.firebaseProjectID
            ?? AuthConfig.firebaseProjectID
    }

    private var developerSection: some View {
        Section {
            if let projectID = developerCloudProjectID {
                LabeledContent(localization.text("settings.cloud.project")) {
                    Text(projectID)
                        .font(.system(size: 12, design: .monospaced))
                        .foregroundStyle(VaultTheme.inkMuted)
                        .textSelection(.enabled)
                }
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
            if let productID = subscriptions.activeProductID {
                LabeledContent(localization.text("settings.billing.product")) {
                    Text(productID)
                        .font(.system(size: 12, design: .monospaced))
                        .foregroundStyle(VaultTheme.inkMuted)
                        .lineLimit(1)
                }
            }
            Button(localization.text("settings.resetOnboarding")) {
                appState.resetOnboardingForDemo()
            }
            .foregroundStyle(VaultTheme.seal)
        } header: {
            Text(localization.text("settings.section.developer"))
        } footer: {
            Text(localization.text("settings.developer.footer"))
        }
    }
    #endif

    // MARK: - Labels

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

    /// Full-row Form action — borderless + contentShape so seal-colored rows stay tappable.
    private func settingsActionButton(
        titleKey: String,
        role: ButtonRole? = nil,
        action: @escaping () -> Void
    ) -> some View {
        Button(role: role, action: action) {
            Text(localization.text(titleKey))
                .foregroundStyle(role == .destructive ? Color.red : VaultTheme.seal)
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())
        }
        .buttonStyle(.borderless)
    }

    /// Legal / external links with the same full-row hit target as action buttons.
    private func settingsLinkRow(titleKey: String, destination: URL) -> some View {
        Link(destination: destination) {
            Text(localization.text(titleKey))
                .foregroundStyle(VaultTheme.seal)
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())
        }
        .buttonStyle(.borderless)
    }

    /// Avoid dumping raw Firestore / permission stack traces into Settings.
    private func customerFacingSyncError(_ raw: String) -> String {
        let lower = raw.lowercased()
        if lower.contains("permission") || lower.contains("unauthenticated") {
            return localization.text("settings.cloud.status.error.friendly")
        }
        if raw.count > 120 {
            return localization.text("settings.cloud.status.error.friendly")
        }
        return raw
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

/// In-app privacy summary when a hosted privacy URL is not yet configured.
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
