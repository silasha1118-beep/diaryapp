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
    /// SwiftUI simultaneous tap — often ignored inside `Form`/`List` (UIKit scroll host wins).
    func resignKeyboardOnBackgroundTap() -> some View {
        simultaneousGesture(
            TapGesture().onEnded { KeyboardDismiss.resign() }
        )
    }

    /// Installs a UIKit tap on the enclosing scroll/table/window that always calls
    /// `UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder)…)`
    /// with `cancelsTouchesInView = false` so Buttons / tab bar still receive the hit.
    func resignKeyboardOnAnyTap() -> some View {
        background {
            KeyboardDismissTapInstaller()
                .frame(width: 0, height: 0)
                .allowsHitTesting(false)
                .accessibilityHidden(true)
        }
    }
}

/// Probe view that attaches a non-cancelling tap recognizer to the nearest scroll host or window.
private struct KeyboardDismissTapInstaller: UIViewRepresentable {
    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIView(context: Context) -> UIView {
        let view = UIView(frame: .zero)
        view.isUserInteractionEnabled = false
        view.backgroundColor = .clear
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        DispatchQueue.main.async {
            context.coordinator.install(from: uiView)
        }
    }

    static func dismantleUIView(_ uiView: UIView, coordinator: Coordinator) {
        coordinator.uninstall()
    }

    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        private weak var installedOn: UIView?
        private var recognizer: UITapGestureRecognizer?

        func install(from probe: UIView) {
            let host = Self.scrollHost(from: probe) ?? probe.window
            guard let host else { return }
            if installedOn === host, recognizer != nil { return }
            uninstall()

            let tap = UITapGestureRecognizer(target: self, action: #selector(handleTap))
            tap.cancelsTouchesInView = false
            tap.requiresExclusiveTouchType = false
            tap.delegate = self
            host.addGestureRecognizer(tap)
            recognizer = tap
            installedOn = host
        }

        func uninstall() {
            if let recognizer, let installedOn {
                installedOn.removeGestureRecognizer(recognizer)
            }
            recognizer = nil
            installedOn = nil
        }

        @objc private func handleTap() {
            KeyboardDismiss.resign()
        }

        func gestureRecognizer(
            _ gestureRecognizer: UIGestureRecognizer,
            shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
        ) -> Bool {
            true
        }

        /// Prefer UITableView / UICollectionView / UIScrollView so Form row taps resign.
        private static func scrollHost(from view: UIView) -> UIView? {
            var current: UIView? = view.superview
            while let cur = current {
                if cur is UITableView || cur is UICollectionView || cur is UIScrollView {
                    return cur
                }
                current = cur.superview
            }
            return nil
        }
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

/// Brand-first vault title block — large serif name, seal rule, quiet subtitle.
struct VaultBrandHeader: View {
    let title: String
    var subtitle: String? = nil
    var compact: Bool = false

    @State private var appeared = false

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(title)
                .font(.system(size: compact ? 34 : 42, weight: .semibold, design: .serif))
                .foregroundStyle(VaultTheme.ink)
                .tracking(compact ? -0.4 : -0.8)
                .lineLimit(1)
                .minimumScaleFactor(0.82)

            Capsule()
                .fill(VaultTheme.seal.opacity(0.9))
                .frame(width: compact ? 28 : 40, height: compact ? 2.5 : 3)
                .padding(.top, compact ? 12 : 16)
                .scaleEffect(x: appeared ? 1 : 0.35, anchor: .leading)
                .opacity(appeared ? 1 : 0)

            if let subtitle, !subtitle.isEmpty {
                Text(subtitle)
                    .font(.system(size: compact ? 14 : 15, weight: .regular))
                    .foregroundStyle(VaultTheme.inkMuted)
                    .lineSpacing(3)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, compact ? 12 : 14)
                    .opacity(appeared ? 1 : 0)
                    .offset(y: appeared ? 0 : 6)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .opacity(appeared ? 1 : 0)
        .offset(y: appeared ? 0 : 8)
        .accessibilityElement(children: .combine)
        .onAppear {
            withAnimation(.easeOut(duration: 0.65)) {
                appeared = true
            }
        }
    }
}
