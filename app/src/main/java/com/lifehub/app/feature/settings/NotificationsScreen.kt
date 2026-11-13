package com.lifehub.app.feature.settings

import androidx.compose.runtime.Composable

@Composable
fun NotificationsRoute(onBack: () -> Unit) = SettingsRoute(onBack, SettingsSection.NOTIFICATIONS)
