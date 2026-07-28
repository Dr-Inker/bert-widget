import Foundation

struct BERTQuoteEnvelope: Codable, Equatable, Sendable {
    let asset: Asset
    let quote: Quote
    let source: Source
    let meta: Meta

    struct Asset: Codable, Equatable, Sendable {
        let chain: String
        let mint: String
        let name: String
        let symbol: String
    }

    struct Quote: Codable, Equatable, Sendable {
        let priceUsd: Double
        let change24hPct: Double?
        let marketCapUsd: Double?
        let volume24hUsd: Double?
        let liquidityUsd: Double?
    }

    struct Source: Codable, Equatable, Sendable {
        let name: String
        let pairAddress: String
        let dex: String
        let pairUrl: URL
        let observedAt: Date
    }

    struct Meta: Codable, Equatable, Sendable {
        let freshness: String
        let ageSeconds: Int
        let warning: String?
    }

    var isStale: Bool { meta.freshness != "fresh" }

    static let preview = BERTQuoteEnvelope(
        asset: .init(
            chain: "solana",
            mint: "HgBRWfYxEfvPhtqkaeymCQtHCrKE46qQ43pKe8HCpump",
            name: "Bertram The Pomeranian",
            symbol: "Bert"
        ),
        quote: .init(
            priceUsd: 0.009199,
            change24hPct: -3.51,
            marketCapUsd: 9_014_624,
            volume24hUsd: 77_204,
            liquidityUsd: 720_329
        ),
        source: .init(
            name: "dexscreener",
            pairAddress: "BmsZE6TkZYskyS1PatPKRyyazGdxWFxdia4BuvLg9AgY",
            dex: "raydium",
            pairUrl: URL(string: "https://dexscreener.com")!,
            observedAt: Date()
        ),
        meta: .init(freshness: "fresh", ageSeconds: 0, warning: nil)
    )
}

enum BERTFormatters {
    static func price(_ value: Double) -> String {
        value.formatted(
            .currency(code: "USD")
                .precision(.fractionLength(value < 0.01 ? 6 : 4))
        )
    }

    static func percent(_ value: Double?) -> String {
        guard let value else { return "—" }
        return value.formatted(
            .number.sign(strategy: .always()).precision(.fractionLength(2))
        ) + "%"
    }

    static func compactUSD(_ value: Double?) -> String {
        guard let value else { return "—" }
        return value.formatted(
            .currency(code: "USD")
                .notation(.compactName)
                .precision(.fractionLength(1))
        )
    }

    static func tokenAmount(_ value: Double?) -> String {
        guard let value else { return "—" }
        return value.formatted(.number.precision(.fractionLength(0...2)))
    }

    static func holdingsUSD(tokens: Double?, price: Double) -> String {
        guard let tokens else { return "Set holdings" }
        return compactUSD(tokens * price)
    }
}
