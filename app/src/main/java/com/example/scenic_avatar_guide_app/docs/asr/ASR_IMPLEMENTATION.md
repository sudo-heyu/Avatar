# Android 端 ASR 实现说明

> 版本：v2.0
> 日期：2026-04-28
> 状态：按当前 API 契约整理

---

## 1. 概述

本项目采用**讯飞 SparkChain SDK** 实现 Android 端本地语音识别。  
当前正式链路是：

```text
长按录音
→ 讯飞 SparkChain ASR 本地识别
→ 产出文本
→ 调用 POST /api/v1/chat/text
  或 POST /api/v1/chat/text/stream
```

因此，ASR 是 Android 端本地输入能力，不是独立的后端 HTTP 契约。

### 1.1 技术选型

| 项目 | 选择 | 说明 |
|------|------|------|
| SDK | 讯飞 SparkChain ASR | 国内领先的语音识别引擎 |
| 实现方式 | 客户端实时流式识别 | 低延迟，用户体验好 |
| 正式后端接口 | `POST /api/v1/chat/text/stream` | 识别结果统一走流式文本问答；返回 `text_delta`、`tts_segment` 等事件 |

### 1.2 功能特性

- ✅ 实时语音识别
- ✅ 音量可视化
- ✅ 长按录音、松开发送
- ✅ 上滑取消
- ✅ 震动反馈
- ✅ 权限动态申请
- ✅ 错误处理与重试

当前不属于正式联调范围：

- `POST /api/v1/chat/voice`
- `audio_url`
- 服务端回传音频

说明：`audio_url` 不作为 ASR 接口字段；在流式问答中，音频由 `tts_segment.audio_url` 随事件流返回，详见 `../api/STREAMING_REFACTOR_PLAN.md`。

---

## 2. 架构设计

### 2.1 文件结构

```
app/src/main/java/com/example/scenic_avatar_guide_app/
├── core/speech/
│   ├── SpeechRecognizerHelper.kt    # 语音识别辅助类
│   └── XunfeiAsrCallback.java       # 讯飞回调适配器
├── ui/screens/
│   ├── MainScreen.kt                # UI 组件（VoiceInputSection）
│   └── MainViewModel.kt             # 状态管理
└── libs/
    ├── SparkChain.aar               # 讯飞 SDK
    └── Codec.aar                    # 编解码库
```

### 2.2 类图

```
┌─────────────────────────┐
│   SpeechRecognizerHelper │
├─────────────────────────┤
│ - asr: ASR              │
│ - audioRecord: AudioRecord│
│ - isListening: Boolean  │
│ - callback: XunfeiAsrCallback│
├─────────────────────────┤
│ + startListening()      │
│ + stopListening()       │
│ + cancel()              │
│ + destroy()             │
│ + hasPermission()       │
└─────────────────────────┘
          │
          │ uses
          ▼
┌─────────────────────────┐
│   XunfeiAsrCallback     │
│   (implements AsrCallbacks)│
├─────────────────────────┤
│ - resultListener        │
│ - errorListener         │
│ - onEndOfSpeech         │
├─────────────────────────┤
│ + onResult()            │
│ + onError()             │
│ + onBeginOfSpeech()     │
│ + onEndOfSpeech()       │
└─────────────────────────┘
```

---

## 3. 核心实现

### 3.1 SpeechRecognizerHelper

语音识别辅助类，封装讯飞 SDK 和 AudioRecord。

```kotlin
class SpeechRecognizerHelper(
    private val context: Context,
    private val onResult: (String) -> Unit,           // 识别成功
    private val onError: (String) -> Unit,            // 错误
    private val onReadyForSpeech: () -> Unit,         // 准备就绪
    private val onEndOfSpeech: () -> Unit,            // 说话结束
    private val onVolumeChanged: ((Float) -> Unit)? = null  // 音量变化
)
```

### 3.2 ASR 配置参数

```kotlin
asr?.language("zh_cn")      // 语言：中文
asr?.domain("iat")          // 领域：通用听写
asr?.accent("mandarin")     // 方言：普通话
asr?.vinfo(true)            // 启用语音信息
asr?.dwa("wpgs")            // 动态修正
```

### 3.3 音频录制配置

```kotlin
companion object {
    private const val SAMPLE_RATE = 16000  // 16kHz 采样率
}

audioRecord = AudioRecord(
    MediaRecorder.AudioSource.MIC,
    SAMPLE_RATE,
    AudioFormat.CHANNEL_IN_MONO,
    AudioFormat.ENCODING_PCM_16BIT,
    bufferSize
)
```

### 3.4 音量计算

通过 RMS（均方根）计算实时音量：

```kotlin
var sumSquares = 0.0
val sampleCount = read / 2
for (i in 0 until read step 2) {
    val sample = ((buffer[i].toInt() and 0xFF) or
                  (buffer[i + 1].toInt() shl 8)).toShort()
    sumSquares += sample.toDouble() * sample.toDouble()
}
val rms = sqrt(sumSquares / sampleCount)
val normalizedVolume = (rms / 32767.0).coerceIn(0.0, 1.0).toFloat()
```

---

## 4. UI 实现

### 4.1 当前主要交互组件

```kotlin
@Composable
private fun VoiceInputButton(
    isRecording: Boolean,
    isCancelZone: Boolean,
    onCancelZoneChange: (Boolean) -> Unit,
    onVoiceStart: () -> Unit,
    onVoiceStop: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
)
```

### 4.2 交互手势

使用 `pointerInput` 实现长按录音和上滑取消：

```kotlin
.pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown()
        vibrator?.vibrate(VibrationEffect.createOneShot(30, DEFAULT_AMPLITUDE))
        onVoiceStart()

        while (true) {
            val event = awaitPointerEvent()
            // 检测上滑取消区域
            if (currentPosition.y < cancelThreshold) {
                isCancelZone = true
            }
        }

        // 松开时决定发送或取消
        if (isCancelZone) onCancel() else onVoiceStop()
    }
}
```

### 4.3 波纹动画

```kotlin
repeat(3) { index ->
    val waveScale by infiniteTransition.animateFloat(
        0.6f, 1.4f,
        infiniteRepeatable(tween(1200, delayMillis = index * 200), Restart)
    )
    Box(Modifier
        .scale(waveScale * (1f + volumeLevel * 0.5f))
        .clip(CircleShape)
        .background(Primary.copy(waveAlpha * volumeLevel))
    )
}
```

---

## 5. 状态管理

### 5.1 MainViewModel 状态

```kotlin
// 语音输入模式
private val _voiceInputMode = MutableStateFlow(false)
val voiceInputMode: StateFlow<Boolean> = _voiceInputMode.asStateFlow()

// 录音状态
private val _isRecording = MutableStateFlow(false)
val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

// 音量级别
private val _volumeLevel = MutableStateFlow(0f)
val volumeLevel: StateFlow<Float> = _volumeLevel.asStateFlow()
```

### 5.2 识别结果处理

```kotlin
fun onSpeechRecognized(text: String) {
    _isRecording.value = false
    _volumeLevel.value = 0f
    if (text.isNotBlank()) {
        addMessage(text, isUser = true)
        sendMessageToBackend(text)  // 统一通过 chat/text/stream 发送
    }
    viewModelScope.launch {
        delay(300)
        _voiceInputMode.value = false
    }
}
```

---

## 6. 权限处理

### 6.1 AndroidManifest.xml

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.VIBRATE" />
```

### 6.2 动态权限申请

```kotlin
var hasAudioPermission by remember {
    mutableStateOf(ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED)
}

val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
) { isGranted ->
    hasAudioPermission = isGranted
    if (isGranted) viewModel.enterVoiceInputMode()
}
```

---

## 7. 错误处理

### 7.1 错误类型

| 错误 | 处理方式 |
|------|----------|
| 权限缺失 | 引导用户授权 |
| SDK 初始化失败 | 显示错误提示 |
| 录音启动失败 | 显示错误提示 |
| 识别失败 | 回调 onError |
| 无识别结果 | 静默退出 |

### 7.2 错误回调

```kotlin
fun onSpeechError(error: String) {
    _isRecording.value = false
    _volumeLevel.value = 0f
    if (!error.contains("权限")) {
        addMessage("语音识别失败：$error", isUser = false)
    }
    viewModelScope.launch {
        delay(500)
        _voiceInputMode.value = false
    }
}
```

---

## 8. 资源管理

### 8.1 生命周期管理

```kotlin
val speechHelper = remember {
    SpeechRecognizerHelper(...)
}

DisposableEffect(Unit) {
    onDispose { speechHelper.destroy() }
}
```

### 8.2 资源释放

```kotlin
fun destroy() {
    cancel()
    asr = null
    callback = null
}
```

---

## 9. 状态码说明

| status | 说明 |
|--------|------|
| 0 | 首帧/中间结果 |
| 1 | 中间结果 |
| 2 | 最终结果 |

**注意：仅 status == 2 时触发 onResult 回调**

---

## 10. 后续扩展

以下能力可以扩展，但必须明确标注为“非当前正式契约”：

1. 独立语音上传接口
2. 服务端 ASR / 服务端 TTS
3. 语音打断数字人播报
4. 全双工语音交互
5. 多语言与方言支持

---

## 11. 依赖配置

### build.gradle.kts

```kotlin
dependencies {
    // 讯飞语音识别 SDK
    implementation(files("libs/SparkChain.aar"))
    implementation(files("libs/Codec.aar"))
}
```

### libs 目录

```
app/libs/
├── SparkChain.aar    # 讯飞 SDK 主库
└── Codec.aar         # 编解码库
```

---

## 12. 测试要点

### 12.1 功能测试

- [ ] 权限拒绝后的处理
- [ ] 长按录音正常工作
- [ ] 松开自动发送
- [ ] 上滑取消功能
- [ ] 音量可视化正确显示
- [ ] 震动反馈正常
- [ ] 连续多次录音稳定

### 12.2 边界测试

- [ ] 无网络时本地识别是否可用
- [ ] 识别结果为空的处理
- [ ] 快速点击多次录音按钮
- [ ] 长时间录音（超过限制）

---

## 13. 总结

当前 Android 端已完成本地语音识别功能，可实现：

**语音输入 → 本地 ASR 识别 → 文本发送到 chat/text/stream → 流式回复**

当前不应再把 `chat/voice`、`audio_url` 或“上传音频到服务端识别”写成既定联调前提。
