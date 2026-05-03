/**
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at https://www.live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

#include "JniBridgeC.hpp"
#include <algorithm>
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
        LAppDelegate::GetInstance()->OnStart();
    }

    JNIEXPORT void JNICALL
    Java_com_example_scenic_1avatar_1guide_1app_core_avatar_JniBridgeJava_nativeOnPause(JNIEnv *env, jclass type)
    {
        std::lock_guard<std::mutex> lock(s_renderMutex);
        LAppDelegate::GetInstance()->OnPause();
    }

    JNIEXPORT void JNICALL
    Java_com_example_scenic_1avatar_1guide_1app_core_avatar_JniBridgeJava_nativeOnStop(JNIEnv *env, jclass type)
    {
        std::lock_guard<std::mutex> lock(s_renderMutex);
        LAppDelegate::GetInstance()->OnStop();
    }

    JNIEXPORT void JNICALL
    Java_com_example_scenic_1avatar_1guide_1app_core_avatar_JniBridgeJava_nativeOnDestroy(JNIEnv *env, jclass type)
    {
        std::lock_guard<std::mutex> lock(s_renderMutex);
        LAppDelegate::GetInstance()->OnDestroy();
    }

    JNIEXPORT void JNICALL
    Java_com_example_scenic_1avatar_1guide_1app_core_avatar_JniBridgeJava_nativeOnSurfaceCreated(JNIEnv *env, jclass type)
    {
        std::lock_guard<std::mutex> lock(s_renderMutex);
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
        LAppDelegate::GetInstance()->OnSurfaceChanged(width, height);
    }

    JNIEXPORT void JNICALL
    Java_com_example_scenic_1avatar_1guide_1app_core_avatar_JniBridgeJava_nativeOnDrawFrame(JNIEnv *env, jclass type)
    {
        std::lock_guard<std::mutex> lock(s_renderMutex);
        LAppDelegate::GetInstance()->Run();
    }

    JNIEXPORT void JNICALL
    Java_com_example_scenic_1avatar_1guide_1app_core_avatar_JniBridgeJava_nativeOnTouchesBegan(JNIEnv *env, jclass type, jfloat pointX, jfloat pointY)
    {
        std::lock_guard<std::mutex> lock(s_renderMutex);
        LAppDelegate::GetInstance()->OnTouchBegan(pointX, pointY);
    }

    JNIEXPORT void JNICALL
    Java_com_example_scenic_1avatar_1guide_1app_core_avatar_JniBridgeJava_nativeOnTouchesEnded(JNIEnv *env, jclass type, jfloat pointX, jfloat pointY)
    {
        std::lock_guard<std::mutex> lock(s_renderMutex);
        LAppDelegate::GetInstance()->OnTouchEnded(pointX, pointY);
    }

    JNIEXPORT void JNICALL
    Java_com_example_scenic_1avatar_1guide_1app_core_avatar_JniBridgeJava_nativeOnTouchesMoved(JNIEnv *env, jclass type, jfloat pointX, jfloat pointY)
    {
        std::lock_guard<std::mutex> lock(s_renderMutex);
        LAppDelegate::GetInstance()->OnTouchMoved(pointX, pointY);
    }

    JNIEXPORT void JNICALL
    Java_com_example_scenic_1avatar_1guide_1app_core_avatar_JniBridgeJava_nativeSetParameter(JNIEnv *env, jclass type, jstring parameterId, jfloat value, jfloat weight)
    {
        if (parameterId == NULL)
        {
            return;
        }

        // 防止在 CubismFramework 初始化前被调用导致空指针崩溃
        if (!CubismFramework::IsInitialized())
        {
            return;
        }

        std::lock_guard<std::mutex> lock(s_renderMutex);
        const char* rawParameterId = env->GetStringUTFChars(parameterId, nullptr);
        LAppLive2DManager::GetInstance()->SetParameter(rawParameterId, value, weight);
        env->ReleaseStringUTFChars(parameterId, rawParameterId);
    }

    JNIEXPORT void JNICALL
    Java_com_example_scenic_1avatar_1guide_1app_core_avatar_JniBridgeJava_nativeSetExpression(JNIEnv *env, jclass type, jstring expressionId)
    {
        if (expressionId == NULL || !CubismFramework::IsInitialized())
        {
            return;
        }
        std::lock_guard<std::mutex> lock(s_renderMutex);
        const char* rawExpressionId = env->GetStringUTFChars(expressionId, nullptr);
        LAppLive2DManager::GetInstance()->SetExpression(rawExpressionId);
        env->ReleaseStringUTFChars(expressionId, rawExpressionId);
    }

    JNIEXPORT void JNICALL
    Java_com_example_scenic_1avatar_1guide_1app_core_avatar_JniBridgeJava_nativeSetUpperBodyMode(JNIEnv *env, jclass type, jboolean enabled)
    {
        s_upperBodyModePending = (enabled == JNI_TRUE);
        if (CubismFramework::IsInitialized())
        {
            std::lock_guard<std::mutex> lock(s_renderMutex);
            LAppLive2DManager::GetInstance()->SetUpperBodyMode(s_upperBodyModePending);
        }
    }

    JNIEXPORT void JNICALL
    Java_com_example_scenic_1avatar_1guide_1app_core_avatar_JniBridgeJava_nativeStartMotion(JNIEnv *env, jclass type, jstring group, jint index, jint priority)
    {
        if (group == NULL || !CubismFramework::IsInitialized())
        {
            return;
        }
        std::lock_guard<std::mutex> lock(s_renderMutex);
        const char* rawGroup = env->GetStringUTFChars(group, nullptr);
        LAppLive2DManager::GetInstance()->StartMotion(rawGroup, static_cast<Csm::csmInt32>(index), static_cast<Csm::csmInt32>(priority));
        env->ReleaseStringUTFChars(group, rawGroup);
    }

    JNIEXPORT void JNICALL
    Java_com_example_scenic_1avatar_1guide_1app_core_avatar_JniBridgeJava_nativeStartRandomMotion(JNIEnv *env, jclass type, jstring group, jint priority)
    {
        if (group == NULL || !CubismFramework::IsInitialized())
        {
            return;
        }
        std::lock_guard<std::mutex> lock(s_renderMutex);
        const char* rawGroup = env->GetStringUTFChars(group, nullptr);
        LAppLive2DManager::GetInstance()->StartRandomMotion(rawGroup, static_cast<Csm::csmInt32>(priority));
        env->ReleaseStringUTFChars(group, rawGroup);
    }

    JNIEXPORT void JNICALL
    Java_com_example_scenic_1avatar_1guide_1app_core_avatar_JniBridgeJava_nativeStartMotionByPath(JNIEnv *env, jclass type, jstring motionPath, jint priority)
    {
        if (motionPath == NULL || !CubismFramework::IsInitialized())
        {
            return;
        }
        std::lock_guard<std::mutex> lock(s_renderMutex);
        const char* rawPath = env->GetStringUTFChars(motionPath, nullptr);
        LAppLive2DManager::GetInstance()->StartMotionByPath(rawPath, static_cast<Csm::csmInt32>(priority));
        env->ReleaseStringUTFChars(motionPath, rawPath);
    }

    JNIEXPORT void JNICALL
    Java_com_example_scenic_1avatar_1guide_1app_core_avatar_JniBridgeJava_nativePreloadMotionByPath(JNIEnv *env, jclass type, jstring motionPath)
    {
        if (motionPath == NULL || !CubismFramework::IsInitialized())
        {
            return;
        }
        std::lock_guard<std::mutex> lock(s_renderMutex);
        const char* rawPath = env->GetStringUTFChars(motionPath, nullptr);
        LAppLive2DManager::GetInstance()->PreloadMotionByPath(rawPath);
        env->ReleaseStringUTFChars(motionPath, rawPath);
    }

    JNIEXPORT jboolean JNICALL
    Java_com_example_scenic_1avatar_1guide_1app_core_avatar_JniBridgeJava_nativeIsMotionFinished(JNIEnv *env, jclass type)
    {
        if (!CubismFramework::IsInitialized())
        {
            return JNI_TRUE;
        }
        std::lock_guard<std::mutex> lock(s_renderMutex);
        return LAppLive2DManager::GetInstance()->IsMotionFinished() ? JNI_TRUE : JNI_FALSE;
    }
}
