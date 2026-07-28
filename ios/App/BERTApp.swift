import SwiftUI
import WidgetKit

@main
struct BERTApp: App {
    var body: some Scene {
        WindowGroup {
            QuoteScreen()
        }
    }
}

@MainActor
final class QuoteViewModel: ObservableObject {
    @Published private(set) var quote: BERTQuoteEnvelope?
    @Published private(set) var errorMessage: String?
    @Published private(set) var isLoading = false
    @Published private(set) var holdings: Double?

    private let client = BERTQuoteClient()
    private let store = BERTQuoteStore()
    private let settings = BERTSettingsStore()

    init() {
        quote = store.load()
        holdings = settings.loadHoldings()
    }

    func refresh() async {
        guard !isLoading else { return }
        isLoading = true
        defer { isLoading = false }

        do {
            let latest = try await client.fetch()
            store.save(latest)
            quote = latest
            errorMessage = nil
            WidgetCenter.shared.reloadAllTimelines()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func saveHoldings(_ amount: Double?) {
        settings.saveHoldings(amount)
        holdings = amount
        WidgetCenter.shared.reloadAllTimelines()
    }
}
