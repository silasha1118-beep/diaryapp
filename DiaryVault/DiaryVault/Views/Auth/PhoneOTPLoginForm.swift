import SwiftUI

/// Shared phone + SMS OTP fields wired to `SMSAuthProviderFactory` (Firebase when plist present).
/// Used by the post-onboarding gate and Settings so beginners can always find phone login.
struct PhoneOTPLoginForm: View {
    enum Chrome {
        /// Full-bleed fields on the auth gate scroll surface.
        case gate
        /// Compact fields inside a Settings `Form` section.
        case settings
    }

    @EnvironmentObject private var localization: LocalizationManager
    @EnvironmentObject private var account: AccountSession
    @EnvironmentObject private var appState: AppState

    var chrome: Chrome = .gate
    /// Called after a successful phone verify (Settings can clear local UI state).
    var onSignedIn: (() -> Void)? = nil

    @State private var dial: PhoneNumberNormalizer.RegionDial = .tw
    @State private var nationalNumber = ""
    @State private var otpCode = ""
    @State private var session: SMSCodeSession?
    @State private var isSending = false
    @State private var isVerifying = false
    @State private var errorMessage: String?
    @State private var infoMessage: String?

    private let sms = SMSAuthProviderFactory.make()

    var body: some View {
        VStack(alignment: .leading, spacing: chrome == .gate ? 12 : 10) {
            phoneStep

            if session != nil {
                otpStep
                    .transition(.opacity.combined(with: .move(edge: .bottom)))
            }

            if let infoMessage {
                Text(infoMessage)
                    .font(.system(size: 13))
                    .foregroundStyle(VaultTheme.inkMuted)
            }

            if let errorMessage {
                Text(errorMessage)
                    .font(.system(size: 14))
                    .foregroundStyle(VaultTheme.seal)
            }
        }
        .animation(.easeInOut(duration: 0.3), value: session != nil)
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

                TextField(localization.text("auth.phone.placeholder"), text: $nationalNumber)
                    .keyboardType(.phonePad)
                    .textContentType(.telephoneNumber)
                    .modifier(AuthFieldChrome(chrome: chrome))
                    .onChange(of: nationalNumber) { _, _ in
                        session = nil
                        otpCode = ""
                        errorMessage = nil
                        infoMessage = nil
                    }
            }

            Button {
                Task { await sendCode() }
            } label: {
                HStack {
                    if isSending {
                        ProgressView()
                            .tint(.white)
                    }
                    Text(
                        session == nil
                            ? localization.text("auth.phone.send")
                            : localization.text("auth.phone.resend")
                    )
                    .font(.system(size: 16, weight: .semibold))
                }
                .foregroundStyle(Color.white)
                .frame(maxWidth: .infinity)
                .frame(height: chrome == .gate ? 48 : 44)
                .background(VaultTheme.seal.opacity(canSend ? 1 : 0.4))
                .clipShape(RoundedRectangle(cornerRadius: 4, style: .continuous))
            }
            .disabled(!canSend || isSending)
            .buttonStyle(.plain)
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
                .modifier(AuthFieldChrome(chrome: chrome))

            Button {
                Task { await verifyCode() }
            } label: {
                HStack {
                    if isVerifying {
                        ProgressView()
                            .tint(.white)
                    }
                    Text(localization.text("auth.otp.verify"))
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
        nationalNumber.filter(\.isNumber).count >= 6
    }

    private var canVerify: Bool {
        session != nil && otpCode.filter(\.isNumber).count == 6
    }

    // MARK: - Actions

    private func sendCode() async {
        errorMessage = nil
        infoMessage = nil
        isSending = true
        defer { isSending = false }

        let e164 = PhoneNumberNormalizer.composeE164(dial: dial, nationalNumber: nationalNumber)
        do {
            let newSession = try await sms.sendCode(toPhoneE164: e164)
            withAnimation(.easeInOut(duration: 0.3)) {
                session = newSession
            }
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
            let user = try await sms.verify(code: otpCode, session: session)
            account.signInWithPhone(
                userID: user.userID,
                phoneE164: user.phoneE164,
                displayName: appState.displayName.isEmpty ? nil : appState.displayName
            )
            nationalNumber = ""
            otpCode = ""
            self.session = nil
            infoMessage = localization.text("settings.account.phone.success")
            onSignedIn?()
        } catch let error as SMSAuthError {
            errorMessage = mapSMSError(error)
        } catch {
            errorMessage = localization.text("auth.otp.error")
        }
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
        case .underlying(let detail):
            #if DEBUG
            return detail.isEmpty ? localization.text("auth.error") : detail
            #else
            return localization.text("auth.error")
            #endif
        }
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
