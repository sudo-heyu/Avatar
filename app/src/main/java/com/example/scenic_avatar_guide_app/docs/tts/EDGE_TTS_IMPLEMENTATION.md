# Edge-TTS 实现文档

版本：v2.0  
日期：2026-05-03

---

## 一、架构

```
用户问题 → ChatService → LLM 生成文本（含 emotion 标签）
                              ↓
                    EmotionAwareSegmenter 解析分段
                              ↓
                    TTS 参数映射（rate/volume/pitch）
                              ↓
                    EdgeTTS 合成音频文件
                              ↓
                    返回 audio_url 给前端
```

---

## 二、调用路径

| 场景 | 接口 | 说明 |
|------|------|------|
| 流式问答 | `POST /api/v1/chat/text/stream` | 推荐，返回 `tts_segment_ready` |
| 非流式/测试 | `POST /api/v1/tts/synthesize` | 降级路径 |

---

## 三、流式 TTS 流程

### 3.1 事件顺序

```
tts_segment        → 分段通知，audio_url 预告
tts_segment_ready  → 音频已生成，可播放
```

### 3.2 URL 方案

**不再推送 chunk**，直接返回完整音频 URL：

```json
{
  "type": "tts_segment_ready",
  "segment_id": "msg_001_000",
  "segment_index": 0,
  "audio_url": "/api/v1/tts/file/tts_msg_001_000.mp3",
  "duration_ms": 5720,
  "marks": [...],
  "emotion": "neutral"
}
```

---

## 四、emotion → 语调映射

| emotion | rate | volume | pitch |
|---------|------|--------|-------|
| excited | +15% | +10% | +0Hz |
| surprised | +10% | +10% | +0Hz |
| grateful | +0% | +5% | +0Hz |
| concerned | -5% | -5% | +0Hz |
| apologetic | -10% | -5% | +0Hz |
| thinking | -5% | +0% | +0Hz |
| reverent | -10% | -10% | +0Hz |
| neutral | +0% | +0% | +0Hz |

---

## 五、性能指标

| 指标 | 数值 |
|------|------|
| 首段就绪延迟 | 600-800ms |
| 生成/播放比 | 5-10x（生成速度远超播放速度） |
| 平均分段长度 | 25-30 字 |

---

## 六、文件存储

- **输出目录**：`storage/audio/output/`
- **命名规则**：`tts_{message_id}_{segment_index:03d}.mp3`
- **自动清理**：每 1 小时保留最新 30 个文件

---

## 七、支持的发音人

| ID | 名称 | 性别 |
|----|------|------|
| zh-CN-XiaoxiaoNeural | 晓晓 | 女 |
| zh-CN-XiaoyiNeural | 晓伊 | 女 |

---

## 八、常见问题

**Q: 为什么不用 chunk 方案？**  
A: Chunk 方案在弱网环境下容易卡顿，URL 方案更稳定。

**Q: emotion 标签会影响文本显示吗？**  
A: 不会，前端收到的是纯文本。

**Q: marks 是什么？**  
A: 词级时间标记，用于口型同步。
