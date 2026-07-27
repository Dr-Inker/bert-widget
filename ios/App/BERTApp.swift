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

    private let client = BERTQuoteClient()
    private let store = BERTQuoteStore()

    init() {
        quote = store.load()
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
}
