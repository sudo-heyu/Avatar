package com.example.scenic_avatar_guide_app.core.audio

import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.IOException
import java.io.InputStream

/**
 * ExoPlayer 自定义 DataSource，用于从 ChunkStreamManager 的 PipedInputStream 读取音频 chunk。
 *
 * URI 格式：chunk://stream/segmentId
 */
private const val TAG_DS = "ChunkDataSource"

class ChunkDataSource(
    private val streamManager: ChunkStreamManager
) : DataSource {
    private var currentStream: InputStream? = null
    private var uri: Uri? = null
    private var totalRead = 0L

    override fun open(dataSpec: DataSpec): Long {
        val id = dataSpec.uri.lastPathSegment ?: dataSpec.uri.host
            ?: throw IOException("No stream id in URI: ${dataSpec.uri}")
        uri = dataSpec.uri
        Log.d(TAG_DS, "open: streamId=$id")

        var stream = streamManager.getInputStream(id)
        if (stream == null) {
            Log.w(TAG_DS, "open: stream $id 不存在，尝试即时创建")
            streamManager.createStream(id)
            stream = streamManager.getInputStream(id)
        }

        currentStream = stream
            ?: throw IOException("Stream not found: $id")
        totalRead = 0L
        return C.LENGTH_UNSET.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        val stream = currentStream ?: return C.RESULT_END_OF_INPUT
        return try {
            val bytesRead = stream.read(buffer, offset, readLength)
            if (bytesRead > 0) {
                totalRead += bytesRead
                if (totalRead <= 200 || bytesRead % 20 == 0) {
                    Log.v(TAG_DS, "read: $bytesRead bytes (total=$totalRead)")
                }
            }
            if (bytesRead >= 0) bytesRead else C.RESULT_END_OF_INPUT
        } catch (e: IOException) {
            Log.w(TAG_DS, "read error: ${e.message}")
            C.RESULT_END_OF_INPUT
        }
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        Log.d(TAG_DS, "close: streamId=${uri?.lastPathSegment}, totalRead=$totalRead")
        currentStream?.close()
        currentStream = null
        uri?.lastPathSegment?.let { streamManager.cleanup(it) }
    }

    override fun addTransferListener(transferListener: TransferListener) {
        // ChunkDataSource 不追踪传输进度，无需处理
    }
}
