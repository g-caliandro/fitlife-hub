package com.lifehub.app.feature.copilot

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.createSavedStateHandle
import com.lifehub.app.copilot.agent.AgentLoop
import com.lifehub.app.copilot.agent.CardCodec
import com.lifehub.app.copilot.agent.ChatCard
import com.lifehub.app.copilot.agent.TurnError
import com.lifehub.app.copilot.agent.TurnResult
import com.lifehub.app.copilot.llm.LlmError
import com.lifehub.app.copilot.secrets.SecretNames
import com.lifehub.app.copilot.capture.CaptureProcessor
import com.lifehub.app.copilot.capture.CaptureStore
import com.lifehub.app.copilot.capture.IllegalTransitionException
import com.lifehub.app.copilot.capture.PendingInputStore
import com.lifehub.app.data.model.CaptureStatus
import com.lifehub.app.data.model.PendingInput
import com.lifehub.app.data.model.PendingStatus
import com.lifehub.app.core.time.TimeSource
import com.lifehub.app.data.model.ChatMessage
import com.lifehub.app.data.model.ChatOrigin
import com.lifehub.app.data.model.ChatRole
import com.lifehub.app.data.repo.ChatStore
import com.lifehub.app.di.AppContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import com.lifehub.app.copilot.capture.CaptureRunner
import com.lifehub.app.copilot.capture.CaptureStage
import com.lifehub.app.copilot.capture.VoiceCaptures
import com.lifehub.app.copilot.stt.RecordingLevel
import com.lifehub.app.copilot.stt.RecordingSession
import java.time.LocalDate

/** One row of the chat list. Tool calls without a card and empty assistant messages are not shown. */
sealed interface ChatItem {
    val key: String
    /** [attachment] = name of the scanned product attached to this message (design doc §8), shown under the text. */
    data class User(override val key: String, val text: String, val attachment: String? = null) : ChatItem
    data class Assistant(override val key: String, val text: String) : ChatItem
    data class Card(override val key: String, val card: ChatCard) : ChatItem
}

sealed interface ChatProblem {
    /** No LLM key saved: composer disabled, link to Copilot settings. */
    data object MissingKey : ChatProblem
    /** The last turn failed ([message] is user-readable); [retryable] shows a Retry button. */
    data class Failed(val message: String, val retryable: Boolean, val network: Boolean) : ChatProblem
}

/** A product scanned in the composer, waiting to be sent with the next message. */
data class ChatAttachment(val foodId: String, val label: String, val incomplete: Boolean = false)

data class ChatUiState(
    val today: LocalDate,
    val items: List<ChatItem> = emptyList(),
    val working: Boolean = false,
    /** What the working row says: transcribing a voice message, or the model thinking. */
    val stage: CaptureStage = CaptureStage.THINKING,
    /** Non-null while the mic records (tap the mic to start, tap stop to send). */
    val recording: RecordingLevel? = null,
    /** Inbox badge: open requests + failed captures. */
    val inboxCount: Int = 0,
    val problem: ChatProblem? = null,
    /** Live state of the pending requests shown as cards (answered elsewhere = closed here too). */
    val pending: Map<String, PendingInput> = emptyMap(),
    /** Captures the user undid: their "Meal logged" cards show "Undone". */
    val undoneCaptures: Set<String> = emptySet(),
    /** The product attached to the message being composed (null = none). */
    val attachment: ChatAttachment? = null,
    /** Live state of the plan drafts shown as cards (approved / rejected elsewhere = updated here too). */
    val drafts: Map<String, DraftLive> = emptyMap(),
    /** Status of this thread's captures by id (meal suggestion cards: logged / undone / failed). */
    val captureStatus: Map<String, CaptureStatus> = emptyMap(),
)

/** Messages of a thread -> visible rows (user text, assistant text, native cards from tool results). */
fun chatItems(messages: List<ChatMessage>, attachments: Map<String, String> = emptyMap()): List<ChatItem> = messages.sortedBy { it.seq }.mapNotNull { m ->
    when (m.role) {
        ChatRole.USER -> m.text?.let { ChatItem.User(m.id, it, m.captureId?.let(attachments::get)) }
        ChatRole.ASSISTANT -> m.text?.takeIf { it.isNotBlank() }?.let { ChatItem.Assistant(m.id, it) }
        ChatRole.TOOL -> CardCodec.decode(m.cardType, m.cardJson)?.let { ChatItem.Card(m.id, it) }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val chat: ChatStore,
    private val agent: AgentLoop,
    private val processor: CaptureProcessor,
    private val captures: CaptureStore,
    private val pendingStore: PendingInputStore,
    private val hasKey: () -> Boolean,
    private val time: TimeSource,
    requestedThreadId: String?,
    private val voice: VoiceCaptures? = null,
    private val runner: CaptureRunner? = null,
    private val newSession: ((onAutoStop: () -> Unit) -> RecordingSession)? = null,
    inboxCount: Flow<Int> = flowOf(0),
    /** Name of an attached product by food id (null when it no longer exists). */
    private val attachmentLoader: suspend (String) -> ChatAttachment? = { null },
    /** Scanner results (food ids) for this chat; [onPickConsumed] clears the one-shot after it is taken. */
    picks: Flow<String?> = flowOf(null),
    private val onPickConsumed: () -> Unit = {},
    /** Phase 6: plan drafts (card status, Approve / Reject) and the active plans an Approve may archive. */
    private val drafts: com.lifehub.app.copilot.plan.PlanDraftStore? = null,
    plans: com.lifehub.app.data.repo.PlanRepository? = null,
    private val suggestions: com.lifehub.app.copilot.tools.MealSuggestions? = null,
    /** Thread chosen in the conversations list (a thread id, or [NEW_THREAD]); [onThreadPickConsumed] clears it. */
    threadPicks: Flow<String?> = flowOf(null),
    private val onThreadPickConsumed: () -> Unit = {},
) : ViewModel() {
    private val attachment = MutableStateFlow<ChatAttachment?>(null)
    private val threadId = MutableStateFlow(requestedThreadId?.takeIf { it != NEW_THREAD })
    private val working = MutableStateFlow(false)
    private val stage = MutableStateFlow(CaptureStage.THINKING)
    private val recording = MutableStateFlow<RecordingLevel?>(null)
    private var session: RecordingSession? = null
    private val problem = MutableStateFlow<ChatProblem?>(if (hasKey()) null else ChatProblem.MissingKey)

    init {
        // No thread asked: reopen the most recent chat thread. NEW_THREAD: start empty (created on the first message).
        if (requestedThreadId == null) viewModelScope.launch { chat.latestThread(ChatOrigin.CHAT)?.let { if (threadId.value == null) threadId.value = it.id } }
        viewModelScope.launch {
            threadPicks.collect { id ->
                if (id == null) return@collect
                if (!working.value) {
                    attachment.value = null
                    problem.value = if (hasKey()) null else ChatProblem.MissingKey
                    threadId.value = id.takeIf { it != NEW_THREAD }
                }
                onThreadPickConsumed()
            }
        }
        // The open thread was deleted from the conversations list: back to an empty chat (a new thread on the next message).
        viewModelScope.launch {
            chat.observeThreads().collect { list ->
                val id = threadId.value ?: return@collect
                if (list.none { it.id == id } && chat.thread(id) == null) threadId.value = null
            }
        }
        viewModelScope.launch {
            picks.collect { id ->
                if (id == null) return@collect
                attachment.value = attachmentLoader(id)
                onPickConsumed()
            }
        }
    }

    fun removeAttachment() { attachment.value = null }

    /** Starts an empty conversation (the thread is created on the first message); the old one stays in the conversations list. */
    fun newChat() {
        if (working.value || session != null) return
        attachment.value = null
        problem.value = if (hasKey()) null else ChatProblem.MissingKey
        threadId.value = null
    }

    val state: StateFlow<ChatUiState> = combine(
        threadId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else chat.observeMessages(id) },
        threadId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else pendingStore.observeByThread(id) },
        threadId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else captures.observeByThread(id) },
        working, problem,
    ) { msgs, pend, caps, w, p ->
        val labels = caps.mapNotNull { c -> c.attachedFoodId?.let { id -> attachmentLoader(id)?.let { c.id to it.label } } }.toMap()
        ChatUiState(time.today(), items = chatItems(msgs, labels), working = w, problem = p, pending = pend.associateBy { it.id }, undoneCaptures = caps.filter { it.status == CaptureStatus.CANCELLED }.map { it.id }.toSet())
    }
        .combine(combine(stage, recording, inboxCount, ::Triple)) { s, (st, rec, inbox) -> s.copy(stage = st, recording = rec, inboxCount = inbox) }
        .combine(attachment) { s, a -> s.copy(attachment = a) }
        .combine(draftsLive(plans)) { s, d -> s.copy(drafts = d) }
        .combine(threadId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else captures.observeByThread(id) }) { s, caps ->
            s.copy(captureStatus = caps.associate { it.id to it.status })
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState(time.today(), problem = problem.value))

    private fun draftsLive(plans: com.lifehub.app.data.repo.PlanRepository?): Flow<Map<String, DraftLive>> {
        val store = drafts ?: return flowOf(emptyMap())
        val active = plans?.observePlans() ?: flowOf(emptyList())
        return combine(store.observeAll(), active) { ds, ps ->
            ds.associate { d ->
                d.id to DraftLive(d.status, com.lifehub.app.copilot.plan.overlappingPlans(com.lifehub.app.copilot.plan.DraftCodec.decode(d), ps).map { it.plan.name })
            }
        }
    }

    /** Approve confirmed on a plan draft card ([archiveOverlapping] = the opt-in checkbox, owner decision 4). */
    fun approveDraft(draftId: String, archiveOverlapping: Boolean) {
        val store = drafts ?: return
        viewModelScope.launch { store.approve(draftId, archiveOverlapping, time.today(), time.now()) }
    }

    fun rejectDraft(draftId: String) {
        val store = drafts ?: return
        viewModelScope.launch { try { store.reject(draftId, time.now()) } catch (_: IllegalTransitionException) {} }
    }

    /** "Log as meal" on a suggestion card: writes it now through a capture of this thread (Undo on the card). */
    fun logSuggestion(card: com.lifehub.app.copilot.agent.MealSuggestionCard) {
        val s = suggestions ?: return
        if (working.value) return
        viewModelScope.launch { s.logAsMeal(card, threadId.value) }
    }

    /** Re-checks the key (e.g. back from Copilot settings). */
    fun refreshKey() {
        val has = hasKey()
        if (!has) problem.value = ChatProblem.MissingKey
        else if (problem.value == ChatProblem.MissingKey) problem.value = null
    }

    fun send(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty() || working.value) return false
        if (!hasKey()) { problem.value = ChatProblem.MissingKey; return false }
        val attached = attachment.value?.foodId
        attachment.value = null
        turn {
            // An open request of this thread is waiting for exactly this: the typed text answers it and resumes its capture
            // (same meal; a new capture would not know what was already logged and would log it again).
            val open = if (attached == null) threadId.value?.let { id -> pendingStore.observeByThread(id).first().lastOrNull { it.status == PendingStatus.OPEN } } else null
            if (open != null) {
                return@turn try { processor.answer(open.id, text = t)?.turn ?: TurnResult.Answered("") }
                catch (_: IllegalTransitionException) { TurnResult.Answered("") }
            }
            // Title = the first message, cheaply (no model call).
            val id = threadId.value ?: chat.createThread(CaptureRunner.threadTitle(t), ChatOrigin.CHAT, time.now()).id.also { threadId.value = it }
            // Every chat message that can write goes through a capture (idempotent writes, Undo, pending requests).
            processor.submitText(id, t, attachedFoodId = attached).turn
        }
        return true
    }

    /** Re-runs the failed turn from the stored thread (the user message is already saved). */
    fun retry() {
        val id = threadId.value ?: return
        if (working.value) return
        if (!hasKey()) { problem.value = ChatProblem.MissingKey; return }
        launchTurn {
            // A voice message that failed before its transcript existed has no USER message yet: retry its capture.
            val failedVoice = captures.observeByThread(id).first().lastOrNull { it.status == CaptureStatus.FAILED && it.inputText == null }
            if (failedVoice != null && runner != null && runner.prepareRetry(failedVoice.id)) return@launchTurn runVoice(failedVoice.id)
            val captureId = chat.messages(id).lastOrNull { it.role == ChatRole.USER }?.captureId
            (captureId?.let { processor.retry(it)?.turn } ?: agent.run(id)).problem()
        }
    }

    /** Mic tap: starts recording (the screen has checked the microphone permission). */
    fun startRecording(): Boolean {
        if (working.value || session != null || newSession == null) return false
        if (!hasKey()) { problem.value = ChatProblem.MissingKey; return false }
        val s = newSession.invoke { stopRecording() }
        if (!s.start()) { problem.value = ChatProblem.Failed("The microphone is not available right now.", retryable = false, network = false); return false }
        session = s
        problem.value = null
        viewModelScope.launch { s.level.collect { l -> if (session === s) recording.value = l } }
        return true
    }

    /** Second mic tap (or a long pause): the audio is saved as a capture of this thread, then transcribed and sent. */
    fun stopRecording() {
        val s = session ?: return
        session = null
        recording.value = null
        val file = s.stop() ?: return
        val v = voice ?: return
        val attached = attachment.value?.foodId
        attachment.value = null
        launchTurn {
            val id = threadId.value ?: chat.createThread(VOICE_TITLE, ChatOrigin.CHAT, time.now()).id.also { threadId.value = it }
            runVoice(v.saveInThread(id, file, attached).id)
        }
    }

    fun cancelRecording() { session?.cancel(); session = null; recording.value = null }

    override fun onCleared() { session?.cancel(); session = null }

    /** Transcribe ("Transcribing…") then the model turn ("Thinking…"); no notification, the user is in the chat. */
    private suspend fun runVoice(captureId: String): ChatProblem? {
        val r = runner ?: return null
        val report = r.run(captureId, notify = false) { stage.value = it }
        report.turn?.let { return it.problem() }
        if (report.status != CaptureStatus.FAILED) return null
        val c = captures.get(captureId)
        return ChatProblem.Failed(c?.errorMessage ?: "Couldn't transcribe the voice message.", retryable = true,
            network = c?.errorCode?.contains("Network") == true)
    }

    /** "Choose" ([option]) or "Reply" ([text]) on a pending request card: closes it and resumes its capture. */
    fun answer(pendingId: String, option: Int? = null, text: String? = null) {
        if (working.value) return
        if (!hasKey()) { problem.value = ChatProblem.MissingKey; return }
        turn {
            try { processor.answer(pendingId, option, text)?.turn ?: TurnResult.Answered("") }
            catch (_: IllegalTransitionException) { TurnResult.Answered("") }  // answered/discarded meanwhile
        }
    }

    /** "Discard" on a request card: drops that item; when it was the last open question the rest of the meal is logged. */
    fun discard(pendingId: String) {
        if (working.value || !hasKey()) {
            viewModelScope.launch { try { processor.discard(pendingId) } catch (_: IllegalTransitionException) {} }
            return
        }
        turn {
            try { processor.discardAndResume(pendingId)?.turn ?: TurnResult.Answered("") }
            catch (_: IllegalTransitionException) { TurnResult.Answered("") }  // answered/discarded meanwhile
        }
    }

    /** Undo on a "Meal logged" card: removes exactly what that capture wrote. */
    fun undo(captureId: String) {
        viewModelScope.launch { processor.undo(captureId) }
    }

    private fun turn(block: suspend () -> TurnResult) = launchTurn { block().problem() }

    private fun launchTurn(block: suspend () -> ChatProblem?) {
        working.value = true
        stage.value = CaptureStage.THINKING
        problem.value = null
        viewModelScope.launch {
            val p = try { block() } finally { working.value = false; stage.value = CaptureStage.THINKING }
            if (p != null) problem.value = p
        }
    }

    private fun TurnResult.problem(): ChatProblem? = (this as? TurnResult.Failed)?.error?.toProblem()

    private fun TurnError.toProblem(): ChatProblem = when {
        this is TurnError.Llm && error == LlmError.MissingKey -> ChatProblem.MissingKey
        this is TurnError.Llm -> ChatProblem.Failed(message, retryable = true, network = error is LlmError.Network)
        this == TurnError.Timeout -> ChatProblem.Failed(message, retryable = true, network = true)
        else -> ChatProblem.Failed(message, retryable = false, network = false)
    }

    companion object {
        const val VOICE_TITLE = CaptureRunner.VOICE_TITLE
        /** `copilot?threadId=new`: open the chat on a new, empty conversation. */
        const val NEW_THREAD = "new"
        fun factory(c: AppContainer) = viewModelFactory {
            initializer {
                val handle: SavedStateHandle = createSavedStateHandle()
                ChatViewModel(c.chat, c.agent, c.captureProcessor, c.captures, c.pendingInputs,
                    { c.secrets.get(SecretNames.LLM_API_KEY) != null }, c.time, handle.get<String>("threadId"),
                    voice = c.voiceCaptures, runner = c.captureRunner,
                    newSession = { stop -> RecordingSession(c.newVoiceRecorder(), c.captureAudio, c.appScope, onAutoStop = { c.appScope.launch(kotlinx.coroutines.Dispatchers.Main) { stop() } }) },
                    inboxCount = inboxBadge(c.pendingInputs, c.captures),
                    attachmentLoader = { id -> c.foodCatalog.food(id)?.let { ChatAttachment(it.id, it.name + (it.brand?.let { b -> " ($b)" } ?: ""), it.incomplete) } },
                    picks = com.lifehub.app.feature.food.ScanPick.foodId, onPickConsumed = com.lifehub.app.feature.food.ScanPick::consume,
                    drafts = c.planDrafts, plans = c.plans, suggestions = c.mealSuggestions,
                    threadPicks = ThreadPick.threadId, onThreadPickConsumed = ThreadPick::consume)
            }
        }
    }
}
