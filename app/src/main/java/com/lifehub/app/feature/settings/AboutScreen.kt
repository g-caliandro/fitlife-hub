package com.lifehub.app.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType
import com.lifehub.app.core.designsystem.components.LhGroupCard
import com.lifehub.app.core.designsystem.components.LhIconSquare
import com.lifehub.app.core.designsystem.components.LhListRow
import com.lifehub.app.core.designsystem.components.LhTopBar

/** V3-DS shell. */
@Composable
fun AboutRoute(onBack: () -> Unit) {
    val c = LhTheme.colors
    Column(Modifier.fillMaxSize()) {
        LhTopBar("About", onBack = onBack)
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            LhIconSquare(Icons.Outlined.Info, size = 72.dp, iconSize = 36.dp)
            Spacer(Modifier.height(12.dp))
            Text("Life Hub", style = LhType.title, color = c.foreground)
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val ver = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "0.3" }
            Text("Version $ver", style = LhType.captionMuted)
            Spacer(Modifier.height(24.dp))
            LhGroupCard(Modifier.fillMaxWidth()) {
                LhListRow("Poppins", subtitle = "SIL Open Font License 1.1")
                LhListRow("Open Food Facts", subtitle = "Food data under the ODbL licence", showDivider = false)
            }
        }
    }
}
