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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.getSystemService
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
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
import com.example.scenic_avatar_guide_app.domain.model.LatLngPoint
import com.example.scenic_avatar_guide_app.domain.model.AvatarState
import com.example.scenic_avatar_guide_app.core.speech.SpeechRecognizerHelper
import com.example.scenic_avatar_guide_app.core.avatar.TestAvatarActions
import com.example.scenic_avatar_guide_app.core.avatar.AvatarPlayAction
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val FallbackBottomReserve = 152.dp
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

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val density = LocalDensity.current
    var isCancelZone by remember { mutableStateOf(false) }
    var showImagePickerDialog by remember { mutableStateOf(false) }
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    var showVoiceDialog by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var bottomControlsContentHeightPx by remember { mutableIntStateOf(0) }
    val navigationBottomPx = WindowInsets.navigationBars.getBottom(density)
    val fixedBottomBarHeight = if (bottomControlsContentHeightPx > 0) {
        with(density) { (bottomControlsContentHeightPx + navigationBottomPx).toDp() } + MessageToFunctionCardGap
    } else {
        FallbackBottomReserve
    }

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
                    TopBar(
                        onMenuClick = { coroutineScope.launch { drawerState.open() } },
                        showTestPanel = showTestPanel,
                        onToggleTestPanel = { viewModel.toggleTestPanel() },
                        onVoiceClick = { showVoiceDialog = true },
                        onScenicClick = { viewModel.showScenicSelectionDialog() },
                        onLogoutClick = { showLogoutConfirm = true },
                        isAuthenticated = isAuthenticated,
                        currentMode = currentMode,
                        onRouteToggle = {
                            viewModel.switchMode(
                                if (currentMode == InteractionMode.Route) InteractionMode.Chat
                                else InteractionMode.Route
                            )
                        }
                    )

                    // 数字人区域：在主内容区域内按比例分配
                    AvatarSection(
                        avatarState = avatarState,
                        fullState = avatarFullState,
                        mouthState = viewModel.mouthState,
                        showUpperBodyOnly = true,
                        onRendererReady = { renderer ->
                            // 设置渲染器状态重置回调，解决 StateFlow 合并跳过 IDLE 问题
                            viewModel.setResetSpeakingStateCallback {
                                renderer.resetSpeakingState()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().weight(2f)
                    )

                    // 消息列表：只为底栏常态高度留白；输入法弹出不额外压缩消息区域
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(3f)
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
                            typewriterFinishedIds = typewriterFinishedIds
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

                            // 功能卡片：模式选择器（位于输入框上方，随输入法同步移动）
                            ModeSelector(
                                currentMode = currentMode,
                                onModeChange = { viewModel.switchMode(it) },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                            )

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
}

@Composable
private fun TopBar(
    onMenuClick: () -> Unit,
    showTestPanel: Boolean,
    onToggleTestPanel: () -> Unit,
    onVoiceClick: () -> Unit,
    onScenicClick: () -> Unit,
    onLogoutClick: () -> Unit,
    isAuthenticated: Boolean,
    currentMode: InteractionMode = InteractionMode.Chat,
    onRouteToggle: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(Color.White)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(
            onClick = onMenuClick,
            modifier = Modifier.size(40.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.padding(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(15.dp)
                        .height(1.5.dp)
                        .clip(RoundedCornerShape(0.75.dp))
                        .background(TextSecondary)
                )
                Box(
                    modifier = Modifier
                        .width(15.dp)
                        .height(1.5.dp)
                        .clip(RoundedCornerShape(0.75.dp))
                        .background(TextSecondary)
                )
                Box(
                    modifier = Modifier
                        .width(8.dp)
                        .height(1.5.dp)
                        .clip(RoundedCornerShape(0.75.dp))
                        .background(TextSecondary)
                )
            }
        }

        Text(
            text = "景灵智导",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = TextSecondary,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )

        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(40.dp)
            ) {
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
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = Color(0xFFE8F5F0)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF8BC4BC), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(32.dp),
                shape = RoundedCornerShape(9.dp),
                color = Primary
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
                color = Primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start
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
    Stories("历史故事", R.drawable.ic_gushi, "历史故事内容待接入"),
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
        containerColor = Color(0xFFFFF8F5),
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFB91C1C),
                            Color(0xFFE5483B),
                            Color(0xFFFFF8F5)
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
                color = Color(0xFFFFFBFA)
            ) {
                val selectedScenicName = indexItems.find { it.scenicId == selectedScenicId }?.name ?: "当前景区"
                when (tab) {
                    ScenicIntroTab.Intro -> {
                        ScenicIntroScreen(
                            modifier = Modifier.fillMaxSize(),
                            viewModel = scenicIntroViewModel,
                            showScenicSelector = false
                        )
                    }
                    ScenicIntroTab.Stories -> {
                        ScenicStoryScreen(
                            scenicId = selectedScenicId,
                            scenicName = selectedScenicName
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
private fun ScenicIntroPlaceholder(tab: ScenicIntroTab) {
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
            color = Color(0xFFFFEFEC)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(id = tab.iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = Color(0xFFC72C2C)
                )
            }
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = tab.title,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF5C1515)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = tab.placeholder,
            fontSize = 14.sp,
            color = Color(0xFF9B5A55),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ScenicStoryScreen(
    scenicId: String,
    scenicName: String
) {
    val content = remember(scenicId) { scenicStoryContent(scenicId) }
    if (content == null) {
        ScenicPortalEmptyState(
            title = scenicName,
            message = "历史故事内容正在整理中"
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
            ScenicStoryFactRow(facts = content.facts)
        }
        items(content.storyCards) { card ->
            ScenicStoryCard(card = card)
        }
        item {
            ScenicTimelineCard(events = content.timeline)
        }
        item {
            ScenicImageStrip(
                title = "图像线索",
                images = content.gallery
            )
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
        color = Color(0xFF2B0D0D),
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
                                Color(0xFF2B0D0D).copy(alpha = 0.2f),
                                Color(0xFF2B0D0D).copy(alpha = 0.88f)
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
private fun ScenicStoryFactRow(facts: List<ScenicFact>) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(facts) { fact ->
            Surface(
                modifier = Modifier
                    .width(154.dp)
                    .height(90.dp),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFFFFF3EE),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD8CE))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = fact.value,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFB91C1C),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = fact.label,
                        modifier = Modifier.padding(top = 5.dp),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = Color(0xFF7C4B43)
                    )
                }
            }
        }
    }
}

@Composable
private fun ScenicStoryCard(card: ScenicStoryCardData) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(22.dp),
        color = Color.White,
        shadowElevation = 3.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFE2DC))
    ) {
        Column {
            card.imageUrl?.let { image ->
                AsyncImage(
                    model = image,
                    contentDescription = card.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(168.dp)
                        .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                )
            }
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = card.label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE5483B)
                )
                Text(
                    text = card.title,
                    modifier = Modifier.padding(top = 5.dp),
                    fontSize = 19.sp,
                    lineHeight = 25.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2A181A)
                )
                Text(
                    text = card.body,
                    modifier = Modifier.padding(top = 9.dp),
                    fontSize = 14.sp,
                    lineHeight = 23.sp,
                    color = Color(0xFF5F4641)
                )
            }
        }
    }
}

@Composable
private fun ScenicTimelineCard(events: List<ScenicTimelineEventData>) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFFFFFBFA),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD8CE)),
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "历史脉络",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF5C1515)
            )
            Spacer(modifier = Modifier.height(12.dp))
            events.forEachIndexed { index, event ->
                Row(verticalAlignment = Alignment.Top) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE5483B))
                        )
                        if (index < events.lastIndex) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(70.dp)
                                    .background(Color(0xFFFFC9BF))
                            )
                        }
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp, bottom = 14.dp)
                    ) {
                        Text(
                            text = event.time,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB91C1C)
                        )
                        Text(
                            text = event.title,
                            modifier = Modifier.padding(top = 3.dp),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF2A181A)
                        )
                        Text(
                            text = event.description,
                            modifier = Modifier.padding(top = 5.dp),
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = Color(0xFF6F5C56)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScenicImageStrip(
    title: String,
    images: List<ScenicGalleryImage>
) {
    Column {
        Text(
            text = title,
            modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF5C1515)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(images) { image ->
                Surface(
                    modifier = Modifier
                        .width(224.dp)
                        .height(178.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White,
                    shadowElevation = 3.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFE2DC))
                ) {
                    Column {
                        AsyncImage(
                            model = image.imageUrl,
                            contentDescription = image.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(118.dp)
                                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                        )
                        Text(
                            text = image.title,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF4C2A27),
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
        color = Color(0xFFFFF3EE),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD8CE))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFFB91C1C),
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    color = Color(0xFF9B5A55)
                )
                Text(
                    text = value,
                    modifier = Modifier.padding(top = 3.dp),
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF5C1515),
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
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFE2DC))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFFFEFEC)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color(0xFFB91C1C),
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
                    color = Color(0xFFE5483B)
                )
                Text(
                    text = title,
                    modifier = Modifier.padding(top = 4.dp),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2A181A)
                )
                Text(
                    text = body,
                    modifier = Modifier.padding(top = 7.dp),
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    color = Color(0xFF5F4641)
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
        color = Color(0xFFB91C1C),
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
            color = Color(0xFFFFEFEC)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(38.dp),
                    tint = Color(0xFFC72C2C)
                )
            }
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = title,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF5C1515),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            fontSize = 14.sp,
            color = Color(0xFF9B5A55),
            textAlign = TextAlign.Center
        )
    }
}

private data class ScenicStoryContent(
    val title: String,
    val subtitle: String,
    val heroImage: String,
    val badges: List<String>,
    val facts: List<ScenicFact>,
    val storyCards: List<ScenicStoryCardData>,
    val timeline: List<ScenicTimelineEventData>,
    val gallery: List<ScenicGalleryImage>
)

private data class ScenicFact(
    val value: String,
    val label: String
)

private data class ScenicStoryCardData(
    val label: String,
    val title: String,
    val body: String,
    val imageUrl: String? = null
)

private data class ScenicTimelineEventData(
    val time: String,
    val title: String,
    val description: String
)

private data class ScenicGalleryImage(
    val imageUrl: String,
    val title: String
)

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

private fun scenicStoryContent(scenicId: String): ScenicStoryContent? {
    val xinhaiBase = "file:///android_asset/scenic_intro/images/xinhai/immersive/"
    val baqiBase = "file:///android_asset/scenic_intro/images/baqi/"
    return when (scenicId) {
        "1911museum" -> ScenicStoryContent(
            title = "辛亥革命真实故事",
            subtitle = "从宝善里意外爆炸到武昌城头枪响，读几个改变起义进程的现场故事。",
            heroImage = xinhaiBase + "wuchang_uprising_scene.png",
            badges = listOf("真实事件", "武昌首义"),
            facts = listOf(
                ScenicFact("1911", "辛亥年革命爆发"),
                ScenicFact("10月9日", "宝善里机关意外暴露"),
                ScenicFact("10月10日", "工程营发难、武昌城响应")
            ),
            storyCards = listOf(
                ScenicStoryCardData(
                    label = "起义前夜",
                    title = "宝善里爆炸：计划被迫提前",
                    body = "1911年10月9日，汉口俄租界宝善里机关里，孙武等革命党人正在为起义准备炸药、文告和旗帜。配制炸药时意外爆炸，孙武面部和手部受伤，被同伴紧急送往医院。爆炸声惊动俄租界巡捕，巡捕进入机关后搜出革命旗帜、起义文告、袖章、印信和革命党人名册。\n\n这些材料很快落到清方手中，原本隐蔽在新军中的革命网络突然暴露。湖广总督瑞澂下令戒严搜捕，武汉三镇气氛骤然紧张。起义本来还在筹划和等待时机，但名册已经暴露，许多革命党人随时可能被捕。正是这场意外，把“准备起义”推成了“必须立刻行动”。",
                    imageUrl = xinhaiBase + "revolution_origin.png"
                ),
                ScenicStoryCardData(
                    label = "黎明之前",
                    title = "彭刘杨三烈士：牺牲在起义爆发前",
                    body = "宝善里机关暴露后，清方按名册和线索搜捕革命党人，彭楚藩、刘复基、杨洪胜相继被捕。三人都是武昌起义筹备中的重要人物：有人负责军务联络，有人参与政治和组织筹备，有人在新军中推动革命力量。他们被捕时，起义尚未真正发动，武昌城仍笼罩在搜捕和恐惧之中。\n\n10月9日晚，三人在刑讯中没有屈服。据报道，他们痛斥时政、慷慨不屈。10月10日凌晨，三人在湖广总督署东辕门外遇害。白天，武昌城看似仍被清方控制；到了夜里，枪声从新军工程营响起。彭刘杨三烈士没有看到起义爆发，却成为首义前夜最沉重的一笔。",
                    imageUrl = xinhaiBase + "memorial_wall.png"
                ),
                ScenicStoryCardData(
                    label = "第一枪后",
                    title = "工程营奔向楚望台军械库",
                    body = "10月10日晚，武昌城南的新军第八镇工程营里，紧张已经压到极点。名册暴露后，革命党人知道再等下去只会被逐个搜捕。熊秉坤等人在营中集合队伍，枪声响起后，工程营士兵冲出营房，目标直指楚望台军械库。这里储有大量枪械弹药，谁先控制军械库，谁就能把零散的起义变成真正的武装行动。\n\n工程营占领楚望台后，起义军获得武器，形势迅速变化。城内外新军听到枪声后相继响应，原本分散在各营的革命力量开始汇合。起义军推举吴兆麟为临时总指挥，战斗向湖广总督署、湖北藩署等清方要害推进。武昌起义不是一声枪响就自然成功，而是在夺取武器、组织响应、攻打要害的一连串行动中完成了突破。",
                    imageUrl = xinhaiBase + "uprising_sculpture.png"
                ),
                ScenicStoryCardData(
                    label = "城门打开",
                    title = "中和门成为“首义胜利的开端”",
                    body = "工程八营发难后，起义军并不只是在城内作战，还必须让城外力量进入武昌。按计划，他们占领中和门，打开城门，迎接驻城外的南湖炮队、马队入城。这个动作极为关键：如果城门不能打开，城外队伍无法及时支援，城内起义军就可能陷入孤立。\n\n炮队入城后，在蛇山等制高点布炮，支援攻打湖广总督署。炮火和各营响应让清方防线迅速动摇，次日凌晨起义军占领武昌全城。中和门后来改名为起义门，被称为“首义胜利的开端”。它的意义不只是一个城门名称，而是那一夜城内外革命力量真正连接起来的节点。",
                    imageUrl = xinhaiBase + "zhonghe_gate.png"
                )
            ),
            timeline = listOf(
                ScenicTimelineEventData("1911.10.09", "宝善里机关暴露", "爆炸牵出名册、文告和旗帜，武汉三镇搜捕骤紧。"),
                ScenicTimelineEventData("1911.10.10 凌晨", "彭刘杨就义", "三位起义骨干在湖广总督署东辕门外遇害。"),
                ScenicTimelineEventData("1911.10.10 晚", "工程营发难", "熊秉坤等率队奔占楚望台军械库，武昌起义爆发。"),
                ScenicTimelineEventData("1911.10.11 凌晨", "武昌光复", "起义军攻克湖广总督署和湖北藩署，武昌城局势改变。")
            ),
            gallery = listOf(
                ScenicGalleryImage(xinhaiBase + "sun_yatsen_portrait.png", "孙中山与革命理想"),
                ScenicGalleryImage(xinhaiBase + "revolution_origin.png", "革命源起"),
                ScenicGalleryImage(xinhaiBase + "found_republic.png", "创建中华民国"),
                ScenicGalleryImage(xinhaiBase + "centenary.png", "辛亥百年纪念")
            )
        )
        "site_of_the_august_7th_conference" -> ScenicStoryContent(
            title = "八七会议真实故事",
            subtitle = "不是泛讲会议意义，而是回到鄱阳街二楼那一天，看会议如何在秘密、炎热和白色恐怖中完成。",
            heroImage = baqiBase + "meeting_painting.jpg",
            badges = listOf("真实事件", "1927"),
            facts = listOf(
                ScenicFact("1927.8.7", "八七会议在汉口召开"),
                ScenicFact("21人", "出席代表人数"),
                ScenicFact("56次", "一天会议中的发言记录")
            ),
            storyCards = listOf(
                ScenicStoryCardData(
                    label = "秘密会场",
                    title = "二楼小房间里，门窗紧闭开了一整天",
                    body = "1927年8月7日，参加中央紧急会议的代表们分批来到汉口鄱阳街一栋三层西式建筑。会议地点设在二楼一个二十多平方米的小房间里，桌椅并不宽裕，却要容纳来自不同岗位的代表讨论党和革命的出路。此时大革命失败不久，白色恐怖笼罩武汉，公开身份的共产党员和革命群众随时可能遭到搜捕。\n\n为了安全，会场门窗紧闭。那是武汉盛夏，室内闷热，但会议不能随意开窗，也不能频繁出入。中午，代表们只吃干粮、喝白开水，然后继续开会。这个故事真正打动人的地方，不是“开了一次会”这样简单，而是在敌人眼皮底下，一群人把一天时间压缩成决定生死方向的讨论。",
                    imageUrl = baqiBase + "second_floor.jpg"
                ),
                ScenicStoryCardData(
                    label = "会务安全",
                    title = "邓小平第一个到会场，最后一个离开",
                    body = "八七会议召开时，邓小平任中共中央秘书，承担了大量不显眼却极其关键的会务工作。他第一个来到开会地点，负责接待代表、安排食宿、维持联络和安全。代表们不能集中公开抵达，有的需要由交通员带入，有的需要变换身份和路线，任何一个环节出错，都可能让会议暴露。\n\n会议本身只开了一天，但邓小平在会场前后待了六天。他最早进会场，最后离开，既要保障会议能开起来，也要让会后不留下明显痕迹。后来邓小平多次回忆八七会议，这段经历也成为他参加的第一次中央级别重要会议。历史故事里常记住发言者，但这类幕后组织和安全工作，正是秘密会议能够完成的前提。",
                    imageUrl = baqiBase + "inscription.jpg"
                ),
                ScenicStoryCardData(
                    label = "纸上证词",
                    title = "20页、12800字：会议记录保存了当天的激烈讨论",
                    body = "八七会议只有一天，但它并不是几句口号式结论。中央档案馆保存的会议记录共20页、12800字，武汉八七会议会址纪念馆也保存有复制件。根据这份记录，出席会议的代表有21人，一天之内留下了56次发言。也就是说，那个二楼小房间里曾经有过密集而尖锐的讨论。\n\n这份记录的珍贵之处，在于它把危机时刻的判断过程留了下来。人们后来谈到八七会议，常直接记住“转折”二字；但记录告诉我们，转折不是凭空发生的，而是在大革命失败后的痛苦反思中，在对错误路线、武装斗争、土地革命和组织重建的反复讨论中形成的。纸上的每一页，都是那一天紧张气氛的证词。",
                    imageUrl = baqiBase + "meeting_record.jpg"
                ),
                ScenicStoryCardData(
                    label = "关键发言",
                    title = "毛泽东在会上提出“须知政权是由枪杆子中取得的”",
                    body = "八七会议讨论军事斗争问题时，毛泽东的发言成为后来最常被提起的历史瞬间。他批评过去“不做军事运动专做民众运动”的偏向，提出以后要非常注意军事，并说“须知政权是由枪杆子中取得的”。这句话并不是孤立的豪言，而是从大革命失败、革命力量遭到屠杀的现实中得出的判断。\n\n这次会议之后，中国共产党开始更加明确地把创建人民武装、领导军事斗争摆到重要位置。毛泽东随后以中央特派员身份前往湖南，传达八七会议精神并领导秋收起义。后来，“须知政权是由枪杆子中取得的”演化为“枪杆子里面出政权”，成为理解八七会议乃至中国革命道路转变的一把钥匙。",
                    imageUrl = baqiBase + "delegates.jpg"
                )
            ),
            timeline = listOf(
                ScenicTimelineEventData("1927.07", "会议一再推迟", "因形势紧急、交通困难，原定7月下旬的紧急会议无法如期召开。"),
                ScenicTimelineEventData("1927.08.07", "代表秘密到场", "代表分批进入会场，有的乔装成农民或商人，由交通员秘密带入。"),
                ScenicTimelineEventData("1927.08.07", "一天内56次发言", "会议在二楼房间持续一整天，记录留下密集讨论。"),
                ScenicTimelineEventData("1980", "会址复原考证", "邓小平重返会址，结合回忆帮助确认会场空间。")
            ),
            gallery = listOf(
                ScenicGalleryImage(baqiBase + "second_floor.jpg", "会址二楼复原场景"),
                ScenicGalleryImage(baqiBase + "meeting_record.jpg", "会议记录手稿复制件"),
                ScenicGalleryImage(baqiBase + "memorial_2.jpg", "纪念馆展陈空间"),
                ScenicGalleryImage(baqiBase + "memorial_3.jpg", "纪念馆外观与陈列")
            )
        )
        else -> null
    }
}

private fun scenicReservationContent(scenicId: String): ScenicReservationContent? {
    val xinhaiBase = "file:///android_asset/scenic_intro/images/xinhai/immersive/"
    val baqiBase = "file:///android_asset/scenic_intro/images/baqi/"
    return when (scenicId) {
        "1911museum" -> ScenicReservationContent(
            title = "辛亥革命博物院参观预约",
            subtitle = "南北馆区错峰开放，个人免费入馆，团体需提前预约。",
            heroImage = xinhaiBase + "museum_hall_1.jpg",
            badges = listOf("免费开放", "团体预约"),
            ticket = "免费",
            bookingType = "个人免预约",
            openingCards = listOf(
                ScenicInfoBlock(
                    label = "南区",
                    title = "辛亥革命博物院南区",
                    body = "每周一闭馆；每周二至周日开放。开放时间为9:00-17:00，16:00停止入馆。法定节假日和特殊情况除外。"
                ),
                ScenicInfoBlock(
                    label = "北区",
                    title = "辛亥革命博物院北区（红楼）",
                    body = "每周二闭馆；每周三至周一开放。开放时间为9:00-17:00，16:00停止入馆。法定节假日和特殊情况除外。"
                )
            ),
            steps = listOf(
                "关注辛亥革命博物院官方微信公众号。",
                "点击底部菜单栏“参观预约”，选择“团体预约”。",
                "进入预约界面后点击“立即预约”。",
                "按提示填写预约日期等信息并上传材料，提交后按提示完成预约。"
            ),
            contact = "北区 027-88875305；南区 027-88051911",
            notice = "节假日等特殊情况以博物院最新安排为准；个人凭有效证件有序入馆。"
        )
        "site_of_the_august_7th_conference" -> ScenicReservationContent(
            title = "八七会议会址纪念馆预约",
            subtitle = "提前预约或携带本人身份证原件，按现场开放状态有序入馆。",
            heroImage = baqiBase + "memorial_1.jpg",
            badges = listOf("免费参观", "凭证入馆"),
            ticket = "免费参观",
            bookingType = "公众号/文旅码",
            openingCards = listOf(
                ScenicInfoBlock(
                    label = "开放时间",
                    title = "周一至周四、周六至周日",
                    body = "每年1月31日至12月31日，09:00-17:00开放，16:30停止入园。"
                ),
                ScenicInfoBlock(
                    label = "闭馆安排",
                    title = "周五闭馆",
                    body = "每周五不开放，法定节假日除外；具体营业状态以当天开放情况为准。"
                )
            ),
            steps = listOf(
                "通过八七会议会址纪念馆微信公众号或武汉文旅码提前预约。",
                "到馆后凭预约码入馆参观。",
                "也可携带本人身份证原件至检票口，刷身份证入馆参观。"
            ),
            contact = "027-82835088",
            notice = "开放时间可能因节假日、活动或现场管理安排调整，出行前建议确认当天开放状态。"
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
    onRendererReady: ((com.example.scenic_avatar_guide_app.core.avatar.Live2DRendererImpl) -> Unit)? = null
) {
    AvatarView(
        avatarState = avatarState,
        fullState = fullState,
        mouthState = mouthState,
        showUpperBodyOnly = showUpperBodyOnly,
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
private fun ModeSelector(currentMode: InteractionMode, onModeChange: (InteractionMode) -> Unit, modifier: Modifier = Modifier) {
    val modes = listOf(
        InteractionMode.Chat to "聊天问答" to Icons.Default.Chat,
        InteractionMode.Route to "路线规划" to Icons.Default.Map
    )

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        modes.forEach { (modeAndLabel, icon) ->
            val (mode, label) = modeAndLabel
            val isSelected = currentMode == mode

            val backgroundColor by animateColorAsState(
                targetValue = if (isSelected) Primary else Color.White,
                animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
                label = "bgColor"
            )

            val contentColor by animateColorAsState(
                targetValue = if (isSelected) Color.White else TextPrimary,
                animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
                label = "contentColor"
            )

            val iconTint by animateColorAsState(
                targetValue = if (isSelected) Color.White else Primary,
                animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
                label = "iconTint"
            )

            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clickable { onModeChange(mode) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = backgroundColor),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = if (isSelected) 4.dp else 1.dp
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = iconTint
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        color = contentColor,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
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
    typewriterFinishedIds: Set<String> = emptySet()
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
                typewriterFinishedIds = typewriterFinishedIds
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
    typewriterFinishedIds: Set<String> = emptySet()
) {
    val isUser = message.isUser
    val imageUri = message.pendingImageUri ?: message.imageUrl
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val vibrator = context.getSystemService<Vibrator>()
    var previewImage by remember(message.id) { mutableStateOf<ChatImageInfo?>(null) }

    val contentToShow by remember(message.content) {
        derivedStateOf { message.content.trimEnd() }
    }
    val shouldShowThinkingAnimation = !isUser && message.isLoading
    val hasContent = contentToShow.isNotBlank()
    val canShowFeedback = isLastAssistant && !isUser && !message.isLoading && !message.isError && hasContent

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

            if (message.images.isNotEmpty()) {
                if (hasContent) {
                    Spacer(modifier = Modifier.height(10.dp))
                }
                AssistantImageGallery(
                    images = message.images,
                    onImageClick = { previewImage = it }
                )
            }

            if (shouldShowThinkingAnimation) {
                Spacer(modifier = Modifier.height(10.dp))
                ThinkingDotsAnimation()
            }

            if (message.routeData != null && !message.isLoading) {
                val isLiveTyping = message.id == currentAssistantMessageId && message.id !in typewriterFinishedIds
                if (!isLiveTyping) {
                    if (hasContent || message.images.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    RoutePreviewCard(
                        routeData = message.routeData,
                        onClick = { onRouteCardClick(message.routeData) }
                    )
                }
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
private fun RoutePreviewCard(
    routeData: RouteData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val highlights = remember(routeData.highlights) {
        routeData.highlights?.take(3) ?: emptyList()
    }

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
            RouteMapThumbnail(
                routeData = routeData,
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
private fun RouteMapThumbnail(
    routeData: RouteData,
    modifier: Modifier = Modifier
) {
    val polyline = remember(routeData) {
        routeData.polyline?.takeIf { it.isNotEmpty() }
            ?: routeData.spots.mapNotNull { s ->
                s.lat?.let { la -> s.lng?.let { lng -> LatLngPoint(la, lng) } }
            }
    }

    if (polyline.isEmpty()) return

    val routeColor = Primary
    val backgroundColor = Color(0xFFEFF6F4)

    Canvas(modifier = modifier.background(backgroundColor)) {
        val width = size.width
        val height = size.height

        val lats = polyline.map { it.lat }
        val lngs = polyline.map { it.lng }
        val minLat = lats.minOrNull() ?: return@Canvas
        val maxLat = lats.maxOrNull() ?: return@Canvas
        val minLng = lngs.minOrNull() ?: return@Canvas
        val maxLng = lngs.maxOrNull() ?: return@Canvas

        val latRange = (maxLat - minLat).takeIf { it > 0.0 } ?: 0.001
        val lngRange = (maxLng - minLng).takeIf { it > 0.0 } ?: 0.001

        val padding = 16.dp.toPx()
        val drawWidth = width - 2 * padding
        val drawHeight = height - 2 * padding

        fun toX(lng: Double) = padding + ((lng - minLng) / lngRange * drawWidth).toFloat()
        fun toY(lat: Double) = height - (padding + ((lat - minLat) / latRange * drawHeight).toFloat())

        // 背景网格，模拟地图道路
        val gridColor = routeColor.copy(alpha = 0.08f)
        val gridSteps = 4
        for (i in 0..gridSteps) {
            val x = padding + (drawWidth / gridSteps) * i
            drawLine(gridColor, Offset(x, padding), Offset(x, height - padding), strokeWidth = 1.dp.toPx())
            val y = padding + (drawHeight / gridSteps) * i
            drawLine(gridColor, Offset(padding, y), Offset(width - padding, y), strokeWidth = 1.dp.toPx())
        }

        // 路线轨迹
        if (polyline.size >= 2) {
            val path = Path().apply {
                moveTo(toX(polyline[0].lng), toY(polyline[0].lat))
                for (i in 1 until polyline.size) {
                    lineTo(toX(polyline[i].lng), toY(polyline[i].lat))
                }
            }
            drawPath(
                path = path,
                color = routeColor.copy(alpha = 0.7f),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        // 景点标记
        routeData.spots.forEach { spot ->
            val lat = spot.lat ?: return@forEach
            val lng = spot.lng ?: return@forEach
            val cx = toX(lng)
            val cy = toY(lat)
            drawCircle(routeColor.copy(alpha = 0.15f), radius = 8.dp.toPx(), center = Offset(cx, cy))
            drawCircle(Color.White, radius = 4.dp.toPx(), center = Offset(cx, cy))
            drawCircle(routeColor, radius = 2.5.dp.toPx(), center = Offset(cx, cy))
        }
    }
}

@Composable
private fun AssistantImageGallery(
    images: List<ChatImageInfo>,
    onImageClick: (ChatImageInfo) -> Unit
) {
    val displayImages = remember(images) { images.take(3).filter { it.imageModel().isNotBlank() } }
    if (displayImages.isEmpty()) return

    if (displayImages.size == 1) {
        AssistantImageCard(
            image = displayImages.first(),
            imageHeight = 190.dp,
            reserveCaptionSpace = false,
            modifier = Modifier.fillMaxWidth(),
            onClick = onImageClick
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
                    onClick = onImageClick
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
    onClick: (ChatImageInfo) -> Unit
) {
    val title = image.title?.takeIf { it.isNotBlank() }
    val description = image.description?.takeIf { it.isNotBlank() }
        ?: image.caption?.takeIf { it.isNotBlank() }
    val altText = image.altText?.takeIf { it.isNotBlank() }
        ?: title
        ?: "图片加载失败"
    val hasCaption = title != null || description != null

    Surface(
        modifier = modifier.clickable { onClick(image) },
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFFFFFFF),
        tonalElevation = 1.dp,
        shadowElevation = 1.dp
    ) {
        Column {
            SubcomposeAsyncImage(
                model = image.imageModel(),
                contentDescription = altText,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(imageHeight)
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)),
                contentScale = ContentScale.Crop,
                loading = {
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
                },
                error = {
                    ImageErrorPlaceholder(text = altText)
                }
            )
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
    onVoiceClick: () -> Unit, onCameraInput: () -> Unit,
    onClearImage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
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
        Box(Modifier.clip(RoundedCornerShape(24.dp)).background(InputBarBg)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
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
