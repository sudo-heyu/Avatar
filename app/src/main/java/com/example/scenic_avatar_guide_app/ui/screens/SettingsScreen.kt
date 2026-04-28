package com.example.scenic_avatar_guide_app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.scenic_avatar_guide_app.core.network.NetworkModule
import com.example.scenic_avatar_guide_app.ui.theme.*

data class ServerEnvironment(
    val name: String,
    val url: String,
    val description: String
)

val ENVIRONMENTS = listOf(
    ServerEnvironment(
        "真机本地",
        NetworkModule.DEVICE_LOCAL,
        "真机调试（需修改为本机IP）"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val baseUrl by viewModel.baseUrl.collectAsState()
    val serverEndpoint by viewModel.serverEndpoint.collectAsState()
    val deviceId by viewModel.deviceId.collectAsState()
    val userId by viewModel.userId.collectAsState()
    val sessionId by viewModel.sessionId.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    var showServerConfigDialog by remember { mutableStateOf(false) }
    var tempScheme by remember { mutableStateOf("http") }
    var tempHost by remember { mutableStateOf("") }
    var tempPort by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(serverEndpoint, showServerConfigDialog) {
        if (showServerConfigDialog) {
            tempScheme = serverEndpoint.scheme.ifBlank { "http" }
            tempHost = serverEndpoint.host
            tempPort = serverEndpoint.port
        }
    }

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
        ) {
            // 服务器设置
            Text(
                text = "服务器设置",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column {
                    // 当前环境显示
                    SettingItem(
                        icon = Icons.Default.Dns,
                        title = "后端地址",
                        subtitle = baseUrl,
                        onClick = { showServerConfigDialog = true }
                    )
                    HorizontalDivider(color = SurfaceVariant)
                    SettingInfoItem(
                        icon = Icons.Default.Lan,
                        title = "服务器主机",
                        subtitle = serverEndpoint.host.ifBlank { "未配置" }
                    )
                    HorizontalDivider(color = SurfaceVariant)
                    SettingInfoItem(
                        icon = Icons.Default.SettingsEthernet,
                        title = "服务器端口",
                        subtitle = buildString {
                            append(serverEndpoint.port.ifBlank { "未配置" })
                            if (serverEndpoint.scheme.isNotBlank()) {
                                append(" · ")
                                append(serverEndpoint.scheme.uppercase())
                            }
                        }
                    )
                    HorizontalDivider(color = SurfaceVariant)
                    // 快速切换按钮
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ENVIRONMENTS.forEach { env ->
                            val isSelected = baseUrl == env.url
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.updateBaseUrl(env.url) },
                                label = { Text(env.name, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Primary.copy(alpha = 0.2f),
                                    selectedLabelColor = Primary
                                )
                            )
                        }
                    }
                    TextButton(
                        onClick = { showServerConfigDialog = true },
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(end = 12.dp, bottom = 8.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("手动配置 IP 和端口")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 会话信息
            Text(
                text = "会话信息",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column {
                    SettingInfoItem(
                        icon = Icons.Default.Person,
                        title = "用户 ID",
                        subtitle = userId ?: "未生成"
                    )
                    HorizontalDivider(color = SurfaceVariant)
                    SettingInfoItem(
                        icon = Icons.Default.Devices,
                        title = "设备 ID",
                        subtitle = deviceId ?: "未生成"
                    )
                    HorizontalDivider(color = SurfaceVariant)
                    SettingInfoItem(
                        icon = Icons.Default.Chat,
                        title = "会话 ID",
                        subtitle = sessionId ?: "未创建"
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 操作
            Text(
                text = "操作",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                SettingItem(
                    icon = Icons.Default.Refresh,
                    title = "重置会话",
                    subtitle = "清除当前会话，创建新会话",
                    onClick = { viewModel.clearSession() },
                    titleColor = Warning
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 关于
            Text(
                text = "关于",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                SettingInfoItem(
                    icon = Icons.Default.Info,
                    title = "版本",
                    subtitle = "1.0.0"
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showServerConfigDialog) {
        AlertDialog(
            onDismissRequest = { showServerConfigDialog = false },
            title = { Text("配置服务器地址") },
            text = {
                Column {
                    Text(
                        text = "用于真机与电脑同网段联调。保存后将清除旧会话，后续请求直接走新地址。",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("http", "https").forEach { scheme ->
                            FilterChip(
                                selected = tempScheme == scheme,
                                onClick = { tempScheme = scheme },
                                label = { Text(scheme.uppercase()) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = tempHost,
                        onValueChange = { tempHost = it },
                        label = { Text("IP 或域名") },
                        placeholder = { Text("例如 192.168.1.23") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = tempPort,
                        onValueChange = { tempPort = it.filter(Char::isDigit) },
                        label = { Text("端口") },
                        placeholder = { Text("例如 8000") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.saveServerEndpoint(
                            scheme = tempScheme,
                            host = tempHost,
                            port = tempPort
                        )
                        showServerConfigDialog = false
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showServerConfigDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun SettingItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit = {},
    titleColor: Color = TextPrimary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = titleColor
            )
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = TextSecondary
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = TextHint
        )
    }
}

@Composable
fun SettingInfoItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = TextSecondary
            )
        }
    }
}
