---
name: live2d-expression-gesture
description: Live2D 数字人表情和动作系统说明，包含状态映射、播放队列和与 TTS 的协同
---

# Live2D 表情动作系统

## 状态定义

### AvatarState（高级状态）
```kotlin
enum class AvatarState {
    IDLE,       // 空闲，等待用户
    LISTENING,  // 听用户说话（ASR 采集中）
    THINKING,   // 思考中（等待 LLM 回复）
    SPEAKING,   // 说话中（TTS 播放）
    ERROR       // 出错
}
```

### AvatarExpression（表情）
```kotlin
enum class AvatarExpression(val value: String) {
    NEUTRAL("neutral"),
    HAPPY("happy"),
    THINKING("thinking"),
    SURPRISED("surprised"),
    EXCITED("excited"),
    CONCERNED("concerned"),
    APologetic("apologetic"),
    WELCOMING("welcoming")
}
```

### AvatarGesture（动作）
```kotlin
enum class AvatarGesture(val value: String) {
    IDLE("idle"),
    NOD("nod"),
    SHAKE("shake"),
    WAVE("wave"),
    POINT_LEFT("point_left"),
    POINT_RIGHT("point_right"),
    POINT_FORWARD("point_forward"),
    BOW("bow"),
    THINKING_POSE("thinking_pose"),
    GUIDE("guide")
}
```

## 状态更新机制

`AvatarPlaybackManager` 通过 `_avatarState: StateFlow<AvatarFullState>` 向 UI 层暴露完整状态。

### 状态更新时序

**TTS 播放流程**：
```
ttsController.onSpeakStart
  → _avatarState.update { state = SPEAKING }
  
ttsController.onPhonemeEvents
  → lipSyncAnimator.start(events, scope)
    → 每 20ms 更新 mouthOpen / mouthForm
      → _avatarState.update { mouthOpen = x, mouthForm = y }

ttsController.onSpeakComplete
  → lipSyncAnimator.stop()
  → _avatarState.update { state = IDLE, mouthOpen = 0, mouthForm = 0 }
```

**手动播放动作**：
```kotlin
fun playGesture(gesture: AvatarGesture, expression: AvatarExpression = NEUTRAL) {
    _avatarState.update { gesture = gesture, expression = expression }
    scope.launch {
        delay(2000)  // 2秒后自动恢复 IDLE
        _avatarState.update { gesture = IDLE }
    }
}
```

## AvatarPlaybackManager.play() 完整流程

```kotlin
fun play(action: AvatarPlayAction) {
    if (isPlaying) stop()
    
    // 1. 设置整体状态
    _avatarState.update {
        state = SPEAKING
        expression = action.expression
        expressionIntensity = action.expressionIntensity
        gesture = action.gesture
    }
    
    // 2. 播放动作队列（按 startOffsetMs 排序）
    if (action.motionQueue.isNotEmpty()) {
        playMotionQueue(action.motionQueue)
    }
    
    // 3. 播放语音
    action.text?.let { ttsController.speak(it) }
}
```

## MotionQueueItem 动作队列

```kotlin
data class MotionQueueItem(
    val type: String,        // 动作类型名称，对应 AvatarGesture.value
    val startOffsetMs: Long, // 相对于播放开始的延迟
    val durationMs: Long     // 动作持续时间，0 表示不自动恢复
)
```

`playMotionQueue()` 使用协程按时间线执行：
```kotlin
queue.sortedBy { it.startOffsetMs }.forEach { item ->
    delay(item.startOffsetMs)
    val gesture = AvatarGesture.fromValue(item.type)
    _avatarState.update { gesture = gesture }
    
    if (item.durationMs > 0) {
        delay(item.durationMs)
        _avatarState.update { gesture = IDLE }
    }
}
```

## 表情到参数的映射

`Live2DRendererImpl.applyExpressionPreset()` 将表情枚举转换为具体的 Live2D 参数值。

### 表情参数表

| 表情 | BROW_L_Y | BROW_R_Y | BROW_L_ANGLE | BROW_R_ANGLE | EYE_OPEN | ANGLE_Z | ANGLE_Y | BODY_ANGLE_X |
|------|----------|----------|--------------|--------------|----------|---------|---------|--------------|
| NEUTRAL | 0 | 0 | 0 | 0 | 1.0 | 0 | 0 | 0 |
| HAPPY | +0.25 | +0.25 | -0.2 | +0.2 | 0.85 | 0 | 0 | 0 |
| THINKING | -0.1 | 0 | -0.35 | +0.15 | 1.0 | 0 | 0 | 0 |
| SURPRISED | +0.45 | +0.45 | 0 | 0 | 1.2 | 0 | 0 | 0 |
| EXCITED | +0.35 | +0.35 | 0 | 0 | 1.05 | -6 | 0 | 0 |
| CONCERNED | -0.15 | -0.15 | +0.35 | -0.35 | 0.8 | 0 | 0 | 0 |
| APologetic | -0.1 | -0.1 | 1.0 | -1.0 | 0.75 | 0 | -10 | 2 |
| WELCOMING | +0.2 | +0.2 | 0 | 0 | 1.0 | -4 | 0 | +4 |

**intensity 缩放**：所有偏移值乘以 `safeIntensity`（0~1），实现表情强弱控制。

### 重置逻辑

每个表情应用前，先重置以下参数到默认值，防止旧表情残留：
```kotlin
EYE_L_OPEN = 1.0f
EYE_R_OPEN = 1.0f
BROW_L_Y = 0f
BROW_R_Y = 0f
BROW_L_ANGLE = 0f
BROW_R_ANGLE = 0f
EYE_BALL_X = 0f
EYE_BALL_Y = 0f
ANGLE_X = 0f
ANGLE_Y = 0f
ANGLE_Z = 0f
BODY_ANGLE_X = 0f
```

## 动作到参数的映射

`Live2DRendererImpl.applyGesturePreset()` 将动作枚举转换为头部/身体角度参数。

### 动作参数表

| 动作 | ANGLE_X | ANGLE_Y | ANGLE_Z | BODY_ANGLE_X |
|------|---------|---------|---------|--------------|
| IDLE | 0 | 0 | 0 | 0 |
| NOD | 0 | -18 | 0 | 0 |
| SHAKE | 18 | 0 | 0 | 0 |
| WAVE | 0 | 0 | -14 | 10 |
| POINT_LEFT | -20 | 0 | 0 | -12 |
| POINT_RIGHT | 20 | 0 | 0 | 12 |
| POINT_FORWARD | 0 | 0 | -8 | 0 |
| BOW | 0 | -26 | 0 | 0 |
| THINKING_POSE | -10 | 10 | 0 | 0 |
| GUIDE | 12 | 0 | 0 | 8 |

### 重置逻辑

每个动作应用前，重置 ANGLE_X/Y/Z 和 BODY_ANGLE_X 为 0，防止动作叠加。

## 降级映射（后端未返回时）

当后端没有返回 `gesture` 或 `expression` 时，使用本地映射：

### IntentToGesture
```kotlin
"greeting"         → WAVE
"farewell"         → WAVE
"introduction"     → POINT_FORWARD
"direction_left"   → POINT_LEFT
"direction_right"  → POINT_RIGHT
"agreement"        → NOD
"disagreement"     → SHAKE
"thinking"         → THINKING_POSE
"apology"          → BOW
"route_recommendation" → GUIDE
```

### EmotionToExpression
```kotlin
"joy" / "happiness"  → HAPPY
"sadness"            → CONCERNED
"surprise"           → SURPRISED
"trust"              → WELCOMING
"anticipation"       → EXCITED
```

## 关键注意事项

1. **状态竞争**：`AvatarPlaybackManager` 的 `_avatarState` 使用 `StateFlow.update { }` 原子更新，避免并发修改导致的状态不一致。

2. **表情与动作独立**：`AvatarFullState` 中 `expression` 和 `gesture` 是两个独立字段，可以同时设置。例如"开心地点头"可以 `expression = HAPPY` + `gesture = NOD`。

3. **自动恢复**：`playGesture()` 会自动在 2 秒后恢复 IDLE。但 `play()` 中的动作不会自动恢复，依赖 motionQueue 的 `durationMs` 或 TTS 结束时的 `onSpeakComplete` 来恢复。

4. **强度范围**：`expressionIntensity` 的有效范围是 0.0 ~ 1.0。在 `applyExpressionPreset` 中用 `.coerceIn(0f, 1f)` 保护。

5. **C++ 层 motion 系统**：`LAppModel` 内置了 motion 播放系统（`StartMotion`、`StartRandomMotion`），但本项目目前主要通过参数直接控制表情和动作，不依赖 C++ 层的 motion 文件。C++ 层的 idle motion 已被禁用。
