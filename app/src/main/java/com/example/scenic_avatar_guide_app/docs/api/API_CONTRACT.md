# Android 端 API 接口文档 v11.1

版本：v11.1  
日期：2026-05-08  
适用端：**Android 移动端**

---

## 变更记录

| 版本 | 日期 | 变更内容 |
|------|------|----------|
| v11.1 | 2026-05-08 | 明确流式 TTS 当前契约：`tts_segment` 仅预告不可播放，`tts_segment_ready` 才可播放；`marks[].text` 为主字段，兼容旧 `word`；`audio_url` 为相对路径 |
| v11.0 | 2026-05-08 | 综合补全：新增会话详情/归档/删除/改标题、聊天中止、TTS 独立接口、图片上传、路线推荐独立接口；修复 PATCH /session/{id} 500 bug |
| v10.4 | 2026-05-06 | 新增 `POST /api/v1/auth/register` 和 `POST /api/v1/auth/login` 认证接口 |
| v10.3 | 2026-05-05 | 会话列表响应新增 `first_user_message` 字段 |
| v10.2 | 2026-05-04 | 新增 `POST /api/v1/chat/feedback` |
| v10.0 | 2026-05-03 | `mode=route` 主链路优先读取 SQLite 路线模板 |
| v9.0 | 2026-05-03 | TTS URL 方案，新增 `tts_segment_ready` |
| v8.0 | 2026-05-02 | 会话管理接口与历史恢复 |

---

## 一、职责划分

### 后端负责
- LLM 对话生成（RAG 知识库检索 + 上下文注入）
- 意图识别、情感分析
- 数字人动作指令生成（Combo 表情/手势/动作队列）
- TTS 语音合成（Edge-TTS）、音频文件管理、词级时间标记
- 流式问答事件输出（SSE）
- emotion 标签解析与 TTS 韵律参数映射
- 用户认证（注册/登录）

### 移动端负责
- UI 展示与交互
- 消费 SSE 流式事件，增量更新气泡
- 仅在收到 `tts_segment_ready` 后，按 `segment_index` 顺序播放音频
- 数字人渲染（Live2D）与口型动画（marks 驱动）

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
| 1002 | 参数格式错误 / 不支持的景区 ID |
| 1003 | 会话不存在 |
| 404 | 资源不存在 |
| 403 | 无权访问（会话不属于该用户） |
| 2001 | 用户名已存在 |
| 2002 | 用户名或密码错误 |
| 2003 | 服务内部错误 |
| 2004 | LLM 服务不可用 |
| 2005 | 知识库检索失败 |

---

## 三、健康检查

### `GET /api/v1/health`

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

## 四、用户认证

### 4.1 注册

**`POST /api/v1/auth/register`**

**请求体**：
```json
{
  "username": "testuser",
  "password": "password123",
  "device_id": "android_001"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| username | String | 是 | 用户名，支持中文/字母/数字/下划线，长度 2–20 |
| password | String | 是 | 密码，长度 6–64 |
| device_id | String | 否 | 设备标识，最长 64 字符 |

**成功响应**：
```json
{
  "code": 0,
  "message": "注册成功",
  "data": {
    "user_id": "u_xxxxxxxxxxxx",
    "username": "testuser",
    "created_at": "2026-05-08T05:30:00+08:00"
  }
}
```

**错误码**：

| code | 说明 |
|------|------|
| 1002 | 用户名或密码格式不符合规则 |
| 2001 | 用户名已存在 |

---

### 4.2 登录

**`POST /api/v1/auth/login`**

**请求体**：
```json
{
  "username": "testuser",
  "password": "password123",
  "device_id": "android_001"
}
```

**成功响应**：
```json
{
  "code": 0,
  "message": "登录成功",
  "data": {
    "user_id": "u_xxxxxxxxxxxx",
    "username": "testuser",
    "created_at": "2026-05-08T05:30:00+08:00"
  }
}
```

**错误码**：

| code | 说明 |
|------|------|
| 1002 | 参数格式错误 |
| 2002 | 用户名或密码错误 |

**认证流程说明**：
1. 前端将 `user_id` 存入 DataStore，作为后续所有接口的用户标识。
2. 未认证用户可使用 `guest_{UUID}` 作为临时 `user_id`，功能不受影响，但数据不长期保存。
3. 登录成功后前端调用 `clearSession()`，以新 `user_id` 创建会话。

---

## 五、会话管理

### 5.1 创建会话

**`POST /api/v1/session/create`**

**请求体**：
```json
{
  "user_id": "u_001",
  "scenic_id": "lingshan",
  "spot_id": "spot_001",
  "device_id": "android_001"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| user_id | String | 是 | 用户 ID |
| scenic_id | String | 是 | 景区 ID（须为服务端支持的 canonical ID） |
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
    "scenic_id": "lingshan",
    "spot_id": null,
    "device_id": null,
    "status": "active",
    "title": null,
    "message_count": 0,
    "context_summary": null,
    "last_message_at": null,
    "created_at": "2026-05-08T12:00:00.000000"
  }
}
```

---

### 5.2 会话列表

**`GET /api/v1/session/list`**

**Query 参数**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| user_id | String | 是 | 用户 ID |
| scenic_id | String | 否 | 景区 ID 筛选 |
| status | String | 否 | `active` / `archived` |
| page | Int | 否 | 页码，默认 1 |
| page_size | Int | 否 | 每页数量，1–50，默认 20 |

**响应**：
```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "total": 15,
    "page": 1,
    "page_size": 20,
    "sessions": [
      {
        "session_id": "s_xxx",
        "user_id": "u_001",
        "scenic_id": "lingshan",
        "spot_id": "buddha",
        "title": "灵山大佛游览咨询",
        "status": "active",
        "message_count": 12,
        "first_user_message": "灵山大佛有多高？",
        "last_message": "祝您游览愉快！",
        "last_message_at": "2026-05-08T14:30:00Z",
        "created_at": "2026-05-08T10:00:00Z"
      }
    ]
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| first_user_message | String? | 第一条用户消息（用于显示会话标题，最多 100 字符） |
| last_message | String? | 最后一条消息内容（最多 100 字符） |
| last_message_at | String? | 最后消息时间（ISO 8601） |

---

### 5.3 获取会话详情

**`GET /api/v1/session/{session_id}`**

**Path 参数**：

| 字段 | 类型 | 说明 |
|------|------|------|
| session_id | String | 会话 ID |

**Query 参数**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| user_id | String | 是 | 用户 ID（用于鉴权） |
| include_messages | Boolean | 否 | 是否返回消息列表，默认 `true` |
| message_limit | Int | 否 | 返回消息数量上限，1–200，默认 50 |

**响应**：
```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "session_id": "s_xxx",
    "user_id": "u_001",
    "scenic_id": "lingshan",
    "spot_id": null,
    "device_id": null,
    "status": "active",
    "title": null,
    "message_count": 3,
    "context_summary": null,
    "last_message_at": "2026-05-08T14:30:00Z",
    "created_at": "2026-05-08T10:00:00Z",
    "messages": [
      {
        "message_id": "m_xxx",
        "session_id": "s_xxx",
        "role": "user",
        "content": "灵山大佛有多高？",
        "created_at": "2026-05-08T10:01:00Z"
      },
      {
        "message_id": "m_yyy",
        "session_id": "s_xxx",
        "role": "assistant",
        "content": "灵山大佛高达88米。",
        "avatar_action": {},
        "sources": [],
        "emotion": "excited",
        "intent": "introduction",
        "latency_ms": 1200,
        "created_at": "2026-05-08T10:01:02Z"
      }
    ]
  }
}
```

**错误码**：

| code | 说明 |
|------|------|
| 1003 | 会话不存在 |
| 403 | 会话不属于该用户 |

---

### 5.4 归档会话

**`POST /api/v1/session/{session_id}/archive`**

**请求体**：
```json
{
  "user_id": "u_001"
}
```

**响应**：
```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "session_id": "s_xxx",
    "status": "archived"
  }
}
```

---

### 5.5 删除会话

**`DELETE /api/v1/session/{session_id}`**

**Query 参数**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| user_id | String | 是 | 用户 ID |

**响应**：
```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "session_id": "s_xxx",
    "deleted": true
  }
}
```

---

### 5.6 修改会话标题

**`PATCH /api/v1/session/{session_id}`**

**Query 参数**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| user_id | String | 是 | 用户 ID |

**请求体**：
```json
{
  "title": "新标题"
}
```

**响应**：返回完整会话对象（同 §5.3 的 `data`，不含 `messages`）。

---

## 六、聊天接口

### 6.1 非流式对话

**`POST /api/v1/chat/text`**

**请求体**：
```json
{
  "session_id": "s_xxx",
  "user_id": "u_001",
  "scenic_id": "lingshan",
  "profile_id": null,
  "question": "灵山大佛有多高？",
  "spot_id": null,
  "mode": "chat",
  "image_url": null,
  "options": {}
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| session_id | String | 是 | 会话 ID |
| user_id | String | 是 | 用户 ID |
| scenic_id | String | 是 | 景区 ID（须为支持的 canonical ID） |
| profile_id | String | 否 | 指定数字人设 ID；不传则用该景区当前激活人设 |
| question | String | 是 | 用户问题，最长 500 字 |
| spot_id | String | 否 | 当前景点 ID |
| mode | String | 否 | `chat`（默认）或 `route` |
| image_url | String | 否 | 图文问答时的图片 URL |
| options | Object | 否 | TTS 等扩展参数（见 §6.2 流式接口） |

**模式说明**：
- `mode=chat`：RAG 知识库检索 + LLM 作答；对于问候、闲聊、自我介绍等意图直接走 LLM，跳过 RAG。
- `mode=route`：优先使用后台已启用的路线模板返回结构化 `route_data`，未命中时回退通用推荐。

**成功响应**（`data` 字段）：

| 字段 | 类型 | 说明 |
|------|------|------|
| message_id | String | 消息唯一标识 |
| session_id | String | 会话 ID |
| reply_text | String | 回复文本（已去除 emotion/combo 标签） |
| avatar_action | Object? | 数字人动作（见 §十一 AvatarAction / §十二 Combo） |
| images | Array | 回复关联图片，字段由后端生成，前端仅展示（见 §14.2 images 字段） |
| sources | Array | RAG 知识引用列表（见 §十五 RAG） |
| metadata | Object | 意图、情绪、置信度等（见 §十五 §6.2） |
| latency_ms | Int | 响应延迟（毫秒） |
| is_fallback | Boolean | 是否为降级回答 |
| route_data | Object? | 路线数据，仅 `mode=route` 返回（见 §十路线响应） |
| created_at | String | 消息创建时间 |

---

### 6.2 流式对话

**`POST /api/v1/chat/text/stream`**

请求体字段与 §6.1 完全一致，另支持 `options` 中的 TTS 参数：

```json
{
  "options": {
    "voice": "zh-CN-XiaoxiaoNeural",
    "rate": "+0%",
    "volume": "+0%",
    "pitch": "+0Hz"
  }
}
```

**传输格式**：SSE（Server-Sent Events），`Content-Type: text/event-stream`。

**事件序列**（实际以服务端为准）：

```
message_start
  → avatar_action（可选，基于 combo 标签实时推送）
  → text_delta × N（纯文本增量，无 emotion 标签）
  → tts_segment × N（分段预告，含 audio_url；文件可能尚未生成）
  → tts_segment_ready × N（音频就绪，含 duration_ms / marks；此时才可播放）
  → tts_audio_error（可选，与分段事件交替出现，表示某分段 TTS 合成失败）
  → avatar_action（收尾动作，可选）
  → images（回复关联图片，mode=chat，可为空）
  → sources（RAG 引用，mode=chat）
  → route_data（可选，mode=route）
  → metadata
  → done
```

> 异常中止时发送 `error` 或 `aborted` 事件。

**各事件格式**：

| type | 说明 | 移动端动作 |
|------|------|------------|
| `message_start` | 回答开始 | 绑定 `message_id` / `session_id` |
| `avatar_action` | 表情 / 手势 / Combo | 更新数字人 |
| `text_delta` | 纯文本增量 | 追加消息气泡 |
| `tts_segment` | 分段预告 | 仅记录 `segment_id` / `segment_index`，不下载、不播放 |
| `tts_segment_ready` | 音频就绪 | 拼接服务器 base URL 后用 ExoPlayer 播放 `audio_url` |
| `tts_audio_error` | 分段合成失败 | 跳过该分段，继续后续分段 |
| `images` | 回复关联图片 | 回填助手消息图片卡片；标题、描述、图注、替代文本均来自后端 |
| `sources` | RAG 引用 | 回填消息来源 |
| `route_data` | 路线结构化数据 | `mode=route` 时使用 |
| `metadata` | 意图/情绪/耗时等 | 统计与 UI |
| `done` | 流结束 | 关闭 loading |
| `aborted` | 流被中止 | 停止 loading，不提示错误 |
| `error` | 异常 | 提示用户 |

**SSE 示例片段**：

```text
event: message_start
data: {"type":"message_start","message_id":"m_xxx","session_id":"s_xxx","created_at":"..."}

event: text_delta
data: {"type":"text_delta","delta":"欢迎来到灵山胜境，"}

event: tts_segment
data: {"type":"tts_segment","segment_id":"m_xxx_000","segment_index":0,"text":"欢迎来到灵山胜境！","audio_url":"/api/v1/tts/file/tts_m_xxx_000.mp3","duration_ms":null,"voice":"zh-CN-XiaoxiaoNeural","rate":"+0%","volume":"+0%","pitch":"+0Hz","emotion":"neutral","marks":[]}

event: tts_segment_ready
data: {"type":"tts_segment_ready","segment_id":"m_xxx_000","segment_index":0,"audio_url":"/api/v1/tts/file/tts_m_xxx_000.mp3","file_name":"tts_m_xxx_000.mp3","duration_ms":2400,"marks":[{"text":"欢","start_ms":0,"end_ms":256,"phonemes":["h","u","an"]},{"text":"迎","start_ms":256,"end_ms":513,"phonemes":["i","ng"]}],"emotion":"neutral"}

event: images
data: {"type":"images","data":[{"image_id":"img_xxx","title":"北区红楼","description":"这张图片展示北区红楼的主体建筑。","alt_text":"北区红楼俯瞰","caption":"北区红楼主体建筑","url":"/static/knowledge/1911museum/images/%E5%8C%97%E5%8C%BA%E7%BA%A2%E6%A5%BC.jpg","public_path":"/static/knowledge/1911museum/images/%E5%8C%97%E5%8C%BA%E7%BA%A2%E6%A5%BC.jpg","width":800,"height":600}]}

event: sources
data: {"type":"sources","data":[]}

event: metadata
data: {"type":"metadata","data":{"intent":"greeting","emotion":"welcoming","confidence":0.9,"is_fallback":false,"latency_ms":800,"combo":"C3"}}

event: done
data: {"type":"done","message_id":"m_xxx","session_id":"s_xxx","full_text":"可选，完整助手回复文本"}
```

`done.full_text` 为可选字段。若存在，客户端优先使用它作为最终 Markdown 渲染源；若不存在，客户端使用已按顺序累积的 `text_delta` 内容。

---

### 6.3 中止流式

**`POST /api/v1/chat/abort`**

主动中止正在进行的流式请求，服务端会向对应流发送 `aborted` 事件。

**请求体**：
```json
{
  "session_id": "s_xxx",
  "message_id": "m_xxx",
  "reason": "client_abort"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| session_id | String | 否 | 目标会话 ID |
| message_id | String | 否 | 目标消息 ID（精确匹配） |
| reason | String | 否 | 中止原因，最长 100 字符，默认 `client_abort` |

**响应**：
```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "aborted": true,
    "aborted_message_ids": ["m_xxx"],
    "reason": "client_abort"
  }
}
```

> `aborted: false` 表示未找到匹配的活跃流（已自然结束或 ID 有误）。

---

### 6.4 满意度反馈

**`POST /api/v1/chat/feedback`**

> 管理后台大盘的满意度统计**完全依赖本接口写入**，App 需在用户打分/投诉时调用。

**请求体**：
```json
{
  "scenic_id": "lingshan",
  "session_id": "s_xxx",
  "user_id": "u_001",
  "message_id": "m_yyy",
  "rating": 5,
  "is_complaint": false,
  "comment": ""
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| scenic_id | String | 是 | 景区 ID |
| rating | Int | 是 | 满意度 **1–5**，5 为最满意 |
| session_id | String | 否 | 关联会话 ID |
| user_id | String | 否 | 用户 ID |
| message_id | String | 否 | 关联助手消息 ID |
| is_complaint | Boolean | 否 | 是否为投诉，默认 `false` |
| comment | String | 否 | 文字反馈，最长 500 字 |

**成功响应**（`data`）：
```json
{
  "feedback_id": "fb_xxxxxxxxxxxx",
  "scenic_id": "lingshan",
  "session_id": "s_xxx",
  "user_id": "u_001",
  "message_id": "m_yyy",
  "rating": 5,
  "is_complaint": false,
  "comment": null,
  "created_at": "2026-05-08T12:00:00+08:00"
}
```

**错误码**：

| code | 说明 |
|------|------|
| 1002 | `rating` 不在 1–5 范围内或参数格式错误 |
| 404 | `session_id` 已传但会话不存在或与景区不匹配 |

---

## 七、路线推荐（独立接口）

> 该接口直接返回路线推荐结果，无需先建会话。与聊天接口的 `mode=route` 逻辑一致，优先读取已启用的路线模板。

### 7.1 路线推荐（GET）

**`GET /api/v1/route/recommend`**

**Query 参数**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| scenic_id | String | 是 | 景区 ID |
| duration_min | Int | 否 | 期望游览时长（分钟），30–720 |
| current_spot | String | 否 | 当前所在景点 |
| interest_tags | String | 否 | 兴趣标签，逗号分隔，如 `亲子,文化` |

### 7.2 路线推荐（POST）

**`POST /api/v1/route/recommend`**

**请求体**：
```json
{
  "scenic_id": "lingshan",
  "duration_min": 240,
  "interest_tags": ["亲子", "文化"],
  "current_spot": "游客中心",
  "question": "我们一家三口想玩半天，有什么推荐路线？"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| scenic_id | String | 是 | 景区 ID |
| duration_min | Int | 否 | 期望游览时长（分钟），30–720 |
| interest_tags | Array | 否 | 兴趣标签列表 |
| current_spot | String | 否 | 当前所在景点 |
| question | String | 否 | 原始用户问题（用于 fallback 推理） |

**成功响应**（`data` 为 `route_data` 结构，见 §十路线规划响应）。

---

## 八、TTS 接口

### 8.1 获取语音列表

**`GET /api/v1/tts/voices`**

返回 Edge-TTS 支持的所有语音。

**响应**（`data` 为数组）：
```json
[
  {
    "id": "zh-CN-XiaoxiaoNeural",
    "locale": "zh-CN",
    "gender": "Female",
    "friendly_name": "Microsoft Xiaoxiao Online (Natural) - Chinese (Mainland)"
  },
  {
    "id": "zh-CN-XiaoyiNeural",
    "locale": "zh-CN",
    "gender": "Female",
    "friendly_name": "Microsoft Xiaoyi Online (Natural) - Chinese (Mainland)"
  }
]
```

> 流式接口当前仅支持 `zh-CN-XiaoxiaoNeural` 和 `zh-CN-XiaoyiNeural`，其他语音可用于独立合成接口。

---

### 8.2 合成语音

**`POST /api/v1/tts/synthesize`**

**请求体**：
```json
{
  "text": "欢迎来到灵山胜境！",
  "voice": "zh-CN-XiaoxiaoNeural"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| text | String | 是 | 合成文本，最长 500 字，不可为纯标点/空白 |
| voice | String | 是 | 语音 ID（见 §8.1） |

**成功响应**（`data`）：
```json
{
  "file_name": "tts_m_xxx_000.mp3",
  "audio_url": "/api/v1/tts/file/tts_m_xxx_000.mp3",
  "duration_ms": 2400,
  "marks": [
    {"text": "欢", "start_ms": 0, "end_ms": 256, "phonemes": ["h", "u", "an"]},
    {"text": "迎", "start_ms": 256, "end_ms": 513, "phonemes": ["i", "ng"]}
  ]
}
```

**错误响应**：

| HTTP | 说明 |
|------|------|
| 400 | 文本不含可朗读内容（仅标点或空白） |
| 422 | 文本超过 500 字（Pydantic 校验） |
| 502 | TTS 合成失败（上游服务异常） |

---

### 8.3 获取音频文件

**`GET /api/v1/tts/file/{file_name}`**

返回 MP3 音频文件内容（`Content-Type: audio/mpeg`）。

**Path 参数**：

| 字段 | 类型 | 说明 |
|------|------|------|
| file_name | String | 音频文件名，如 `tts_m_xxx_000.mp3` |

**响应**：直接返回二进制音频内容（`FileResponse`），无 JSON 外层。

**错误**：文件不存在时返回 HTTP 404，body 为 `{"detail":"audio file not found"}`。

---

## 九、文件上传

### 9.1 上传图片

**`POST /api/v1/upload/image`**

**Content-Type**：`multipart/form-data`

**表单字段**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| image | File | 是 | 图片文件（MIME 类型须以 `image/` 开头），最大 5MB |

**成功响应**：
```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "image_url": "/uploads/img_xxxxxxxx.jpg"
  }
}
```

上传后的 `image_url` 可直接传入聊天接口的 `image_url` 字段实现图文问答。

**错误响应**：

| HTTP / code | 说明 |
|------|------|
| 400 | 文件 MIME 类型不是图片 |
| 400 | 文件超过 5MB |

---

## 十、路线规划响应详解

`mode=route` 时，`data.route_data` 结构如下：

```json
{
  "route_id": "rt_001",
  "title": "亲子家庭路线",
  "scenic_id": "lingshan",
  "interest_tags": ["亲子", "家庭"],
  "current_spot": "游客中心",
  "total_duration_min": 240,
  "total_distance_m": 3200,
  "reason": "适合亲子游客轻松游览。",
  "highlights": ["九龙灌浴", "大佛打卡"],
  "tips": ["提前准备饮水"],
  "spots": [
    {
      "name": "游客中心",
      "lat": 31.4875,
      "lng": 120.1234,
      "order": 1,
      "stay_min": 30,
      "description": "推荐停留点。"
    }
  ],
  "polyline": [
    {"lat": 31.4875, "lng": 120.1234},
    {"lat": 31.4880, "lng": 120.1240}
  ]
}
```

**字段说明**：

| 字段 | 类型 | 说明 |
|------|------|------|
| route_id | String? | 路线模板 ID；通用推荐时可为空 |
| title | String | 路线名称 |
| scenic_id | String | 景区 ID |
| interest_tags | Array | 命中的兴趣标签 |
| current_spot | String? | 当前景点 |
| total_duration_min | Int | 预计总时长（分钟） |
| total_distance_m | Int? | 预计总距离（米） |
| reason | String | 推荐理由 |
| highlights | Array | 路线亮点 |
| tips | Array | 游玩提醒 |
| spots | Array | 景点节点列表 |
| polyline | Array | 地图路径坐标 |

**spots 节点结构**：

| 字段 | 类型 | 说明 |
|------|------|------|
| name | String | 景点名称 |
| lat | Double | 纬度 |
| lng | Double | 经度 |
| order | Int | 游览顺序 |
| stay_min | Int | 建议停留时长（分钟） |
| description | String | 景点简介 |
| image_url | String? | 景点图片（可选） |

---

## 十一、TTS 流式事件详解

### 11.1 tts_segment（分段预告，不可播放）

TTS 开始生成时发送，告知前端即将有新分段。此时音频文件可能尚未写盘完成，移动端不能请求或播放该事件中的 `audio_url`，否则可能得到 404。

```json
{
  "type": "tts_segment",
  "segment_id": "m_xxx_000",
  "segment_index": 0,
  "text": "欢迎来到灵山胜境！",
  "audio_url": "/api/v1/tts/file/tts_m_xxx_000.mp3",
  "duration_ms": null,
  "voice": "zh-CN-XiaoxiaoNeural",
  "rate": "+0%",
  "volume": "+0%",
  "pitch": "+0Hz",
  "emotion": "neutral",
  "marks": []
}
```

### 11.2 tts_segment_ready（音频就绪）

音频文件已生成完成，包含完整元数据。移动端只在收到该事件后播放分段音频：

```json
{
  "type": "tts_segment_ready",
  "segment_id": "m_xxx_000",
  "segment_index": 0,
  "audio_url": "/api/v1/tts/file/tts_m_xxx_000.mp3",
  "file_name": "tts_m_xxx_000.mp3",
  "duration_ms": 2400,
  "marks": [
    {"text": "欢", "start_ms": 0, "end_ms": 256, "phonemes": ["h", "u", "an"]},
    {"text": "迎", "start_ms": 256, "end_ms": 513, "phonemes": ["i", "ng"]},
    {"text": "，", "start_ms": 513, "end_ms": 650, "phonemes": ["SIL"]}
  ],
  "emotion": "neutral"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| segment_id | String | 分段唯一标识 |
| segment_index | Int | 分段序号（从 0 开始，按顺序播放） |
| audio_url | String | 音频文件相对 URL，完整 URL 为 `baseUrl + audio_url` |
| file_name | String | 音频文件名 |
| duration_ms | Int | 音频时长（毫秒） |
| marks | Array | 字/词级时间标记（用于口型同步）；主字段为 `text`，移动端兼容旧字段 `word` |
| emotion | String | 情绪标签 |

### 11.3 tts_audio_error（分段合成失败）

某个分段合成失败时发送。移动端应跳过该 `segment_index`，继续等待或播放后续分段：

```json
{
  "type": "tts_audio_error",
  "segment_id": "m_xxx_001",
  "segment_index": 1,
  "message": "tts synthesis failed"
}
```

---

### 11.4 marks 字段

```json
{
  "text": "欢",
  "start_ms": 0,
  "end_ms": 256,
  "phonemes": ["h", "u", "an"]
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| text | String | 当前 mark 对应文本，当前推荐字段 |
| word | String | 旧字段名，移动端兼容读取 |
| start_ms | Int | 当前分段音频内起始时间 |
| end_ms | Int | 当前分段音频内结束时间 |
| phonemes | String[] | 可选音素数组；`["SIL"]` 表示静音 |

---

## 十二、Emotion 标签策略

### 12.1 LLM 输出示例

LLM 只对需要强调的词语标注 emotion：

```
欢迎来到灵山胜境！<emotion="excited">非常</emotion>壮观！
这里是太湖之滨最著名的佛教文化景区。
```

### 12.2 前端收到的文本

前端通过 `text_delta` 收到**纯文本**，不含任何标签：

```
欢迎来到灵山胜境！非常壮观！
这里是太湖之滨最著名的佛教文化景区。
```

### 12.3 Emotion → TTS 韵律映射

| emotion | rate | volume | pitch | 用途 |
|---------|------|--------|-------|------|
| excited | +15% | +10% | +0Hz | 强调词、惊叹 |
| surprised | +10% | +10% | +0Hz | 意外、惊叹 |
| welcoming | +10% | +5% | +0Hz | 欢迎 |
| happy | +5% | +0% | +0Hz | 开心 |
| grateful | +0% | +5% | +0Hz | 感谢 |
| playful | +10% | +5% | +0Hz | 活泼 |
| focused | +0% | +0% | +0Hz | 专注介绍 |
| concerned | -5% | -5% | +0Hz | 关心、提醒 |
| thinking | -5% | +0% | +0Hz | 思考 |
| apologetic | -10% | -5% | +0Hz | 道歉 |
| reverent | -10% | -10% | +0Hz | 庄重 |
| neutral | +0% | +0% | +0Hz | 默认（大部分文本） |

> `pitch` 统一为 `+0Hz`，避免同一人声因音调差异听起来像换了声音。

### 12.4 无 Emotion 标签时的处理

当 LLM 输出的文本段落不包含 emotion 标签时，TTS 韵律使用 **neutral**（+0%/+0%/+0Hz）。服务端不对无标签文本做意图推断。

---

## 十三、数字人 Combo 系统

### 13.1 概述

LLM 可在回复中插入 `<combo="C1"/>` 格式的短促情绪反馈标签。服务端解析后通过 `avatar_action` 事件返回结构化 Combo 数据，移动端据此驱动数字人执行表情+动作序列。

### 13.2 Combo 数据结构

每个 Combo 包含文本、表情、手势和动作队列：

```json
{
  "text": "嘻嘻",
  "expression": {
    "type": "happy",
    "intensity": 0.7,
    "transition_ms": 200
  },
  "gesture": {
    "type": "nod"
  },
  "motion_queue": [
    {"type": "nod", "start_offset_ms": 0, "duration_ms": 600},
    {"type": "nod", "start_offset_ms": 800, "duration_ms": 600}
  ]
}
```

### 13.3 预设 Combo 列表

| Combo | 文本 | 表情 | 手势 | 典型场景 |
|-------|------|------|------|----------|
| C1 | 嘻嘻 | happy 0.7 | nod | 轻松互动 |
| C2 | 太棒了 | excited 0.88 | nod | 表示赞扬 |
| C3 | 您好呀 | welcoming 0.8 | wave | 欢迎迎接 |
| C4 | 这边请 | welcoming 0.8 | guide | 引导方向 |
| C5 | 往左走 | neutral 0.5 | point_left | 左侧指引 |
| C6 | 往右走 | neutral 0.5 | point_right | 右侧指引 |
| C7 | 往前走 | neutral 0.5 | point_forward | 前方指引 |
| C8 | 哇！ | surprised 0.9 | surprised_pose | 表达惊喜 |
| C9 | 真厉害！ | surprised 0.9 | clap | 赞赏肯定 |
| C10 | 请跟我来 | reverent 0.85 | guide | 引导讲解 |
| C11 | 嗯嗯 | happy 0.7 | nod | 肯定认可 |
| C12 | 嗯～ | thinking 0.7 | thinking_pose | 思考回应 |
| C13 | 不好意思 | apologetic 0.8 | bow | 道歉 |
| C14 | 请注意 | concerned 0.7 | concern_pose | 安全提醒 |
| C15 | 感谢您 | grateful 0.8 | bow | 表达感谢 |
| C16 | 再见啦 | welcoming 0.8 | wave | 送别 |
| C17 | 哈哈 | playful 0.8 | nod | 轻松幽默 |
| C18 | 了解！ | focused 0.6 | nod | 理解确认 |

---

## 十四、RAG 知识引用

### 14.1 sources 字段

当 `mode=chat` 时，非流式 `data.sources` 和流式 `type: "sources"` 事件的 `data` 数组结构：

```json
[
  {
    "document_id": "doc_xxx",
    "chunk_id": "ck_001",
    "title": "灵山胜境景区导览手册",
    "source_path": "storage/knowledge/lingshan/doc_xxx.md",
    "score": 0.92,
    "snippet": "灵山大佛高达88米，是全球最高的青铜露天释迦牟尼立像。"
  }
]
```

| 字段 | 类型 | 说明 |
|------|------|------|
| document_id | String | 文档标识 |
| chunk_id | String | 切片标识 |
| title | String | 展示标题 |
| source_path | String | 存储路径（便于排错） |
| score | Float | 相似度分数 |
| snippet | String | 命中摘要片段 |

> `sources` 为空数组表示无知识库命中（fallback 场景）。

### 14.2 images 字段

当 `mode=chat` 时，非流式 `data.images` 和流式 `type: "images"` 事件的 `data` 数组结构一致。`images` 可为空数组；为空时移动端保持纯文本气泡。

| 字段 | 类型 | 说明 |
|------|------|------|
| image_id | String | 图片唯一标识，用于去重/缓存 |
| title | String | 图片标题，移动端直接展示 |
| description | String | 图片描述，移动端直接展示 |
| alt_text | String | 替代文本；移动端在图片加载失败时展示 |
| caption | String | 图注；当 `description` 为空时移动端可作为副标题展示 |
| url | String | 图片访问路径；相对路径需由移动端拼接当前服务器 base URL |
| public_path | String | 公开路径，通常与 `url` 等价，作为冗余兜底 |
| document_id | String | 所属知识文档，移动端可忽略 |
| chunk_id | String | 所属知识切片，移动端可忽略 |
| source_path | String | 服务端存储路径，仅调试使用，不作为图片地址 |
| width | Int? | 原始宽度，可用于布局 |
| height | Int? | 原始高度，可用于布局 |

移动端展示约定：

- 图片、标题、描述、图注、替代文本均来自后端 `images` 字段；前端不根据图片内容生成介绍。
- 展示优先级：标题使用 `title`；副标题使用 `description`，为空时使用 `caption`；加载失败占位使用 `alt_text`，再退回 `title`。
- TTS 只播报 `reply_text` / `text_delta`，不得朗读图片标题、描述、路径或调试字段。
- `url` / `public_path` 可能是 `/static/...` 相对路径，移动端需拼接当前后端地址后交给图片库加载。

### 14.3 metadata 事件

```json
{
  "type": "metadata",
  "data": {
    "intent": "introduction",
    "emotion": "excited",
    "confidence": 0.85,
    "is_fallback": false,
    "latency_ms": 800,
    "combo": "C3"
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| intent | String | 识别出的用户意图 |
| emotion | String | 本次回复的情绪 |
| confidence | Float | 知识库检索置信度 |
| is_fallback | Boolean | 是否为降级回答 |
| latency_ms | Int | 总延迟（毫秒） |
| combo | String? | 触发的 Combo 键名 |

---

## 十五、AvatarAction 结构

```kotlin
@Serializable
data class AvatarAction(
    @SerialName("text")     val text: String? = null,
    @SerialName("expression") val expression: AvatarExpressionData? = null,
    @SerialName("gesture")  val gesture: AvatarGestureData? = null,
    @SerialName("motion_queue") val motionQueue: List<MotionQueueItem>? = null
)

@Serializable
data class AvatarExpressionData(
    @SerialName("type")         val type: String = "neutral",
    @SerialName("intensity")    val intensity: Float = 0.7f,
    @SerialName("transition_ms") val transitionMs: Long = 200
)

@Serializable
data class AvatarGestureData(
    @SerialName("type") val type: String = "idle"
)

@Serializable
data class MotionQueueItem(
    @SerialName("type")             val type: String,
    @SerialName("start_offset_ms")  val startOffsetMs: Long,
    @SerialName("duration_ms")      val durationMs: Long = 0
)
```

**表情类型**：

| 类型 | 说明 |
|------|------|
| `neutral` | 中性默认 |
| `happy` | 开心 |
| `excited` | 兴奋 |
| `welcoming` | 欢迎 |
| `surprised` | 惊讶 |
| `thinking` | 思考 |
| `concerned` | 关切 |
| `apologetic` | 抱歉 |
| `grateful` | 感激 |
| `reverent` | 庄重 |
| `playful` | 活泼 |
| `focused` | 专注 |

---

## 十六、分段约束

1. **分段规则**：按标点切分，max_chars=60，min_chars=15
2. **emotion 边界**：emotion 标签会强制分段
3. **顺序播放**：前端按 `segment_index` 顺序播放
4. **纯文本**：`text_delta` 不包含任何标签（emotion/combo 均已去除）

---

## 十七、移动端播放逻辑

```kotlin
val pendingSegments = mutableMapOf<String, TtsSegmentEvent>()
val readyQueue = PriorityQueue<TtsSegmentReadyEvent>(compareBy { it.segmentIndex })
var isPlaying = false
var nextExpectedIndex = 0

when (event.type) {
    "tts_segment" -> {
        // 预告事件：只记录，不下载、不播放。
        pendingSegments[event.segmentId] = event
    }
    "tts_segment_ready" -> {
        readyQueue.add(event)
        if (!isPlaying) playNextInOrder()
    }
    "tts_audio_error" -> {
        nextExpectedIndex++
        if (!isPlaying) playNextInOrder()
    }
}

fun playNextInOrder() {
    val segment = readyQueue.peek() ?: return
    if (segment.segmentIndex != nextExpectedIndex) return
    readyQueue.poll()
    isPlaying = true

    val fullUrl = baseUrl.removeSuffix("/") + "/" + segment.audioUrl.removePrefix("/")
    exoPlayer.setMediaItem(MediaItem.fromUri(fullUrl))
    exoPlayer.prepare()
    exoPlayer.play()
    exoPlayer.addListener(object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED) {
                nextExpectedIndex++
                isPlaying = false
                playNextInOrder()
            }
        }
    })
}
```

---

## 十八、FAQ

| # | 问题 | 答案 |
|---|------|------|
| 1 | TTS 是否由移动端完成？ | **否**（v5.0 起改为后端 Edge-TTS） |
| 2 | 问答主链路最小返回 | `reply_text + sources + latency_ms + confidence + is_fallback` |
| 3 | `scenic_id` 是否必填？ | **是**，须为服务端支持的 canonical ID（别名会被自动规范） |
| 4 | `avatar_action` 是否必返？ | 否，为增强字段；无 Combo 触发时返回 `null` |
| 5 | 流式与非流式接口是否对齐？ | **是**，逻辑对齐，非流式为降级路径 |
| 6 | `route_data` 何时返回？ | 仅 `mode=route` 时返回 |
| 7 | 满意度统计依赖哪个接口？ | `POST /api/v1/chat/feedback`，App 需主动调用 |
| 8 | 口型同步优先用什么驱动？ | **marks（词级时间戳）**，无 marks 时字符估算兜底 |
| 9 | 闲聊/问候是否走 RAG？ | **否**，greeting/farewell/self_identity/casual_chat 意图直接走 LLM |
| 10 | 无 emotion 标签时 TTS 用什么参数？ | neutral（+0%/+0%/+0Hz） |
| 11 | Combo 和 AvatarAction 是什么关系？ | Combo 是预设的完整 AvatarAction，包含 text/expression/gesture/motion_queue |
| 12 | 图片上传后如何用于问答？ | 上传后获得 `image_url`，传入聊天接口的 `image_url` 字段 |
| 13 | `profile_id` 不传时用哪个人设？ | 该景区当前 `is_active=true` 的人设 |
| 14 | 路线推荐有几种方式？ | 两种：独立接口（§七）；聊天接口 `mode=route`（§六），逻辑相同 |

---

## 十九、版本历史

| 版本 | 日期 | 变更 |
|------|------|------|
| v1.0 | 2026-04-28 | 初始版本 |
| v2.0 | 2026-04-28 | 完善数字人系统 |
| v5.0 | 2026-04-28 | TTS 方案切换为后端 Edge-TTS |
| v6.0 | 2026-04-29 | 交互模式重构：三种缩减为两种，新增 route_data 和图片上传接口 |
| v7.0 | 2026-04-29 | 新增 SSE 流式接口：text_delta / tts_segment / done 等事件 |
| v8.0 | 2026-05-02 | 新增会话管理接口（list/get/archive/delete/patch） |
| v9.0 | 2026-05-03 | 聊天接口新增 `profile_id` 字段，支持指定数字人设 |
| v10.0 | 2026-05-03 | `mode=route` 优先读取 SQLite 路线模板 |
| v10.2 | 2026-05-04 | 新增 `POST /api/v1/chat/feedback` |
| v10.4 | 2026-05-06 | 新增用户认证接口（注册/登录） |
| v11.0 | 2026-05-08 | 综合补全：会话详情/归档/删除/改标题、聊天中止、TTS 独立接口、图片上传、路线推荐独立接口；pitch 统一为 +0Hz |
