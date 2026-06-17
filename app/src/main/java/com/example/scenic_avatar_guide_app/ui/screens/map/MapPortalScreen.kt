package com.example.scenic_avatar_guide_app.ui.screens.map

import android.Manifest
import android.graphics.Color.parseColor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MyLocation
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
import com.amap.api.maps.model.PolylineOptions
import com.example.scenic_avatar_guide_app.R
import com.example.scenic_avatar_guide_app.core.common.UiState
import com.example.scenic_avatar_guide_app.domain.model.RouteData
import com.example.scenic_avatar_guide_app.domain.model.ScenicMapBundle
import com.example.scenic_avatar_guide_app.domain.model.ScenicRoute
import com.example.scenic_avatar_guide_app.ui.theme.*

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
    val selectedRouteId by viewModel.selectedRouteId.collectAsStateWithLifecycle()
    val currentScenicId by viewModel.currentScenicId.collectAsStateWithLifecycle()

    // 隐私同意状态：未同意时不初始化 MapView
    var privacyAgreed by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(true) }

    // 是否正在跟踪用户位置（点击定位按钮后触发）
    var isTrackingUser by remember { mutableStateOf(false) }

    // 首次获得用户位置时自动跟踪
    LaunchedEffect(userLocation) {
        if (userLocation != null && !isTrackingUser) {
            isTrackingUser = true
        }
    }

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

                    if (showPrivacyDialog) {
                        PrivacyConsentDialog(
                            onAgree = {
                                MapsInitializer.updatePrivacyShow(context, true, true)
                                MapsInitializer.updatePrivacyAgree(context, true)
                                privacyAgreed = true
                                showPrivacyDialog = false
                            },
                            onDisagree = { showPrivacyDialog = false }
                        )
                    }

                    if (privacyAgreed) {
                        MapViewContainer(
                            bundle = bundle,
                            userLocation = userLocation,
                            selectedRouteId = selectedRouteId,
                            isTrackingUser = isTrackingUser,
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
                            Text(
                                text = stringResource(R.string.map_privacy_required),
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }

                    MapTopBar(
                        title = bundle.area.name,
                        onBackClick = onBackClick,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )

                    RouteChips(
                        routes = bundle.mapData.routes,
                        selectedRouteId = selectedRouteId,
                        onRouteSelected = viewModel::selectRoute,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 96.dp, start = 16.dp, end = 16.dp)
                    )

                    FloatingActionButton(
                        onClick = { isTrackingUser = true },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 160.dp),
                        shape = CircleShape,
                        containerColor = Surface,
                        contentColor = Primary
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = stringResource(R.string.map_locate_me)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MapTopBar(
    title: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        shape = RoundedCornerShape(12.dp),
        color = Surface.copy(alpha = 0.92f),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.map_back),
                    tint = TextPrimary
                )
            }
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                fontSize = 18.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RouteChips(
    routes: List<ScenicRoute>,
    selectedRouteId: String?,
    onRouteSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    if (routes.isEmpty()) return

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        items(routes, key = { it.routeId }) { route ->
            val selected = route.routeId == selectedRouteId
            FilterChip(
                selected = selected,
                onClick = {
                    onRouteSelected(if (selected) null else route.routeId)
                },
                label = { Text(route.name, fontSize = 14.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = try {
                        androidx.compose.ui.graphics.Color(parseColor(route.color))
                    } catch (_: Exception) {
                        Primary
                    },
                    selectedLabelColor = androidx.compose.ui.graphics.Color.White,
                    containerColor = Surface,
                    labelColor = TextPrimary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected,
                    borderColor = try {
                        androidx.compose.ui.graphics.Color(parseColor(route.color))
                    } catch (_: Exception) {
                        Primary
                    }
                )
            )
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
    selectedRouteId: String?,
    isTrackingUser: Boolean,
    onLocationPermissionResult: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mapView = rememberMapViewWithLifecycle()

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
        factory = { mapView },
        update = { _ ->
            val aMap = mapView.map ?: return@AndroidView

            // 开启定位蓝点
            aMap.setMyLocationEnabled(true)
            aMap.myLocationStyle = MyLocationStyle().apply {
                myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE_NO_CENTER)
            }

            // 移动相机到景区中心或用户位置
            val center = if (isTrackingUser && userLocation != null) {
                userLocation
            } else {
                LatLng(bundle.mapData.centerLat, bundle.mapData.centerLng)
            }
            aMap.moveCamera(
                CameraUpdateFactory.newLatLngZoom(center, bundle.mapData.defaultZoom)
            )

            // 清除旧 Marker 和 Polyline
            aMap.clear()

            // 添加景点 Marker
            bundle.spotsWithLocation.forEach { spot ->
                val lat = spot.lat ?: return@forEach
                val lng = spot.lng ?: return@forEach
                aMap.addMarker(
                    MarkerOptions()
                        .position(LatLng(lat, lng))
                        .title(spot.name)
                        .snippet(spot.description.takeIf { it.isNotBlank() })
                )
            }

            // 绘制选中路线
            selectedRouteId?.let { routeId ->
                val route = bundle.mapData.routes.find { it.routeId == routeId }
                route?.polyline?.let { points ->
                    if (points.size >= 2) {
                        val colorInt = try {
                            parseColor(route.color)
                        } catch (_: Exception) {
                            parseColor("#1D7A6D")
                        }
                        aMap.addPolyline(
                            PolylineOptions()
                                .addAll(points.map { LatLng(it.lat, it.lng) })
                                .color(colorInt)
                                .width(12f)
                                .lineJoinType(PolylineOptions.LineJoinType.LineJoinRound)
                        )
                    }
                }
            }
        }
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
