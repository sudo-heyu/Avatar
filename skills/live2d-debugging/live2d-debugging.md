---
name: live2d-debugging
description: Live2D 数字人渲染常见问题排查指南，覆盖白屏、崩溃、参数不生效等问题
---

# Live2D 数字人调试排查指南

## 问题一：白屏/模型不显示

### 现象
- Live2D 区域显示纯白或纯色背景
- Lottie 过渡动画正常播放，但 Live2D 模型不出现

### 排查步骤

1. **检查 C++ 编译是否成功**
   ```bash
   ./gradlew :app:buildCMakeDebug
   ```
   确认没有编译错误，且 `.so` 文件被正确打包到 APK 中。

2. **检查模型加载日志**
   查看 logcat 中 `Live2DRendererImpl` 标签的日志：
   - `Model validated: ...` 表示模型文件读取成功
   - `Failed to load model` 表示加载失败

3. **检查 `LAppModel::Update()` 是否被破坏**
   关键代码结构必须是：
   ```cpp
   _model->LoadParameters();
   if (!_motionManager->IsFinished()) {
       _motionManager->UpdateMotion(_model, deltaTimeSeconds);
   }
   _model->SaveParameters();
   ```
   如果 `SaveParameters()` 被删除或 `LoadParameters()` / `SaveParameters()` 之间的逻辑被破坏，模型参数状态会异常，导致模型不可见。

4. **检查 `SetParameterValue()` 中的 `SaveParameters()`**
   此函数必须包含 `_model->SaveParameters()` 调用。如果删除，手动设置的参数不会被保存到基线。

5. **检查 GL 上下文**
   `Live2DGLSurfaceView.initialize()` 必须被调用且 `setRenderer()` 成功。如果 `isRendererSet` 为 false，`runOnRenderThread` 会丢弃所有参数设置请求。

6. **检查 surface attach**
   `Live2DRendererImpl.attachSurfaceView()` 必须在 `AndroidView` 的 `factory` 或 `update` 块中被调用。如果 renderer 没有 attach surface，`isModelLoaded` 返回 false，所有参数设置被跳过。

---

## 问题二：Native Crash (SIGSEGV) in GLThread

### 现象
```
Fatal signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x0 in tid [GLThread]
```

### 根因
在 `CubismFramework::Initialize()` 完成前调用了 `CubismIdManager::GetId()`，导致空指针解引用。

### 修复
三层保护：
1. **Kotlin 层**：`Live2DGLSurfaceView.runOnRenderThread()` 在 `!isRendererSet` 时直接返回
2. **Kotlin 层**：`Live2DRendererImpl.runOnRenderThread()` 在 `surfaceView == null` 时返回
3. **C++ 层**：`nativeSetParameter` 中添加：
   ```cpp
   if (!CubismFramework::IsInitialized()) return;
   ```

---

## 问题三：后台返回仍重新加载或等待数秒

### 现象
- 按 Home 或切到其他应用一段时间后返回，Live2D 区域先显示加载/过渡画面，几秒后模型才出现。
- logcat 可能重复出现 `LoadAssets()`、`SetupTextures()`、`ReloadAllRenderers()` 相关日志。

### 当前目标
只要应用进程没被清除，返回时模型应立即显示。完整重载只允许发生在进程被杀或 EGL context 被系统回收的兜底路径。

### 必须保持的实现
1. `Live2DGLSurfaceView.initialize()` 必须设置：
   ```kotlin
   preserveEGLContextOnPause = true
   ```
2. `Live2DGLSurfaceView.onPause()` 不要主动把 `isSurfaceCreated` 置为 false。
3. `AvatarView` 普通 dispose 只调用：
   ```kotlin
   renderer?.detachSurfaceView()
   ```
   不要调用 `renderer.release()`、`nativeOnStop()`、`nativeOnDestroy()`。
4. `DisposableEffect` 不要依赖 `renderer` 状态变化；使用 `rememberUpdatedState(renderer)` 获取最新实例，避免初始化时触发旧 effect 的 `onDispose()`。
5. `LAppDelegate::OnSurfaceCreate()` 首次加载时优先 `LoadAssets -> CreateRenderer` 成功；shader/renderer 重建只在已有模型时执行。

### 排查
- 如果每次返回都走 `onSurfaceCreated()`，优先检查 `preserveEGLContextOnPause` 是否被移除。
- 如果首屏黑屏或闪退，检查是否在首次 `OnSurfaceCreate` 中提前删除 shader 单例。
- 如果返回后状态/口型不更新，检查 `attachSurfaceView()` 是否重新绑定了 `onAfterDrawFrame` 和 `surfaceViewRef`。

---

## 问题四：模型显示全身而非上半身

### 排查
1. **检查 Compose 层**：`AvatarView` 中 `showUpperBodyOnly` 参数是否正确传递
2. **检查 `AndroidView` factory/update**：`renderer?.setUpperBodyMode(showUpperBodyOnly)` 必须在 factory 和 update 中都调用
3. **检查 C++ 层**：`LAppLive2DManager::OnUpdate()` 中的 `_upperBodyMode` 分支是否正确执行

### 关键 C++ 代码
```cpp
if (_upperBodyMode) {
    model->GetModelMatrix()->SetWidth(2.0f);
    projection.Scale(1.0f, aspectRatio);
    projection.ScaleRelative(1.6f, 1.6f);
    projection.TranslateRelative(0.0f, -0.9f);
}
```
`TranslateRelative` 的 Y 值必须使用裁剪空间坐标（-1.0 ~ 1.0），不能依赖 `canvasWidth`。

---

## 问题五：模型自动播放待机动作

### 根因
`LAppModel::Update()` 在 motion finished 后会自动调用 `StartRandomMotion(MotionGroupIdle, PriorityIdle)`。

### 修复
将 idle motion 启动注释掉，但保留 `else` 分支结构和 `_motionUpdated = false`：
```cpp
if (!_motionManager->IsFinished()) {
    _motionUpdated = _motionManager->UpdateMotion(_model, deltaTimeSeconds);
} else {
    _motionUpdated = false;
    // StartRandomMotion(MotionGroupIdle, PriorityIdle);  // 已禁用
}
```
**注意**：不能直接删除整个 `else` 分支，否则 `_model->SaveParameters()` 等后续代码的执行路径会改变。

---

## 问题六：头部/表情动作僵硬

### 根因
1. **参数残留**：切换表情/动作时旧参数未被重置，导致混合效果异常
2. **SaveParameters 位置错误**：在 `SetParameterValue()` 中调用 `SaveParameters()` 会破坏 `Update()` 中的参数基线

### 修复
1. `applyExpressionPreset()` 和 `applyGesturePreset()` 开头重置所有可能被修改的参数：
   ```kotlin
   setParameter(Live2DParams.ANGLE_X, 0f)
   setParameter(Live2DParams.ANGLE_Y, 0f)
   setParameter(Live2DParams.ANGLE_Z, 0f)
   setParameter(Live2DParams.BODY_ANGLE_X, 0f)
   // ... 其他参数
   ```

2. `SetParameterValue()` 中保留 `_model->SaveParameters()`，不要删除。参数"僵硬"的问题应通过重置逻辑解决，而不是删除 SaveParameters。

---

## 问题七：口型不同步或跳动

### 排查
1. 检查 `AvatarPlaybackManager` 中是否同时存在 `onPhoneme` 和 `onPhonemeEvents` 回调同时更新状态（会导致双重竞争）
2. 正确做法：`onPhonemeEvents` 驱动 `LipSyncAnimator`，`onPhoneme` 不直接更新状态
3. 检查 `LipSyncAnimator` 的 `job` 是否正确取消和重建

---

## 日志关键词速查

| 日志标签 | 用途 |
|---------|------|
| `Live2DRendererImpl` | Kotlin 层模型加载、参数设置 |
| `JniBridgeJava` / `JniBridgeC` | JNI 调用日志 |
| `[APP]load model` | C++ 层模型加载 |
| `[APP]upperBodyMode` | 上半身模式投影矩阵 |
| `[APP]start motion` | 动作播放 |
| `[APP]expression` | 表情切换 |

## 编译和验证流程

修改 C++ 代码后必须：
1. `./gradlew :app:buildCMakeDebug` — 编译 C++
2. `./gradlew :app:assembleDebug` — 打包 APK
3. 安装并运行，观察 logcat

如果怀疑缓存问题：
```bash
./gradlew clean
./gradlew :app:buildCMakeDebug
```
