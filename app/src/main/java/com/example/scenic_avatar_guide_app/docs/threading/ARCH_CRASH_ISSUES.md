# 架构级崩溃问题文档

> **创建日期**：2026-05-06  
> **背景**：经过代码审计确认，项目的频繁闪退不是单点 bug 造成的，而是三个架构级设计问题的累积效应。本文档描述问题本质、复现路径和改善方向，供后续重构参考。  
> **关联文档**：`THREAD_SAFETY_REFACTOR.md`（已执行的线程修复）、`CRASH_FIXPLAN_SUPPLEMENT.md`（已执行的补丁修复）

---

## 问题一：Live2D Native 集成的三条并发驱动路径

### 问题描述

Live2D Native SDK 通过 JNI 暴露一组有状态的 C++ 函数（`nativeSetParameter`、`nativeStartMotionByPath`、`nativeOnDestroy` 等）。这些函数共享同一份 Native 状态机，不是线程安全的。当前架构中，有三条路径同时驱动这些函数，相互之间没有统一的互斥保护：

```
路径 A: animationTick()
    mainHandler.postDelayed(16ms)
    → 主线程 → runOnRenderThread { JniBridgeJava.nativeSetParameter(...) }

路径 B: setMouth() / setExpression() / playMotion() 等公开方法
    主线程同步调用
    → runOnRenderThread { JniBridgeJava.nativeSetParameter(...) }

路径 C: onAfterDrawFrame callback（GL 线程主动触发）
    GL 线程每帧 → synchronized 块读取 overrideMouthOpenY/Form
    → JniBridgeJava.nativeSetParameter(...)
```

每条路径都有自己的 `isReleased` 检查，但检查与实际 JNI 调用之间存在不可消除的窗口。

### 触发崩溃的具体机制

**机制 1：时间估算替代 SDK 状态导致并发动作**

`animationTick()` 用本地时间估算判断原生动作是否完成：

```kotlin
// Live2DRendererImpl.kt:508
val timeBasedFinished = elapsed >= nativeMotionDurationMs
if (timeBasedFinished || sdkMotionFinished) {
    nativeMotionPlaying = false
    // 立即发起下一个动作
    playIdleMotion()  // 或 transitionToGestureInternal(...)
}
```

`nativeMotionDurationMs` 来自硬编码的估算表（`estimateMotionDuration()`）。在低端设备上，SDK 实际播放速度慢于估算，导致：

1. 主线程认为动作已结束，调用 `playIdleMotion()` → `nativeStartMotionByPath(idle)`
2. GL 线程仍在执行上一个动作的渲染帧
3. 两个动作的 C++ 内部状态叠加，SDK 内部指针混乱 → SIGSEGV

**机制 2：页面切换时 GL 队列中的残留任务**

`detachSurfaceView()` 调用 `mainHandler.removeCallbacksAndMessages(null)` 停止新的 `animationTick` 调度，但此前已通过 `runOnRenderThread` 入队的 lambda 仍留在 GL 线程的队列中。当这些 lambda 执行时，`isReleased` 检查能阻止大多数 JNI 调用，但快速的 `attach → detach → attach` 循环（如 Compose 重组）中，新旧 `surfaceViewRef` 发生交替，旧 lambda 可能经由新的 `SurfaceView` 访问未完全初始化的 Native 状态。

**机制 3：路径 B 和路径 C 的竞态**

`setMouth()` 在主线程的 `synchronized(this)` 块中更新 `overrideMouthOpenY/Form`，然后通过 `runOnRenderThread` 投递 JNI 调用。与此同时，GL 线程的 `onAfterDrawFrame` 在自己的 `synchronized(this)` 块中读取这两个字段并直接调用 JNI。当两个 JNI 调用同时进入 Native 层时，Native 侧的参数设置函数没有重入保护，可能产生参数覆盖或野指针。

### 涉及文件

- `core/avatar/Live2DRendererImpl.kt`：`animationTick()`、`scheduleAnimationTick()`、`runOnRenderThread()`、`onAfterDrawFrame` 回调、`setMouth()`
- `core/avatar/animation/` 目录下所有动画控制器

### 改善方向

1. **取消时间估算，改为 GL 线程内轮询**：在 GL 线程的 `onAfterDrawFrame` 中检查 `nativeIsMotionFinished()`，而不是在主线程用 `postDelayed` 估算。这样动作完成的判断与 SDK 状态严格同步，消除路径 A 的不确定性。

2. **把 animationTick 迁移到 `onAfterDrawFrame` 驱动**：当前由主线程 Handler 定时触发，改为由 GL 线程每帧触发。好处是：所有 JNI 写操作集中在 GL 线程，路径 A 和路径 C 合并为一条路径，消除并发。

3. **渐进可行的临时方案**：在 `animationTick` 中，把所有 `runOnRenderThread` 的 JNI 调用改为先检查一次 `synchronized` 块的 `isReleased` 和 `nativeMotionPlaying`，再入队，减少残留任务的影响范围。

---

## 问题二：AvatarPlaybackManager 协调了过多并发状态，stop() 的清理不是原子的

### 问题描述

`AvatarPlaybackManager`（1200+ 行）是整条播放链路的核心协调层，同时持有和管理以下 6 个并发执行单元：

| Job | 职责 | 退出条件 |
|-----|------|----------|
| `audioPositionSyncJob` | 16ms 轮询音频进度，驱动口型 | `isPlayingRef.get() == false` |
| `streamingLipSyncJob` | 备用口型同步路径 | 协程取消 |
| `motionQueueJob` | 按时序播放动作队列 | 协程取消 |
| `expressionTimelineJob` | 按时序切换表情 | 协程取消 |
| `waitingCloseJob` | 延迟关闭等待 | 协程取消 |
| `StreamingTtsQueue` 内部协程 | 串行处理 TTS 分段 | channel 关闭或取消 |

`stop()` 的清理序列：

```kotlin
fun stop() {
    runCatching {                          // ← 异常被吞，部分失败不可见
        ttsController.stop()
        streamingTtsQueue.cancel()         // ← 停止 TTS 队列
        synchronized(lipSyncLock) {
            jobsToCancel = listOf(...)
            audioPositionSyncJob = null    // ← 置 null，但协程还在运行
            streamingLipSyncJob = null
        }
        jobsToCancel.forEach { it?.cancel() }  // ← 发送取消信号（不等待完成）
        motionQueueJob?.cancel()           // ← 同上
        expressionTimelineJob?.cancel()
        _avatarState.update { AvatarFullState() }  // ← 通过 StateFlow 异步投递
    }
}
```

### 触发崩溃的具体机制

**机制 1：16ms 口型循环的退出延迟**

`audioPositionSyncJob` 的退出条件是 `isPlayingRef.get()`，而这个值在 `stop()` 的 `synchronized` 块中被设为 `false`。但协程取消（`it?.cancel()`）只是发出信号，协程在下一个 `delay(16)` 才真正停止。在这 16ms 内，`startStreaming()` 已经把 `isPlayingRef` 重新设为 `true`（通过新的 `onSegmentStart` 回调），旧协程检查 `isPlayingRef.get()` 时发现是 `true`，**不会退出**，继续运行。

结果是新旧两个 `audioPositionSyncJob` 同时对 `_mouthState.value` 写入，互相覆盖，导致口型抖动，并在某些设备上触发 StateFlow 内部的并发写断言。

**机制 2：`_avatarState` StateFlow 的合并跳过 IDLE 状态**

`stop()` 调用 `_avatarState.update { AvatarFullState() }`（state = IDLE），随即 `startStreaming()` 调用 `_avatarState.update { THINKING }`。`StateFlow` 是有损的——如果两次更新发生在同一个 Main 线程帧内，Compose 收集器可能直接看到 `THINKING`，**跳过 `IDLE`**。

这意味着 `Live2DRendererImpl.updateState(IDLE)` 从未被调用，`isSpeaking` 和 `speakingMouthOverride` 保持上一会话的 `true` 状态，进入新会话。GL 线程的 `onAfterDrawFrame` 在新会话开始初期使用旧的 `overrideMouthOpenY/Form` 值覆盖嘴型参数，与实际音频完全不同步。

**机制 3：runCatching 吞掉中途失败**

如果 `ttsController.stop()` 抛出异常（例如 ExoPlayer 处于非法状态），`runCatching` 捕获后只记录日志，后续的 `streamingTtsQueue.cancel()`、Job 取消、StateFlow 重置**全部跳过**。下次 `start()` 时，上一会话的 Job 和状态依然活跃。

### 涉及文件

- `core/avatar/AvatarPlaybackManager.kt`：`stop()`、`startStreaming()`、`startAudioSyncedLipSync()`、`onAllComplete` 回调
- `core/avatar/Live2DRendererImpl.kt`：`updateState()`、`isSpeaking`、`speakingMouthOverride`

### 改善方向

1. **`stop()` 内同步重置渲染器状态**：`AvatarPlaybackManager` 持有对 `Live2DRenderer` 的引用（或通过回调），在 `stop()` 内直接调用 `renderer.resetSpeakingState()` 而不依赖 StateFlow 异步链。这消除了 IDLE 状态被跳过的问题。

2. **用单一 `sessionId` 替代分散的退出条件**：`audioPositionSyncJob` 的退出条件改为对比会话 ID，而不是 `isPlayingRef`。新会话开始时 `sessionId` 递增，旧循环检查到 ID 不匹配立即退出，与 epoch 机制保持一致。

3. **`stop()` 中去掉顶层 `runCatching`**，改为对每个步骤单独处理，确保某步失败不跳过后续清理。

4. **建立渲染器的直接重置接口**：在 `Live2DRenderer` 接口中增加 `resetToIdle()` 方法，调用者直接驱动，不走 StateFlow 异步路径。

---

## 问题三：五套"是否在播放"状态分散在不同层，没有单一真值源

### 问题描述

整条播放链路中，以下五个状态都在描述同一件事——"当前是否有内容在播放"，但它们各自独立更新，在时序上不保证一致：

```
AvatarPlaybackManager.isPlayingRef       AtomicBoolean，stop() 同步写，onSegmentStart 写
StreamingTtsQueue.playbackState.isActive PlaybackState 密封类，cancel()/start() 写
AudioPlayer._isPlaying                   StateFlow<Boolean>，ExoPlayer 事件驱动写
Live2DRendererImpl.isSpeaking            Boolean，updateState() 写（StateFlow 异步投递）
MainViewModel._isConversationActive      StateFlow<Boolean>，observeAvatarState 写
```

### 触发不一致的场景

用户连续快速发送两条消息时，各状态的更新顺序如下（括号内为触发线程/机制）：

```
T0  cancelCurrentStream()
      isConversationActive = false        （主线程同步）
      streamingTtsQueue.cancel()
        playbackState = Idle              （主线程同步）
        sessionEpoch++                   （原子操作）
      audioPlayer.stop()                 （主线程同步，ExoPlayer 命令入队）
      isPlayingRef = false               （主线程同步）

T1  startStreaming()
      playbackState = Receiving          （主线程同步）

T2  第一个 TtsSegment 到达
      onSegmentStart → isPlayingRef = true  （主线程同步）
      _avatarState = SPEAKING            （StateFlow，异步投递）

T3  ExoPlayer 处理 stop() 命令完成
      AudioPlayer._isPlaying = false     （ExoPlayer 内部线程 → 主线程事件）

T4  Compose 收集器处理 _avatarState = SPEAKING
      renderer.updateState(SPEAKING)
        isSpeaking = true                （主线程，StateFlow 异步）

T5  此时各状态：
      isPlayingRef = true                ✓
      playbackState = Receiving          ✓
      _isPlaying = false                 ← 滞后于实际播放状态
      isSpeaking = true                  ✓（但可能晚到）
      isConversationActive = false       ← 滞后，observeAvatarState 未更新
```

T5 时刻 `_isPlaying = false` 是一个典型的滞后态。`audioPositionSyncJob` 在 `while (isActive && isPlayingRef.get())` 中用 `isPlayingRef` 判断，但调用 `streamingAudioPlayer.isActuallyPlaying()` 内部依赖 `_isPlaying`。两者的值在 T2~T3 之间不一致，导致口型循环在音频实际播放时仍然输出"静止"口型值，产生口型与音频不同步的视觉 bug。

### 更严重的场景：三次快速发送

每次 cancel→start 都产生一个"不一致窗口"，三次叠加后，某个状态可能处于任意中间值，导致：

- `isSpeaking = true` 但 `isPlayingRef = false`：GL 线程强制覆盖嘴型，但口型循环已退出，嘴型被锁死在上次值
- `playbackState = Idle` 但 `isPlayingRef = true`：TTS 队列拒绝接受新 segment，但口型循环认为仍在播放，无限循环直到超时退出

### 涉及文件

- `core/avatar/AvatarPlaybackManager.kt`：`isPlayingRef`、`onSegmentStart` 回调、`startAudioSyncedLipSync()`
- `core/tts/StreamingTtsQueue.kt`：`playbackState`
- `core/audio/AudioPlayer.kt`：`_isPlaying`
- `core/avatar/Live2DRendererImpl.kt`：`isSpeaking`
- `ui/screens/MainViewModel.kt`：`_isConversationActive`

### 改善方向

1. **引入一个中心化的 `SessionState` 密封类**，作为整条播放链路的唯一真值：

   ```kotlin
   sealed class SessionState {
       object Idle : SessionState()
       object Connecting : SessionState()          // 已发起请求，等待第一个事件
       data class Streaming(val epoch: Int) : SessionState()   // 文字和 TTS 正在流入
       data class Speaking(val epoch: Int) : SessionState()    // 音频播放中
       data class WaitingForSegment(val epoch: Int) : SessionState()  // TTS 队列暂停
       object Finishing : SessionState()           // finishInput 已收到，等待播完
   }
   ```

   `AvatarPlaybackManager` 管理这个状态，其他模块订阅它而不是维护自己的标志。

2. **短期可行方案**：把 `audioPositionSyncJob` 的启动和退出条件改为基于 `StreamingTtsQueue.sessionEpoch`，而不是 `isPlayingRef`。口型循环在启动时捕获当前 epoch，每帧校验；`cancel()` 递增 epoch，旧循环立即检测到不匹配并退出。这消除了退出延迟问题，且不需要引入新的架构层。

---

## 问题影响矩阵

| 问题 | 触发场景 | 表现 | 崩溃类型 |
|------|----------|------|----------|
| **问题一** 动作时间估算失准 | 低端设备、长文本回答有多个动作 | SIGSEGV / App 闪退 | 硬崩溃 |
| **问题一** 页面切换时 GL 残留任务 | 快速前后台切换 | SIGSEGV | 硬崩溃 |
| **问题二** 旧 audioPositionSyncJob 未退出 | 连续快速发消息 | 口型抖动 / IllegalStateException | 软崩溃或视觉异常 |
| **问题二** StateFlow 跳过 IDLE | 连续发消息（两条间隔 < 500ms） | isSpeaking 状态泄漏到新会话 | 视觉异常 |
| **问题二** runCatching 吞异常 | ExoPlayer 异常状态（如网络切换） | 播放链路半死锁 | 软崩溃 |
| **问题三** isPlaying 不一致窗口 | 任何 cancel→start | 口型与音频不同步 | 视觉异常 |
| **问题三** 三次快速发送的叠加 | 压力下连续操作 | 嘴型锁死 / 无限循环 | 软崩溃 |

---

## 已执行的修复（不在本文档范围内）

以下问题已在 `CRASH_FIXPLAN_SUPPLEMENT.md` 和相关 commit 中修复，不再是待处理项：

- `@Volatile` 已加到 `overrideMouthOpenY/Form`（N3）
- `trySend` 返回值已检查（N1）
- `sessionEpoch` 已用于事件处理器的跨会话过滤（N2）
- `PlaybackState` 密封类已替代 TTS 队列的多标志（Phase 3）
- `processSegmentSerial` 的 IO 挂起后加了 epoch 双重校验（N2 补充）
- `_messages` 列表已限制最大 100 条（N4）

### 问题三修复（2026-05-06）

在 `AvatarPlaybackManager` 中引入 `sessionEpoch` 机制，用于口型同步循环的精确退出判断：

- 新增 `sessionEpoch: AtomicInteger` 字段
- `startStreaming()` 时递增 epoch，并同步设置 `isPlayingRef = true`
- `stop()` 时递增 epoch，让旧循环立即检测到不匹配
- `startAudioSyncedLipSync()` 和 `startNonStreamingLipSync()` 在循环开头校验 epoch
- epoch 不匹配时立即退出循环，无需等待 `isPlayingRef` 或 ExoPlayer 异步状态

**修复效果**：快速 cancel→start 操作时，旧口型循环在下一帧即可检测到 epoch 变化并退出，消除状态不一致窗口导致的口型抖动问题。

---

## 附：崩溃频率预估（基于场景）

| 操作场景 | 当前崩溃概率 | 根因 |
|----------|-------------|------|
| 单条消息完整对话 | 低 | 无并发压力，路径单一 |
| 连续发 2 条（间隔 < 1s） | 中 | 问题二的旧 Job 未退出 |
| 连续发 3+ 条 | 高 | 问题三的状态不一致叠加 |
| 同时有复杂动作（点头、挥手）| 中高 | 问题一的时间估算失准 |
| 低端设备（< 4GB RAM） | 高 | 问题一 + Live2D 帧时间不稳 |
| 快速前后台切换 | 中 | 问题一的 GL 残留任务 |
