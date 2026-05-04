# 数据库架构设计

## 一、架构概览

```
┌─────────────────────────────────────────────────────────────────────────┐
│                              SQLite 数据库                               │
│                           ./scenic_guide.db                              │
└─────────────────────────────────────────────────────────────────────────┘
                                     │
         ┌───────────────────────────┼───────────────────────────┐
         │                           │                           │
         ▼                           ▼                           ▼
┌─────────────────┐     ┌─────────────────────┐     ┌─────────────────────┐
│   chat_session  │     │    chat_message     │     │ knowledge_document  │
│   (会话表)       │     │    (消息表)          │     │   (知识文档表)       │
└─────────────────┘     └─────────────────────┘     └─────────────────────┘
         │                           │                           │
         │                           │                           │
         │                           │                           ▼
         │                           │              ┌─────────────────────┐
         │                           │              │  knowledge_chunk    │
         │                           │              │   (知识分块表)       │
         │                           │              └─────────────────────┘
         │                           │                           │
         │                           │                           ▼
         │                           │              ┌─────────────────────┐
         │                           │              │knowledge_chunk_vector│
         │                           │              │   (向量索引表)        │
         │                           │              └─────────────────────┘
         │                           │
         └───────────────────────────┴──────────────────────────┐
                                                                     │
                                                                     ▼
                                                        ┌─────────────────────┐
                                                        │ knowledge_chunk_fts │
                                                        │  (FTS5 全文索引)     │
                                                        └─────────────────────┘
```

---

## 二、数据表定义

### 2.1 会话表 (chat_session)

存储用户会话信息，支持多设备登录和历史会话恢复。

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | VARCHAR(20) | PRIMARY KEY | 会话 ID，格式: `s_{timestamp}_{random}` |
| user_id | VARCHAR(36) | NOT NULL, INDEX | 用户 ID |
| scenic_id | VARCHAR(36) | NOT NULL | 景区 ID |
| spot_id | VARCHAR(36) | NULLABLE | 当前景点 ID |
| device_id | VARCHAR(64) | NULLABLE | 设备标识 |
| status | VARCHAR(20) | DEFAULT 'active', INDEX | 状态: active/archived/deleted |
| title | VARCHAR(100) | NULLABLE | 会话标题 |
| message_count | INTEGER | DEFAULT 0 | 消息总数 |
| context_summary | TEXT | NULLABLE | 上下文摘要（压缩存储） |
| context_entities | JSON | NULLABLE | 关键实体: 景点、意图、偏好 |
| last_message_at | DATETIME | NULLABLE | 最后消息时间 |
| created_at | DATETIME | NOT NULL | 创建时间 (UTC) |
| updated_at | DATETIME | NOT NULL | 更新时间 (UTC) |

**索引:**
- `idx_user_id` ON (user_id)
- `idx_status` ON (status)

**关系:**
- `messages`: 一对多关联 chat_message，级联删除

**模型文件:** `backend/app/models/session.py`

**API 计算字段:**
以下字段不在数据库表中存储，而是在 API 返回时从消息表查询计算：
- `first_user_message`: 第一条用户消息内容（最多 100 字符），用于前端显示会话标题
- `last_message`: 最后一条消息内容（最多 100 字符）

---

### 2.2 消息表 (chat_message)

存储会话中的所有消息，包括用户提问和助手回复。

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | VARCHAR(20) | PRIMARY KEY | 消息 ID，格式: `m_{timestamp}_{random}` |
| session_id | VARCHAR(20) | FOREIGN KEY, NOT NULL, INDEX | 所属会话 ID |
| role | VARCHAR(20) | NOT NULL | 角色: user/assistant |
| content | TEXT | NOT NULL | 消息内容（纯文本，无标签） |
| content_type | VARCHAR(20) | DEFAULT 'text' | 内容类型: text/voice/image |
| audio_url | VARCHAR(255) | NULLABLE | 音频文件 URL |
| avatar_action | JSON | NULLABLE | 数字人动作数据 |
| sources | JSON | NULLABLE | 知识库来源引用 |
| emotion | VARCHAR(20) | NULLABLE | 情绪标签 |
| intent | VARCHAR(50) | NULLABLE | 意图标签 |
| latency_ms | INTEGER | NULLABLE | 响应延迟（毫秒） |
| created_at | DATETIME | NOT NULL | 创建时间 (UTC) |

**索引:**
- `idx_session_id` ON (session_id)

**关系:**
- `session`: 多对一关联 chat_session

**模型文件:** `backend/app/models/message.py`

---

### 2.3 知识文档表 (knowledge_document)

存储上传的知识库文档元数据。

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | VARCHAR(20) | PRIMARY KEY | 文档 ID |
| scenic_id | VARCHAR(36) | NOT NULL, INDEX | 景区 ID |
| title | VARCHAR(200) | NOT NULL | 文档标题 |
| category | VARCHAR(50) | NOT NULL, DEFAULT 'general' | 分类 |
| spot_name | VARCHAR(100) | NULLABLE | 关联景点名称 |
| tags | TEXT | NULLABLE | 标签（逗号分隔） |
| doc_type | VARCHAR(20) | NOT NULL, DEFAULT 'md' | 文档类型: md/txt/json |
| source_path | VARCHAR(255) | NOT NULL | 源文件路径 |
| file_name | VARCHAR(255) | NOT NULL | 原始文件名 |
| file_size | INTEGER | NOT NULL, DEFAULT 0 | 文件大小（字节） |
| content_hash | VARCHAR(64) | NOT NULL, INDEX | 内容哈希（SHA256） |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'active', INDEX | 状态: active/deleted |
| chunk_count | INTEGER | NOT NULL, DEFAULT 0 | 分块数量 |
| summary | TEXT | NULLABLE | 文档摘要 |
| created_at | DATETIME | NOT NULL | 创建时间 (UTC) |
| updated_at | DATETIME | NOT NULL | 更新时间 (UTC) |

**索引:**
- `idx_scenic_id` ON (scenic_id)
- `idx_content_hash` ON (content_hash)
- `idx_status` ON (status)

**关系:**
- `chunks`: 一对多关联 knowledge_chunk，级联删除

**模型文件:** `backend/app/models/knowledge_document.py`

---

### 2.4 知识分块表 (knowledge_chunk)

存储文档分块后的内容片段。

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | VARCHAR(24) | PRIMARY KEY | 分块 ID |
| document_id | VARCHAR(20) | FOREIGN KEY, NOT NULL, INDEX | 所属文档 ID |
| scenic_id | VARCHAR(36) | NOT NULL, INDEX | 景区 ID |
| chunk_index | INTEGER | NOT NULL | 分块序号 |
| content | TEXT | NOT NULL | 分块内容 |
| snippet | TEXT | NOT NULL | 内容摘要（前 200 字） |
| token_count | INTEGER | NOT NULL, DEFAULT 0 | Token 数量 |
| keywords | TEXT | NULLABLE | 关键词 |
| created_at | DATETIME | NOT NULL | 创建时间 (UTC) |

**索引:**
- `idx_document_id` ON (document_id)
- `idx_scenic_id` ON (scenic_id)

**关系:**
- `document`: 多对一关联 knowledge_document

**模型文件:** `backend/app/models/knowledge_chunk.py`

---

### 2.5 向量索引表 (knowledge_chunk_vector)

存储分块的向量表示，用于语义检索。

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| chunk_id | VARCHAR(24) | PRIMARY KEY, FOREIGN KEY | 分块 ID |
| scenic_id | VARCHAR(36) | NOT NULL, INDEX | 景区 ID |
| dimension | INTEGER | NOT NULL | 向量维度 |
| model_name | VARCHAR(50) | NOT NULL | 向量化模型名称 |
| vector_json | TEXT | NOT NULL | 向量数据（JSON 序列化） |
| updated_at | DATETIME | NOT NULL | 更新时间 (UTC) |

**索引:**
- `idx_scenic_id` ON (scenic_id)

**模型文件:** `backend/app/models/knowledge_chunk_vector.py`

---

### 2.6 全文索引表 (knowledge_chunk_fts)

SQLite FTS5 虚拟表，用于全文检索。

| 字段 | 类型 | 说明 |
|------|------|------|
| chunk_id | UNINDEXED | 分块 ID |
| document_id | UNINDEXED | 文档 ID |
| scenic_id | UNINDEXED | 景区 ID |
| title | INDEXED | 文档标题 |
| content | INDEXED | 分块内容 |
| snippet | INDEXED | 内容摘要 |
| keywords | INDEXED | 关键词 |
| tokens | INDEXED | 分词文本 |

**特点:**
- 使用 `unicode61` 分词器
- 支持 BM25 排序
- 通过 `match` 查询实现全文检索

---

## 三、Repository 层

### 3.1 SessionRepository

```python
# backend/app/repositories/session_repo.py

class SessionRepository:
    def __init__(self, db: Session):
        self.db = db

    def create(session_id, user_id, scenic_id, ...) -> Session
    def get_by_id(session_id) -> Session | None
    def get_by_id_and_user(session_id, user_id) -> Session | None
    def get_active_by_id_and_user(session_id, user_id) -> Session | None
    def list_by_user(user_id, status, scenic_id, page, page_size) -> tuple[list[Session], int]
    def update(session, **kwargs) -> Session
    def update_status(session_id, status) -> Session | None
    def update_message_count(session_id, increment) -> Session | None
    def update_context(session_id, summary, entities) -> Session | None
    def delete(session_id) -> bool
    def soft_delete(session_id) -> Session | None
```

### 3.2 MessageRepository

```python
# backend/app/repositories/message_repo.py

class MessageRepository:
    def __init__(self, db: Session):
        self.db = db

    def create(message_id, session_id, role, content, ...) -> Message
    def get_by_id(message_id) -> Message | None
    def list_by_session(session_id, limit, offset) -> list[Message]
    def get_recent_by_session(session_id, limit) -> list[Message]
    def count_by_session(session_id) -> int
    def delete_by_session(session_id) -> int
```

### 3.3 KnowledgeRepository

```python
# backend/app/repositories/knowledge_repo.py

class KnowledgeRepository:
    def __init__(self, db: Session):
        self.db = db

    # 文档操作
    def create_document(...) -> KnowledgeDocument
    def get_document(document_id) -> KnowledgeDocument | None
    def get_active_document(document_id) -> KnowledgeDocument | None
    def list_documents(...) -> tuple[list[KnowledgeDocument], int]
    def get_by_hash(scenic_id, content_hash) -> KnowledgeDocument | None
    def delete_document(document_id) -> KnowledgeDocument | None

    # 分块操作
    def replace_document_chunks(document_id, scenic_id, chunks) -> list[KnowledgeChunk]
    def list_active_chunks(scenic_id) -> list[dict]
    def list_all_active_chunks() -> list[dict]

    # 向量操作
    def replace_document_vectors(scenic_id, vectors) -> None
    def list_chunk_vectors(scenic_id) -> list[dict]
    def clear_vectors(scenic_id) -> None
    def clear_vectors_for_document(document_id) -> None

    # 检索
    def search_chunks(scenic_id, query, limit) -> list[dict]

    # 统计
    def count_documents(scenic_id) -> int
    def count_chunks(scenic_id) -> int
    def count_vectors(scenic_id) -> int

    # 全文索引
    def rebuild_fts_index(scenic_id) -> int
```

---

## 四、数据库配置

### 4.1 连接配置

```python
# backend/app/core/database.py

from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker, declarative_base

engine = create_engine(
    settings.DATABASE_URL,  # sqlite:///./scenic_guide.db
    connect_args={"check_same_thread": False},  # SQLite 必需
    echo=settings.APP_ENV == "dev",  # 开发环境打印 SQL
)

SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
Base = declarative_base()
```

### 4.2 依赖注入

```python
def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
```

### 4.3 表初始化

```python
def init_db():
    db_dir = os.path.dirname(settings.DATABASE_URL.replace("sqlite:///", ""))
    if db_dir and not os.path.exists(db_dir):
        os.makedirs(db_dir, exist_ok=True)
    
    from backend.app.models.session import Session
    from backend.app.models.message import Message
    from backend.app.models.knowledge_document import KnowledgeDocument
    from backend.app.models.knowledge_chunk import KnowledgeChunk
    from backend.app.models.knowledge_chunk_vector import KnowledgeChunkVector
    
    Base.metadata.create_all(bind=engine)
```

---

## 五、数据迁移

### 5.1 备份

```bash
# SQLite 备份：直接复制文件
cp ./scenic_guide.db ./backup/scenic_guide_$(date +%Y%m%d_%H%M%S).db
```

### 5.2 迁移至 PostgreSQL

如需扩展至 PostgreSQL，修改配置：

```python
# .env
DATABASE_URL=postgresql://user:password@localhost:5432/scenic_guide
```

移除 `connect_args={"check_same_thread": False}` 配置。

---

## 六、文件结构

```
backend/app/
├── core/
│   ├── database.py          # SQLAlchemy 引擎、会话工厂、Base
│   └── config.py            # DATABASE_URL 配置
│
├── models/
│   ├── __init__.py
│   ├── base.py              # Base 模型基类
│   ├── session.py           # Session 会话模型
│   ├── message.py           # Message 消息模型
│   ├── knowledge_document.py    # KnowledgeDocument 模型
│   ├── knowledge_chunk.py       # KnowledgeChunk 模型
│   └── knowledge_chunk_vector.py # KnowledgeChunkVector 模型
│
├── repositories/
│   ├── __init__.py
│   ├── session_repo.py      # 会话数据访问
│   ├── message_repo.py      # 消息数据访问
│   └── knowledge_repo.py    # 知识库数据访问
│
└── services/
    ├── session_service.py   # 会话业务逻辑
    ├── context_service.py   # 上下文管理
    └── knowledge_service.py # 知识库业务逻辑
```

---

## 七、查询优化

### 7.1 索引策略

| 表 | 索引字段 | 用途 |
|---|---------|------|
| chat_session | user_id | 用户会话列表查询 |
| chat_session | status | 状态筛选 |
| chat_message | session_id | 会话消息列表 |
| knowledge_document | scenic_id | 景区文档列表 |
| knowledge_document | content_hash | 去重检测 |
| knowledge_document | status | 状态筛选 |
| knowledge_chunk | document_id | 文档分块查询 |
| knowledge_chunk | scenic_id | 景区分块查询 |
| knowledge_chunk_vector | scenic_id | 景区向量查询 |

### 7.2 分页查询

```python
# 会话列表分页
def list_by_user(self, user_id, page, page_size):
    query = self.db.query(Session).filter(Session.user_id == user_id)
    total = query.count()
    sessions = query.order_by(Session.last_message_at.desc()) \
        .offset((page - 1) * page_size) \
        .limit(page_size) \
        .all()
    return sessions, total
```

---

## 八、事务管理

### 8.1 自动提交

Repository 层方法默认自动提交：

```python
def create(self, ...):
    session = Session(...)
    self.db.add(session)
    self.db.commit()
    self.db.refresh(session)
    return session
```

### 8.2 级联删除

```python
# Session 模型定义
messages = relationship(
    "Message",
    back_populates="session",
    cascade="all, delete-orphan"  # 删除会话时级联删除消息
)
```

---

## 九、配置项

```python
# backend/app/core/config.py

class Settings(BaseSettings):
    DATABASE_URL: str = "sqlite:///./scenic_guide.db"
    # 其他配置...
```

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| DATABASE_URL | sqlite:///./scenic_guide.db | 数据库连接串 |
