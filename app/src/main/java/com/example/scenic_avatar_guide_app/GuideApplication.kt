package com.example.scenic_avatar_guide_app

import android.app.Application
import android.util.Log
import com.amap.api.location.AMapLocationClient
import com.amap.api.maps.MapsInitializer
import com.amap.api.services.core.ServiceSettings
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
        // 高德隐私合规：在进程启动时声明「应用已展示隐私政策，并包含高德政策」。
        // 地图 SDK 与定位 SDK 各需调用一次，时机早于任何地图/定位 API 触发网络。
        initAmapPrivacy()
    }

    private fun initAmapPrivacy() {
        try {
            MapsInitializer.updatePrivacyShow(applicationContext, true, true)
            AMapLocationClient.updatePrivacyShow(applicationContext, true, true)
            // 搜索 SDK（PoiSearch 等）独立声明隐私政策，否则调用 PoiSearch 会崩溃
            ServiceSettings.updatePrivacyShow(applicationContext, true, true)
        } catch (e: Exception) {
            Log.e(TAG, "高德隐私声明初始化异常: ${e.message}", e)
        }
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
