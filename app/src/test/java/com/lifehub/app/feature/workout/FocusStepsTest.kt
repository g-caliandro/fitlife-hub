package com.lifehub.app.feature.workout

import com.lifehub.app.core.designsystem.ExerciseIcons
import com.lifehub.app.data.model.SessionSet
import com.lifehub.app.data.model.SetStatus
import com.lifehub.app.data.model.TargetType
import org.junit.Assert.assertEquals
import org.junit.Test

class FocusStepsTest {
    private fun s(order: Int, idx: Int, st: SetStatus, type: TargetType = TargetType.REPS) =
        SessionSet("$order-$idx", "x", "e$order", "Ex$order", order, idx, type, 5, null, null, null, st, null, 90)

    private val sets = listOf(
        s(0, 0, SetStatus.DONE), s(0, 1, SetStatus.PENDING),
        s(1, 0, SetStatus.SKIPPED), s(1, 1, SetStatus.PENDING, TargetType.SECONDS),
    )

    @Test fun plainSetStep() {
        assertEquals(FocusStep(StepKind.SET, 1), FocusSteps.derive(sets, 1, null, null, 1000))
    }

    @Test fun focusedNonPendingMovesToNextPending() {
        assertEquals(1, FocusSteps.derive(sets, 0, null, null, 0).setIndex)
        assertEquals(3, FocusSteps.derive(sets, 2, null, null, 0).setIndex)
    }

    @Test fun restCountdownThenReadyUntilAcknowledged() {
        assertEquals(StepKind.REST, FocusSteps.derive(sets, 1, 5000, null, 4999).kind)
        assertEquals(StepKind.REST_DONE, FocusSteps.derive(sets, 1, 5000, null, 5000).kind)
        // resume long after the end (process death): still waiting for the acknowledge
        assertEquals(StepKind.REST_DONE, FocusSteps.derive(sets, 1, 5000, null, 999_000).kind)
        // acknowledged / skipped -> restEndsAt cleared -> next set
        assertEquals(StepKind.SET, FocusSteps.derive(sets, 1, null, null, 999_000).kind)
    }

    @Test fun holdOnlyForSecondsSets() {
        assertEquals(StepKind.SET, FocusSteps.derive(sets, 1, null, 100, 200).kind)
        assertEquals(StepKind.HOLD_RUNNING, FocusSteps.derive(sets, 3, null, 100, 200).kind)
        assertEquals(StepKind.REST, FocusSteps.derive(sets, 3, 900, 100, 200).kind) // rest wins
    }

    @Test fun finishedWhenNothingPending() {
        val done = sets.map { it.copy(status = SetStatus.DONE) }
        assertEquals(FocusStep(StepKind.FINISHED, null), FocusSteps.derive(done, 3, null, null, 0))
        assertEquals(StepKind.FINISHED, FocusSteps.derive(done, 3, 5000, null, 0).kind)
    }

    @Test fun segmentsGroupedByExercise() {
        val g = FocusSteps.segments(sets, 1)
        assertEquals(listOf(2, 2), g.map { it.size })
        assertEquals(listOf(false, true, false, false), g.flatten().map { it.current })
    }

    @Test fun position() {
        assertEquals(FocusSteps.Position(2, 2, 2, 2), FocusSteps.position(sets, 3))
        assertEquals(FocusSteps.Position(1, 2, 2, 2), FocusSteps.position(sets, 1))
    }

    @Test fun iconSlugs() {
        assertEquals("ex_pull_up", ExerciseIcons.resourceName("Pull-up"))
        assertEquals("ex_l_sit", ExerciseIcons.resourceName("L-sit"))
        assertEquals("ex_straight_bar_dip_transition", ExerciseIcons.resourceName("Straight-bar dip transition"))
    }
}
