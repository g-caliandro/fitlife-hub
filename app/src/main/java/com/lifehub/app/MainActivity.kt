package com.lifehub.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.navigation.AppScaffold
import com.lifehub.app.navigation.DeepLinks
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val pendingRoute = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) readRoute(intent)
        setContent {
            LhTheme {
                AppScaffold(pendingRoute = pendingRoute, onRouteConsumed = { pendingRoute.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readRoute(intent)
    }

    private fun readRoute(intent: Intent?) {
        intent?.getStringExtra(DeepLinks.EXTRA_ROUTE)?.let { pendingRoute.value = it }
    }
}
