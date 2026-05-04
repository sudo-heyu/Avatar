/**
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at https://www.live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

#include "LAppModel.hpp"
#include <fstream>
#include <vector>
#include <CubismModelSettingJson.hpp>
#include <Motion/CubismMotion.hpp>
#include <Physics/CubismPhysics.hpp>
#include <CubismDefaultParameterId.hpp>
#include <Rendering/OpenGL/CubismRenderer_OpenGLES2.hpp>
#include <Utils/CubismString.hpp>
#include <Id/CubismIdManager.hpp>
#include <Motion/CubismMotionQueueEntry.hpp>
#include "LAppDefine.hpp"
#include "LAppPal.hpp"
#include "LAppTextureManager.hpp"
#include "LAppDelegate.hpp"
#include "Motion/CubismBreathUpdater.hpp"
#include "Motion/CubismLookUpdater.hpp"
#include "Motion/CubismExpressionUpdater.hpp"
#include "Motion/CubismEyeBlinkUpdater.hpp"
#include "Motion/CubismPhysicsUpdater.hpp"
#include "Motion/CubismPoseUpdater.hpp"

using namespace Live2D::Cubism::Framework;
using namespace Live2D::Cubism::Framework::DefaultParameterId;
using namespace LAppDefine;

LAppModel::LAppModel()
    : LAppModel_Common()
    , _modelSetting(NULL)
    , _userTimeSeconds(0.0f)
    , _motionUpdated(false)
    , _overrideMouthOpenY(0.0f)
    , _overrideMouthForm(0.0f)
    , _mouthOverrideActive(false)
{
    if (DebugLogEnable)
    {
        _debugMode = true;
    }

    _idParamAngleX = CubismFramework::GetIdManager()->GetId(ParamAngleX);
    _idParamAngleY = CubismFramework::GetIdManager()->GetId(ParamAngleY);
    _idParamAngleZ = CubismFramework::GetIdManager()->GetId(ParamAngleZ);
    _idParamBodyAngleX = CubismFramework::GetIdManager()->GetId(ParamBodyAngleX);
    _idParamEyeBallX = CubismFramework::GetIdManager()->GetId(ParamEyeBallX);
    _idParamEyeBallY = CubismFramework::GetIdManager()->GetId(ParamEyeBallY);
    _idParamMouthOpenY = CubismFramework::GetIdManager()->GetId(ParamMouthOpenY);
    _idParamMouthForm = CubismFramework::GetIdManager()->GetId(ParamMouthForm);
}

LAppModel::~LAppModel()
{
    _renderBuffer.DestroyRenderTarget();

    ReleaseMotions();
    ReleaseExpressions();

    if (_modelSetting != NULL)
    {
        for (csmInt32 i = 0; i < _modelSetting->GetMotionGroupCount(); i++)
        {
            const csmChar* group = _modelSetting->GetMotionGroupName(i);
            ReleaseMotionGroup(group);
        }
        delete _modelSetting;
    }
}

void LAppModel::LoadAssets(const csmChar* dir, const csmChar* fileName)
{
    _modelHomeDir = dir;

    if (_debugMode)
    {
        LAppPal::PrintLogLn("[APP]load model setting: %s", fileName);
    }

    csmSizeInt size;
    const csmString path = csmString(dir) + fileName;

    csmByte* buffer = CreateBuffer(path.GetRawString(), &size);
    ICubismModelSetting* setting = new CubismModelSettingJson(buffer, size);
    DeleteBuffer(buffer, path.GetRawString());

    SetupModel(setting);

    if (_model == NULL)
    {
        LAppPal::PrintLogLn("Failed to LoadAssets().");
        return;
    }

    CreateRenderer(LAppDelegate::GetInstance()->GetWindowWidth(), LAppDelegate::GetInstance()->GetWindowHeight());

    SetupTextures();
}


void LAppModel::SetupModel(ICubismModelSetting* setting)
{
    _updating = true;
    _initialized = false;

    _modelSetting = setting;

    csmByte* buffer;
    csmSizeInt size;

    //Cubism Model
    if (strcmp(_modelSetting->GetModelFileName(), "") != 0)
    {
        csmString path = _modelSetting->GetModelFileName();
        path = _modelHomeDir + path;

        if (_debugMode)
        {
            LAppPal::PrintLogLn("[APP]create model: %s", setting->GetModelFileName());
        }

        buffer = CreateBuffer(path.GetRawString(), &size);
        LoadModel(buffer, size);
        DeleteBuffer(buffer, path.GetRawString());
    }

    //Expression
    if (_modelSetting->GetExpressionCount() > 0)
    {
        const csmInt32 count = _modelSetting->GetExpressionCount();
        for (csmInt32 i = 0; i < count; i++)
        {
            csmString name = _modelSetting->GetExpressionName(i);
            csmString path = _modelSetting->GetExpressionFileName(i);
            path = _modelHomeDir + path;

            buffer = CreateBuffer(path.GetRawString(), &size);
            ACubismMotion* motion = LoadExpression(buffer, size, name.GetRawString());

            if (motion)
            {
                if (_expressions[name] != NULL)
                {
                    ACubismMotion::Delete(_expressions[name]);
                    _expressions[name] = NULL;
                }
                _expressions[name] = motion;
            }

            DeleteBuffer(buffer, path.GetRawString());
        }

        CubismExpressionUpdater* expression = CSM_NEW CubismExpressionUpdater(*_expressionManager);
        _updateScheduler.AddUpdatableList(expression);
    }

    //Physics
    if (strcmp(_modelSetting->GetPhysicsFileName(), "") != 0)
    {
        csmString path = _modelSetting->GetPhysicsFileName();
        path = _modelHomeDir + path;

        buffer = CreateBuffer(path.GetRawString(), &size);
        LoadPhysics(buffer, size);
        if (_physics != nullptr)
        {
            CubismPhysicsUpdater* physics = CSM_NEW CubismPhysicsUpdater(*_physics);
            _updateScheduler.AddUpdatableList(physics);
        }
        DeleteBuffer(buffer, path.GetRawString());
    }

    //Pose
    if (strcmp(_modelSetting->GetPoseFileName(), "") != 0)
    {
        csmString path = _modelSetting->GetPoseFileName();
        path = _modelHomeDir + path;

        buffer = CreateBuffer(path.GetRawString(), &size);
        LoadPose(buffer, size);
        if (_pose != nullptr)
        {
            CubismPoseUpdater* pose = CSM_NEW CubismPoseUpdater(*_pose);
            _updateScheduler.AddUpdatableList(pose);
        }
        DeleteBuffer(buffer, path.GetRawString());
    }

    //EyeBlink
    {
        if (_modelSetting->GetEyeBlinkParameterCount() > 0)
        {
            _eyeBlink = CubismEyeBlink::Create(_modelSetting);

            CubismEyeBlinkUpdater* eyeBlink = CSM_NEW CubismEyeBlinkUpdater(_motionUpdated, *_eyeBlink);
            _updateScheduler.AddUpdatableList(eyeBlink);
        }
    }

    //Breath
    {
        _breath = CubismBreath::Create();

        csmVector<CubismBreath::BreathParameterData> breathParameters;

        breathParameters.PushBack(CubismBreath::BreathParameterData(_idParamAngleX, 0.0f, 15.0f, 6.5345f, 0.5f));
        breathParameters.PushBack(CubismBreath::BreathParameterData(_idParamAngleY, 0.0f, 8.0f, 3.5345f, 0.5f));
        breathParameters.PushBack(CubismBreath::BreathParameterData(_idParamAngleZ, 0.0f, 10.0f, 5.5345f, 0.5f));
        breathParameters.PushBack(CubismBreath::BreathParameterData(_idParamBodyAngleX, 0.0f, 4.0f, 15.5345f, 0.5f));
        breathParameters.PushBack(CubismBreath::BreathParameterData(CubismFramework::GetIdManager()->GetId(ParamBreath), 0.5f, 0.5f, 3.2345f, 0.5f));

        _breath->SetParameters(breathParameters);

        CubismBreathUpdater* breath = CSM_NEW CubismBreathUpdater(*_breath);
        _updateScheduler.AddUpdatableList(breath);
    }

    //UserData
    if (strcmp(_modelSetting->GetUserDataFile(), "") != 0)
    {
        csmString path = _modelSetting->GetUserDataFile();
        path = _modelHomeDir + path;
        buffer = CreateBuffer(path.GetRawString(), &size);
        LoadUserData(buffer, size);
        DeleteBuffer(buffer, path.GetRawString());
    }

    // EyeBlinkIds
    {
        csmInt32 eyeBlinkIdCount = _modelSetting->GetEyeBlinkParameterCount();
        for (csmInt32 i = 0; i < eyeBlinkIdCount; ++i)
        {
            _eyeBlinkIds.PushBack(_modelSetting->GetEyeBlinkParameterId(i));
        }
    }

    // LipSyncIds
    {
        csmInt32 lipSyncIdCount = _modelSetting->GetLipSyncParameterCount();
        for (csmInt32 i = 0; i < lipSyncIdCount; ++i)
        {
            _lipSyncIds.PushBack(_modelSetting->GetLipSyncParameterId(i));
        }
    }

    // Look
    {
        _look = CubismLook::Create();

        csmVector<CubismLook::LookParameterData> lookParameters;

        lookParameters.PushBack(CubismLook::LookParameterData(_idParamAngleX, 30.0f));
        lookParameters.PushBack(CubismLook::LookParameterData(_idParamAngleY, 0.0f, 30.0f));
        lookParameters.PushBack(CubismLook::LookParameterData(_idParamAngleZ, 0.0f, 0.0f, -30.0f));
        lookParameters.PushBack(CubismLook::LookParameterData(_idParamBodyAngleX, 10.0f));
        lookParameters.PushBack(CubismLook::LookParameterData(_idParamEyeBallX, 1.0f));
        lookParameters.PushBack(CubismLook::LookParameterData(_idParamEyeBallY, 0.0f, 1.0f));

        _look->SetParameters(lookParameters);

        CubismLookUpdater* look = CSM_NEW CubismLookUpdater(*_look, *_dragManager);
        _updateScheduler.AddUpdatableList(look);
    }

    _updateScheduler.SortUpdatableList();

    if (_modelSetting == NULL || _modelMatrix == NULL)
    {
        LAppPal::PrintLogLn("Failed to SetupModel().");
        return;
    }

    //Layout
    csmMap<csmString, csmFloat32> layout;
    _modelSetting->GetLayoutMap(layout);
    _modelMatrix->SetupFromLayout(layout);

    _model->SaveParameters();

    for (csmInt32 i = 0; i < _modelSetting->GetMotionGroupCount(); i++)
    {
        const csmChar* group = _modelSetting->GetMotionGroupName(i);
        PreloadMotionGroup(group);
    }

    _motionManager->StopAllMotions();

    _updating = false;
    _initialized = true;
}

void LAppModel::PreloadMotionGroup(const csmChar* group)
{
    const csmInt32 count = _modelSetting->GetMotionCount(group);

    for (csmInt32 i = 0; i < count; i++)
    {
        //ex) idle_0
        csmString name = Utils::CubismString::GetFormatedString("%s_%d", group, i);
        csmString path = _modelSetting->GetMotionFileName(group, i);
        path = _modelHomeDir + path;

        if (_debugMode)
        {
            LAppPal::PrintLogLn("[APP]load motion: %s => [%s_%d] ", path.GetRawString(), group, i);
        }

        csmByte* buffer;
        csmSizeInt size;
        buffer = CreateBuffer(path.GetRawString(), &size);
        CubismMotion* tmpMotion = static_cast<CubismMotion*>(LoadMotion(buffer, size, name.GetRawString(), NULL, NULL, _modelSetting, group, i));

        if (tmpMotion)
        {
            tmpMotion->SetEffectIds(_eyeBlinkIds, _lipSyncIds);

            if (_motions[name] != NULL)
            {
                ACubismMotion::Delete(_motions[name]);
            }
            _motions[name] = tmpMotion;
        }

        DeleteBuffer(buffer, path.GetRawString());
    }
}

void LAppModel::ReleaseMotionGroup(const csmChar* group) const
{
    const csmInt32 count = _modelSetting->GetMotionCount(group);
    for (csmInt32 i = 0; i < count; i++)
    {
        csmString voice = _modelSetting->GetMotionSoundFileName(group, i);
        if (strcmp(voice.GetRawString(), "") != 0)
        {
            csmString path = voice;
            path = _modelHomeDir + path;
        }
    }
}

/**
* @brief すべてのモーションデータの解放
*
* すべてのモーションデータを解放する。
*/
void LAppModel::ReleaseMotions()
{
    for (csmMap<csmString, ACubismMotion*>::const_iterator iter = _motions.Begin(); iter != _motions.End(); ++iter)
    {
        ACubismMotion::Delete(iter->Second);
    }

    _motions.Clear();
}

/**
* @brief すべての表情データの解放
*
* すべての表情データを解放する。
*/
void LAppModel::ReleaseExpressions()
{
    for (csmMap<csmString, ACubismMotion*>::const_iterator iter = _expressions.Begin(); iter != _expressions.End(); ++iter)
    {
        ACubismMotion::Delete(iter->Second);
    }

    _expressions.Clear();
}

void LAppModel::Update()
{
    // 防御：モデルが無効な場合は何もしない
    if (_model == NULL)
    {
        return;
    }

    // 防御：motionManager が無効な場合は何もしない
    if (_motionManager == NULL)
    {
        LAppPal::PrintLogLn("[APP]Update: _motionManager is NULL, skipping");
        return;
    }

    // 防御：_motionData が無効な場合は更新をスキップ
    // これは motion が解放された後に Update が呼ばれるのを防ぐ
    if (_motionManager->IsFinished() == false && _motions.GetSize() == 0)
    {
        LAppPal::PrintLogLn("[APP]Update: motion data may be invalid, stopping motion");
        _motionManager->StopAllMotions();
    }

    csmFloat32 deltaTimeSeconds = LAppPal::GetDeltaTime();

    // 防御：deltaTime の異常値をチェック（二重保護）
    // NaN、Infinity、または極端に大きい値は 0 にリセット
    if (deltaTimeSeconds != deltaTimeSeconds || // NaN check
        deltaTimeSeconds < 0.0f ||
        deltaTimeSeconds > 0.1f) // 最大 100ms
    {
        LAppPal::PrintLogLn("[APP]Update: Invalid deltaTime=%.4f, resetting to 0", deltaTimeSeconds);
        deltaTimeSeconds = 0.0f;
    }

    _userTimeSeconds += deltaTimeSeconds;

    // モーションによるパラメータ更新の有無
    _motionUpdated = false;

    //-----------------------------------------------------------------
    _model->LoadParameters(); // 前回セーブされた状態をロード

    // Debug: Log motion state
    static int frameCount = 0;
    if (frameCount % 60 == 0) { // Log every 60 frames
        csmUint32 pendingCount = 0;
        {
            std::lock_guard<std::mutex> lock(_pendingParametersMutex);
            pendingCount = _pendingParameters.GetSize();
        }
        LAppPal::PrintLogLn("[APP]Update: IsFinished=%d, PendingParams=%d",
            _motionManager->IsFinished(), pendingCount);
    }
    frameCount++;

    // 安全なモーション更新：例外をキャッチしてクラッシュを防ぐ
    if (!_motionManager->IsFinished())
    {
        try {
            _motionUpdated = _motionManager->UpdateMotion(_model, deltaTimeSeconds); // モーションを更新
            if (_motionUpdated) {
                // Debug: Log ParamAngleY value after motion update
                csmFloat32 angleY = _model->GetParameterValue(_idParamAngleY);
                LAppPal::PrintLogLn("[APP]Motion updated! ParamAngleY=%.2f", angleY);
            }
        } catch (...) {
            // モーション更新中にエラーが発生した場合はモーションを停止
            LAppPal::PrintLogLn("[APP]Update: Motion update failed, stopping all motions");
            _motionManager->StopAllMotions();
            _motionUpdated = false;
        }
    }
    else
    {
        _motionUpdated = false;
        // 保留：不自动播放 Idle motion，由上层显式控制
        // StartRandomMotion(MotionGroupIdle, PriorityIdle);
    }

    // Debug: Log pending parameters count
    {
        std::lock_guard<std::mutex> lock(_pendingParametersMutex);
        if (_pendingParameters.GetSize() > 0) {
            LAppPal::PrintLogLn("[APP]FlushPendingParameters: count=%d", _pendingParameters.GetSize());
        }
    }

    // 执行 Java 层待设置的参数（在 SaveParameters 之前）
    // 这样 Java 层的参数会被保存，而 Breath 叠加不会被保存
    FlushPendingParameters();

    _model->SaveParameters(); // 状態を保存
    //-----------------------------------------------------------------

    // 不透明度
    _opacity = _model->GetModelOpacity();

    _updateScheduler.OnLateUpdate(_model, deltaTimeSeconds);

    // 口型参数强制覆盖：确保口型参数具有最高优先级
    // 在所有其他参数更新（Motion、Expression、Breath、Physics）之后执行
    // 这样可以覆盖 Idle 动画中 ParamMouthForm = 1 的设置
    // 注意：需要加锁读取，因为 SetParameterValue() 可能在其他线程写入
    {
        csmFloat32 mouthOpenY = 0.0f;
        csmFloat32 mouthForm = 0.0f;
        csmBool overrideActive = false;
        {
            std::lock_guard<std::mutex> lock(_pendingParametersMutex);
            overrideActive = _mouthOverrideActive;
            mouthOpenY = _overrideMouthOpenY;
            mouthForm = _overrideMouthForm;
        }
        if (overrideActive)
        {
            _model->SetParameterValue(_idParamMouthOpenY, mouthOpenY, 1.0f);
            _model->SetParameterValue(_idParamMouthForm, mouthForm, 1.0f);
        }
    }

    _model->Update();

}

CubismMotionQueueEntryHandle LAppModel::StartMotion(const csmChar* group, csmInt32 no, csmInt32 priority, ACubismMotion::FinishedMotionCallback onFinishedMotionHandler, ACubismMotion::BeganMotionCallback onBeganMotionHandler)
{
    if (priority == PriorityForce)
    {
        _motionManager->SetReservePriority(priority);
    }
    else if (!_motionManager->ReserveMotion(priority))
    {
        if (_debugMode)
        {
            LAppPal::PrintLogLn("[APP]can't start motion.");
        }
        return InvalidMotionQueueEntryHandleValue;
    }

    const csmString fileName = _modelSetting->GetMotionFileName(group, no);

    //ex) idle_0
    csmString name = Utils::CubismString::GetFormatedString("%s_%d", group, no);
    CubismMotion* motion = static_cast<CubismMotion*>(_motions[name.GetRawString()]);
    csmBool autoDelete = false;

    if (motion == NULL)
    {
        csmString path = fileName;
        path = _modelHomeDir + path;

        csmByte* buffer;
        csmSizeInt size;
        buffer = CreateBuffer(path.GetRawString(), &size);
        motion = static_cast<CubismMotion*>(LoadMotion(buffer, size, NULL, onFinishedMotionHandler, onBeganMotionHandler, _modelSetting, group, no));

        if (motion)
        {
            motion->SetEffectIds(_eyeBlinkIds, _lipSyncIds);
            autoDelete = true; // 終了時にメモリから削除
        }

        DeleteBuffer(buffer, path.GetRawString());
    }
    else
    {
        motion->SetBeganMotionHandler(onBeganMotionHandler);
        motion->SetFinishedMotionHandler(onFinishedMotionHandler);
    }

    //voice
    csmString voice = _modelSetting->GetMotionSoundFileName(group, no);
    if (strcmp(voice.GetRawString(), "") != 0)
    {
        csmString path = voice;
        path = _modelHomeDir + path;
    }

    if (_debugMode)
    {
        LAppPal::PrintLogLn("[APP]start motion: [%s_%d]", group, no);
    }
    return  _motionManager->StartMotionPriority(motion, autoDelete, priority);
}

CubismMotionQueueEntryHandle LAppModel::StartRandomMotion(const csmChar* group, csmInt32 priority, ACubismMotion::FinishedMotionCallback onFinishedMotionHandler, ACubismMotion::BeganMotionCallback onBeganMotionHandler)
{
    if (_modelSetting->GetMotionCount(group) == 0)
    {
        return InvalidMotionQueueEntryHandleValue;
    }

    csmInt32 no = rand() % _modelSetting->GetMotionCount(group);

    return StartMotion(group, no, priority, onFinishedMotionHandler, onBeganMotionHandler);
}

CubismMotionQueueEntryHandle LAppModel::StartMotionByPath(const csmChar* motionPath, csmInt32 priority, ACubismMotion::FinishedMotionCallback onFinishedMotionHandler, ACubismMotion::BeganMotionCallback onBeganMotionHandler)
{
    LAppPal::PrintLogLn("[APP]StartMotionByPath: [%s], priority=%d", motionPath, priority);

    if (priority == PriorityForce)
    {
        _motionManager->SetReservePriority(priority);
    }
    else if (!_motionManager->ReserveMotion(priority))
    {
        LAppPal::PrintLogLn("[APP]can't start motion (reserve failed)");
        return InvalidMotionQueueEntryHandleValue;
    }

    // Check cache first - use motion path as cache key
    csmString cacheKey = csmString(motionPath);
    CubismMotion* motion = static_cast<CubismMotion*>(_motions[cacheKey.GetRawString()]);

    if (motion != NULL)
    {
        // Cache hit - reuse cached motion
        LAppPal::PrintLogLn("[APP]Motion cache hit: [%s]", motionPath);
        motion->SetBeganMotionHandler(onBeganMotionHandler);
        motion->SetFinishedMotionHandler(onFinishedMotionHandler);
    }
    else
    {
        // Cache miss - load motion file
        LAppPal::PrintLogLn("[APP]Motion cache miss, loading: [%s]", motionPath);

        csmByte* buffer;
        csmSizeInt size;
        buffer = CreateBuffer(motionPath, &size);

        if (buffer == NULL || size == 0)
        {
            LAppPal::PrintLogLn("[APP]failed to load motion file: [%s], size=%d", motionPath, size);
            return InvalidMotionQueueEntryHandleValue;
        }

        motion = static_cast<CubismMotion*>(LoadMotion(buffer, size, cacheKey.GetRawString(), onFinishedMotionHandler, onBeganMotionHandler, NULL, NULL, -1, false));

        DeleteBuffer(buffer, motionPath);

        if (motion == NULL)
        {
            LAppPal::PrintLogLn("[APP]failed to parse motion: [%s]", motionPath);
            return InvalidMotionQueueEntryHandleValue;
        }

        // Cache the motion for future use
        motion->SetEffectIds(_eyeBlinkIds, _lipSyncIds);
        _motions[cacheKey] = motion;
        // CRITICAL FIX: Do NOT set autoDelete=true for cached motions!
        // Cached motions must persist in _motions for reuse. Setting autoDelete=true
        // causes SDK to delete the motion after playback, leaving a dangling pointer
        // in _motions cache, leading to use-after-free crash on subsequent access.
        LAppPal::PrintLogLn("[APP]Motion cached: [%s]", motionPath);
    }

    // For cached motions, autoDelete must be false to prevent use-after-free.
    // The motion will be cleaned up in ReleaseMotions() or destructor.
    CubismMotionQueueEntryHandle handle = _motionManager->StartMotionPriority(motion, false, priority);
    LAppPal::PrintLogLn("[APP]Motion started, IsFinished=%d", _motionManager->IsFinished());
    return handle;
}

void LAppModel::PreloadMotionByPath(const csmChar* motionPath)
{
    LAppPal::PrintLogLn("[APP]PreloadMotionByPath: [%s]", motionPath);

    // Use motion path as cache key
    csmString cacheKey = csmString(motionPath);

    // Check if already cached
    if (_motions[cacheKey.GetRawString()] != NULL)
    {
        LAppPal::PrintLogLn("[APP]Motion already cached: [%s]", motionPath);
        return;
    }

    // Load motion file
    csmByte* buffer;
    csmSizeInt size;
    buffer = CreateBuffer(motionPath, &size);

    if (buffer == NULL || size == 0)
    {
        LAppPal::PrintLogLn("[APP]Preload failed: [%s], size=%d", motionPath, size);
        return;
    }

    // Parse motion (no callbacks for preload)
    CubismMotion* motion = static_cast<CubismMotion*>(LoadMotion(buffer, size, cacheKey.GetRawString(), NULL, NULL, NULL, NULL, -1, false));

    DeleteBuffer(buffer, motionPath);

    if (motion == NULL)
    {
        LAppPal::PrintLogLn("[APP]Preload parse failed: [%s]", motionPath);
        return;
    }

    // Cache the motion
    motion->SetEffectIds(_eyeBlinkIds, _lipSyncIds);
    _motions[cacheKey] = motion;
    LAppPal::PrintLogLn("[APP]Motion preloaded and cached: [%s]", motionPath);
}

void LAppModel::DoDraw()
{
    if (_model == NULL)
    {
        return;
    }

    GetRenderer<Rendering::CubismRenderer_OpenGLES2>()->DrawModel();
}

void LAppModel::Draw(CubismMatrix44& matrix)
{
    if (_model == NULL)
    {
        return;
    }

    matrix.MultiplyByMatrix(_modelMatrix);

    GetRenderer<Rendering::CubismRenderer_OpenGLES2>()->SetMvpMatrix(&matrix);

    DoDraw();
}

csmBool LAppModel::HitTest(const csmChar* hitAreaName, csmFloat32 x, csmFloat32 y)
{
    // 透明時は当たり判定なし。
    if (_opacity < 1)
    {
        return false;
    }
    const csmInt32 count = _modelSetting->GetHitAreasCount();
    for (csmInt32 i = 0; i < count; i++)
    {
        if (strcmp(_modelSetting->GetHitAreaName(i), hitAreaName) == 0)
        {
            const CubismIdHandle drawID = _modelSetting->GetHitAreaId(i);
            return IsHit(drawID, x, y);
        }
    }
    return false; // 存在しない場合はfalse
}

void LAppModel::SetExpression(const csmChar* expressionID)
{
    ACubismMotion* motion = _expressions[expressionID];
    if (_debugMode)
    {
        LAppPal::PrintLogLn("[APP]expression: [%s]", expressionID);
    }

    if (motion != NULL)
    {
        _expressionManager->StartMotion(motion, false);
    }
    else
    {
        if (_debugMode) LAppPal::PrintLogLn("[APP]expression[%s] is null ", expressionID);
    }
}

void LAppModel::SetParameterValue(const csmChar* parameterId, csmFloat32 value, csmFloat32 weight)
{
    if (_model == NULL || parameterId == NULL)
    {
        return;
    }

    // 防御：检查参数值是否有效，防止 NaN/Infinity 导致后续动画计算崩溃
    if (value != value || // NaN check
        value < -1000.0f || value > 1000.0f || // 极端值检查
        weight != weight || // NaN check
        weight <= 0.0f)
    {
        return;
    }

    const CubismIdHandle id = CubismFramework::GetIdManager()->GetId(parameterId);

    // 检测口型参数并缓存覆盖值（需要锁保护，因为 Update() 在 GL 线程读取）
    {
        std::lock_guard<std::mutex> lock(_pendingParametersMutex);
        if (id == _idParamMouthOpenY)
        {
            _overrideMouthOpenY = value;
            _mouthOverrideActive = true;
        }
        else if (id == _idParamMouthForm)
        {
            _overrideMouthForm = value;
            _mouthOverrideActive = true;
        }
    }

    // 将参数添加到待设置队列，而不是直接设置
    // 这样可以确保在 Update() 流程中的正确时机执行
    // 参数会在 SaveParameters() 之前、LoadParameters() 之后执行
    // 避免被 LoadParameters() 覆盖，也不会污染 Breath 叠加后的值
    PendingParameterData data;
    data.ParameterId = id;
    data.Value = value;
    data.Weight = weight;

    std::lock_guard<std::mutex> lock(_pendingParametersMutex);
    _pendingParameters.PushBack(data);
}

void LAppModel::FlushPendingParameters()
{
    std::lock_guard<std::mutex> lock(_pendingParametersMutex);

    // 执行所有待设置的参数
    for (csmUint32 i = 0; i < _pendingParameters.GetSize(); ++i)
    {
        PendingParameterData* data = &_pendingParameters[i];
        _model->SetParameterValue(data->ParameterId, data->Value, data->Weight);
    }

    // 清空队列
    _pendingParameters.Clear();
}

Csm::csmBool LAppModel::IsMotionFinished() const
{
    return _motionManager->IsFinished();
}

void LAppModel::SetRandomExpression()
{
    if (_expressions.GetSize() == 0)
    {
        return;
    }

    csmInt32 no = rand() % _expressions.GetSize();
    csmMap<csmString, ACubismMotion*>::const_iterator map_ite;
    csmInt32 i = 0;
    for (map_ite = _expressions.Begin(); map_ite != _expressions.End(); map_ite++)
    {
        if (i == no)
        {
            csmString name = (*map_ite).First;
            SetExpression(name.GetRawString());
            return;
        }
        i++;
    }
}

void LAppModel::ReloadRenderer()
{
    // 停止所有正在播放的动作，防止在重建渲染器时访问无效资源
    if (_motionManager != NULL)
    {
        _motionManager->StopAllMotions();
    }

    // 清除动作缓存，因为 GL 上下文可能已丢失
    // 动作文件会在下次播放时重新加载
    ReleaseMotions();
    LAppPal::PrintLogLn("[APP]ReloadRenderer: Motion cache cleared due to GL context change");

    DeleteRenderer();

    CreateRenderer(LAppDelegate::GetInstance()->GetWindowWidth(), LAppDelegate::GetInstance()->GetWindowHeight());

    SetupTextures();
}

void LAppModel::SetupTextures()
{
    for (csmInt32 modelTextureNumber = 0; modelTextureNumber < _modelSetting->GetTextureCount(); modelTextureNumber++)
    {
        // テクスチャ名が空文字だった場合はロード・バインド処理をスキップ
        if (strcmp(_modelSetting->GetTextureFileName(modelTextureNumber), "") == 0)
        {
            continue;
        }

        //OpenGLのテクスチャユニットにテクスチャをロードする
        csmString texturePath = _modelSetting->GetTextureFileName(modelTextureNumber);
        texturePath = _modelHomeDir + texturePath;

        LAppTextureManager::TextureInfo* texture = LAppDelegate::GetInstance()->GetTextureManager()->CreateTextureFromPngFile(texturePath.GetRawString());
        if (texture == NULL)
        {
            LAppPal::PrintLogLn("[APP]SetupTextures: failed to load texture[%d]: %s", modelTextureNumber, texturePath.GetRawString());
            continue;
        }
        const csmInt32 glTextueNumber = texture->id;

        //OpenGL
        GetRenderer<Rendering::CubismRenderer_OpenGLES2>()->BindTexture(modelTextureNumber, glTextueNumber);
    }

#ifdef PREMULTIPLIED_ALPHA_ENABLE
    GetRenderer<Rendering::CubismRenderer_OpenGLES2>()->IsPremultipliedAlpha(true);
#else
    GetRenderer<Rendering::CubismRenderer_OpenGLES2>()->IsPremultipliedAlpha(false);
#endif
}

void LAppModel::MotionEventFired(const csmString& eventValue)
{
    CubismLogInfo("%s is fired on LAppModel!!", eventValue.GetRawString());
}

Csm::Rendering::CubismRenderTarget_OpenGLES2& LAppModel::GetRenderBuffer()
{
    return _renderBuffer;
}
