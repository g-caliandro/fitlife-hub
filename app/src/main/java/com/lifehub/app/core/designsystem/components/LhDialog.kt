package com.lifehub.app.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.lifehub.app.core.designsystem.LhRadius
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType

/** Restyled dialog chrome: background, 1dp border, radius lg, 24dp padding. */
@Composable
internal fun LhDialogContainer(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = LhTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp), color = c.card, contentColor = c.foreground,
            border = BorderStroke(1.dp, c.border), tonalElevation = 0.dp, shadowElevation = 0.dp,
        ) {
            Column(Modifier.padding(24.dp), content = content)
        }
    }
}

@Composable
fun LhConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
    dismissText: String = "Cancel",
) {
    LhDialogContainer(onDismiss) {
        Text(title, style = LhType.heading, color = LhTheme.colors.foreground)
        Text(message, style = LhType.body, color = LhTheme.colors.mutedForeground, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            LhButton(dismissText, onDismiss, variant = LhButtonVariant.Outline)
            LhButton(confirmText, onConfirm, variant = if (destructive) LhButtonVariant.Destructive else LhButtonVariant.Primary)
        }
    }
}

@Composable
fun LhTypedConfirmDialog(
    title: String,
    message: String,
    requiredText: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var typed by rememberSaveable { mutableStateOf("") }
    LhDialogContainer(onDismiss) {
        Text(title, style = LhType.heading, color = LhTheme.colors.foreground)
        Text(message, style = LhType.body, color = LhTheme.colors.mutedForeground, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
        LhTextField(typed, { typed = it }, label = "Type $requiredText to confirm", placeholder = requiredText)
        Row(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            LhButton("Cancel", onDismiss, variant = LhButtonVariant.Outline)
            LhButton(confirmText, onConfirm, variant = LhButtonVariant.Destructive, enabled = typed == requiredText)
        }
    }
}
