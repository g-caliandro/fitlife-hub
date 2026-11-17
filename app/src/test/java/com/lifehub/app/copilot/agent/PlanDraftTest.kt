package com.lifehub.app.copilot.agent

import com.lifehub.app.copilot.COPILOT_BOOKKEEPING
import com.lifehub.app.copilot.CopilotFixture
import com.lifehub.app.copilot.FakeLlmClient.Companion.call
import com.lifehub.app.copilot.FakeLlmClient.Companion.text
import com.lifehub.app.copilot.FakeLlmClient.Companion.tools
import com.lifehub.app.copilot.StepLlmClient
import com.lifehub.app.copilot.dumpTables
import com.lifehub.app.copilot.llm.LlmToolCall
import com.lifehub.app.copilot.plan.ApproveResult
import com.lifehub.app.copilot.plan.DraftCodec
import com.lifehub.app.data.model.ChatOrigin
import com.lifehub.app.data.model.ChatRole
import com.lifehub.app.data.model.GoalState
import com.lifehub.app.data.model.PlanDraftStatus
import com.lifehub.app.domain.plannedOn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Phase 6 acceptance: "a plan draft is visible in the workouts screen and does not replace the active plan", and the
 * cross-cutting criterion "a workout plan never becomes active without Approve". Fake model, in-memory DB, no network.
 * Seed (CopilotFixture): active "Pull day" (Mon, Thu: Pull-up 4 × 8), active "Legs" (Wed), archived "Old plan" (Mon).
 */
@RunWith(RobolectricTestRunner::class)
class PlanDraftTest {
    private val f = CopilotFixture()
    @After fun tearDown() = f.close()

    /** Every user table (plans, plan_exercises, exercises, goals, links, diary...) except copilot bookkeeping and drafts. */
    private fun userData() = dumpTables(f.db, COPILOT_BOOKKEEPING + "plan_drafts")

    private val proposal = """{"title":"Verso il muscle up","goal":{"name":"Muscle up pulito","weeks":4},
        "plans":[{"name":"Pull A","weekdays":["monday","friday"],"day_type":"pull","exercises":[
          {"name":"pull-up","sets":5,"target_type":"reps","target_value":6,"rest_seconds":150},
          {"name":"Muscle up negativo","category":"pull","sets":3,"target_type":"reps","target_value":3},
          {"name":"Hollow hold","category":"core","sets":3,"target_type":"seconds","target_value":30}]}]}"""

    private suspend fun proposeInChat(): Pair<String, String> {
        val thread = f.chat.createThread("t", ChatOrigin.CHAT, f.now).id
        val llm = StepLlmClient(
            { tools(call("c1", "get_workout_plan")) },
            { tools(call("c2", "propose_workout_plan", proposal)) },
            { text("Ho preparato una bozza di 4 settimane: approvala se ti va bene.") },
        )
        f.processor(llm).submitText(thread, "Impostami 4 settimane verso il muscle up")
        return thread to f.drafts.all().single().id
    }

    private suspend fun exec(name: String, args: String = "{}", captureId: String? = null) =
        f.fullRegistry.execute(LlmToolCall("x", name, args), ToolContext(f.now, f.zone, null, captureId))

    @Test fun proposeSavesADraftAndLeavesEveryPlanUntouched() = runBlocking {
        val before = userData()
        val activeBefore = plannedOn(f.today, f.plans.observePlans().first()).map { it.plan.id }
        val (thread, id) = proposeInChat()

        assertEquals("the plans, exercises and goals tables are identical", before, userData())
        assertEquals("Home/streak input unchanged", activeBefore, plannedOn(f.today, f.plans.observePlans().first()).map { it.plan.id })
        assertTrue(f.plans.observePlans().first().none { it.plan.name == "Pull A" })
        val d = f.drafts.get(id)!!
        assertEquals(PlanDraftStatus.DRAFT, d.status); assertEquals(thread, d.threadId); assertEquals("Muscle up pulito", d.goalName); assertEquals(4, d.weeks)
        val p = DraftCodec.decode(d)
        assertEquals(listOf(1, 5), p.plans.single().weekdays)
        // Known exercise matched case-insensitively to the user's one; unknown ones kept as new (created only on Approve).
        assertEquals("e-pullup", p.plans.single().exercises[0].exerciseId); assertEquals("Pull-up", p.plans.single().exercises[0].name)
        assertTrue(p.plans.single().exercises[1].isNew)

        // The card is stored on the tool message and redraws on reopening.
        val card = f.chat.messages(thread).mapNotNull { CardCodec.decode(it.cardType, it.cardJson) }.filterIsInstance<PlanDraftCard>().single()
        assertEquals(id, card.draftId); assertEquals("Mon · Fri", card.plans.single().weekdays)
        assertEquals(listOf("5 × 6", "3 × 3", "3 × 30 s"), card.plans.single().exercises.map { it.summary })
        assertEquals(listOf(false, true, true), card.plans.single().exercises.map { it.isNew })
        assertEquals("Ho preparato una bozza di 4 settimane: approvala se ti va bene.", f.chat.messages(thread).last { it.role == ChatRole.ASSISTANT }.text)
    }

    @Test fun draftIsVisibleToGetWorkoutPlanNextToTheActivePlans() = runBlocking {
        val (_, id) = proposeInChat()
        val r = (exec("get_workout_plan").outcome as ToolOutcome.Ok).result
        assertEquals(setOf("Pull day", "Legs"), r.getValue("plans").jsonArray.map { it.jsonObject.getValue("name").jsonPrimitive.content }.toSet())
        val draft = r.getValue("drafts").jsonArray.single().jsonObject
        assertEquals(id, draft.getValue("draft_id").jsonPrimitive.content)
        assertEquals("draft", draft.getValue("status").jsonPrimitive.content)
        assertEquals("Pull A", draft.getValue("plans").jsonArray.single().jsonObject.getValue("name").jsonPrimitive.content)

        f.drafts.reject(id, f.now)
        val after = (exec("get_workout_plan").outcome as ToolOutcome.Ok).result.getValue("drafts").jsonArray.single().jsonObject
        assertEquals("rejected", after.getValue("status").jsonPrimitive.content)
        assertNull("decided drafts only as a header", after["plans"])
    }

    @Test fun approveWithoutTheCheckboxAddsPlansAndKeepsTheActiveOnes() = runBlocking {
        val (_, id) = proposeInChat()
        val r = f.drafts.approve(id, archiveOverlapping = false, today = f.today, now = f.now) as ApproveResult.Approved
        assertTrue(r.archivedPlanIds.isEmpty())
        val all = f.plans.observePlans().first()
        assertEquals(setOf("Pull day", "Legs", "Pull A"), all.filter { !it.plan.isArchived }.map { it.plan.name }.toSet())
        val pullA = all.single { it.plan.name == "Pull A" }
        assertEquals(setOf(1, 5), pullA.plan.weekdays)
        assertEquals(listOf("Pull-up", "Muscle up negativo", "Hollow hold"), pullA.exercises.map { it.exercise.name })
        assertEquals("e-pullup", pullA.exercises[0].exercise.id)
        assertEquals(150, pullA.exercises[0].planExercise.restSeconds)
        assertTrue(pullA.exercises[1].exercise.isCustom)
        assertEquals(com.lifehub.app.data.model.TargetType.SECONDS, pullA.exercises[2].exercise.defaultTargetType)
        // New goal: start today, deadline = today + 4 weeks, linked to the new plan.
        val goal = f.goals.observeGoals().first().single { it.goal.name == "Muscle up pulito" }
        assertEquals(f.today, goal.goal.startDate); assertEquals(f.today.plusWeeks(4), goal.goal.deadline); assertEquals(GoalState.ACTIVE, goal.goal.state)
        assertEquals(listOf(pullA.plan.id), goal.links.map { it.planId })
        assertEquals(PlanDraftStatus.APPROVED, f.drafts.get(id)!!.status)
        assertEquals(pullA.plan.id, f.drafts.get(id)!!.approvedPlanIds)

        // A second Approve does nothing.
        val snapshot = userData()
        assertEquals(ApproveResult.NotADraft(PlanDraftStatus.APPROVED), f.drafts.approve(id, true, f.today, f.now))
        assertEquals(snapshot, userData())
    }

    @Test fun approveWithTheCheckboxArchivesOnlyActivePlansOnTheSameWeekdays() = runBlocking {
        val (_, id) = proposeInChat()
        val r = f.drafts.approve(id, archiveOverlapping = true, today = f.today, now = f.now) as ApproveResult.Approved
        assertEquals(listOf("p-pull"), r.archivedPlanIds)   // Mon/Thu overlaps Mon; Legs (Wed) does not; Old plan already archived
        val all = f.plans.observePlans().first().associateBy { it.plan.name }
        assertTrue(all.getValue("Pull day").plan.isArchived)
        assertFalse(all.getValue("Legs").plan.isArchived)
        assertFalse(all.getValue("Pull A").plan.isArchived)
        assertTrue(all.getValue("Old plan").plan.isArchived)
        // Archived, not deleted: its exercises and goal link are still there.
        assertEquals(1, all.getValue("Pull day").exercises.size)
        assertEquals(listOf("p-pull"), f.goals.observeGoals().first().single { it.goal.id == "g-mu" }.links.map { it.planId })
    }

    @Test fun rejectClosesTheDraftAndNothingEverBecomesActive() = runBlocking {
        val before = userData()
        val (_, id) = proposeInChat()
        assertEquals(PlanDraftStatus.REJECTED, f.drafts.reject(id, f.now).status)
        assertEquals(ApproveResult.NotADraft(PlanDraftStatus.REJECTED), f.drafts.approve(id, false, f.today, f.now))
        assertEquals(before, userData())

        // The copilot cannot bring it back: replacing a rejected draft is refused, nothing is saved.
        val r = exec("propose_workout_plan", proposal.dropLast(1) + ""","replaces_draft_id":"$id"}""")
        assertTrue((r.outcome as ToolOutcome.Error).message.contains("cannot be replaced or brought back"))
        assertEquals(1, f.drafts.all().size)
        assertEquals(PlanDraftStatus.REJECTED, f.drafts.get(id)!!.status)
    }

    @Test fun aRevisedDraftSupersedesTheOpenOneAndARetryNeverDuplicates() = runBlocking {
        val first = (exec("propose_workout_plan", proposal).outcome as ToolOutcome.Ok).result.getValue("draft_id").jsonPrimitive.content
        val revised = exec("propose_workout_plan", proposal.dropLast(1) + ""","replaces_draft_id":"$first"}""", captureId = null)
        val second = (revised.outcome as ToolOutcome.Ok).result.getValue("draft_id").jsonPrimitive.content
        assertNotEquals(first, second)
        assertEquals(PlanDraftStatus.SUPERSEDED, f.drafts.get(first)!!.status)
        assertEquals(PlanDraftStatus.DRAFT, f.drafts.get(second)!!.status)

        // Same capture run twice (Retry): one draft only.
        val cap = f.captures.create(null, ChatOrigin.CHAT, "x", f.zone, f.now).id
        val a = (exec("propose_workout_plan", proposal, cap).outcome as ToolOutcome.Ok).result.getValue("draft_id").jsonPrimitive.content
        val b = (exec("propose_workout_plan", proposal, cap).outcome as ToolOutcome.Ok).result
        assertEquals(a, b.getValue("draft_id").jsonPrimitive.content)
        assertTrue(b.getValue("note").jsonPrimitive.content.contains("already saved"))
        assertEquals(1, f.drafts.byCapture(cap).size)
    }

    @Test fun unknownExerciseWithoutCategoryAndBadTargetsAreReadableErrors() = runBlocking {
        val before = userData()
        val r = exec("propose_workout_plan", """{"title":"x","plans":[{"name":"A","weekdays":["monday"],"exercises":[
            {"name":"Front lever raise","sets":3,"target_type":"reps","target_value":5},
            {"name":"Pull-up","sets":3,"target_type":"reps"}]}]}""")
        val msg = (r.outcome as ToolOutcome.Error).message
        assertTrue(msg, msg.contains("no exercise called 'Front lever raise'") && msg.contains("category"))
        assertTrue(msg, msg.contains("target_value' is required"))
        // Schema violations (sets out of range, unknown weekday) are rejected before the tool runs.
        assertTrue(exec("propose_workout_plan", """{"title":"x","plans":[{"name":"A","weekdays":["lunedi"],"exercises":[{"name":"Pull-up","sets":30,"target_type":"max"}]}]}""").outcome is ToolOutcome.Error)
        assertTrue(f.drafts.all().isEmpty())
        assertEquals(before, userData())
        // An unknown goal id is refused too.
        assertTrue(exec("propose_workout_plan", """{"title":"x","goal":{"goal_id":"nope"},"plans":[{"name":"A","weekdays":["monday"],"exercises":[{"name":"Pull-up","sets":3,"target_type":"max"}]}]}""").outcome is ToolOutcome.Error)
        // An existing goal is linked on Approve instead of creating one.
        val ok = exec("propose_workout_plan", """{"title":"x","goal":{"goal_id":"g-mu"},"plans":[{"name":"A","weekdays":["saturday"],"exercises":[{"name":"Pull-up","sets":3,"target_type":"max"}]}]}""")
        val id = (ok.outcome as ToolOutcome.Ok).result.getValue("draft_id").jsonPrimitive.content
        val ap = f.drafts.approve(id, false, f.today, f.now) as ApproveResult.Approved
        assertEquals("g-mu", ap.goalId)
        assertEquals(2, f.goals.observeGoals().first().single { it.goal.id == "g-mu" }.links.size)
        assertNull(f.plans.get(ap.planIds.single())!!.exercises.single().planExercise.targetValue)
    }

    @Test fun deleteRemovesTheDraftButNotApprovedPlans() = runBlocking {
        val (_, id) = proposeInChat()
        val ap = f.drafts.approve(id, false, f.today, f.now) as ApproveResult.Approved
        f.drafts.delete(id)
        assertNull(f.drafts.get(id))
        assertEquals("Pull A", f.plans.get(ap.planIds.single())!!.plan.name)
    }
}
