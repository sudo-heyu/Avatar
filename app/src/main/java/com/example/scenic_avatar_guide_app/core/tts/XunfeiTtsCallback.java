package com.example.scenic_avatar_guide_app.core.tts;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.iflytek.sparkchain.core.tts.TTS;
import com.iflytek.sparkchain.core.tts.TTSCallbacks;

/**
 * 讯飞 TTS 回调适配器
 * 实现 TTSCallbacks 接口以处理语音合成回调
 */
public class XunfeiTtsCallback implements TTSCallbacks {

    private static final String TAG = "XunfeiTtsCallback";

    @Override
    public void onResult(TTS.TTSResult result, Object usrTag) {
        if (result != null) {
            byte[] audio = result.getData();
            int status = result.getStatus();

            Log.d(TAG, "onResult: status=" + status + ", audioLen=" + (audio != null ? audio.length : 0));

            // 转发到主线程
            new Handler(Looper.getMainLooper()).post(() -> {
                onResult(result, usrTag);
            });
        }
    }

    @Override
    public void onError(TTS.TTSError error, Object usrTag) {
        if (error != null) {
            int code = error.getCode();
            String msg = error.getErrMsg();

            Log.e(TAG, "onError: code=" + code + ", msg=" + msg);

            new Handler(Looper.getMainLooper()).post(() -> {
                onError(error, usrTag);
            });
        }
    }
}
