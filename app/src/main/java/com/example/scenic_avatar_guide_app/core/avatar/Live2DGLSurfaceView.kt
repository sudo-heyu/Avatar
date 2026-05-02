package com.example.scenic_avatar_guide_app.core.avatar

import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.MotionEvent
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Live2D GLSurfaceView
 * 管理 OpenGL ES 渲染上下文和 Live2D 绘制
 */
class Live2DGLSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    private var isRendererSet = false

    /**
     * Surface 是否已创建（C++ CubismFramework 已初始化）
     */
    @Volatile
    var isSurfaceCreated = false
        private set

    /**
     * Surface 创建完成后的回调（用于延迟预加载等操作）
     */
    var onSurfaceCreatedListener: (() -> Unit)? = null

    /**
     * 每帧渲染完成后的回调（用于在 SDK 动画更新后强制覆盖嘴部参数）
     */
    var onAfterDrawFrame: (() -> Unit)? = null

    /**
     * 初始化渲染器
     */
    fun initialize() {
        if (isRendererSet) return

        // 设置 OpenGL ES 2.0 上下文
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        // 后台返回必须优先保留 EGL context 和纹理资源。
        // 如果 context 未被系统回收，恢复时不会重新触发 onSurfaceCreated/SetupTextures。
        preserveEGLContextOnPause = true

        // 设置渲染器
        setRenderer(Live2DInternalRenderer())

        // 持续渲染，保证模型待机动画和口型更新可见
        renderMode = RENDERMODE_CONTINUOUSLY

        isRendererSet = true
    }

    fun runOnRenderThread(block: () -> Unit) {
        if (!isRendererSet) { return }
        queueEvent(block)
    }

    /**
     * 触摸事件处理
     * 必须通过 queueEvent 投递到 GL 渲染线程，与 nativeOnDrawFrame 串行执行。
     * 官方 CubismNativeSamples v5-r.2 (2024-12-19) 已将此作为 bug 修复合入。
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isRendererSet) return true
        val x = event.x
        val y = event.y
        when (event.action) {
            MotionEvent.ACTION_DOWN -> queueEvent { JniBridgeJava.nativeOnTouchesBegan(x, y) }
            MotionEvent.ACTION_MOVE -> queueEvent { JniBridgeJava.nativeOnTouchesMoved(x, y) }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> queueEvent { JniBridgeJava.nativeOnTouchesEnded(x, y) }
        }
        return true
    }

    /**
     * 内部渲染器
     */
    private inner class Live2DInternalRenderer : Renderer {

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            JniBridgeJava.nativeOnSurfaceCreated()
            isSurfaceCreated = true
            onSurfaceCreatedListener?.invoke()
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            JniBridgeJava.nativeOnSurfaceChanged(width, height)
        }

        override fun onDrawFrame(gl: GL10?) {
            try {
                JniBridgeJava.nativeOnDrawFrame()
                onAfterDrawFrame?.invoke()
            } catch (e: Exception) {
                android.util.Log.e("Live2DGLSurfaceView", "nativeOnDrawFrame failed", e)
                // 不重新抛出 Exception，尝试继续渲染下一帧
            } catch (e: Error) {
                // Native 崩溃（如 SIGSEGV、SIGFPE 等）
                // 注意：不要在这里调用 nativeOnStop/nativeOnDestroy，因为这会破坏 GL 上下文
                // 导致字体纹理和其他 GPU 资源丢失，使所有文字变成方块
                android.util.Log.e("Live2DGLSurfaceView", "nativeOnDrawFrame error (native crash): ${e.message}", e)
                // 重新抛出 Error，让应用崩溃。Native 崩溃通常无法安全恢复。
                throw e
            }
        }
    }

    /**
     * 暂停渲染
     */
    override fun onPause() {
        super.onPause()
        runCatching {
            JniBridgeJava.nativeOnPause()
        }.onFailure {
            android.util.Log.w("Live2DGLSurfaceView", "nativeOnPause failed: ${it.message}")
        }
    }

    /**
     * 恢复渲染
     */
    override fun onResume() {
        super.onResume()
        runCatching {
            JniBridgeJava.nativeOnStart()
        }.onFailure {
            android.util.Log.w("Live2DGLSurfaceView", "nativeOnStart failed: ${it.message}")
        }
    }
}
