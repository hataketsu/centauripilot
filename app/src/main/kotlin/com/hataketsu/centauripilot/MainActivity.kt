package com.hataketsu.centauripilot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.hataketsu.centauripilot.ui.AppRoot
import com.hataketsu.centauripilot.ui.CentauriPilotTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CentauriPilotTheme {
                androidx.compose.foundation.layout.Box(
                    Modifier.fillMaxSize().background(Color(0xFF0A0F1A))
                ) {
                    AppRoot(vm)
                }
            }
        }
    }
}
