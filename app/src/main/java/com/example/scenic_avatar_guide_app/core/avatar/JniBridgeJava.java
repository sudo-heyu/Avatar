/**
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at https://www.live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

package com.example.scenic_avatar_guide_app.core.avatar;

import android.app.Activity;
import android.content.Context;

import java.io.IOException;
import java.io.InputStream;

public class JniBridgeJava {
    // Native -----------------------------------------------------------------

    public static native void nativeOnStart();

    public static native void nativeOnPause();

    public static native void nativeOnStop();

    public static native void nativeOnDestroy();

    public static native void nativeOnSurfaceCreated();

    public static native void nativeOnSurfaceChanged(int width, int height);

    public static native void nativeOnDrawFrame();

    public static native void nativeOnTouchesBegan(float pointX, float pointY);

    public static native void nativeOnTouchesEnded(float pointX, float pointY);

    public static native void nativeOnTouchesMoved(float pointX, float pointY);

    public static native void nativeSetParameter(String parameterId, float value, float weight);

    public static native void nativeSetExpression(String expressionId);

    public static native void nativeSetUpperBodyMode(boolean enabled);

    /**
     * 播放指定动作
     * @param group 动作组名（如 "Idle", "TapBody"）
     * @param index 动作索引
     * @param priority 优先级（1=Idle, 2=Normal, 3=Force）
     */
    public static native void nativeStartMotion(String group, int index, int priority);

    /**
     * 播放随机动作
     * @param group 动作组名
     * @param priority 优先级
     */
    public static native void nativeStartRandomMotion(String group, int priority);

    /**
     * 播放指定路径的动作文件
     * @param motionPath 动作文件路径（相对于 assets，如 "live2d/hiyori/motions/Hiyori_nod.motion3.json"）
     * @param priority 优先级
     */
    public static native void nativeStartMotionByPath(String motionPath, int priority);

    /**
     * 预加载动作文件到缓存
     * 在模型初始化后调用，避免首次播放时的延迟
     * @param motionPath 动作文件路径（相对于 assets）
     */
    public static native void nativePreloadMotionByPath(String motionPath);

    /**
     * 查询当前动作是否已播放完毕
     * @return true = 无动作在播放（已结束），false = 动作播放中
     */
    public static native boolean nativeIsMotionFinished();

    // Java -----------------------------------------------------------------

    public static void SetContext(Context context) {
        JniBridgeJava.context = context;
    }

    public static void SetActivityInstance(Activity activity) {
        activityInstance = activity;
    }

    public static String[] GetAssetList(String dirPath) {
        try {
            return context.getAssets().list(dirPath);
        } catch (IOException e) {
            e.printStackTrace();
            return new String[0];
        }
    }

    public static byte[] LoadFile(String filePath) {
        InputStream fileData = null;
        try {
            fileData = context.getAssets().open(filePath);
            int fileSize = fileData.available();
            byte[] fileBuffer = new byte[fileSize];
            fileData.read(fileBuffer, 0, fileSize);
            return fileBuffer;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            try {
                if (fileData != null) {
                    fileData.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void MoveTaskToBack() {
        if (activityInstance != null) {
            activityInstance.moveTaskToBack(true);
        }
    }

    private static Activity activityInstance;
    private static Context context;
    private static final String LIBRARY_NAME = "Demo";

    static {
        System.loadLibrary(LIBRARY_NAME);
    }
}
