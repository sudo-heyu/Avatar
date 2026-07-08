package com.example.scenic_avatar_guide_app.ui.screens.map

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.MapsInitializer
import com.amap.api.maps.model.BitmapDescriptor
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.CustomMapStyleOptions
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.MyLocationStyle
import com.amap.api.maps.model.PolylineOptions
import com.amap.api.location.AMapLocationClient
import com.amap.api.services.core.ServiceSettings
import com.example.scenic_avatar_guide_app.R
import com.example.scenic_avatar_guide_app.core.common.UiState
import com.example.scenic_avatar_guide_app.domain.model.RouteData
import com.example.scenic_avatar_guide_app.domain.model.MapPoi
import com.example.scenic_avatar_guide_app.domain.model.ScenicMapBundle
import com.example.scenic_avatar_guide_app.domain.model.ScenicRoute
import com.example.scenic_avatar_guide_app.domain.model.ScenicSpot
import com.example.scenic_avatar_guide_app.ui.theme.*
import coil.compose.AsyncImage

private const val FOLLOW_ZOOM = 17f
/** 用户距景区中心在此范围内（米）才以用户位置为初始聚焦点 */
private const val NEAR_SCENIC_RADIUS_M = 5_000.0
private const val POI_MARKER_OBJECT_PREFIX = "poi:"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapPortalScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    routeData: RouteData? = null,
    viewModel: MapViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val userLocation by viewModel.userLocation.collectAsStateWithLifecycle()
    val locationPermissionGranted by viewModel.locationPermissionGranted.collectAsStateWithLifecycle()
    val currentScenicId by viewModel.currentScenicId.collectAsStateWithLifecycle()
    val amapPrivacyAgreed by viewModel.amapPrivacyAgreed.collectAsStateWithLifecycle()
    val selectedRouteId by viewModel.selectedRouteId.collectAsStateWithLifecycle()

    // 隐私同意状态：未同意时不初始化 MapView。
    // privacyAgreed 来自 DataStore（全局持久化，同意后不再弹窗）；
    // showPrivacyDialog 控制弹窗显隐，仅在尚未同意时弹出一次。
    var showPrivacyDialog by remember { mutableStateOf(!amapPrivacyAgreed) }

    // 将 SDK 的 agree 状态与持久化状态对齐。已同意用户每次重启进入地图时
    // 重新声明 agree(true)，避免 SDK 在重启后丢失同意状态（默认未同意）。
    LaunchedEffect(amapPrivacyAgreed) {
        runCatching {
            MapsInitializer.updatePrivacyAgree(context, amapPrivacyAgreed)
            AMapLocationClient.updatePrivacyAgree(context, amapPrivacyAgreed)
            // 搜索 SDK 的 agree 必须与地图/定位同步，否则 PoiSearch 仍会崩溃
            ServiceSettings.updatePrivacyAgree(context, amapPrivacyAgreed)
        }
    }

    // 是否正在跟踪用户位置（FAB 切换；用户拖图时自动取消）
    var isTrackingUser by remember { mutableStateOf(false) }

    // 进入地图后的一次性初始聚焦，仅执行一次
    var initialFocusDone by remember { mutableStateOf(false) }

    // 当前选中的景点（Marker 点击 / 信息卡），第 3 批消费
    var selectedSpotId by remember { mutableStateOf<String?>(null) }

    // 当前选中的 POI 搜索结果（点击结果项时设置，弹信息卡）
    var selectedPoi by remember { mutableStateOf<MapPoi?>(null) }

    // 搜索框文本与是否展开结果面板
    var searchText by remember { mutableStateOf("") }
    var showSearchPanel by remember { mutableStateOf(false) }

    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val searchError by viewModel.searchError.collectAsStateWithLifecycle()

    // 根据传入的 routeData 或当前景区 ID 加载地图。
    // 路线入口也监听 currentScenicId：当后端 route_data 未带 scenic_id 时，
    // 当前景区 ID 到达后可重新解析成本地完整景区地图。
    LaunchedEffect(routeData, currentScenicId) {
        if (routeData != null) {
            viewModel.loadRouteMap(routeData)
        } else {
            currentScenicId?.let { viewModel.loadMap(it) }
        }
    }

    // 页面退出时停止定位，避免 ViewModel 复用时定位任务泄漏
    DisposableEffect(Unit) {
        onDispose { viewModel.stopLocationUpdates() }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Surface,
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .navigationBarsPadding()
        ) {
            when (val state = uiState) {
                is UiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Primary)
                    }
                }

                is UiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = state.message,
                            color = Error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                is UiState.Success -> {
                    val bundle = state.data

                    LaunchedEffect(bundle) {
                        searchText = ""
                        showSearchPanel = false
                        selectedSpotId = null
                        selectedPoi = null
                        initialFocusDone = false
                    }

                    if (showPrivacyDialog && !amapPrivacyAgreed) {
                        PrivacyConsentDialog(
                            onAgree = {
                                MapsInitializer.updatePrivacyAgree(context, true)
                                AMapLocationClient.updatePrivacyAgree(context, true)
                                ServiceSettings.updatePrivacyAgree(context, true)
                                viewModel.setAmapPrivacyAgreed(true)
                                showPrivacyDialog = false
                            },
                            onDisagree = {
                                MapsInitializer.updatePrivacyAgree(context, false)
                                AMapLocationClient.updatePrivacyAgree(context, false)
                                ServiceSettings.updatePrivacyAgree(context, false)
                                showPrivacyDialog = false
                            }
                        )
                    }

                    if (amapPrivacyAgreed) {
                        MapViewContainer(
                            bundle = bundle,
                            selectedSpotId = selectedSpotId,
                            selectedRouteId = selectedRouteId,
                            searchResults = searchResults,
                            userLocation = userLocation,
                            isTrackingUser = isTrackingUser,
                            locationPermissionGranted = locationPermissionGranted,
                            initialFocusDone = initialFocusDone,
                            selectedPoi = selectedPoi,
                            onInitialFocusDone = { initialFocusDone = true },
                            onUserDragged = { isTrackingUser = false },
                            onMapTap = {
                                selectedSpotId = null
                                selectedPoi = null
                            },
                            onMarkerClicked = {
                                when {
                                    it?.startsWith(POI_MARKER_OBJECT_PREFIX) == true -> {
                                        val markerKey = it.removePrefix(POI_MARKER_OBJECT_PREFIX)
                                        searchResults.firstOrNull { poi -> poiMarkerKey(poi) == markerKey }?.let { poi ->
                                            selectedPoi = poi
                                            selectedSpotId = poi.localSpotId
                                        }
                                    }
                                    else -> {
                                        selectedPoi = null
                                        selectedSpotId = it
                                    }
                                }
                            },
                            onLocationPermissionResult = { granted ->
                                viewModel.setLocationPermissionGranted(granted)
                                if (granted) viewModel.startLocationUpdates(context)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (!showPrivacyDialog) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = stringResource(R.string.map_privacy_required),
                                    color = TextSecondary,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Spacer(Modifier.height(12.dp))
                                Button(onClick = { showPrivacyDialog = true }) {
                                    Text(stringResource(R.string.map_privacy_redisplay))
                                }
                            }
                        }
                    }

                    // 顶部叠加层：搜索框 + (搜索结果面板 / 路线切换栏) 纵向堆叠，
                    // 避免绝对定位在不同字号/密度下相互重叠
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .padding(top = 12.dp, start = 12.dp, end = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MapSearchBar(
                            text = searchText,
                            isSearching = isSearching,
                            onTextChange = {
                                if (it != searchText) {
                                    searchText = it
                                    selectedPoi = null
                                    showSearchPanel = it.isNotBlank()
                                    viewModel.previewSearchPois(it)
                                }
                            },
                            onSearch = {
                                if (searchText.isNotBlank()) {
                                    viewModel.searchPois(searchText)
                                    showSearchPanel = true
                                } else {
                                    viewModel.clearSearch()
                                    showSearchPanel = false
                                }
                            },
                            onClear = {
                                searchText = ""
                                selectedPoi = null
                                viewModel.clearSearch()
                                showSearchPanel = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        // 搜索结果面板与路线切换栏互斥，避免同时占位
                        if (showSearchPanel) {
                            SearchResultsPanel(
                                results = searchResults,
                                isSearching = isSearching,
                                error = searchError,
                                hasQuery = searchText.isNotBlank(),
                                userLocation = userLocation,
                                locationPermissionGranted = locationPermissionGranted,
                                onResultClick = { poi ->
                                    selectedPoi = poi
                                    selectedSpotId = poi.localSpotId
                                    showSearchPanel = false
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 280.dp)
                            )
                        } else if (bundle.mapData.routes.isNotEmpty()) {
                            RouteChipsRow(
                                routes = bundle.mapData.routes,
                                selectedRouteId = selectedRouteId,
                                onSelect = {
                                    selectedPoi = null
                                    selectedSpotId = null
                                    viewModel.selectRoute(it)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // 信息卡：优先显示搜索 POI，其次景点 Marker
                    val infoName: String?
                    val infoDesc: String
                    val infoImage: String?
                    val infoIntro: String
                    val infoNav: (() -> Unit)?
                    when {
                        selectedPoi != null -> {
                            val poi = selectedPoi!!
                            val localSpot = poi.localSpotId?.let { spotId ->
                                bundle.spotsWithLocation.find { it.id == spotId }
                            }
                            val navLat = localSpot?.lat ?: poi.lat
                            val navLng = localSpot?.lng ?: poi.lng
                            val navName = localSpot?.name ?: poi.name
                            infoName = navName
                            infoDesc = localSpot?.description ?: poi.address
                            infoImage = localSpot?.imageUrl
                            infoIntro = localSpot?.intro ?: ""
                            infoNav = { launchNavigation(context, navLat, navLng, navName) }
                        }
                        selectedSpotId != null -> {
                            val spot = bundle.spotsWithLocation.find { it.id == selectedSpotId }
                            infoName = spot?.name
                            infoDesc = spot?.description ?: ""
                            infoImage = spot?.imageUrl
                            infoIntro = spot?.intro ?: ""
                            infoNav = spot?.let { s ->
                                val lat = s.lat
                                val lng = s.lng
                                if (lat != null && lng != null) {
                                    { launchNavigation(context, lat, lng, s.name) }
                                } else null
                            }
                        }
                        else -> {
                            infoName = null
                            infoDesc = ""
                            infoImage = null
                            infoIntro = ""
                            infoNav = null
                        }
                    }

                    // 底部叠加层：定位 FAB + 景点信息卡纵向堆叠，信息卡出现时 FAB 自动上移避免重叠
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.End
                    ) {
                        FloatingActionButton(
                            onClick = {
                                if (!isTrackingUser && userLocation == null) {
                                    android.widget.Toast.makeText(
                                        context,
                                        context.getString(R.string.map_locating),
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                                isTrackingUser = !isTrackingUser
                            },
                            modifier = Modifier
                                .padding(end = 16.dp, bottom = 12.dp),
                            shape = CircleShape,
                            containerColor = if (isTrackingUser) Primary else Surface,
                            contentColor = if (isTrackingUser) androidx.compose.ui.graphics.Color.White else Primary
                        ) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = stringResource(
                                    if (isTrackingUser) R.string.map_stop_tracking else R.string.map_locate_me
                                )
                            )
                        }
                        if (infoName != null && infoNav != null) {
                            SpotInfoCard(
                                name = infoName,
                                description = infoDesc,
                                imageUrl = infoImage,
                                intro = infoIntro,
                                onNavigate = infoNav,
                                onDismiss = {
                                    selectedPoi = null
                                    selectedSpotId = null
                                },
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivacyConsentDialog(
    onAgree: () -> Unit,
    onDisagree: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.map_privacy_title)) },
        text = { Text(stringResource(R.string.map_privacy_message)) },
        confirmButton = {
            TextButton(onClick = onAgree) {
                Text(stringResource(R.string.map_privacy_agree))
            }
        },
        dismissButton = {
            TextButton(onClick = onDisagree) {
                Text(stringResource(R.string.map_privacy_disagree))
            }
        }
    )
}

@Composable
private fun MapViewContainer(
    bundle: ScenicMapBundle,
    selectedSpotId: String?,
    selectedRouteId: String?,
    searchResults: List<MapPoi>,
    userLocation: LatLng?,
    isTrackingUser: Boolean,
    locationPermissionGranted: Boolean,
    initialFocusDone: Boolean,
    selectedPoi: MapPoi?,
    onInitialFocusDone: () -> Unit,
    onUserDragged: () -> Unit,
    onMapTap: () -> Unit,
    onMarkerClicked: (String?) -> Unit,
    onLocationPermissionResult: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mapView = rememberMapViewWithLifecycle()
    val aMap = remember { mapView.map }

    val markerIconCache = remember { mutableMapOf<String, SpotMarkerIcon>() }

    // 用 rememberUpdatedState 让只注册一次的监听器始终调用最新 lambda
    val currentOnUserDragged by rememberUpdatedState(onUserDragged)
    val currentOnMapTap by rememberUpdatedState(onMapTap)
    val currentOnMarkerClicked by rememberUpdatedState(onMarkerClicked)

    // 检查并请求定位权限
    val locationPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        onLocationPermissionResult(results.values.any { it })
    }

    LaunchedEffect(Unit) {
        val granted = locationPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PermissionChecker.PERMISSION_GRANTED
        }
        if (granted) {
            onLocationPermissionResult(true)
        } else {
            permissionLauncher.launch(locationPermissions)
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { mapView }
    )

    // Effect A — 一次性地图配置（蓝点样式、触摸/Marker 监听）
    LaunchedEffect(aMap) {
        aMap ?: return@LaunchedEffect
        aMap.uiSettings.isMyLocationButtonEnabled = false
        // 关闭高德内置缩放按钮：默认位于右下角，会与底部信息卡/FAB 叠加，改为手势缩放
        aMap.uiSettings.isZoomControlsEnabled = false
        aMap.setMyLocationEnabled(true)
        aMap.myLocationStyle = MyLocationStyle().apply {
            // 蓝点持续显示，但不自动居中、不旋转，相机完全由我们控制
            myLocationType(MyLocationStyle.LOCATION_TYPE_FOLLOW_NO_CENTER)
            interval(2000L)
        }
        aMap.setOnMapTouchListener { event ->
            when (event.action) {
                // 拖拽/平移：取消跟随（程序化 animateCamera 不产生触摸事件，无误报）
                MotionEvent.ACTION_MOVE -> currentOnUserDragged()
                // 点击地图空白：关闭信息卡
                MotionEvent.ACTION_DOWN -> currentOnMapTap()
            }
        }
        aMap.setOnMarkerClickListener { marker ->
            currentOnMarkerClicked(marker.`object` as? String)
            // 返回 true 屏蔽原生 infoWindow，第 3 批用 Compose 叠加卡替代
            true
        }
    }

    // Effect B — 应用自定义样式、绘制景点 Marker、搜索结果 Marker 与选中路线折线。
    // 搜索 POI 也放在本 Effect 中，避免 aMap.clear() 后丢失地标。
    LaunchedEffect(bundle, selectedSpotId, selectedRouteId, searchResults, selectedPoi) {
        aMap ?: return@LaunchedEffect

        // 应用景区自定义地图样式（离线 styleJson）
        bundle.mapData.styleJsonPath?.let { path ->
            runCatching {
                val styleData = context.assets.open(path).use { it.readBytes() }
                val options = CustomMapStyleOptions()
                    .setEnable(true)
                    .setStyleData(styleData)
                aMap.setCustomMapStyle(options)
            }.onFailure {
                android.util.Log.w("MapPortal", "加载自定义地图样式失败: $path", it)
            }
        } ?: aMap.setCustomMapStyle(CustomMapStyleOptions().setEnable(false))

        aMap.clear()
        bundle.spotsWithLocation.forEach { spot ->
            val lat = spot.lat ?: return@forEach
            val lng = spot.lng ?: return@forEach
            val isSelected = selectedSpotId == spot.id
            val markerKey = buildMarkerIconCacheKey(context, spot, isSelected)
            val markerIcon = markerIconCache.getOrPut(markerKey) {
                createSpotMarkerIcon(context, spot, isSelected)
            }
            val marker = aMap.addMarker(
                MarkerOptions()
                    .position(LatLng(lat, lng))
                    .icon(markerIcon.descriptor)
                    .anchor(0.5f, markerIcon.anchorY)
                    .title(spot.name)
                    .snippet(spot.description.takeIf { it.isNotBlank() })
            )
            marker.`object` = spot.id
        }

        // 绘制选中路线折线。必须与 Marker 同一 Effect：aMap.clear() 会清掉所有 overlay，
        // 若放在单独 Effect，selectedSpotId 变化触发本 Effect 的 clear 会抹掉折线且不重画。
        val route = bundle.mapData.routes.firstOrNull { it.routeId == selectedRouteId }
        if (route != null) drawRouteOverlay(aMap, context, route, bundle)

        val selectedPoiKey = selectedPoi?.let { poiMarkerKey(it) }
        searchResults
            .filter { it.localSpotId == null }
            .forEach { poi ->
                val markerKey = poiMarkerKey(poi)
                val isSelected = markerKey == selectedPoiKey
                val marker = aMap.addMarker(
                        MarkerOptions()
                            .position(LatLng(poi.lat, poi.lng))
                            .icon(createPoiMarkerDescriptor(context, poi.category, isSelected))
                        .anchor(0.5f, 1f)
                        .title(poi.name)
                        .snippet(poi.address.takeIf { it.isNotBlank() })
                        .zIndex(if (isSelected) 160f else 130f)
                )
                marker.`object` = POI_MARKER_OBJECT_PREFIX + markerKey
            }
    }

    // Effect C — 相机跟随（仅跟踪时移动相机；用户拖拽 → onUserDragged 置 false → 此 effect 跳过）
    LaunchedEffect(isTrackingUser, userLocation) {
        if (isTrackingUser && userLocation != null) {
            aMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(userLocation, FOLLOW_ZOOM))
        }
    }

    // Effect D — 进入初始聚焦（仅一次）：景区中心优先，用户在 5km 内才切用户位置
    LaunchedEffect(bundle, userLocation, locationPermissionGranted, initialFocusDone) {
        if (initialFocusDone) return@LaunchedEffect
        aMap ?: return@LaunchedEffect
        val center = LatLng(bundle.mapData.centerLat, bundle.mapData.centerLng)
        val focusUser = locationPermissionGranted && userLocation != null &&
            distanceMeters(userLocation, center) <= NEAR_SCENIC_RADIUS_M
        val target = if (focusUser) userLocation!! else center
        aMap.moveCamera(
            CameraUpdateFactory.newLatLngZoom(target, bundle.mapData.defaultZoom)
        )
        onInitialFocusDone()
    }

    // Effect E — 选中 POI 搜索结果：移动相机。Marker 在 Effect B 中统一绘制，避免被 clear() 清掉。
    LaunchedEffect(selectedPoi) {
        aMap ?: return@LaunchedEffect
        val poi = selectedPoi ?: return@LaunchedEffect
        aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(poi.lat, poi.lng), FOLLOW_ZOOM))
    }
}

private data class SpotMarkerIcon(
    val descriptor: BitmapDescriptor,
    val anchorY: Float
)

private fun drawRouteOverlay(
    aMap: com.amap.api.maps.AMap,
    context: android.content.Context,
    route: ScenicRoute,
    bundle: ScenicMapBundle
) {
    val points = route.polyline.map { LatLng(it.lat, it.lng) }
    if (points.size >= 2) {
        val routeColor = parseRouteColor(route.color)
        val shadowColor = withAlpha(android.graphics.Color.BLACK, 0.20f)
        val casingColor = android.graphics.Color.WHITE

        aMap.addPolyline(
            PolylineOptions()
                .addAll(points)
                .color(shadowColor)
                .width(24f)
                .zIndex(35f)
        )
        aMap.addPolyline(
            PolylineOptions()
                .addAll(points)
                .color(casingColor)
                .width(18f)
                .zIndex(36f)
        )
        aMap.addPolyline(
            PolylineOptions()
                .addAll(points)
                .color(routeColor)
                .width(11f)
                .zIndex(37f)
        )

        points.zipWithNext().forEachIndexed { index, (start, end) ->
            if (index % 2 == 0 && distanceMeters(start, end) > 35.0) {
                val marker = aMap.addMarker(
                    MarkerOptions()
                        .position(midpoint(start, end))
                        .icon(createRouteArrowDescriptor(context, routeColor))
                        .anchor(0.5f, 0.5f)
                        .rotateAngle(routeBearing(start, end))
                        .zIndex(90f)
                )
                marker.`object` = null
            }
        }
    }

    val routeSpotsById = bundle.spotsWithLocation.associateBy { it.id }
    val routeSpots = route.spotOrder.mapNotNull { routeSpotsById[it] }
    routeSpots.forEachIndexed { index, spot ->
        val lat = spot.lat ?: return@forEachIndexed
        val lng = spot.lng ?: return@forEachIndexed
        val label = when (index) {
            0 -> "起"
            routeSpots.lastIndex -> "终"
            else -> (index + 1).toString()
        }
        val marker = aMap.addMarker(
            MarkerOptions()
                .position(LatLng(lat, lng))
                .icon(createRouteStopDescriptor(context, label, route.color))
                .anchor(0.5f, 0.5f)
                .title(spot.name)
                .snippet(spot.description.takeIf { it.isNotBlank() })
                .zIndex(100f)
        )
        marker.`object` = spot.id
    }
}

private fun parseRouteColor(colorHex: String): Int =
    runCatching { android.graphics.Color.parseColor(colorHex) }
        .getOrElse { android.graphics.Color.parseColor("#1D7A6D") }

private fun withAlpha(color: Int, alpha: Float): Int =
    android.graphics.Color.argb(
        (alpha.coerceIn(0f, 1f) * 255).toInt(),
        android.graphics.Color.red(color),
        android.graphics.Color.green(color),
        android.graphics.Color.blue(color)
    )

private fun midpoint(start: LatLng, end: LatLng): LatLng =
    LatLng(
        (start.latitude + end.latitude) / 2.0,
        (start.longitude + end.longitude) / 2.0
    )

private fun routeBearing(start: LatLng, end: LatLng): Float {
    val lat1 = Math.toRadians(start.latitude)
    val lat2 = Math.toRadians(end.latitude)
    val dLng = Math.toRadians(end.longitude - start.longitude)
    val y = Math.sin(dLng) * Math.cos(lat2)
    val x = Math.cos(lat1) * Math.sin(lat2) -
        Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLng)
    return ((Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0).toFloat()
}

private fun createRouteArrowDescriptor(
    context: android.content.Context,
    routeColor: Int
): BitmapDescriptor {
    val density = context.resources.displayMetrics.density
    val size = (28 * density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = size / 2f
    val cy = size / 2f

    val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, 11f * density, haloPaint)

    val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = routeColor
        style = Paint.Style.FILL
    }
    val arrowPath = Path().apply {
        moveTo(cx, cy - 8f * density)
        lineTo(cx + 7f * density, cy + 7f * density)
        lineTo(cx, cy + 3f * density)
        lineTo(cx - 7f * density, cy + 7f * density)
        close()
    }
    canvas.drawPath(arrowPath, arrowPaint)

    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

private fun createRouteStopDescriptor(
    context: android.content.Context,
    label: String,
    colorHex: String
): BitmapDescriptor {
    val density = context.resources.displayMetrics.density
    val size = (34 * density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = size / 2f
    val cy = size / 2f
    val routeColor = parseRouteColor(colorHex)

    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = withAlpha(android.graphics.Color.BLACK, 0.22f)
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx + density, cy + 2f * density, 13f * density, shadowPaint)

    val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, 14f * density, outerPaint)

    val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = routeColor
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, 10.5f * density, innerPaint)

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = if (label.length > 1) 11f * density else 13f * density
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2f
    canvas.drawText(label, cx, textY, textPaint)

    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

private fun buildMarkerIconCacheKey(
    context: android.content.Context,
    spot: ScenicSpot,
    isSelected: Boolean
): String {
    val metrics = context.resources.displayMetrics
    val fontScale = context.resources.configuration.fontScale
    return listOf(
        spot.id,
        spot.name,
        spot.imageUrl.orEmpty(),
        isSelected.toString(),
        metrics.density.toString(),
        fontScale.toString()
    ).joinToString("|")
}

/**
 * 为景点生成参考图样式的图片气泡 Marker：左侧景点图作为 logo，右侧显示景点名。
 */
private fun createSpotMarkerIcon(
    context: android.content.Context,
    spot: ScenicSpot,
    isSelected: Boolean
): SpotMarkerIcon {
    val density = context.resources.displayMetrics.density
    val fontScale = context.resources.configuration.fontScale

    val logoSize = (42 * density).toInt()
    val horizontalPadding = (8 * density).toInt()
    val verticalPadding = (6 * density).toInt()
    val textGap = (8 * density).toInt()
    val bubbleRadius = 12 * density
    val logoRadius = 8 * density
    val tailHeight = (10 * density).toInt()
    val tailHalfWidth = 8 * density
    val shadowPadding = (3 * density).toInt()
    val textSize = 14 * density * fontScale
    val maxTextWidth = 108 * density

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.textSize = textSize
        typeface = Typeface.DEFAULT_BOLD
        color = android.graphics.Color.parseColor("#1C2328")
        textAlign = Paint.Align.LEFT
    }

    val textBounds = Rect()
    val normalizedName = spot.name.trim()
    val displayName = ellipsizeText(normalizedName, textPaint, maxTextWidth)
    textPaint.getTextBounds(displayName, 0, displayName.length, textBounds)

    val textWidth = textPaint.measureText(displayName).toInt()
    val bubbleWidth = (
        horizontalPadding + logoSize + textGap + textWidth + horizontalPadding
    ).coerceAtLeast((112 * density).toInt())
    val bubbleHeight = maxOf(
        logoSize + verticalPadding * 2,
        textBounds.height() + verticalPadding * 2
    )
    val totalWidth = bubbleWidth + shadowPadding * 2
    val totalHeight = bubbleHeight + tailHeight + shadowPadding * 2

    val bitmap = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val bubbleLeft = shadowPadding.toFloat()
    val bubbleTop = shadowPadding.toFloat()
    val bubbleRight = bubbleLeft + bubbleWidth
    val bubbleBottom = bubbleTop + bubbleHeight
    val bubbleRect = RectF(bubbleLeft, bubbleTop, bubbleRight, bubbleBottom)
    val tailTipX = totalWidth / 2f
    val tailTipY = bubbleBottom + tailHeight
    val tailPath = Path().apply {
        moveTo(tailTipX - tailHalfWidth, bubbleBottom - 1f)
        lineTo(tailTipX + tailHalfWidth, bubbleBottom - 1f)
        lineTo(tailTipX, tailTipY)
        close()
    }

    // 1. 绘制气泡阴影、背景与底部指针。
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#1D000000")
        style = Paint.Style.FILL
    }
    val shadowTailPath = Path(tailPath)
    shadowTailPath.offset(1f, 2f)
    canvas.drawPath(shadowTailPath, shadowPaint)
    canvas.drawRoundRect(
        bubbleRect.left + 1f,
        bubbleRect.top + 2f,
        bubbleRect.right + 1f,
        bubbleRect.bottom + 2f,
        bubbleRadius,
        bubbleRadius,
        shadowPaint
    )
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor(if (isSelected) "#FFF7E6" else "#FFFFFF")
        style = Paint.Style.FILL
    }
    canvas.drawPath(tailPath, bgPaint)
    canvas.drawRoundRect(bubbleRect, bubbleRadius, bubbleRadius, bgPaint)
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor(if (isSelected) "#F2A541" else "#00000000")
        style = Paint.Style.STROKE
        strokeWidth = if (isSelected) 2f * density else 0f
    }
    if (isSelected) canvas.drawRoundRect(bubbleRect, bubbleRadius, bubbleRadius, borderPaint)

    // 2. 绘制左侧景点图片 logo。
    val imageLeft = bubbleLeft + horizontalPadding
    val imageTop = bubbleTop + (bubbleHeight - logoSize) / 2f
    val imageRect = RectF(imageLeft, imageTop, imageLeft + logoSize, imageTop + logoSize)
    val imageBitmap = loadMarkerBitmap(context, spot.imageUrl, logoSize)
    if (imageBitmap != null) {
        drawRoundedBitmap(canvas, imageBitmap, imageRect, logoRadius)
    } else {
        drawFallbackLogo(canvas, imageRect, logoRadius, normalizedName, density)
    }

    // 3. 绘制景点名称。
    val textX = imageRect.right + textGap
    val textY = bubbleTop + bubbleHeight / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
    canvas.drawText(displayName, textX, textY, textPaint)

    return SpotMarkerIcon(
        descriptor = BitmapDescriptorFactory.fromBitmap(bitmap),
        anchorY = (tailTipY / totalHeight).coerceIn(0f, 1f)
    )
}

private fun ellipsizeText(text: String, paint: Paint, maxWidth: Float): String {
    if (paint.measureText(text) <= maxWidth) return text
    val ellipsis = "…"
    var end = text.length
    while (end > 1 && paint.measureText(text.substring(0, end) + ellipsis) > maxWidth) {
        end--
    }
    return text.substring(0, end) + ellipsis
}

private fun loadMarkerBitmap(
    context: android.content.Context,
    imageUrl: String?,
    targetSize: Int
): Bitmap? {
    val assetPath = imageUrl
        ?.takeIf { it.startsWith("file:///android_asset/") }
        ?.removePrefix("file:///android_asset/")
        ?: return null

    return runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.assets.open(assetPath).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

        val options = BitmapFactory.Options().apply {
            inSampleSize = markerBitmapSampleSize(bounds.outWidth, bounds.outHeight, targetSize)
        }
        context.assets.open(assetPath).use { BitmapFactory.decodeStream(it, null, options) }
    }.getOrNull()
}

private fun markerBitmapSampleSize(width: Int, height: Int, targetSize: Int): Int {
    var sampleSize = 1
    while (width / (sampleSize * 2) >= targetSize && height / (sampleSize * 2) >= targetSize) {
        sampleSize *= 2
    }
    return sampleSize
}

private fun drawRoundedBitmap(canvas: Canvas, bitmap: Bitmap, dest: RectF, radius: Float) {
    val src = centerCropSource(bitmap, dest)
    val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
        isDither = true
    }
    val clip = Path().apply {
        addRoundRect(dest, radius, radius, Path.Direction.CW)
    }
    canvas.save()
    canvas.clipPath(clip)
    canvas.drawBitmap(bitmap, src, dest, imagePaint)
    canvas.restore()
}

private fun centerCropSource(bitmap: Bitmap, dest: RectF): Rect {
    val srcAspect = bitmap.width.toFloat() / bitmap.height.toFloat()
    val destAspect = dest.width() / dest.height()
    return if (srcAspect > destAspect) {
        val cropWidth = (bitmap.height * destAspect).toInt().coerceAtMost(bitmap.width)
        val left = (bitmap.width - cropWidth) / 2
        Rect(left, 0, left + cropWidth, bitmap.height)
    } else {
        val cropHeight = (bitmap.width / destAspect).toInt().coerceAtMost(bitmap.height)
        val top = (bitmap.height - cropHeight) / 2
        Rect(0, top, bitmap.width, top + cropHeight)
    }
}

private fun drawFallbackLogo(
    canvas: Canvas,
    dest: RectF,
    radius: Float,
    name: String,
    density: Float
) {
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#DCEBE7")
        style = Paint.Style.FILL
    }
    canvas.drawRoundRect(dest, radius, radius, bgPaint)
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#1D7A6D")
        textSize = 18f * density
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    val label = name.take(1).ifBlank { "景" }
    val textY = dest.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
    canvas.drawText(label, dest.centerX(), textY, textPaint)
}

/**
 * 为 POI 搜索结果生成「水滴针」Marker：厕所、餐饮、停车、游客中心使用通用图标，
 * 叠加竖向渐变、顶部高光与柔影，使图标在地图上醒目易辨。针尖贴底，anchor(0.5,1) 即精确坐标点。
 */
private fun createPoiMarkerDescriptor(
    context: android.content.Context,
    category: String?,
    selected: Boolean
): com.amap.api.maps.model.BitmapDescriptor {
    val density = context.resources.displayMetrics.density
    val headDp = if (selected) 50 else 40
    val r = headDp * density / 2f
    val tailH = r * 0.58f
    val pad = 6f * density
    val canvasW = (r * 2 + pad * 2).toInt()
    val canvasH = (pad + r * 2 + tailH).toInt()
    val bitmap = Bitmap.createBitmap(canvasW, canvasH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = canvasW / 2f
    val cy = pad + r
    val tipY = canvasH.toFloat()
    val baseColor = poiCategoryColor(category)
    val lightColor = mixColor(baseColor, android.graphics.Color.WHITE, 0.32f)
    val darkColor = mixColor(baseColor, android.graphics.Color.BLACK, 0.22f)
    val pinPath = buildPinPath(cx, cy, r, tailH)

    // 选中态：针头后方一圈彩色光晕，提升识别
    if (selected) {
        val glowR = r + 8f * density
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = android.graphics.RadialGradient(
                cx, cy, glowR,
                intArrayOf(withAlpha(baseColor, 0.45f), withAlpha(baseColor, 0f)),
                floatArrayOf(0.4f, 1f),
                android.graphics.Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(cx, cy, glowR, glowPaint)
    }

    // 主体：竖向渐变 + 柔影，呈现立体光泽
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        shader = android.graphics.LinearGradient(
            cx, cy - r, cx, tipY,
            intArrayOf(lightColor, baseColor, darkColor),
            floatArrayOf(0f, 0.5f, 1f),
            android.graphics.Shader.TileMode.CLAMP
        )
        setShadowLayer(5f * density, 0f, 2.5f * density, 0x42000000)
    }
    canvas.drawPath(pinPath, fillPaint)
    fillPaint.clearShadowLayer()

    // 顶部高光：裁剪到针形内，画半透白反光
    canvas.save()
    canvas.clipPath(pinPath)
    val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        shader = android.graphics.LinearGradient(
            cx, cy - r, cx, cy,
            withAlpha(android.graphics.Color.WHITE, 0.6f),
            withAlpha(android.graphics.Color.WHITE, 0f),
            android.graphics.Shader.TileMode.CLAMP
        )
    }
    canvas.drawOval(
        RectF(cx - r * 0.6f, cy - r * 0.85f, cx + r * 0.6f, cy - r * 0.05f),
        sheenPaint
    )
    canvas.restore()

    // 白色描边，与地图背景分离
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = (if (selected) 2.5f else 2f) * density
        strokeJoin = Paint.Join.ROUND
    }
    canvas.drawPath(pinPath, strokePaint)

    // 选中态：针头外圈再描一圈强调色
    if (selected) {
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(baseColor, 0.9f)
            style = Paint.Style.STROKE
            strokeWidth = 2f * density
        }
        canvas.drawCircle(cx, cy, r + 3.5f * density, ringPaint)
    }

    // 分类图标
    val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
        strokeWidth = 1.6f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    drawPoiPictogram(canvas, category, cx, cy, r * 0.62f, iconPaint)
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

private fun poiCategoryColor(category: String?): Int =
    when (category) {
        "restroom" -> android.graphics.Color.parseColor("#2F80ED")
        "dining" -> android.graphics.Color.parseColor("#F2994A")
        "parking" -> android.graphics.Color.parseColor("#2D9CDB")
        "visitor_center" -> android.graphics.Color.parseColor("#27AE60")
        else -> android.graphics.Color.parseColor("#F2A541")
    }

/**
 * 通用 POI 分类图标（白色反相绘制）：厕所=男女图样、餐饮=刀叉、停车=P、游客中心=i。
 * 同时供地图 Marker 与搜索列表徽章复用，保证两处观感一致。
 */
private fun drawPoiPictogram(
    canvas: Canvas,
    category: String?,
    cx: Float,
    cy: Float,
    u: Float,
    paint: Paint
) {
    when (category) {
        "restroom" -> drawRestroomPictogram(canvas, cx, cy, u, paint)
        "dining" -> drawDiningPictogram(canvas, cx, cy, u, paint)
        "parking" -> drawPoiText(canvas, "P", cx, cy, u, paint)
        "visitor_center" -> drawPoiText(canvas, "i", cx, cy, u, paint)
        else -> canvas.drawCircle(cx, cy, u * 0.35f, paint)
    }
}

/** 卫生间：男（直立）+ 女（A 字裙）填充图样，全球通用的卫生间标识。 */
private fun drawRestroomPictogram(canvas: Canvas, cx: Float, cy: Float, u: Float, paint: Paint) {
    val sep = 0.30f * u
    val mx = cx - sep
    val wx = cx + sep
    canvas.drawCircle(mx, cy - 0.42f * u, 0.12f * u, paint)
    canvas.drawCircle(wx, cy - 0.42f * u, 0.12f * u, paint)
    val man = Path().apply {
        moveTo(mx - 0.16f * u, cy - 0.24f * u)
        lineTo(mx + 0.16f * u, cy - 0.24f * u)
        lineTo(mx + 0.13f * u, cy + 0.12f * u)
        lineTo(mx + 0.12f * u, cy + 0.52f * u)
        lineTo(mx + 0.03f * u, cy + 0.52f * u)
        lineTo(mx + 0.01f * u, cy + 0.20f * u)
        lineTo(mx - 0.01f * u, cy + 0.20f * u)
        lineTo(mx - 0.03f * u, cy + 0.52f * u)
        lineTo(mx - 0.12f * u, cy + 0.52f * u)
        lineTo(mx - 0.13f * u, cy + 0.12f * u)
        close()
    }
    canvas.drawPath(man, paint)
    val woman = Path().apply {
        moveTo(wx - 0.14f * u, cy - 0.24f * u)
        lineTo(wx + 0.14f * u, cy - 0.24f * u)
        lineTo(wx + 0.27f * u, cy + 0.52f * u)
        lineTo(wx - 0.27f * u, cy + 0.52f * u)
        close()
    }
    canvas.drawPath(woman, paint)
}

/** 餐饮：叉子（三齿）+ 刀，通用餐厅标识。 */
private fun drawDiningPictogram(canvas: Canvas, cx: Float, cy: Float, u: Float, paint: Paint) {
    val fx = cx - 0.26f * u
    val kx = cx + 0.26f * u
    val top = cy - 0.52f * u
    val tineBot = cy - 0.18f * u
    floatArrayOf(-0.11f, 0f, 0.11f).forEach { off ->
        canvas.drawRect(
            RectF(fx + off * u - 0.035f * u, top, fx + off * u + 0.035f * u, tineBot),
            paint
        )
    }
    canvas.drawRect(RectF(fx - 0.12f * u, tineBot, fx + 0.12f * u, cy - 0.02f * u), paint)
    canvas.drawRect(RectF(fx - 0.055f * u, cy - 0.02f * u, fx + 0.055f * u, cy + 0.52f * u), paint)
    val blade = Path().apply {
        moveTo(kx - 0.07f * u, top)
        lineTo(kx + 0.07f * u, top)
        lineTo(kx + 0.07f * u, cy - 0.12f * u)
        lineTo(kx - 0.02f * u, cy - 0.02f * u)
        lineTo(kx - 0.07f * u, cy - 0.12f * u)
        close()
    }
    canvas.drawPath(blade, paint)
    canvas.drawRect(RectF(kx - 0.05f * u, cy - 0.02f * u, kx + 0.05f * u, cy + 0.52f * u), paint)
}

/** 停车（P）/ 游客中心（i）：通用单字标识。 */
private fun drawPoiText(canvas: Canvas, text: String, cx: Float, cy: Float, u: Float, paint: Paint) {
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = paint.color
        textSize = u * 1.55f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    val y = cy - (textPaint.descent() + textPaint.ascent()) / 2f
    canvas.drawText(text, cx, y, textPaint)
}

/**
 * 搜索列表用的圆形分类徽章：与地图 Marker 同色同图标，让列表与地图观感统一。
 */
private fun createPoiBadgeBitmap(
    context: android.content.Context,
    category: String?,
    sizeDp: Int
): Bitmap {
    val density = context.resources.displayMetrics.density
    val size = sizeDp * density
    val bitmap = Bitmap.createBitmap(size.toInt(), size.toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = size / 2f
    val cy = size / 2f
    val r = size / 2f - 2f * density
    val baseColor = poiCategoryColor(category)
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        shader = android.graphics.LinearGradient(
            cx, cy - r, cx, cy + r,
            mixColor(baseColor, android.graphics.Color.WHITE, 0.28f),
            mixColor(baseColor, android.graphics.Color.BLACK, 0.16f),
            android.graphics.Shader.TileMode.CLAMP
        )
        setShadowLayer(2.5f * density, 0f, 1f * density, 0x33000000)
    }
    canvas.drawCircle(cx, cy, r, fillPaint)
    fillPaint.clearShadowLayer()
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }
    canvas.drawCircle(cx, cy, r, strokePaint)
    val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    drawPoiPictogram(canvas, category, cx, cy, r * 0.6f, iconPaint)
    return bitmap
}

/** 构造水滴针路径：圆头 + 三角尾，针尾与圆相切。 */
private fun buildPinPath(cx: Float, cy: Float, r: Float, tailH: Float): Path {
    val alpha = Math.toRadians(35.0)
    val sinA = Math.sin(alpha).toFloat()
    val cosA = Math.cos(alpha).toFloat()
    val leftX = cx - r * sinA
    val leftY = cy + r * cosA
    val tipY = cy + r + tailH
    val oval = RectF(cx - r, cy - r, cx + r, cy + r)
    return Path().apply {
        moveTo(leftX, leftY)
        arcTo(oval, 125f, 290f, false)
        lineTo(cx, tipY)
        close()
    }
}

/** 两色线性混合，t=0 取 a，t=1 取 b。用于生成渐变高光/暗部色。 */
private fun mixColor(a: Int, b: Int, t: Float): Int {
    val tt = t.coerceIn(0f, 1f)
    return android.graphics.Color.rgb(
        (android.graphics.Color.red(a) + (android.graphics.Color.red(b) - android.graphics.Color.red(a)) * tt).toInt(),
        (android.graphics.Color.green(a) + (android.graphics.Color.green(b) - android.graphics.Color.green(a)) * tt).toInt(),
        (android.graphics.Color.blue(a) + (android.graphics.Color.blue(b) - android.graphics.Color.blue(a)) * tt).toInt()
    )
}

private fun poiMarkerKey(poi: MapPoi): String =
    "${poi.poiId}:${formatCoordinate(poi.lat)}:${formatCoordinate(poi.lng)}"

/**
 * 粗略球面距离（米），用于判断用户是否在景区附近。精度足够做阈值判断。
 */
private fun distanceMeters(a: LatLng, b: LatLng): Double {
    val r = 6371000.0 // 地球半径（米）
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLng = Math.toRadians(b.longitude - a.longitude)
    val sinDLat = Math.sin(dLat / 2)
    val sinDLng = Math.sin(dLng / 2)
    val h = sinDLat * sinDLat +
        Math.cos(Math.toRadians(a.latitude)) * Math.cos(Math.toRadians(b.latitude)) * sinDLng * sinDLng
    return 2 * r * Math.asin(Math.min(1.0, Math.sqrt(h)))
}

/**
 * 调起导航：优先高德地图 URI，回退到系统 geo: 选择器，再回退 Toast。
 */
private fun launchNavigation(context: android.content.Context, lat: Double, lng: Double, name: String) {
    if (!isValidCoordinate(lat, lng)) {
        android.widget.Toast.makeText(
            context,
            context.getString(R.string.map_invalid_destination),
            android.widget.Toast.LENGTH_SHORT
        ).show()
        return
    }

    val latText = formatCoordinate(lat)
    val lngText = formatCoordinate(lng)
    val amapIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
        data = android.net.Uri.Builder()
            .scheme("androidamap")
            .authority("navi")
            .appendQueryParameter("sourceApplication", context.getString(R.string.app_name))
            .appendQueryParameter("poiname", name.trim().ifBlank { context.getString(R.string.map_destination) })
            .appendQueryParameter("lat", latText)
            .appendQueryParameter("lon", lngText)
            .appendQueryParameter("dev", "0")
            .appendQueryParameter("style", "2")
            .build()
        addCategory(android.content.Intent.CATEGORY_DEFAULT)
        setPackage("com.autonavi.minimap")
    }
    try {
        context.startActivity(amapIntent)
        return
    } catch (_: android.content.ActivityNotFoundException) {
        // 高德地图未安装，尝试系统 geo: 选择器
    }
    val geoIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
        data = android.net.Uri.parse("geo:$latText,$lngText?q=$latText,$lngText(${android.net.Uri.encode(name)})")
    }
    try {
        context.startActivity(android.content.Intent.createChooser(geoIntent, "选择地图应用"))
    } catch (_: android.content.ActivityNotFoundException) {
        android.widget.Toast.makeText(
            context,
            context.getString(R.string.map_no_nav_app),
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }
}

private fun isValidCoordinate(lat: Double, lng: Double): Boolean =
    !lat.isNaN() && !lat.isInfinite() &&
        !lng.isNaN() && !lng.isInfinite() &&
        lat in -90.0..90.0 &&
        lng in -180.0..180.0

private fun formatCoordinate(value: Double): String =
    java.lang.String.format(java.util.Locale.US, "%.6f", value)

@Composable
private fun SpotInfoCard(
    name: String,
    description: String,
    imageUrl: String?,
    intro: String,
    onNavigate: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        color = Surface,
        shadowElevation = 8.dp
    ) {
        Column {
            // 顶部景点图片：全宽展示，由 Surface 的圆角自动裁剪顶部两角
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                )
            }
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name,
                        modifier = Modifier.weight(1f),
                        fontSize = 16.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.map_marker_close),
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                // 一段话简介优先；无 intro 时回退到单句 description（如 POI 地址）
                val body = intro.takeIf { it.isNotBlank() } ?: description
                if (body.isNotBlank()) {
                    Text(
                        text = body,
                        modifier = Modifier.padding(top = 4.dp),
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = TextSecondary,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Button(
                    onClick = onNavigate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.map_marker_navigate), fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun MapSearchBar(
    text: String,
    isSearching: Boolean,
    onTextChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Surface,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            androidx.compose.foundation.text.BasicTextField(
                value = text,
                onValueChange = onTextChange,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 15.sp,
                    color = TextPrimary
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Primary),
                decorationBox = { inner ->
                    if (text.isEmpty()) {
                        Text(
                            text = "搜索景区内地点，如 卫生间",
                            fontSize = 15.sp,
                            color = TextHint
                        )
                    }
                    inner()
                },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Search
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSearch = { onSearch() }
                ),
                modifier = Modifier.weight(1f)
            )
            if (isSearching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Primary
                )
            } else if (text.isNotEmpty()) {
                IconButton(onClick = onClear, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "清除",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                Spacer(Modifier.size(28.dp))
            }
        }
    }
}

@Composable
private fun SearchResultsPanel(
    results: List<MapPoi>,
    isSearching: Boolean,
    error: String?,
    hasQuery: Boolean,
    userLocation: LatLng?,
    locationPermissionGranted: Boolean,
    onResultClick: (MapPoi) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Surface,
        shadowElevation = 6.dp
    ) {
        when {
            isSearching && results.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Primary, modifier = Modifier.size(24.dp))
                }
            }
            error != null && results.isEmpty() -> {
                Text(
                    text = error,
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }
            results.isEmpty() -> {
                Text(
                    text = if (hasQuery) "按回车搜索更多地点" else "输入关键字搜索",
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    color = TextHint,
                    fontSize = 14.sp
                )
            }
            else -> {
                LazyColumn {
                    items(results, key = { it.poiId + it.lat + it.lng }) { poi ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onResultClick(poi) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                bitmap = remember(poi.category) {
                                    createPoiBadgeBitmap(context, poi.category, 28).asImageBitmap()
                                },
                                contentDescription = null,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = poi.name,
                                    fontSize = 15.sp,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (poi.address.isNotBlank()) {
                                    Text(
                                        text = poi.address,
                                        fontSize = 12.sp,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = formatPoiDistance(userLocation, locationPermissionGranted, poi),
                                fontSize = 12.sp,
                                color = TextSecondary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatPoiDistance(
    userLocation: LatLng?,
    locationPermissionGranted: Boolean,
    poi: MapPoi
): String {
    if (userLocation == null) return if (locationPermissionGranted) "定位中" else "未定位"
    val meters = distanceMeters(userLocation, LatLng(poi.lat, poi.lng))
    return when {
        meters < 1000.0 -> "${meters.toInt()}m"
        else -> java.lang.String.format(java.util.Locale.US, "%.1fkm", meters / 1000.0)
    }
}

@Composable
private fun RouteChipsRow(
    routes: List<ScenicRoute>,
    selectedRouteId: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            RouteChip(
                label = "全部景点",
                colorHex = "#1D7A6D",
                selected = selectedRouteId == null,
                onClick = { onSelect(null) }
            )
        }
        items(routes, key = { it.routeId }) { route ->
            RouteChip(
                label = route.name,
                colorHex = route.color,
                selected = selectedRouteId == route.routeId,
                onClick = { onSelect(route.routeId) }
            )
        }
    }
}

@Composable
private fun RouteChip(
    label: String,
    colorHex: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val containerColor = remember(colorHex) {
        runCatching { android.graphics.Color.parseColor(colorHex) }
            .getOrDefault(0xFF1D7A6D.toInt())
    }
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 13.sp) },
        leadingIcon = if (selected) {
            {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
            }
        } else null,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Color(containerColor),
            selectedLabelColor = Color.White,
            selectedLeadingIconColor = Color.White
        )
    )
}

@Composable
private fun rememberMapViewWithLifecycle(): MapView {
    val context = LocalContext.current
    val mapView = remember { MapView(context).apply { onCreate(null) } }
    val lifecycleObserver = remember(mapView) {
        LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, lifecycleObserver) {
        lifecycle.addObserver(lifecycleObserver)
        onDispose {
            lifecycle.removeObserver(lifecycleObserver)
        }
    }
    return mapView
}
