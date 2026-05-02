# 成员 C 接口草案 v1

更新时间：2026-05-02

## 统一约定

- 基础前缀：`/api/v1`
- 统一响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {}
}
```

- 分页结构：

```json
{
  "total": 0,
  "page": 1,
  "page_size": 20,
  "documents": []
}
```

- 当前使用错误码：
  - `0` 成功
  - `1002` 参数格式错误
  - `2002` LLM 服务不可用
  - `2003` 知识库检索失败
  - `404` 资源不存在
  - `403` 无权访问

## 管理侧接口

### 1. 获取数据大屏概览

- `GET /api/v1/admin/dashboard/overview?scenic_id={scenic_id}`

返回字段：

- `service_count`
- `qa_count`
- `knowledge_doc_count`
- `fallback_rate`
- `hot_questions`
- `satisfaction_trend`

### 2. 获取知识库文档列表

- `GET /api/v1/admin/knowledge/documents`

请求参数：

- `scenic_id`
- `category`
- `spot_name`
- `keyword`
- `page`
- `page_size`

响应 `data`：

```json
{
  "total": 1,
  "page": 1,
  "page_size": 20,
  "filters": {
    "category": "guide",
    "spot_name": "灵山大佛",
    "keyword": "佛教"
  },
  "documents": [
    {
      "document_id": "doc_xxx",
      "scenic_id": "lingshan",
      "title": "灵山大佛讲解词",
      "category": "guide",
      "spot_name": "灵山大佛",
      "tags": "地标,佛教,热门景点",
      "doc_type": "md",
      "source_path": "storage/knowledge/lingshan/doc_xxx_guide.md",
      "file_name": "guide.md",
      "file_size": 1024,
      "status": "active",
      "chunk_count": 3,
      "summary": "文档摘要",
      "created_at": "2026-05-02T08:00:00+00:00",
      "updated_at": "2026-05-02T08:00:00+00:00"
    }
  ]
}
```

### 3. 上传知识库文档

- `POST /api/v1/admin/knowledge/upload`
- `Content-Type: multipart/form-data`

表单字段：

- `scenic_id`
- `title`
- `category`
- `spot_name`
- `tags`
- `file`，当前支持 `.md/.txt`

说明：

- `category` 当前建议值：
  - `general`
  - `faq`
  - `guide`
  - `history`
  - `route`
  - `notice`
- `spot_name` 用于标识知识所属景点
- `tags` 用于补充主题词、业务词，多个标签可用逗号分隔

### 4. 搜索知识块

- `POST /api/v1/admin/knowledge/search`

请求体：

```json
{
  "scenic_id": "lingshan",
  "query": "灵山大佛多高",
  "limit": 5
}
```

响应 `data.items` 中每项字段：

- `document_id`
- `chunk_id`
- `title`
- `category`
- `spot_name`
- `tags`
- `source_path`
- `score`
- `bm25_score`
- `rerank_score`（当前服务内部会计算，是否前端展示可按页面需要决定）
- `snippet`
- `content`

### 5. 重建知识库索引

- `POST /api/v1/admin/knowledge/reindex`

请求体：

```json
{
  "scenic_id": "lingshan"
}
```

响应 `data`：

```json
{
  "scenic_id": "lingshan",
  "sparse": {
    "scenic_id": "lingshan",
    "rebuilt_chunk_count": 8,
    "document_count": 3,
    "chunk_count": 8,
    "index_type": "fts5_bm25"
  },
  "vector": {
    "scenic_id": "lingshan",
    "rebuilt_vector_count": 8,
    "vector_count": 8,
    "dimension": 256,
    "model_name": "hashing-token-v1"
  },
  "faiss": {
    "scenic_id": "lingshan",
    "provider": "faiss",
    "configured": true,
    "available": true,
    "items": {
      "lingshan": {
        "status": "ready",
        "vector_count": 8,
        "dimension": 1024
      }
    },
    "total_vector_count": 8
  },
  "backend": "hybrid"
}
```

说明：

- 当前重建不是只重建 FTS，而是会同时处理：
  - 稀疏索引
  - 本地向量索引
  - FAISS 向量库索引
- 若 embedding / FAISS 不可用，`faiss.items.xxx.status` 会返回降级状态而不是让主流程失败

### 6. 删除知识库文档

- `DELETE /api/v1/admin/knowledge/documents/{document_id}`

响应 `data`：

```json
{
  "document_id": "doc_xxx",
  "scenic_id": "lingshan",
  "title": "灵山大佛讲解词",
  "deleted": true
}
```

说明：

- 删除操作会同步清理：
  - 原始上传文件
  - 文档记录
  - 分块记录
  - 向量记录
  - FTS 索引
  - FAISS 对应景区索引重建

### 7. 游客感受度报告

- `GET /api/v1/admin/reports/visitor-sentiment?scenic_id={scenic_id}`

返回字段：

- `focus_points`
- `emotion_trend`
- `service_suggestions`

## 游客侧相关接口

### 8. 路线推荐

- `GET /api/v1/route/recommend`
- `POST /api/v1/route/recommend`

输入字段：

- `scenic_id`
- `duration_min`
- `interest_tags`
- `current_spot`
- `question`

输出字段：

- `title`
- `scenic_id`
- `interest_tags`
- `current_spot`
- `total_duration_min`
- `total_distance_m`
- `reason`
- `spots`
- `polyline`

## 与聊天主链路对接

- `mode=chat`：优先走知识检索与 RAG，返回 `sources`
- `mode=route`：保留路线模式，返回 `route_data`
- 当前知识检索已支持：
  - sparse recall
  - vector recall
  - Hybrid 融合
  - rerank
- SSE 流式收口事件继续使用：
  - `sources`
  - `route_data`
  - `metadata`
  - `done`
