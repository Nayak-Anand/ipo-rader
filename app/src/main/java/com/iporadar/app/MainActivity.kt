package com.iporadar.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iporadar.app.data.local.DarkMode
import com.iporadar.app.di.ServiceLocator
import com.iporadar.app.ui.nav.IpoRadarNavHost
import com.iporadar.app.ui.theme.IpoRadarTheme

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val deepLinkIpoId = intent?.getStringExtra(EXTRA_IPO_ID)

        setContent {
            val mode by ServiceLocator.prefs.darkMode
                .collectAsStateWithLifecycle(initialValue = DarkMode.SYSTEM)

            IpoRadarTheme(darkMode = mode) {
                IpoRadarNavHost(startIpoId = deepLinkIpoId)
            }
        }
    }

    companion object {
        const val EXTRA_IPO_ID = "extra_ipo_id"
    }
}
