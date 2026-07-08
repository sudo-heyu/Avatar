package com.example.scenic_avatar_guide_app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.getSystemService
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.rememberAsyncImagePainter
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import com.example.scenic_avatar_guide_app.R
import com.example.scenic_avatar_guide_app.core.common.UiState
import com.example.scenic_avatar_guide_app.ui.theme.*
import com.example.scenic_avatar_guide_app.ui.components.ArcWaveform
import com.example.scenic_avatar_guide_app.ui.components.AvatarView
import com.example.scenic_avatar_guide_app.ui.components.MarkdownBubbleText
import com.example.scenic_avatar_guide_app.ui.components.MarkdownWithTable
import com.example.scenic_avatar_guide_app.ui.components.scenicintro.ScenicIntroScreen
import com.example.scenic_avatar_guide_app.ui.components.scenicintro.ScenicSelectorChip
import com.example.scenic_avatar_guide_app.domain.model.ChatMessage
import com.example.scenic_avatar_guide_app.domain.model.ChatImageInfo
import com.example.scenic_avatar_guide_app.domain.model.RouteData
import com.example.scenic_avatar_guide_app.domain.model.MapCover
import com.example.scenic_avatar_guide_app.domain.model.AvatarState
import com.example.scenic_avatar_guide_app.core.speech.SpeechRecognizerHelper
import com.example.scenic_avatar_guide_app.core.avatar.AvatarDisplayMode
import com.example.scenic_avatar_guide_app.core.avatar.TestAvatarActions
import com.example.scenic_avatar_guide_app.core.avatar.AvatarPlayAction
import com.example.scenic_avatar_guide_app.core.avatar.AvatarCostumeOption
import com.example.scenic_avatar_guide_app.core.avatar.AvatarCostumeSelectionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val FallbackBottomReserve = 100.dp
private val MessageToFunctionCardGap = 8.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onSettingsClick: () -> Unit = {},
    externalAuthTrigger: Int = 0,
    viewModel: MainViewModel = hiltViewModel()
) {
    var scenicPortalTab by remember { mutableStateOf<ScenicIntroTab?>(null) }
    var mapRouteData by remember { mutableStateOf<RouteData?>(null) }

    BackHandler(enabled = scenicPortalTab != null || mapRouteData != null) {
        when {
            mapRouteData != null -> mapRouteData = null
            scenicPortalTab != null -> scenicPortalTab = null
        }
    }

    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val inputText by viewModel.inputText.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val currentMode by viewModel.currentMode.collectAsStateWithLifecycle()
    val avatarState by viewModel.avatarState.collectAsStateWithLifecycle()
    val avatarFullState by viewModel.avatarFullState.collectAsStateWithLifecycle()
    val voiceInputMode by viewModel.voiceInputMode.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val volumeLevel by viewModel.volumeLevel.collectAsStateWithLifecycle()
    val showTestPanel by viewModel.showTestPanel.collectAsStateWithLifecycle()
    val pendingImageUri by viewModel.pendingImageUri.collectAsStateWithLifecycle()
    val isConversationActive by viewModel.isConversationActive.collectAsStateWithLifecycle()
    val showScenicSelection by viewModel.showScenicSelection.collectAsStateWithLifecycle()
    val scenicAreas by viewModel.scenicAreas.collectAsStateWithLifecycle()
    val showAuthDialog by viewModel.showAuthDialog.collectAsStateWithLifecycle()
    val isAuthenticated by viewModel.isAuthenticated.collectAsStateWithLifecycle()
    val authUsername by viewModel.authUsername.collectAsStateWithLifecycle()
    val isAuthLoading by viewModel.isAuthLoading.collectAsStateWithLifecycle()
    val authError by viewModel.authError.collectAsStateWithLifecycle()
    val currentVoice by viewModel.currentVoice.collectAsStateWithLifecycle()
    val showFeedbackDialog by viewModel.showFeedbackDialog.collectAsStateWithLifecycle()
    val isSubmittingFeedback by viewModel.isSubmittingFeedback.collectAsStateWithLifecycle()
    val feedbackResult by viewModel.feedbackResult.collectAsStateWithLifecycle()
    val currentAssistantMessageId by viewModel.currentAssistantMessageId.collectAsStateWithLifecycle()
    val typewriterFinishedIds by viewModel.typewriterFinishedIds.collectAsStateWithLifecycle()
    val avatarCostumeState by viewModel.avatarCostumeState.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val density = LocalDensity.current
    var isCancelZone by remember { mutableStateOf(false) }
    var showImagePickerDialog by remember { mutableStateOf(false) }
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    var showVoiceDialog by remember { mutableStateOf(false) }
    var showAvatarDialog by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var avatarDisplayMode by rememberSaveable { mutableStateOf(AvatarDisplayMode.UpperBody) }
    var bottomControlsContentHeightPx by remember { mutableIntStateOf(0) }
    val navigationBottomPx = WindowInsets.navigationBars.getBottom(density)
    val fixedBottomBarHeight = if (bottomControlsContentHeightPx > 0) {
        with(density) { (bottomControlsContentHeightPx + navigationBottomPx).toDp() } + MessageToFunctionCardGap
    } else {
        FallbackBottomReserve
    }
    val fullBodyExpanded = avatarDisplayMode == AvatarDisplayMode.FullBodyExpanded
    val avatarSectionWeight by animateFloatAsState(
        targetValue = if (fullBodyExpanded) 3.55f else 2f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "avatarSectionWeight"
    )
    val messageSectionWeight by animateFloatAsState(
        targetValue = if (fullBodyExpanded) 1.45f else 3f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "messageSectionWeight"
    )

    // 外部触发显示认证弹窗（例如从设置页点击登录）
    LaunchedEffect(externalAuthTrigger) {
        if (externalAuthTrigger > 0 && !showScenicSelection) {
            viewModel.showAuthDialog()
        }
    }

    // 用户是否在底部附近（用于判断是否自动滚动）
    // 当用户上滑查看历史时，不自动滚动；用户滚回底部时恢复自动滚动
    var isUserAtBottom by remember { mutableStateOf(true) }
    var autoScrollEnabled by remember { mutableStateOf(true) }
    var isProgrammaticScroll by remember { mutableStateOf(false) }

    // 用于跟踪最后一条消息的内容变化（打字机效果）
    var lastMessageContent by remember { mutableStateOf("") }

    // 侧边栏状态
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var sessionListRefreshKey by remember { mutableStateOf(0) }

    val sessionListNeedsRefresh by viewModel.sessionListNeedsRefresh.collectAsStateWithLifecycle()

    LaunchedEffect(sessionListNeedsRefresh) {
        if (sessionListNeedsRefresh > 0) {
            sessionListRefreshKey++
        }
    }

    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen) {
            sessionListRefreshKey++
        }
    }

    var hasAudioPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAudioPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        hasAudioPermission = isGranted
        if (isGranted) viewModel.enterVoiceInputMode()
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.setPendingImage(it.toString()) }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            cameraImageUri?.let { viewModel.setPendingImage(it.toString()) }
        }
        cameraImageUri = null
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        hasCameraPermission = isGranted
        if (isGranted) {
            val uri = createImageUri(context)
            if (uri != null) {
                cameraImageUri = uri
                cameraLauncher.launch(uri)
            }
        }
    }

    val speechHelper = remember {
        SpeechRecognizerHelper(
            context = context,
            onResult = { viewModel.onSpeechRecognized(it) },
            onError = { viewModel.onSpeechError(it) },
            onReadyForSpeech = { viewModel.startRecording() },
            onEndOfSpeech = { viewModel.stopRecording() },
            onVolumeChanged = { viewModel.updateVolumeLevel(it) }
        )
    }

    DisposableEffect(Unit) { onDispose { speechHelper.destroy() } }

    var autoScrollJob by remember { mutableStateOf<Job?>(null) }

    fun isMessageListAtBottom(): Boolean {
        if (messages.isEmpty()) return true
        val layoutInfo = listState.layoutInfo
        val lastItem = layoutInfo.visibleItemsInfo.lastOrNull { it.index == messages.lastIndex }
            ?: return false
        return lastItem.offset + lastItem.size <= layoutInfo.viewportEndOffset + 12
    }

    suspend fun alignLastMessageToBottom() {
        if (messages.isEmpty()) return
        val lastIndex = messages.lastIndex
        var layoutInfo = listState.layoutInfo
        var lastItem = layoutInfo.visibleItemsInfo.lastOrNull { it.index == lastIndex }

        if (lastItem == null) {
            listState.scrollToItem(lastIndex)
            delay(16)
            layoutInfo = listState.layoutInfo
            lastItem = layoutInfo.visibleItemsInfo.lastOrNull { it.index == lastIndex }
        }

        val visibleLastItem = lastItem ?: return
        val delta = visibleLastItem.offset + visibleLastItem.size - layoutInfo.viewportEndOffset + 8
        if (delta > 1) {
            listState.scrollBy(delta.toFloat())
        }
    }

    // 滚动到底部，确保最新内容可见
    val scrollToBottom = {
        autoScrollJob?.cancel()
        autoScrollJob = coroutineScope.launch {
            isProgrammaticScroll = true
            try {
                alignLastMessageToBottom()
            } finally {
                isProgrammaticScroll = false
            }
        }
    }

    LaunchedEffect(listState, messages.size) {
        snapshotFlow { listState.isScrollInProgress to isMessageListAtBottom() }
            .collect { (isScrolling, isAtBottom) ->
                isUserAtBottom = isAtBottom
                if (isScrolling && !isProgrammaticScroll && !isAtBottom) {
                    autoScrollEnabled = false
                    autoScrollJob?.cancel()
                } else if (isAtBottom) {
                    autoScrollEnabled = true
                }
            }
    }

    // 新消息到来时滚动到底部
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && autoScrollEnabled) {
            scrollToBottom()
        }
    }

    // 打字机效果：当最后一条消息内容变化时，如果用户在底部则跟随滚动
    LaunchedEffect(messages.lastOrNull()?.content) {
        val lastMessage = messages.lastOrNull()
        if (lastMessage != null && !lastMessage.isUser && lastMessage.content != lastMessageContent) {
            lastMessageContent = lastMessage.content
            // 只有当用户在底部附近时才自动滚动
            if (autoScrollEnabled && messages.isNotEmpty()) {
                scrollToBottom()
            }
        }
    }

    LaunchedEffect(messages.lastOrNull()?.images?.joinToString { it.imageId ?: it.url ?: it.publicPath.orEmpty() }) {
        val lastMessage = messages.lastOrNull()
        if (lastMessage != null && !lastMessage.isUser && lastMessage.images.isNotEmpty()) {
            scrollToBottom()
        }
    }

    // 侧边栏：占屏幕3/4宽度
    val configuration = LocalConfiguration.current
    val drawerWidth = remember { (configuration.screenWidthDp.dp * 3 / 4) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ChatHistoryDrawer(
                drawerWidth = drawerWidth,
                refreshKey = sessionListRefreshKey,
                onSettingsClick = onSettingsClick,
                onCloseDrawer = { coroutineScope.launch { drawerState.close() } },
                onSessionSelected = { sessionId ->
                    coroutineScope.launch { drawerState.close() }
                    viewModel.switchToSession(sessionId)
                },
                onNewSession = {
                    coroutineScope.launch { drawerState.close() }
                    viewModel.startEmptyChat()
                },
                onScenicIntroClick = { tab ->
                    scenicPortalTab = tab
                    coroutineScope.launch { drawerState.snapTo(DrawerValue.Closed) }
                },
                isAuthenticated = isAuthenticated,
                authUsername = authUsername,
                onAuthClick = {
                    coroutineScope.launch { drawerState.close() }
                    viewModel.showAuthDialog()
                }
            )
        },
        gesturesEnabled = drawerState.isOpen && scenicPortalTab == null && mapRouteData == null
    ) {
        Scaffold(
            containerColor = Surface,
            contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)
        ) { paddingValues ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .statusBarsPadding()
            ) {
                // 主内容区域不读取、不测量 IME。底部 Spacer 使用底栏内容的静态高度，
                // 让消息区域真实截止在固定功能卡片上方，而不是判定到整页底部。
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // 数字人区域：在主内容区域内按比例分配
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(avatarSectionWeight)
                    ) {
                        AvatarSection(
                            avatarState = avatarState,
                            fullState = avatarFullState,
                            mouthState = viewModel.mouthState,
                            displayMode = avatarDisplayMode,
                            onRendererReady = { renderer ->
                                // 设置渲染器状态重置回调，解决 StateFlow 合并跳过 IDLE 问题
                                viewModel.setResetSpeakingStateCallback {
                                    renderer.resetSpeakingState()
                                }
                                // 注入渲染器给形象管理器，用于换装时 GL 线程热重载贴图
                                viewModel.attachAvatarRenderer(renderer)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        AvatarOverlayControls(
                            onMenuClick = { coroutineScope.launch { drawerState.open() } },
                            showTestPanel = showTestPanel,
                            onToggleTestPanel = { viewModel.toggleTestPanel() },
                            onVoiceClick = { showVoiceDialog = true },
                            onAvatarClick = {
                                viewModel.refreshAvatarCostumes()
                                showAvatarDialog = true
                            },
                            onScenicClick = { viewModel.showScenicSelectionDialog() },
                            onLogoutClick = { showLogoutConfirm = true },
                            isAuthenticated = isAuthenticated,
                            currentMode = currentMode,
                            avatarDisplayMode = avatarDisplayMode,
                            onAvatarDisplayModeChange = { avatarDisplayMode = it },
                            onRouteToggle = {
                                viewModel.switchMode(
                                    if (currentMode == InteractionMode.Route) InteractionMode.Chat
                                    else InteractionMode.Route
                                )
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // 消息列表：只为底栏常态高度留白；输入法弹出不额外压缩消息区域
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(messageSectionWeight)
                    ) {
                        MessageList(
                            messages = messages,
                            isLoading = isLoading,
                            listState = listState,
                            modifier = Modifier.fillMaxSize(),
                            bottomPaddingDp = 8.dp,
                            onUserInteraction = {
                                autoScrollEnabled = false
                                autoScrollJob?.cancel()
                            },
                            onFeedbackClick = { messageId -> viewModel.showFeedbackDialog(messageId) },
                            onRouteCardClick = { routeData -> mapRouteData = routeData },
                            currentAssistantMessageId = currentAssistantMessageId,
                            typewriterFinishedIds = typewriterFinishedIds,
                            resolveMapCover = viewModel::resolveMapCover
                        )

                        // 滚动到底部按钮：当用户上滑查看历史时显示
                        if (!isUserAtBottom && messages.isNotEmpty()) {
                            FloatingActionButton(
                                onClick = {
                                    autoScrollEnabled = true
                                    isUserAtBottom = true
                                    scrollToBottom()
                                },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 16.dp)
                                    .size(40.dp),
                                containerColor = Primary,
                                contentColor = Color.White
                            ) {
                                Icon(
                                    Icons.Default.KeyboardArrowDown,
                                    contentDescription = "滚动到底部",
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(fixedBottomBarHeight)
                    )
                }

                // 底栏：随输入法上升；主内容区不响应 IME，保持数字人和消息区域位置固定
                // 测试卡片在稳定控制区之外单独渲染，不参与 Spacer 高度计算
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Surface)
                    ) {
                        // 测试面板：位于最上方，但不参与稳定高度测量，出现/消失不影响消息区和数字人区
                        if (showTestPanel) {
                            CompactTestPanel(
                                onActionClick = { action -> viewModel.playTestAction(action) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // 稳定控制区：只测量这部分高度作为 Spacer 依据
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onSizeChanged { size ->
                                    bottomControlsContentHeightPx = size.height
                                }
                        ) {
                            if (voiceInputMode && isRecording) {
                                CancelZone(
                                    isCancelZone = isCancelZone,
                                    modifier = Modifier.fillMaxWidth().height(48.dp)
                                )
                            }

                            if (voiceInputMode && isRecording) {
                                VoiceWaveformSection(
                                    volumeLevel = volumeLevel,
                                    isCancelZone = isCancelZone,
                                    modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp)
                                )
                            }

                            // 输入框：位于最下方
                            if (voiceInputMode) {
                                VoiceInputButton(
                                    isRecording = isRecording,
                                    isCancelZone = isCancelZone,
                                    onCancelZoneChange = { isCancelZone = it },
                                    onVoiceStart = { speechHelper.startListening() },
                                    onVoiceStop = { speechHelper.stopListening() },
                                    onCancel = { speechHelper.cancel(); viewModel.exitVoiceInputMode() },
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            } else {
                                InputSection(
                                    mode = currentMode, inputText = inputText, isLoading = isLoading,
                                    isConversationActive = isConversationActive,
                                    pendingImageUri = pendingImageUri,
                                    onInputChange = { viewModel.updateInputText(it) },
                                    onSend = { viewModel.sendMessage() },
                                    onAbort = { viewModel.abortConversation() },
                                    onModeChange = { viewModel.switchMode(it) },
                                    onVoiceClick = {
                                        if (hasAudioPermission) viewModel.enterVoiceInputMode()
                                        else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    },
                                    onCameraInput = { showImagePickerDialog = true },
                                    onClearImage = { viewModel.clearPendingImage() },
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }

                            // AI 生成内容提示
                            Text(
                                text = "内容由AI生成",
                                fontSize = 10.sp,
                                color = TextHint,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp, bottom = 4.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }

    when {
        mapRouteData != null -> {
            com.example.scenic_avatar_guide_app.ui.screens.map.MapPortalScreen(
                routeData = mapRouteData,
                onBackClick = { mapRouteData = null },
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            )
        }
        scenicPortalTab != null -> {
            val tab = scenicPortalTab!!
            if (tab == ScenicIntroTab.Map) {
                com.example.scenic_avatar_guide_app.ui.screens.map.MapPortalScreen(
                    onBackClick = { scenicPortalTab = null },
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                )
            } else {
                ScenicIntroPortalScreen(
                    tab = tab,
                    onBackClick = { scenicPortalTab = null },
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                )
            }
        }
    }

    if (showImagePickerDialog) {
        ImageSourceDialog(
            onDismiss = { showImagePickerDialog = false },
            onCameraClick = {
                showImagePickerDialog = false
                if (hasCameraPermission) {
                    val uri = createImageUri(context)
                    if (uri != null) {
                        cameraImageUri = uri
                        cameraLauncher.launch(uri)
                    }
                } else {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            onGalleryClick = {
                showImagePickerDialog = false
                galleryLauncher.launch("image/*")
            }
        )
    }

    if (showScenicSelection) {
        ScenicSelectionDialog(
            scenicAreas = scenicAreas,
            onSelected = { scenicId, spotId ->
                viewModel.onScenicSpotSelected(scenicId, spotId)
            },
            onDismiss = { viewModel.dismissScenicSelection() }
        )
    }

    if (showAuthDialog) {
        com.example.scenic_avatar_guide_app.ui.components.AuthDialog(
            onDismiss = { viewModel.dismissAuthDialog() },
            onLogin = { username, password -> viewModel.login(username, password) },
            onRegister = { username, password -> viewModel.register(username, password) },
            isLoading = isAuthLoading,
            errorMessage = authError
        )
    }

    if (showVoiceDialog) {
        VoiceSelectionDialog(
            voices = viewModel.getAvailableVoices(),
            currentVoiceId = currentVoice.id,
            onVoiceSelected = { voiceId ->
                viewModel.setVoice(voiceId)
                showVoiceDialog = false
            },
            onDismiss = { showVoiceDialog = false }
        )
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = {
                Text(
                    "确认退出登录？",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    "退出后将清除当前会话，并切换为游客模式。",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirm = false
                        viewModel.logout()
                        Toast.makeText(context, "已退出登录", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Error)
                ) {
                    Text("确认退出", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showLogoutConfirm = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("取消", fontSize = 15.sp, color = TextSecondary)
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showFeedbackDialog) {
        com.example.scenic_avatar_guide_app.ui.components.FeedbackDialog(
            onDismiss = { viewModel.dismissFeedbackDialog() },
            onSubmit = { rating, isComplaint, comment ->
                viewModel.submitFeedback(rating, isComplaint, comment)
            },
            isSubmitting = isSubmittingFeedback
        )
    }

    feedbackResult?.let { result ->
        LaunchedEffect(result) {
            Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
            viewModel.clearFeedbackResult()
        }
    }

    if (showAvatarDialog) {
        AvatarCostumeSelectionDialog(
            state = avatarCostumeState,
            onDismiss = { showAvatarDialog = false },
            onRefresh = { viewModel.refreshAvatarCostumes() },
            onSelect = { optionId -> viewModel.applyAvatarCostume(optionId) }
        )
    }

    LaunchedEffect(avatarCostumeState) {
        when (val s = avatarCostumeState) {
            is AvatarCostumeSelectionState.Applied -> {
                Toast.makeText(context, "已切换为${s.option.name}", Toast.LENGTH_SHORT).show()
                showAvatarDialog = false
                viewModel.dismissAvatarCostumeState()
            }
            is AvatarCostumeSelectionState.Failed -> {
                Toast.makeText(context, "形象切换失败：${s.message}", Toast.LENGTH_LONG).show()
                viewModel.dismissAvatarCostumeState()
            }
            else -> Unit
        }
    }
}

@Composable
private fun AvatarOverlayControls(
    onMenuClick: () -> Unit,
    showTestPanel: Boolean,
    onToggleTestPanel: () -> Unit,
    onVoiceClick: () -> Unit,
    onAvatarClick: () -> Unit,
    onScenicClick: () -> Unit,
    onLogoutClick: () -> Unit,
    isAuthenticated: Boolean,
    currentMode: InteractionMode = InteractionMode.Chat,
    avatarDisplayMode: AvatarDisplayMode,
    onAvatarDisplayModeChange: (AvatarDisplayMode) -> Unit,
    onRouteToggle: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        AvatarCornerIconButton(
            onClick = onMenuClick,
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "打开侧边栏",
                tint = TextSecondary,
                modifier = Modifier.size(22.dp)
            )
        }

        Box(modifier = Modifier.align(Alignment.TopEnd)) {
            AvatarCornerIconButton(onClick = { showMenu = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "更多选项",
                    tint = TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.width(196.dp),
                containerColor = Color.White,
                shape = RoundedCornerShape(12.dp),
                shadowElevation = 8.dp
            ) {
                DropdownMenuItem(
                    text = { Text("音色选择", fontSize = 14.sp, color = TextPrimary) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = Primary
                        )
                    },
                    onClick = {
                        showMenu = false
                        onVoiceClick()
                    }
                )
                DropdownMenuItem(
                    text = { Text("形象选择", fontSize = 14.sp, color = TextPrimary) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Face,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = Primary
                        )
                    },
                    onClick = {
                        showMenu = false
                        onAvatarClick()
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            "半身显示",
                            fontSize = 14.sp,
                            color = if (avatarDisplayMode == AvatarDisplayMode.UpperBody) Primary else TextPrimary,
                            fontWeight = if (avatarDisplayMode == AvatarDisplayMode.UpperBody) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.AccessibilityNew,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if (avatarDisplayMode == AvatarDisplayMode.UpperBody) Primary else TextSecondary
                        )
                    },
                    trailingIcon = if (avatarDisplayMode == AvatarDisplayMode.UpperBody) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Primary
                            )
                        }
                    } else null,
                    onClick = {
                        showMenu = false
                        onAvatarDisplayModeChange(AvatarDisplayMode.UpperBody)
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            "全身显示",
                            fontSize = 14.sp,
                            color = if (avatarDisplayMode == AvatarDisplayMode.FullBodyExpanded) Primary else TextPrimary,
                            fontWeight = if (avatarDisplayMode == AvatarDisplayMode.FullBodyExpanded) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.OpenInFull,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if (avatarDisplayMode == AvatarDisplayMode.FullBodyExpanded) Primary else TextSecondary
                        )
                    },
                    trailingIcon = if (avatarDisplayMode == AvatarDisplayMode.FullBodyExpanded) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Primary
                            )
                        }
                    } else null,
                    onClick = {
                        showMenu = false
                        onAvatarDisplayModeChange(AvatarDisplayMode.FullBodyExpanded)
                    }
                )
                val isRouteMode = currentMode == InteractionMode.Route
                DropdownMenuItem(
                    text = {
                        Text(
                            if (isRouteMode) "路线规划（已开启）" else "路线规划",
                            fontSize = 14.sp,
                            color = if (isRouteMode) Primary else TextPrimary,
                            fontWeight = if (isRouteMode) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if (isRouteMode) Primary else TextSecondary
                        )
                    },
                    trailingIcon = if (isRouteMode) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Primary
                            )
                        }
                    } else null,
                    onClick = {
                        showMenu = false
                        onRouteToggle()
                    }
                )
                DropdownMenuItem(
                    text = { Text("景点位置", fontSize = 14.sp, color = TextPrimary) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Landscape,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = Primary
                        )
                    },
                    onClick = {
                        showMenu = false
                        onScenicClick()
                    }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    color = SurfaceVariant
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            if (showTestPanel) "隐藏测试面板" else "显示测试面板",
                            fontSize = 14.sp,
                            color = TextPrimary
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (showTestPanel) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = TextSecondary
                        )
                    },
                    onClick = {
                        showMenu = false
                        onToggleTestPanel()
                    }
                )
                if (isAuthenticated) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        color = SurfaceVariant
                    )
                    DropdownMenuItem(
                        text = { Text("退出登录", fontSize = 14.sp, color = Error) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Logout,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = Error
                            )
                        },
                        onClick = {
                            showMenu = false
                            onLogoutClick()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AvatarCornerIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Surface(
        modifier = modifier.size(42.dp),
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.88f),
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, SurfaceVariant.copy(alpha = 0.75f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
            content = content
        )
    }
}

@Composable
private fun AvatarCostumeSelectionDialog(
    state: AvatarCostumeSelectionState,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onSelect: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("形象选择", fontWeight = FontWeight.SemiBold) },
        text = {
            when (state) {
                is AvatarCostumeSelectionState.Loading -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("加载中", fontSize = 14.sp, color = TextSecondary)
                    }
                }
                is AvatarCostumeSelectionState.Ready -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(state.items, key = { it.id }) { option ->
                            AvatarCostumeOptionRow(
                                option = option,
                                selected = option.id == state.selectedId,
                                applying = false,
                                onClick = { onSelect(option.id) }
                            )
                        }
                    }
                }
                is AvatarCostumeSelectionState.Applying -> {
                    AvatarCostumeOptionRow(
                        option = state.option,
                        selected = true,
                        applying = true,
                        onClick = {}
                    )
                }
                is AvatarCostumeSelectionState.Applied -> {
                    AvatarCostumeOptionRow(
                        option = state.option,
                        selected = true,
                        applying = false,
                        onClick = {}
                    )
                }
                is AvatarCostumeSelectionState.Failed -> {
                    Text(state.message, fontSize = 14.sp, color = Error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onRefresh) {
                Text("刷新")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(18.dp)
    )
}

@Composable
private fun AvatarCostumeOptionRow(
    option: AvatarCostumeOption,
    selected: Boolean,
    applying: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (selected) Primary.copy(alpha = 0.65f) else SurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .background(if (selected) Primary.copy(alpha = 0.06f) else Color.White)
            .clickable(enabled = !applying, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceVariant.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center
        ) {
            if (option.previewUrl != null) {
                AsyncImage(
                    model = option.previewUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Face,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = option.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = option.directoryName ?: "local_default",
                fontSize = 12.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (applying) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else if (selected) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * 历史会话侧边栏
 */
@Composable
private fun ChatHistoryDrawer(
    drawerWidth: Dp,
    refreshKey: Int,
    onSettingsClick: () -> Unit,
    onCloseDrawer: () -> Unit,
    onSessionSelected: (String) -> Unit,
    onNewSession: () -> Unit,
    onScenicIntroClick: (ScenicIntroTab) -> Unit,
    isAuthenticated: Boolean = false,
    authUsername: String? = null,
    onAuthClick: () -> Unit = {},
    viewModel: SessionListViewModel = hiltViewModel()
) {
    val sessions by viewModel.sessions.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    LaunchedEffect(refreshKey) {
        viewModel.loadSessions()
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let { viewModel.clearError() }
    }

    ModalDrawerSheet(
        modifier = Modifier.width(drawerWidth),
        drawerContainerColor = Surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            // 标题区域 + 新建按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "菜单",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                IconButton(onClick = onNewSession) {
                    Icon(
                        painter = painterResource(id = R.drawable.create_session),
                        contentDescription = "新建对话",
                        modifier = Modifier.size(24.dp),
                        tint = Primary
                    )
                }
            }

            Text(
                text = "功能入口",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            ScenicFeatureEntryGrid(onEntryClick = onScenicIntroClick)

            Text(
                text = "历史记录",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary,
                modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)
            )

            // 会话列表区域
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (isLoading && sessions.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (sessions.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .padding(top = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.ChatBubbleOutline,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = TextHint
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "暂无历史会话",
                            fontSize = 14.sp,
                            color = TextHint
                        )
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = onNewSession,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Primary
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                width = 1.dp,
                                color = Primary.copy(alpha = 0.35f)
                            )
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.create_session),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("新建会话")
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        items(sessions, key = { it.sessionId }) { session ->
                            SessionDrawerItem(
                                session = session,
                                onClick = {
                                    onSessionSelected(session.sessionId)
                                },
                                onDelete = { viewModel.deleteSession(session.sessionId) }
                            )
                        }
                    }
                }
            }

            // 底部区域：左侧头像+用户名，右侧设置按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧：头像 + 用户名/登录按钮
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(enabled = !isAuthenticated) { onAuthClick() }
                ) {
                    Image(
                        painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                        contentDescription = "头像",
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isAuthenticated) (authUsername ?: "用户") else "登录",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isAuthenticated) Primary else TextSecondary
                        )
                        if (!isAuthenticated) {
                            Text(
                                text = "点击登录账号",
                                fontSize = 12.sp,
                                color = TextHint
                            )
                        }
                    }
                }

                // 右侧：设置按钮
                IconButton(
                    onClick = {
                        onCloseDrawer()
                        onSettingsClick()
                    }
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "设置",
                        tint = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun ScenicFeatureEntryGrid(
    onEntryClick: (ScenicIntroTab) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ScenicIntroTab.entries.forEach { tab ->
            ScenicFeatureEntryItem(
                tab = tab,
                onClick = { onEntryClick(tab) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ScenicFeatureEntryItem(
    tab: ScenicIntroTab,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp),
        shape = RoundedCornerShape(12.dp),
        color = ScenicPrimaryBg,
        border = BorderStroke(1.dp, ScenicPrimaryLight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(32.dp),
                shape = RoundedCornerShape(9.dp),
                color = ScenicPrimaryDark
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(id = tab.iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(19.dp),
                        tint = Color.White
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = tab.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = ScenicPrimaryDarker,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private enum class ScenicIntroTab(
    val title: String,
    val iconRes: Int,
    val placeholder: String
) {
    Intro("景区介绍", R.drawable.ic_jingqu, "景区介绍内容待接入"),
    Reservation("场馆预约", R.drawable.ic_changguan, "预约服务内容待接入"),
    Map("景区地图", R.drawable.ic_map, "景区地图")
}

@Composable
private fun ScenicIntroPortalScreen(
    tab: ScenicIntroTab,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scenicIntroViewModel: ScenicIntroViewModel = hiltViewModel()
    val indexState by scenicIntroViewModel.indexState.collectAsState()
    val selectedScenicId by scenicIntroViewModel.selectedScenicId.collectAsState()
    val indexItems = when (val state = indexState) {
        is UiState.Success -> state.data.scenics
        else -> emptyList()
    }

    Scaffold(
        modifier = modifier,
        containerColor = ScenicPrimaryBg,
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            ScenicPrimaryDark,
                            ScenicPrimary,
                            ScenicPrimaryBg
                        )
                    )
                )
                .padding(paddingValues)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.18f)
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回聊天",
                            tint = Color.White
                        )
                    }
                }
                Text(
                    text = tab.title,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (indexItems.isNotEmpty()) {
                    ScenicSelectorChip(
                        indexItems = indexItems,
                        selectedScenicId = selectedScenicId,
                        onScenicSelected = scenicIntroViewModel::selectScenic,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.size(42.dp))
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = ScenicPrimaryBg
            ) {
                val selectedScenicName = indexItems.find { it.scenicId == selectedScenicId }?.name ?: "当前景区"
                when (tab) {
                    ScenicIntroTab.Intro -> {
                        ScenicIntroScreen(
                            modifier = Modifier.fillMaxSize(),
                            viewModel = scenicIntroViewModel,
                            showScenicSelector = false,
                            showTopBar = false
                        )
                    }
                    ScenicIntroTab.Reservation -> {
                        ScenicReservationScreen(
                            scenicId = selectedScenicId,
                            scenicName = selectedScenicName
                        )
                    }
                    ScenicIntroTab.Map -> {
                        // Map 由外层单独渲染为全屏 Portal，不会进入此处
                        Box(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

@Composable
private fun ScenicReservationScreen(
    scenicId: String,
    scenicName: String
) {
    val content = remember(scenicId) { scenicReservationContent(scenicId) }
    if (content == null) {
        ScenicPortalEmptyState(
            title = scenicName,
            message = "场馆预约信息正在整理中"
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ScenicPortalHeroCard(
                title = content.title,
                subtitle = content.subtitle,
                imageUrl = content.heroImage,
                badges = content.badges
            )
        }
        item {
            ScenicReservationSummary(content = content)
        }
        items(content.openingCards) { card ->
            ScenicInfoCard(
                icon = Icons.Default.AccessTime,
                title = card.title,
                body = card.body,
                label = card.label
            )
        }
        item {
            ScenicReservationSteps(steps = content.steps)
        }
        item {
            ScenicInfoCard(
                icon = Icons.Default.Phone,
                title = "咨询电话",
                body = content.contact,
                label = "服务咨询"
            )
        }
        content.notice?.let { notice ->
            item {
                ScenicNoticeCard(text = notice)
            }
        }
    }
}

@Composable
private fun ScenicPortalHeroCard(
    title: String,
    subtitle: String,
    imageUrl: String,
    badges: List<String>
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        shape = RoundedCornerShape(26.dp),
        color = ScenicPrimaryDeep,
        shadowElevation = 8.dp
    ) {
        Box(modifier = Modifier.height(240.dp)) {
            AsyncImage(
                model = imageUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                ScenicPrimaryDeep.copy(alpha = 0.2f),
                                ScenicPrimaryDeep.copy(alpha = 0.88f)
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(18.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    badges.take(2).forEach { badge ->
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color.White.copy(alpha = 0.18f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                Color.White.copy(alpha = 0.28f)
                            )
                        ) {
                            Text(
                                text = badge,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                fontSize = 11.sp,
                                color = Color.White
                            )
                        }
                    }
                }
                Text(
                    text = title,
                    modifier = Modifier.padding(top = 12.dp),
                    fontSize = 24.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = subtitle,
                    modifier = Modifier.padding(top = 6.dp),
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = Color.White.copy(alpha = 0.78f)
                )
            }
        }
    }
}

@Composable
private fun ScenicReservationSummary(content: ScenicReservationContent) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ScenicMiniInfoCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.ConfirmationNumber,
            title = "门票",
            value = content.ticket
        )
        ScenicMiniInfoCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.EventAvailable,
            title = "预约",
            value = content.bookingType
        )
    }
}

@Composable
private fun ScenicMiniInfoCard(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String
) {
    Surface(
        modifier = modifier.height(112.dp),
        shape = RoundedCornerShape(18.dp),
        color = ScenicPrimaryBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, ScenicPrimaryLighter)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ScenicPrimaryDark,
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    color = ScenicPrimaryDarker
                )
                Text(
                    text = value,
                    modifier = Modifier.padding(top = 3.dp),
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = ScenicPrimaryDeep,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ScenicInfoCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    label: String
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(22.dp),
        color = Color.White,
        shadowElevation = 3.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, ScenicPrimaryLighter)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(14.dp),
                color = ScenicPrimaryBg
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = ScenicPrimaryDark,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 13.dp)
            ) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = ScenicPrimary
                )
                Text(
                    text = title,
                    modifier = Modifier.padding(top = 4.dp),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = body,
                    modifier = Modifier.padding(top = 7.dp),
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun ScenicReservationSteps(steps: List<String>) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(22.dp),
        color = ScenicPrimaryDark,
        shadowElevation = 5.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "预约流程",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(12.dp))
            steps.forEachIndexed { index, step ->
                Row(
                    modifier = Modifier.padding(vertical = 7.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Surface(
                        modifier = Modifier.size(28.dp),
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.2f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.28f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = (index + 1).toString(),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                    Text(
                        text = step,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 10.dp),
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        color = Color.White.copy(alpha = 0.88f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ScenicNoticeCard(text: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFFFF7E6),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFDFA3))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(14.dp),
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = Color(0xFF7A4A12)
        )
    }
}

@Composable
private fun ScenicPortalEmptyState(
    title: String,
    message: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(72.dp),
            shape = RoundedCornerShape(22.dp),
            color = ScenicPrimaryBg
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(38.dp),
                    tint = ScenicPrimaryDark
                )
            }
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = title,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = ScenicPrimaryDeep,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            fontSize = 14.sp,
            color = ScenicPrimaryDarker,
            textAlign = TextAlign.Center
        )
    }
}

private data class ScenicReservationContent(
    val title: String,
    val subtitle: String,
    val heroImage: String,
    val badges: List<String>,
    val ticket: String,
    val bookingType: String,
    val openingCards: List<ScenicInfoBlock>,
    val steps: List<String>,
    val contact: String,
    val notice: String? = null
)

private data class ScenicInfoBlock(
    val label: String,
    val title: String,
    val body: String
)

private fun scenicReservationContent(scenicId: String): ScenicReservationContent? {
    val lingshanBase = "file:///android_asset/scenic_intro/images/lingshan/"
    val nianhuaBase = "file:///android_asset/scenic_intro/images/nianhua/"
    return when (scenicId) {
        "lingshan" -> ScenicReservationContent(
            title = "灵山胜境演出与讲解预约",
            subtitle = "吉祥颂需另购票，博览馆讲解免费定时，建议按场次规划行程。",
            heroImage = lingshanBase + "LS-013_灵山梵宫.jpg",
            badges = listOf("演出预约", "免费讲解"),
            ticket = "吉祥颂 50元/人",
            bookingType = "小程序/现场",
            openingCards = listOf(
                ScenicInfoBlock(
                    label = "吉祥颂",
                    title = "灵山梵宫圣坛《吉祥颂》",
                    body = "270°沉浸式实景演绎，约20分钟。场次 10:35、11:30、14:00、16:00，节假日可能加演。普通门票不含，另购 50元/人，6周岁以下或70周岁以上同价。建议提前30分钟排队入场。"
                ),
                ScenicInfoBlock(
                    label = "九龙灌浴",
                    title = "九龙灌浴动态音乐群雕",
                    body = "大型音乐动态群雕，约15分钟。平日 10:00、11:30、13:30、15:00，周末节假日加场以广播为准。含门票，表演结束后可接取龙头流出的“圣水”。"
                ),
                ScenicInfoBlock(
                    label = "博览馆讲解",
                    title = "佛教文化博览馆免费定时讲解",
                    body = "位于灵山大佛座基内三层。场次 9:30、11:00、14:30、16:00。一层五方五佛与四大名山，二层世界佛教发展史，三层万佛殿祈福。免费，另有每30分钟一场沉浸式投影，可免费领取祈福卡。"
                )
            ),
            steps = listOf(
                "关注“灵山胜境”官方小程序，查看当日演出场次表。",
                "《吉祥颂》需在小程序或现场另购票，提前30分钟排队入场。",
                "九龙灌浴与博览馆讲解含门票或免费，提前10分钟到场即可。",
                "节假日以景区广播与小程序公告为准，适时调整行程。"
            ),
            contact = "400-128-7777 / 0510-85086637",
            notice = "演出时间可能因天气、节假日或现场管理调整，出行前建议通过“灵山胜境”官方小程序确认当日场次。"
        )
        "nianhua" -> ScenicReservationContent(
            title = "拈花湾夜游演艺与体验预约",
            subtitle = "《禅行》夜游为核心，手作与禅修体验可提前预约，建议下午入园兼顾日夜。",
            heroImage = nianhuaBase + "NH-007_拈花塔.jpg",
            badges = listOf("夜游演艺", "手作预约"),
            ticket = "含大门票",
            bookingType = "小程序/现场",
            openingCards = listOf(
                ScenicInfoBlock(
                    label = "禅行夜游",
                    title = "《禅行》沉浸式夜游四大篇章",
                    body = "行进式夜游含一苇渡江、亮塔仪式、花开五叶、拈花一笑。一苇渡江 18:00–20:50 每20分钟一场，亮塔仪式 18:30–20:50 每30分钟一场，花开五叶 18:45–20:45 每30分钟一场，拈花一笑 19:30/20:30 两场。建议提前30分钟到观赏点占位。"
                ),
                ScenicInfoBlock(
                    label = "开园仪式",
                    title = "拈花广场开园仪式",
                    body = "每日 9:30，节假日加演 14:30，约15分钟。入园第一项仪式，建议准点到场。"
                ),
                ScenicInfoBlock(
                    label = "禅修体验",
                    title = "拈花堂禅坐·抄经·禅茶",
                    body = "9:30–19:00 开放，禅坐、抄经、禅茶免费，讲座 10:30、15:30 各一场。手作体验（香囊/陶艺/木刻约50元、漆扇68–98元）可小程序或店铺预约。"
                )
            ),
            steps = listOf(
                "微信搜索“拈花湾”小程序，首页“节目演艺”查看当日场次表。",
                "《禅行》含大门票，按场次提前30分钟到达各观赏点。",
                "手作体验可小程序或店铺预约，禅修至拈花堂现场参与。",
                "雨天部分室外演出可能调整，以景区公告为准。"
            ),
            contact = "400-128-7777 / 0510-85086637",
            notice = "建议下午3点后入园兼顾日景与夜景；住景区内酒店可无限次进出并有专属观赏区域。"
        )
        else -> null
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionDrawerItem(
    session: com.example.scenic_avatar_guide_app.domain.model.SessionInfo,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showDeleteConfirm = true }
            )
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = session.displayTitle(),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }

    if (showDeleteConfirm) {
        Dialog(onDismissRequest = { showDeleteConfirm = false }) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = "确认删除",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "删除后该对话记录将无法恢复，是否继续？",
                        fontSize = 14.sp,
                        color = TextSecondary,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            onDelete()
                            showDeleteConfirm = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Error)
                    ) {
                        Text("删除", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { showDeleteConfirm = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("取消", fontSize = 15.sp, color = TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun AvatarSection(
    avatarState: AvatarState,
    fullState: com.example.scenic_avatar_guide_app.domain.model.AvatarFullState,
    mouthState: kotlinx.coroutines.flow.StateFlow<Pair<Float, Float>>,
    modifier: Modifier = Modifier,
    showUpperBodyOnly: Boolean = false,
    displayMode: AvatarDisplayMode? = null,
    onRendererReady: ((com.example.scenic_avatar_guide_app.core.avatar.Live2DRendererImpl) -> Unit)? = null
) {
    AvatarView(
        avatarState = avatarState,
        fullState = fullState,
        mouthState = mouthState,
        showUpperBodyOnly = showUpperBodyOnly,
        displayMode = displayMode,
        onRendererReady = onRendererReady,
        modifier = modifier
    )
}

/**
 * 测试面板：纯文字入口 + 内部展开，每行多个按钮
 */
@Composable
private fun CompactTestPanel(
    onActionClick: (AvatarPlayAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val expressionButtons = remember { TestAvatarActions.getExpressionButtons() }
    val gestureButtons = remember { TestAvatarActions.getGestureButtons() }
    val scenarioButtons = remember { TestAvatarActions.getScenarioButtons() }
    val lipSyncButtons = remember { TestAvatarActions.getLipSyncTestButtons() }
    val comboButtons = remember { TestAvatarActions.getComboButtons() }
    var selectedPanel by remember { mutableStateOf<TestPanelType?>(null) }

    Card(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 0.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { selectedPanel = toggle(selectedPanel, TestPanelType.EXPRESSION) },
                    colors = ButtonDefaults.textButtonColors(contentColor = Primary),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    modifier = Modifier.heightIn(max = 28.dp)
                ) {
                    Text("表情", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                TextButton(
                    onClick = { selectedPanel = toggle(selectedPanel, TestPanelType.GESTURE) },
                    colors = ButtonDefaults.textButtonColors(contentColor = Primary),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    modifier = Modifier.heightIn(max = 28.dp)
                ) {
                    Text("动作", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                TextButton(
                    onClick = { selectedPanel = toggle(selectedPanel, TestPanelType.SCENARIO) },
                    colors = ButtonDefaults.textButtonColors(contentColor = Primary),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    modifier = Modifier.heightIn(max = 28.dp)
                ) {
                    Text("场景", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                TextButton(
                    onClick = { selectedPanel = toggle(selectedPanel, TestPanelType.LIPSYNC) },
                    colors = ButtonDefaults.textButtonColors(contentColor = Primary),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    modifier = Modifier.heightIn(max = 28.dp)
                ) {
                    Text("口型", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                TextButton(
                    onClick = { selectedPanel = toggle(selectedPanel, TestPanelType.COMBO) },
                    colors = ButtonDefaults.textButtonColors(contentColor = Primary),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    modifier = Modifier.heightIn(max = 28.dp)
                ) {
                    Text("Combo", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            if (selectedPanel != null) {
                Spacer(Modifier.height(1.dp))
                when (selectedPanel) {
                    TestPanelType.EXPRESSION -> {
                        TestButtonGrid(
                            buttons = expressionButtons.map { it.label to it.toPlayAction() },
                            onActionClick = onActionClick
                        )
                    }
                    TestPanelType.GESTURE -> {
                        TestButtonGrid(
                            buttons = gestureButtons.map { it.label to it.toPlayAction() },
                            onActionClick = onActionClick
                        )
                    }
                    TestPanelType.SCENARIO -> {
                        ScenarioScrollList(
                            scenarios = scenarioButtons,
                            onActionClick = onActionClick
                        )
                    }
                    TestPanelType.LIPSYNC -> {
                        LipSyncTestScrollList(
                            tests = lipSyncButtons,
                            onActionClick = onActionClick
                        )
                    }
                    TestPanelType.COMBO -> {
                        ComboScrollList(
                            combos = comboButtons,
                            onActionClick = onActionClick
                        )
                    }
                    null -> {}
                }
            }
        }
    }
}

/**
 * 场景紧凑滚动列表
 */
@Composable
private fun ScenarioScrollList(
    scenarios: List<TestAvatarActions.ScenarioButton>,
    onActionClick: (AvatarPlayAction) -> Unit
) {
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.height(120.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        items(scenarios, key = { it.id }) { scenario ->
            ScenarioCompactItem(
                scenario = scenario,
                onClick = { onActionClick(scenario.action) }
            )
        }
    }
}

/**
 * 紧凑场景卡片项
 */
@Composable
private fun ScenarioCompactItem(
    scenario: TestAvatarActions.ScenarioButton,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(18.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color.White.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 类别标签
        Text(
            text = scenario.category,
            fontSize = 12.sp,
            color = Primary,
            modifier = Modifier
                .background(Primary.copy(alpha = 0.1f), RoundedCornerShape(2.dp))
                .padding(horizontal = 2.dp, vertical = 0.5.dp)
        )
        Spacer(Modifier.width(3.dp))
        // 场景名称
        Text(
            text = scenario.label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        // 表情和动作标签
        Text(
            text = scenario.expression.value,
            fontSize = 12.sp,
            color = TextSecondary,
            modifier = Modifier
                .background(SurfaceVariant, RoundedCornerShape(2.dp))
                .padding(horizontal = 2.dp, vertical = 0.5.dp)
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = scenario.gesture.value,
            fontSize = 12.sp,
            color = TextSecondary,
            modifier = Modifier
                .background(SurfaceVariant, RoundedCornerShape(2.dp))
                .padding(horizontal = 2.dp, vertical = 0.5.dp)
        )
    }
}

/**
 * 口型测试滚动列表
 */
@Composable
private fun LipSyncTestScrollList(
    tests: List<TestAvatarActions.LipSyncTestButton>,
    onActionClick: (AvatarPlayAction) -> Unit
) {
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.height(120.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        items(tests, key = { it.id }) { test ->
            LipSyncTestItem(
                test = test,
                onClick = { onActionClick(test.action) }
            )
        }
    }
}

/**
 * 口型测试卡片项
 */
@Composable
private fun LipSyncTestItem(
    test: TestAvatarActions.LipSyncTestButton,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(18.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color.White.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 类别标签
        Text(
            text = test.category,
            fontSize = 12.sp,
            color = Accent,
            modifier = Modifier
                .background(Accent.copy(alpha = 0.1f), RoundedCornerShape(2.dp))
                .padding(horizontal = 2.dp, vertical = 0.5.dp)
        )
        Spacer(Modifier.width(3.dp))
        // 测试名称
        Text(
            text = test.label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        // 播放图标
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = "播放",
            modifier = Modifier.size(12.dp),
            tint = Primary
        )
    }
}

/**
 * Combo 紧凑滚动列表
 */
@Composable
private fun ComboScrollList(
    combos: List<TestAvatarActions.ComboButton>,
    onActionClick: (AvatarPlayAction) -> Unit
) {
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.height(120.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        items(combos, key = { it.id }) { combo ->
            ComboCompactItem(
                combo = combo,
                onClick = { onActionClick(combo.action) }
            )
        }
    }
}

/**
 * 紧凑 Combo 卡片项
 */
@Composable
private fun ComboCompactItem(
    combo: TestAvatarActions.ComboButton,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(18.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color.White.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 情感类别标签
        Text(
            text = combo.emotionCategory,
            fontSize = 12.sp,
            color = Accent,
            modifier = Modifier
                .background(Accent.copy(alpha = 0.1f), RoundedCornerShape(2.dp))
                .padding(horizontal = 2.dp, vertical = 0.5.dp)
        )
        Spacer(Modifier.width(3.dp))
        // Combo 名称
        Text(
            text = combo.label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        // 情感标签预览
        Text(
            text = combo.emotionTags.take(3).joinToString(" "),
            fontSize = 10.sp,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(3.dp))
        // 播放图标
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = "播放",
            modifier = Modifier.size(12.dp),
            tint = Primary
        )
    }
}

private fun toggle(current: TestPanelType?, target: TestPanelType): TestPanelType? =
    if (current == target) null else target

@Composable
private fun TestButtonGrid(
    buttons: List<Pair<String, AvatarPlayAction>>,
    onActionClick: (AvatarPlayAction) -> Unit
) {
    val chunked = buttons.chunked(4)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        chunked.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                row.forEach { (label, action) ->
                    OutlinedButton(
                        onClick = { onActionClick(action) },
                        modifier = Modifier.weight(1f).height(24.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary),
                        border = null
                    ) {
                        Text(label, fontSize = 13.sp, maxLines = 1)
                    }
                }
                // 补足空位保持对齐
                repeat(4 - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

private enum class TestPanelType {
    EXPRESSION,
    GESTURE,
    SCENARIO,
    LIPSYNC,
    COMBO
}

@Composable
private fun CancelZone(
    isCancelZone: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (isCancelZone) Error.copy(0.15f) else SurfaceVariant)
            .border(
                width = 1.dp,
                color = if (isCancelZone) Error else Color.Transparent,
                shape = RoundedCornerShape(24.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Cancel,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (isCancelZone) Error else TextHint
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = if (isCancelZone) "松开取消" else "上滑取消",
                fontSize = 14.sp,
                color = if (isCancelZone) Error else TextHint
            )
        }
    }
}

@Composable
private fun MessageList(
    messages: List<ChatMessage>,
    isLoading: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier,
    bottomPaddingDp: androidx.compose.ui.unit.Dp = 8.dp,
    onUserInteraction: () -> Unit = {},
    onFeedbackClick: (String) -> Unit = {},
    onRouteCardClick: (RouteData) -> Unit = {},
    currentAssistantMessageId: String? = null,
    typewriterFinishedIds: Set<String> = emptySet(),
    resolveMapCover: (RouteData) -> MapCover? = { null }
) {
    val lastAssistantMessageId by remember(messages) {
        derivedStateOf { messages.findLast { !it.isUser }?.id }
    }
    val userScrollConnection = remember(onUserInteraction) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) {
                    onUserInteraction()
                }
                return Offset.Zero
            }
        }
    }
    LazyColumn(
        modifier = modifier
            .nestedScroll(userScrollConnection)
            .padding(horizontal = 4.dp),
        state = listState,
        contentPadding = PaddingValues(top = 8.dp, bottom = bottomPaddingDp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            messages,
            key = { it.id },
            contentType = { if (it.isUser) "user" else "assistant" }
        ) { message ->
            val isLastAssistant = message.id == lastAssistantMessageId
            MessageBubble(
                message = message,
                isLastAssistant = isLastAssistant,
                onFeedbackClick = onFeedbackClick,
                onRouteCardClick = onRouteCardClick,
                currentAssistantMessageId = currentAssistantMessageId,
                typewriterFinishedIds = typewriterFinishedIds,
                resolveMapCover = resolveMapCover
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: ChatMessage,
    isLastAssistant: Boolean = false,
    onFeedbackClick: (String) -> Unit = {},
    onRouteCardClick: (RouteData) -> Unit = {},
    currentAssistantMessageId: String? = null,
    typewriterFinishedIds: Set<String> = emptySet(),
    resolveMapCover: (RouteData) -> MapCover? = { null }
) {
    val isUser = message.isUser
    val imageUri = message.pendingImageUri ?: message.imageUrl
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val vibrator = context.getSystemService<Vibrator>()
    var previewImage by remember(message.id) { mutableStateOf<ChatImageInfo?>(null) }
    val responseImages = remember(message.images, message.routeData?.coverImage) {
        message.routeData?.toCoverChatImage()?.let { coverImage ->
            if (message.images.any { it.imageModel() == coverImage.imageModel() }) {
                message.images
            } else {
                message.images + coverImage
            }
        } ?: message.images
    }
    val contentToShow by remember(message.content) {
        derivedStateOf { message.content.trimEnd() }
    }
    val shouldShowThinkingAnimation = !isUser && message.isLoading
    val hasContent = contentToShow.isNotBlank()
    val canShowFeedback = isLastAssistant && !isUser && !message.isLoading && !message.isError && hasContent
    val textFinished = message.id in typewriterFinishedIds
    val visibleResponseImages = if (textFinished) responseImages else emptyList()
    // 图片是否全部加载渲染成功（供地图卡片弹出门控；无图片时立即就绪）
    var imagesReady by remember(message.id) { mutableStateOf(visibleResponseImages.isEmpty()) }
    LaunchedEffect(visibleResponseImages) {
        imagesReady = visibleResponseImages.isEmpty()
    }

    if (isUser) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.Bottom
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onLongClick = {
                            if (hasContent) {
                                clipboardManager.setText(AnnotatedString(contentToShow))
                                vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                                Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onClick = {}
                    ),
                shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
                color = UserBubbleBg
            ) {
                Column {
                    if (imageUri != null) {
                        AsyncImage(
                            model = imageUri,
                            contentDescription = "图片",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp)),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    }
                    if (hasContent) {
                        val safeContent = remember(contentToShow) {
                            sanitizeRenderableText(contentToShow)
                        }
                        Text(
                            text = safeContent,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = UserBubbleText
                        )
                    }
                }
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onLongClick = {
                    if (hasContent) {
                        clipboardManager.setText(AnnotatedString(contentToShow))
                        vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                        Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                    }
                },
                onClick = {}
            )
            .padding(horizontal = 20.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            if (imageUri != null) {
                AsyncImage(
                    model = imageUri,
                    contentDescription = "图片",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (hasContent) {
                val safeContent = remember(contentToShow) {
                    normalizeRenderableMarkdown(sanitizeRenderableText(contentToShow))
                }
                val textColor = when {
                    message.isError -> ErrorBubbleBorder
                    isUser -> UserBubbleBg
                    else -> AssistantBubbleText
                }
                if (!message.isError) {
                    MarkdownWithTable(
                        content = safeContent,
                        textColor = textColor,
                        linkColor = Color(0xFF2B59C3),
                        codeBackgroundColor = textColor.copy(alpha = 0.08f),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        text = safeContent,
                        modifier = Modifier.fillMaxWidth(),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = textColor,
                        textAlign = if (isUser) TextAlign.End else TextAlign.Start
                    )
                }
            }

            if (visibleResponseImages.isNotEmpty()) {
                if (hasContent) {
                    Spacer(modifier = Modifier.height(10.dp))
                }
                AssistantImageGallery(
                    images = visibleResponseImages,
                    onImageClick = { previewImage = it },
                    onReadyChange = { imagesReady = it }
                )
            }

            if (shouldShowThinkingAnimation) {
                Spacer(modifier = Modifier.height(10.dp))
                ThinkingDotsAnimation()
            }

            // 地图入口卡片：必须等文本、图片全部加载渲染成功（对话框全部结束）后才在结尾弹出。
            // 用 typewriterFinishedIds（打字机 onFinished / 历史恢复时加入，稳定）作为
            // "文本已输入完"信号——不依赖 currentAssistantMessageId（会被数字人 IDLE 提前置 null，导致提前弹出+抖动）。
            val responseSettled = !message.isLoading && !message.isError
            if (message.routeData != null && responseSettled && textFinished && imagesReady) {
                if (hasContent || visibleResponseImages.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                }
                MapEntryCard(
                    routeData = message.routeData,
                    onClick = { onRouteCardClick(message.routeData) },
                    resolveMapCover = resolveMapCover
                )
            }
        }

        if (canShowFeedback) {
            Spacer(modifier = Modifier.height(6.dp))
            com.example.scenic_avatar_guide_app.ui.components.FeedbackButton(
                hasFeedback = message.hasFeedback,
                onClick = { onFeedbackClick(message.id) },
                modifier = Modifier.align(Alignment.Start)
            )
        }
    }

    previewImage?.let { image ->
        ImagePreviewDialog(
            image = image,
            onDismiss = { previewImage = null }
        )
    }
}

@Composable
private fun MapEntryCard(
    routeData: RouteData,
    onClick: () -> Unit,
    resolveMapCover: (RouteData) -> MapCover?,
    modifier: Modifier = Modifier
) {
    val highlights = remember(routeData.highlights) {
        routeData.highlights?.take(3) ?: emptyList()
    }
    val cover = remember(routeData) { resolveMapCover(routeData) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = Surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp
    ) {
        Column {
            ScenicMapCover(
                cover = cover,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            )

            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = routeData.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val durationText = buildString {
                            append("约 ${routeData.totalDurationMin} 分钟")
                            routeData.totalDistanceM?.let {
                                append(" · 约 ${it / 1000} 公里")
                            }
                        }
                        Text(
                            text = durationText,
                            fontSize = 12.sp,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = TextHint
                    )
                }

                if (highlights.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        highlights.forEach { highlight ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Primary.copy(alpha = 0.08f)
                            ) {
                                Text(
                                    text = highlight,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontSize = 11.sp,
                                    color = Primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScenicMapCover(
    cover: MapCover?,
    modifier: Modifier = Modifier
) {
    // 未配置高德 Web Key 或无坐标：直接占位，避免空请求
    val webKey = com.example.scenic_avatar_guide_app.BuildConfig.AMAP_WEB_KEY
    if (cover == null || webKey.isBlank()) {
        MapCoverPlaceholder(modifier = modifier)
        return
    }

    // 高德静态地图 REST API：纯图片，无 MapView/GL 线程，不会触发 native 销毁崩溃。
    // 注意 location 参数为「经度,纬度」顺序；zoom 取景区配置；size 取接近卡片封面比例。
    // 如果本地有景点坐标，把所有景点以默认 marker 标注在封面图上。
    val url = remember(cover) {
        val markers = buildString {
            val list = cover.spotMarkers
                .filter { it.lat != 0.0 && it.lng != 0.0 }
                .take(10)
            if (list.isNotEmpty()) {
                append("&markers=")
                append("mid,0x1D7A6D,:")
                append(
                    list.joinToString(";") { spot ->
                        "${spot.lng},${spot.lat}"
                    }
                )
            }
        }
        "https://restapi.amap.com/v3/staticmap" +
            "?location=${cover.lng},${cover.lat}" +
            "&zoom=${cover.zoom.toInt()}" +
            "&size=750*380" +
            "&scale=2" +
            markers +
            "&key=$webKey"
    }

    SubcomposeAsyncImage(
        model = url,
        contentDescription = "景区地图",
        modifier = modifier,
        contentScale = ContentScale.Crop,
        loading = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFEFF6F4)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = Primary
                )
            }
        },
        error = { MapCoverPlaceholder(modifier = Modifier.fillMaxSize()) }
    )
}

@Composable
private fun MapCoverPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(Color(0xFFEFF6F4)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = null,
            tint = Primary.copy(alpha = 0.4f),
            modifier = Modifier.size(36.dp)
        )
    }
}

@Composable
private fun AssistantImageGallery(
    images: List<ChatImageInfo>,
    onImageClick: (ChatImageInfo) -> Unit,
    onReadyChange: ((Boolean) -> Unit)? = null
) {
    val displayImages = remember(images) { images.take(3).filter { it.imageModel().isNotBlank() } }
    if (displayImages.isEmpty()) {
        // 无可渲染图片：立即就绪
        LaunchedEffect(Unit) { onReadyChange?.invoke(true) }
        return
    }

    // 每张图的加载结果（true=成功，false=失败）；key = imageId ?: imageModel()
    // 不按 images 列表 key：流式追加图片时列表会变，若重置 map 会丢失已加载状态，
    // 而 painter.state 不变不会重发，导致就绪态卡死为 false。map 在画廊存活期内保持稳定。
    val loadStates = remember { mutableStateMapOf<String, Boolean>() }

    // 汇总：所有展示图片均加载成功才算就绪（严格门控）
    LaunchedEffect(displayImages) {
        snapshotFlow {
            val keys = displayImages.map { it.imageId ?: it.imageModel() }
            keys to loadStates.toMap()
        }.collect { (keys, states) ->
            onReadyChange?.invoke(keys.isNotEmpty() && keys.all { states[it] == true })
        }
    }

    if (displayImages.size == 1) {
        AssistantImageCard(
            image = displayImages.first(),
            imageHeight = 190.dp,
            reserveCaptionSpace = false,
            modifier = Modifier.fillMaxWidth(),
            onClick = onImageClick,
            onState = { ok -> loadStates[displayImages.first().imageId ?: displayImages.first().imageModel()] = ok }
        )
    } else {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(end = 20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(displayImages, key = { it.imageId ?: it.imageModel() }) { image ->
                AssistantImageCard(
                    image = image,
                    imageHeight = 132.dp,
                    reserveCaptionSpace = true,
                    modifier = Modifier.width(210.dp),
                    onClick = onImageClick,
                    onState = { ok -> loadStates[image.imageId ?: image.imageModel()] = ok }
                )
            }
        }
    }
}

@Composable
private fun AssistantImageCard(
    image: ChatImageInfo,
    imageHeight: Dp,
    reserveCaptionSpace: Boolean,
    modifier: Modifier = Modifier,
    onClick: (ChatImageInfo) -> Unit,
    onState: ((loaded: Boolean) -> Unit)? = null
) {
    val title = image.title?.takeIf { it.isNotBlank() }
    val description = image.description?.takeIf { it.isNotBlank() }
        ?: image.caption?.takeIf { it.isNotBlank() }
    val altText = image.altText?.takeIf { it.isNotBlank() }
        ?: title
        ?: "图片加载失败"
    val hasCaption = title != null || description != null

    val painter = rememberAsyncImagePainter(model = image.imageModel())
    // 观察 Coil 加载状态，向上汇报成功/失败（供卡片弹出门控）
    LaunchedEffect(painter) {
        snapshotFlow { painter.state }.collect { state ->
            when (state) {
                is AsyncImagePainter.State.Success -> onState?.invoke(true)
                is AsyncImagePainter.State.Error -> onState?.invoke(false)
                else -> {}
            }
        }
    }

    Surface(
        modifier = modifier.clickable { onClick(image) },
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFFFFFFF),
        tonalElevation = 1.dp,
        shadowElevation = 1.dp
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(imageHeight)
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            ) {
                Image(
                    painter = painter,
                    contentDescription = altText,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                when (painter.state) {
                    is AsyncImagePainter.State.Loading,
                    is AsyncImagePainter.State.Empty -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFFECEFF3)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                                color = Primary
                            )
                        }
                    }
                    is AsyncImagePainter.State.Error -> ImageErrorPlaceholder(text = altText)
                    else -> {}
                }
            }
            if (hasCaption || reserveCaptionSpace) {
                val captionModifier = if (reserveCaptionSpace) {
                    Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                } else {
                    Modifier.fillMaxWidth()
                }
                Column(
                    modifier = captionModifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    title?.let {
                        Text(
                            text = it,
                            fontSize = 13.sp,
                            lineHeight = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AssistantBubbleText,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    description?.let {
                        Text(
                            text = it,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = TextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageErrorPlaceholder(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFECEFF3))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ImagePreviewDialog(
    image: ChatImageInfo,
    onDismiss: () -> Unit
) {
    val title = image.title?.takeIf { it.isNotBlank() }
    val description = image.description?.takeIf { it.isNotBlank() }
        ?: image.caption?.takeIf { it.isNotBlank() }
    val altText = image.altText?.takeIf { it.isNotBlank() }
        ?: title
        ?: "图片加载失败"

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp),
            shape = RoundedCornerShape(8.dp),
            color = Color.White
        ) {
            Column {
                Box {
                    SubcomposeAsyncImage(
                        model = image.imageModel(),
                        contentDescription = altText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 220.dp, max = 420.dp),
                        contentScale = ContentScale.Fit,
                        loading = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFFECEFF3)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = Primary)
                            }
                        },
                        error = {
                            ImageErrorPlaceholder(text = altText)
                        }
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                            .size(36.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "关闭", tint = Color.White)
                    }
                }
                if (title != null || description != null) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        title?.let {
                            Text(
                                text = it,
                                fontSize = 16.sp,
                                lineHeight = 22.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AssistantBubbleText
                            )
                        }
                        description?.let {
                            Text(
                                text = it,
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun ChatImageInfo.imageModel(): String {
    return url?.takeIf { it.isNotBlank() }
        ?: publicPath?.takeIf { it.isNotBlank() }
        ?: ""
}

private fun RouteData.toCoverChatImage(): ChatImageInfo? {
    val cover = coverImage ?: return null
    val url = cover.url?.takeIf { it.isNotBlank() } ?: return null
    return ChatImageInfo(
        imageId = "route-cover-${routeId ?: title}",
        title = title,
        description = cover.altText,
        altText = cover.altText ?: "$title 路线封面",
        url = url
    )
}

/**
 * 思考中点阵动画
 */
@Composable
private fun ThinkingDotsAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "thinking")
    val dotCount = 3
    val dotColor = AssistantBubbleText

    Row(
        modifier = Modifier.padding(start = 8.dp, top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(dotCount) { index ->
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 400, delayMillis = index * 150),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot_$index"
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(dotColor.copy(alpha = alpha), CircleShape)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InputSection(
    mode: InteractionMode, inputText: String, isLoading: Boolean,
    isConversationActive: Boolean,
    pendingImageUri: String?,
    onInputChange: (String) -> Unit, onSend: () -> Unit, onAbort: () -> Unit,
    onModeChange: (InteractionMode) -> Unit,
    onVoiceClick: () -> Unit, onCameraInput: () -> Unit,
    onClearImage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        var modeMenuExpanded by remember { mutableStateOf(false) }
        if (pendingImageUri != null) {
            Box(
                modifier = Modifier
                    .padding(bottom = 6.dp)
                    .height(80.dp)
                    .widthIn(max = 120.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                AsyncImage(
                    model = pendingImageUri,
                    contentDescription = "待发送图片",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
                IconButton(
                    onClick = onClearImage,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(24.dp)
                        .padding(2.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "删除图片",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        Box {
            Box(Modifier.clip(RoundedCornerShape(24.dp)).background(InputBarBg)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    InputModeButton(
                        currentMode = mode,
                        onClick = { modeMenuExpanded = true }
                    )
                    if (mode == InteractionMode.Chat) IconButton(onClick = onCameraInput, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.CameraAlt, "拍照识景", Modifier.size(22.dp), Primary) }
                    val canSend = inputText.isNotBlank() || pendingImageUri != null
                    BasicTextField(
                        value = inputText,
                        onValueChange = { if (it.length <= 300) onInputChange(it) },
                        modifier = Modifier.weight(1f),
                        maxLines = 3,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = { if (canSend && !isLoading) onSend() }
                        ),
                        decorationBox = { innerTextField ->
                            OutlinedTextFieldDefaults.DecorationBox(
                                value = inputText,
                                innerTextField = innerTextField,
                                enabled = true,
                                singleLine = false,
                                visualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                placeholder = { Text(when (mode) { InteractionMode.Chat -> "输入消息或拍照..."; InteractionMode.Route -> "输入路线偏好..." }, fontSize = 14.sp, color = TextHint) },
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent),
                                contentPadding = PaddingValues(start = 12.dp, end = 48.dp, top = 8.dp, bottom = 8.dp)
                            )
                        }
                    )
                    when {
                        isConversationActive -> {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF5A6772))
                                    .clickable(onClick = onAbort),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Stop,
                                    contentDescription = "停止回复",
                                    modifier = Modifier.size(16.dp),
                                    tint = Color.White
                                )
                            }
                        }
                        canSend -> {
                            FilledIconButton(
                                onClick = onSend,
                                enabled = !isLoading,
                                modifier = Modifier.size(40.dp),
                                shape = CircleShape
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, "发送", Modifier.size(20.dp), Color.White)
                            }
                        }
                        else -> {
                            IconButton(onClick = onVoiceClick, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Default.Mic, "语音输入", Modifier.size(22.dp), TextSecondary)
                            }
                        }
                    }
                }
            }

            if (modeMenuExpanded) {
                InputModePopup(
                    currentMode = mode,
                    onDismiss = { modeMenuExpanded = false },
                    onModeChange = { selectedMode ->
                        modeMenuExpanded = false
                        onModeChange(selectedMode)
                    }
                )
            }
        }
    }
}

@Composable
private fun InputModeButton(
    currentMode: InteractionMode,
    onClick: () -> Unit
) {
    val label = when (currentMode) {
        InteractionMode.Chat -> "聊天"
        InteractionMode.Route -> "规划"
    }

    Surface(
        modifier = Modifier
            .height(36.dp)
            .widthIn(min = 56.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        border = BorderStroke(1.dp, SurfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(start = 10.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Primary,
                maxLines = 1,
                softWrap = false
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = Primary
            )
        }
    }
}

@Composable
private fun InputModePopup(
    currentMode: InteractionMode,
    onDismiss: () -> Unit,
    onModeChange: (InteractionMode) -> Unit
) {
    val positionProvider = remember {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                val popupX = anchorBounds.left.coerceIn(
                    minimumValue = 0,
                    maximumValue = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
                )
                val popupY = (anchorBounds.top - popupContentSize.height).coerceAtLeast(0)
                return IntOffset(popupX, popupY)
            }
        }
    }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Surface(
            modifier = Modifier.width(176.dp),
            shape = RoundedCornerShape(12.dp),
            color = Color.White,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, SurfaceVariant.copy(alpha = 0.7f))
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                InputModeMenuItem(
                    selected = currentMode == InteractionMode.Chat,
                    label = "聊天问答",
                    icon = Icons.AutoMirrored.Filled.Chat,
                    onClick = { onModeChange(InteractionMode.Chat) }
                )
                InputModeMenuItem(
                    selected = currentMode == InteractionMode.Route,
                    label = "路线规划",
                    icon = Icons.Default.Map,
                    onClick = { onModeChange(InteractionMode.Route) }
                )
            }
        }
    }
}

@Composable
private fun InputModeMenuItem(
    selected: Boolean,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clickable(onClick = onClick)
            .background(if (selected) Primary.copy(alpha = 0.08f) else Color.Transparent)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (selected) Primary else TextSecondary
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            fontSize = 14.sp,
            color = if (selected) Primary else TextPrimary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = Primary
            )
        }
    }
}

@Composable
private fun VoiceWaveformSection(
    volumeLevel: Float,
    isCancelZone: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isCancelZone) Error.copy(0.15f) else Primary.copy(0.1f)),
        contentAlignment = Alignment.Center
    ) {
        ArcWaveform(
            volumeLevel = volumeLevel,
            isRecording = true,
            modifier = Modifier.fillMaxSize(),
            color = if (isCancelZone) Error else Primary
        )
    }
}

@Composable
private fun VoiceInputButton(
    isRecording: Boolean,
    isCancelZone: Boolean,
    onCancelZoneChange: (Boolean) -> Unit,
    onVoiceStart: () -> Unit,
    onVoiceStop: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val vibrator = remember { context.getSystemService<Vibrator>() }
    val cancelThreshold = -200.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(if (isRecording) {
                if (isCancelZone) Error else Primary
            } else InputBarBg)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    vibrator?.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
                    onVoiceStart()
                    onCancelZoneChange(false)

                    val startTime = System.currentTimeMillis()
                    var currentCancelZone = false

                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val pointer = event.changes.find { it.id == down.id }
                            if (pointer == null) break
                            if (pointer.pressed) {
                                val inCancelZone = pointer.position.y < cancelThreshold.toPx()
                                if (inCancelZone != currentCancelZone) {
                                    currentCancelZone = inCancelZone
                                    onCancelZoneChange(inCancelZone)
                                }
                                pointer.consume()
                            } else break
                        }
                    } catch (e: Exception) {}

                    val duration = System.currentTimeMillis() - startTime
                    if (currentCancelZone) onCancel()
                    else if (duration < 500) onCancel()
                    else onVoiceStop()
                    onCancelZoneChange(false)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (isRecording) Icons.Default.Mic else Icons.Default.MicNone,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (isRecording) Color.White else TextSecondary
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = when {
                    isCancelZone -> "松开取消"
                    isRecording -> "松开发送"
                    else -> "按住说话"
                },
                fontSize = 15.sp,
                color = if (isRecording) Color.White else TextSecondary,
                fontWeight = if (isRecording) FontWeight.Medium else FontWeight.Normal
            )
        }
    }
}

enum class InteractionMode { Chat, Route }

@Composable
private fun ImageSourceDialog(
    onDismiss: () -> Unit,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择图片来源", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    onClick = onCameraClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, null, tint = Primary, modifier = Modifier.size(28.dp))
                        Text("拍照上传", fontSize = 15.sp, color = TextPrimary)
                    }
                }
                Surface(
                    onClick = onGalleryClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, null, tint = Primary, modifier = Modifier.size(28.dp))
                        Text("从相册选择", fontSize = 15.sp, color = TextPrimary)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

private fun createImageUri(context: android.content.Context): Uri? {
    return try {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFile = File.createTempFile("JPEG_${timeStamp}_", ".jpg", context.getExternalFilesDir(Environment.DIRECTORY_PICTURES))
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", imageFile)
    } catch (e: Exception) {
        null
    }
}

@Composable
fun ScenicSelectionDialog(
    scenicAreas: List<com.example.scenic_avatar_guide_app.domain.model.ScenicArea>,
    onSelected: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedScenicId by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (selectedScenicId == null) "选择景区" else "选择景点",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            if (selectedScenicId == null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    scenicAreas.forEach { area ->
                        Surface(
                            onClick = { selectedScenicId = area.id },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = SurfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Default.Landscape, null, tint = Primary, modifier = Modifier.size(28.dp))
                                Text(area.name, fontSize = 15.sp, color = TextPrimary)
                            }
                        }
                    }
                }
            } else {
                val area = scenicAreas.find { it.id == selectedScenicId }
                Column {
                    if (area != null) {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 320.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(area.spots) { spot ->
                                Surface(
                                    onClick = { onSelected(area.id, spot.id) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    color = SurfaceVariant
                                ) {
                                    Text(
                                        spot.name,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        fontSize = 14.sp,
                                        color = TextPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { if (selectedScenicId == null) onDismiss() else selectedScenicId = null }) {
                Text(if (selectedScenicId == null) "取消" else "返回")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun VoiceSelectionDialog(
    voices: List<com.example.scenic_avatar_guide_app.core.tts.VoiceInfo>,
    currentVoiceId: String,
    onVoiceSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = Primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("选择音色", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                voices.forEach { voice ->
                    val isSelected = voice.id == currentVoiceId
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onVoiceSelected(voice.id) },
                        color = if (isSelected) Primary.copy(alpha = 0.1f) else Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Face,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = if (isSelected) Primary else TextSecondary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = voice.displayName,
                                    fontSize = 15.sp,
                                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                    color = if (isSelected) Primary else TextPrimary
                                )
                                Text(
                                    text = voice.description,
                                    fontSize = 12.sp,
                                    color = TextHint
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "已选择",
                                    tint = Primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    if (voice != voices.last()) {
                        HorizontalDivider(
                            color = SurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        shape = RoundedCornerShape(20.dp)
    )
}


private fun sanitizeRenderableText(text: String): String {
    val output = StringBuilder(text.length)
    var index = 0
    while (index < text.length) {
        val char = text[index]
        when {
            char == '\n' || char == '\t' -> output.append(char)
            char.isISOControl() -> {}
            char.isHighSurrogate() -> {
                if (index + 1 < text.length && text[index + 1].isLowSurrogate()) {
                    output.append(char)
                    output.append(text[index + 1])
                    index++
                } else {
                    output.append('\uFFFD')
                }
            }
            char.isLowSurrogate() -> output.append('\uFFFD')
            else -> output.append(char)
        }
        index++
    }
    return output.toString()
}

private fun normalizeRenderableMarkdown(text: String): String {
    return Regex("(?m)^(#{1,3})([^#\\s])").replace(text) { match ->
        "${match.groupValues[1]} ${match.groupValues[2]}"
    }
}
