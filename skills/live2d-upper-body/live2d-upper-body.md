---
name: live2d-upper-body
description: 调整 Live2D 数字人显示范围为上身的技能，包含投影矩阵修复和 Compose 层配置
---

# Live2D 数字人上半身显示调整

当数字人显示范围不正确（例如从头到膝盖，而非从头到腰部）时，使用本技能修复。

## 问题现象

- 启用 `showUpperBodyOnly = true` 后，数字人仍然显示下半身的一部分（如膝盖）。
- 原因是 C++ 渲染层的投影矩阵平移量计算错误。

## 涉及文件

1. `app/src/main/cpp/LAppLive2DManager.cpp` — 核心修复位置（`OnUpdate` 方法中的 `_upperBodyMode` 分支）
2. `app/src/main/java/com/example/scenic_avatar_guide_app/ui/components/AvatarView.kt` — Compose 层，`showUpperBodyOnly` 参数传递给 `Live2DRendererImpl`
3. `app/src/main/java/com/example/scenic_avatar_guide_app/ui/screens/MainScreen.kt` — 调用方，`AvatarSection(showUpperBodyOnly = true)`

## 修复步骤

### 1. 检查 Compose 层是否已启用上半身模式

确认 `MainScreen.kt` 中传入了 `showUpperBodyOnly = true`：

```kotlin
AvatarSection(
    avatarState = avatarState,
    fullState = avatarFullState,
    showUpperBodyOnly = true,  // 确保为 true
    modifier = Modifier.fillMaxWidth().weight(2f)
)
```

### 2. 修复 C++ 投影矩阵（关键）

打开 `app/src/main/cpp/LAppLive2DManager.cpp`，定位到 `OnUpdate` 中的 `_upperBodyMode` 分支。

**错误的写法（旧代码）**：
- 使用硬编码的 `virtualAspectRatio = 0.6f`
- 平移量依赖 `canvasWidth`：`projection.TranslateRelative(0.0f, -canvasWidth / 2.0f)`
- 这会导致平移量随模型 canvas 尺寸变化，无法稳定将腰部对齐到底部

**正确的写法**：

```cpp
if (_upperBodyMode)
{
    // 上半身模式：宽度适配 + 正常 aspect ratio + 放大 + 下移
    model->GetModelMatrix()->SetWidth(2.0f);
    projection.Scale(1.0f, aspectRatio);

    // 整体放大 1.6 倍，使上半身占据更多画面
    projection.ScaleRelative(1.6f, 1.6f);

    // 向下平移，使画布中心（腰部附近）靠近视口底部
    // 裁剪空间 Y 范围 [-1, 1]，底部为 -1
    projection.TranslateRelative(0.0f, -0.9f);

    if (DebugLogEnable)
    {
        LAppPal::PrintLogLn("[APP]upperBodyMode: aspectRatio=%.2f", aspectRatio);
    }
}
```

**核心原则**：
- 使用真实的 `aspectRatio`（`width / height`）保持模型不变形
- `TranslateRelative` 的平移量应基于裁剪空间坐标（`-1.0 ~ 1.0`），而不是模型的 `canvasWidth`
- 放大倍数（如 `1.6f`）和平移量（如 `-0.9f`）可根据具体模型微调

### 3. 重新编译

修改 C++ 后必须重新构建以生成新的 `libDemo.so`：

```bash
./gradlew assembleDebug
```

Windows 环境下使用：

```bash
gradlew.bat assembleDebug
```

## 验证方法

1. 构建并安装 APK
2. 进入主界面，观察数字人显示范围
3. 预期效果：仅显示头部到腰部，腰部以下被裁出画面

## 常见陷阱

- **平移量依赖 canvasWidth**：`SetWidth(2.0f)` 后，模型画布坐标到裁剪空间的映射已经确定，平移量不应再依赖原始 `canvasWidth`。错误的公式会导致不同模型的显示效果不一致。
- **忽略 aspectRatio**：使用硬编码的虚拟比例会导致模型在竖屏/横屏设备上拉伸变形。
- **忘记重新编译**：C++ 源码修改后，必须重新运行 Gradle 构建才能生效。`./gradlew clean` 后再构建可确保旧 so 文件被替换。

## 参数微调指南

如果显示范围仍不理想，可调整以下参数：

| 参数 | 作用 | 增大效果 | 减小效果 |
|------|------|---------|---------|
| `ScaleRelative(x, x)` | 整体放大倍数 | 显示内容更少，模型更大 | 显示内容更多，模型更小 |
| `TranslateRelative(0.0f, y)` | 垂直平移 | 更负：模型上移，显示更靠上的部位 | 较不负：模型下移，显示更靠下的部位 |

**建议**：先调整 `TranslateRelative` 的 Y 值使腰部对齐画面底部，再调整 `ScaleRelative` 使头部大小合适。
