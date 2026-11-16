package com.lifehub.app.di

import android.content.Context
import com.lifehub.app.core.time.SystemTimeSource
import com.lifehub.app.core.time.TimeSource
import com.lifehub.app.data.db.LifeHubDatabase
import com.lifehub.app.copilot.agent.AgentLoop
import com.lifehub.app.copilot.agent.ToolRegistry
import com.lifehub.app.copilot.llm.ConnectionTester
import com.lifehub.app.copilot.tools.ReadTools
import com.lifehub.app.copilot.tools.FoodTools
import com.lifehub.app.copilot.tools.FoodRefLookup
import com.lifehub.app.copilot.capture.CaptureProcessor
import com.lifehub.app.copilot.capture.CaptureStore
import com.lifehub.app.copilot.capture.CaptureStoreImpl
import com.lifehub.app.copilot.capture.FoodLogWriter
import com.lifehub.app.copilot.capture.PendingInputStore
import com.lifehub.app.copilot.capture.PendingInputStoreImpl
import com.lifehub.app.copilot.food.FoodCatalog
import com.lifehub.app.copilot.food.FoodResolver
import com.lifehub.app.copilot.food.OffCandidateCache
import com.lifehub.app.copilot.food.RoomFoodCatalog
import com.lifehub.app.data.off.OffProductSearch
import com.lifehub.app.data.off.SearchaliciousClient
import com.lifehub.app.copilot.llm.LlmClient
import com.lifehub.app.copilot.llm.LlmConfig
import com.lifehub.app.copilot.llm.OpenAiCompatibleLlmClient
import com.lifehub.app.copilot.llm.UrlConnectionTransport
import com.lifehub.app.copilot.secrets.EncryptedFileSecretStore
import com.lifehub.app.copilot.secrets.SecretNames
import com.lifehub.app.copilot.secrets.SecretStore
import com.lifehub.app.data.prefs.CopilotSettingsRepository
import com.lifehub.app.data.prefs.SettingsRepository
import com.lifehub.app.data.repo.*
import com.lifehub.app.copilot.capture.CaptureRunner
import com.lifehub.app.copilot.capture.VoiceCaptures
import com.lifehub.app.copilot.capture.CaptureScheduler
import com.lifehub.app.copilot.capture.WorkManagerCaptureScheduler
import com.lifehub.app.copilot.notify.AndroidNotificationSink
import com.lifehub.app.copilot.notify.CopilotActions
import com.lifehub.app.copilot.notify.NotificationCenter
import com.lifehub.app.copilot.stt.CaptureAudioFiles
import com.lifehub.app.copilot.stt.MediaVoiceRecorder
import com.lifehub.app.copilot.stt.SttConfig
import com.lifehub.app.copilot.stt.Transcriber
import com.lifehub.app.copilot.stt.VoiceRecorder
import com.lifehub.app.copilot.stt.WhisperApiTranscriber
import com.lifehub.app.data.model.CaptureStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(context: Context, val time: TimeSource = SystemTimeSource()) {
    val appContext: Context = context.applicationContext
    val db: LifeHubDatabase = LifeHubDatabase.build(appContext)
    val settings = SettingsRepository(appContext)
    val goals: GoalRepository = GoalRepositoryImpl(db)
    val plans: PlanRepository = PlanRepositoryImpl(db)
    val exercises: ExerciseRepository = ExerciseRepositoryImpl(db)
    val sessions: SessionRepository = SessionRepositoryImpl(db)
    val quickAdds: QuickAddRepository = QuickAddRepositoryImpl(db)
    val food: FoodRepository = FoodRepositoryImpl(db)
    val off: com.lifehub.app.data.off.OffApi = com.lifehub.app.data.off.OpenFoodFactsClient()
    val body: BodyRepository = BodyRepositoryImpl(db)
    val achievements: AchievementRepository = AchievementRepositoryImpl(db, settings)
    val backup: BackupRepository = BackupRepositoryImpl(db, settings)
    val copilotSettings = CopilotSettingsRepository(appContext)
    /** Encrypted API keys (Keystore + noBackupFilesDir). Never enters Settings, the export or logs. */
    val secrets: SecretStore = EncryptedFileSecretStore.create(appContext)
    val llm: LlmClient = OpenAiCompatibleLlmClient(
        config = { copilotSettings.current().let { LlmConfig(it.llmBaseUrl, it.llmModel, secrets.get(SecretNames.LLM_API_KEY)) } },
        transport = UrlConnectionTransport(),
    )
    val llmConnectionTester = ConnectionTester(llm) { copilotSettings.current().llmModel }
    val chat: ChatStore = ChatStoreImpl(db)
    val baseFoods = BaseFoodRepository(db) { appContext.assets.open("copilot/base_foods.json").bufferedReader().use { it.readText() } }
    val measures = HouseholdMeasureRepository(db)
    val foodCatalog: FoodCatalog = RoomFoodCatalog(db, baseFoods)
    val offSearch: OffProductSearch = SearchaliciousClient()
    val offCandidates = OffCandidateCache()
    val foodResolver = FoodResolver(foodCatalog, offSearch, offCandidates)
    val captures: CaptureStore = CaptureStoreImpl(db)
    /** Open requests to the user; independent from the chat UI (inbox and notifications use it in phase 4). */
    val pendingInputs: PendingInputStore = PendingInputStoreImpl(db)
    val foodLog = FoodLogWriter(db)
    val foodRefs = FoodRefLookup(foodCatalog, offCandidates, pendingInputs)
    /** All copilot tools. Phases 5-6 append their write / draft tools here. */
    val copilotTools = ToolRegistry(
        ReadTools.all(food, { settings.current() }, goals, plans, sessions, body) +
            FoodTools.all(foodResolver, foodRefs, foodCatalog, foodLog, captures, pendingInputs, food) { settings.current() },
    )
    val agent = AgentLoop(llm, copilotTools, chat, time)

    // Phase 4: voice, background worker, notifications.
    val transcriber: Transcriber = WhisperApiTranscriber(
        config = { copilotSettings.current().let { SttConfig(it.sttBaseUrl, it.sttModel, secrets.get(SecretNames.STT_API_KEY)) } },
        transport = UrlConnectionTransport(),
    )
    val captureAudio = CaptureAudioFiles.forContext(appContext)
    fun newVoiceRecorder(): VoiceRecorder = MediaVoiceRecorder(appContext)
    val notificationCenter = NotificationCenter(
        captures, pendingInputs, { id -> db.foodEntryDao().byCapture(id) }, AndroidNotificationSink(appContext),
        { copilotSettings.current().notificationsEnabled }, time,
    )
    val captureProcessor = CaptureProcessor(captures, pendingInputs, foodLog, agent, chat, time, notificationCenter)
    val captureRunner = CaptureRunner(captures, chat, captureProcessor, transcriber, { copilotSettings.current().sttLanguage }, time, notificationCenter)
    val captureScheduler: CaptureScheduler = WorkManagerCaptureScheduler(appContext)
    val voiceCaptures = VoiceCaptures(chat, captures, captureScheduler, time)
    /** Inbox and notification actions (quick DB changes; the model turn is queued for the CaptureWorker). */
    val copilotActions = CopilotActions(captureProcessor, captureRunner, captureScheduler, notificationCenter)
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Captures left RECORDED/PROCESSING by an earlier process (e.g. the chat was closed mid-turn) are queued again; work
     * WorkManager already holds is chained, and a run with nothing left to do is a no-op.
     */
    fun recoverCaptures() {
        val startedAt = time.now()
        appScope.launch {
            runCatching {
                captures.byStatus(listOf(CaptureStatus.RECORDED, CaptureStatus.PROCESSING))
                    .filter { it.updatedAt < startedAt }
                    .forEach { captureScheduler.enqueue(it.id) }
            }
        }
    }

    init {
        // Copy the bundled base food table into the DB in the background (no-op when already identical).
        appScope.launch { runCatching { baseFoods.ensureSeeded() } }
    }
}
