import XCTest

/// Live integration tests: exercise SwiftUI, the Kotlin bridge, Darwin networking and DataStore.
/// These tests intentionally require Open-Meteo connectivity; commonTest covers deterministic failures.
@MainActor
final class WeatherFlowTests: XCTestCase {
    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(zh-Hans)", "-AppleLocale", "zh_CN"]
        app.launch()
        returnToSearch()
        if app.buttons["清除搜索"].exists { app.buttons["清除搜索"].tap() }
    }

    func testPopularCityWeatherRefreshAndForecasts() {
        app.buttons["location_1816670"].firstMatch.tap()
        assertWeatherLoaded()
        attachScreenshot("beijing-current-weather")
        let refreshButton = app.buttons["刷新天气"]
        refreshButton.tap()
        let refreshed = expectation(for: NSPredicate(format: "enabled == true"), evaluatedWith: refreshButton)
        wait(for: [refreshed], timeout: 35)
        XCTAssertTrue(app.staticTexts["当前天气"].exists)
        XCTAssertFalse(app.staticTexts["刷新失败，保留上次天气"].exists)

        scrollToText("未来 24 小时")
        XCTAssertTrue(app.staticTexts["降水概率"].firstMatch.exists)
        attachScreenshot("hourly-forecast")
        scrollToText("七天预报")
        scrollToText("城市时区：Asia/Shanghai")
        attachScreenshot("daily-forecast")

        XCUIDevice.shared.press(.home)
        app.activate()
        XCTAssertTrue(app.buttons["刷新天气"].waitForExistence(timeout: 10))
        returnToSearch()
        XCTAssertTrue(app.buttons["remove_history_1816670"].waitForExistence(timeout: 10))
    }

    func testEnglishSearchAndShortQueryValidation() {
        let field = app.textFields["city_search"]
        field.tap()
        field.typeText("B")
        XCTAssertTrue(app.staticTexts["请输入完整一些的城市名"].waitForExistence(timeout: 5))
        app.buttons["清除搜索"].tap()
        field.tap()
        field.typeText("London")
        let london = app.buttons["location_2643743"].firstMatch
        XCTAssertTrue(london.waitForExistence(timeout: 30), "London must be returned by the live geocoding API")
        attachScreenshot("london-search")
        london.tap()
        assertWeatherLoaded()
        returnToSearch()
        XCTAssertEqual(field.value as? String, "搜索城市，如北京 / London")
        XCTAssertTrue(app.buttons["remove_history_2643743"].exists)
    }

    func testGuangzhouHourlyForecastSymbols() {
        app.buttons["location_1809858"].firstMatch.tap()
        assertWeatherLoaded()
        let symbol = app.images.matching(NSPredicate(format: "identifier BEGINSWITH %@", "hourly_weather_symbol_")).firstMatch
        XCTAssertTrue(symbol.waitForExistence(timeout: 10))
        let scrollView = app.scrollViews["weather_screen"].firstMatch
        for _ in 0..<5 {
            if symbol.isHittable { break }
            scrollView.swipeUp()
        }
        attachScreenshot("guangzhou-hourly-forecast")
        XCTAssertTrue(symbol.isHittable, "The first hourly symbol must be visible in the current display")
    }

    func testHistorySurvivesRelaunchAndCanBeRemovedAndCleared() {
        if app.buttons["清空"].exists { app.buttons["清空"].tap() }
        app.buttons["location_1816670"].firstMatch.tap()
        assertWeatherLoaded()
        returnToSearch()
        let removeBeijing = app.buttons["remove_history_1816670"]
        XCTAssertTrue(removeBeijing.waitForExistence(timeout: 10))

        app.terminate()
        app.launch()
        returnToSearch()
        XCTAssertTrue(removeBeijing.waitForExistence(timeout: 10), "DataStore must persist history across app processes")
        attachScreenshot("persisted-history")
        removeBeijing.tap()
        XCTAssertTrue(removeBeijing.waitForNonExistence(timeout: 5))
        XCTAssertTrue(app.buttons["location_1816670"].firstMatch.exists)

        app.buttons["location_1796236"].firstMatch.tap()
        assertWeatherLoaded()
        returnToSearch()
        XCTAssertTrue(app.buttons["清空"].waitForExistence(timeout: 10))
        app.buttons["清空"].tap()
        XCTAssertTrue(app.staticTexts["最近搜索"].waitForNonExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["常用城市"].exists)
    }

    private func assertWeatherLoaded(file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertTrue(app.staticTexts["当前天气"].waitForExistence(timeout: 35),
                      "Live weather must load through the Darwin engine", file: file, line: line)
        XCTAssertTrue(app.staticTexts.matching(NSPredicate(format: "label MATCHES %@", "^-?[0-9]+°C$")).firstMatch.exists,
                      "A real temperature should be displayed", file: file, line: line)
        XCTAssertTrue(app.staticTexts["体感温度"].exists, file: file, line: line)
        XCTAssertTrue(app.staticTexts["紫外线指数"].exists, file: file, line: line)
    }

    private func returnToSearch() {
        if !app.textFields["city_search"].waitForExistence(timeout: 3) {
            // iOS 27.1 on iPhone Duo places BackButton in an adaptive Toolbar,
            // outside NavigationBar. Resolve its stable identifier first.
            let back = app.buttons["BackButton"]
            if back.exists { back.tap() }
            else if app.navigationBars.buttons.firstMatch.exists { app.navigationBars.buttons.firstMatch.tap() }
        }
        XCTAssertTrue(app.textFields["city_search"].waitForExistence(timeout: 10))
    }

    private func scrollToText(_ text: String) {
        let target = app.staticTexts[text]
        for _ in 0..<8 {
            if target.exists && target.isHittable { return }
            app.swipeUp()
        }
        XCTAssertTrue(target.exists && target.isHittable, "Expected visible text: \(text)")
    }

    private func attachScreenshot(_ name: String) {
        // The expanded iPhone Duo can put the app on a screen other than main.
        for (index, screen) in XCUIScreen.screens.enumerated() {
            let attachment = XCTAttachment(screenshot: screen.screenshot())
            attachment.name = "\(name)-screen-\(index)"
            attachment.lifetime = .keepAlways
            add(attachment)
        }
    }
}
