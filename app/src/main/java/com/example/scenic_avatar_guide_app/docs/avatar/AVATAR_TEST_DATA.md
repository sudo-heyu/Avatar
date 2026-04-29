# 数字人测试数据说明

版本：v1.0  
日期：2026-04-28  
适用范围：Android 端数字人联调、前端本地 mock、TTS/Live2D 联动测试

---

## 1. 文档目的

本文件用于统一记录数字人测试时使用的数据结构，以及模拟后端常见返回。  
**以当前仓库 `domain/model/Models.kt` 为准**，避免使用旧版 `audio_url` 或未落地字段。

当前共识：

- TTS 由后端 Edge-TTS 服务统一提供
- 非流式接口返回 `reply_text` + `avatar_action` + `metadata`，移动端额外调用 `POST /api/v1/tts/synthesize` 获取音频 URL
- 流式接口返回 `text_delta` + `tts_segment` + 结构化收口事件，移动端将分段音频排队播放
- 当前正式接口包括 `POST /api/v1/chat/text` 与新增的 `POST /api/v1/chat/text/stream`

---

## 2. 当前响应结构

统一外层格式：

```json
{
  "code": 0,
  "message": "ok",
  "data": {}
}
```

`data` 对应 `ChatResponseData`：

```json
{
  "message_id": "m_001",
  "session_id": "s_001",
  "reply_text": "您好，欢迎来到灵山胜境。",
  "avatar_action": {
    "expression": {
      "type": "welcoming",
      "intensity": 0.8,
      "transition_ms": 200
    },
    "gesture": {
      "type": "wave",
      "loop": false,
      "speed": 1.0,
      "priority": "normal"
    },
    "motion_queue": [
      {
        "type": "wave",
        "start_offset_ms": 0,
        "duration_ms": 1200
      }
    ],
    "marks": [
      {
        "position": 0.3,
        "type": "emphasis"
      }
    ]
  },
  "sources": [],
  "metadata": {
    "intent": "greeting",
    "emotion": "joy",
    "confidence": 0.98,
    "latency_ms": 620
  },
  "created_at": "2026-04-28T12:00:00Z"
}
```

---

## 3. 可用枚举范围

### expression.type

- `neutral`
- `happy`
- `thinking`
- `surprised`
- `excited`
- `concerned`
- `apologetic`
- `welcoming`

### gesture.type

- `idle`
- `nod`
- `shake`
- `wave`
- `point_left`
- `point_right`
- `point_forward`
- `bow`
- `thinking_pose`
- `guide`

### gesture.priority

- `low`
- `normal`
- `high`

### metadata.intent 常用值

- `greeting`
- `farewell`
- `introduction`
- `direction`
- `route_recommendation`
- `unknown`

---

## 4. 测试场景清单

| 场景 | 目的 | 关键字段 |
|------|------|----------|
| 欢迎问候 | 验证开场播报与挥手 | `welcoming` + `wave` |
| 景点讲解 | 验证长文本播报和引用展示 | `excited` + `point_forward` + `sources` |
| 左右指路 | 验证左右动作切换 | `point_left` / `point_right` |
| 路线推荐 | 验证动作队列 | `guide` + `motion_queue` |
| 安全提醒 | 验证提醒类表情 | `concerned` + `point_forward` |
| 无法回答 | 验证兜底与道歉动作 | `apologetic` + `bow` |
| 纯文本降级 | 验证无 `avatar_action` 时的兼容 | `avatar_action: null` |
| 仅 metadata 降级 | 验证端侧用 intent/emotion 推断动作 | 只返回 `metadata` |
| 后端业务错误 | 验证错误提示与重试 | `code != 0` |

---

## 5. 常见 Mock 返回

### 5.1 欢迎问候

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "message_id": "m_greeting_001",
    "session_id": "s_demo_001",
    "reply_text": "您好，欢迎来到灵山胜境，我是您的数字导游。请问您想先了解景点讲解、路线推荐，还是语音导览？",
    "avatar_action": {
      "expression": { "type": "welcoming", "intensity": 0.85, "transition_ms": 180 },
      "gesture": { "type": "wave", "loop": false, "speed": 1.0, "priority": "normal" }
    },
    "sources": [],
    "metadata": { "intent": "greeting", "emotion": "joy", "confidence": 0.99, "latency_ms": 480 },
    "created_at": "2026-04-28T12:00:00Z"
  }
}
```

### 5.2 景点讲解

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "message_id": "m_intro_001",
    "session_id": "s_demo_001",
    "reply_text": "灵山大佛是灵山胜境的核心地标，佛像高八十八米，整体气势宏伟，是游客最常参观的讲解点之一。",
    "avatar_action": {
      "expression": { "type": "excited", "intensity": 0.72, "transition_ms": 220 },
      "gesture": { "type": "point_forward", "loop": false, "speed": 1.0, "priority": "normal" }
    },
    "sources": [
      { "title": "灵山胜境游览指南", "content": "灵山大佛是核心地标。", "relevance_score": 0.94 }
    ],
    "metadata": { "intent": "introduction", "emotion": "joy", "confidence": 0.95, "latency_ms": 910 },
    "created_at": "2026-04-28T12:01:00Z"
  }
}
```

### 5.3 左侧指路

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "message_id": "m_direction_left_001",
    "session_id": "s_demo_001",
    "reply_text": "您左前方是祥符禅寺，沿着当前步道继续前行大约两分钟即可到达。",
    "avatar_action": {
      "expression": { "type": "neutral", "intensity": 0.6, "transition_ms": 150 },
      "gesture": { "type": "point_left", "loop": false, "speed": 1.0, "priority": "high" }
    },
    "sources": [],
    "metadata": { "intent": "direction", "emotion": "trust", "confidence": 0.93, "latency_ms": 700 },
    "created_at": "2026-04-28T12:02:00Z"
  }
}
```

### 5.4 路线推荐

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "message_id": "m_route_001",
    "session_id": "s_demo_001",
    "reply_text": "如果您有半天时间，建议先参观九龙灌浴，再前往灵山大佛，最后到梵宫完成整条核心游览路线。",
    "avatar_action": {
      "expression": { "type": "happy", "intensity": 0.75, "transition_ms": 200 },
      "gesture": { "type": "guide", "loop": false, "speed": 1.0, "priority": "normal" },
      "motion_queue": [
        { "type": "guide", "start_offset_ms": 0, "duration_ms": 1600 },
        { "type": "point_forward", "start_offset_ms": 1800, "duration_ms": 1800 }
      ]
    },
    "sources": [
      { "title": "灵山胜境路线规划", "content": "半日游以核心轴线为主。", "relevance_score": 0.91 }
    ],
    "metadata": { "intent": "route_recommendation", "emotion": "joy", "confidence": 0.92, "latency_ms": 1180 },
    "created_at": "2026-04-28T12:03:00Z"
  }
}
```

### 5.5 安全提醒

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "message_id": "m_warning_001",
    "session_id": "s_demo_001",
    "reply_text": "前方台阶较多，请您注意脚下安全，雨天路面可能会有些湿滑。",
    "avatar_action": {
      "expression": { "type": "concerned", "intensity": 0.7, "transition_ms": 180 },
      "gesture": { "type": "point_forward", "loop": false, "speed": 0.9, "priority": "high" },
      "marks": [
        { "position": 0.45, "type": "emphasis" }
      ]
    },
    "sources": [],
    "metadata": { "intent": "direction", "emotion": "concern", "confidence": 0.9, "latency_ms": 560 },
    "created_at": "2026-04-28T12:04:00Z"
  }
}
```

### 5.6 无法回答 / 兜底

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "message_id": "m_fallback_001",
    "session_id": "s_demo_001",
    "reply_text": "抱歉，我目前主要提供景区导览、景点讲解和路线推荐，暂时无法回答这个问题。",
    "avatar_action": {
      "expression": { "type": "apologetic", "intensity": 0.75, "transition_ms": 220 },
      "gesture": { "type": "bow", "loop": false, "speed": 0.9, "priority": "normal" }
    },
    "sources": [],
    "metadata": { "intent": "unknown", "emotion": "sadness", "confidence": 0.78, "latency_ms": 420 },
    "created_at": "2026-04-28T12:05:00Z"
  }
}
```

### 5.7 纯文本降级

适合验证：后端没有动作指令时，页面仍能显示文本与 TTS。

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "message_id": "m_text_only_001",
    "session_id": "s_demo_001",
    "reply_text": "祥符禅寺是灵山胜境的重要历史节点，适合慢慢参观和祈福。",
    "avatar_action": null,
    "sources": [],
    "metadata": { "intent": "introduction", "emotion": "trust", "confidence": 0.88, "latency_ms": 610 },
    "created_at": "2026-04-28T12:06:00Z"
  }
}
```

### 5.8 仅 metadata 降级

适合验证：端侧是否能根据 `intent` / `emotion` 做动作降级。

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "message_id": "m_metadata_only_001",
    "session_id": "s_demo_001",
    "reply_text": "接下来我可以继续为您介绍梵宫，或者帮您规划一条轻松路线。",
    "avatar_action": null,
    "sources": [],
    "metadata": { "intent": "greeting", "emotion": "joy", "confidence": 0.9, "latency_ms": 530 },
    "created_at": "2026-04-28T12:07:00Z"
  }
}
```

### 5.9 后端业务错误

适合验证：错误提示、重试按钮、会话失效处理。

```json
{
  "code": 1003,
  "message": "session not found",
  "data": {}
}
```

```json
{
  "code": 2002,
  "message": "LLM service unavailable",
  "data": {}
}
```

---

## 6. 推荐测试组合

### 基础联调

1. 欢迎问候
2. 景点讲解
3. 路线推荐
4. 无法回答

### 数字人专项

1. 左指路 `point_left`
2. 右指路 `point_right`
3. 引导动作队列 `motion_queue`
4. 仅 metadata 降级

### 异常与兼容

1. `avatar_action = null`
2. `sources = []`
3. `code != 0`
4. 超长 `reply_text`

---

## 7. 使用建议

- 前端本地 mock 时，优先覆盖 `reply_text`、`avatar_action`、`metadata` 三部分。
- 若只测数字人动作，不必返回 `sources`。
- 若只测 TTS 与口型，可固定 `expression=neutral`，仅替换 `reply_text`，并调用 `POST /api/v1/tts/synthesize` 获取音频。
- 若测流式播放，可 mock `text_delta` 与 `tts_segment`，其中 `tts_segment.marks` 为片段内相对时间。
- 若测端侧降级逻辑，刻意返回 `avatar_action: null`。
- 非流式 `audio_url` 由 `POST /api/v1/tts/synthesize` 返回；流式 `audio_url` 位于 `tts_segment.audio_url`。

---

## 8. 后续可扩展字段

如果后续恢复语音接口或更复杂的数字人协议，可以在不影响当前结构的前提下扩展：

- `marks.params`
- 更细粒度 `intent`
- 更丰富的 `emotion`
- 独立 `chat/voice`
- 打断/双工相关控制字段
- 更细粒度的流式控制事件
