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
import com.example.scenic_avatar_guide_app.domain.model.ScenicMapBundle
import com.example.scenic_avatar_guide_app.domain.model.ScenicMapData
import com.example.scenic_avatar_guide_app.domain.model.ScenicRoute
import com.example.scenic_avatar_guide_app.domain.model.ScenicSpot
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
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
                updateScenicCenter(bundle.mapData.centerLat, bundle.mapData.centerLng)
                clearSearch()
                UiState.Success(bundle)
            } else {
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
            val bundle = withContext(Dispatchers.IO) {
                val base = routeData.scenicId?.let { mapDataRepository.loadMapBundle(it) }
                buildRouteBundle(base, routeData)
            }
            _uiState.value = if (bundle != null) {
                _selectedRouteId.value = null
                updateScenicCenter(bundle.mapData.centerLat, bundle.mapData.centerLng)
                clearSearch()
                UiState.Success(bundle)
            } else {
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
        if (routeData.spots.isEmpty()) return null

        val baseSpotByName = base?.area?.spots.orEmpty().associateBy { normalizePoiName(it.name) }
        val spots = routeData.spots.mapIndexed { index, spot ->
            val localSpot = baseSpotByName[normalizePoiName(spot.name)]
            ScenicSpot(
                id = routeSpotId(routeData, index),
                name = spot.name,
                description = spot.description ?: "",
                sortOrder = spot.order,
                lat = spot.lat,
                lng = spot.lng,
                imageUrl = spot.imageUrl ?: localSpot?.imageUrl
            )
        }
        val spotsWithLocation = spots.filter { it.lat != null && it.lng != null }
        val polyline = routeData.polyline?.takeIf { it.isNotEmpty() }
            ?: spotsWithLocation.mapNotNull { s ->
                s.lat?.let { la -> s.lng?.let { lng -> LatLngPoint(la, lng) } }
            }

        val route = ScenicRoute(
            routeId = routeData.routeId ?: routeData.title,
            name = routeData.title,
            color = "#1D7A6D",
            spotOrder = spotsWithLocation.map { it.id },
            polyline = polyline
        )

        return if (base != null) {
            ScenicMapBundle(
                area = base.area,
                spotsWithLocation = spotsWithLocation,
                mapData = base.mapData.copy(routes = listOf(route))
            )
        } else {
            val (centerLat, centerLng) = computeCenter(spotsWithLocation, polyline)
                ?: return null
            ScenicMapBundle(
                area = ScenicArea(
                    id = routeData.scenicId ?: routeData.routeId ?: "unknown",
                    name = routeData.title,
                    description = routeData.reason ?: "",
                    spots = spots
                ),
                spotsWithLocation = spotsWithLocation,
                mapData = ScenicMapData(
                    centerLat = centerLat,
                    centerLng = centerLng,
                    defaultZoom = 16.8f,
                    routes = listOf(route)
                )
            )
        }
    }

    private fun routeSpotId(routeData: RouteData, index: Int): String {
        val prefix = routeData.routeId?.takeIf { it.isNotBlank() } ?: routeData.title
        return "${prefix}_spot_$index"
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

    // ==================== POI 搜索 ====================

    private val _searchResults = MutableStateFlow<List<MapPoi>>(emptyList())
    val searchResults: StateFlow<List<MapPoi>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()

    /** 当前景区中心坐标，供搜索周边使用 */
    private var scenicCenter: LatLng? = null

    /**
     * 以当前景区中心为圆心做周边 POI 关键字搜索。
     * @param keyword 关键字，如「卫生间」「餐厅」「出口」
     */
    fun searchPois(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) {
            _searchResults.value = emptyList()
            _searchError.value = null
            return
        }
        val center = scenicCenter ?: run {
            _searchError.value = "景区坐标未就绪，请稍后再试"
            return
        }
        // 搜索 SDK 需隐私同意后才可用
        if (!amapPrivacyAgreed.value) {
            _searchError.value = "请先同意地图隐私政策"
            return
        }
        viewModelScope.launch {
            _isSearching.value = true
            _searchError.value = null
            val results = withContext(Dispatchers.IO) {
                runCatching { doPoiSearch(trimmed, center) }
            }
            _isSearching.value = false
            results.fold(
                onSuccess = { pois ->
                    _searchResults.value = pois
                    if (pois.isEmpty()) _searchError.value = "未找到相关地点"
                },
                onFailure = { _searchError.value = "搜索失败，请检查网络后重试" }
            )
        }
    }

    fun clearSearch() {
        _searchResults.value = emptyList()
        _searchError.value = null
    }

    /**
     * 记录当前景区中心，供搜索使用。在 loadMap / loadRouteMap 成功后调用。
     */
    private fun updateScenicCenter(lat: Double, lng: Double) {
        scenicCenter = LatLng(lat, lng)
    }

    private suspend fun doPoiSearch(keyword: String, center: LatLng): List<MapPoi> =
        suspendCancellableCoroutine { cont ->
            val query = PoiSearch.Query(keyword, "", "").apply {
                pageSize = 20
                pageNum = 0
            }
            val search = PoiSearch(appContext, query).apply {
                // 周边 3000 米
                bound = PoiSearch.SearchBound(LatLonPoint(center.latitude, center.longitude), 3000)
                setOnPoiSearchListener(object : PoiSearch.OnPoiSearchListener {
                    override fun onPoiSearched(result: PoiResult?, rCode: Int) {
                        if (cont.isCompleted) return
                        if (rCode != 1000) {
                            cont.resume(emptyList())
                            return
                        }
                        val pois = result?.pois?.mapNotNull { it.toMapPoi() } ?: emptyList()
                        cont.resume(pois)
                    }

                    override fun onPoiItemSearched(item: PoiItem?, rCode: Int) {}
                })
            }
            cont.invokeOnCancellation { runCatching { search } }
            search.searchPOIAsyn()
        }

    private fun PoiItem.toMapPoi(): MapPoi? {
        val point = latLonPoint ?: return null
        return MapPoi(
            poiId = poiId ?: "",
            name = title ?: "未知地点",
            address = snippet ?: "",
            lat = point.latitude,
            lng = point.longitude
        )
    }

    override fun onCleared() {
        super.onCleared()
        stopLocationUpdates()
    }

    private fun normalizePoiName(value: String): String =
        value
            .trim()
            .replace("·", "")
            .replace("・", "")
            .replace(" ", "")
            .replace("　", "")
            .replace("-", "")
            .replace("－", "")
            .replace("景区", "")
            .replace("景点", "")
}
