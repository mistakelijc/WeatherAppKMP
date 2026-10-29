import SwiftUI
import Shared

@MainActor
struct SearchView: View {
    @StateObject private var model = SearchModel()
    @State private var selectedLocation: Shared.Location?
    @State private var showingWeather = false
    @FocusState private var searchFocused: Bool

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    Label("leon的KMP天气应用", systemImage: "sun.max")
                        .font(.title2.bold()).foregroundStyle(WeatherStyle.accent)
                    Text("搜索一座城市，看看那里的天气。")
                        .font(.subheadline).foregroundStyle(.secondary)
                    HStack {
                        Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
                        TextField("搜索城市，如北京 / London", text: Binding(
                            get: { model.state.query }, set: { model.setQuery($0) }
                        ))
                        .focused($searchFocused)
                        .autocorrectionDisabled().textInputAutocapitalization(.never)
                        .submitLabel(.search).onSubmit { searchFocused = false; model.retry() }
                        .accessibilityIdentifier("city_search")
                        if !model.state.query.isEmpty {
                            Button { model.setQuery("") } label: { Image(systemName: "xmark.circle.fill") }
                                .accessibilityLabel("清除搜索")
                        }
                    }
                    .padding().background(WeatherStyle.card, in: RoundedRectangle(cornerRadius: 16))
                    searchContent
                    AttributionFooter()
                }
                .padding(24)
            }
            .background(WeatherStyle.background)
            .navigationBarTitleDisplayMode(.inline)
            .navigationDestination(isPresented: $showingWeather) {
                if let selectedLocation { WeatherView(location: selectedLocation) }
            }
            .accessibilityIdentifier("search_screen")
        }
        .tint(WeatherStyle.accent)
    }

    @ViewBuilder private var searchContent: some View {
        let state = model.state
        if state.phase == .idle {
            if !state.history.isEmpty {
                HStack {
                    Text("最近搜索").font(.headline)
                    Spacer()
                    Button("清空", action: model.clearHistory).font(.subheadline)
                }
                VStack(spacing: 0) {
                    ForEach(state.history, id: \.id) { city in
                        HStack {
                            cityButton(city)
                            Button { model.remove(city) } label: { Image(systemName: "xmark").padding(14) }
                                .accessibilityLabel("删除\(city.name)的历史记录")
                                .accessibilityIdentifier("remove_history_\(city.id)")
                        }
                    }
                }
                .background(WeatherStyle.card, in: RoundedRectangle(cornerRadius: 18))
            }
            if state.historyUpdateFailed {
                Text("历史记录保存失败，请重试。").font(.caption).foregroundStyle(.red)
            }
            Text("常用城市").font(.headline)
            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
                ForEach(model.popularLocations, id: \.id) { city in
                    cityButton(city).background(WeatherStyle.card, in: RoundedRectangle(cornerRadius: 18))
                }
            }
        } else if state.phase == .loading {
            ProgressView("正在搜索城市…").frame(maxWidth: .infinity).padding(32)
        } else if state.phase == .tooshort {
            StatusMessage(title: "请输入完整一些的城市名", message: "至少输入两个字符。")
        } else if state.phase == .empty {
            StatusMessage(title: "没有找到这座城市", message: "试试完整城市名、中文名或英文名。")
        } else if state.phase == .error {
            StatusMessage(title: "搜索暂时失败", message: state.errorMessage ?? "请稍后重试。", retry: model.retry)
        } else if state.phase == .results {
            Text("选择城市").font(.headline)
            ForEach(state.results, id: \.id) { city in
                cityButton(city).background(WeatherStyle.card, in: RoundedRectangle(cornerRadius: 18))
            }
        }
    }

    private func cityButton(_ city: Shared.Location) -> some View {
        Button {
            searchFocused = false
            selectedLocation = city
            model.select(city)
            showingWeather = true
        } label: {
            VStack(alignment: .leading, spacing: 6) {
                Text(city.name).font(.headline).foregroundStyle(.primary)
                Text(city.subtitle).font(.caption).foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading).padding(16)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("location_\(city.id)")
    }
}
