# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

**景灵智导 (ScenicGuide Avatar)** 是一款景区游客数字导览 Android 应用，支持文本/语音问答、路线推荐和数字人播报功能。

**当前阶段**：早期开发阶段，仅有基础项目脚手架，需实现第一阶段功能。

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
- **UI**：Jetpack Compose（优先）或 XML + ViewModel
- **架构**：MVVM + Repository 模式
- **异步**：Coroutines + Flow
- **网络**：Retrofit + OkHttp
- **依赖注入**：Hilt（推荐）
- **本地存储**：DataStore（配置）、Room（可选，用于聊天历史）
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

### 第一阶段接口（必须实现）
- `GET /api/v1/health` - 健康检查
- `POST /api/v1/session/create` - 创建会话（返回 session_id）
- `POST /api/v1/chat/text` - 文本问答（返回 reply_text、sources[]、latency_ms）
- `POST /api/v1/chat/text/stream` - 流式问答（返回 text_delta、tts_segment、done 等事件）

### 需要建模的预留字段（第一阶段可能为空）
- `avatar_action`、`sources`、`tts_segment.audio_url` - 为语音/数字人和流式分段 TTS 功能预留

## 推荐包结构

```
app/ui/              # Activity、Composable、ViewModel
app/navigation/      # 导航逻辑
feature/chat/        # 聊天功能模块
feature/session/     # 会话管理
feature/settings/    # API 配置、调试设置
data/remote/         # Retrofit 接口、DTO
data/local/          # DataStore、Room
domain/model/        # 业务模型
domain/usecase/      # 用例
core/network/        # 网络客户端配置
core/common/         # 通用工具
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

## 核心用户流程（第一阶段）

1. **应用启动**：健康检查 -> 创建会话 -> 跳转聊天页
2. **发送消息**：校验输入 -> 显示用户气泡 -> 显示加载态 -> 展示回复
3. **错误处理**：网络超时/服务异常 -> 显示重试按钮 -> 保留输入内容

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
- `app/src/main/java/com/example/scenic_avatar_guide_app/docs/api/STREAMING_REFACTOR_PLAN.md` - 流式输入输出重构方案
- `app/src/main/java/com/example/scenic_avatar_guide_app/docs/backend/backend_architecture.md` - 后端对接基线与历史架构设计
