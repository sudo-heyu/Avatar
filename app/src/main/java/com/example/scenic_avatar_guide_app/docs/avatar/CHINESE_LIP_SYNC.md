# 高精度中文口型同步实现方案

版本：v1.2  
日期：2026-04-30  
定位：Android 端中文数字人口型系统重构（含流式音频同步）

---

## 一、现状问题

当前数字人口型系统存在严重缺陷：

1. `RemoteTTSController` 使用字符时长估算（180ms/字）驱动口型
2. `VisemeType.fromChar()` 只能识别拉丁字母，所有中文字符均映射到 `NEUTRAL`（mouthOpen=0.1）
3. 结果：说话时嘴巴几乎不动，完全无法体现中文发音特征
4. 后端已设计 `marks` 时间戳接口，但 Android 端未消费

---

## 二、设计目标

1. **音素级精度**：每个汉字的拼音音素（声母+韵母）都有对应的口型参数
2. **时间精确对齐**：优先使用后端 `marks`，无 marks 时使用音频振幅辅助校准
3. **自然过渡**：相邻口型之间使用缓动插值，模拟协同发音
4. **零额外网络开销**：非流式下 marks 随 TTS 响应一次性返回；流式下 marks 随 `tts_segment` 分段返回，不要求移动端额外请求 TTS
5. **向后兼容**：保留现有接口，新功能可开关

---

## 三、技术架构（三层）

```text
Layer 3: 渲染层 (Live2D / Placeholder)
  - mouthOpen + mouthForm -> Live2D 参数
  - 60fps 口型插值渲染
  - mouthOpen 缩放因子控制整体张开度
  - mouthForm 偏移控制嘴型大小

Layer 2: 动画层 (LipSyncAnimator)
  - 音素时间轴管理
  - 协同发音平滑 (Coarticulation Blending)
  - 音频振幅微调 (Audio Amplitude Feedback)
  - 声母过渡保留（非简单合并）
  - SIL 事件强制闭唇

Layer 1: 音素层 (ChinesePhonemeEngine)
  - 汉字 -> 拼音 -> 声母+韵母 -> 音素事件
  - 多音字字典 (景区专用词库)
  - 轻声/儿化音处理
```

---

## 四、口型参数空间

扩展为 15 种音素口型，覆盖所有汉语拼音：

| 口型ID | 描述 | mouthOpen | mouthForm | 对应拼音 |
|--------|------|-----------|-----------|----------|
| SIL | 静音/闭唇 | 0.0 | 0.0 | 停顿 |
| BP | 双唇塞音 | 0.05 | 0.0 | b, p, m |
| F | 唇齿音 | 0.2 | -0.2 | f |
| DT | 舌尖中音 | 0.3 | 0.0 | d, t, n, l |
| GK | 舌根音 | 0.55 | 0.0 | g, k, h |
| JQ | 舌面音 | 0.4 | -0.4 | j, q, x |
| ZC | 舌尖前音 | 0.35 | 0.0 | z, c, s |
| ZH | 舌尖后音 | 0.4 | 0.0 | zh, ch, sh, r |
| A | 开口呼 | 1.0 | 0.0 | a, ai, an, ang, ao |
| O | 合口呼圆唇 | 0.85 | 0.6 | o, ou, ong |
| E | 半开口 | 0.7 | 0.0 | e, ei, en, eng, er |
| I | 齐齿呼扁嘴 | 0.55 | -0.5 | i, ie, iu, in, ing |
| U | 合口呼收圆 | 0.65 | 0.4 | u, ui, un |
| V | 撮口呼 | 0.55 | -0.3 | u:, u:e, u:n |
| UA | 复合元音过渡 | 0.9 | 0.2 | ua, uai, uan, uang, iao, ian |

**注**：复合韵母（如 "huan" = h + u + an）拆分为多个连续口型，而非固定口型。

---

## 五、拼音到音素事件分解

每个汉字分解为一个或多个音素事件：

```text
例："欢" (huan)
  -> 声母 h  -> 音素 GK (舌根音), mouthOpen=0.55, mouthForm=0.0
  -> 韵母 u  -> 音素 U (合口呼), mouthOpen=0.65, mouthForm=0.4
  -> 韵尾 an -> 音素 A (开口呼), mouthOpen=0.9, mouthForm=0.0

例："迎" (ying)
  -> 零声母 -> 跳过
  -> 韵母 i  -> 音素 I, mouthOpen=0.55, mouthForm=-0.5
  -> 韵尾 ng -> 音素 GK (舌根鼻音), mouthOpen=0.55, mouthForm=0.0
```

声母时长约占 20-30%（根据声母类型），韵母占 70-80%。韵母内部再按介音/主元音/韵尾分配。

---

## 六、协同发音平滑

真实说话时口型不会在音素边界瞬间切换。使用滑动窗口加权平均：

```text
当前口型参数 = 0.5 x 当前音素目标 + 0.3 x 前一音素 + 0.2 x 后一音素
```

在 marks 给定的时间区间内，按上述权重计算每一帧的 mouthOpen 和 mouthForm。

**连续大开口音增强**：当连续出现大开口音（>=0.8）时，增强前后音素权重到 25%，让过渡变化更明显。

---

## 六之一、按字合并音素事件（增强版）

同一汉字的声母+韵母若完全合并，会导致连续开口音时嘴巴一直大张无变化。优化方案是**保留声母过渡阶段**：

```kotlin
// LipSyncAnimator.kt
fun mergeEventsByChar(events: List<PhonemeEvent>): List<PhonemeEvent> {
    return events.groupBy { it.charIndex }
        .toSortedMap()
        .values
        .flatMap { group ->
            if (group.size == 1) {
                listOf(group.first())
            } else if (group.any { it.viseme == VisemeType.BP }) {
                // 含闭唇声母（b,p,m）的字保留原样，确保闭嘴动作不被吞掉
                group.sortedBy { it.startMs }
            } else {
                // 获取声母和韵母
                val sorted = group.sortedBy { it.startMs }
                val initial = sorted.firstOrNull()
                val finalEvents = sorted.drop(1)
                
                // 计算声母时长占比
                val initialRatio = ChineseVisemeMapper.getInitialDurationRatio(initial?.phoneme)
                val initialDuration = (charDuration * initialRatio).toLong()
                
                if (initialDuration > 30 && initial?.viseme != VisemeType.SIL) {
                    // 保留声母过渡阶段
                    listOf(
                        // 声母阶段
                        PhonemeEvent(phoneme, startMs, startMs + initialDuration, initial.viseme, ...),
                        // 韵母阶段（扩展到字结束）
                        PhonemeEvent(phoneme, startMs + initialDuration, endMs, finalViseme, ...)
                    )
                } else {
                    // 声母时长太短或无声母，只保留韵母
                    listOf(finalEvent)
                }
            }
        }
}
```

**声母时长占比**：
| 声母类型 | 时长占比 | 示例 |
|---------|---------|------|
| 爆破音 (b,p,d,t,g,k,zh,ch,z,c,j,q) | 30% | 需要闭气准备 |
| 擦音 (f,h,sh,r,s,x) | 20% | 连续气流 |
| 鼻音/边音 (m,n,l) | 20% | 连续 voiced |
| 零声母/半元音 (y,w,空) | 0% | 无声母 |

**效果**：
- 消除字内声母导致的快速闭合抖动
- 保留声母过渡阶段，让连续开口音有明显起伏变化

---

## 六之二、SIL 事件强制闭唇

遇到标点符号或停顿时，强制闭唇以增加自然度：

```kotlin
// AvatarPlaybackManager.kt / LipSyncAnimator.kt
if (currentEvent.viseme == VisemeType.SIL) {
    // SIL 事件：强制闭唇
    lastMouthOpen = lerp(lastMouthOpen, 0f, 0.5f)
    lastMouthForm = lerp(lastMouthForm, 0f, 0.5f)
}

// 字间过渡时，如果前后有 SIL 事件，也强制闭唇
if (prevEvent.viseme == VisemeType.SIL || nextEvent.viseme == VisemeType.SIL) {
    lastMouthOpen = lerp(lastMouthOpen, 0f, 0.4f)
}
```

---

## 六之三、音频同步模式（流式播放核心）

非流式模式下，口型动画按系统时间推进；流式模式下，每段 TTS 音频独立播放，口型必须与音频进度严格对齐。

### 核心机制

```kotlin
// AvatarPlaybackManager.kt
private fun startAudioSyncedLipSync() {
    val lipSyncLeadMs = 30L  // 超前补偿

    audioPositionSyncJob = scope.launch {
        while (isActive && isPlaying) {
            val currentAudioPosition = streamingAudioPlayer.getCurrentPosition()
            val lipSyncPosition = (currentAudioPosition + lipSyncLeadMs).coerceAtLeast(0L)

            // 查找当前口型事件
            val currentEvent = currentSegmentEvents.find {
                lipSyncPosition in it.startMs..it.endMs
            }

            // 计算口型并更新状态
            val (open, form) = calculateMouthShapeForPosition(lipSyncPosition)
            _avatarState.update { it.copy(mouthOpen = open, mouthForm = form) }

            delay(16) // ~60fps
        }
    }
}
```

### 超前补偿

ExoPlayer 报告的播放位置可能有轻微延迟，且口型需要提前准备。使用 `lipSyncLeadMs = 30L` 让口型比音频提前 30ms 触发，确保视觉与听觉同步。

### 字间过渡（Gap 处理）

不在任何音素事件内时（两事件之间），动态保持口型开度，避免快语速时频繁闭合：

```kotlin
val gap = nextEvent.startMs - prevEvent.endMs
val holdFactor = when {
    gap >= 150 -> 0.4f        // 长停顿，闭合更多
    gap >= 80 -> 0.5f         // 中等停顿
    avgOpen >= 0.7f -> 0.5f   // 连续高开口音，要有起伏
    avgOpen >= 0.5f -> 0.55f
    else -> 0.65f
}
```

### 事件内缓动

在每个音素事件内应用"中间高两端低"的缓动，模拟自然说话：

```kotlin
val easedOpen = currentEvent.viseme.mouthOpen * when {
    progress < 0.3f -> 0.7f + progress      // 进入阶段：从70%渐增
    progress > 0.7f -> 0.7f + (1f - progress) // 退出阶段：渐减到70%
    else -> 1f                               // 中间阶段：100%
}
```

---

## 六之四、保底机制

流式播放中可能出现各种异常，需要多层保底：

| 保底层级 | 触发条件 | 行为 |
|---------|---------|------|
| 进度停滞 | 音频位置连续 3 帧不变 | 强制闭合嘴巴，结束口型协程 |
| 音频结束 | 播放位置接近音频末尾（提前 30ms） | 强制闭合嘴巴，结束口型协程 |
| 时间轴结束 | 口型位置超过最后事件 endMs | 保持闭合，继续循环等待（可能下一段马上开始） |
| 音频未播放 | `isActuallyPlaying() == false` | 强制闭合嘴巴，结束口型协程 |

---

## 六之五、渲染层参数调整

### mouthOpen 缩放

控制整体嘴唇张开程度，避免嘴巴张太大：

```kotlin
// Live2DRendererImpl.kt
val amplifiedMouthOpen = (currentMouthOpen * 0.65f).coerceAtMost(1.0f)
```

当前使用 `0.65f` 缩放因子，最大张开度约为 65%。

### mouthForm 偏移

控制嘴型形状（扁嘴/圆嘴）：

```kotlin
// Live2DRendererImpl.kt
var mouthWidthScale: Float = 0.3f
val scaledMouthForm = (mouthForm + mouthWidthScale).coerceIn(-1f, 1.5f)
```

| 音素 | mouthForm 原值 | 偏移后值 | 效果 |
|-----|---------------|---------|------|
| i (扁嘴) | -0.5 | -0.2 | 仍扁 |
| a (中性) | 0.0 | 0.3 | 略圆 |
| o (圆嘴) | 0.6 | 0.9 | 圆 |

---

## 七、后端 Marks 集成

marks 字段扩展为字级，每个 mark 增加 `phonemes`：

```json
{
  "text": "欢",
  "start_ms": 340,
  "end_ms": 540,
  "phonemes": ["h", "u", "an"]
}
```

Android 端 `RemoteTTSController` 解析 marks，生成精确时间轴。

如果后端暂未支持 phonemes，Android 端使用 `ChinesePhonemeEngine` 本地分解。

---

## 八、音频振幅辅助校准

当 marks 精度不足或缺失时，从 ExoPlayer 获取音频振幅：

```kotlin
audioPlayer.getCurrentAmplitude() -> 映射到 mouthOpen 微调系数
```

- 有声段：按拼音映射张开
- 无声段（振幅<阈值）：快速过渡到 SIL（闭嘴）

---

## 九、实施步骤

### Phase 1: 核心引擎

1. 添加拼音库依赖（pinyin4j）
2. 新建 `ChinesePhonemeEngine.kt`（汉字->拼音->音素分解）
3. 新建 `ChineseVisemeMapper.kt`（15 种音素口型映射）
4. 单元测试

### Phase 2: 动画层

1. 新建 `LipSyncAnimator.kt`（时间轴管理+协同发音平滑）
2. 改造 `RemoteTTSController.kt`（解析 marks，驱动音素时间轴）
3. 保留字符估算兜底

### Phase 3: 渲染层优化

1. 优化 `AvatarView` Placeholder 使用 `mouthForm`
2. 验证 Live2D 参数传递
3. 调整 mouthOpen 缩放因子和 mouthForm 偏移

### Phase 4: 联调与打磨

1. 端到端测试（"欢迎来到灵山胜境"等）
2. 性能优化（拼音缓存）
3. 调整口型参数以获得最佳视觉效果

---

## 十、关键文件清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `app/build.gradle.kts` | 修改 | 添加 pinyin4j 依赖 |
| `core/avatar/ChinesePhonemeEngine.kt` | 新建 | 拼音转换与音素分解 |
| `core/avatar/ChineseVisemeMapper.kt` | 新建 | 音素->口型参数映射 |
| `core/avatar/LipSyncAnimator.kt` | 新建 | 时间轴管理、协同发音、音频同步、按字合并、SIL闭唇 |
| `core/tts/RemoteTTSController.kt` | 修改 | 集成 marks 解析，提供音频进度查询 |
| `core/audio/AudioPlayer.kt` | 修改 | 新增 `isActuallyPlaying()`、`removeMediaItem()` |
| `domain/model/TtsModels.kt` | 修改 | `TtsMarkItem` 增加 `phonemes` 字段 |
| `ui/components/AvatarView.kt` | 修改 | Placeholder 渲染使用 mouthForm |
| `core/avatar/AvatarPlaybackManager.kt` | 修改 | 接入 LipSyncAnimator、流式分段口型同步、动作/表情时间轴、SIL闭唇 |
| `core/avatar/Live2DRendererImpl.kt` | 修改 | mouthOpen 缩放因子、mouthForm 偏移调整 |

---

## 十一、风险与对策

| 风险 | 对策 |
|------|------|
| pinyin4j 多音字不准 | 景区专用词库覆盖 |
| Live2D 模型不支持 ParamMouthForm | 回退到仅使用 ParamMouthOpenY |
| 后端 marks 未实现 | Android 端本地生成音素时间轴 |
| 性能问题 | 拼音结果缓存，动画使用 ValueAnimator |
| 连续开口音无明显变化 | 保留声母过渡阶段，增强协同发音权重 |

---

## 十二、验收标准

1. 说 "啊" 时嘴巴明显张大（mouthOpen > 0.6）
2. 说 "一" 时嘴巴变扁（mouthForm < -0.2）
3. 说 "不" 时先闭口再开口，过渡自然
4. 整句话播放时口型与音频节奏误差 < 100ms
5. 连续 10 句不同内容的口型均正确
6. 遇到逗号、句号等标点时嘴巴闭合
7. 连续开口音之间有明显起伏变化
