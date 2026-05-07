/**
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at https://www.live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

package com.example.scenic_avatar_guide_app.core.avatar;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class JniBridgeJava {
    /**
     * Live2D 资源缓存结构版本。
     * 修改大资源或需要强制刷新全部 Live2D 缓存时 bump 这里。
     */
    private static final int LIVE2D_ASSET_CACHE_REVISION = 3;

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
        // 优先从内部存储读取（已缓存的文件，跳过 APK 解压开销）
        if (context != null) {
            File cachedFile = new File(context.getFilesDir(), filePath);
            if (cachedFile.isFile() && cachedFile.length() > 0) {
                try (FileInputStream fis = new FileInputStream(cachedFile)) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream((int) cachedFile.length());
                    byte[] chunk = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = fis.read(chunk)) != -1) {
                        out.write(chunk, 0, bytesRead);
                    }
                    return out.toByteArray();
                } catch (IOException e) {
                    // 读取失败则降级到 assets
                }
            }
        }

        // 降级：从 APK assets 读取（available() 仅返回估算值，必须循环读完）
        try (InputStream fileData = context.getAssets().open(filePath)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int bytesRead;
            while ((bytesRead = fileData.read(chunk)) != -1) {
                out.write(chunk, 0, bytesRead);
            }
            return out.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 将模型资源从 APK assets 提取到内部存储，加快后续启动速度并提高稳定性。
     * 已提取且版本一致时直接返回（幂等）。应在 IO 线程调用。
     */
    public static synchronized void extractModelAssets() {
        if (context == null) return;

        SharedPreferences prefs = context.getSharedPreferences("live2d_asset_cache", Context.MODE_PRIVATE);
        int cachedVersion = prefs.getInt("version", -1);
        int currentVersion = getLive2DAssetCacheVersion();

        File live2dDir = new File(context.getFilesDir(), "live2d");
        if (cachedVersion == currentVersion && live2dDir.exists() && !isAppDebuggable()) {
            return;
        }

        try {
            copyAssetDirectory("live2d", live2dDir);
            prefs.edit().putInt("version", currentVersion).apply();
        } catch (IOException e) {
            e.printStackTrace();
            // 提取失败时静默降级，后续 LoadFile 仍会从 assets 读取
        }
    }

    private static int getLive2DAssetCacheVersion() {
        try {
            int appVersionCode = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionCode;
            return appVersionCode * 1000 + LIVE2D_ASSET_CACHE_REVISION;
        } catch (Exception e) {
            return LIVE2D_ASSET_CACHE_REVISION;
        }
    }

    private static boolean isAppDebuggable() {
        return context != null
                && (context.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    private static void copyAssetDirectory(String assetPath, File destPath) throws IOException {
        String[] children = context.getAssets().list(assetPath);
        if (children != null && children.length > 0) {
            // 目录：递归处理
            if (!destPath.exists() && !destPath.mkdirs()) {
                throw new IOException("Cannot create directory: " + destPath);
            }
            for (String child : children) {
                copyAssetDirectory(assetPath + "/" + child, new File(destPath, child));
            }
        } else {
            // 大资源已存在则跳过；JSON/EXP3/MOTION3 等文本资产需要允许内容变更覆盖。
            if (destPath.isFile() && destPath.length() > 0 && !shouldRefreshTextAsset(assetPath, destPath)) {
                return;
            }
            File parent = destPath.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (InputStream in = context.getAssets().open(assetPath);
                 FileOutputStream out = new FileOutputStream(destPath)) {
                byte[] buffer = new byte[16384];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
        }
    }

    private static boolean shouldRefreshTextAsset(String assetPath, File destPath) {
        String lower = assetPath.toLowerCase();
        if (!(lower.endsWith(".json") || lower.endsWith(".exp3.json") || lower.endsWith(".motion3.json"))) {
            return false;
        }

        try (InputStream in = context.getAssets().open(assetPath);
             FileInputStream cached = new FileInputStream(destPath)) {
            byte[] assetBuffer = new byte[8192];
            byte[] cachedBuffer = new byte[8192];
            while (true) {
                int assetRead = in.read(assetBuffer);
                int cachedRead = cached.read(cachedBuffer);
                if (assetRead != cachedRead) {
                    return true;
                }
                if (assetRead == -1) {
                    return false;
                }
                for (int i = 0; i < assetRead; i++) {
                    if (assetBuffer[i] != cachedBuffer[i]) {
                        return true;
                    }
                }
            }
        } catch (IOException e) {
            return true;
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
