# サンプルプロジェクトにモデルを追加する (Java)

> **来源**: Live2D Cubism SDK チュートリアル  
> **原文链接**: https://docs.live2d.com/cubism-sdk-tutorials/add-model-to-sample-project-java/

---

## 概述

本教程说明如何向 Live2D Cubism SDK for Java 的示例项目添加 Live2D Cubism 模型。

**前提条件**：需先按照「Android示例执行步骤」完成示例项目的构建。

**示例模型**：教程使用「Miara」作为示例模型。

关于从下载模型到加工处理、再到准备嵌入程序的完整流程，请参阅 SDK for Native 的「向示例项目添加模型」页面。

---

## 程序修改步骤

### 步骤1：添加模型文件夹

将配置完成的模型文件夹添加至资源文件夹：
`[root]/Sample/src/main/assets`

### 步骤2：修改 LAppDefine.java

在 `LAppDefine.java` 的 `ModelDir` 枚举类型中添加 MIARA：

```java
public enum ModelDir {
    HARU(0, "Haru"),
    HIYORI(1, "Hiyori"),
    MARK(2, "Mark"),
    NATORI(3, "Natori"),
    RICE(4, "Rice"),
    MIARA(5,"miara"); // < added
    ...
}
```

---

## 重要注意事项

> **索引连续性要求**："第1引数的索引必须正确保持连续编号，否则将无法正常工作。"

> **文件夹名称**："第2参数应输入模型的文件夹名称。"

---

## 反馈

本教程内容至此结束。如有相关意见或建议，请通过反馈表单提交（仅限发送）。

**© 2010 - 2026 Live2D Inc.**
