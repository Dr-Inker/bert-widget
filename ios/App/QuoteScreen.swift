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

            Section {
                BERTThemeStudio()
            } header: {
                Text("Theme Studio")
            } footer: {
                Text("Save a theme, then choose it from Settings → Wallpaper. Matching widget styles are planned for the signed iOS release.")
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

private struct PhoneTheme: Identifiable {
    let id: String
    let name: String
    let subtitle: String
    let homeResource: String
    let lockResource: String
    let accent: Color

    static let all: [PhoneTheme] = [
        PhoneTheme(id: "mayor", name: "Mayor Purple", subtitle: "BERT.GLOBAL · BIG ENERGY", homeResource: "wallpaper_mayor_purple_home", lockResource: "wallpaper_mayor_purple_lock", accent: Color(red: 0.55, green: 0.22, blue: 0.97)),
        PhoneTheme(id: "woofhub", name: "Woofhub Night", subtitle: "POWERED BY BERT", homeResource: "wallpaper_woofhub_night_home", lockResource: "wallpaper_woofhub_night_lock", accent: Color(red: 0.55, green: 0.22, blue: 0.97)),
        PhoneTheme(id: "berthalla", name: "Berthalla Nights", subtitle: "THE BERT ECOSYSTEM", homeResource: "wallpaper_berthalla_nights_home", lockResource: "wallpaper_berthalla_nights_lock", accent: Color(red: 1, green: 0.33, blue: 0.21)),
    ]
}

private struct BERTThemeStudio: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Make your iPhone unmistakably BERT.")
                .font(.headline)
            Text("Three complete looks for your Home and Lock Screens.")
                .font(.caption)
                .foregroundStyle(.secondary)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(alignment: .top, spacing: 14) {
                    ForEach(PhoneTheme.all) { theme in
                        themeCard(theme)
                    }
                }
            }
            .contentMargins(.horizontal, 1, for: .scrollContent)
        }
        .padding(.vertical, 6)
    }

    private func themeCard(_ theme: PhoneTheme) -> some View {
        VStack(alignment: .leading, spacing: 7) {
            Image(theme.lockResource)
                .resizable()
                .scaledToFill()
                .frame(width: 142, height: 300)
                .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
                .overlay {
                    RoundedRectangle(cornerRadius: 22, style: .continuous)
                        .strokeBorder(.white.opacity(0.16))
                }
                .accessibilityLabel("\(theme.name) wallpaper preview")

            Text(theme.name)
                .font(.subheadline.bold())
                .lineLimit(1)
            Text(theme.subtitle)
                .font(.system(size: 8, weight: .bold))
                .tracking(0.35)
                .foregroundStyle(.secondary)
                .lineLimit(1)

            if let lockURL = Bundle.main.url(forResource: theme.lockResource, withExtension: "png"),
               let homeURL = Bundle.main.url(forResource: theme.homeResource, withExtension: "png") {
                VStack(spacing: 6) {
                    ShareLink(item: lockURL) { Label("Lock", systemImage: "lock") }.buttonStyle(.borderedProminent).tint(theme.accent)
                    ShareLink(item: homeURL) { Label("Home", systemImage: "apps.iphone") }.buttonStyle(.bordered)
                }
                .frame(maxWidth: .infinity)
            }
        }
        .frame(width: 142)
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
