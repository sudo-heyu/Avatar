package com.example.scenic_avatar_guide_app.ui.screens.map

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import com.amap.api.maps.model.LatLng
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.core.PoiItem
import com.amap.api.services.poisearch.PoiResult
import com.amap.api.services.poisearch.PoiSearch
import com.example.scenic_avatar_guide_app.core.common.UiState
import com.example.scenic_avatar_guide_app.data.local.SettingsDataStore
import com.example.scenic_avatar_guide_app.data.repository.MapDataRepository
import com.example.scenic_avatar_guide_app.domain.model.LatLngPoint
import com.example.scenic_avatar_guide_app.domain.model.MapPoi
import com.example.scenic_avatar_guide_app.domain.model.RouteData
import com.example.scenic_avatar_guide_app.domain.model.ScenicArea
import com.example.scenic_avatar_guide_app.domain.model.ScenicFacility
import com.example.scenic_avatar_guide_app.domain.model.ScenicMapBundle
import com.example.scenic_avatar_guide_app.domain.model.ScenicMapData
import com.example.scenic_avatar_guide_app.domain.model.ScenicRoute
import com.example.scenic_avatar_guide_app.domain.model.ScenicSpot
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@HiltViewModel
class MapViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val mapDataRepository: MapDataRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<ScenicMapBundle>>(UiState.Loading)
    val uiState: StateFlow<UiState<ScenicMapBundle>> = _uiState.asStateFlow()

    private val _userLocation = MutableStateFlow<LatLng?>(null)
    val userLocation: StateFlow<LatLng?> = _userLocation.asStateFlow()

    private val _selectedRouteId = MutableStateFlow<String?>(null)
    val selectedRouteId: StateFlow<String?> = _selectedRouteId.asStateFlow()

    private val _locationPermissionGranted = MutableStateFlow(false)
    val locationPermissionGranted: StateFlow<Boolean> = _locationPermissionGranted.asStateFlow()

    private var locationClient: AMapLocationClient? = null
    private var searchableScenicSpots: List<ScenicSpot> = emptyList()
    private var searchableFacilities: List<ScenicFacility> = emptyList()

    /**
     * 当前选中的景区 ID，来自 SettingsDataStore。
     */
    val currentScenicId: StateFlow<String?> = settingsDataStore.scenicId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * 高德隐私协议是否已同意（全局持久化）。
     */
    val amapPrivacyAgreed: StateFlow<Boolean> = settingsDataStore.amapPrivacyAgreed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setAmapPrivacyAgreed(agreed: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setAmapPrivacyAgreed(agreed)
        }
    }

    /**
     * 加载指定景区的地图数据。
     */
    fun loadMap(scenicId: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val bundle = withContext(Dispatchers.IO) {
                mapDataRepository.loadMapBundle(scenicId)
            }
            _uiState.value = if (bundle != null) {
                _selectedRouteId.value = null
                updateSearchablePois(bundle)
                updateScenicCenter(bundle.mapData.centerLat, bundle.mapData.centerLng)
                clearSearch()
                UiState.Success(bundle)
            } else {
                updateSearchablePois(null)
                UiState.Error("暂无该景区的地图数据")
            }
        }
    }

    /**
     * 根据后端返回的路线数据加载地图。
     */
    fun loadRouteMap(routeData: RouteData) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val bundleResult = withContext(Dispatchers.IO) {
                runCatching {
                    val base = resolveRouteBaseBundle(routeData)
                    buildRouteBundle(base, routeData)
                }
            }
            val bundle = bundleResult.getOrNull()
            _uiState.value = if (bundle != null) {
                _selectedRouteId.value = plannedRouteId(routeData)
                updateSearchablePois(bundle)
                updateScenicCenter(bundle.mapData.centerLat, bundle.mapData.centerLng)
                clearSearch()
                UiState.Success(bundle)
            } else {
                updateSearchablePois(null)
                UiState.Error("路线数据无法显示")
            }
        }
    }

    /**
     * 切换要高亮显示的路线。
     */
    fun selectRoute(routeId: String?) {
        _selectedRouteId.value = routeId
    }

    /**
     * 记录定位权限是否已授予。
     */
    fun setLocationPermissionGranted(granted: Boolean) {
        _locationPermissionGranted.value = granted
    }

    /**
     * 启动高德定位客户端。需要在隐私合规同意且权限已授予后调用。
     */
    fun startLocationUpdates(context: Context) {
        if (!_locationPermissionGranted.value) return
        stopLocationUpdates()

        try {
            val client = AMapLocationClient(context.applicationContext).apply {
                setLocationOption(
                    AMapLocationClientOption().apply {
                        locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                        interval = 2000
                        isOnceLocation = false
                        isNeedAddress = false
                    }
                )
                setLocationListener(locationListener)
            }
            client.startLocation()
            locationClient = client
        } catch (_: Exception) {
            // 定位客户端初始化失败（如隐私未同意），静默忽略
        }
    }

    /**
     * 停止定位更新。
     */
    fun stopLocationUpdates() {
        locationClient?.stopLocation()
        locationClient?.onDestroy()
        locationClient = null
    }

    private val locationListener = AMapLocationListener { location: AMapLocation? ->
        if (location != null && location.errorCode == AMapLocation.LOCATION_SUCCESS) {
            _userLocation.value = LatLng(location.latitude, location.longitude)
        }
    }

    /**
     * 将后端返回的 RouteData 与本地景区底图合并为 ScenicMapBundle。
     * 若本地无对应景区配置，则完全根据 RouteData 的坐标合成。
     */
    private fun buildRouteBundle(
        base: ScenicMapBundle?,
        routeData: RouteData
    ): ScenicMapBundle? {
        if (routeData.spots.isEmpty() && base == null) return null

        val baseSpotByName = base?.area?.spots.orEmpty().associateBy { normalizePoiName(it.name) }
        val spots = routeData.spots.mapIndexed { index, spot ->
            val localSpot = findLocalSpotByRouteName(baseSpotByName, spot.name)
            ScenicSpot(
                id = localSpot?.id ?: routeSpotId(routeData, index),
                name = spot.name,
                description = spot.description ?: localSpot?.description ?: "",
                intro = localSpot?.intro ?: "",
                sortOrder = spot.order,
                lat = spot.lat ?: localSpot?.lat,
                lng = spot.lng ?: localSpot?.lng,
                imageUrl = spot.imageUrl ?: localSpot?.imageUrl
            )
        }
        val routeSpotsWithLocation = spots.filter { spot ->
            val lat = spot.lat
            val lng = spot.lng
            lat != null && lng != null && isValidLatLng(lat, lng)
        }
        val polyline = routeData.polyline
            ?.filter { isValidLatLng(it.lat, it.lng) }
            ?.takeIf { it.isNotEmpty() }
            ?: routeSpotsWithLocation.mapNotNull { s ->
                s.lat?.let { la -> s.lng?.let { lng -> LatLngPoint(la, lng) } }
            }

        val route = ScenicRoute(
            routeId = plannedRouteId(routeData),
            name = routeData.title,
            color = "#1D7A6D",
            spotOrder = routeSpotsWithLocation.map { it.id },
            polyline = polyline
        )

        return if (base != null) {
            ScenicMapBundle(
                area = base.area,
                spotsWithLocation = base.spotsWithLocation.filter {
                    val lat = it.lat
                    val lng = it.lng
                    lat != null && lng != null && isValidLatLng(lat, lng)
                },
                mapData = base.mapData.copy(routes = mergePlannedRoute(route, base.mapData.routes)),
                facilities = base.facilities
            )
        } else {
            val (centerLat, centerLng) = computeCenter(routeSpotsWithLocation, polyline)
                ?: return null
            ScenicMapBundle(
                area = ScenicArea(
                    id = routeData.scenicId ?: routeData.routeId ?: "unknown",
                    name = routeData.title,
                    description = routeData.reason ?: "",
                    spots = spots
                ),
                spotsWithLocation = routeSpotsWithLocation,
                mapData = ScenicMapData(
                    centerLat = centerLat,
                    centerLng = centerLng,
                    defaultZoom = 16.8f,
                    routes = listOf(route)
                ),
                facilities = emptyList()
            )
        }
    }

    private fun resolveRouteBaseBundle(routeData: RouteData): ScenicMapBundle? {
        val candidates = listOfNotNull(
            routeData.scenicId?.takeIf { it.isNotBlank() },
            currentScenicId.value?.takeIf { it.isNotBlank() }
        ).distinct()

        return candidates.firstNotNullOfOrNull { scenicId ->
            mapDataRepository.loadMapBundle(scenicId)
        } ?: inferRouteBaseBundle(routeData)
    }

    private fun inferRouteBaseBundle(routeData: RouteData): ScenicMapBundle? {
        return mapDataRepository.loadAllMapBundles()
            .map { bundle -> bundle to routeScenicMatchScore(routeData, bundle) }
            .filter { (_, score) -> score > 0 }
            .maxByOrNull { (_, score) -> score }
            ?.first
    }

    private fun routeScenicMatchScore(routeData: RouteData, bundle: ScenicMapBundle): Int {
        var score = 0
        val routeScenicId = routeData.scenicId?.let { normalizePoiName(it) }.orEmpty()
        val areaId = normalizePoiName(bundle.area.id)
        val areaName = normalizePoiName(bundle.area.name)
        val routeTitle = normalizePoiName(routeData.title)

        if (routeScenicId.isNotBlank()) {
            if (routeScenicId == areaId || routeScenicId == areaName) score += 100
            if (areaName.contains(routeScenicId) || routeScenicId.contains(areaName)) score += 60
        }
        if (routeTitle.contains(areaName) || areaName.contains(routeTitle)) score += 40

        val localSpotNames = bundle.area.spots.map { normalizePoiName(it.name) }
        routeData.spots.forEach { routeSpot ->
            val routeSpotName = normalizePoiName(routeSpot.name)
            if (routeSpotName.isNotBlank() && localSpotNames.any { localName ->
                    localName == routeSpotName ||
                        localName.contains(routeSpotName) ||
                        routeSpotName.contains(localName)
                }
            ) {
                score += 10
            }
        }
        return score
    }

    private fun routeSpotId(routeData: RouteData, index: Int): String {
        val prefix = plannedRouteId(routeData)
        return "${prefix}_spot_$index"
    }

    private fun plannedRouteId(routeData: RouteData): String =
        routeData.routeId?.takeIf { it.isNotBlank() } ?: routeData.title

    private fun mergePlannedRoute(
        plannedRoute: ScenicRoute,
        baseRoutes: List<ScenicRoute>
    ): List<ScenicRoute> =
        listOf(plannedRoute) + baseRoutes.filter { it.routeId != plannedRoute.routeId }

    private fun findLocalSpotByRouteName(
        baseSpotByName: Map<String, ScenicSpot>,
        routeSpotName: String
    ): ScenicSpot? {
        val normalizedName = normalizePoiName(routeSpotName)
        if (normalizedName.isBlank()) return null
        return baseSpotByName[normalizedName]
            ?: baseSpotByName.entries.firstOrNull { (localName, _) ->
                localName.contains(normalizedName) || normalizedName.contains(localName)
            }?.value
    }

    private fun computeCenter(
        spots: List<ScenicSpot>,
        polyline: List<LatLngPoint>
    ): Pair<Double, Double>? {
        val points = polyline.takeIf { it.isNotEmpty() }
            ?: spots.mapNotNull { spot ->
                spot.lat?.let { lat ->
                    spot.lng?.let { lng -> LatLngPoint(lat, lng) }
                }
            }
        if (points.isEmpty()) return null
        val avgLat = points.map { it.lat }.average()
        val avgLng = points.map { it.lng }.average()
        return avgLat to avgLng
    }

    private fun isValidLatLng(lat: Double, lng: Double): Boolean =
        lat in -90.0..90.0 && lng in -180.0..180.0

    // ==================== POI 搜索 ====================

    private val _searchResults = MutableStateFlow<List<MapPoi>>(emptyList())
    val searchResults: StateFlow<List<MapPoi>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()

    /** 当前景区中心坐标，供搜索周边使用 */
    private var scenicCenter: LatLng? = null
    private var searchJob: Job? = null

    /**
     * 以当前景区中心为圆心做周边 POI 关键字搜索。
     * @param keyword 关键字，如「卫生间」「餐厅」「出口」
     */
    fun searchPois(keyword: String) {
        searchJob?.cancel()
        searchJob = null
        _isSearching.value = false
        _searchResults.value = emptyList()
        _searchError.value = null

        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) {
            return
        }
        val center = scenicCenter ?: run {
            _searchError.value = "景区坐标未就绪，请稍后再试"
            return
        }
        // 搜索 SDK 需隐私同意后才可用
        if (!amapPrivacyAgreed.value) {
            val localPois = searchLocalScenicPois(trimmed)
            _searchResults.value = localPois
            if (localPois.isEmpty()) {
                _searchError.value = "请先同意地图隐私政策"
            }
            return
        }
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            _isSearching.value = true
            try {
                val localPois = searchLocalScenicPois(trimmed)
                if (localPois.isNotEmpty()) {
                    _searchResults.value = localPois
                }
                val profile = resolvePoiSearchProfile(trimmed)
                val pois = withContext(Dispatchers.IO) {
                    doPoiSearch(profile, center)
                }
                if (searchJob !== coroutineContext[Job]) return@launch
                val mergedPois = mergePoiResults(localPois, pois)
                if (mergedPois.isEmpty()) {
                    _searchError.value = "未找到相关地点"
                }
                _searchResults.value = mergedPois
            } catch (e: CancellationException) {
                throw e
            } catch (e: PoiSearchException) {
                if (searchJob === coroutineContext[Job]) {
                    val localPois = searchLocalScenicPois(trimmed)
                    _searchResults.value = localPois
                    if (localPois.isEmpty()) {
                        _searchError.value = poiSearchErrorMessage(e.rCode)
                    }
                }
            } catch (_: Exception) {
                if (searchJob === coroutineContext[Job]) {
                    val localPois = searchLocalScenicPois(trimmed)
                    _searchResults.value = localPois
                    if (localPois.isEmpty()) {
                        _searchError.value = "搜索失败，请检查网络后重试"
                    }
                }
            } finally {
                if (searchJob === coroutineContext[Job]) {
                    _isSearching.value = false
                    searchJob = null
                }
            }
        }
        searchJob = job
        job.start()
    }

    /**
     * 输入框实时候选：只查本地景区点位，不触发高德网络请求。
     */
    fun previewSearchPois(keyword: String) {
        searchJob?.cancel()
        searchJob = null
        _isSearching.value = false
        _searchError.value = null

        val trimmed = keyword.trim()
        _searchResults.value = if (trimmed.isBlank()) {
            emptyList()
        } else {
            searchLocalScenicPois(trimmed)
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        searchJob = null
        _isSearching.value = false
        _searchResults.value = emptyList()
        _searchError.value = null
    }

    /**
     * 记录当前景区中心，供搜索使用。在 loadMap / loadRouteMap 成功后调用。
     */
    private fun updateScenicCenter(lat: Double, lng: Double) {
        scenicCenter = LatLng(lat, lng)
    }

    private fun updateSearchablePois(bundle: ScenicMapBundle?) {
        searchableScenicSpots = bundle?.spotsWithLocation.orEmpty()
        searchableFacilities = bundle?.facilities.orEmpty()
    }

    private fun searchLocalScenicPois(keyword: String): List<MapPoi> {
        val normalizedKeyword = normalizePoiName(keyword)
        if (normalizedKeyword.isBlank()) return emptyList()

        val spotPois = searchableScenicSpots
            .filter { spot ->
                val lat = spot.lat
                val lng = spot.lng
                lat != null && lng != null &&
                    (normalizePoiName(spot.name).contains(normalizedKeyword) ||
                        normalizePoiName(spot.description).contains(normalizedKeyword))
            }
            .map { spot ->
                MapPoi(
                    poiId = "local:${spot.id}",
                    name = spot.name,
                    address = spot.description,
                    lat = spot.lat ?: 0.0,
                    lng = spot.lng ?: 0.0,
                    localSpotId = spot.id,
                    category = "spot"
                )
            }

        val facilityPois = searchableFacilities
            .filter { facility -> facility.matchesKeyword(normalizedKeyword) }
            .map { facility ->
                MapPoi(
                    poiId = "facility:${facility.id}",
                    name = facility.name,
                    address = facility.address.ifBlank { facility.categoryLabel() },
                    lat = facility.lat,
                    lng = facility.lng,
                    category = facility.category
                )
            }

        return spotPois + facilityPois
    }

    private fun mergePoiResults(localPois: List<MapPoi>, remotePois: List<MapPoi>): List<MapPoi> {
        val seen = mutableSetOf<String>()
        val enrichedRemotePois = remotePois.map { poi ->
            poi.localSpotId?.let { poi } ?: findMatchingLocalSpot(poi)?.let { poi.copy(localSpotId = it.id) } ?: poi
        }
        return (localPois + enrichedRemotePois).filter { poi ->
            val key = "${normalizePoiName(poi.name)}:${poi.lat.roundToCoordinateKey()}:${poi.lng.roundToCoordinateKey()}"
            seen.add(key)
        }
    }

    private fun findMatchingLocalSpot(poi: MapPoi): ScenicSpot? {
        val normalizedPoiName = normalizePoiName(poi.name)
        if (normalizedPoiName.isBlank()) return null
        return searchableScenicSpots.firstOrNull { spot ->
            val lat = spot.lat
            val lng = spot.lng
            lat != null && lng != null &&
                (normalizePoiName(spot.name) == normalizedPoiName ||
                    normalizePoiName(spot.name).contains(normalizedPoiName) ||
                    normalizedPoiName.contains(normalizePoiName(spot.name))) &&
                distanceMeters(lat, lng, poi.lat, poi.lng) <= LOCAL_POI_MATCH_RADIUS_M
        }
    }

    private suspend fun doPoiSearch(profile: PoiSearchProfile, center: LatLng): List<MapPoi> =
        suspendCancellableCoroutine { cont ->
            val query = PoiSearch.Query(profile.keyword, profile.types, "").apply {
                pageSize = 20
                pageNum = 0
            }
            val search = PoiSearch(appContext, query).apply {
                bound = PoiSearch.SearchBound(
                    LatLonPoint(center.latitude, center.longitude),
                    profile.radiusMeters
                )
                setOnPoiSearchListener(object : PoiSearch.OnPoiSearchListener {
                    override fun onPoiSearched(result: PoiResult?, rCode: Int) {
                        if (!cont.isActive) return
                        if (rCode != 1000) {
                            cont.resumeWithException(PoiSearchException(rCode))
                            return
                        }
                        val pois = result?.pois
                            ?.mapNotNull { it.toMapPoi(profile.category) }
                            ?.sortedBy { distanceMeters(center.latitude, center.longitude, it.lat, it.lng) }
                            ?: emptyList()
                        cont.resume(pois)
                    }

                    override fun onPoiItemSearched(item: PoiItem?, rCode: Int) {}
                })
            }
            cont.invokeOnCancellation {
                runCatching { search.setOnPoiSearchListener(null) }
            }
            search.searchPOIAsyn()
        }

    private fun PoiItem.toMapPoi(category: String?): MapPoi? {
        val point = latLonPoint ?: return null
        return MapPoi(
            poiId = poiId ?: "",
            name = title ?: "未知地点",
            address = snippet ?: "",
            lat = point.latitude,
            lng = point.longitude,
            category = category
        )
    }

    override fun onCleared() {
        super.onCleared()
        stopLocationUpdates()
    }

    private fun normalizePoiName(value: String): String =
        value
            .trim()
            .lowercase()
            .replace("·", "")
            .replace("・", "")
            .replace(" ", "")
            .replace("　", "")
            .replace("-", "")
            .replace("－", "")
            .replace("景区", "")
            .replace("景点", "")
}

private class PoiSearchException(val rCode: Int) : Exception("Poi search failed: $rCode")

private data class PoiSearchProfile(
    val keyword: String,
    val types: String = "",
    val radiusMeters: Int = DEFAULT_POI_SEARCH_RADIUS_M,
    val category: String? = null
)

private fun resolvePoiSearchProfile(keyword: String): PoiSearchProfile {
    val normalized = keyword
        .trim()
        .lowercase()
        .replace(" ", "")
        .replace("　", "")
    return when {
        normalized in setOf("卫生间", "洗手间", "厕所", "公共厕所", "公厕", "wc", "toilet", "restroom") ->
            PoiSearchProfile(keyword = "公共厕所", types = "200300", category = "restroom")
        normalized in setOf("餐饮", "餐厅", "饭店", "吃饭", "美食", "素斋", "小吃") ->
            PoiSearchProfile(keyword = keyword, types = "050000", category = "dining")
        normalized in setOf("停车", "停车场", "车位", "停车点") ->
            PoiSearchProfile(keyword = "停车场", types = "150900", category = "parking")
        normalized in setOf("游客中心", "游客服务中心", "服务中心", "咨询处", "游客服务") ->
            PoiSearchProfile(keyword = "游客服务中心", category = "visitor_center")
        normalized in setOf("出口", "入口", "出入口", "检票口", "售票处") ->
            PoiSearchProfile(keyword = keyword)
        else -> PoiSearchProfile(keyword = keyword)
    }
}

private fun poiSearchErrorMessage(rCode: Int): String =
    when (rCode) {
        1008 -> "高德搜索鉴权失败（1008），请检查 Android Key 的包名/SHA1/安全码配置"
        else -> "搜索失败（错误码 $rCode），请稍后重试"
    }

private fun ScenicFacility.matchesKeyword(normalizedKeyword: String): Boolean {
    val terms = listOf(name, address, category, categoryLabel()) + categoryAliases()
    return terms.any { term ->
        val normalizedTerm = normalizeFacilityTerm(term)
        normalizedTerm.isNotBlank() &&
            (normalizedTerm.contains(normalizedKeyword) || normalizedKeyword.contains(normalizedTerm))
    }
}

private fun ScenicFacility.categoryAliases(): List<String> =
    when (category) {
        "restroom" -> listOf("卫生间", "洗手间", "厕所", "公共厕所", "公厕", "wc", "toilet", "restroom")
        "dining" -> listOf("餐饮", "餐厅", "饭店", "吃饭", "美食", "素斋", "小吃", "咖啡")
        "parking" -> listOf("停车", "停车场", "车位", "停车点")
        "visitor_center" -> listOf("游客中心", "游客服务中心", "服务中心", "咨询处", "游客服务")
        else -> emptyList()
    }

private fun ScenicFacility.categoryLabel(): String =
    when (category) {
        "restroom" -> "卫生间"
        "dining" -> "餐饮"
        "parking" -> "停车场"
        "visitor_center" -> "游客中心"
        else -> category
    }

private fun normalizeFacilityTerm(value: String): String =
    value
        .trim()
        .lowercase()
        .replace("·", "")
        .replace("・", "")
        .replace(" ", "")
        .replace("　", "")
        .replace("-", "")
        .replace("－", "")
        .replace("景区", "")
        .replace("景点", "")

private fun Double.roundToCoordinateKey(): String = String.format("%.5f", this)

private const val LOCAL_POI_MATCH_RADIUS_M = 80.0
private const val DEFAULT_POI_SEARCH_RADIUS_M = 4_000

private fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val sinDLat = Math.sin(dLat / 2)
    val sinDLng = Math.sin(dLng / 2)
    val h = sinDLat * sinDLat +
        Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * sinDLng * sinDLng
    return 2 * r * Math.asin(Math.min(1.0, Math.sqrt(h)))
}
