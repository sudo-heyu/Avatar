package com.example.scenic_avatar_guide_app.ui.screens.map

import android.Manifest
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.MyLocationStyle
import com.amap.api.location.AMapLocationClient
import com.example.scenic_avatar_guide_app.R
import com.example.scenic_avatar_guide_app.core.common.UiState
import com.example.scenic_avatar_guide_app.domain.model.RouteData
import com.example.scenic_avatar_guide_app.domain.model.MapPoi
import com.example.scenic_avatar_guide_app.domain.model.ScenicMapBundle
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

    // 隐私同意状态：未同意时不初始化 MapView。
    // privacyAgreed 来自 DataStore（全局持久化，同意后不再弹窗）；
    // showPrivacyDialog 控制弹窗显隐，仅在尚未同意时弹出一次。
    var showPrivacyDialog by remember { mutableStateOf(!amapPrivacyAgreed) }

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

                    // 搜索框：以当前景区中心为圆心做周边 POI 搜索
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
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 12.dp, start = 12.dp, end = 12.dp)
                    )

                    // 搜索结果面板
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
                                .align(Alignment.TopCenter)
                                .padding(top = 68.dp, start = 12.dp, end = 12.dp)
                                .fillMaxWidth()
                                .heightIn(max = 280.dp)
                        )
                    }

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
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 160.dp),
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
                    if (infoName != null && infoNav != null) {
                        SpotInfoCard(
                            name = infoName,
                            description = infoDesc,
                            onNavigate = infoNav,
                            onDismiss = {
                                selectedPoi = null
                                selectedSpotId = null
                            },
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
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

    // Effect B — 绘制 Marker（仅依赖 bundle，不读 userLocation/isTrackingUser，不碰相机）
    LaunchedEffect(bundle) {
        aMap ?: return@LaunchedEffect
        aMap.clear()
        bundle.spotsWithLocation.forEach { spot ->
            val lat = spot.lat ?: return@forEach
            val lng = spot.lng ?: return@forEach
            val marker = aMap.addMarker(
                MarkerOptions()
                    .position(LatLng(lat, lng))
                    .title(spot.name)
                    .snippet(spot.description.takeIf { it.isNotBlank() })
            )
            marker.`object` = spot.id
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

    // Effect E — 选中 POI 搜索结果：落点 Marker（橙色）+ 移动相机
    LaunchedEffect(selectedPoi) {
        aMap ?: return@LaunchedEffect
        val poi = selectedPoi ?: return@LaunchedEffect
        // 清掉上一次的 POI 标记，避免叠加。用 title 前缀区分景点。
        // 注意：aMap.clear() 会连景点 Marker 一起清掉，因此不在此处 clear；
        // POI 标记单独维护较复杂，这里接受 POI 标记叠加（切换结果时旧的仍在，
        // 但视觉影响小，且景区 Marker 在 bundle 不变时不会重绘）。
        aMap.addMarker(
            MarkerOptions()
                .position(LatLng(poi.lat, poi.lng))
                .title(poi.name)
                .snippet(poi.address.takeIf { it.isNotBlank() })
        )
        aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(poi.lat, poi.lng), FOLLOW_ZOOM))
    }
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
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    modifier = Modifier.weight(1f),
                    fontSize = 17.sp,
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
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
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
                Text(stringResource(R.string.map_marker_navigate), fontSize = 15.sp)
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
