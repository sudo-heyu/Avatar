package com.example.scenic_avatar_guide_app.core.avatar.animation

import android.content.res.AssetManager
import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.PI
import kotlin.math.cos

private const val TAG = "KotlinCubismMotion"

enum class CubismMotionCurveTarget {
    Model,
    Parameter,
    PartOpacity
}

data class KotlinCubismMotion(
    val assetPath: String,
    val durationSeconds: Float,
    val fps: Float,
    val loop: Boolean,
    val curves: List<KotlinCubismMotionCurve>,
    val defaultFadeInMs: Long = 500L,
    val defaultFadeOutMs: Long = 500L
) {
    val durationMs: Long = (durationSeconds * 1000f).toLong().coerceAtLeast(1L)

    fun sample(
        timeSeconds: Float,
        isLoopCorrection: Boolean,
        correctedEndTimeSeconds: Float
    ): KotlinCubismMotionValues {
        val parameters = LinkedHashMap<String, Float>()
        val partOpacities = LinkedHashMap<String, Float>()

        curves.forEach { curve ->
            val value = curve.evaluate(timeSeconds, isLoopCorrection, correctedEndTimeSeconds)
            when (curve.target) {
                CubismMotionCurveTarget.Parameter -> parameters[curve.id] = value
                CubismMotionCurveTarget.PartOpacity -> partOpacities[curve.id] = value
                CubismMotionCurveTarget.Model -> Unit
            }
        }

        return KotlinCubismMotionValues(
            parameters = parameters,
            partOpacities = partOpacities
        )
    }
}

data class KotlinCubismMotionValues(
    val parameters: Map<String, Float>,
    val partOpacities: Map<String, Float>
)

data class KotlinCubismMotionFrame(
    val parameters: Map<String, Float>,
    val partOpacities: Map<String, Float>,
    val weight: Float,
    val finished: Boolean
)

data class KotlinCubismMotionCurve(
    val target: CubismMotionCurveTarget,
    val id: String,
    val firstPoint: CubismMotionPoint,
    val segments: List<CubismMotionSegment>
) {
    fun evaluate(
        timeSeconds: Float,
        isLoopCorrection: Boolean,
        correctedEndTimeSeconds: Float
    ): Float {
        if (segments.isEmpty() || timeSeconds <= firstPoint.timeSeconds) {
            return firstPoint.value
        }

        for (segment in segments) {
            if (segment.end.timeSeconds > timeSeconds) {
                return segment.evaluate(timeSeconds)
            }
        }

        val lastSegment = segments.last()
        if (isLoopCorrection && timeSeconds < correctedEndTimeSeconds) {
            return lastSegment.evaluateLoopCorrection(
                nextStart = firstPoint.copy(timeSeconds = correctedEndTimeSeconds),
                timeSeconds = timeSeconds
            )
        }

        return lastSegment.end.value
    }
}

data class CubismMotionPoint(
    val timeSeconds: Float,
    val value: Float
)

sealed class CubismMotionSegment(
    val start: CubismMotionPoint,
    val end: CubismMotionPoint
) {
    abstract fun evaluate(timeSeconds: Float): Float

    open fun evaluateLoopCorrection(nextStart: CubismMotionPoint, timeSeconds: Float): Float {
        return Linear(start = end, end = nextStart).evaluate(timeSeconds)
    }

    class Linear(
        start: CubismMotionPoint,
        end: CubismMotionPoint
    ) : CubismMotionSegment(start, end) {
        override fun evaluate(timeSeconds: Float): Float {
            val duration = end.timeSeconds - start.timeSeconds
            if (duration <= 0f) return end.value
            val t = ((timeSeconds - start.timeSeconds) / duration).coerceIn(0f, 1f)
            return start.value + (end.value - start.value) * t
        }
    }

    class Bezier(
        start: CubismMotionPoint,
        val control1: CubismMotionPoint,
        val control2: CubismMotionPoint,
        end: CubismMotionPoint
    ) : CubismMotionSegment(start, end) {
        override fun evaluate(timeSeconds: Float): Float {
            if (end.timeSeconds <= start.timeSeconds) return end.value

            var low = 0f
            var high = 1f
            repeat(20) {
                val mid = (low + high) * 0.5f
                val x = cubic(start.timeSeconds, control1.timeSeconds, control2.timeSeconds, end.timeSeconds, mid)
                if (x < timeSeconds) {
                    low = mid
                } else {
                    high = mid
                }
            }

            val t = ((low + high) * 0.5f).coerceIn(0f, 1f)
            return cubic(start.value, control1.value, control2.value, end.value, t)
        }

        private fun cubic(p0: Float, p1: Float, p2: Float, p3: Float, t: Float): Float {
            val inv = 1f - t
            return inv * inv * inv * p0 +
                3f * inv * inv * t * p1 +
                3f * inv * t * t * p2 +
                t * t * t * p3
        }
    }

    class Stepped(
        start: CubismMotionPoint,
        end: CubismMotionPoint
    ) : CubismMotionSegment(start, end) {
        override fun evaluate(timeSeconds: Float): Float = start.value

        override fun evaluateLoopCorrection(nextStart: CubismMotionPoint, timeSeconds: Float): Float {
            return end.value
        }
    }

    class InverseStepped(
        start: CubismMotionPoint,
        end: CubismMotionPoint
    ) : CubismMotionSegment(start, end) {
        override fun evaluate(timeSeconds: Float): Float = end.value

        override fun evaluateLoopCorrection(nextStart: CubismMotionPoint, timeSeconds: Float): Float {
            return nextStart.value
        }
    }
}

class KotlinCubismMotionPlayer {

    private data class ActiveMotion(
        val motion: KotlinCubismMotion,
        val loop: Boolean,
        val fadeInMs: Long,
        val fadeOutMs: Long,
        val startTimeMs: Long
    )

    private var activeMotion: ActiveMotion? = null

    fun play(
        motion: KotlinCubismMotion,
        loop: Boolean = motion.loop,
        fadeInMs: Long = motion.defaultFadeInMs,
        fadeOutMs: Long = motion.defaultFadeOutMs,
        nowMs: Long = System.currentTimeMillis()
    ) {
        activeMotion = ActiveMotion(
            motion = motion,
            loop = loop,
            fadeInMs = fadeInMs.coerceAtLeast(0L),
            fadeOutMs = fadeOutMs.coerceAtLeast(0L),
            startTimeMs = nowMs
        )
    }

    fun stop() {
        activeMotion = null
    }

    fun isPlaying(): Boolean = activeMotion != null

    fun currentAssetPath(): String? = activeMotion?.motion?.assetPath

    fun sample(nowMs: Long = System.currentTimeMillis()): KotlinCubismMotionFrame? {
        val active = activeMotion ?: return null
        val elapsedMs = (nowMs - active.startTimeMs).coerceAtLeast(0L)
        val motion = active.motion

        if (!active.loop && elapsedMs >= motion.durationMs) {
            val values = motion.sample(
                timeSeconds = motion.durationSeconds,
                isLoopCorrection = false,
                correctedEndTimeSeconds = motion.durationSeconds
            )
            return KotlinCubismMotionFrame(
                parameters = values.parameters,
                partOpacities = values.partOpacities,
                weight = 0f,
                finished = true
            )
        }

        val correctedDuration = correctedDurationSeconds(motion, active.loop)
        val elapsedSeconds = elapsedMs / 1000f
        val timeSeconds = if (active.loop && correctedDuration > 0f) {
            elapsedSeconds % correctedDuration
        } else {
            elapsedSeconds.coerceAtMost(motion.durationSeconds)
        }

        val values = motion.sample(
            timeSeconds = timeSeconds,
            isLoopCorrection = active.loop,
            correctedEndTimeSeconds = correctedDuration
        )

        return KotlinCubismMotionFrame(
            parameters = values.parameters,
            partOpacities = values.partOpacities,
            weight = calculateWeight(active, elapsedMs),
            finished = false
        )
    }

    private fun correctedDurationSeconds(motion: KotlinCubismMotion, loop: Boolean): Float {
        if (!loop) return motion.durationSeconds
        val fpsCorrection = if (motion.fps > 0f) 1f / motion.fps else 0f
        return motion.durationSeconds + fpsCorrection
    }

    private fun calculateWeight(active: ActiveMotion, elapsedMs: Long): Float {
        var weight = 1f

        if (active.fadeInMs > 0L) {
            weight *= easingSine(elapsedMs.toFloat() / active.fadeInMs)
        }

        if (!active.loop && active.fadeOutMs > 0L) {
            val remainingMs = active.motion.durationMs - elapsedMs
            if (remainingMs < active.fadeOutMs) {
                weight *= easingSine(remainingMs.toFloat() / active.fadeOutMs)
            }
        }

        return weight.coerceIn(0f, 1f)
    }

    private fun easingSine(t: Float): Float {
        val clamped = t.coerceIn(0f, 1f)
        return (0.5f - 0.5f * cos(clamped * PI)).toFloat()
    }
}

class KotlinCubismMotionRepository(
    private val assetManager: AssetManager
) {
    private val cache = LinkedHashMap<String, KotlinCubismMotion>()
    private val json = Json { ignoreUnknownKeys = true }

    fun load(assetPath: String): Result<KotlinCubismMotion> = runCatching {
        cache[assetPath]?.let { return@runCatching it }

        val text = assetManager.open(assetPath).bufferedReader().use { it.readText() }
        val root = json.parseToJsonElement(text).jsonObject
        val meta = root["Meta"]?.jsonObject ?: error("Missing Meta in $assetPath")
        val duration = meta.float("Duration") ?: error("Missing Meta.Duration in $assetPath")
        val fps = meta.float("Fps") ?: 30f
        val loop = meta.boolean("Loop") ?: false

        val curves = root["Curves"]?.jsonArray.orEmpty()
            .mapNotNull { parseCurve(assetPath, it.jsonObject) }

        KotlinCubismMotion(
            assetPath = assetPath,
            durationSeconds = duration,
            fps = fps,
            loop = loop,
            curves = curves
        ).also {
            cache[assetPath] = it
            Log.d(TAG, "Loaded $assetPath: duration=${it.durationSeconds}, curves=${it.curves.size}")
        }
    }

    fun preload(assetPaths: Iterable<String>) {
        assetPaths.forEach { path ->
            load(path).onFailure { error ->
                Log.w(TAG, "Failed to preload motion: $path", error)
            }
        }
    }

    private fun parseCurve(assetPath: String, curve: JsonObject): KotlinCubismMotionCurve? {
        val target = when (curve.string("Target")) {
            "Parameter" -> CubismMotionCurveTarget.Parameter
            "PartOpacity" -> CubismMotionCurveTarget.PartOpacity
            "Model" -> CubismMotionCurveTarget.Model
            else -> return null
        }
        val id = curve.string("Id") ?: return null
        val segmentValues = curve["Segments"]?.jsonArray?.toFloatList().orEmpty()
        if (segmentValues.size < 2) {
            Log.w(TAG, "Curve has no first point: $assetPath/$id")
            return null
        }

        val firstPoint = CubismMotionPoint(
            timeSeconds = segmentValues[0],
            value = segmentValues[1]
        )

        return KotlinCubismMotionCurve(
            target = target,
            id = id,
            firstPoint = firstPoint,
            segments = parseSegments(assetPath, id, firstPoint, segmentValues)
        )
    }

    private fun parseSegments(
        assetPath: String,
        curveId: String,
        firstPoint: CubismMotionPoint,
        values: List<Float>
    ): List<CubismMotionSegment> {
        val segments = mutableListOf<CubismMotionSegment>()
        var index = 2
        var start = firstPoint

        while (index < values.size) {
            val type = values[index].toInt()
            val segment = when (type) {
                0 -> {
                    if (index + 2 >= values.size) break
                    val end = CubismMotionPoint(values[index + 1], values[index + 2])
                    index += 3
                    CubismMotionSegment.Linear(start, end)
                }
                1 -> {
                    if (index + 6 >= values.size) break
                    val control1 = CubismMotionPoint(values[index + 1], values[index + 2])
                    val control2 = CubismMotionPoint(values[index + 3], values[index + 4])
                    val end = CubismMotionPoint(values[index + 5], values[index + 6])
                    index += 7
                    CubismMotionSegment.Bezier(start, control1, control2, end)
                }
                2 -> {
                    if (index + 2 >= values.size) break
                    val end = CubismMotionPoint(values[index + 1], values[index + 2])
                    index += 3
                    CubismMotionSegment.Stepped(start, end)
                }
                3 -> {
                    if (index + 2 >= values.size) break
                    val end = CubismMotionPoint(values[index + 1], values[index + 2])
                    index += 3
                    CubismMotionSegment.InverseStepped(start, end)
                }
                else -> {
                    Log.w(TAG, "Unknown segment type $type in $assetPath/$curveId at $index")
                    break
                }
            }
            segments += segment
            start = segment.end
        }

        return segments
    }

    private fun JsonObject.string(key: String): String? {
        return this[key]?.jsonPrimitive?.content
    }

    private fun JsonObject.float(key: String): Float? {
        return this[key]?.jsonPrimitive?.floatOrNull
    }

    private fun JsonObject.boolean(key: String): Boolean? {
        return this[key]?.jsonPrimitive?.booleanOrNull
    }

    private fun JsonArray.toFloatList(): List<Float> {
        return mapNotNull { it.jsonPrimitive.floatOrNull }
    }
}
