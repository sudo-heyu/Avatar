package com.example.scenic_avatar_guide_app.core.audio

import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "ChunkStreamManager"
private const val BUFFER_SIZE = 512 * 1024 // 512KB，增大缓冲区以应对后端不稳定

/**
 * 管理流式 TTS chunk 的 PipedStream 对。
 *
 * 每个 segment 对应一组 PipedInputStream/PipedOutputStream：
 * - ExoPlayer 通过 ChunkDataSource 从 PipedInputStream 读取
 * - 收到 tts_audio_chunk 时通过 PipedOutputStream 写入
 * - 写端关闭后，ExoPlayer 读取到 EOF，该 MediaItem 自然结束
 */
class ChunkStreamManager {
    private val outputs = ConcurrentHashMap<String, PipedOutputStream>()
    private val inputs = ConcurrentHashMap<String, PipedInputStream>()
    private val bytesWritten = ConcurrentHashMap<String, Long>()

    @Synchronized
    fun createStream(id: String): Boolean {
        if (outputs.containsKey(id)) {
            Log.w(TAG, "Stream $id 已存在，跳过创建")
            return false
        }
        val input = PipedInputStream(BUFFER_SIZE)
        val output = PipedOutputStream(input)
        inputs[id] = input
        outputs[id] = output
        bytesWritten[id] = 0L
        Log.d(TAG, "创建 stream: $id")
        return true
    }

    fun writeChunk(id: String, bytes: ByteArray) {
        val output = outputs[id]
        if (output == null) {
            Log.w(TAG, "Stream $id 不存在，忽略 ${bytes.size} bytes chunk")
            return
        }
        try {
            output.write(bytes)
            output.flush()
            val total = bytesWritten.compute(id) { _, v -> (v ?: 0L) + bytes.size } ?: bytes.size.toLong()
            if (total <= 500 || bytes.size > 1000) {
                Log.d(TAG, "writeChunk: $id, +${bytes.size} bytes (total=$total)")
            }
        } catch (e: IOException) {
            // 读端可能已关闭（如 abort 或 ExoPlayer 已释放）
            Log.w(TAG, "Stream $id 写入失败（读端已关闭？）: ${e.message}")
        }
    }

    fun endStream(id: String) {
        val total = bytesWritten[id] ?: 0L
        val output = outputs.remove(id)
        if (output != null) {
            try {
                output.close()
                Log.d(TAG, "关闭 stream 写端: $id, totalWritten=$total")
            } catch (e: IOException) {
                Log.w(TAG, "关闭 stream 写端失败: $id", e)
            }
        }
        // 写端关闭后，读端仍可能在被 ExoPlayer 读取；待 ExoPlayer 读取到 EOF 后
        // ChunkDataSource.close() 会关闭 input。这里延迟清理 inputs map，避免影响正在进行的读取。
        // 实际清理在 ChunkDataSource.close() 触发或后续 abort/cleanup 时完成。
    }

    fun abortStream(id: String) {
        val total = bytesWritten[id] ?: 0L
        outputs.remove(id)?.close()
        inputs.remove(id)?.let {
            try {
                it.close()
            } catch (_: IOException) {
            }
        }
        bytesWritten.remove(id)
        Log.d(TAG, "强制中断 stream: $id, totalWritten=$total")
    }

    fun getInputStream(id: String): InputStream? = inputs[id]

    fun getBytesWritten(id: String): Long = bytesWritten[id] ?: 0L

    fun cleanup(id: String) {
        inputs.remove(id)?.let {
            try {
                it.close()
            } catch (_: IOException) {
            }
        }
        outputs.remove(id)
        bytesWritten.remove(id)
        Log.d(TAG, "清理 stream: $id")
    }

    fun cleanupAll() {
        val ids = outputs.keys.toList() + inputs.keys.toList()
        ids.distinct().forEach { abortStream(it) }
        inputs.clear()
        outputs.clear()
        bytesWritten.clear()
        Log.d(TAG, "清理所有 stream: ${ids.distinct().size} 个")
    }
}
