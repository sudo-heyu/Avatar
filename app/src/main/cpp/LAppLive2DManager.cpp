/**
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at https://www.live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

#include "LAppLive2DManager.hpp"
#include <string.h>
#include <stdlib.h>
#include <mutex>
#include <GLES2/gl2.h>
#include <Rendering/CubismRenderer.hpp>
#include <Rendering/OpenGL/CubismOffscreenManager_OpenGLES2.hpp>
#include "LAppPal.hpp"
#include "LAppDefine.hpp"
#include "LAppDelegate.hpp"
#include "LAppModel.hpp"
#include "LAppView.hpp"
#include "JniBridgeC.hpp"

using namespace Csm;
using namespace LAppDefine;

namespace {
    LAppLive2DManager* s_instance = NULL;
    std::mutex s_instanceMutex;
    const char DefaultModelDir[] = "live2d/hiyori";
    const char DefaultModelJson[] = "Hiyori.model3.json";
    const char DefaultSceneKey[] = "hiyori";

    void BeganMotion(ACubismMotion* self)
    {
        LAppPal::PrintLogLn("Motion Began: %x", self);
    }

    void FinishedMotion(ACubismMotion* self)
    {
        LAppPal::PrintLogLn("Motion Finished: %x", self);
    }

    int CompareCsmString(const void* a, const void* b)
    {
        return strcmp(reinterpret_cast<const Csm::csmString*>(a)->GetRawString(),
            reinterpret_cast<const Csm::csmString*>(b)->GetRawString());
    }
}

LAppLive2DManager* LAppLive2DManager::GetInstance()
{
    std::lock_guard<std::mutex> lock(s_instanceMutex);
    if (s_instance == NULL)
    {
        s_instance = new LAppLive2DManager();
    }
    return s_instance;
}

void LAppLive2DManager::ReleaseInstance()
{
    std::lock_guard<std::mutex> lock(s_instanceMutex);
    if (s_instance != NULL)
    {
        delete s_instance;
    }
    s_instance = NULL;
}

LAppLive2DManager::LAppLive2DManager()
    : _viewMatrix(NULL),
      _upperBodyMode(false)
{
    _viewMatrix = new CubismMatrix44();
    SetUpModel();

    ChangeScene(LAppDelegate::GetInstance()->GetSceneIndex());
}

LAppLive2DManager::~LAppLive2DManager()
{
    {
        std::lock_guard<std::mutex> lock(_managerMutex);
        ReleaseAllModel();
    }
    delete _viewMatrix;
    Csm::Rendering::CubismOffscreenManager_OpenGLES2::ReleaseInstance();
}

void LAppLive2DManager::ReleaseAllModel()
{
    // 注意：调用者必须持有 _managerMutex 锁
    // 先保存需要删除的模型指针，避免 delete 和 Clear 之间的时间窗口内
    // 其他线程通过 GetModel() 拿到悬空指针
    csmVector<LAppModel*> modelsToDelete;
    for (csmUint32 i = 0; i < _models.GetSize(); i++)
    {
        modelsToDelete.PushBack(_models[i]);
    }
    _models.Clear();

    for (csmUint32 i = 0; i < modelsToDelete.GetSize(); i++)
    {
        delete modelsToDelete[i];
    }
}

void LAppLive2DManager::SetUpModel()
{
    _modelDir.Clear();
    _modelDir.PushBack(DefaultSceneKey);
}

LAppModel* LAppLive2DManager::GetModel(csmUint32 no) const
{
    std::lock_guard<std::mutex> lock(_managerMutex);
    if (no < _models.GetSize())
    {
        return _models[no];
    }

    return NULL;
}

void LAppLive2DManager::SetRenderTargetSize(csmUint32 width, csmUint32 height)
{
    std::lock_guard<std::mutex> lock(_managerMutex);
    for (csmUint32 i = 0; i < _models.GetSize(); i++)
    {
        LAppModel* model = _models[i];
        if (model == NULL)
        {
            continue;
        }

        model->SetRenderTargetSize(width, height);
    }
}

void LAppLive2DManager::OnDrag(csmFloat32 x, csmFloat32 y) const
{
    std::lock_guard<std::mutex> lock(_managerMutex);
    for (csmUint32 i = 0; i < _models.GetSize(); i++)
    {
        LAppModel* model = _models[i];
        if (model == NULL)
        {
            continue;
        }
        model->SetDragging(x, y);
    }
}

void LAppLive2DManager::OnTap(csmFloat32 x, csmFloat32 y)
{
    std::lock_guard<std::mutex> lock(_managerMutex);
    int width = LAppDelegate::GetInstance()->GetWindowWidth();
    int height = LAppDelegate::GetInstance()->GetWindowHeight();
    float aspectRatio = static_cast<float>(width) / static_cast<float>(height);
    float displayRatio = static_cast<float>(height) / static_cast<float>(width);

    if (DebugLogEnable)
    {
        LAppPal::PrintLogLn("[APP]tap point: {x:%.2f y:%.2f}", x, y);
    }

    for (csmUint32 i = 0; i < _models.GetSize(); i++)
    {
        LAppModel* model = _models[i];
        if (model == NULL || model->GetModel() == NULL)
        {
            continue;
        }
        float canvasRatio = model->GetModel()->GetCanvasHeight() / model->GetModel()->GetCanvasWidth();

        csmFloat32 adjustedX = x;
        csmFloat32 adjustedY = y;

        if (canvasRatio < displayRatio)
        {
            // OnUpdateでのプロジェクションスケールを打ち消してモデル座標系に変換
            adjustedX = x / aspectRatio;
            adjustedY = y / aspectRatio;
        }

        if (model->HitTest(HitAreaNameHead, adjustedX, adjustedY))
        {
            if (DebugLogEnable)
            {
                LAppPal::PrintLogLn("[APP]hit area: [%s]", HitAreaNameHead);
            }
            model->SetRandomExpression();
        }
        else if (model->HitTest(HitAreaNameBody, adjustedX, adjustedY))
        {
            if (DebugLogEnable)
            {
                LAppPal::PrintLogLn("[APP]hit area: [%s]", HitAreaNameBody);
            }
            model->StartRandomMotion(MotionGroupTapBody, PriorityNormal, FinishedMotion, BeganMotion);
        }
    }
}

void LAppLive2DManager::OnUpdate() const
{
    std::lock_guard<std::mutex> lock(_managerMutex);
    int width = LAppDelegate::GetInstance()->GetWindowWidth();
    int height = LAppDelegate::GetInstance()->GetWindowHeight();
    float aspectRatio = static_cast<float>(width) / static_cast<float>(height);
    float displayRatio = static_cast<float>(height) / static_cast<float>(width);

    // モデルで使用するオフスクリーン管理の開始処理
    Csm::Rendering::CubismOffscreenManager_OpenGLES2::GetInstance()->BeginFrameProcess();

    csmUint32 modelCount = _models.GetSize();
    for (csmUint32 i = 0; i < modelCount; ++i)
    {
        CubismMatrix44 projection;
        LAppModel* model = _models[i];

        if (model == NULL)
        {
            LAppPal::PrintLogLn("Failed to GetModel(%d).", i);
            continue;
        }

        if (model->GetModel() == NULL)
        {
            LAppPal::PrintLogLn("Failed to model->GetModel().");
            continue;
        }

        float canvasRatio = model->GetModel()->GetCanvasHeight() / model->GetModel()->GetCanvasWidth();

        if (_upperBodyMode)
        {
            // 上半身模式：宽度适配 + 正常 aspect ratio + 放大 + 下移
            model->GetModelMatrix()->SetWidth(2.0f);
            projection.Scale(1.0f, aspectRatio);

            // 整体放大 1.6 倍，使上半身占据更多画面
            projection.ScaleRelative(1.6f, 1.6f);

            // 向下平移，使画布中心（腰部附近）靠近视口底部
            // 裁剪空间 Y 范围 [-1, 1]，底部为 -1
            projection.TranslateRelative(0.0f, -0.9f);
        }
        else if (canvasRatio < displayRatio)
        {
            // 横長モデルを幅に合わせて縦方向のスケールを調整
            model->GetModelMatrix()->SetWidth(2.0f);
            projection.Scale(1.0f, aspectRatio);
        }
        else
        {
            // 縦長モデルを高さに合わせて横方向のスケールを調整
            model->GetModelMatrix()->SetHeight(2.0f);
            projection.Scale(1.0f / aspectRatio, 1.0f);
        }

        // 必要があればここで乗算
        if (_viewMatrix != NULL)
        {
            projection.MultiplyByMatrix(_viewMatrix);
        }

        // モデル1体描画前コール
        LAppDelegate::GetInstance()->GetView()->PreModelDraw(*model);

        model->Update();
        model->Draw(projection);///< 参照渡しなのでprojectionは変質する

        // モデル1体描画後コール
        LAppDelegate::GetInstance()->GetView()->PostModelDraw(*model);
    }

    // モデルで使用するオフスクリーン管理の終了処理
    Csm::Rendering::CubismOffscreenManager_OpenGLES2::GetInstance()->EndFrameProcess();
    // もし余っているオフスクリーンのリソースを解放したい場合行う処理
    Csm::Rendering::CubismOffscreenManager_OpenGLES2::GetInstance()->ReleaseStaleRenderTextures();
}

void LAppLive2DManager::SetParameter(const csmChar* parameterId, csmFloat32 value, csmFloat32 weight)
{
    std::lock_guard<std::mutex> lock(_managerMutex);
    for (csmUint32 i = 0; i < _models.GetSize(); ++i)
    {
        if (_models[i] != NULL)
        {
            _models[i]->SetParameterValue(parameterId, value, weight);
        }
    }
}

void LAppLive2DManager::SetExpression(const csmChar* expressionId)
{
    std::lock_guard<std::mutex> lock(_managerMutex);
    for (csmUint32 i = 0; i < _models.GetSize(); ++i)
    {
        if (_models[i] != NULL)
        {
            _models[i]->SetExpression(expressionId);
        }
    }
}

void LAppLive2DManager::NextScene()
{
    csmInt32 no = (LAppDelegate::GetInstance()->GetSceneIndex() + 1) % _modelDir.GetSize();
    ChangeScene(no);
}

void LAppLive2DManager::ChangeScene(Csm::csmInt32 index)
{
    std::lock_guard<std::mutex> lock(_managerMutex);
    LAppDelegate::GetInstance()->SetSceneIndex(index);
    if (DebugLogEnable)
    {
        LAppPal::PrintLogLn("[APP]model index: %d", index);
    }

    // model3.jsonのパスを決定する.
    // ディレクトリ名とmodel3.jsonの名前を一致していることが条件
    csmString modelPath(ResourcesPath);
    modelPath += DefaultModelDir;
    modelPath += "/";
    csmString modelJsonName(DefaultModelJson);

    ReleaseAllModel();
    _models.PushBack(new LAppModel());
    _models[0]->LoadAssets(modelPath.GetRawString(), modelJsonName.GetRawString());

    /*
     * モデル半透明表示を行うサンプルを提示する。
     * ここでUSE_RENDER_TARGET、USE_MODEL_RENDER_TARGETが定義されている場合
     * 別のレンダリングターゲットにモデルを描画し、描画結果をテクスチャとして別のスプライトに張り付ける。
     */
    {
#if defined(USE_RENDER_TARGET)
        // LAppViewの持つターゲットに描画を行う場合、こちらを選択
        LAppView::SelectTarget useRenderTarget = LAppView::SelectTarget_ViewFrameBuffer;
#elif defined(USE_MODEL_RENDER_TARGET)
        // 各LAppModelの持つターゲットに描画を行う場合、こちらを選択
        LAppView::SelectTarget useRenderTarget = LAppView::SelectTarget_ModelFrameBuffer;
#else
        // デフォルトのメインフレームバッファへレンダリングする(通常)
        LAppView::SelectTarget useRenderTarget = LAppView::SelectTarget_None;
#endif

#if defined(USE_RENDER_TARGET) || defined(USE_MODEL_RENDER_TARGET)
        // モデル個別にαを付けるサンプルとして、もう1体モデルを作成し、少し位置をずらす
        _models.PushBack(new LAppModel());
        _models[1]->LoadAssets(modelPath.GetRawString(), modelJsonName.GetRawString());
        _models[1]->GetModelMatrix()->TranslateX(0.2f);
#endif

        LAppDelegate::GetInstance()->GetView()->SwitchRenderingTarget(useRenderTarget);

        // 別レンダリング先を選択した際の背景クリア色
        float clearColor[3] = { 0.0f, 0.0f, 0.0f };
        LAppDelegate::GetInstance()->GetView()->SetRenderTargetClearColor(clearColor[0], clearColor[1], clearColor[2]);
    }
}

csmUint32 LAppLive2DManager::GetModelNum() const
{
    return _models.GetSize();
}

void LAppLive2DManager::SetViewMatrix(CubismMatrix44* m)
{
    std::lock_guard<std::mutex> lock(_managerMutex);
    for (int i = 0; i < 16; i++) {
        _viewMatrix->GetArray()[i] = m->GetArray()[i];
    }
}

void LAppLive2DManager::SetUpperBodyMode(bool enabled)
{
    std::lock_guard<std::mutex> lock(_managerMutex);
    _upperBodyMode = enabled;
}

bool LAppLive2DManager::IsMotionFinished() const
{
    std::lock_guard<std::mutex> lock(_managerMutex);
    if (_models.GetSize() == 0)
    {
        return true;
    }

    LAppModel* model = _models[0];
    if (model == NULL)
    {
        return true;
    }

    return model->IsMotionFinished();
}

void LAppLive2DManager::ReloadAllRenderers() const
{
    std::lock_guard<std::mutex> lock(_managerMutex);
    for (Csm::csmUint32 i = 0; i < _models.GetSize(); i++)
    {
        LAppModel* model = _models[i];
        if (model != NULL)
        {
            model->ReloadRenderer();
        }
    }
}

void LAppLive2DManager::StartMotion(const csmChar* group, csmInt32 index, csmInt32 priority)
{
    std::lock_guard<std::mutex> lock(_managerMutex);
    for (csmUint32 i = 0; i < _models.GetSize(); ++i)
    {
        if (_models[i] != NULL)
        {
            _models[i]->StartMotion(group, index, priority, FinishedMotion, BeganMotion);
        }
    }
}

void LAppLive2DManager::StartRandomMotion(const csmChar* group, csmInt32 priority)
{
    std::lock_guard<std::mutex> lock(_managerMutex);
    for (csmUint32 i = 0; i < _models.GetSize(); ++i)
    {
        if (_models[i] != NULL)
        {
            _models[i]->StartRandomMotion(group, priority, FinishedMotion, BeganMotion);
        }
    }
}

void LAppLive2DManager::StartMotionByPath(const csmChar* motionPath, csmInt32 priority)
{
    std::lock_guard<std::mutex> lock(_managerMutex);
    for (csmUint32 i = 0; i < _models.GetSize(); ++i)
    {
        if (_models[i] != NULL)
        {
            _models[i]->StartMotionByPath(motionPath, priority, FinishedMotion, BeganMotion);
        }
    }
}

void LAppLive2DManager::PreloadMotionByPath(const csmChar* motionPath)
{
    std::lock_guard<std::mutex> lock(_managerMutex);
    for (csmUint32 i = 0; i < _models.GetSize(); ++i)
    {
        if (_models[i] != NULL)
        {
            _models[i]->PreloadMotionByPath(motionPath);
        }
    }
}
