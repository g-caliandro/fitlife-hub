package com.lifehub.app.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lifehub.app.core.designsystem.LhSize
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType

@Composable
fun LhTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    titleIcon: com.lifehub.app.data.model.DayType? = null,
    icon: ImageVector? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = LhTheme.colors
    Column(modifier.fillMaxWidth().background(c.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(LhSize.topBar).padding(start = if (onBack != null) 4.dp else 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                LhIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack)
                Box(Modifier.padding(start = 4.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (titleIcon != null) {
                        com.lifehub.app.core.designsystem.DayTypeIcon(titleIcon, size = 22.dp)
                        Box(Modifier.padding(start = 8.dp))
                    } else if (icon != null) {
                        Icon(icon, null, Modifier.size(22.dp), tint = c.foreground)
                        Box(Modifier.padding(start = 8.dp))
                    }
                    Text(title, style = LhType.title, color = c.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                }
                if (subtitle != null) Text(subtitle, style = LhType.captionMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            actions()
        }
    }
}
