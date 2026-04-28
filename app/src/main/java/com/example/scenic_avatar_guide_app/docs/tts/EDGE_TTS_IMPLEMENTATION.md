# Edge-TTS 接入实现方案

版本：v1.1  
日期：2026-04-29  
适用范围：比赛演示、低成本中文数字人、无 GPU 部署

---

## 1. 结论

对于当前项目，`edge-tts` 是一个**非常适合比赛阶段**的 TTS 方案：

- 中文音色通常明显好于系统 TTS 和很多低端云音色
- 不需要 GPU
- 无需单独申请 API Key
- 可快速部署，适合 3 个月左右的演示型项目
- 可以录制完整演示视频，也可以提交完整源码

但要明确：

- `edge-tts` 是**开源客户端**，不是开源语音模型
- 实际语音能力来自 Microsoft Edge 在线 TTS 服务
- 依赖外网，不适合作为长期商用核心链路
- 项目许可证为 **GPLv3**

官方项目：

- GitHub: `https://github.com/rany2/edge-tts`

---

## 2. 为什么适合本项目

当前项目的核心目标不是长期商用，而是：

- 快速做出自然中文数字人
- 预算低，尽量免费
- 要有源码可交付
- 要能稳定完成录屏和演示

在这个前提下，`edge-tts` 比本地大模型 TTS 更现实：

- 不需要显卡
- 音色自然度通常高于很多免费本地 CPU 方案
- 接入成本低
- Python 服务端实现简单，便于和当前 Android App 联调

---

## 3. 不建议直接在 Android 端内置

不建议把 `edge-tts` 直接塞进 Android App，本项目推荐：

`Android App -> 现有后端 /api/v1/tts/* -> edge-tts -> 返回音频/时间标记`

原因：

1. `edge-tts` 是 Python 模块，不是 Android 原生 SDK
2. 当前项目已经有后端和会话接口，直接并入现有后端最省联调成本
3. 服务端更方便做缓存、失败重试、音色切换、统一日志
4. 后续如果换成 `MeloTTS`、`CosyVoice`、讯飞或火山，只需要替换后端内部实现

---

## 4. 推荐架构

### 4.1 最小可用架构

```text
Android App
   |
   | POST /api/v1/tts/synthesize
   v
现有 FastAPI 后端
   |
   | app/services/tts_service.py
   | integrations/tts/edge_tts_adapter.py
   v
Microsoft Edge 在线 TTS
```

服务端返回：

- 音频文件 URL，或
- 直接音频字节流

这种模式最简单，适合先把“能说话”做通。

### 4.2 数字人推荐架构

```text
Android App
   |
   | POST /api/v1/tts/synthesize
   v
现有 FastAPI 后端
   |
   | edge-tts 音频 + WordBoundary
   v
返回 audio_url + marks
```

其中 `marks` 用来驱动：

- 字幕高亮
- 口型节奏
- 强调停顿

---

## 5. 部署方式

### 5.1 环境要求

- Python `3.10` 或 `3.11`
- 建议 Windows / Linux 都可
- 服务器电脑需要可访问外网；Android 手机只需与服务器处于同一局域网

### 5.2 安装依赖

```bash
pip install edge-tts fastapi uvicorn[standard] aiofiles
```

如果要固定版本，可以单独记录在 `requirements.txt`：

```txt
edge-tts
fastapi
uvicorn[standard]
aiofiles
```

### 5.3 命令行验证

先在服务器本机验证 `edge-tts` 能否正常出音：

```bash
edge-tts --text "您好，欢迎来到灵山胜境。" --write-media demo.mp3 --write-subtitles demo.srt
```

也可以先查看可用声音：

```bash
edge-tts --list-voices
```

建议优先筛选中文声音，例如：

- `zh-CN-*`
- `zh-HK-*`
- `zh-TW-*`

具体音色名称不要写死在文档里，部署时以实际 `--list-voices` 结果为准。

---

## 6. 现有后端目录建议

建议直接并入现有后端，例如：

```text
backend/app/
├── api/v1/app_tts.py
├── schemas/tts.py
├── services/tts_service.py
├── integrations/tts/
│   ├── base.py
│   └── edge_tts_adapter.py
└── utils/audio_cache.py
```

职责划分：

- `api/v1/app_tts.py`：游客侧 TTS 接口
- `schemas/tts.py`：请求和响应结构
- `services/tts_service.py`：缓存、文件生成、错误处理
- `integrations/tts/edge_tts_adapter.py`：对 `edge-tts` 的实际调用

---

## 7. 推荐接口设计

### 7.1 获取发音人列表

**接口**：`GET /api/v1/tts/voices`

**响应示例**：

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
    }
  ]
}
```

说明：

- 这个接口用于 App 拉取可选音色
- 首次可以先手工配置，不一定必须动态获取

### 7.2 文本合成

**接口**：`POST /api/v1/tts/synthesize`

**请求示例**：

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

字段建议：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| text | String | 是 | 待合成文本 |
| voice | String | 否 | 发音人 ID |
| rate | String | 否 | 语速，如 `+10%` |
| volume | String | 否 | 音量，如 `+0%` |
| pitch | String | 否 | 音调，如 `+0Hz` |
| format | String | 否 | `audio` 或 `audio_with_marks` |

**响应示例**：

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
      { "text": "欢迎", "start_ms": 340, "end_ms": 760 }
    ]
  }
}
```

说明：

- 如果只追求最小实现，可以先返回 `audio_url`
- 如果要做数字人口型同步，建议增加 `marks`

### 7.3 音频文件读取

**接口**：`GET /api/v1/tts/file/{file_name}`

返回：

- `audio/mpeg`

---

## 8. 服务端实现要点

### 8.1 最小实现流程

1. 接收文本请求
2. 调用 `edge-tts` 生成音频
3. 保存为 `mp3`
4. 返回 `audio_url`

这是最容易成功的一版，适合先把比赛演示主链路做通。

### 8.2 推荐实现流程

1. 接收文本与音色参数
2. 调用 `edge-tts` 流式生成
3. 解析音频块和 `WordBoundary` 事件
4. 保存音频文件
5. 生成 `marks`
6. 返回 `audio_url + marks`

其中 `marks` 可以不追求音素级，只做：

- 词级
- 字级
- 停顿级

对于当前项目，**词级或字级已经够用**。

### 8.3 缓存建议

对比赛演示来说，缓存非常重要。

建议用以下字段生成缓存键：

- `text`
- `voice`
- `rate`
- `pitch`
- `volume`

可用 `md5` 作为文件名，例如：

```text
md5(text + voice + rate + pitch + volume).mp3
```

这样同一句讲解不会重复请求线上服务。

---

## 9. Android 端接入建议

### 9.1 推荐改造方向

当前项目的 TTS 已由后端 Edge-TTS 服务统一提供。移动端接入方式为：

- Android 不再直接做在线合成
- Android 只负责请求 TTS 服务、播放音频、驱动口型

即：

`reply_text -> 请求 /tts/synthesize -> 播放 mp3 -> 根据 marks 驱动 mouthOpen`

### 9.2 最小改造

如果你只想尽快出效果：

1. 保留当前 `AvatarPlaybackManager`
2. 把 `XunfeiTTSController` 替换成 `RemoteTTSController`
3. `RemoteTTSController` 请求 `audio_url`
4. 用 `MediaPlayer` 或 `ExoPlayer` 播放
5. 口型先沿用“按字符时长估算”

这样开发量最小。

### 9.3 推荐改造

如果你要更自然的数字人效果：

1. 服务端返回 `marks`
2. Android 播放音频时同步消费 `marks`
3. 每个 mark 对应一次口型变化
4. 强调词可以同步触发表情或轻动作

---

## 10. 口型同步策略

### 10.1 方案 A：纯估算

做法：

- 不依赖服务端时间戳
- 根据文本长度和标点估算每字持续时间

优点：

- 实现最简单

缺点：

- 精度一般
- 长句或快语速时容易飘

### 10.2 方案 B：基于 marks 的节奏同步

做法：

- 服务端返回词级或字级 `start_ms / end_ms`
- Android 按时间推进口型状态

优点：

- 比纯估算稳定
- 足够支撑比赛演示

缺点：

- 不是严格音素级

### 10.3 方案 C：音频振幅驱动

做法：

- 播放时分析音频振幅
- 用振幅控制 `mouthOpen`

优点：

- 不依赖时间戳

缺点：

- 工程复杂度更高
- 当前项目没必要优先做

结论：当前项目优先推荐 **方案 B**。

---

## 11. 推荐音色策略

建议只保留 2 到 4 个中文音色，不要一次开放太多：

- 亲和女声
- 活泼女声
- 稳重男声
- 备用女声

原因：

- 便于比赛演示时稳定选择
- 减少音色切换带来的不确定性
- 更容易做统一口型和动作风格

---

## 12. 异常处理

必须处理以下场景：

1. `edge-tts` 请求失败
2. 外网不可达
3. 返回音频为空
4. 单句过长
5. 服务端超时

建议兜底策略：

- 第一优先：读取本地缓存音频
- 第二优先：回退 Android 系统 TTS
- 第三优先：仅显示文本，不播报

---

## 13. 比赛场景建议

如果目标是“录视频 + 可答辩 + 可交源码”，建议这样落地：

### 方案一：最稳

- 常见讲解词预生成缓存
- 现场问答再实时请求 `edge-tts`

这样即使网络波动，核心演示内容仍可稳定播放。

### 方案二：完全演示型

- 所有比赛脚本提前批量生成音频
- App 演示时优先播放缓存

这是最稳的录屏方案。

---

## 14. 与当前项目的衔接（已实现）

已新增 `RemoteTTSController`，职责如下：

- 请求 `POST /api/v1/tts/synthesize`
- 使用 ExoPlayer 播放返回的 `audio_url`
- 失败时自动回退到 `SystemTTSController`

`AvatarPlaybackManager` 已接入 `RemoteTTSController`，`MainViewModel` 通过 `playbackManager` 统一调用。

---

## 15. 当前实现状态

对于当前仓库，以下步骤**已完成**：

1. 保留现有数字人播放架构
2. 在现有后端新增 `/api/v1/tts/*`
3. Android 端改成远程 TTS 请求
4. 第一阶段返回 `audio_url` 并播放
5. 系统 TTS 兜底已接入

后续可选优化：

- 服务端返回 `marks`，Android 端用 marks 替换字符估算驱动口型
- 增加服务端音频缓存

---

## 16. 已完成工作清单

- [x] 后端新增 `app_tts.py / tts_service.py / edge_tts_adapter.py`
- [x] 实现 `/api/v1/tts/synthesize`
- [x] 实现缓存
- [x] Android 新增 `RemoteTTSController`
- [x] 播放器使用 ExoPlayer
- [x] 字符估算驱动口型（marks 驱动预留）
- [x] 保留系统 TTS 兜底

## 17. 待优化项

1. 服务端返回 `marks`，Android 端用 marks 替换字符估算驱动口型
2. 增加更多 Edge-TTS 音色选择

---

## 18. 参考命令

安装：

```bash
pip install edge-tts fastapi uvicorn[standard] aiofiles
```

查看声音：

```bash
edge-tts --list-voices
```

测试生成：

```bash
edge-tts --text "您好，欢迎来到灵山胜境。" --voice zh-CN-XiaoxiaoNeural --write-media demo.mp3 --write-subtitles demo.srt
```

启动服务：

```bash
uvicorn app:app --host 0.0.0.0 --port 8081
```

---

## 19. 总结

`edge-tts` 不是长期商用的最佳方案，但对于当前项目，它是一个**低成本、无 GPU、中文效果好、实现速度快**的非常合适的比赛方案。

当前 Android 端已完成接入：后端合成 + ExoPlayer 播放 + 系统 TTS 兜底。如果后续项目继续演进，再把服务端实现替换成 `MeloTTS` 或其他自控方案即可，Android 端接口层可以保持不变。
