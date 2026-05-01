---
name: live2d-jni-bridge
description: Live2D JNI 桥接层完整说明，覆盖 Kotlin-Java-C++ 调用链、生命周期管理和线程模型
---

# Live2D JNI 桥接层

## 三层架构

```
Kotlin 层
  Live2DRendererImpl ──► JniBridgeJava (Java 类，含 native 方法声明)
                           │
                           ▼
                     JNI 注册 (JNI_OnLoad)
                           │
                           ▼
C++ 层
  JniBridgeC.cpp ──► LAppDelegate ──► LAppLive2DManager ──► LAppModel
```

## Kotlin 层入口

### Live2DRendererImpl
`Live2DRendererImpl` 是 Kotlin 层的主要封装类，持有 `Live2DGLSurfaceView` 的弱引用。

关键方法：
- `initialize()` — 调用 `JniBridgeJava.SetActivityInstance()` 和 `SetContext()`
- `loadModel()` — 验证 assets 文件后调用 `JniBridgeJava.LoadFile()` 做 JNI 验证
- `setParameter()` — 通过 `runOnRenderThread { JniBridgeJava.nativeSetParameter(...) }` 在 GL 线程执行
- `attachSurfaceView()` — 建立与 `Live2DGLSurfaceView` 的连接

### Live2DGLSurfaceView
继承自 `GLSurfaceView`，管理 OpenGL ES 2.0 上下文：

```kotlin
setEGLContextClientVersion(2)
setRenderer(Live2DInternalRenderer())
renderMode = RENDERMODE_CONTINUOUSLY
```

**后台保活策略（2026-04-30 已完成）**：
- 本项目设置 `preserveEGLContextOnPause = true`，优先保留 EGL context、纹理和 renderer，保证正常后台返回时模型瞬时显示。
- `onPause()` 只调用 `super.onPause()` 和 `nativeOnPause()`，不把 `isSurfaceCreated` 主动置 false。
- `AvatarView` dispose 只调用 `Live2DRendererImpl.detachSurfaceView()` 解绑回调，不调用 `release()`，避免触发 `nativeOnStop()` / `nativeOnDestroy()` 释放模型。
- 只有系统回收 EGL context 或进程被杀时才走慢路径：`onSurfaceCreated()` → renderer/texture 重建或完整模型加载。

`runOnRenderThread(block)` 使用 `queueEvent(block)` 将操作投递到 GL 渲染线程。

**关键保护**：如果 `!isRendererSet`，直接返回不执行 block，防止在 renderer 初始化前调用 native 方法。

## Java 层桥梁

### JniBridgeJava.java
包含两类方法：

**Native 方法声明**（在 C++ 层实现）：
```java
public static native void nativeOnStart();
public static native void nativeOnPause();
public static native void nativeOnStop();
public static native void nativeOnDestroy();
public static native void nativeOnSurfaceCreated();
public static native void nativeOnSurfaceChanged(int width, int height);
public static native void nativeOnDrawFrame();
public static native void nativeOnTouchesBegan(float pointX, float pointY);
public static native void nativeOnTouchesEnded(float pointX, float pointY);
public static native void nativeOnTouchesMoved(float pointX, float pointY);
public static native void nativeSetParameter(String parameterId, float value, float weight);
public static native void nativeSetUpperBodyMode(boolean enabled);
```

**Java 辅助方法**（被 C++ 通过 JNI 回调）：
```java
public static void SetContext(Context context)
public static void SetActivityInstance(Activity activity)
public static String[] GetAssetList(String dirPath)      // C++ 读取 assets 目录
public static byte[] LoadFile(String filePath)            // C++ 读取 assets 文件
public static void MoveTaskToBack()                       // C++ 调用返回桌面
```

静态初始化块加载 native 库：
```java
static {
    System.loadLibrary("Demo");
}
```

## C++ 层实现

### JNI 注册（JniBridgeC.cpp）

`JNI_OnLoad()` 中缓存 `JniBridgeJava` 类引用和方法 ID：
```cpp
jclass clazz = env->FindClass("com/example/scenic_avatar_guide_app/core/avatar/JniBridgeJava");
g_JniBridgeJavaClass = reinterpret_cast<jclass>(env->NewGlobalRef(clazz));
g_GetAssetsMethodId = env->GetStaticMethodID(g_JniBridgeJavaClass, "GetAssetList", "(Ljava/lang/String;)[Ljava/lang/String;");
g_LoadFileMethodId = env->GetStaticMethodID(g_JniBridgeJavaClass, "LoadFile", "(Ljava/lang/String;)[B");
g_MoveTaskToBackMethodId = env->GetStaticMethodID(g_JniBridgeJavaClass, "MoveTaskToBack", "()V");
```

**全局引用**：`g_JniBridgeJavaClass` 使用 `NewGlobalRef`，确保在 `JNI_OnUnload` 前一直有效。

### C++ → Java 文件读取

`JniBridgeC::LoadFileAsBytesFromJava()`：
1. 调用 `JniBridgeJava.LoadFile(filePath)`
2. 将返回的 `jbyteArray` 复制到 C++ `char*` buffer
3. 返回 buffer 和 size

这个机制让 C++ 层可以通过 Java 的 `AssetManager` 读取 APK assets 中的模型文件。

### Native 方法映射

| Java Native 方法 | C++ 实现 | 说明 |
|-----------------|---------|------|
| `nativeOnStart` | `LAppDelegate::OnStart()` | 恢复 active 标志 |
| `nativeOnPause` | `LAppDelegate::OnPause()` | 空实现 |
| `nativeOnStop` | `LAppDelegate::OnStop()` | 释放 view、texture、Live2DManager、dispose CubismFramework |
| `nativeOnDestroy` | `LAppDelegate::OnDestroy()` | 释放 LAppDelegate 单例 |
| `nativeOnSurfaceCreated` | `LAppDelegate::OnSurfaceCreate()` | 初始化 OpenGL、纹理管理器、CubismFramework、重新加载模型 renderer |
| `nativeOnSurfaceChanged` | `LAppDelegate::OnSurfaceChanged()` | 设置 viewport、初始化 view 和 sprite |
| `nativeOnDrawFrame` | `LAppDelegate::Run()` | 每帧执行：更新时间、清屏、调用 view->Render() |
| `nativeSetParameter` | `LAppLive2DManager::SetParameter()` | 设置模型参数，有 `CubismFramework::IsInitialized()` 保护 |
| `nativeSetUpperBodyMode` | 设置全局标志 `s_upperBodyModePending` | 在 surface created 时应用 |

### 生命周期调用链

**初始化**：
```
AvatarView LaunchedEffect
  → Live2DRendererImpl.initialize()
    → JniBridgeJava.SetActivityInstance(activity)
    → JniBridgeJava.SetContext(context)

Live2DGLSurfaceView.initialize()
  → setRenderer(Live2DInternalRenderer())
    → GLSurfaceView 创建 GL 线程
      → onSurfaceCreated()
        → JniBridgeJava.nativeOnSurfaceCreated()
          → LAppDelegate::OnSurfaceCreate()
            → gl 初始化、纹理管理器初始化
            → CubismFramework::Initialize()（如果未初始化）
            → LAppLive2DManager::GetInstance()（首次创建时加载模型）
```

**每帧渲染**：
```
GL 线程 onDrawFrame()
  → JniBridgeJava.nativeOnDrawFrame()
    → LAppDelegate::Run()
      → LAppPal::UpdateTime()
      → glClear()
      → LAppView::Render()
        → LAppLive2DManager::OnUpdate()
          → 对每个模型：Update() + Draw()
```

**销毁**：
```
AvatarView DisposableEffect onDispose
  → renderer.detachSurfaceView()
    → 清除 onAfterDrawFrame / onSurfaceCreatedListener
    → 清除 SurfaceView 弱引用
    → 保留 Native Cubism/模型单例
```

`renderer.release()` 仍保留为真正释放路径，但普通页面销毁和后台切换不应调用它；否则会释放 `LAppLive2DManager`，返回时必然重新加载模型。

## 线程模型

| 操作 | 线程 |
|------|------|
| `initialize()`, `loadModel()` | Kotlin 协程（IO 或 Main）|
| `setParameter()`, `setMouth()`, `setExpression()` | 通过 `queueEvent()` 投递到 **GL 线程** |
| `nativeOnDrawFrame()` | **GL 线程** |
| `nativeOnSurfaceCreated/Changed()` | **GL 线程** |
| C++ `LoadFile` → Java `LoadFile` | **GL 线程**（通过 JNI）|

**重要**：所有 OpenGL 和 Cubism SDK 操作必须在 GL 线程执行。Kotlin 层通过 `runOnRenderThread { ... }` 确保这一点。

## 单例管理

C++ 层使用单例模式：
- `LAppDelegate` — 全局委托，管理窗口、view、渲染循环
- `LAppLive2DManager` — 模型管理器，管理模型列表和场景切换
- `CubismOffscreenManager_OpenGLES2` — 离屏渲染管理

**注意**：`LAppLive2DManager::ChangeScene()` 会 `ReleaseAllModel()` 并创建新模型。频繁切换场景会导致模型重新加载。

**当前保活约束**：
- 不要在 `Activity.ON_PAUSE`、Compose dispose、导航离开时调用 `nativeOnStop()`。
- 不要把 `DisposableEffect` 绑定到 `renderer` 状态变化；`renderer` 从 null 初始化为实例时会触发旧 effect 的 dispose，可能导致首屏 Surface 被误 pause。
- `LAppDelegate::OnSurfaceCreate()` 首次模型加载前不要提前删除 shader 单例；已有模型恢复时再执行 shader/renderer 重建。

## 常见问题

1. **JNI 方法签名错误**：修改 `JniBridgeJava` 的 native 方法签名后，必须同步修改 `JniBridgeC.cpp` 中的 `JNIEXPORT` 函数签名，否则运行时找不到方法。

2. **类加载器问题**：`JNI_OnLoad` 中使用 `FindClass` 获取的局部引用必须通过 `NewGlobalRef` 提升为全局引用，否则方法 ID 在后续调用中会失效。

3. **线程安全**：`nativeSetParameter` 等 native 方法由 Kotlin 的 GL 线程调用，但 C++ 层没有额外同步。如果 Kotlin 层在非 GL 线程直接调用 native 方法（不经过 `queueEvent`），会导致线程安全问题。
