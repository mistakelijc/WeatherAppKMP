# WeatherApp → Kotlin Multiplatform 改造评估

评估日期：2026-10-29。依据是本地 WeatherApp 当前源码和本次实际迁移、编译、测试结果。原目录仅作为参考读取；新项目位于 WeatherAppKMP。

## 结论

整体复杂度为**中等，约 3/5**。原工程只有搜索与天气详情两个页面，构造函数注入、Repository 接口、DTO/领域模型以及测试已经齐备。数据与业务规则可以集中维护；新增 SwiftUI 界面、Swift/Kotlin 生命周期桥接和双端构建验证是主要增量成本。

按一名熟悉 Kotlin、协程、Compose 和 SwiftUI 的工程师估算，从原工程迁移到双端功能验收约 **7–12 人日**。这是工程工作量估计，不是本次自动化执行用时，也不包含等待 SDK 下载、商店审核和后续新需求。若此前没有 Swift/KMP 经验，建议按 2–3 周安排学习、实现和联调。

## 代码范围与共享边界

原工程的生产 Kotlin 源码为 19 个文件、约 1,427 行（含空行与注释，不含 XML、Gradle 和测试）。本次提取覆盖所有网络访问、DTO、领域模型、数据校验、时区转换、城市补查、历史记录读写、搜索防抖和天气刷新规则。

| 领域 | shared 的责任 | 平台层的责任 |
| --- | --- | --- |
| 网络 | Ktor 请求参数、JSON、超时、错误分类和 Repository | Android/JVM 选 OkHttp 引擎；iOS 选 Darwin 引擎 |
| 时间 | 城市当地日期、DST、小时/每日筛选、15 分钟过期策略、数据格式化 | 页面触发生命周期事件 |
| 历史 | Preferences DataStore、JSON 校验、原子更新、最多五条、去重/删除/清空 | 提供应用沙盒文件路径 |
| 搜索 | 300ms 防抖、协程取消、请求版本保护、中英文查询/“市”后缀补查 | 输入控件、键盘、导航 |
| 天气状态 | 初次加载、合并刷新、保留旧数据、过期/跨日刷新 | Compose StateFlow 订阅或 SwiftUI ObservableObject |
| UI | Swift 可直接使用的基础类型展示模型 | Compose / SwiftUI 各自布局、图标、无障碍和场景恢复 |

`shared/commonMain` 不依赖 Android Context、ViewModel、Compose、SwiftUI、Retrofit 或 `java.time`。DataStore 本身支持 KMP，因此无需把历史规则分别重写到 SharedPreferences 与 UserDefaults。两端数据保存在各自设备沙盒，不包含云同步。

## 实际实施内容

- 新增 `shared`、`androidApp`、`iosApp`，Gradle Wrapper 沿用 9.1.0，AGP 沿用 9.0.1。
- KMP 使用 `com.android.kotlin.multiplatform.library`；Android 应用仍独立使用 `com.android.application`。
- Kotlin 调整为 2.4.21：官方兼容表覆盖当前 Gradle/AGP 组合。其列出的 Xcode 为 26.4；本机安装的是 27.1，兼容性以实际构建结果为准。
- Retrofit 改为 Ktor 3.2.3，保留原 API 参数、25 个小时输入窗口、七天范围、摄氏度及 m/s 单位。
- `java.time` 改为 kotlinx-datetime 0.6.2，保留可注入时钟、城市当地跨日，以及每日数据按响应固定偏移还原日期的规则。
- 搜索与天气 ViewModel 中的业务规则提取为 `SearchStore` / `WeatherStore`。Android ViewModel 只拥有作用域、转发事件、保存查询状态。
- iOS 新增 SwiftUI 搜索、历史、天气详情、小时与七天预报、加载/错误/重试；当前查询与导航为内存状态，城市历史通过 DataStore 持久化。
- iOS 订阅回调在主线程发出，Swift 采用弱引用，ObservableObject 释放时取消订阅及协程作用域。无需额外引入 SKIE、KMP-NativeCoroutines 或共享 UI 框架。
- 提供 Xcode 工程、共享 Scheme 与 `embedAndSignAppleFrameworkForXcode` 构建脚本。

## 复杂度与工作量

| 工作项 | 难度 | 估计人日 | 主要原因 |
| --- | --- | ---: | --- |
| 多模块和双平台构建 | 中 | 0.5–1 | AGP/Kotlin/Gradle/Xcode 版本矩阵、framework 集成 |
| 网络与错误迁移 | 中 | 0.5–1 | Retrofit → Ktor、Darwin 与 JVM 异常类型差异 |
| 时间与映射迁移 | 中偏高 | 1–1.5 | DST 重复小时、响应偏移、缺失值和城市当地跨日 |
| 历史持久化 | 低至中 | 0.5–1 | 复用 DataStore，替换路径与 IO 异常 |
| 共享状态及 Swift 桥接 | 中 | 0.5–1.5 | 取消、主线程、回调释放、生命周期一致性 |
| Android 适配 | 低 | 0.5–1 | 大部分 Compose 页面直接复用 |
| SwiftUI 界面与导航 | 中 | 1.5–2.5 | 原工程没有 iOS 视图，需要独立实现 |
| 双端回归、设备验收和工程整理 | 中 | 1–2 | 网络、字体、前后台、导航恢复和构建验证 |
| 合计 | 中 | 6.5–11.5 | 排期取整约 7–12 人日 |

这份估算不包含定位权限、离线天气磁盘缓存、后台更新、Widget、推送、地图、账号、更多语言或商店发布；这些都不是原工程的现成功能。

## 本次迁移暴露的关键问题

1. **跨平台异常并不只是换 import。** Ktor 使用 `kotlinx.io.IOException`，DataStore 使用 `okio.IOException`；它们在 JVM 上都可对应 Java IOException，但在 Native 上是不同类型。网络和存储已分别处理，避免 Android 测试通过却在 iOS 离线时崩溃。
2. **每日与小时预报的时区处理不同。** 小时按真实 Instant；每日按接口返回的固定 offset 还原日期，不能统一套未来日期的 DST 偏移。
3. **原页面状态值得共享。** 防抖、旧请求覆盖保护、15 分钟过期、前台跨日、刷新失败保留数据均已迁入 shared，新增业务修正只需要改一份。
4. **Swift 不直接消费复杂 Flow/日期类型。** 展示模型保留必要的基础字段与稳定 ID；重复的 DST 小时不会造成 SwiftUI 列表 ID 冲突。
5. **工程验证必须双端执行。** JVM 测试与 Swift 语法检查不等价于 Kotlin/Native 链接、Swift 类型检查或 iOS 运行测试。

## 验证记录

最终结果见根目录 README 的验证表。测试包含 Ktor MockEngine 协议与异常、捕获的迈阿密 DST 数据、搜索竞争、历史持久化、刷新策略，以及 Compose 端到端界面流程。MockEngine 与界面 fake repository 测试不等同于真实服务可用性保证。

## 后续维护建议

新增数据规则优先写入 shared 并增加 commonTest；平台项目只接收状态、触发操作和处理导航生命周期。保留独立 iOS 和 Android 的 UI 验收。本次已补充 iOS 27.1 界面集成测试；上线前继续扩展 UI 边界场景覆盖、真机弱网/内存检查、应用图标与签名配置，以及按实际 SDK 使用情况检查隐私清单。

Android 使用独立 applicationId `com.leon.weather.kmp`，可与原应用共存。历史数据结构沿用原 JSON 字段，但新应用不会自动读取原 applicationId 的沙盒；若将来作为原 Android 应用升级发布，需要恢复原包名、签名及相应版本号后验证历史数据迁移。

## 官方资料

- [KMP 兼容性表](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html)
- [Android KMP library 插件](https://developer.android.com/kotlin/multiplatform/plugin)
- [DataStore 的 KMP 支持](https://developer.android.com/kotlin/multiplatform/datastore)
- [Ktor 客户端引擎](https://ktor.io/docs/client-engines.html)
- [Kotlin 与 Xcode 直接集成](https://kotlinlang.org/docs/multiplatform/multiplatform-direct-integration.html)
