import SwiftUI
import AuthenticationServices

struct AuthGateView: View {
    @EnvironmentObject private var localization: LocalizationManager
    @EnvironmentObject private var account: AccountSession
    @EnvironmentObject private var appState: AppState

    @State private var dial: PhoneNumberNormalizer.RegionDial = .tw
    @State private var nationalNumber = ""
    @State private var otpCode = ""
    @State private var session: SMSCodeSession?
    @State private var isSending = false
    @State private var isVerifying = false
    @State private var errorMessage: String?
    @State private var infoMessage: String?
    @State private var appeared = false
    @State private var showAppleBackup = false

    private let sms = SMSAuthProviderFactory.make()

    var body: some View {
        ZStack {
            VaultBackground()

            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    Spacer().frame(height: 24)

                    Text(localization.text("brand.name"))
                        .font(.system(size: 36, weight: .semibold, design: .serif))
                        .foregroundStyle(VaultTheme.ink)
                        .opacity(appeared ? 1 : 0)
                        .offset(y: appeared ? 0 : 10)

                    Text(localization.text("auth.headline"))
                        .font(.system(size: 22, weight: .regular, design: .serif))
                        .foregroundStyle(VaultTheme.ink)
                        .fixedSize(horizontal: false, vertical: true)

                    Text(localization.text("auth.body"))
                        .font(.system(size: 16))
                        .foregroundStyle(VaultTheme.inkMuted)
                        .lineSpacing(4)
                        .fixedSize(horizontal: false, vertical: true)

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

                    Divider()
                        .overlay(VaultTheme.line)
                        .padding(.vertical, 4)

                    Button {
                        withAnimation(.easeInOut(duration: 0.25)) {
                            showAppleBackup.toggle()
                        }
                    } label: {
                        Text(localization.text("auth.apple.backup"))
                            .font(.system(size: 14, weight: .medium))
                            .foregroundStyle(VaultTheme.inkMuted)
                    }
                    .buttonStyle(.plain)

                    if showAppleBackup {
                        SignInWithAppleButton(.signIn) { request in
                            request.requestedScopes = [.fullName, .email]
                        } onCompletion: { result in
                            handleApple(result)
                        }
                        .signInWithAppleButtonStyle(.black)
                        .frame(height: 48)
                        .clipShape(RoundedRectangle(cornerRadius: 4, style: .continuous))
                    }

                    #if DEBUG
                    Button {
                        account.signInLocally(displayName: appState.displayName.isEmpty ? nil : appState.displayName)
                    } label: {
                        Text(localization.text("auth.devLocal"))
                            .font(.system(size: 14, weight: .medium))
                            .foregroundStyle(VaultTheme.inkMuted)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                    }
                    .buttonStyle(.plain)
                    #endif

                    Spacer().frame(height: 40)
                }
                .padding(.horizontal, 28)
            }
        }
        .onAppear {
            withAnimation(.easeOut(duration: 0.7)) { appeared = true }
        }
    }

    // MARK: - Phone

    private var phoneStep: some View {
        VStack(alignment: .leading, spacing: 12) {
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
                    .padding(.horizontal, 12)
                    .padding(.vertical, 12)
                    .background(VaultTheme.paper.opacity(0.9))
                    .overlay(
                        RoundedRectangle(cornerRadius: 4, style: .continuous)
                            .stroke(VaultTheme.line, lineWidth: 1)
                    )
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
                .frame(height: 48)
                .background(VaultTheme.seal.opacity(canSend ? 1 : 0.4))
                .clipShape(RoundedRectangle(cornerRadius: 4, style: .continuous))
            }
            .disabled(!canSend || isSending)
            .buttonStyle(.plain)
        }
    }

    private var otpStep: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(localization.text("auth.otp.label"))
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(VaultTheme.inkMuted)

            TextField(localization.text("auth.otp.placeholder"), text: $otpCode)
                .keyboardType(.numberPad)
                .textContentType(.oneTimeCode)
                .padding(.horizontal, 12)
                .padding(.vertical, 12)
                .background(VaultTheme.paper.opacity(0.9))
                .overlay(
                    RoundedRectangle(cornerRadius: 4, style: .continuous)
                        .stroke(VaultTheme.line, lineWidth: 1)
                )

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
                .frame(height: 48)
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
                infoMessage = String(
                    format: localization.text("auth.stub.hint"),
                    hint
                )
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

    private func handleApple(_ result: Result<ASAuthorization, Error>) {
        switch result {
        case .failure(let error):
            let ns = error as NSError
            if ns.domain == ASAuthorizationError.errorDomain,
               ns.code == ASAuthorizationError.canceled.rawValue {
                errorMessage = nil
                return
            }
            errorMessage = localization.text("auth.apple.error")
        case .success(let authorization):
            guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential else {
                errorMessage = localization.text("auth.apple.error")
                return
            }
            let fullName = PersonNameComponentsFormatter().string(from: credential.fullName ?? PersonNameComponents())
            let trimmedName = fullName.trimmingCharacters(in: .whitespacesAndNewlines)
            account.signInWithApple(
                userID: credential.user,
                email: credential.email,
                fullName: trimmedName.isEmpty ? nil : trimmedName
            )
            if let name = account.fullName, !name.isEmpty, appState.displayName.isEmpty {
                appState.displayName = name
            }
            errorMessage = nil
        }
    }
}
