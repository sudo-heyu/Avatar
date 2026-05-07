# 数字人测试模式说明

版本：v1.2  
日期：2026-05-07  
适用范围：Android 端主界面内置测试面板、Live2D 表情/动作/场景/口型/Combo 联调、后端 mock/API 降级测试

---

## 1. 入口

测试模式入口位于主界面顶部栏右侧的可见性按钮：

```text
MainScreen.TopBar
→ showTestPanel
→ CompactTestPanel
```

测试面板由 `MainScreen.kt` 直接读取 `TestAvatarActions`：

```kotlin
val expressionButtons = TestAvatarActions.getExpressionButtons()
val gestureButtons = TestAvatarActions.getGestureButtons()
val scenarioButtons = TestAvatarActions.getScenarioButtons()
val lipSyncButtons = TestAvatarActions.getLipSyncTestButtons()
val comboButtons = TestAvatarActions.getComboButtons()
```

点击任意测试项后调用：

```text
MainScreen → MainViewModel.playTestAction(action)
           → AvatarPlaybackManager.play(action)
```

---

## 2. 当前测试分类

当前测试模式分为 5 个页签：

| 页签 | 数据来源 | 数量 | 目的 |
|------|----------|------|------|
| 表情 | `getExpressionButtons()` | 13 | 单独验证 Expression 层 |
| 动作 | `getGestureButtons()` | 13 | 单独验证 Gesture 层 |
| 场景 | `getScenarioButtons()` | 36 | 验证 Expression + Gesture + LipSync 协同 |
| 口型 | `getLipSyncTestButtons()` | 12 | 专项验证中文口型同步 |
| Combo | `getComboButtons()` | 18 | 验证短情绪反馈、方向引导和 LLM 情绪匹配素材 |

---

## 3. 表情测试

表情测试只设置表情，不播放动作和语音：

```kotlin
AvatarPlayAction(
    text = null,
    expression = expression,
    expressionIntensity = 1.0f,
    gesture = AvatarGesture.IDLE,
    motionQueue = emptyList()
)
```

当前覆盖 13 个表情：

| ID | 中文标签 | 枚举 |
|----|----------|------|
| neutral | 中性 | `NEUTRAL` |
| happy | 开心 | `HAPPY` |
| excited | 兴奋 | `EXCITED` |
| welcoming | 欢迎 | `WELCOMING` |
| approving | 赞许 | `APPROVING` |
| surprised | 惊叹 | `SURPRISED` |
| grateful | 感恩 | `GRATEFUL` |
| playful | 俏皮 | `PLAYFUL` |
| thinking | 思考 | `THINKING` |
| focused | 专注 | `FOCUSED` |
| reverent | 敬畏 | `REVERENT` |
| concerned | 关切 | `CONCERNED` |
| apologetic | 歉意 | `APologetic` |

---

## 4. 动作测试

动作测试只设置中性表情和单个动作；非 `IDLE` 动作会生成一个 1600ms 的 `MotionQueueItem`：

```kotlin
AvatarPlayAction(
    text = null,
    expression = AvatarExpression.NEUTRAL,
    gesture = gesture,
    motionQueue = listOf(MotionQueueItem(gesture.value, 0, 1600))
)
```

当前覆盖 13 个动作：

| ID | 中文标签 | 枚举 |
|----|----------|------|
| idle | 待机 | `IDLE` |
| nod | 点头 | `NOD` |
| shake | 摇头 | `SHAKE` |
| wave | 致意 | `WAVE` |
| welcome_gesture | 欢迎 | `WELCOME_GESTURE` |
| point_left | 看左 | `POINT_LEFT` |
| point_right | 看右 | `POINT_RIGHT` |
| point_forward | 示意 | `POINT_FORWARD` |
| bow | 欠身 | `BOW` |
| thinking_pose | 沉思 | `THINKING_POSE` |
| guide | 引导 | `GUIDE` |
| look_up | 仰望 | `LOOK_UP` |
| listen | 聆听 | `LISTEN` |

---

## 5. 场景测试

场景测试是完整联动测试，使用 `TestAvatarActions.allScenarios` 中的 36 个 `AvatarPlayAction`。

每个场景可以包含：

- `text`：触发 TTS 播放和口型同步
- `expression` / `expressionIntensity`：初始表情
- `gesture`：初始动作
- `motionQueue`：按时间轴播放动作队列
- `expressionTimeline`：按时间轴切换表情

当前 36 个场景分组：

| 分组 | 数量 | 场景 |
|------|------|------|
| 入园指引 | 4 | 欢迎入园、购票指引、园区概览、大照壁 |
| 景点讲解 | 6 | 大佛壮观、九龙灌浴、梵宫艺术、五印坛城、祥符禅寺、曼飞龙塔 |
| 特色体验 | 6 | 抱佛脚、天下第一掌、转经祈福、撞钟祈福、接圣水、祈福许愿 |
| 路线推荐 | 4 | 历史文化游、亲子轻松游、老年无障碍、最佳拍照点 |
| 导航指引 | 4 | 左侧指路、右侧指路、设施指引、观光车 |
| 餐饮购物 | 4 | 餐饮推荐、纪念品、灵山精舍、文化演出 |
| 安全应急 | 2 | 安全提醒、天气预警 |
| 互动响应 | 2 | 语音聆听、理解确认 |
| 离园兜底 | 4 | 感谢告别、交通指引、兜底致歉、网络错误 |

---

## 6. 口型测试

口型测试使用 12 个专项文本场景，固定为中性表情和待机动作，目的是排除动作/表情干扰，专注观察口型。

```kotlin
AvatarPlayAction(
    text = "...",
    expression = AvatarExpression.NEUTRAL,
    expressionIntensity = 0.5f,
    gesture = AvatarGesture.IDLE
)
```

当前覆盖：

| 标签 | 测试重点 |
|------|----------|
| bpm爆破音 | b/p/m 与元音快速过渡 |
| dtnl舌尖音 | d/t/n/l 清晰度 |
| gkh舌根音 | g/k/h 开合 |
| jqx舌面音 | j/q/x 扁嘴口型 |
| zcs舌尖前音 | z/c/s |
| zhchshr舌尖后音 | zh/ch/sh/r |
| 元音过渡 | a/o/e/i/u/ü 平滑过渡 |
| 混合音素 | 多音素连续发音 |
| 葡萄皮绕口令 | 快速语速同步 |
| 标兵炮兵极限 | 爆破音压力测试 |
| 鼻音n/ng对比 | n 与 ng 对比 |
| 妈马骂麻声调 | 相同音素不同声调 |

---

## 7. Combo 测试

Combo 测试使用 `TestAvatarActions.allCombos` 中的 18 个精简情感表现单元。每个 Combo 都是一个短 `AvatarPlayAction`，用于验证短 TTS、表情时间轴、动作队列和口型同步的快速联动。

当前 Combo 面板展示：

- `emotionCategory`：情感/用途分类，如 `喜悦`、`引导`、`敬畏`、`聆听`
- `label`：直接来自 `action.text`，避免维护重复文案
- `emotionTags`：展示前 3 个标签，辅助检查 LLM 匹配词

当前 18 个 Combo：

| 分组 | 数量 | Combo |
|------|------|-------|
| 喜悦/赞赏 | 2 | 嘻嘻、太棒了 |
| 欢迎/引导 | 5 | 您好呀、这边请、往左走、往右走、往前走 |
| 惊叹/敬畏 | 3 | 哇、好壮观、好神圣 |
| 认同/思考 | 2 | 好的、让我想想 |
| 歉意/关切 | 2 | 抱歉呀、小心哦 |
| 感恩/告别 | 2 | 感谢您、一路平安 |
| 俏皮/聆听 | 2 | 猜猜看、我在听 |

设计细节见 `docs/animation/AVATAR_COMBO_DESIGN.md`。

---

## 8. 后端联调数据结构

当前 Android 端主链路使用流式接口：

```text
POST /api/v1/chat/text/stream
```

流式事件由 `text_delta`、`tts_segment`、`avatar_action` 和结构化收口事件组成。移动端将分段音频排队播放，并根据 `avatar_action` 驱动表情、动作和口型同步。

独立 TTS 接口仅保留用于测试、缓存预热或极端兜底：

```text
POST /api/v1/tts/synthesize
```

非流式 `POST /api/v1/chat/text` 已从 Android 端移除，仅作为后端内部保留接口或本地 mock 参考。

后端保留接口和本地 mock 可使用统一外层结构：

```json
{
  "code": 0,
  "message": "ok",
  "data": {}
}
```

保留接口的 `data` 结构示例：

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

字段约束：

| 字段 | 用途 |
|------|------|
| `reply_text` | 文本显示、TTS 合成和口型同步输入 |
| `avatar_action.expression.type` | 对应本文档表情枚举 |
| `avatar_action.expression.intensity` | 表情强度，常用范围 `0.0` 到 `1.0` |
| `avatar_action.gesture.type` | 对应本文档动作枚举 |
| `avatar_action.gesture.priority` | 可用值：`low`、`normal`、`high` |
| `motion_queue` | 按 `start_offset_ms` 和 `duration_ms` 播放动作队列 |
| `marks` | 语音或文本强调标记，可用于后续精细动作同步 |
| `sources` | RAG 引用来源；只测数字人时可为空数组 |
| `metadata.intent` | 常用值：`greeting`、`farewell`、`introduction`、`direction`、`route_recommendation`、`unknown` |

## 9. 后端 Mock 场景

以下场景用于补充主界面测试面板之外的接口兼容、降级和异常验证。

| 场景 | 目的 | 关键字段 |
|------|------|----------|
| 欢迎问候 | 验证开场播报与挥手 | `welcoming` + `wave` |
| 景点讲解 | 验证长文本播报和引用展示 | `excited` + `point_forward` + `sources` |
| 左右指路 | 验证左右动作切换 | `point_left` / `point_right` |
| 路线推荐 | 验证动作队列 | `guide` + `motion_queue` |
| 安全提醒 | 验证提醒类表情 | `concerned` + `point_forward` |
| 无法回答 | 验证兜底与道歉动作 | `apologetic` + `bow` |
| 纯文本降级 | 验证无动作指令时仍能播报 | `avatar_action: null` |
| 仅 metadata 降级 | 验证端侧是否能按意图推断动作 | 只返回 `metadata` |
| 后端业务错误 | 验证错误提示与重试 | `code != 0` |

推荐 mock 组合：

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

```json
{
  "code": 1003,
  "message": "session not found",
  "data": {}
}
```

联调建议：

1. 前端本地 mock 时，优先覆盖 `reply_text`、`avatar_action`、`metadata`。
2. 只测数字人动作时，可以省略 `sources`。
3. 只测 TTS 与口型时，固定 `expression=neutral`、`gesture=idle`，仅替换 `reply_text`。
4. 流式播放测试应 mock `text_delta` 和 `tts_segment`，其中 `tts_segment.marks` 为片段内相对时间。
5. 降级测试应覆盖 `avatar_action: null`、`sources: []`、`code != 0` 和超长 `reply_text`。

## 10. 当前一致性结论

以 `TestAvatarActions.kt` 和 `AvatarState.kt` 为准：

- 表情测试与当前 `AvatarExpression` 一致：13/13 覆盖。
- 动作测试与当前 `AvatarGesture` 一致：13/13 覆盖。
- 场景测试与当前实现一致：实际为 36 个场景，旧注释中的 32 已修正。
- 口型测试与当前实现一致：12 个专项文本场景。
- Combo 测试与当前实现一致：32 个旧 Combo 已收缩为 18 个高频 Combo。

本文档是当前数字人测试模式、后端 mock/API 降级测试和维护规则的唯一说明。

---

## 11. 维护规则

1. 新增 `AvatarExpression` 时，同步更新：
   - `TestAvatarActions.getExpressionButtons()`
   - `docs/animation/EXPRESSION_MOTIONS.md`
   - 本文档
2. 新增 `AvatarGesture` 时，同步更新：
   - `TestAvatarActions.getGestureButtons()`
   - `docs/animation/GESTURE_MOTIONS.md`
   - `Live2DRendererImpl.getMotionPathForGesture()`
   - 本文档
3. 新增场景时，同步更新：
   - `allScenarios`
   - `getScenarioButtons()` 中的 `categories` 和 `labels`
   - 本文档的场景分组数量
4. 新增口型专项时，同步更新：
   - `lipSyncTestScenarios`
   - `getLipSyncTestButtons()` 中的 `categories`、`labels`、`descriptions`
   - 本文档
5. 新增或删除 Combo 时，同步更新：
   - `allCombos`
   - `getComboButtons()` 中的 `categories`、`tags`、`descriptions`
   - `docs/animation/AVATAR_COMBO_DESIGN.md`
   - 本文档
6. 调整后端数字人协议、流式事件或降级策略时，同步更新：
   - 本文档的后端联调数据结构
   - 后端 API 契约文档
   - Android 端解析和降级实现
