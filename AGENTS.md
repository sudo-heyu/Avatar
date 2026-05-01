# 仓库指南

## 项目结构
单模块 Android 应用 `:app`。Kotlin/Java 代码位于 `app/src/main/java/com/example/scenic_avatar_guide_app`：
- `ui/` - Compose 界面、ViewModel、导航、组件
- `core/` - 平台服务：`avatar/`（Live2D）、`audio/`、`speech/`、`tts/`、`network/`
- `data/` - 仓库、远程 API、本地 DataStore
- `domain/` - 领域模型

原生代码：`app/src/main/cpp`（Live2D + JNI 桥接）、`app/src/main/jniLibs`（预编译 .so）、`app/src/main/assets`（Live2D 模型、着色器、Lottie）。

## 构建命令
```bash
gradlew.bat assembleDebug          # 调试 APK
gradlew.bat assembleRelease        # 发布 APK
gradlew.bat test                   # 单元测试
gradlew.bat connectedAndroidTest   # 仪器测试（需要设备/模拟器）
gradlew.bat lint                   # Lint 检查
```

## NDK 与原生构建要求
- **NDK 版本**：`27.0.12077973`（Gradle 自动下载）
- **CMake**：`3.22.1`
- **ABI 过滤器**：`arm64-v8a`、`x86`、`x86_64`
- 原生库名称：`Demo`（通过 `JniBridgeJava.java` 的 `System.loadLibrary("Demo")` 加载）
- JNI 桥接：`JniBridgeJava.java`（Kotlin 调用方）↔ `JniBridgeC.cpp`（原生实现）

## 本地依赖
- `app/libs/SparkChain.aar` - 讯飞语音识别 SDK
- `app/libs/Codec.aar` - 讯飞编解码器

## 后端配置
- 默认后端 URL：`http://10.0.2.2:8000/`（Android 模拟器本地回环地址）
- 可通过设置界面配置 → 持久化到 DataStore
- `NetworkModule.kt` 中的 `DynamicBaseUrlInterceptor` 在运行时重写请求

## 代码风格
- 4 空格缩进，类/Composable 用 `PascalCase`，方法/属性用 `camelCase`
- ViewModel 放在 `ui/screens/`，仓库放在 `data/repository/`
- UI 状态使用 `StateFlow` 或不可变模型
- C++ 文件遵循现有命名：`LAppView.cpp`、`JniBridgeC.cpp`

## 测试
- JUnit 4 用于单元测试（`app/src/test`）
- AndroidX JUnit/Espresso 用于仪器测试（`app/src/androidTest`）
- 测试文件以目标类命名：`MainViewModelTest.kt`

## 提交
简短、功能聚焦的中文主题（如 `添加服务器环境切换功能和UI组件`）。

## Live2D 数字人参数分层（上半身模式）

三层独立控制数字人参数：

| 参数 | Expression | Gesture | LipSync |
|------|:----------:|:-------:|:-------:|
| `ParamBrow*`、`ParamEye*`、`ParamCheek` | ✅ | ❌ | ❌ |
| `ParamMouthForm` | ✅ | ❌ | ❌ |
| `ParamMouthOpenY` | ❌ | ❌ | ✅ |
| `ParamAngleX/Y/Z` | ✅（叠加） | ✅（覆盖） | ❌ |
| `ParamBodyAngle*`、`ParamShoulder`、`ParamArm*`、`ParamHand*` | ❌ | ✅ | ❌ |
| `ParamBreath`、`ParamHair*`、`ParamRibbon*`、`ParamSkirt*` | ✅ | ❌ | ❌ |

**Expression**（`.exp3.json`）：持续情绪状态，带淡入淡出过渡。通过 Add 模式控制面部 + 头部微姿态。

**Gesture**（代码驱动）：事件驱动的功能性动作（点头、挥手）。覆盖 `ParamAngleX/Y/Z`、`ParamBodyAngle*`、手臂/手部参数。回到 IDLE 时归零所有 Gesture 参数。

**LipSync**（实时）：独占控制 `ParamMouthOpenY` 用于语音同步。

**约束**：
- `.exp3.json` 中不得添加 `ParamBodyAngle*`、`ParamArm*`、`ParamHand*`、`ParamShoulder`
- Gesture 预设不得修改面部参数或 `ParamMouthOpenY`/`ParamMouthForm`
- LipSync 必须是唯一写入 `ParamMouthOpenY` 的层
