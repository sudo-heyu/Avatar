# wavファイルの音量に基づくリップシンク(Native)

> **来源**: Live2D Cubism SDK チュートリアル  
> **最后更新**: 2021年2月17日  
> **原文链接**: https://docs.live2d.com/cubism-sdk-tutorials/native-lipsync-from-wav-native/

---

# 基于WAV文件音量的唇形同步（Native）

## 概述

Cubism SDK for Native示例程序提供了唇形同步功能，可以根据WAV文件的音频数据音量，实时驱动唇形同步动画。此功能从 Cubism 4 SDK for Native R2 开始提供。

---

## 使用方法

### 准备工作

在 `.motion3.json` 文件中为各段动画关联对应的WAV文件，通过 **"Sound"** 键指定WAV文件路径：

```json
{
    "Motions": {
        "Idle": [
            {"File": "motions/haru_g_idle.motion3.json", "FadeInTime": 0.5, "FadeOutTime": 0.5, "Sound": "sounds/haru_normal_05.wav"}
        ],
        "TapBody": [
            {"File": "motions/haru_g_m06.motion3.json", "FadeInTime": 0.5, "FadeOutTime": 0.5, "Sound": "sounds/haru_normal_01.wav"}
        ]
    }
}
```

**注意：** 必须将WAV文件放置在指定路径下，路径错误将导致唇形同步无法执行。

---

### 开始获取音量

通过 `LAppWavFileHandler` 类处理WAV文件信息。调用 `LAppWavFileHandler::Start()` 可读取WAV文件音频数据并初始化唇形同步所需的内部状态。

```cpp
CubismMotionQueueEntryHandle LAppModel::StartMotion(
    const csmChar* group,
    csmInt32 no,
    csmInt32 priority,
    ACubismMotion::FinishedMotionCallback onFinishedMotionHandler)
{
    // 获取关联的音频文件路径
    csmString voice = _modelSetting->GetMotionSoundFileName(group, no);
    if (strcmp(voice.GetRawString(), "") != 0)
    {
        csmString path = voice;
        path = _modelHomeDir + path;
        LAppPal::PrintLog("[APP]start lipsync:%s", path.GetRawString());
        _wavFileHandler.Start(path);  // 启动音频处理
    }
    // ...
}
```

---

### 状态更新与音量获取

调用 `LAppWavFileHandler::Update()` 根据经过的时间获取对应位置的音量值。通过 `LAppWavFileHandler::GetRms()` 获取测量结果，再使用 `CubismModel::AddParameterValue` 将音量作为唇形同步值设置到模型上。

```cpp
void LAppModel::Update()
{
    // 唇形同步设置
    if (_lipSync)
    {
        csmFloat32 value = 0.0f;
        
        // 状态更新并获取RMS值
        _wavFileHandler.Update(deltaTimeSeconds);
        value = _wavFileHandler.GetRms();
        
        // 将音量乘以0.8后设置为唇形同步值
        for (csmUint32 i = 0; i < _lipSyncIds.GetSize(); ++i)
        {
            _model->AddParameterValue(_lipSyncIds[i], value, 0.8f);
        }
    }
}
```

---

## 补充说明

- 示例程序不包含在设备上播放音频的功能。
- `LAppWavFileHandler::GetRms()` 返回0～1范围内的音量值。
- 音量单位为 **RMS（均方根值）**。
- 对于立体声音频，会计算左右声道音频的平均值。

---

## 支持格式限制

示例程序仅支持以下WAV文件格式，不支持的格式将导致唇形同步失效：

| 项目 | 支持规格 |
|------|----------|
| 格式 | Microsoft WAV（小端序格式） |
| 编码 | 线性PCM（不支持μ-law、ADPCM等编码） |
| 声道数 | 单声道/立体声 |
| 位深度 | 8bit、16bit、24bit有符号整数 |
