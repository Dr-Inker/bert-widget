import Foundation

enum BERTConfiguration {
    static let appGroup = "group.global.bert.widget"

    static var quoteURL: URL {
        let rawValue = Bundle.main.object(forInfoDictionaryKey: "BERT_QUOTE_URL") as? String
        let value = rawValue?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""

        guard !value.isEmpty, let url = URL(string: value) else {
            preconditionFailure("BERT_QUOTE_URL is missing or invalid")
        }
        return url
    }
}
