# zyfun-tv

基于 zyfun（Hiram-Wong/ZyPlayer）协议与数据模型重写的 **原生 Kotlin Android TV / 手机双端客户端**。

## 界面预览

> 截图为占位，待真机 / 模拟器验证后替换为实际界面图。

| 手机端 ZyfunPhone | 电视端 ZyfunTV | 双端版 ZyfunDuo |
| --- | --- | --- |
| ![ZyfunPhone](screenshots/phone-placeholder.svg) | ![ZyfunTV](screenshots/tv-placeholder.svg) | ![ZyfunDuo](screenshots/duo-placeholder.svg) |

## 下载

Release 附件提供三个变体的签名 APK（不随源码仓库提交）：

| 变体 | 应用名 | applicationId | 适用端 | 下载 |
| --- | --- | --- | --- | --- |
| `universal` | ZyfunDuo | `com.hoopoe.zyfun.duo` | 双端合一，运行时切换 | [Releases](https://github.com/ballad0324/zyfun-tv/releases/latest) |
| `phone` | ZyfunPhone | `com.hoopoe.zyfun.phone` | 手机端 | [Releases](https://github.com/ballad0324/zyfun-tv/releases/latest) |
| `tv` | ZyfunTV | `com.hoopoe.zyfun.tv` | 电视端 | [Releases](https://github.com/ballad0324/zyfun-tv/releases/latest) |

## 技术基线

| 项 | 版本 / 路径 |
|---|---|
| 语言 | Kotlin 2.0.21 |
| UI | Jetpack Compose 1.7.3 + Compose for TV (`androidx.tv:tv-material:1.0.0`) |
| 构建 | Gradle 8.9 + AGP 8.7.3 |
| JDK | 17 |
| SDK | compileSdk 35 / minSdk 21 / targetSdk 35，build-tools 36.0.0 |
| 播放器 | Media3 ExoPlayer 1.4.1（HLS/DASH/MP4） |
| 存储 | Room（待接入）+ DataStore Preferences 1.1.1 |
| 网络/序列化 | OkHttp 4.12.0 + kotlinx-serialization-json 1.7.3 + Coil 2.7.0 |

- 代码 namespace：`com.hoopoe.zyfuntv`；三产品变体 applicationId 分别为 `com.hoopoe.zyfun.duo`（双端）、`com.hoopoe.zyfun.phone`（手机）、`com.hoopoe.zyfun.tv`（电视），在 `app/build.gradle` 的 productFlavors 中配置。
- 仓库镜像：阿里云 public/central/google/releases/gradle-plugin + google()/mavenCentral()（见 `settings.gradle`）。

## 本机构建（命令行）

先设置 `JAVA_HOME`（JDK 17）与 `ANDROID_HOME`（Android SDK），再用本机 gradle 执行：

```powershell
$env:JAVA_HOME='<JDK17_HOME>'
$env:ANDROID_HOME='<ANDROID_SDK_HOME>'
$g='<GRADLE_BAT_OR_EXECUTABLE>'
& $g :app:dependencies --configuration debugCompileClasspath   # 仅解析依赖
& $g :app:assembleUniversalDebug                               # 出 debug APK（需明确要求时再跑）
```

三变体构建任务：`assembleUniversalDebug` / `assemblePhoneDebug` / `assembleTvDebug`，release 对应 `assemble<Flavor>Release`。根目录 `build-debug.ps1` / `build-release.ps1` / `verify-release.ps1` 可批量构建与校验，脚本内环境路径需按本机实际填写。

> 约定：改完功能先在模拟器/真机测试，不自动打包；仅在明确要求时构建版本。

## 目录结构（当前骨架）

```
app/src/main/
├── AndroidManifest.xml
├── java/com/hoopoe/zyfuntv/
│   ├── MainActivity.kt
│   ├── core/model/Enums.kt          # 站点/直播/解析/代理类型常量
│   ├── data/local/                  # Room 实体、DAO、AppDatabase
│   ├── data/prefs/SettingsRepository.kt  # DataStore 设置
│   ├── data/remote/model/TvboxModels.kt  # TVBox/Catvod 序列化模型
│   ├── data/repository/ConfigImporter.kt # 配置导入落库
│   ├── data/parser/ChannelParser.kt # M3U/TXT 频道解析
│   ├── player/PlayerController.kt   # ExoPlayer 封装 + 状态
│   ├── player/ZyfunPlayerView.kt    # Compose 播放视图
│   ├── ui/App.kt                    # 自适应脚手架（TV 侧栏 / 手机底栏）
│   ├── ui/nav/Dest.kt               # 目的地：影视/直播/解析/过刻/实验室/设置
│   ├── ui/component/TvNavRail.kt    # 电视端左侧导航（聚焦即选中）
│   ├── ui/component/PhoneNavBar.kt  # 手机端底部导航（预留）
│   ├── ui/screens/PlaceholderScreen.kt
│   ├── ui/screens/SettingScreen.kt  # 界面模式切换 + 分区占位
│   └── ui/theme/Theme.kt
└── res/values/
```

界面模式：`UiMode`（auto/tv/phone）持久化于 DataStore，默认电视端；自动模式按 leanback 特性判定，手机端布局预留。

后续可按 core-network / core-crypto / data-repo / player / feature-film / feature-live 等模块拆分。

## 许可

本项目为 zyfun（Hiram-Wong/ZyPlayer）的衍生作品，依 **AGPL-3.0** 协议开源，全文见 [LICENSE](LICENSE)；对外分发衍生版本时需保留本许可并以相同协议开放源码。
