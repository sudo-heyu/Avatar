package com.example.scenic_avatar_guide_app.core.speech;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.iflytek.sparkchain.core.asr.ASR;
import com.iflytek.sparkchain.core.asr.AsrCallbacks;

/**
 * 讯飞 ASR 回调适配器
 */
public class XunfeiAsrCallback implements AsrCallbacks {

    private static final String TAG = "XunfeiAsrCallback";

    private OnResultListener resultListener;
    private OnErrorListener errorListener;
    private Runnable onEndOfSpeech;

    public interface OnResultListener {
        void onResult(String text, int status);
    }

    public interface OnErrorListener {
        void onError(String message);
    }

    public void setOnResultListener(OnResultListener listener) {
        this.resultListener = listener;
    }

    public void setOnErrorListener(OnErrorListener listener) {
        this.errorListener = listener;
    }

    public void setOnEndOfSpeech(Runnable callback) {
        this.onEndOfSpeech = callback;
    }

    @Override
    public void onResult(ASR.ASRResult result, Object obj) {
        if (result != null) {
            String text = result.getBestMatchText();
            int status = result.getStatus();

            Log.d(TAG, "识别结果: " + text + ", status: " + status);

            // 只处理最终结果 (status == 2)
            if (status == 2 && resultListener != null) {
                String finalText = text != null ? text : "";
                new Handler(Looper.getMainLooper()).post(() -> {
                    resultListener.onResult(finalText, 2);
                });
            }
        }
    }

    @Override
    public void onError(ASR.ASRError error, Object obj) {
        if (error != null) {
            String msg = error.getErrMsg();
            int code = error.getCode();
            Log.e(TAG, "识别错误: " + msg + ", code: " + code);

            if (errorListener != null) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    errorListener.onError("识别错误: " + msg);
                });
            }
        }
    }

    @Override
    public void onBeginOfSpeech() {
        Log.d(TAG, "开始说话");
    }

    @Override
    public void onEndOfSpeech() {
        Log.d(TAG, "说话结束");
        if (onEndOfSpeech != null) {
            new Handler(Looper.getMainLooper()).post(onEndOfSpeech);
        }
    }
}
