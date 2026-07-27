import SwiftUI
import WidgetKit

struct BERTEntry: TimelineEntry {
    let date: Date
    let quote: BERTQuoteEnvelope?
    let message: String?
}

struct BERTProvider: TimelineProvider {
    private let client = BERTQuoteClient()
    private let store = BERTQuoteStore()

    func placeholder(in context: Context) -> BERTEntry {
        BERTEntry(date: Date(), quote: .preview, message: nil)
    }

    func getSnapshot(in context: Context, completion: @escaping (BERTEntry) -> Void) {
        completion(BERTEntry(
            date: Date(),
            quote: context.isPreview ? .preview : store.load(),
            message: nil
        ))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<BERTEntry>) -> Void) {
        Task {
            let entry: BERTEntry
            do {
                let quote = try await client.fetch()
                store.save(quote)
                entry = BERTEntry(date: Date(), quote: quote, message: nil)
            } catch {
                entry = BERTEntry(
                    date: Date(),
                    quote: store.load(),
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
                colors: [Color.orange.opacity(0.2), Color.black.opacity(0.03)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        }
        .widgetURL(URL(string: "bertwidget://quote"))
    }

    private func quoteView(_ envelope: BERTQuoteEnvelope) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Label("$BERT", systemImage: "pawprint.fill")
                    .font(.headline)
                    .foregroundStyle(.orange)
                Spacer()
                if envelope.isStale || entry.message != nil {
                    Image(systemName: "clock.badge.exclamationmark")
                        .foregroundStyle(.orange)
                        .accessibilityLabel("Cached quote")
                }
            }

            Text(BERTFormatters.price(envelope.quote.priceUsd))
                .font(.system(.title2, design: .rounded, weight: .bold))
                .minimumScaleFactor(0.75)

            Text(BERTFormatters.percent(envelope.quote.change24hPct) + " 24h")
                .font(.subheadline.bold())
                .foregroundStyle(changeColor(envelope.quote.change24hPct))

            if family == .systemMedium {
                HStack(spacing: 18) {
                    metric("Market cap", envelope.quote.marketCapUsd)
                    metric("Volume", envelope.quote.volume24hUsd)
                    metric("Liquidity", envelope.quote.liquidityUsd)
                }
                .padding(.top, 2)
            }

            Spacer(minLength: 0)
            Text(envelope.source.observedAt, style: .relative)
                .font(.caption2)
                .foregroundStyle(.secondary)
        }
    }

    private var unavailableView: some View {
        VStack(alignment: .leading, spacing: 8) {
            Image(systemName: "pawprint.fill")
                .font(.title)
                .foregroundStyle(.orange)
            Text("$BERT")
                .font(.headline)
            Text("Open the BERT app to load a quote.")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }

    private func metric(_ label: String, _ value: Double?) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label).font(.caption2).foregroundStyle(.secondary)
            Text(BERTFormatters.compactUSD(value)).font(.caption.bold())
        }
    }

    private func changeColor(_ value: Double?) -> Color {
        guard let value else { return .secondary }
        return value >= 0 ? .green : .red
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
    BERTEntry(date: .now, quote: .preview, message: nil)
}

#Preview(as: .systemMedium) {
    BERTWidget()
} timeline: {
    BERTEntry(date: .now, quote: .preview, message: nil)
}
