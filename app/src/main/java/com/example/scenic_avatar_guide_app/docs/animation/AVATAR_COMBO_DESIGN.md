# 数字人 Combo 设计文档

本文档记录 `TestAvatarActions.allCombos` 中的 Combo 设计原则、分层规则和当前保留的 18 个高频 Combo。

## 1. 定位

Combo 是数字人的最短情感表现单元，用于在正式讲解前后插入短促、明确的情绪反馈。

| 维度 | Combo | Scenario |
|------|-------|----------|
| 时长 | 约 0.5-2.5 秒 | 约 10-15 秒 |
| 文本 | 短语或感叹词 | 完整讲解段落 |
| 信息量 | 低，偏情绪和交互 | 高，承载景区内容 |
| 动作 | 1-3 个动作 | 多动作队列 |
| 触发 | LLM 情绪匹配或测试按钮 | 固定场景讲解 |

Combo 不承担景点知识讲解，只负责欢迎、回应、惊叹、方向引导、歉意、感谢、聆听等交互状态。

## 2. 分层约束

Combo 仍遵循 Live2D 三层参数分工：

| 层 | 负责内容 | Combo 中的用法 |
|----|----------|----------------|
| Expression | 情绪状态、面部、头部微姿态 | 通过 `expression` 和 `expressionTimeline` 控制 |
| Gesture | 功能性动作、身体、手臂、头部覆盖 | 通过 `gesture` 和 `motionQueue` 控制 |
| LipSync | 语音口型 | 由短 TTS 文本驱动 `ParamMouthOpenY` |

约束：

- Expression 不写入身体、手臂、手部和肩部参数。
- Gesture 不写入口型参数和面部表情参数。
- LipSync 是 `ParamMouthOpenY` 的唯一实时写入层。
- Combo 文本要短，优先选择 1-4 字的口语反馈。
- 同一语义只保留一个代表 Combo，避免“点头 + 开心表情”的换皮重复。
- Combo 的 `motionQueue` 内部不回到 `IDLE`，避免短动作之间出现僵硬停顿。

## 3. 设计原则

1. 先确定交互意图，再选择表情和动作。
2. 基础 Combo 使用单动作，增强 Combo 使用 2-3 个动作。
3. 动作起点尽量贴合短语音节，例如“嘻嘻”用稳定眯眼笑和单次轻点头表达短反馈。
4. `emotionTags` 覆盖口语和书面表达，但同类 Combo 核心词不要重复。
5. 保留方向类 Combo，因为左、右、前在导览场景中语义不可互换。
6. 删除只改变文案、但动作/表情几乎相同的 Combo。
7. Combo 动作之间保持当前姿态并直接交叉过渡，队列末尾再平滑收尾。

## 4. 当前 Combo 清单

| ID | 文本 | 类别 | 表情 | 动作 | 类型 | 用途 |
|----|------|------|------|------|------|------|
| C1 | 嘻嘻 | 喜悦 | PLAYFUL | NOD | 基础 | 开心互动、回应赞美 |
| C2 | 太棒了 | 赞赏 | EXCITED -> HAPPY | NOD x2 + GUIDE | 增强 | 强烈赞赏、正向反馈 |
| C3 | 您好呀 | 欢迎 | WELCOMING -> HAPPY | WAVE | 基础 | 初次见面、打招呼 |
| C4 | 这边请 | 引导 | WELCOMING -> HAPPY | GUIDE | 基础 | 邀请跟随、带路 |
| C5 | 往左走 | 引导 | NEUTRAL -> HAPPY | POINT_LEFT | 基础 | 左侧方向指引 |
| C6 | 往右走 | 引导 | NEUTRAL -> HAPPY | POINT_RIGHT | 基础 | 右侧方向指引 |
| C7 | 往前走 | 引导 | NEUTRAL -> HAPPY | POINT_FORWARD | 基础 | 前方/直行指引 |
| C8 | 哇 | 惊叹 | SURPRISED -> EXCITED | LOOK_UP | 基础 | 惊讶、看到亮点 |
| C9 | 好壮观 | 惊叹 | SURPRISED -> REVERENT -> EXCITED | LOOK_UP + NOD + GUIDE | 增强 | 宏伟景观、震撼表达 |
| C10 | 好神圣 | 敬畏 | REVERENT | LOOK_UP + BOW + NOD | 增强 | 佛教文化、庄严氛围 |
| C11 | 好的 | 认同 | HAPPY -> APPROVING | NOD | 基础 | 确认、接受、同意 |
| C12 | 让我想想 | 思考 | THINKING | THINKING_POSE | 基础 | 查询前、推理中 |
| C13 | 抱歉呀 | 歉意 | APologetic -> NEUTRAL | BOW | 基础 | 兜底回复、无法满足 |
| C14 | 小心哦 | 关切 | CONCERNED -> NEUTRAL | NOD | 基础 | 安全提醒、温和警告 |
| C15 | 感谢您 | 感恩 | GRATEFUL -> HAPPY | BOW x2 | 增强 | 感谢游客、礼貌回应 |
| C16 | 一路平安 | 告别 | WELCOMING -> GRATEFUL | WAVE + BOW | 增强 | 离园告别、祝福 |
| C17 | 猜猜看 | 俏皮 | PLAYFUL | NOD x2 + GUIDE | 增强 | 轻松互动、设置悬念 |
| C18 | 我在听 | 聆听 | FOCUSED -> HAPPY | LISTEN | 基础 | 等待用户继续说 |

## 5. 已删除重复项

本轮清理从 32 个 Combo 收缩到 18 个。删除标准：

- 文本不同但情绪和动作完全近似。
- 同类标签高度重叠，LLM 难以稳定区分。
- 测试面板中占位过多，但导览场景价值低。

删除示例：

| 删除项 | 保留替代 | 原因 |
|--------|----------|------|
| 真好呀 | 嘻嘻 / 太棒了 | 与喜悦、赞赏语义重叠 |
| 欢迎来玩 | 您好呀 / 这边请 | 欢迎与引导已覆盖 |
| 看这里 | 往前走 | 都是前向示意，合并到前方引导 |
| 好美啊 | 好壮观 | 惊叹类动作和标签重叠 |
| 对的呀 / 当然啦 | 好的 | 都是点头认同 |
| 不太确定 | 让我想想 | 思考和疑惑测试价值接近 |
| 好遗憾 | 抱歉呀 | 歉意/遗憾交互可统一 |
| 别担心 | 小心哦 | 关切类保留安全提醒优先 |
| 谢谢您 | 感谢您 | 感恩语义重复 |
| 再见啦 | 一路平安 | 告别保留更完整祝福 |
| 嘿嘿 / 好玩吧 | 猜猜看 | 俏皮类保留更有交互动作的版本 |
| 明白了 | 好的 / 我在听 | 认同和聆听已覆盖 |

## 6. emotionTags 设计

`emotionTags` 用于 LLM 或规则层选择 Combo。每个 Combo 保留 5-15 个标签，覆盖直接表达、同义词和口语变体。

示例：

```kotlin
listOf("壮观", "宏伟", "雄伟", "大气", "气势磅礴", "震撼人心", "气势恢弘", "恢弘", "好美", "绝美")
```

同一类别内可以有少量交叉词，但核心词必须清晰：

- `哇`：偏即时惊讶，例如“哇塞”“天哪”“惊呆了”。
- `好壮观`：偏宏伟景观，例如“壮观”“宏伟”“气势磅礴”。
- `好神圣`：偏宗教敬畏，例如“庄严”“肃穆”“崇敬”。

## 7. 播放编排要求

每个 Combo 由以下字段共同完成：

```kotlin
AvatarPlayAction(
    text = "太棒了",
    expression = AvatarExpression.EXCITED,
    expressionIntensity = 0.88f,
    gesture = AvatarGesture.NOD,
    motionQueue = listOf(
        MotionQueueItem("nod", 0, 450),
        MotionQueueItem("nod", 500, 450),
        MotionQueueItem("guide", 1000, 700)
    ),
    expressionTimeline = listOf(
        ExpressionTimelineItem(AvatarExpression.EXCITED, 0, 0.88f),
        ExpressionTimelineItem(AvatarExpression.HAPPY, 600, 0.85f),
        ExpressionTimelineItem(AvatarExpression.HAPPY, 1200, 0.8f)
    )
)
```

编排规则：

- `text` 控制 TTS 和口型，不能写长句。
- `expression` 是起始情绪，`expressionTimeline` 是细化变化。
- `gesture` 是基础动作，`motionQueue` 是可精确排布的动作序列。
- 若 `motionQueue` 存在，以队列为准表达节奏。
- 所有 Combo 统一设置 `returnToIdleBetweenMotions = false` 和 `returnToIdleAfterMotionQueue = true`。
- Combo 内部动作不插入 IDLE；动作队列末尾才平滑回到 IDLE，避免残留 Gesture 参数。
- 如果短 TTS 比动作队列更早结束，播放管理器会延后非流式收尾，等当前 `motionQueue` 完成后再统一回到 IDLE。
- 场景讲解仍保留默认策略：动作间可以回到 IDLE，用于表达长文本中的自然停顿。

## 8. 新增 Combo 检查清单

新增前必须确认：

- 是否已有同义 Combo 可以复用。
- 是否和现有 `emotionTags` 大量重叠。
- 是否具有独立的导览用途。
- 文本是否短于 4 个汉字或接近 1 秒口播。
- 是否违反 Expression / Gesture / LipSync 分层。
- 是否沿用 Combo 连续动作策略，避免 motionQueue 中间回 IDLE。
- 是否需要新增测试按钮分类或只作为内部触发。

建议优先补齐真正缺口，例如“鼓励”“等待”“网络错误轻提示”，不要增加同类点头、同类感谢或同类欢迎的换皮版本。
