---
name: live2d-parameter-reference
description: Live2D Cubism SDK 参数系统完整参考，用于调试和修改数字人模型参数
---

# Live2D 参数系统参考

本项目使用 Live2D Cubism SDK for Native (C++) 通过 JNI 桥接到 Android Kotlin 层。所有参数操作最终通过 `JniBridgeJava.nativeSetParameter(paramId, value, weight)` 传递到 C++ 层的 `LAppModel::SetParameterValue()`。

## 可用参数常量

定义在 `app/src/main/java/.../core/avatar/Live2DParams.kt`：

| 常量名 | 参数 ID | 取值范围 | 说明 |
|--------|---------|---------|------|
| `MOUTH_OPEN_Y` | `ParamMouthOpenY` | 0.0 ~ 1.0 | 嘴巴垂直开合度 |
| `MOUTH_FORM` | `ParamMouthForm` | -1.0 ~ 1.0 | 嘴型：负值=扁嘴(i)，0=中性，正值=圆嘴(o/u) |

## 嘴部视觉大小调整

当前模型（Hiyori）没有独立的「嘴整体大小」参数。为在视觉上放大嘴部，项目使用 `mouthWidthScale` 对 `ParamMouthForm` 进行偏移叠加：

```kotlin
// Live2DRendererImpl.kt
var mouthWidthScale: Float = 1.0f  // 默认值 1.0，嘴部更宽更大
val scaledMouthForm = (mouthForm + mouthWidthScale).coerceIn(-1f, 1.5f)
setParameter(Live2DParams.MOUTH_FORM, scaledMouthForm)
```

| 配置值 | 效果 |
|--------|------|
| `0.0f` | 保持模型原始嘴形，不额外放大 |
| `0.25f` | 轻微加宽 |
| `1.0f` | **项目当前默认值**，嘴显著更宽更大，视觉占比明显提升 |

> 注意：`ParamMouthForm` 的有效范围约为 `-1.0 ~ 1.5`。超过上限后变形增益会饱和。如需进一步放大，应通过 Cubism Editor 修改模型 ArtMesh 本身。 |
| `EYE_L_OPEN` | `ParamEyeLOpen` | 0.0 ~ 1.0+ | 左眼开合，>1 可瞪大 |
| `EYE_R_OPEN` | `ParamEyeROpen` | 0.0 ~ 1.0+ | 右眼开合 |
| `EYE_BALL_X` | `ParamEyeBallX` | -1.0 ~ 1.0 | 眼球水平移动，负=左，正=右 |
| `EYE_BALL_Y` | `ParamEyeBallY` | -1.0 ~ 1.0 | 眼球垂直移动 |
| `BROW_L_Y` | `ParamBrowLY` | -1.0 ~ 1.0 | 左眉垂直位置 |
| `BROW_R_Y` | `ParamBrowRY` | -1.0 ~ 1.0 | 右眉垂直位置 |
| `BROW_L_ANGLE` | `ParamBrowLAngle` | -1.0 ~ 1.0 | 左眉倾斜角度 |
| `BROW_R_ANGLE` | `ParamBrowRAngle` | -1.0 ~ 1.0 | 右眉倾斜角度 |
| `ANGLE_X` | `ParamAngleX` | -30.0 ~ 30.0 | 头部左右转动（俯仰）|
| `ANGLE_Y` | `ParamAngleY` | -30.0 ~ 30.0 | 头部上下转动（点头）|
| `ANGLE_Z` | `ParamAngleZ` | -30.0 ~ 30.0 | 头部左右倾斜（摇头）|
| `BODY_ANGLE_X` | `ParamBodyAngleX` | -10.0 ~ 10.0 | 身体左右转动 |
| `BODY_ANGLE_Y` | `ParamBodyAngleY` | -10.0 ~ 10.0 | 身体前后转动 |
| `BREATH` | `ParamBreath` | 0.0 ~ 1.0 | 呼吸参数，由 BreathUpdater 自动驱动 |

## 参数设置流程

```
Kotlin: Live2DRendererImpl.setParameter()
  → JniBridgeJava.nativeSetParameter(paramId, value, weight)
    → C++: JniBridgeC::nativeSetParameter()
      → LAppLive2DManager::SetParameter()
        → LAppModel::SetParameterValue()
          → CubismModel::SetParameterValue(id, value, weight)
          → CubismModel::SaveParameters()
```

## 关键注意事项

1. **SaveParameters 必须调用**：`SetParameterValue()` 内部会调用 `_model->SaveParameters()`，这是必须的。如果省略此调用，参数不会在下一帧被 `LoadParameters()` 恢复，导致参数丢失或闪烁。

2. **C++ 层初始化保护**：`nativeSetParameter` 在 C++ 层有 `CubismFramework::IsInitialized()` 保护。如果 GL 线程尚未初始化就调用参数设置，会被静默丢弃而不会崩溃。

3. **Kotlin 层 null 保护**：`Live2DRendererImpl.runOnRenderThread()` 会在 `surfaceView` 或 `isRendererSet` 为 null/false 时直接返回，不执行 block。

4. **参数值不受约束**：传入的值不会被 SDK 自动 clamp 到有效范围。超出范围的值可能导致模型变形异常。应在 Kotlin 层使用 `.coerceIn()` 进行限制。

## 参数修改对模型状态的影响

`LAppModel::Update()` 每帧执行：
1. `_model->LoadParameters()` — 加载上一帧保存的参数基线
2. `_motionManager->UpdateMotion()` — 如果 motion 正在播放，覆盖相关参数
3. `_model->SaveParameters()` — 保存当前状态作为下一帧基线
4. `_updateScheduler.OnLateUpdate()` — 执行表情、物理、呼吸、眨眼等 updater
5. `_model->Update()` — 最终模型更新

**这意味着**：
- 通过 `setParameter` 手动设置的参数会被 motion 覆盖（如果 motion 控制同一参数）
- 表情（Expression）在 `OnLateUpdate` 中应用，会覆盖手动设置的眉毛/眼睛参数
- 物理（Physics）在 `OnLateUpdate` 中应用，会覆盖手动设置的身体/头发参数

## 表情预设参数映射

`Live2DRendererImpl.applyExpressionPreset()` 使用以下参数组合：

| 表情 | 主要参数变化 |
|------|-------------|
| NEUTRAL | 全部重置为默认值 |
| HAPPY | BROW_Y +0.25, BROW_ANGLE ±0.2, EYE_OPEN 0.85 |
| THINKING | BROW_L_ANGLE -0.35, EYE_BALL_X -0.25 |
| SURPRISED | BROW_Y +0.45, EYE_OPEN 1.2 |
| EXCITED | BROW_Y +0.35, EYE_OPEN 1.05, ANGLE_Z -6 |
| CONCERNED | BROW_Y -0.15, BROW_ANGLE ±0.35, EYE_OPEN 0.8 |
| APologetic | BROW_Y -0.1, BROW_ANGLE ±1.0, EYE_OPEN 0.75, EYE_BALL_Y -0.3, ANGLE_Y -10, BODY_ANGLE_X +2, MOUTH_FORM -0.45 |
| WELCOMING | BROW_Y +0.2, ANGLE_Z -4, BODY_ANGLE_X +4 |

## 动作预设参数映射

`Live2DRendererImpl.applyGesturePreset()` 使用以下参数：

| 动作 | ANGLE_X | ANGLE_Y | ANGLE_Z | BODY_ANGLE_X |
|------|---------|---------|---------|--------------|
| NOD | 0 | -18 | 0 | 0 |
| SHAKE | 18 | 0 | 0 | 0 |
| WAVE | 0 | 0 | -14 | 10 |
| POINT_LEFT | -20 | 0 | 0 | -12 |
| POINT_RIGHT | 20 | 0 | 0 | 12 |
| POINT_FORWARD | 0 | 0 | -8 | 0 |
| BOW | 0 | -26 | 0 | 0 |
| THINKING_POSE | -10 | 10 | 0 | 0 |
| GUIDE | 12 | 0 | 0 | 8 |

**注意**：表情和动作预设都会先重置 ANGLE_X/Y/Z 和 BODY_ANGLE_X 为 0，然后应用各自的偏移。这防止了旧参数残留导致的"僵硬"问题。
