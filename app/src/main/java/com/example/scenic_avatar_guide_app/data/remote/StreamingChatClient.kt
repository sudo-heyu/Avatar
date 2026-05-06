package com.example.scenic_avatar_guide_app.data.remote

import android.util.Log
import com.example.scenic_avatar_guide_app.core.network.StreamingOkHttp
import com.example.scenic_avatar_guide_app.data.local.SettingsDataStore
import com.example.scenic_avatar_guide_app.domain.model.AvatarAction
import com.example.scenic_avatar_guide_app.domain.model.ChatStreamEvent
import com.example.scenic_avatar_guide_app.domain.model.ChatTextRequest
import com.example.scenic_avatar_guide_app.domain.model.ResponseMetadata
import com.example.scenic_avatar_guide_app.domain.model.RouteData
import com.example.scenic_avatar_guide_app.domain.model.SourceInfo
import com.example.scenic_avatar_guide_app.domain.model.TtsMarkItem
import com.example.scenic_avatar_guide_app.domain.model.TtsSegmentData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "StreamingChatClient"

@Singleton
class StreamingChatClient @Inject constructor(
    @StreamingOkHttp private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    fun streamChat(request: ChatTextRequest): Flow<ChatStreamEvent> = callbackFlow {
        val jsonString = json.encodeToString(request)
        Log.d(TAG, "请求体: $jsonString")

        val requestBody = jsonString.toRequestBody("application/json".toMediaType())

        // URL 中的 host/scheme/port 由 DynamicBaseUrlInterceptor 运行时替换
        val httpRequest = Request.Builder()
            .url("${SettingsDataStore.DEFAULT_BASE_URL}api/v1/chat/text/stream")
            .post(requestBody)
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .build()

        Log.d(TAG, "发起流式请求: ${httpRequest.url}")
        val call = okHttpClient.newCall(httpRequest)

        val readJob = launch(Dispatchers.IO) {
            try {
                call.execute().use { response ->
                    Log.d(TAG, "收到响应: code=${response.code}, content-type=${response.header("Content-Type")}")
                    if (!response.isSuccessful) {
                        trySend(ChatStreamEvent.Error(response.code, response.message))
                        return@use
                    }

                    val source = response.body?.source()
                    if (source == null) {
                        Log.w(TAG, "流式响应 body 为空")
                        trySend(ChatStreamEvent.Error(message = "流式响应为空"))
                        return@use
                    }

                    val sseDataLines = mutableListOf<String>()
                    var currentEventType: String? = null
                    var sawTerminalEvent = false
                    var lineCount = 0
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: continue
                        lineCount++
                        if (lineCount <= 20) {
                            Log.d(TAG, "收到原始行[$lineCount]: $line")
                        }
                        val event = parseStreamLine(
                            rawLine = line,
                            sseDataLines = sseDataLines,
                            eventType = currentEventType,
                            onEventType = { currentEventType = it },
                            onEventFlushed = { currentEventType = null }
                        )
                        if (event != null) {
                            Log.d(TAG, "解析事件: ${event::class.simpleName}, eventType=$currentEventType")
                            sawTerminalEvent = sawTerminalEvent || event.isTerminalStreamEvent()
                            trySend(event)
                        }
                    }
                    Log.d(TAG, "流式响应读取结束, 共 $lineCount 行, sawTerminalEvent=$sawTerminalEvent, isCanceled=${call.isCanceled()}")

                    flushSseData(sseDataLines, currentEventType)?.let {
                        Log.d(TAG, "EOF 时 flush 出事件: ${it::class.simpleName}")
                        sawTerminalEvent = sawTerminalEvent || it.isTerminalStreamEvent()
                        trySend(it)
                    }
                    if (!sawTerminalEvent && !call.isCanceled()) {
                        Log.w(TAG, "流式响应 EOF 但未收到 done/aborted/error 终止事件")
                        trySend(ChatStreamEvent.PrematurelyEnded)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "流式请求异常: ${e::class.simpleName}: ${e.message}", e)
                if (!call.isCanceled()) {
                    trySend(ChatStreamEvent.Error(message = "[${e::class.simpleName}] ${e.message ?: "流式请求失败"}"))
                }
            } finally {
                close()
            }
        }

        awaitClose {
            readJob.cancel()
            call.cancel()
        }
    }

    private fun ChatStreamEvent.isTerminalStreamEvent(): Boolean {
        return this is ChatStreamEvent.Done ||
            this is ChatStreamEvent.PrematurelyEnded ||
            this is ChatStreamEvent.Aborted ||
            this is ChatStreamEvent.Error
    }

    private fun parseStreamLine(
        rawLine: String,
        sseDataLines: MutableList<String>,
        eventType: String? = null,
        onEventType: (String) -> Unit = {},
        onEventFlushed: () -> Unit = {}
    ): ChatStreamEvent? {
        val line = rawLine.trimEnd()
        if (line.isBlank()) {
            val hadData = sseDataLines.isNotEmpty()
            return flushSseData(sseDataLines, eventType).also {
                if (hadData) onEventFlushed()
            }
        }
        if (line.startsWith(":")) return null
        if (line.startsWith("event:")) {
            val eventType = line.removePrefix("event:").trim()
            Log.d(TAG, "SSE event 类型: $eventType")
            onEventType(eventType)
            return null
        }
        if (line.startsWith("data:")) {
            sseDataLines += line.removePrefix("data:").trimStart()
            return null
        }
        if (line.startsWith("{")) {
            return decodeEvent(line, eventType)
        }
        return null
    }

    private fun flushSseData(
        sseDataLines: MutableList<String>,
        eventType: String? = null
    ): ChatStreamEvent? {
        if (sseDataLines.isEmpty()) return null
        val payload = sseDataLines.joinToString(separator = "\n")
        sseDataLines.clear()
        return decodeEvent(payload, eventType)
    }

    private fun decodeEvent(payload: String, eventType: String? = null): ChatStreamEvent? {
        if (payload == "[DONE]") {
            return ChatStreamEvent.Done
        }

        val envelope = runCatching {
            json.decodeFromString(ChatStreamEnvelope.serializer(), payload)
        }.getOrElse { e ->
            if (eventType == "text_delta") {
                return ChatStreamEvent.TextDelta(payload)
            }
            Log.w(TAG, "跳过无法解析的流式事件: eventType=$eventType, payload=${payload.take(160)}", e)
            return null
        }

        val type = envelope.type ?: eventType
        if (type.isNullOrBlank()) {
            Log.w(TAG, "跳过缺少 type/event 的流式事件: payload=${payload.take(160)}")
            return null
        }

        Log.d(TAG, "decodeEvent: type=$type, segmentId=${envelope.segmentId}, hasData=${envelope.data != null}")

        return when (type) {
            "message_start" -> ChatStreamEvent.MessageStart(
                messageId = envelope.messageId,
                sessionId = envelope.sessionId,
                createdAt = envelope.createdAt
            )
            "text_delta" -> decodeTextDelta(envelope)
                ?.let { ChatStreamEvent.TextDelta(it) }
            "tts_segment", "segment_tts", "segmenttts" -> decodeTtsSegment(envelope)
                ?.let { ChatStreamEvent.TtsSegment(it) }
            "tts_segment_ready" -> decodeTtsSegmentReady(envelope)
                ?.let { ChatStreamEvent.TtsSegmentReady(it) }
            "tts_audio_chunk", "tts_audio_end" -> null
            "avatar_action" -> envelope.data
                ?.let { json.decodeFromJsonElement<AvatarAction>(it) }
                ?.let { ChatStreamEvent.AvatarActionDelta(it) }
            "sources" -> envelope.data
                ?.let { json.decodeFromJsonElement(ListSerializer(SourceInfo.serializer()), it) }
                ?.let { ChatStreamEvent.SourcesDelta(it) }
            "route_data" -> envelope.data
                ?.let { json.decodeFromJsonElement<RouteData>(it) }
                ?.let { ChatStreamEvent.RouteDataDelta(it) }
            "metadata" -> envelope.data
                ?.let { json.decodeFromJsonElement<ResponseMetadata>(it) }
                ?.let { ChatStreamEvent.MetadataDelta(it) }
            "done" -> ChatStreamEvent.Done
            "aborted" -> ChatStreamEvent.Aborted(
                messageId = envelope.messageId,
                sessionId = envelope.sessionId,
                reason = envelope.reason
            )
            "error" -> ChatStreamEvent.Error(envelope.code, envelope.message ?: "流式响应错误")
            else -> null
        }
    }

    private fun decodeTextDelta(envelope: ChatStreamEnvelope): String? {
        envelope.delta?.let { return it }
        val data = envelope.data ?: return null
        return runCatching {
            val obj = data.jsonObject
            obj["delta"]?.jsonPrimitive?.contentOrNull
                ?: obj["text"]?.jsonPrimitive?.contentOrNull
                ?: obj["content"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()
    }

    private fun decodeTtsSegment(envelope: ChatStreamEnvelope): TtsSegmentData? {
        envelope.data?.let { data ->
            runCatching {
                return json.decodeFromJsonElement<TtsSegmentData>(data)
            }
        }

        val text = envelope.text ?: return null
        val audioUrl = envelope.audioUrl ?: return null
        return TtsSegmentData(
            segmentId = envelope.segmentId ?: "seg_${UUID.randomUUID()}",
            segmentIndex = envelope.segmentIndex,
            text = text,
            audioUrl = audioUrl,
            durationMs = envelope.durationMs,
            voice = envelope.voice,
            rate = envelope.rate,
            volume = envelope.volume,
            pitch = envelope.pitch,
            emotion = envelope.emotion,
            marks = envelope.marks
        )
    }

    private fun decodeTtsSegmentReady(envelope: ChatStreamEnvelope): TtsSegmentData? {
        envelope.data?.let { data ->
            runCatching {
                return json.decodeFromJsonElement<TtsSegmentData>(data)
            }
        }

        val segmentId = envelope.segmentId ?: return null
        val audioUrl = envelope.audioUrl ?: return null
        return TtsSegmentData(
            segmentId = segmentId,
            segmentIndex = envelope.segmentIndex,
            text = envelope.text ?: "",
            audioUrl = audioUrl,
            durationMs = envelope.durationMs,
            emotion = envelope.emotion,
            marks = envelope.marks
        )
    }
}

@Serializable
private data class ChatStreamEnvelope(
    val type: String? = null,
    @SerialName("message_id")
    val messageId: String? = null,
    @SerialName("session_id")
    val sessionId: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    val delta: String? = null,
    val data: JsonElement? = null,
    @SerialName("segment_id")
    val segmentId: String? = null,
    @SerialName("segment_index")
    val segmentIndex: Int? = null,
    val text: String? = null,
    @SerialName("audio_url")
    val audioUrl: String? = null,
    @SerialName("duration_ms")
    val durationMs: Int? = null,
    val voice: String? = null,
    val rate: String? = null,
    val volume: String? = null,
    val pitch: String? = null,
    val emotion: String? = null,
    val marks: List<TtsMarkItem>? = null,
    val sequence: Int? = null,
    @SerialName("audio_format")
    val audioFormat: String? = null,
    @SerialName("audio_profile")
    val audioProfile: String? = null,
    @SerialName("audio_base64")
    val audioBase64: String? = null,
    @SerialName("file_name")
    val fileName: String? = null,
    @SerialName("chunk_count")
    val chunkCount: Int? = null,
    @SerialName("stream_audio_offset_ms")
    val streamAudioOffsetMs: Int? = null,
    @SerialName("stream_audio_duration_ms")
    val streamAudioDurationMs: Int? = null,
    val code: Int? = null,
    val message: String? = null,
    val reason: String? = null
)
