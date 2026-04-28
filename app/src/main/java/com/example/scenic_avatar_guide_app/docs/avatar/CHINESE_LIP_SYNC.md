# 高精度中文口型同步实现方案

版本：v1.0  
日期：2026-04-29  
定位：Android 端中文数字人口型系统重构

---

## 一、现状问题

当前数字人口型系统存在严重缺陷：

1. `RemoteTTSController` 使用字符时长估算（180ms/字）驱动口型
2. `VisemeType.fromChar()` 只能识别拉丁字母，所有中文字符均映射到 `NEUTRAL`（mouthOpen=0.1）
3. 结果：说话时嘴巴几乎不动，完全无法体现中文发音特征
4. 后端已设计 `marks` 时间戳接口，但 Android 端未消费

---

## 二、设计目标

1. **音素级精度**：每个汉字的拼音音素（声母+韵母）都有对应的口型参数
2. **时间精确对齐**：优先使用后端 `marks`，无 marks 时使用音频振幅辅助校准
3. **自然过渡**：相邻口型之间使用缓动插值，模拟协同发音
4. **零额外网络开销**：marks 随 TTS 响应一次性返回，不增加请求次数
5. **向后兼容**：保留现有接口，新功能可开关

---

## 三、技术架构（三层）

```text
Layer 3: 渲染层 (Live2D / Placeholder)
  - mouthOpen + mouthForm -> Live2D 参数
  - 60fps 口型插值渲染

Layer 2: 动画层 (LipSyncAnimator)
  - 音素时间轴管理
  - 协同发音平滑 (Coarticulation Blending)
  - 音频振幅微调 (Audio Amplitude Feedback)

Layer 1: 音素层 (ChinesePhonemeEngine)
  - 汉字 -> 拼音 -> 声母+韵母 -> 音素事件
  - 多音字字典 (景区专用词库)
  - 轻声/儿化音处理
```

---

## 四、口型参数空间

扩展为 15 种音素口型，覆盖所有汉语拼音：

| 口型ID | 描述 | mouthOpen | mouthForm | 对应拼音 |
|--------|------|-----------|-----------|----------|
| SIL | 静音/闭唇 | 0.0 | 0.0 | 停顿 |
| BP | 双唇塞音 | 0.0 | 0.0 | b, p, m |
| F | 唇齿音 | 0.1 | -0.2 | f |
| DT | 舌尖中音 | 0.15 | 0.0 | d, t, n, l |
| GK | 舌根音 | 0.35 | 0.0 | g, k, h |
| JQ | 舌面音 | 0.25 | -0.4 | j, q, x |
| ZC | 舌尖前音 | 0.2 | 0.0 | z, c, s |
| ZH | 舌尖后音 | 0.25 | 0.0 | zh, ch, sh, r |
| A | 开口呼 | 0.9 | 0.0 | a, ai, an, ang, ao |
| O | 合口呼圆唇 | 0.6 | 0.6 | o, ou, ong |
| E | 半开口 | 0.5 | 0.0 | e, ei, en, eng, er |
| I | 齐齿呼扁嘴 | 0.3 | -0.5 | i, ie, iu, in, ing |
| U | 合口呼收圆 | 0.4 | 0.4 | u, ui, un |
| V | 撮口呼 | 0.35 | -0.3 | u:, u:e, u:n |
| UA | 复合元音过渡 | 0.7 | 0.2 | ua, uai, uan, uang, iao, ian |

**注**：复合韵母（如 "huan" = h + u + an）拆分为多个连续口型，而非固定口型。

---

## 五、拼音到音素事件分解

每个汉字分解为一个或多个音素事件：

```text
例："欢" (huan)
  -> 声母 h  -> 音素 GK (舌根音), mouthOpen=0.35, mouthForm=0.0
  -> 韵母 u  -> 音素 U (合口呼), mouthOpen=0.4, mouthForm=0.4
  -> 韵尾 an -> 音素 A (开口呼), mouthOpen=0.9, mouthForm=0.0

例："迎" (ying)
  -> 零声母 -> 跳过
  -> 韵母 i  -> 音素 I, mouthOpen=0.3, mouthForm=-0.5
  -> 韵尾 ng -> 音素 GK (舌根鼻音), mouthOpen=0.35, mouthForm=0.0
```

声母时长约占 30%，韵母占 70%。韵母内部再按介音/主元音/韵尾分配。

---

## 六、协同发音平滑

真实说话时口型不会在音素边界瞬间切换。使用滑动窗口加权平均：

```text
当前口型参数 = 0.5 x 当前音素目标 + 0.3 x 前一音素 + 0.2 x 后一音素
```

在 marks 给定的时间区间内，按上述权重计算每一帧的 mouthOpen 和 mouthForm。

---

## 七、后端 Marks 集成

marks 字段扩展为字级，每个 mark 增加 `phonemes`：

```json
{
  "text": "欢",
  "start_ms": 340,
  "end_ms": 540,
  "phonemes": ["h", "u", "an"]
}
```

Android 端 `RemoteTTSController` 解析 marks，生成精确时间轴。

如果后端暂未支持 phonemes，Android 端使用 `ChinesePhonemeEngine` 本地分解。

---

## 八、音频振幅辅助校准

当 marks 精度不足或缺失时，从 ExoPlayer 获取音频振幅：

```kotlin
audioPlayer.getCurrentAmplitude() -> 映射到 mouthOpen 微调系数
```

- 有声段：按拼音映射张开
- 无声段（振幅<阈值）：快速过渡到 SIL（闭嘴）

---

## 九、实施步骤

### Phase 1: 核心引擎

1. 添加拼音库依赖（pinyin4j）
2. 新建 `ChinesePhonemeEngine.kt`（汉字->拼音->音素分解）
3. 新建 `ChineseVisemeMapper.kt`（15 种音素口型映射）
4. 单元测试

### Phase 2: 动画层

1. 新建 `LipSyncAnimator.kt`（时间轴管理+协同发音平滑）
2. 改造 `RemoteTTSController.kt`（解析 marks，驱动音素时间轴）
3. 保留字符估算兜底

### Phase 3: 渲染层优化

1. 优化 `AvatarView` Placeholder 使用 `mouthForm`
2. 验证 Live2D 参数传递

### Phase 4: 联调与打磨

1. 端到端测试（"欢迎来到灵山胜境"等）
2. 性能优化（拼音缓存）

---

## 十、关键文件清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `app/build.gradle.kts` | 修改 | 添加 pinyin4j 依赖 |
| `core/avatar/ChinesePhonemeEngine.kt` | 新建 | 拼音转换与音素分解 |
| `core/avatar/ChineseVisemeMapper.kt` | 新建 | 音素->口型参数映射 |
| `core/avatar/LipSyncAnimator.kt` | 新建 | 时间轴管理与平滑动画 |
| `core/tts/RemoteTTSController.kt` | 修改 | 集成 marks 解析与音素驱动 |
| `domain/model/TtsModels.kt` | 修改 | `TtsMarkItem` 增加 `phonemes` 字段 |
| `ui/components/AvatarView.kt` | 修改 | Placeholder 渲染使用 mouthForm |
| `core/avatar/AvatarPlaybackManager.kt` | 修改 | 接入 LipSyncAnimator |

---

## 十一、风险与对策

| 风险 | 对策 |
|------|------|
| pinyin4j 多音字不准 | 景区专用词库覆盖 |
| Live2D 模型不支持 ParamMouthForm | 回退到仅使用 ParamMouthOpenY |
| 后端 marks 未实现 | Android 端本地生成音素时间轴 |
| 性能问题 | 拼音结果缓存，动画使用 ValueAnimator |

---

## 十二、验收标准

1. 说 "啊" 时嘴巴明显张大（mouthOpen > 0.8）
2. 说 "一" 时嘴巴变扁（mouthForm < -0.3）
3. 说 "不" 时先闭口再开口，过渡自然
4. 整句话播放时口型与音频节奏误差 < 100ms
5. 连续 10 句不同内容的口型均正确
