plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

import java.util.Properties
import java.io.FileInputStream

// 从 local.properties（不进 git）读取高德 Web 服务 Key，用于静态地图封面。
// 未配置时为空串，封面降级为占位图。
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) FileInputStream(f).use { load(it) }
}
val amapWebKey: String = localProps.getProperty("AMAP_WEB_KEY", "")

android {
    namespace = "com.example.scenic_avatar_guide_app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.scenic_avatar_guide_app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 高德 Web 服务 Key（静态地图 REST API）。空串表示未配置。
        buildConfigField("String", "AMAP_WEB_KEY", "\"$amapWebKey\"")

        // NDK 配置
        ndk {
            abiFilters += listOf("arm64-v8a", "x86", "x86_64")
        }

        // 指定 NDK 版本（Gradle 会自动下载）
        ndkVersion = "27.0.12077973"
    }

    // 指定 NDK 版本（需要在 Android Studio 中安装）
    // ndkVersion = "27.0.12077973"

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    // NDK CMake 配置
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Network
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // 讯飞语音识别 SDK
    implementation(files("libs/SparkChain.aar"))
    implementation(files("libs/Codec.aar"))

    // 高德地图 SDK（3D 地图已内置定位能力）
    implementation(libs.amap.map3d)
    // 高德搜索 SDK（POI 关键字检索 / 周边搜索）
    implementation(libs.amap.search)

    // ExoPlayer 音频播放
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    // Image loading
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Pinyin conversion for Chinese lip sync
    implementation("com.belerweb:pinyin4j:2.5.1")

    // Markdown 渲染
    implementation("com.mikepenz:multiplatform-markdown-renderer-android:0.26.0")
    implementation("com.mikepenz:multiplatform-markdown-renderer-m3:0.26.0")

    // Lottie animation
    implementation(libs.lottie.compose)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
