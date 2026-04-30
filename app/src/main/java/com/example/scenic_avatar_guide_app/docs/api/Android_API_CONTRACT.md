u# 移动端 API 接口契约

版本：v7.0  
日期：2026-04-30  
状态：**Android 端已全面切换为 SSE 流式接口；`POST /api/v1/chat/text` 与整段 TTS 降级路径已从 Android 端移除，仅作为后端保留接口**

---

## 一、职责划分

### 后端负责
- LLM 对话生成
- 意图识别、情感分析
- 数字人动作指令生成（表情、动作）
- 知识库检索
- **TTS 语音合成**（Edge-TTS 服务）
- **音频文件生成与缓存**
- **WordBoundary 对齐的字级音素时间戳（marks）生成**

### 移动端负责
- UI 展示与交互
- 请求后端 TTS 接口获取音频 URL
- 音频播放（ExoPlayer）
- 数字人渲染（Live2D）
- 口型动画（根据 marks 或音频进度驱动）
- 解析 `POST /api/v1/chat/text/stream` 的 SSE 事件流，并在 `text_delta` 到达时增量更新消息气泡
- 按 `tts_segment` 入队播放音频，并根据 `tts_segment.emotion` 驱动数字人表情更新

---

## 二、通用响应格式

```json
{
  "code": 0,
  "message": "ok",
  "data": {}
}
```

| code | 说明 |
|------|------|
| 0 | 成功 |
| 1001 | 参数缺失 |
| 1002 | 参数格式错误 |
| 1003 | 会话不存在 |
| 2001 | 服务内部错误 |
| 2002 | LLM 服务不可用 |
| 2003 | 知识库检索失败 |

---

## 三、核心接口

### 3.1 健康检查

**接口**：`GET /api/v1/health`

**响应**：
```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "service": "ai-tour-guide-backend",
    "status": "running",
    "version": "1.0.0"
  }
}
```

---

### 3.2 创建会话

**接口**：`POST /api/v1/session/create`

**请求**：
```json
{
  "user_id": "u_001",
  "scenic_id": "scenic_001",
  "spot_id": "spot_001",
  "device_id": "android_001"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| user_id | String | 是 | 用户唯一标识 |
| scenic_id | String | 是 | 景区 ID |
| spot_id | String | 否 | 当前景点 ID |
| device_id | String | 否 | 设备标识 |

**响应**：
```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "session_id": "s_xxx",
    "user_id": "u_001",
    "scenic_id": "scenic_001",
    "status": "active",
    "created_at": "2026-04-28T12:00:00Z"
  }
}
```

---

### 3.3 统一交互接口（非流式）

**接口**：`POST /api/v1/chat/text`

> **Android 端已移除。** 该接口仅作为后端保留接口，供其他客户端或内部服务使用。Android 客户端自 v7.0 起全面使用流式接口（3.4），不再提供非流式降级。

---

### 3.4 统一交互流式接口（Android 唯一主链路）

**接口**：`POST /api/v1/chat/text/stream`

**请求头**：

```http
Accept: text/event-stream
Content-Type: application/json
```

**说明**：

- 请求体与 `POST /api/v1/chat/text` 保持一致，可额外携带 `options`。
- 响应类型为 `text/event-stream`。
- 后端已实现文本增量流、分段 TTS 音频事件、最终数字人动作、来源、路线数据、元数据和完成事件。
- `tts_segment` 已由后端生成：后端会按句子边界或最大长度切分短音频，并通过后台任务合成，后续 `text_delta` 不再等待 TTS 合成完成。
- 流式 TTS 的 `options.voice` 仅接受 `zh-CN-XiaoxiaoNeural`（晓晓）和 `zh-CN-XiaoyiNeural`（晓伊）；未传或传入其它音色时，后端统一回落到默认 `zh-CN-XiaoxiaoNeural`。
- 流式 TTS 会使用与 `/api/v1/tts/synthesize` 相同的 `rate`、`volume`、`pitch` 规则；未传时默认 `rate=+0%`、`volume=+0%`、`pitch=+0Hz`。
- Android 端将 `tts_segment` 入队播放；每个 `tts_segment` 携带的 `emotion` 字段用于驱动该片段播放期间的数字人表情动态更新。
- 若流式接口失败，Android 端应提示用户重试，不再降级到非流式接口。

**请求示例**：

```json
{
  "session_id": "s_xxx",
  "user_id": "u_001",
  "scenic_id": "scenic_001",
  "question": "请用一句话介绍灵山大佛。",
  "spot_id": null,
  "mode": "chat",
  "image_url": null,
  "options": {
    "need_avatar": true,
    "need_sources": true
  }
}
```

**options 字段说明（流式接口）**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| need_avatar | Boolean | 否 | 预留字段，当前后端不依赖该值决定是否返回 `avatar_action` |
| need_sources | Boolean | 否 | 预留字段，当前未接 RAG 时 `sources` 为空数组 |
| voice | String | 否 | 流式 TTS 音色，仅支持 `zh-CN-XiaoxiaoNeural` 和 `zh-CN-XiaoyiNeural`；其它值会回落到默认晓晓 |
| rate | String | 否 | 流式 TTS 语速，默认 `+0%` |
| volume | String | 否 | 流式 TTS 音量，默认 `+0%` |
| pitch | String | 否 | 流式 TTS 音调，默认 `+0Hz` |
| emotion | String | 否 | 期望的情感风格，后端可能映射到 `tts_segment.emotion` |

**SSE 响应示例**：

```text
event: message_start
data: {"type":"message_start","message_id":"m_xxx","session_id":"s_xxx","created_at":"2026-04-29T20:26:08+08:00"}

event: text_delta
data: {"type":"text_delta","delta":"灵山大佛是一座高达88米的"}

event: text_delta
data: {"type":"text_delta","delta":"青铜释迦牟尼立像。"}

event: tts_segment
data: {"type":"tts_segment","segment_id":"seg_m_xxx_0","text":"灵山大佛是一座高达88米的青铜释迦牟尼立像。","audio_url":"/api/v1/tts/file/tts_m_xxx_000.mp3","duration_ms":1800,"voice":"zh-CN-XiaoxiaoNeural","rate":"+0%","volume":"+0%","pitch":"+0Hz","emotion":"excited","marks":[]}

event: avatar_action
data: {"type":"avatar_action","data":{"expression":{"type":"excited","intensity":0.7,"transition_ms":200},"gesture":{"type":"point_right","loop":false,"speed":1.0,"priority":"normal"},"motion_queue":[]}}

event: sources
data: {"type":"sources","data":[]}

event: metadata
data: {"type":"metadata","data":{"intent":"introduction","emotion":"excited","confidence":0.9,"is_fallback":false,"latency_ms":1000}}

event: done
data: {"type":"done","message_id":"m_xxx","session_id":"s_xxx"}
```

`tts_segment.voice`、`rate`、`volume`、`pitch` 是后端实际用于合成音频的参数。移动端应以这些返回值为准；若请求里传了非法流式音色，返回值会是回落后的 `zh-CN-XiaoxiaoNeural`。

**事件说明**：

| type | 必返 | 说明 |
|------|------|------|
| `message_start` | 是 | 流开始，绑定 `message_id`、`session_id` 与创建时间 |
| `text_delta` | 是 | 文本增量，按顺序追加，不重复发送已发送内容 |
| `tts_segment` | 否 | 后端已支持；每个可朗读片段完成 TTS 合成后发送，客户端可入队播放；`emotion` 字段用于驱动该片段播放期间的数字人表情 |
| `avatar_action` | 是 | 当前实现于文本生成完成后发送 |
| `sources` | 是 | 当前未接 RAG 时为空数组 |
| `route_data` | 条件 | 仅 `mode=route` 且后端解析出路线数据时发送 |
| `metadata` | 是 | 意图、情绪、耗时、置信度与降级信息 |
| `done` | 是 | 后端文本与结构化事件发送完成 |
| `error` | 否 | 流式链路异常，格式为 `{"type":"error","code":2002,"message":"..."}` |

---

## 四、TTS 接口（Edge-TTS 方案）

> 自 v5.0 起，TTS 由后端统一提供。Android 端通过以下接口请求音频合成，使用 ExoPlayer 播放返回的音频 URL。
> 
> **注意**：`POST /api/v1/tts/synthesize` 现为测试/调试专用接口，不再作为聊天主链路的降级方案。Android 端聊天音频仅通过流式接口的 `tts_segment` 获取。

### 4.1 获取发音人列表

**接口**：`GET /api/v1/tts/voices`

**响应**：
```json
{
  "code": 0,
  "message": "ok",
  "data": [
    {
      "id": "zh-CN-XiaoxiaoNeural",
      "locale": "zh-CN",
      "gender": "Female",
      "friendly_name": "晓晓"
    },
    {
      "id": "zh-CN-YunyangNeural",
      "locale": "zh-CN",
      "gender": "Male",
      "friendly_name": "云扬"
    }
  ]
}
```

**字段说明**：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | String | 发音人标识，用于 `synthesize` 请求 |
| locale | String | 语言区域，如 `zh-CN` |
| gender | String | `Male` / `Female` |
| friendly_name | String | 展示用中文名 |

---

### 4.2 文本合成

**接口**：`POST /api/v1/tts/synthesize`

**请求**：
```json
{
  "text": "您好，欢迎来到灵山胜境。",
  "voice": "zh-CN-XiaoxiaoNeural",
  "rate": "+0%",
  "volume": "+0%",
  "pitch": "+0Hz",
  "format": "audio"
}
```

**请求字段说明**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| text | String | 是 | 待合成文本，最长 500 字 |
| voice | String | 否 | 发音人 ID，默认 `zh-CN-XiaoxiaoNeural` |
| rate | String | 否 | 语速，如 `+10%`、`-20%`，默认 `+0%` |
| volume | String | 否 | 音量，如 `+10%`，默认 `+0%` |
| pitch | String | 否 | 音调，如 `+5Hz`、`-5Hz`，默认 `+0Hz` |
| format | String | 否 | `audio` 或 `audio_with_marks`，默认 `audio` |

**响应示例（format=audio）**：
```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "audio_url": "/api/v1/tts/file/7d4f1f.mp3",
    "duration_ms": 4200,
    "voice": "zh-CN-XiaoxiaoNeural"
  }
}
```

**响应示例（format=audio_with_marks）**：
```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "audio_url": "/api/v1/tts/file/7d4f1f.mp3",
    "duration_ms": 4200,
    "voice": "zh-CN-XiaoxiaoNeural",
    "marks": [
      { "text": "您", "start_ms": 0, "end_ms": 160, "phonemes": ["n", "i", "n"] },
      { "text": "好", "start_ms": 160, "end_ms": 320, "phonemes": ["h", "ao"] },
      { "text": "，", "start_ms": 320, "end_ms": 520, "phonemes": ["SIL"] }
    ]
  }
}
```

**响应字段说明**：

| 字段 | 类型 | 说明 |
|------|------|------|
| audio_url | String | 音频文件相对路径，需拼接 base URL 访问 |
| duration_ms | Long / null | 保存后的 MP3 真实音频总时长（毫秒） |
| voice | String | 实际使用的发音人 |
| marks | Array | WordBoundary 对齐的字级音素时间戳，仅 `audio_with_marks` 返回 |

**marks 结构**：

| 字段 | 类型 | 说明 |
|------|------|------|
| text | String | 单个字符；尾部静音补齐时可为空字符串 |
| start_ms | Long | 片段内开始时间（毫秒，从 0 开始） |
| end_ms | Long | 片段内结束时间（毫秒） |
| phonemes | List<String> | 中文音素数组；标点/停顿为 `["SIL"]`；非 CJK 字符可为空数组 |

**口型同步约束**：

- `marks[-1].end_ms` 与 `duration_ms` 对齐。
- 后端已基于 Edge-TTS `WordBoundary` 生成时间轴，Android 端不要按字符数或 `duration_ms` 重新拉伸 marks。
- 端侧应使用播放器当前进度匹配 `start_ms <= currentPositionMs < end_ms` 的 mark；marks 直接使用原始时间值，不再在客户端做时间缩放。

---

### 4.3 音频文件读取

**接口**：`GET /api/v1/tts/file/{file_name}`

**返回**：`audio/mpeg` 音频流

**说明**：
- 该接口用于 ExoPlayer 直接播放
- 音频文件由后端缓存管理，有效期 7 天
- 若文件不存在返回 `404`

---

## 五、聊天响应结构

### 5.1 图片上传接口（前置）

**接口**：`POST /api/v1/upload/image`

**请求**：multipart/form-data
- `image`：图片文件（JPEG/PNG，最大 5MB）

**响应**：
```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "image_url": "/uploads/img_abc123.jpg"
  }
}
```

**说明**：
- 用户上传图片后，将返回的 `image_url` 填入流式聊天请求的 `image_url` 字段，实现图文问答。

---

### 5.2 流式事件数据组装说明

Android 端不再接收完整的 `ChatResponseData` JSON，而是通过 SSE 流逐事件组装最终状态：

- `text_delta` 事件：追加到 `reply_text`
- `tts_segment` 事件：入队到音频播放器，并根据 `emotion` 更新数字人表情
- `avatar_action` 事件：更新数字人动作与表情
- `sources` 事件：填充来源引用列表
- `metadata` 事件：填充意图、情绪、耗时等元数据
- `done` 事件：标记当前消息流结束

### 5.3 路线规划模式流式示例

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "message_id": "m_003",
    "session_id": "s_001",
    "reply_text": "建议您从九龙灌浴开始，游览约1小时后前往灵山大佛，最后到梵宫结束行程。全程约4小时。",
    "route_data": {
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
          "description": "整点有水景表演，非常震撼"
        },
        {
          "name": "灵山大佛",
          "lat": 31.4880,
          "lng": 120.1240,
          "order": 2,
          "stay_min": 90,
          "description": "核心地标，高88米"
        },
        {
          "name": "梵宫",
          "lat": 31.4885,
          "lng": 120.1245,
          "order": 3,
          "stay_min": 60,
          "description": "金碧辉煌的建筑艺术殿堂"
        }
      ],
      "polyline": [
        {"lat": 31.4875, "lng": 120.1234},
        {"lat": 31.4880, "lng": 120.1240},
        {"lat": 31.4885, "lng": 120.1245}
      ]
    },
    "avatar_action": {
      "expression": {"type": "happy"},
      "gesture": {"type": "guide"}
    },
    "metadata": {"intent": "route_recommendation", "latency_ms": 1500}
  }
}
```

**route_data 字段说明**：

| 字段 | 类型 | 说明 |
|------|------|------|
| title | String | 路线名称 |
| total_duration_min | Int | 预计总时长（分钟） |
| total_distance_m | Int | 预计总距离（米），可选 |
| spots | Array | 景点节点列表 |
| polyline | Array | 地图路径坐标数组，用于绘制路线 |

**spots 节点结构**：

| 字段 | 类型 | 说明 |
|------|------|------|
| name | String | 景点名称 |
| lat | Double | 纬度 |
| lng | Double | 经度 |
| order | Int | 游览顺序 |
| stay_min | Int | 建议停留时长（分钟） |
| description | String | 景点简介 |
| image_url | String | 景点图片，可选 |

---

## 六、AvatarAction 结构

### 6.1 数据模型

```kotlin
@Serializable
data class AvatarAction(
    @SerialName("expression")
    val expression: AvatarExpressionData? = null,

    @SerialName("gesture")
    val gesture: AvatarGestureData? = null,

    @SerialName("motion_queue")
    val motionQueue: List<MotionQueueItem>? = null,

    @SerialName("marks")
    val marks: List<AvatarMarkData>? = null
)
```

### 6.2 表情系统

```kotlin
@Serializable
data class AvatarExpressionData(
    @SerialName("type")
    val type: String = "neutral",

    @SerialName("intensity")
    val intensity: Float = 0.7f,

    @SerialName("transition_ms")
    val transitionMs: Long = 200
)
```

**表情类型**：

| 类型 | 说明 | 典型场景 |
|------|------|---------|
| `neutral` | 中性 | 普通回复 |
| `happy` | 开心 | 欢迎、推荐 |
| `thinking` | 思考 | 回答问题 |
| `surprised` | 惊讶 | 意外信息 |
| `excited` | 兴奋 | 介绍亮点 |
| `concerned` | 关切 | 提醒注意 |
| `apologetic` | 抱歉 | 无法回答 |
| `welcoming` | 欢迎 | 开场白 |

---

### 6.3 动作系统

```kotlin
@Serializable
data class AvatarGestureData(
    @SerialName("type")
    val type: String = "idle",

    @SerialName("loop")
    val loop: Boolean = false,

    @SerialName("speed")
    val speed: Float = 1.0f,

    @SerialName("priority")
    val priority: String = "normal"
)
```

**动作类型**：

| 类型 | 说明 | 典型场景 |
|------|------|---------|
| `idle` | 待机 | 默认状态 |
| `nod` | 点头 | 肯定、同意 |
| `shake` | 摇头 | 否定 |
| `wave` | 挥手 | 欢迎、再见 |
| `point_left` | 指左 | 介绍左侧景点 |
| `point_right` | 指右 | 介绍右侧景点 |
| `point_forward` | 指前 | 介绍前方景点 |
| `bow` | 鞠躬 | 感谢、道歉 |
| `thinking_pose` | 思考姿势 | 回答问题 |
| `guide` | 引导姿势 | 路线指引 |

---

### 6.4 动作队列

```kotlin
@Serializable
data class MotionQueueItem(
    @SerialName("type")
    val type: String,

    @SerialName("start_offset_ms")
    val startOffsetMs: Long,

    @SerialName("duration_ms")
    val durationMs: Long = 0
)
```

**示例**：

```json
{
  "motion_queue": [
    {"type": "wave", "start_offset_ms": 0, "duration_ms": 1500},
    {"type": "point_right", "start_offset_ms": 2000, "duration_ms": 3000}
  ]
}
```

---

### 6.5 特效标记

```kotlin
@Serializable
data class AvatarMarkData(
    @SerialName("position")
    val position: Float,  // 0.0-1.0 相对位置

    @SerialName("type")
    val type: String
)
```

**标记类型**：

| 类型 | 说明 |
|------|------|
| `emphasis` | 强调 |
| `blink` | 眨眼 |
| `pause` | 停顿 |

---

## 七、来源引用结构

```kotlin
@Serializable
data class SourceInfo(
    @SerialName("document_id")
    val documentId: String? = null,

    @SerialName("chunk_id")
    val chunkId: String? = null,

    @SerialName("title")
    val title: String? = null,

    @SerialName("content")
    val content: String? = null,

    @SerialName("url")
    val url: String? = null,

    @SerialName("source_path")
    val sourcePath: String? = null,

    @SerialName("score")
    val score: Float? = null,

    @SerialName("snippet")
    val snippet: String? = null,

    @SerialName("relevance_score")
    val relevanceScore: Float? = null
)
```

其中：

- `document_id / chunk_id / source_path / score / snippet` 来自同事新增的 `stage1-api.md`
- `title / content / url / relevance_score` 保留为兼容旧版文档与前端展示需求

---

## 八、元数据结构

```kotlin
@Serializable
data class ResponseMetadata(
    @SerialName("intent")
    val intent: String? = null,

    @SerialName("emotion")
    val emotion: String? = null,

    @SerialName("confidence")
    val confidence: Float? = null,

    @SerialName("is_fallback")
    val isFallback: Boolean? = null,

    @SerialName("latency_ms")
    val latencyMs: Long? = null
)
```

**意图类型**：

| intent | 说明 |
|--------|------|
| `greeting` | 打招呼 |
| `farewell` | 告别 |
| `introduction` | 景点介绍 |
| `direction` | 方向指引 |
| `route_recommendation` | 路线推荐 |
| `unknown` | 未知意图 |

---

## 九、端侧处理流程

### 8.1 流式完整播放流程

```
用户发送问题
      │
      ▼
  THINKING        显示思考状态
      │
      │ 建立 SSE 流式连接
      ▼
┌─────────────────────────────────────┐
│ 实时解析 SSE 事件                     │
│ ├── text_delta → 增量更新消息气泡     │
│ ├── tts_segment → 入队音频播放器      │
│ │   └── tts_segment.emotion → 表情更新│
│ ├── avatar_action → 动作/表情         │
│ └── sources / metadata → 结构化数据   │
└─────────────────────────────────────┘
      │
      ▼
┌─────────────────────────────────────┐
│ Android 播放与口型同步                │
│ ├── ExoPlayer 播放 tts_segment 队列   │
│ ├── marks 驱动口型动画（如可用）      │
│ └── 无 marks 时按字符时长估算兜底     │
└─────────────────────────────────────┘
      │
      ▼
  SPEAKING        音频播放 + 口型同步 + 动作
      │
      ▼
    IDLE          恢复待机状态
```

### 8.2 口型同步机制

#### 方案 A：基于 marks 的精确同步（推荐）

```
tts_segment.marks：
[
  { "text": "您好", "start_ms": 0, "end_ms": 320 },
  { "text": "欢迎", "start_ms": 340, "end_ms": 760 }
]
    │
    ▼
ExoPlayer 播放进度回调（currentPosition）
    │
    ▼
按时间匹配当前 mark → VisemeType 映射
    │
    ▼
设置 Live2D 口型参数：
├── ParamMouthOpenY = viseme.mouthOpen
└── ParamMouthForm = viseme.mouthForm
```

> **注意**：marks 时间值直接使用，不再在客户端做时间缩放。

#### 方案 B：字符时长估算（兜底）

当 `tts_segment` 未携带 marks 时：

```
tts_segment.text
    │
    ▼
按字符估算时长（中文约 180ms/字，标点约 300ms）
    │
    ▼
定时器推进口型状态
    │
    ▼
设置 Live2D 口型参数
```

---

## 十、移动端数据模型（Kotlin）

### 9.1 流式事件模型（Kotlin）

```kotlin
@Serializable
data class ChatOptions(
    @SerialName("need_avatar")
    val needAvatar: Boolean? = null,

    @SerialName("need_sources")
    val needSources: Boolean? = null,

    @SerialName("voice")
    val voice: String? = null,

    @SerialName("rate")
    val rate: String? = null,

    @SerialName("volume")
    val volume: String? = null,

    @SerialName("pitch")
    val pitch: String? = null,

    @SerialName("emotion")
    val emotion: String? = null
)

@Serializable
data class TtsSegmentData(
    @SerialName("segment_id")
    val segmentId: String,

    @SerialName("text")
    val text: String,

    @SerialName("audio_url")
    val audioUrl: String,

    @SerialName("duration_ms")
    val durationMs: Long,

    @SerialName("voice")
    val voice: String,

    @SerialName("rate")
    val rate: String,

    @SerialName("volume")
    val volume: String,

    @SerialName("pitch")
    val pitch: String,

    @SerialName("emotion")
    val emotion: String? = null,

    @SerialName("marks")
    val marks: List<MarkData>? = null
)

@Serializable
data class AvatarAction(
    @SerialName("expression")
    val expression: AvatarExpressionData? = null,

    @SerialName("gesture")
    val gesture: AvatarGestureData? = null,

    @SerialName("motion_queue")
    val motionQueue: List<MotionQueueItem>? = null,

    @SerialName("marks")
    val marks: List<AvatarMarkData>? = null
)
```

> **注意**：`ChatResponseData` 与 `ChatTextResponse` 已从 Android 端移除，不再使用。

### 9.2 端侧状态模型

```kotlin
enum class AvatarState {
    IDLE, LISTENING, THINKING, SPEAKING, ERROR
}

enum class AvatarExpression(val value: String) {
    NEUTRAL("neutral"),
    HAPPY("happy"),
    THINKING("thinking"),
    SURPRISED("surprised"),
    EXCITED("excited"),
    CONCERNED("concerned"),
    APologetic("apologetic"),
    WELCOMING("welcoming");

    companion object {
        fun fromValue(value: String?) = 
            entries.find { it.value == value } ?: NEUTRAL
    }
}

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
    GUIDE("guide");

    companion object {
        fun fromValue(value: String?) = 
            entries.find { it.value == value } ?: IDLE
    }
}

enum class VisemeType(val mouthOpen: Float, val mouthForm: Float = 0f) {
    CLOSED(0.0f),
    SLIGHT(0.25f),
    HALF(0.5f),
    OPEN(0.9f),
    WIDE(0.6f, -0.3f),
    ROUND(0.5f, 0.6f),
    NEUTRAL(0.1f);

    companion object {
        fun fromPhoneme(phoneme: String): VisemeType { ... }
    }
}
```

---

## 十一、场景示例

### 场景 1：欢迎问候

**用户**：你好

**响应**：
```json
{
  "message_id": "m_001",
  "session_id": "s_001",
  "reply_text": "您好！欢迎来到黄山风景区，我是您的智能导游。请问有什么可以帮您？",
  "avatar_action": {
    "expression": {"type": "welcoming", "intensity": 0.8},
    "gesture": {"type": "wave"}
  },
  "metadata": {"intent": "greeting", "emotion": "joy", "latency_ms": 500}
}
```

### 场景 2：景点介绍

**用户**：迎客松有什么故事？

**响应**：
```json
{
  "message_id": "m_002",
  "session_id": "s_001",
  "reply_text": "迎客松是黄山的标志性景观，树龄已有八百多年。它姿态优美，像一位热情好客的主人。",
  "avatar_action": {
    "expression": {"type": "excited", "intensity": 0.7},
    "gesture": {"type": "point_right"},
    "motion_queue": [
      {"type": "wave", "start_offset_ms": 0, "duration_ms": 1500},
      {"type": "point_right", "start_offset_ms": 2500}
    ]
  },
  "sources": [
    {
      "document_id": "doc_huangshan_manual",
      "chunk_id": "ck_001",
      "title": "黄山景区导览手册",
      "source_path": "data/raw/scenic_docs/huangshan_manual.md",
      "score": 0.95,
      "snippet": "迎客松是黄山标志性景观。"
    }
  ],
  "latency_ms": 1200,
  "confidence": 0.95,
  "is_fallback": false,
  "metadata": {"intent": "introduction", "latency_ms": 1200, "confidence": 0.95, "is_fallback": false}
}
```

### 场景 3：路线推荐

**用户**：半天时间怎么游览？

**响应**：
```json
{
  "message_id": "m_003",
  "session_id": "s_001",
  "reply_text": "建议您从慈光阁乘索道上山，游览迎客松和莲花峰，约需4小时。",
  "avatar_action": {
    "expression": {"type": "happy"},
    "gesture": {"type": "guide"}
  },
  "metadata": {"intent": "route_recommendation", "latency_ms": 1500}
}
```

### 场景 4：无法回答

**用户**：今天股票行情怎么样？

**响应**：
```json
{
  "message_id": "m_004",
  "session_id": "s_001",
  "reply_text": "抱歉，我主要提供景区导览服务，暂时无法回答其他问题。",
  "avatar_action": {
    "expression": {"type": "apologetic", "intensity": 0.7},
    "gesture": {"type": "bow"}
  },
  "metadata": {"intent": "unknown", "latency_ms": 300}
}
```

---

## 十二、当前确认结论

### 必须确认

| # | 问题 | 建议 |
|---|------|------|
| 1 | TTS 是否由移动端完成？ | **否**（v5.0 起改为后端 Edge-TTS） |
| 2 | 问答主链路最小返回 | SSE 流式事件：`text_delta` + `metadata` + `done` |
| 3 | `sources` 是否按 `stage1-api.md` 扩展？ | **是** |
| 4 | `scenic_id` 是否属于问答主请求必填？ | **是** |
| 5 | `avatar_action` 是否必返？ | 否，属于增强字段 |
| 6 | TTS 接口是否独立于 chat 接口？ | **是**，`POST /api/v1/tts/synthesize`（现为测试/调试专用） |
| 7 | 口型同步优先用什么驱动？ | **marks（词级时间戳）**，无 marks 时字符估算兜底；marks 不做客户端时间缩放 |
| 8 | 交互模式是否统一为 `chat/text/stream` 接口？ | **是**，通过 `mode` 字段区分 `chat` / `route` |
| 9 | `route_data` 是否只在 `mode=route` 时返回？ | **是**，`mode=chat` 时返回 null |
| 10 | 是否支持流式文本输出？ | **是**，`POST /api/v1/chat/text/stream`，SSE 格式；Android 唯一主链路 |
| 11 | 流式接口是否已包含分段 TTS？ | **是**；`tts_segment` 入队播放，`tts_segment.emotion` 驱动片段级表情更新 |
| 12 | 非流式 `chat/text` 是否仍作为 Android 降级？ | **否**，Android 端已移除，仅后端保留 |

### 可选确认

| # | 问题 | 说明 |
|---|------|------|
| 12 | 是否返回 `emotion` 字段 | 可用于表情降级，推荐保留 |
| 13 | 是否返回 `intent` 字段 | 可用于动作降级，推荐保留 |

---

## 十三、版本历史

| 版本 | 日期 | 变更 |
|------|------|------|
| v1.0 | 2026-04-28 | 初始版本 |
| v2.0 | 2026-04-28 | 完善数字人系统 |
| v2.1 | 2026-04-28 | 修正：TTS 由后端完成 |
| v3.0 | 2026-04-28 | **修正：TTS 由移动端完成，移除 audio 字段** |
| v4.0 | 2026-04-28 | **同步 `stage1-api.md`：补充 `scenic_id`、`sources` 新结构、`latency_ms/confidence/is_fallback` 兼容说明** |
| v5.0 | 2026-04-28 | **TTS 方案切换为后端 Edge-TTS：新增 `/api/v1/tts/*` 接口，更新职责划分与播放流程，marks 驱动口型同步** |
| v5.1 | 2026-04-29 | **Edge-TTS 接口已完成 Android 端联调；修正 `duration_ms` 可空类型；确认系统 TTS 降级兜底正常** |
| v6.0 | 2026-04-29 | **交互模式重构：三种模式缩减为两种（聊天问答 + 路线规划），统一 `POST /api/v1/chat/text` 接口，通过 `mode` 字段区分；新增 `route_data` 响应结构；新增图片上传接口** |
| v6.1 | 2026-04-29 | **新增 `POST /api/v1/chat/text/stream` SSE 流式文本接口；保留非流式接口作为降级** |
| v6.2 | 2026-04-30 | **同步后端现状：`chat/text/stream` 已发送阶段性 `tts_segment` 事件；Android 可按 Feature Flag 接入，保留整段 TTS 降级** |
| v6.3 | 2026-04-30 | **修复流式 TTS 阻塞文本 delta、长文本分段过长和 TTS 文件名安全问题；`tts_segment` 后端生成能力进入可联调状态** |
| v6.4 | 2026-04-30 | **同步 TTS marks 新实现：`duration_ms` 使用真实 MP3 时长，marks 使用 Edge-TTS WordBoundary 对齐的字级音素时间戳，端侧不要二次拉伸 marks** |
| v6.5 | 2026-04-30 | **流式 TTS 音色增加后端白名单：仅允许晓晓/晓伊，非法 `options.voice` 回落到默认晓晓，避免流式音源与普通 TTS 接口不一致** |
| v6.6 | 2026-04-30 | **修复流式对话与口型/场景测试接口的 TTS 参数不一致：流式链路现在透传 rate/volume/pitch，默认 pitch 改为 `+0Hz`，`tts_segment` 返回实际合成参数** |
| v7.0 | 2026-04-30 | **Android 端全面移除非流式降级：`POST /api/v1/chat/text` 与整段 TTS 降级路径从 Android 端移除；`chat/text/stream` 成为唯一主链路；`ChatOptions` 与 `TtsSegmentData` 新增 `emotion` 字段；marks 不再在客户端做时间缩放；`ChatResponseData` 与 `ChatTextResponse` 从 Android 端删除** |
