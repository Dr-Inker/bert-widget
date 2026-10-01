import Foundation

struct BERTQuoteStore: Sendable {
    private let key = "last-valid-bert-quote"

    func load() -> BERTQuoteEnvelope? {
        guard let defaults = UserDefaults(suiteName: BERTConfiguration.appGroup),
              let data = defaults.data(forKey: key) else { return nil }

        return try? BERTJSON.decoder().decode(BERTQuoteEnvelope.self, from: data)
    }

    func save(_ quote: BERTQuoteEnvelope) {
        guard let data = try? BERTJSON.encoder().encode(quote) else { return }
        UserDefaults(suiteName: BERTConfiguration.appGroup)?.set(data, forKey: key)
    }
}
