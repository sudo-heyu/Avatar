# 流式输入输出重构方案

版本：v3.1
日期：2026-05-08  
状态：**Android 端已完成全部接入**：`StreamingChatClient` SSE 解析、`StreamingTtsQueue` 分段播放、`TypewriterController` 打字机效果、自动续写、`AuthDialog` 认证、`FeedbackDialog` 满意度反馈。流式 TTS 主事件为 `tts_segment_ready`；`tts_segment` 仅作预告不播放；`tts_audio_error` 用于跳过合成失败片段；`tts_audio_chunk`/`tts_audio_end` 后端保留发送但 Android 端已忽略

---

## 一、目标

将当前“请求一次、完整响应一次、整段 TTS 播放”的交互链路，重构为“事件流驱动 UI、TTS、数字人状态”的流式链路。

核心体验目标：

1. 后端生成文本时，移动端消息气泡立即增量展示。
2. 一旦收到 `tts_segment_ready`，TTS 分段立即按序播放。
3. 若 LLM 生成文本卡顿，TTS 播完已有片段后自然停顿，数字人保持等待状态。
4. 后续文本继续到达后，TTS 自动继续播放后续片段。
5. 用户发送新问题、退出页面或手动停止时，旧流与旧音频队列必须被取消。
6. 保留现有 `POST /api/v1/chat/text` 作为非流式降级路径。

### 1.1 当前落地状态

| 能力 | 状态 | 说明 |
|------|------|------|
| `POST /api/v1/chat/text/stream` | 已实现 | FastAPI `StreamingResponse`，SSE 格式 |
| LLM token/text 流 | 已实现 | DeepSeek 官方 OpenAI 兼容接口使用 `stream=True`，默认 `deepseek-v4-flash` |
| `message_start` | 已实现 | 流开始时发送 |
| `text_delta` | 已实现 | 按模型增量顺序发送 |
| `avatar_action` / `sources` / `metadata` / `done` | 已实现 | 文本生成完成后发送结构化事件 |
| `route_data` | 已实现 | `mode=route` 且解析出路线数据时发送 |
| `error` / `aborted` | 已实现 | 流式链路异常或用户取消时发送 |
| `tts_segment` | 已实现 | `ChatService` 中按情绪边界/句子边界分段，立即推送段落元信息 |
| `tts_segment_ready` | 已实现 | **当前主事件**：音频文件已生成，返回完整 `audio_url`、`duration_ms`、`marks` |
| `tts_audio_error` | 已实现 | 某个 TTS 片段合成失败或被取消；Android 端跳过该 segment，继续后续片段 |
| `tts_audio_chunk` / `tts_audio_end` | 后端保留 | Android 端 `StreamingChatClient` 已忽略（返回 `null`），不再作为播放主链路 |
| `PrematurelyEnded` | Android 端生成 | EOF 但未收到终止事件时触发，配合自动续写机制（最多 3 次） |
| `chat/abort` | 已实现 | 客户端可按 `message_id` 或 `session_id` 取消正在进行的 SSE 流 |
| TTS 不阻塞文本流 | 已实现 | LLM delta 通过事件队列立即发送，TTS 合成在后台任务中执行 |
| Android `StreamingChatClient` | 已实现 | OkHttp SSE 解析，动态 base URL，协程取消联动 |
| Android `StreamingTtsQueue` | 已实现 | 分段播放、`PlaybackState` 状态机、`sessionEpoch` 防串扰、20 容量有界缓冲 |
| Android `TypewriterController` | 已实现 | TTS 同步打字机效果，超时 1.5s 兜底，自动续写时无缝衔接 |
| Android 用户认证 | 已实现 | `AuthDialog` + `AuthRepository`，注册/登录/登出，guest ID 兜底 |
| Android 满意度反馈 | 已实现 | `FeedbackDialog` + `POST /api/v1/chat/feedback`，支持评分/投诉/留言 |
| Android 会话管理 | 已实现 | 侧边栏展示会话列表，支持恢复、归档、删除 |
| 自动化测试 | 已补充 | `tests/integration/test_chat_stream.py`、`tests/unit/test_route_data.py`、`tests/unit/test_streaming_tts.py`、`tests/unit/test_tts_service.py` |

---

## 二、当前架构基线

### 2.1 非流式降级链路

```text
用户发送问题
      │
      ▼
MainViewModel.sendMessageToBackend()
      │
      ▼
GuideRepository.sendTextMessage()
      │
      ▼
ApiService.chatText()
      │
      ▼
后端一次性返回 ChatTextResponse
      │
      ▼
UI 添加完整机器人消息
      │
      ▼
AvatarPlaybackManager.play()
      │
      ▼
RemoteTTSController.speak(完整 reply_text)
      │
      ▼
/api/v1/tts/synthesize 生成完整音频
      │
      ▼
ExoPlayer/MSE 播放实时 MP3 chunk，失败时回退完整 audio_url
```

### 2.2 Android 端当前阻塞点

| 文件 | 当前职责 | 流式阻塞点 |
|------|----------|------------|
| `data/remote/ApiService.kt` | Retrofit JSON API 定义 | `chatText()` 只能返回完整 `ChatTextResponse` |
| `data/repository/GuideRepository.kt` | 封装问答、上传、TTS 请求 | `sendTextMessage()` 只能返回完整 `ChatResponseData` |
| `ui/screens/MainViewModel.kt` | 消息状态、会话状态、数字人调度 | 只有完整响应后才添加机器人消息与启动 TTS |
| `core/tts/RemoteTTSController.kt` | 后端 TTS 合成与播放 | `speak(text)` 以完整文本为输入 |
| `core/audio/AudioPlayer.kt` | 单个音频 URL 播放 | 缺少音频片段队列与等待续播状态 |

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
      ├─ TtsSegment      -> 创建 TTS 片段元信息（预告）
      ├─ TtsSegmentReady -> 音频已生成，播放 audio_url
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

`options.voice/rate/volume/pitch` 是流式 TTS 的可选参数。当前后端仅允许 `voice` 为 `zh-CN-XiaoxiaoNeural` 或 `zh-CN-XiaoyiNeural`；未传或传入其它音色时统一使用后端默认流式音色（当前为 `zh-CN-XiaoyiNeural`）。`rate`、`volume`、`pitch` 与 `/api/v1/tts/synthesize` 保持一致，默认分别是 `+0%`、`+0%`、`+0Hz`。

### 4.2 传输格式

当前后端已采用 SSE：

```text
event: text_delta
data: {"type":"text_delta","delta":"欢迎来到灵山胜境，"}
```

`tts_segment` 当前已由后端发送，格式如下。它是片段元信息事件，不代表最终 MP3 文件已写盘完成：

```text
event: tts_segment
data: {"type":"tts_segment","segment_id":"seg_001","segment_index":0,"text":"欢迎来到灵山胜境，","audio_url":"/api/v1/tts/file/seg_001.mp3","duration_ms":null,"voice":"zh-CN-XiaoxiaoNeural","rate":"+0%","volume":"+0%","pitch":"+0Hz","emotion":"neutral","marks":[]}
```

音频就绪事件：

```text
event: tts_segment_ready
data: {"type":"tts_segment_ready","segment_id":"seg_001","segment_index":0,"audio_url":"/api/v1/tts/file/seg_001.mp3","file_name":"seg_001.mp3","duration_ms":1800,"marks":[{"text":"欢","start_ms":0,"end_ms":210,"phonemes":["h","u","an"]},{"text":"迎","start_ms":210,"end_ms":420,"phonemes":["i","ng"]}],"emotion":"neutral"}
```

NDJSON 不再作为当前后端实现目标，只作为后续兼容备选：

```jsonl
{"type":"text_delta","delta":"欢迎来到灵山胜境，"}
{"type":"tts_segment","segment_id":"seg_001","segment_index":0,"text":"欢迎来到灵山胜境，","audio_url":"/api/v1/tts/file/seg_001.mp3","duration_ms":null,"voice":"zh-CN-XiaoxiaoNeural","rate":"+0%","volume":"+0%","pitch":"+0Hz","emotion":"neutral","marks":[]}
{"type":"tts_segment_ready","segment_id":"seg_001","segment_index":0,"audio_url":"/api/v1/tts/file/seg_001.mp3","file_name":"seg_001.mp3","duration_ms":1800,"marks":[],"emotion":"neutral"}
```

Android 端应优先实现 SSE 解析，同时可将解析器设计为按行读取，便于兼容 NDJSON。

---

## 五、流式事件协议

### 5.1 事件总览

| type | 发送时机 | 是否必需 | 移动端动作 |
|------|----------|----------|------------|
| `message_start` | 流开始 | 当前已实现 | 绑定 `message_id`、`session_id` |
| `text_delta` | 每次文本增量 | 是 | 追加到机器人消息气泡 |
| `tts_segment` | 可朗读片段切出后立即发送 | 已实现 | 记录片段元信息和预告 URL，不下载、不播放 |
| `tts_segment_ready` | **音频文件已生成** | 已实现 | **播放 `audio_url`，用 `marks` 驱动口型** |
| `tts_audio_error` | 某个 TTS 片段合成失败或被取消 | 已实现 | 标记该片段失败，跳过播放并释放后续排队片段 |
| `avatar_action` | 流开始后立即发送（基于用户问题推断） | 当前已实现 | 更新数字人表情、动作 |
| `sources` | 检索来源完成后 | 当前已实现，未接 RAG 时为空数组 | 补齐消息来源 |
| `route_data` | 路线规划结构化数据完成后 | 当前已实现，`mode=route` 且有数据时发送 | 补齐路线卡片数据 |
| `metadata` | 意图、情绪、耗时等完成后 | 当前已实现 | 补齐统计与降级字段 |
| `done` | 文本与结构化数据发送完成 | 是 | 关闭 loading，等待 TTS 队列播放完成 |
| `aborted` | 收到 `chat/abort` | 已实现 | 停止流、清空本次 TTS 队列、恢复待机 |
| `error` | 流式链路异常 | 当前已实现 | 停止流与 TTS，显示错误 |
| `PrematurelyEnded` | Android 端生成：EOF 但未收到终止事件 | Android 端 | 触发自动续写，最多 3 次 |

> **保留但不播放的事件**：`tts_audio_chunk`、`tts_audio_end` 后端仍可能发送，但 Android 端 `StreamingChatClient` 忽略不处理。

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

> 后端已发送该事件。`ChatService.stream_chat_events()` 在接收 `text_delta` 的同时按情绪边界优先、句子边界次之的策略缓冲文本，切分出可朗读片段后立即推送 `tts_segment`。音频合成在后台执行，文件写盘完成后再推送 `tts_segment_ready`。

```json
{
  "type": "tts_segment",
  "segment_id": "seg_001",
  "segment_index": 0,
  "text": "欢迎来到灵山胜境，",
  "audio_url": "/api/v1/tts/file/seg_001.mp3",
  "duration_ms": null,
  "voice": "zh-CN-XiaoxiaoNeural",
  "rate": "+0%",
  "volume": "+0%",
  "pitch": "+0Hz",
  "emotion": "welcoming",
  "marks": []
}
```

字段说明：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `segment_id` | String | 是 | 分段音频 ID，单次回答内唯一 |
| `segment_index` | Int | 是 | 分段序号，单次回答内从 0 开始递增；客户端播放队列应以该字段排序 |
| `text` | String | 是 | 该音频片段对应文本 |
| `audio_url` | String | 是 | 音频相对路径预告；真实可播放文件以 `tts_segment_ready` 为准 |
| `duration_ms` | Int / null | 否 | `tts_segment` 阶段通常为 `null`；真实值见 `tts_segment_ready.duration_ms` |
| `voice` | String | 否 | 实际使用发音人；只会是晓晓或晓伊，非法请求值会回落为后端默认流式音色 |
| `rate` | String | 否 | 实际使用语速 |
| `volume` | String | 否 | 实际使用音量 |
| `pitch` | String | 否 | 实际使用音调 |
| `emotion` | String | 否 | LLM 标注的情绪或后验推断的情绪，如 `welcoming`、`excited`、`thinking` 等 |
| `marks` | Array | 否 | `tts_segment` 阶段通常为空；真实值见 `tts_segment_ready.marks` |

约束：

- `tts_segment.text` 必须是已通过 `text_delta` 展示过的文本子串。
- `tts_segment` 只负责让移动端记录队列元信息；不要立即请求 `audio_url`，此时文件可能不存在并返回 404。
- 移动端播放下一段前，应重置或重新启动该片段的口型时间轴。

### 5.5 `tts_segment_ready`（当前主事件）

```json
{
  "type": "tts_segment_ready",
  "segment_id": "seg_001",
  "segment_index": 0,
  "audio_url": "/api/v1/tts/file/seg_001.mp3",
  "file_name": "seg_001.mp3",
  "duration_ms": 1800,
  "marks": [
    { "text": "欢", "start_ms": 0, "end_ms": 210, "phonemes": ["h", "u", "an"] },
    { "text": "迎", "start_ms": 210, "end_ms": 420, "phonemes": ["i", "ng"] }
  ],
  "emotion": "welcoming"
}
```

字段说明：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `segment_id` | String | 是 | 对应 `tts_segment.segment_id` |
| `segment_index` | Int | 是 | 对应 `tts_segment.segment_index` |
| `audio_url` | String | 是 | 最终落盘 MP3 的相对路径，拼接服务器 base URL 后播放 |
| `file_name` | String | 否 | 音频文件名 |
| `duration_ms` | Int / null | 否 | 音频总时长（毫秒） |
| `marks` | Array | 否 | 字/词级时间标记，用于口型同步；`text` 为主字段，Android 兼容旧 `word` 字段 |
| `emotion` | String | 否 | 该片段情绪 |

约束：

- 移动端收到 `tts_segment_ready` 后，按 `segment_index` 排序，用 ExoPlayer 播放 `audio_url`。
- `audio_url` 是相对路径，播放前用当前服务器 base URL 拼成完整地址。
- 口型同步直接用播放器当前进度匹配 `marks.start_ms/end_ms`。
- `marks[-1].end_ms` 与 `duration_ms` 对齐；不要按字符数或其它时长重新拉伸 marks。
- 若 `audio_url` 播放失败，跳过该段，等待后续片段或 `done` 后整段 TTS 降级。

### 5.6 `tts_audio_error`

当后台 TTS 合成任务被取消、超时或失败时，后端发送该事件。移动端必须跳过对应片段，不能继续请求该片段的预告 `audio_url`。

```json
{
  "type": "tts_audio_error",
  "segment_id": "seg_001",
  "segment_index": 1,
  "message": "tts synthesize cancelled"
}
```

移动端行为：

1. 记录失败的 `segment_id` / `segment_index`。
2. 调用 `skipSpeechSegment()` 推进 TTS 播放队列。
3. 如果后续又收到同一片段的 `tts_segment_ready`，直接忽略。
4. 释放已缓存但被该失败 index 卡住的后续片段。
5. 通知打字机可以继续显示文本，避免无音频时 UI 卡住。

### 5.7 `PrematurelyEnded`（Android 端生成）

当 SSE 流读取到 EOF，但从未收到 `done`、`aborted`、`error` 等终止事件时，Android 端 `StreamingChatClient` 会生成 `ChatStreamEvent.PrematurelyEnded`。

**触发条件**：
- 网络不稳定导致连接提前断开
- 后端异常崩溃未发送终止事件
- 代理/网关中途截断响应

**移动端行为**：
1. 保留已收到的文本与已播放的音频。
2. 触发自动续写：将当前已展示文本作为上下文，构造续写提示词重新调用 `POST /api/v1/chat/text/stream`。
3. 最多尝试 3 次（`MAX_STREAM_CONTINUATION_ATTEMPTS = 3`）。
4. 续写流的首个 `text_delta` 会无缝追加到同一消息气泡。
5. 超过最大次数仍未恢复，则标记消息为"回答中断"。

### 5.8 已弃用音频 Chunk 事件（`tts_audio_chunk` / `tts_audio_end`）

> 以下事件后端仍保留发送，但 Android 端 `StreamingChatClient` 已忽略不处理：
> ```kotlin
> "tts_audio_chunk", "tts_audio_end" -> null
> ```
> 
> 当前播放以 `tts_segment_ready.audio_url` 为准，不再消费实时音频 chunk。
> 若后端未来重新启用 chunk 方案，Android 端需恢复对应事件解析与缓冲逻辑。

### 5.9 `avatar_action`

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
- **表情（`expression`）与 TTS 情绪（`tts_segment.emotion`）已解耦**：
    - `expression.type` 由 `_intent_to_expression` 映射，专用于数字人面部表情展示。
    - `tts_segment.emotion` 由 `_intent_to_emotion` 映射，专用于 TTS 韵律参数（rate/volume）。
    - 两者可独立取值，例如用户问路时 TTS 保持中性语速（`thinking`），但表情呈现专注态（`focused`）。
- 若后续需要更细粒度的实时表情切换，前端可结合 `tts_segment.emotion` 在播放每个片段时动态更新表情。
- Gesture 层不得修改面部参数和 `ParamMouthOpenY` / `ParamMouthForm`。

### 5.9 `sources`

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

### 5.10 `route_data`

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

### 5.11 `metadata`

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

### 5.12 `done`

```json
{
  "type": "done",
  "message_id": "m_xxx",
  "session_id": "s_xxx",
  "full_text": "可选，完整助手回复文本"
}
```

说明：

- `done` 表示后端文本、结构化数据、TTS 片段都已发送完毕。
- `done` 不代表移动端音频播放已完成。
- `full_text` 为可选字段。若存在，移动端优先使用它作为最终 Markdown 渲染源；若不存在，移动端使用已按顺序累积的 `text_delta` 内容。
- 后端若对最终文本做了清洗、截断或安全过滤，应通过 `full_text` 返回权威版本。
- 移动端收到 `done` 后应调用 TTS 队列 `finishInput()`，队列会在最后一个音频片段播放完后恢复 IDLE。

### 5.13 `aborted`

```json
{
  "type": "aborted",
  "message_id": "m_xxx",
  "session_id": "s_xxx",
  "reason": "user_stop"
}
```

收到 `aborted` 后，移动端应停止追加文本、清空未播放的本次回答音频队列，并恢复数字人待机状态。本次流不会再发送 `done`。

### 5.14 `error`

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
      ├─ 立即发送 tts_segment（段落元信息预告）
      └─ 合成完成后发送 tts_segment_ready（audio_url / duration_ms / marks）
      ▼
移动端收到 tts_segment_ready 后拼接完整 audio_url 并排队播放
```

当前后端实现中，`text_delta`、`tts_segment` 和 TTS 合成已解耦：原始 delta 会先解析并剥离情绪标签后发送给移动端，TTS 合成在后台任务中执行，音频文件生成完成后通过 `tts_segment_ready` 推送，避免语音合成阻塞后续文字展示。

### 6.2.1 LLM 接入配置

当前后端已从 PPIO 代理切换为 DeepSeek 官方 OpenAI 兼容接口。环境变量名暂沿用 `PPIO_*`，避免影响现有代码和部署脚本：

```env
PPIO_BASE_URL=https://api.deepseek.com
PPIO_API_KEY=<your_deepseek_api_key>
PPIO_MODEL_NAME=deepseek-v4-flash
```

`deepseek-v4-flash` 调用时通过 `extra_body={"thinking":{"type":"disabled"}}` 关闭思考模式，流式输出直接消费 `delta.content`。真实 API Key 只允许写入本地 `.env` 或部署密钥管理系统，不写入文档、仓库或示例响应。

### 6.3 TTS 参数选择优先级

每个 `tts_segment` 的 `rate` / `volume` / `pitch` 按以下优先级确定：

1. **用户自定义参数最高优先级**：若请求 `options` 中传入了 `rate` / `volume` / `pitch`，直接使用该值。
2. **LLM 标注的情绪映射**：若 LLM 输出了 `<emotion="xxx">` 标签，使用该情绪对应的 `_EMOTION_TO_TTS_PROSODY` 参数。
3. **后验推断回退**：若文本无情绪标签（plain text），调用 `_detect_intent` 基于关键词匹配推断情绪，再映射为 TTS 参数。
4. **neutral 默认**：以上均未命中时，使用 `neutral` 的默认参数（`+0%` / `+0%` / `+0Hz`）。

可用情绪与对应参数：

| 情绪 | rate | volume | pitch |
|------|------|--------|-------|
| welcoming | +10% | +5% | +0Hz |
| happy | +5% | +0% | +0Hz |
| excited | +15% | +10% | +0Hz |
| thinking | -5% | +0% | +0Hz |
| apologetic | -10% | -5% | +0Hz |
| concerned | -5% | -5% | +0Hz |
| surprised | +10% | +10% | +0Hz |
| grateful | +0% | +5% | +0Hz |
| reverent | -10% | -10% | +0Hz |
| playful | +10% | +5% | +0Hz |
| approving | +0% | +0% | +0Hz |
| focused | +0% | +0% | +0Hz |
| neutral | +0% | +0% | +0Hz |

**设计说明**：`pitch` 统一为 `+0Hz`。早期尝试过按情绪映射不同 `pitch`（如 excited +30Hz、apologetic -20Hz），但 `edge-tts` 的 `prosody pitch` 会实质改变音色，导致同一段语音的不同 segment 听起来像多个人在说话。情绪差异仅通过 `rate` 和 `volume` 体现，已验证听感自然稳定。

### 6.3 文本一致性要求

后端需维护两份文本：

- `display_text`：用于 `text_delta`，尽量保留适合展示的格式。
- `speech_text`：用于 `tts_segment.text`，清洗 markdown、HTML、URL 等不适合朗读的内容。

若两者不完全一致，`tts_segment.text` 应是用户可理解的朗读文本，不要求 UI 再次展示。

---

## 七、Android 端数据模型

### 7.1 `ChatStreamEvent`（已落地）

文件：`app/src/main/java/com/example/scenic_avatar_guide_app/domain/model/ChatStreamEvent.kt`

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

    data class TtsSegmentReady(
        val segment: TtsSegmentData
    ) : ChatStreamEvent

    data class TtsAudioError(
        val error: TtsAudioErrorData
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

    data object PrematurelyEnded : ChatStreamEvent

    data class Aborted(
        val messageId: String? = null,
        val sessionId: String? = null,
        val reason: String? = null
    ) : ChatStreamEvent

    data class Error(
        val code: Int? = null,
        val message: String
    ) : ChatStreamEvent
}
```

> 注：`TtsAudioChunk`、`TtsAudioEnd` 已从主播放事件移除；后端若发送，解析器返回 `null` 忽略。`TtsAudioError` 保留，用于跳过失败片段。

### 7.2 `TtsAudioErrorData`

```kotlin
@Serializable
data class TtsAudioErrorData(
    @SerialName("segment_id")
    val segmentId: String? = null,
    @SerialName("segment_index")
    val segmentIndex: Int? = null,
    val code: Int? = null,
    val message: String? = null,
    val reason: String? = null,
    val error: String? = null
)
```

### 7.3 `TtsSegmentData`

```kotlin
@Serializable
data class TtsSegmentData(
    @SerialName("segment_id")
    val segmentId: String,
    @SerialName("segment_index")
    val segmentIndex: Int? = null,
    val text: String = "",
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

@Serializable
data class TtsMarkItem(
    @SerialName("word")
    val word: String = "",
    @SerialName("text")
    val text: String? = null,
    @SerialName("start_ms")
    val startMs: Int,
    @SerialName("end_ms")
    val endMs: Int,
    val phonemes: List<String>? = null
) {
    val spokenText: String
        get() = word.ifBlank { text.orEmpty() }
}
```

### 7.4 流式解析内部 DTO

`StreamingChatClient` 内部使用 `ChatStreamEnvelope` 统一反序列化 SSE payload，再映射为 `ChatStreamEvent`。该 DTO 包含所有可能字段（含已弃用的 chunk 字段），通过 `type` 字段分发。

---

## 八、Android 网络层改造

### 8.1 `StreamingChatClient`（已落地）

文件：`app/src/main/java/com/example/scenic_avatar_guide_app/data/remote/StreamingChatClient.kt`

职责：

1. 构造 `POST /api/v1/chat/text/stream` 请求。
2. 复用动态 base URL 逻辑（`DynamicBaseUrlInterceptor`）。
3. 使用 OkHttp 直接读取 `ResponseBody`。
4. 逐行解析 SSE。
5. 将 JSON 事件转换为 `ChatStreamEvent`；`tts_audio_error` 转换为跳过片段事件，`tts_audio_chunk`/`tts_audio_end` 返回 `null` 忽略。
6. EOF 时若未收到终止事件，生成 `ChatStreamEvent.PrematurelyEnded`。
7. 协程取消时取消 OkHttp `Call`。

核心逻辑：

```kotlin
class StreamingChatClient @Inject constructor(
    @StreamingOkHttp private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    fun streamChat(request: ChatTextRequest): Flow<ChatStreamEvent> = callbackFlow {
        // ... 构造请求 ...
        val call = okHttpClient.newCall(httpRequest)

        val readJob = launch(Dispatchers.IO) {
            try {
                call.execute().use { response ->
                    // ... 读取 SSE 行 ...
                    var sawTerminalEvent = false
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: continue
                        val event = parseStreamLine(line, sseDataLines, ...)
                        if (event != null) {
                            sawTerminalEvent = sawTerminalEvent || event.isTerminalStreamEvent()
                            trySend(event)
                        }
                    }
                    // EOF flush
                    flushSseData(sseDataLines, currentEventType)?.let {
                        sawTerminalEvent = sawTerminalEvent || it.isTerminalStreamEvent()
                        trySend(it)
                    }
                    if (!sawTerminalEvent && !call.isCanceled()) {
                        trySend(ChatStreamEvent.PrematurelyEnded)
                    }
                }
            } catch (e: Exception) {
                if (!call.isCanceled()) {
                    trySend(ChatStreamEvent.Error(message = "..."))
                }
            } finally {
                close()
            }
        }

        awaitClose {
            readJob.cancel()
            call.cancel()
        }
    }

    private fun ChatStreamEvent.isTerminalStreamEvent(): Boolean {
        return this is ChatStreamEvent.Done ||
            this is ChatStreamEvent.PrematurelyEnded ||
            this is ChatStreamEvent.Aborted ||
            this is ChatStreamEvent.Error
    }
}
```

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

保留旧方法：

```kotlin
suspend fun sendTextMessage(...): Result<ChatResponseData>
```

新增流式方法：

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
2. 构造 `ChatTextRequest`。
3. 调用 `StreamingChatClient.streamChat(request)`。
4. 将上游异常转换为 `ChatStreamEvent.Error`。

保留旧方法的原因：

- 后端流式接口不可用时可降级。
- 便于灰度切换。
- 避免一次重构影响现有稳定主链路。

---

## 十、ViewModel 改造

### 10.1 状态与 Job（已落地）

`MainViewModel` 关键状态：

```kotlin
private var currentStreamJob: Job? = null
private var currentAssistantMessageId: String? = null
private val typewriterController = TypewriterController(viewModelScope)
private var prematureStreamEnd = false
private var continuationAttempts = 0
private var receivedText = false

// 认证状态
private val _isAuthenticated = MutableStateFlow(false)
val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

// 满意度反馈
private val _showFeedbackDialog = MutableStateFlow(false)
val showFeedbackDialog: StateFlow<Boolean> = _showFeedbackDialog.asStateFlow()
```

### 10.2 `TypewriterController`

文件：`MainViewModel.kt` 内联类

职责：
1. 按固定间隔（约 32ms）逐字追加文本，形成打字机效果。
2. 与 TTS 同步：首个 `tts_segment_ready` 到达后触发 `canStartDisplay`，打字机才开始展示文字。
3. 超时保护：若 TTS 在 1.5s 内未就绪，直接开始显示文字。
4. 自动续写时无缝衔接：续写流的 `text_delta` 直接追加到同一消息。

```kotlin
class TypewriterController(private val scope: CoroutineScope) {
    var onTextUpdate: ((messageId: String, text: String) -> Unit)? = null
    private var canStartDisplay = false

    fun notifyTtsReady() { canStartDisplay = true }

    fun start(messageId: String, fullText: String) {
        scope.launch {
            // 等待 TTS 同步信号或超时
            // ...
            // 逐字追加
        }
    }
}
```

### 10.3 流式发送流程

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
              ├─ typewriterController.start()
              └─ collect repository.sendTextMessageStream()
                      ├─ MessageStart    -> 绑定 messageId / sessionId
                      ├─ TextDelta       -> typewriterController 追加 / 直接更新
                      ├─ TtsSegment      -> playbackManager.prepareSpeechSegment()
                      ├─ TtsSegmentReady -> playbackManager.playSpeechSegment()
                      │                     typewriterController.notifyTtsReady()
                      ├─ AvatarAction    -> playbackManager.updateStreamingAction()
                      ├─ Sources         -> updateAssistantMessage()
                      ├─ RouteData       -> updateAssistantMessage()
                      ├─ Metadata        -> 缓存或更新降级字段
                      ├─ Done            -> playbackManager.finishStreamingInput()
                      ├─ PrematurelyEnded -> 标记 prematureStreamEnd = true
                      └─ Error           -> 标记错误并停止播放
              │
              └─ 流结束后：若 prematureStreamEnd 且收到过文本
                     且 continuationAttempts < 3 -> 自动续写
```

### 10.4 自动续写机制

当收到 `PrematurelyEnded` 且已收到过文本时：

```kotlin
if (prematureStreamEnd && receivedText && continuationAttempts < MAX_STREAM_CONTINUATION_ATTEMPTS) {
    continuationAttempts += 1
    val continuationPrompt = buildContinuationPrompt(currentMessageContent)
    // 重新调用 streamChat，新流的 text_delta 追加到同一消息
}
```

约束：
- 最多续写 3 次。
- 续写提示词基于当前已展示文本构造，保持上下文连贯。
- 若续写流再次 `PrematurelyEnded`，继续计数；超过上限则放弃并标记中断。

### 10.5 认证与反馈集成

**认证**：
- `AuthDialog` 提供注册/登录界面。
- `AuthRepository` 处理 `authRegister` / `authLogin` / `logout`。
- 未登录用户使用 `guest_{随机16位}` ID 兜底。
- 登录成功后 `saveAuthData` 同步更新 `userId` 到 DataStore。

**满意度反馈**：
- 每条助手消息支持长按/点击触发 `FeedbackDialog`。
- `submitFeedback(rating, isComplaint, comment)` 调用 `POST /api/v1/chat/feedback`。
- 评分 1-5 星，可选标记为投诉并填写留言。

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

### 11.1 `StreamingTtsQueue`（已落地）

文件：`app/src/main/java/com/example/scenic_avatar_guide_app/core/tts/StreamingTtsQueue.kt`

职责：

1. 接收后端 `tts_segment_ready` 获取可播放 `audio_url`。
2. 使用 `Channel<TtsSegmentData>(20)` 有界缓冲队列排队播放。
3. `PlaybackState` 状态机管理：`Idle` → `Receiving` → `Draining` → `Idle`。
4. `sessionEpoch` 机制：每次新问答递增 epoch，旧事件到达时自动丢弃，防止串扰。
5. `assertMainThread()` 强制主线程操作，避免并发崩溃。
6. 队列空但输入未结束时进入等待状态；队列空且输入已结束时恢复 IDLE。
7. 支持取消与清空。

核心结构：

```kotlin
class StreamingTtsQueue @Inject constructor(
    private val audioPlayer: AudioPlayer
) {
    sealed class PlaybackState {
        object Idle : PlaybackState()
        object Receiving : PlaybackState()
        object Draining : PlaybackState()
    }

    private val segmentChannel = Channel<TtsSegmentData>(20)
    private var playbackState: PlaybackState = PlaybackState.Idle
    private var sessionEpoch = 0

    fun startNewSession(): Int {
        sessionEpoch++
        // 清空旧队列...
        return sessionEpoch
    }

    fun enqueue(segment: TtsSegmentData, epoch: Int) {
        assertMainThread()
        if (epoch != sessionEpoch) return // 丢弃过期事件
        segmentChannel.trySend(segment)
        // 驱动播放协程...
    }

    fun finishInput(epoch: Int) { ... }
    fun cancel() { ... }
}
```

### 11.2 播放规则

| 场景 | 行为 |
|------|------|
| 收到 `tts_segment` | 仅记录元信息和 `segment_index`，不请求、不播放 `audio_url` |
| 收到 `tts_segment_ready` | 入队；若当前空闲则立即播放 |
| 当前片段播放中，收到新 `tts_segment_ready` | 加入队列尾部，顺序播放 |
| 当前片段播完，队列有下一段 | 立即播放下一段 |
| 当前片段播完，队列为空，流未结束 | 进入等待状态；后续 ready 到达后从 ENDED 状态恢复播放 |
| 当前片段播完，队列为空，流已结束 | 恢复 IDLE |
| 收到 `tts_audio_error` | 跳过对应 `segment_index`，释放后续可播放片段 |
| 用户发送新问题 | 取消当前音频、清空队列、epoch 递增 |
| `sessionEpoch` 不匹配 | 丢弃该事件，防止旧流串扰 |

### 11.3 与 `RemoteTTSController` 的关系

- 流式问答主链路：`StreamingTtsQueue` 消费 `tts_segment_ready`。
- 非流式降级：保留 `RemoteTTSController.speak(完整文本)` 用于独立 TTS 合成。
- 系统 TTS 兜底：极端离线场景下使用 `SystemTTS`。

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

如果流式接口失败，可按场景降级：

| 场景 | 降级策略 |
|------|----------|
| 首个事件前连接失败 | 调用旧 `sendTextMessage()` |
| 已收到部分文本后失败 | 保留部分文本，触发自动续写（最多 3 次）或标记“回答中断” |
| `tts_segment_ready` 播放失败 | 跳过该段，继续播放下一段；仍失败则保留文字流 |
| `tts_segment` 长时间不到 | UI 继续展示文字，数字人等待；可在最终用整段 TTS 兜底 |
| 流异常断开 | `PrematurelyEnded` 触发自动续写；超过上限则标记中断 |

第一阶段建议只实现最小降级：

- 首事件前失败：走旧接口。
- 首事件后失败：保留已展示文本并提示“回答中断，请重试”。

---

## 十四、实施顺序

### 阶段 1：协议与模型

1. 新增 `ChatStreamModels.kt`。
2. 定义 `ChatStreamEvent`、`TtsSegmentData`、解析 DTO。
3. 更新 API 文档，确认事件字段与后端实现一致。

### 阶段 2：网络流读取

1. 新增 `StreamingChatClient`。
2. 新增流式 OkHttpClient，禁用 BODY 日志，调整 `readTimeout`。
3. 实现 SSE/NDJSON 按行解析。
4. 用本地 mock 流验证解析。

### 阶段 3：Repository 接入

1. `GuideRepository` 新增 `sendTextMessageStream()`。
2. 保留旧 `sendTextMessage()`。
3. 增加异常到 `ChatStreamEvent.Error` 的转换。

### 阶段 4：UI 增量消息

1. `MainViewModel` 新增机器人占位消息。
2. 实现 `text_delta` 追加。
3. 实现 `sources`、`route_data`、`avatar_action` 回填。
4. 保证 LazyColumn 能随增量文本滚动到底部。

### 阶段 5：TTS 片段队列

1. 新增 `StreamingTtsQueue`。
2. 消费 `tts_segment_ready.audio_url`，拼接当前服务器 base URL 后交给 ExoPlayer 播放。
3. 使用 `Channel<TtsSegmentData>(20)` 有界缓冲，防止内存无限增长。
4. 实现 `PlaybackState` 状态机：`Idle` → `Receiving` → `Draining` → `Idle`。
5. 实现 `sessionEpoch` 机制，防止旧流事件串扰。
6. 队列空但流未结束时进入等待状态；流结束且队列空时恢复 IDLE。

### 阶段 6：口型与数字人

1. 收到 `tts_segment_ready.marks` 后转为片段内 `PhonemeEvent`。
2. 播放 `audio_url` 时，直接用播放器当前进度匹配 `marks.start_ms/end_ms`。
3. 每段音频结束时停止口型并归零。
4. 接入流式表情与动作更新。
5. 实现 `AvatarPlaybackManager` 的 `sessionEpoch` 检查，防止旧流口型串扰。

### 阶段 7：取消、降级、验收

1. 实现新问题打断旧流。
2. 实现页面销毁取消。
3. 实现首事件失败回退旧接口。
4. 完成真机联调与弱网测试。

---

## 十五、验收标准

### 15.1 基础流式展示

- 用户发送消息后，机器人消息气泡先出现占位。
- 收到首个 `text_delta` 后，文字立即显示。
- 后续 `text_delta` 按顺序追加，不重复、不乱序。
- 收到 `done` 后，`isLoading` 关闭。

### 15.2 流式 TTS

- 收到 `tts_segment_ready` 后，拼接完整 `audio_url` 并按 `segment_index` 入队播放。
- 当前片段播放时，后续片段可以继续入队。
- 文本卡顿且队列为空时，TTS 自然停顿，等待后续 ready 片段。
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
- 网络中断或流异常断开时，触发 `PrematurelyEnded` 自动续写（最多 3 次）。
- 超过续写次数上限后，标记消息为“回答中断，请重试”。
- `ViewModel.onCleared()` 后无后台音频继续播放。

---

## 十六、待确认问题

| # | 问题 | 建议 |
|---|------|------|
| 1 | 流式传输格式用 SSE 还是 NDJSON | **已确认：后端使用 SSE**，NDJSON 仅作为兼容备选 |
| 2 | `tts_segment` 由后端生成还是移动端拿 delta 后再请求 TTS | **已采用后端生成**；Android 端仍需保留整段 TTS 降级 |
| 3 | 首段最短长度 | 建议 8-12 个中文字符或 800ms 超时强制切分 |
| 4 | `tts_segment.text` 与展示文本不一致时如何处理 | 允许不完全一致，但必须语义一致 |
| 5 | TTS 播放失败是否跳过该段 | **播放 `tts_segment_ready.audio_url` 拼接后的完整 URL**；播放失败则跳过该段，继续播放下一段 |
| 6 | 是否保留非流式开关 | **已保留**：`POST /api/v1/chat/text` 作为降级路径 |
| 7 | `tts_audio_chunk` 是否仍需解析 | **已确认：Android 端忽略**，后端保留发送但 `StreamingChatClient` 返回 `null` |
| 8 | 自动续写次数上限 | **已确认：3 次**（`MAX_STREAM_CONTINUATION_ATTEMPTS = 3`） |
| 9 | 打字机超时时间 | **已确认：1.5s**（TTS 未就绪时直接开始展示文字） |

---

## 十七、与现有接口文档的关系

- [`../api/API_CONTRACT.md`](../api/API_CONTRACT.md) 已正式纳入 `POST /api/v1/chat/text/stream` 及全部事件定义。
- 本文档描述流式链路的完整实现与 Android 端架构。
- 当前稳定范围：**文本 SSE + 结构化事件 + `tts_segment_ready` 分段音频 + `PrematurelyEnded` 自动续写 + 认证/反馈/会话管理**。
- [`../tts/API_TTS_USAGE.md`](../tts/API_TTS_USAGE.md) 中的独立 TTS 接口仍保留，作为非流式与降级能力。
