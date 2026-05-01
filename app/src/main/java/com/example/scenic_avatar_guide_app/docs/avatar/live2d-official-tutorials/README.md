# Live2D 官方教程文档（离线归档）

本目录收录了 Live2D Cubism SDK 官方教程的中文翻译/提取版本，用于项目开发参考。

---

## 文档列表

| 文件名 | 内容 | 适用平台 |
|--------|------|----------|
| [android-sample-run.md](android-sample-run.md) | Android 示例运行步骤 | Java / Android |
| [add-model-java.md](add-model-java.md) | 向示例项目添加模型（Java） | Java / Android |
| [multi-motion-management-java.md](multi-motion-management-java.md) | 部位差异化动画管理（Java） | Java / Android |
| [sample-build-opengl.md](sample-build-opengl.md) | OpenGL 示例构建教程 | Native / C++ |
| [add-model-native.md](add-model-native.md) | 向示例项目添加模型（Native） | Native / C++ |
| [native-lipsync-from-wav.md](native-lipsync-from-wav.md) | 基于 WAV 音量的唇形同步 | Native / C++ |
| [multi-motion-management-native.md](multi-motion-management-native.md) | 部位差异化动画管理（Native） | Native / C++ |

---

## 与本项目相关的重点文档

### 口型同步（Lip Sync）
- [native-lipsync-from-wav.md](native-lipsync-from-wav.md) — 官方基于 WAV 音量的唇形同步实现方案
- 可参考其 `LAppWavFileHandler` 和 `Update()` 中的参数设置逻辑

### Android 集成
- [android-sample-run.md](android-sample-run.md) — 官方 Android 示例运行环境配置
- [add-model-java.md](add-model-java.md) — Java 端模型添加和枚举配置方式

### 多部位动画
- [multi-motion-management-java.md](multi-motion-management-java.md) / [multi-motion-management-native.md](multi-motion-management-native.md) — 多运动管理器并行播放不同部位动画的实现

---

## 官方资源链接

- **SDK 手册**: https://docs.live2d.com/cubism-sdk-manual/top/
- **教程总览**: https://docs.live2d.com/cubism-sdk-tutorials/top/
- **GitHub 组织**: https://github.com/Live2D
- **社区论坛**: https://community.live2d.com/
- **Cubism 规范**: https://github.com/Live2D/CubismSpecs
