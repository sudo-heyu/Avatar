/**
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at https://www.live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

#pragma once

#include <CubismFramework.hpp>
#include <Math/CubismMatrix44.hpp>
#include <Type/csmVector.hpp>
#include <Type/csmString.hpp>
#include <mutex>

class LAppModel;

/**
* @brief サンプルアプリケーションにおいてCubismModelを管理するクラス<br>
*         モデル生成と破棄、タップイベントの処理、モデル切り替えを行う。
*
*/
class LAppLive2DManager
{

public:
    /**
    * @brief   クラスのインスタンス（シングルトン）を返す。<br>
    *           インスタンスが生成されていない場合は内部でインスタンを生成する。
    *
    * @return  クラスのインスタンス
    */
    static LAppLive2DManager* GetInstance();

    /**
    * @brief   クラスのインスタンス（シングルトン）を解放する。
    *
    */
    static void ReleaseInstance();

    /**
    * @brief   Resources フォルダにあるモデルフォルダ名をセットする
    *
    */
    void SetUpModel();

    /**
    * @brief   現在のシーンで保持しているモデルを返す
    *
    * @param[in]   no  モデルリストのインデックス値
    * @return      モデルのインスタンスを返す。インデックス値が範囲外の場合はNULLを返す。
    */
    LAppModel* GetModel(Csm::csmUint32 no) const;

    /**
    * @brief   モデルのオフスクリーンのサイズを設定
    *
    * @param[in]   width   ウインドウの幅
    * @param[in]   height  ウインドウの高さ
    */
    void SetRenderTargetSize(Csm::csmUint32 width, Csm::csmUint32 height);

    /**
    * @brief   現在のシーンで保持しているすべてのモデルを解放する
    *
    */
    void ReleaseAllModel();

    /**
    * @brief   画面をドラッグしたときの処理
    *
    * @param[in]   x   画面のX座標
    * @param[in]   y   画面のY座標
    */
    void OnDrag(Csm::csmFloat32 x, Csm::csmFloat32 y) const;

    /**
    * @brief   画面をタップしたときの処理
    *
    * @param[in]   x   画面のX座標
    * @param[in]   y   画面のY座標
    */
    void OnTap(Csm::csmFloat32 x, Csm::csmFloat32 y);

    /**
    * @brief   画面を更新するときの処理
    *          モデルの更新処理および描画処理を行う
    */
    void OnUpdate() const;

    /**
     * @brief   向当前模型下发参数
     */
    void SetParameter(const Csm::csmChar* parameterId, Csm::csmFloat32 value, Csm::csmFloat32 weight);

    /**
     * @brief   播放指定表情
     */
    void SetExpression(const Csm::csmChar* expressionId);

    /**
     * @brief   播放指定动作
     * @param group 动作组名
     * @param index 动作索引
     * @param priority 优先级
     */
    void StartMotion(const Csm::csmChar* group, Csm::csmInt32 index, Csm::csmInt32 priority);

    /**
     * @brief   播放随机动作
     * @param group 动作组名
     * @param priority 优先级
     */
    void StartRandomMotion(const Csm::csmChar* group, Csm::csmInt32 priority);

    /**
     * @brief   按文件路径播放动作（动态加载）
     * @param motionPath 动作文件路径（相对于 assets）
     * @param priority 优先级
     */
    void StartMotionByPath(const Csm::csmChar* motionPath, Csm::csmInt32 priority);

    /**
     * @brief   预加载动作文件
     *          在模型初始化后调用，避免首次播放时的延迟
     * @param motionPath 动作文件路径（相对于 assets）
     */
    void PreloadMotionByPath(const Csm::csmChar* motionPath);

    /**
     * @brief   次のシーンに切り替える<br>
    *           サンプルアプリケーションではモデルセットの切り替えを行う。
    */
    void NextScene();

    /**
    * @brief   シーンを切り替える<br>
    *           サンプルアプリケーションではモデルセットの切り替えを行う。
    */
    void ChangeScene(Csm::csmInt32 index);

    /**
     * @brief   モデル個数を得る
     * @return  所持モデル個数
     */
    Csm::csmUint32 GetModelNum() const;

    /**
     * @brief   viewMatrixをセットする
     */
    void SetViewMatrix(Live2D::Cubism::Framework::CubismMatrix44* m);

    /**
     * @brief   上半身のみ表示モードを設定
     */
    void SetUpperBodyMode(bool enabled);

    /**
     * @brief   現在のモーション再生が終了しているかを判定
     * @return  終了していれば true、再生中なら false
     */
    bool IsMotionFinished() const;

    /**
     * @brief   安全地重新加载所有模型的渲染器（持有 _managerMutex）
     */
    void ReloadAllRenderers() const;

private:
    /**
    * @brief  コンストラクタ
    */
    LAppLive2DManager();

    /**
    * @brief  デストラクタ
    */
    virtual ~LAppLive2DManager();

    Csm::CubismMatrix44*        _viewMatrix; ///< モデル描画に用いるView行列
    Csm::csmVector<LAppModel*>  _models; ///< モデルインスタンスのコンテナ

    Csm::csmVector<Csm::csmString> _modelDir; ///< モデルディレクトリ名のコンテナ

    bool _upperBodyMode; ///< 上半身のみ表示モード

    mutable std::mutex _managerMutex; ///< 保护模型状态跨线程访问的互斥锁
};
