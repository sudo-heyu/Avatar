package com.example.scenic_avatar_guide_app.domain.model

import kotlinx.serialization.Serializable

// ==================== 景区介绍内容模型 ====================

@Serializable
data class ScenicIntroContent(
    val scenicId: String,
    val scenicName: String,
    val subtitle: String? = null,
    val heroImage: HeroImage? = null,
    val highlights: List<String>? = null,
    val sections: List<ContentSection> = emptyList()
)

@Serializable
data class HeroImage(
    val url: String,
    val caption: String? = null,
    val photographer: String? = null
)

@Serializable
data class ContentSection(
    val id: String,
    val layout: SectionLayout,
    val title: String? = null,
    val subtitle: String? = null,
    val items: List<ContentItem> = emptyList()
)

@Serializable
enum class SectionLayout {
    TEXT_ONLY,
    IMAGE_FULL,
    IMAGE_GALLERY,
    TEXT_IMAGE_RIGHT,
    TEXT_IMAGE_LEFT,
    QUOTE,
    HIGHLIGHTS,
    TIMELINE,
    SPOTS_GRID,
    STATISTICS
}

@Serializable
data class ContentItem(
    val type: ContentType,
    val text: String? = null,
    val imageUrl: String? = null,
    val imageCaption: String? = null,
    val highlights: List<String>? = null,
    val timelineEvents: List<TimelineEvent>? = null,
    val spots: List<SpotCard>? = null,
    val statisticValue: String? = null,
    val statisticLabel: String? = null
)

@Serializable
enum class ContentType {
    PARAGRAPH,
    HEADING,
    IMAGE,
    QUOTE,
    LIST,
    HIGHLIGHTS,
    TIMELINE,
    SPOTS,
    STATISTIC
}

@Serializable
data class TimelineEvent(
    val year: String,
    val title: String,
    val description: String
)

@Serializable
data class SpotCard(
    val name: String,
    val imageUrl: String? = null,
    val description: String,
    val tags: List<String>? = null
)

// ==================== 景区索引模型 ====================

@Serializable
data class ScenicIndex(
    val version: Int,
    val lastUpdated: String,
    val scenics: List<ScenicIndexItem>
)

@Serializable
data class ScenicIndexItem(
    val scenicId: String,
    val name: String,
    val thumbnail: String,
    val description: String
)
