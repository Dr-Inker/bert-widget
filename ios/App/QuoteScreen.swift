import SwiftUI

struct QuoteScreen: View {
    @StateObject private var model = QuoteViewModel()

    var body: some View {
        NavigationStack {
            Group {
                if let quote = model.quote {
                    quoteContent(quote)
                } else if model.isLoading {
                    ProgressView("Loading BERT…")
                } else {
                    ContentUnavailableView(
                        "BERT quote unavailable",
                        systemImage: "wifi.exclamationmark",
                        description: Text(model.errorMessage ?? "Pull to try again.")
                    )
                }
            }
            .navigationTitle("$BERT")
            .toolbar {
                Button {
                    Task { await model.refresh() }
                } label: {
                    Image(systemName: "arrow.clockwise")
                }
                .disabled(model.isLoading)
                .accessibilityLabel("Refresh quote")
            }
        }
        .task { await model.refresh() }
        .onOpenURL { url in
            guard url.scheme == "bertwidget" else { return }
            Task { await model.refresh() }
        }
    }

    private func quoteContent(_ envelope: BERTQuoteEnvelope) -> some View {
        List {
            Section {
                HStack(spacing: 16) {
                    Image("bert_token")
                        .resizable()
                        .scaledToFill()
                        .frame(width: 58, height: 58)
                        .clipShape(Circle())
                        .accessibilityLabel("BERT token")
                    VStack(alignment: .leading) {
                        Text(BERTFormatters.price(envelope.quote.priceUsd))
                            .font(.title.bold())
                        Text(BERTFormatters.percent(envelope.quote.change24hPct) + " today")
                            .foregroundStyle(changeColor(envelope.quote.change24hPct))
                    }
                }
                .padding(.vertical, 8)
            }

            Section("Market") {
                metric("Market cap", envelope.quote.marketCapUsd)
                metric("24h volume", envelope.quote.volume24hUsd)
                metric("Liquidity", envelope.quote.liquidityUsd)
            }

            Section("Your BERT") {
                HoldingsEditor(
                    amount: model.holdings,
                    price: envelope.quote.priceUsd,
                    onSave: model.saveHoldings
                )
            }

            Section("Data") {
                LabeledContent("Updated") {
                    Text(envelope.source.observedAt, style: .relative)
                }
                LabeledContent("Source", value: "DEX Screener · \(envelope.source.dex.capitalized)")
                if envelope.isStale {
                    Label("Showing cached data", systemImage: "clock.badge.exclamationmark")
                        .foregroundStyle(.orange)
                }
            }
        }
        .refreshable { await model.refresh() }
        .safeAreaInset(edge: .bottom) {
            Text("Market data is informational and may be delayed.")
                .font(.caption)
                .foregroundStyle(.secondary)
                .padding(8)
        }
    }

    private func metric(_ label: String, _ value: Double?) -> some View {
        LabeledContent(label, value: BERTFormatters.compactUSD(value))
    }

    private func changeColor(_ value: Double?) -> Color {
        guard let value else { return .secondary }
        return value >= 0 ? .green : .red
    }
}

private struct HoldingsEditor: View {
    let price: Double
    let onSave: (Double?) -> Void

    @State private var input: String

    init(amount: Double?, price: Double, onSave: @escaping (Double?) -> Void) {
        self.price = price
        self.onSave = onSave
        _input = State(initialValue: amount.map { BERTFormatters.tokenAmount($0).replacingOccurrences(of: ",", with: "") } ?? "")
    }

    private var parsedAmount: Double? {
        let normalized = input.replacingOccurrences(of: ",", with: "").trimmingCharacters(in: .whitespaces)
        guard let value = Double(normalized), value.isFinite, value >= 0 else { return nil }
        return value
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            TextField("BERT amount", text: $input)
                .keyboardType(.decimalPad)
                .textInputAutocapitalization(.never)
                .accessibilityLabel("BERT holdings amount")

            if input.isEmpty {
                Text("Enter an amount to show its live USD value in the medium widget.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            } else if let amount = parsedAmount {
                LabeledContent("Current value", value: BERTFormatters.compactUSD(amount * price))
            } else {
                Text("Enter a valid non-negative number.")
                    .font(.caption)
                    .foregroundStyle(.red)
            }

            HStack {
                Button("Save to widget") { onSave(parsedAmount) }
                    .buttonStyle(.borderedProminent)
                    .disabled(!input.isEmpty && parsedAmount == nil)
                if !input.isEmpty {
                    Button("Remove", role: .destructive) {
                        input = ""
                        onSave(nil)
                    }
                }
            }
        }
        .padding(.vertical, 4)
    }
}

#Preview {
    QuoteScreen()
}
