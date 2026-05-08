# TTS Marks 规范 — WordBoundary 对齐的字级音素时间戳

版本：v1.2  
日期：2026-05-08  
定位：Android 端高精度中文口型同步数据源

---

## 1. 概述

`marks` 是 TTS 合成接口返回的**字级时间戳数组**。后端使用 Edge-TTS `WordBoundary` 获取真实词级边界，再把词内时长分配到单个汉字，因此 marks 不再按整句平均分配。

每个元素包含：

- 字符文本
- 开始/结束时间（毫秒）
- 拼音音素数组（声母 + 韵母拆分）

Android 端 `LipSyncAnimator` 消费 `marks` 驱动口型动画，替代原来的固定 180ms/字估算。

关键约束：

- `duration_ms` 是保存后的 MP3 真实帧时长，供播放队列和片段续播使用。
- `marks.start_ms` / `marks.end_ms` 是当前音频片段内的相对时间，从 0 开始。
- `marks[-1].end_ms` 会对齐到 `duration_ms`，尾部 MP3 帧余量会作为 `SIL` 处理。
- Android 端应直接按音频播放进度匹配 marks，不要按 `duration_ms` 再次拉伸 marks。
- `marks[].text` 是当前推荐文本字段；Android 端兼容旧字段 `marks[].word`，读取时以 `word.ifBlank { text }` 兜底。

---

## 2. 数据格式

### 2.1 单条 Mark 结构

```json
{
  "text": "欢",
  "start_ms": 340,
  "end_ms": 540,
  "phonemes": ["h", "u", "an"]
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `text` | string | 单个字符（中文、英文、数字、标点或空格）；尾部补齐静音时可为空字符串 |
| `start_ms` | int | 该字符在音频中的起始时间（毫秒，从 0 开始） |
| `end_ms` | int | 该字符在音频中的结束时间（毫秒） |
| `phonemes` | list[string] | 音素数组；非 CJK 字符为空列表，纯标点为 `["SIL"]` |

### 2.2 完整响应示例

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "audio_url": "/api/v1/tts/file/tts_876d5c53e9309c94.mp3",
    "file_name": "tts_876d5c53e9309c94.mp3",
    "voice": "zh-CN-XiaoxiaoNeural",
    "duration_ms": 2052,
    "marks": [
      { "text": "欢", "start_ms": 0,   "end_ms": 256, "phonemes": ["h", "u", "an"] },
      { "text": "迎", "start_ms": 256, "end_ms": 513, "phonemes": ["i", "ng"] },
      { "text": "来", "start_ms": 513, "end_ms": 769, "phonemes": ["l", "ai"] },
      { "text": "到", "start_ms": 769, "end_ms": 1026, "phonemes": ["d", "ao"] },
      { "text": "灵", "start_ms": 1026, "end_ms": 1282, "phonemes": ["l", "i", "ng"] },
      { "text": "山", "start_ms": 1282, "end_ms": 1539, "phonemes": ["sh", "an"] },
      { "text": "胜", "start_ms": 1539, "end_ms": 1795, "phonemes": ["sh", "eng"] },
      { "text": "境", "start_ms": 1795, "end_ms": 2052, "phonemes": ["j", "i", "ng"] }
    ]
  }
}
```

---

## 3. 音素分解规则

### 3.1 处理流程

```text
文本 -> edge-tts WordBoundary（真实词级边界）
  -> 按词边界定位到原文本
  -> 词内时长均分到单个字符
  -> 标点/词间间隙写入 SIL
  -> MP3 帧时长补齐到 marks 尾部
  -> pypinyin 获取拼音/声母/韵母
  -> 音素拆分
  -> marks
```

说明：

- Edge-TTS 的 `WordBoundary` 对中文通常返回词组，例如“您好”“欢迎”“来到”“灵山”“胜境”。
- 词组内部仍然没有单字真实边界，因此后端只在词内均分；这比整句均分精度高很多。
- 若 Edge-TTS 未返回 `WordBoundary`，后端保留 `SentenceBoundary` 均分兜底。

### 3.2 声母处理

- **真实声母**（b/p/m/f/d/t/n/l/g/k/h/j/q/x/zh/ch/sh/r/z/c/s）保留
- **隔音字母 y/w** 视为零声母，不加入 phonemes
- **零声母**直接跳过，phonemes 从韵母开始

### 3.3 韵母处理

| 类型 | 示例 | 拆分结果 |
|------|------|----------|
| 普通韵母 | `an` | `["an"]` |
| 带介音韵母 | `huan` = `h` + `u` + `an` | `["h", "u", "an"]` |
| yu 系列 | `yuan` = `ü` + `an` | `["v", "an"]` （ü 用 `v` 表示） |
| 齐齿呼 | `ying` = `i` + `ng` | `["i", "ng"]` |

### 3.4 特殊字符

| 字符类型 | phonemes | 说明 |
|----------|----------|------|
| 中文标点/空格/换行 | `["SIL"]` | 静音停顿 |
| 英文字母/数字 | `[]` | 空数组，Android 端用振幅兜底 |
| 非 CJK 符号 | `[]` | 同上 |
| 尾部 MP3 帧补齐 | `["SIL"]` | `text` 可为空，用于让 `marks[-1].end_ms == duration_ms` |

---

## 4. 与 Android 口型系统对接

### 4.1 Android 消费方式

```kotlin
// 解析响应
val marks = response.data.marks

// 每个 mark 生成音素事件时间轴
marks.forEach { mark ->
    val visemes = mark.phonemes.map { phoneme ->
        ChineseVisemeMapper.map(phoneme)  // 映射到 15 种口型
    }
    lipSyncAnimator.addSegment(mark.start_ms, mark.end_ms, visemes)
}
```

同步建议：

- 使用播放器当前播放进度作为唯一时间源。
- 按 `currentPositionMs` 查找 `start_ms <= currentPositionMs < end_ms` 的 mark。
- `duration_ms` 只用于音频队列、续播等待和片段总时长展示。
- 不要根据字符数量重新计算 mark 时长，也不要把 marks 缩放到其它时长。

### 4.2 Fallback 策略

| 场景 | Android 处理 |
|------|-------------|
| `phonemes` 非空 | 使用音素映射口型 |
| `phonemes == []` | 使用音频振幅微调 mouthOpen |
| `phonemes == ["SIL"]` | 强制闭嘴（mouthOpen = 0） |
| marks 缺失 | 退回到字符时长估算（180ms/字） |
| `text == "" && phonemes == ["SIL"]` | 尾部静音补齐，保持闭嘴 |

### 4.3 口型参数映射

Android 端 `ChineseVisemeMapper` 将音素映射到 15 种口型，覆盖所有汉语拼音：

| 音素 | 口型ID | mouthOpen | mouthForm |
|------|--------|-----------|-----------|
| b, p, m | BP | 0.0 | 0.0 |
| f | F | 0.1 | -0.2 |
| d, t, n, l | DT | 0.15 | 0.0 |
| g, k, h | GK | 0.35 | 0.0 |
| j, q, x | JQ | 0.25 | -0.4 |
| z, c, s | ZC | 0.2 | 0.0 |
| zh, ch, sh, r | ZH | 0.25 | 0.0 |
| a, ai, an, ang, ao | A | 0.9 | 0.0 |
| o, ou, ong | O | 0.6 | 0.6 |
| e, ei, en, eng, er | E | 0.5 | 0.0 |
| i, ie, iu, in, ing | I | 0.3 | -0.5 |
| u, ui, un | U | 0.4 | 0.4 |
| v, ve, vn (ü) | V | 0.35 | -0.3 |
| ua, uai, uan, uang, iao, ian | UA | 0.7 | 0.2 |
| SIL | SIL | 0.0 | 0.0 |

---

## 5. 边缘情况处理

### 5.1 后端校验

| 输入 | 后端行为 |
|------|----------|
| 纯标点/空格 | 返回 `400 text contains no speakable content` |
| 文本 > 500 字符 | 返回 `400 text is too long` |
| 混合中英文 | 中文正常分解，英文/数字 phonemes 为空 |
| edge-tts 网络故障 | 返回 `502 tts synthesize failed` |

### 5.2 缓存

marks 随音频一起缓存，文件对：

```text
storage/audio/output/
├── tts_xxxx.mp3      # 音频
└── tts_xxxx.json     # marks 数据
```

缓存键包含文本、音色、语速、音量、音调、返回格式和后端缓存版本。当前 marks 算法升级为 `WordBoundary` 后，旧的整句均分缓存不会继续复用。

---

## 6. 相关文件

| 文件 | 职责 |
|------|------|
| `backend/app/integrations/tts/edge_tts_adapter.py` | 音素分解与 marks 构建 |
| `backend/app/services/tts_service.py` | marks 缓存与返回 |
| `backend/app/api/v1/tts.py` | API 校验与响应 |
| `backend/app/schemas/tts.py` | 数据模型定义 |
| `docs/architecture/android/HIGH_PRECISION_CHINESE_LIP_SYNC.md` | Android 端口型同步方案 |
