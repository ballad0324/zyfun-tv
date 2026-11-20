# zyfun-tv

**语言：** [中文](README.md) | [English](README.en.md)

参照上游 [Hiram-Wong/zyfun](https://github.com/Hiram-Wong/zyfun)（原 ZyPlayer，Electron + Vue3 + TypeScript 跨平台桌面播放器）的协议与数据模型，在 Android 原生侧重写的 **Kotlin + Jetpack Compose TV / 手机双端客户端**。

## 致谢与声明

- 感谢上游开发者提供的开源源代码项目，本项目在其协议与数据模型基础上完成 Android 端移植。
- 本项目定位为**移植骨架项目**；相对上游新拓展的功能，仅为在手机端向电视端设备同步配置数据信息。
- **版本声明**：本项目到此仅此一版，不再进行后续功能性开发，欢迎更多开发者在此基础上继续完善后续工作。
- 至此 zyfun 拥有了安卓手机客户端、安卓电视端，以及一个双端共存版本——两套 UI 布局、两套设置项。
- 首次开发仅用时 24 小时，由 AI 辅助补漏；功能与体验可能仍有不少缺失与 bug，敬请谅解。
- **本项目谢绝商用，仅供复刻、学习与研究。**
- 联系：98555576（平台请自行辨认）。

## 项目介绍

### 起源与关系

- **上游**：[Hiram-Wong/zyfun](https://github.com/Hiram-Wong/zyfun)（原 ZyPlayer），TypeScript + Electron + Vue3 的跨平台桌面端视频资源播放器，AGPL-3.0 协议。
- **本项目**：非代码级 fork，而是沿用上游的站点 / 直播 / 解析 / 网盘协议与数据模型，以原生 Android 技术栈重新实现；属上游的 AGPL-3.0 衍生作品。
- **定位**：同一套代码同时覆盖电视（Leanback）与手机（Phone），并拆为三个产品变体。

### 开发语言与技术栈

- **语言**：Kotlin（源文件全部为 `.kt`，无 Java）；Gradle 构建脚本使用 Groovy DSL（`.gradle`）。
- **UI**：Jetpack Compose（Material3 + Compose for TV `tv-material`），零 XML 布局；电视 / 手机各自独立主题、组件与页面。
- **播放器**：Media3 ExoPlayer 1.4.1（HLS / DASH / MP4）。
- **存储**：Room 2.6.1（KSP 编译）+ DataStore Preferences 1.1.1。
- **网络 / 序列化 / 图片**：OkHttp 4.12.0、kotlinx-serialization-json 1.7.3、Coil 2.7.0。
- **构建**：JDK 17、AGP 8.7.3、compileSdk / targetSdk 35、minSdk 21、build-tools 36.0.0。

### 功能模块

对齐上游的模块：影视站点（film）、直播 IPTV（live）、线路解析（parse）、过刻 / 短剧（moment）、实验室（lab）、设置（setting）、播放器（player）、网页浏览（WebView）。

Android 端落地与扩展：

- 双端界面：电视左侧栏导航 + 手机底部导航，双端独立主题与组件；`universal` 运行时自适应，`phone` / `tv` 强制端型。
- 榜单热榜：热榜抓取 + 搜索 / 解析兜底播放。
- 网盘资源：Alist 适配器 + WebDAV 客户端，资源管理器式浏览点播。
- 局域网同步：设备探测 + 客户端 / 服务端互推。
- 基础能力：深链唤起、JS 脚本执行、SM4 / AES / MAC 加解密、网页嗅探、TLS 兼容、屏内调试日志、配置备份 / 归档导入。
- 多解析适配器：Catvod、AppYsV2、AppGet、Xbpq、Xyq、T0Xml、Xmltv 等。

### 版本

- 当前版本：versionName `0.3.59`，versionCode `62`（内部构建递增号）。
- 本公开仓库首个 Release：`v0.3.59`；早期为本地迭代的 debug / release 测试包，未在此仓库保留历史节点。

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
| 存储 | Room + DataStore Preferences 1.1.1 |
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

## 目录结构

```
app/src/main/java/com/hoopoe/zyfuntv/
├── MainActivity.kt
├── core/
│   ├── archive/        # 配置备份 / 归档导入
│   ├── crypto/         # SM4 / AES / MAC 加解密
│   ├── debug/          # 屏内调试日志采集
│   ├── deeplink/       # 外部链接唤起解析
│   ├── model/          # 枚举与共享数据模型
│   ├── network/        # HTTP 抓取、TLS 兼容、网页嗅探
│   ├── notify/         # 通知
│   ├── player/         # 播放器外部意图
│   ├── script/         # JS 脚本执行
│   ├── sync/           # 局域网同步（设备探测 / 客户端 / 服务端）
│   └── webdav/         # WebDAV 客户端
├── data/
│   ├── local/          # Room 实体 / DAO / 数据库
│   ├── parser/         # 站点 / 直播 / 解析 / 网盘等适配器
│   ├── prefs/          # DataStore 设置
│   ├── remote/model/   # CMS / TVBox 序列化模型
│   └── repository/     # 仓库与配置导入
├── player/             # ExoPlayer 控制器与 Compose 视图
└── ui/
    ├── App.kt          # 双端自适应根壳
    ├── nav/            # 目的地定义
    ├── component/      # mobile / tv 双端组件
    ├── screens/        # mobile / tv 双端页面
    └── theme/          # mobile / tv 双端主题（色 / 形 / 字 / 尺寸）
```

界面模式：`UiMode`（auto/phone/tv）持久化于 DataStore，默认手机端；`universal` 变体运行时自适应，`phone` / `tv` 变体经 `FORCED_UI_MODE` 强制对应端型。

双端各自独立主题：`ui/theme/mobile` 与 `ui/theme/tv`；页面与组件按 `ui/screens/mobile`、`ui/screens/tv`、`ui/component/mobile`、`ui/component/tv` 拆分。

## 许可

本项目为 [Hiram-Wong/zyfun](https://github.com/Hiram-Wong/zyfun) 的衍生作品，依 **AGPL-3.0** 协议开源，全文见 [LICENSE](LICENSE)；对外分发衍生版本时需保留本许可并以相同协议开放源码。
