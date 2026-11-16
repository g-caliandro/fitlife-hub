package com.lifehub.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Scale
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifehub.app.container
import com.lifehub.app.feature.copilot.rememberNotificationPermissionRequest
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType
import com.lifehub.app.core.designsystem.components.*
import kotlinx.coroutines.delay

@Composable
fun CopilotSettingsRoute(onBack: () -> Unit, onOpenMeasures: () -> Unit = {}) {
    val c = LocalContext.current.container
    val vm: CopilotSettingsViewModel = viewModel(factory = CopilotSettingsViewModel.factory(c))
    val state by vm.ui.collectAsState()
    val ctx = LocalContext.current
    val askNotif = rememberNotificationPermissionRequest()
    CopilotSettingsScreen(
        state, onBack,
        onLlmBaseUrl = vm::setLlmBaseUrl, onLlmModel = vm::setLlmModel,
        onSaveLlmKey = vm::saveLlmKey, onRemoveLlmKey = vm::removeLlmKey,
        onSttBaseUrl = vm::setSttBaseUrl, onSttModel = vm::setSttModel, onSttLanguage = vm::setSttLanguage,
        onSaveSttKey = vm::saveSttKey, onRemoveSttKey = vm::removeSttKey,
        onTest = vm::testConnection, onOpenMeasures = onOpenMeasures,
        onNotifications = { on ->
            vm.setNotificationsEnabled(on)
            if (on && !com.lifehub.app.notifications.Notifier.hasPermission(ctx)) askNotif()
        },
    )
}

@Composable
fun CopilotSettingsScreen(
    state: CopilotUiState,
    onBack: () -> Unit,
    onLlmBaseUrl: (String) -> Unit = {}, onLlmModel: (String) -> Unit = {},
    onSaveLlmKey: (String) -> Unit = {}, onRemoveLlmKey: () -> Unit = {},
    onSttBaseUrl: (String) -> Unit = {}, onSttModel: (String) -> Unit = {}, onSttLanguage: (String) -> Unit = {},
    onSaveSttKey: (String) -> Unit = {}, onRemoveSttKey: () -> Unit = {},
    onTest: () -> Unit = {},
    onOpenMeasures: () -> Unit = {},
    onNotifications: (Boolean) -> Unit = {},
) {
    val s = state.settings
    Column(Modifier.fillMaxSize()) {
        LhTopBar("Copilot", onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
            LhSectionHeader("Language model", topPadding = 8.dp)
            LhGroupCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CommittedField("Base URL", s.llmBaseUrl, onLlmBaseUrl, uri = true)
                    CommittedField("Model", s.llmModel, onLlmModel, uri = true, supporting = "Must support tool calling.")
                    KeyField("API key", state.llmKeyMask, onSaveLlmKey, onRemoveLlmKey)
                }
            }

            LhSectionHeader("Speech to text")
            LhGroupCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CommittedField("Base URL", s.sttBaseUrl, onSttBaseUrl, uri = true)
                    CommittedField("Model", s.sttModel, onSttModel, uri = true)
                    KeyField("API key", state.sttKeyMask, onSaveSttKey, onRemoveSttKey)
                    CommittedField("Transcription language", s.sttLanguage, onSttLanguage, uri = true, supporting = "ISO 639-1 code, e.g. it, en.")
                }
            }

            LhSectionHeader("Connection")
            val running = state.test == ConnectionTestState.Running
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LhButton("Test connection", onTest, variant = LhButtonVariant.Tonal, leadingIcon = Icons.Outlined.NetworkCheck, enabled = !running)
                if (running) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = LhTheme.colors.foreground)
            }
            when (val t = state.test) {
                is ConnectionTestState.Success ->
                    Text("Connected. Key and model work, and the model supports tool calling (${t.model}).", style = LhType.caption, color = LhTheme.colors.primary, modifier = Modifier.padding(top = 8.dp))
                is ConnectionTestState.Failed ->
                    Text(t.message, style = LhType.caption, color = LhTheme.colors.destructive, modifier = Modifier.padding(top = 8.dp))
                else -> Text("Sends one tiny request to the language model. Speech-to-text is not tested yet.", style = LhType.captionMuted, modifier = Modifier.padding(top = 8.dp))
            }
            LhSectionHeader("Voice capture")
            LhGroupCard(Modifier.fillMaxWidth()) {
                LhSwitchRow(
                    "Capture notifications", s.notificationsEnabled, onNotifications, Modifier.testTag("copilot-notifications"),
                    subtitle = "Outcome with Undo, questions that need an answer, and errors with Retry", showDivider = false,
                )
            }
            LhSectionHeader("Food logging")
            LhGroupCard(Modifier.fillMaxWidth()) {
                LhListRow(
                    "Household measures", Modifier.testTag("copilot-measures"),
                    subtitle = "Grams per spoon, glass, slice, piece", leadingIcon = Icons.Outlined.Scale,
                    onClick = onOpenMeasures, showDivider = false,
                )
            }
            Text("Food values for unpackaged foods: USDA FoodData Central (SR Legacy), public domain.", style = LhType.captionMuted, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
            Text("API keys are stored encrypted on this phone, excluded from backups and never included in data exports.", style = LhType.captionMuted, modifier = Modifier.padding(top = 16.dp, start = 4.dp))
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Text field that commits (debounced) after the user stops typing; blank input is ignored. */
@Composable
private fun CommittedField(label: String, value: String, onCommit: (String) -> Unit, uri: Boolean, supporting: String? = null) {
    var text by remember(value) { mutableStateOf(value) }
    LaunchedEffect(text) {
        delay(700)
        if (text.isNotBlank() && text.trim() != value) onCommit(text)
    }
    LhTextField(
        text, { text = it }, Modifier.fillMaxWidth(), label = label, supportingText = supporting,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, keyboardType = if (uri) KeyboardType.Uri else KeyboardType.Text, autoCorrectEnabled = false),
    )
}

/** Masked key entry: typing a new key then "Save key" stores it encrypted; the stored key is shown only as "••••abcd". */
@Composable
private fun KeyField(label: String, mask: String?, onSave: (String) -> Unit, onRemove: () -> Unit) {
    var text by remember(mask) { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LhTextField(
            text, { text = it }, Modifier.fillMaxWidth(), label = label,
            placeholder = if (mask != null) "Saved $mask. Paste a new key to replace." else "Paste your API key",
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            visualTransformation = PasswordVisualTransformation(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LhButton("Save key", { onSave(text); text = "" }, size = LhButtonSize.Sm, enabled = text.isNotBlank())
            if (mask != null) LhButton("Remove key", onRemove, variant = LhButtonVariant.Tonal, size = LhButtonSize.Sm)
        }
    }
}
