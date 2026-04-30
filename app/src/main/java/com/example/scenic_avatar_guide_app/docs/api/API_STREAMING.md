# 流式输入输出重构方案

版本：v1.7  
日期：2026-04-30  
状态：Android端已实现并删除降级链路

---

## 一、目标

将当前“请求一次、完整响应一次、整段 TTS 播放”的交互链路，重构为“事件流驱动 UI、TTS、数字人状态”的流式链路。

核心体验目标：

1. 后端生成文本时，移动端消息气泡立即增量展示。
2. 一旦有可朗读文本片段，TTS 也立即开始播放。
3. 若 LLM 生成文本卡顿，TTS 播完已有片段后自然停顿，口型归零，数字人保持等待状态。
4. 后续文本继续到达后，TTS 自动继续播放后续片段。
5. 用户发送新问题、退出页面或手动停止时，旧流与旧音频队列必须被取消。
6. ~~保留现有 `POST /api/v1/chat/text` 作为非流式降级路径。~~ 非流式降级链路已从 Android 端移除，统一走流式接口。

### 1.1 当前落地状态

| 能力 | 状态 | 说明 |
|------|------|------|
| `POST /api/v1/chat/text/stream` | 已接入 | FastAPI `StreamingResponse`，SSE 格式 |
| LLM token/text 流 | 已接入 | PPIO/OpenAI 兼容调用使用 `stream=True` |
| `message_start` | 已接入 | 流开始时发送 |
| `text_delta` | 已接入 | 按模型增量顺序发送 |
| `avatar_action` / `sources` / `metadata` / `done` | 已接入 | 文本生成完成后发送结构化事件 |
| `route_data` | 已接入 | `mode=route` 且解析出路线数据时发送 |
| `error` | 已接入 | 流式链路异常时发送 |
| `tts_segment` | 已接入 | `ChatService` 中按句子边界/最大长度分段，后台合成并按序推送 SSE 事件 |
| TTS 不阻塞文本流 | 已接入 | LLM delta 通过事件队列立即发送，TTS 合成在后台任务中执行 |
| TTS 文件名安全 | 已接入 | 自定义文件名仅允许安全 `.mp3` 文件名，拒绝路径穿越和绝对路径 |
| 自动化测试 | 已补充 | `tests/integration/test_chat_stream.py`、`tests/unit/test_route_data.py`、`tests/unit/test_streaming_tts.py`、`tests/unit/test_tts_service.py` |
| 真实模型流式验证 | 已完成 | 本地 Uvicorn + HTTP streaming 调用通过，首个 `text_delta` 可在最终完成前到达 |
| Android 流式 UI 增量 | 已接入 | `MainViewModel` 已接入 `text_delta` 追加与占位消息 |
| Android TTS 片段队列 | 已接入 | `StreamingTtsQueue` 串行播放 `tts_segment` |
| Android 数字人口型 | 已接入 | `marks` 驱动口型，片段间归零 |
| Android 流取消与打断 | 已接入 | 新问题发送、页面销毁时取消流与音频 |

---

## 二、当前架构基线

### 2.1 非流式降级链路（已移除）

> **Android 端已删除非流式降级链路。** `sendTextMessage()`、`chatText()`、`ChatResponseData` 以及 `RemoteTTSController.speak()` 主链路调用均已移除。当前统一通过 `POST /api/v1/chat/text/stream` 走流式链路。
>
> 旧链路示意（仅作历史参考）：
> ```text
> 用户发送问题 -> MainViewModel.sendMessageToBackend()
>   -> GuideRepository.sendTextMessage() -> ApiService.chatText()
>   -> 后端一次性返回 ChatTextResponse -> UI 添加完整机器人消息
>   -> AvatarPlaybackManager.play() -> RemoteTTSController.speak(完整 reply_text)
>   -> /api/v1/tts/synthesize 生成完整音频 -> ExoPlayer 播放完整 audio_url
> ```

### 2.2 Android 端流式阻塞点（已解决）

| 文件 | 职责 | 状态 |
|------|------|------|
| `data/remote/StreamingChatClient.kt` | OkHttp SSE 读取与事件解析 | 已接入 |
| `data/repository/GuideRepository.kt` | 封装流式问答请求，暴露 `Flow<ChatStreamEvent>` | 已接入 |
| `ui/screens/MainViewModel.kt` | 消息状态、会话状态、数字人调度 | 已接入增量消息与 TTS 队列调度 |
| `core/tts/StreamingTtsQueue.kt` | TTS 片段排队、播放、等待、取消 | 已接入 |
| `core/audio/AudioPlayer.kt` | 单段音频 URL 播放 | 已接入片段级播放 |

---

## 三、目标架构

### 3.1 总体链路

```text
用户提交问题
      │
      ▼
GuideRepository.sendTextMessageStream(): Flow<ChatStreamEvent>
      │
      ├─ MessageStart    -> 创建或绑定机器人消息
      ├─ AvatarAction    -> 基于用户问题提前设置数字人表情/动作
      ├─ TextDelta       -> UI 立即追加文字
      ├─ TtsSegment      -> TTS 音频片段入队播放
      ├─ Sources         -> 补齐来源引用
      ├─ RouteData       -> 补齐路线规划数据
      ├─ Metadata        -> 补齐意图、情绪、耗时、置信度
      ├─ Done           -> 文本流结束，等待 TTS 队列自然播完
      └─ Error          -> 停止流、停止 TTS、显示错误
```

### 3.2 推荐分层

```text
data/remote/StreamingChatClient
      │  负责 OkHttp SSE/NDJSON 读取与事件解析
      ▼
data/repository/GuideRepository
      │  负责组装请求、读取 DataStore 上下文、暴露 Flow
      ▼
ui/screens/MainViewModel
      │  负责消息增量更新、流取消、TTS 队列调度
      ▼
core/avatar/AvatarPlaybackManager
      │  负责流式数字人状态、表情、动作、口型协调
      ▼
core/tts/StreamingTtsQueue
      │  负责 TTS 片段排队、播放、等待、取消
      ▼
core/audio/AudioPlayer
      │  负责单段音频播放
```

---

## 四、后端流式接口设计

### 4.1 新增接口

```http
POST /api/v1/chat/text/stream
Accept: text/event-stream
Content-Type: application/json
```

请求体与现有 `POST /api/v1/chat/text` 保持一致：

```json
{
  "session_id": "s_xxx",
  "user_id": "u_001",
  "scenic_id": "lingshan",
  "question": "半天时间怎么游览？",
  "spot_id": "spot_001",
  "mode": "route",
  "image_url": null,
  "options": {
    "need_avatar": true,
    "need_sources": true,
    "voice": "zh-CN-XiaoxiaoNeural",
    "rate": "+0%",
    "volume": "+0%",
    "pitch": "+0Hz"
  }
}
```

`options.voice/rate/volume/pitch` 是流式 TTS 的可选参数。当前后端仅允许 `voice` 为 `zh-CN-XiaoxiaoNeural` 或 `zh-CN-XiaoyiNeural`；未传或传入其它音色时统一使用默认 `zh-CN-XiaoxiaoNeural`。`rate`、`volume`、`pitch` 与 `/api/v1/tts/synthesize` 保持一致，默认分别是 `+0%`、`+0%`、`+0Hz`。

Android 端 `ChatOptions` 模型已同步包含 `rate`、`volume`、`pitch` 字段，并在发起流式请求时透传。

### 4.2 传输格式

当前后端已采用 SSE：

```text
event: text_delta
data: {"type":"text_delta","delta":"欢迎来到灵山胜境，"}
```

`tts_segment` 当前已由后端发送，格式如下：

```text
event: tts_segment
data: {"type":"tts_segment","segment_id":"seg_001","text":"欢迎来到灵山胜境，","audio_url":"/api/v1/tts/file/seg_001.mp3","duration_ms":1800,"voice":"zh-CN-XiaoxiaoNeural","rate":"+0%","volume":"+0%","pitch":"+0Hz","emotion":"welcoming","marks":[]}
```

NDJSON 不再作为当前后端实现目标，只作为后续兼容备选：

```jsonl
{"type":"text_delta","delta":"欢迎来到灵山胜境，"}
{"type":"tts_segment","segment_id":"seg_001","text":"欢迎来到灵山胜境，","audio_url":"/api/v1/tts/file/seg_001.mp3","duration_ms":1800,"voice":"zh-CN-XiaoxiaoNeural","rate":"+0%","volume":"+0%","pitch":"+0Hz","emotion":"welcoming","marks":[]}
```

Android 端应优先实现 SSE 解析，同时可将解析器设计为按行读取，便于兼容 NDJSON。

---

## 五、流式事件协议

### 5.1 事件总览

| type | 发送时机 | 是否必需 | 移动端动作 |
|------|----------|----------|------------|
| `message_start` | 流开始 | 当前已实现 | 绑定 `message_id`、`session_id` |
| `text_delta` | 每次文本增量 | 是 | 追加到机器人消息气泡 |
| `tts_segment` | 每个可朗读片段完成 TTS 合成 | 已实现 | 入队播放音频片段 |
| `avatar_action` | 流开始后立即发送（基于用户问题推断） | 当前已实现 | 更新数字人表情、动作 |
| `sources` | 检索来源完成后 | 当前已实现，未接 RAG 时为空数组 | 补齐消息来源 |
| `route_data` | 路线规划结构化数据完成后 | 当前已实现，`mode=route` 且有数据时发送 | 补齐路线卡片数据 |
| `metadata` | 意图、情绪、耗时等完成后 | 当前已实现 | 补齐统计与降级字段 |
| `done` | 文本与结构化数据发送完成 | 是 | 关闭 loading，等待 TTS 队列播放完成 |
| `error` | 流式链路异常 | 当前已实现 | 停止流与 TTS，显示错误 |

### 5.2 `message_start`

```json
{
  "type": "message_start",
  "message_id": "m_xxx",
  "session_id": "s_xxx",
  "created_at": "2026-04-29T12:00:00Z"
}
```

### 5.3 `text_delta`

```json
{
  "type": "text_delta",
  "delta": "欢迎来到灵山胜境，"
}
```

约束：

- `delta` 必须按最终展示顺序发送。
- `delta` 不应重复发送已发送内容。
- 移动端只做字符串追加，不做复杂 diff。

### 5.4 `tts_segment`

> 后端已发送该事件。`ChatService.stream_chat_events()` 在接收 `text_delta` 的同时按情绪边界优先、句子边界次之的策略缓冲文本，切分后调用 `TTSService.synthesize()` 生成音频，随后通过 SSE 推送 `tts_segment`。

```json
{
  "type": "tts_segment",
  "segment_id": "seg_001",
  "text": "欢迎来到灵山胜境，",
  "audio_url": "/api/v1/tts/file/seg_001.mp3",
  "duration_ms": 1800,
  "voice": "zh-CN-XiaoxiaoNeural",
  "rate": "+0%",
  "volume": "+0%",
  "pitch": "+0Hz",
  "emotion": "welcoming",
  "marks": [
    { "text": "欢", "start_ms": 0, "end_ms": 210, "phonemes": ["h", "u", "an"] },
    { "text": "迎", "start_ms": 210, "end_ms": 420, "phonemes": ["i", "ng"] },
    { "text": "，", "start_ms": 420, "end_ms": 560, "phonemes": ["SIL"] }
  ]
}
```

字段说明：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `segment_id` | String | 是 | 分段音频 ID，单次回答内唯一 |
| `text` | String | 是 | 该音频片段对应文本 |
| `audio_url` | String | 是 | 音频相对路径或完整 URL |
| `duration_ms` | Int / null | 否 | 保存后的 MP3 真实音频时长 |
| `voice` | String | 否 | 实际使用发音人；只会是晓晓或晓伊，非法请求值会回落为晓晓 |
| `rate` | String | 否 | 实际使用语速 |
| `volume` | String | 否 | 实际使用音量 |
| `pitch` | String | 否 | 实际使用音调 |
| `emotion` | String | 否 | LLM 标注的情绪或后验推断的情绪，如 `welcoming`、`excited`、`thinking` 等。Android 端在播放该片段时，会依据 `emotion` 动态更新数字人表情 |
| `marks` | Array | 否 | 该片段内 WordBoundary 对齐的字级音素时间戳 |

约束：

- `tts_segment.text` 必须是已通过 `text_delta` 展示过的文本子串。
- `marks.start_ms` 与 `marks.end_ms` 均为片段内相对时间，不是整段回答的全局时间。
- `marks[-1].end_ms` 与 `duration_ms` 对齐；标点、词间停顿和尾部补齐以 `phonemes: ["SIL"]` 表示。
- 移动端应按播放器当前播放进度直接匹配 marks，不要按字符数量或 `duration_ms` 重新拉伸 marks（时间缩放逻辑已移除）。
- 移动端播放下一段前，应重置或重新启动该片段的口型时间轴。

### 5.5 `avatar_action`

```json
{
  "type": "avatar_action",
  "data": {
    "expression": {
      "type": "happy",
      "intensity": 0.8
    },
    "gesture": {
      "type": "guide"
    },
    "motion_queue": [
      { "type": "wave", "start_offset_ms": 0, "duration_ms": 1200 }
    ]
  }
}
```

说明：

- **当前后端在 `message_start` 之后立即发送**，基于用户问题推断预期意图，让数字人在 LLM 生成第一个字之前就进入合适表情和动作。
- 表情/动作/运动队列的内容与最终 `done` 前发送的 `avatar_action` 一致（均基于同一套意图映射）。
- Android 端已结合 `tts_segment.emotion` 在播放每个片段时动态更新数字人表情，实现更细粒度的实时表情切换。
- Gesture 层不得修改面部参数和 `ParamMouthOpenY` / `ParamMouthForm`。

### 5.6 `sources`

```json
{
  "type": "sources",
  "data": [
    {
      "document_id": "doc_lingshan_faq",
      "chunk_id": "ck_001",
      "title": "灵山胜境导览手册",
      "source_path": "data/raw/scenic_docs/lingshan.md",
      "score": 0.92,
      "snippet": "灵山大佛是景区核心地标..."
    }
  ]
}
```

### 5.7 `route_data`

```json
{
  "type": "route_data",
  "data": {
    "title": "灵山胜境半日精华游",
    "total_duration_min": 240,
    "total_distance_m": 3200,
    "spots": [
      {
        "name": "九龙灌浴",
        "lat": 31.4875,
        "lng": 120.1234,
        "order": 1,
        "stay_min": 60,
        "description": "整点有水景表演"
      }
    ],
    "polyline": [
      { "lat": 31.4875, "lng": 120.1234 }
    ]
  }
}
```

### 5.8 `metadata`

```json
{
  "type": "metadata",
  "data": {
    "intent": "route_recommendation",
    "emotion": "joy",
    "confidence": 0.95,
    "is_fallback": false,
    "latency_ms": 1200
  }
}
```

### 5.9 `done`

```json
{
  "type": "done",
  "message_id": "m_xxx",
  "session_id": "s_xxx"
}
```

说明：

- `done` 表示后端文本、结构化数据、TTS 片段都已发送完毕。
- `done` 不代表移动端音频播放已完成。
- 移动端收到 `done` 后应调用 TTS 队列 `finishInput()`，队列会在最后一个音频片段播放完后恢复 IDLE。

### 5.10 `error`

```json
{
  "type": "error",
  "code": 2002,
  "message": "LLM 服务不可用"
}
```

说明：

- 若已发送部分文本，移动端可保留已展示内容，并在消息上标记异常。
- 若错误发生在首个 `text_delta` 前，移动端显示统一错误文案。

---

## 六、后端 TTS 分段策略

### 6.1 分段原则

不要按 token 或单字合成 TTS。当前后端采用**情绪边界优先、句子边界次之**的双层分段策略：

1. **情绪边界（最高优先级）**：当 LLM 输出 `<emotion="xxx">...</emotion>` 标签时，情绪变化处必须截断，不允许跨情绪的 TTS 段。
2. **句子边界**：同一情绪内部，遇到 `，。！？；：` 等中文标点时切分。
3. **长度边界**：当前缓冲超过 12-25 个中文字符时允许切分；超过最大长度时强制切分。
4. **首响优化**：首段超过 800ms 仍未遇到标点时，强制切一个短片段，提升首响。
5. **语义保护**：英文、数字、景点名、专有名词尽量不要从中间切断。
6. **内容清洗**：markdown、表格、链接等内容需在 TTS 合成前清洗为纯朗读文本；`text_delta` 事件已自动剥离情绪标签，移动端不会收到 XML 标签。

### 6.2 推荐后端处理流程

```text
LLM token/text stream (含 <emotion="xxx"> 标签)
      │
      ├─ 解析纯文本，立即发送 text_delta（不含标签）
      │
      ▼
情绪感知分段器 (EmotionAwareSegmenter)
      │
      ├─ 情绪边界截断
      ├─ 句子边界切分
      ▼
TTS 合成短句（携带情绪对应韵律参数）
      │
      ├─ 生成 audio_url
      ├─ 生成 duration_ms
      └─ 生成 marks
      ▼
发送 tts_segment（含 emotion 字段）
```

当前后端实现中，`text_delta` 和 `tts_segment` 已解耦：原始 delta 会先解析并剥离情绪标签后发送给移动端，TTS 合成在后台任务中执行，完成后再按 segment 序号推送 `tts_segment`，避免语音合成阻塞后续文字展示。

### 6.3 TTS 参数选择优先级

每个 `tts_segment` 的 `rate` / `volume` / `pitch` 按以下优先级确定：

1. **用户自定义参数最高优先级**：若请求 `options` 中传入了 `rate` / `volume` / `pitch`，直接使用该值。
2. **LLM 标注的情绪映射**：若 LLM 输出了 `<emotion="xxx">` 标签，使用该情绪对应的 `_EMOTION_TO_TTS_PROSODY` 参数。
3. **后验推断回退**：若文本无情绪标签（plain text），调用 `_detect_intent` 基于关键词匹配推断情绪，再映射为 TTS 参数。
4. **neutral 默认**：以上均未命中时，使用 `neutral` 的默认参数（`+0%` / `+0%` / `+0Hz`）。

可用情绪与对应参数：

| 情绪 | rate | volume | pitch |
|------|------|--------|-------|
| welcoming | +10% | +5% | +20Hz |
| happy | +5% | +0% | +10Hz |
| excited | +15% | +10% | +30Hz |
| thinking | -5% | +0% | -10Hz |
| apologetic | -10% | -5% | -20Hz |
| concerned | -5% | -5% | -15Hz |
| surprised | +10% | +10% | +25Hz |
| grateful | +0% | +5% | +10Hz |
| reverent | -10% | -10% | -20Hz |
| playful | +10% | +5% | +15Hz |
| approving | +0% | +0% | +5Hz |
| focused | +0% | +0% | +0Hz |
| neutral | +0% | +0% | +0Hz |

### 6.3 文本一致性要求

后端需维护两份文本：

- `display_text`：用于 `text_delta`，尽量保留适合展示的格式。
- `speech_text`：用于 `tts_segment.text`，清洗 markdown、HTML、URL 等不适合朗读的内容。

若两者不完全一致，`tts_segment.text` 应是用户可理解的朗读文本，不要求 UI 再次展示。

---

## 七、Android 端数据模型

### 7.1 新增 `ChatStreamEvent`

建议新增文件：

`app/src/main/java/com/example/scenic_avatar_guide_app/domain/model/ChatStreamModels.kt`

```kotlin
sealed interface ChatStreamEvent {
    data class MessageStart(
        val messageId: String?,
        val sessionId: String?,
        val createdAt: String? = null
    ) : ChatStreamEvent

    data class TextDelta(
        val delta: String
    ) : ChatStreamEvent

    data class TtsSegment(
        val segment: TtsSegmentData
    ) : ChatStreamEvent

    data class AvatarActionDelta(
        val action: AvatarAction
    ) : ChatStreamEvent

    data class SourcesDelta(
        val sources: List<SourceInfo>
    ) : ChatStreamEvent

    data class RouteDataDelta(
        val routeData: RouteData
    ) : ChatStreamEvent

    data class MetadataDelta(
        val metadata: ResponseMetadata
    ) : ChatStreamEvent

    data object Done : ChatStreamEvent

    data class Error(
        val code: Int? = null,
        val message: String
    ) : ChatStreamEvent
}
```

### 7.2 新增 `TtsSegmentData`

```kotlin
@Serializable
data class TtsSegmentData(
    @SerialName("segment_id")
    val segmentId: String,
    val text: String,
    @SerialName("audio_url")
    val audioUrl: String,
    @SerialName("duration_ms")
    val durationMs: Int? = null,
    val voice: String? = null,
    val rate: String? = null,
    val volume: String? = null,
    val pitch: String? = null,
    val emotion: String? = null,
    val marks: List<TtsMarkItem>? = null
)
```

### 7.3 流式解析 DTO

可为网络解析新增可序列化 DTO，再转换为领域事件：

```kotlin
@Serializable
data class ChatStreamEnvelope(
    val type: String,
    @SerialName("message_id")
    val messageId: String? = null,
    @SerialName("session_id")
    val sessionId: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    val delta: String? = null,
    val data: JsonElement? = null,
    @SerialName("segment_id")
    val segmentId: String? = null,
    val text: String? = null,
    @SerialName("audio_url")
    val audioUrl: String? = null,
    @SerialName("duration_ms")
    val durationMs: Int? = null,
    val voice: String? = null,
    val rate: String? = null,
    val volume: String? = null,
    val pitch: String? = null,
    val emotion: String? = null,
    val marks: List<TtsMarkItem>? = null,
    val code: Int? = null,
    val message: String? = null
)
```

---

## 八、Android 网络层改造

### 8.1 新增 `StreamingChatClient`

建议新增文件：

`app/src/main/java/com/example/scenic_avatar_guide_app/data/remote/StreamingChatClient.kt`

职责：

1. 构造 `POST /api/v1/chat/text/stream` 请求。
2. 复用动态 base URL 逻辑。
3. 使用 OkHttp 直接读取 `ResponseBody`。
4. 逐行解析 SSE 或 NDJSON。
5. 将 JSON 事件转换为 `ChatStreamEvent`。
6. 协程取消时取消 OkHttp `Call`。

伪代码：

```kotlin
class StreamingChatClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    fun streamChat(request: ChatTextRequest): Flow<ChatStreamEvent> = callbackFlow {
        val body = json.encodeToString(ChatTextRequest.serializer(), request)
            .toRequestBody("application/json".toMediaType())

        val httpRequest = Request.Builder()
            .url("http://placeholder/api/v1/chat/text/stream")
            .post(body)
            .header("Accept", "text/event-stream")
            .build()

        val call = okHttpClient.newCall(httpRequest)

        launch(Dispatchers.IO) {
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        trySend(ChatStreamEvent.Error(response.code, response.message))
                        close()
                        return@use
                    }

                    val source = response.body?.source()
                    while (source != null && !source.exhausted()) {
                        val line = source.readUtf8Line() ?: continue
                        parseLine(line)?.let { trySend(it) }
                    }
                }
            } catch (e: Exception) {
                trySend(ChatStreamEvent.Error(message = e.message ?: "流式请求失败"))
            } finally {
                close()
            }
        }

        awaitClose { call.cancel() }
    }
}
```

> 当前 `StreamingChatClient` 已实现并接入主链路。`ChatTextRequest` 透传 `options.rate`、`options.volume`、`options.pitch`。

### 8.2 OkHttp 配置

流式请求不应使用普通 30 秒读取超时。建议提供单独 client：

```kotlin
@Provides
@Singleton
@StreamingOkHttp
fun provideStreamingOkHttpClient(settingsDataStore: SettingsDataStore): OkHttpClient {
    return OkHttpClient.Builder()
        .addInterceptor(DynamicBaseUrlInterceptor(settingsDataStore))
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
}
```

注意：

- 流式接口不要启用 `HttpLoggingInterceptor.Level.BODY`，否则可能缓冲或刷屏。
- 当前 `DynamicBaseUrlInterceptor` 是 `NetworkModule` 私有类，若流式 client 需要复用，应提升为独立类或公开 provider 内复用。

---

## 九、Repository 改造

旧方法 `sendTextMessage()` 与 `ChatResponseData` 已从 Android 端移除。

当前仅保留流式方法：

```kotlin
fun sendTextMessageStream(
    sessionId: String,
    message: String,
    mode: String = "chat",
    imageUrl: String? = null
): Flow<ChatStreamEvent>
```

职责：

1. 从 `SettingsDataStore` 读取 `userId`、`scenicId`、`spotId`。
2. 构造 `ChatTextRequest`（包含 `rate`、`volume`、`pitch`）。
3. 调用 `StreamingChatClient.streamChat(request)`。
4. 将上游异常转换为 `ChatStreamEvent.Error`。

---

## 十、ViewModel 改造

### 10.1 状态与 Job

`MainViewModel` 新增：

```kotlin
private var currentStreamJob: Job? = null
private var currentAssistantMessageId: String? = null
private var accumulatedAssistantText: String = ""
```

### 10.2 新增消息更新工具方法

```kotlin
private fun addAssistantPlaceholder(): String

private fun appendAssistantDelta(
    id: String,
    delta: String
)

private fun updateAssistantMessage(
    id: String,
    sources: List<SourceInfo>? = null,
    avatarAction: AvatarAction? = null,
    routeData: RouteData? = null,
    isError: Boolean? = null
)

private fun cancelCurrentStream()
```

### ### 10.3 流式发送流程

```text
sendMessage()
      │
      ├─ 添加用户消息
      ├─ 清空输入框
      └─ sendMessageToBackendStream()
              │
              ├─ cancelCurrentStream()
              ├─ 上传图片（如有）
              ├─ 确保 session_id
              ├─ 创建机器人占位消息
              ├─ playbackManager.startStreaming()
              └─ collect repository.sendTextMessageStream()
                      ├─ TextDelta       -> appendAssistantDelta()
                      ├─ TtsSegment      -> playbackManager.enqueueSpeechSegment()
                      ├─ AvatarAction    -> playbackManager.updateStreamingAction()
                      ├─ Sources         -> updateAssistantMessage()
                      ├─ RouteData       -> updateAssistantMessage()
                      ├─ Metadata        -> 缓存或更新降级字段
                      ├─ Done            -> playbackManager.finishStreamingInput()
                      └─ Error           -> 标记错误并停止播放
```

> 非流式降级链路已从 Android 端移除。`sendMessageToBackend()` 旧入口已废弃，统一走上述流式流程。

### 10.4 Loading 行为

建议拆分两个状态：

| 状态 | 含义 |
|------|------|
| `isLoading` | 后端流还未 `done`，发送按钮不可用 |
| `isSpeaking` | TTS 队列仍在播放，数字人仍处于说话/等待续播 |

第一阶段可继续复用现有 `_isLoading`：

- 流开始：`true`
- 收到 `done` 或 `error`：`false`
- TTS 是否播放由 `AvatarPlaybackManager.avatarState` 表示

---

## 十一、TTS 队列改造

### 11.1 新增 `StreamingTtsQueue`

建议新增文件：

`app/src/main/java/com/example/scenic_avatar_guide_app/core/tts/StreamingTtsQueue.kt`

职责：

1. 接收后端 `tts_segment`。
2. 按 `segment_id` 顺序串行播放。
3. 当前段播放中时，后续段排队。
4. 队列空但输入未结束时进入等待状态。
5. 队列空且输入已结束时回调完成。
6. 支持取消。

建议接口：

```kotlin
class StreamingTtsQueue(
    private val audioPlayer: AudioPlayer,
    private val buildAudioUrl: suspend (String) -> String,
    private val onSegmentStart: (TtsSegmentData) -> Unit,
    private val onSegmentComplete: (TtsSegmentData) -> Unit,
    private val onWaitingForSegment: () -> Unit,
    private val onAllComplete: () -> Unit,
    private val onError: (Throwable) -> Unit
) {
    fun start()
    fun enqueue(segment: TtsSegmentData)
    fun finishInput()
    fun cancel()
}
```

### 11.2 播放规则

| 场景 | 行为 |
|------|------|
| 队列为空，收到新片段 | 立即播放 |
| 当前片段播放中，收到新片段 | 入队 |
| 当前片段播完，队列有下一段 | 立即播放下一段 |
| 当前片段播完，队列为空，流未结束 | 进入等待状态，口型归零 |
| 当前片段播完，队列为空，流已结束 | 回调全部完成，数字人恢复 IDLE |
| 用户发送新问题 | 取消当前音频、清空队列 |
| 音频播放失败 | 可跳过该段继续下一段，或降级系统 TTS；第一阶段建议报错并停止 |

### 11.3 与现有 `RemoteTTSController` 的关系

`RemoteTTSController.speak(text)` 已从主链路移除，当前仅作为测试/调试入口保留。

当前状态：

- 流式链路：统一使用 `StreamingTtsQueue`，直接播放后端已生成的 `audio_url`。
- 非流式：`RemoteTTSController` 不再被 `MainViewModel` 调用，仅用于独立 TTS 测试场景。
- `AudioPlayer.playPcmData()` 已作为冗余方法移除。

---

## 十二、数字人状态机

### 12.1 状态映射

| 流式阶段 | AvatarState | 表情 | 动作 | 口型 |
|----------|-------------|------|------|------|
| 请求已发送，未收到文本 | `THINKING` | `thinking` 或当前默认 | `thinking_pose` 可选 | 归零 |
| 收到 `avatar_action` | `THINKING` 或 `SPEAKING` | 按后端 expression | 按后端 gesture | 不变 |
| TTS 片段播放中 | `SPEAKING` | 保持当前 expression | 保持当前 gesture / motion queue | marks 驱动 |
| 队列空，流未结束 | `THINKING` | 保持当前 expression | 可回到 `IDLE` 或轻微等待动作 | 归零 |
| 流结束，队列播放完 | `IDLE` | 可保留最终表情短暂过渡 | `IDLE` | 归零 |
| 流或播放失败 | `ERROR` | `concerned` / `apologetic` | `IDLE` | 归零 |

### 12.2 Live2D 参数分层约束

流式改造必须继续遵守现有三层参数分工：

- Expression 层可控制 `ParamMouthForm`，不可控制 `ParamMouthOpenY`。
- Gesture 层不可修改面部参数，不可修改 `ParamMouthOpenY` / `ParamMouthForm`。
- LipSync 层独占 `ParamMouthOpenY`。
- TTS 片段之间的等待状态必须将 `ParamMouthOpenY` 归零。

---

## 十三、异常、取消与降级

### 13.1 取消场景

以下场景必须取消当前流：

1. 用户发送新问题。
2. 用户点击停止播放。
3. 用户退出当前页面或 `ViewModel.onCleared()`。
4. 会话切换、景区/景点切换。
5. 网络错误或后端发送 `error`。

取消动作：

```text
currentStreamJob.cancel()
OkHttp Call.cancel()
StreamingTtsQueue.cancel()
AvatarPlaybackManager.stop()
_isLoading = false
```

### 13.2 降级路径

Android 端已移除非流式降级链路，当前统一走流式接口。异常处理策略如下：

| 场景 | 处理策略 |
|------|----------|
| 首个事件前连接失败 | 提示网络错误，保留用户输入，支持重试 |
| 已收到部分文本后失败 | 保留部分文本，标记“回答中断” |
| TTS 片段播放失败 | 停止 TTS，保留文字流 |
| `tts_segment` 长时间不到 | UI 继续展示文字，数字人等待 |

---

## 十四、实施顺序

### 阶段 1：协议与模型 [已完成]

1. ~~新增 `ChatStreamModels.kt`。~~ 已完成
2. ~~定义 `ChatStreamEvent`、`TtsSegmentData`、解析 DTO。~~ 已完成
3. ~~更新 API 文档，确认事件字段与后端实现一致。~~ 已完成

### 阶段 2：网络流读取 [已完成]

1. ~~新增 `StreamingChatClient`。~~ 已完成
2. ~~新增流式 OkHttpClient，禁用 BODY 日志，调整 `readTimeout`。~~ 已完成
3. ~~实现 SSE/NDJSON 按行解析。~~ 已完成
4. ~~用本地 mock 流验证解析。~~ 已完成

### 阶段 3：Repository 接入 [已完成]

1. ~~`GuideRepository` 新增 `sendTextMessageStream()`。~~ 已完成
2. ~~保留旧 `sendTextMessage()`。~~ 已移除，统一走流式
3. ~~增加异常到 `ChatStreamEvent.Error` 的转换。~~ 已完成

### 阶段 4：UI 增量消息 [已完成]

1. ~~`MainViewModel` 新增机器人占位消息。~~ 已完成
2. ~~实现 `text_delta` 追加。~~ 已完成
3. ~~实现 `sources`、`route_data`、`avatar_action` 回填。~~ 已完成
4. ~~保证 LazyColumn 能随增量文本滚动到底部。~~ 已完成

### 阶段 5：TTS 片段队列 [已完成]

1. ~~新增 `StreamingTtsQueue`。~~ 已完成
2. ~~先实现串行播放 `audio_url`。~~ 已完成
3. ~~队列空但流未结束时进入等待状态。~~ 已完成
4. ~~流结束且队列空时恢复 IDLE。~~ 已完成

### 阶段 6：口型与数字人 [已完成]

1. ~~每段 `tts_segment.marks` 转为片段内 `PhonemeEvent`。~~ 已完成
2. ~~每段音频开始时启动对应口型动画。~~ 已完成
3. ~~每段音频结束时停止口型并归零。~~ 已完成
4. ~~接入流式表情与动作更新。~~ 已完成

### 阶段 7：取消、降级、验收 [已完成]

1. ~~实现新问题打断旧流。~~ 已完成
2. ~~实现页面销毁取消。~~ 已完成
3. ~~实现首事件失败回退旧接口。~~ 已移除降级链路，首事件前失败直接提示重试
4. ~~完成真机联调与弱网测试。~~ 已完成

---

## 十五、验收标准

### 15.1 基础流式展示

- 用户发送消息后，机器人消息气泡先出现占位。
- 收到首个 `text_delta` 后，文字立即显示。
- 后续 `text_delta` 按顺序追加，不重复、不乱序。
- 收到 `done` 后，`isLoading` 关闭。

### 15.2 流式 TTS

- 收到首个 `tts_segment` 后，TTS 立即播放。
- 当前片段播放时，后续片段可以继续入队。
- 文本卡顿且队列为空时，TTS 自然停顿，口型归零。
- 后续新片段到达后，TTS 自动继续播放。
- 所有片段播放完成后，数字人恢复 IDLE。

### 15.3 数字人表现

- `avatar_action` 到达后，表情和动作能提前生效。
- 口型只在音频播放期间打开。
- 等待后续文本时嘴巴关闭。
- 不违反 Live2D 参数分层约束。

### 15.4 取消与异常

- 用户发送新问题时，旧回答文本流停止。
- 用户发送新问题时，旧 TTS 音频立即停止。
- 网络中断时，消息能明确展示失败或中断状态。
- `ViewModel.onCleared()` 后无后台音频继续播放。

---

## 十六、待确认问题

| # | 问题 | 建议 |
|---|------|------|
| 1 | 流式传输格式用 SSE 还是 NDJSON | **已确认：后端使用 SSE**，NDJSON 仅作为兼容备选 |
| 2 | `tts_segment` 由后端生成还是移动端拿 delta 后再请求 TTS | **已采用后端生成**；Android 端已移除整段 TTS 降级链路 |
| 3 | 首段最短长度 | 建议 8-12 个中文字符或 800ms 超时强制切分 |
| 4 | `tts_segment.text` 与展示文本不一致时如何处理 | 允许不完全一致，但必须语义一致 |
| 5 | TTS 播放失败是否跳过该段 | 第一阶段停止并提示；后续可跳过坏段继续 |
| 6 | 是否保留非流式开关 | **已移除**：Android 端不再保留 `POST /api/v1/chat/text` 降级路径，统一走流式 |

---

## 十七、与现有接口文档的关系

- `Android_API_CONTRACT.md` v6.1 已正式纳入 `POST /api/v1/chat/text/stream`。
- 本文档继续描述流式链路的分阶段实现与 Android 端改造建议。
- 当前稳定范围是”文本 SSE + 结构化事件 + 后端分段 TTS 事件 + Android 端完整播放队列”。
- `API_TTS_USAGE.md` 中的独立 TTS 接口仍保留，仅用于测试场景，不再作为主链路降级能力。
