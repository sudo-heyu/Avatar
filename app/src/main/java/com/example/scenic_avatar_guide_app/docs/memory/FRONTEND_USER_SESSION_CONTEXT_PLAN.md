# 前端：用户、会话、历史记录与上下文管理 — 实施记录

版本：v1.1
日期：2026-05-02
状态：已完成（阶段一 + 阶段二 + 阶段三）
对标后端文档：`USER_SESSION_CONTEXT_PLAN.md` v2.0

---

## 一、架构原则

**后端负责持久化（SQLite/PostgreSQL），前端零本地数据库。**
前端只做：调后端 API + UI 展示 + 会话切换。

## 二、已实施内容

### 2.1 userId 格式对齐

- 文件：`data/repository/GuideRepository.kt`
- 改动：`"u_${UUID.take(8)}"` → `"guest_${UUID.replace(\"-\", \"\").take(16)}"`
- 示例：`u_a1b2c3d4` → `guest_a1b2c3d4e5f6g7h8`
- 与后端规范 `guest_{16位hex}` 对齐

### 2.2 新增 API 数据模型

- 文件：`domain/model/Models.kt`
- 新增：`SessionListResponse`、`SessionListData`、`SessionInfo`
- 新增：`SessionDetailResponse`、`SessionDetailData`、`MessageInfo`
- 新增：`ArchiveSessionRequest`、`ArchiveSessionResponse`、`DeleteSessionResponse`

### 2.3 ApiService 新增会话接口

- 文件：`data/remote/ApiService.kt`
- 新增：`GET /api/v1/session/list` — 获取会话列表
- 新增：`GET /api/v1/session/{id}` — 获取会话详情（含消息）
- 新增：`POST /api/v1/session/{id}/archive` — 归档会话
- 新增：`DELETE /api/v1/session/{id}` — 删除会话

### 2.4 SessionRepository（新增）

- 文件：`data/repository/SessionRepository.kt`
- 纯 API 封装，无本地缓存
- 方法：`getSessionList()`、`getSessionDetail()`、`archiveSession()`、`deleteSession()`
- 附带 `MessageInfo.toChatMessage()` 扩展函数

### 2.5 SessionListViewModel（新增）

- 文件：`ui/screens/SessionListViewModel.kt`
- 管理会话列表加载、归档、删除、恢复

### 2.6 SessionListScreen（新增）

- 文件：`ui/screens/SessionListScreen.kt`
- Compose UI：会话卡片列表（标题、最后消息、相对时间）
- 支持下拉刷新、归档/删除菜单、新建对话按钮
- 从左侧滑入，与 SettingsScreen 并存

### 2.7 MainViewModel 改造

- 文件：`ui/screens/MainViewModel.kt`
- 注入 `SessionRepository`
- 新增 `switchToSession(sessionId)`：从后端拉取历史消息恢复
- 新增 `startNewSession()`：创建全新会话

### 2.8 MainScreen 改造

- 文件：`ui/screens/MainScreen.kt`
- 新增 `onHistoryClick`、`switchToSessionId`、`newSessionTrigger` 参数
- TopBar 新增历史按钮（`Icons.Default.History`）
- `LaunchedEffect` 监听会话切换/新建事件

### 2.9 MainActivity 改造

- 文件：`MainActivity.kt`
- 新增 `SessionListScreen` 从左侧滑入的 `AnimatedVisibility`
- 持 `switchToSessionId` 和 `newSessionTrigger` 状态实现跨屏通信

## 三、数据流

### 应用启动
```
MainActivity → MainScreen → MainViewModel.init()
  → 读取 DataStore sessionId
  → 如果有 → 恢复会话（内存，无历史消息）
  → 如果无 → createNewSession() → API session/create
```

### 查看历史会话
```
用户点击 TopBar 历史图标
  → MainActivity showSessionList = true
  → SessionListScreen 显示
  → SessionListViewModel.loadSessions()
  → API GET /session/list → 显示卡片列表
```

### 恢复历史会话
```
用户点击会话卡片
  → MainActivity switchToSessionId = sessionId
  → MainScreen LaunchedEffect 触发
  → MainViewModel.switchToSession(sessionId)
  → API GET /session/{id} → 加载消息 → 更新 UI
```

### 新建会话
```
用户点击"新建对话"
  → MainActivity newSessionTrigger++
  → MainViewModel.startNewSession()
  → 清空消息 + createNewSession() → API session/create
```

## 四、变更文件清单

| 文件 | 操作 | 
|------|------|
| `data/repository/GuideRepository.kt` | 改 — userId 格式对齐 |
| `domain/model/Models.kt` | 改 — 新增会话相关数据类 |
| `data/remote/ApiService.kt` | 改 — 新增 4 个会话接口 |
| `data/repository/SessionRepository.kt` | 新增 |
| `ui/screens/SessionListViewModel.kt` | 新增 |
| `ui/screens/SessionListScreen.kt` | 新增 |
| `ui/screens/MainViewModel.kt` | 改 — 注入 SessionRepository + 新增方法 |
| `ui/screens/MainScreen.kt` | 改 — 新增参数 + TopBar 历史按钮 |
| `MainActivity.kt` | 改 — 新增 SessionListScreen 入口 |

## 五、依赖后端接口

前端依赖以下后端接口就绪：

| 接口 | 状态 |
|------|------|
| `POST /api/v1/session/create` | 已有 |
| `GET /api/v1/session/list` | 待后端实现 |
| `GET /api/v1/session/{id}` | 待后端实现 |
| `POST /api/v1/session/{id}/archive` | 待后端实现 |
| `DELETE /api/v1/session/{id}` | 待后端实现 |

后端接口就绪前，SessionListScreen 会显示空列表状态（不会崩溃）。
