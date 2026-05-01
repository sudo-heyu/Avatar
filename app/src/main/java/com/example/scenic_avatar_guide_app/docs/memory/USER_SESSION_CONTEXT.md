# 用户、会话与上下文管理架构

## 一、架构概览

```
┌─────────────────────────────────────────────────────────────────────────┐
│                              API Layer                                   │
│  /session/create  /session/list  /session/{id}  /chat/text/stream       │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                           Service Layer                                  │
│  SessionService          ChatService           ContextService           │
│  - 会话验证              - 消息保存             - 上下文构建             │
│  - 归属检查              - 流式处理             - 历史注入               │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                         Repository Layer                                 │
│  SessionRepository                    MessageRepository                  │
│  - CRUD 操作                          - CRUD 操作                       │
│  - 查询方法                           - 历史消息查询                     │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                           Model Layer                                    │
│  Session (会话)                        Message (消息)                    │
│  - id, user_id, scenic_id             - id, session_id, role            │
│  - status, title, context_summary     - content, avatar_action          │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                          Database Layer                                  │
│                        SQLite (单文件数据库)                              │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 二、数据模型

### 2.1 Session (会话表)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | VARCHAR(20) | 会话 ID，格式: `s_{timestamp}_{random}` |
| user_id | VARCHAR(36) | 用户 ID |
| scenic_id | VARCHAR(36) | 景区 ID |
| spot_id | VARCHAR(36) | 景点 ID (可选) |
| device_id | VARCHAR(64) | 设备 ID (可选) |
| status | VARCHAR(20) | 状态: active / archived / deleted |
| title | VARCHAR(100) | 会话标题 |
| message_count | INTEGER | 消息总数 |
| context_summary | TEXT | 上下文摘要 (压缩后) |
| context_entities | JSON | 关键实体 (景点、意图、偏好) |
| last_message_at | DATETIME | 最后消息时间 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

### 2.2 Message (消息表)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | VARCHAR(20) | 消息 ID，格式: `m_{timestamp}_{random}` |
| session_id | VARCHAR(20) | 所属会话 ID (外键) |
| role | VARCHAR(20) | 角色: user / assistant |
| content | TEXT | 消息内容 |
| content_type | VARCHAR(20) | 内容类型: text / voice / image |
| audio_url | VARCHAR(255) | 音频 URL |
| avatar_action | JSON | 数字人动作数据 |
| sources | JSON | 知识库来源 |
| emotion | VARCHAR(20) | 情绪标签 |
| intent | VARCHAR(50) | 意图标签 |
| latency_ms | INTEGER | 响应延迟 |
| created_at | DATETIME | 创建时间 |

---

## 三、核心流程

### 3.1 会话创建流程

```
Client                         API                    Service                 Database
   │                            │                        │                       │
   │ POST /session/create       │                        │                       │
   │ {user_id, scenic_id}       │                        │                       │
   │───────────────────────────>│                        │                       │
   │                            │ create_session()       │                       │
   │                            │───────────────────────>│                       │
   │                            │                        │ generate_session_id() │
   │                            │                        │──────────────────────>│
   │                            │                        │                       │
   │                            │                        │ INSERT INTO session   │
   │                            │                        │──────────────────────>│
   │                            │                        │                       │
   │                            │<───────────────────────│                       │
   │<───────────────────────────│ {session_id, ...}      │                       │
```

### 3.2 聊天请求流程

```
Client                         API                    Service                 Database
   │                            │                        │                       │
   │ POST /chat/text/stream     │                        │                       │
   │ {session_id, user_id, ...} │                        │                       │
   │───────────────────────────>│                        │                       │
   │                            │ validate_session()     │                       │
   │                            │───────────────────────>│                       │
   │                            │                        │ SELECT FROM session   │
   │                            │                        │ WHERE id=? AND user_id=?
   │                            │                        │──────────────────────>│
   │                            │                        │<──────────────────────│
   │                            │<───────────────────────│                       │
   │                            │                        │                       │
   │                            │ build_context()        │                       │
   │                            │───────────────────────>│                       │
   │                            │                        │ SELECT FROM message   │
   │                            │                        │ WHERE session_id=?    │
   │                            │                        │ ORDER BY created_at   │
   │                            │                        │ LIMIT 10              │
   │                            │                        │──────────────────────>│
   │                            │                        │<──────────────────────│
   │                            │<───────────────────────│                       │
   │                            │                        │                       │
   │                            │ LLM 生成回复            │                       │
   │                            │───────────────────────>│                       │
   │                            │                        │                       │
   │<─── SSE: message_start ────│                        │                       │
   │<─── SSE: text_delta ───────│                        │                       │
   │<─── SSE: tts_segment ──────│                        │                       │
   │<─── SSE: done ─────────────│                        │                       │
   │                            │                        │                       │
   │                            │ save_message()         │                       │
   │                            │───────────────────────>│                       │
   │                            │                        │ INSERT INTO message   │
   │                            │                        │──────────────────────>│
```

---

## 四、安全性设计

### 4.1 会话归属验证

每次聊天请求都会验证 session_id 是否属于该 user_id：

```python
# SessionService.validate_session()
def validate_session(self, session_id: str, user_id: str) -> Session:
    session = self.session_repo.get_active_by_id_and_user(session_id, user_id)
    if not session:
        session_exists = self.session_repo.get_by_id(session_id)
        if not session_exists:
            raise NotFoundError(f"Session {session_id} not found")
        raise ForbiddenError(f"Session does not belong to user")
    return session
```

### 4.2 错误响应

| 场景 | HTTP 状态码 | 业务码 | 说明 |
|------|------------|--------|------|
| 会话不存在 | 200 | 404 | Session not found |
| 会话不属于用户 | 200 | 403 | Access forbidden |
| 会话已归档 | 200 | 404 | Session not active |

---

## 五、上下文管理

### 5.1 上下文构建策略

```
历史消息 (最近10条)
       │
       ▼
┌─────────────────────────────────────────┐
│  User: 灵山大佛有多高？                   │
│  Assistant: 灵山大佛高达88米...           │
│  User: 现在可以参观吗？                   │
│  Assistant: 现在开放时间为...             │
└─────────────────────────────────────────┘
       │
       ▼
构建上下文文本
       │
       ▼
注入 System Prompt
```

### 5.2 Token 预算控制

```
总预算: 4096 tokens

├── System Prompt: ~500 tokens
│   └── 角色定义 + 输出格式指令 + 情绪标注
│
├── 上下文摘要: ~500 tokens
│   └── 历史对话压缩摘要
│
├── 最近对话: ~1500 tokens
│   └── 最近 10 条完整消息
│
├── 当前问题: ~200 tokens
│
└── 预留空间: ~1396 tokens
    └── LLM 回复缓冲
```

### 5.3 ContextService 实现

```python
class ContextService:
    DEFAULT_RECENT_MESSAGES = 10
    
    def build_context(self, session_id: str, max_messages: int = None) -> str:
        messages = self.message_repo.list_by_session(
            session_id, limit=max_messages or self.DEFAULT_RECENT_MESSAGES
        )
        
        if not messages:
            return ""
        
        parts = []
        for msg in messages:
            if msg.role == "user":
                parts.append(f"User: {msg.content}")
            elif msg.role == "assistant":
                content = msg.content[:200] if len(msg.content) > 200 else msg.content
                parts.append(f"Assistant: {content}")
        
        return "\n".join(parts)
```

---

## 六、文件结构

```
backend/app/
├── core/
│   ├── database.py          # SQLAlchemy 配置
│   ├── exceptions.py        # 异常定义 (NotFoundError, ForbiddenError)
│   └── config.py            # 配置 (DATABASE_URL)
│
├── models/
│   ├── __init__.py
│   ├── session.py           # Session 模型
│   └── message.py           # Message 模型
│
├── repositories/
│   ├── __init__.py
│   ├── session_repo.py      # 会话数据访问
│   └── message_repo.py      # 消息数据访问
│
├── services/
│   ├── session_service.py   # 会话业务逻辑
│   ├── chat_service.py      # 聊天服务
│   └── context_service.py   # 上下文管理
│
├── api/v1/
│   ├── session.py           # 会话接口
│   └── chat.py              # 聊天接口
│
└── main.py                  # 应用入口 (init_db)
```

---

## 七、API 接口

### 7.1 会话管理

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/session/create` | 创建会话 |
| GET | `/session/list` | 获取会话列表 |
| GET | `/session/{id}` | 获取会话详情 (恢复会话) |
| POST | `/session/{id}/archive` | 归档会话 |
| DELETE | `/session/{id}` | 删除会话 |
| PATCH | `/session/{id}` | 更新会话标题 |

### 7.2 聊天接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/chat/text` | 同步聊天 |
| POST | `/chat/text/stream` | 流式聊天 (SSE) |
| POST | `/chat/abort` | 中断流式响应 |

---

## 八、数据流向

### 8.1 创建会话

```json
// POST /session/create
{
  "user_id": "guest_abc123",
  "scenic_id": "lingshan",
  "spot_id": "buddha",
  "device_id": "android_xxx"
}

// Response
{
  "code": 0,
  "data": {
    "session_id": "s_12345678_a1b2",
    "user_id": "guest_abc123",
    "scenic_id": "lingshan",
    "status": "active",
    "created_at": "2026-05-02T10:00:00Z"
  }
}
```

### 8.2 流式聊天

```json
// POST /chat/text/stream
{
  "session_id": "s_12345678_a1b2",
  "user_id": "guest_abc123",
  "scenic_id": "lingshan",
  "question": "灵山大佛有多高？",
  "mode": "chat"
}

// SSE Events
event: message_start
data: {"type":"message_start","message_id":"m_xxx","session_id":"s_xxx"}

event: avatar_action
data: {"type":"avatar_action","data":{...}}

event: text_delta
data: {"type":"text_delta","delta":"灵山大佛高达"}

event: tts_segment
data: {"type":"tts_segment","audio_url":"/api/v1/tts/file/xxx.mp3",...}

event: done
data: {"type":"done","message_id":"m_xxx","session_id":"s_xxx"}
```

---

## 九、数据库配置

### 9.1 SQLite 配置

```python
# backend/app/core/database.py
DATABASE_URL = "sqlite:///./data/guide.db"

engine = create_engine(
    DATABASE_URL,
    connect_args={"check_same_thread": False},  # SQLite 单进程必需
    echo=settings.APP_ENV == "dev",
)
```

### 9.2 表初始化

```python
# backend/app/main.py
@asynccontextmanager
async def lifespan(app: FastAPI):
    init_db()  # 创建表
    ...

# backend/app/core/database.py
def init_db():
    Base.metadata.create_all(bind=engine)
```

---

## 十、性能考量

### 10.1 查询优化

- `user_id` 字段建立索引
- `session_id` + `user_id` 复合查询
- 消息列表按 `created_at` 排序

### 10.2 存储策略

- 小规模测试 (~50用户): SQLite 足够
- 生产扩展: 可迁移至 PostgreSQL

### 10.3 备份方案

```bash
# 复制数据库文件即可备份
cp ./data/guide.db ./backup/guide_$(date +%Y%m%d).db
```

---

## 十一、错误处理

### 11.1 异常层次

```
BusinessException (基类)
├── NotFoundError (404) - 资源不存在
├── ForbiddenError (403) - 无权访问
└── ValidationError (400) - 参数校验失败
```

### 11.2 统一响应格式

```json
// 成功
{"code": 0, "message": "ok", "data": {...}}

// 失败
{"code": 404, "message": "Session not found", "data": {}}
```

---

## 十二、扩展点

### 12.1 上下文压缩

当消息数超过阈值时，可调用 LLM 生成摘要：

```python
def compress_context(messages: list[Message]) -> str:
    # 使用 LLM 生成摘要
    summary = llm_client.simple_chat(
        f"请压缩以下对话历史: {[m.content for m in messages]}"
    )
    return summary
```

### 12.2 会话过期

```python
# 定时任务清理过期会话
def cleanup_expired_sessions():
    threshold = datetime.utcnow() - timedelta(days=90)
    db.query(Session).filter(
        Session.last_message_at < threshold,
        Session.status == "active"
    ).update({"status": "archived"})
```

### 12.3 多设备同步

通过 `device_id` 字段支持多设备登录，同一用户可在多设备查看相同会话历史。
