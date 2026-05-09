# 开发调试 MCP 工具链

本文档记录景灵智导项目配置的 AI 辅助开发调试工具（MCP Servers）。

## 配置位置

所有 MCP server 定义存储在项目根目录 `.mcp.json` 中，随仓库版本控制，团队成员共享。

```json
{
  "mcpServers": {
    "android": { ... },
    "fadcat": { ... },
    "gradle": { ... }
  }
}
```

---

## android-mcp-server

**仓库**：https://github.com/martingeidobler/android-mcp-server

通过 ADB 控制 Android 设备和模拟器，提供 25 个工具：

| 类别 | 代表工具 |
|------|---------|
| 设备管理 | `list_devices`、`start_emulator` |
| 截图与 UI | `screenshot`、`get_ui_tree` |
| 交互操作 | `tap`、`swipe`、`type_text`、`press_key` |
| 诊断 | `get_logs`、`clear_logs`、`get_device_info` |
| 应用管理 | `launch_app`、`install_apk` |

**运行方式**：`npx -y android-mcp-server`

**环境要求**：
- `ANDROID_HOME` 指向 Android SDK 根目录（本项目配置为 `F:\Software\AndroidStudioSDK`）
- ADB 已连接设备或模拟器

---

## FadCat

**仓库**：https://github.com/anonfaded/fadcat

Android logcat 增强工具，支持 GUI / CLI / MCP 三种模式。

**本地源码位置**：`~/fadcat`（已 clone）

**运行方式**：
```bash
cd ~/fadcat
pip3 install -r requirements.txt
python -m src.mcp
```

**MCP 配置**：
```json
{
  "command": "F:/Software/anaconda/python",
  "args": ["-m", "src.mcp"],
  "env": {
    "PYTHONUNBUFFERED": "1",
    "PYTHONPATH": "C:/Users/xumin/fadcat"
  }
}
```

**核心能力**：
- 多设备 logcat 会话与搜索（正则、模糊匹配）
- 应用进程检查
- FadCam 媒体浏览与备份
- 设备信息与存储统计

---

## Gradle MCP

**仓库**：https://github.com/rnett/gradle-mcp

Gradle 项目智能交互 MCP server。

**运行方式**：通过 jbang 执行 Maven Central 工件
```bash
jbang run --quiet --fresh dev.rnett.gradle-mcp:gradle-mcp:+
```

**环境要求**：
- **JDK 25+**（硬性要求，jbang 会自动下载管理）
- `GRADLE_MCP_PROJECT_ROOT` 指向项目根目录

**核心能力**：
- 项目结构映射（多模块、任务、属性）
- 智能任务执行与后台构建监控
- 依赖审计与源码搜索
- Kotlin REPL（项目运行时环境）
- Compose UI 预览渲染
- Gradle 文档检索
- Develocity Build Scans

**首次启动注意**：jbang 需下载 JDK 25 和大量依赖，耗时 3-5 分钟，请耐心等待。

---

## 本机环境基线

| 工具 | 版本 | 路径 |
|------|------|------|
| adb | 1.0.41 | `F:/Software/AndroidStudioSDK/platform-tools/adb.exe` |
| Android Studio JBR | JDK 21 | `F:/Software/AndroidStudio/jbr` |
| Python | 3.12.4 | `F:/Software/anaconda/python` |
| Node.js | 22.20.0 | `C:/Program Files/nodejs` |
| jbang | latest | `C:/Users/xumin/.jbang/bin/jbang` |

---

## 已知问题与技巧

### Git Bash 路径转换

Windows Git Bash 中执行 `adb shell` 时，Unix 路径（如 `/sdcard/screen.png`）会被自动转换为 Windows 路径，导致命令失败。

**解决**：加 `MSYS_NO_PATHCONV=1` 前缀：
```bash
MSYS_NO_PATHCONV=1 adb shell screencap -p /sdcard/screen.png
```

### 设备日志为空

`adb logcat` 无输出时，通常是设备日志缓冲区为空，不代表工具故障。可先操作应用产生日志再查看。
