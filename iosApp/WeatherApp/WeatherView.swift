import SwiftUI
import Shared

@MainActor
struct WeatherView: View {
    let location: Shared.Location
    @StateObject private var model: WeatherModel
    @Environment(\.scenePhase) private var scenePhase

    init(location: Shared.Location) {
        self.location = location
        _model = StateObject(wrappedValue: WeatherModel(location: location))
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                Text(location.subtitle).font(.subheadline).foregroundStyle(.secondary)
                if model.state.phase == .loading {
                    ProgressView("正在获取天气…").frame(maxWidth: .infinity).padding(.vertical, 80)
                } else if let data = model.state.details {
                    if model.state.isRefreshing { ProgressView("正在刷新…") }
                    if let error = model.state.refreshErrorMessage {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("刷新失败，保留上次天气").font(.headline)
                            Text(error)
                            Text("天气数据时间：\(data.updatedAt)").font(.caption)
                            Button("重试", action: model.refresh)
                        }
                        .padding().frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color.orange.opacity(0.12), in: RoundedRectangle(cornerRadius: 16))
                    }
                    currentCard(data)
                    Text("未来 24 小时").font(.title3.bold())
                    if data.hours.isEmpty {
                        Text("暂无未来小时预报，请刷新后重试。").foregroundStyle(.secondary)
                    } else {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 12) {
                                ForEach(data.hours, id: \.id) { hour in hourCard(hour) }
                            }
                        }
                        if data.hours.count < 24 {
                            Text("当前可用 \(data.hours.count) 小时预报").font(.caption).foregroundStyle(.secondary)
                        }
                    }
                    Text("七天预报").font(.title3.bold())
                    if data.days.isEmpty {
                        Text("暂无每日预报，请刷新后重试。").foregroundStyle(.secondary)
                    } else {
                        VStack(spacing: 0) {
                            ForEach(data.days, id: \.id) { day in
                                HStack(spacing: 12) {
                                    VStack(alignment: .leading, spacing: 4) {
                                        Text(day.day).font(.subheadline.bold())
                                        Text(day.date).font(.caption).foregroundStyle(.secondary)
                                    }.frame(width: 50, alignment: .leading)
                                    WeatherSymbol(condition: day.conditionKey, size: 24)
                                    Text(day.conditionLabel).font(.subheadline)
                                    Spacer(minLength: 4)
                                    VStack(alignment: .trailing, spacing: 4) {
                                        Text("高 \(day.high)").font(.subheadline.bold())
                                        Text("低 \(day.low)").font(.caption).foregroundStyle(.secondary)
                                    }
                                }
                                .padding(14)
                            }
                        }.background(WeatherStyle.card, in: RoundedRectangle(cornerRadius: 20))
                        if data.days.count < 7 {
                            Text("当前可用 \(data.days.count) 天预报").font(.caption).foregroundStyle(.secondary)
                        }
                    }
                    Text("城市时区：\(data.timeZone)").font(.caption).foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity)
                    AttributionFooter()
                } else {
                    StatusMessage(title: "暂时无法获取天气", message: model.state.errorMessage ?? "请稍后重试。", retry: model.refresh)
                }
            }.padding(24)
        }
        .background(WeatherStyle.background)
        .navigationTitle(location.name).navigationBarTitleDisplayMode(.inline)
        .toolbar {
            Button(action: model.refresh) { Image(systemName: "arrow.clockwise") }
                .disabled(model.state.phase == .loading || model.state.isRefreshing)
                .accessibilityLabel("刷新天气")
        }
        .onAppear(perform: model.onForeground)
        .onChange(of: scenePhase) { if $0 == .active { model.onForeground() } }
        .accessibilityIdentifier("weather_screen")
    }

    private func currentCard(_ data: WeatherDetails) -> some View {
        VStack(alignment: .leading, spacing: 20) {
            Text("当前天气").font(.subheadline).opacity(0.8)
            HStack {
                VStack(alignment: .leading, spacing: 8) {
                    Text(data.temperature).font(.system(size: 58, weight: .light)).minimumScaleFactor(0.5).lineLimit(1)
                    Text(data.conditionLabel).font(.title3)
                }
                Spacer(minLength: 8)
                WeatherSymbol(condition: data.conditionKey, isDay: data.isDay, size: 55, onDarkBackground: true)
            }
            HStack { Text("最高 \(data.high)"); Spacer(); Text("最低 \(data.low)") }.font(.subheadline)
            Text("天气数据时间：\(data.updatedAt)").font(.caption).opacity(0.8)
            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 20) {
                metric("体感温度", data.apparentTemperature)
                metric("湿度", data.humidity)
                metric("风速", data.wind)
                metric("紫外线指数", data.uv)
            }.padding(16).background(Color.white.opacity(0.08), in: RoundedRectangle(cornerRadius: 16))
        }
        .foregroundStyle(.white).padding(24)
        .background(WeatherStyle.hero, in: RoundedRectangle(cornerRadius: 28))
    }

    private func metric(_ label: String, _ value: String) -> some View {
        VStack(spacing: 6) {
            Text(label).font(.caption).opacity(0.8)
            Text(value).font(.subheadline.bold())
        }.frame(maxWidth: .infinity)
    }

    private func hourCard(_ hour: HourViewData) -> some View {
        VStack(spacing: 10) {
            Text(hour.day).font(.caption).foregroundStyle(.secondary)
            Text(hour.hour).font(.subheadline.bold())
            WeatherSymbol(condition: hour.conditionKey, isDay: hour.isDay)
                .accessibilityLabel(hour.conditionLabel).accessibilityHidden(false)
                .accessibilityIdentifier("hourly_weather_symbol_\(hour.id)")
            Text(hour.temperature).font(.subheadline.bold())
            Text("降水概率").font(.caption2).foregroundStyle(.secondary)
            Text(hour.precipitation).font(.caption).foregroundStyle(WeatherStyle.accent)
        }
        .frame(width: 90).padding(.vertical, 16)
        .background(WeatherStyle.card, in: RoundedRectangle(cornerRadius: 20))
    }
}
