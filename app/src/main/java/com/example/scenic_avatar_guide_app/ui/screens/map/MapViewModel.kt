package com.example.scenic_avatar_guide_app.ui.screens.map

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import com.amap.api.maps.model.LatLng
import com.example.scenic_avatar_guide_app.core.common.UiState
import com.example.scenic_avatar_guide_app.data.local.SettingsDataStore
import com.example.scenic_avatar_guide_app.data.repository.MapDataRepository
import com.example.scenic_avatar_guide_app.domain.model.LatLngPoint
import com.example.scenic_avatar_guide_app.domain.model.RouteData
import com.example.scenic_avatar_guide_app.domain.model.ScenicArea
import com.example.scenic_avatar_guide_app.domain.model.ScenicMapBundle
import com.example.scenic_avatar_guide_app.domain.model.ScenicMapData
import com.example.scenic_avatar_guide_app.domain.model.ScenicRoute
import com.example.scenic_avatar_guide_app.domain.model.ScenicSpot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    private val mapDataRepository: MapDataRepository,
    settingsDataStore: SettingsDataStore
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
     * 加载指定景区的地图数据。
     */
    fun loadMap(scenicId: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val bundle = withContext(Dispatchers.IO) {
                mapDataRepository.loadMapBundle(scenicId)
            }
            _uiState.value = if (bundle != null) {
                _selectedRouteId.value = bundle.mapData.routes.firstOrNull()?.routeId
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
                _selectedRouteId.value = bundle.mapData.routes.firstOrNull()?.routeId
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

        val spots = routeData.spots.mapIndexed { index, spot ->
            ScenicSpot(
                id = routeSpotId(routeData, index),
                name = spot.name,
                description = spot.description ?: "",
                sortOrder = spot.order,
                lat = spot.lat,
                lng = spot.lng
            )
        }
        val spotsWithLocation = spots.filter { it.lat != null && it.lng != null }
        val polyline = routeData.polyline?.takeIf { it.isNotEmpty() }
            ?: spots.map { LatLngPoint(it.lat!!, it.lng!!) }

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
                    defaultZoom = 15f,
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

    override fun onCleared() {
        super.onCleared()
        stopLocationUpdates()
    }
}
