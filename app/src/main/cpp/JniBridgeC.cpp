/**
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at https://www.live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

#include "JniBridgeC.hpp"
#include <algorithm>
#include <atomic>
#include <mutex>
#include <jni.h>
#include "LAppDelegate.hpp"
#include "LAppPal.hpp"
#include "LAppLive2DManager.hpp"
#include "CubismFramework.hpp"

using namespace Csm;

static JavaVM* g_JVM; // JavaVM is valid for all threads, so just save it globally
static bool s_upperBodyModePending = false;
// Serializes all C++ singleton access across GL threads. Prevents data races when
// the user rapidly opens/closes the app, causing a new GL thread to start before
// the old one fully stops (crash site: CubismPhysics::Evaluate()).
static std::mutex s_renderMutex;
// 全局销毁标志：阻止 nativeOnDestroy() 后的所有 Native 调用
// 解决核心崩溃：GL 线程在 onDestroy 后仍可能调用 nativeOnDrawFrame，
// 导致 LAppDelegate 被重建并访问已销毁的 GL 资源
static std::atomic<bool> s_isDestroyed{false};
static jclass  g_JniBridgeJavaClass;
static jmethodID g_GetAssetsMethodId;
static jmethodID g_LoadFileMethodId;
static jmethodID g_MoveTaskToBackMethodId;

JNIEnv* GetEnv()
{
    JNIEnv* env = NULL;
    g_JVM->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
    return env;
}

// The VM calls JNI_OnLoad when the native library is loaded
jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved)
{
    g_JVM = vm;

    JNIEnv *env;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK)
    {
        return JNI_ERR;
    }

    jclass clazz = env->FindClass("com/example/scenic_avatar_guide_app/core/avatar/JniBridgeJava");
    g_JniBridgeJavaClass = reinterpret_cast<jclass>(env->NewGlobalRef(clazz));
    g_GetAssetsMethodId = env->GetStaticMethodID(g_JniBridgeJavaClass, "GetAssetList", "(Ljava/lang/String;)[Ljava/lang/String;");
    g_LoadFileMethodId = env->GetStaticMethodID(g_JniBridgeJavaClass, "LoadFile", "(Ljava/lang/String;)[B");
    g_MoveTaskToBackMethodId = env->GetStaticMethodID(g_JniBridgeJavaClass, "MoveTaskToBack", "()V");

    return JNI_VERSION_1_6;
}

void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved)
{
    JNIEnv *env = GetEnv();
    env->DeleteGlobalRef(g_JniBridgeJavaClass);
}

Csm::csmVector<Csm::csmString>JniBridgeC::GetAssetList(const Csm::csmString& path)
{
    JNIEnv *env = GetEnv();
    if (env == nullptr) return Csm::csmVector<Csm::csmString>();

    jstring jPath = env->NewStringUTF(path.GetRawString());
    jobjectArray obj = reinterpret_cast<jobjectArray>(env->CallStaticObjectMethod(g_JniBridgeJavaClass, g_GetAssetsMethodId, jPath));
    env->DeleteLocalRef(jPath);

    if (obj == nullptr) return Csm::csmVector<Csm::csmString>();

    unsigned int size = static_cast<unsigned int>(env->GetArrayLength(obj));
    Csm::csmVector<Csm::csmString> list(size);
    for (unsigned int i = 0; i < size; i++)
    {
        jstring jstr = reinterpret_cast<jstring>(env->GetObjectArrayElement(obj, i));
        const char* chars = env->GetStringUTFChars(jstr, nullptr);
        list.PushBack(Csm::csmString(chars));
        env->ReleaseStringUTFChars(jstr, chars);
        env->DeleteLocalRef(jstr);
    }
    env->DeleteLocalRef(obj);
    return list;
}

char* JniBridgeC::LoadFileAsBytesFromJava(const char* filePath, unsigned int* outSize)
{
    JNIEnv *env = GetEnv();
    if (env == nullptr) return nullptr;

    jstring jPath = env->NewStringUTF(filePath);
    jbyteArray obj = (jbyteArray)env->CallStaticObjectMethod(g_JniBridgeJavaClass, g_LoadFileMethodId, jPath);
    env->DeleteLocalRef(jPath);

    // ファイルが見つからなかったらnullが返ってくるためチェック
    if (!obj)
    {
        return NULL;
    }

    *outSize = static_cast<unsigned int>(env->GetArrayLength(obj));

    char* buffer = new char[*outSize];
    env->GetByteArrayRegion(obj, 0, *outSize, reinterpret_cast<jbyte *>(buffer));
    env->DeleteLocalRef(obj);

    return buffer;
}

void JniBridgeC::MoveTaskToBack()
{
    JNIEnv *env = GetEnv();

    // アプリ終了
    env->CallStaticVoidMethod(g_JniBridgeJavaClass, g_MoveTaskToBackMethodId, NULL);
}

extern "C"
{
    JNIEXPORT void JNICALL
    Java_com_example_scenic_1avatar_1guide_1app_core_avatar_JniBridgeJava_nativeOnStart(JNIEnv *env, jclass type)
    {
        std::lock_guard<std::mutex> lock(s_renderMutex);
        // OnStart 不清除销毁标志，只有 OnSurfaceCreated 才能清除
        // 防止在 OnDestroy 后通过 OnStart 重新激活
        if (s_isDestroyed.load(std::memory_order_relaxed)) {
            return;
        }
        LAppDelegate::GetInstance()->OnStart();
    }

    JNIEXPORT void JNICALL
    Java_com_example_scenic_1avatar_1guide_1app_core_avatar_JniBridgeJava_nativeOnPause(JNIEnv *env, jclass type)
    {
        std::lock_guard<std::mutex> lock(s_renderMutex);
        if (s_isDestroyed.load(std::memory_order_relaxed)) {
            return;
        }
        LAppDelegate::GetInstance()->OnPause();
    }

    JNIEXPORT void JNICALL
    Java_com_example_scenic_1avatar_1guide_1app_core_avatar_JniBridgeJava_nativeOnStop(JNIEnv *env, jclass type)
    {
        std::lock_guard<std::mutex> lock(s_renderMutex);
        // 在 OnStop 时也设置销毁标志，防止后续调用
        s_isDestroyed.store(true, std::memory_order_release);
        LAppDelegate::GetInstance()->OnStop();
    }

    JNIEXPORT void JNICALL
    Java_com_example_scenic_1avatar_1guide_1app_core_avatar_JniBridgeJava_nativeOnDestroy(JNIEnv *env, jclass type)
    {
        std::lock_guard<std::mutex> lock(s_renderMutex);
        s_isDestroyed.store(true, std::memory_order_release);
        LAppDelegate::GetInstance()->OnDestroy();
    }

    JNIEXPORT void JNICALL
    Java_com_example_scenic_1avatar_1guide_1app_core_avatar_JniBridgeJava_nativeOnSurfaceCreated(JNIEnv *env, jclass type)
    {
        std::lock_guard<std::mutex> lock(s_renderMutex);
        // 重新初始化时清除销毁标志
        s_isDestroyed.store(false, std::memory_order_release);
        LAppDelegate::GetInstance()->OnSurfaceCreate();
        if (s_upperBodyModePending)
        {
            LAppLive2DManager::GetInstance()->SetUpperBodyMode(true);
        }
    }

    JNIEXPORT void JNICALL
    Java_com_example_scenic_1avatar_1guide_1app_core_avatar_JniBridgeJava_nativeOnSurfaceChanged(JNIEnv *env, jclass type, jint width, jint height)
    {
        std::lock_guard<std::mutex> lock(s_renderMutex);
        if (s_isDestroyed.load(std::memory_order_relaxed)) return;
        LAppDelegate::GetInstance()->OnSurfaceChanged(width, height);
    }

    JNIEXPORT void JNICALL
    Java_com_example_scenic_1avatar_1guide_1app_core_avatar_JniBridgeJava_nativeOnDrawFrame(JNIEnv *env, jclass type)
    {
        // 快速路径：销毁后直接返回，不获取锁
        if (s_isDestroyed.load(std::memory_order_acquire)) {
            return;
        }
        std::lock_guard<std::mutex> lock(s_renderMutex);
        // 双重检查：锁内再次确认（防止在等待锁时被销毁）
        if (s_isDestroyed.load(std::memory_order_relaxed)) {
            return;
        }
        LAppDelegate::GetInstance()->Run();
    }

    JNIEXPORT void JNICALL
    Java_com_example_scenic_1avatar_1guide_1app_core_avatar_JniBridgeJava_nativeOnTouchesBegan(JNIEnv *env, jclass type, jfloat pointX, jfloat pointY)
    {
        if (s_isDestroyed.load(std::memory_order_acquire)) return;
        std::lock_guard<std::mutex> lock(s_renderMutex);
        if (s_isDestroyed.load(std::memory_order_relaxed)) return;
        LAppDelegate::GetInstance()->OnTouchBegan(pointX, pointY);
    }

    JNIEXPORT void JNICALL
    Java_com_example_scenic_1avatar_1guide_1app_core_avatar_JniBridgeJava_nativeOnTouchesEnded(JNIEnv *env, jclass type, jfloat pointX, jfloat pointY)
    {
        if (s_isDestroyed.load(std::memory_order_acquire)) return;
        std::lock_guard<std::mutex> lock(s_renderMutex);
        if (s_isDestroyed.load(std::memory_order_relaxed)) return;
        LAppDelegate::GetInstance()->OnTouchEnded(pointX, pointY);
    }

    JNIEXPORT void JNICALL
    Java_com_example_scenic_1avatar_1guide_1app_core_avatar_JniBridgeJava_nativeOnTouchesMoved(JNIEnv *env, jclass type, jfloat pointX, jfloat pointY)
    {
        if (s_isDestroyed.load(std::memory_order_acquire)) return;
        std::lock_guard<std::mutex> lock(s_renderMutex);
        if (s_isDestroyed.load(std::memory_order_relaxed)) return;
        LAppDelegate::GetInstance()->OnTouchMoved(pointX, pointY);
    }

    JNIEXPORT void JNICALL
    Java_com_example_scenic_1avatar_1guide_1app_core_avatar_JniBridgeJava_nativeSetParameter(JNIEnv *env, jclass type, jstring parameterId, jfloat value, jfloat weight)
    {
        // 快速路径：销毁后直接返回
        if (s_isDestroyed.load(std::memory_order_acquire)) {
            return;
        }
        if (parameterId == NULL)
        {
            return;
        }

        // 防止在 CubismFramework 初始化前被调用导致空指针崩溃
        if (!CubismFramework::IsInitialized())
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

        std::lock_guard<std::mutex> lock(s_renderMutex);
        // 双重检查：锁内再次确认
        if (s_isDestroyed.load(std::memory_order_relaxed)) {
            return;
        }
        const char* rawParameterId = env->GetStringUTFChars(parameterId, nullptr);
        LAppLive2DManager::GetInstance()->SetParameter(rawParameterId, value, weight);
        env->ReleaseStringUTFChars(parameterId, rawParameterId);
    }

    JNIEXPORT void JNICALL
    Java_com_example_scenic_1avatar_1guide_1app_core_avatar_JniBridgeJava_nativeSetExpression(JNIEnv *env, jclass type, jstring expressionId)
    {
        // 快速路径：销毁后直接返回
        if (s_isDestroyed.load(std::memory_order_acquire)) {
            return;
        }
        if (expressionId == NULL || !CubismFramework::IsInitialized())
        {
            return;
        }
        std::lock_guard<std::mutex> lock(s_renderMutex);
        // 双重检查：锁内再次确认
        if (s_isDestroyed.load(std::memory_order_relaxed)) {
            return;
        }
        const char* rawExpressionId = env->GetStringUTFChars(expressionId, nullptr);
        LAppLive2DManager::GetInstance()->SetExpression(rawExpressionId);
        env->ReleaseStringUTFChars(expressionId, rawExpressionId);
    }

    JNIEXPORT void JNICALL
    Java_com_example_scenic_1avatar_1guide_1app_core_avatar_JniBridgeJava_nativeSetUpperBodyMode(JNIEnv *env, jclass type, jboolean enabled)
    {
        s_upperBodyModePending = (enabled == JNI_TRUE);
        if (s_isDestroyed.load(std::memory_order_acquire)) return;
        if (CubismFramework::IsInitialized())
        {
            std::lock_guard<std::mutex> lock(s_renderMutex);
            if (s_isDestroyed.load(std::memory_order_relaxed)) return;
            LAppLive2DManager::GetInstance()->SetUpperBodyMode(s_upperBodyModePending);
        }
    }

    JNIEXPORT void JNICALL
    Java_com_example_scenic_1avatar_1guide_1app_core_avatar_JniBridgeJava_nativeStartMotion(JNIEnv *env, jclass type, jstring group, jint index, jint priority)
    {
        // Disabled for stability: runtime gestures are parameter-driven from Kotlin.
    }

    JNIEXPORT void JNICALL
    Java_com_example_scenic_1avatar_1guide_1app_core_avatar_JniBridgeJava_nativeStartRandomMotion(JNIEnv *env, jclass type, jstring group, jint priority)
    {
        // Disabled for stability: runtime gestures are parameter-driven from Kotlin.
    }

    JNIEXPORT void JNICALL
    Java_com_example_scenic_1avatar_1guide_1app_core_avatar_JniBridgeJava_nativeStartMotionByPath(JNIEnv *env, jclass type, jstring motionPath, jint priority)
    {
        // Disabled for stability: runtime gestures are parameter-driven from Kotlin.
    }

    JNIEXPORT void JNICALL
    Java_com_example_scenic_1avatar_1guide_1app_core_avatar_JniBridgeJava_nativePreloadMotionByPath(JNIEnv *env, jclass type, jstring motionPath)
    {
        // Disabled for stability: runtime gestures are parameter-driven from Kotlin.
    }

    JNIEXPORT jboolean JNICALL
    Java_com_example_scenic_1avatar_1guide_1app_core_avatar_JniBridgeJava_nativeIsMotionFinished(JNIEnv *env, jclass type)
    {
        if (s_isDestroyed.load(std::memory_order_acquire)) return JNI_TRUE;
        if (!CubismFramework::IsInitialized())
        {
            return JNI_TRUE;
        }
        std::lock_guard<std::mutex> lock(s_renderMutex);
        if (s_isDestroyed.load(std::memory_order_relaxed)) return JNI_TRUE;
        return LAppLive2DManager::GetInstance()->IsMotionFinished() ? JNI_TRUE : JNI_FALSE;
    }
}
