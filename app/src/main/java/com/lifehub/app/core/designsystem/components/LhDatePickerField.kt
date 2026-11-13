package com.lifehub.app.core.designsystem.components

import android.text.format.DateFormat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType
import com.lifehub.app.core.designsystem.fmtDateFull
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LhDatePickerField(
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    minDate: LocalDate? = null,
    maxDate: LocalDate? = null,
    error: String? = null,
) {
    var open by remember { mutableStateOf(false) }
    LhFieldBase(
        value = fmtDateFull(date), onValueChange = {}, modifier = modifier, label = label, placeholder = null,
        error = error, supportingText = null, singleLine = true, minLines = 1,
        keyboardOptions = KeyboardOptions.Default, keyboardActions = KeyboardActions.Default, enabled = true,
        readOnly = true, onClickOverride = { open = true },
        trailing = { Icon(Icons.Outlined.CalendarToday, null, Modifier.padding(0.dp), tint = LhTheme.colors.mutedForeground) },
    )
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val d = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                    return (minDate == null || !d.isBefore(minDate)) && (maxDate == null || !d.isAfter(maxDate))
                }
                override fun isSelectableYear(year: Int): Boolean =
                    (minDate == null || year >= minDate.year) && (maxDate == null || year <= maxDate.year)
            },
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onDateChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LhTimePickerField(
    time: LocalTime,
    onTimeChange: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    var open by remember { mutableStateOf(false) }
    val is24 = DateFormat.is24HourFormat(LocalContext.current)
    val text = if (is24) String.format(Locale.US, "%02d:%02d", time.hour, time.minute)
    else String.format(Locale.US, "%d:%02d %s", if (time.hour % 12 == 0) 12 else time.hour % 12, time.minute, if (time.hour < 12) "AM" else "PM")
    LhFieldBase(
        value = text, onValueChange = {}, modifier = modifier, label = label, placeholder = null,
        error = null, supportingText = null, singleLine = true, minLines = 1,
        keyboardOptions = KeyboardOptions.Default, keyboardActions = KeyboardActions.Default, enabled = true,
        readOnly = true, onClickOverride = { open = true },
        trailing = { Icon(Icons.Outlined.AccessTime, null, tint = LhTheme.colors.mutedForeground) },
    )
    if (open) {
        val state = rememberTimePickerState(time.hour, time.minute, is24)
        LhDialogContainer(onDismiss = { open = false }) {
            Text("Select time", style = LhType.heading, color = LhTheme.colors.foreground)
            Column(Modifier.fillMaxWidth().padding(vertical = 16.dp)) { TimePicker(state) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End)) {
                LhButton("Cancel", { open = false }, variant = LhButtonVariant.Outline)
                LhButton("OK", { onTimeChange(LocalTime.of(state.hour, state.minute)); open = false })
            }
        }
    }
}
