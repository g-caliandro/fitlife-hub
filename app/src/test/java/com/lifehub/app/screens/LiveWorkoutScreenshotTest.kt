package com.lifehub.app.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertTextEquals
import com.github.takahirom.roborazzi.captureRoboImage
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.data.model.SessionSet
import com.lifehub.app.data.model.SetStatus
import com.lifehub.app.data.model.TargetType
import com.lifehub.app.feature.workout.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h720dp-xxhdpi")
class LiveWorkoutScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private val t0 = Instant.parse("2026-11-12T10:00:00Z")
    private fun s(order: Int, idx: Int, name: String, type: TargetType, v: Int?, st: SetStatus, reps: Int? = null, w: Double? = null) =
        SessionSet("$order-$idx", "x", "e$order", name, order, idx, type, v, if (st == SetStatus.DONE && type != TargetType.SECONDS) reps else null,
            if (st == SetStatus.DONE && type == TargetType.SECONDS) reps else null, w, st, if (st == SetStatus.DONE) t0 else null, 90)

    private val sets = listOf(
        s(0, 0, "Pull-up", TargetType.REPS, 6, SetStatus.DONE, 6), s(0, 1, "Pull-up", TargetType.REPS, 6, SetStatus.DONE, 6),
        s(0, 2, "Pull-up", TargetType.REPS, 6, SetStatus.PENDING), s(0, 3, "Pull-up", TargetType.REPS, 6, SetStatus.PENDING),
        s(1, 0, "Dip", TargetType.REPS, 8, SetStatus.SKIPPED, w = 10.0), s(1, 1, "Dip", TargetType.REPS, 8, SetStatus.PENDING, w = 10.0),
        s(2, 0, "Explosive pull-up", TargetType.MAX, null, SetStatus.PENDING),
        s(3, 0, "Dead hang", TargetType.SECONDS, 30, SetStatus.PENDING), s(3, 1, "Dead hang", TargetType.SECONDS, 30, SetStatus.PENDING),
    )
    private val now = t0.toEpochMilli() + 32 * 60_000L
    private fun ui(focus: Int, rest: Long? = null, hold: Long? = null, pr: PrBanner? = null) = LiveUi(
        loaded = true, planName = "Pull & Bar", startedAtMillis = t0.toEpochMilli(), sets = sets, focusedIndex = focus,
        input = WorkoutOps.prefill(sets[focus]), restEndsAtMillis = rest, restTotalSec = 90, holdStartMillis = hold, pr = pr,
        previous = mapOf("0-0" to "6", "0-1" to "6", "0-2" to "5", "1-0" to "8 · +10kg", "1-1" to "8 · +10kg", "3-0" to "28 s"),
    )

    private fun shot(name: String, u: LiveUi, dark: Boolean = false) {
        rule.setContent { LhTheme(darkTheme = dark) { LiveWorkoutScreen(u, now, LiveActions()) } }
        rule.onRoot().captureRoboImage("build/screens/$name.png")
    }

    @Test fun liveInput() = shot("live-workout", ui(2))
    @Test fun liveInputDark() = shot("live-workout-dark", ui(2), dark = true)
    @Test fun liveReady() = shot("live-workout-ready", ui(3, rest = now - 2_000))
    @Test fun liveFinished() = shot("live-workout-finished", ui(3).copy(sets = sets.map { if (it.status == SetStatus.PENDING) it.copy(status = SetStatus.DONE, actualReps = 5) else it }))
    @Test fun liveRest() = shot("live-workout-rest", ui(3, rest = now + 83_000))
    @Test fun liveSeconds() = shot("live-workout-seconds", ui(7, hold = now - 18_000))
    @Test fun livePr() = shot("live-workout-pr", ui(2, pr = PrBanner(1, "New PR · 12 reps Pull-up")))
    @Test fun livePrDark() = shot("live-workout-pr-dark", ui(2, pr = PrBanner(1, "New PR · 12 reps Pull-up")), dark = true)
    @Test fun liveMax() = shot("live-workout-max", ui(6))
    @Test fun liveWeighted() = shot("live-workout-weighted", ui(5))
    @Test fun liveWeightedDark() = shot("live-workout-weighted-dark", ui(5), dark = true)
    @Test fun liveSecondsReady() = shot("live-workout-seconds-ready", ui(7))
    @Test fun liveSecondsReadyDark() = shot("live-workout-seconds-ready-dark", ui(7), dark = true)

    /** Seconds picker on a user-defined hold with an off-grid target (32 s on a 5 s wheel) and a weight. */
    private fun customHold(): LiveUi {
        val ss = sets.map { if (it.exerciseOrder == 3) it.copy(exerciseName = "Wall sit (weighted)", exerciseId = "user-wall-sit", targetValue = 32, weightKg = 12.5) else it }
        return ui(7).copy(sets = ss, input = WorkoutOps.prefill(ss[7]))
    }
    @Test fun liveSecondsPicker() = shot("live-workout-seconds-picker", customHold())
    @Test fun liveSecondsPickerDark() = shot("live-workout-seconds-picker-dark", customHold(), dark = true)
    @Test fun secondsPickerShowsTheExactTarget() {
        rule.setContent { LhTheme { LiveWorkoutScreen(customHold(), now, LiveActions()) } }
        rule.onNodeWithTag("picker-value-Seconds", useUnmergedTree = true).assertTextEquals("32 s")
        rule.onNodeWithTag("picker-value-Weight", useUnmergedTree = true).assertTextEquals("12.5 kg")
    }

    @Test fun finishSummary() = sheet("finish-summary", false)
    @Test fun finishSummaryDark() = sheet("finish-summary-dark", true)
    private fun sheet(name: String, dark: Boolean) {
        rule.setContent {
            LhTheme(darkTheme = dark) {
                FinishSheet(WorkoutOps.summarize(sets), 32, { listOf("Pull-up: 12 reps in one set") }, { _, _ -> }, {})
            }
        }
        rule.onAllNodes(androidx.compose.ui.test.isRoot())[1].captureRoboImage("build/screens/$name.png")
    }

    private val plan = com.lifehub.app.data.model.PlanWithExercises(
        com.lifehub.app.data.model.Plan("p", "Pull & Bar", setOf(1, 4), null, false, Instant.EPOCH, com.lifehub.app.data.model.DayType.PULL),
        listOf(
            Triple("Pull-up", 4, 6), Triple("Dip", 3, 8), Triple("Dead hang", 2, 30),
        ).mapIndexed { i, (n, sets, v) ->
            com.lifehub.app.data.model.PlanExerciseItem(
                com.lifehub.app.data.model.PlanExercise("pe$i", "p", "e$i", i, sets, if (n == "Dead hang") TargetType.SECONDS else TargetType.REPS, v, if (i == 1) 10.0 else null, 90),
                com.lifehub.app.data.model.Exercise("e$i", n, com.lifehub.app.data.model.ExerciseCategory.values().first(), TargetType.REPS, false, false),
            )
        },
    )
    @Test fun preview() = previewShot("workout-preview", false)
    @Test fun previewDark() = previewShot("workout-preview-dark", true)
    private fun previewShot(name: String, dark: Boolean) {
        rule.setContent { LhTheme(darkTheme = dark) { WorkoutPreviewScreen(plan, {}, {}) } }
        rule.onRoot().captureRoboImage("build/screens/$name.png")
    }
}
