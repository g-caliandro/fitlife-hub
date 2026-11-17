package com.lifehub.app.copilot

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lifehub.app.copilot.agent.AgentLoop
import com.lifehub.app.copilot.agent.ToolRegistry
import com.lifehub.app.copilot.capture.CaptureProcessor
import com.lifehub.app.copilot.capture.CaptureStoreImpl
import com.lifehub.app.copilot.capture.FoodLogWriter
import com.lifehub.app.copilot.capture.PendingInputStoreImpl
import com.lifehub.app.copilot.food.FoodResolver
import com.lifehub.app.copilot.food.OffCandidateCache
import com.lifehub.app.copilot.food.RoomFoodCatalog
import com.lifehub.app.copilot.tools.FoodRefLookup
import com.lifehub.app.copilot.tools.FoodTools
import com.lifehub.app.data.off.OffApi
import com.lifehub.app.data.off.OffBarcodeSource
import com.lifehub.app.data.off.OffProduct
import com.lifehub.app.copilot.capture.AttachedProductContext
import com.lifehub.app.copilot.capture.BodyLogWriter
import com.lifehub.app.copilot.tools.BodyTools
import com.lifehub.app.data.off.OffProductSearch
import com.lifehub.app.data.off.OffResult
import com.lifehub.app.copilot.llm.LlmClient
import com.lifehub.app.copilot.llm.LlmMessage
import com.lifehub.app.copilot.llm.LlmReply
import com.lifehub.app.copilot.llm.LlmResult
import com.lifehub.app.copilot.llm.LlmTool
import com.lifehub.app.copilot.llm.LlmToolCall
import com.lifehub.app.copilot.llm.LlmToolChoice
import com.lifehub.app.copilot.tools.ReadTools
import com.lifehub.app.core.time.FixedTimeSource
import com.lifehub.app.data.db.LifeHubDatabase
import com.lifehub.app.data.model.*
import com.lifehub.app.data.prefs.SettingsRepository
import com.lifehub.app.data.repo.*
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * In-memory DB + real repositories + the phase 2 read tools, seeded with a small realistic data set.
 * Today = Monday 2026-11-16 (Europe/Rome). Call [close] in @After.
 */
class CopilotFixture(seed: Boolean = true) {
    val ctx: Context = ApplicationProvider.getApplicationContext()
    val zone: ZoneId = ZoneId.of("Europe/Rome")
    val today: LocalDate = LocalDate.of(2026, 11, 16)
    val now: Instant = Instant.parse("2026-11-16T13:05:00Z")
    val time = FixedTimeSource(now, zone)
    val db = LifeHubDatabase.inMemory(ctx)
    val settings = SettingsRepository(ctx).also { it.clear() }
    val food = FoodRepositoryImpl(db)
    val goals = GoalRepositoryImpl(db)
    val plans = PlanRepositoryImpl(db)
    val sessions = SessionRepositoryImpl(db)
    val body = BodyRepositoryImpl(db)
    val chat = ChatStoreImpl(db)
    val registry = ToolRegistry(ReadTools.all(food, { settings.current() }, goals, plans, sessions, body))

    // Phase 3: food resolution, captures, pending requests, meal log writer (real implementations, fake OFF).
    val baseFoods = BaseFoodRepository(db) { java.io.File("src/main/assets/copilot/base_foods.json").readText() }
    val catalog = RoomFoodCatalog(db, baseFoods)
    val off = FakeOff()
    val offCache = OffCandidateCache()
    val resolver = FoodResolver(catalog, off, offCache)
    val captures = CaptureStoreImpl(db)
    val pending = PendingInputStoreImpl(db)
    val writer = FoodLogWriter(db)
    val refs = FoodRefLookup(catalog, offCache, pending)
    // Phase 5: barcode lookup (fake Open Food Facts) and body weight.
    val offApi = FakeOffApi()
    val offBarcodes = FakeOffBarcodes()
    val barcodes = BarcodeLookup(food, offApi, offBarcodes, db, time)
    val bodyLog = BodyLogWriter(db)
    // Phase 6: plan drafts, user memory, meal suggestions.
    val drafts = com.lifehub.app.copilot.plan.PlanDraftStoreImpl(db)
    val memory = com.lifehub.app.copilot.memory.UserMemoryStoreImpl(db)
    val suggestions = com.lifehub.app.copilot.tools.MealSuggestions(captures, writer, time)
    /** Every copilot tool, as in AppContainer. */
    val fullRegistry = ToolRegistry(
        ReadTools.all(food, { settings.current() }, goals, plans, sessions, body, drafts) +
            FoodTools.all(resolver, refs, catalog, writer, captures, pending, food, barcodes, offCache) { settings.current() } +
            BodyTools.all(bodyLog, captures) +
            com.lifehub.app.copilot.tools.PlanTools.all(drafts, goals) { db.exerciseDao().findByName(it) } +
            listOf(
                com.lifehub.app.copilot.tools.SuggestMealTool(refs, food, memory) { settings.current() },
                com.lifehub.app.copilot.tools.SaveUserMemoryTool(memory),
            ),
    )
    /** The agent as in AppContainer (user memory injected in every request). */
    fun agent(llm: LlmClient) = AgentLoop(llm, fullRegistry, chat, time, contextProviders = listOf(com.lifehub.app.copilot.memory.MemoryContext(memory)))
    fun processor(llm: LlmClient, deleteAudio: (String) -> Unit = { runCatching { java.io.File(it).delete() } }) =
        CaptureProcessor(captures, pending, writer, agent(llm), chat, time, attached = AttachedProductContext(catalog), deleteAudio = deleteAudio)

    init { if (seed) runBlocking { seed() } }

    private suspend fun seed() {
        settings.setProteinTarget(150); settings.setKcalTarget(2200); settings.setFatTarget(70); settings.setCarbsTarget(null)
        val chicken = Food("f-chicken", FoodSource.CUSTOM, null, "Petto di pollo", null, 110.0, 23.3, 0.0, 1.6, null, false, null)
        val rice = Food("f-rice", FoodSource.CUSTOM, null, "Riso", null, 360.0, 7.0, 79.0, 0.6, null, false, null)
        food.addEntry(chicken, today, 150.0, now.minusSeconds(3600))
        food.addEntry(rice, today, 80.0, now.minusSeconds(3000))
        db.quickAddDao().upsert(QuickAddEntry("q-yogurt", today, "Yogurt", 120, 10, 12, 3, false, now.minusSeconds(2000)))
        // Yesterday: one item with unknown protein -> protein "partial".
        db.quickAddDao().upsert(QuickAddEntry("q-pizza", today.minusDays(1), "Pizza", 800, null, null, null, false, now.minusSeconds(90_000)))
        food.addEntry(rice, today.minusDays(1), 100.0, now.minusSeconds(80_000))

        db.exerciseDao().insertAll(listOf(
            Exercise("e-pullup", "Pull-up", ExerciseCategory.PULL, TargetType.REPS, true, false),
            Exercise("e-squat", "Squat", ExerciseCategory.LEGS, TargetType.REPS, true, false),
        ))
        val created = Instant.parse("2026-10-01T08:00:00Z")
        plans.save(Plan("p-pull", "Pull day", setOf(1, 4), null, false, created, DayType.PULL),
            listOf(PlanExercise("pe1", "p-pull", "e-pullup", 0, 4, TargetType.REPS, 8, null, 120)), emptyList())
        plans.save(Plan("p-legs", "Legs", setOf(3), null, false, created, DayType.LEGS),
            listOf(PlanExercise("pe2", "p-legs", "e-squat", 0, 3, TargetType.REPS, 10, 60.0, 90)), emptyList())
        plans.save(Plan("p-old", "Old plan", setOf(1), null, true, created), emptyList(), emptyList())
        goals.save(Goal("g-mu", "Muscle up", "Strict", LocalDate.of(2026, 10, 1), today.plusDays(84), false, GoalState.ACTIVE, null, created))
        goals.upsertLink(GoalPlanLink("g-mu", "p-pull", 24))
        goals.save(Goal("g-10", "10 pull-ups", null, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 30), false, GoalState.ACHIEVED, LocalDate.of(2026, 10, 20), created))
        db.sessionDao().upsert(WorkoutSession("s1", "p-pull", "Pull day", today, now.minusSeconds(20_000), now.minusSeconds(17_000), 3000,
            SessionStatus.COMPLETED, 7, null, false, 0, null))

        body.upsert(BodyEntry(LocalDate.of(2026, 11, 10), 78.0, null, null, now))
        body.upsert(BodyEntry(LocalDate.of(2026, 11, 15), 77.4, 35.0, null, now))
    }

    fun close() = db.close()
}

/** Scripted [LlmClient]: returns [script] replies in order and records every request. */
class FakeLlmClient(vararg script: LlmResult) : LlmClient {
    data class Request(val messages: List<LlmMessage>, val tools: List<LlmTool>)
    private val queue = ArrayDeque(script.toList())
    val requests = mutableListOf<Request>()
    var fallback: (() -> LlmResult)? = null

    override suspend fun chat(messages: List<LlmMessage>, tools: List<LlmTool>, toolChoice: LlmToolChoice, maxTokens: Int?): LlmResult {
        requests += Request(messages, tools)
        return queue.removeFirstOrNull() ?: fallback?.invoke() ?: error("FakeLlmClient: script exhausted")
    }

    companion object {
        fun text(t: String) = LlmResult.Ok(LlmReply(LlmMessage.Assistant(t), "stop"))
        fun tools(vararg calls: LlmToolCall, text: String? = null) = LlmResult.Ok(LlmReply(LlmMessage.Assistant(text, calls.toList()), "tool_calls"))
        fun call(id: String, name: String, args: String = "{}") = LlmToolCall(id, name, args)
    }
}

/** Open Food Facts search stand-in: returns [products] (or an error) and records the queries. No network. */
class FakeOff(var products: List<OffProduct> = emptyList(), var fail: Boolean = false) : OffProductSearch {
    val queries = mutableListOf<String>()
    override suspend fun search(query: String): OffResult<List<OffProduct>> {
        queries += query
        return if (fail) OffResult.Error("offline") else OffResult.Ok(products)
    }
}

/** Legacy OFF API stand-in (scanner flow): [byCode] answers barcode lookups; records the codes asked. */
class FakeOffApi(var byCode: Map<String, Food> = emptyMap(), var fail: Boolean = false) : OffApi {
    val codes = mutableListOf<String>()
    override suspend fun search(query: String): OffResult<List<Food>> = OffResult.Ok(emptyList())
    override suspend fun byBarcode(code: String): OffResult<Food?> {
        codes += code
        return if (fail) OffResult.Error("offline") else OffResult.Ok(byCode[code])
    }
}

/** Copilot barcode source stand-in: [products] by code (absent = unknown barcode). */
class FakeOffBarcodes(var products: Map<String, OffProduct> = emptyMap(), var fail: Boolean = false) : OffBarcodeSource {
    val codes = mutableListOf<String>()
    override suspend fun product(code: String): OffResult<OffProduct?> {
        codes += code
        return if (fail) OffResult.Error("offline") else OffResult.Ok(products[code])
    }
}

/**
 * Scripted model whose replies are computed from the request it receives (e.g. to reuse the refs that resolve_food
 * returned), one step per call. Records every request.
 */
class StepLlmClient(vararg steps: (List<LlmMessage>) -> LlmResult) : LlmClient {
    private val queue = ArrayDeque(steps.toList())
    val requests = mutableListOf<List<LlmMessage>>()
    /** Names of the tools offered on each call. */
    val toolNames = mutableListOf<List<String>>()
    override suspend fun chat(messages: List<LlmMessage>, tools: List<LlmTool>, toolChoice: LlmToolChoice, maxTokens: Int?): LlmResult {
        requests += messages
        toolNames += tools.map { it.name }
        val step = queue.removeFirstOrNull() ?: error("StepLlmClient: script exhausted")
        return step(messages)
    }
    fun add(step: (List<LlmMessage>) -> LlmResult) { queue.addLast(step) }
    val remaining: Int get() = queue.size
}

/** Every user table as sorted row strings (exact REAL values), for "DB identical after Undo" checks. */
fun dumpTables(db: LifeHubDatabase, exclude: Set<String> = COPILOT_BOOKKEEPING): Map<String, List<String>> {
    val sdb = db.openHelper.readableDatabase
    val tables = sdb.query("SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name").use { c ->
        buildList { while (c.moveToNext()) add(c.getString(0)) }
    }.filter { it !in exclude && !it.startsWith("sqlite_") && it != "room_master_table" && it != "android_metadata" }
    return tables.associateWith { t ->
        sdb.query("SELECT * FROM `$t`").use { c ->
            buildList {
                while (c.moveToNext()) add((0 until c.columnCount).joinToString("|") { i ->
                    when (c.getType(i)) {
                        android.database.Cursor.FIELD_TYPE_NULL -> "NULL"
                        android.database.Cursor.FIELD_TYPE_FLOAT -> java.lang.Double.toString(c.getDouble(i))
                        android.database.Cursor.FIELD_TYPE_INTEGER -> c.getLong(i).toString()
                        else -> c.getString(i)
                    }
                })
            }.sorted()
        }
    }
}

/** Copilot bookkeeping, not user data: excluded from the Undo identity comparison (owner decision 8 scope). */
val COPILOT_BOOKKEEPING = setOf("captures", "capture_writes", "pending_inputs", "chat_threads", "chat_messages")
