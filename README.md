# 景灵智导 (ScenicGuide Avatar)

一款面向景区游客的 Android 数字导览应用，支持文本/语音问答、智能路线推荐和 Live2D 数字人播报讲解。

---

## 功能特性

- **智能问答**：基于大模型的多轮对话，支持流式响应与分段 TTS 播报
- **语音交互**：端侧 ASR 识别 + 语音唤醒，解放双手
- **数字人播报**：Live2D 实时渲染，高精度口型同步（15 种 Viseme），支持表情与动作过渡
- **路线规划**：景区路线智能推荐（规划中）
- **环境切换**：支持动态配置后端地址，便于开发/生产环境切换

---

## 技术栈

| 层级 | 技术 |
|------|------|
| UI | Jetpack Compose |
| 架构 | MVVM + Repository + Hilt 依赖注入 |
| 异步 | Kotlin Coroutines + Flow |
| 网络 | Retrofit + OkHttp |
| 数字人 | Live2D Cubism SDK + 自定义 JNI 桥接 |
| 音频 | ExoPlayer + Edge-TTS |
| 存储 | DataStore |

---

## 项目演示

### 界面截图

<p float="left">
  <img src="res_demo/pic1.jpg" width="48%" />
  <img src="res_demo/pic2.jpg" width="48%" />
</p>

### 功能演示

**演示 1**

<video src="res_demo/video1.mp4" width="48%"></video>

**演示 2**

<video src="res_demo/video2.mp4" width="48%"></video>

---

## 快速开始

```bash
# 构建 Debug APK
./gradlew assembleDebug

# 运行单元测试
./gradlew test

# 连接设备运行仪器测试
./gradlew connectedAndroidTest
```

---

## 后端接口

详见 [`docs/api/API_CONTRACT.md`](app/src/main/java/com/example/scenic_avatar_guide_app/docs/api/API_CONTRACT.md)。

---

> 本项目处于早期开发阶段，当前聚焦第一阶段核心能力：流式问答与数字人播报联调。
