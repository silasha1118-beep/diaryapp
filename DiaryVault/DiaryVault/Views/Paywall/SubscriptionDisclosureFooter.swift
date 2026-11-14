import SwiftUI

/// Shared Guideline 3.1.2 footer for paywall + upgrade checkout:
/// restore, Privacy Policy, Terms (EULA), and auto-renewal disclosure.
struct SubscriptionDisclosureFooter: View {
    @EnvironmentObject private var localization: LocalizationManager
    @EnvironmentObject private var subscriptions: SubscriptionStore

    var showRestore: Bool = true

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            if showRestore {
                Button {
                    Task { await subscriptions.restore() }
                } label: {
                    Text(localization.text("paywall.restore"))
                        .font(.system(size: 14, weight: .medium))
                        .foregroundStyle(VaultTheme.inkMuted)
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.plain)
                .disabled(subscriptions.isLoading)
            }

            legalLinks

            Text(localization.text("paywall.disclosure"))
                .font(.system(size: 11))
                .foregroundStyle(VaultTheme.inkMuted)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 2)
        }
    }

    private var legalLinks: some View {
        HStack(spacing: 16) {
            if let privacy = LegalConfig.privacyPolicyURL {
                Link(localization.text("settings.legal.privacy"), destination: privacy)
                    .font(.system(size: 13, weight: .medium))
            }
            Link(
                localization.text("settings.legal.terms"),
                destination: LegalConfig.effectiveTermsURL
            )
            .font(.system(size: 13, weight: .medium))
        }
        .foregroundStyle(VaultTheme.seal)
    }
}

/// Bullet list of what a paid tier includes (Guideline 3.1.2 “what is included”).
struct SubscriptionBenefitsList: View {
    @EnvironmentObject private var localization: LocalizationManager

    let tier: BillingConfig.Tier

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            ForEach(benefitKeys, id: \.self) { key in
                HStack(alignment: .top, spacing: 8) {
                    Text("·")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(VaultTheme.seal)
                    Text(localization.text(key))
                        .font(.system(size: 13))
                        .foregroundStyle(VaultTheme.inkMuted)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }

    private var benefitKeys: [String] {
        switch tier {
        case .authorPlus:
            return ["paywall.plus.benefit1", "paywall.plus.benefit2", "paywall.plus.benefit3"]
        case .authorPro:
            return ["paywall.pro.benefit1", "paywall.pro.benefit2", "paywall.pro.benefit3"]
        case .free:
            return ["paywall.free.benefit1", "paywall.free.benefit2", "paywall.free.benefit3"]
        }
    }
}
