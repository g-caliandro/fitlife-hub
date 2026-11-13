package com.lifehub.app.core.designsystem.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lifehub.app.core.designsystem.LhRadius
import com.lifehub.app.core.designsystem.LhSize
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType
import java.util.Locale

private fun fmtStep(v: Double, decimals: Int): String =
    if (decimals <= 0) v.toLong().toString() else String.format(Locale.US, "%.${decimals}f", v)

@Composable
fun LhStepper(
    value: String,
    onValueChange: (String) -> Unit,
    step: Double,
    modifier: Modifier = Modifier,
    label: String? = null,
    suffix: String? = null,
    decimals: Int = 0,
    min: Double = 0.0,
    max: Double = 999.0,
    placeholder: String? = null,
) {
    val c = LhTheme.colors
    val current = value.toDoubleOrNull()
    fun bump(dir: Int) {
        val next = ((current ?: min) + dir * step).coerceIn(min, max)
        onValueChange(fmtStep(next, decimals))
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) Text(label, style = LhType.label, color = c.foreground)
        Row(
            Modifier.fillMaxWidth().height(56.dp).background(c.card, LhRadius.itemShape).border(1.dp, c.input, LhRadius.itemShape).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LhIconButton(Icons.Outlined.Remove, "Decrease", { bump(-1) }, variant = LhButtonVariant.Tonal, enabled = current == null || current > min)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Box(contentAlignment = Alignment.Center) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(placeholder, style = LhType.tnum(LhType.bodyLarge), color = c.mutedForeground, textAlign = TextAlign.Center)
                        }
                        BasicTextField(
                            value = value, onValueChange = { onValueChange(filterNumber(it, decimals)) },
                            textStyle = LhType.tnum(LhType.bodyLarge).copy(color = c.foreground, textAlign = TextAlign.Center),
                            cursorBrush = SolidColor(c.foreground), singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = if (decimals > 0) KeyboardType.Decimal else KeyboardType.Number, imeAction = ImeAction.Done),
                        )
                    }
                    if (suffix != null) Text(suffix, style = LhType.bodyLarge, color = c.mutedForeground, modifier = Modifier.padding(start = 4.dp))
                }
            }
            LhIconButton(Icons.Outlined.Add, "Increase", { bump(1) }, variant = LhButtonVariant.Tonal, enabled = current == null || current < max)
        }
    }
}
