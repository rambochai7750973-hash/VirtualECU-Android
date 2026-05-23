package com.virtualecu.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.virtualecu.android.ui.screens.CanLogScreen
import com.virtualecu.android.ui.screens.DashboardScreen
import com.virtualecu.android.ui.screens.PeriodicMessagesScreen
import com.virtualecu.android.ui.theme.VirtualECUTheme

enum class Screen {
    Dashboard, PeriodicMessages, CanLog
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VirtualECUTheme {
                App()
            }
        }
    }
}

@Composable
fun App() {
    var currentScreen by remember { mutableStateOf(Screen.Dashboard) }

    when (currentScreen) {
        Screen.Dashboard -> {
            DashboardScreen(
                onNavigateToPeriodic = { currentScreen = Screen.PeriodicMessages },
                onNavigateToLog = { currentScreen = Screen.CanLog }
            )
        }
        Screen.PeriodicMessages -> {
            PeriodicMessagesScreen(
                onBack = { currentScreen = Screen.Dashboard }
            )
        }
        Screen.CanLog -> {
            CanLogScreen(
                onBack = { currentScreen = Screen.Dashboard }
            )
        }
    }
}
