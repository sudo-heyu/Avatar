# 线程安全重构计划

> **状态**：Phase 0 ✅ | Phase 1 ✅ | Phase 2 ✅ | Phase 3 ✅ | Phase 4 ✅
> **最新更新**：2026-05-06 — 全部阶段已完成
> **背景**：应用频繁出现各类崩溃（native crash in ReferenceQueueDaemon、IndexOutOfBoundsException、IllegalStateException、SIGSEGV），根因是播放链路各层之间的线程契约不清晰，竞态条件和死锁风险高度集中。
> **目标**：通过四个阶段的重构，建立清晰的单线程写入模型，消灭所有已知的线程安全问题，使崩溃率降至可接受水平。

---

## 目录

1. [当前线程模型诊断](#1-当前线程模型诊断)
2. [问题清单（含文件行号）](#2-问题清单含文件行号)
3. [目标线程模型设计](#3-目标线程模型设计)
4. [阶段零：紧急止血（P0 死锁/崩溃）](#4-阶段零紧急止血)
5. [阶段一：播放链路线程统一](#5-阶段一播放链路线程统一)
6. [阶段二：回调 → Flow 迁移](#6-阶段二回调--flow-迁移)
7. [阶段三：状态机显式化](#7-阶段三状态机显式化)
8. [测试策略](#8-测试策略)
9. [风险评估与回滚计划](#9-风险评估与回滚计划)

---

## 1. 当前线程模型诊断

### 1.1 执行上下文图谱

当前代码中存在以下并发执行上下文，且相互之间边界模糊：

```
┌─────────────────────────────────────────────────────────────────┐
│ 主线程 (Dispatchers.Main)                                        │
│  ├─ MainViewModel.viewModelScope.launch { ... }                  │
│  ├─ AvatarPlaybackManager.scope (Main + SupervisorJob)           │
│  ├─ StreamingTtsQueue.scope (Main + SupervisorJob)               │
│  ├─ ExoPlayer 内部消息队列 (必须主线程)                           │
│  └─ Player.Listener 回调 (ExoPlayer 保证在主线程回调)             │
│                                                                  │
│ IO 线程池 (Dispatchers.IO)                                       │
│  ├─ StreamingChatClient.launch(Dispatchers.IO) { call.execute }  │
│  ├─ StreamingTtsQueue.withContext(IO) { onSegmentEnqueue }       │
│  ├─ DataStore 内部读盘操作                                        │
│  └─ DynamicBaseUrlInterceptor 中的 runBlocking（阻塞OkHttp线程）  │
│                                                                  │
│ OkHttp Dispatcher 线程池（独立，非 Dispatchers.IO）               │
│  └─ Interceptor.intercept() 执行点                               │
│                                                                  │
│ AudioPlayer.progressScope (Main + SupervisorJob)                 │
│  └─ startProgressTracking 中的 delay 循环                        │
└─────────────────────────────────────────────────────────────────┘
```

### 1.2 共享可变状态访问矩阵

下表列出所有跨线程访问的可变状态：

| 变量 | 定义位置 | 写入线程 | 读取线程 | 有无保护 |
|------|----------|----------|----------|----------|
| `segmentList` | StreamingTtsQueue:34 | Main（processSegmentSerial） | Main（onMediaItemTransition 回调） | ❌ 无 |
| `currentPlayingIndex` | StreamingTtsQueue:35 | Main（多处）| Main（多处） | ❌ 无原子性 |
| `active` | StreamingTtsQueue:37 | Main（cancel/start/enqueue）| Main（回调中）| ❌ 无 |
| `inputFinished` | StreamingTtsQueue:38 | Main（finishInput）| Main（onPlayComplete）| ❌ 无 |
| `currentSegmentEvents` | AvatarPlaybackManager:212 | Main（多处赋值/clear）| Main（协程 while 循环读）| ❌ 无 |
| `isPlaying` | AvatarPlaybackManager:196 | Main（onSegmentStart/onAllComplete）| Main（协程 while 循环）| ❌ 无 |
| `currentSegmentId` | AvatarPlaybackManager:215 | Main（onSegmentStart）| Main（startAudioSyncedLipSync）| ⚠️ 部分用 synchronized |
| `preloadedLipSyncEvents` | AvatarPlaybackManager:226 | IO（processSegmentSerial→onSegmentEnqueue→preloadSegmentLipSync）| Main（startAudioSyncedLipSync:823）| ❌ 跨线程无保护 |
| `resolvedUrl` | NetworkModule:97 | OkHttp线程（首次） | OkHttp线程（后续） | ⚠️ @Volatile，但 check-then-act 非原子 |

### 1.3 关键数据流路径

```
用户发消息
  │
  ▼
MainViewModel.sendMessageToBackend()          [Main]
  │  viewModelScope.launch
  │
  ▼
StreamingChatClient.streamChat()
  │  launch(Dispatchers.IO)                   [IO]
  │  call.execute() — 阻塞 IO 线程
  │  SSE 事件通过 trySend 推入 Flow
  │
  ▼
MainViewModel.collect { event -> }            [Main]
  │  TtsSegmentReady → playbackManager.enqueueSpeechSegment()
  │
  ▼
AvatarPlaybackManager.enqueueSpeechSegment()  [Main]
  │  pendingSegments / streamingTtsQueue.enqueue()
  │
  ▼
StreamingTtsQueue.enqueue()                   [Main]
  │  segmentChannel.trySend(segment)
  │
  ▼
processSegmentSerial() 协程                   [Main]
  │  withContext(Dispatchers.IO)              [IO] ← 切换线程
  │    onSegmentEnqueue → preloadSegmentLipSync  ← 在 IO 写 preloadedLipSyncEvents！
  │  // 切回 Main ← 依赖协程调度
  │  buildAudioUrl(segment.audioUrl)
  │  audioPlayer.play(fullUrl)               [Main] ← ExoPlayer 必须主线程
  │
  ▼
AudioPlayer.onMediaItemTransition 回调        [Main]
  │  onSegmentStart → AvatarPlaybackManager.onSegmentStart
  │
  ▼
AvatarPlaybackManager.startAudioSyncedLipSync()  [Main]
  │  synchronized(lipSyncLock)
  │  读 preloadedLipSyncEvents                ← 在 Main 读 IO 写入的 Map！竞态！
  │  audioPositionSyncJob = scope.launch {}   [Main]
  │    while (isActive && isPlaying) { ... }  [Main，suspend 循环]
  │
  ▼
stop() 被调用时：
  synchronized(lipSyncLock) {                 [Main]
    audioPositionSyncJob?.cancel()            ← cancel 后协程等锁 → 死锁！
  }
```

---

## 2. 问题清单（含文件行号）

### P0 — 必须立即修复，直接导致崩溃

#### P0-1：`synchronized` 持锁期间 `cancel` 协程 → 死锁

- **文件**：`AvatarPlaybackManager.kt:650-663`
- **现象**：`stop()` 持有 `lipSyncLock` 后调用 `audioPositionSyncJob?.cancel()`；而该协程的 `while` 循环体内（约 `:880` 附近）也需要获取 `lipSyncLock`（读取 `currentSegmentEvents`）。主线程持锁等协程退出，协程等锁无法退出 → 循环死锁。
- **崩溃方式**：进程挂死 → ART 检测到后触发 native crash，在 `ReferenceQueueDaemon` 线程可见。

```kotlin
// 当前代码（有死锁风险）
fun stop() {
    synchronized(lipSyncLock) {          // ← 主线程持锁
        audioPositionSyncJob?.cancel()   // ← cancel 后协程想获取同一把锁
        ...
    }
}

// 同一把锁在协程循环里也被使用
while (isActive && isPlaying) {
    synchronized(lipSyncLock) {          // ← 协程等这把锁，永远等不到
        ...
    }
}
```

#### P0-2：`preloadedLipSyncEvents` 跨线程无保护读写

- **文件**：写入位置 `AvatarPlaybackManager.kt:752`（由 `withContext(Dispatchers.IO)` 内的 `onSegmentEnqueue` 触发）；读取位置 `AvatarPlaybackManager.kt:823`（主线程 `startAudioSyncedLipSync`）
- **现象**：`processSegmentSerial` 在 `withContext(IO)` 内调用 `preloadSegmentLipSync`，向 `preloadedLipSyncEvents`（普通 `MutableMap`）写入数据；主线程的 `startAudioSyncedLipSync` 同时读取该 Map。`HashMap` 非线程安全，并发读写可能触发 `ConcurrentModificationException` 或返回损坏数据。

#### P0-3：`segmentList[currentPlayingIndex]` 无原子性保护

- **文件**：`StreamingTtsQueue.kt:60-67`
- **现象**：
  ```kotlin
  // onMediaItemTransition 回调中：
  val completed = segmentList[currentPlayingIndex]  // 读
  currentPlayingIndex++                             // 增
  if (currentPlayingIndex < segmentList.size) {
      val nextSegment = segmentList[currentPlayingIndex]  // 再读
  }
  ```
  若 `cancel()` 在上述步骤中间被调用并执行 `segmentList.clear()`，则 `segmentList[currentPlayingIndex]` 抛 `IndexOutOfBoundsException`。

#### P0-4：`ExoPlayer` 被从非主线程调用

- **文件**：`AudioPlayer.kt:123-127`（`checkMainThread` 只打日志）；`StreamingTtsQueue.kt:174`（`buildAudioUrl` 之后调用 `audioPlayer.play`）
- **现象**：`withContext(Dispatchers.IO)` 之后，协程恢复在哪个线程取决于协程调度器实现。虽然理论上会切回 `Dispatchers.Main`，但代码中没有显式 `withContext(Dispatchers.Main)` 保证。若调度出现意外，ExoPlayer 被从后台线程调用 → `IllegalStateException`。

#### P0-5：Live2D Motion 缓存 use-after-free（2026-05-05 新增）

- **文件**：`LAppModel.cpp:575-640`（`StartMotionByPath` 函数）
- **现象**：聊天触发手势动作（如点头）时，若动作缓存未命中，新加载的 motion 被添加到 `_motions` 缓存，但同时设置了 `autoDelete = true`。当 motion 播放完成后，SDK 自动删除该 motion 对象，但 `_motions` 缓存中仍持有悬空指针。下次触发相同动作时访问已删除的对象 → SIGSEGV 崩溃。
- **崩溃方式**：Signal 11 (SIGSEGV), code 2 (SEGV_ACCERR)，崩溃位置 `CubismMotion::DoUpdateParameters` on GLThread。
- **根因**：违反了 Live2D SDK 的缓存设计模式：
  - **官方 `StartMotion` 模式**：缓存命中的 motion 使用 `autoDelete = false`（默认）；临时加载的 motion 使用 `autoDelete = true` 且**不添加到缓存**。
  - **错误的 `StartMotionByPath` 实现**：缓存未命中时，加载 motion 并添加到缓存，同时设置 `autoDelete = true`，导致缓存中的指针在播放完成后变为悬空指针。

```cpp
// 错误代码（修复前）
if (motion == NULL) {
    motion = LoadMotion(...);
    _motions[cacheKey] = motion;  // 添加到缓存
    autoDelete = true;            // 但设置了 autoDelete！
}
_motionManager->StartMotionPriority(motion, autoDelete, priority);  // 播放完成后 motion 被删除

// 下次访问 _motions[cacheKey] → 访问已删除对象 → 崩溃！
```

---

### P1 — 高概率崩溃，应在阶段一修复

#### P1-1：`currentSegmentEvents` 无同步，`ConcurrentModificationException`

- **文件**：`AvatarPlaybackManager.kt:212`（定义）；`:788`（整体赋值）；`:319`（`clear()`）；`:356`（`while` 循环中遍历）
- **问题**：普通 `MutableList`，多个协程无锁读写。

#### P1-2：`isPlaying` 竞态

- **文件**：`AvatarPlaybackManager.kt:196`（定义）；`:289`（写 true）；`:307`（写 false）；`:349`（循环中读）
- **问题**：普通 `Boolean` var，多个协程并发读写，JVM 不保证可见性。

#### P1-3：`AudioPlayer` 的 var 回调 Lambda 生命周期不对称

- **文件**：`AudioPlayer.kt:50-56`（6 个 `var` 回调属性）
- **问题**：ExoPlayer listener 回调触发时，`onPlayStart` 等 lambda 可能已被外部替换或置 null，且原 lambda 捕获的上层对象可能已销毁，访问已销毁对象 → NPE 或 IllegalStateException。

#### P1-4：`runBlocking` 阻塞 OkHttp 线程

- **文件**：`NetworkModule.kt:102`
- **问题**：OkHttp Dispatcher 线程池默认最大 64 个并发，`runBlocking` 阻塞一个线程等待 DataStore IO 完成。启动时多个并发请求可能耗尽线程池 → ANR。

#### P1-5：`Channel.UNLIMITED` 内存无上限

- **文件**：`StreamingTtsQueue.kt:32`
- **问题**：弱网下后端疯狂推送 segment，或 `active=false` 时 `trySend` 仍被调用，segment 堆积在 channel → OOM。

---

### P2 — 中等概率，影响稳定性

#### P2-1：`TypewriterController` 内部状态并发修改

- **文件**：`MainViewModel.kt`（TypewriterController 定义内部）
- **问题**：`append()` 和 `start()` 可能从不同协程并发调用，`pendingText`/`displayedText` 未同步。

#### P2-2：`resolvedUrl` check-then-act 非原子

- **文件**：`NetworkModule.kt:97-104`
- **问题**：两个并发请求可能同时通过 `resolvedUrl == null` 检查，各自执行一次 `runBlocking`。`@Volatile` 保证可见性但不保证原子性。

#### P2-3：`preloadedLipSyncEvents` 无限增长

- **文件**：`AvatarPlaybackManager.kt:226`
- **问题**：对话被中止时，`preloadedLipSyncEvents` 中已预生成的事件未被清除（`stop()` 里有清除，但若异常路径跳过 `stop()`，则泄漏）。

---

## 3. 目标线程模型设计

### 3.1 核心原则

1. **单一写入者**：每个状态变量只由一个线程/协程写入，其他线程只读。
2. **主线程单一入口**：所有播放状态（`segmentList`、`currentPlayingIndex`、`isPlaying` 等）仅在 `Dispatchers.Main` 修改，ExoPlayer 调用也必须在主线程。
3. **`Mutex` 替代 `synchronized`**：协程边界内使用 `kotlinx.coroutines.sync.Mutex` + `withLock`，避免持普通锁时 `cancel` 协程造成死锁。
4. **IO 操作不持状态锁**：网络、磁盘 IO 操作（`buildAudioUrl`、`DataStore` 读取）在 IO 线程执行完毕后将结果切回主线程使用，不在 IO 线程直接写播放状态。
5. **Flow 替代 var 回调**：跨层通知改为 `SharedFlow`/`StateFlow`，生命周期由 collector 的 scope 控制。

### 3.2 目标执行上下文分配

```
主线程 (Dispatchers.Main) — 唯一可写播放状态的线程
  ├─ AvatarPlaybackManager 所有状态字段的写入
  ├─ StreamingTtsQueue 所有状态字段的写入
  ├─ AudioPlayer（ExoPlayer）的所有 API 调用
  ├─ Player.Listener 回调（ExoPlayer 保证在主线程）
  └─ MainViewModel 的 Flow collect

IO 线程 (Dispatchers.IO) — 纯计算/IO，不写播放状态
  ├─ StreamingChatClient SSE 读取
  ├─ buildAudioUrl（DataStore 读取）
  └─ onSegmentEnqueue（口型预生成，结果通过 withContext(Main) 写回）

OkHttp 线程 — 仅读取已缓存的 resolvedUrl
  └─ DynamicBaseUrlInterceptor.intercept()
```

### 3.3 目标状态所有权

| 状态 | 所有者 | 访问方式 |
|------|--------|----------|
| `segmentList` | StreamingTtsQueue（主线程）| 仅主线程读写，禁止跨线程 |
| `currentPlayingIndex` | StreamingTtsQueue（主线程）| 同上 |
| `active`, `inputFinished` | StreamingTtsQueue（主线程）| 同上 |
| `currentSegmentEvents` | AvatarPlaybackManager（主线程）| 同上，移除 synchronized |
| `isPlaying` | AvatarPlaybackManager | 改为 `AtomicBoolean`，或限定主线程读写 |
| `preloadedLipSyncEvents` | AvatarPlaybackManager（主线程）| 口型预生成结果通过 `withContext(Main)` 写回主线程 |
| `resolvedUrl` | DynamicBaseUrlInterceptor | `AtomicReference<HttpUrl?>`，原子 compareAndSet |
| 播放状态枚举 | StreamingTtsQueue | 用 sealed class `PlaybackState` 替代多个布尔 |

---

## 4. 阶段零：紧急止血

**目标**：消灭 P0 死锁和跨线程裸读写，恢复基本稳定性。  
**工期预估**：1 天  
**可独立合并**：是

### 4.1 修复 P0-1：消灭 `synchronized` + `cancel` 死锁

**改动文件**：`AvatarPlaybackManager.kt`

**方案**：`stop()` 里先取出 job 引用、清空字段，**离开锁后**再取消 job。锁只保护字段赋值，不包含协程操作。

```kotlin
// ✅ 修复后
fun stop() {
    runCatching {
        ttsController.stop()
        streamingTtsQueue.cancel()

        // 1. 持锁期间只做字段赋值，取出 job 引用
        val jobsToCancel: List<Job?>
        synchronized(lipSyncLock) {
            jobsToCancel = listOf(audioPositionSyncJob, streamingLipSyncJob)
            audioPositionSyncJob = null
            streamingLipSyncJob = null
            isPlaying = false
            currentSegmentEvents.clear()
            currentSegmentId = null
            currentStreamOffsetMs = 0L
            currentSegmentText = ""
            preloadedLipSyncEvents.clear()
            pendingSegments.clear()
            nextExpectedSegmentIndex = 0
        }
        // 2. 锁外取消，协程可以自由退出（不需要等锁）
        jobsToCancel.forEach { it?.cancel() }

        isLipSyncActive.set(false)
        currentLipSyncSegmentId.set(null)
        motionQueueJob?.cancel()
        motionQueueJob = null
        expressionTimelineJob?.cancel()
        expressionTimelineJob = null
        cancelWaitingClose()
        currentPlayAction = null
        _mouthState.value = Pair(0f, 0f)
        _avatarState.update { AvatarFullState() }
    }.onFailure {
        Log.w(TAG, "stop failed: ${it.message}")
    }
}
```

同样的模式适用于 `startAudioSyncedLipSync` 里的 `synchronized` 块：先取出需要 cancel 的 job，离开 `synchronized` 块后再 cancel。

### 4.2 修复 P0-2：`preloadedLipSyncEvents` 改为主线程写入

**改动文件**：`StreamingTtsQueue.kt`、`AvatarPlaybackManager.kt`

**方案**：`withContext(Dispatchers.IO)` 块内只做纯 IO（如文件读写、网络等），`preloadSegmentLipSync` 是 CPU 计算，不涉及 IO，直接移出 IO 上下文。

```kotlin
// StreamingTtsQueue.processSegmentSerial
private suspend fun processSegmentSerial(segment: TtsSegmentData) {
    try {
        val index = segmentList.size
        segmentList.add(segment)

        // ✅ onSegmentEnqueue 改为在主线程执行（它内部的 preloadSegmentLipSync 是 CPU 计算）
        onSegmentEnqueue(segment)   // 移除 withContext(IO) 包裹

        val fullUrl = withContext(Dispatchers.IO) {
            buildAudioUrl(segment.audioUrl)   // 只有 IO 操作留在 IO 上下文
        }

        if (!active) return

        if (!audioPlayer.hasMediaItems()) {
            audioPlayer.play(fullUrl)   // 始终在 Main
        } else {
            audioPlayer.enqueue(fullUrl)
        }
        nextEnqueueIndex++
    } catch (e: CancellationException) {
        throw e   // ✅ CancellationException 必须重新抛出，不能被 catch(Exception) 吞掉
    } catch (e: Exception) {
        Log.e(TAG, "[ERROR] ${e.message}", e)
        if (active) {
            active = false
            onError(e)
        }
    }
}
```

### 4.3 修复 P0-3：`segmentList` 访问添加快照保护

**改动文件**：`StreamingTtsQueue.kt`

**方案**：在 `onMediaItemTransition` 回调中，用局部变量捕获当前索引，避免"读-增-读"期间被 `cancel()` 打断。

```kotlin
Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> {
    val completedIndex = currentPlayingIndex  // ✅ 先快照
    if (completedIndex >= 0 && completedIndex < segmentList.size) {
        val completed = segmentList[completedIndex]
        val elapsed = System.currentTimeMillis() - segmentStartTime
        onSegmentComplete(completed, elapsed)
    }
    val nextIndex = completedIndex + 1
    currentPlayingIndex = nextIndex
    if (nextIndex < segmentList.size) {
        val nextSegment = segmentList[nextIndex]
        segmentStartTime = System.currentTimeMillis()
        onSegmentStart(nextSegment)
    } else {
        currentPlayingIndex = -1
        if (inputFinished) {
            active = false
            onAllComplete()
        } else {
            onWaitingForSegment()
        }
    }
}
```

### 4.4 修复 P0-4：ExoPlayer 调用强制主线程保证

**改动文件**：`AudioPlayer.kt`、`StreamingTtsQueue.kt`

在 `StreamingTtsQueue.processSegmentSerial` 中，`buildAudioUrl` 之后、调用 `audioPlayer.play/enqueue` 之前，显式切换到主线程：

```kotlin
val fullUrl = withContext(Dispatchers.IO) { buildAudioUrl(segment.audioUrl) }

// ✅ 显式保证在主线程调用 ExoPlayer
withContext(Dispatchers.Main.immediate) {
    if (!active) return@withContext
    if (!audioPlayer.hasMediaItems()) {
        audioPlayer.play(fullUrl)
    } else {
        audioPlayer.enqueue(fullUrl)
    }
}
```

同时，将 `checkMainThread()` 改为真正的 guard，阻断错误调用：

```kotlin
// AudioPlayer.kt
private fun checkMainThread() {
    check(Looper.myLooper() == Looper.getMainLooper()) {
        "ExoPlayer API 必须在主线程调用，当前线程: ${Thread.currentThread().name}"
    }
}
```

### 4.5 修复 P1-4：`runBlocking` → 原子懒缓存

**改动文件**：`NetworkModule.kt`

用 `AtomicReference` + `compareAndSet` 替代 `@Volatile` + check-then-act：

```kotlin
private class DynamicBaseUrlInterceptor(
    private val settingsDataStore: SettingsDataStore
) : Interceptor {
    private val resolvedUrlRef = AtomicReference<HttpUrl?>(null)

    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val request = chain.request()
        val configuredBaseUrl = resolvedUrlRef.get()
            ?: run {
                val url = runBlocking { settingsDataStore.baseUrl.first() }.toHttpUrlOrNull()
                if (url != null) {
                    resolvedUrlRef.compareAndSet(null, url)   // ✅ 原子写，多线程只写一次
                }
                resolvedUrlRef.get()
            }

        if (configuredBaseUrl == null) return chain.proceed(request)

        val newUrl = request.url.newBuilder()
            .scheme(configuredBaseUrl.scheme)
            .host(configuredBaseUrl.host)
            .port(configuredBaseUrl.port)
            .build()

        android.util.Log.d("DynamicBaseUrl", "URL替换: ${request.url} -> $newUrl")
        return chain.proceed(request.newBuilder().url(newUrl).build())
    }
}
```

### 4.6 修复 P1-5：`Channel` 容量限制

**改动文件**：`StreamingTtsQueue.kt:32`

```kotlin
// 修复前
private val segmentChannel = Channel<TtsSegmentData>(Channel.UNLIMITED)

// ✅ 修复后：设置合理上限，超出时 trySend 返回失败而不是 OOM
private val segmentChannel = Channel<TtsSegmentData>(capacity = 20)
```

同时在 `enqueue()` 里处理发送失败的情况：

```kotlin
fun enqueue(segment: TtsSegmentData) {
    if (!active) start()
    val result = segmentChannel.trySend(segment)
    if (result.isFailure) {
        Log.e(TAG, "[ENQUEUE-FAIL] channel 满，丢弃 segment: ${segment.segmentId}")
    }
}
```

### 4.7 修复 CancellationException 被吞

**改动文件**：`StreamingTtsQueue.kt`、`StreamingChatClient.kt`、`AvatarPlaybackManager.kt`

所有 `catch (e: Exception)` 块必须先检查并重新抛出 `CancellationException`：

```kotlin
// ✅ 正确模式
catch (e: CancellationException) {
    throw e  // 不能被吞掉，协程框架需要它来完成取消流程
} catch (e: Exception) {
    // 处理真实错误
}
```

---

## 5. 阶段一：播放链路线程统一

**目标**：明确所有状态字段的所有者线程，消灭 P1 级别问题。  
**依赖**：阶段零完成  
**工期预估**：2-3 天

### 5.1 `isPlaying` 改为 `AtomicBoolean`

**改动文件**：`AvatarPlaybackManager.kt`

```kotlin
// 修复前
private var isPlaying = false

// ✅ 修复后
private val isPlayingRef = AtomicBoolean(false)
// 读取：isPlayingRef.get()
// 写入：isPlayingRef.set(true/false)
```

所有读写点（约 8 处）同步更新。协程的 `while` 循环改为：

```kotlin
while (isActive && isPlayingRef.get()) { ... }
```

### 5.2 `currentSegmentEvents` 改为主线程独占 + 防御性复制

**改动文件**：`AvatarPlaybackManager.kt`

阶段零已保证 `preloadSegmentLipSync` 在主线程调用，因此 `currentSegmentEvents` 的所有读写都在主线程。移除 `synchronized(lipSyncLock)` 中对该字段的包裹（不再需要），并在协程 `while` 循环里使用快照：

```kotlin
// ✅ 每帧循环开始时快照，避免循环中途被其他代码修改列表
val events = currentSegmentEvents.toList()  // 防御性复制
val (mouthOpen, mouthForm) = lipSyncAnimator.advanceFrame(
    events = events,
    positionMs = audioPosition,
    ...
)
```

### 5.3 `TypewriterController` 并发写保护

**改动文件**：`MainViewModel.kt`（TypewriterController 内联类）

用 `Mutex` 保护 `pendingText` 和 `displayedText` 的并发修改：

```kotlin
private val mutex = Mutex()

suspend fun append(text: String) = mutex.withLock {
    pendingBuffer.append(text)
}

suspend fun start(messageId: String, waitForSync: Boolean) = mutex.withLock {
    // 重置状态
    pendingBuffer.clear()
    displayedText.clear()
    ...
}
```

### 5.4 明确 `StreamingTtsQueue` 的主线程调用契约

**改动文件**：`StreamingTtsQueue.kt`

在类头注释中声明线程契约，并在关键公开方法入口添加断言：

```kotlin
/**
 * 流式 TTS 分段播放队列。
 *
 * 线程契约：所有公开方法（enqueue、finishInput、cancel、start）必须从主线程调用。
 * 内部协程使用 Dispatchers.Main scope，状态字段无需额外同步。
 */
class StreamingTtsQueue(...) {

    private fun assertMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "StreamingTtsQueue 必须在主线程调用，当前: ${Thread.currentThread().name}"
        }
    }

    fun enqueue(segment: TtsSegmentData) {
        assertMainThread()
        ...
    }

    fun finishInput() {
        assertMainThread()
        ...
    }

    fun cancel() {
        assertMainThread()
        ...
    }
}
```

### 5.5 `AvatarPlaybackManager` 主线程断言

**改动文件**：`AvatarPlaybackManager.kt`

所有公开方法（`enqueueSpeechSegment`、`finishStreamingInput`、`stop`、`startStreaming`）添加主线程断言：

```kotlin
private fun assertMainThread() {
    check(Looper.myLooper() == Looper.getMainLooper()) {
        "AvatarPlaybackManager 必须在主线程调用，当前: ${Thread.currentThread().name}"
    }
}
```

---

## 6. 阶段二：回调 → Flow 迁移

**目标**：消灭 P1-3（var 回调 Lambda 生命周期问题），用 Flow 替代 6 个 `var` 回调属性。  
**依赖**：阶段一完成  
**工期预估**：2 天

### 6.1 `AudioPlayer` 回调改为 `SharedFlow`

**改动文件**：`AudioPlayer.kt`、`StreamingTtsQueue.kt`

**现状**：

```kotlin
var onPlayStart: (() -> Unit)? = null
var onPlayComplete: (() -> Unit)? = null
var onPlayError: ((String) -> Unit)? = null
var onMediaItemTransition: ((reason: Int, mediaItem: MediaItem?) -> Unit)? = null
var onBufferingStateChanged: ((isBuffering: Boolean) -> Unit)? = null
var onIsPlayingChanged: ((isPlaying: Boolean) -> Unit)? = null
```

**目标**：

```kotlin
sealed class AudioPlayerEvent {
    object PlayStart : AudioPlayerEvent()
    object PlayComplete : AudioPlayerEvent()
    data class PlayError(val message: String) : AudioPlayerEvent()
    data class MediaItemTransition(val reason: Int, val mediaItem: MediaItem?) : AudioPlayerEvent()
    data class BufferingStateChanged(val isBuffering: Boolean) : AudioPlayerEvent()
    data class IsPlayingChanged(val isPlaying: Boolean) : AudioPlayerEvent()
}

private val _events = MutableSharedFlow<AudioPlayerEvent>(
    extraBufferCapacity = 16,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
)
val events: SharedFlow<AudioPlayerEvent> = _events.asSharedFlow()
```

`StreamingTtsQueue` 改为在 `init` 里 `collect` 该 Flow，并在自己的 scope 取消时自动停止：

```kotlin
init {
    scope.launch {
        audioPlayer.events.collect { event ->
            when (event) {
                is AudioPlayerEvent.MediaItemTransition -> handleTransition(event.reason, event.mediaItem)
                is AudioPlayerEvent.PlayComplete -> handlePlayComplete()
                is AudioPlayerEvent.PlayError -> handleError(event.message)
                is AudioPlayerEvent.BufferingStateChanged -> onBufferingStateChanged?.invoke(event.isBuffering)
                else -> {}
            }
        }
    }
}
```

**收益**：
- 无需在 `cancel()` 里手动清除 lambda 引用
- collector 的 scope 取消时自动停止接收，无泄漏
- `SharedFlow` 内置背压保护，不会因回调堆积崩溃

### 6.2 迁移步骤

1. 在 `AudioPlayer` 中定义 `AudioPlayerEvent` sealed class
2. 将 `Player.Listener` 回调内的 `onXxx?.invoke()` 改为 `_events.tryEmit(AudioPlayerEvent.XxxEvent(...))`
3. 删除 6 个 `var` 回调属性
4. 修改 `StreamingTtsQueue` 的构造参数，移除所有 `on*` 回调 lambda，改为在 `init` 中 `collect` `audioPlayer.events`
5. 修改 `AvatarPlaybackManager`，移除注册到 `audioPlayer` 上的 lambda，改为订阅

---

## 7. 阶段三：状态机显式化

**目标**：用 sealed class 替代 6 个布尔标志，消除非法状态组合。  
**依赖**：阶段二完成  
**工期预估**：2-3 天

### 7.1 当前状态标志分析

`StreamingTtsQueue` 中存在以下状态变量：

```
active: Boolean
inputFinished: Boolean
currentPlayingIndex: Int  (-1 表示未播放)
isTransitioning: AtomicBoolean
isCompleting: AtomicBoolean
```

这 5 个变量理论上有 `2 × 2 × N × 2 × 2 = 16N` 种组合，但合法组合远少于此，非法组合是 bug 的温床。

### 7.2 目标状态机

```
                   start()
    ┌──────────────────────────────────┐
    │                                  ▼
  IDLE ──────────────────────────► READY
                                    │
                            enqueue(segment)
                                    │
                                    ▼
                              BUFFERING ◄─────── onWaitingForSegment()
                                    │
                        PLAYLIST_CHANGED回调
                                    │
                                    ▼
                               PLAYING ◄──────── AUTO过渡/下一段
                                    │
                        finishInput() + 最后一段播完
                                    │
                                    ▼
                             ALL_COMPLETE
                                    │
                              stop()/cancel()
                                    │
                                    ▼
                                  IDLE
```

### 7.3 状态机实现

**改动文件**：`StreamingTtsQueue.kt`

```kotlin
private sealed class PlaybackState {
    object Idle : PlaybackState()
    object Ready : PlaybackState()          // start() 已调用，等待第一个 segment
    object Buffering : PlaybackState()      // 已有 segment，等待更多
    data class Playing(
        val segmentIndex: Int               // 当前正在播放的 segment 索引
    ) : PlaybackState()
    object AllComplete : PlaybackState()
}

private var state: PlaybackState = PlaybackState.Idle

// 状态转换函数（主线程调用）
private fun transitionTo(newState: PlaybackState) {
    val oldState = state
    state = newState
    Log.d(TAG, "[STATE] $oldState -> $newState")
    // 状态驱动副作用（如回调）
    when (newState) {
        is PlaybackState.Playing -> {
            val segment = segmentList.getOrNull(newState.segmentIndex) ?: return
            segmentStartTime = System.currentTimeMillis()
            onSegmentStart(segment)
        }
        is PlaybackState.AllComplete -> {
            onAllComplete()
        }
        is PlaybackState.Buffering -> {
            onWaitingForSegment()
        }
        else -> {}
    }
}
```

### 7.4 移除冗余 `AtomicBoolean`

状态机引入后，`isTransitioning` 和 `isCompleting` 的竞态保护由状态转换函数的单线程性质（主线程）自然消除，可以删除这两个 `AtomicBoolean`。

---

## 8. 测试策略

### 8.1 单元测试要求

**阶段零完成后必须通过的测试**：

| 测试场景 | 验证点 |
|----------|--------|
| `stop()` 在协程 while 循环中间被调用 | 不死锁，3 秒内返回 |
| `cancel()` 在 `processSegmentSerial` 的 `withContext(IO)` 中间被调用 | `CancellationException` 正确传播，不吞掉 |
| 两个线程同时通过 `resolvedUrl == null` | `compareAndSet` 保证只有一个执行 `runBlocking` |
| `segmentList` 在 `onMediaItemTransition` 回调中 `cancel()` 并发 | 不抛 `IndexOutOfBoundsException` |

**阶段一完成后**：

| 测试场景 | 验证点 |
|----------|--------|
| `audioPlayer.play()` 从 IO 线程调用 | `checkMainThread()` 抛 `IllegalStateException` |
| `enqueue()` 从后台线程调用 | `assertMainThread()` 断言失败 |
| 快速发送 30 个 segment，`Channel` 容量 20 | 第 21 个 segment 被丢弃，日志有记录，不崩溃 |

### 8.2 集成测试场景

1. **连续中断测试**：用户发消息后立即再次发消息，连续 20 次，验证不崩溃。
2. **弱网测试**：使用 Android Emulator 网络限速至 10kb/s，发送多条消息，验证不 OOM、不 ANR。
3. **后台切换测试**：播放中按 Home 键 → 等待 30 秒 → 返回，验证状态恢复正常。
4. **快速旋转测试**：播放中旋转屏幕 10 次，验证 ViewModel 清理和重建不崩溃。

### 8.3 崩溃监控指标

重构前后应对比以下指标：

- `IllegalStateException`（ExoPlayer 线程违规）发生次数
- `IndexOutOfBoundsException`（`segmentList` 竞态）发生次数
- 进程 ANR 次数（OkHttp 线程阻塞超时）
- 内存峰值（Channel.UNLIMITED vs Channel(20)）

---

## 9. 风险评估与回滚计划

### 9.1 风险矩阵

| 改动 | 风险 | 缓解措施 |
|------|------|----------|
| `stop()` 锁外 cancel | 低——逻辑等价，只是顺序调整 | 单元测试覆盖并发 cancel |
| `withContext(Main.immediate)` 包裹 ExoPlayer 调用 | 低——只增加线程保证 | 对比播放延迟指标 |
| `Channel` 容量限制为 20 | 中——极长段落可能丢失 | 配合后端 segment 数量监控，必要时调整上限 |
| `AudioPlayer` 回调 → `SharedFlow` | 中——接口变化影响所有调用方 | 先保留旧接口作为 deprecated 过渡，分两步合并 |
| 状态机重构 | 高——逻辑变化大，需完整测试 | 在 feature 分支开发，所有集成测试通过后合并 |

### 9.2 分支策略

```
main
  └─ feature/thread-safety-phase0   (阶段零，1天，独立可回滚)
       └─ feature/thread-safety-phase1  (阶段一，依赖 phase0)
            └─ feature/thread-safety-phase2  (阶段二)
                 └─ feature/thread-safety-phase3  (阶段三)
```

每个阶段独立 PR，code review 通过且手动测试 OK 后合并。

### 9.3 回滚触发条件

- 合并后 24 小时内崩溃率超过合并前的 120%，立即 revert
- 出现新的必现崩溃路径，立即 revert
- 播放延迟（首段音频启动时间）增加超过 200ms，调查后决定

---

## 10. 阶段四：Native 层 GL 上下文生命周期安全

**目标**：修复应用后台切换导致的 Live2D Native 崩溃（SIGSEGV）。  
**依赖**：阶段零~二完成  
**工期预估**：1 天

### 10.1 问题分析

**崩溃现象**：
- 用户从应用外切换回应用，重新问问题后崩溃
- Signal 11 (SIGSEGV), code 2 (SEGV_ACCERR)
- 崩溃位置：`CubismMotion::DoUpdateParameters` on GLThread
- Scudo 错误："failed to get the guest state header for thread"

**根因分析**：
1. 当应用进入后台，GL 上下文可能被系统回收（即使设置了 `preserveEGLContextOnPause = true`）
2. 当应用恢复时，`nativeOnSurfaceCreated` 被调用，触发 `ReloadAllRenderers()`
3. 但动作缓存 `LAppModel._motions` 中的数据可能引用了无效的 GL 资源
4. 当动作更新时访问无效内存，导致 SIGSEGV

**关键代码路径**：
```
nativeOnSurfaceCreated()
  → LAppDelegate::OnSurfaceCreate()
    → LAppLive2DManager::ReloadAllRenderers()
      → LAppModel::ReloadRenderer()
        // 问题：只重建渲染器，未清除动作缓存

nativeOnDrawFrame()
  → LAppDelegate::Run()
    → LAppView::Render()
      → LAppLive2DManager::OnUpdate()
        → LAppModel::Update()
          → _motionManager->UpdateMotion() // 崩溃点
```

### 10.2 修复方案

**改动文件**：`LAppModel.cpp`, `LAppDelegate.cpp`, `LAppLive2DManager.cpp`

#### 10.2.1 `LAppModel::ReloadRenderer()` 清除动作缓存

```cpp
void LAppModel::ReloadRenderer()
{
    // 停止所有正在播放的动作，防止在重建渲染器时访问无效资源
    if (_motionManager != NULL)
    {
        _motionManager->StopAllMotions();
    }

    // 清除动作缓存，因为 GL 上下文可能已丢失
    // 动作文件会在下次播放时重新加载
    ReleaseMotions();
    LAppPal::PrintLogLn("[APP]ReloadRenderer: Motion cache cleared due to GL context change");

    DeleteRenderer();
    CreateRenderer(...);
    SetupTextures();
}
```

#### 10.2.2 `LAppDelegate::OnSurfaceCreate()` 添加安全检查

```cpp
void LAppDelegate::OnSurfaceCreate()
{
    // ... GL setup ...

    // 安全检查：确保 CubismFramework 已初始化后再获取 manager
    if (!CubismFramework::IsInitialized())
    {
        LAppPal::PrintLogLn("[APP]OnSurfaceCreate: CubismFramework not initialized, skipping");
        return;
    }

    LAppLive2DManager* manager = LAppLive2DManager::GetInstance();
    if (manager == NULL)
    {
        LAppPal::PrintLogLn("[APP]OnSurfaceCreate: Failed to get manager instance");
        return;
    }

    if (manager->GetModelNum() > 0)
    {
        LAppPal::PrintLogLn("[APP]OnSurfaceCreate: Reloading renderers for %d models", modelNum);
        manager->ReloadAllRenderers();
    }
}
```

#### 10.2.3 `LAppDelegate::Run()` 添加防御性检查

```cpp
void LAppDelegate::Run()
{
    // 安全检查：确保 CubismFramework 已初始化
    if (!CubismFramework::IsInitialized())
    {
        return;
    }

    // ... existing code ...

    if (_view != NULL)
    {
        try {
            _view->Render();
        } catch (...) {
            LAppPal::PrintLogLn("[APP]Run: Exception in _view->Render(), skipping frame");
        }
    }
}
```

#### 10.2.4 `LAppLive2DManager::OnUpdate()` 添加防御性检查

```cpp
void LAppLive2DManager::OnUpdate() const
{
    std::lock_guard<std::mutex> lock(_managerMutex);

    // 安全检查：确保 CubismFramework 已初始化
    if (!CubismFramework::IsInitialized())
    {
        return;
    }

    // ... existing code ...

    LAppView* view = LAppDelegate::GetInstance()->GetView();
    if (view == NULL)
    {
        continue;
    }

    try {
        model->Update();
        model->Draw(projection);
    } catch (...) {
        LAppPal::PrintLogLn("[APP]OnUpdate: Exception in model update/draw");
    }
}
```

### 10.2.5 P0-5 修复：`StartMotionByPath` 缓存 motion 的 autoDelete 问题（2026-05-05）

**问题**：`StartMotionByPath` 在缓存未命中时，加载 motion 并添加到 `_motions` 缓存，但同时设置了 `autoDelete = true`，导致 motion 播放完成后被 SDK 删除，缓存中留下悬空指针。

**修复方案**：对于缓存到 `_motions` 的 motion，必须设置 `autoDelete = false`，motion 对象由 `ReleaseMotions()` 或析构函数统一清理。

```cpp
// ✅ 修复后（LAppModel.cpp:575-640）
CubismMotionQueueEntryHandle LAppModel::StartMotionByPath(const csmChar* motionPath, ...)
{
    // Check cache first
    csmString cacheKey = csmString(motionPath);
    CubismMotion* motion = static_cast<CubismMotion*>(_motions[cacheKey.GetRawString()]);

    if (motion != NULL) {
        // Cache hit - reuse cached motion
        motion->SetBeganMotionHandler(onBeganMotionHandler);
        motion->SetFinishedMotionHandler(onFinishedMotionHandler);
    } else {
        // Cache miss - load motion file
        motion = LoadMotion(...);
        // Cache the motion for future use
        _motions[cacheKey] = motion;
        // CRITICAL FIX: Do NOT set autoDelete=true for cached motions!
        // Cached motions must persist in _motions for reuse.
    }

    // For cached motions, autoDelete must be false to prevent use-after-free.
    // The motion will be cleaned up in ReleaseMotions() or destructor.
    return _motionManager->StartMotionPriority(motion, false, priority);
}
```

**关键改动**：
1. 移除 `autoDelete` 变量，将 `StartMotionPriority` 的第二个参数固定为 `false`
2. 添加详细注释说明为何缓存 motion 不能设置 `autoDelete = true`
3. motion 对象在 `ReleaseMotions()` 或 `LAppModel` 析构时统一清理

**验证测试**：
| 测试场景 | 验证点 |
|----------|--------|
| 触发点头动作 → 等待完成 → 再次触发点头 | 不崩溃，动作正常播放 |
| 连续触发多个不同动作（点头、摇头、挥手）| 不崩溃，所有动作正常播放 |
| 播放中切换后台 → 返回 → 触发动作 | 不崩溃，GL 上下文重建后动作正常 |

### 10.3 测试场景

| 测试场景 | 验证点 |
|----------|--------|
| 播放中按 Home → 等待 10 秒 → 返回 | 不崩溃，Live2D 继续播放 |
| 播放中按 Home → 等待 60 秒 → 返回 | 不崩溃，GL 上下文重建成功 |
| 播放中切换到其他应用 → 返回 → 发新消息 | 不崩溃，TTS 和 Live2D 正常工作 |
| 连续后台/前台切换 10 次 | 不崩溃，内存无泄漏 |

---

## 附录：关键文件线程契约速查

| 文件 | 调用线程要求 | 当前状态 |
|------|-------------|---------|
| `AudioPlayer` 所有公开方法 | 主线程 | ⚠️ 仅日志检查 |
| `StreamingTtsQueue` 所有公开方法 | 主线程 | ❌ 无检查 |
| `AvatarPlaybackManager` 所有公开方法 | 主线程 | ❌ 无检查 |
| `GuideRepository.buildAudioUrl` | 任意（suspend，内部切换）| ✅ 正确 |
| `StreamingChatClient.streamChat` | 任意（返回 Flow）| ✅ 正确 |
| `DynamicBaseUrlInterceptor.intercept` | OkHttp 线程（任意）| ⚠️ runBlocking 存在 |
| `MainViewModel` 所有方法 | 主线程（viewModelScope）| ✅ 正确 |

---

## 附录 B：Native 层修复清单

| 问题 | 文件 | 修复状态 | 说明 |
|------|------|---------|------|
| GL 上下文丢失后访问无效资源 | `LAppModel::ReloadRenderer` | ✅ 已修复 | 清除 motion 缓存，下次播放时重新加载 |
| CubismFramework 未初始化访问 | `LAppDelegate::Run` | ✅ 已修复 | 添加 `IsInitialized()` 检查 |
| CubismFramework 未初始化访问 | `LAppDelegate::OnSurfaceCreate` | ✅ 已修复 | 添加安全检查和日志 |
| CubismFramework 未初始化访问 | `LAppLive2DManager::OnUpdate` | ✅ 已修复 | 添加安全检查，跳过渲染帧 |
| View 为空时访问 | `LAppLive2DManager::OnUpdate` | ✅ 已修复 | 添加 NULL 检查 |
| 模型更新异常 | `LAppLive2DManager::OnUpdate` | ✅ 已修复 | 添加 try-catch 保护 |
| **Motion 缓存 use-after-free** | `LAppModel::StartMotionByPath` | ✅ 已修复 | 缓存 motion 使用 `autoDelete=false` |

---

## 附录 C：Live2D SDK Motion 缓存设计模式

### 官方设计原则（来自 SDK 源码）

Live2D SDK 提供两种 motion 生命周期管理方式：

1. **临时 motion（autoDelete = true）**
   - 不缓存，每次播放时加载
   - 播放完成后由 SDK 自动删除
   - 适用于：一次性播放、内存敏感场景
   - 官方示例：`StartMotion` 中缓存未命中的情况

2. **缓存 motion（autoDelete = false）**
   - 存储在 `_motions` Map 中复用
   - 播放完成后不删除，等待下次使用
   - 由 `ReleaseMotions()` 或析构函数统一清理
   - 适用于：频繁播放的动作（如 Idle、点头、摇头）

### `CubismMotionQueueEntry` 析构函数

```cpp
// CubismMotionQueueEntry.cpp:32-38
CubismMotionQueueEntry::~CubismMotionQueueEntry()
{
    if (_autoDelete && _motion)
    {
        ACubismMotion::Delete(_motion); // autoDelete=true 时删除 motion
    }
}
```

### 正确用法对比

| 场景 | 缓存行为 | autoDelete | 生命周期 |
|------|---------|------------|---------|
| `StartMotion` 缓存命中 | 已在 `_motions` | false（默认） | 由缓存管理 |
| `StartMotion` 缓存未命中 | 不添加到缓存 | true | 播放完成即删除 |
| `StartMotionByPath` 缓存命中 | 已在 `_motions` | false | 由缓存管理 |
| `StartMotionByPath` 缓存未命中 | **添加到缓存** | **必须 false** | **由缓存管理** |
