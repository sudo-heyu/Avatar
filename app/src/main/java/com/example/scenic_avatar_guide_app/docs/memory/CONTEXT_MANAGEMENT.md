# 上下文管理设计

## 一、架构概览

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           ChatService                                    │
│                     stream_chat_events()                                 │
└─────────────────────────────────────────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                         ContextService                                   │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐         │
│  │  build_context  │  │get_context_msgs │  │  Token 控制      │         │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘         │
└─────────────────────────────────────────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                       MessageRepository                                  │
│  ┌─────────────────┐  ┌─────────────────┐                              │
│  │ list_by_session │  │get_recent_by_   │                              │
│  │                 │  │   session       │                              │
│  └─────────────────┘  └─────────────────┘                              │
└─────────────────────────────────────────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                          SQLite                                          │
│                        chat_message                                      │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 二、上下文构建流程

### 2.1 完整流程

```
用户请求
    │
    ▼
┌─────────────────────────────────────────┐
│ 1. SessionService.validate_session()    │
│    验证会话有效性和用户归属               │
└─────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────┐
│ 2. ContextService.build_context()       │
│    查询最近 N 条历史消息                  │
└─────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────┐
│ 3. 格式化为 "User: xxx\nAssistant: xxx" │
│    助手消息截断至 200 字符                │
└─────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────┐
│ 4. 注入 System Prompt                   │
│    添加上下文段落                        │
└─────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────┐
│ 5. 发送给 LLM                           │
│    流式生成回复                         │
└─────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────┐
│ 6. MessageRepository.create()           │
│    保存用户消息和助手回复                 │
└─────────────────────────────────────────┘
```

### 2.2 System Prompt 构建

```python
# backend/app/services/chat_service.py

def _build_system_prompt_with_context(
    self,
    session_id: str,
    scenic_id: str,
    mode: str,
    knowledge_context: str | None = None,
) -> str:
    # 1. 基础角色定义
    base_prompt = self._build_system_prompt(scenic_id, mode)
    
    # 2. 添加知识库上下文（如果有）
    if knowledge_context:
        base_prompt += f"\n\n以下是可用知识库资料：\n{knowledge_context}"
    
    # 3. 添加历史对话上下文
    context_service = self._get_context_service()
    if context_service:
        context = context_service.build_context(session_id)
        if context:
            base_prompt += f"\n\n以下是与该用户的历史对话上下文：\n{context}\n"
    
    return base_prompt
```

---

## 三、ContextService 实现

### 3.1 核心代码

```python
# backend/app/services/context_service.py

class ContextService:
    DEFAULT_RECENT_MESSAGES = 10

    def __init__(self, db: DbSession):
        self.db = db
        self.message_repo = MessageRepository(db)

    def build_context(
        self,
        session_id: str,
        max_messages: int = None,
    ) -> str:
        """构建上下文字符串，用于注入 System Prompt"""
        try:
            messages = self.message_repo.list_by_session(
                session_id,
                limit=max_messages or self.DEFAULT_RECENT_MESSAGES,
            )

            if not messages:
                return ""

            parts = []
            for msg in messages:
                if msg.role == "user":
                    parts.append(f"User: {msg.content}")
                elif msg.role == "assistant":
                    # 助手消息截断至 200 字符
                    content = msg.content[:200] if len(msg.content) > 200 else msg.content
                    parts.append(f"Assistant: {content}")

            return "\n".join(parts)
        except Exception as e:
            logger.warning(f"ContextService.build_context failed: {e}")
            return ""

    def get_context_messages(
        self,
        session_id: str,
        max_messages: int = None,
    ) -> list[dict]:
        """获取上下文消息列表，用于多轮对话 API"""
        try:
            messages = self.message_repo.list_by_session(
                session_id,
                limit=max_messages or self.DEFAULT_RECENT_MESSAGES,
            )

            return [
                {"role": msg.role, "content": msg.content}
                for msg in messages
            ]
        except Exception as e:
            logger.warning(f"ContextService.get_context_messages failed: {e}")
            return []
```

### 3.2 关键参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| DEFAULT_RECENT_MESSAGES | 10 | 默认加载最近 10 条消息 |
| 助手消息截断长度 | 200 字符 | 防止上下文过长 |

---

## 四、Token 预算控制

### 4.1 Token 分配

```
总预算: 4096 tokens (DeepSeek 模型)

├── System Prompt: ~600 tokens
│   ├── 角色定义: ~200 tokens
│   ├── 情绪标注指令: ~150 tokens
│   ├── avatar_combo 指令: ~200 tokens
│   └── 输出格式说明: ~50 tokens
│
├── 知识库上下文: ~500 tokens
│   └── RAG 检索结果
│
├── 历史对话上下文: ~1000 tokens
│   └── 最近 10 条消息（助手截断）
│
├── 当前问题: ~200 tokens
│
└── 预留空间: ~1796 tokens
    └── LLM 回复缓冲
```

### 4.2 控制策略

1. **消息数量限制**: 默认加载最近 10 条消息
2. **助手消息截断**: 每条助手回复截断至 200 字符
3. **知识库检索**: 限制返回 Top-K 结果

---

## 五、消息存储流程

### 5.1 存储时机

```python
# backend/app/services/chat_service.py (stream_chat_events)

# 保存用户消息（流式开始前）
if message_repo:
    message_repo.create(
        message_id=user_message_id,
        session_id=session_id,
        role="user",
        content=full_question,
    )

# ... 流式生成回复 ...

# 保存助手消息（流式结束后）
if message_repo:
    message_repo.create(
        message_id=message_id,
        session_id=session_id,
        role="assistant",
        content=answer_text,  # 包含标签的原始文本
        avatar_action=final_response.get("avatar_action"),
        sources=final_response.get("sources"),
        emotion=final_response.get("metadata", {}).get("emotion"),
        intent=final_response.get("metadata", {}).get("intent"),
        latency_ms=final_response.get("latency_ms"),
    )

# 更新会话消息计数
if session_service:
    session_service.session_repo.update_message_count(session_id, increment=2)
```

### 5.2 消息内容说明

| 字段 | 内容 | 说明 |
|------|------|------|
| content (user) | 用户原始问题 | 完整文本 |
| content (assistant) | LLM 原始输出 | **包含标签**（emotion/avatar_combo） |
| 前端显示 | 纯文本 | 通过 `pure_text` 过滤标签 |

**注意**: 数据库存储的 `content` 包含标签，但发送给前端的文本通过 `EmotionAwareSegmenter.pure_text` 过滤。

---

## 六、上下文示例

### 6.1 空会话（首次对话）

```
System Prompt:
你是灵山胜境景区的智能导游...

[无历史上下文]

User: 灵山大佛有多高？
```

### 6.2 有历史上下文

```
System Prompt:
你是灵山胜境景区的智能导游...

以下是与该用户的历史对话上下文：
User: 灵山大佛有多高？
Assistant: 灵山大佛高达88米，是世界上最高的青铜佛像之一...
User: 现在可以参观吗？
Assistant: 景区开放时间为每天7:30-17:30...

User: 那门票多少钱？
```

---

## 七、会话状态管理

### 7.1 会话状态流转

```
创建会话
    │
    ▼
┌─────────┐
│ active  │ ◄─────── 正常使用
└─────────┘
    │
    ├─── archive_session() ───► ┌───────────┐
    │                           │ archived  │
    │                           └───────────┘
    │
    └─── delete_session() ────► ┌───────────┐
                                │ (删除)     │
                                └───────────┘
```

### 7.2 会话验证

```python
# backend/app/services/session_service.py

def validate_session(self, session_id: str, user_id: str) -> Session:
    session = self.session_repo.get_by_id(session_id)
    
    # 1. 会话是否存在
    if not session:
        raise NotFoundError(f"Session {session_id} not found")
    
    # 2. 会话是否激活
    if session.status != "active":
        raise NotFoundError(f"Session {session_id} is not active")
    
    # 3. 会话归属验证
    if session.user_id != user_id:
        raise ForbiddenError(f"Session does not belong to user")
    
    return session
```

---

## 八、异常处理

### 8.1 上下文构建失败

```python
def build_context(self, session_id: str, max_messages: int = None) -> str:
    try:
        # ... 构建逻辑 ...
    except Exception as e:
        logger.warning(f"ContextService.build_context failed: {e}")
        return ""  # 返回空字符串，不影响主流程
```

### 8.2 消息保存失败

```python
# 消息保存失败不影响回复返回
try:
    if message_repo:
        message_repo.create(...)
except Exception as e:
    logger.warning(f"Failed to save message: {e}")
```

---

## 九、文件结构

```
backend/app/
├── services/
│   ├── context_service.py   # 上下文构建服务
│   ├── session_service.py   # 会话管理服务
│   └── chat_service.py      # 聊天服务（调用上下文）
│
├── repositories/
│   ├── message_repo.py      # 消息数据访问
│   └── session_repo.py      # 会话数据访问
│
├── models/
│   ├── session.py           # Session 模型
│   └── message.py           # Message 模型
│
└── api/v1/
    ├── session.py           # 会话 API
    └── chat.py              # 聊天 API
```

---

## 十、API 接口

### 10.1 会话管理

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/session/create` | 创建新会话 |
| GET | `/api/v1/session/list` | 获取用户会话列表 |
| GET | `/api/v1/session/{id}` | 获取会话详情（含历史消息） |
| POST | `/api/v1/session/{id}/archive` | 归档会话 |
| DELETE | `/api/v1/session/{id}` | 删除会话 |
| PATCH | `/api/v1/session/{id}` | 更新会话标题 |

### 10.2 创建会话示例

```json
// POST /api/v1/session/create
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
    "spot_id": "buddha",
    "status": "active",
    "title": null,
    "message_count": 0,
    "created_at": "2026-05-05T10:00:00Z"
  }
}
```

### 10.3 获取会话列表

```json
// GET /api/v1/session/list?user_id=guest_abc123

{
  "code": 0,
  "data": {
    "total": 5,
    "page": 1,
    "page_size": 20,
    "sessions": [
      {
        "session_id": "s_12345678_a1b2",
        "user_id": "guest_abc123",
        "scenic_id": "lingshan",
        "spot_id": "buddha",
        "status": "active",
        "title": null,
        "message_count": 4,
        "first_user_message": "灵山大佛有多高？",
        "last_message": "祝您游览愉快！",
        "last_message_at": "2026-05-05T10:05:00Z",
        "created_at": "2026-05-05T10:00:00Z"
      }
    ]
  }
}
```

**会话列表字段说明**：

| 字段 | 类型 | 说明 |
|------|------|------|
| session_id | String | 会话 ID |
| user_id | String | 用户 ID |
| scenic_id | String | 景区 ID |
| spot_id | String? | 景点 ID |
| status | String | 状态：active / archived / deleted |
| title | String? | 会话标题（用户可修改） |
| message_count | Int | 消息总数 |
| first_user_message | String? | 第一条用户消息（用于显示会话标题，最多 100 字符） |
| last_message | String? | 最后一条消息（最多 100 字符） |
| last_message_at | String? | 最后消息时间 |
| created_at | String | 创建时间 |

### 10.4 获取会话详情

```json
// GET /api/v1/session/s_12345678_a1b2?user_id=guest_abc123

{
  "code": 0,
  "data": {
    "session_id": "s_12345678_a1b2",
    "user_id": "guest_abc123",
    "status": "active",
    "message_count": 4,
    "messages": [
      {
        "message_id": "m_001",
        "role": "user",
        "content": "灵山大佛有多高？",
        "created_at": "2026-05-04T10:00:00Z"
      },
      {
        "message_id": "m_002",
        "role": "assistant",
        "content": "灵山大佛高达88米...",
        "emotion": "excited",
        "avatar_action": {...},
        "created_at": "2026-05-04T10:00:05Z"
      }
    ]
  }
}
```

---

## 十一、扩展设计

### 11.1 上下文压缩

当会话消息过多时，可使用 LLM 生成摘要：

```python
# 预留扩展点
def compress_context(messages: list[Message]) -> str:
    """使用 LLM 压缩历史对话"""
    content = "\n".join([f"{m.role}: {m.content}" for m in messages])
    summary = llm_client.simple_chat(
        f"请将以下对话压缩为简洁摘要:\n{content}"
    )
    return summary

# 存储到 Session.context_summary
session_repo.update_context(session_id, summary=compressed)
```

### 11.2 实体提取

```python
# 从对话中提取关键实体
def extract_entities(messages: list[Message]) -> dict:
    """提取景点、意图、偏好等实体"""
    return {
        "mentioned_spots": ["灵山大佛", "梵宫"],
        "user_intent": "游览咨询",
        "preferences": ["历史背景", "开放时间"]
    }

# 存储到 Session.context_entities
```

### 11.3 多轮对话优化

```python
# 基于意图的上下文筛选
def filter_relevant_context(messages: list[Message], current_intent: str) -> list[Message]:
    """只保留与当前意图相关的历史消息"""
    if current_intent == "route":
        return [m for m in messages if "路线" in m.content or "导航" in m.content]
    return messages
```

---

## 十二、配置项

| 配置项 | 默认值 | 位置 | 说明 |
|--------|--------|------|------|
| DEFAULT_RECENT_MESSAGES | 10 | context_service.py | 默认加载消息数 |
| 助手消息截断长度 | 200 | context_service.py | 单条助手消息最大字符 |
| DATABASE_URL | sqlite:///./scenic_guide.db | config.py | 数据库连接串 |
