---
name: 嘴部大小配置
description: Live2D 模型嘴部宽度缩放因子设置
type: feedback
---

Live2D 模型的嘴部宽度缩放因子 `mouthWidthScale` 应固定为 **1.0f**。

**Why:** 用户认为默认嘴部太小，经过调整后 1.0f 的值视觉效果最佳。

**How to apply:** 在 `Live2DRendererImpl.kt` 中，`mouthWidthScale` 属性应保持为 `1.0f`，不要随意修改。此值通过增加 `ParamMouthForm` 参数的偏移量来放大嘴的视觉宽度。
