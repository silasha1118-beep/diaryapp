import SwiftUI

struct OnboardingView: View {
    @EnvironmentObject private var appState: AppState
    @EnvironmentObject private var localization: LocalizationManager

    @State private var name = ""
    @State private var step = 0
    @State private var appeared = false

    var body: some View {
        ZStack {
            VaultBackground()

            VStack(spacing: 0) {
                languageStrip
                    .padding(.top, 12)

                TabView(selection: $step) {
                    welcomePage.tag(0)
                    privacyPage.tag(1)
                    namePage.tag(2)
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
                .animation(.easeInOut, value: step)

                bottomBar
                    .padding(.horizontal, 24)
                    .padding(.bottom, 28)
            }
        }
        .onAppear {
            withAnimation(.easeOut(duration: 0.8)) { appeared = true }
        }
    }

    private var languageStrip: some View {
        HStack(spacing: 8) {
            ForEach(AppLanguage.allCases) { lang in
                Button {
                    withAnimation(.easeInOut(duration: 0.2)) {
                        localization.language = lang
                    }
                } label: {
                    Text(lang.shortLabel)
                        .font(.system(size: 13, weight: .medium, design: .serif))
                        .foregroundStyle(localization.language == lang ? VaultTheme.paper : VaultTheme.inkMuted)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(
                            Capsule()
                                .fill(localization.language == lang ? VaultTheme.ink : Color.clear)
                        )
                        .overlay(
                            Capsule()
                                .stroke(VaultTheme.line, lineWidth: localization.language == lang ? 0 : 1)
                        )
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 24)
        .frame(maxWidth: .infinity, alignment: .trailing)
    }

    private var welcomePage: some View {
        VStack(alignment: .leading, spacing: 20) {
            Spacer()
            Text(localization.text("brand.name"))
                .font(.system(size: 42, weight: .semibold, design: .serif))
                .foregroundStyle(VaultTheme.ink)
                .opacity(appeared ? 1 : 0)
                .offset(y: appeared ? 0 : 12)

            Text(localization.text("onboarding.welcome.headline"))
                .font(.system(size: 22, weight: .regular, design: .serif))
                .foregroundStyle(VaultTheme.ink)
                .fixedSize(horizontal: false, vertical: true)

            Text(localization.text("onboarding.welcome.body"))
                .font(.system(size: 16, weight: .regular, design: .default))
                .foregroundStyle(VaultTheme.inkMuted)
                .lineSpacing(4)
                .fixedSize(horizontal: false, vertical: true)

            Spacer()
        }
        .padding(.horizontal, 28)
    }

    private var privacyPage: some View {
        VStack(alignment: .leading, spacing: 20) {
            Spacer()
            Text(localization.text("onboarding.privacy.title"))
                .font(.system(size: 28, weight: .semibold, design: .serif))
                .foregroundStyle(VaultTheme.ink)

            bullet(localization.text("onboarding.privacy.point1"))
            bullet(localization.text("onboarding.privacy.point2"))
            bullet(localization.text("onboarding.privacy.point3"))

            Text(localization.text("onboarding.privacy.residency"))
                .font(.system(size: 14))
                .foregroundStyle(VaultTheme.inkMuted)
                .padding(.top, 8)

            Spacer()
        }
        .padding(.horizontal, 28)
    }

    private var namePage: some View {
        VStack(alignment: .leading, spacing: 20) {
            Spacer()
            Text(localization.text("onboarding.name.title"))
                .font(.system(size: 28, weight: .semibold, design: .serif))
                .foregroundStyle(VaultTheme.ink)

            Text(localization.text("onboarding.name.body"))
                .font(.system(size: 16))
                .foregroundStyle(VaultTheme.inkMuted)

            TextField(localization.text("onboarding.name.placeholder"), text: $name)
                .textFieldStyle(.plain)
                .font(.system(size: 20, design: .serif))
                .padding(.vertical, 12)
                .overlay(alignment: .bottom) {
                    Rectangle().fill(VaultTheme.line).frame(height: 1)
                }
                .autocorrectionDisabled()

            Spacer()
        }
        .padding(.horizontal, 28)
    }

    private var bottomBar: some View {
        HStack {
            if step > 0 {
                Button(localization.text("common.back")) {
                    withAnimation { step -= 1 }
                }
                .foregroundStyle(VaultTheme.inkMuted)
            }
            Spacer()
            Button {
                if step < 2 {
                    withAnimation { step += 1 }
                } else {
                    appState.completeOnboarding(name: name)
                }
            } label: {
                Text(step < 2 ? localization.text("common.continue") : localization.text("onboarding.enter"))
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(VaultTheme.paper)
                    .padding(.horizontal, 22)
                    .padding(.vertical, 12)
                    .background(VaultTheme.ink)
                    .clipShape(RoundedRectangle(cornerRadius: 4, style: .continuous))
            }
            .buttonStyle(.plain)
        }
    }

    private func bullet(_ text: String) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Circle()
                .fill(VaultTheme.seal)
                .frame(width: 6, height: 6)
                .padding(.top, 7)
            Text(text)
                .font(.system(size: 16))
                .foregroundStyle(VaultTheme.ink)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}
