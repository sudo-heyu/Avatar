package com.example.scenic_avatar_guide_app.data.repository

import android.content.Context
import com.example.scenic_avatar_guide_app.data.local.ScenicDataSource
import com.example.scenic_avatar_guide_app.domain.model.ScenicMapBundle
import com.example.scenic_avatar_guide_app.domain.model.ScenicMapData
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MapDataRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scenicDataSource: ScenicDataSource,
    private val json: Json
) {
    private val mapDataCache = mutableMapOf<String, ScenicMapData>()

    /**
     * 加载指定景区的地图数据包。
     * 如果该景区在 scenic_map_data.json 中无配置，则返回 null。
     */
    fun loadMapBundle(scenicId: String): ScenicMapBundle? {
        val area = scenicDataSource.loadScenicAreas().find { it.id == scenicId } ?: return null
        val mapData = loadScenicMapData(scenicId) ?: return null
        val spotsWithLocation = area.spots.filter { it.lat != null && it.lng != null }
        return ScenicMapBundle(area, spotsWithLocation, mapData)
    }

    /**
     * 加载景区地图配置。结果按 scenicId 缓存。
     */
    private fun loadScenicMapData(scenicId: String): ScenicMapData? {
        mapDataCache[scenicId]?.let { return it }

        return try {
            val text = context.assets.open("scenic_map_data.json").use { it.readBytes().decodeToString() }
            val all = json.decodeFromString<Map<String, ScenicMapData>>(text)
            all[scenicId]?.also { mapDataCache[scenicId] = it }
        } catch (e: Exception) {
            null
        }
    }

    fun clearCache() {
        mapDataCache.clear()
    }
}
