import SwiftUI

/// Shared phone + SMS OTP fields wired to `SMSAuthProviderFactory` (Firebase when plist present).
/// Used by the post-onboarding gate and Settings so beginners can always find phone login / link.
struct PhoneOTPLoginForm: View {
    enum Chrome {
        /// Full-bleed fields on the auth gate scroll surface.
        case gate
        /// Compact fields inside a Settings `Form` section.
        case settings
    }

    /// Sign-in creates a session; link attaches phone to the current Firebase UID.
    enum Mode {
        case signIn
        case link
    }

    @EnvironmentObject private var localization: LocalizationManager
    @EnvironmentObject private var account: AccountSession
    @EnvironmentObject private var appState: AppState

    var chrome: Chrome = .gate
    var mode: Mode = .signIn
    /// Called after a successful phone verify / link (Settings can clear local UI state).
    var onSignedIn: (() -> Void)? = nil

    @State private var dial: PhoneNumberNormalizer.RegionDial = .tw
    @State private var nationalNumber = ""
    @State private var otpCode = ""
    @State private var session: SMSCodeSession?
    @State private var isSending = false
    @State private var isVerifying = false
    @State private var errorMessage: String?
    @State private var infoMessage: String?
    /// Seconds left before Resend is enabled again after a successful SMS send.
    @State private var resendSecondsRemaining = 0
    @State private var resendCooldownTask: Task<Void, Never>?
    /// Created on first Send / Verify only — Settings / Apple session UI must not touch Phone Auth.
    @State private var smsProvider: SMSAuthProviderHolder?
    @FocusState private var focusedField: Field?

    private static let resendCooldownSeconds = 60

    private enum Field: Hashable {
        case phone
        case otp
    }

    var body: some View {
        VStack(alignment: .leading, spacing: chrome == .gate ? 12 : 10) {
            // Settings link section already shows title/banner/footer — avoid duplicating login-looking chrome.
            if mode == .link && chrome != .settings {
                Text(localization.text("settings.account.link.phone.title"))
                    .font(.system(size: chrome == .gate ? 15 : 14, weight: .semibold))
                    .foregroundStyle(VaultTheme.ink)
                Text(localization.text("settings.account.link.phone.note"))
                    .font(.system(size: 12))
                    .foregroundStyle(VaultTheme.inkMuted)
                    .fixedSize(horizontal: false, vertical: true)
            }

            providerBanner

            phoneStep

            if session != nil {
                otpStep
                    .transition(.opacity.combined(with: .move(edge: .bottom)))
            }

            if let infoMessage {
                Text(infoMessage)
                    .font(.system(size: 13))
                    .foregroundStyle(VaultTheme.inkMuted)
                    .fixedSize(horizontal: false, vertical: true)
            }

            if let errorMessage {
                Text(errorMessage)
                    .font(.system(size: 14))
                    .foregroundStyle(VaultTheme.seal)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .contentShape(Rectangle())
        .resignKeyboardOnBackgroundTap()
        .resignKeyboardOnAnyTap()
        .animation(.easeInOut(duration: 0.3), value: session != nil)
        // phonePad / numberPad have no Return — Done toolbar lets users clear the pad and reach Mirror.
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button(localization.text("common.done")) {
                    dismissKeyboard()
                }
            }
        }
        .onAppear {
            surfacePhoneAuthURLSchemeIfNeeded()
        }
        .onDisappear {
            cancelResendCooldown()
            dismissKeyboard()
        }
    }

    private func dismissKeyboard() {
        focusedField = nil
        KeyboardDismiss.resign()
    }

    /// Always show Firebase vs Stub so a misconfigured local plist / scheme is obvious.
    private var providerBanner: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(providerTitle)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(AuthConfig.smsProvider == .stub ? VaultTheme.seal : VaultTheme.ink)
            if AuthConfig.smsProvider == .stub {
                Text(stubHelpText)
                    .font(.system(size: 12))
                    .foregroundStyle(VaultTheme.inkMuted)
                    .fixedSize(horizontal: false, vertical: true)
            } else if AuthConfig.smsProvider == .firebase,
                      !FirebasePhoneAuthURLScheme.readinessCheck().canInstantiatePhoneAuthProvider {
                Text(localization.text("auth.sms.appVerification"))
                    .font(.system(size: 12))
                    .foregroundStyle(VaultTheme.seal)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel(providerTitle)
    }

    private var providerTitle: String {
        switch AuthConfig.smsProvider {
        case .firebase:
            return localization.text("auth.sms.provider.firebase")
        case .stub:
            return localization.text("auth.sms.provider.stub")
        case .twilio:
            return localization.text("auth.sms.provider.twilio")
        }
    }

    private var stubHelpText: String {
        switch AuthConfig.smsProviderResolution {
        case .explicitEnvOrPlist:
            return localization.text("auth.sms.provider.stub.explicit")
        case .autoStubNoPlist:
            return localization.text("auth.sms.provider.stub.missingPlist")
        case .autoFirebasePlistPresent:
            return localization.text("auth.sms.provider.stub.missingPlist")
        }
    }

    // MARK: - Phone

    private var phoneStep: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(localization.text("auth.phone.label"))
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(VaultTheme.inkMuted)

            HStack(spacing: 10) {
                Picker(localization.text("auth.phone.region"), selection: $dial) {
                    ForEach(PhoneNumberNormalizer.RegionDial.allCases) { region in
                        Text(localization.text(region.labelKey)).tag(region)
                    }
                }
                .pickerStyle(.menu)
                .tint(VaultTheme.ink)
                .onChange(of: dial) { _, _ in
                    resetOTPChallenge()
                }

                TextField(localization.text("auth.phone.placeholder"), text: $nationalNumber)
                    .keyboardType(.phonePad)
                    .textContentType(.telephoneNumber)
                    .focused($focusedField, equals: .phone)
                    .modifier(AuthFieldChrome(chrome: chrome))
                    .onChange(of: nationalNumber) { _, _ in
                        resetOTPChallenge()
                    }
            }

            Button {
                dismissKeyboard()
                Task { await sendCode() }
            } label: {
                HStack {
                    if isSending {
                        ProgressView()
                            .tint(.white)
                    }
                    Text(sendOrResendLabel)
                        .font(.system(size: 16, weight: .semibold))
                }
                .foregroundStyle(Color.white)
                .frame(maxWidth: .infinity)
                .frame(height: chrome == .gate ? 48 : 44)
                .background(VaultTheme.seal.opacity(sendButtonEnabled ? 1 : 0.4))
                .clipShape(RoundedRectangle(cornerRadius: 4, style: .continuous))
            }
            .disabled(!sendButtonEnabled || isSending)
            .buttonStyle(.plain)
            .accessibilityLabel(sendOrResendLabel)
        }
    }

    private var otpStep: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(localization.text("auth.otp.label"))
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(VaultTheme.inkMuted)

            TextField(localization.text("auth.otp.placeholder"), text: $otpCode)
                .keyboardType(.numberPad)
                .textContentType(.oneTimeCode)
                .focused($focusedField, equals: .otp)
                .modifier(AuthFieldChrome(chrome: chrome))

            Button {
                dismissKeyboard()
                Task { await verifyCode() }
            } label: {
                HStack {
                    if isVerifying {
                        ProgressView()
                            .tint(.white)
                    }
                    Text(
                        mode == .link
                            ? localization.text("settings.account.link.phone.action")
                            : localization.text("auth.otp.verify")
                    )
                        .font(.system(size: 16, weight: .semibold))
                }
                .foregroundStyle(Color.white)
                .frame(maxWidth: .infinity)
                .frame(height: chrome == .gate ? 48 : 44)
                .background(VaultTheme.ink.opacity(canVerify ? 1 : 0.35))
                .clipShape(RoundedRectangle(cornerRadius: 4, style: .continuous))
            }
            .disabled(!canVerify || isVerifying)
            .buttonStyle(.plain)
        }
    }

    private var canSend: Bool {
        guard PhoneNumberNormalizer.isPlausibleNationalNumber(dial: dial, nationalNumber: nationalNumber) else {
            return false
        }
        // Block Send when Firebase Phone Auth would fatal inside PhoneAuthProvider.init.
        if AuthConfig.smsProvider == .firebase {
            return FirebasePhoneAuthURLScheme.readinessCheck().canInstantiatePhoneAuthProvider
        }
        return true
    }

    private var isResendCoolingDown: Bool {
        session != nil && resendSecondsRemaining > 0
    }

    private var sendButtonEnabled: Bool {
        canSend && !isResendCoolingDown
    }

    private var sendOrResendLabel: String {
        if session == nil {
            return localization.text(
                mode == .link
                    ? "settings.account.link.phone.send"
                    : "auth.phone.send"
            )
        }
        if resendSecondsRemaining > 0 {
            return String(
                format: localization.text(
                    mode == .link
                        ? "settings.account.link.phone.resend.cooldown"
                        : "auth.phone.resend.cooldown"
                ),
                resendSecondsRemaining
            )
        }
        return localization.text(
            mode == .link
                ? "settings.account.link.phone.resend"
                : "auth.phone.resend"
        )
    }

    private var canVerify: Bool {
        session != nil && otpCode.filter(\.isNumber).count == 6
    }

    // MARK: - Actions

    private func sendCode() async {
        guard !isResendCoolingDown else { return }
        errorMessage = nil
        infoMessage = nil
        isSending = true
        defer { isSending = false }

        guard PhoneNumberNormalizer.isPlausibleNationalNumber(dial: dial, nationalNumber: nationalNumber) else {
            errorMessage = localization.text("auth.phone.invalid")
            return
        }
        let e164 = PhoneNumberNormalizer.composeE164(dial: dial, nationalNumber: nationalNumber)
        do {
            let newSession = try await sms().sendCode(toPhoneE164: e164)
            withAnimation(.easeInOut(duration: 0.3)) {
                session = newSession
            }
            startResendCooldown()
            if let hint = newSession.stubDebugHint {
                infoMessage = String(format: localization.text("auth.stub.hint"), hint)
            } else {
                infoMessage = localization.text("auth.phone.sent")
            }
        } catch let error as SMSAuthError {
            errorMessage = mapSMSError(error)
        } catch {
            errorMessage = localization.text("auth.error")
        }
    }

    private func verifyCode() async {
        guard let session else { return }
        errorMessage = nil
        isVerifying = true
        defer { isVerifying = false }

        do {
            switch mode {
            case .signIn:
                let user = try await sms().verify(code: otpCode, session: session)
                account.signInWithPhone(
                    userID: user.userID,
                    phoneE164: user.phoneE164,
                    displayName: appState.displayName.isEmpty ? nil : appState.displayName
                )
                infoMessage = localization.text("settings.account.phone.success")
            case .link:
                _ = try await sms().linkPhone(code: otpCode, session: session)
                await account.refreshAfterProviderLink(
                    displayName: appState.displayName.isEmpty ? nil : appState.displayName
                )
                infoMessage = localization.text("settings.account.link.phone.success")
            }
            nationalNumber = ""
            otpCode = ""
            self.session = nil
            cancelResendCooldown()
            onSignedIn?()
        } catch let error as SMSAuthError {
            errorMessage = mapSMSError(error)
        } catch {
            errorMessage = localization.text("auth.otp.error")
        }
    }

    private func resetOTPChallenge() {
        session = nil
        otpCode = ""
        errorMessage = nil
        infoMessage = nil
        cancelResendCooldown()
    }

    private func startResendCooldown() {
        cancelResendCooldown()
        resendSecondsRemaining = Self.resendCooldownSeconds
        resendCooldownTask = Task { @MainActor in
            while resendSecondsRemaining > 0 {
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                guard !Task.isCancelled else { return }
                resendSecondsRemaining -= 1
            }
        }
    }

    private func cancelResendCooldown() {
        resendCooldownTask?.cancel()
        resendCooldownTask = nil
        resendSecondsRemaining = 0
    }

    private func mapSMSError(_ error: SMSAuthError) -> String {
        switch error {
        case .invalidPhone:
            return localization.text("auth.phone.invalid")
        case .invalidCode:
            return localization.text("auth.otp.error")
        case .notConfigured:
            return localization.text("auth.sms.notConfigured")
        case .rateLimited:
            return localization.text("auth.sms.rateLimited")
        case .regionNotAllowed:
            return localization.text("auth.sms.regionBlocked")
        case .appVerificationFailed:
            return localization.text("auth.sms.appVerification")
        case .notSignedInForLink:
            return localization.text("settings.account.link.needSignedIn")
        case .credentialAlreadyInUse:
            return localization.text("settings.account.link.phone.alreadyInUse")
                + "\n"
                + localization.text("settings.account.link.mergeGuidance")
        case .providerAlreadyLinked:
            return localization.text("settings.account.link.phone.alreadyLinked")
        case .underlying(let detail):
            #if DEBUG
            return detail.isEmpty ? localization.text("auth.error") : detail
            #else
            return localization.text("auth.error")
            #endif
        }
    }

    /// Lazy SMS backend — avoids constructing Firebase phone plumbing until the user sends an OTP.
    private func sms() -> any SMSAuthProviding {
        if let smsProvider {
            return smsProvider.provider
        }
        let created = SMSAuthProviderFactory.make()
        smsProvider = SMSAuthProviderHolder(created)
        return created
    }

    /// Surface missing URL scheme before the user taps Send — never instantiate PhoneAuthProvider here.
    private func surfacePhoneAuthURLSchemeIfNeeded() {
        guard AuthConfig.smsProvider == .firebase else { return }
        let readiness = FirebasePhoneAuthURLScheme.readinessCheck()
        guard !readiness.canInstantiatePhoneAuthProvider else { return }
        if errorMessage == nil {
            errorMessage = localization.text("auth.sms.appVerification")
        }
        #if DEBUG
        print("DiaryVault Phone Auth URL scheme not ready: \(readiness.debugSummary)")
        #endif
    }
}

/// `@State`-friendly box so we do not recreate the SMS backend on every SwiftUI body eval.
private final class SMSAuthProviderHolder {
    let provider: any SMSAuthProviding
    init(_ provider: any SMSAuthProviding) {
        self.provider = provider
    }
}

private struct AuthFieldChrome: ViewModifier {
    let chrome: PhoneOTPLoginForm.Chrome

    func body(content: Content) -> some View {
        switch chrome {
        case .gate:
            content
                .padding(.horizontal, 12)
                .padding(.vertical, 12)
                .background(VaultTheme.paper.opacity(0.9))
                .overlay(
                    RoundedRectangle(cornerRadius: 4, style: .continuous)
                        .stroke(VaultTheme.line, lineWidth: 1)
                )
        case .settings:
            content
                .padding(.horizontal, 10)
                .padding(.vertical, 10)
                .background(VaultTheme.paper.opacity(0.85))
                .clipShape(RoundedRectangle(cornerRadius: 4, style: .continuous))
        }
    }
}
