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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

private const val FOLLOW_ZOOM = 17f
/** 用户距景区中心在此范围内（米）才以用户位置为初始聚焦点 */
private const val NEAR_SCENIC_RADIUS_M = 5_000.0

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

    // 根据传入的 routeData 或当前景区 ID 加载地图
    LaunchedEffect(routeData) {
        routeData?.let { viewModel.loadRouteMap(it) }
    }
    LaunchedEffect(currentScenicId, routeData) {
        if (routeData == null) {
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

                    if (showPrivacyDialog && !amapPrivacyAgreed) {
                        PrivacyConsentDialog(
                            onAgree = {
                                MapsInitializer.updatePrivacyAgree(context, true)
                                AMapLocationClient.updatePrivacyAgree(context, true)
                                viewModel.setAmapPrivacyAgreed(true)
                                showPrivacyDialog = false
                            },
                            onDisagree = {
                                MapsInitializer.updatePrivacyAgree(context, false)
                                AMapLocationClient.updatePrivacyAgree(context, false)
                                showPrivacyDialog = false
                            }
                        )
                    }

                    if (amapPrivacyAgreed) {
                        MapViewContainer(
                            bundle = bundle,
                            selectedSpotId = selectedSpotId,
                            selectedRouteId = selectedRouteId,
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
                            onMarkerClicked = { selectedSpotId = it },
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
                                searchText = it
                                showSearchPanel = it.isNotBlank()
                            },
                            onSearch = {
                                viewModel.searchPois(searchText)
                                showSearchPanel = true
                            },
                            onClear = {
                                searchText = ""
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
                                onResultClick = { poi ->
                                    selectedPoi = poi
                                    selectedSpotId = null
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
                                onSelect = { viewModel.selectRoute(it) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // 信息卡：优先显示搜索 POI，其次景点 Marker
                    val infoName: String?
                    val infoDesc: String
                    val infoNav: (() -> Unit)?
                    when {
                        selectedPoi != null -> {
                            val poi = selectedPoi!!
                            infoName = poi.name
                            infoDesc = poi.address
                            infoNav = { launchNavigation(context, poi.lat, poi.lng, poi.name) }
                        }
                        selectedSpotId != null -> {
                            val spot = bundle.spotsWithLocation.find { it.id == selectedSpotId }
                            infoName = spot?.name
                            infoDesc = spot?.description ?: ""
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

    // 单独维护的 POI 搜索结果标记：切换结果时移除上一个，避免叠加堆积
    val poiMarker = remember { mutableStateOf<Marker?>(null) }
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

    // Effect B — 应用自定义样式、绘制景点 Marker 与选中路线折线（依赖 bundle/选中态/路线）
    LaunchedEffect(bundle, selectedSpotId, selectedRouteId) {
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
        if (route != null && route.polyline.isNotEmpty()) {
            val points = route.polyline.map { LatLng(it.lat, it.lng) }
            val colorInt = runCatching {
                android.graphics.Color.parseColor(route.color)
            }.getOrElse { android.graphics.Color.parseColor("#1D7A6D") }
            aMap.addPolyline(
                PolylineOptions()
                    .addAll(points)
                    .color(colorInt)
                    .width(10f)
            )
        }
    }

    // Effect C — 相机跟随（仅跟踪时移动相机；用户拖拽 → onUserDragged 置 false → 此 effect 跳过）
    LaunchedEffect(isTrackingUser, userLocation) {
        if (isTrackingUser && userLocation != null) {
            aMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(userLocation, FOLLOW_ZOOM))
        }
    }

    // Effect D — 进入初始聚焦（仅一次）：景区中心优先，用户在 5km 内才切用户位置
    LaunchedEffect(bundle, userLocation, locationPermissionGranted) {
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

    // Effect E — 选中 POI 搜索结果：落点橙色 Marker + 移动相机。单独维护，切换时移除上一个
    LaunchedEffect(selectedPoi) {
        aMap ?: return@LaunchedEffect
        // 移除上一次的 POI 标记（Effect B 的 clear() 也会清掉它，remove 失效标记用 runCatching 兜底）
        poiMarker.value?.let { runCatching { it.remove() } }
        poiMarker.value = null
        val poi = selectedPoi ?: return@LaunchedEffect
        val marker = aMap.addMarker(
            MarkerOptions()
                .position(LatLng(poi.lat, poi.lng))
                .icon(createPoiMarkerDescriptor(context))
                .anchor(0.5f, 0.5f)
                .title(poi.name)
                .snippet(poi.address.takeIf { it.isNotBlank() })
        )
        poiMarker.value = marker
        aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(poi.lat, poi.lng), FOLLOW_ZOOM))
    }
}

private data class SpotMarkerIcon(
    val descriptor: BitmapDescriptor,
    val anchorY: Float
)

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
 * 为 POI 搜索结果生成橙色圆点 Marker（与景点绿色定位针视觉区分）。
 */
private fun createPoiMarkerDescriptor(
    context: android.content.Context
): com.amap.api.maps.model.BitmapDescriptor {
    val density = context.resources.displayMetrics.density
    val size = (22 * density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = size / 2f
    val cy = size / 2f
    val radius = size / 2f - (2 * density)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#F2A541")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, radius, fill)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
    }
    canvas.drawCircle(cx, cy, radius, stroke)
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

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
    val amapIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
        data = android.net.Uri.parse("androidamap://navi?sourceApplication=景灵智导&latlng=$lat,$lng&dev=0&style=2")
        setPackage("com.autonavi.minimap")
    }
    try {
        context.startActivity(amapIntent)
        return
    } catch (_: android.content.ActivityNotFoundException) {
        // 高德地图未安装，尝试系统 geo: 选择器
    }
    val geoIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
        data = android.net.Uri.parse("geo:$lat,$lng?q=$lat,$lng(${android.net.Uri.encode(name)})")
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

@Composable
private fun SpotInfoCard(
    name: String,
    description: String,
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
            if (description.isNotBlank()) {
                Text(
                    text = description,
                    modifier = Modifier.padding(top = 4.dp),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = TextSecondary,
                    maxLines = 3,
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
    onResultClick: (MapPoi) -> Unit,
    modifier: Modifier = Modifier
) {
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
                    text = "输入关键字搜索",
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
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = Primary,
                                modifier = Modifier.size(18.dp)
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
                        }
                    }
                }
            }
        }
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
