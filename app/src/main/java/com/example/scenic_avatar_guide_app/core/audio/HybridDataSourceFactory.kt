package com.example.scenic_avatar_guide_app.core.audio

import android.content.Context
import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

private const val CHUNK_SCHEME = "chunk"

/**
 * 混合 DataSource.Factory，让 ExoPlayer 同时支持普通 URL 和 chunk:// 流式 URI。
 *
 * - chunk://segmentId → 创建 ChunkDataSource（从 PipedStream 读取）
 * - http/https/file 等 → 创建 DefaultDataSource（原有行为）
 */
class HybridDataSourceFactory(
    private val context: Context,
    private val chunkStreamManager: ChunkStreamManager
) : DataSource.Factory {

    private val defaultFactory = DefaultDataSource.Factory(context)

    override fun createDataSource(): DataSource {
        return HybridDataSource(context, chunkStreamManager, defaultFactory)
    }
}

class HybridDataSource(
    private val context: Context,
    private val chunkStreamManager: ChunkStreamManager,
    private val defaultFactory: DataSource.Factory
) : DataSource {

    private var delegate: DataSource? = null
    private var transferListener: TransferListener? = null

    override fun addTransferListener(transferListener: TransferListener) {
        this.transferListener = transferListener
    }

    override fun open(dataSpec: DataSpec): Long {
        delegate = if (dataSpec.uri.scheme == CHUNK_SCHEME) {
            ChunkDataSource(chunkStreamManager)
        } else {
            defaultFactory.createDataSource()
        }
        transferListener?.let { delegate!!.addTransferListener(it) }
        return delegate!!.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        return delegate!!.read(buffer, offset, readLength)
    }

    override fun getUri(): Uri? = delegate?.getUri()

    override fun close() {
        delegate?.close()
        delegate = null
    }
}
