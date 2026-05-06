package com.example.scenic_avatar_guide_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.scenic_avatar_guide_app.core.avatar.JniBridgeJava
import com.example.scenic_avatar_guide_app.ui.screens.MainScreen
import com.example.scenic_avatar_guide_app.ui.screens.SettingsScreen
import com.example.scenic_avatar_guide_app.ui.theme.GuideTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        JniBridgeJava.SetActivityInstance(this)
        JniBridgeJava.SetContext(this)
        enableEdgeToEdge()
        setContent {
            GuideTheme {
                var showSettings by remember { mutableStateOf(false) }
                var authDialogTrigger by remember { mutableStateOf(0) }

                BackHandler(enabled = showSettings) {
                    if (showSettings) showSettings = false
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    MainScreen(
                        onSettingsClick = { showSettings = true },
                        externalAuthTrigger = authDialogTrigger
                    )
                    AnimatedVisibility(
                        visible = showSettings,
                        enter = slideInHorizontally { it },
                        exit = slideOutHorizontally { it }
                    ) {
                        SettingsScreen(
                            onNavigateBack = { showSettings = false },
                            onShowAuthDialog = {
                                authDialogTrigger++
                                showSettings = false
                            }
                        )
                    }
                }
            }
        }
    }
}
