package com.qrstudio

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.qrstudio.ui.QrApp
import com.qrstudio.ui.theme.QrStudioTheme

class MainActivity : ComponentActivity() {

    // Bumped on every scan request so QrApp can react even when already visible
    // (the activity is singleTask: a shortcut tap lands in onNewIntent).
    private var scanRequestCount by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Only on a fresh launch: getIntent() keeps the shortcut action across
        // recreations (rotation…) and must not re-trigger the navigation then.
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            QrStudioTheme {
                QrApp(scanRequestCount = scanRequestCount)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_SCAN) scanRequestCount++
    }

    companion object {
        /** Launcher shortcut action: open the app directly on the scanner tab. */
        const val ACTION_SCAN = "com.qrstudio.action.SCAN"
    }
}
