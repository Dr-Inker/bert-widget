import Foundation

struct BERTQuoteStore: Sendable {
    private let key = "last-valid-bert-quote"

    func load() -> BERTQuoteEnvelope? {
        guard let defaults = UserDefaults(suiteName: BERTConfiguration.appGroup),
              let data = defaults.data(forKey: key) else { return nil }

        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return try? decoder.decode(BERTQuoteEnvelope.self, from: data)
    }

    func save(_ quote: BERTQuoteEnvelope) {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        guard let data = try? encoder.encode(quote) else { return }
        UserDefaults(suiteName: BERTConfiguration.appGroup)?.set(data, forKey: key)
    }
}
