# 后端问题清单与修正建议

> 适用范围：本文件用于梳理 `scenic-avatar-guide` 后端在第一阶段落地开发与最终完整版架构演进中存在的问题，并给出修正建议。内容优先保障最小可联调链路，同时兼顾最终版框架的可扩展性。

## 1. 目标与原则

### 1.1 当前目标
当前后端的首要目标不是一次性做全，而是先保证最小可联调链路稳定可用：

- FastAPI 服务可启动
- Swagger `/docs` 可访问
- `GET /api/v1/health` 可用
- `POST /api/v1/session/create` 可用
- `POST /api/v1/chat/text` 可用
- Android 端可使用统一后端进行文本联调

### 1.2 演进原则
在满足最小链路的前提下，后端架构应逐步扩展到完整版：

- 业务逻辑分层清晰
- 游客侧与管理侧接口隔离
- 第三方能力通过适配层接入
- RAG、ASR、TTS、数字人引擎可独立演进
- 代码结构与文档结构保持一致

## 2. 问题清单

### 问题 1：当前问答链路为最小可用版本，尚未接入完整 RAG 编排
#### 现象
目前文本问答链路主要是：

- 请求进入 `chat_service`
- 调用 PPIO 或 mock 返回
- 尚未接入成员 C 的完整 RAG 流程

#### 影响
- 还不能充分利用景区知识库
- `sources`、检索结果、重排结果尚未形成标准化输出
- 后续与成员 C 的协作接口还需要明确

#### 修正建议
第一阶段保持最小问答直通，后续逐步补齐：

- `rag_service`
- `retriever`
- `reranker`
- `prompt_builder`
- `source_formatter`
- 幻觉控制与拒答策略

建议先定义统一的 RAG 输出结构，再替换 `chat_service` 的内部实现。

### 问题 2：ASR / TTS / 数字人能力目前仅有适配层空壳
#### 现象
当前仅建立了：

- `SenseVoiceAdapter`
- `CosyVoiceAdapter`
- `OpenAvatarChatClient`
- `LiteAvatarAdapter`

但尚未实现完整调用逻辑。

#### 影响
- 语音问答链路未打通
- 数字人口型、表情、动作输出还无法闭环
- 只能验证文本链路

#### 修正建议
保持适配层空壳，但要提前约定接口：

- 输入参数
- 返回值结构
- 错误码
- 降级策略

后续按优先级推进：

1. TTS 先通
2. ASR 再通
3. OpenAvatarChat / LiteAvatar 再接入
4. 最后做打断与双工

### 问题 3：依赖管理还需要继续细化
#### 现象
当前 `requirements.txt` 只包含基础启动依赖。

#### 影响
- 后续数据库、RAG、任务队列、语音能力接入时还要补依赖
- 容易出现“代码写完了，环境没装好”的问题

#### 修正建议
后续拆分为更清晰的依赖层次：

- 运行依赖
- 开发依赖
- 测试依赖

如果项目继续扩展，建议补充 `pyproject.toml` 或更完整的依赖锁定方案。

### 问题 4：Python 包初始化不完整会影响导入稳定性
#### 现象
若目录缺少 `__init__.py`，某些环境下会导致：

- 模块导入不稳定
- IDE 解析异常
- 测试环境下包路径识别不一致

#### 影响
- 影响开发体验
- 增加联调问题排查成本

#### 修正建议
所有会被 Python 作为包引用的目录都应补齐 `__init__.py`。

已建议覆盖目录：

- `backend/`
- `backend/app/`
- `backend/app/api/`
- `backend/app/api/v1/`
- `backend/app/core/`
- `backend/app/services/`
- `backend/app/schemas/`
- `backend/app/integrations/`
- `backend/app/integrations/asr/`
- `backend/app/integrations/tts/`
- `backend/app/integrations/openavatar/`
- `backend/app/analytics/`
- `backend/app/models/`
- `backend/app/repositories/`
- `backend/app/rag/`
- `backend/app/workers/`
- `backend/app/utils/`

### 问题 5：最终版架构目录较大，第一阶段不宜一次性全实现
#### 现象
目前文本问答链路主要是：

- 请求进入 `chat_service`
- 调用 PPIO 或 mock 返回
- 尚未接入成员 C 的完整 RAG 流程

#### 影响
- 还不能充分利用景区知识库
- `sources`、检索结果、重排结果尚未形成标准化输出
- 后续与成员 C 的协作接口还需要明确

#### 修正建议
第一阶段保持最小问答直通，后续逐步补齐：

- `rag_service`
- `retriever`
- `reranker`
- `prompt_builder`
- `source_formatter`
- 幻觉控制与拒答策略

建议先定义统一的 RAG 输出结构，再替换 `chat_service` 的内部实现。

### 问题 6：ASR / TTS / 数字人能力目前仅有适配层空壳
#### 现象
当前仅建立了：

- `SenseVoiceAdapter`
- `CosyVoiceAdapter`
- `OpenAvatarChatClient`
- `LiteAvatarAdapter`

但尚未实现完整调用逻辑。

#### 影响
- 语音问答链路未打通
- 数字人口型、表情、动作输出还无法闭环
- 只能验证文本链路

#### 修正建议
保持适配层空壳，但要提前约定接口：

- 输入参数
- 返回值结构
- 错误码
- 降级策略

后续按优先级推进：

1. TTS 先通
2. ASR 再通
3. OpenAvatarChat / LiteAvatar 再接入
4. 最后做打断与双工

### 问题 7：依赖管理还需要继续细化
#### 现象
当前 `requirements.txt` 只包含基础启动依赖。

#### 影响
- 后续数据库、RAG、任务队列、语音能力接入时还要补依赖
- 容易出现“代码写完了，环境没装好”的问题

#### 修正建议
后续拆分为更清晰的依赖层次：

- 运行依赖
- 开发依赖
- 测试依赖

如果项目继续扩展，建议补充 `pyproject.toml` 或更完整的依赖锁定方案。

### 问题 8：Python 包初始化不完整会影响导入稳定性
#### 现象
若目录缺少 `__init__.py`，某些环境下会导致：

- 模块导入不稳定
- IDE 解析异常
- 测试环境下包路径识别不一致

#### 影响
- 影响开发体验
- 增加联调问题排查成本

#### 修正建议
所有会被 Python 作为包引用的目录都应补齐 `__init__.py`。

已建议覆盖目录：

- `backend/`
- `backend/app/`
- `backend/app/api/`
- `backend/app/api/v1/`
- `backend/app/core/`
- `backend/app/services/`
- `backend/app/schemas/`
- `backend/app/integrations/`
- `backend/app/integrations/asr/`
- `backend/app/integrations/tts/`
- `backend/app/integrations/openavatar/`
- `backend/app/analytics/`
- `backend/app/models/`
- `backend/app/repositories/`
- `backend/app/rag/`
- `backend/app/workers/`
- `backend/app/utils/`

### 问题 9：最终版架构目录较大，第一阶段不宜一次性全实现
#### 现象
最终版架构包含：

- `models/`
- `repositories/`
- `rag/`
- `analytics/`
- `workers/`
- `admin-web/`
- `migrations/`

#### 影响
- 第一阶段工作量过大
- 容易分散资源
- 影响最小联调链路的落地速度

#### 修正建议
按“先闭环、后扩展”的顺序推进：

1. 最小闭环
   - 健康检查
   - 创建会话
   - 文本问答

2. 业务增强
   - RAG
   - 路线推荐
   - 用户偏好

3. 多模态能力
   - ASR
   - TTS
   - 数字人驱动

4. 管理侧能力
   - 知识库管理
   - 反馈分析
   - 数据大屏

5. 运营与部署
   - worker
   - migration
   - admin-web

## 3. 建议的最终实现顺序

### 第 1 优先级：最小可联调链路
目标是让 Android 端尽快联通后端。

- FastAPI 启动
- `/docs` 可访问
- `/api/v1/health`
- `/api/v1/session/create`
- `/api/v1/chat/text`

### 第 2 优先级：稳定问答编排
- 接入成员 C 的 RAG 服务
- 统一 `sources` 格式
- 规范错误码与降级策略

### 第 3 优先级：语音与数字人
- ASR
- TTS
- OpenAvatarChat
- LiteAvatar

### 第 4 优先级：管理侧与统计分析
- 知识库管理
- 游客反馈分析
- 数据大屏
- 报表导出

### 第 5 优先级：工程化完善
- 数据库模型
- 仓储层
- 异步任务
- 日志与监控
- 部署脚本

## 4. 最终建议

当前后端开发不应把“最终架构”与“第一阶段交付”混为一谈。推荐的实施原则是：

- 第一阶段：保证最小联调链路稳定可用
- 第二阶段：补齐 RAG 与路线推荐
- 第三阶段：接入语音与数字人
- 第四阶段：补齐管理侧与统计分析
- 最终阶段：工程化、部署化、可维护化

只要按这个顺序推进，项目就能在保证进度的同时保留完整扩展空间。
