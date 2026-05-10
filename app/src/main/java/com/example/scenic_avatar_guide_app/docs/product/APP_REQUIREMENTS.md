# 景灵智导 Android 端开发需求文档


版本：v2.0  
日期：2026-05-07  
适用仓库：scenic-avatar-guide

---

## 1. 文档目标

本文件用于定义 Android 游客端的完整开发需求，目标是：

1. 第一阶段快速打通最小联调闭环。
2. 第二阶段平滑扩展到语音、数字人、RAG、路线推荐。
3. UI 要求美观、清晰、可演示，交互流程顺畅。
4. 需求粒度足够细，能够直接作为 AI 开发任务输入。

本文件依据以下现有文档整理：

1. docs/architecture/backend-issues-and-fixes.md
2. docs/architecture/final_backend_architecture.md
3. docs/architecture/week1_completion_table.md

并结合当前已落地后端接口现状（health/session/chat）做了“可开发落地化”补充。

---

## 2. 产品定位与范围

### 2.1 产品定位

景灵智导 Android App 是景区游客侧数字导览入口，核心价值：

1. 让游客通过文本或语音与数字导游自然交互。
2. 快速获得景点讲解、路线建议、游玩决策支持。
3. 在后续阶段支持数字人播报、个性化偏好和反馈闭环。

### 2.2 第一阶段范围（必须实现）

1. 健康检查探活。
2. 创建会话（支持持久化）。
3. 文本问答（消息自动保存）。
4. 流式问答（SSE，`text_delta` 增量展示 + `tts_segment_ready` 分段播放）。
5. 打字机效果（TTS 同步逐字展示，超时兜底）。
6. 自动续写（流异常断开时自动续写，最多 3 次）。
7. 基础聊天 UI（消息列表、输入、发送、加载态、错误态）。
8. 语音输入（端侧 ASR：长按录音、本地识别、自动发送）。
9. 用户认证（注册/登录/登出，guest ID 兜底）。
10. 满意度反馈（单条消息评分/投诉/留言）。
11. 会话历史侧边栏（列表、恢复、归档、删除）。
12. 可配置 API Base URL（开发联调必须）。
13. 统一错误提示与重试。

### 2.3 第二阶段范围（建议实现）

1. 数字人播报区域（含动作状态占位）。
2. 游客偏好设置（兴趣标签、时长、出游偏好）。
3. 路线推荐与讲解卡片。
4. 图文问答（图片上传 + 图文混合输入）。

### 2.4 第三阶段范围（可选增强）

1. 打断与双工交互。
2. GPS 场景触发推荐。
3. 拍照识景/VLM 扩展。
4. 离线缓存增强与弱网优化。

---

## 3. 设计原则（UI 美观 + 交互顺畅）

### 3.1 视觉方向

采用“自然景区 + 现代导览”视觉语言：

1. 主色：山湖青绿系，传达自然感与可信度。
2. 辅色：暖阳橙用于重点 CTA 和状态反馈。
3. 中性色：高可读灰阶，用于信息层级。
4. 背景：柔和渐变与轻纹理，避免单调纯色。

### 3.2 设计 Token（建议）

1. Primary：#1D7A6D
2. Secondary：#F2A541
3. Accent：#2B59C3
4. Success：#2E9E5B
5. Warning：#D9822B
6. Error：#C44536
7. Surface：#F8FAF9
8. Text Primary：#1C2328
9. Text Secondary：#5A6772

### 3.3 字体与排版

1. 中文主字体：Noto Sans SC。
2. 英文与数字：Inter。
3. 标题字号梯度：32/24/20/18。
4. 正文字号梯度：16/14/12。
5. 行高建议：$1.4\sim1.6$ 倍。

### 3.4 动效规范

1. 页面入场：250ms 淡入 + 轻位移。
2. 消息出现：120ms 纵向展开。
3. 发送按钮反馈：80ms 缩放回弹。
4. 错误提示：轻震动 + 颜色提醒。

### 3.5 交互原则

1. 输入到响应全程可感知（加载点、状态文案）。
2. 失败可恢复（重试按钮、保留输入内容）。
3. 重要动作可撤销或二次确认。
4. 手势与点击区域符合单手操作。

---

## 4. 用户角色与核心场景

### 4.1 用户角色

1. 首次到访游客：需要快速获取推荐路线与景点介绍。
2. 深度游游客：希望持续提问并获得准确、可执行建议。
3. 弱网络游客：需要稳定、可恢复的对话体验。

### 4.2 核心场景

1. 入园前快速问“怎么逛最省时间”。
2. 到达景点后问“这个景点看点是什么”。
3. 人多或天气变化时问“替代路线建议”。
4. 结束游玩后反馈满意度与建议。

---

## 5. 信息架构与页面清单

### 5.1 一级页面

1. 启动页 Splash。
2. 首页 Home（推荐入口 + 快捷问题）。
3. 对话页 Chat（第一阶段核心页面）。
4. 路线页 Route（第二阶段）。
5. 偏好页 Profile（第二阶段）。
6. 我的页 Mine（设置、日志开关、版本信息）。

### 5.2 第一阶段必须页面

1. SplashActivity / SplashScreen。
2. HomeActivity 或 HomeComposeScreen。
3. ChatActivity 或 ChatComposeScreen（含会话历史侧边栏）。
4. SettingsDialog（API 地址、调试开关）。
5. AuthDialog（注册/登录/登出）。
6. FeedbackDialog（评分/投诉/留言）。

### 5.3 页面结构要求（Chat 页）

1. 顶部栏：景区名、会话状态、网络状态、用户头像（点击展开认证/登出）。
2. 消息流：用户消息、助手消息、时间分组；助手消息支持长按触发反馈。
3. 输入区：文本输入、发送按钮、语音按钮。
4. 底部状态：加载中、重试、接口延迟。
5. 附加区：来源 sources 展示（RAG 已接入）。
6. 侧边栏：会话历史列表（恢复、归档、删除），支持滑出/滑入。

---

## 6. 关键流程设计

### 6.1 首次进入流程（第一阶段）

1. App 启动。
2. 调用 health 探活。
3. 读取本地 userId（已登录用户 / guest ID）。
4. 调用 session/create 获取 session_id。
5. 跳转 Chat 页。
6. 用户输入问题（文本或语音）。
7. 调用 chat/text/stream（流式）。
8. `text_delta` 增量展示，`tts_segment_ready` 分段播放。
9. 展示 reply_text、来源、路线卡片等。

### 6.2 消息发送流程（流式）

1. 输入校验（非空、长度上限 300 字）。
2. 本地立即插入用户消息气泡。
3. 插入“思考中”占位消息。
4. 调用 `chat/text/stream`。
5. `text_delta` 增量追加到机器人消息气泡（打字机效果）。
6. `tts_segment_ready` 到达后排队播放音频。
7. `done` 到达后关闭 loading，等待 TTS 队列自然播完。
8. 若收到 `PrematurelyEnded`，触发自动续写（最多 3 次）。
9. 错误时标记消息为“发送失败，可重试”。

### 6.3 弱网与失败流程

1. 请求超时：提示“网络不稳定，点击重试”。
2. 服务异常：展示 code/message。
3. 无网络：进入离线态，不清空输入框。
4. 重试成功后自动恢复消息连续性。

### 6.4 语音输入流程（端侧 ASR，已实现）

1. 长按语音按钮 → 震动反馈 → 开始录音。
2. 讯飞 SparkChain SDK 本地实时识别。
3. 上滑取消区域 → 松开后取消。
4. 松开（非取消区）→ 停止识别 → 文本填入输入框。
5. 自动触发消息发送流程（走 `chat/text/stream`）。

### 6.5 用户认证流程

1. 点击顶部用户头像 → 展开认证面板。
2. 未登录状态：显示注册/登录选项。
3. 注册：`POST /api/v1/auth/register` → 保存 userId / username → 同步更新会话。
4. 登录：`POST /api/v1/auth/login` → 保存 userId / username → 拉取该用户会话列表。
5. 登出：清除本地认证状态 → 生成新 guest ID → 清空当前会话列表。

### 6.6 满意度反馈流程

1. 长按任意助手消息 → 弹出 FeedbackDialog。
2. 用户选择 1-5 星评分。
3. 1-2 星可勾选“投诉”并填写留言。
4. 点击提交 → `POST /api/v1/chat/feedback`。
5. 反馈成功后显示轻提示，关闭弹窗。

### 6.7 会话管理流程

1. 点击顶部栏菜单按钮 → 滑出会话历史侧边栏。
2. 展示当前用户会话列表（`GET /api/v1/session/list`）。
3. 点击会话 → 恢复该会话历史消息（`GET /api/v1/session/{id}`）。
4. 左滑会话项 → 归档（`POST /api/v1/session/{id}/archive`）。
5. 继续左滑 → 删除（`DELETE /api/v1/session/{id}`）。

---

## 7. Android 技术方案要求

### 7.1 开发栈

1. Kotlin。
2. Jetpack Compose（优先）或 XML + ViewModel。
3. MVVM + Repository 分层。
4. Coroutines + Flow。
5. Retrofit + OkHttp。
6. kotlinx.serialization 或 Moshi。
7. Hilt（建议）用于依赖注入。

### 7.2 包结构建议

1. app/ui
2. app/navigation
3. feature/chat
4. feature/session
5. feature/settings
6. data/remote
7. data/local
8. domain/model
9. domain/usecase
10. core/network
11. core/common

### 7.3 状态管理规范

1. 页面状态统一使用 UiState（Loading/Success/Error/Empty）。
2. 单次事件使用 Event Channel（Toast、导航、弹窗）。
3. 聊天消息流使用不可变列表，避免错位刷新。
4. 所有接口请求必须具备超时与取消能力。

### 7.4 本地存储

1. DataStore 存储配置项（baseUrl、deviceId、用户偏好）。
2. Room 存储会话摘要与最近消息（可选）。
3. 日志开关仅在 debug 可见。

---

## 8. 与后端联调契约（当前已实现）

统一返回格式：

```json
{
  "code": 0,
  "message": "ok",
  "data": {}
}
```

### 8.1 健康检查

接口：GET /api/v1/health  
用途：探活

成功示例：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "service": "ai-tour-guide-backend",
    "status": "running"
  }
}
```

### 8.2 创建会话

接口：POST /api/v1/session/create

请求示例：

```json
{
  "user_id": "u_001",
  "scenic_id": "scenic_001",
  "spot_id": "spot_001",
  "device_id": "android_001"
}
```

data 成功示例：

```json
{
  "session_id": "s_xxx",
  "user_id": "u_001",
  "scenic_id": "scenic_001",
  "spot_id": "spot_001",
  "device_id": "android_001",
  "status": "active",
  "created_at": "2026-04-27T12:34:56Z"
}
```

### 8.3 文本问答（非流式）

接口：POST /api/v1/chat/text

请求示例：

```json
{
  "session_id": "s_xxx",
  "user_id": "u_001",
  "scenic_id": "scenic_001",
  "question": "你好，景区有哪些推荐路线？",
  "spot_id": "spot_001",
  "mode": "chat"
}
```

data 成功示例：

```json
{
  "message_id": "m_xxx",
  "session_id": "s_xxx",
  "reply_text": "建议先游览主景轴线，再前往湖区步道。",
  "avatar_action": null,
  "sources": [],
  "latency_ms": 1000,
  "created_at": "2026-04-27T12:35:30Z"
}
```

说明：

1. 当前阶段 sources 为空数组，需在 UI 中预留展示位。
2. `mode` 可选 `chat`（聊天问答）或 `route`（路线规划）。

### 8.4 文本问答（流式，推荐）

接口：POST /api/v1/chat/text/stream

请求头：
```http
Accept: text/event-stream
Content-Type: application/json
```

请求示例：

```json
{
  "session_id": "s_xxx",
  "user_id": "u_001",
  "scenic_id": "scenic_001",
  "question": "请介绍灵山大佛",
  "mode": "chat",
  "options": {
    "need_avatar": true,
    "voice": "zh-CN-XiaoxiaoNeural"
  }
}
```

事件示例：

```text
event: message_start
data: {"type":"message_start","message_id":"m_xxx","session_id":"s_xxx"}

event: text_delta
data: {"type":"text_delta","delta":"欢迎来到灵山胜境，"}

event: tts_segment
data: {"type":"tts_segment","segment_id":"seg_001","segment_index":0,"text":"欢迎来到灵山胜境，","audio_url":"/api/v1/tts/file/seg_001.mp3","voice":"zh-CN-XiaoxiaoNeural"}

event: tts_segment_ready
data: {"type":"tts_segment_ready","segment_id":"seg_001","segment_index":0,"audio_url":"/api/v1/tts/file/seg_001.mp3","duration_ms":1800,"marks":[{"word":"欢迎","start_ms":0,"end_ms":420}]}

event: avatar_action
data: {"type":"avatar_action","data":{"expression":{"type":"happy"},"gesture":{"type":"guide"}}}

event: done
data: {"type":"done","message_id":"m_xxx","session_id":"s_xxx","full_text":"可选，完整助手回复文本"}
```

说明：

1. Android 端应优先使用流式接口，降低首屏等待时间。
2. `text_delta` 按打字机效果逐字追加展示（TTS 同步或 1.5s 超时兜底）。
3. `tts_segment_ready` 到达后直接播放 `audio_url`，用 `marks` 驱动口型同步。
4. `tts_audio_chunk` / `tts_audio_end` 后端仍发送，但 Android 端已忽略。
5. `done.full_text` 为可选字段；存在时优先作为最终 Markdown 渲染源，不存在时使用已累积的 `text_delta`。
6. EOF 未收到终止事件时生成 `PrematurelyEnded`，触发自动续写（最多 3 次）。
7. 非流式接口 `chat/text` 作为降级路径保留。

---

## 9. TTS 接口

### 9.1 获取发音人列表

接口：GET /api/v1/tts/voices

### 9.2 合成语音

接口：POST /api/v1/tts/synthesize

请求示例：

```json
{
  "text": "您好，欢迎来到景灵智导。",
  "voice": "zh-CN-XiaoxiaoNeural",
  "format": "audio_with_marks"
}
```

响应示例：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "audio_url": "/api/v1/tts/file/tts_xxx.mp3",
    "duration_ms": 2052,
    "marks": [
      { "text": "欢", "start_ms": 0, "end_ms": 256, "phonemes": ["h", "u", "an"] }
    ]
  }
}
```

### 9.3 下载音频

接口：GET /api/v1/tts/file/{file_name}

---

## 10. 已实现接口清单

以下接口已实现，可直接对接：

### 10.1 会话管理接口
1. `POST /api/v1/session/create` — 创建会话
2. `GET /api/v1/session/list` — 查询会话列表（支持分页、状态筛选）
3. `GET /api/v1/session/{id}` — 获取会话详情（恢复历史会话）
4. `POST /api/v1/session/{id}/archive` — 归档会话
5. `DELETE /api/v1/session/{id}` — 删除会话

### 10.2 聊天接口
1. `POST /api/v1/chat/text` — 同步文本问答（降级路径）
2. `POST /api/v1/chat/text/stream` — 流式文本问答（SSE，主链路）
3. `POST /api/v1/chat/abort` — 取消流式回答
4. `POST /api/v1/chat/feedback` — 满意度反馈上报

### 10.3 TTS 接口
1. `GET /api/v1/tts/voices` — 获取发音人列表
2. `POST /api/v1/tts/synthesize` — 文本合成
3. `GET /api/v1/tts/file/{file_name}` — 音频文件读取

### 10.4 用户认证接口
1. `POST /api/v1/auth/register` — 用户注册
2. `POST /api/v1/auth/login` — 用户登录

## 11. 后续扩展接口契约（第二阶段建议）

以下接口来自架构规划，当前尚未落地，实现时请保持兼容：

1. `POST /api/v1/chat/voice`（语音问答）
2. `GET/POST /api/v1/user/profile`
3. `GET /api/v1/route/recommend`
4. `GET /api/v1/scenic/info`

扩展约束：

1. 继续使用 code/message/data 响应包装。
2. 流式与非流式结果结构尽量同构。
3. sources 结构固定后禁止频繁变更字段名。
4. 所有新增接口必须补示例请求与示例返回。

---

## 12. 聊天体验细节要求

### 12.1 文本输入体验

1. 输入框最多 300 字。
2. 为空时发送按钮置灰。
3. 支持换行输入，回车不直接发送。
4. 发送后自动清空并滚动到底部。

### 12.2 消息气泡规范

1. 用户气泡靠右，主色浅填充。
2. 助手气泡靠左，白底 + 描边。
3. 错误消息气泡使用警示色边框。
4. 时间戳按分钟粒度分组显示。

### 12.3 打字机效果与 TTS 同步

1. 助手回复采用打字机逐字展示，模拟自然说话节奏。
2. 打字机与 TTS 播放同步：首个 `tts_segment_ready` 到达后才开始展示文字。
3. 若 TTS 超过 1.5 秒未就绪，直接开始展示文字（超时兜底）。
4. `text_delta` 持续追加到同一消息，与打字机队列无缝衔接。

### 12.4 自动续写机制

1. 流式响应异常断开（`PrematurelyEnded`）时，自动触发续写。
2. 基于当前已展示文本构造续写提示词，重新调用 `chat/text/stream`。
3. 续写流的首个 `text_delta` 追加到同一消息气泡，用户无感知。
4. 最多续写 3 次；超过上限则标记消息为“回答中断，请重试”。

### 12.5 加载与反馈

1. 助手“思考中”点阵动画必须有。
2. 超过 2.5 秒显示“正在为你查找更准确信息”。
3. 超过 8 秒显示“网络较慢，可稍后重试”。
4. 成功返回后显示 latency_ms（debug 可见）。

### 12.6 满意度反馈

1. 长按任意助手消息 → 弹出 FeedbackDialog。
2. 支持 1-5 星评分，1-2 星可标记为投诉。
3. 可选填写留言（最多 200 字）。
4. 未登录游客也可提交反馈。

### 12.7 安全与输入约束

1. 前端不记录敏感明文。
2. 日志中掩码用户唯一标识。
3. 非法空白字符统一清洗。
4. 连续快速点击发送需防抖。

---

## 13. 性能与稳定性指标

### 13.1 性能指标

1. 冷启动时间：< 2.0s（中端 Android 设备）。
2. Chat 首次可交互时间：< 1.2s。
3. 列表滚动保持 60fps 目标。
4. 单次文本问答 UI 不得卡顿超过 100ms。
5. 流式首字延迟：< 1.5s。
6. TTS 首段播放延迟：< 2.5s。

### 13.2 稳定性指标

1. 连续发送 20 条消息不崩溃。
2. 断网重连后可继续对话。
3. session_id 丢失时可自动重建。
4. API 5xx/超时均有可恢复路径。
5. 自动续写机制在弱网下可恢复 80% 以上的中断回答。

---

## 14. 埋点与可观测性要求

### 14.1 必要埋点事件

1. app_launch
2. health_check_result
3. session_create_result
4. chat_send
5. chat_response_success
6. chat_response_error
7. retry_click
8. voice_entry_click
9. auth_register / auth_login / auth_logout
10. feedback_submit
11. session_switch / session_archive / session_delete
12. stream_prematurely_ended / stream_continuation_attempt

### 14.2 关键埋点字段

1. session_id
2. message_id
3. network_type
4. request_latency_ms
5. error_code
6. is_retry

---

## 15. 测试与验收标准

### 15.1 功能验收（第一阶段必须通过）

1. 打开 App 能自动完成探活、认证状态恢复与会话创建。
2. 文本发送与回复完整可见，打字机效果自然。
3. 流式 TTS 分段播放正常，口型同步可用。
4. 返回结构字段解析正确，无崩溃。
5. 异常场景有提示并支持重试；弱网下自动续写可恢复。
6. 同一会话内连续问答流程稳定。
7. 语音输入（长按录音 → ASR → 自动发送）流程完整。
8. 注册/登录/登出功能正常，认证状态持久化。
9. 满意度反馈可正常提交。
10. 会话历史侧边栏可展示、恢复、归档、删除。

### 15.2 UI 验收

1. 页面布局一致，无明显错位。
2. 主色与辅助色使用统一。
3. 动效流畅，无突兀跳变。
4. 深浅色模式至少保证基础可读。

### 15.3 联调验收

1. 可在 debug 中切换 baseUrl。
2. 能完成 health -> session/create -> chat/text/stream 全链路。
3. 对 code!=0 有统一处理策略。
4. 网络超时和 500 错误可复现并可恢复。
5. 认证、反馈、会话管理接口联调通过。

---

## 16. 项目交付清单（Android 侧）

### 16.1 代码交付

1. 完整 Android 工程。
2. 网络层、数据层、UI 层分层代码。
3. 至少包含 Home + Chat + Settings + Auth + Feedback。
4. 基础单元测试与 UI 测试。

### 16.2 文档交付

1. README（运行方式、配置说明）。
2. 接口映射表（前端模型 <-> 后端字段）。
3. 错误码处理说明。
4. 已知问题与后续计划。

### 16.3 演示交付

1. 录屏：完整问答链路（含语音输入、认证、反馈）。
2. 截图：首页、对话页、错误态、认证弹窗、反馈弹窗。
3. 演示脚本：3 个典型问题 + 1 个异常恢复流程 + 1 个语音问答。

---

## 17. AI 执行任务书（可直接复制给 AI）

将以下内容直接作为 AI 编码指令：

```text
你是资深 Android 工程师。请在一个新 Android 项目中实现"景灵智导"游客端第一阶段版本，技术栈为 Kotlin + Jetpack Compose + MVVM + Retrofit + Coroutines + Flow + Hilt。

必须实现：
1) 接口联调链路：GET /api/v1/health -> POST /api/v1/session/create -> POST /api/v1/chat/text/stream（流式 SSE）。
2) 会话管理：GET /api/v1/session/list -> GET /api/v1/session/{id}（恢复历史会话）、归档、删除。
3) UI 页面：Splash、Home、Chat、Settings、SessionHistory、AuthDialog、FeedbackDialog。
4) Chat 页面支持消息列表、发送、加载态、失败重试、自动滚动、输入校验。
5) 打字机效果：TTS 同步逐字展示，1.5s 超时兜底。
6) 自动续写：PrematurelyEnded 时自动续写，最多 3 次。
7) 统一解析响应格式：{ code, message, data }。
8) DataStore 保存 baseUrl、deviceId、最近 session_id、认证状态。
9) 错误处理：超时、无网、服务异常分别提示且可重试；支持 404（会话不存在）、403（无权访问）错误码。
10) 预留字段支持：audio_url、avatar_action、sources、route_data。
11) SSE 流式解析：支持 text_delta、tts_segment、tts_segment_ready、done、PrematurelyEnded 事件类型；tts_audio_chunk/tts_audio_end 忽略。
12) 用户认证：注册/登录/登出，guest ID 兜底，认证状态持久化。
13) 满意度反馈：单条消息评分 1-5 星、投诉标记、留言提交。
14) 语音输入：长按录音、讯飞端侧 ASR、自动填入输入框发送。

UI 视觉要求：
1) 自然景区风格，主色 #1D7A6D，辅色 #F2A541。
2) 聊天气泡有层次，背景避免纯白单色，加入轻渐变。
3) 消息出现和发送按钮有轻动效。

代码质量要求：
1) 分层清晰：ui/domain/data/core。
2) 关键类补充简洁注释。
3) 提供 8 个以上单元测试（ViewModel 与 Repository 为主）。
4) 提供至少 2 个 Compose UI 测试（发送消息、错误重试）。

交付要求：
1) 输出完整目录树。
2) 输出所有关键文件代码。
3) 输出运行步骤与联调说明。
4) 输出后续二阶段扩展位（voice、route、profile）的 TODO 标记。
```

---

## 18. 里程碑建议

### M1（1-2 天）

1. 项目脚手架。
2. 网络层 + 配置层 + 认证模块。
3. health、session/create、auth 打通。

### M2（2-3 天）

1. Chat 核心页面完成（含打字机、流式 TTS、自动续写）。
2. chat/text/stream 打通。
3. 错误态与重试完善。
4. 语音输入（ASR）接入。

### M3（1-2 天）

1. UI 细节打磨（AuthDialog、FeedbackDialog、会话侧边栏）。
2. 满意度反馈与会话管理对接。
3. 测试与演示材料输出。

---

## 19. 风险与规避

1. 接口字段变更风险。
   规避：以 app-client-contract 和本文件字段为准，新增字段只增不改。

2. 后端阶段性未实现语音接口。
   规避：前端 ASR 为端侧本地能力（讯飞 SparkChain），不依赖后端语音接口。

3. 第三方模型延迟波动。
   规避：前端显示分级加载文案、打字机效果、自动续写机制兜底。

4. 多成员并行开发冲突。
   规避：按页面/模块切分分支，固定每日接口对齐。

---

## 20. 最终结论

本需求文档已对齐 Android 端当前最新实现：流式问答、打字机效果、自动续写、端侧 ASR、用户认证、满意度反馈、会话管理均已落地。  
后续仅需按里程碑逐步接入数字人完整播报、RAG 来源展示、路线推荐卡片与图文问答，即可平滑演进到完整比赛版产品。
