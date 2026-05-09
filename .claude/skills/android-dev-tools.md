# Android 开发调试工具链 Skill

当用户需要进行 Android 设备调试、日志分析、截图验证或 Gradle 构建分析时，优先使用本项目已配置的三个 MCP server 完成，避免手动执行 adb/gradlew 命令。

## 工具速查

### android-mcp-server
- **何时使用**：需要与 Android 设备交互（截图、点击、查看 UI 结构、安装 APK、读取日志）
- **入口**：`.mcp.json` 中 `android` server
- **关键工具**：`screenshot`、`get_ui_tree`、`tap_element`、`get_logs`、`launch_app`
- **前提**：ADB 已连接设备（`adb devices` 有输出）

### FadCat
- **何时使用**：需要深入分析 logcat、多设备日志对比、进程检查、FadCam 媒体管理
- **入口**：`.mcp.json` 中 `fadcat` server
- **关键工具**：logcat 流式读取、设备发现、进程检查
- **前提**：Python 依赖已安装（`~/fadcat/requirements.txt`）

### Gradle MCP
- **何时使用**：需要执行 Gradle 任务、分析构建失败、审计依赖、探索项目结构、运行测试
- **入口**：`.mcp.json` 中 `gradle` server
- **关键工具**：智能任务执行、依赖搜索、源码索引、Compose UI 预览、Kotlin REPL
- **前提**：jbang 已安装，首次启动会自动下载 JDK 25+（耗时 3-5 分钟）

## 环境路径

- **ANDROID_HOME**：`F:\Software\AndroidStudioSDK`
- **adb**：`F:/Software/AndroidStudioSDK/platform-tools/adb.exe`
- **FadCat 源码**：`C:/Users/xumin/fadcat`
- **jbang**：`C:/Users/xumin/.jbang/bin/jbang`
- **项目根目录**：`E:/scenic_avatar_guide_app`

## 常见场景

| 场景 | 推荐工具 |
|------|---------|
| "帮我截个图看看当前页面" | android-mcp-server `screenshot` |
| "这个按钮点不了怎么回事" | android-mcp-server `get_ui_tree` + `tap_element` |
| "应用崩溃了看看日志" | FadCat logcat 分析 / android-mcp-server `get_logs` |
| "构建失败了分析一下" | Gradle MCP 任务执行与错误诊断 |
| "这个依赖是哪个版本的" | Gradle MCP 依赖审计 |
| "跑一下测试看看结果" | Gradle MCP 测试执行 |

## 注意事项

1. **Gradle MCP 首次启动慢**：jbang 自动下载 JDK 25 和依赖，请等待，不要重复触发。
2. **Git Bash 路径问题**：Windows Git Bash 中运行 `adb shell` 涉及 `/sdcard` 等路径时，需加 `MSYS_NO_PATHCONV=1`。
3. **logcat 无输出**：设备日志缓冲区为空时属正常现象，先操作应用再查看。
