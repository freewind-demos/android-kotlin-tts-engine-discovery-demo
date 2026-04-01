# Android：枚举本机 TTS 朗读引擎（含第三方）

## 简介

这个 Demo 装在真机上提供一个极简界面：用 `TextToSpeech.getEngines()` 填引擎下拉框，任选其一，对可编辑的中英文混合示例文本进行朗读，并用滑块调节语速。清单里仍保留对 `TTS_SERVICE` 的 `<queries>` 声明（Android 11+ 包可见性），与系统「文字转语音」能力一致。

## 快速开始

### 环境要求

本机安装 **JDK 17**（Android Gradle Plugin 官方推荐）。  
准备 **Android SDK**（Android Studio 或命令行 `sdkmanager` 均可）。  
真机或模拟器 **API 24+**。

### 运行

在项目根目录执行：

```bash
./gradlew assembleDebug
```

生成的 APK 路径一般为：

`app/build/outputs/apk/debug/app-debug.apk`

安装后桌面应用名是 **「TTS 试听」**（可在 `app/src/main/res/values/strings.xml` 里改 `app_name`）。

## 概念讲解

### 第一部分：为什么要写 `<queries>`（Android 11+）

从 Android 11 开始，应用默认 **看不到** 其它已安装应用的某些组件。若你的清单里不写对 `TTS_SERVICE` 的查询声明，`queryIntentServices` 返回的列表会明显偏短，**第三方朗读引擎可能整条消失**，这不是手机没装，而是 **包可见性** 挡住了。

本 Demo 在 `AndroidManifest.xml` 根节点下包含：

```xml
<queries>
    <intent>
        <action android:name="android.intent.action.TTS_SERVICE" />
    </intent>
</queries>
```

没有这一段时，你与「系统设置 → 文字转语音」里看到的列表可能对不齐，也会出现「我明明装了讯飞语记，为什么自己写的列表里没有」这类现象。

### 第二部分：`queryIntentServices` 与 `MATCH_DISABLED_COMPONENTS`

标准做法是 `Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE)` 交给 `PackageManager.queryIntentServices`。  
在 Android N（API 24）及以上，可以追加 `PackageManager.MATCH_DISABLED_COMPONENTS`，这样 **已被禁用但仍安装** 的 Service 仍可能出现，便于排查「引擎装上了但被关掉了」。

### 第三部分：`TextToSpeech.getEngines()`

`TextToSpeech` 构造成功后调用 `engines`（Java 里对应 `getEngines()`），得到的是框架认可的引擎信息列表，通常与系统「首选引擎」界面高度相关。  
若某个包 **只出现在 PackageManager 结果里、却不出现在 `getEngines()`** 里，说明它注册了 Service，但系统没有把它纳入「用户可切换的标准 TTS 引擎」集合，需要结合厂商实现看是否还支持用 `TextToSpeech(context, listener, "包名")` 等方式绑定。

### 第四部分：和「开源阅读」等 App 的差异

阅读类 App 除了标准 `TextToSpeech`，还可能：

- 自己的清单里正确声明了 `<queries>`，所以能扫到更多包；
- 接入厂商私有 SDK 或自有进程通信，不经过 `TTS_SERVICE`；
- 使用无障碍、悬浮窗等非标准路径播放。

因此 **本 Demo 只负责把系统标准路径上能看见的东西列清楚**，不把「和某某 App 行为 100% 一致」当作目标。

## 完整示例

核心逻辑在 `app/src/main/java/com/example/ttsenginediscovery/MainActivity.kt`：

- `queryFrameworkEngines()`：构造 `TextToSpeech(this) { }`，读取 `engines` 后立即 `shutdown()`，排序后填入 `Spinner`。
- `TextToSpeech(context, listener, 包名)`：按在下拉框中选中的引擎初始化并 `speak`；语速由 `setSpeechRate` 控制。

界面仅保留「重新扫描」「打开 TTS 设置」、引擎下拉、大字号多行输入、语速滑块与「朗读 / 停止」按钮。「打开 TTS 设置」会依次尝试常见厂商的 Intent，失败则提示进系统设置搜索「文字转语音」。

## 注意事项

包名是 `com.example.ttsenginediscovery`，与仓库里另一个「TTS 朗读 Queue」Demo 可以并存安装。  
若 Gradle 下载慢，可自行配置镜像或离线 Gradle 发行包。

## 完整讲解（中文）

Android 把「朗读引擎」能力收拢在 Intent 动作 `android.intent.action.TTS_SERVICE` 上。第三方 TTS 一般在 `AndroidManifest.xml` 里声明符合合同的 `Service` 并挂上该动作。若用 `PackageManager.queryIntentServices` 自行枚举，问得越完整（含 `<queries>`、必要时带 `MATCH_DISABLED_COMPONENTS`），能看见的已安装候选就越多；本 Demo 的主界面则直接使用框架筛选后的 `TextToSpeech.getEngines()`。

下文「第二部分」仍介绍 `PackageManager` 与 `getEngines()` 的差异，便于排查「为什么某个引擎在系统里能读、却不在你的下拉框里」：通常只有出现在 **`getEngines()`** 里的包名才能用 `TextToSpeech(..., engine)` 绑定试听。

**包可见性** 仍是 Android 11+ 的常见坑：若你扩展为自行枚举 `queryIntentServices(TTS_SERVICE)`，清单里需要 `<queries>`，否则第三方包会从查询结果里消失。
