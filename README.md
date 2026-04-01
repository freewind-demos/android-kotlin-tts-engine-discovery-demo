# Android：枚举本机 TTS 朗读引擎（含第三方）

## 简介

这个 Demo 装在真机上会列出两类来源的「文字转语音」引擎：一是用 `PackageManager` 按系统标准动作 `android.intent.action.TTS_SERVICE` 去查所有声明了的 `Service`；二是框架提供的 `TextToSpeech.getEngines()`。两者对照着看，更容易理解为什么有的 App 列表里没有某个引擎（例如未声明包可见性、或引擎没按标准暴露 Service），而另一些阅读类 App 仍能用厂商能力。

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

安装后桌面应用名是 **「TTS 引擎列表」**（可在 `app/src/main/res/values/strings.xml` 里改 `app_name`）。

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

- `queryPackageManagerEngines()`：解析 `TTS_SERVICE`，合并 `GET_META_DATA` 与（API 24+）带 `MATCH_DISABLED_COMPONENTS` 的查询结果，按包名与服务名去重后展示。
- `queryFrameworkEngines()`：构造 `TextToSpeech(this) { }`，读取 `engines` 后立即 `shutdown()`。
- 第三段列表：**仅在 PackageManager 里出现的包名、却不在 `getEngines()` 里**，方便你重点核对（例如讯飞语记是否暴露标准接口、是否需进系统设置启用）。

界面用 `RecyclerView` 分节展示；按钮「打开 TTS 设置」会依次尝试常见厂商的 Intent，失败则提示用户进系统设置搜索「文字转语音」。

## 注意事项

包名是 `com.example.ttsenginediscovery`，与仓库里另一个「TTS 朗读 Queue」Demo 可以并存安装。  
若 Gradle 下载慢，可自行配置镜像或离线 Gradle 发行包。

## 完整讲解（中文）

这个小程序要解决的其实是一件事：**我到底能看见哪些「给系统当朗读引擎」的应用？** Android 把这类能力收拢在一个固定的 Intent 动作上：`android.intent.action.TTS_SERVICE`。凡是正经按文档做的第三方 TTS，一般会在自己的 `AndroidManifest.xml` 里声明一个继承 `TextToSpeechService`（或符合合同）的 `Service`，并挂上这个动作。我们的第一个列表就是用 `PackageManager` 去「问系统：谁声明了这个 Service？」——问得越完整（含 `<queries>`、必要时带禁用组件标志），你看到的第三方就越多。

第二个列表走的是 **`TextToSpeech` 框架**。它已经帮你在系统里做过筛选和排序，返回的是「框架认为可以当成引擎切换项」的那批。现实里会出现「PackageManager 能看见某个包，`getEngines()` 却没有」——这往往意味着：应用装了、甚至注册了 Service，但没有完全走入系统认可的那条产品线，或者用户从未在系统界面里启用过它。第三个区块把差集单独列出来，就是让你一眼盯住这种「灰色地带」。

最后提醒一点：**包可见性** 是 Android 11 之后非常容易踩的坑。你之前遇到「阅读软件能用讯飞、自己的小工具却列不出来」，第一件事应该是对照本 Demo 检查清单里有没有 `<queries>`；第二件事才是去怀疑讯飞有没有公开标准 TTS Service、或者是否要走厂商文档里的别的集成方式。这个仓库把路径写死、列表写实，就是想让你少在「是不是我代码写错了」上绕圈，多把时间花在「系统到底公开了哪些引擎」这件可被验证的事实上。
