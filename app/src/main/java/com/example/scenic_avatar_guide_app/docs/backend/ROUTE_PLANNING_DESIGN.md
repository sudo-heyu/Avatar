# 后端路线规划方案说明

本文说明当前后端路线规划能力的入口、数据来源、匹配策略和降级逻辑。接口字段细节见 `docs/api/API_ANDROID.md` 和 `docs/api/API_ADMIN.md`。

## 结论

当前后端对游客侧提供 **2 条路线规划调用入口**，底层共用 `RouteService.recommend_route()`：

| 入口 | 接口 | 适用场景 |
|------|------|----------|
| 聊天路线模式 | `POST /api/v1/chat/text`、`POST /api/v1/chat/text/stream`，请求中 `mode=route` | 用户在会话里问“半天怎么玩”“亲子路线怎么走”等，需要同时返回文本、TTS 和结构化 `route_data` |
| 独立路线推荐 | `GET /api/v1/route/recommend`、`POST /api/v1/route/recommend` | App 或其他端直接获取路线数据，不依赖会话流式回答 |

管理端还提供 **3 类路线模板来源**，用于维护游客侧推荐可命中的路线库：

| 来源 | 接口 | 说明 |
|------|------|------|
| 上传路线文档解析 | `POST /api/v1/admin/routes/parse` | 上传 `.md`、`.txt`、`.docx`、`.xlsx`，先规则解析，再用 LLM 增强结构化结果 |
| 手动新增草稿 | `POST /api/v1/admin/routes/manual-draft` | 创建空草稿，由管理员手动填写路线信息与点位 |
| 服务器文件批量导入 | `POST /api/v1/admin/routes/import-from-file` | 从服务端已有 `游览路线.md` 等文件批量导入，可选择替换旧模板和生成封面图 |

因此，“有几条路线规划”需要按语义区分：

- 对游客侧调用入口来说：**2 条**。
- 对管理端模板创建来源来说：**3 类**。
- 对推荐匹配算法来说：**4 层优先级**，见下文。

## 核心模块

| 模块 | 职责 |
|------|------|
| `backend/app/api/v1/route.py` | 独立路线推荐接口，解析 Query/Body 参数，调用推荐服务 |
| `backend/app/services/chat_service.py` | 聊天 `mode=route` 分支，解析会话、用户问题、偏好与 TTS 输出 |
| `backend/app/services/route_service.py` | 路线模板解析、保存、推荐匹配、降级生成 |
| `backend/app/services/visitor_preference_service.py` | 结合显式参数、历史画像和用户问题解析兴趣标签与游玩时长 |
| `backend/app/integrations/document/document_structure_client.py` | 路线文档 LLM 结构化增强 |
| `backend/app/services/route_image_service.py` | 路线封面上传、复用、生成和删除 |

## 游客侧路线规划入口

### 1. 聊天 `mode=route`

聊天接口进入 `mode=route` 后，后端会：

1. 从 `options` 中读取 `route_id` / `route_name`，支持用户或前端指定某条路线。
2. 使用 LLM 或本地规则解析用户问题中的路线语义意图，如偏好、人群、约束、时长。
3. 使用 `VisitorPreferenceService` 合并用户画像、设备画像、问题文本和显式参数。
4. 调用 `RouteService.recommend_route()` 生成 `route_data`。
5. 把 `route_data` 转成面向用户的路线讲解文本。
6. 非流式接口直接返回；流式接口先下发 `route_data`，再输出路线文本和 TTS 分段。

典型返回包含：

- `reply_text`：给用户看的路线说明。
- `route_data`：结构化路线数据，包含路线名、总时长、亮点、提醒、点位和 polyline。
- 流式场景下还包含 `tts_segment` / `tts_segment_ready` 事件。

### 2. 独立路线推荐接口

`/api/v1/route/recommend` 不依赖会话，可通过 GET 或 POST 调用。它会：

1. 校验 `scenic_id`。
2. 读取 `duration_min`、`interest_tags`、`current_spot`、`question`、`route_id`、`route_name`。
3. 通过 `VisitorPreferenceService` 补全兴趣标签和时长。
4. 调用同一个 `RouteService.recommend_route()`。

该入口适合路线页、地图页、首页推荐卡片等不需要聊天上下文的场景。

## 推荐匹配策略

`RouteService.recommend_route()` 优先读取数据库中 `status=active` 且 `is_active=true` 的路线模板。匹配顺序如下：

| 优先级 | 策略 | 说明 |
|--------|------|------|
| 1 | `route_id` 精确匹配 | 前端明确指定路线 ID 时直接返回该模板 |
| 2 | `route_name` 精确 / 模糊匹配 | 用户或前端指定路线名时优先命中 |
| 3 | 语义评分匹配 | 根据兴趣标签、人群画像、时长、强度约束、当前位置、问题关键词综合打分 |
| 4 | 通用降级路线 | 没有可用模板时，按当前位置、兴趣和时长生成基础路线数据 |

语义评分主要考虑：

- 显式兴趣标签与模板 `interest_tags` 的交集。
- 用户画像和语义意图中的 `preferences`、`crowd`、`constraints`。
- 目标时长与模板 `duration_minutes` 的差值。
- “轻松”“少走路”等约束与 `intensity_level` 的匹配。
- 当前景点是否包含在路线点位中。
- 用户问题是否提到路线名、摘要或亮点关键词。

## 路线模板生命周期

### 1. 创建草稿

路线草稿来自文档上传、手动创建或服务器文件导入。文档上传支持 `.md`、`.txt`、`.docx`、`.xlsx`。

解析流程：

1. `DocumentParserService` 将文件转成纯文本。
2. 规则解析识别 Markdown 二级标题和“路线规划”等结构。
3. `DocumentStructureClient` 使用 LLM 按路线模板 JSON 结构增强解析结果。
4. 草稿保存到 `route_draft` 表，源文件保存到 `storage/knowledge/_routes/{scenic_id}/drafts/`。

### 2. 保存模板

管理员可保存单条草稿路线，也可批量保存全部路线。保存时写入：

- `route_template`：路线基础信息、时长、标签、人群、强度、摘要、亮点、提醒。
- `route_template_spot`：路线点位顺序、停留时间、讲解重点和描述。

保存时还可处理封面图：

- `upload`：上传封面。
- `generate`：生成封面。
- `reuse`：复用已有封面。
- `skip`：跳过封面处理。

### 3. 维护状态

管理端支持：

- 列表查询：`GET /api/v1/admin/routes`
- 更新模板：`PUT /api/v1/admin/routes/{route_id}`
- 启用 / 停用：`POST /api/v1/admin/routes/{route_id}/activate`
- 删除单条：`DELETE /api/v1/admin/routes/{route_id}`
- 删除某景区全部路线：`POST /api/v1/admin/routes/delete-all`
- 上传封面：`PUT /api/v1/admin/routes/{route_id}/cover`
- 重新生成封面：`POST /api/v1/admin/routes/{route_id}/regenerate-cover`

只有启用中的模板会参与游客侧推荐。

## 降级与限制

当前路线规划是“模板优先”的推荐系统，不是实时地图导航引擎。需要注意：

- `polyline` 目前由点位顺序生成，坐标使用后端占位逻辑或模板点位映射，不计算真实步行路网。
- `total_distance_m` 是按点位数量估算，不是地图服务返回的真实距离。
- 没有启用模板时，会生成通用 fallback 路线，保证接口可返回结构化数据。
- 路线语义解析依赖 LLM 时，如果 LLM 不可用，会退回本地规则和显式参数。
- 景区、路线模板和管理员权限都会按 `scenic_id` 校验。

## 相关文档

- `docs/api/API_ANDROID.md`：游客端聊天、独立路线推荐、`route_data` 结构。
- `docs/api/API_ADMIN.md`：管理端路线模板接口。
- `docs/knowledge/路线导览图实现方案.md`：路线导览图相关方案。
- `docs/knowledge/路线导览图接口说明.md`：路线导览图接口说明。
