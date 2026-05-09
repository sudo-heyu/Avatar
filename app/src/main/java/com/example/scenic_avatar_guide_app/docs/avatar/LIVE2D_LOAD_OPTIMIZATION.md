# Live2D 模型加载加速改造说明

## 背景

用户进入应用后，数字人模型需要等待较长时间才能显示。本文档记录问题根因、安全评估过程和实施方案。

---

## 一、加载链路全貌

```
Application.onCreate()
  └─ initSparkChain()

MainActivity.onCreate()
  └─ JniBridgeJava.SetActivityInstance(this)
  └─ JniBridgeJava.SetContext(this)      ← GL 操作所需 Activity context

setContent { MainScreen → AvatarView }
  │
  ├─ remember { Live2DGLSurfaceView(...).apply { initialize() } }
  │    └─ setEGLContextClientVersion(2)
  │    └─ setRenderer(Live2DInternalRenderer())
  │    └─ renderMode = RENDERMODE_CONTINUOUSLY
  │    （GLSurfaceView 对象已创建，但尚未加入视图树）
  │
  └─ LaunchedEffect(enableLive2D) {          ← Dispatchers.Main（协程默认）
       withContext(Dispatchers.IO) {
         extractModelAssets()               ← 首次安装 1-3s；后续瞬间（versionCode 缓存）        
     +
         
         
         
         
         
         +LoadFile(modelPath)                ← 仅校验文件可读性，字节用完即丢
       }
       _isModelLoaded = true
       isLive2DReady = true                ← 约 100-200ms（后续启动）
     }

isLive2DReady = true 后：
  └─ AndroidView(factory = { live2DView }) ← GLSurfaceView 加入 Compose 树
       └─ GL 线程启动
            └─ onSurfaceCreated → nativeOnSurfaceCreated()  ← 真正的模型加载
                 │   CubismFramework 初始化
                 │   Native 侧回调 LoadFile() 读模型/纹理文件
                 │   纹理上传 GPU
                 │   耗时 0.5-2s（与设备性能相关）
                 └─ isSurfaceCreated = true                 ← 模型此时真正可渲染
```

---

## 二、问题根因

### 后续启动（用户反映最多的场景）

```
改造前 showLive2D 门控条件：
  enableLive2D && isLive2DReady && live2dError == null && lottieFinished
                                                          ↑
                                             Lottie 动画必须播完才触发
```

- `isLive2DReady` 在约 150ms 即为 true（仅文件校验）
- `nativeOnSurfaceCreated()` 在 Lottie 播放期间并行运行，通常 1s 内完成
- **Lottie 动画本身耗时 2-4 秒（1 次完整播放）**
- 结果：GL 模型早已就绪，用户还在等 Lottie 动画放完

### 首次安装

- `extractModelAssets()` 需 1-3s 解压模型到内部存储
- 此操作在 `LaunchedEffect` 里触发，属于"UI 显示后才开始"
- 可与 Application/Activity 初始化并行化

---

## 三、对照官方文档的安全约束

基于 `CubismNativeSamples` 官方 `MainActivity.java` + `GLRenderer.java`：

| 约束 | 说明 | 项目现状 |
|------|------|---------|
| `nativeOnSurfaceCreated()` 必须在 GL 线程 | EGL Context 仅在 GL 线程有效 | `Live2DInternalRenderer.onSurfaceCreated()` ✓ |
| `nativeSetParameter()` 等必须在 GL 线程 | 否则 SIGSEGV | `queueEvent` 投递 ✓ |
| `SetActivityInstance` / `SetContext` 在 Activity.onCreate() | native 侧需要 Activity/Context | `MainActivity.onCreate()` ✓ |
| `nativeOnDestroy()` 在 GL 线程完成后 | 否则 use-after-free | CountDownLatch 同步 ✓ |
| `GLSurfaceView` 需要 Activity/Window context | Application context 缺少 Window Token | `LocalContext`（Activity）✓ |

**绝对不能做**：
- 在 GL 线程以外调用任何 `native*` 方法
- 在 `Application.onCreate()` 创建 `GLSurfaceView`（会 BadWindowTokenException）
- 提前调用 `SetActivityInstance`（Activity 尚未创建）

---

## 四、已确认的误判（改造前须知）

### 误判 1：extractModelAssets() 没有缓存

实际已有 versionCode 缓存（`JniBridgeJava.java:145-151`），后续启动瞬间返回。
**无需修改此逻辑。**

### 误判 2：loadModel() 里的 LoadFile() 是真正的模型加载

`loadModel()` 里的 `JniBridgeJava.LoadFile()` 只是**文件可读性校验**，
真正的模型加载在 `nativeOnSurfaceCreated()`（GL 线程）中由 Native 侧触发。

### 误判 3：直接删除 lottieFinished 就能解决问题

若仅删除 `lottieFinished`，`showLive2D` 会在 `isLive2DReady=true`（约 150ms）时触发，
此时 `nativeOnSurfaceCreated()` 尚未运行，用户看到的是黑色/空白 GL 画面。
**必须以 `isSurfaceCreated`（GL 真正就绪）替换 `lottieFinished`。**

---

## 五、改造方案

### 方案一：引入 `isGLReady` + GLSurfaceView 提前入树（以内存换时间）

**文件**：`ui/components/AvatarView.kt`

**原理**：
- `Live2DGLSurfaceView.isSurfaceCreated` 是 `@Volatile` 字段，`nativeOnSurfaceCreated()` 返回后由 GL 线程置 true
- 在协程（主线程）中以 `delay(32)` 轮询，安全读取；轮询设置 Compose state，无跨线程写问题
- 关键：`AndroidView` 不再等 `isLive2DReady`（Kotlin 文件校验），GLSurfaceView 在 t≈50ms 直接加入 Compose 树
  - `nativeOnSurfaceCreated()` 与 Kotlin 文件校验并行运行（节省约 100-200ms）
  - `renderer` 就绪后（t≈150ms）由独立 `LaunchedEffect(renderer)` 调用 `attachSurfaceView`，无时序问题
- `lottieFinished` 不再参与 `showLive2D` 门控

**showLive2D 新条件**：
```
enableLive2D && isGLReady && live2dError == null
```

**效果**：后续启动总节省约 1.2-3 秒（GL 提前入树 ~150ms + 不等 Lottie 播完 ~1-3s）。

**以存储换时间的说明**：
GLSurfaceView 在 t≈50ms 即进入 Compose 树并占用 GPU 内存（纹理已上传），即使用户尚未看到数字人区域。
这是一种"提前占用 GPU 显存资源，换取用户等待时间"的典型 trade-off。

### 方案二：Application 级 extractModelAssets 预热

**文件**：`GuideApplication.kt`

**原理**：
- `extractModelAssets()` 的所有 API（`getFilesDir`、`getAssets` 等）均支持 `applicationContext`
- `extractModelAssets()` 是 `synchronized` 方法，与后续 `loadModel()` 串行，幂等安全
- 在 `Application.onCreate()` 中用 `applicationContext` 调 `SetContext()`，仅供文件 I/O
- `MainActivity.onCreate()` 仍覆盖为 Activity context，供 GL 操作使用

**效果**：首次安装的 1-3 秒解压与 Application 初始化并行，消除感知延迟。

---

## 六、改造后的时间线对比

### 后续启动

```
改造前：
t=0      App 启动
t=150ms  isLive2DReady=true → AndroidView 加入树 → GL 线程启动
t=800ms  nativeOnSurfaceCreated() 完成（模型已就绪）
t=3000ms Lottie 播完 → showLive2D=true → 用户看到数字人   ← 等待约 3s

改造后：
t=0      App 启动
t=150ms  isLive2DReady=true → AndroidView 加入树 → GL 线程启动
t=800ms  nativeOnSurfaceCreated() 完成 → isSurfaceCreated=true
t=832ms  轮询发现 isSurfaceCreated=true → isGLReady=true → showLive2D=true
t=1432ms Lottie fadeOut 600ms 完成 → 数字人完全可见          ← 约 1.4s
```

### 首次安装

```
改造前：
t=0      Application.onCreate()
t=200ms  MainActivity.onCreate()，setContent
t=250ms  LaunchedEffect 启动 → extractModelAssets() 开始
t=2000ms extractModelAssets() 完成
t=...    后续加载

改造后：
t=0      Application.onCreate() → extractModelAssets() 后台启动
t=200ms  MainActivity.onCreate()，setContent
t=250ms  LaunchedEffect 里 extractModelAssets() synchronized 等待
t=2000ms Application 后台提前完成 → LaunchedEffect 里瞬间返回
```

---

## 七、不改动项（当前已正确）

以下均已正确实现，不触碰：

- `nativeOnDrawFrame()` 通过 `queueEvent` 在 GL 线程执行
- `release()` 中 CountDownLatch 确保 GL 线程同步完成 `nativeOnDestroy()`
- `extractModelAssets()` versionCode 缓存（后续启动瞬间返回）
- NaN/Infinity 参数守卫（防止物理引擎崩溃）
- `preserveEGLContextOnPause = true`（后台返回不重新加载纹理）
- `isReleased` 双重检查守卫（防止 use-after-free）
