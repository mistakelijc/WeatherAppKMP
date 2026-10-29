import SwiftUI
import Shared

// The shared presenters deliver callbacks on Dispatchers.Main.immediate.
// Weak captures and explicit subscription disposal avoid a Swift/Kotlin retain cycle.
@MainActor
final class SearchModel: ObservableObject {
    private let presenter: IosSearchPresenter
    private var subscription: StateSubscription?
    @Published private(set) var state: SearchViewData
    let popularLocations: [Shared.Location]

    init() {
        let presenter = IosSearchPresenter()
        self.presenter = presenter
        state = presenter.state
        popularLocations = presenter.popularLocations
        subscription = presenter.watch { [weak self] state in self?.state = state }
    }

    func setQuery(_ query: String) { presenter.setQuery(query: query) }
    func retry() { presenter.retry() }
    func select(_ location: Shared.Location) { presenter.select(location: location) }
    func remove(_ location: Shared.Location) { presenter.removeHistory(id: location.id) }
    func clearHistory() { presenter.clearHistory() }
    deinit { subscription?.close(); presenter.close() }
}

@MainActor
final class WeatherModel: ObservableObject {
    private let presenter: IosWeatherPresenter
    private var subscription: StateSubscription?
    @Published private(set) var state: WeatherViewData

    init(location: Shared.Location) {
        let presenter = IosWeatherPresenter(location: location)
        self.presenter = presenter
        state = presenter.state
        subscription = presenter.watch { [weak self] state in self?.state = state }
    }

    func refresh() { presenter.refresh() }
    func onForeground() { presenter.onForeground() }
    deinit { subscription?.close(); presenter.close() }
}
