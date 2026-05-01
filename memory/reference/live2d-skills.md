---
name: live2d-skills-index
description: 项目中 Live2D 相关 Claude Code skill 文件的索引，供 agent 快速定位参考
type: reference
---

本项目在 `.claude/skills/` 下维护了 6 个 Live2D 数字人相关的 skill 文件，agent 处理 Live2D 相关问题时应优先读取：

| Skill 目录 | 内容 |
|-----------|------|
| `.claude/skills/live2d-parameter-reference/` | Live2D 参数系统完整参考（口型、眼睛、眉毛、头部、身体参数 ID、取值范围、设置流程） |
| `.claude/skills/live2d-debugging/` | 常见问题排查指南（白屏、SIGSEGV、全身显示、自动待机、僵硬、口型不同步） |
| `.claude/skills/live2d-lipsync-system/` | 口型同步系统说明（Viseme 映射、协同发音算法、TTS 集成） |
| `.claude/skills/live2d-jni-bridge/` | JNI 桥接层说明（Kotlin-Java-C++ 调用链、生命周期、线程模型） |
| `.claude/skills/live2d-expression-gesture/` | 表情动作系统（状态定义、参数映射表、播放队列、降级映射） |
| `.claude/skills/live2d-upper-body/` | 上半身显示范围调整（投影矩阵修复、Compose 层配置） |

**Why:** 这些 skill 总结了项目内 Live2D 集成的关键知识，避免 agent 每次都需要从头阅读源码理解参数系统和 JNI 调用链。

**How to apply:** 当用户询问 Live2D 相关问题（模型不显示、参数不生效、口型同步、动作僵硬、上半身裁剪等）时，先读取对应 skill 获取准确的参数值和修复步骤，再结合当前代码状态给出建议。
