import SwiftUI
import WidgetKit

struct BERTEntry: TimelineEntry {
    let date: Date
    let quote: BERTQuoteEnvelope?
    let holdings: Double?
    let message: String?
}

struct BERTProvider: TimelineProvider {
    private let client = BERTQuoteClient()
    private let store = BERTQuoteStore()
    private let settings = BERTSettingsStore()

    func placeholder(in context: Context) -> BERTEntry {
        BERTEntry(date: Date(), quote: .preview, holdings: 1_000_000, message: nil)
    }

    func getSnapshot(in context: Context, completion: @escaping (BERTEntry) -> Void) {
        completion(BERTEntry(
            date: Date(),
            quote: context.isPreview ? .preview : store.load(),
            holdings: context.isPreview ? 1_000_000 : settings.loadHoldings(),
            message: nil
        ))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<BERTEntry>) -> Void) {
        Task {
            let entry: BERTEntry
            do {
                let quote = try await client.fetch()
                store.save(quote)
                entry = BERTEntry(date: Date(), quote: quote, holdings: settings.loadHoldings(), message: nil)
            } catch {
                entry = BERTEntry(
                    date: Date(),
                    quote: store.load(),
                    holdings: settings.loadHoldings(),
                    message: "Update delayed"
                )
            }

            let nextUpdate = Calendar.current.date(byAdding: .minute, value: 15, to: Date())!
            completion(Timeline(entries: [entry], policy: .after(nextUpdate)))
        }
    }
}

struct BERTWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: BERTEntry

    var body: some View {
        Group {
            if let quote = entry.quote {
                quoteView(quote)
            } else {
                unavailableView
            }
        }
        .containerBackground(for: .widget) {
            LinearGradient(
                colors: [Color(red: 0.03, green: 0.10, blue: 0.18), Color(red: 0.07, green: 0.22, blue: 0.34)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        }
        .widgetURL(URL(string: "bertwidget://quote"))
    }

    private func quoteView(_ envelope: BERTQuoteEnvelope) -> some View {
        VStack(alignment: .leading, spacing: family == .systemMedium ? 6 : 9) {
            HStack {
                Image("bert_token")
                    .resizable()
                    .scaledToFill()
                    .frame(width: family == .systemMedium ? 34 : 40, height: family == .systemMedium ? 34 : 40)
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                    .accessibilityLabel("BERT token")
                VStack(alignment: .leading, spacing: 1) {
                    Text("$BERT")
                        .font(.headline.bold())
                        .foregroundStyle(.white)
                    Text("BERTRAM THE POMERANIAN")
                        .font(.system(size: 8, weight: .semibold))
                        .tracking(0.5)
                        .foregroundStyle(.white.opacity(0.58))
                        .lineLimit(1)
                }
                Spacer()
                if envelope.isStale || entry.message != nil {
                    Image(systemName: "clock.badge.exclamationmark")
                        .foregroundStyle(Color(red: 1, green: 0.78, blue: 0.34))
                        .accessibilityLabel("Delayed quote")
                }
            }

            HStack(alignment: .top, spacing: 16) {
                VStack(alignment: .leading, spacing: 3) {
                    Text(BERTFormatters.price(envelope.quote.priceUsd))
                        .font(.system(.title2, design: .rounded, weight: .heavy))
                        .foregroundStyle(.white)
                        .minimumScaleFactor(0.65)
                        .lineLimit(1)
                    Text(BERTFormatters.percent(envelope.quote.change24hPct) + " · 24H")
                        .font(.caption.bold())
                        .foregroundStyle(changeColor(envelope.quote.change24hPct))
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                if family == .systemMedium {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("YOUR BERT")
                            .font(.system(size: 8, weight: .semibold))
                            .tracking(0.4)
                            .foregroundStyle(.white.opacity(0.55))
                        Text(BERTFormatters.holdingsUSD(tokens: entry.holdings, price: envelope.quote.priceUsd))
                            .font(.headline.bold())
                            .foregroundStyle(entry.holdings == nil ? amber : .white)
                        if let holdings = entry.holdings {
                            Text(BERTFormatters.tokenAmount(holdings) + " BERT")
                                .font(.system(size: 8, weight: .medium))
                                .foregroundStyle(.white.opacity(0.58))
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            }

            if family == .systemMedium {
                HStack(spacing: 12) {
                    metric("MARKET CAP", envelope.quote.marketCapUsd)
                    metric("24H VOLUME", envelope.quote.volume24hUsd)
                    metric("LIQUIDITY", envelope.quote.liquidityUsd)
                }
                .padding(.top, 2)
            }

            Spacer(minLength: 0)
            HStack(spacing: 4) {
                if envelope.isStale || entry.message != nil {
                    Text("DELAYED ·")
                        .foregroundStyle(Color(red: 1, green: 0.78, blue: 0.34))
                }
                Text("Updated")
                Text(envelope.source.observedAt, style: .relative)
            }
                .font(.caption2)
                .foregroundStyle(.white.opacity(0.58))
        }
    }

    private var unavailableView: some View {
        VStack(alignment: .leading, spacing: 8) {
            Image("bert_token")
                .resizable()
                .scaledToFill()
                .frame(width: 48, height: 48)
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                .accessibilityLabel("BERT token")
            Text("$BERT")
                .font(.headline.bold())
                .foregroundStyle(.white)
            Text("Open BERT to refresh the price.")
                .font(.caption)
                .foregroundStyle(.white.opacity(0.62))
        }
    }

    private func metric(_ label: String, _ value: Double?) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label)
                .font(.system(size: 8, weight: .semibold))
                .tracking(0.4)
                .foregroundStyle(.white.opacity(0.55))
            Text(BERTFormatters.compactUSD(value)).font(.caption.bold()).foregroundStyle(.white)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var amber: Color { Color(red: 1, green: 0.78, blue: 0.34) }

    private func changeColor(_ value: Double?) -> Color {
        guard let value else { return .white.opacity(0.55) }
        return value >= 0
            ? Color(red: 0.27, green: 0.88, blue: 0.60)
            : Color(red: 1, green: 0.42, blue: 0.48)
    }
}

@main
struct BERTWidget: Widget {
    let kind = "global.bert.widget.quote"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: BERTProvider()) { entry in
            BERTWidgetView(entry: entry)
        }
        .configurationDisplayName("BERT Price")
        .description("Track Bertram The Pomeranian at a glance.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

#Preview(as: .systemSmall) {
    BERTWidget()
} timeline: {
    BERTEntry(date: .now, quote: .preview, holdings: 1_000_000, message: nil)
}

#Preview(as: .systemMedium) {
    BERTWidget()
} timeline: {
    BERTEntry(date: .now, quote: .preview, holdings: 1_000_000, message: nil)
}
