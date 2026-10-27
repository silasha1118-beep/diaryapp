import Foundation

/// Local daily request counter stub (not a server-side quota).
enum MirrorUsageGuard {
    private static let dayKey = "mirrorAI.usageDay"
    private static let countKey = "mirrorAI.usageCount"

    /// Call before a cloud request. Throws ``MirrorAIError/rateLimited`` when the local daily cap is hit.
    static func checkAndIncrement() throws {
        let limit = AIConfig.dailyRequestLimit
        guard limit > 0 else { return }

        let defaults = UserDefaults.standard
        let today = dayStamp()
        let storedDay = defaults.string(forKey: dayKey)
        var count = defaults.integer(forKey: countKey)
        if storedDay != today {
            count = 0
            defaults.set(today, forKey: dayKey)
        }
        if count >= limit {
            throw MirrorAIError.rateLimited(limit)
        }
        defaults.set(count + 1, forKey: countKey)
    }

    private static func dayStamp() -> String {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone.current
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: Date())
    }
}
