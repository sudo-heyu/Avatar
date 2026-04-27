# 景灵智导 Android 端开发需求文档（可直接投喂 AI 执行）

版本：v1.0  
日期：2026-04-27  
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
2. 创建会话。
3. 文本问答。
4. 基础聊天 UI（消息列表、输入、发送、加载态、错误态）。
5. 可配置 API Base URL（开发联调必须）。
6. 统一错误提示与重试。

### 2.3 第二阶段范围（建议实现）

1. 语音问答入口（录音、上传、转写展示）。
2. 数字人播报区域（含动作状态占位）。
3. 游客偏好设置（兴趣标签、时长、出游偏好）。
4. 路线推荐与讲解卡片。
5. 会话历史查看。

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
3. ChatActivity 或 ChatComposeScreen。
4. SettingsDialog（API 地址、调试开关）。

### 5.3 页面结构要求（Chat 页）

1. 顶部栏：景区名、会话状态、网络状态。
2. 消息流：用户消息、助手消息、时间分组。
3. 输入区：文本输入、发送按钮、语音按钮占位。
4. 底部状态：加载中、重试、接口延迟。
5. 附加区：来源 sources 占位（后续接 RAG）。

---

## 6. 关键流程设计

### 6.1 首次进入流程（第一阶段）

1. App 启动。
2. 调用 health 探活。
3. 调用 session/create 获取 session_id。
4. 跳转 Chat 页。
5. 用户输入问题。
6. 调用 chat/text。
7. 展示 reply_text 与基础状态。

### 6.2 消息发送流程

1. 输入校验（非空、长度上限）。
2. 本地立即插入用户消息气泡。
3. 插入“思考中”占位消息。
4. 请求返回后替换占位为正式回答。
5. 错误时将占位转为“发送失败，可重试”。

### 6.3 弱网与失败流程

1. 请求超时：提示“网络不稳定，点击重试”。
2. 服务异常：展示 code/message。
3. 无网络：进入离线态，不清空输入框。
4. 重试成功后自动恢复消息连续性。

### 6.4 第二阶段语音流程（预留）

1. 长按录音。
2. 上传音频。
3. 返回文本答案 + audio_url。
4. 若 need_avatar=true，返回 avatar_action 并驱动播放区状态。

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

### 8.3 文本问答

接口：POST /api/v1/chat/text

请求示例：

```json
{
  "session_id": "s_xxx",
  "user_id": "u_001",
  "question": "你好，景区有哪些推荐路线？",
  "spot_id": "spot_001",
  "need_audio": false,
  "need_avatar": false
}
```

data 成功示例：

```json
{
  "message_id": "m_xxx",
  "session_id": "s_xxx",
  "reply_text": "建议先游览主景轴线，再前往湖区步道。",
  "audio_url": null,
  "avatar_action": null,
  "sources": [],
  "latency_ms": 1000,
  "created_at": "2026-04-27T12:35:30Z"
}
```

说明：

1. 当前阶段 sources 为空数组，需在 UI 中预留展示位。
2. need_avatar=true 时可能返回 avatar_action 占位结构。
3. 当 PPIO_KEY 为空时后端会返回 mock-answer，不影响联调流程。

---

## 9. 后续扩展接口契约（第二阶段建议）

以下接口来自架构规划，当前可能尚未落地，实现时请保持兼容：

1. POST /api/v1/chat/voice
2. POST /api/v1/chat/interrupt
3. GET /api/v1/session/history
4. GET/POST /api/v1/user/profile
5. GET /api/v1/route/recommend
6. GET /api/v1/scenic/info

扩展约束：

1. 继续使用 code/message/data 响应包装。
2. chat/text 与 chat/voice 结果结构尽量同构。
3. sources 结构固定后禁止频繁变更字段名。
4. 所有新增接口必须补示例请求与示例返回。

---

## 10. 聊天体验细节要求

### 10.1 文本输入体验

1. 输入框最多 300 字。
2. 为空时发送按钮置灰。
3. 支持换行输入，回车不直接发送。
4. 发送后自动清空并滚动到底部。

### 10.2 消息气泡规范

1. 用户气泡靠右，主色浅填充。
2. 助手气泡靠左，白底 + 描边。
3. 错误消息气泡使用警示色边框。
4. 时间戳按分钟粒度分组显示。

### 10.3 加载与反馈

1. 助手“思考中”点阵动画必须有。
2. 超过 2.5 秒显示“正在为你查找更准确信息”。
3. 超过 8 秒显示“网络较慢，可稍后重试”。
4. 成功返回后显示 latency_ms（debug 可见）。

### 10.4 安全与输入约束

1. 前端不记录敏感明文。
2. 日志中掩码用户唯一标识。
3. 非法空白字符统一清洗。
4. 连续快速点击发送需防抖。

---

## 11. 性能与稳定性指标

### 11.1 性能指标

1. 冷启动时间：< 2.0s（中端 Android 设备）。
2. Chat 首次可交互时间：< 1.2s。
3. 列表滚动保持 60fps 目标。
4. 单次文本问答 UI 不得卡顿超过 100ms。

### 11.2 稳定性指标

1. 连续发送 20 条消息不崩溃。
2. 断网重连后可继续对话。
3. session_id 丢失时可自动重建。
4. API 5xx/超时均有可恢复路径。

---

## 12. 埋点与可观测性要求

### 12.1 必要埋点事件

1. app_launch
2. health_check_result
3. session_create_result
4. chat_send
5. chat_response_success
6. chat_response_error
7. retry_click
8. voice_entry_click（预留）

### 12.2 关键埋点字段

1. session_id
2. message_id
3. network_type
4. request_latency_ms
5. error_code
6. is_retry

---

## 13. 测试与验收标准

### 13.1 功能验收（第一阶段必须通过）

1. 打开 App 能自动完成探活与会话创建。
2. 文本发送与回复完整可见。
3. 返回结构字段解析正确，无崩溃。
4. 异常场景有提示并支持重试。
5. 同一会话内连续问答流程稳定。

### 13.2 UI 验收

1. 页面布局一致，无明显错位。
2. 主色与辅助色使用统一。
3. 动效流畅，无突兀跳变。
4. 深浅色模式至少保证基础可读。

### 13.3 联调验收

1. 可在 debug 中切换 baseUrl。
2. 能完成 health -> session/create -> chat/text 全链路。
3. 对 code!=0 有统一处理策略。
4. 网络超时和 500 错误可复现并可恢复。

---

## 14. 项目交付清单（Android 侧）

### 14.1 代码交付

1. 完整 Android 工程。
2. 网络层、数据层、UI 层分层代码。
3. 至少包含 Home + Chat + Settings。
4. 基础单元测试与 UI 测试。

### 14.2 文档交付

1. README（运行方式、配置说明）。
2. 接口映射表（前端模型 <-> 后端字段）。
3. 错误码处理说明。
4. 已知问题与后续计划。

### 14.3 演示交付

1. 录屏：完整问答链路。
2. 截图：首页、对话页、错误态。
3. 演示脚本：3 个典型问题 + 1 个异常恢复流程。

---

## 15. AI 执行任务书（可直接复制给 AI）

将以下内容直接作为 AI 编码指令：

```text
你是资深 Android 工程师。请在一个新 Android 项目中实现“景灵智导”游客端第一阶段版本，技术栈为 Kotlin + Jetpack Compose + MVVM + Retrofit + Coroutines + Flow + Hilt。

必须实现：
1) 接口联调链路：GET /api/v1/health -> POST /api/v1/session/create -> POST /api/v1/chat/text。
2) UI 页面：Splash、Home、Chat、Settings。
3) Chat 页面支持消息列表、发送、加载态、失败重试、自动滚动、输入校验。
4) 统一解析响应格式：{ code, message, data }。
5) DataStore 保存 baseUrl、deviceId、最近 session_id。
6) 错误处理：超时、无网、服务异常分别提示且可重试。
7) 预留字段支持：audio_url、avatar_action、sources（即使第一阶段为空也要建模）。

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

## 16. 里程碑建议

### M1（1-2 天）

1. 项目脚手架。
2. 网络层 + 配置层。
3. health 与 session/create 打通。

### M2（2-3 天）

1. Chat 核心页面完成。
2. chat/text 打通。
3. 错误态与重试完善。

### M3（1-2 天）

1. UI 细节打磨。
2. 埋点与日志完善。
3. 测试与演示材料输出。

---

## 17. 风险与规避

1. 接口字段变更风险。
规避：以 app-client-contract 和本文件字段为准，新增字段只增不改。

2. 后端阶段性未实现语音接口。
规避：前端先做占位模块与 Feature Flag。

3. 第三方模型延迟波动。
规避：前端显示分级加载文案并支持重试。

4. 多成员并行开发冲突。
规避：按页面/模块切分分支，固定每日接口对齐。

---

## 19. 语音识别（ASR）实现说明

### 19.1 技术选型

项目使用 **讯飞 SparkChain SDK** 作为语音识别引擎：

| 项目 | 说明 |
|------|------|
| SDK 名称 | SparkChain ASR |
| 依赖文件 | `libs/SparkChain.aar`、`libs/Codec.aar` |
| 采样率 | 16kHz |
| 语言 | 中文（zh_cn） |
| 领域 | iat（通用听写） |
| 方言 | 普通话（mandarin） |

### 19.2 核心实现文件

```
core/speech/
├── SpeechRecognizerHelper.kt   # 语音识别辅助类（Kotlin）
└── XunfeiAsrCallback.java      # 讯飞 ASR 回调适配器（Java）
```

### 19.3 SpeechRecognizerHelper 核心功能

```kotlin
class SpeechRecognizerHelper(
    private val context: Context,
    private val onResult: (String) -> Unit,           // 识别成功回调
    private val onError: (String) -> Unit,            // 错误回调
    private val onReadyForSpeech: () -> Unit,         // 准备就绪回调
    private val onEndOfSpeech: () -> Unit,            // 说话结束回调
    private val onVolumeChanged: ((Float) -> Unit)?   // 音量变化回调（可选）
)
```

**主要方法：**

| 方法 | 说明 |
|------|------|
| `hasPermission()` | 检查录音权限 |
| `startListening()` | 开始录音和识别 |
| `stopListening()` | 停止录音并发送识别 |
| `cancel()` | 取消录音和识别 |
| `destroy()` | 销毁资源 |

### 19.4 UI 集成（MainScreen）

语音输入 UI 位于 `MainScreen.kt`，主要特性：

1. **VoiceInputSection 组件**
   - 长按录音，松开发送
   - 上滑取消录音
   - 波纹动画效果（音量可视化）
   - 震动反馈

2. **交互流程**
   ```
   点击麦克风按钮 → 请求权限 → 进入语音模式
   长按录音按钮 → 开始录音 → 显示波纹动画
   松开 → 停止录音 → 发送识别 → 显示结果
   上滑 → 取消录音
   ```

3. **状态管理（MainViewModel）**
   - `voiceInputMode: Boolean` - 语音输入模式状态
   - `isRecording: Boolean` - 录音状态
   - `volumeLevel: Float` - 音量级别（0-1）

### 19.5 权限配置

**AndroidManifest.xml：**
```xml
<!-- 语音识别权限 -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<!-- 震动权限 -->
<uses-permission android:name="android.permission.VIBRATE" />
```

**build.gradle.kts 依赖：**
```kotlin
// 讯飞语音识别 SDK
implementation(files("libs/SparkChain.aar"))
implementation(files("libs/Codec.aar"))
```

### 19.6 识别状态码

| status | 说明 |
|--------|------|
| 0 | 首帧/中间结果 |
| 1 | 中间结果 |
| 2 | 最终结果（仅此状态触发 onResult 回调） |

### 19.7 ASR 配置参数

```kotlin
asr?.language("zh_cn")      // 语言：中文
asr?.domain("iat")          // 领域：通用听写
asr?.accent("mandarin")     // 方言：普通话
asr?.vinfo(true)            // 启用语音信息
asr?.dwa("wpgs")            // 动态修正
```

### 19.8 音量计算

实时音量通过 RMS（均方根）计算：

```kotlin
// 计算 RMS
var sumSquares = 0.0
val sampleCount = read / 2
for (i in 0 until read step 2) {
    val sample = ((buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)).toShort()
    sumSquares += sample.toDouble() * sample.toDouble()
}
val rms = sqrt(sumSquares / sampleCount)
val normalizedVolume = (rms / 32767.0).coerceIn(0.0, 1.0).toFloat()
```

### 19.9 错误处理

| 错误类型 | 处理方式 |
|----------|----------|
| 权限缺失 | 提示用户授权 |
| 启动失败 | 显示错误信息 |
| 识别错误 | 回调 onError |
| 无识别结果 | 不显示消息，静默退出 |

### 19.10 资源释放

```kotlin
// 在 Composable 中自动释放
DisposableEffect(Unit) {
    onDispose { speechHelper.destroy() }
}
```

### 19.11 后续扩展计划

1. **语音上传**：将音频上传到后端进行服务端识别
2. **语音问答**：集成后端 `/api/v1/chat/voice` 接口
3. **打断功能**：支持用户打断数字人播报
4. **双工交互**：全双工语音交互模式

---

## 20. 最终结论

本需求文档可以直接驱动 Android 第一阶段开工，并与现有后端最小联调链路完全对齐。  
后续仅需按里程碑逐步接入语音、数字人、RAG 与路线推荐，即可平滑演进到完整比赛版产品。
