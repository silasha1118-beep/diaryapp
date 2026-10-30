import Foundation
import Combine
import SwiftUI

enum AppLanguage: String, CaseIterable, Identifiable {
    case zhHant = "zh-Hant"
    case zhHans = "zh-Hans"
    case english = "en"
    case japanese = "ja"
    case thai = "th"

    var id: String { rawValue }

    var locale: Locale { Locale(identifier: rawValue) }

    var displayName: String {
        switch self {
        case .zhHant: return "繁體中文"
        case .zhHans: return "简体中文"
        case .english: return "English"
        case .japanese: return "日本語"
        case .thai: return "ไทย"
        }
    }

    var shortLabel: String {
        switch self {
        case .zhHant: return "繁中"
        case .zhHans: return "簡中"
        case .english: return "EN"
        case .japanese: return "JA"
        case .thai: return "TH"
        }
    }
}

@MainActor
final class LocalizationManager: ObservableObject {
    private let defaults = UserDefaults.standard
    private let key = "appLanguage"

    @Published var language: AppLanguage {
        didSet {
            defaults.set(language.rawValue, forKey: key)
            bundle = Self.loadBundle(for: language)
        }
    }

    private(set) var bundle: Bundle = .main

    var locale: Locale { language.locale }

    init() {
        // First launch: follow iOS system / App Store preferred language.
        // After the user picks a language in Settings or onboarding, that choice sticks.
        let stored = defaults.string(forKey: key).flatMap(AppLanguage.init(rawValue:))
        let initial = stored ?? Self.preferredFromSystem()
        bundle = Self.loadBundle(for: initial)
        language = initial
    }

    func text(_ key: String) -> String {
        NSLocalizedString(key, tableName: "Localizable", bundle: bundle, value: key, comment: "")
    }

    /// Maps system / App Store locale to an app language. Never forces zh-Hant.
    /// Uses `Bundle.preferredLocalizations` first (respects `CFBundleLocalizations`),
    /// then `Locale.preferredLanguages` (ja→Japanese, th→Thai, zh-Hans/Hant, en for MY/SG).
    private static func preferredFromSystem() -> AppLanguage {
        for id in Bundle.main.preferredLocalizations {
            if let lang = AppLanguage(rawValue: id) {
                return lang
            }
            if let mapped = mapSystemLanguageID(id) {
                return mapped
            }
        }
        for id in Locale.preferredLanguages {
            if let mapped = mapSystemLanguageID(id) {
                return mapped
            }
        }
        return .english
    }

    private static func mapSystemLanguageID(_ raw: String) -> AppLanguage? {
        let id = raw.replacingOccurrences(of: "_", with: "-")
        if let exact = AppLanguage(rawValue: id) {
            return exact
        }
        if id.hasPrefix("zh-Hans") || id.hasPrefix("zh-CN") || id.localizedCaseInsensitiveContains("Hans") {
            return .zhHans
        }
        if id.hasPrefix("zh-Hant")
            || id.hasPrefix("zh-TW")
            || id.hasPrefix("zh-HK")
            || id.hasPrefix("zh-MO")
            || id.localizedCaseInsensitiveContains("Hant") {
            return .zhHant
        }
        // Bare "zh" without script is ambiguous; do not assume Hant — fall through.
        if id.hasPrefix("ja") {
            return .japanese
        }
        if id.hasPrefix("th") {
            return .thai
        }
        if id.hasPrefix("en") {
            return .english
        }
        // Malay and other SEA languages: English covers MY / SG.
        if id.hasPrefix("ms") {
            return .english
        }
        return nil
    }

    private static func loadBundle(for language: AppLanguage) -> Bundle {
        if let path = Bundle.main.path(forResource: language.rawValue, ofType: "lproj"),
           let bundle = Bundle(path: path) {
            return bundle
        }
        return .main
    }
}

/// Convenience view helper for localized strings bound to `LocalizationManager`.
struct LText: View {
    @EnvironmentObject private var localization: LocalizationManager
    let key: String

    init(_ key: String) {
        self.key = key
    }

    var body: some View {
        Text(localization.text(key))
    }
}
