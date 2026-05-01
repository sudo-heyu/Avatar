# Live2D 官方文档速查 Skill

## 描述

当项目中涉及 Live2D Cubism SDK 集成、模型配置、动画管理或唇形同步开发时，提供官方教程的关键知识点和实现要点。

## 触发场景

- 添加/切换 Live2D 模型
- 实现多部位并行动画（如手臂与身体动画分离）
- 配置唇形同步（LipSync）
- 调整模型参数（如 mouthWidthScale、眨眼等）
- 构建 OpenGL/Android 示例环境

## 核心知识点

### 1. 模型添加流程

**Java 端**：
1. 将模型文件夹放入 `src/main/assets`
2. 在 `LAppDefine.java` 的 `ModelDir` 枚举中添加模型，**索引必须连续**
   ```java
   public enum ModelDir {
       HARU(0, "Haru"),
       HIYORI(1, "Hiyori"),
       MIARA(5,"miara"); // 索引必须连续！
   }
   ```

**Native 端**：
1. 使用 Cubism Editor 为模型添加 HitArea（Body/Head）
2. 设置 EyeBlink 和 LipSync 参数组
3. 用 Cubism Viewer (for OW) 导出 `.model3.json`，**文件夹名必须与 json 文件名一致**
4. 放入 `Samples/Resources/` 目录

### 2. model3.json 关键结构

```json
{
    "FileReferences": {
        "Moc": "miara.moc3",
        "Textures": ["miara.4096/texture_00.png"],
        "Physics": "miara.physics3.json",
        "Expressions": [
            {"Name": "happy.exp3.json", "File": "expressions/happy.exp3.json"}
        ],
        "Motions": {
            "Idle": [{"File": "motions/Scene1.motion3.json"}],
            "TapBody": [{"File": "motions/Scene2.motion3.json"}]
        }
    },
    "Groups": [
        {"Target": "Parameter", "Name": "EyeBlink", "Ids": ["ParamEyeLOpen", "ParamEyeROpen"]},
        {"Target": "Parameter", "Name": "LipSync", "Ids": ["ParamMouthOpenY"]}
    ],
    "HitAreas": [
        {"Id": "HitArea", "Name": "Body"},
        {"Id": "HitArea2", "Name": "Head"}
    ]
}
```

### 3. 多部位动画管理（并行播放）

**核心问题**：单个 `CubismMotionManager` 播放新动画时会淡出旧动画，无法并行。

**解决方案**：为不同部位创建独立的 MotionManager。

**Java 实现要点**：
- 在 `LAppModel` 中添加多个 `CubismMotionManager` 字段
- 在 `update()` 中，**所有 `updateMotion()` 调用必须在 `loadParameters()` 和 `saveParameters()` 之间**
- 后执行的 `updateMotion` 会覆盖先执行的同名参数

```java
_model.loadParameters();
// ... 各部位 updateMotion 调用 ...
isMotionUpdated |= rightArmMotionManager.updateMotion(_model, deltaTimeSeconds);
isMotionUpdated |= leftArmMotionManager.updateMotion(_model, deltaTimeSeconds);
_model.saveParameters();
```

**参数冲突预防**：
- 动画制作时严格分离各管理器负责的参数
- 高优先级动画播放完毕后，低优先级待机动画可能覆盖其参数

> **重要**：Java 层通过 JNI 设置参数时，不要直接调用 `SaveParameters()`，否则会污染基础状态。详见 [Live2D Parameter Queue Pattern](live2d-parameter-queue.md)。

### 4. 唇形同步（LipSync）

**官方 Native 方案（基于 WAV 音量）**：
- 在 `.motion3.json` 中用 `"Sound"` 键关联 WAV 文件
- 使用 `LAppWavFileHandler` 处理音频
- 调用 `Start()` 初始化，`Update(deltaTime)` 更新，`GetRms()` 获取 0~1 音量值
- 通过 `_model->AddParameterValue(_lipSyncIds[i], value, 0.8f)` 设置口型

**WAV 格式限制**：
| 项目 | 支持规格 |
|------|----------|
| 格式 | Microsoft WAV（小端序） |
| 编码 | 线性 PCM（不支持 μ-law、ADPCM） |
| 声道 | 单声道/立体声 |
| 位深度 | 8bit、16bit、24bit 有符号整数 |

> 注意：示例程序不包含设备音频播放功能，需自行集成音频播放器。

### 5. 本项目相关文件位置

- 官方教程归档：`app/src/main/java/com/example/scenic_avatar_guide_app/docs/avatar/live2d-official-tutorials/`
- 中文口型同步方案：`app/src/main/java/com/example/scenic_avatar_guide_app/docs/avatar/CHINESE_LIP_SYNC.md`
- 口型优化记录：`app/src/main/java/com/example/scenic_avatar_guide_app/docs/avatar/LIP_SYNC_OPTIMIZATION.md`

### 6. 官方资源链接

- SDK 手册：https://docs.live2d.com/cubism-sdk-manual/top/
- 教程总览：https://docs.live2d.com/cubism-sdk-tutorials/top/
- GitHub 组织：https://github.com/Live2D
- Cubism 规范：https://github.com/Live2D/CubismSpecs
