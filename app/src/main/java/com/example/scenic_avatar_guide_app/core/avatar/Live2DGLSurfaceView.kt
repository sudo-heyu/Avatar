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
     * 初始化渲染器
     */
    fun initialize() {
        if (isRendererSet) return

        // 设置 OpenGL ES 2.0 上下文
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        preserveEGLContextOnPause = true

        // 设置渲染器
        setRenderer(Live2DInternalRenderer())

        // 持续渲染，保证模型待机动画和口型更新可见
        renderMode = RENDERMODE_CONTINUOUSLY

        isRendererSet = true
    }

    fun runOnRenderThread(block: () -> Unit) {
        if (!isRendererSet) {
            block()
            return
        }
        queueEvent(block)
    }

    /**
     * 触摸事件处理
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                JniBridgeJava.nativeOnTouchesBegan(event.x, event.y)
            }
            MotionEvent.ACTION_MOVE -> {
                JniBridgeJava.nativeOnTouchesMoved(event.x, event.y)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                JniBridgeJava.nativeOnTouchesEnded(event.x, event.y)
            }
        }
        return true
    }

    /**
     * 内部渲染器
     */
    private inner class Live2DInternalRenderer : Renderer {

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            JniBridgeJava.nativeOnSurfaceCreated()
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            JniBridgeJava.nativeOnSurfaceChanged(width, height)
        }

        override fun onDrawFrame(gl: GL10?) {
            JniBridgeJava.nativeOnDrawFrame()
        }
    }

    /**
     * 暂停渲染
     */
    override fun onPause() {
        super.onPause()
        JniBridgeJava.nativeOnPause()
    }

    /**
     * 恢复渲染
     */
    override fun onResume() {
        super.onResume()
        JniBridgeJava.nativeOnStart()
    }
}
