import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var localization: LocalizationManager
    @EnvironmentObject private var appState: AppState

    var body: some View {
        NavigationStack {
            ZStack {
                VaultBackground()

                Form {
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
        }
    }
}
