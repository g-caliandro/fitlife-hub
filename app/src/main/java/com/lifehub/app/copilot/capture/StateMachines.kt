package com.lifehub.app.copilot.capture

import com.lifehub.app.data.model.CaptureStatus
import com.lifehub.app.data.model.CaptureStatus.*
import com.lifehub.app.data.model.PendingStatus

/** Allowed capture transitions (design doc §4, "ciclo di vita di una cattura"). Pure, unit-tested. */
object CaptureStateMachine {
    private val allowed: Map<CaptureStatus, Set<CaptureStatus>> = mapOf(
        RECORDED to setOf(PROCESSING, FAILED, CANCELLED),
        PROCESSING to setOf(DONE, AWAITING_INPUT, FAILED, CANCELLED),
        AWAITING_INPUT to setOf(PROCESSING, DONE, CANCELLED),
        // Retry: back to RECORDED (phase 4 worker, re-transcribes if needed) or straight to PROCESSING (text capture).
        FAILED to setOf(RECORDED, PROCESSING, CANCELLED),
        DONE to setOf(CANCELLED),
        CANCELLED to emptySet(),
    )

    fun canMove(from: CaptureStatus, to: CaptureStatus): Boolean = to in allowed.getValue(from)

    /** Undo is allowed from every state except CANCELLED (already undone). */
    fun canUndo(s: CaptureStatus) = canMove(s, CANCELLED)

    /** Writes are refused once the user undid the capture: the copilot never redoes an undone log by itself. */
    fun acceptsWrites(s: CaptureStatus) = s == PROCESSING
}

/** OPEN -> ANSWERED | DISCARDED. Both are final: answering from one place (chat, inbox, notification) closes it everywhere. */
object PendingStateMachine {
    fun canMove(from: PendingStatus, to: PendingStatus): Boolean = from == PendingStatus.OPEN && to != PendingStatus.OPEN
}

class IllegalTransitionException(message: String) : IllegalStateException(message)
