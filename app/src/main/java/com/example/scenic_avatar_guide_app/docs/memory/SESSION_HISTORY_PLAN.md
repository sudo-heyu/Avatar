# 会话历史记录与上下文管理实施计划

版本：v1.0
日期：2026-04-30
状态：规划中

---

## 一、功能概述

### 1.1 目标

为用户提供会话历史记录功能，使用户能够：
- 查看历史会话列表
- 回到之前的会话状态继续对话
- 在多设备间同步会话历史

### 1.2 职责划分

| 端 | 职责 |
|---|---|
| **后端** | 会话持久化存储、上下文压缩与恢复、历史列表查询、会话状态管理 |
| **前端（Android）** | 进入应用时获取会话列表、展示历史会话、恢复会话时加载上下文 |

### 1.3 核心能力

1. **会话持久化**：每次对话自动保存，支持断点续聊
2. **上下文压缩**：长会话自动压缩，保留关键信息，降低存储和传输成本
3. **快速恢复**：用户选择历史会话后，快速恢复对话上下文
4. **会话归档**：自动归档过期会话，支持用户手动删除

---

## 二、数据模型设计

### 2.1 会话表（chat_session）

```sql
CREATE TABLE chat_session (
    id VARCHAR(36) PRIMARY KEY,              -- 会话 ID（UUID）
    user_id VARCHAR(36) NOT NULL,            -- 用户 ID
    scenic_id VARCHAR(36) NOT NULL,          -- 景区 ID
    status VARCHAR(20) DEFAULT 'active',     -- 状态：active / archived / deleted
    title VARCHAR(100),                       -- 会话标题（自动生成或首条消息摘要）
    message_count INT DEFAULT 0,              -- 消息总数
    context_summary TEXT,                     -- 压缩后的上下文摘要
    context_metadata JSON,                    -- 上下文元数据（关键实体、意图统计等）
    last_message_at TIMESTAMP,                -- 最后一条消息时间
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),

    INDEX idx_user_id (user_id),
    INDEX idx_scenic_id (scenic_id),
    INDEX idx_status (status),
    INDEX idx_last_message (last_message_at)
);
```

### 2.2 消息表（chat_message）

```sql
CREATE TABLE chat_message (
    id VARCHAR(36) PRIMARY KEY,              -- 消息 ID
    session_id VARCHAR(36) NOT NULL,         -- 会话 ID
    role VARCHAR(20) NOT NULL,               -- 角色：user / assistant
    content TEXT NOT NULL,                   -- 消息内容
    content_type VARCHAR(20) DEFAULT 'text', -- 内容类型：text / voice / image
    audio_url VARCHAR(255),                   -- 音频 URL（如有）
    avatar_action JSON,                       -- 数字人动作数据
    sources JSON,                             -- 知识库来源
    metadata JSON,                            -- 元数据（意图、情绪、延迟等）
    created_at TIMESTAMP DEFAULT NOW(),

    INDEX idx_session_id (session_id),
    INDEX idx_created_at (created_at),

    FOREIGN KEY (session_id) REFERENCES chat_session(id)
);
```

### 2.3 上下文快照表（context_snapshot）

```sql
CREATE TABLE context_snapshot (
    id VARCHAR(36) PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL,
    snapshot_type VARCHAR(20) NOT NULL,      -- 类型：full / compressed / summary
    message_range_start INT,                  -- 起始消息序号
    message_range_end INT,                    -- 结束消息序号
    context_data JSON NOT NULL,               -- 快照数据
    token_count INT,                          -- Token 数量估算
    created_at TIMESTAMP DEFAULT NOW(),

    INDEX idx_session_id (session_id),
    INDEX idx_snapshot_type (snapshot_type),

    FOREIGN KEY (session_id) REFERENCES chat_session(id)
);
```

---

## 三、后端接口设计

### 3.1 获取会话列表

**接口**：`GET /api/v1/session/list`

**请求参数**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| user_id | String | 是 | 用户 ID |
| scenic_id | String | 否 | 景区 ID（筛选特定景区） |
| status | String | 否 | 状态筛选：active / archived |
| page | Int | 否 | 页码，默认 1 |
| page_size | Int | 否 | 每页数量，默认 20，最大 50 |

**响应**：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "total": 15,
    "page": 1,
    "page_size": 20,
    "sessions": [
      {
        "session_id": "s_xxx",
        "title": "灵山大佛游览咨询",
        "status": "active",
        "message_count": 12,
        "last_message": "祝您游览愉快！",
        "last_message_at": "2026-04-30T14:30:00Z",
        "created_at": "2026-04-30T10:00:00Z"
      }
    ]
  }
}
```

---

### 3.2 获取会话详情（恢复会话）

**接口**：`GET /api/v1/session/{session_id}`

**请求参数**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| session_id | String | 是 | 会话 ID（路径参数） |
| include_messages | Boolean | 否 | 是否包含消息列表，默认 true |
| message_limit | Int | 否 | 消息数量限制，默认 50 |

**响应**：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "session_id": "s_xxx",
    "user_id": "u_001",
    "scenic_id": "scenic_001",
    "title": "灵山大佛游览咨询",
    "status": "active",
    "message_count": 12,
    "context_summary": "用户询问了灵山大佛的历史和游览路线，对梵宫建筑感兴趣...",
    "created_at": "2026-04-30T10:00:00Z",
    "messages": [
      {
        "message_id": "m_001",
        "role": "user",
        "content": "灵山大佛有多高？",
        "created_at": "2026-04-30T10:05:00Z"
      },
      {
        "message_id": "m_002",
        "role": "assistant",
        "content": "灵山大佛高达88米...",
        "avatar_action": { "expression": {"type": "excited"} },
        "sources": [...],
        "created_at": "2026-04-30T10:05:02Z"
      }
    ]
  }
}
```

---

### 3.3 归档会话

**接口**：`POST /api/v1/session/{session_id}/archive`

**请求**：

```json
{
  "session_id": "s_xxx"
}
```

**响应**：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "session_id": "s_xxx",
    "status": "archived"
  }
}
```

---

### 3.4 删除会话

**接口**：`DELETE /api/v1/session/{session_id}`

**响应**：

```json
{
  "code": 0,
  "message": "ok"
}
```

---

### 3.5 更新会话标题

**接口**：`PATCH /api/v1/session/{session_id}`

**请求**：

```json
{
  "title": "梵宫参观计划"
}
```

---

## 四、上下文压缩机制

### 4.1 压缩策略

#### 阶段一：滑动窗口（简单有效）

- 保留最近 N 条完整消息（N=10-20）
- 超出部分仅保留关键信息

```
[压缩区] ← [完整窗口区] ← [新消息]
   ↓            ↓
 摘要生成    正常对话
```

#### 阶段二：智能压缩（可选增强）

| 压缩层级 | 触发条件 | 压缩方式 |
|---------|---------|---------|
| 轻度压缩 | 消息数 > 20 | 合并相邻短消息 |
| 中度压缩 | 消息数 > 50 | 提取关键对话摘要 |
| 深度压缩 | 消息数 > 100 | 生成会话主题摘要 |

### 4.2 压缩算法

```
输入：原始消息列表 messages[]
输出：压缩后的上下文

1. 提取关键实体：
   - 景点名称（灵山大佛、梵宫、九龙灌浴...）
   - 用户意图（问路线、问历史、问餐饮...）
   - 时间约束（半天、2小时、上午...）
   - 偏好信息（喜欢拍照、行动不便...）

2. 生成摘要：
   - 使用 LLM 生成会话摘要
   - 摘要模板：[用户]询问了[X]，[助手]推荐了[Y]，用户表示[Z]

3. 保留关键对话：
   - 最近的 5-10 条完整消息
   - 包含重要决策的对话
   - 用户表达偏好的对话

4. 合并输出：
   {
     "summary": "用户计划半天游览灵山胜境，对梵宫建筑感兴趣...",
     "key_entities": ["灵山大佛", "梵宫", "九龙灌浴"],
     "user_preferences": ["喜欢拍照", "对佛教文化感兴趣"],
     "recent_messages": [...]
   }
```

### 4.3 压缩触发时机

| 场景 | 触发条件 | 操作 |
|-----|---------|------|
| 会话进行中 | 消息数达到阈值 | 异步压缩，不阻塞对话 |
| 会话归档 | 用户主动归档 | 立即压缩并存储快照 |
| 定时任务 | 每日凌晨 | 批量压缩活跃会话 |

### 4.4 Token 预算控制

```
总 Token 预算：4096

分配方案：
├── 系统提示词：500
├── 上下文摘要：800
├── 历史关键对话：1500
├── 最新完整对话：1000
└── 预留空间：296
```

---

## 五、前端集成方案

### 5.1 应用启动流程

```
应用启动
    │
    ▼
健康检查（/api/v1/health）
    │
    ▼
检查本地是否有 session_id
    │
    ├─ 有 → 验证会话是否有效 → 有效则恢复，无效则创建新会话
    │
    └─ 无 → 创建新会话
    │
    ▼
获取会话列表（/api/v1/session/list）
    │
    ▼
展示会话历史入口
```

### 5.2 会话恢复流程

```
用户点击历史会话
    │
    ▼
GET /api/v1/session/{session_id}
    │
    ▼
加载会话详情与消息列表
    │
    ▼
更新本地 session_id
    │
    ▼
渲染消息气泡
    │
    ▼
用户可继续对话
```

### 5.3 Android 数据模型

```kotlin
@Serializable
data class SessionInfo(
    @SerialName("session_id")
    val sessionId: String,

    @SerialName("title")
    val title: String? = null,

    @SerialName("status")
    val status: String,

    @SerialName("message_count")
    val messageCount: Int,

    @SerialName("last_message")
    val lastMessage: String? = null,

    @SerialName("last_message_at")
    val lastMessageAt: String? = null,

    @SerialName("created_at")
    val createdAt: String
)

@Serializable
data class SessionDetail(
    @SerialName("session_id")
    val sessionId: String,

    @SerialName("title")
    val title: String? = null,

    @SerialName("status")
    val status: String,

    @SerialName("message_count")
    val messageCount: Int,

    @SerialName("context_summary")
    val contextSummary: String? = null,

    @SerialName("messages")
    val messages: List<MessageData> = emptyList()
)
```

### 5.4 本地缓存策略

```kotlin
// 使用 DataStore 存储当前会话 ID
data class SessionPrefs(
    val currentSessionId: String? = null,
    val lastSyncTime: Long = 0
)

// 使用 Room 缓存会话列表和消息
@Entity(tableName = "session_cache")
data class SessionCache(
    @PrimaryKey val sessionId: String,
    val title: String?,
    val lastMessage: String?,
    val lastMessageAt: Long,
    val cachedAt: Long
)
```

---

## 六、实施阶段

### 第一阶段：基础存储（MVP）

**目标**：实现会话持久化，支持基本的历史记录查看

**后端任务**：
- [ ] 创建会话表和消息表
- [ ] 实现 `GET /api/v1/session/list`
- [ ] 实现 `GET /api/v1/session/{session_id}`
- [ ] 修改现有接口，自动保存消息到数据库
- [ ] 实现会话标题自动生成（首条消息截取）

**前端任务**：
- [ ] 新增会话历史列表页
- [ ] 实现会话恢复逻辑
- [ ] 本地缓存当前会话 ID

**预计工期**：3-5 天

---

### 第二阶段：压缩与优化

**目标**：实现上下文压缩，支持长会话场景

**后端任务**：
- [ ] 实现滑动窗口压缩算法
- [ ] 创建上下文快照表
- [ ] 实现异步压缩任务
- [ ] 优化 Token 预算控制

**前端任务**：
- [ ] 支持加载更多历史消息（分页）
- [ ] 会话搜索功能

**预计工期**：3-4 天

---

### 第三阶段：增强功能

**目标**：完善用户体验，支持会话管理

**后端任务**：
- [ ] 实现会话归档/删除接口
- [ ] 实现会话标题修改
- [ ] 实现会话搜索（按标题、内容）
- [ ] 实现智能摘要生成（LLM）

**前端任务**：
- [ ] 会话管理页面（归档、删除、重命名）
- [ ] 会话搜索 UI
- [ ] 多设备同步提示

**预计工期**：3-4 天

---

## 七、性能考量

### 7.1 查询优化

- 会话列表查询添加索引（user_id, status, last_message_at）
- 消息列表查询使用分页
- 热点数据缓存（Redis）

### 7.2 存储优化

- 消息内容使用压缩存储
- 音频文件使用对象存储，数据库只存 URL
- 超过 90 天的会话自动归档

### 7.3 传输优化

- 历史消息按需加载（首次加载最近 20 条）
- 压缩后的上下文摘要优先传输
- 支持增量同步

---

## 八、安全与隐私

### 8.1 数据隔离

- 用户只能访问自己的会话
- 会话查询强制带 user_id 过滤

### 8.2 数据保留

- 活跃会话：永久保留
- 归档会话：保留 180 天
- 已删除会话：软删除 30 天后物理删除

### 8.3 敏感信息处理

- 会话摘要不包含用户 PII
- 音频文件定期清理

---

## 九、监控与运维

### 9.1 关键指标

- 会话创建速率
- 会话平均消息数
- 压缩任务执行时间
- 历史查询响应时间

### 9.2 告警规则

- 会话存储空间使用率 > 80%
- 压缩任务失败率 > 5%
- 历史查询 P99 > 2s

---

## 十、风险与缓解

| 风险 | 影响 | 缓解措施 |
|-----|------|---------|
| 长会话上下文丢失 | 对话连贯性下降 | 保留关键实体，多级压缩 |
| 存储成本增长快 | 运营成本上升 | 自动归档，压缩存储 |
| 恢复会话加载慢 | 用户体验差 | 分页加载，预加载摘要 |
| 多设备同步冲突 | 数据不一致 | 最后写入优先，提示用户 |

---

## 十一、附录

### A. 参考文档

- [API_CONTRACT.md](../api/API_CONTRACT.md) - 现有 API 接口契约
- [backend_architecture.md](../backend/backend_architecture.md) - 后端架构设计

### B. 变更记录

| 版本 | 日期 | 变更 |
|------|------|------|
| v1.0 | 2026-04-30 | 初始版本 |
