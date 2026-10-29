import SwiftUI
import XCTest

/// Components.swift is also compiled into this test bundle, so this renders the production view.
/// Accessibility existence checks alone cannot detect white symbols on a white forecast card.
@MainActor
final class WeatherSymbolRenderingTests: XCTestCase {
    private let conditions = [
        "Clear", "MostlyClear", "PartlyCloudy", "Overcast", "Fog", "Drizzle",
        "Rain", "Snow", "Showers", "Thunderstorm", "Unknown"
    ]

    func testForecastSymbolsHaveVisiblePixelsInBothColorSchemes() throws {
        for scheme in [ColorScheme.light, .dark] {
            for size: CGFloat in [24, 32] {
                for condition in conditions {
                    for isDay in [true, false] {
                        let view = WeatherSymbol(condition: condition, isDay: isDay, size: size)
                            .padding(4).background(WeatherStyle.card)
                            .environment(\.colorScheme, scheme)
                        try assertVisible(view, label: "\(condition), day=\(isDay), \(scheme), size=\(size)")
                    }
                }
            }
            try attachContactSheet(scheme: scheme, hero: false)
        }
    }

    func testHeroSymbolsHaveVisiblePixelsInBothColorSchemes() throws {
        for scheme in [ColorScheme.light, .dark] {
            for condition in conditions {
                for isDay in [true, false] {
                    let view = WeatherSymbol(condition: condition, isDay: isDay, size: 55, onDarkBackground: true)
                        .padding(4).background(WeatherStyle.hero)
                        .environment(\.colorScheme, scheme)
                    try assertVisible(view, label: "hero: \(condition), day=\(isDay), \(scheme)")
                }
            }
        }
        try attachContactSheet(scheme: .light, hero: true)
    }

    private func render<V: View>(_ view: V) throws -> CGImage {
        let renderer = ImageRenderer(content: view)
        renderer.scale = 2
        return try XCTUnwrap(renderer.cgImage)
    }

    private func assertVisible<V: View>(_ view: V, label: String,
                                      file: StaticString = #filePath, line: UInt = #line) throws {
        let image = try render(view)
        let width = image.width, height = image.height
        var pixels = [UInt8](repeating: 0, count: width * height * 4)
        let colorSpace = try XCTUnwrap(CGColorSpace(name: CGColorSpace.sRGB))
        try pixels.withUnsafeMutableBytes { bytes in
            let context = try XCTUnwrap(CGContext(data: bytes.baseAddress, width: width, height: height,
                                                 bitsPerComponent: 8, bytesPerRow: width * 4,
                                                 space: colorSpace,
                                                 bitmapInfo: CGBitmapInfo.byteOrder32Big.rawValue |
                                                     CGImageAlphaInfo.premultipliedLast.rawValue))
            context.draw(image, in: CGRect(x: 0, y: 0, width: width, height: height))
        }
        func luminance(at offset: Int) -> Double {
            func linear(_ value: UInt8) -> Double {
                let component = Double(value) / 255
                return component <= 0.04045 ? component / 12.92 : pow((component + 0.055) / 1.055, 2.4)
            }
            return 0.2126 * linear(pixels[offset]) + 0.7152 * linear(pixels[offset + 1]) + 0.0722 * linear(pixels[offset + 2])
        }
        let background = luminance(at: 0)
        let visiblePixels = stride(from: 0, to: pixels.count, by: 4).filter { offset in
            let foreground = luminance(at: offset)
            let contrast = (max(background, foreground) + 0.05) / (min(background, foreground) + 0.05)
            return contrast >= 3
        }.count
        // Require actual visible shape area, not a stray colored pixel or an accessible empty image.
        XCTAssertGreaterThan(Double(visiblePixels) / Double(width * height), 0.06, label, file: file, line: line)
    }

    private func attachContactSheet(scheme: ColorScheme, hero: Bool) throws {
        let view = VStack(alignment: .leading, spacing: 12) {
            Text(hero ? "Dark weather card" : "Forecast cards · \(scheme)").font(.headline)
            HStack { Text("Condition").frame(width: 130, alignment: .leading); Text("Day").frame(width: 56); Text("Night").frame(width: 56) }
            ForEach(conditions, id: \.self) { condition in
                HStack {
                    Text(condition).font(.caption).frame(width: 130, alignment: .leading)
                    WeatherSymbol(condition: condition, isDay: true, onDarkBackground: hero).frame(width: 56)
                    WeatherSymbol(condition: condition, isDay: false, onDarkBackground: hero).frame(width: 56)
                }
            }
        }
        .padding(20).foregroundStyle(hero ? Color.white : Color.primary)
        .background(hero ? WeatherStyle.hero : WeatherStyle.card)
        .environment(\.colorScheme, scheme)
        let attachment = XCTAttachment(image: UIImage(cgImage: try render(view)))
        attachment.name = hero ? "symbols-hero" : "symbols-\(scheme)"
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
