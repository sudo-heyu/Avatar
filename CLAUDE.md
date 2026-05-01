# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

**景灵智导 (ScenicGuide Avatar)** 是一款景区游客数字导览 Android 应用，支持文本/语音问答、路线推荐和数字人播报功能。

**当前阶段**：第一阶段功能已完成，包含流式问答、Live2D 数字人、Edge-TTS 语音播报、端侧 ASR 语音识别、路线规划等核心能力。

## 构建命令

```bash
# 构建项目
./gradlew assembleDebug

# 构建正式版 APK
./gradlew assembleRelease

# 运行单元测试
./gradlew test

# 运行 Android 仪器测试（需连接设备或模拟器）
./gradlew connectedAndroidTest

# 清理构建
./gradlew clean

# Lint 检查
./gradlew lint
```

## 技术栈

- **语言**：Kotlin
- **UI**：Jetpack Compose
- **架构**：MVVM + Repository 模式
- **异步**：Coroutines + Flow
- **网络**：Retrofit + OkHttp（SSE 流式通过 OkHttp 直连）
- **序列化**：kotlinx.serialization
- **依赖注入**：Hilt
- **本地存储**：DataStore（配置、会话持久化）
- **数字人**：Live2D Cubism 4 Native + JNI
- **语音识别**：讯飞 SparkChain SDK（端侧 ASR）
- **语音播报**：后端 Edge-TTS + ExoPlayer，系统 TTS 兜底
- **最低 SDK**：24，目标 SDK：36

## 设计规范

```
主色：    #1D7A6D  （山湖青绿色）
辅色：    #F2A541  （暖阳橙，用于重点按钮）
强调色：  #2B59C3
错误色：  #C44536
背景色：  #F8FAF9
主文字：  #1C2328
次文字：  #5A6772
```

## 后端接口契约

Base URL 可配置（存储在 DataStore 中，便于开发/生产环境切换）。

### 统一响应格式
```json
{ "code": 0, "message": "ok", "data": {} }
```

### 当前正式接口
- `GET /api/v1/health` - 健康检查
- `POST /api/v1/session/create` - 创建会话（返回 session_id）
- `POST /api/v1/chat/text/stream` - 流式问答（SSE，返回 text_delta、tts_segment、tts_audio_chunk、done 等事件）
- `POST /api/v1/chat/abort` - 中止当前流式对话
- `POST /api/v1/tts/synthesize` - 文本合成音频（测试/降级用）
- `GET /api/v1/tts/voices` - 获取可用发音人列表
- `POST /api/v1/upload/image` - 图片上传（图文问答前置）

## 当前包结构

```
app/src/main/java/com/example/scenic_avatar_guide_app/
├── core/avatar/         # 数字人状态、播放管理、Live2D 渲染封装
├── core/audio/          # ExoPlayer 音频播放封装
├── core/network/        # Retrofit / OkHttp / Base URL 动态配置
├── core/speech/         # 讯飞端侧 ASR 封装
├── core/tts/            # TTS 抽象与实现（RemoteTTS / SystemTTS / StreamingTtsQueue）
├── core/common/         # UiState 等通用工具
├── data/local/          # DataStore 持久化
├── data/remote/         # ApiService (Retrofit)、StreamingChatClient (SSE)
├── data/repository/     # GuideRepository
├── domain/model/        # API 数据模型、Avatar 状态模型、路线规划模型
├── ui/components/       # AvatarView、ArcWaveform
├── ui/screens/          # MainScreen / MainViewModel / SettingsScreen
├── ui/theme/            # 主题与颜色
└── ui/navigation/       # 导航（预留）
```

## UI 状态管理

使用密封类 UiState 模式：
```kotlin
sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}
```

## 核心用户流程

1. **应用启动**：读取本地 sessionId -> 若无则调用 session/create -> 进入聊天页
2. **发送消息（流式）**：校验输入 -> 显示用户气泡 -> 调用 stream API -> text_delta 增量展示 -> tts_segment 排队播放 -> done 后恢复 IDLE
3. **语音输入**：长按录音 -> 端侧 ASR 识别 -> 自动填入输入框发送
4. **错误处理**：网络超时/服务异常 -> 显示重试按钮 -> 保留输入内容

## 聊天 UI 要求

- 输入框最多 300 字
- 用户气泡靠右（主色填充）
- 助手气泡靠左（白底 + 描边）
- 加载态显示点阵动画
- 自动滚动到最新消息
- 失败消息支持重试

## 文档参考

架构和需求文档位于：
- `app/src/main/java/com/example/scenic_avatar_guide_app/docs/product/APP_REQUIREMENTS.md` - Android 端需求基线
- `app/src/main/java/com/example/scenic_avatar_guide_app/docs/api/API_CONTRACT.md` - 移动端 API 接口契约
- `app/src/main/java/com/example/scenic_avatar_guide_app/docs/api/API_STREAMING.md` - 流式输入输出方案
- `app/src/main/java/com/example/scenic_avatar_guide_app/docs/backend/backend_architecture.md` - 后端对接基线与历史架构设计
