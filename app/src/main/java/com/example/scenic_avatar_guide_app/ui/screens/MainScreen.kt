package com.example.scenic_avatar_guide_app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
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
import com.example.scenic_avatar_guide_app.ui.theme.*
import com.example.scenic_avatar_guide_app.ui.components.ArcWaveform
import com.example.scenic_avatar_guide_app.ui.components.AvatarView
import com.example.scenic_avatar_guide_app.domain.model.ChatMessage
import com.example.scenic_avatar_guide_app.domain.model.AvatarState
import com.example.scenic_avatar_guide_app.core.speech.SpeechRecognizerHelper
import com.example.scenic_avatar_guide_app.core.avatar.TestAvatarActions
import com.example.scenic_avatar_guide_app.core.avatar.AvatarPlayAction
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val showScenicSelection by viewModel.showScenicSelection.collectAsStateWithLifecycle()
    val scenicAreas = viewModel.scenicAreas

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val density = LocalDensity.current
    var isCancelZone by remember { mutableStateOf(false) }
    var showImagePickerDialog by remember { mutableStateOf(false) }
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }

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

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) coroutineScope.launch { listState.animateScrollToItem(messages.size - 1) }
    }

    // 输入法弹出/收起时滚动到底部，确保最新消息不被遮挡
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(imeBottom) {
        if (messages.isNotEmpty()) {
            delay(150)
            coroutineScope.launch { listState.animateScrollToItem(messages.size - 1) }
        }
    }

    var bottomBarContentHeight by remember { mutableStateOf(0.dp) }

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
            val totalHeight = remember { maxHeight }

            // 主内容区域：固定为初始屏幕高度减去底栏高度，输入法弹出时位置和大小完全不变
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(totalHeight - bottomBarContentHeight)
            ) {
                TopBar(
                    onSettingsClick = onSettingsClick,
                    onTestToggle = { viewModel.toggleTestPanel() },
                    showTestPanel = showTestPanel
                )

                // 数字人区域：在主内容区域内按比例分配
                AvatarSection(
                    avatarState = avatarState,
                    fullState = avatarFullState,
                    showUpperBodyOnly = true,
                    modifier = Modifier.fillMaxWidth().weight(2f)
                )

                // 消息列表：在主内容区域内填充剩余空间
                MessageList(
                    messages = messages,
                    isLoading = isLoading,
                    listState = listState,
                    modifier = Modifier.fillMaxWidth().weight(3f),
                    bottomPaddingDp = 4.dp
                )
            }

            // 底栏：绝对定位在底部，只让底栏响应输入法上推
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding()
                    .background(Surface)
            ) {
                // 内层容器单独测量内容高度（不含 imePadding），只测一次
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coordinates ->
                            if (bottomBarContentHeight == 0.dp) {
                                bottomBarContentHeight = with(density) { coordinates.size.height.toDp() }
                            }
                        }
                ) {
                    // 测试面板：功能卡片正上方，单行左右滑动
                    if (showTestPanel) {
                        CompactTestPanel(
                            onActionClick = { action -> viewModel.playTestAction(action) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

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

                    ModeSelector(currentMode, { viewModel.switchMode(it) }, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp))

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
                            pendingImageUri = pendingImageUri,
                            onInputChange = { viewModel.updateInputText(it) },
                            onSend = { viewModel.sendMessage() },
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    onSettingsClick: () -> Unit,
    onTestToggle: () -> Unit,
    showTestPanel: Boolean
) {
    TopAppBar(
        title = { Text("景灵智导", fontWeight = FontWeight.Bold) },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Primary, titleContentColor = Color.White),
        actions = {
            // 测试面板切换按钮
            IconButton(onTestToggle) {
                Icon(
                    if (showTestPanel) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "测试面板",
                    tint = Color.White
                )
            }
            IconButton(onSettingsClick) { Icon(Icons.Default.Settings, "设置", tint = Color.White) }
        }
    )
}

@Composable
private fun AvatarSection(
    avatarState: AvatarState,
    fullState: com.example.scenic_avatar_guide_app.domain.model.AvatarFullState,
    modifier: Modifier = Modifier,
    showUpperBodyOnly: Boolean = false
) {
    AvatarView(
        avatarState = avatarState,
        fullState = fullState,
        showUpperBodyOnly = showUpperBodyOnly,
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
    var selectedPanel by remember { mutableStateOf<TestPanelType?>(null) }

    Card(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TextButton(
                    onClick = { selectedPanel = toggle(selectedPanel, TestPanelType.EXPRESSION) },
                    colors = ButtonDefaults.textButtonColors(contentColor = Primary)
                ) {
                    Text("表情", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                TextButton(
                    onClick = { selectedPanel = toggle(selectedPanel, TestPanelType.GESTURE) },
                    colors = ButtonDefaults.textButtonColors(contentColor = Primary)
                ) {
                    Text("动作", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                TextButton(
                    onClick = { selectedPanel = toggle(selectedPanel, TestPanelType.SCENARIO) },
                    colors = ButtonDefaults.textButtonColors(contentColor = Primary)
                ) {
                    Text("场景", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            if (selectedPanel != null) {
                val buttons: List<Pair<String, AvatarPlayAction>> = when (selectedPanel) {
                    TestPanelType.EXPRESSION -> expressionButtons.map { it.label to it.toPlayAction() }
                    TestPanelType.GESTURE -> gestureButtons.map { it.label to it.toPlayAction() }
                    TestPanelType.SCENARIO -> scenarioButtons.map { it.label to it.action }
                    null -> emptyList()
                }
                Spacer(Modifier.height(4.dp))
                TestButtonGrid(buttons, onActionClick)
            }
        }
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
                        modifier = Modifier.weight(1f).height(32.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary),
                        border = null
                    ) {
                        Text(label, fontSize = 11.sp, maxLines = 1)
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
    SCENARIO
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
            Box(Modifier.weight(1f).height(32.dp).clip(RoundedCornerShape(16.dp)).background(if (isSelected) Primary else SurfaceVariant).clickable { onModeChange(mode) }, contentAlignment = Alignment.Center) {
                Text(label, fontSize = 13.sp, color = if (isSelected) Color.White else TextPrimary, fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal)
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
    bottomPaddingDp: androidx.compose.ui.unit.Dp = 8.dp
) {
    LazyColumn(
        modifier = modifier.padding(horizontal = 12.dp),
        state = listState,
        contentPadding = PaddingValues(top = 8.dp, bottom = bottomPaddingDp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(messages, key = { it.id }) { MessageBubble(it) }
        if (isLoading) item {
            Row(Modifier.fillMaxWidth(), Arrangement.Start) {
                Surface(shape = RoundedCornerShape(16.dp), color = SurfaceVariant) { Text("思考中...", Modifier.padding(12.dp), fontSize = 14.sp, color = TextSecondary) }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.isUser
    val imageUri = message.pendingImageUri ?: message.imageUrl
    Row(Modifier.fillMaxWidth(), if (isUser) Arrangement.End else Arrangement.Start) {
        Surface(
            modifier = Modifier.widthIn(max = 260.dp),
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
                if (message.content.isNotBlank()) {
                    Text(
                        message.content,
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = if (isUser) UserBubbleText else AssistantBubbleText
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InputSection(
    mode: InteractionMode, inputText: String, isLoading: Boolean,
    pendingImageUri: String?,
    onInputChange: (String) -> Unit, onSend: () -> Unit, onVoiceClick: () -> Unit, onCameraInput: () -> Unit,
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
                            contentPadding = PaddingValues(start = 12.dp, end = 4.dp, top = 0.dp, bottom = 0.dp)
                        )
                    }
                )
                if (canSend) FilledIconButton(onClick = onSend, enabled = !isLoading, modifier = Modifier.size(40.dp), shape = CircleShape) { Icon(Icons.AutoMirrored.Filled.Send, "发送", Modifier.size(20.dp), Color.White) }
                else IconButton(onClick = onVoiceClick, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Mic, "语音输入", Modifier.size(22.dp), TextSecondary) }
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
