# Android サンプルの実行手順

> **来源**: Live2D Cubism SDK チュートリアル  
> **最后更新**: 2022年12月8日  
> **原文链接**: https://docs.live2d.com/cubism-sdk-tutorials/android-sample-run/

---

## 概述

本文介绍在 Android Studio 中运行 Live2D Cubism SDK for Java 附带的 Android 项目示例应用程序的步骤。

> 注意：文章内容基于 Cubism 4 SDK for Java R1 beta1。不同版本的 SDK 可能有不同的方法和步骤。

---

## 所需环境

### 必要条件

| 软件 | 说明 |
|------|------|
| **Live2D Cubism SDK for Java** | SDK 本体，从 Live2D 社区专用页面下载 |
| **Android Studio** | 开发环境，请使用 Android Studio Chipmunk 及以上版本 |

---

## 设置步骤

### 1. Android Studio 安装与配置

1. 安装 Android Studio，使用默认设置即可
2. 安装完成后，通过 **"Tools" → SDK Manager** 打开 SDK 管理器

### 2. SDK 组件安装

在 SDK Manager 中进行以下配置：

#### SDK Platforms
- 勾选 **"Android 12.0 (S)"**

#### SDK Tools
- 按照说明安装所需的工具（参见官方截图）

### 3. 硬件加速（可选但推荐）

| 处理器类型 | 需要的加速器 |
|------------|-------------|
| Intel | Intel x86 Emulator Accelerator (HAXM installer) |
| AMD | Android Emulator Hypervisor Driver for AMD Processors |

> 重要提示：如果启用了 Hyper-V，需要启用 **"Windows Hypervisor Platform"**。详细说明请参阅 [Android Studio 用户指南](https://developer.android.com/studio/run/emulator-acceleration)。

---

## 构建项目

1. Android 项目使用 Gradle 作为构建工具
2. 打开示例应用程序后，依次点击 **"Build" → "Rebuild Project"** 执行 Gradle Build

---

## 运行应用

1. 构建成功后即可运行
2. 拥有实体 Android 设备：连接设备后直接运行
3. 没有实体设备：使用模拟器运行

> 注意：在 Minimum Demo 中，不会显示背景切换、模型切换齿轮图标等 UI 元素。

---

## Minimum Demo 运行方法

除了常规示例外，SDK 还提供了只包含最基本功能的 **"Minimum Demo"**。

### 切换步骤

1. 点击左下角的 **"Build Variants"** 按钮
2. 点击 **"Active Build Variants"**，从下拉列表中选择 **"Minimum Debug"**
3. 运行 Minimum Demo

---

## 注意事项

- 最低演示版（Minimum Demo）的 UI 功能有限
- 不同 SDK 版本的具体方法和步骤可能有所不同
- 建议使用较新版本的 Android Studio 以获得最佳兼容性
