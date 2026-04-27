# 第一周任务完成情况表

> 说明：本表用于记录成员 B 第一周的完成情况，优先围绕最小可联调链路评估当前进度，并兼顾后续最终版框架的衔接。

| 任务项 | 当前状态 | 已完成内容 | 未完成内容 | 下一步建议 |
|---|---|---|---|---|
| 后端项目骨架初始化 | 已完成 | 已建立 `backend/app/` 主要目录，补齐核心包结构与 `__init__.py`，准备后续扩展到最终版架构。 | 仍需持续补充最终版目录下的 `models`、`repositories`、`rag`、`analytics`、`workers` 等完整实现。 | 继续按“先闭环、后扩展”的顺序推进，优先维护 `app/` 主链路。 |
| FastAPI 主入口与路由总线 | 已完成 | 已补 `backend/app/main.py`、`backend/app/api/router.py`，形成统一入口与 v1 路由注册结构。 | 仍需确认运行环境下可无报错启动并加载全部路由。 | 执行启动验证，确认 `uvicorn backend.app.main:app --reload` 可正常运行。 |
| 基础核心能力 | 已完成 | 已补 `config.py`、`logger.py`、`exceptions.py`、`response.py`，具备基础配置、日志、异常和统一响应能力。 | 后续可继续完善中间件、鉴权、数据库连接和缓存支持。 | 先保持轻量，优先保证第一周接口稳定可用。 |
| 健康检查接口 | 已完成 | 已补 `GET /api/v1/health`，用于服务状态检查。 | 尚未在实际运行环境中验证端到端可访问性。 | 启动服务后访问 `/api/v1/health`，确认返回成功。 |
| 会话创建接口 | 已完成 | 已补 `POST /api/v1/session/create`，可生成 `session_id` 并返回会话基础信息。 | 目前会话仍为内存/返回结构级实现，未接数据库持久化。 | 启动后联调该接口，确认 Android 端可拿到 `session_id`。 |
| 文本问答接口 | 已完成（最小版） | 已补 `POST /api/v1/chat/text`，可返回最小问答结果，支持 mock/PPIO 直连。 | 未接入完整 RAG、来源引用、历史上下文与多模态输出。 | 后续接入成员 C 的 `rag_service`，再扩展 `sources` 与知识库问答能力。 |
| 统一请求/响应结构 | 已完成 | 已补 `schemas/` 与统一 JSON 返回格式，便于 Android 和后续 Web 统一对接。 | 当前 schema 仍是最小字段集，未覆盖管理侧和语音链路全部字段。 | 保持最小字段集稳定，后续按接口扩展。 |
| PPIO 大模型适配层 | 已完成（基础版） | 已封装 `backend/app/integrations/ppio/llm_client.py`，支持无 Key 时 mock 返回，有 Key 时按 OpenAI 兼容方式调用。 | 暂未与 RAG 聚合逻辑深度绑定，也未加入完整超时/重试策略。 | 在联调时先验证 mock，再逐步切换真实 API。 |
| OpenAvatarChat / LiteAvatar / ASR / TTS 适配层 | 已完成（空壳） | 已预留 `OpenAvatarChatClient`、`LiteAvatarAdapter`、`SenseVoiceAdapter`、`CosyVoiceAdapter`。 | 这些能力还未真正接通，语音/数字人链路尚未闭环。 | 先保持空壳稳定，后续按 TTS → ASR → 数字人 的顺序接入。 |
| Android 客户端语音识别（讯飞 ASR） | 已完成 | 已实现 `SpeechRecognizerHelper.kt` 和 `XunfeiAsrCallback.java`，集成讯飞 SparkChain SDK；UI 支持长按录音、上滑取消、音量可视化；权限处理完整。 | 尚未与后端 `chat/voice` 接口联调；服务端 ASR 尚未实现。 | 后端实现 `POST /api/v1/chat/voice` 接口后进行联调。 |
| 项目环境与依赖约定 | 已完成 | 已统一项目路径为 `~/scenic-avatar-guide`，环境名为 `guide`，并写入 `README.md` 与任务文档。 | `requirements.txt` 目前仍是基础依赖清单，后续要扩展。 | 保持当前约定不变，后续补充运行/开发/测试依赖拆分。 |
| 问题清单与修正建议文档 | 已完成 | 已生成并整理 `docs/architecture/backend-issues-and-fixes.md`，并保留后续待关注问题。 | 仍需要随着实现进展持续更新。 | 每完成一个阶段，就回写一次问题清单状态。 |
| 启动验证 | 待完成 | 已具备启动所需文件与目录。 | 还未完成真实环境下的启动确认。 | 使用 `uvicorn backend.app.main:app --reload` 启动后，确认 `/docs`、`/api/v1/health` 可访问。 |
| 接口联调验证 | 待完成 | 接口定义已完成最小版。 | 还未与 Android 端做完整联调。 | 先联调 `health`、`session/create`、`chat/text`，确认请求/响应字段一致。 |
| 最小闭环稳定性确认 | 待完成 | 已形成最小闭环的代码结构。 | 还未验证连续请求、异常场景、mock/真实 API 切换稳定性。 | 连续调用 10 次以上健康检查与文本问答，观察是否有异常、超时或字段缺失。 |
| 与其他成员的接口约定 | 待完成 | 已明确后端需要对接成员 C 的 RAG 服务。 | 尚未正式形成完整接口契约文档和字段定义。 | 先固定 `rag_service` 输入输出、`sources` 结构、失败返回格式，再同步给成员 C。 |

## 启动验证方案

这部分就是后端启动验证，目的是确认服务、Swagger 和最小接口链路是否真正可用。建议按下面步骤执行：

1. 进入项目目录并激活环境

```bash
cd ~/scenic-avatar-guide
conda activate guide
```

2. 安装依赖

```bash
pip install -r requirements.txt
```

3. 启动后端服务

```bash
uvicorn backend.app.main:app --reload
```

4. 检查 Swagger 页面

- 浏览器访问 `http://127.0.0.1:8000/docs`
- 确认页面可以正常打开
- 确认左侧接口列表能看到 `health`、`session`、`chat`

5. 验证健康检查接口

```bash
curl http://127.0.0.1:8000/api/v1/health
```

期望结果：返回 `code=0`，`status=running`

6. 验证会话创建接口

```bash
curl -X POST http://127.0.0.1:8000/api/v1/session/create \
  -H "Content-Type: application/json" \
  -d '{"user_id":"u_001","scenic_id":"scenic_001","spot_id":"spot_001","device_id":"android_001"}'
```

期望结果：返回可用的 `session_id`

7. 验证文本问答接口

```bash
curl -X POST http://127.0.0.1:8000/api/v1/chat/text \
  -H "Content-Type: application/json" \
  -d '{"session_id":"s_xxx","user_id":"u_001","question":"你好，景区有什么推荐路线？","spot_id":"spot_001","need_audio":false,"need_avatar":false}'
```

期望结果：返回 `reply_text`，并保持统一 JSON 结构

8. 通过标准

- 服务正常启动无报错
- Swagger 可打开
- `health`、`session/create`、`chat/text` 三个接口均能返回预期 JSON
- 连续刷新和重复调用不会报错


## 接口联调验证方案

这部分用于验证 Android 端和后端最小接口是否真正对得上。建议按下面方式执行：

### 1. 先联调的接口
- `GET /api/v1/health`
- `POST /api/v1/session/create`
- `POST /api/v1/chat/text`

### 2. 建议联调顺序
1. 先检查 `health`
2. 再创建 `session`
3. 最后发起 `chat/text`

### 3. 联调准备
- 确认后端已启动
- 确认 Android 端知道当前服务地址
- 确认请求体字段与后端 schema 一致

### 4. 联调关注点
- `session_id` 是否能稳定生成并回传
- `reply_text` 是否可直接展示
- `audio_url`、`avatar_action` 是否能按需返回占位结构
- `sources` 字段是否保持兼容，便于后续接 RAG
- 返回 JSON 是否和 Android 端约定一致


## 最小闭环稳定性确认方案

这部分用于确认最小链路连续跑起来之后是否稳定。建议至少跑一轮小规模压力和重复调用。

### 目标
确认最小链路在连续调用时稳定，不出现明显异常。

### 检查方式
1. 连续调用 `GET /api/v1/health` 至少 10 次
2. 连续调用 `POST /api/v1/session/create` 至少 10 次
3. 连续调用 `POST /api/v1/chat/text` 至少 10 次
4. 尝试切换一次 mock 和真实 PPIO 配置，再重复调用 `chat/text`

### 关注指标
- 是否返回 200
- 是否有字段缺失
- 是否出现空响应或异常堆栈
- mock 和真实 PPIO 切换是否平滑
- 响应结构是否稳定
- 响应时间是否出现明显抖动

### 通过标准
- 连续请求均正常返回
- 返回 JSON 结构一致
- 没有明显阻塞、报错或超时
- 在重复调用下不会出现 session 或 message 字段异常


## 还需要接入的 API 与接口

### 1. 现阶段已完成并优先稳定的后端 API
- `GET /api/v1/health`
- `POST /api/v1/session/create`
- `POST /api/v1/chat/text`

### 2. 第一阶段后续建议补充的接口
- `POST /api/v1/chat/voice`
- `POST /api/v1/chat/interrupt`
- `GET /api/v1/session/history`
- `POST /api/v1/user/profile`
- `GET /api/v1/user/profile`
- `GET /api/v1/scenic/info`
- `GET /api/v1/route/recommend`

### 3. 未来管理侧可对接接口
- `POST /api/v1/admin/auth/login`
- `GET /api/v1/admin/dashboard/overview`
- `POST /api/v1/admin/knowledge/upload`
- `GET /api/v1/admin/sessions`
- `GET /api/v1/admin/feedback`

### 4. 接口约定说明

#### 4.1 `GET /api/v1/health`
- **用途**：后端服务健康检查
- **请求参数**：无
- **返回字段**：
  - `code`：0 表示成功
  - `message`：固定 `ok`
  - `data.service`：服务名称
  - `data.status`：运行状态，当前应为 `running`
- **使用场景**：启动验证、联调前探活、监控巡检

#### 4.2 `POST /api/v1/session/create`
- **用途**：为游客创建会话，返回 `session_id`
- **请求体字段**：
  - `user_id`：游客标识，必填
  - `scenic_id`：景区标识，必填
  - `spot_id`：当前景点，可选
  - `device_id`：设备标识，可选
- **返回字段**：
  - `session_id`：会话唯一标识
  - `user_id`：原样回传
  - `scenic_id`：原样回传
  - `spot_id`：原样回传
  - `device_id`：原样回传
  - `status`：会话状态，通常为 `active`
  - `created_at`：创建时间
- **使用场景**：Android 端进入导览前初始化会话

#### 4.3 `POST /api/v1/chat/text`
- **用途**：文本问答主接口，第一阶段用于直接大模型问答，后续可切换到 RAG 编排
- **请求体字段**：
  - `session_id`：会话标识，必填
  - `user_id`：游客标识，必填
  - `question`：用户问题，必填
  - `spot_id`：当前景点，可选
  - `need_audio`：是否需要语音输出，可选，默认 `false`
  - `need_avatar`：是否需要数字人动作，可选，默认 `false`
- **返回字段**：
  - `message_id`：消息唯一标识
  - `session_id`：原样回传
  - `reply_text`：大模型返回的文本答案
  - `audio_url`：音频地址，第一阶段可为空
  - `avatar_action`：数字人动作信息，第一阶段可为空
  - `sources`：来源信息，第一阶段可为空数组
  - `latency_ms`：响应耗时
  - `created_at`：响应时间
- **约定说明**：
  - 第一阶段默认直连 PPIO，不经过 RAG
  - 后续接入成员 C 的 RAG 后，`sources` 需要保留兼容
  - 如果 `need_audio=false`，`audio_url` 可返回 `null`
  - 如果 `need_avatar=false`，`avatar_action` 可返回 `null`
- **使用场景**：Android 端文本提问、联调验证、后续知识库问答入口

#### 4.4 `POST /api/v1/chat/voice`
- **用途**：语音问答入口，后续接入 ASR + LLM + TTS
- **请求体字段**：
  - `session_id`、`user_id`、`audio_url` 或音频上传内容
- **返回字段**：
  - `reply_text`、`audio_url`、`avatar_action`、`sources`
- **约定说明**：第一阶段先不实现，仅保留接口契约
- **客户端实现状态**：Android 端已实现本地语音识别（讯飞 SparkChain SDK），可将识别文本通过 `chat/text` 发送；后续可扩展为上传音频文件由服务端处理

#### 4.5 `POST /api/v1/chat/interrupt`
- **用途**：处理游客打断数字人播报或中断当前回答
- **约定说明**：后续用于语音双工与实时交互

#### 4.6 `GET /api/v1/session/history`
- **用途**：查询当前会话历史消息
- **建议参数**：
  - `session_id`
  - `page`
  - `page_size`
- **返回字段**：消息列表、总数、分页信息

#### 4.7 `POST /api/v1/user/profile` / `GET /api/v1/user/profile`
- **用途**：保存和读取游客兴趣标签、出行偏好
- **建议字段**：
  - `interest_tags`
  - `travel_mode`
  - `play_duration`

#### 4.8 `GET /api/v1/scenic/info`
- **用途**：获取景区/景点基础信息
- **建议字段**：景区介绍、开放时间、推荐路线、景点列表

#### 4.9 `GET /api/v1/route/recommend`
- **用途**：获取路线推荐结果
- **建议字段**：游玩时长、兴趣标签、推荐路线、路线说明

### 5. 需要额外明确的接口形态
- `chat/text` 的 `sources` 结构
- `chat/voice` 的音频上传与返回结构
- `session/history` 的分页结构
- `user/profile` 的兴趣标签和出行偏好字段
- 错误码与降级返回字段
- 是否统一使用 `code/message/data` 响应格式


## 需要给其他成员的接口

### 1. 给 Android 端的接口
- `GET /api/v1/health`
- `POST /api/v1/session/create`
- `POST /api/v1/chat/text`

### 2. 给成员 C 的接口约定
需要成员 C 侧配合明确以下内容：
- `rag_service` 的调用方式
- 输入字段：`session_id`、`question`、`scenic_id`、`spot_id`、`user_id`
- 输出字段：`reply_text`、`sources`、`latency_ms`、`confidence`、`is_fallback`
- `sources` 的结构格式
- 拒答和降级策略

### 3. 给成员 A 的接口信息
- 当前后端可联调的地址
- `reply_text` 是否直接展示
- `audio_url` 是否第一阶段暂不启用
- `avatar_action` 是否第一阶段仅作占位

### 4. 给成员 B 自身的接口补充建议
- 后续应新增 `chat/voice` 作为语音问答入口
- 后续应新增 `chat/interrupt` 处理打断
- 后续应新增 `session/history` 支持历史消息回看


## 下一步建议

1. 先完成启动验证
2. 再完成 Android 接口联调
3. 然后确认最小闭环稳定
4. 接着补成员 C 的 RAG 接口契约
5. 最后再向语音和数字人方向扩展
