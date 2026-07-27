import Foundation

struct BERTQuoteClient: Sendable {
    enum ClientError: LocalizedError {
        case invalidResponse
        case server(status: Int)
        case wrongAsset

        var errorDescription: String? {
            switch self {
            case .invalidResponse: "The quote service returned an invalid response."
            case .server(let status): "The quote service returned HTTP \(status)."
            case .wrongAsset: "The quote did not match the pinned BERT mint."
            }
        }
    }

    private static let expectedMint = "HgBRWfYxEfvPhtqkaeymCQtHCrKE46qQ43pKe8HCpump"

    func fetch() async throws -> BERTQuoteEnvelope {
        var request = URLRequest(url: BERTConfiguration.quoteURL)
        request.timeoutInterval = 8
        request.setValue("application/json", forHTTPHeaderField: "Accept")

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw ClientError.invalidResponse
        }
        guard (200..<300).contains(http.statusCode) else {
            throw ClientError.server(status: http.statusCode)
        }

        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        let envelope = try decoder.decode(BERTQuoteEnvelope.self, from: data)
        guard envelope.asset.chain == "solana",
              envelope.asset.mint == Self.expectedMint else {
            throw ClientError.wrongAsset
        }
        return envelope
    }
}
