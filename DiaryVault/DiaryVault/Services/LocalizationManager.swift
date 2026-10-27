import Foundation
import Combine
import SwiftUI

enum AppLanguage: String, CaseIterable, Identifiable {
    case zhHant = "zh-Hant"
    case zhHans = "zh-Hans"
    case english = "en"

    var id: String { rawValue }

    var locale: Locale { Locale(identifier: rawValue) }

    var displayName: String {
        switch self {
        case .zhHant: return "繁體中文"
        case .zhHans: return "简体中文"
        case .english: return "English"
        }
    }

    var shortLabel: String {
        switch self {
        case .zhHant: return "繁中"
        case .zhHans: return "簡中"
        case .english: return "EN"
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
        let stored = defaults.string(forKey: key).flatMap(AppLanguage.init(rawValue:))
        let initial = stored ?? Self.preferredFromSystem()
        bundle = Self.loadBundle(for: initial)
        language = initial
    }

    func text(_ key: String) -> String {
        NSLocalizedString(key, tableName: "Localizable", bundle: bundle, value: key, comment: "")
    }

    private static func preferredFromSystem() -> AppLanguage {
        let preferred = Locale.preferredLanguages.first ?? "en"
        if preferred.hasPrefix("zh-Hans") || preferred.hasPrefix("zh-CN") {
            return .zhHans
        }
        if preferred.hasPrefix("zh") {
            return .zhHant
        }
        return .english
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
