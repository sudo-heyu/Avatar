# サンプルプロジェクトにモデルを追加する (Native)

> **来源**: Live2D Cubism SDK チュートリアル  
> **最后更新**: 2024年12月19日  
> **原文链接**: https://docs.live2d.com/cubism-sdk-tutorials/model-add-to-sample-project/

---

# Live2D Cubism SDK for Native - 教程：向示例项目添加模型

## 前置条件

本教程假设您已按照「[Cocos2d-xサンプルのビルド](https://docs.live2d.com/cubism-sdk-tutorials/sample-build/)」或「[OpenGLサンプルのビルド](https://docs.live2d.com/cubism-sdk-tutorials/sample-build-opengl/)」完成示例项目构建。

## 工具准备

下载并安装 **Cubism Editor 5**（链接：https://www.live2d.com/download/cubism/）。安装后会自动包含 **Cubism Viewer (for OW)** 工具，用于模型数据加工。

## 模型准备

本教程使用官方示例模型「**ミアラ（Miara）**」，下载地址：https://www.live2d.com/download/sample-data/

## 添加碰撞检测区域（HitArea）

示例程序功能：
- 点击模型中"Head"区域：切换表情
- 点击模型中"Body"区域：随机播放动作

### 操作步骤

在 Cubism Editor 中，为ミアラ模型添加两个碰撞检测用的艺术网格：

| 区域 | 名称 | 说明 |
|------|------|------|
| HitArea | Body | 点击触发随机动作 |
| HitArea2 | Head | 点击切换表情 |

详细操作请参阅「[当たり判定の設定準備](https://docs.live2d.com/cubism-editor-manual/hittest/)」。

## 添加参数设置

ミアラ模型缺少眨眼和口型同步参数，需手动添加：

1. 设置 **EyeBlink** 参数（用于自动眨眼）
2. 设置 **LipSync** 参数（用于口型同步）

详细设置请参阅「[まばたき設定](https://docs.live2d.com/cubism-editor-manual/eye-blink-settings/)」。

## 创建表情动作

ミアラ模型自带步行和动作动画，但缺少表情动作。

### 创建步骤

1. 在 Animator 中创建三个表情：
   - **happy** - 开心表情
   - **normal** - 普通表情
   - **sad** - 悲伤表情

2. 将两眼的开闭切换为「**乘算模式**」

详细教程请参阅「[アニメーションビューで表情を作成](https://docs.live2d.com/cubism-editor-manual/create-facial-expressions/)」。

## 使用 Cubism Viewer (for OW) 处理

### HitArea 设置

Editor 中定义的艺术网格 HitArea 无法被应用程序直接识别。需使用 Cubism Viewer (for OW) 将 HitArea 信息写入 `.model3.json` 文件。

### 表情文件转换

Animator 中创建的表情动作无法直接使用，需在 Cubism Viewer (for OW) 中转换为 `.exp3.json` 文件。

详细教程请参阅「[表情の設定と書き出し](https://docs.live2d.com/cubism-editor-manual/setting-and-exporting-facial-expressions/)」。

### 动作文件注册

在 Cubism Viewer (for OW) 中注册动作并设置分组：

| 分组名称 | 包含文件 |
|----------|----------|
| **Idle** | Scene1.motion3.json |
| **TapBody** | Scene2.motion3.json, Scene3.motion3.json |

详细设置请参阅「[モーションの設定](https://docs.live2d.com/cubism-editor-manual/motion-setting/)。

## 导出模型文件

完成所有设置后，导出 `.model3.json` 模型配置文件。会自动同时导出 `.exp3.json`、`.pose3.json` 等文件。

### 重要提示

> 出于程序设计要求，导出的文件夹名称必须与 `.model3.json` 文件名一致。

### model3.json 文件结构示例

```json
{
	"Version": 3,
	"FileReferences": {
		"Moc": "miara.moc3",
		"Textures": [
			"miara.4096/texture_00.png"
		],
		"Physics": "miara.physics3.json",
		"UserData": "miara.userdata3.json",
		"Pose": "miara.pose3.json",
		"Expressions": [
			{
				"Name": "happy.exp3.json",
				"File": "expressions/happy.exp3.json"
			},
			{
				"Name": "normal.exp3.json",
				"File": "expressions/normal.exp3.json"
			},
			{
				"Name": "sad.exp3.json",
				"File": "expressions/sad.exp3.json"
			}
		],
		"Motions": {
			"Idle": [
				{
					"File": "motions/Scene1.motion3.json"
				}
			],
			"TapBody": [
				{
					"File": "motions/Scene2.motion3.json"
				},
				{
					"File": "motions/Scene3.motion3.json"
				}
			]
		}
	},
	"Groups": [
		{
			"Target": "Parameter",
			"Name": "EyeBlink",
			"Ids": [
				"ParamEyeLOpen",
				"ParamEyeROpen"
			]
		},
		{
			"Target": "Parameter",
			"Name": "LipSync",
			"Ids": [
				"ParamMouthOpenY"
			]
		}
	],
	"HitAreas": [
		{
			"Id": "HitArea",
			"Name": "Body"
		},
		{
			"Id": "HitArea2",
			"Name": "Head"
		}
	]
}
```

各 JSON 详细规格可参阅 GitHub：https://github.com/Live2D/CubismSpecs

## 集成到示例程序

将导出的模型文件夹复制到以下路径：

```
[项目根目录]\Samples\Resources\
```

## 运行测试

1. 重新编译项目
2. 运行程序
3. 点击右上角齿轮图标可切换显示的模型
4. 模型切换顺序按 Resources 文件夹内文件夹名称的字母顺序排列

## 相关文档链接

- [Cubism Editor 手册](https://docs.live2d.com/cubism-editor-manual/top/)
- [Cubism SDK 手册](https://docs.live2d.com/cubism-sdk-manual/top/)
- [Cubism 规范（GitHub）](https://github.com/Live2D/CubismSpecs)

---

**文档信息**
- 最后更新：2024年12月19日
- 版本：Cubism Editor 5
