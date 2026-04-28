package com.example.scenic_avatar_guide_app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
// import com.example.scenic_avatar_guide_app.core.network.NetworkModule
import com.example.scenic_avatar_guide_app.ui.theme.*

// ==================== 主界面 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val baseUrl by viewModel.baseUrl.collectAsStateWithLifecycle()
    val serverEndpoint by viewModel.serverEndpoint.collectAsStateWithLifecycle()
    val deviceId by viewModel.deviceId.collectAsStateWithLifecycle()
    val userId by viewModel.userId.collectAsStateWithLifecycle()
    val sessionId by viewModel.sessionId.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val isChecking by viewModel.isCheckingConnection.collectAsStateWithLifecycle()
    val currentVoiceId by viewModel.currentVoiceId.collectAsStateWithLifecycle()
    val availableVoices = viewModel.availableVoices
    val scenicId by viewModel.scenicId.collectAsStateWithLifecycle()
    val spotId by viewModel.spotId.collectAsStateWithLifecycle()
    val scenicAreas = viewModel.scenicAreas

    var showServerDialog by remember { mutableStateOf(false) }
    var showScenicDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val currentScenicName = scenicAreas.find { it.id == scenicId }?.name ?: "未选择"
    val currentSpotName = scenicAreas.find { it.id == scenicId }?.spots?.find { it.id == spotId }?.name ?: "未选择"
    val scenicSubtitle = if (scenicId == null || spotId == null) "请选择景区和景点" else "$currentScenicName · $currentSpotName"

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Primary,
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .background(Surface)
        ) {
            // 连接状态卡片
            ConnectionStatusCard(
                connectionState = connectionState,
                isChecking = isChecking,
                onCheck = { viewModel.checkConnection() },
                modifier = Modifier.padding(16.dp)
            )

            // 服务器设置
            SettingsGroup(title = "服务器配置") {
                // 当前地址
                SettingsListItem(
                    icon = Icons.Default.Dns,
                    title = "后端地址",
                    subtitle = if (baseUrl.isBlank()) "未配置" else baseUrl,
                    onClick = { showServerDialog = true },
                    trailing = {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )

                HorizontalDivider(color = SurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))

                // 主机信息
                SettingsListItem(
                    icon = Icons.Default.Lan,
                    title = "主机",
                    subtitle = serverEndpoint.host.ifBlank { "未配置" }
                )

                HorizontalDivider(color = SurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))

                // 端口信息
                SettingsListItem(
                    icon = Icons.Default.SettingsEthernet,
                    title = "端口 / 协议",
                    subtitle = buildString {
                        append(serverEndpoint.port.ifBlank { "未配置" })
                        if (serverEndpoint.scheme.isNotBlank()) {
                            append(" · ")
                            append(serverEndpoint.scheme.uppercase())
                        }
                    }
                )

            }

            Spacer(modifier = Modifier.height(16.dp))

            // 会话信息
            SettingsGroup(title = "会话信息") {
                SettingsListItem(
                    icon = Icons.Default.Person,
                    iconBg = Primary.copy(alpha = 0.1f),
                    title = "用户 ID",
                    subtitle = userId ?: "未生成"
                )
                HorizontalDivider(color = SurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                SettingsListItem(
                    icon = Icons.Default.Devices,
                    iconBg = Primary.copy(alpha = 0.1f),
                    title = "设备 ID",
                    subtitle = deviceId ?: "未生成"
                )
                HorizontalDivider(color = SurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                SettingsListItem(
                    icon = Icons.AutoMirrored.Default.Chat,
                    iconBg = Primary.copy(alpha = 0.1f),
                    title = "会话 ID",
                    subtitle = sessionId ?: "未创建"
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 景区与景点设置
            SettingsGroup(title = "景区与景点") {
                SettingsListItem(
                    icon = Icons.Default.Landscape,
                    iconBg = Primary.copy(alpha = 0.1f),
                    title = "当前位置",
                    subtitle = scenicSubtitle,
                    onClick = { showScenicDialog = true },
                    trailing = {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )

                if (showScenicDialog) {
                    ScenicSelectionDialog(
                        scenicAreas = scenicAreas,
                        onSelected = { newScenicId, newSpotId ->
                            viewModel.setScenicSpot(newScenicId, newSpotId)
                            showScenicDialog = false
                        },
                        onDismiss = { showScenicDialog = false }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 语音设置
            SettingsGroup(title = "语音设置") {
                val currentVoice = availableVoices.find { it.id == currentVoiceId } ?: availableVoices.first()
                var showVoiceDialog by remember { mutableStateOf(false) }

                SettingsListItem(
                    icon = Icons.Default.RecordVoiceOver,
                    iconBg = Primary.copy(alpha = 0.1f),
                    title = "发音人",
                    subtitle = "${currentVoice.displayName} · ${currentVoice.description}",
                    onClick = { showVoiceDialog = true },
                    trailing = {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )

                if (showVoiceDialog) {
                    VoiceSelectionDialog(
                        voices = availableVoices,
                        currentVoiceId = currentVoiceId,
                        onVoiceSelected = { voiceId ->
                            viewModel.setVoiceId(voiceId)
                            showVoiceDialog = false
                        },
                        onDismiss = { showVoiceDialog = false }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 操作
            SettingsGroup(title = "操作") {
                SettingsListItem(
                    icon = Icons.Default.Refresh,
                    iconBg = Warning.copy(alpha = 0.1f),
                    iconTint = Warning,
                    title = "重置会话",
                    subtitle = "清除当前会话并创建新会话",
                    titleColor = Warning,
                    onClick = { viewModel.clearSession() },
                    trailing = {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = TextHint
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 关于
            SettingsGroup(title = "关于") {
                SettingsListItem(
                    icon = Icons.Default.Info,
                    iconBg = TextHint.copy(alpha = 0.1f),
                    iconTint = TextSecondary,
                    title = "版本",
                    subtitle = "景灵智导 v1.0.0"
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // 服务器配置对话框
    if (showServerDialog) {
        ServerConfigDialog(
            currentEndpoint = serverEndpoint,
            onDismiss = { showServerDialog = false },
            onSave = { scheme, host, port ->
                viewModel.saveServerEndpoint(scheme, host, port)
                showServerDialog = false
            }
        )
    }
}

// ==================== 连接状态卡片 ====================

@Composable
private fun ConnectionStatusCard(
    connectionState: ConnectionState,
    isChecking: Boolean,
    onCheck: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (bgColor, icon, text) = when (connectionState) {
        ConnectionState.UNKNOWN -> Triple(SurfaceVariant, Icons.AutoMirrored.Default.HelpOutline, "未检测连接状态")
        ConnectionState.CHECKING -> Triple(SurfaceVariant, Icons.Default.Sync, "正在检测...")
        ConnectionState.CONNECTED -> Triple(Success.copy(alpha = 0.1f), Icons.Default.CheckCircle, "后端连接正常")
        ConnectionState.DISCONNECTED -> Triple(Error.copy(alpha = 0.1f), Icons.Default.ErrorOutline, "无法连接后端")
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(bgColor),
                contentAlignment = Alignment.Center
            ) {
                if (isChecking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = Primary
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = when (connectionState) {
                            ConnectionState.CONNECTED -> Success
                            ConnectionState.DISCONNECTED -> Error
                            else -> TextSecondary
                        },
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                Text(
                    text = when (connectionState) {
                        ConnectionState.CONNECTED -> "可以正常使用语音和问答功能"
                        ConnectionState.DISCONNECTED -> "请检查服务器地址和网络"
                        else -> "点击右侧按钮检测后端连通性"
                    },
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            FilledTonalButton(
                onClick = onCheck,
                enabled = !isChecking,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Primary.copy(alpha = 0.1f),
                    contentColor = Primary
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text("检测", fontSize = 13.sp)
            }
        }
    }
}

// ==================== 设置分组 ====================

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(content = content)
        }
    }
}

// ==================== 统一设置项 ====================

@Composable
private fun SettingsListItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    iconBg: Color = Primary.copy(alpha = 0.1f),
    iconTint: Color = Primary,
    titleColor: Color = TextPrimary,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else Modifier

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(clickableModifier)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = titleColor
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = TextSecondary,
                    lineHeight = 18.sp
                )
            }
        }

        trailing?.invoke()
    }
}

// ==================== 发音人选择对话框 ====================

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
                Text("选择发音人")
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
                                imageVector = if (voice.gender == com.example.scenic_avatar_guide_app.core.tts.Gender.FEMALE)
                                    Icons.Default.Face else Icons.Default.Face,
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

// ==================== 服务器配置对话框 ====================

@Composable
private fun ServerConfigDialog(
    currentEndpoint: ServerEndpointConfig,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var tempScheme by remember { mutableStateOf(currentEndpoint.scheme.ifBlank { "http" }) }
    var tempHost by remember { mutableStateOf(currentEndpoint.host) }
    var tempPort by remember { mutableStateOf(currentEndpoint.port) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Dns, contentDescription = null, tint = Primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("配置服务器地址")
            }
        },
        text = {
            Column {
                Text(
                    text = "用于真机与电脑同网段联调。保存后将清除旧会话。",
                    fontSize = 13.sp,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 协议选择
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("http", "https").forEach { scheme ->
                        FilterChip(
                            selected = tempScheme == scheme,
                            onClick = { tempScheme = scheme },
                            label = { Text(scheme.uppercase()) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Primary.copy(alpha = 0.15f),
                                selectedLabelColor = Primary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = tempHost,
                    onValueChange = { tempHost = it },
                    label = { Text("IP 或域名") },
                    placeholder = { Text("例如 192.168.1.100") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = tempPort,
                    onValueChange = { tempPort = it.filter(Char::isDigit) },
                    label = { Text("端口") },
                    placeholder = { Text("例如 8000 或 8081") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(tempScheme, tempHost, tempPort) },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}
