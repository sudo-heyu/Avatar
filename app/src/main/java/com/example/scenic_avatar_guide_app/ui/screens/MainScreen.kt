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
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
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
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.getSystemService
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import android.widget.Toast
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import com.example.scenic_avatar_guide_app.R
import com.example.scenic_avatar_guide_app.ui.theme.*
import com.example.scenic_avatar_guide_app.ui.components.ArcWaveform
import com.example.scenic_avatar_guide_app.ui.components.AvatarView
import com.example.scenic_avatar_guide_app.domain.model.ChatMessage
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

private const val LONG_TEXT_MARKDOWN_LIMIT = 1200
private const val TEXT_RENDER_CHUNK_SIZE = 700
private val FallbackBottomReserve = 116.dp
private val FallbackBottomReserveWithTestPanel = 156.dp
private val MessageToFunctionCardGap = 8.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onSettingsClick: () -> Unit = {},
    viewModel: MainViewModel = hiltViewModel()
) {
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
    val scenicAreas = viewModel.scenicAreas
    val showFeedbackDialog by viewModel.showFeedbackDialog.collectAsStateWithLifecycle()
    val isSubmittingFeedback by viewModel.isSubmittingFeedback.collectAsStateWithLifecycle()
    val feedbackResult by viewModel.feedbackResult.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val density = LocalDensity.current
    var isCancelZone by remember { mutableStateOf(false) }
    var showImagePickerDialog by remember { mutableStateOf(false) }
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    var bottomControlsContentHeightPx by remember { mutableIntStateOf(0) }
    val navigationBottomPx = WindowInsets.navigationBars.getBottom(density)
    val fixedBottomBarHeight = if (bottomControlsContentHeightPx > 0) {
        with(density) { (bottomControlsContentHeightPx + navigationBottomPx).toDp() } + MessageToFunctionCardGap
    } else if (showTestPanel) {
        FallbackBottomReserveWithTestPanel
    } else {
        FallbackBottomReserve
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
                    viewModel.startNewSession()
                }
            )
        },
        gesturesEnabled = drawerState.isOpen
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
                        onToggleTestPanel = { viewModel.toggleTestPanel() }
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
                            onFeedbackClick = { messageId -> viewModel.showFeedbackDialog(messageId) }
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
                // 顺序从上到下：测试卡片 → 功能卡片（模式选择器）→ 输入框
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
                            .onSizeChanged { size ->
                                bottomControlsContentHeightPx = size.height
                            }
                            .background(Surface)
                    ) {
                        // 测试面板：位于最上方
                        if (showTestPanel) {
                            CompactTestPanel(
                                onActionClick = { action -> viewModel.playTestAction(action) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // 功能卡片：模式选择器
                        ModeSelector(currentMode, { viewModel.switchMode(it) }, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp))

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
                                onVoiceClick = {
                                    if (hasAudioPermission) viewModel.enterVoiceInputMode()
                                    else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                },
                                onCameraInput = { showImagePickerDialog = true },
                                onClearImage = { viewModel.clearPendingImage() },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    onMenuClick: () -> Unit,
    showTestPanel: Boolean,
    onToggleTestPanel: () -> Unit
) {
    TopAppBar(
        title = { Text("景灵智导", fontWeight = FontWeight.Bold) },
        navigationIcon = {
            IconButton(onMenuClick) {
                Icon(Icons.Default.Menu, "打开侧边栏", tint = Color.White)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Primary, titleContentColor = Color.White),
        actions = {
            IconButton(onToggleTestPanel) {
                Icon(
                    imageVector = if (showTestPanel) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (showTestPanel) "隐藏测试卡片" else "显示测试卡片",
                    tint = Color.White
                )
            }
        }
    )
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
                    text = "历史会话",
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
                        modifier = Modifier.align(Alignment.Center),
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
                        Button(onClick = onNewSession) {
                            Icon(
                                painter = painterResource(id = R.drawable.create_session),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("新建对话")
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
                // 左侧：头像 + 用户名
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                        contentDescription = "头像",
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "灵山游",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionDrawerItem(
    session: com.example.scenic_avatar_guide_app.domain.model.SessionInfo,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onDelete
            )
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = session.displayTitle(),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatSessionTime(session.lastMessageAt ?: session.createdAt),
                fontSize = 12.sp,
                color = TextHint.copy(alpha = 0.85f)
            )
        }
    }
}

private fun formatSessionTime(timeStr: String?): String {
    if (timeStr.isNullOrBlank()) return ""
    return try {
        val date = java.time.OffsetDateTime.parse(timeStr).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()
        val diff = now - date
        when {
            diff < 60_000 -> "刚刚"
            diff < 3_600_000 -> "${diff / 60_000} 分钟前"
            diff < 86_400_000 -> "${diff / 3_600_000} 小时前"
            diff < 604_800_000 -> "${diff / 86_400_000} 天前"
            else -> {
                val localDate = java.time.Instant.ofEpochMilli(date).atZone(java.time.ZoneId.systemDefault())
                "${localDate.monthValue}-${localDate.dayOfMonth}"
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("formatSessionTime", "parse error: $timeStr", e)
        ""
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
    val modes = listOf(InteractionMode.Chat to "聊天问答", InteractionMode.Route to "路线规划")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        modes.forEach { (mode, label) ->
            val isSelected = currentMode == mode
            Box(Modifier.weight(1f).height(24.dp).clip(RoundedCornerShape(12.dp)).background(if (isSelected) Primary else SurfaceVariant).clickable { onModeChange(mode) }, contentAlignment = Alignment.Center) {
                Text(label, fontSize = 11.sp, color = if (isSelected) Color.White else TextPrimary, fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal)
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
    onFeedbackClick: (String) -> Unit = {}
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
            .padding(horizontal = 12.dp),
        state = listState,
        contentPadding = PaddingValues(top = 8.dp, bottom = bottomPaddingDp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                onFeedbackClick = onFeedbackClick
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: ChatMessage,
    isLastAssistant: Boolean = false,
    onFeedbackClick: (String) -> Unit = {}
) {
    val isUser = message.isUser
    val imageUri = message.pendingImageUri ?: message.imageUrl
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val vibrator = context.getSystemService<Vibrator>()

    val contentToShow by remember(message.content) {
        derivedStateOf { message.content.trimEnd() }
    }
    val shouldShowThinkingAnimation = !isUser && message.isLoading
    val hasContent = contentToShow.isNotBlank()
    val canShowFeedback = isLastAssistant && !isUser && !message.isLoading && !message.isError && hasContent

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
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
            shape = RoundedCornerShape(16.dp, 16.dp, if (isUser) 16.dp else 4.dp, if (isUser) 4.dp else 16.dp),
            color = if (isUser) UserBubbleBg else AssistantBubbleBg
        ) {
            Column {
                if (imageUri != null) {
                    AsyncImage(
                        model = imageUri,
                        contentDescription = "图片",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = if (isUser) 16.dp else 0.dp, bottomEnd = if (isUser) 0.dp else 16.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                }
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasContent) {
                        val safeContent = remember(contentToShow) {
                            sanitizeRenderableText(contentToShow)
                        }
                        if (safeContent.length > LONG_TEXT_MARKDOWN_LIMIT) {
                            ChunkedMessageText(
                                text = safeContent,
                                color = if (isUser) UserBubbleText else AssistantBubbleText
                            )
                        } else {
                            val annotatedText = remember(safeContent) {
                                parseInlineMarkdown(safeContent)
                            }
                            Text(
                                text = annotatedText,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                color = if (isUser) UserBubbleText else AssistantBubbleText
                            )
                        }
                    }

                    if (shouldShowThinkingAnimation) {
                        ThinkingDotsAnimation()
                    }
                }
            }
        }

        if (canShowFeedback) {
            Spacer(modifier = Modifier.width(4.dp))
            com.example.scenic_avatar_guide_app.ui.components.FeedbackButton(
                hasFeedback = message.hasFeedback,
                onClick = { onFeedbackClick(message.id) }
            )
        }
    }
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
        modifier = Modifier.padding(start = 4.dp),
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
private fun ChunkedMessageText(
    text: String,
    color: Color
) {
    val chunks = remember(text) { chunkTextForRendering(text, TEXT_RENDER_CHUNK_SIZE) }
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        chunks.forEach { chunk ->
            Text(
                text = chunk,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = color,
                softWrap = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
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

private fun chunkTextForRendering(text: String, chunkSize: Int): List<String> {
    if (text.length <= chunkSize) return listOf(text)
    val chunks = mutableListOf<String>()
    var start = 0
    while (start < text.length) {
        val maxEnd = minOf(start + chunkSize, text.length)
        val newlineEnd = text.lastIndexOf('\n', maxEnd - 1).takeIf { it > start + chunkSize / 2 }
        val punctuationEnd = findLastBreakBefore(text, start, maxEnd)
        val end = when {
            maxEnd == text.length -> maxEnd
            newlineEnd != null -> newlineEnd + 1
            punctuationEnd != -1 -> punctuationEnd + 1
            else -> maxEnd
        }
        chunks += text.substring(start, end)
        start = end
    }
    return chunks
}

private fun findLastBreakBefore(text: String, start: Int, end: Int): Int {
    for (index in end - 1 downTo start + 1) {
        when (text[index]) {
            '。', '！', '？', '；', '.', '!', '?', ';', '，', ',' -> return index
        }
    }
    return -1
}

private fun parseInlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            i + 1 < text.length && text[i] == '*' && text[i + 1] == '*' -> {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(text[i])
                    i++
                }
            }
            i + 1 < text.length && text[i] == '_' && text[i + 1] == '_' -> {
                val end = text.indexOf("__", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(text[i])
                    i++
                }
            }
            text[i] == '*' -> {
                val end = text.indexOf('*', i + 1)
                if (end != -1 && end > i + 1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            text[i] == '_' -> {
                val end = text.indexOf('_', i + 1)
                if (end != -1 && end > i + 1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end != -1 && end > i + 1) {
                    withStyle(SpanStyle(
                        background = Color(0xFFE0E0E0),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            i + 1 < text.length && text[i] == '~' && text[i + 1] == '~' -> {
                val end = text.indexOf("~~", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(text[i])
                    i++
                }
            }
            i + 1 < text.length && text[i] == '[' -> {
                val textEnd = text.indexOf(']', i + 1)
                if (textEnd != -1 && textEnd + 1 < text.length && text[textEnd + 1] == '(') {
                    val urlEnd = text.indexOf(')', textEnd + 2)
                    if (urlEnd != -1) {
                        val linkText = text.substring(i + 1, textEnd)
                        val linkUrl = text.substring(textEnd + 2, urlEnd)
                        pushStringAnnotation(tag = "URL", annotation = linkUrl)
                        withStyle(SpanStyle(
                            color = Color(0xFF1565C0),
                            textDecoration = TextDecoration.Underline
                        )) {
                            append(linkText)
                        }
                        pop()
                        i = urlEnd + 1
                    } else {
                        append(text[i])
                        i++
                    }
                } else {
                    append(text[i])
                    i++
                }
            }
            else -> {
                append(text[i])
                i++
            }
        }
    }
}
