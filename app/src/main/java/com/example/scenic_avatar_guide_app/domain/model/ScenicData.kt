package com.example.scenic_avatar_guide_app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ScenicArea(
    val id: String,
    val name: String,
    val description: String = "",
    val spots: List<ScenicSpot>
)

@Serializable
data class ScenicSpot(
    val id: String,
    val name: String,
    val description: String = "",
    val sortOrder: Int = 0,
    val lat: Double? = null,
    val lng: Double? = null,
    val imageUrl: String? = null,
    @SerialName("coordinate_system")
    val coordinateSystem: String? = null,
    @SerialName("coordinate_locked")
    val coordinateLocked: Boolean = false
)

/**
 * 景区地图配置（前端本地数据）
 */
@Serializable
data class ScenicMapData(
    @SerialName("center_lat")
    val centerLat: Double,
    @SerialName("center_lng")
    val centerLng: Double,
    @SerialName("default_zoom")
    val defaultZoom: Float,
    @SerialName("style_json_path")
    val styleJsonPath: String? = null,
    @SerialName("coordinate_system")
    val coordinateSystem: String = "GCJ02",
    val routes: List<ScenicRoute> = emptyList()
)

/**
 * 景区地图中的推荐路线
 */
@Serializable
data class ScenicRoute(
    @SerialName("route_id")
    val routeId: String,
    val name: String,
    val color: String = "#1D7A6D",
    @SerialName("spot_order")
    val spotOrder: List<String> = emptyList(),
    val polyline: List<LatLngPoint> = emptyList()
)

/**
 * 景区地图完整数据包（景点 + 地图配置 + 路线）
 */
data class ScenicMapBundle(
    val area: ScenicArea,
    val spotsWithLocation: List<ScenicSpot>,
    val mapData: ScenicMapData
)

/**
 * 高德 POI 搜索结果（与 SDK 类型解耦，供 UI 层使用）
 */
data class MapPoi(
    val poiId: String,
    val name: String,
    val address: String,
    val lat: Double,
    val lng: Double
)

@Serializable
data class PublicScenicListResponse(
    val items: List<PublicScenicInfo> = emptyList()
)

@Serializable
data class PublicScenicInfo(
    @SerialName("scenic_id")
    val scenicId: String,
    val name: String,
    val description: String = ""
)

@Serializable
data class PublicScenicSpotListResponse(
    val items: List<PublicScenicSpotInfo> = emptyList()
)

@Serializable
data class PublicScenicSpotInfo(
    @SerialName("spot_id")
    val spotId: String,
    val name: String,
    val description: String = "",
    @SerialName("sort_order")
    val sortOrder: Int = 0
)
