# 景灵智导 Android 端需求基线

版本：v2.3  
日期：2026-04-29  
适用仓库：`scenic_avatar_guide_app`  
变更：
1. TTS 方案由端侧讯飞切换为后端 Edge-TTS
2. 交互模式由三种缩减为两种：聊天问答 + 路线规划

---

## 1. 文档定位

本文件只描述当前 Android 客户端的正式联调基线。  
字段与接口以 [API_CONTRACT.md](../architecture/API_CONTRACT.md) 为准；若未来规划与当前实现冲突，以 API 契约和源码为准。

---

## 2. 当前正式范围

### 2.1 正式 HTTP 接口

当前 Android 端依赖以下接口：

**核心问答接口（已有）**：
1. `GET /api/v1/health`
2. `POST /api/v1/session/create`
3. `POST /api/v1/chat/text`

**TTS 接口（Edge-TTS 方案新增）**：
4. `POST /api/v1/tts/synthesize` — 文本合成音频
5. `GET /api/v1/tts/voices` — 获取可用发音人列表
6. `GET /api/v1/tts/file/{file_name}` — 读取音频文件

### 2.2 当前交互能力

1. 文本输入问答
2. 端侧 ASR 识别后转文本问答
3. 后端 TTS 合成 `reply_text`，返回 `audio_url`
4. Android 端使用 ExoPlayer 播放音频
5. 基于 `avatar_action` 与 `marks` 的数字人状态联动
6. Base URL、用户 ID、设备 ID、会话 ID 的本地存储
7. 右上角设置页可配置后端 `IP / 端口 / 协议` 并持久化保存

### 2.3 不属于当前正式契约的内容

以下内容只能视为后续扩展，不应作为当前联调前提：

1. `POST /api/v1/chat/voice`
2. 服务端 ASR 作为正式主链路
3. `marks` 词级时间戳驱动口型（第一阶段可用字符估算兜底）
4. 独立的路线推荐、游客偏好、会话历史 HTTP 接口
5. 管理后台、OpenAvatarChat、LiteAvatar 等完整后端部署方案

说明：

1. `audio_url` 与 `/api/v1/tts/*` 自本版本起纳入当前正式契约，属于 Edge-TTS 方案必需接口。
2. 端侧讯飞 TTS 与系统 TTS 仅作为 **网络异常时的降级兜底**，不再是主链路。

---

## 3. 当前实现概览

### 3.1 页面现状

当前 `MainActivity` 直接加载 `MainScreen`，主流程为单页聊天界面：

1. 顶部栏
2. 数字人区域
3. 消息流
4. 模式切换（聊天问答 / 路线规划）
5. 文本输入 / 拍照上传 / 长按语音输入
6. 动作测试面板

代码中提供了 `SettingsScreen.kt` 和 `SettingsViewModel.kt`，并已接入主页面顶部“设置”按钮，用于配置后端地址和查看会话信息。

### 3.2 当前启动流程

```text
App 启动
→ 进入 MainScreen
→ 读取本地 sessionId
→ 若无会话则调用 /api/v1/session/create
→ 插入本地欢迎语
```

说明：

1. `health` 接口已在 `ApiService` 与 `GuideRepository` 中定义。
2. 当前 `MainViewModel` 默认启动流程不会自动调用 `health`。
3. `health` 更适合用于设置页、诊断页或显式探活按钮。
4. 设置页保存后端地址后，后续请求会按最新配置动态改写目标主机和端口。

---

## 4. 当前页面与流程

### 4.1 当前页面

| 页面/模块 | 当前状态 | 说明 |
|-----------|----------|------|
| MainScreen | 已接入 | 对话主页面 |
| SettingsScreen | 已接入 | 后端地址、IP/端口配置与会话信息 |
| Route/Profile 等独立页面 | 未纳入正式联调 | 不应假定已有独立接口 |

### 4.2 当前交互流程

**聊天问答模式**：
```text
输入文本、拍照或本地 ASR 识别成功
→ 插入用户消息
→ 调用 /api/v1/chat/text (mode=chat)
→ 展示 reply_text + sources
→ 调用 /api/v1/tts/synthesize 请求音频
→ ExoPlayer 播放 audio_url
→ 数字人进入 SPEAKING / 恢复 IDLE
```

**路线规划模式**：
```text
输入路线偏好或本地 ASR 识别成功
→ 插入用户消息
→ 调用 /api/v1/chat/text (mode=route)
→ 展示 reply_text + route_data（地图 + 景点卡片）
→ 调用 /api/v1/tts/synthesize 请求音频
→ ExoPlayer 播放 audio_url
→ 数字人进入 SPEAKING / 恢复 IDLE
```

### 4.3 当前数字人播放流程

```text
收到响应
→ 解析 reply_text / sources / latency_ms / confidence / is_fallback
→ 若存在 avatar_action / metadata，则进一步消费增强字段
→ 请求后端 /api/v1/tts/synthesize 获取 audio_url
→ ExoPlayer 播放音频
→ 播放过程中驱动口型（marks 优先，无 marks 则字符估算）
→ 数字人动作完成后恢复待机
```

Edge-TTS 音频由后端生成，Android 端通过 `audio_url` 播放。

---

## 5. 技术方案

### 5.1 当前技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Kotlin |
| UI | Jetpack Compose |
| 架构 | MVVM + Repository |
| 异步 | Coroutines + Flow |
| 网络 | Retrofit + OkHttp |
| 序列化 | kotlinx.serialization |
| 依赖注入 | Hilt |
| 本地存储 | DataStore |
| 数字人 | Live2D Native + JNI |
| 语音识别 | 讯飞 SparkChain SDK（端侧） |
| 语音播报 | 后端 Edge-TTS + ExoPlayer 播放（主链路） |
| TTS 兜底 | Android System TTS（无网络时降级） |

### 5.2 当前关键目录

```text
app/src/main/java/com/example/scenic_avatar_guide_app/
├── core/avatar/         # 数字人状态、播放管理、Live2D 渲染封装
├── core/network/        # Retrofit / OkHttp / Base URL 配置
├── core/speech/         # 讯飞 ASR 封装
├── core/tts/            # TTS 抽象与实现
│   ├── TTSProvider.kt              # TTS 统一接口
│   ├── RemoteTTSController.kt      # 后端 Edge-TTS 调用（主链路）
│   └── SystemTTSController.kt      # Android 系统 TTS（降级兜底）
├── core/audio/          # ExoPlayer 音频播放封装
├── data/local/          # DataStore
├── data/remote/         # ApiService（聊天、TTS、图片上传接口）
├── data/repository/     # GuideRepository
├── domain/model/        # API 数据模型、Avatar 状态模型、路线规划模型
├── ui/components/       # AvatarView、波形组件
└── ui/screens/          # MainScreen / MainViewModel / SettingsScreen
```

另外，Live2D JNI 桥和 C++ 代码位于：

```text
app/src/main/java/com/example/scenic_avatar_guide_app/core/avatar/
app/src/main/cpp/
app/src/main/jniLibs/
```

### 5.3 状态管理

当前主状态以 `StateFlow` 为中心，重点包括：

1. `messages`
2. `inputText`
3. `isLoading`
4. `avatarState`
5. `avatarFullState`
6. `voiceInputMode`
7. `isRecording`
8. `volumeLevel`

---

## 6. 聊天体验要求

### 7.1 文本输入

- 输入框最多 300 字
- 为空时发送按钮置灰
- 发送后自动清空并滚动到底部

### 7.2 消息气泡

- 用户气泡靠右，主色填充
- 助手气泡靠左，白底 + 描边
- 错误消息使用警示色边框

### 7.3 加载与反馈

- 显示"思考中"点阵动画
- 超过 2.5 秒显示"正在查找更准确信息"
- 超过 8 秒显示"网络较慢，可稍后重试"

---

## 7. 性能指标

| 指标 | 目标 |
|------|------|
| 冷启动时间 | < 2.0s |
| Chat 首次可交互 | < 1.2s |
| 列表滚动 | 60fps |
| 连续发送消息 | 20 条不崩溃 |

---

## 8. 测试验收

### 9.1 功能验收

- [x] 无会话时能自动创建会话
- [x] 文本发送与回复完整可见
- [x] 返回结构字段解析正确
- [x] 异常场景有提示并支持重试
- [x] 连续问答流程稳定

### 9.2 UI 验收

- [x] 页面布局一致
- [x] 主色与辅助色使用统一
- [x] 动效流畅

### 9.3 联调验收

- [x] 可切换 baseUrl
- [x] 完成 session/create → chat/text 主链路
- [x] health 可用于显式探活
- [x] 对 code!=0 有统一处理
- [x] 网络超时和错误可恢复
- [x] Edge-TTS 音频合成与播放正常
- [x] 系统 TTS 降级兜底正常

---

## 9. 语音识别（ASR）

### 10.1 技术选型

| 项目 | 说明 |
|------|------|
| SDK | 讯飞 SparkChain ASR |
| 采样率 | 16kHz |
| 语言 | 中文（zh_cn） |
| 领域 | iat（通用听写） |

### 10.2 核心文件

```
core/speech/
├── SpeechRecognizerHelper.kt   # 语音识别辅助类
└── XunfeiAsrCallback.java      # 讯飞回调适配器
```

### 10.3 交互流程

```
点击麦克风 → 请求权限 → 长按录音 → 松开发送 → 显示结果
上滑取消
```

### 10.4 权限

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.VIBRATE" />
```

---

## 10. 当前实现差异

以下是当前代码状态，文档需按事实理解：

1. `MainViewModel` 当前已优先消费后端返回的 `avatar_action`，若缺失则按 `intent / emotion` 做动作与表情降级。
2. `health` 已在网络层和仓库层定义，但未纳入默认启动流程。
3. `SettingsScreen` 已从主页面导航进入，并支持保存 `IP / 端口 / 协议`。

---

## 11. 参考文档

- [API 接口契约](../architecture/API_CONTRACT.md)
- [Live2D 开发计划](../architecture/AVATAR_LIVE2D_PLAN.md)
- [ASR 实现说明](./ASR_IMPLEMENTATION.md)
