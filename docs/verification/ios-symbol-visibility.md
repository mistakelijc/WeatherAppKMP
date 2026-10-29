# iOS 天气图标可见性修复

日期：2026-10-29。验证设备为 iPhone Duo / iOS 27.1。

## 原因与修复

原来的 `WeatherSymbol` 使用 `.multicolor`，月亮、云朵等图层显示为白色，在白色预报卡片上几乎不可见。之前的界面流程测试只检查元素和文字存在，未检查像素对比度，因此没有捕捉到该问题。

现在组件采用 `.palette` 和显式前景颜色：浅色背景使用靛蓝月亮、蓝灰云朵和蓝色降水，深色背景使用较亮配色。当前天气的大卡片固定为深色，通过 `onDarkBackground` 明确选择对应配色；共享层的天气代码映射保持不变。

调色方式参考 [Apple 的 SF Symbols 渲染说明](https://developer.apple.com/documentation/uikit/configuring-and-displaying-symbol-images-in-your-ui)。

## 验证

- `WeatherSymbolRenderingTests`：2/2 通过。直接编译并渲染生产组件，覆盖 10 类天气及未知状态、昼夜、24/32 点预报尺寸、55 点当前天气尺寸、浅色/深色背景，共 132 种组合。检查实际渲染像素，要求有足够面积与背景达到至少 3:1 对比度，防止空白图标仅靠 accessibility 存在检查过关。
- `testGuangzhouHourlyForecastSymbols`：1/1 通过。真实请求广州天气，滚动实际天气 ScrollView，检查首个小时图标可见，并保存所有活动屏幕的截图。
- 人工核对实际展开屏幕截图：夜间小时预报的月亮、七天预报的阴天、毛毛雨、阵雨和雷雨图标可见。
- Xcode 编译、链接及模拟器安装通过。应用名称保持“leon的KMP天气应用”。

iPhone Duo 展开后，`XCUIScreen.main` 可能对应未使用的屏幕，因此截图改为遍历 `XCUIScreen.screens`。默认屏幕黑图和仅基于坐标范围的旋转检查不作为视觉验证证据；本次实际页面截图检查针对模拟器当前展开状态。

## 记录与截图

- [实际广州预报页面](../screenshots/ios27-symbols/guangzhou-forecast-fixed.png)
- [浅色图标样张](../screenshots/ios27-symbols/symbols-light.png)
- [深色图标样张](../screenshots/ios27-symbols/symbols-dark.png)
- [当前天气大卡片图标样张](../screenshots/ios27-symbols/symbols-hero.png)
- [渲染测试结果](ios-symbol-rendering-test-results.json)
- [页面测试结果](ios-symbol-ui-test-results.json)

完整测试结果包：`iosApp/build/ios27-symbol-rendering.xcresult` 和 `iosApp/build/ios27-symbol-live-page.xcresult`。本次没有重跑无改动的 Android/shared 测试。

```bash
xcodebuild -project iosApp/WeatherApp.xcodeproj -scheme WeatherAppKMP \
  -destination 'platform=iOS Simulator,name=iPhone Duo,OS=27.1' \
  -derivedDataPath iosApp/build/DerivedData \
  -only-testing:WeatherAppUITests/WeatherSymbolRenderingTests \
  -only-testing:WeatherAppUITests/WeatherFlowTests/testGuangzhouHourlyForecastSymbols \
  -parallel-testing-enabled NO -collect-test-diagnostics never \
  CODE_SIGNING_ALLOWED=NO test
```
