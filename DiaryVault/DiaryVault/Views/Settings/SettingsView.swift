import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var localization: LocalizationManager
    @EnvironmentObject private var appState: AppState
    @EnvironmentObject private var account: AccountSession
    @EnvironmentObject private var subscriptions: SubscriptionStore

    @State private var showPaywall = false

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
                            Button(role: .destructive) {
                                account.signOut()
                            } label: {
                                Text(localization.text("settings.account.signOut"))
                            }
                        } else {
                            Text(localization.text("settings.account.signedOut"))
                                .foregroundStyle(VaultTheme.inkMuted)
                        }

                        PhoneOTPLoginForm(chrome: .settings)
                            .listRowInsets(EdgeInsets(top: 12, leading: 16, bottom: 12, trailing: 16))
                    } header: {
                        Text(localization.text("settings.section.accountPhone"))
                    } footer: {
                        Text(localization.text("settings.account.phone.footer"))
                    }

                    Section {
                        LabeledContent(localization.text("settings.billing.plan")) {
                            Text(
                                subscriptions.isSubscribed
                                    ? localization.text("settings.billing.active")
                                    : localization.text("settings.billing.free")
                            )
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
            }
            .navigationTitle(localization.text("settings.title"))
            .sheet(isPresented: $showPaywall) {
                PaywallView()
                    .environmentObject(localization)
                    .environmentObject(subscriptions)
            }
        }
    }

    private var providerLabel: String {
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
}
