package com.example.scenic_avatar_guide_app.data.repository

import android.content.Context
import com.example.scenic_avatar_guide_app.domain.model.ScenicIndex
import com.example.scenic_avatar_guide_app.domain.model.ScenicIntroContent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScenicIntroRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json
) {
    private val contentCache = mutableMapOf<String, ScenicIntroContent>()

    suspend fun loadScenicIntro(scenicId: String): ScenicIntroContent {
        contentCache[scenicId]?.let { return it }

        return withContext(Dispatchers.IO) {
            val assetId = when (scenicId) {
                "site_of_the_august_7th_conference" -> "baqi_memorial"
                else -> scenicId
            }
            val assetPath = "scenic_intro/${assetId}.json"
            val jsonString = context.assets.open(assetPath).bufferedReader().use { it.readText() }
            val content = json.decodeFromString(ScenicIntroContent.serializer(), jsonString)
            contentCache[scenicId] = content
            content
        }
    }

    suspend fun loadScenicIndex(): ScenicIndex {
        return withContext(Dispatchers.IO) {
            val jsonString = context.assets.open("scenic_intro/index.json").bufferedReader().use { it.readText() }
            json.decodeFromString(ScenicIndex.serializer(), jsonString)
        }
    }
}
