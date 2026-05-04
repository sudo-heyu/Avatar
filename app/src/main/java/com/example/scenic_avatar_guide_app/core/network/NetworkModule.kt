package com.example.scenic_avatar_guide_app.core.network

import com.example.scenic_avatar_guide_app.data.local.SettingsDataStore
import com.example.scenic_avatar_guide_app.data.remote.ApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class StreamingOkHttp

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // 预设环境地址
    const val DEVICE_LOCAL = "http://192.168.1.100:8000/"         // 真机调试（需改成本机IP）

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        isLenient = true
        prettyPrint = false
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(settingsDataStore: SettingsDataStore): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(DynamicBaseUrlInterceptor(settingsDataStore))
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }
            )
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @StreamingOkHttp
    fun provideStreamingOkHttpClient(settingsDataStore: SettingsDataStore): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(DynamicBaseUrlInterceptor(settingsDataStore))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        json: Json,
        okHttpClient: OkHttpClient
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(SettingsDataStore.DEFAULT_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService {
        return retrofit.create(ApiService::class.java)
    }

    private class DynamicBaseUrlInterceptor(
        private val settingsDataStore: SettingsDataStore
    ) : Interceptor {
        // 首次请求后缓存，避免后续每次请求都 runBlocking
        @Volatile
        private var resolvedUrl: HttpUrl? = null

        override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
            val request = chain.request()
            val configuredBaseUrl = resolvedUrl
                ?: runBlocking { settingsDataStore.baseUrl.first() }
                    .toHttpUrlOrNull()
                    ?.also { resolvedUrl = it }

            if (configuredBaseUrl == null) {
                return chain.proceed(request)
            }

            val newUrl = request.url.newBuilder()
                .scheme(configuredBaseUrl.scheme)
                .host(configuredBaseUrl.host)
                .port(configuredBaseUrl.port)
                .build()

            android.util.Log.d("DynamicBaseUrl", "URL替换: ${request.url} -> $newUrl")

            return chain.proceed(request.newBuilder().url(newUrl).build())
        }
    }
}
