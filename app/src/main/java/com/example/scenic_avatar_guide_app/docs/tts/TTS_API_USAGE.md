# Edge TTS API 使用指南

## 概述

后端已完整实现 Edge TTS 在线语音生成功能，前端可通过以下接口实现文字转语音。

自流式问答方案起，TTS 有两种使用方式：

1. **非流式 / 降级路径**：移动端继续调用 `POST /api/v1/tts/synthesize`，用完整文本生成完整音频。
2. **流式问答路径**：后端在 `POST /api/v1/chat/text/stream` 的事件流中返回 `tts_segment` 预告和 `tts_segment_ready` 就绪事件，移动端只播放 ready 分段。

## 基础信息

- **Base URL**: `http://server_ip:8000/api/v1/tts`
- **支持格式**: MP3 (音频文件)
- **最大文本长度**: 500 字符
- **缓存机制**: 相同文本+音色组合自动复用已生成的音频
- **流式分段**: 长回答按情绪边界优先、句子边界次之生成多个音频片段，先发 `tts_segment` 预告，文件生成后再发 `tts_segment_ready`

---

## API 接口

### 1. 获取语音列表

**请求**

```http
GET /api/v1/tts/voices
```

**响应示例**

```json
{
  "code": 0,
  "message": "ok",
  "data": [
    {
      "id": "zh-CN-XiaoxiaoNeural",
      "locale": "zh-CN",
      "gender": "Female",
      "friendly_name": "晓晓 (Natural) - 中文 (中国)"
    }
  ]
}
```

---

### 2. 合成语音

**请求**

```http
POST /api/v1/tts/synthesize
Content-Type: application/json

{
  "text": "你好，欢迎来到景灵智导。",
  "voice": "zh-CN-XiaoxiaoNeural",
  "rate": "+0%",
  "volume": "+0%",
  "pitch": "+0Hz",
  "format": "audio_with_marks"
}
```

**请求参数**

| 参数 | 类型 | 必需 | 说明 | 示例 |
|------|------|------|------|------|
| text | string | ✓ | 待合成的文本 | "你好，欢迎。" |
| voice | string | ✗ | 语音音色 ID | "zh-CN-XiaoxiaoNeural" |
| rate | string | ✗ | 语速调整 | "+0%", "+10%", "-20%" |
| volume | string | ✗ | 音量调整 | "+0%", "+10%", "-20%" |
| pitch | string | ✗ | 音调调整 | 固定 `"+0Hz"`（系统统一，修改会导致同一人声听起来像多人） |
| format | string | ✗ | 返回格式 | "audio" (默认), "audio_with_marks" |

**响应示例**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "audio_url": "/api/v1/tts/file/tts_dd73dd073dca85db.mp3",
    "file_name": "tts_dd73dd073dca85db.mp3",
    "voice": "zh-CN-XiaoxiaoNeural",
    "duration_ms": 2052,
    "marks": [
      { "text": "欢", "start_ms": 0,   "end_ms": 256, "phonemes": ["h", "u", "an"] },
      { "text": "迎", "start_ms": 256, "end_ms": 513, "phonemes": ["i", "ng"] }
    ]
  }
}
```

**返回字段**

| 字段 | 说明 |
|------|------|
| audio_url | 音频文件相对 URL，配合 server_ip 组成完整路径 |
| file_name | 生成的音频文件名 |
| voice | 实际使用的语音音色 |
| duration_ms | 音频总时长（毫秒） |
| marks | 字级时间戳+音素数组，用于 Android 口型同步 |

---

### 3. 下载音频文件

**请求**

```http
GET /api/v1/tts/file/{file_name}
```

**示例**

```
GET /api/v1/tts/file/tts_dd73dd073dca85db.mp3
```

**响应**

- Content-Type: `audio/mpeg`
- Body: MP3 音频二进制数据

---

## 流式问答中的 TTS 分段

流式问答不建议移动端拿到 `text_delta` 后再自行调用 `/tts/synthesize`，否则会增加额外 HTTP 往返并导致首句播放不稳定。后端在生成文本时同步维护朗读缓冲区，按**情绪边界优先、句子边界次之**的策略分段，并通过以下事件下发：

### 事件顺序

```
tts_segment (段落元信息预告)
    ↓
tts_segment_ready (音频已生成，可播放)

tts_audio_error (可选，与分段事件交替出现；某个分段合成失败时跳过该段)
```

### `tts_segment` 事件

```json
{
  "type": "tts_segment",
  "segment_id": "seg_001",
  "segment_index": 0,
  "text": "欢迎来到灵山胜境！",
  "audio_url": "/api/v1/tts/file/seg_001.mp3",
  "duration_ms": null,
  "voice": "zh-CN-XiaoxiaoNeural",
  "rate": "+0%",
  "volume": "+0%",
  "pitch": "+0Hz",
  "emotion": "neutral",
  "marks": []
}
```

**字段说明**：

| 字段 | 说明 |
|------|------|
| segment_id | 分段音频 ID |
| segment_index | 分段序号，从 0 开始，客户端应按此排序播放 |
| text | 该片段对应文本 |
| audio_url | 音频 URL 预告（此时文件可能还未生成完成，不可播放） |
| duration_ms | 本阶段通常为 null |
| emotion | LLM 标注的情绪，如 `excited`、`thinking`、`neutral` |

> `tts_segment` 只是预告。Android 收到后只记录 `segment_id` / `segment_index`，不能下载或播放 `audio_url`，否则文件未生成时会返回 404。

### `tts_segment_ready` 事件

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
    { "text": "迎", "start_ms": 210, "end_ms": 420, "phonemes": ["i", "ng"] },
    { "text": "，", "start_ms": 420, "end_ms": 560, "phonemes": ["SIL"] }
  ],
  "emotion": "neutral"
}
```

**播放规则**：
- 只在收到 `tts_segment_ready` 后播放，忽略 `tts_segment.audio_url`
- 按 `segment_index` 顺序播放
- `audio_url` 是相对路径，需先拼当前服务器地址再交给 ExoPlayer
- 用 `marks` 驱动口型同步
- `marks[].text` 是当前推荐字段，Android 兼容旧字段 `marks[].word`

### `tts_audio_error` 事件

```json
{
  "type": "tts_audio_error",
  "segment_id": "seg_002",
  "segment_index": 1,
  "message": "tts synthesis failed"
}
```

移动端应跳过该分段，推进 `nextExpectedIndex`，继续等待或播放后续 `tts_segment_ready`。

### 分段规则

1. **情绪边界优先**：LLM 输出 `<emotion="xxx">` 标签时，情绪变化处必须截断
2. **句子边界次之**：同一情绪内遇到 `，。！？；：` 切分
3. **参数**：max_chars=60，min_chars=15
4. **语义保护**：英文、数字、景点名、专有名词尽量不从中间切断

### 与独立 TTS 接口的关系

| 场景 | 推荐方式 |
|------|----------|
| 普通非流式回答 | 调用 `/api/v1/tts/synthesize` |
| 流式回答 | `tts_segment` 只记录；消费 `tts_segment_ready` 播放 |
| TTS 功能测试 | 调用 `/api/v1/tts/synthesize` |
| 固定文案缓存预热 | 调用 `/api/v1/tts/synthesize` |
| 流式接口失败降级 | 可回退非流式回答，再调用 `/api/v1/tts/synthesize` |

详细流式方案见：`docs/avatar/streaming/STREAMING_REFACTOR_PLAN.md`。

---

## 前端调用示例 (Kotlin/Android)

```kotlin
// 1. 获取语音列表
val response = httpClient.get("http://192.168.1.100:8000/api/v1/tts/voices")
val voices = response.body<ApiResponse<List<TtsVoice>>>()

// 2. 合成语音
val synthesizeResponse = httpClient.post("http://192.168.1.100:8000/api/v1/tts/synthesize") {
    contentType(ContentType.Application.Json)
    setBody(TTSSynthesizeRequest(
        text = "你好，欢迎来到景灵智导。",
        voice = "zh-CN-XiaoxiaoNeural",
        format = "audio_with_marks"
    ))
}
val result = synthesizeResponse.body<ApiResponse<TTSSynthesizeResult>>()

// 3. 播放音频：后端返回相对路径，播放前拼接当前服务器地址
val audioUrl = "http://192.168.1.100:8000" + result.data.audio_url
exoPlayer.setMediaItem(MediaItem.fromUri(audioUrl))
exoPlayer.prepare()
exoPlayer.play()

// 4. 流式播放：tts_segment 只记录；tts_segment_ready 才入播放队列
when (event.type) {
    "tts_segment" -> pendingSegments[event.segmentId] = event
    "tts_segment_ready" -> readyQueue.add(event)
    "tts_audio_error" -> skipSegment(event.segmentIndex)
}
```

---

## 常见错误处理

### 400 - 文本过长

```json
{
  "detail": "text is too long"
}
```

**解决**: 将文本分割成 <= 500 字符的块，分别合成

### 404 - 文件不存在

```json
{
  "detail": "audio file not found"
}
```

**解决**: 检查文件名拼写，重新调用合成接口

### 502 - 合成失败

```json
{
  "detail": "tts synthesize failed: ..."
}
```

**可能原因**:
- 网络连接问题 (需要外网访问 Microsoft TTS)
- 音色 ID 不存在
- 参数格式错误

**解决**: 检查 text、voice、rate/volume/pitch 参数

---

## 性能优化建议

1. **复用音色**: 同一景点讲解推荐固定使用一个语音音色
2. **文本缓存**: 对于固定文案（如景点介绍），缓存合成结果
3. **流式播放**: 对于长文本，由后端分段合成并通过流式事件下发，移动端排队播放
4. **参数简化**: 大多数情况下保持默认参数 (rate/volume/pitch 不需要调整)
5. **按序播放**: 流式场景按 `segment_index` 顺序播放 `tts_segment_ready.audio_url`，播放前拼接服务器 base URL

---

## 后续扩展

### 音色导出

当前支持所有 Microsoft Edge 在线 TTS 的中文音色。若需要其他语言，可通过调用 `/voices` 端点查询。

### 本地 / 云端 TTS 切换

架构已预留接口，只需替换适配器即可：

| 方案 | 状态 | 说明 |
|------|------|------|
| Edge-TTS | **生产中** | 默认方案，Microsoft 云端，需外网 |
| MiniMax Speech-2.8-Turbo | **已实现** | 经 PPIO 平台调用，支持情感语音（`backend/app/integrations/tts/minimax_tts_adapter.py`） |
| CosyVoice | 可接入 | 本地离线 |
| MeloTTS | 可接入 | 本地离线 |
| 讯飞 / 火山 TTS | 可接入 | 云端商用方案 |

---

## 部署检查清单

- [x] `edge-tts` 已安装到 Python 环境
- [x] `storage/audio/output` 目录存在且可写
- [x] 服务器有外网访问权限 (调用 Microsoft TTS)
- [x] `/api/v1/tts/voices` 和 `/api/v1/tts/synthesize` 接口可访问
- [x] 音频文件能正常生成和下载
- [x] 流式问答接口可返回 `tts_segment`、`tts_segment_ready`
- [x] 分段音频的 `audio_url` 在 `tts_segment_ready` 后可被移动端拼接完整 URL 播放
- [x] 分段 `marks` 与片段音频时长对齐
