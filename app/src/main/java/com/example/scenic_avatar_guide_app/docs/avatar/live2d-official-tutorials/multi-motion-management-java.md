# 部位で異なるモーション管理を行う(Java)

> **来源**: Live2D Cubism SDK チュートリアル  
> **原文链接**: https://docs.live2d.com/cubism-sdk-tutorials/multi-motion-management-java/

---

# Live2D Cubism SDK for Java — 部位差异化管理多段动画

## 教程概述

本教程介绍如何在 Java 环境下，为 Live2D 模型的不同身体部位实现独立的动画管理。以右手和左手分别控制不同动画为例，通过为 LAppModel 增加多个 CubismMotionManager 实例来实现并行播放不同动画的功能。

## 一、为何需要增加 CubismMotionManager

CubismMotionManager 虽然支持淡入淡出和临时叠加多个动画播放，但播放新动画时会自动淡出正在播放的动画，无法同时并行播放多个动画。

> "この問題を解決するために、モーションマネージャーのインスタンスを複数用意し、再生させるモーションマネージャーをすみ分ける因而并行动画播放得以实现。"

## 二、实现步骤

### 2.1 添加运动管理器声明

在 `LAppModel` 类中添加两个新的运动管理器字段：

```java
public class LAppModel extends CubismUserModel {
    // ... 其他字段 ...
    private CubismMotionManager rightArmMotionManager;  // 右臂管理器
    private CubismMotionManager leftArmMotionManager;   // 左臂管理器
}
```

### 2.2 在构造函数中初始化

```java
public LAppModel() {
    // ... 其他初始化代码 ...
    rightArmMotionManager = new CubismMotionManager();
    leftArmMotionManager = new CubismMotionManager();
}
```

### 2.3 修改更新处理逻辑

在 `update()` 方法中，在 `loadParameters()` 和 `saveParameters()` 之间添加运动管理器更新调用：

```java
public void update() {
    // ... 其他代码 ...
    
    // 加载上次保存的状态
    _model.loadParameters();

    // 如果没有正在播放的动画，随机播放待机动画
    if (_motionManager.isFinished()) {
        startRandomMotion(LAppDefine.MotionGroup.IDLE.getId(), 
                          LAppDefine.Priority.IDLE.getPriority());
    } else {
        isMotionUpdated = _motionManager.updateMotion(_model, deltaTimeSeconds);
    }
    
    // 添加新的运动管理器更新
    isMotionUpdated |= rightArmMotionManager.updateMotion(_model, deltaTimeSeconds);
    isMotionUpdated |= leftArmMotionManager.updateMotion(_model, deltaTimeSeconds);

    // 保存模型状态
    _model.saveParameters();
}
```

**重要提示**：`updateMotion` 调用必须放在 `_model.loadParameters()` 和 `_model.saveParameters()` 之间。如果执行多次 `updateMotion`，更新参数可能重复，**后者执行的内容会覆盖前者**。

### 2.4 创建新的播放方法

创建 `startHandMotion` 方法接收目标运动管理器作为参数：

```java
public int startHandMotion(
    CubismMotionManager targetManager,
    final String group,
    int number,
    int priority
) {
    if (priority == LAppDefine.Priority.FORCE.getPriority()) {
        targetManager.setReservationPriority(priority);
    } else if (!targetManager.reserveMotion(priority)) {
        if (_debugMode) {
            LAppPal.logger.print("Cannot start motion.");
            return -1;
        }
    }
    
    final String fileName = _modelSetting.getMotionFileName(group, number);
    String name = group + "_" + number;
    CubismMotion motion = (CubismMotion) _motions.get(name);

    if (motion == null) {
        return -1;  // 未预加载则不播放
    }
    if (_debugMode) {
        LAppPal.logger.print("Start motion: [" + group + "_" + number + "]");
    }
    
    return targetManager.startMotionPriority(motion, priority);
}
```

创建随机播放左右手动画的便捷方法：

```java
public int startRandomRightHandMotion(final String group, int priority) {
    if (_modelSetting.getMotionCount(group) == 0) {
        return -1;
    }
    Random random = new Random();
    int number = random.nextInt(Integer.MAX_VALUE) % _modelSetting.getMotionCount(group);
    return startHandMotion(rightArmMotionManager, group, number, priority);
}

public int startRandomLeftHandMotion(final String group, int priority) {
    if (_modelSetting.getMotionCount(group) == 0) {
        return -1;
    }
    Random random = new Random();
    int number = random.nextInt(Integer.MAX_VALUE) % _modelSetting.getMotionCount(group);
    return startHandMotion(leftArmMotionManager, group, number, priority);
}
```

### 2.5 添加触摸交互

修改 `onTap` 方法，添加左右手臂区域的点击检测和动画触发：

```java
public void onTap(float x, float y) {
    for (LAppModel model : _models) {
        // 点击头部随机播放表情
        if (model.hitTest(HitAreaName.HEAD.getId(), x, y)) {
            model.setRandomExpression();
        }
        // 点击身体随机播放全身动画
        else if (model.hitTest(HitAreaName.BODY.getId(), x, y)) {
            model.startRandomMotion(MotionGroup.TAP_BODY.getId(), 
                                    Priority.NORMAL.getPriority(), _finishedMotion);
        }
        // 点击右臂区域
        else if (model.hitTest("Right", x, y)) {
            model.startRandomRightHandMotion("Right", Priority.FORCE.getPriority());
        }
        // 点击左臂区域
        else if (model.hitTest("Left", x, y)) {
            model.startRandomLeftHandMotion("Left", Priority.FORCE.getPriority());
        }
    }
}
```

## 三、参数分配注意事项

当使用多个运动管理器时，如果不同动画间存在参数重叠，后执行的 `updateMotion` 会覆盖先前的值。

> "この現象は、優先順位が低いモーションマネージャーで再生するモーションデータ内に更新対象としては意図していないパラメータに基准値が設定されている場合に見られます。"

例如，高优先级运动管理器（负责手臂动画）播放完毕后，低优先级的待机动画管理器会覆盖手臂参数。

**解决方案**：在制作动画时，将各运动管理器负责的参数严格分离，避免不需要更新的参数被意外覆盖。

> "パラメータごとに分離しておくするのか、上書きを前提とするのか、膨大な修正をしないためにもモーションの作成前に仕様をはっきりと定めておくことが重要です。"

## 四、总结要点

| 要点 | 说明 |
|------|------|
| 运动管理器数量 | 根据需要并行播放的部位数量添加 |
| `updateMotion` 调用位置 | 必须在 `loadParameters()` 和 `saveParameters()` 之间 |
| 参数覆盖规则 | 多次调用时，后者覆盖前者 |
| 动画制作策略 | 提前规划好各动画的参数分配范围 |
