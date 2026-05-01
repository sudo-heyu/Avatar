---
name: live2d-lipsync-system
description: 数字人口型同步系统说明，包含 Viseme 映射、协同发音算法和 TTS 集成方式
---

# Live2D 口型同步系统

## 架构概述

口型同步由三层协作完成：

```
TTS 引擎（讯飞/Edge-TTS）
  → PhonemeEvent 流（音素 + 时间戳）
    → LipSyncAnimator（平滑动画 + 协同发音）
      → AvatarPlaybackManager._avatarState（mouthOpen, mouthForm）
        → Live2DRendererImpl.setMouth()
          → JniBridgeJava.nativeSetParameter(ParamMouthOpenY / ParamMouthForm)
```

## Viseme 类型定义

`VisemeType` 枚举定义在 `AvatarState.kt`，每个 viseme 包含 `mouthOpen`（开合度）和 `mouthForm`（嘴型）：

### 基础口型（向后兼容）

| Viseme | mouthOpen | mouthForm | 对应音素 |
|--------|-----------|-----------|---------|
| `CLOSED` | 0.0 | 0.0 | b, p, m, l, w |
| `SLIGHT` | 0.25 | 0.0 | d, t, n, l |
| `HALF` | 0.5 | 0.0 | e, g, k, h |
| `OPEN` | 0.9 | 0.0 | a |
| `WIDE` | 0.6 | -0.3 | i, ü |
| `ROUND` | 0.5 | 0.6 | o, u |
| `NEUTRAL` | 0.1 | 0.0 | 默认 |

### 高精度口型（中文优化）

| Viseme | mouthOpen | mouthForm | 对应音素/韵母 |
|--------|-----------|-----------|--------------|
| `SIL` | 0.0 | 0.0 | 静音/停顿 |
| `BP` | 0.0 | 0.0 | 双唇音 b, p, m |
| `F` | 0.1 | -0.2 | 唇齿音 f |
| `DT` | 0.15 | 0.0 | 舌尖中音 d, t, n, l |
| `GK` | 0.35 | 0.0 | 舌根音 g, k, h |
| `JQ` | 0.25 | -0.4 | 舌面音 j, q, x |
| `ZC` | 0.2 | 0.0 | 舌尖前音 z, c, s |
| `ZH` | 0.25 | 0.0 | 舌尖后音 zh, ch, sh, r |
| `A` | 0.9 | 0.0 | 开口呼 a, ai, an, ang, ao |
| `O` | 0.6 | 0.6 | 合口呼圆唇 o, ou, ong |
| `E` | 0.5 | 0.0 | 半开口 e, ei, en, eng, er |
| `I` | 0.3 | -0.5 | 齐齿呼扁嘴 i, ie, iu, in, ing |
| `U` | 0.4 | 0.4 | 合口呼收圆 u, ui, un |
| `V` | 0.35 | -0.3 | 撮口呼 ü, üe, ün |
| `UA` | 0.7 | 0.2 | 复合元音过渡 |

## 协同发音（Coarticulation）

`LipSyncAnimator.blendMouth()` 在音素切换时进行加权混合，避免口型跳动：

```
混合值 = 前一音素 × 0.2 + 当前音素 × 0.6 + 后一音素 × 0.2
```

这模拟了真实发音时口型预先准备（anticipation）和滞后释放（coarticulation）的效果。

## 动画平滑过渡

`animateTransition()` 在音素持续时间内执行线性插值：
- 步进间隔：20ms（约 50fps）
- 从当前口型平滑过渡到目标口型
- 每个音素结束后 `lastOpen`/`lastForm` 被更新，作为下一个过渡的起点

## TTS 集成接口

### PhonemeEvent 数据结构
```kotlin
data class PhonemeEvent(
    val phoneme: String,    // 音素标识
    val startMs: Long,      // 开始时间（毫秒，相对于 TTS 开始）
    val endMs: Long,        // 结束时间
    val viseme: VisemeType, // 映射的口型
    val charIndex: Int = 0  // 对应文本字符位置
)
```

### 回调配置
在 `AvatarPlaybackManager.setupTTSCallbacks()` 中：

```kotlin
// 正确做法：使用音素事件列表驱动高精度动画
ttsController.onPhonemeEvents = { events ->
    lipSyncAnimator.start(events, scope)
}

// 错误做法：不要用 onPhoneme 单独回调直接更新状态
// 这会导致与 LipSyncAnimator 的竞争，口型跳动
```

## 关键注意事项

1. **Scope 必须正确**：`LipSyncAnimator.start()` 需要传入一个活跃的 CoroutineScope（通常使用 `AvatarPlaybackManager` 的 `Dispatchers.Main + SupervisorJob()` scope）。

2. **取消机制**：
   - TTS 结束时 `ttsController.onSpeakComplete` 调用 `lipSyncAnimator.stop()`
   - `stop()` 会取消 `job` 并将嘴巴重置为闭合状态
   - 新的播放开始时 `start()` 会先调用 `stop()` 清理旧的动画

3. **空事件处理**：如果 `events` 为空，`start()` 直接设置嘴巴为 0,0 并返回

4. **持续时间保护**：`(event.endMs - event.startMs).coerceAtLeast(40)` 确保每个音素至少有 40ms 的过渡时间，避免极短音素导致动画不自然

5. **mainOpen/mouthForm 约束**：在传入 `AvatarFullState` 前，值应已被限制在有效范围内（open: 0~1, form: -1~1）。`Live2DRendererImpl.setMouth()` 中使用了 `.coerceIn()` 作为最终保护。

## 调试口型同步

1. 在 `LipSyncAnimator` 中添加日志打印当前 `open`/`form` 值
2. 检查 TTS 返回的音素事件时间戳是否单调递增
3. 如果口型与语音明显不同步，检查 TTS 的 `startMs` 是相对于语音播放开始还是合成开始
4. 如果口型跳动，检查是否同时存在多个更新源（如 `onPhoneme` 和 `LipSyncAnimator` 同时更新 `_avatarState`）
