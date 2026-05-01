package com.example.scenic_avatar_guide_app.core.avatar

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Live2D 依赖注入模块
 */
@Module
@InstallIn(SingletonComponent::class)
object Live2DModule {

    @Provides
    @Singleton
    fun provideLive2DRenderer(
        @ApplicationContext context: Context
    ): Live2DRendererImpl {
        return Live2DRendererImpl(context)
    }

    @Provides
    @Singleton
    fun provideAvatarController(
        @ApplicationContext context: Context
    ): AvatarController {
        return AvatarController(context)
    }
}
