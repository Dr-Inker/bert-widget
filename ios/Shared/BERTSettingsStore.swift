import Foundation

struct BERTSettingsStore: Sendable {
    private let holdingsKey = "bert-holdings"

    func loadHoldings() -> Double? {
        guard let defaults = UserDefaults(suiteName: BERTConfiguration.appGroup),
              let rawValue = defaults.string(forKey: holdingsKey),
              let value = Double(rawValue), value.isFinite, value >= 0 else { return nil }
        return value
    }

    func saveHoldings(_ amount: Double?) {
        guard let defaults = UserDefaults(suiteName: BERTConfiguration.appGroup) else { return }
        if let amount, amount.isFinite, amount >= 0 {
            defaults.set(String(amount), forKey: holdingsKey)
        } else {
            defaults.removeObject(forKey: holdingsKey)
        }
    }
}
