package com.lifehub.app.core.designsystem

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** A message to show on the destination screen (hosted by AppScaffold). */
data class BusMessage(val message: String, val actionLabel: String?, val onAction: (() -> Unit)?, val postedAtMs: Long)

/**
 * Post-navigation snackbars (e.g. "Macros saved · Undo"). Call [show] right AFTER popping/navigating back.
 * AppScaffold dismisses the current snackbar synchronously on every destination change, so a message posted
 * after the navigation lives exactly until the next one (never covers the next screen's Save bar).
 */
object SnackbarBus {
    private val _events = MutableSharedFlow<BusMessage>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<BusMessage> = _events

    fun show(message: String, actionLabel: String? = "Undo", onAction: (() -> Unit)? = null) {
        _events.tryEmit(BusMessage(message, if (onAction == null) null else actionLabel, onAction, System.currentTimeMillis()))
    }
}
