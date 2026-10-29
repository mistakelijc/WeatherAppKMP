import SwiftUI

@main
@MainActor
struct WeatherApplication: App {
    var body: some Scene {
        WindowGroup { SearchView() }
    }
}
