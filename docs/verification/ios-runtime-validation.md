# iOS 27.1 运行验证

日期：2026-10-29。Xcode 27.1，iPhone Duo ARM64 模拟器，iOS 27.1（24A94425）。

后续发现并修复了白色预报卡片上的图标可见性问题，见[修复与像素回归记录](ios-symbol-visibility.md)。本文截图保留为首次运行记录。

## 结果

| 检查 | 结果 |
| --- | --- |
| 针对该模拟器构建、安装、实际启动 | 通过 |
| Kotlin/Native commonTest | 48/48 通过，0 失败、0 跳过 |
| SwiftUI XCUITest 集成测试 | 3/3 通过，0 失败、0 跳过；xcodebuild 返回 TEST SUCCEEDED |
| London 真实城市搜索及输入长度校验 | 通过 |
| 北京、上海、London 真实天气加载 | 通过，页面显示实际温度及天气指标 |
| 手动刷新、小时预报、七天预报、城市时区 | 通过 |
| 返回搜索页、前后台切换 | 通过 |
| 历史在终止并重启应用后保留 | 通过，使用 iOS DataStore 文件 |
| 历史单条删除、清空，常用城市保留 | 通过 |

界面测试访问真实 Open-Meteo 服务，覆盖 SwiftUI → Kotlin presenter → Darwin HTTP → 映射 → UI 的调用链。Native 测试使用 MockEngine / fake repository，覆盖确定性的错误、取消、竞争、跨日与夏令时逻辑。

第一次界面运行暴露的是测试选择器差异：iPhone Duo 在 iOS 27.1 将系统返回按钮放在独立 Toolbar 中，不能只查询 NavigationBar.buttons。测试已改为优先查找实际系统标识 BackButton。历史删除按钮增加了按城市 ID 命名的 accessibilityIdentifier，避免服务端返回“倫敦/伦敦”等显示名称差异影响测试定位。无需修改天气业务逻辑。

## 证据

- [Xcode 测试结果 JSON](ios-ui-test-results.json)
- [界面测试摘要](ios-ui-tests.txt)
- [Native 测试摘要](ios-native-tests.txt)
- [当前天气截图](../screenshots/ios27/beijing-current-weather.png)
- [小时预报截图](../screenshots/ios27/hourly-forecast.png)
- [七天预报截图](../screenshots/ios27/daily-forecast.png)
- [London 搜索截图](../screenshots/ios27/london-search.png)
- [重启后历史截图](../screenshots/ios27/persisted-history.png)

完整结果包保存在 `iosApp/build/ios27-ui-final.xcresult`，可用 Xcode 打开。Native HTML 报告在 `shared/build/reports/tests/iosSimulatorArm64Test/index.html`。

## 验证边界

本次覆盖 iPhone Duo 的 iOS 27.1 模拟器，尚未做 iOS 真机、其他尺寸/系统版本、VoiceOver、长时间运行与内存专项测试。当前 iOS 查询与导航为内存状态；跨进程验证针对城市历史，不代表查询文本或导航路径恢复。在线测试结果仅代表本次服务状态，不保证外部 API 永久可用。
