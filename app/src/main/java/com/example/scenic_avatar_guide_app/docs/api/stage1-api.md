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

### 流式输出扩展

阶段一非流式返回仍保持上述出参不变。新增流式接口时，主问答结果拆分为事件流输出：

- `message_start`: 回答开始，返回 `message_id`、`session_id`
- `text_delta`: 回答文本增量，移动端按顺序追加展示
- `tts_segment`: 后端已合成的短句音频片段，包含 `audio_url`、`duration_ms`、`marks`
- `sources`: 命中的知识来源列表
- `metadata`: `latency_ms`、`confidence`、`is_fallback` 等统计与降级信息
- `done`: 后端流式输出结束
- `error`: 流式链路异常

推荐接口：

```http
POST /api/v1/chat/text/stream
Accept: text/event-stream
Content-Type: application/json
```

示例事件：

```text
event: text_delta
data: {"type":"text_delta","delta":"建议您从九龙灌浴开始，"}

event: tts_segment
data: {"type":"tts_segment","segment_id":"seg_001","text":"建议您从九龙灌浴开始，","audio_url":"/api/v1/tts/file/seg_001.mp3","duration_ms":2100,"marks":[]}

event: done
data: {"type":"done","message_id":"m_xxx","session_id":"s_xxx"}
```

流式接口只改变传输方式，不改变问答主链路的业务语义。非流式接口继续作为降级路径保留。

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

## 3. 相关文档

- `API_CONTRACT.md`: 移动端完整接口契约，包含非流式与流式接口摘要
- `API_TTS_USAGE.md`: Edge TTS 独立接口与流式分段 TTS 说明
- `STREAMING_REFACTOR_PLAN.md`: 流式输入输出重构详细方案
