package com.example.scenic_avatar_guide_app

import android.app.Application
import android.util.Log
import com.iflytek.sparkchain.core.SparkChain
import com.iflytek.sparkchain.core.SparkChainConfig
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class GuideApplication : Application() {

    companion object {
        private const val TAG = "GuideApplication"

        // 讯飞 SDK 配置
        private const val APP_ID = "45c1bb12"
        private const val API_KEY = "448d28c3ee63c5db30686c927f7b15bd"
        private const val API_SECRET = "ZTMwZDAxMjkzYzg5MWMwYjBmYTZjMWVh"
    }

    override fun onCreate() {
        super.onCreate()
        // 初始化讯飞语音 SDK
        initSparkChain()
    }

    private fun initSparkChain() {
        try {
            val config = SparkChainConfig.builder()
                .appID(APP_ID)
                .apiKey(API_KEY)
                .apiSecret(API_SECRET)

            val ret = SparkChain.getInst().init(applicationContext, config)
            if (ret == 0) {
                Log.d(TAG, "讯飞 SDK 初始化成功")
            } else {
                Log.e(TAG, "讯飞 SDK 初始化失败，错误码: $ret")
            }
        } catch (e: Exception) {
            Log.e(TAG, "讯飞 SDK 初始化异常: ${e.message}", e)
        }
    }
}
