# CubismNativeSamples 官方源码参考 Skill

## 描述

当需要查阅 Live2D Cubism SDK for Native (C++) 的官方实现时，提供本地缓存的官方源码。

## 触发场景

- 遇到 Android OpenGL 渲染问题（如后台恢复崩溃、GL 上下文丢失）
- 需要对比官方实现与项目代码的差异
- 查阅官方 CHANGELOG 了解 bug 修复记录
- 参考 Android 平台的生命周期处理

## 官方仓库信息

- **仓库地址**：https://github.com/Live2D/CubismNativeSamples
- **默认分支**：`develop`
- **最新版本**：5-r.5 (2026-04-02)
- **SDK 手册**：https://docs.live2d.com/cubism-sdk-manual/top/

## 本地源码目录结构

```
skills/cubism-native-samples/
├── cubism-native-samples.md    # 本文件
├── CHANGELOG.md                # 变更日志
├── README.md                   # 英文说明
├── README.ja.md                # 日文说明
├── LICENSE.md                  # 许可证
├── NOTICE.md                   # 声明
└── src/
    ├── cpp/                    # Native 层源码
    │   ├── LAppDelegate.cpp/hpp
    │   ├── LAppLive2DManager.cpp/hpp
    │   ├── LAppModel.cpp/hpp
    │   ├── LAppView.cpp/hpp
    │   ├── JniBridgeC.cpp/hpp
    │   ├── LAppTextureManager.cpp/hpp
    │   ├── LAppPal.cpp/hpp
    │   ├── LAppDefine.cpp/hpp
    │   ├── LAppSprite.cpp/hpp
    │   └── LAppSpriteShader.cpp/hpp
    └── java/                   # Java 层源码
        ├── MainActivity.java
        ├── GLRenderer.java
        └── JniBridgeJava.java
```

## 关键 Bug 修复记录

### 5-r.5 (2026-04-02)
- Fix shader regeneration to be triggered when OnSurfaceCreate is called on Android OpenGL

### 5-r.2 (2024-12-19)
- Fix an issue that could cause drawing errors when the application is restored from the background
- Change to use GLSurfaceView event queues to handle touch events
- Fix an issue in the Android sample where the model display would reset after performing certain operations

### 5-r.5-beta.3 (2026-01-29)
- Fix background image distortion when window size is changed
- Fix OpenGL background rendering issue on iOS when returning to the app from the home screen

## 官方示例的关键实现差异

| 方面 | 官方实现 | 说明 |
|------|---------|------|
| `preserveEGLContextOnPause` | **未设置**（默认 false） | 官方样例偏向标准 GL 生命周期，允许暂停/恢复时重新创建 GL 上下文 |
| `OnSurfaceCreate` 着色器重建 | **无条件执行** | 官方 5-r.5 修复要求 Android OpenGL 的 OnSurfaceCreate 触发 shader regeneration |
| 触摸事件处理 | 使用 `queueEvent` | 通过 GLSurfaceView 事件队列投递到 GL 线程 |

## 本项目的有意偏离：后台返回瞬时恢复

**完成日期**：2026-04-30

本项目的产品目标是“只要应用进程没被清除，返回应用时 Live2D 模型应立即显示”，因此生命周期策略与官方示例有以下有意偏离：

| 方面 | 本项目实现 | 原因 |
|------|-----------|------|
| `preserveEGLContextOnPause` | **设置为 true** | 优先保留 EGL context、纹理和 renderer，避免返回时 `SetupTextures()` 造成几秒等待 |
| Compose dispose | 只 `detachSurfaceView()` | 页面销毁/导航切换不释放 Native 模型，避免重新 `LoadAssets()` |
| `nativeOnStop/nativeOnDestroy` | 普通后台切换不调用 | 这些方法会释放 `LAppLive2DManager` 和 CubismFramework，返回必然重载模型 |
| 首次 `OnSurfaceCreate` shader 重建 | 已有模型时才执行 | 首屏 `LoadAssets -> CreateRenderer` 优先稳定，避免首次创建期间删除 shader 单例导致崩溃 |

注意：如果 Android 系统因内存压力回收 EGL context，仍会触发 `onSurfaceCreated()` 并进入 renderer/texture 重建兜底；如果应用进程被杀，必须完整重新加载模型。

## 本项目对应文件

| 官方文件 | 本项目文件 |
|---------|-----------|
| src/cpp/LAppDelegate.cpp | `app/src/main/cpp/LAppDelegate.cpp` |
| src/cpp/LAppLive2DManager.cpp | `app/src/main/cpp/LAppLive2DManager.cpp` |
| src/cpp/LAppModel.cpp | `app/src/main/cpp/LAppModel.cpp` |
| src/cpp/JniBridgeC.cpp | `app/src/main/cpp/JniBridgeC.cpp` |
| src/java/MainActivity.java | `app/src/main/java/.../Live2DGLSurfaceView.kt` (概念对应) |

## 更新源码

如需更新到最新版本，运行以下命令：

```powershell
$baseCpp = "https://raw.githubusercontent.com/Live2D/CubismNativeSamples/develop/Samples/OpenGL/Demo/proj.android.cmake/Full/app/src/main/cpp"
$destCpp = "E:\scenic_avatar_guide_app\skills\cubism-native-samples\src\cpp"
$files = @("LAppDelegate.cpp", "LAppDelegate.hpp", "LAppLive2DManager.cpp", "LAppLive2DManager.hpp", "LAppModel.cpp", "LAppModel.hpp", "LAppView.cpp", "LAppView.hpp", "JniBridgeC.cpp", "JniBridgeC.hpp", "LAppTextureManager.cpp", "LAppTextureManager.hpp", "LAppPal.cpp", "LAppPal.hpp", "LAppDefine.cpp", "LAppDefine.hpp")
foreach ($f in $files) { Invoke-WebRequest -Uri "$baseCpp/$f" -OutFile (Join-Path $destCpp $f) }
```

## 在线资源

- **GitHub 仓库**：https://github.com/Live2D/CubismNativeSamples
- **Raw 文件 URL 格式**：`https://raw.githubusercontent.com/Live2D/CubismNativeSamples/develop/{文件路径}`
