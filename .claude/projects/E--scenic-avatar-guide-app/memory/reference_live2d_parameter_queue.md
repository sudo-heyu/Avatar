---
name: Live2D Parameter Queue Pattern
description: Live2D 参数队列模式 — 解决 JNI 参数设置与 Breath/Physics 叠加冲突导致的参数漂移问题
type: reference
---

# Live2D Parameter Queue Pattern

## 问题症状

- 口型/动作测试时头部僵硬、持续偏移
- Idle 时自然，一旦播放动画就异常
- 参数逐帧漂移，失去正弦对称性

## 根本原因

Live2D SDK 参数更新流程：

```
① LoadParameters()   ← 恢复保存的"基础状态"
② UpdateMotion()     ← 应用动作
③ SaveParameters()   ← 保存基础状态
④ OnLateUpdate()     ← Breath/Physics 叠加（临时，不保存）
⑤ Render()
```

**问题**：Java 层通过 JNI 设置参数时调用 `SaveParameters()`，会把 Breath 叠加后的参数错误保存为基础状态。

下一帧 `LoadParameters()` 恢复到被污染的值，导致参数持续漂移。

## 解决方案

使用参数队列延迟执行：

1. `SetParameterValue()` 将参数加入队列，不直接设置
2. `Update()` 中在 `SaveParameters()` 之前执行队列
3. 参数被正确保存，Breath 叠加不会被保存

## 关键代码位置

- `LAppModel.hpp` — `PendingParameterData` 结构和 `_pendingParameters` 队列
- `LAppModel.cpp:SetParameterValue()` — 参数入队
- `LAppModel.cpp:FlushPendingParameters()` — 执行队列
- `LAppModel.cpp:Update()` — 在正确时机调用 Flush

## 注意事项

**Why:** JNI 回调时机不可控（queueEvent 在 onDrawFrame 之后执行），必须用队列同步到正确的渲染时机。

**How to apply:** 遇到 Live2D 参数异常时，检查是否有 JNI 层直接调用 `SaveParameters()`，改用队列模式。
