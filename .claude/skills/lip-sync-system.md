---
name: 口型同步系统
description: 景灵智导 Live2D 口型同步系统的完整知识索引，涵盖架构、音素映射、协同发音、Idle 竞争保护、流式 TTS 口型、参数配置、已知问题及修复方案。当修改口型/嘴型/LipSync/Viseme/唇形同步相关代码时查阅。
---

# 口型同步系统 Skill

## 一、系统架构

```
后端 SSE 事件流
  ├── tts_segment（分段元信息 + marks/文本）
  └── tts_audio_chunk（base64 音频块）
        │
        ▼
AvatarPlaybackManager（播放协调器）
  ├── preloadSegmentLipSync()    ← 入队时预生成口型事件
  ├── startAudioSyncedLipSync()  ← 音频进度驱动的口型循环（~60fps）
  └── calculateLipSync()         ← 按 audioPos 查表 + 字间过渡
        │
        ▼
_mouthState: StateFlow<Pair<Float,Float>>  ← (mouthOpen, mouthForm)
        │
        ▼
AvatarView（Compose UI）
  └── LaunchedEffect 收集 mouthState → renderer.setMouth(open, form)
        │
        ▼
Live2DRendererImpl
  ├── setMouth()                ← 直接 JNI 设参 + 激活 speakingMouthOverride
  └── onAfterDrawFrame          ← 每帧渲染后强制覆盖 Idle 动画嘴部参数
        │
        ▼
JniBridgeJava.nativeSetParameter()
  ├── ParamMouthOpenY           ← Live2D 嘴部开度
  └── ParamMouthForm            ← Live2D 嘴型（正值=圆唇/O型，负值=扁唇/微笑）
```

## 二、核心文件

| 文件 | 职责 |
|------|------|
| `core/avatar/AvatarPlaybackManager.kt` | 播放协调器：口型事件生成、音频同步循环、每帧查表计算 |
| `core/avatar/LipSyncAnimator.kt` | 口型动画驱动器：协同发音混合、缓动过渡、事件合并 |
| `core/avatar/ChinesePhonemeEngine.kt` | 汉字→拼音→音素分解；marks→PhonemeEvent 转换 |
| `core/avatar/ChineseVisemeMapper.kt` | 中文音素→VisemeType 映射规则 |
| `core/avatar/Live2DRendererImpl.kt` | Live2D 渲染：setMouth() + onAfterDrawFrame 口型覆盖 |
| `core/avatar/Live2DGLSurfaceView.kt` | GLSurfaceView：onDrawFrame 中调用 onAfterDrawFrame |
| `core/avatar/animation/MotionTransitionManager.kt` | 动作层混合过渡（不含嘴部参数） |
| `core/avatar/animation/GestureTransitionController.kt` | 动作参数过渡控制器（角度/身体/眼球，不含嘴部） |
| `core/tts/StreamingTtsQueue.kt` | 流式 TTS 分段队列：管理 segment 播放顺序 |
| `core/audio/AudioPlayer.kt` | ExoPlayer 封装：提供 getCurrentPosition() / isActuallyPlaying() |
| `domain/model/AvatarState.kt` | VisemeType 枚举（18 种）、AvatarState、AvatarExpression、AvatarGesture |
| `domain/model/ChatStreamModels.kt` | TtsSegmentData、TtsAudioChunkData、TtsAudioEndData |
| `ui/components/AvatarView.kt` | Compose 组件：mouthState 收集 → setMouth()，fullState → updateState() |

## 三、Viseme 口型体系

### 3.1 18 种 Viseme

```kotlin
enum class VisemeType(val mouthOpen: Float, val mouthForm: Float = 0f) {
    // 基本七种
    CLOSED(0.0f),           // 闭嘴（b, p, m）
    SLIGHT(0.25f),          // 微张（d, t, n, l）
    HALF(0.5f),             // 半开（g, k, h）
    OPEN(0.9f),             // 大开（a）
    WIDE(0.6f, -0.3f),      // 扁唇（i, e）
    ROUND(0.5f, 0.6f),      // 圆唇（o, u, ü）
    NEUTRAL(0.1f),          // 中性（默认）

    // 11 种高精度扩展
    SIL(0.0f),              // 静音/标点停顿
    BP(0.0f),               // 双唇爆破音（b, p）- 需要闭嘴动作
    F(0.15f, 0.0f),         // 唇齿音（f）
    DT(0.2f),               // 舌尖中爆破音（d, t）
    GK(0.35f),              // 舌根爆破音（g, k）
    JQ(0.3f, -0.2f),        // 舌面音（j, q, x）
    ZC(0.25f),              // 舌尖前音（z, c, s）
    ZH(0.3f),               // 舌尖后音（zh, ch, sh, r）
    A(0.85f),               // 开元音 a
    O(0.6f, 0.5f),          // 中后圆唇 o
    E(0.55f, -0.2f),        // 中前非圆唇 e
    I(0.45f, -0.35f),       // 高前非圆唇 i
    U(0.3f, 0.7f),          // 高后圆唇 u
    V(0.35f, 0.6f),         // 高前圆唇 ü
    UA(0.4f, 0.65f),        // ua 复合
}
```

### 3.2 音素→Viseme 映射

`ChineseVisemeMapper.map()` 负责声母/韵母到 Viseme 的映射：
- 声母根据发音部位映射（b,p→BP, d,t→DT, g,k→GK, j,q,x→JQ 等）
- 韵母根据开口度和唇形映射（a→A, o→O, i→I, u→U, e→E 等）
- 声母时长占比由 `getInitialDurationRatio()` 控制

## 四、口型同步的两条路径

### 4.1 流式 TTS（主链路）

```
tts_segment 入队 → preloadSegmentLipSync()
  ├── 有 marks：marksToPhonemeEvents() 精确生成
  └── 无 marks：textToPhonemeEvents() 文本估算兜底
       │
  onSegmentStart → startAudioSyncedLipSync()
  └── 加载预生成缓存 → 每帧 calculateLipSync(audioPos)
       └── audioPos 减去 streamAudioOffsetMs 得到 effectivePos
            └── 查表 PhonemeEvent → lerp 平滑 → _mouthState
```

关键点：
- **预生成缓存**：segment 入队时就生成口型事件，音频开播立即可用
- **stream_audio_offset_ms**：chunk 流的 MP3 有静音前置，marks 时间需减去偏移
- **动态替换**：收到 `tts_audio_end.marks` 后，若当前正在播放该 segment，用精确 marks 替换估算事件

### 4.2 非流式 TTS（测试/降级）

```
RemoteTTSController.speak(text) → tts/synthesize API
  → 返回 audio_url
  → ChinesePhonemeEngine.textToPhonemeEvents() 生成事件
  → startNonStreamingLipSync() → 同 calculateLipSync()
```

## 五、口型计算算法（calculateLipSync）

位于 `AvatarPlaybackManager.kt` 第 1180-1270 行：

```
effectivePos = audioPos - currentStreamOffsetMs

查找 currentEvent (effectivePos 落在 [startMs, endMs) 内)

├── 找到事件
│   ├── SIL → lerp→0（强制闭唇）
│   └── 非SIL → 事件内进度 × 缓动曲线 × mouthOpen
│
├── 两事件之间
│   ├── 前后有 SIL → lerp→0
│   └── 字间过渡 → lerp(prev, next, t) × holdFactor
│       holdFactor: gap≥150→0.4, gap≥80→0.5, avgOpen≥0.7→0.5
│
├── 第一个事件之前 → 渐进打开
└── 最后一个事件之后 → 逐帧衰减(*0.7)
```

帧间平滑：`lerpValue(last, target, 0.3~0.5)` 保证无突兀跳跃。

## 六、协同发音（LipSyncAnimator）

`LipSyncAnimator.mergeEventsByChar()` 负责按字合并音素：
- 含闭唇声母（BP, ZC, JQ, ZH）的字保留原样，确保闭嘴动作不被吞掉
- 其他字：保留声母过渡阶段（20-30% 时长），韵母占 70-80%
- 爆破音（BP/DT/GK）特殊处理：55% 闭气准备 + 45% 快速释放

`blendMouth()` / `blendMouthAdvanced()` 负责混合前后音素：
- 正常：前一音素 20% + 当前 60% + 后一 20%
- 连续大开口音（>=0.8）：增强前后权重到 25%，让过渡更明显

## 七、Idle 动画与口型的竞争保护

### 7.1 问题本质

Live2D SDK 的 Idle 动画组（`nativeStartRandomMotion("Idle", 1)`）会**每帧**更新嘴部参数（`ParamMouthOpenY`, `ParamMouthForm`），与我们的口型同步形成竞争。

### 7.2 三层保护

| 层 | 位置 | 机制 |
|----|------|------|
| 1 | `setMouth()` 直接 JNI | 调用时立即设参 |
| 2 | `onAfterDrawFrame` | **每帧** SDK 渲染后强制覆盖 |
| 3 | `updateState()` 提前激活 | 进入 SPEAKING 时立即设 `speakingMouthOverride=true` |

### 7.3 保护开关状态机

```
SPEAKING 进入 → speakingMouthOverride = true
                  ↓
         每帧 onAfterDrawFrame 覆盖嘴部参数
                  ↓
SPEAKING 退出 → speakingMouthOverride = false
                setMouth(0f, 0f) 闭口
```

### 7.4 注意：非 SPEAKING 期间的嘴部

`onWaitingForSegment`（segment 间等待）时 state=THINKING，`isSpeaking=false`：
- `forceCloseMouth()` 被调用，嘴巴闭合
- 但 Idle 动画仍可控制嘴部（此时口型覆盖已关闭）
- segment 间停顿的嘴部由 Idle 动画决定，这是**预期行为**

## 八、嘴部参数配置

### 8.1 参数常量

```kotlin
// Live2DRendererImpl.kt
var mouthWidthScale: Float = 0.3f  // 嘴部宽度偏移（正值=更圆，负值=更扁）

// setMouth() 中
amplifiedMouthOpen = mouthOpen * 0.65f  // 整体张开度缩放到 65%
scaledMouthForm = if (mouthOpen > 0.02f) mouthForm + mouthWidthScale else 0f
```

- `mouthOpen <= 0.02f` 时 `mouthForm` 强制归零——防止闭嘴时残留 Form 值造成圆唇
- `mouthWidthScale` 默认 0.3f 让嘴型更圆润自然

### 8.2 情绪影响

`ChinesePhonemeEngine.emotionMultiplier` 根据当前情绪调整口型幅度（通过 `applyEmotion()` 在 `applyAllCorrections()` 中生效）。

## 九、已知问题与修复记录

### 9.1 O 型嘴固着问题（已修复）

**症状**：动作切换到待机时嘴变成 O 型，接下来说话时整个说话过程保持 O 型嘴。

**根因**：`speakingMouthOverride` 仅在 `setMouth()` 中设为 true，但从 `updateState(isSpeaking=true)` 到首次 `setMouth()` 之间存在 1-2 帧窗口，此时 Idle 动画的 O 型嘴未被覆盖。

**修复**（2026-04-30）：在 `updateState()` 中增加提前激活逻辑：
```kotlin
if (!wasSpeaking && isSpeaking) {
    speakingMouthOverride = true   // 不等 setMouth()，立即激活覆盖
}
if (wasSpeaking && !isSpeaking) {
    speakingMouthOverride = false  // 退出时同步关闭
    setMouth(0f, 0f)
}
```

### 9.2 流式 segment 切换瞬间口型抖动（已修复）

**症状**：segment 之间切换时口型短暂归零再恢复，视觉上不连贯。

**修复**：`onWaitingForSegment` 中增加 `scheduleMouthClose(300ms)` 延迟闭口，短 gap（<300ms）内保持最后口型不变。

### 9.3 口型事件为空导致全程闭嘴

**兜底机制**：
1. `preloadSegmentLipSync()` 预生成（入队时）
2. `startAudioSyncedLipSync()` 从预生成缓存加载
3. 缓存为空时从 `currentSegmentText` 文本估算兜底
4. 收到 `tts_audio_end.marks` 后动态替换

## 十、调试技巧

### 10.1 关键日志 TAG

| TAG | 来源 | 用途 |
|-----|------|------|
| `AvatarPlaybackManager` | `[LIPSYNC]` | 口型帧计数、音频位置、事件匹配 |
| `AvatarPlaybackManager` | `[PRELOAD]` | 预生成缓存状态 |
| `AvatarPlaybackManager` | `[SEGMENT]` | segment 口型事件详情 |
| `AvatarPlaybackManager` | `[STREAM]` | chunk/segment 入队 |
| `LipSyncAnimator` | `[AUDIOSYNC]` | 音频同步模式帧详情 |
| `L2D` | Live2DRendererImpl | setMouth/updateState/playIdleMotion |

### 10.2 常见排查路径

1. **口型不动**：检查 `currentSegmentEvents` 是否为空 → 检查预生成日志 → 检查 marks/文本
2. **口型与音频不同步**：检查 `currentStreamOffsetMs` 是否正确 → 检查 `effectivePos` 计算
3. **口型抖动**：检查 `samePositionCount` 阈值 → 检查 segment 切换逻辑
4. **Idle 嘴型顽固**：检查 `speakingMouthOverride` 状态 → 检查 `onAfterDrawFrame` 是否被调用

## 十一、相关文档

- `docs/api/API_STREAMING.md` — SSE 流式事件协议（tts_segment, tts_audio_chunk, tts_audio_end）
- `docs/api/API_TTS_USAGE.md` — TTS 接口使用说明
- `docs/avatar/CHINESE_LIP_SYNC.md` — 中文口型同步方案
- `docs/avatar/AVATAR_LIVE2D_PLAN.md` — Live2D 开发计划
- `docs/tts/TTS_MARKS_SPEC.md` — TTS marks 格式规范
- `docs/tts/EDGE_TTS_IMPLEMENTATION.md` — Edge-TTS 实现说明
- `docs/animation/EXPRESSION_MOTIONS.md` — 表情动作文档
- `docs/animation/GESTURE_MOTIONS.md` — 手势动作文档
