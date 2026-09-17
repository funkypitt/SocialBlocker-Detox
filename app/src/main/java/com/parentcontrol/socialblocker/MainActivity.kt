package com.parentcontrol.socialblocker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.parentcontrol.socialblocker.ui.ChallengeScreen
import com.parentcontrol.socialblocker.ui.HomeScreen
import com.parentcontrol.socialblocker.ui.HoursScreen
import com.parentcontrol.socialblocker.ui.LocalColors
import com.parentcontrol.socialblocker.ui.Nav
import com.parentcontrol.socialblocker.ui.ReaderTheme
import com.parentcontrol.socialblocker.ui.Screen
import com.parentcontrol.socialblocker.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    private val nav = Nav()
    private val askNotif = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val app = application as App
        setContent {
            val settings by app.prefs.settings.collectAsState()
            ReaderTheme(settings) {
                Bars()
                when (nav.current) {
                    Screen.Home -> HomeScreen(nav, app)
                    Screen.Hours -> HoursScreen(nav, app)
                    Screen.Challenge -> ChallengeScreen(nav, app)
                    Screen.Settings -> SettingsScreen(nav, app)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // the service may have stopped itself, or the battery setting changed meanwhile
        nav.version++
    }

    @Composable
    private fun Bars() {
        val view = LocalView.current
        val colors = LocalColors.current
        LaunchedEffect(colors.isDark) {
            val c = WindowInsetsControllerCompat(window, view)
            c.isAppearanceLightStatusBars = !colors.isDark
            c.isAppearanceLightNavigationBars = !colors.isDark
        }
    }
}
