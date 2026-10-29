import SwiftUI

enum WeatherStyle {
    static let background = Color(.systemGroupedBackground)
    static let card = Color(.secondarySystemGroupedBackground)
    static let accent = Color(red: 0.09, green: 0.31, blue: 0.40)
    static let hero = Color(red: 0.086, green: 0.243, blue: 0.337)
}

struct WeatherSymbol: View {
    @Environment(\.colorScheme) private var colorScheme
    let condition: String
    var isDay = true
    var size: CGFloat = 32
    var onDarkBackground = false

    private var usesLightColors: Bool { onDarkBackground || colorScheme == .dark }

    private var sunColor: Color {
        usesLightColors ? Color(red: 1, green: 0.78, blue: 0.24) : Color(red: 0.68, green: 0.39, blue: 0.02)
    }

    private var moonColor: Color {
        usesLightColors ? Color(red: 0.75, green: 0.79, blue: 1) : Color(red: 0.29, green: 0.32, blue: 0.69)
    }

    private var cloudColor: Color {
        usesLightColors ? Color(red: 0.74, green: 0.83, blue: 0.91) : Color(red: 0.27, green: 0.39, blue: 0.47)
    }

    private var primaryColor: Color {
        switch condition {
        case "Clear", "MostlyClear": return isDay ? sunColor : moonColor
        default: return cloudColor
        }
    }

    private var detailColor: Color {
        switch condition {
        case "PartlyCloudy": return isDay ? sunColor : moonColor
        case "Drizzle", "Rain", "Showers", "Snow":
            return usesLightColors ? Color(red: 0.42, green: 0.77, blue: 1) : Color(red: 0.09, green: 0.40, blue: 0.68)
        case "Thunderstorm": return sunColor
        default: return primaryColor
        }
    }

    private var symbol: String {
        switch condition {
        case "Clear", "MostlyClear": return isDay ? "sun.max.fill" : "moon.stars.fill"
        case "PartlyCloudy": return isDay ? "cloud.sun.fill" : "cloud.moon.fill"
        case "Overcast": return "cloud.fill"
        case "Fog": return "cloud.fog.fill"
        case "Drizzle": return "cloud.drizzle.fill"
        case "Rain": return "cloud.rain.fill"
        case "Snow": return "cloud.snow.fill"
        case "Showers": return "cloud.heavyrain.fill"
        case "Thunderstorm": return "cloud.bolt.rain.fill"
        default: return "questionmark.circle"
        }
    }

    var body: some View {
        Image(systemName: symbol)
            // Multicolor weather symbols can use white moon/cloud layers on white cards.
            // Own both palette layers so every condition works on cards and the dark hero.
            .symbolRenderingMode(.palette)
            .foregroundStyle(primaryColor, detailColor)
            .font(.system(size: size))
            .frame(width: size + 12, height: size + 12)
            .accessibilityHidden(true)
    }
}

struct StatusMessage: View {
    let title: String
    let message: String
    var retry: (() -> Void)? = nil

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: "cloud").font(.largeTitle).foregroundStyle(.secondary)
            Text(title).font(.headline)
            Text(message).font(.subheadline).foregroundStyle(.secondary).multilineTextAlignment(.center)
            if let retry { Button("重试", action: retry).buttonStyle(.borderedProminent) }
        }
        .frame(maxWidth: .infinity).padding(.vertical, 32)
    }
}

struct AttributionFooter: View {
    var body: some View {
        VStack(spacing: 5) {
            Link("天气与地点数据：Open-Meteo", destination: URL(string: "https://open-meteo.com/")!)
            Link("CC BY 4.0 · 已进行中文映射、时间转换与数值处理", destination: URL(string: "https://creativecommons.org/licenses/by/4.0/")!)
        }
        .font(.caption2).foregroundStyle(.secondary)
        .frame(maxWidth: .infinity).padding(.vertical, 16)
    }
}
