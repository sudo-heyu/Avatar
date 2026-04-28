# Live2D 数字人实现说明

版本：v2.0  
日期：2026-04-28  
定位：按当前 Android 客户端源码和 API 契约整理

---

## 一、当前基线

当前数字人正式链路应理解为：

```text
后端返回 reply_text / avatar_action
→ Android 端请求后端 /api/v1/tts/synthesize 获取 audio_url
→ ExoPlayer 播放音频
→ 音频播放过程中驱动口型
→ 数字人根据状态和动作数据联动
```

这意味着：

1. TTS 由后端 Edge-TTS 服务统一提供
2. 后端返回文本与动作数据，移动端额外调用 TTS 接口获取音频
3. 当前正式接口仍是 `POST /api/v1/chat/text`，TTS 接口为 `POST /api/v1/tts/synthesize`

---

## 二、当前代码结构

### 2.1 状态与接口模型

```text
domain/model/
├── Models.kt
└── AvatarState.kt
```

### 2.2 数字人控制与渲染

```text
core/avatar/
├── AvatarController.kt
├── AvatarPlaybackManager.kt
├── Live2DRenderer.kt
├── Live2DRendererImpl.kt
├── Live2DModule.kt
└── Live2DGLSurfaceView.kt
```

### 2.3 TTS、UI 与原生桥接

```text
core/tts/TTSController.kt
ui/components/AvatarView.kt
app/src/main/java/com/live2d/demo/
app/src/main/cpp/
app/src/main/jniLibs/
```

### 2.4 当前仓库资源

当前仓库已经内置 Hiyori 示例模型资源：

```text
app/src/main/assets/live2d/hiyori/
├── Hiyori.model3.json
├── Hiyori.moc3
├── Hiyori.physics3.json
├── Hiyori.pose3.json
└── motions/*.motion3.json
```

因此，本仓库的说明应以 `jniLibs + cpp + JNI bridge` 路线为准，而不是默认写成 `Live2DCubismCore.aar` 直接接入。

---

## 三、与 API 契约的映射

当前数字人侧正式依赖字段：

1. `reply_text`
2. `avatar_action.expression`
3. `avatar_action.gesture`
4. `avatar_action.motion_queue`
5. `avatar_action.marks`
6. `metadata.intent`
7. `metadata.emotion`

当前 `AvatarState.kt` 已定义与契约一致的表情和动作值，文档不应再使用旧字段如 `audio_url` 或“服务端音频播报”。

---

## 四、当前实现差异

以下是当前代码状态，需要作为阅读后续历史计划时的前置说明：

1. `MainViewModel` 已优先消费 `response.avatarAction`；若后端只返回 stage1 最小字段，则再按 `intent/emotion` 做动作与表情降级。
2. `RemoteTTSController` 当前调用后端 Edge-TTS 服务获取音频 URL，并通过字符持续时间估算口型事件；若后端返回 `marks`，可切换为精确时间戳驱动。
3. `Live2DRendererImpl` 当前已经完成 JNI 初始化和参数入口封装，但真实参数细节仍依赖 JNI / C++ 层逐步补全。
4. 下文保留的阶段性规划仅作历史参考；若与本节冲突，以本节和 API 契约为准。

## 五、历史模块职责图（保留归档）

```
┌─────────────────────────────────────────────────────────────────────┐
│                          UI 层 (Compose)                             │
│  AvatarView.kt                                                       │
└─────────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────┐
│                        AvatarController                              │
│  - 状态管理 (IDLE, SPEAKING, THINKING...)                           │
│  - 接收后端数据                                                      │
│  - 协调 TTS 和渲染器                                                 │
└─────────────────────────────────────────────────────────────────────┘
                    │                           │
                    ▼                           ▼
┌───────────────────────────────┐   ┌───────────────────────────────┐
│       TTSController           │   │       Live2DRenderer          │
│  - 讯飞/系统 TTS              │   │  - 加载 .moc3 模型            │
│  - 音素回调                   │   │  - 参数控制 (口型/表情)       │
│  - 播放控制                   │   │  - 动作播放                   │
└───────────────────────────────┘   │  - 换装换贴图                 │
            │                       └───────────────────────────────┘
            │ 音素事件                         │
            └─────────────────────────────────┘
                          驱动口型
```

---

## 六、历史开发阶段划分

### Phase 1: SDK 集成与基础渲染（2天）

**目标**：在 App 中显示 Live2D 模型

**任务清单**：
- [ ] 添加 Live2D Cubism SDK 依赖
- [ ] 创建 Live2DRenderer 基础框架
- [ ] 实现模型加载（.moc3 + textures）
- [ ] 在 AvatarView 中显示模型
- [ ] 验证官方示例模型正常显示

**验收标准**：
- App 能显示 Live2D 角色
- 模型能播放空闲动画

---

### Phase 2: 参数控制与口型同步（2天）

**目标**：实现口型与 TTS 同步

**任务清单**：
- [ ] 实现 TTSController 接口
- [ ] 集成讯飞 TTS（已有 SDK）
- [ ] 实现音素回调监听
- [ ] 实现 PhonemeToVisemeMapper
- [ ] Live2DRenderer 实现口型参数映射
- [ ] 联调测试：说话时口型同步

**Live2D 参数映射**：
```kotlin
// 口型参数
ParamMouthOpenY  → mouthOpen (0.0 - 1.0)
ParamMouthForm   → mouthForm (-1.0 - 1.0)
```

**验收标准**：
- TTS 播放时，模型嘴巴同步开合
- 不同音素有不同嘴型

---

### Phase 3: 表情与动作系统（2天）

**目标**：实现表情切换和动作播放

**任务清单**：
- [ ] 定义表情与 Live2D 参数映射
- [ ] 实现表情切换方法
- [ ] 加载动作文件（.motion3.json）
- [ ] 实现动作播放（play, stop, loop）
- [ ] 集成 AvatarActionData 解析
- [ ] 测试各表情和动作

**表情映射表**：
| ExpressionType | Live2D 动作/参数 |
|----------------|------------------|
| NEUTRAL | 默认状态 |
| HAPPY | ParamMouthForm + 眼睛参数 |
| THINKING | thinking 动作文件 |
| WELCOMING | welcome 动作文件 |
| APOLOGETIC | bow 动作文件 |

**验收标准**：
- 能切换 5 种以上表情
- 能播放 3 种以上动作
- 表情过渡自然

---

### Phase 4: 与后端数据对接（1天）

**目标**：接收后端指令驱动数字人

**任务清单**：
- [ ] 实现 AvatarPlaybackManager
- [ ] 解析 AvatarActionData
- [ ] 实现完整播放流程
- [ ] 测试端到端流程

**播放流程**：
```
后端响应 → 解析 AvatarActionData → 设置表情 → 触发动作 → TTS播放 → 口型同步
```

**验收标准**：
- 收到后端数据后自动播放
- 表情、动作、口型协调工作
- 播放完成后恢复 IDLE

---

### Phase 5: 换装系统（1天）

**目标**：支持切换外观

**任务清单**：
- [ ] 定义 AvatarConfig 数据模型
- [ ] 实现贴图动态加载
- [ ] 实现分层换装（服装、发型、配饰）
- [ ] 创建形象配置 UI
- [ ] 测试换装功能

**换装能力**：
```kotlin
// 切换服装
renderer.switchCostume("costume_summer.png")

// 切换发型
renderer.switchHair("hair_ponytail.png")

// 切换配饰
renderer.switchAccessory("glasses_01.png")
```

**验收标准**：
- 能切换服装
- 能切换发型
- 切换后动作表情不受影响

---

### Phase 6: 优化与打磨（1天）

**目标**：提升用户体验

**任务清单**：
- [ ] 添加自动眨眼
- [ ] 添加呼吸动画
- [ ] 优化口型过渡
- [ ] 添加头部微动
- [ ] 性能优化
- [ ] 异常处理完善

**验收标准**：
- 动画流畅（≥30fps）
- 无卡顿
- 异常有兜底处理

---

## 七、历史关键代码设计

### 5.1 Live2DRenderer 核心接口

```kotlin
interface AvatarRenderer {
    // 生命周期
    fun initialize(context: Context, config: AvatarRenderConfig)
    suspend fun loadModel(modelPath: String): Result<Unit>
    fun release()
    
    // 状态控制
    fun updateState(state: AvatarFullState, deltaTimeMs: Long)
    
    // 参数控制
    fun setMouth(mouthOpen: Float, mouthForm: Float)
    fun setExpression(expression: ExpressionType, intensity: Float, transitionMs: Long)
    fun triggerGesture(gesture: GestureType, loop: Boolean)
    
    // 动画控制
    fun playMotion(group: String, index: Int, loop: Boolean = false)
    fun stopMotion()
    
    // 换装
    fun setTexture(index: Int, texturePath: String)
    
    // 视图
    fun getView(): View
}
```

### 5.2 Live2D 参数映射

```kotlin
object Live2DParams {
    // 口型
    const val MOUTH_OPEN_Y = "ParamMouthOpenY"
    const val MOUTH_FORM = "ParamMouthForm"
    
    // 眼睛
    const val EYE_L_OPEN = "ParamEyeLOpen"
    const val EYE_R_OPEN = "ParamEyeROpen"
    const val EYE_BALL_X = "ParamEyeBallX"
    const val EYE_BALL_Y = "ParamEyeBallY"
    
    // 眉毛
    const val BROW_L_Y = "ParamBrowLY"
    const val BROW_R_Y = "ParamBrowRY"
    const val BROW_L_ANGLE = "ParamBrowLAngle"
    const val BROW_R_ANGLE = "ParamBrowRAngle"
    
    // 头部
    const val ANGLE_X = "ParamAngleX"
    const val ANGLE_Y = "ParamAngleY"
    const val ANGLE_Z = "ParamAngleZ"
    
    // 身体
    const val BODY_ANGLE_X = "ParamBodyAngleX"
    const val BREATH = "ParamBreath"
}
```

### 5.3 音素到 Live2D 参数

```kotlin
object PhonemeToLive2D {
    fun map(phoneme: String): Pair<Float, Float> {
        // 返回 (mouthOpen, mouthForm)
        return when (phoneme.lowercase()) {
            // 闭唇音
            "b", "p", "m" -> 0.0f to 0.0f
            
            // 开口元音
            "a" -> 1.0f to 0.0f
            "o", "u" -> 0.6f to 0.5f    // 圆唇
            "e" -> 0.5f to 0.0f
            
            // 扁唇音
            "i", "ü" -> 0.4f to -0.5f   // 扁嘴
            
            // 其他辅音
            else -> 0.2f to 0.0f
        }
    }
}
```

---

## 八、历史依赖配置

### 6.1 build.gradle.kts

```kotlin
// Live2D Cubism SDK
// 方式一：本地 AAR（从官网下载）
implementation(files("libs/Live2DCubismCore.aar"))

// 方式二：如果有的话，使用 Maven
// implementation("com.live2d:cubism-core:4.r.7")

// ExoPlayer (音频播放)
implementation("androidx.media3:media3-exoplayer:1.2.1")

// 讯飞 SDK（备用端侧 TTS，当前主链路为后端 Edge-TTS）
// implementation(...)
```

### 6.2 资源目录

```
app/src/main/assets/
└── live2d/
    └── hiyori/                    # 模型目录
        ├── hiyori.model3.json     # 模型配置
        ├── hiyori.moc3            # 模型文件
        ├── textures/              # 贴图
        │   └── texture_00.png
        └── motions/               # 动作文件
            ├── idle_01.motion3.json
            ├── tap_body.motion3.json
            └── ...
```

---

## 九、历史风险与对策

| 风险 | 影响 | 对策 |
|------|------|------|
| Live2D SDK 集成困难 | 延期 | 先看官方 Sample，逐步迁移 |
| 免费模型质量差 | 效果不佳 | 先用官方模型，后期换定制 |
| 口型同步不自然 | 体验差 | 调整参数映射，增加过渡 |
| 性能问题 | 卡顿 | 减少参数更新频率，异步加载 |

---

## 十、历史时间规划

| 阶段 | 工作日 | 完成标志 |
|------|--------|---------|
| Phase 1 | Day 1-2 | 显示 Live2D 模型 |
| Phase 2 | Day 3-4 | 口型同步 TTS |
| Phase 3 | Day 5-6 | 表情动作系统 |
| Phase 4 | Day 7 | 后端数据对接 |
| Phase 5 | Day 8 | 换装系统 |
| Phase 6 | Day 9 | 优化打磨 |

**总计：约 9 个工作日**

---

## 十一、历史下一步行动

1. **立即**：下载 Live2D Cubism SDK for Native
2. **立即**：获取官方示例模型（Hiyori）
3. **开始 Phase 1**：SDK 集成与基础渲染
