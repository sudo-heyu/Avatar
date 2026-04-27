package com.example.scenic_avatar_guide_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.scenic_avatar_guide_app.ui.screens.MainScreen
import com.example.scenic_avatar_guide_app.ui.theme.GuideTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GuideTheme {
                MainScreen()
            }
        }
    }
}
