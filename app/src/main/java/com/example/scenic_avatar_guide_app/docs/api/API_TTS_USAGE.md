# Edge TTS API 使用指南

## 概述

后端已完整实现 Edge TTS 在线语音生成功能，前端可通过以下接口实现文字转语音。

自流式问答方案起，TTS 使用方式如下：

1. **流式问答路径（主链路）**：后端在 `POST /api/v1/chat/text/stream` 的事件流中直接返回 `tts_segment`，移动端只负责分段音频排队播放。
2. **独立 TTS 接口（测试/兜底）**：`POST /api/v1/tts/synthesize` 用于完整文本生成完整音频，仅在独立测试、缓存预热或极端降级场景使用。

## 基础信息

- **Base URL**: `http://server_ip:8000/api/v1/tts`
- **支持格式**: MP3 (音频文件)
- **最大文本长度**: 500 字符
- **缓存机制**: 相同文本+音色组合自动复用已生成的音频
- **流式分段**: 长回答按可朗读短句生成多个音频片段，每段通过 `tts_segment` 事件下发

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
  "data": {
    "voices": [
      {
        "id": "zh-CN-XiaoxiaoNeural",
        "locale": "zh-CN",
        "gender": "Female",
        "friendly_name": "晓晓 (Natural) - 中文 (中国)"
      }
    ]
  }
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
  "pitch": "+0Hz"
}
```

**请求参数**

| 参数 | 类型 | 必需 | 说明 | 示例 |
|------|------|------|------|------|
| text | string | ✓ | 待合成的文本 | "你好，欢迎。" |
| voice | string | ✗ | 语音音色 ID | "zh-CN-XiaoxiaoNeural" |
| rate | string | ✗ | 语速调整 | "+0%", "+10%", "-20%" |
| volume | string | ✗ | 音量调整 | "+0%", "+10%", "-20%" |
| pitch | string | ✗ | 音调调整 | "+0Hz", "+50Hz", "-100Hz" |
| format | string | ✗ | 返回格式 | "audio" (默认) |

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

流式问答不建议移动端拿到 `text_delta` 后再自行调用 `/tts/synthesize`，否则会增加额外 HTTP 往返并导致首句播放不稳定。推荐由后端在生成文本时同步维护朗读缓冲区，达到可朗读边界后生成音频片段，并通过 `tts_segment` 事件下发。

### `tts_segment` 事件示例

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
    { "text": "来到", "start_ms": 430, "end_ms": 820 },
    { "text": "灵山胜境", "start_ms": 830, "end_ms": 1600 }
  ]
}
```

### 分段规则

1. 不按 token 或单字合成 TTS，避免接口压力和播放碎片感。
2. 推荐遇到 `，。！？；：` 等标点切分。
3. 首段超过 800ms 未遇到标点时，可强制切出 8-12 个中文字符作为首段。
4. 每个 `marks` 时间戳都是该音频片段内的相对时间。
5. 移动端播放到队列为空且后端流未结束时，应停止口型并等待后续 `tts_segment`。

### 与独立 TTS 接口的关系

| 场景 | 推荐方式 |
|------|----------|
| 流式回答（主链路） | 消费 `/api/v1/chat/text/stream` 中的 `tts_segment` |
| TTS 功能测试 | 调用 `/api/v1/tts/synthesize` |
| 固定文案缓存预热 | 调用 `/api/v1/tts/synthesize` |
| 极端离线兜底 | 调用系统 TTS 或 `/api/v1/tts/synthesize` |

详细流式方案见：`STREAMING_REFACTOR_PLAN.md`。

---

## 前端调用示例 (Kotlin/Android)

```kotlin
// 1. 获取语音列表
val response = httpClient.get("http://192.168.1.100:8000/api/v1/tts/voices")
val voices = response.body<TTSVoicesResponse>()

// 2. 合成语音
val synthesizeResponse = httpClient.post("http://192.168.1.100:8000/api/v1/tts/synthesize") {
    contentType(ContentType.Application.Json)
    setBody(TTSSynthesizeRequest(
        text = "你好，欢迎来到景灵智导。",
        voice = "zh-CN-XiaoxiaoNeural"
    ))
}
val result = synthesizeResponse.body<ApiResponse<TTSSynthesizeResult>>()

// 3. 播放音频
val audioUrl = "http://192.168.1.100:8000" + result.data.audio_url
mediaPlayer.setDataSource(audioUrl)
mediaPlayer.prepare()
mediaPlayer.start()
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
3. **流式播放**: 对于长文本，由后端分段合成并通过 `tts_segment` 下发，移动端排队播放
4. **参数简化**: 大多数情况下保持默认参数 (rate/volume/pitch 不需要调整)

---

## 后续扩展

### 音色导出

当前支持所有 Microsoft Edge 在线 TTS 的中文音色。若需要其他语言，可通过调用 `/voices` 端点查询。

### 本地 TTS 切换

若需更好的离线支持或自定义音色，后端可轻松切换到:
- CosyVoice (本地离线)
- MeloTTS (本地离线)
- 讯飞/火山 TTS (云端商用方案)

架构已预留接口，只需替换 `EdgeTTSAdapter` 即可。

---

## 部署检查清单

- [x] `edge-tts` 已安装到 Python 环境
- [x] `storage/audio/output` 目录存在且可写
- [x] 服务器有外网访问权限 (调用 Microsoft TTS)
- [x] `/api/v1/tts/voices` 和 `/api/v1/tts/synthesize` 接口可访问
- [x] 音频文件能正常生成和下载
- [ ] 流式问答接口可返回 `tts_segment`
- [ ] 分段音频的 `audio_url` 可被移动端直接播放
- [ ] 分段 `marks` 与片段音频时长对齐
