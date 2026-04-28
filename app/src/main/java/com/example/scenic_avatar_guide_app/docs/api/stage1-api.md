# 第一阶段接口文档

## 1. 问答主接口（核心）

### 调用方式
- Python 直接调用：`backend/app/rag/rag_service.py`
- 方法：`RagService.ask(session_id, question, scenic_id, spot_id, user_id, mode)`

### 入参
- `session_id`: 会话ID，字符串，必填
- `question`: 用户问题，字符串，必填
- `scenic_id`: 景区ID，字符串，必填
- `spot_id`: 景点ID，字符串，可空
- `user_id`: 用户ID，字符串，必填
- `mode`: 交互模式，字符串，可空，默认 `"chat"`。取值：`"chat"`（聊天问答）或 `"route"`（路线规划）

### 出参
- `reply_text`: 回答文本
- `sources`: 命中的知识来源列表
- `latency_ms`: 本次问答耗时（毫秒）
- `confidence`: 置信度，范围 0-1
- `is_fallback`: 是否走了拒答/降级策略

### `sources` 结构
```json
[
  {
    "document_id": "doc_xxx",
    "chunk_id": "ck_xxx",
    "title": "faq",
    "source_path": "data/raw/scenic_docs/faq.md",
    "score": 0.9,
    "snippet": "建议优先选择苏堤春晓..."
  }
]
```

### 拒答与降级策略
1. `Retriever` 无命中 chunk：
- `reply_text` 返回安全兜底文案
- `sources=[]`
- `is_fallback=true`
- `confidence=0.2`

2. 有 chunk 但 LLM 调用异常：
- `LlmClient` 启用本地拼接回答
- `is_fallback=false`（可答但质量下降）
- `confidence` 适度下调

3. 无 chunk 且 LLM 不可用：
- 保持拒答兜底输出，避免幻觉

## 2. 管理端占位 API
- `src/api/dashboard.js`: Dashboard 概览占位
- `src/api/knowledge.js`: 知识文档列表占位
- `src/api/records.js`: 问答记录占位
