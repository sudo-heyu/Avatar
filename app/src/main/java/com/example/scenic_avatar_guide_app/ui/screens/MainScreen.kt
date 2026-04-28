package com.example.scenic_avatar_guide_app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.scenic_avatar_guide_app.ui.theme.*
import com.example.scenic_avatar_guide_app.ui.components.ArcWaveform
import com.example.scenic_avatar_guide_app.ui.components.AvatarView
import com.example.scenic_avatar_guide_app.domain.model.ChatMessage
import com.example.scenic_avatar_guide_app.domain.model.AvatarState
import com.example.scenic_avatar_guide_app.core.speech.SpeechRecognizerHelper
import com.example.scenic_avatar_guide_app.core.avatar.TestAvatarActions
import com.example.scenic_avatar_guide_app.core.avatar.AvatarPlayAction
import com.example.scenic_avatar_guide_app.core.tts.VoiceInfo
import com.example.scenic_avatar_guide_app.core.tts.VoiceStyle
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

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
    val currentVoice by viewModel.currentVoice.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var isCancelZone by remember { mutableStateOf(false) }

    var hasAudioPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        hasAudioPermission = isGranted
        if (isGranted) viewModel.enterVoiceInputMode()
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

    Scaffold(
        containerColor = Surface,
        contentWindowInsets = WindowInsets.ime
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                TopBar(
                    onSettingsClick = onSettingsClick,
                    onTestToggle = { viewModel.toggleTestPanel() },
                    showTestPanel = showTestPanel
                )

                AvatarSection(
                    avatarState = avatarState,
                    fullState = avatarFullState,
                    modifier = Modifier.fillMaxWidth().height(130.dp)
                )

                // 测试面板
                if (showTestPanel) {
                    TestActionPanel(
                        onActionClick = { action -> viewModel.playTestAction(action) },
                        currentVoice = currentVoice,
                        onVoiceChange = { voiceId -> viewModel.setVoice(voiceId) },
                        getAvailableVoices = { viewModel.getAvailableVoices() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (voiceInputMode && isRecording) {
                    CancelZone(
                        isCancelZone = isCancelZone,
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    )
                }

                MessageList(messages, isLoading, listState, Modifier.weight(1f).fillMaxWidth())

                Column(
                    modifier = Modifier.fillMaxWidth().background(Surface).imePadding().navigationBarsPadding()
                ) {
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
                            onInputChange = { viewModel.updateInputText(it) },
                            onSend = { viewModel.sendMessage() },
                            onVoiceClick = {
                                if (hasAudioPermission) viewModel.enterVoiceInputMode()
                                else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            onCameraInput = { viewModel.startCameraInput() },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
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
    modifier: Modifier = Modifier
) {
    AvatarView(
        avatarState = avatarState,
        fullState = fullState,
        modifier = modifier
    )
}

/**
 * 测试动作面板
 */
@Composable
private fun TestActionPanel(
    onActionClick: (AvatarPlayAction) -> Unit,
    currentVoice: VoiceInfo,
    onVoiceChange: (String) -> Unit,
    getAvailableVoices: () -> List<VoiceInfo>,
    modifier: Modifier = Modifier
) {
    val testButtons = remember { TestAvatarActions.getButtons() }
    var showVoiceSelector by remember { mutableStateOf(false) }
    val voices = remember { getAvailableVoices() }

    Card(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🎭 测试场景",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )

                // 发音人选择按钮
                OutlinedButton(
                    onClick = { showVoiceSelector = !showVoiceSelector },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Primary
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    Icon(
                        Icons.Default.RecordVoiceOver,
                        contentDescription = "发音人",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = currentVoice.displayName,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            }

            // 发音人选择面板
            if (showVoiceSelector) {
                Spacer(Modifier.height(8.dp))
                VoiceSelector(
                    voices = voices,
                    currentVoice = currentVoice,
                    onVoiceSelected = { voice ->
                        onVoiceChange(voice.id)
                        showVoiceSelector = false
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(8.dp))

            // 动作按钮网格 - 4列
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                testButtons.chunked(4).forEach { rowButtons ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowButtons.forEach { button ->
                            ActionChip(
                                label = button.label,
                                icon = button.icon,
                                description = button.description,
                                onClick = { onActionClick(button.action) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 发音人选择器
 */
@Composable
private fun VoiceSelector(
    voices: List<VoiceInfo>,
    currentVoice: VoiceInfo,
    onVoiceSelected: (VoiceInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    Surface(
        modifier = modifier.heightIn(max = 200.dp),
        shape = RoundedCornerShape(12.dp),
        color = Surface
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            // 按风格分组
            val groupedVoices = voices.groupBy { it.style }

            groupedVoices.forEach { (style, styleVoices) ->
                // 风格标题
                item {
                    Text(
                        text = styleToLabel(style),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextHint,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }

                // 该风格下的发音人
                items(styleVoices.take(4)) { voice ->
                    VoiceItem(
                        voice = voice,
                        isSelected = voice.id == currentVoice.id,
                        onClick = { onVoiceSelected(voice) }
                    )
                }
            }
        }
    }
}

/**
 * 发音人条目
 */
@Composable
private fun VoiceItem(
    voice: VoiceInfo,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (isSelected) Primary.copy(0.1f) else Color.Transparent,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 性别图标
            Icon(
                if (voice.gender == com.example.scenic_avatar_guide_app.core.tts.Gender.FEMALE)
                    Icons.Default.Face else Icons.Default.Face,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (isSelected) Primary else TextSecondary
            )

            Spacer(Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = voice.displayName,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    color = if (isSelected) Primary else TextPrimary
                )
                Text(
                    text = voice.description,
                    fontSize = 11.sp,
                    color = TextHint
                )
            }

            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "已选择",
                    modifier = Modifier.size(18.dp),
                    tint = Primary
                )
            }
        }
    }
}

/**
 * 风格转标签
 */
private fun styleToLabel(style: VoiceStyle): String = when (style) {
    VoiceStyle.FRIENDLY -> "亲和系"
    VoiceStyle.GENTLE -> "温柔系"
    VoiceStyle.INTELLECTUAL -> "知性系"
    VoiceStyle.PROFESSIONAL -> "专业系"
    VoiceStyle.CHILD -> "童声系"
    VoiceStyle.LITERARY -> "文艺系"
    VoiceStyle.NEWS -> "新闻系"
}

/**
 * 动作按钮
 */
@Composable
private fun ActionChip(
    label: String,
    icon: String,
    description: String = "",
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (enabled) Primary.copy(0.1f) else SurfaceVariant,
        enabled = enabled,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = icon,
                fontSize = 18.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (enabled) Primary else TextHint
            )
        }
    }
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
    val modes = listOf(InteractionMode.Chat to "聊天", InteractionMode.QA to "问答", InteractionMode.Route to "路线")
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
private fun MessageList(messages: List<ChatMessage>, isLoading: Boolean, listState: androidx.compose.foundation.lazy.LazyListState, modifier: Modifier = Modifier) {
    LazyColumn(modifier.padding(horizontal = 12.dp), state = listState, contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
    Row(Modifier.fillMaxWidth(), if (isUser) Arrangement.End else Arrangement.Start) {
        Surface(
            modifier = Modifier.widthIn(max = 260.dp),
            shape = RoundedCornerShape(16.dp, 16.dp, if (isUser) 16.dp else 4.dp, if (isUser) 4.dp else 16.dp),
            color = if (isUser) UserBubbleBg else AssistantBubbleBg
        ) {
            Text(message.content, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), fontSize = 14.sp, lineHeight = 20.sp, color = if (isUser) UserBubbleText else AssistantBubbleText)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InputSection(
    mode: InteractionMode, inputText: String, isLoading: Boolean,
    onInputChange: (String) -> Unit, onSend: () -> Unit, onVoiceClick: () -> Unit, onCameraInput: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier.clip(RoundedCornerShape(24.dp)).background(InputBarBg)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (mode == InteractionMode.QA) IconButton(onClick = onCameraInput, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.CameraAlt, "拍照识景", Modifier.size(22.dp), Primary) }
            BasicTextField(
                value = inputText,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                maxLines = 3,
                keyboardOptions = KeyboardOptions.Default,
                keyboardActions = KeyboardActions.Default,
                decorationBox = { innerTextField ->
                    OutlinedTextFieldDefaults.DecorationBox(
                        value = inputText,
                        innerTextField = innerTextField,
                        enabled = true,
                        singleLine = false,
                        visualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        placeholder = { Text(when (mode) { InteractionMode.Chat -> "输入消息..."; InteractionMode.QA -> "输入问题或拍照..."; InteractionMode.Route -> "输入偏好..." }, fontSize = 14.sp, color = TextHint) },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent),
                        contentPadding = PaddingValues(start = 12.dp, end = 4.dp, top = 0.dp, bottom = 0.dp)
                    )
                }
            )
            if (inputText.isNotBlank()) FilledIconButton(onClick = onSend, enabled = !isLoading, modifier = Modifier.size(40.dp), shape = CircleShape) { Icon(Icons.AutoMirrored.Filled.Send, "发送", Modifier.size(20.dp), Color.White) }
            else IconButton(onClick = onVoiceClick, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Mic, "语音输入", Modifier.size(22.dp), TextSecondary) }
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

enum class InteractionMode { Chat, QA, Route }
