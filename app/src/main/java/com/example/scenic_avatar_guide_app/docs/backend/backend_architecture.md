# 后端对接基线说明

> 2026-04-30 更新：本文件下方的大型”完整版后端目录设计”属于历史规划稿，**不再作为当前 Android 联调依据**。
>
> 当前正式基线以 [API_CONTRACT.md](./API_CONTRACT.md) 为准，Android 客户端依赖以下接口：
>
> **Android 端核心接口（streaming-only）**：
> 1. `GET /api/v1/health`
> 2. `POST /api/v1/session/create`
> 3. `POST /api/v1/chat/text/stream`
> 4. `POST /api/v1/chat/abort`
>
> **辅助接口**：
> 5. `POST /api/v1/upload/image` — 图文问答前置（图片上传）
>
> **TTS 接口（Edge-TTS 方案，测试/兜底用）**：
> 6. `POST /api/v1/tts/synthesize`
> 7. `GET /api/v1/tts/voices`
> 8. `GET /api/v1/tts/file/{file_name}`
>
> 当前职责划分：
>
> 1. **Android 端仅使用流式接口**：`POST /api/v1/chat/text/stream` 返回 `text_delta`、`tts_segment`、`avatar_action`、`sources`、`metadata`、`done` 等事件；后端负责按可朗读片段生成音频
> 2. `tts_segment` 由后端分段生成，Android 端通过 ExoPlayer 排队播放分段 `audio_url`
> 3. Android 端负责 ASR、消费事件流、分段播放、口型与数字人联动
>
> 当前测试阶段补充约定：
>
> 1. `edge-tts` **已并入后端主链路**，新增 `/api/v1/tts/*`；Android 端不再直连独立 Python TTS 服务；
> 2. Android 端通过设置页配置同网段电脑的 `IP + 端口` 访问当前测试后端；
> 3. 系统 TTS 仅作为 **极端离线场景的兜底**，用于独立文本合成测试；主链路不再依赖端侧 TTS。
> 4. `POST /api/v1/chat/text`（非流式）已从 Android 端移除，仅作为后端内部保留接口。
> 5. 流式接口详细方案见 `../api/API_STREAMING.md`。
> 6. Android 端音频播放通过 `audio_url` 拼接 Base URL 访问 `GET /api/v1/tts/file/{file_name}`，不通过 Retrofit 直接调用。
>
> 以下能力都只能视为后续扩展，不能当作当前联调事实：
>
> 1. `chat/voice`
> 2. 服务端 ASR 主链路
> 3. 独立路线推荐、游客偏好、会话历史接口
> 4. OpenAvatarChat / LiteAvatar / 管理后台的完整落地结构
>
> 阅读下方历史规划时，若与本更新说明冲突，以本更新说明和 `API_CONTRACT.md` 为准。

# 《景灵智导 / scenic-avatar-guide》完整版后端文件框架设计

> 适用范围：本文件用于本项目**最终完整版本**的后端目录设计，不仅覆盖第一阶段的 Android 游客端与统一接口层，也覆盖后续管理侧 Web、知识库管理、游客反馈分析、数据大屏、OpenAvatarChat / LiteAvatar 部署、第三方模型接入、统计分析与运维部署。
>
> 设计目标：
> 1. 第一阶段可以快速落地开发；
> 2. 第二阶段扩展管理后台和统计分析时无需大改；
> 3. 明确成员 B、成员 C 的工作边界；
> 4. 为 Android 游客端和管理侧 Web 预留稳定接口；
> 5. 将 OpenAvatarChat 作为**主框架/编排层**，LiteAvatar 作为**默认数字人驱动引擎**纳入整体部署。

---

## 一、整体设计原则

### 1. 主框架与数字人引擎分层
- **OpenAvatarChat**：作为多模态数字人交互主框架，负责会话编排、ASR/LLM/TTS/Avatar 调度、对话控制、前后端联动。
- **LiteAvatar**：作为默认 2D 数字人驱动引擎，负责口型同步、说话动画与轻量实时渲染。
- 本项目最终实际部署建议为：**OpenAvatarChat + LiteAvatar + 自研统一后端服务 + Android App + 管理侧 Web**。

### 2. 后端分层
后端统一拆成以下几层：
- **API 接口层**：对外提供给 Android App 与管理 Web 的接口；
- **业务服务层**：实现会话、问答、路线推荐、知识库、统计分析等逻辑；
- **RAG / 知识库层**：实现文档导入、切块、检索、重排、回答生成；
- **第三方集成层**：对接 PPIO、OpenAvatarChat、LiteAvatar、ASR、TTS、数据库、对象存储；
- **部署与运维层**：Docker / WSL2 / Nginx / Systemd / Compose / 环境变量等。

### 3. 最终版本能力范围
本目录结构覆盖以下最终功能：
- 游客侧 Android App：
  - 文本问答
  - 语音问答
  - 数字人播报
  - 景点讲解
  - 个性化路线推荐
  - 会话历史
  - 满意度反馈
  - 可选拍照识景 / GPS 场景扩展
- 管理侧 Web：
  - 登录与权限
  - 知识库管理
  - 景点与路线管理
  - 数字人配置管理
  - 问答记录查看
  - 游客反馈分析
  - 热门问题统计
  - 数据大屏概览
- 后端支撑：
  - 会话管理
  - 知识库问答
  - OpenAvatarChat 编排接入
  - LiteAvatar 调用
  - ASR / TTS 调用
  - PPIO LLM / VLM 接入
  - 数据统计与分析任务
  - 部署脚本与配置管理

---

## 二、推荐仓库名

- 中文项目名：**景灵智导**
- 英文项目名：**ScenicGuide Avatar**
- GitHub 仓库名：**`scenic-avatar-guide`**

---

## 三、完整版后端目录结构（带注释）

```text
scenic-avatar-guide/
├── README.md                                    # 项目总说明：项目简介、技术栈、快速启动、目录说明
├── LICENSE                                      # 自研部分许可证
├── .gitignore                                   # Git 忽略规则
├── .env.example                                 # 环境变量模板
├── pyproject.toml                               # Python 项目依赖与构建配置
├── requirements.txt                             # 兼容 pip 的依赖清单
├── docker-compose.yml                           # 完整开发/演示环境编排：API、DB、Redis、Nginx、管理前端等
├── Makefile                                     # 常用命令：run / test / lint / seed / import-docs / compose-up
│
├── docs/                                        # 项目文档总目录
│   ├── architecture/
│   │   ├── overall-architecture.md              # 系统总体架构说明
│   │   ├── backend-architecture.md              # 后端分层与模块说明
│   │   ├── openavatarchat-integration.md        # OpenAvatarChat / LiteAvatar 集成说明
│   │   ├── rag-architecture.md                  # 知识库/RAG 架构说明
│   │   ├── android-api-flow.md                  # Android 游客端主要交互流程
│   │   └── admin-web-api-flow.md                # 管理侧 Web 主要交互流程
│   ├── api/
│   │   ├── stage1-api.md                        # 第一阶段接口文档
│   │   ├── final-api.md                         # 最终完整接口文档
│   │   ├── app-openapi.yaml                     # 游客侧 App OpenAPI 描述
│   │   ├── admin-openapi.yaml                   # 管理侧 Web OpenAPI 描述
│   │   └── error-codes.md                       # 错误码说明
│   ├── deployment/
│   │   ├── wsl2-dev-guide.md                    # WSL2 开发环境说明
│   │   ├── docker-dev-guide.md                  # Docker / Compose 开发说明
│   │   ├── local-demo-guide.md                  # 本地演示部署说明
│   │   ├── server-deploy-guide.md               # 服务器部署说明
│   │   └── env-vars.md                          # 环境变量说明
│   ├── database/
│   │   ├── data-model.md                        # 数据库表设计说明
│   │   └── migration-guide.md                   # 数据迁移说明
│   └── testing/
│       ├── qa-test-plan.md                      # 问答测试计划
│       ├── latency-test-plan.md                 # 延迟测试计划
│       └── regression-checklist.md              # 回归检查清单
│
├── scripts/                                     # 脚本目录，供初始化、导入、部署、测试使用
│   ├── bootstrap/
│   │   ├── init_env.sh                          # 初始化开发环境
│   │   ├── init_db.py                           # 初始化数据库
│   │   ├── create_admin.py                      # 初始化管理员账号
│   │   └── seed_demo_data.py                    # 导入示范景区与景点数据
│   ├── knowledge/
│   │   ├── import_knowledge.py                  # 导入原始知识文档
│   │   ├── clean_knowledge.py                   # 清洗文档
│   │   ├── chunk_knowledge.py                   # 文档切块
│   │   ├── build_vector_index.py                # 建立/重建向量索引
│   │   └── export_knowledge_report.py           # 导出知识库处理报告
│   ├── deployment/
│   │   ├── run_api.sh                           # 启动主 API 服务
│   │   ├── run_worker.sh                        # 启动异步任务 Worker
│   │   ├── run_admin_web.sh                     # 启动管理侧 Web（本地开发）
│   │   ├── run_nginx.sh                         # 启动 Nginx
│   │   └── check_services.sh                    # 检查关键服务状态
│   ├── testing/
│   │   ├── smoke_test.sh                        # 冒烟测试
│   │   ├── api_benchmark.py                     # 接口压测脚本
│   │   ├── qa_eval.py                           # 知识问答评测脚本
│   │   └── route_eval.py                        # 路线推荐评测脚本
│   └── maintenance/
│       ├── rotate_logs.sh                       # 日志轮转
│       ├── cleanup_temp.sh                      # 清理临时文件
│       └── backup_db.sh                         # 数据库备份
│
├── data/                                        # 项目数据目录（不建议把大型数据直接提交到仓库）
│   ├── raw/
│   │   ├── scenic_docs/                         # 原始景区资料（文史、讲解词、FAQ、公告等）
│   │   ├── scenic_images/                       # 景区相关图片，用于 VLM / 管理后台展示
│   │   └── route_materials/                     # 路线素材与说明
│   ├── processed/
│   │   ├── cleaned_docs/                        # 清洗后的知识文档
│   │   ├── chunks/                              # 切块结果
│   │   ├── embeddings/                          # 向量化产物（若本地存储）
│   │   └── metadata/                            # 文档元数据、标签、来源信息
│   ├── fixtures/
│   │   ├── demo_questions.json                  # 问答测试样例
│   │   ├── scenic_spots.json                    # 景点基础数据
│   │   └── route_templates.json                 # 路线模板数据
│   └── temp/                                    # 临时文件目录
│
├── storage/                                     # 运行时文件存储目录
│   ├── audio/
│   │   ├── input/                               # 游客上传的原始音频
│   │   ├── asr_cache/                           # ASR 中间缓存
│   │   └── output/                              # TTS 生成的语音输出
│   ├── avatar/
│   │   ├── frames/                              # 数字人帧缓存
│   │   ├── clips/                               # 数字人生成片段缓存
│   │   └── profiles/                            # 数字人形象配置缓存
│   ├── upload/
│   │   ├── knowledge/                           # 管理端上传的知识文档
│   │   └── assets/                              # 管理端上传的图片、音频、头像资源
│   ├── reports/
│   │   ├── daily/                               # 日报
│   │   ├── weekly/                              # 周报
│   │   └── exports/                             # 导出报表
│   └── logs/                                    # 运行日志
│
├── deploy/                                      # 部署与运维相关配置
│   ├── docker/
│   │   ├── api.Dockerfile                       # 自研 API 服务镜像
│   │   ├── worker.Dockerfile                    # 异步任务 Worker 镜像
│   │   ├── admin-web.Dockerfile                 # 管理侧 Web 镜像
│   │   └── nginx.Dockerfile                     # Nginx 镜像（可选）
│   ├── compose/
│   │   ├── compose.dev.yml                      # 本地开发环境
│   │   ├── compose.demo.yml                     # 演示环境
│   │   └── compose.prod.yml                     # 最终部署环境
│   ├── nginx/
│   │   ├── nginx.conf                           # Nginx 主配置
│   │   ├── app.conf                             # 游客侧 API 转发配置
│   │   └── admin.conf                           # 管理侧 Web/API 转发配置
│   ├── systemd/
│   │   ├── scenic-api.service                   # API 服务 systemd
│   │   ├── scenic-worker.service                # Worker 服务 systemd
│   │   └── scenic-admin.service                 # 管理端服务 systemd
│   ├── env/
│   │   ├── dev.env                              # 开发环境变量
│   │   ├── demo.env                             # 演示环境变量
│   │   └── prod.env                             # 生产/比赛答辩环境变量
│   └── wsl2/
│       ├── setup_wsl2.md                        # WSL2 统一开发说明
│       └── docker_engine_in_wsl2.md             # 可选：WSL2 内 Docker Engine 安装说明
│
├── third_party/                                 # 第三方项目接入位（建议用 submodule 或单独部署）
│   ├── README.md                                # 说明哪些项目使用 submodule，哪些仅文档登记
│   ├── openavatarchat/                          # 可选：OpenAvatarChat 子模块/镜像构建目录
│   │   ├── README.md                            # 如何拉取官方仓库、如何启动
│   │   ├── config/                              # 引用或复制后的配置文件
│   │   ├── scripts/                             # 本项目针对 OpenAvatarChat 的启动封装脚本
│   │   └── patches/                             # 如需对官方仓库做兼容补丁，放在这里
│   ├── liteavatar/                              # 可选：LiteAvatar 子模块/参考脚本目录
│   │   ├── README.md                            # LiteAvatar 集成说明
│   │   └── assets/                              # LiteAvatar 示例资产（仅小文件/占位）
│   └── openavatarchat-webui/                    # 可选：官方 WebUI 接入说明（主要用于内部调试，不是管理后台）
│       └── README.md
│
├── tests/                                       # 测试目录
│   ├── conftest.py                              # Pytest 公共 fixture
│   ├── unit/
│   │   ├── test_session_service.py              # 会话服务单元测试
│   │   ├── test_chat_service.py                 # 问答服务单元测试
│   │   ├── test_route_service.py                # 路线推荐单元测试
│   │   ├── test_rag_service.py                  # RAG 单元测试
│   │   ├── test_feedback_service.py             # 反馈分析单元测试
│   │   └── test_permissions.py                  # 权限控制测试
│   ├── integration/
│   │   ├── test_api_app.py                      # 游客侧 App 接口联调测试
│   │   ├── test_api_admin.py                    # 管理侧 Web 接口联调测试
│   │   ├── test_openavatarchat_integration.py   # OpenAvatarChat 集成测试
│   │   ├── test_ppio_client.py                  # PPIO 调用测试
│   │   ├── test_asr_tts_pipeline.py             # ASR-TTS 链路测试
│   │   └── test_db_repository.py                # 数据访问层集成测试
│   ├── e2e/
│   │   ├── test_app_full_chat_flow.py           # 游客端完整问答链路
│   │   ├── test_app_voice_flow.py               # 游客端语音完整链路
│   │   └── test_admin_knowledge_flow.py         # 管理端知识库完整链路
│   └── fixtures/
│       ├── sample_audio.wav                     # 测试音频
│       ├── sample_docs.json                     # 测试文档
│       └── sample_route_request.json            # 路线推荐测试请求
│
└── backend/                                     # 自研后端核心代码目录
    ├── app/
    │   ├── main.py                              # FastAPI 主入口
    │   ├── bootstrap.py                         # 应用启动时初始化逻辑
    │   │
    │   ├── core/                                # 核心基础设施层
    │   │   ├── config.py                        # 环境变量与全局配置
    │   │   ├── constants.py                     # 常量定义
    │   │   ├── logger.py                        # 日志配置
    │   │   ├── response.py                      # 统一响应格式
    │   │   ├── exceptions.py                    # 自定义异常
    │   │   ├── security.py                      # 鉴权、安全相关工具
    │   │   ├── middleware.py                    # 中间件：日志、追踪、异常处理、CORS
    │   │   ├── cache.py                         # Redis 缓存接入
    │   │   ├── db.py                            # 数据库连接
    │   │   ├── settings_schema.py               # 配置数据结构
    │   │   └── feature_flags.py                 # 功能开关（例如是否启用 VLM / GPS）
    │   │
    │   ├── api/                                 # API 层：只处理参数、鉴权、调用 service、返回
    │   │   ├── deps.py                          # 依赖注入
    │   │   ├── router.py                        # 路由总入口
    │   │   └── v1/
    │   │       ├── health.py                    # 健康检查接口
    │   │       ├── app_session.py               # 游客侧 App：会话接口
    │   │       ├── app_chat.py                  # 游客侧 App：文本问答、语音问答、打断、历史
    │   │       ├── app_profile.py               # 游客侧 App：游客偏好、兴趣标签
    │   │       ├── app_route.py                 # 游客侧 App：路线推荐、景点路线详情
    │   │       ├── app_scenic.py                # 游客侧 App：景区、景点、导览点基础信息
    │   │       ├── app_feedback.py              # 游客侧 App：满意度、问题反馈
    │   │       ├── app_media.py                 # 游客侧 App：音频文件、头像资源、图片资源
    │   │       ├── app_location.py              # 游客侧 App：可选定位/GPS 场景接口
    │   │       ├── admin_auth.py                # 管理端：登录、登出、鉴权、权限校验
    │   │       ├── admin_dashboard.py           # 管理端：数据概览、大屏接口
    │   │       ├── admin_knowledge.py           # 管理端：知识库管理
    │   │       ├── admin_scenic.py              # 管理端：景区/景点/路线管理
    │   │       ├── admin_avatar.py              # 管理端：数字人配置管理
    │   │       ├── admin_sessions.py            # 管理端：会话记录与问答审查
    │   │       ├── admin_feedback.py            # 管理端：游客反馈与情绪趋势
    │   │       ├── admin_reports.py             # 管理端：报表导出、日报周报
    │   │       ├── admin_assets.py              # 管理端：上传图片、文档、数字人资源
    │   │       └── admin_system.py              # 管理端：系统配置、服务状态查看
    │   │
    │   ├── schemas/                             # Pydantic 数据结构定义
    │   │   ├── common.py                        # 通用请求/响应结构
    │   │   ├── auth.py                          # 登录鉴权数据结构
    │   │   ├── session.py                       # 会话请求/响应结构
    │   │   ├── chat.py                          # 问答请求/响应结构
    │   │   ├── user_profile.py                  # 用户偏好结构
    │   │   ├── route.py                         # 路线推荐结构
    │   │   ├── scenic.py                        # 景区景点结构
    │   │   ├── feedback.py                      # 反馈与满意度结构
    │   │   ├── media.py                         # 音频/资源结构
    │   │   ├── location.py                      # 定位/GPS 结构
    │   │   ├── knowledge.py                     # 知识库相关结构
    │   │   ├── avatar.py                        # 数字人配置结构
    │   │   ├── dashboard.py                     # 仪表盘统计结构
    │   │   ├── report.py                        # 报表结构
    │   │   └── system.py                        # 系统状态/配置结构
    │   │
    │   ├── models/                              # 数据库模型（ORM）
    │   │   ├── base.py                          # ORM 基类
    │   │   ├── admin_user.py                    # 管理员账号
    │   │   ├── admin_role.py                    # 管理员角色
    │   │   ├── admin_permission.py              # 权限定义
    │   │   ├── scenic_project.py                # 景区项目（支持未来多景区）
    │   │   ├── scenic_spot.py                   # 景点表
    │   │   ├── scenic_route.py                  # 路线模板表
    │   │   ├── scenic_route_spot.py             # 路线-景点关联表
    │   │   ├── avatar_profile.py                # 数字人形象配置
    │   │   ├── tts_voice_profile.py             # 语音音色配置
    │   │   ├── user_profile.py                  # 游客偏好表
    │   │   ├── chat_session.py                  # 会话表
    │   │   ├── chat_message.py                  # 问答记录表
    │   │   ├── chat_attachment.py               # 问答附件（音频/图片）
    │   │   ├── knowledge_document.py            # 知识文档表
    │   │   ├── knowledge_chunk.py               # 知识切块表
    │   │   ├── knowledge_tag.py                 # 知识标签表
    │   │   ├── feedback_record.py               # 满意度反馈表
    │   │   ├── emotion_record.py                # 情绪标签记录
    │   │   ├── route_recommend_record.py        # 路线推荐记录
    │   │   ├── analytics_snapshot.py            # 统计快照
    │   │   ├── operation_log.py                 # 后台操作日志
    │   │   └── system_config.py                 # 系统配置表
    │   │
    │   ├── repositories/                        # 数据访问层：只负责 CRUD / Query
    │   │   ├── admin_repository.py              # 管理员与权限查询
    │   │   ├── scenic_repository.py             # 景区景点查询
    │   │   ├── route_repository.py              # 路线与路线记录查询
    │   │   ├── avatar_repository.py             # 数字人配置查询
    │   │   ├── user_repository.py               # 游客偏好查询
    │   │   ├── session_repository.py            # 会话查询
    │   │   ├── chat_repository.py               # 问答记录查询
    │   │   ├── knowledge_repository.py          # 知识文档/切块查询
    │   │   ├── feedback_repository.py           # 反馈与情绪记录查询
    │   │   ├── analytics_repository.py          # 统计数据查询
    │   │   └── system_repository.py             # 系统配置查询
    │   │
    │   ├── services/                            # 业务服务层
    │   │   ├── auth_service.py                  # 管理端登录、权限校验
    │   │   ├── session_service.py               # 会话创建、关闭、上下文管理
    │   │   ├── chat_service.py                  # 文本问答总编排
    │   │   ├── voice_service.py                 # 语音问答链路编排
    │   │   ├── media_service.py                 # 音频/图片/文件读写
    │   │   ├── profile_service.py               # 游客偏好服务
    │   │   ├── scenic_service.py                # 景区/景点基础信息服务
    │   │   ├── route_service.py                 # 个性化路线推荐服务
    │   │   ├── avatar_service.py                # 数字人配置与调用服务
    │   │   ├── knowledge_service.py             # 知识库管理服务
    │   │   ├── rag_service.py                   # RAG 聚合服务
    │   │   ├── feedback_service.py              # 游客反馈、满意度、情绪标签
    │   │   ├── analytics_service.py             # 热门问题、趋势统计、仪表盘聚合
    │   │   ├── report_service.py                # 日报/周报/导出报表
    │   │   ├── admin_system_service.py          # 系统配置与状态服务
    │   │   └── location_service.py              # 可选定位/GPS 逻辑
    │   │
    │   ├── rag/                                 # 知识库/RAG 专用模块
    │   │   ├── document_cleaner.py              # 文档清洗
    │   │   ├── document_parser.py               # 文档解析（txt/md/pdf/docx 等）
    │   │   ├── chunker.py                       # 文本切块
    │   │   ├── embedder.py                      # 向量化接口
    │   │   ├── retriever.py                     # 召回器
    │   │   ├── reranker.py                      # 重排器
    │   │   ├── prompt_builder.py                # Prompt 构建
    │   │   ├── answer_generator.py              # 基于 LLM 的答案生成
    │   │   ├── hallucination_guard.py           # 幻觉控制与拒答策略
    │   │   ├── source_formatter.py              # 引用来源格式化
    │   │   └── evaluators.py                    # RAG 评估工具
    │   │
    │   ├── analytics/                           # 数据分析与报表逻辑
    │   │   ├── hot_questions.py                 # 热门问题统计
    │   │   ├── sentiment_trend.py               # 情绪趋势分析
    │   │   ├── topic_cluster.py                 # 游客关注点聚类
    │   │   ├── latency_stats.py                 # 响应耗时统计
    │   │   ├── route_usage_stats.py             # 路线使用分析
    │   │   ├── satisfaction_stats.py            # 满意度分析
    │   │   └── dashboard_aggregator.py          # 大屏指标聚合
    │   │
    │   ├── integrations/                        # 第三方集成层
    │   │   ├── ppio/
    │   │   │   ├── client.py                    # PPIO 基础客户端
    │   │   │   ├── llm_client.py                # PPIO LLM 调用封装
    │   │   │   ├── vlm_client.py                # PPIO VLM 调用封装
    │   │   │   └── embeddings_client.py         # PPIO 向量化/嵌入（如使用）
    │   │   ├── openavatarchat/
    │   │   │   ├── client.py                    # OpenAvatarChat 服务调用封装
    │   │   │   ├── session_adapter.py           # 会话适配
    │   │   │   ├── liteavatar_adapter.py        # LiteAvatar 适配层
    │   │   │   ├── interrupt_adapter.py         # 打断/双工适配
    │   │   │   └── config_adapter.py            # OpenAvatarChat 配置映射
    │   │   ├── asr/
    │   │   │   ├── base.py                      # ASR 抽象基类
    │   │   │   ├── sensevoice_adapter.py        # SenseVoice 接入
    │   │   │   └── whisper_adapter.py           # 可选：Whisper 兜底
    │   │   ├── tts/
    │   │   │   ├── base.py                      # TTS 抽象基类
    │   │   │   ├── cosyvoice_adapter.py         # CosyVoice 接入
    │   │   │   └── api_tts_adapter.py           # 云端 TTS 兜底适配
    │   │   ├── storage/
    │   │   │   ├── local_file_storage.py        # 本地文件存储
    │   │   │   └── object_storage.py            # 可选对象存储（OSS / MinIO）
    │   │   ├── database/
    │   │   │   └── vector_store.py              # 向量索引适配
    │   │   └── monitor/
    │   │       └── healthcheck_client.py        # 服务健康检查
    │   │
    │   ├── workers/                             # 异步任务执行层
    │   │   ├── queue.py                         # 队列入口
    │   │   ├── knowledge_worker.py              # 知识库处理任务
    │   │   ├── analytics_worker.py              # 统计分析任务
    │   │   ├── report_worker.py                 # 报表生成任务
    │   │   └── cleanup_worker.py                # 清理任务
    │   │
    │   ├── utils/                               # 通用工具
    │   │   ├── id_util.py                       # ID 生成
    │   │   ├── time_util.py                     # 时间处理
    │   │   ├── file_util.py                     # 文件处理
    │   │   ├── text_util.py                     # 文本清理
    │   │   ├── audio_util.py                    # 音频处理与转码
    │   │   ├── image_util.py                    # 图片处理
    │   │   ├── hash_util.py                     # 哈希工具
    │   │   └── validate_util.py                 # 校验工具
    │   │
    │   └── migrations/                          # 数据库迁移目录（Alembic 等）
    │       ├── env.py
    │       └── versions/
    │
    ├── admin-web/                               # 管理侧 Web 前端（成员 C 负责）
    │   ├── package.json                         # 前端依赖
    │   ├── vite.config.ts                       # Vite 配置
    │   ├── tsconfig.json                        # TypeScript 配置
    │   ├── public/
    │   └── src/
    │       ├── main.ts                          # Web 前端入口
    │       ├── router/                          # 前端路由
    │       ├── api/                             # 调用 backend/app/api/v1/admin_* 接口
    │       ├── stores/                          # Pinia/状态管理
    │       ├── layouts/                         # 管理后台布局
    │       ├── views/
    │       │   ├── Login.vue                    # 登录页
    │       │   ├── Dashboard.vue                # 数据概览/大屏页
    │       │   ├── KnowledgeList.vue            # 知识库列表
    │       │   ├── KnowledgeUpload.vue          # 文档上传
    │       │   ├── ScenicSpots.vue              # 景点管理
    │       │   ├── Routes.vue                   # 路线管理
    │       │   ├── AvatarProfiles.vue           # 数字人配置页
    │       │   ├── Sessions.vue                 # 会话记录页
    │       │   ├── Feedback.vue                 # 游客反馈页
    │       │   ├── Reports.vue                  # 报表页
    │       │   └── SystemConfig.vue             # 系统配置页
    │       ├── components/
    │       │   ├── charts/                      # 图表组件
    │       │   ├── tables/                      # 表格组件
    │       │   └── forms/                       # 表单组件
    │       └── types/                           # TS 类型定义
    │
    └── app-client-contract/                     # 给 Android App 的接口契约与 SDK 说明（成员 B/C 共用）
        ├── README.md                            # Android 接口契约说明
        ├── openapi-app.yaml                     # Android 游客端专用 OpenAPI
        ├── examples/
        │   ├── chat_text_request.json           # 文本问答示例请求
        │   ├── chat_voice_request.http          # 语音问答示例请求
        │   └── route_recommend_response.json    # 路线推荐示例返回
        └── sdk/
            └── kotlin/                          # 可选：自动生成/手写 Kotlin SDK 封装说明
```

---

## 四、目录说明与职责解释

## 1. `third_party/`：OpenAvatarChat / LiteAvatar 部署位
这个目录不是让你们把第三方仓库代码全部手工塞进来，而是用于管理第三方依赖的**接入位**。

### 推荐做法
- `third_party/openavatarchat/`：记录如何拉取官方仓库，如何用官方配置启动。
- `third_party/liteavatar/`：记录 LiteAvatar 的集成说明与必要资产。
- 真正部署时，可以采用两种方式：
  1. **子模块方式（Git submodule）**
  2. **独立服务部署 + 本项目只保留接入说明**

### 为什么要单独放这一层
因为 OpenAvatarChat、LiteAvatar 不是你们自研业务代码，但它们又是系统关键组成部分。单独放一层，可以避免：
- 把第三方代码和自研代码混在一起；
- 后续升级官方版本时难以管理；
- 团队成员不清楚哪些文件能改、哪些不能改。

---

## 2. `backend/app/api/v1/`：明确分开 App 接口与管理 Web 接口
这一层是整个系统对外的 HTTP API。

### 面向游客侧 Android App 的接口
- `app_session.py`
- `app_chat.py`
- `app_profile.py`
- `app_route.py`
- `app_scenic.py`
- `app_feedback.py`
- `app_media.py`
- `app_location.py`

### 面向管理侧 Web 的接口
- `admin_auth.py`
- `admin_dashboard.py`
- `admin_knowledge.py`
- `admin_scenic.py`
- `admin_avatar.py`
- `admin_sessions.py`
- `admin_feedback.py`
- `admin_reports.py`
- `admin_assets.py`
- `admin_system.py`

### 这样拆分的意义
- 游客端和管理端的接口边界清楚；
- 未来接口鉴权策略可不同；
- Android 和 Web 各自联调更方便；
- 不会把所有接口都塞进一个 `chat.py` 或一个 `admin.py` 里导致混乱。

---

## 3. `backend/app/services/`：成员 B 与成员 C 的核心工作区
服务层负责真正的业务逻辑。

### 成员 B 主要负责的服务
成员 B 负责“实时交互主链路”，重点是把游客端问答链路跑通、跑稳。

对应文件：
- `session_service.py`
- `chat_service.py`
- `voice_service.py`
- `media_service.py`
- `avatar_service.py`
- 与 OpenAvatarChat / ASR / TTS 相关的集成层

B 的核心职责：
1. 对接 OpenAvatarChat；
2. 对接 LiteAvatar；
3. 对接 ASR / TTS；
4. 打通文本问答和语音问答；
5. 处理会话状态、打断、返回音频与数字人动作；
6. 保证游客侧 App 的接口稳定。

### 成员 C 主要负责的服务
成员 C 负责“知识库 + 路线推荐 + 管理侧业务 + 数据分析 + 管理 Web”。

对应文件：
- `profile_service.py`
- `scenic_service.py`
- `route_service.py`
- `knowledge_service.py`
- `rag_service.py`
- `feedback_service.py`
- `analytics_service.py`
- `report_service.py`
- `admin_system_service.py`
- `location_service.py`
- 管理侧 Web 前端目录 `backend/admin-web/`

C 的核心职责：
1. 构建景区知识库；
2. 实现 RAG 问答逻辑；
3. 实现个性化路线推荐；
4. 设计数据表、日志与统计逻辑；
5. 输出管理侧需要的各种接口；
6. 开发管理侧 Web 前端页面。

---

## 4. `backend/app/rag/`：最终版本知识库必须单独拆层
这个目录不是“可选优化”，而是最终版本必须保留的独立层。

### 为什么不能把知识库逻辑直接写进 `chat_service.py`
因为最终版本需要的不只是“能回答”，还包括：
- 文档导入与解析；
- 切块；
- 向量化；
- 检索；
- 重排；
- Prompt 拼接；
- 幻觉控制；
- 引用来源格式化；
- 评测与调优。

把这些都塞进聊天服务，会导致后面完全无法维护。

---

## 5. `backend/app/analytics/`：最终比赛作品必须有“游客洞察”
赛题不只是要求问答，还要求：
- 游客关注点分析；
- 情绪趋势报告；
- 服务建议反馈；
- 数据概览展示。

所以统计分析目录必须单独成立。

建议最少做这些分析模块：
- `hot_questions.py`
- `sentiment_trend.py`
- `topic_cluster.py`
- `latency_stats.py`
- `route_usage_stats.py`
- `satisfaction_stats.py`
- `dashboard_aggregator.py`

第一阶段可以只实现一部分，但目录必须先设计好。

---

## 6. `backend/admin-web/`：管理侧 Web 前端直接纳入同仓库
由于你们已经决定把管理侧网页交给成员 C，同时为了方便联调，建议将管理前端与后端放在同一仓库下，用子目录管理。

### 优点
- 成员 C 做业务服务时，能立刻联调页面；
- 后端接口和前端页面保持同步；
- 演示部署时更方便统一启动；
- 第二阶段不需要新建一个独立仓库再重新约定接口。

> 注意：这里的 `admin-web` 是你们自己的“景区管理后台”，不是 OpenAvatarChat 官方的 WebUI。  
> OpenAvatarChat-WebUI 主要用于官方框架调试与演示，而你们项目的管理后台要服务于知识库管理、游客反馈分析和数据大屏，业务完全不同。

---

## 7. `backend/app-client-contract/`：给 Android 端预留标准接口契约
虽然 Android 代码不在后端目录里，但为了让 A、B、C 能稳定联调，建议在后端仓库里保留一个契约目录。

它的作用是：
- 固化 App 接口字段；
- 存放游客端 OpenAPI；
- 提供请求/响应示例；
- 避免字段频繁变化导致 Android 联调困难。

---

## 五、最终版本功能与目录映射

### 1. 游客侧 Android App 功能
| 功能 | 主要目录 |
|---|---|
| 创建会话 / 历史会话 | `api/v1/app_session.py`, `services/session_service.py` |
| 文本问答 | `api/v1/app_chat.py`, `services/chat_service.py` |
| 语音问答 | `api/v1/app_chat.py`, `services/voice_service.py`, `integrations/asr`, `integrations/tts` |
| 数字人播报 | `services/avatar_service.py`, `integrations/openavatarchat` |
| 景点信息与讲解 | `api/v1/app_scenic.py`, `services/scenic_service.py` |
| 路线推荐 | `api/v1/app_route.py`, `services/route_service.py` |
| 游客偏好 | `api/v1/app_profile.py`, `services/profile_service.py` |
| 满意度反馈 | `api/v1/app_feedback.py`, `services/feedback_service.py` |
| 可选定位/GPS | `api/v1/app_location.py`, `services/location_service.py` |

### 2. 管理侧 Web 功能
| 功能 | 主要目录 |
|---|---|
| 登录与权限 | `api/v1/admin_auth.py`, `services/auth_service.py`, `models/admin_*` |
| 数据概览 / 大屏 | `api/v1/admin_dashboard.py`, `services/analytics_service.py`, `analytics/` |
| 知识库管理 | `api/v1/admin_knowledge.py`, `services/knowledge_service.py`, `rag/` |
| 景区/景点/路线管理 | `api/v1/admin_scenic.py`, `services/scenic_service.py`, `services/route_service.py` |
| 数字人配置 | `api/v1/admin_avatar.py`, `services/avatar_service.py` |
| 会话记录审查 | `api/v1/admin_sessions.py`, `repositories/chat_repository.py` |
| 游客反馈分析 | `api/v1/admin_feedback.py`, `services/feedback_service.py`, `analytics/sentiment_trend.py` |
| 报表导出 | `api/v1/admin_reports.py`, `services/report_service.py`, `workers/report_worker.py` |

---

## 六、OpenAvatarChat / LiteAvatar 在本项目中的位置

### 1. 为什么目录里既有 `third_party/`，又有 `integrations/openavatarchat/`
因为这两个目录职责不同：

- `third_party/openavatarchat/`：用于放**官方项目接入位**、官方配置、启动脚本、子模块或镜像封装说明；
- `backend/app/integrations/openavatarchat/`：用于放**你们自研后端对 OpenAvatarChat 的调用适配代码**。

### 2. 最终实际部署关系
最终部署关系建议如下：

- **OpenAvatarChat**：以独立服务方式启动，承担多模态交互主框架职责；
- **LiteAvatar**：作为 OpenAvatarChat 选定的默认 Avatar 引擎；
- **自研统一后端**：负责业务接口、知识库、路线推荐、统计分析、管理后台接口；
- **Android App**：调用自研统一后端；
- **管理侧 Web**：调用自研统一后端；
- 自研后端通过 `integrations/openavatarchat/` 与 OpenAvatarChat 通信。

### 3. 为什么不建议把 OpenAvatarChat 的代码直接融进 `backend/app/`
因为 OpenAvatarChat 是独立开源框架，后续你们可能需要：
- 升级官方版本；
- 更换配置；
- 只保留调用，不修改源码；
- 在演示环境单独部署它。

独立出来更容易维护。

---

## 七、成员 B 与成员 C 的完整工作边界

## 成员 B：实时交互主链路负责人
建议重点负责以下目录：

```text
backend/app/api/v1/app_session.py
backend/app/api/v1/app_chat.py
backend/app/api/v1/app_media.py
backend/app/services/session_service.py
backend/app/services/chat_service.py
backend/app/services/voice_service.py
backend/app/services/media_service.py
backend/app/services/avatar_service.py
backend/app/integrations/openavatarchat/
backend/app/integrations/asr/
backend/app/integrations/tts/
backend/app/core/
backend/app/main.py
backend/app/bootstrap.py
```

### B 的核心产出
- 游客侧 App 统一接口；
- 会话和问答主链路；
- 语音上传与音频回传；
- 数字人播报结果；
- OpenAvatarChat / LiteAvatar 对接；
- ASR / TTS 适配与故障降级。

---

## 成员 C：知识库、数据分析、管理 Web 负责人
建议重点负责以下目录：

```text
backend/app/api/v1/admin_*.py
backend/app/api/v1/app_profile.py
backend/app/api/v1/app_route.py
backend/app/api/v1/app_scenic.py
backend/app/api/v1/app_feedback.py
backend/app/api/v1/app_location.py
backend/app/services/profile_service.py
backend/app/services/scenic_service.py
backend/app/services/route_service.py
backend/app/services/knowledge_service.py
backend/app/services/rag_service.py
backend/app/services/feedback_service.py
backend/app/services/analytics_service.py
backend/app/services/report_service.py
backend/app/services/admin_system_service.py
backend/app/services/location_service.py
backend/app/rag/
backend/app/analytics/
backend/app/models/
backend/app/repositories/
backend/app/integrations/ppio/
backend/admin-web/
scripts/knowledge/
scripts/testing/qa_eval.py
```

### C 的核心产出
- 景区知识库与 RAG；
- 路线推荐与景点信息管理；
- 游客反馈、满意度与情绪分析；
- 热门问题、趋势统计与大屏指标；
- 管理侧 Web 页面；
- 管理员所需的知识库、景点、路线、报表接口。

---

## 八、第一阶段与最终版本的关系

### 第一阶段先做
- `app_session.py`
- `app_chat.py`
- `app_profile.py`
- `app_route.py`
- `app_scenic.py`
- `session_service.py`
- `chat_service.py`
- `voice_service.py`
- `avatar_service.py`
- `route_service.py`
- `rag/` 的最小版
- `integrations/ppio/llm_client.py`
- `integrations/openavatarchat/`
- `admin-web/` 可先只做静态/简化管理页

### 第二阶段补全
- `admin_auth.py`
- `admin_dashboard.py`
- `admin_knowledge.py`
- `admin_avatar.py`
- `admin_feedback.py`
- `analytics/` 全套
- `report_service.py`
- `workers/`
- `location_service.py`
- 更完整的权限控制和系统配置管理

---

## 九、实现建议

### 1. 不要一开始把所有模块都写满
目录要一次设计完整，但编码时按优先级推进：
1. 会话 + 文本问答；
2. 语音问答 + 数字人；
3. 路线推荐 + 知识库；
4. 管理侧基础页面；
5. 统计分析与报表；
6. 权限、定位、导出等增强功能。

### 2. 仓库中第三方代码尽量“弱耦合”
建议：
- OpenAvatarChat 用子模块或独立部署；
- LiteAvatar 由 OpenAvatarChat 调用；
- 你们只写适配层，不直接魔改第三方核心代码。

### 3. 统一命名与分层
- 面向游客侧 App 的接口全部以 `app_` 开头；
- 面向管理侧 Web 的接口全部以 `admin_` 开头；
- 服务层统一以 `*_service.py` 命名；
- 仓储层统一以 `*_repository.py` 命名。

---

## 十、结论

这份目录结构不是“第一阶段的简化骨架”，而是**面向最终完整作品的后端总框架**。  
它已经将以下内容全部纳入：

- 游客侧 Android App 的所有后端接口位；
- 管理侧 Web 的所有后端接口位；
- 成员 B 的实时交互与数字人主链路工作；
- 成员 C 的知识库、数据分析、管理后台工作；
- OpenAvatarChat 与 LiteAvatar 的部署与接入位；
- PPIO、ASR、TTS、RAG、统计分析、报表导出、部署运维等完整能力。

后续你们开发时，可以把这份文件当成：
1. 仓库建目录的依据；
2. B / C 任务边界划分依据；
3. 总体设计文档中的“后端架构设计”初稿；
4. 第二阶段扩展管理后台与分析模块的基础。
