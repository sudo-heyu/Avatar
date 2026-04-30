# Repository Guidelines

## Project Structure & Module Organization
This repository is a single-module Android app in `:app`. Kotlin and Java application code lives under `app/src/main/java/com/example/scenic_avatar_guide_app`, organized by layer: `ui/`, `core/`, `data/`, and `domain/`. Native Live2D and rendering code is under `app/src/main/cpp`, packaged native libraries are in `app/src/main/jniLibs`, and model/shader assets are in `app/src/main/assets`. Android resources remain in `app/src/main/res`. Unit tests go in `app/src/test`, and device or emulator tests go in `app/src/androidTest`.

## Build, Test, and Development Commands
Use the Gradle wrapper from the repository root.

- `./gradlew assembleDebug` builds the debug APK.
- `./gradlew assembleRelease` builds the release APK.
- `./gradlew test` runs local JVM unit tests.
- `./gradlew connectedAndroidTest` runs instrumentation tests on a connected device or emulator.
- `./gradlew lint` runs Android lint checks.
- `./gradlew clean` removes Gradle build outputs.

On Windows, use `gradlew.bat` instead of `./gradlew`.

## Coding Style & Naming Conventions
Follow the existing Kotlin style: 4-space indentation, `PascalCase` for classes and composables, `camelCase` for methods and properties, and package names mirroring the directory tree. Keep ViewModels in `ui/screens`, repository code in `data/repository`, and shared platform services in `core/*`. Prefer small, single-purpose classes and keep UI state in `StateFlow` or immutable models. Mirror existing native file naming for C++ sources such as `LAppView.cpp` and `JniBridgeC.cpp`.

## Testing Guidelines
JUnit 4 is configured for local tests, and AndroidX JUnit/Espresso are available for instrumentation. Name test files after the target class, for example `MainViewModelTest.kt` or `GuideRepositoryTest.kt`. Add unit tests for new business logic and ViewModel behavior; use instrumentation tests when Android framework, OpenGL, audio, or JNI integration is involved. No coverage gate is configured, so rely on meaningful tests around changed code.

## Commit & Pull Request Guidelines
Recent commits use short, feature-focused subjects in Chinese, for example `添加服务器环境切换功能和UI组件`. Keep commits scoped to one change and start with the user-visible intent. Pull requests should include a concise summary, affected areas, verification steps, linked issues, and screenshots or recordings for UI changes. Call out any NDK, SDK, or asset updates explicitly.

## Security & Configuration Tips
Do not commit secrets, test credentials, or machine-specific changes to `local.properties`. Keep environment-specific base URLs and speech SDK settings configurable, and avoid hardcoding keys in source files.

## Live2D Avatar 参数分层架构（上半身模式）

数字人渲染采用三层参数叠加机制，各层职责明确、互不重叠：

### 1. Expression 层（`.exp3.json`）
**职责**：持续情绪状态，通过 FadeIn/FadeOut 平滑过渡。

**控制参数**：
- 面部：`ParamBrowLY/RY/LX/RX/LAngle/RAngle/LForm/RForm`、`ParamEyeLOpen/ROpen/LSmile/RSmile`、`ParamEyeBallX/Y`、`ParamCheek`
- 嘴部变形：`ParamMouthForm`（嘴形：微笑/扁嘴/圆嘴）
- 头部微姿态：`ParamAngleX/Y/Z`（Add 叠加，情绪性偏移，如歉意时低头、兴奋时后仰）
- 生动感：`ParamBreath`、`ParamHairAhoge/Front/Back`、`ParamRibbon/SideupRibbon`、`ParamSkirt`

**不控制**：`ParamMouthOpenY`（开合度）、`ParamBodyAngleX/Y/Z`、`ParamArm*`、`ParamHand*`、`ParamShoulder`

### 2. Gesture 层（代码硬编码）
**职责**：功能性动作，事件驱动，有明确的开始和结束。

**控制参数**：
- 头部功能性动作：`ParamAngleX/Y/Z`（覆盖基础值，如点头、摇头）
- 身体旋转：`ParamBodyAngleX/Y/Z`
- 肩膀：`ParamShoulder`
- 手臂：`ParamArmLA/RA/LB/RB`
- 手部：`ParamHandL/R/LB/RB`

**不控制**：面部参数、`ParamMouthOpenY`、`ParamMouthForm`

**叠加机制**：Gesture 通过 `setParameter` 直接覆盖基础状态，Expression 在 C++ `LateUpdate` 中以 Add 模式叠加。因此 Gesture 的头部角度会覆盖 Expression 的基础值，但 Expression 的 Add 偏移仍会生效。Gesture 停止（IDLE）时归零所有 Gesture 层参数，Expression 的微姿态恢复主导。

### 3. LipSync 层（实时参数）
**职责**：语音口型同步，每帧实时更新。

**独占参数**：`ParamMouthOpenY`

**不控制**：`ParamMouthForm`（由 Expression 管）

**叠加机制**：直接覆盖 `ParamMouthOpenY`，不与其他层竞争。Expression 可以同时控制 `ParamMouthForm`，实现"笑着说话"、"圆唇说话"等不同嘴形。

### 参数管辖速查表

| 参数 | Expression | Gesture | LipSync |
|------|:----------:|:-------:|:-------:|
| `ParamBrow*` | ✅ | ❌ | ❌ |
| `ParamEye*` | ✅ | ❌ | ❌ |
| `ParamCheek` | ✅ | ❌ | ❌ |
| `ParamMouthForm` | ✅ | ❌ | ❌ |
| `ParamMouthOpenY` | ❌ | ❌ | ✅ |
| `ParamAngleX/Y/Z` | ✅(Add) | ✅(覆盖) | ❌ |
| `ParamBodyAngle*` | ❌ | ✅ | ❌ |
| `ParamShoulder` | ❌ | ✅ | ❌ |
| `ParamArm*` | ❌ | ✅ | ❌ |
| `ParamHand*` | ❌ | ✅ | ❌ |
| `ParamBreath` | ✅ | ❌ | ❌ |
| `ParamHair*` / `ParamRibbon*` / `ParamSkirt*` | ✅ | ❌ | ❌ |

### 修改约束

- 新增或修改 `.exp3.json` 时，**不得**加入 `ParamBodyAngle*`、`ParamArm*`、`ParamHand*`、`ParamShoulder` 参数。
- 新增 Gesture 预设时，**不得**修改面部参数和 `ParamMouthOpenY`/`ParamMouthForm`。
- LipSync 动画**独占** `ParamMouthOpenY`，其他层不得直接写入该参数。
