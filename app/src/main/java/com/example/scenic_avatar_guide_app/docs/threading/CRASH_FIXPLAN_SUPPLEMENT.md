# 崩溃修复补充计划

> **状态**：全部已完成 ✅
> **创建日期**：2026-05-06
> **完成日期**：2026-05-06
> **关联文档**：`THREAD_SAFETY_REFACTOR.md`（Phase 0–4 已执行，本文档为补充发现）
> **背景**：在对现有重构文档进行全面代码审计后，发现原文档未覆盖的 4 个新问题，以及 1 项现存的 Phase 3 待执行项（全部已完成）。

---

## 目录

1. [N1 — trySend 返回值未检查（StreamingTtsQueue）](#n1--trysend-返回值未检查)
2. [N2 — ExoPlayer 陈旧 PlayComplete 事件（cancel→start 竞态）](#n2--exoplayer-陈旧-playcomplete-事件)
3. [N3 — overrideMouthOpenY/Form 缺少 @Volatile（Live2DRendererImpl）](#n3--overridemouth-字段缺少-volatile)
4. [Phase 3 — PlaybackState 状态机显式化（StreamingTtsQueue）](#phase-3--playbackstate-状态机显式化)
5. [N4 — 消息列表无界增长（MainViewModel）](#n4--消息列表无界增长)
6. [架构确认：release() 不调用设计的合理性](#架构确认relase-不调用设计)
7. [执行顺序与风险评估](#执行顺序与风险评估)

---

## N1 — trySend 返回值未检查

### 问题描述

**文件**：`core/tts/StreamingTtsQueue.kt:267`

```kotlin
fun enqueue(segment: TtsSegmentData) {
    assertMainThread()
    if (!active) start()
    // 发送到 channel，由单一协程串行处理
    segmentChannel.trySend(segment)   // ← 返回值被丢弃！
}
```

`segmentChannel` 容量为 20（`Channel<TtsSegmentData>(20)`）。当上游在极短时间内连续发送超过 20 个 segment 时（如网络优先到达所有 TTS 音频块），`trySend` 会返回 `ChannelResult.failure`，segment 被静默丢弃，导致：

- 用户听到的语音出现无征兆的跳字/段落缺失
- `segmentList` 与实际入队段落不一致，触发后续 `IndexOutOfBoundsException`
- 日志中没有任何错误指示，难以排查

### 根因分析

Phase 1 修复仅设置了 channel 容量（防止 `trySend` 挂起），但遗漏了对失败结果的处理。这是一个**不完整的修复**（P1 补丁）。

### 修复方案

```kotlin
fun enqueue(segment: TtsSegmentData) {
    assertMainThread()
    if (!active) start()
    
    Log.d(TAG, "[ENQUEUE-REQ] segmentId=${segment.segmentId}")
    
    val result = segmentChannel.trySend(segment)
    if (result.isFailure) {
        // channel 已满（容量20）说明消费协程严重滞后，这是系统级异常
        Log.e(TAG, "[ENQUEUE-FAIL] channel full, segment dropped: id=${segment.segmentId}, " +
              "listSize=${segmentList.size}, playingIdx=$currentPlayingIndex")
        // 直接触发错误回调，让上层感知并可以选择中断当前会话
        onError(IllegalStateException("TTS segment channel full, segment ${segment.segmentId} dropped"))
    }
}
```

### 风险评估

| 风险点 | 说明 |
|--------|------|
| 是否引入新崩溃 | 否。`onError` 路径已有调用方（`handleError`），上层 ViewModel 有统一错误处理 |
| 是否改变正常路径行为 | 否。`trySend` 成功时行为完全不变 |
| 是否需要测试 | 建议压测场景：连续 25 次 `enqueue`，验证日志和错误回调 |

---

## N2 — ExoPlayer 陈旧 PlayComplete 事件

### 问题描述

**文件**：`core/tts/StreamingTtsQueue.kt` init 块（事件收集）、`handlePlayComplete()`

**问题场景**（时序图）：

```
[T1] 音频播放完 → AudioPlayer._events.tryEmit(PlayComplete)
                   ↓ SharedFlow buffer 中排队（还未被协程处理）

[T2] MainViewModel.cancelCurrentStream()
       → StreamingTtsQueue.cancel()
           → active = false
           → audioPlayer.stop()

[T3] MainViewModel.sendMessageToBackend() 启动新一轮对话
       → StreamingTtsQueue.start() [若被隐式调用]
           → active = true  ← ！！关键时间点

[T4] SharedFlow 协程调度到，处理 T1 时排队的 PlayComplete
       → handlePlayComplete() 检查 active == true（T3 已设为 true）
       → 进入 onAllComplete() 路径
       → AvatarPlaybackManager 收到"全部完成"信号
       → 数字人提前回到 IDLE 状态，正在播放的新回答被打断
```

**根因**：`MutableSharedFlow(extraBufferCapacity = 16, replay = 1)` 的 buffer 中可能存有来自上一轮会话的陈旧 `PlayComplete`。`cancel()` 仅设置 `active = false` 但不清空 flow buffer。若 `start()` 在 buffer 被消费前调用，陈旧事件会以 `active = true` 被处理。

`handlePlayComplete()` 中的 `if (!active)` 保护是**充分条件但不是必要条件**：只要 T2 与 T3 之间存在协程调度间隙，保护就会失效。

### 修复方案

使用**会话 epoch 计数器**标记每一轮会话，丢弃来自旧会话的事件：

```kotlin
// StreamingTtsQueue.kt 成员变量区
private val sessionEpoch = AtomicInteger(0)
```

```kotlin
fun start() {
    assertMainThread()
    // 递增 epoch，使上一轮所有未处理事件失效
    sessionEpoch.incrementAndGet()
    
    segmentList.clear()
    currentPlayingIndex = -1
    nextEnqueueIndex = 0
    inputFinished = false
    active = true
}
```

```kotlin
// init 块的事件收集处：在发起处理前捕获当前 epoch
scope.launch {
    audioPlayer.events.collect { event ->
        val capturedEpoch = sessionEpoch.get()
        withContext(Dispatchers.Main.immediate) {
            // 仅处理属于当前会话的事件
            if (sessionEpoch.get() != capturedEpoch) {
                Log.d(TAG, "[STALE_EVENT] 丢弃陈旧事件 $event (epoch mismatch)")
                return@withContext
            }
            when (event) {
                is AudioPlayerEvent.MediaItemTransition -> handleMediaItemTransition(event.reason, event.mediaItem)
                is AudioPlayerEvent.PlayComplete -> handlePlayComplete()
                is AudioPlayerEvent.PlayError -> handleError(event.message)
                is AudioPlayerEvent.BufferingStateChanged -> if (active) onBufferingStateChanged?.invoke(event.isBuffering)
                is AudioPlayerEvent.PlayStart -> { /* 无动作 */ }
            }
        }
    }
}
```

> **注意**：当前 init 块中的 collect 已在 `scope`（Dispatchers.Main）上运行，因此 `withContext(Dispatchers.Main.immediate)` 主要起到确保语义明确的作用，不会引入额外切换开销。

### 替代方案（更简洁）

若不希望修改事件收集结构，可在 `handlePlayComplete` 和 `handleMediaItemTransition` 入口处加 epoch 快照比较：

```kotlin
// cancel() 中递增
fun cancel() {
    assertMainThread()
    val newEpoch = sessionEpoch.incrementAndGet()
    Log.d(TAG, "[CANCEL] epoch → $newEpoch")
    active = false
    ...
}

// handlePlayComplete() 入口增加 epoch 检查
private fun handlePlayComplete(eventEpoch: Int) {
    if (sessionEpoch.get() != eventEpoch) {
        Log.d(TAG, "[STALE_COMPLETE] 丢弃陈旧 PlayComplete (epoch=$eventEpoch, current=${sessionEpoch.get()})")
        return
    }
    if (!active) { ... }
    ...
}
```

### 风险评估

| 风险点 | 说明 |
|--------|------|
| 是否会丢失合法事件 | 不会。epoch 只在 `cancel()` 时递增，正常播放链路中 epoch 不变 |
| AtomicInteger 是否必要 | 是。虽然全部在 Main 线程，但 GL 线程的回调（`onAfterDrawFrame`）也可能间接触发，AtomicInteger 保证可见性 |
| 是否影响多 segment 顺序 | 不影响。每个事件捕获的是当时的 epoch，顺序由事件自然排队决定 |

---

## N3 — overrideMouth 字段缺少 @Volatile

### 问题描述

**文件**：`core/avatar/Live2DRendererImpl.kt:103-104`

```kotlin
private var speakingMouthOverride = false
@Volatile
private var isSpeaking = false
private var overrideMouthOpenY = 0f   // ← 缺少 @Volatile
private var overrideMouthForm = 0f    // ← 缺少 @Volatile
```

**读写路径**：

| 操作 | 线程 | 是否有 synchronized 保护 |
|------|------|--------------------------|
| 写入（setMouth 中赋值） | Main | ✅ `synchronized(this)` 内 |
| 读取（onAfterDrawFrame） | GL 线程 | ✅ `synchronized(this)` 内 |
| 读取（setMouth 的 runOnRenderThread lambda） | GL 线程 | ❌ `synchronized` 块**外部** |

```kotlin
override fun setMouth(mouthOpen: Float, mouthForm: Float) {
    synchronized(this) {
        ...
        overrideMouthOpenY = amplifiedMouthOpen   // 写入：在 synchronized 内
        overrideMouthForm = scaledMouthForm
        ...
    }
    runOnRenderThread {
        synchronized(this@Live2DRendererImpl) {
            if (isReleased || !_isModelLoaded) return@runOnRenderThread
        }
        // ↓ 读取：synchronized 块已退出，GL 线程可能看到 JVM 缓存的旧值！
        JniBridgeJava.nativeSetParameter(Live2DParams.MOUTH_OPEN_Y, overrideMouthOpenY, 1.0f)
        JniBridgeJava.nativeSetParameter(Live2DParams.MOUTH_FORM, overrideMouthForm, 1.0f)
    }
}
```

GL 线程在 `runOnRenderThread` lambda 中读取这两个字段时，JVM 不保证对 Main 线程最新写入的可见性（没有 happens-before 关系）。后果：GL 线程使用旧的嘴型参数，导致口型不同步或卡帧。

### 修复方案（方案 A：@Volatile，推荐）

最小化改动，加注解：

```kotlin
@Volatile private var overrideMouthOpenY = 0f
@Volatile private var overrideMouthForm = 0f
```

`@Volatile` 保证写入后所有线程立即可见，开销极低（JVM 内存屏障），且与现有 `synchronized` 写入路径完全兼容。

### 修复方案（方案 B：lambda 内使用局部变量捕获）

若担心 `@Volatile` 的语义副作用（如与 `synchronized` 写入路径的交互），可在进入 `runOnRenderThread` 前捕获值：

```kotlin
override fun setMouth(mouthOpen: Float, mouthForm: Float) {
    val capturedOpenY: Float
    val capturedForm: Float
    synchronized(this) {
        ...
        overrideMouthOpenY = amplifiedMouthOpen
        overrideMouthForm = scaledMouthForm
        capturedOpenY = overrideMouthOpenY
        capturedForm = overrideMouthForm
        ...
    }
    runOnRenderThread {
        synchronized(this@Live2DRendererImpl) {
            if (isReleased || !_isModelLoaded) return@runOnRenderThread
        }
        JniBridgeJava.nativeSetParameter(Live2DParams.MOUTH_OPEN_Y, capturedOpenY, 1.0f)
        JniBridgeJava.nativeSetParameter(Live2DParams.MOUTH_FORM, capturedForm, 1.0f)
    }
}
```

**推荐方案 A**（`@Volatile`），因为 `onAfterDrawFrame` 路径已经通过 `synchronized` 读取，保持一致性更重要。

### 风险评估

| 风险点 | 说明 |
|--------|------|
| 性能 | `@Volatile` 仅增加内存屏障，在 16ms 帧周期中不可测量 |
| 是否影响现有 synchronized 路径 | 不影响。`synchronized` 本身已包含内存屏障，`@Volatile` 只对非 synchronized 读取路径生效 |
| 是否引入新问题 | 否 |

---

## Phase 3 — PlaybackState 状态机显式化

> **说明**：此项已在 `THREAD_SAFETY_REFACTOR.md` 的 Phase 3 中列为待执行项。本文档补充具体实现代码，以便直接执行。

### 当前状态（5 个离散标志）

```kotlin
// StreamingTtsQueue.kt 当前字段
private var active = false
private var inputFinished = false
private var currentPlayingIndex = -1
private val isTransitioning = AtomicBoolean(false)
private val isCompleting = AtomicBoolean(false)
```

**问题**：5 个标志的组合状态空间为 2^4 × N（N 为 index 范围）≈ 无穷多种，但合法组合仅约 6 种。非法组合（如 `active=false, isCompleting=true`）可能在竞态下出现，且无法在编译期或断言中检查。

### 目标状态机

```
IDLE ──────────────────────────────────────────────────────┐
  │  start() / enqueue()                                    │
  ▼                                                         │
RECEIVING（接收 segment，正在播放）                          │
  │  finishInput()                                          │
  ▼                                                         │
DRAINING（不再有新 segment，等待播放完成）                    │
  │  onAllComplete()                                        │
  ▼                                                         │
COMPLETING（触发完成回调，防重入）                            │
  │  回调执行完毕                                            │
  └─────────────────────────────────────────────────────────┘
  cancel() 可以从任何状态回到 IDLE
```

### 实现

```kotlin
// 在 StreamingTtsQueue.kt 文件顶部（类外）添加：

sealed class PlaybackState {
    object Idle : PlaybackState()
    data class Receiving(val playingIndex: Int) : PlaybackState()
    data class Draining(val playingIndex: Int) : PlaybackState()
    object Completing : PlaybackState()
    
    val isActive: Boolean get() = this !is Idle
    val currentIndex: Int get() = when (this) {
        is Receiving -> playingIndex
        is Draining -> playingIndex
        else -> -1
    }
}
```

```kotlin
// StreamingTtsQueue.kt 字段区替换：

// 替换前：
private var active = false
private var inputFinished = false
private var currentPlayingIndex = -1
private val isTransitioning = AtomicBoolean(false)
private val isCompleting = AtomicBoolean(false)

// 替换后：
private var playbackState: PlaybackState = PlaybackState.Idle
// 保留 pendingSegmentIndex（用于 handleMediaItemTransition 的并发控制，不属于状态机）
private val pendingSegmentIndex = AtomicInteger(-1)
```

```kotlin
// start() 修改：
fun start() {
    assertMainThread()
    sessionEpoch.incrementAndGet()  // N2 修复
    segmentList.clear()
    nextEnqueueIndex = 0
    playbackState = PlaybackState.Receiving(-1)
    Log.d(TAG, "[START] state=${playbackState}, epoch=${sessionEpoch.get()}")
}

// cancel() 修改：
fun cancel() {
    assertMainThread()
    sessionEpoch.incrementAndGet()  // N2 修复
    Log.d(TAG, "[CANCEL] from state=$playbackState, epoch→${sessionEpoch.get()}")
    playbackState = PlaybackState.Idle
    segmentList.clear()
    nextEnqueueIndex = 0
    pendingSegmentIndex.set(-1)
    audioPlayer.stop()
}

// finishInput() 修改：
fun finishInput() {
    assertMainThread()
    val currentState = playbackState
    if (currentState !is PlaybackState.Receiving) {
        Log.w(TAG, "[FINISH] unexpected state: $currentState, ignoring")
        return
    }
    Log.d(TAG, "[FINISH] Receiving→Draining, idx=${currentState.playingIndex}, listSize=${segmentList.size}")
    playbackState = PlaybackState.Draining(currentState.playingIndex)
    
    if (currentState.playingIndex < 0 || currentState.playingIndex >= segmentList.size) {
        if (segmentList.isEmpty() || currentState.playingIndex >= segmentList.size) {
            Log.d(TAG, "[ALL_COMPLETE] finishInput 触发完成（无 segment）")
            triggerAllComplete()
        }
    }
}

// handlePlayComplete() 修改：
private fun handlePlayComplete() {
    val state = playbackState
    when (state) {
        is PlaybackState.Idle -> Log.d(TAG, "[PLAY_COMPLETE] ignored: Idle")
        is PlaybackState.Completing -> Log.w(TAG, "[PLAY_COMPLETE] 重入保护：已在 Completing")
        is PlaybackState.Receiving, is PlaybackState.Draining -> {
            val isDraining = state is PlaybackState.Draining
            Log.d(TAG, "[PLAY_COMPLETE] state=$state, draining=$isDraining")
            
            playbackState = PlaybackState.Completing
            try {
                segmentList.clear()
                
                if (isDraining) {
                    Log.d(TAG, "[ALL_COMPLETE] PlayComplete 触发完成")
                    triggerAllComplete()  // 内部将 state 置回 Idle
                } else {
                    playbackState = PlaybackState.Receiving(-1)
                    onWaitingForSegment()
                }
            } catch (e: Exception) {
                playbackState = PlaybackState.Idle
                throw e
            }
        }
    }
}

// 新增辅助方法：
private fun triggerAllComplete() {
    playbackState = PlaybackState.Idle
    onAllComplete()
}
```

### 兼容性说明

所有原有的 `active` 字段引用替换为 `playbackState.isActive`，`currentPlayingIndex` 替换为 `playbackState.currentIndex`，`inputFinished` 替换为 `playbackState is PlaybackState.Draining`。

这是一次纯内部重构，外部接口（`enqueue`, `finishInput`, `cancel`, `release`）签名不变。

---

## N4 — 消息列表无界增长

### 问题描述

**文件**：`ui/screens/MainViewModel.kt`

`_messages: MutableStateFlow<List<ChatMessage>>` 在整个会话期间只增不减。对于普通景区导览场景，单次会话不超过 30-50 条消息，问题不大。但在以下边缘场景可能触发 OOM：

- 自动化测试长时间运行
- 用户保持 App 在后台，网络恢复后批量接收补发消息
- 开发者使用压测脚本验证流式功能

每条 `ChatMessage` 包含 AI 回答全文（可达数千字节），100 条消息约占 1-5 MB。在 RAM 受限的低端设备（512 MB）上，结合 Live2D 模型内存（约 50-80 MB）和 ExoPlayer 缓冲区，OOM 风险是真实的。

### 修复方案

在 `_messages` 更新的所有路径末尾添加裁剪逻辑：

```kotlin
// MainViewModel.kt 新增常量
private companion object {
    const val MAX_MESSAGES = 100
}

// 新增扩展方法（或内联到 updateMessages 处）
private fun List<ChatMessage>.trimToMaxSize(): List<ChatMessage> {
    return if (size > MAX_MESSAGES) {
        // 保留最新的 MAX_MESSAGES 条，从头部删除
        // 尽量以 user+assistant 消息对为单位删除，保持对话连贯性
        val excess = size - MAX_MESSAGES
        drop(excess)
    } else this
}
```

```kotlin
// 在所有更新 _messages 的地方，最后一步加 .trimToMaxSize()
// 例如 addMessage 或 updateLastAssistantMessage 处：
_messages.value = newMessages.trimToMaxSize()
```

### 风险评估

| 风险点 | 说明 |
|--------|------|
| 截断正在展示的消息 | 不会：Compose LazyColumn 是基于 index 渲染的，列表头部被裁剪时 UI 自动重新渲染，用户看到的是"向上滚动可查看更早消息"——不会崩溃，只是最早的对话不再显示 |
| 截断时序 | `trimToMaxSize` 在每次 `_messages` 写入后调用，与 StateFlow 同步更新，不存在竞态 |
| 100 条是否合适 | 保守值。可根据产品决策调整。景区导览的 P99 会话长度预计不超过 50 条 |

---

## 架构确认：release() 不调用设计

### 结论：当前架构是正确的，无需更改

**设计原则**（已在 `AvatarView.kt` 注释中说明）：

```
// 普通页面销毁只暂停/解绑 Surface，Native 模型按进程生命周期保活
```

**理由**：

1. **Live2D 官方建议**：Native SDK 模型加载代价极高（数秒），官方 Android Sample 也仅在 `onDestroy`（Activity 真正销毁）时调用 `nativeOnDestroy`，而非每次 Fragment/Compose 重组。

2. **当前调用路径**：
   - `AvatarView.DisposableEffect.onDispose` → `renderer.detachSurfaceView()`（解绑 GLSurfaceView）
   - 真正的 `release()` 仅从 `MainViewModel.onCleared()` 触发，即 ViewModel 被系统回收时

3. **CountDownLatch.await(5s) ANR 风险**：`release()` 中的 CountDownLatch 确实存在阻塞 Main 线程的风险。但由于实际调用点在 `onCleared()`（App 退出流程），此时 ANR 不会对用户产生可见影响。若未来需要在 UI 交互中调用 `release()`，需改为：
   ```kotlin
   // 建议的非阻塞替代方案（备用，当前无需实施）
   fun releaseAsync(onReleased: () -> Unit) {
       scope.launch {
           withContext(Dispatchers.IO) {
               // 等待 GL 线程完成清理
           }
           onReleased()
       }
   }
   ```

4. **当前风险等级**：低。文档化即可，无需代码变更。

---

## 执行顺序与风险评估

### 推荐执行顺序

| 优先级 | 项目 | 预计时间 | 崩溃影响 |
|--------|------|----------|----------|
| P0 | **N3** — @Volatile | 5 分钟 | 口型 GL 线程数据竞争，可能导致 JNI 崩溃 |
| P1 | **N1** — trySend 检查 | 15 分钟 | 静默段落丢失，降级为 IndexOutOfBoundsException |
| P2 | **N2** — Epoch 计数器 | 30 分钟 | 数字人提前返回 IDLE，破坏对话流程 |
| P3 | **Phase 3** — 状态机 | 2 小时 | 多标志竞态，降低未来维护性 |
| P4 | **N4** — 消息列表限制 | 20 分钟 | OOM（低概率，影响低端设备） |

### 各项互依赖关系

```
N3 ──────────────── 独立，可立即执行
N1 ──────────────── 独立，可立即执行
N2 ─┐
    ├─ 均依赖 StreamingTtsQueue，建议顺序执行
Phase 3 ─┘
N4 ──────────────── 独立，可与任何项并行执行
```

N2（epoch 计数器）与 Phase 3（状态机）有重叠字段（`active`、cancel 逻辑），**建议在同一 PR 中合并执行**，避免中间状态不一致。

### 测试检查单

执行完毕后，逐项验证：

- [x] **N3**：快速连续说话/停止 10 次，观察 GL 线程是否输出口型相关 JNI 异常 ✅
- [x] **N1**：使用 adb shell 配合 logcat 过滤 `[ENQUEUE-FAIL]`，压测 25+ segments ✅
- [x] **N2**：连续快速发送 3 条消息（每条在上条回答未完成前发送），观察数字人是否在第 1 条播完时就回到 IDLE（bug 表现），修复后应持续播放至最新一条 ✅
- [x] **Phase 3**：在 cancel→start 边界处打断点，验证 `playbackState` 转换路径正确 ✅
- [x] **N4**：adb shell `dumpsys meminfo` 对比 50 条 vs 150 条消息时的 VM heap 大小 ✅

---

*本文档与 `THREAD_SAFETY_REFACTOR.md` 配合使用。原文档 Phase 0–4 全部已完成；N1-N4 全部已完成。*
