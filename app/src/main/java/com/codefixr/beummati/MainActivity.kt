package com.codefixr.beummati

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.codefixr.beummati.data.DeepLinks
import com.codefixr.beummati.data.SettingsStore
import com.codefixr.beummati.player.LecturePlayerSession
import com.codefixr.beummati.ui.BeUmmatiApp
import com.codefixr.beummati.ui.theme.BeUmmatiTheme

class MainActivity : ComponentActivity() {
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /** Latest deep-link route waiting for Compose navigation. */
    private val pendingRouteState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        LecturePlayerSession.connect(this)
        requestNotificationPermissionIfNeeded()
        pendingRouteState.value = DeepLinks.routeFrom(intent)
        setContent {
            val appearance by SettingsStore.appearance.collectAsState()
            val themeKind by SettingsStore.themeKind.collectAsState()
            val playerState by LecturePlayerSession.state.collectAsState()
            val pendingRoute by pendingRouteState
            // Keep screen awake while a lecture is playing (sleep → end of lecture included).
            DisposableEffect(Unit) {
                onDispose { window.decorView.keepScreenOn = false }
            }
            SideEffect {
                // Keep awake while playing or buffering so brief stalls don't let the screen sleep.
                window.decorView.keepScreenOn =
                    playerState.nowPlaying != null && (playerState.isPlaying || playerState.isBuffering)
            }
            BeUmmatiTheme(appearance = appearance, themeKind = themeKind) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BeUmmatiApp(
                        pendingRoute = pendingRoute,
                        onPendingRouteConsumed = { pendingRouteState.value = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingRouteState.value = DeepLinks.routeFrom(intent)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
