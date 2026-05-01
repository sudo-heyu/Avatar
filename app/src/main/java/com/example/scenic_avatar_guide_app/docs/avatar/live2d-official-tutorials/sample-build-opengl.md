# OpenGLサンプルのビルド

> **来源**: Live2D Cubism SDK チュートリアル  
> **原文链接**: https://docs.live2d.com/cubism-sdk-tutorials/sample-build-opengl/

---

# OpenGLサンプルのビルド教程

## 概述

本教程说明在Windows环境下，使用Visual Studio编译Live2D Cubism SDK for Native附带OpenGL示例项目的完整步骤。

## 准备工作

**所需工具：**
- Live2D Cubism SDK for Native（从官网下载）
- Visual Studio 2017或更高版本（C++编译环境）
- CMake（需配置PATH环境变量）
- GLFW（OpenGL辅助库）
- GLEW（OpenGL扩展库）

> **注意：** GLFW和GLEW可通过SDK示例包中的setup.bat自动下载，无需手动预装。

## 安装步骤

### 1. 安装Visual Studio
安装IDE开发环境，确保启用C++编译支持。

### 2. 安装CMake
安装后需配置环境变量，确保用户可直接调用cmake命令。

### 3. 下载 GLFW 和 GLEW
执行 `Samples/OpenGL/thirdParty/setup.bat`，下载完成后生成第三方库文件夹。

## CMake配置

### 批处理文件选择
批处理文件位于 `Samples/OpenGL/Demo/proj.win.cmake/scripts` 目录：

| 前缀 | 说明 |
|------|------|
| nmake_ | 命令行编译，生成可执行文件 |
| proj_ | 生成Visual Studio解决方案文件 |

### 配置选项
运行 `proj_msvc2017.bat` 等脚本后，需依次选择：

1. **构建工具**：NMake 或 Project
2. **架构**：`x86 (Win32)` 或 `x64 (Win64)`
3. **运行时库**：
   - MD：多线程DLL版本
   - MT：多线程静态版本
   - 参考Microsoft官方文档选择
4. **项目内容**：
   - **Full Demo**：完整功能示例
   - **Minimum Demo**：最小配置，仅包含基础功能

### 生成的解决方案
按上述选项选择后，生成路径示例：
```
[根目录]/Samples/OpenGL/Demo/proj.win.cmake/build/proj_msvc2017_x64_md/
```

## 编译与运行

### 编译
1. 在生成目录中找到 `Demo.sln`
2. 使用Visual Studio打开
3. 执行生成/编译

### 运行
编译成功后以调试模式运行。成功标志：
- 显示图形窗口
- 命令行输出日志信息

> **注意：** Minimum Demo不显示背景和UI元素。

## 窗口尺寸调整

在 `LAppDelegate.cpp` 的 `LAppDelegate::Initialize()` 函数中修改：

```cpp
_window = glfwCreateWindow(RenderTargetWidth, RenderTargetHeight, "SAMPLE", NULL, NULL);
```

其中 `RenderTargetWidth` 和 `RenderTargetHeight` 的实际值定义在 `LAppDefine.cpp` 中。

## 输出文件位置

**重要：** 可执行文件输出到项目文件夹的**同级目录**，而非 `Release` 子文件夹。

例如：项目目录为 `proj_msvc2017_x64_md` 时，可执行文件直接输出到此目录，不会输出到 `proj_msvc2017_x64_md/Release`。
