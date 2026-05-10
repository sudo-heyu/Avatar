# Markdown 渲染规范 v1.0

版本：v1.0  
日期：2026-05-09  
适用范围：**后端 LLM 输出格式** + **Android 前端气泡渲染**

---

## 目录

- [一、背景与目标](#一背景与目标)
- [二、支持的 Markdown 语法集](#二支持的-markdown-语法集)
- [三、后端实现方案](#三后端实现方案)
- [四、Android 前端实现方案](#四android-前端实现方案)
- [五、样式规范](#五样式规范)
- [六、边界处理](#六边界处理)
- [七、验收标准](#七验收标准)
- [八、版本历史](#八版本历史)

---

## 一、背景与目标

当前聊天气泡中的助手回复仅支持有限的内联 Markdown（粗体、斜体、行内代码、删除线、链接），无法渲染标题、列表、代码块、引用块等块级元素。LLM 在回答导览类问题时，天然会生成结构化内容（路线步骤、景点分项说明、注意事项列表等），缺少块级 Markdown 渲染导致这些内容可读性差。

**目标：**

1. 后端规范 LLM 的 Markdown 输出格式，确保语法集可控
2. 前端替换自定义解析器，接入标准 Markdown 渲染库
3. 前后端共同遵守同一套语法白名单，避免渲染不一致

---

## 二、支持的 Markdown 语法集

### 2.1 白名单（允许输出与渲染）

| 类型 | 语法示例 | 典型使用场景 |
|------|----------|-------------|
| 一级标题 | `# 标题` | 景点/路线大标题 |
| 二级标题 | `## 副标题` | 段落分组标题 |
| 三级标题 | `### 小标题` | 最小标题层级，不再往下嵌套 |
| 粗体 | `**重要**` | 强调关键词 |
| 斜体 | `*提示*` | 轻量强调 |
| 行内代码 | `` `代码` `` | 参数、票价、时间等固定值 |
| 代码块 | ```` ``` ```` | 多行结构化数据 |
| 无序列表 | `- 项目` | 景点列举、注意事项 |
| 有序列表 | `1. 步骤` | 路线步骤、操作流程 |
| 引用块 | `> 引用` | 小贴士、官方说明 |
| 水平分割线 | `---` | 内容分区 |
| 链接 | `[文字](https://...)` | 外部参考链接（仅限 https） |

### 2.2 黑名单（禁止输出）

| 类型 | 禁用原因 |
|------|----------|
| 表格 `\|...\|` | 气泡最大宽度 280dp，水平溢出无法处理 |
| 嵌入图片 `![](url)` | 图片通过独立消息附件渲染，不走文本流 |
| HTML 标签 | 安全风险，渲染库默认不解析 |
| 四级及以下标题 `####` | 嵌套过深，小屏上可读性差 |
| 任务列表 `- [x]` | 交互性与场景不符 |
| 非 https 链接 | 安全合规要求 |

### 2.3 嵌套规则

- 列表项内可使用粗体、斜体、行内代码
- 标题内只允许纯文本，不嵌套粗体/斜体
- 列表最多嵌套 **2 层**，子列表用 2 空格缩进
- 引用块内可包含列表或粗体，不嵌套代码块

---

## 三、后端实现方案

### 3.1 System Prompt 设计

在向 LLM 发送的 System Prompt 中，**增加以下 Markdown 格式约束块**（追加在 System Prompt 末尾）：

```text
## 输出格式规范

回答使用 Markdown 格式，只允许使用以下语法：
- 标题：# ## ###（最多三级，短问答不使用标题）
- 强调：**粗体** 和 *斜体*
- 列表：- 无序列表 或 1. 有序列表（最多两层嵌套，子列表用2空格缩进）
- 代码：行内 `代码` 或代码块（用三个反引号包裹）
- 引用：> 小贴士或注意事项
- 分割线：---（仅在长篇回答的大段落之间使用）
- 链接：[文字](https://...)（仅 https 链接）

禁止使用：表格、嵌入图片 ![](url)、HTML 标签、四级及以下标题（####）。

格式使用原则：
- 景点介绍、功能列举 → 无序列表
- 路线步骤、操作流程 → 有序列表
- 小贴士、注意事项 → 引用块（>）
- 简单问答（"几点开门？"）→ 直接回答，不使用任何 Markdown
- 避免滥用标题，单条回复不超过 2 个标题层级
```

### 3.2 流式输出（SSE）注意事项

流式 `text_delta` 事件逐 Token 推送，客户端在流式过程中会看到不完整的 Markdown 语法（如只输出了 `**` 的前半部分）。

**前后端约定的渲染切换策略：**

| 状态 | 客户端渲染策略 |
|------|---------------|
| 流式进行中（`isLoading=true`） | 按**纯文本**展示累积内容，不解析 Markdown |
| 收到 `done` 事件后（`isLoading=false`） | 对最终完整文本触发 **Markdown 渲染** |

`done.full_text` 是可选字段，不作为 Markdown 渲染的硬依赖：

- 若 `done.full_text` 存在，客户端优先使用它作为最终 Markdown 渲染源。
- 若 `done.full_text` 不存在，客户端使用已按顺序累积的 `text_delta` 内容作为 Markdown 渲染源。
- 后端若需要在结束时做最终清洗、截断或内容安全处理，可通过 `full_text` 返回权威版本；否则保持现有 `done` 事件即可。
- 当前比赛版本不要求后端新增 `done.full_text`。Android 已支持 `full_text` 缺席路径：累积 `text_delta`，收到 `done` 后触发 Markdown 渲染。

```json
// done 事件示例（兼容现有协议，full_text 可选）
{
  "event": "done",
  "data": {
    "type": "done",
    "message_id": "m_xxx",
    "session_id": "abc123",
    "full_text": "# 灵山大佛\n\n灵山大佛高88米，是世界最高青铜坐佛...\n\n## 参观建议\n\n- 建议上午9点前到达，避开人流高峰\n- 门票需提前在官网预约"
  }
}
```

> Markdown 渲染源为 `done.full_text ?: delta 累积文本`。这样既兼容现有后端，也允许后端在未来返回最终权威文本。

**当前后端交付口径：**

- 不需要为了 Markdown 渲染修改 `done` 事件协议；`full_text` 暂不作为验收项。
- 若未来后端要将清洗、截断后的最终文本作为权威结果返回，再新增可选字段 `done.full_text`，并通知 Android 扩展解析。
- 在没有 `full_text` 的当前版本中，移动端最终展示内容以 `text_delta` 顺序累积结果为准。

### 3.3 内容安全过滤

后端在将 LLM 输出写入 SSE 流之前，需过滤以下内容：

```python
import re

def sanitize_markdown(text: str) -> str:
    # 1. 移除所有 HTML 标签
    text = re.sub(r'<[^>]+>', '', text)
    # 2. 移除嵌入图片语法 ![alt](url)
    text = re.sub(r'!\[.*?\]\(.*?\)', '', text)
    # 3. 将非 https 链接降级为纯文本（保留链接文字）
    text = re.sub(r'\[([^\]]+)\]\(((?!https://)[^)]+)\)', r'\1', text)
    return text.strip()
```

该过滤在 LLM 完整输出后执行。当前流式版本不新增 `done.full_text`，因此 `text_delta` 发送前只做 HTML 标签过滤，避免破坏流式增量顺序。图片语法和非 https 链接主要通过 System Prompt 约束生成，并由前端 Markdown 渲染层禁用图片、HTML 等不安全能力兜底。

若未来后端返回 `done.full_text`，则应对完整文本执行 `sanitize_markdown()`，并将清洗后的结果放入 `done.full_text`，作为客户端最终覆盖文本。

### 3.4 路线回复与 TTS

路线模式的回复也应输出 Markdown，建议结构如下：

```markdown
## 推荐路线：经典半日游

1. 第一站：入口广场
    - 预计停留：10 分钟
    - 看点：集合、拍照、了解整体布局
2. 第二站：核心景点
    - 预计停留：30 分钟
    - 看点：重点讲解与参观

> 游玩提醒：节假日人流较多，建议提前预约并预留排队时间。
```

路线步骤使用有序列表；注意事项、风险提示使用引用块。若展示文本包含 Markdown，TTS 合成不能直接朗读 Markdown 原文，应先使用后端已有 `clean_speech_text()` 清洗为纯文本，再进入语音合成。

---

## 四、Android 前端实现方案

### 4.1 依赖选型

使用 **[mikepenz/multiplatform-markdown-renderer](https://github.com/mikepenz/multiplatform-markdown-renderer)**：

- 纯 Jetpack Compose 实现，无需 `AndroidView` 包装
- 支持白名单内所有语法（标题、列表、代码块、引用块等）
- 可自定义主题颜色，对接现有设计系统
- minSdk 21 兼容，无构建冲突

在 `app/build.gradle.kts` 中添加：

```kotlin
// Markdown 渲染
implementation("com.mikepenz:multiplatform-markdown-renderer-android:<latest_version>")
implementation("com.mikepenz:multiplatform-markdown-renderer-m3:<latest_version>")
```

> 版本号以 [Maven Central](https://search.maven.org/artifact/com.mikepenz/multiplatform-markdown-renderer-android) 发布的最新稳定版为准，两个 artifact 版本号需保持一致。

### 4.2 渲染策略（流式 vs 完成）

```text
消息状态
  │
  ├─ isLoading = true（流式进行中）
  │    └─ 纯文本 Text 展示累积 delta + ThinkingDotsAnimation
  │
  └─ isLoading = false（流式结束 / 历史消息）
       └─ MarkdownBubbleText 完整渲染
```

### 4.3 新建 MarkdownBubbleText 组件

新建文件 `ui/components/MarkdownBubbleText.kt`：

```kotlin
package com.example.scenic_avatar_guide_app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography

@Composable
fun MarkdownBubbleText(
    content: String,
    textColor: Color,
    linkColor: Color,
    codeBackgroundColor: Color,
    modifier: Modifier = Modifier
) {
    Markdown(
        content = content,
        modifier = modifier,
        colors = markdownColor(
            text = textColor,
            codeText = textColor,
            codeBackground = codeBackgroundColor,
            linkText = linkColor,
            dividerColor = textColor.copy(alpha = 0.2f)
        ),
        typography = markdownTypography(
            h1 = TextStyle(fontSize = 18.sp, color = textColor),
            h2 = TextStyle(fontSize = 16.sp, color = textColor),
            h3 = TextStyle(fontSize = 14.sp, color = textColor),
            paragraph = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, color = textColor),
            ordered = TextStyle(fontSize = 14.sp, color = textColor),
            bullet = TextStyle(fontSize = 14.sp, color = textColor),
            code = TextStyle(fontSize = 13.sp, color = textColor),
            quote = TextStyle(fontSize = 14.sp, color = textColor.copy(alpha = 0.75f))
        )
    )
}
```

### 4.4 MessageBubble 改造

在 `MainScreen.kt` 的 `MessageBubble` 内，替换现有文本渲染分支：

**改造前：**

```kotlin
if (safeContent.length > LONG_TEXT_MARKDOWN_LIMIT) {
    ChunkedMessageText(text = safeContent, color = bubbleTextColor)
} else {
    val annotatedText = remember(safeContent) { parseInlineMarkdown(safeContent) }
    Text(text = annotatedText, fontSize = 14.sp, lineHeight = 20.sp, color = bubbleTextColor)
}
```

**改造后：**

```kotlin
if (message.isLoading) {
    // 流式进行中：纯文本 + 动画，避免不完整 Markdown 语法闪烁
    Text(
        text = safeContent,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = bubbleTextColor
    )
} else {
    // 流式结束 / 历史消息：完整 Markdown 渲染
    MarkdownBubbleText(
        content = safeContent,
        textColor = bubbleTextColor,
        linkColor = MaterialTheme.colorScheme.primary,
        codeBackgroundColor = bubbleTextColor.copy(alpha = 0.08f),
        modifier = Modifier.fillMaxWidth()
    )
}
```

**注意：** 用户气泡（`isUser = true`）始终走纯文本 `Text`，不经过 Markdown 渲染，此逻辑在调用上面分支前已判断，无需改动。

### 4.5 删除废弃代码

完成上述改造后，以下函数和常量可安全删除：

| 函数 / 常量 | 所在行（参考） | 删除原因 |
|------------|--------------|----------|
| `parseInlineMarkdown()` | ~2273 行 | 被 Markdown 库替代 |
| `ChunkedMessageText()` | ~2200 行 | 长文本由库内部处理 |
| `chunkTextForRendering()` | ~2244 行 | 同上 |
| `LONG_TEXT_MARKDOWN_LIMIT` | 常量区 | 不再需要字符数限制 |
| `TEXT_RENDER_CHUNK_SIZE` | 常量区 | 同上 |

> `sanitizeRenderableText()` 保留，继续在渲染前调用，用于过滤控制字符。

---

## 五、样式规范

所有颜色来源于现有设计系统，无需新增颜色定义：

| Markdown 元素 | 用户气泡（isUser=true） | 助手气泡（isUser=false） |
|--------------|----------------------|------------------------|
| 正文文字 | `UserBubbleText` | `AssistantBubbleText` |
| 链接颜色 | `#2B59C3`（强调色） | `#2B59C3`（强调色） |
| 代码背景 | `UserBubbleText.copy(alpha=0.08f)` | `AssistantBubbleText.copy(alpha=0.08f)` |
| 引用块左边线 | `UserBubbleText.copy(alpha=0.3f)` | `AssistantBubbleText.copy(alpha=0.3f)` |
| 水平分割线 | `UserBubbleText.copy(alpha=0.2f)` | `AssistantBubbleText.copy(alpha=0.2f)` |
| H1 字号 | 18sp | 18sp |
| H2 字号 | 16sp | 16sp |
| H3 字号 | 14sp（与正文一致） | 14sp |
| 正文字号 | 14sp / 行高 20sp | 14sp / 行高 20sp |
| 代码字号 | 13sp | 13sp |

---

## 六、边界处理

### 6.1 纯文本消息

LLM 对简单问答（"几点开门？"）可能返回纯文本，无任何 Markdown 语法。Markdown 渲染库对纯文本透传无副作用，直接显示。

### 6.2 流式过程中的不完整语法

按第 3.2 节约定，流式进行中不解析 Markdown，仅展示原始字符串。收到 `done` 后 `isLoading` 变为 `false`，Compose 重新 composition，切换到 `MarkdownBubbleText` 渲染。切换时会有轻微重排，属于预期行为。

### 6.3 超长文本

移除原有 1200 字符硬限制。Markdown 库按节点树分段渲染，无需手动分块。若单条回复出现性能问题，后端可在生成阶段对最终文本设置 **5000 字符上限**（超出截断并追加提示语）；若返回 `done.full_text`，该字段应同步使用截断后的最终文本。

### 6.4 用户消息气泡

用户输入内容**不解析 Markdown**，始终以纯文本 `Text` 渲染，避免用户输入的 `*` 等字符被误渲染。此逻辑由 `isUser` 判断控制，在 `MessageBubble` 中的现有分支已覆盖，无需额外处理。

### 6.5 错误消息

`isError = true` 的消息气泡保持纯文本 `Text` 渲染，不经过 Markdown 解析，格式简洁。

### 6.6 空内容

`safeContent.isBlank()` 时不渲染 `MarkdownBubbleText`，继续走现有的 `ThinkingDotsAnimation` 逻辑，避免渲染库对空字符串的边界行为。

---

## 七、验收标准

### 7.1 后端验收

- [ ] System Prompt 末尾包含 §3.1 的 Markdown 格式约束块
- [ ] LLM 回复景点介绍时使用无序列表分项
- [ ] LLM 回复路线推荐时使用有序列表标注步骤
- [ ] LLM 简单问答（单句回答）不使用任何 Markdown
- [ ] 后端过滤函数支持清除 `![](url)` 和 HTML 标签，并支持将非 https 链接降级为纯文本
- [ ] 流式 `text_delta` 发送前至少过滤 HTML 标签
- [ ] 路线回复使用 Markdown 结构：路线步骤为有序列表，提醒为引用块
- [ ] 路线模式 TTS 使用 `clean_speech_text()` 后的纯文本合成，不直接朗读 Markdown 原文
- [ ] 当前版本不要求 `done.full_text`；若后端主动返回该可选字段，其内容应为清洗/截断后的权威最终文本

### 7.2 前端验收

- [ ] 助手气泡（`isLoading=false`）正确渲染：`# 标题`、`**粗体**`、`- 列表`、` ``` 代码块 ``` `、`> 引用`
- [ ] 流式进行中（`isLoading=true`）显示纯文本，Markdown 语法字符不闪烁
- [ ] 收到 `done` 后自动切换到 Markdown 渲染，无需手动刷新
- [ ] 用户气泡始终显示纯文本（输入 `**加粗**` 不触发粗体渲染）
- [ ] 链接颜色为 `#2B59C3`，点击可跳转（系统浏览器打开）
- [ ] 代码块灰色背景正确显示，等宽字体可读
- [ ] 气泡宽度（max 280dp）内，长单词换行正常，无水平溢出
- [ ] 历史消息重新进入页面后，Markdown 渲染结果与首次一致
- [ ] 500 字以上长消息渲染流畅，滑动不卡顿（目标：无丢帧）

---

## 八、版本历史

| 版本 | 日期 | 变更内容 |
|------|------|----------|
| v1.0 | 2026-05-09 | 初版，确立前后端 Markdown 渲染规范、语法白名单、实现步骤与验收标准 |
