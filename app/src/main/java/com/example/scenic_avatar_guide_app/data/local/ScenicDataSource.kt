package com.example.scenic_avatar_guide_app.data.local

import android.content.Context
import com.example.scenic_avatar_guide_app.domain.model.ScenicArea
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScenicDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var cachedAreas: List<ScenicArea>? = null

    fun loadScenicAreas(): List<ScenicArea> {
        cachedAreas?.let { return it }

        val jsonString = context.assets.open("scenic_spots.json").use { it.readBytes().decodeToString() }
        val areas = Json.decodeFromString<List<ScenicArea>>(jsonString)
        cachedAreas = areas
        return areas
    }

    fun findSpotName(scenicId: String, spotId: String): String? {
        val area = loadScenicAreas().find { it.id == scenicId } ?: return null
        return area.spots.find { it.id == spotId }?.name
    }

    fun findAreaName(scenicId: String): String? {
        return loadScenicAreas().find { it.id == scenicId }?.name
    }
}
