import SwiftUI
import UIKit

/// Resign the current first responder (phone / number pads have no Return key).
enum KeyboardDismiss {
    static func resign() {
        UIApplication.shared.sendAction(
            #selector(UIResponder.resignFirstResponder),
            to: nil,
            from: nil,
            for: nil
        )
    }
}

extension View {
    /// Tap empty chrome / scroll surface to dismiss keyboard without stealing Button hits.
    func resignKeyboardOnBackgroundTap() -> some View {
        simultaneousGesture(
            TapGesture().onEnded { KeyboardDismiss.resign() }
        )
    }
}

/// Visual language: personal vault — warm paper, ink, soft depth. Not generic notes chrome.
enum VaultTheme {
    static let paper = Color(red: 0.96, green: 0.94, blue: 0.90)
    static let paperDeep = Color(red: 0.90, green: 0.87, blue: 0.81)
    static let ink = Color(red: 0.16, green: 0.14, blue: 0.12)
    static let inkMuted = Color(red: 0.40, green: 0.36, blue: 0.32)
    static let seal = Color(red: 0.55, green: 0.22, blue: 0.18) // quiet vermillion, not purple AI
    static let line = Color(red: 0.78, green: 0.74, blue: 0.68)
    static let mirrorBubble = Color(red: 0.93, green: 0.90, blue: 0.85)
    static let userBubble = Color(red: 0.88, green: 0.82, blue: 0.74)

    static var backgroundGradient: LinearGradient {
        LinearGradient(
            colors: [
                Color(red: 0.97, green: 0.95, blue: 0.91),
                Color(red: 0.92, green: 0.89, blue: 0.84),
                Color(red: 0.88, green: 0.86, blue: 0.80)
            ],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
    }
}

struct VaultBackground: View {
    var body: some View {
        ZStack {
            VaultTheme.backgroundGradient.ignoresSafeArea()
            // Soft fiber noise via layered ellipses — atmosphere without flat fill.
            GeometryReader { geo in
                Circle()
                    .fill(VaultTheme.paperDeep.opacity(0.45))
                    .frame(width: geo.size.width * 0.7)
                    .blur(radius: 60)
                    .offset(x: -geo.size.width * 0.2, y: -40)
                Circle()
                    .fill(VaultTheme.seal.opacity(0.06))
                    .frame(width: geo.size.width * 0.5)
                    .blur(radius: 50)
                    .offset(x: geo.size.width * 0.45, y: geo.size.height * 0.55)
            }
            .ignoresSafeArea()
        }
    }
}
