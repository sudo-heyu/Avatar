package com.example.scenic_avatar_guide_app.data.repository

import android.content.Context
import com.example.scenic_avatar_guide_app.data.local.ScenicDataSource
import com.example.scenic_avatar_guide_app.domain.model.ScenicFacility
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
    private val facilityCache = mutableMapOf<String, List<ScenicFacility>>()

    /**
     * 加载指定景区的地图数据包。
     * 如果该景区在 scenic_map_data.json 中无配置，则返回 null。
     */
    fun loadMapBundle(scenicId: String): ScenicMapBundle? {
        val area = scenicDataSource.loadScenicAreas().find { it.id == scenicId } ?: return null
        val mapData = loadScenicMapData(scenicId) ?: return null
        val spotsWithLocation = area.spots.filter { it.lat != null && it.lng != null }
        val facilities = loadScenicFacilities(scenicId)
        return ScenicMapBundle(area, spotsWithLocation, mapData, facilities)
    }

    fun loadAllMapBundles(): List<ScenicMapBundle> =
        scenicDataSource.loadScenicAreas().mapNotNull { area ->
            loadMapBundle(area.id)
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

    private fun loadScenicFacilities(scenicId: String): List<ScenicFacility> {
        facilityCache[scenicId]?.let { return it }

        return try {
            val text = context.assets.open("scenic_facilities.json").use { it.readBytes().decodeToString() }
            val all = json.decodeFromString<Map<String, List<ScenicFacility>>>(text)
            all[scenicId].orEmpty().also { facilityCache[scenicId] = it }
        } catch (e: Exception) {
            emptyList<ScenicFacility>().also { facilityCache[scenicId] = it }
        }
    }

    fun clearCache() {
        mapDataCache.clear()
        facilityCache.clear()
    }
}
