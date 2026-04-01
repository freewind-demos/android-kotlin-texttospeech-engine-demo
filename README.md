# Android 本机 TTS 朗读（分段排队 + 调速）

## 简介

从开源阅读器 **Legado** 里抽出的「本地 TextToSpeech」用法：用系统安装的 TTS 引擎朗读文本，支持多段文本排队播放，并用 `setSpeechRate` 调速。本仓库是一个最小可运行的单页 Demo：输入多行文字，点「朗读」即按行分段送入引擎。

## 与 Legado 的对应关系

Legado 在 `TTSReadAloudService` 里对系统 TTS 做了这些关键事：

- 使用 `android.speech.tts.TextToSpeech`，可在设置里指定引擎包名（本 Demo 使用系统默认引擎）。
- 朗读列表里**第一段**用 `TextToSpeech.QUEUE_FLUSH`（清空队列再说），**后续段**用 `TextToSpeech.QUEUE_ADD`（排队），这样一段接一段播，不会只读最后一段。
- 语速：配置里存的是整数档位（SeekBar 0～45），实际调用  
  `setSpeechRate((progress + 5) / 10f)`  
  例如 progress 为 5 时倍率为 `1.0f`。与 `ReadAloudDialog`、`TTSReadAloudService.upSpeechRate` 一致。
- 若用户勾选「跟随系统」，Legado 会**不再**手动 `setSpeechRate`，而是让引擎用系统默认（必要时重新初始化 TTS）。本 Demo 在勾选「跟随系统」时会重启 `TextToSpeech` 实例，避免仍沿用上次手动倍数。

RSS/章节里「一句一句」的拆句在 Legado 里由正文分页/段落列表完成；本 Demo 用**换行**示意「多段」，你若要按句号切分，只需把输入预处理成多行即可。

## 快速开始

### 环境要求

- Android Studio Hedgehog 或更新（或兼容 AGP 8.1 的环境）
- JDK 17（与当前 Android Gradle Plugin 默认一致即可）
- 一台真机或模拟器，系统里至少装有可用的 TTS（如 Google 语音服务）

### 运行

在项目根目录执行：

```bash
./gradlew installDebug
```

或在 Android Studio 中打开本目录，选择 `app` 运行。

### 命令行只编译出 APK 文件（不安装到手机）

需要本机已安装 **JDK 17**、**Android SDK**，且能访问 Google Maven（构建时会下载依赖）。

在项目根目录执行**调试包**（默认 debug 签名，适合自测）：

```bash
cd /path/to/android-kotlin-texttospeech-engine-demo
./gradlew assembleDebug
```

生成的可安装文件路径为：

```text
app/build/outputs/apk/debug/app-debug.apk
```

若要打 **release** 包（当前模板里的 `release` 仍使用 debug 签名，仅作本地试装；上架需自行配置 `signingConfig`）：

```bash
./gradlew assembleRelease
```

产物一般在：

```text
app/build/outputs/apk/release/app-release.apk
```

把 `app-debug.apk` 或 `app-release.apk` 拷到手机，允许「安装未知来源应用」后即可安装。

## 概念讲解

### 第一部分：初始化与 OnInitListener

`TextToSpeech` 构造后会异步初始化，必须在 `OnInitListener.onInit` 收到 `SUCCESS` 后再 `speak`，否则可能无效。初始化成功后本 Demo 会 `setLanguage(Locale.getDefault())`，并挂上 `UtteranceProgressListener` 以便在状态栏文案里看到当前读到哪一段。

### 第二部分：QUEUE_FLUSH 与 QUEUE_ADD

第一段调用：

```kotlin
tts.speak(firstParagraph, TextToSpeech.QUEUE_FLUSH, null, "id_0")
```

后面每一段调用：

```kotlin
tts.speak(nextParagraph, TextToSpeech.QUEUE_ADD, null, "id_1")
```

若全部使用 `QUEUE_ADD` 且未先 `FLUSH`，旧队列里可能还有上一轮的句子；若全部 `FLUSH` 则只会保留最后一次调用。Legado 与健康实践都是：**第一句 FLUSH，其余 ADD**。

### 第三部分：语速 setSpeechRate

`setSpeechRate(1.0f)` 为文档所说的正常速度；大于 1 变快，小于 1 变慢。Legado 用 SeekBar 把整数档位映射到 `0.5×`～`5.0×` 左右（`(progress + 5) / 10f`，progress 最大 45）。Demo 里复用同一公式，并已限制 SeekBar `max=\"45\"`、`progress` 默认 5（即 1.0×）。

## 完整示例（核心逻辑摘录）

以下为 `MainActivity` 中与 Legado 最相关的片段说明（完整代码见源码）。

朗读入队：先 `stop()`，再对每一段选择 `QUEUE_FLUSH` 或 `QUEUE_ADD`，每段带唯一 `utteranceId`（API 21+），便于监听进度。

调速：在未勾选「跟随系统」时：

```kotlin
val rate = (seekBar.progress + 5) / 10f
textToSpeech.setSpeechRate(rate)
```

勾选「跟随系统」时：不再调用 `setSpeechRate`，并重启引擎以清除上次倍率。

## 注意事项

- 没有本机 TTS 或缺少某种语言的离线包时，`setLanguage` 可能返回 `LANG_MISSING_DATA`，需在系统设置里下载语音包。
- `speak(..., utteranceId)` 需要 API 21+；本 Demo `minSdk` 为 24。
- READ 大型作品时 Legado 还会配合前台服务、翻页与 `UtteranceProgressListener` 同步阅读位置；本 Demo 仅演示 **引擎调用与排队**，不包含书架与通知栏。

## 完整讲解（中文）

把阅读软件里的朗读功能想成「播放器」：系统 TTS 就是播一段段文字的声音引擎。Legado 先把当前页要读的内容拆成**很多小段**（类似小节或句子），然后把这些小段按顺序丢给 TTS。关键技术只有两点：第一点，**第一段要先清空队列**再读，不然会和上一轮没播完的混在一起；从第二段开始**只往队列后面加**。第二点，**语速**不是魔法参数，就是 Android 提供的 `setSpeechRate`，Legado 用滑块存一个整数，再按固定公式变成小数倍率，这样界面好看、保存配置也简单。如果你希望完全听系统里设的语速，就不要手动 `setSpeechRate`，或者用新创建的 `TextToSpeech`，否则引擎可能还记得上次设为很快的倍数。这个 Demo 故意做得很小：只有输入框、朗读、停止、语速滑块和「跟随系统」开关，方便你对照 Legado 的 `TTSReadAloudService` 和设置里的朗读面板理解整条链路。
