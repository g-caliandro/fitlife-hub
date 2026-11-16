package com.lifehub.app.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lifehub.app.core.designsystem.LhRadius
import com.lifehub.app.core.designsystem.LhSize
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType

/** Shared field chrome: label above, bordered box, optional suffix/trailing, error/supporting caption. */
@Composable
internal fun LhFieldBase(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
    label: String?,
    placeholder: String?,
    error: String?,
    supportingText: String?,
    singleLine: Boolean,
    minLines: Int,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
    enabled: Boolean,
    suffix: String? = null,
    textAlign: TextAlign = TextAlign.Start,
    readOnly: Boolean = false,
    onClickOverride: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val c = LhTheme.colors
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val borderStroke = when {
        error != null -> BorderStroke(1.dp, c.destructive)
        focused -> BorderStroke(2.dp, c.primary)
        else -> BorderStroke(1.dp, c.input)
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) Text(label, style = LhType.label, color = c.foreground)
        Box(
            Modifier.fillMaxWidth()
                .defaultMinSize(minHeight = 52.dp)
                .background(c.card, LhRadius.itemShape)
                .border(borderStroke, LhRadius.itemShape)
                .let { if (onClickOverride != null) it.clickable(onClick = onClickOverride) else it }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(placeholder, style = LhType.bodyLarge, color = c.mutedForeground, modifier = Modifier.fillMaxWidth(), textAlign = textAlign)
                    }
                    BasicTextField(
                        value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(),
                        enabled = enabled && onClickOverride == null, readOnly = readOnly,
                        textStyle = LhType.bodyLarge.copy(color = if (enabled) c.foreground else c.mutedForeground, textAlign = textAlign),
                        cursorBrush = SolidColor(c.foreground), singleLine = singleLine,
                        minLines = minLines, keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
                        interactionSource = src, visualTransformation = visualTransformation,
                    )
                }
                if (suffix != null) {
                    Text(suffix, style = LhType.bodyLarge, color = c.mutedForeground, modifier = Modifier.padding(start = 8.dp))
                }
                if (trailing != null) Box(Modifier.padding(start = 8.dp)) { trailing() }
            }
        }
        val caption = error ?: supportingText
        if (caption != null) {
            Text(caption, style = LhType.caption, color = if (error != null) c.destructive else c.mutedForeground)
        }
    }
}

@Composable
fun LhTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    error: String? = null,
    supportingText: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLength: Int? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    enabled: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    LhFieldBase(
        value = value,
        onValueChange = { if (maxLength == null || it.length <= maxLength) onValueChange(it) },
        modifier = modifier, label = label, placeholder = placeholder, error = error, supportingText = supportingText,
        singleLine = singleLine, minLines = if (singleLine) 1 else minLines,
        keyboardOptions = keyboardOptions, keyboardActions = keyboardActions, enabled = enabled,
        visualTransformation = visualTransformation,
    )
}

/** Filters raw input: decimals=0 digits only; decimals=1 digits + one '.', ',' -> '.', max 1 fractional digit. */
fun filterNumber(input: String, decimals: Int): String {
    if (decimals <= 0) return input.filter { it.isDigit() }
    val s = input.replace(',', '.')
    val sb = StringBuilder()
    var seenDot = false
    var frac = 0
    for (ch in s) {
        when {
            ch.isDigit() -> {
                if (seenDot) { if (frac < decimals) { sb.append(ch); frac++ } } else sb.append(ch)
            }
            ch == '.' && !seenDot -> { seenDot = true; sb.append(ch) }
        }
    }
    return sb.toString()
}

@Composable
fun LhNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    suffix: String? = null,
    decimals: Int = 0,
    error: String? = null,
    supportingText: String? = null,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    enabled: Boolean = true,
) {
    LhFieldBase(
        value = value, onValueChange = { onValueChange(filterNumber(it, decimals)) },
        modifier = modifier, label = label, placeholder = placeholder, error = error, supportingText = supportingText,
        singleLine = true, minLines = 1,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimals > 0) KeyboardType.Decimal else KeyboardType.Number, imeAction = imeAction),
        keyboardActions = KeyboardActions(onNext = { onImeAction() }, onDone = { onImeAction() }, onGo = { onImeAction() }),
        enabled = enabled, suffix = suffix,
        textAlign = if (suffix != null) TextAlign.End else TextAlign.Start,
    )
}
