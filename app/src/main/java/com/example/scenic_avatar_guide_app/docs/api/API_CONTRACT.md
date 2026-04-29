# 移动端 API 接口契约

版本：v7.0  
日期：2026-04-29  
状态：**新增流式问答与分段 TTS 事件协议，保留非流式接口作为降级路径**

---

## 一、职责划分

### 后端负责
- LLM 对话生成
- 意图识别、情感分析
- 数字人动作指令生成（表情、动作）
- 知识库检索
- **TTS 语音合成**（Edge-TTS 服务）
- **音频文件生成与缓存**
- **词级时间标记（marks）生成**
- 流式问答事件输出（`text_delta` / `tts_segment` / 结构化收口事件）
- 长回答按可朗读片段切分并生成分段音频

### 移动端负责
- UI 展示与交互
- 请求后端 TTS 接口获取音频 URL
- 音频播放（ExoPlayer）
- 数字人渲染（Live2D）
- 口型动画（根据 marks 或音频进度驱动）
- 消费流式事件并增量更新消息气泡
- 将后端返回的 `tts_segment` 分段音频排队播放
- 在流式文本卡顿时停止口型并等待后续片段

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

### 3.3 统一交互接口（核心）

**接口**：`POST /api/v1/chat/text`

> 自 v6.0 起，聊天问答与路线规划统一为同一个接口，通过 `mode` 字段区分交互模式。

**请求**：
```json
{
  "session_id": "s_xxx",
  "user_id": "u_001",
  "scenic_id": "scenic_001",
  "question": "半天时间怎么游览？",
  "spot_id": null,
  "mode": "route",
  "image_url": null
}
```

**请求字段说明**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| session_id | String | 是 | 会话 ID |
| user_id | String | 是 | 用户 ID |
| scenic_id | String | 是 | 景区 ID |
| question | String | 是 | 用户问题（最长 500 字） |
| spot_id | String | 否 | 当前景点 ID |
| mode | String | 否 | 交互模式：`chat`（聊天问答，默认）或 `route`（路线规划） |
| image_url | String | 否 | 用户上传图片的 URL，聊天模式下支持图文问答 |

**模式说明**：

1. `mode=chat`：后端走 RAG 知识库问答，返回 `reply_text` 与 `sources`。
2. `mode=route`：后端切换为路线规划 Prompt，返回 `reply_text` 与结构化的 `route_data`。
3. 图片问答：先调用 `POST /api/v1/upload/image` 上传图片获取 `image_url`，再带 `image_url` 调用本接口。

---

### 3.4 统一交互流式接口（新增）

**接口**：`POST /api/v1/chat/text/stream`

**请求头**：
```http
Accept: text/event-stream
Content-Type: application/json
```

**请求体**：与 `POST /api/v1/chat/text` 保持一致。

**推荐传输格式**：SSE。若后端实现受限，可使用 NDJSON 作为兼容备选。

**核心事件示例**：
```text
event: message_start
data: {"type":"message_start","message_id":"m_xxx","session_id":"s_xxx"}

event: text_delta
data: {"type":"text_delta","delta":"欢迎来到灵山胜境，"}

event: tts_segment
data: {"type":"tts_segment","segment_id":"seg_001","text":"欢迎来到灵山胜境，","audio_url":"/api/v1/tts/file/seg_001.mp3","duration_ms":1800,"marks":[]}

event: done
data: {"type":"done","message_id":"m_xxx","session_id":"s_xxx"}
```

**事件类型说明**：

| type | 说明 | 移动端动作 |
|------|------|------------|
| `message_start` | 回答开始 | 绑定消息 ID 与会话 ID |
| `text_delta` | 文本增量 | 追加到当前机器人消息 |
| `tts_segment` | 可播放的 TTS 分段 | 音频入队播放，marks 驱动该段口型 |
| `avatar_action` | 数字人表情/动作 | 提前更新 Expression / Gesture |
| `sources` | 来源引用 | 回填到当前消息 |
| `route_data` | 路线规划结构化数据 | 回填路线卡片 |
| `metadata` | 意图、情绪、耗时、置信度等 | 回填统计与降级字段 |
| `done` | 后端事件流结束 | 关闭 loading，等待 TTS 队列自然播完 |
| `error` | 流式链路异常 | 停止流和 TTS，显示错误 |

**`tts_segment` 响应结构**：
```json
{
  "type": "tts_segment",
  "segment_id": "seg_001",
  "text": "欢迎来到灵山胜境，",
  "audio_url": "/api/v1/tts/file/seg_001.mp3",
  "duration_ms": 1800,
  "voice": "zh-CN-XiaoxiaoNeural",
  "marks": [
    { "text": "欢迎", "start_ms": 0, "end_ms": 420 },
    { "text": "来到", "start_ms": 430, "end_ms": 820 }
  ]
}
```

**分段约束**：

1. 不按 token 或单字合成 TTS，应按可朗读短句切分。
2. 推荐遇到 `，。！？；：` 切分；当前缓冲超过 12–25 个中文字符时允许切分。
3. 首段超过 800ms 仍未遇到标点时，强制切分一个短片段以提升首响。
4. 英文、数字、景点名、专有名词尽量不要从中间切断。
5. markdown、表格、链接等内容需在 TTS 合成前清洗为纯朗读文本。
6. `tts_segment.marks` 的时间戳为片段内相对时间。
7. `done` 表示后端事件发送完成，不表示移动端音频播放完成。
8. 旧接口 `POST /api/v1/chat/text` 继续保留，作为非流式降级路径。

**文本一致性要求**：

后端需维护两份文本：
- `display_text`：用于 `text_delta`，尽量保留适合展示的格式。
- `speech_text`：用于 `tts_segment.text`，清洗 markdown、HTML、URL 等不适合朗读的内容。

若两者不完全一致，`tts_segment.text` 应是用户可理解的朗读文本，不要求 UI 再次展示。

---

## 四、TTS 接口（Edge-TTS 方案）

> 自 v5.0 起，TTS 由后端统一提供。Android 端通过以下接口请求音频合成，使用 ExoPlayer 播放返回的音频 URL。
>
> 自 v7.0 起，流式问答推荐由后端在 `POST /api/v1/chat/text/stream` 中直接返回 `tts_segment` 事件。独立 TTS 接口仍用于非流式播放、测试、缓存预热和降级。

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
      { "text": "您好", "start_ms": 0, "end_ms": 320 },
      { "text": "欢迎", "start_ms": 340, "end_ms": 760 },
      { "text": "来到", "start_ms": 780, "end_ms": 1100 },
      { "text": "灵山胜境", "start_ms": 1120, "end_ms": 2100 }
    ]
  }
}
```

**响应字段说明**：

| 字段 | 类型 | 说明 |
|------|------|------|
| audio_url | String | 音频文件相对路径，需拼接 base URL 访问 |
| duration_ms | Long / null | 音频总时长（毫秒），后端暂时未计算时可返回 null |
| voice | String | 实际使用的发音人 |
| marks | Array | 词级时间标记，仅 `audio_with_marks` 返回 |

**marks 结构**：

| 字段 | 类型 | 说明 |
|------|------|------|
| text | String | 对应文本片段 |
| start_ms | Long | 开始时间（毫秒） |
| end_ms | Long | 结束时间（毫秒） |

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
- 用户上传图片后，将返回的 `image_url` 填入 `chat/text` 请求的 `image_url` 字段，实现图文问答。

---

### 5.2 完整响应示例（聊天问答模式）

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "message_id": "m_xxx",
    "session_id": "s_xxx",
    "reply_text": "欢迎来到黄山！这是著名的迎客松，它已有八百多年的历史了。",
    "latency_ms": 1200,
    "confidence": 0.95,
    "is_fallback": false,
    
    "avatar_action": {
      "expression": {
        "type": "excited",
        "intensity": 0.8
      },
      "gesture": {
        "type": "point_right"
      },
      "motion_queue": [
        {"type": "wave", "start_offset_ms": 0},
        {"type": "point_right", "start_offset_ms": 2000}
      ]
    },
    
    "sources": [
      {
        "document_id": "doc_huangshan_faq",
        "chunk_id": "ck_001",
        "title": "黄山景区导览手册",
        "source_path": "data/raw/scenic_docs/huangshan_faq.md",
        "score": 0.92,
        "snippet": "迎客松位于玉屏楼左侧，是黄山代表性景观之一。"
      }
    ],
    
    "metadata": {
      "intent": "introduction",
      "emotion": "joy",
      "confidence": 0.95,
      "latency_ms": 1200,
      "is_fallback": false
    },
    
    "created_at": "2026-04-28T12:00:00Z"
  }
}
```

### 5.3 响应字段说明

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| message_id | String | 否 | 消息唯一标识；若后端直接暴露 stage1 源结果，可暂不返回 |
| session_id | String | 否 | 会话 ID；若 HTTP 层已知上下文，可不重复返回 |
| reply_text | String | 是 | 回复文本（后端 TTS 合成后移动端播放） |
| latency_ms | Long | 否 | 阶段一主链路直接返回的耗时字段 |
| confidence | Float | 否 | 阶段一主链路直接返回的置信度 |
| is_fallback | Boolean | 否 | 阶段一主链路直接返回的降级标记 |
| avatar_action | Object | 否 | 数字人动作数据 |
| sources | Array | 否 | 来源引用列表 |
| metadata | Object | 否 | 增强版元数据；若存在则优先于同名顶层字段 |
| created_at | String | 否 | 创建时间 |
| route_data | Object | 否 | 路线规划数据，仅在 `mode=route` 时返回 |

兼容说明：

1. **阶段一最小可用返回**：`reply_text + sources + latency_ms + confidence + is_fallback`
2. **增强版返回**：在最小字段基础上，增加 `avatar_action`、`metadata`、`message_id`、`created_at`、`route_data`
3. Android 客户端应兼容上述两种返回形态

---

### 5.4 路线规划模式响应示例

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

### 5.5 拒答与降级策略

后端在以下场景返回降级响应，移动端应正确识别并展示：

**场景 1：检索无命中**
- `reply_text` 返回安全兜底文案
- `sources = []`
- `is_fallback = true`
- `confidence = 0.2`（或类似低置信度）

**场景 2：有检索结果但 LLM 调用异常**
- `LlmClient` 启用本地拼接回答
- `is_fallback = false`（可回答但质量下降）
- `confidence` 适度下调

**场景 3：无检索结果且 LLM 不可用**
- 保持拒答兜底输出，避免幻觉
- `is_fallback = true`

移动端处理建议：
- 当 `is_fallback = true` 时，可在 UI 上给出轻微提示（如"以下回答基于通用知识"），但不强制。
- 无论是否降级，`reply_text` 都应向用户展示。

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

### 6.6 场景设计规范

以下内容作为后端生成 `avatar_action` 时的参考标准，确保数字人表现与文本语义、时长节奏高度匹配。

#### 动作密度

| 文本时长 | 推荐动作数 |
|----------|-----------|
| 10 秒 | 5–6 个 |
| 12 秒 | 6–8 个 |
| 15 秒 | 7–9 个 |

#### 表情变化节奏

- **开场**：匹配初始语气（`welcoming` / `happy` / `excited`）
- **中段**：跟随语义转折（`thinking` / `surprised` / `concerned`）
- **结束**：回归适当状态（`happy` / `neutral`）

#### 动作语义匹配

| 文本内容 | 推荐动作 |
|----------|---------|
| "请看左/右前方" | `point_left` / `point_right` |
| "抬头看" | `point_forward`（或扩展 `look_up`） |
| "建议您" | `guide` |
| "明白/确认" | `nod` |
| "抱歉/无法" | `bow` + `shake` |
| "千年历史" | `thinking_pose` |
| "世界最高" | `surprised`（表情）+ `point_forward` |
| "祈福/神圣" | `reverent`（表情）+ `guide` |

#### 场景分类参考

| 分类 | 平均文本时长 | 平均动作数 | 平均表情数 |
|------|-------------|-----------|-----------|
| A. 入园阶段 | 13.2 秒 | 7.5 | 5.5 |
| B. 景点讲解 | 12.8 秒 | 7.7 | 5.5 |
| C. 特色体验 | 11.3 秒 | 6.7 | 5.0 |
| D. 路线推荐 | 11.8 秒 | 7.0 | 5.2 |
| E. 导航指引 | 9.5 秒 | 5.5 | 4.5 |
| F. 餐饮购物 | 9.8 秒 | 5.5 | 4.5 |
| G. 安全应急 | 11.0 秒 | 6.5 | 5.0 |
| H. 互动响应 | 6.0 秒 | 3.5 | 3.0 |
| I. 离园兜底 | 8.0 秒 | 5.2 | 4.2 |

#### 典型场景示例

**热情欢迎**
- 文本约 14 秒，包含欢迎、介绍、建议
- 动作：`wave` → `nod` → `guide` → `point_forward` ×2 → `thinking_pose` → `guide` → `nod`
- 表情：`welcoming` → `happy` → `excited`

**景点介绍**
- 文本约 13 秒，以"请抬头看！"引发震撼感
- 动作：`point_forward` → `nod` → `point_forward` → `guide` → `nod` → `thinking_pose` → `nod`
- 表情：`surprised` → `excited` → `happy`

**兜底致歉**
- 文本约 9 秒，致歉→说明→询问
- 动作：`bow` → `shake` → `bow` → `guide` → `nod` → `guide`
- 表情：`apologetic` → `concerned` → `neutral`

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

- `document_id / chunk_id / source_path / score / snippet` 来自第一阶段接口文档
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

### 8.1 完整播放流程

```
用户发送问题
      │
      ▼
  THINKING        显示思考状态
      │
      │ 后端返回响应
      ▼
┌─────────────────────────────────────┐
│ 解析响应数据                          │
│ ├── reply_text → 请求后端 TTS 合成    │
│ ├── avatar_action.expression → 表情   │
│ ├── avatar_action.gesture → 动作      │
│ └── avatar_action.motion_queue → 队列 │
└─────────────────────────────────────┘
      │
      ▼
┌─────────────────────────────────────┐
│ 后端 TTS 合成（Edge-TTS）             │
│ ├── 生成音频文件                      │
│ ├── 返回 audio_url + duration_ms      │
│ └── 可选返回 marks（词级时间戳）      │
└─────────────────────────────────────┘
      │
      ▼
┌─────────────────────────────────────┐
│ Android 播放与口型同步                │
│ ├── ExoPlayer 播放 audio_url          │
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

### 8.1.1 流式播放流程

```
用户发送问题
      │
      ▼
  THINKING        等待首个文本/音频事件
      │
      │ 后端持续返回 SSE/NDJSON 事件
      ▼
┌─────────────────────────────────────┐
│ 事件流处理                            │
│ ├── text_delta → 消息气泡增量展示      │
│ ├── tts_segment → 音频片段入队播放     │
│ ├── avatar_action → 表情/动作提前生效  │
│ ├── sources/route_data → 回填结构化数据│
│ └── done → 后端流结束                 │
└─────────────────────────────────────┘
      │
      ▼
┌─────────────────────────────────────┐
│ TTS 分段队列                          │
│ ├── 队列有片段：播放下一段             │
│ ├── 队列为空且流未结束：停顿等待       │
│ └── 队列为空且流已结束：恢复 IDLE      │
└─────────────────────────────────────┘
```

### 8.2 口型同步机制

#### 方案 A：基于 marks 的精确同步（推荐）

```
后端返回 marks：
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

#### 方案 B：字符时长估算（兜底）

当后端未返回 marks 或请求 `format=audio` 时：

```
reply_text
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

### 9.1 后端响应模型

```kotlin
@Serializable
data class ChatResponseData(
    @SerialName("message_id")
    val messageId: String? = null,
    
    @SerialName("session_id")
    val sessionId: String? = null,
    
    @SerialName("reply_text")
    val replyText: String,
    
    @SerialName("avatar_action")
    val avatarAction: AvatarAction? = null,
    
    @SerialName("sources")
    val sources: List<SourceInfo> = emptyList(),

    @SerialName("latency_ms")
    val latencyMs: Long? = null,

    @SerialName("confidence")
    val confidence: Float? = null,

    @SerialName("is_fallback")
    val isFallback: Boolean? = null,
    
    @SerialName("metadata")
    val metadata: ResponseMetadata? = null,
    
    @SerialName("created_at")
    val createdAt: String? = null
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
| 2 | 问答主链路最小返回 | `reply_text + sources + latency_ms + confidence + is_fallback` |
| 3 | `sources` 是否按第一阶段接口文档扩展？ | **是** |
| 4 | `scenic_id` 是否属于问答主请求必填？ | **是** |
| 5 | `avatar_action` 是否必返？ | 否，属于增强字段 |
| 6 | TTS 接口是否独立于 chat 接口？ | **是**，`POST /api/v1/tts/synthesize` |
| 7 | 口型同步优先用什么驱动？ | **marks（词级时间戳）**，无 marks 时字符估算兜底 |
| 8 | 交互模式是否统一为 `chat/text` 接口？ | **是**，通过 `mode` 字段区分 `chat` / `route` |
| 9 | `route_data` 是否只在 `mode=route` 时返回？ | **是**，`mode=chat` 时返回 null |
| 10 | 流式接口是否替代非流式接口？ | **否**，流式接口新增，非流式接口保留为降级路径 |
| 11 | 流式 TTS 是否由移动端自行按 delta 调用合成？ | **否**，推荐后端随流返回 `tts_segment` |

### 可选确认

| # | 问题 | 说明 |
|---|------|------|
| 12 | 是否返回 `emotion` 字段 | 可用于表情降级，推荐保留 |
| 13 | 是否返回 `intent` 字段 | 可用于动作降级，推荐保留 |
| 14 | 流式传输是否兼容 NDJSON | 推荐兼容，便于后端实现与调试 |
| 15 | 流式首段最短长度 | 建议 8–12 个中文字符或 800ms 超时强制切分 |
| 16 | `tts_segment.text` 与展示文本不一致时如何处理 | 允许不完全一致，但必须语义一致 |

---

## 十三、版本历史

| 版本 | 日期 | 变更 |
|------|------|------|
| v1.0 | 2026-04-28 | 初始版本 |
| v2.0 | 2026-04-28 | 完善数字人系统 |
| v2.1 | 2026-04-28 | 修正：TTS 由后端完成 |
| v3.0 | 2026-04-28 | **修正：TTS 由移动端完成，移除 audio 字段** |
| v4.0 | 2026-04-28 | **同步第一阶段接口文档：补充 `scenic_id`、`sources` 新结构、`latency_ms/confidence/is_fallback` 兼容说明** |
| v5.0 | 2026-04-28 | **TTS 方案切换为后端 Edge-TTS：新增 `/api/v1/tts/*` 接口，更新职责划分与播放流程，marks 驱动口型同步** |
| v5.1 | 2026-04-29 | **Edge-TTS 接口已完成 Android 端联调；修正 `duration_ms` 可空类型；确认系统 TTS 降级兜底正常** |
| v6.0 | 2026-04-29 | **交互模式重构：三种模式缩减为两种（聊天问答 + 路线规划），统一 `POST /api/v1/chat/text` 接口，通过 `mode` 字段区分；新增 `route_data` 响应结构；新增图片上传接口** |
| v7.0 | 2026-04-29 | **新增 `POST /api/v1/chat/text/stream` 流式接口摘要；引入 `text_delta`、`tts_segment`、`done` 等事件；明确分段 TTS 队列播放与非流式降级路径** |
