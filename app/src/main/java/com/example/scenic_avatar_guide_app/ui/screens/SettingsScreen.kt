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
        "模拟器本地",
        NetworkModule.EMULATOR_LOCAL,
        "Android模拟器访问本机开发环境"
    ),
    ServerEnvironment(
        "真机本地",
        NetworkModule.DEVICE_LOCAL,
        "真机调试（需修改为本机IP）"
    ),
    ServerEnvironment(
        "Cloudflare Tunnel",
        NetworkModule.CLOUDFLARE_TUNNEL,
        "内网穿透临时公网访问"
    ),
    ServerEnvironment(
        "ngrok",
        NetworkModule.NGROK,
        "ngrok内网穿透"
    ),
    ServerEnvironment(
        "阿里云FC",
        NetworkModule.ALIYUN_FC,
        "阿里云函数计算正式环境"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val baseUrl by viewModel.baseUrl.collectAsState()
    val deviceId by viewModel.deviceId.collectAsState()
    val userId by viewModel.userId.collectAsState()
    val sessionId by viewModel.sessionId.collectAsState()

    var showEnvironmentDialog by remember { mutableStateOf(false) }
    var showCustomUrlDialog by remember { mutableStateOf(false) }
    var tempUrl by remember { mutableStateOf("") }

    LaunchedEffect(baseUrl) {
        tempUrl = baseUrl
    }

    Scaffold(
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
                        onClick = { showEnvironmentDialog = true }
                    )
                    HorizontalDivider(color = SurfaceVariant)
                    // 快速切换按钮
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ENVIRONMENTS.take(3).forEach { env ->
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

    // 环境选择对话框
    if (showEnvironmentDialog) {
        AlertDialog(
            onDismissRequest = { showEnvironmentDialog = false },
            title = { Text("选择服务器环境") },
            text = {
                Column {
                    ENVIRONMENTS.forEach { env ->
                        val isSelected = baseUrl == env.url
                        ListItem(
                            headlineContent = { Text(env.name) },
                            supportingContent = { Text(env.description, fontSize = 12.sp) },
                            trailingContent = {
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "已选择",
                                        tint = Primary
                                    )
                                }
                            },
                            modifier = Modifier.clickable {
                                viewModel.updateBaseUrl(env.url)
                                showEnvironmentDialog = false
                            }
                        )
                    }
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("自定义地址") },
                        supportingContent = { Text("手动输入后端URL") },
                        leadingContent = {
                            Icon(Icons.Default.Edit, contentDescription = null)
                        },
                        modifier = Modifier.clickable {
                            showEnvironmentDialog = false
                            showCustomUrlDialog = true
                        }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showEnvironmentDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 自定义 URL 对话框
    if (showCustomUrlDialog) {
        AlertDialog(
            onDismissRequest = { showCustomUrlDialog = false },
            title = { Text("自定义后端地址") },
            text = {
                OutlinedTextField(
                    value = tempUrl,
                    onValueChange = { tempUrl = it },
                    label = { Text("URL") },
                    placeholder = { Text("https://api.example.com/") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateBaseUrl(tempUrl)
                        showCustomUrlDialog = false
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomUrlDialog = false }) {
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
