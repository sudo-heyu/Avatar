# 部位で異なるモーション管理を行う(Native)

> **来源**: Live2D Cubism SDK チュートリアル  
> **原文链接**: https://docs.live2d.com/cubism-sdk-tutorials/multi-motion-management/

---

# Live2D Cubism SDK Native 教程：部位差异化管理

## 教程目标

实现同一模型不同部位（例：左手、右手）播放不同动画的功能。

---

## 1. 添加动画管理器

在 LAppModel 类中声明额外的动画管理器：

```cpp
class LAppModel : public Csm::CubismUserModel
{
    Live2D::Cubism::Framework::CubismMotionManager* _rightArmMotionManager; // 新增
    Live2D::Cubism::Framework::CubismMotionManager* _leftArmMotionManager;  // 新增
};
```

## 2. 构造函数与析构函数

```cpp
LAppModel::LAppModel()
{
    _rightArmMotionManager = new CubismMotionManager(); // 新增
    _leftArmMotionManager = new CubismMotionManager();  // 新增
}

LAppModel::~LAppModel()
{
    CSM_DELETE(_rightArmMotionManager); // 新增
    CSM_DELETE(_leftArmMotionManager);  // 新增
}
```

## 3. 更新处理

在 Update 方法中添加多个动画管理器的更新：

```cpp
void LAppModel::Update()
{
    _model->LoadParameters(); // 加载上次保存的状态
    if (_motionManager->IsFinished())
    {
        StartRandomMotion(MotionGroupIdle, PriorityIdle);
    }
    else
    {
        motionUpdated = _motionManager->UpdateMotion(_model, deltaTimeSeconds);
    }
    motionUpdated |= _rightArmMotionManager->UpdateMotion(_model, deltaTimeSeconds); // 新增
    motionUpdated |= _leftArmMotionManager->UpdateMotion(_model, deltaTimeSeconds);   // 新增
    _model->SaveParameters(); // 保存状态
}
```

**注意事项**：`UpdateMotion` 必须放在 `LoadParameters` 和 `SaveParameters` 之间。若多个管理器更新相同参数，后执行的内容会覆盖前者。

## 4. 创建播放函数

创建 StartHandMotion 函数（接收目标管理器参数）：

```cpp
CubismMotionQueueEntryHandle LAppModel::StartHandMotion(
    CubismMotionManager* targetManage, 
    const csmChar* group, 
    csmInt32 no, 
    csmInt32 priority)
{
    if (priority == PriorityForce)
    {
        targetManage->SetReservePriority(priority);
    }
    else if (!targetManage->ReserveMotion(priority))
    {
        return InvalidMotionQueueEntryHandleValue;
    }

    csmString name = Utils::CubismString::GetFormatedString("%s_%d", group, no);
    CubismMotion* motion = static_cast<CubismMotion*>(_motions[name.GetRawString()]);

    if (motion == NULL) return InvalidMotionQueueEntryHandleValue;

    return targetManage->StartMotionPriority(motion, autoDelete, priority);
}
```

创建随机播放函数：

```cpp
CubismMotionQueueEntryHandle LAppModel::StartRandomRightHandMotion(
    const csmChar* group, csmInt32 priority)
{
    csmInt32 no = rand() % _modelSetting->GetMotionCount(group);
    return StartHandMotion(_rightArmMotionManager, group, no, priority);
}

CubismMotionQueueEntryHandle LAppModel::StartRandomLeftHandMotion(
    const csmChar* group, csmInt32 priority)
{
    csmInt32 no = rand() % _modelSetting->GetMotionCount(group);
    return StartHandMotion(_leftArmMotionManager, group, no, priority);
}
```

## 5. 碰撞检测集成

在 OnTap 回调中响应点击：

```cpp
void LAppLive2DManager::OnTap(csmFloat32 x, csmFloat32 y)
{
    for (csmUint32 i = 0; i < _models.GetSize(); i++)
    {
        if (_models[i]->HitTest("Right", x, y))
        {
            _models[i]->StartRandomRightHandMotion("Right", PriorityForce);
        }
        else if (_models[i]->HitTest("Left", x, y))
        {
            _models[i]->StartRandomLeftHandMotion("Left", PriorityForce);
        }
    }
}
```

---

## 关键注意事项

**参数冲突问题**：多个动画管理器若操作同一参数，后执行的管理器会覆盖前面的结果。高优先级管理器（如手部动作）播放完毕后，低优先级管理器（如待机动作）可能重新设置该参数。

**解决方案**：
1. 确保各动画数据仅包含其负责部位的关键帧
2. 非负责参数保持空白或使用默认值
3. **在制作动画前务必规划好参数分配方案**，避免后期大量修改
