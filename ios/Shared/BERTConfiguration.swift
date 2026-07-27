import Foundation

enum BERTConfiguration {
    static let appGroup = "group.global.bert.widget"

    static var quoteURL: URL {
        let rawValue = Bundle.main.object(forInfoDictionaryKey: "BERT_API_BASE_URL") as? String
        let base = rawValue?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""

        guard !base.isEmpty,
              let url = URL(string: base)?.appending(path: "v1/bert/quote") else {
            preconditionFailure("BERT_API_BASE_URL is missing or invalid")
        }
        return url
    }
}
