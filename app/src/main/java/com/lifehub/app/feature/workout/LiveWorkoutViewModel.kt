package com.lifehub.app.feature.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lifehub.app.data.model.Exercise
import com.lifehub.app.data.model.SessionSet
import com.lifehub.app.data.model.SessionWithSets
import com.lifehub.app.data.model.SetStatus
import com.lifehub.app.data.model.TargetType
import com.lifehub.app.di.AppContainer
import com.lifehub.app.domain.AchievementEngine
import com.lifehub.app.domain.Bests
import com.lifehub.app.notifications.RestAlarm
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

data class LiveUi(
    val loaded: Boolean = false,
    val missing: Boolean = false,
    val planName: String = "",
    val dayType: com.lifehub.app.data.model.DayType? = null,
    val startedAtMillis: Long = 0,
    val sets: List<SessionSet> = emptyList(),      // ordered by (exerciseOrder, setIndex)
    val focusedIndex: Int = 0,
    val input: SetInput = SetInput("", ""),
    val restEndsAtMillis: Long? = null,
    val restTotalSec: Int = 0,
    val holdStartMillis: Long? = null,
    val exercises: List<Exercise> = emptyList(),
    val pr: PrBanner? = null,
    val previous: Map<String, String> = emptyMap(),   // setId -> "Previous" column label
) {
    val focused: SessionSet? get() = sets.getOrNull(focusedIndex)
    val resolved: Int get() = sets.count { it.status != SetStatus.PENDING }
    val pending: Int get() = sets.count { it.status == SetStatus.PENDING }
    val nextPending: SessionSet? get() = WorkoutOps.nextPendingIndex(sets, focusedIndex)?.let { sets[it] }
}

/** Transient "New PR" celebration shown at the top of the live workout. */
data class PrBanner(val id: Long, val text: String)

class LiveWorkoutViewModel(private val c: AppContainer, private val handle: SavedStateHandle) : ViewModel() {
    private data class Flags(val hold: Long?, val pr: PrBanner?)
    private data class Snap(val loaded: Boolean, val data: SessionWithSets?)

    private val snap = c.sessions.observeInProgress().map { Snap(true, it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Snap(false, null))
    private val focusedId = MutableStateFlow<String?>(handle["focusedId"])
    private val edits = MutableStateFlow<Pair<String, SetInput>?>(
        handle.get<String>("editSet")?.let { it to SetInput(weight = handle["editWeight"] ?: "", value = handle["editValue"] ?: "") },
    )
    private fun setEdits(e: Pair<String, SetInput>?) {
        edits.value = e
        handle["editSet"] = e?.first; handle["editValue"] = e?.second?.value; handle["editWeight"] = e?.second?.weight
    }
    private fun setFocused(id: String?) { focusedId.value = id; handle["focusedId"] = id }
    private val holdStart = handle.getStateFlow<Long?>("holdStart", null)
    private val pr = MutableStateFlow<PrBanner?>(null)
    private var leaving = false
    private var busy = false

    private val base = combine(
        snap, focusedId, edits, combine(holdStart, pr) { h, p -> Flags(h, p) }, c.exercises.observeAll(),
    ) { sn, fid, ed, flags, exs ->
        val data = sn.data
        if (data == null) return@combine LiveUi(loaded = sn.loaded, missing = sn.loaded && !leaving, exercises = exs)
        val sets = WorkoutOps.ordered(data.sets)
        val idx0 = sets.indexOfFirst { it.id == fid }.takeIf { it >= 0 }
            ?: data.session.currentSetIndex.coerceIn(0, (sets.size - 1).coerceAtLeast(0))
        val idx = FocusSteps.currentIndex(sets, idx0) ?: idx0
        val focused = sets.getOrNull(idx)
        val input = focused?.let { f -> ed?.takeIf { it.first == f.id }?.second ?: WorkoutOps.prefill(f) } ?: SetInput("", "")
        val endsAt = data.session.restEndsAt?.toEpochMilli()
        val lastDone = sets.filter { it.status == SetStatus.DONE && it.completedAt != null }.maxByOrNull { it.completedAt!! }
        val remaining = endsAt?.let { ((it - c.time.now().toEpochMilli()) / 1000).toInt().coerceAtLeast(0) } ?: 0
        LiveUi(
            loaded = true, planName = data.session.planName, startedAtMillis = data.session.startedAt.toEpochMilli(),
            sets = sets, focusedIndex = idx, input = input, restEndsAtMillis = endsAt,
            restTotalSec = maxOf(lastDone?.restSeconds ?: 0, remaining), 
            holdStartMillis = flags.hold, exercises = exs, pr = flags.pr,
        )
    }
    private val dayType = c.plans.observePlans().combine(snap) { plans, sn ->
        sn.data?.session?.planId?.let { id -> plans.firstOrNull { it.plan.id == id }?.plan?.dayType }
    }
    private val history: kotlinx.coroutines.flow.Flow<Map<String, List<SessionSet>>> =
        snap.map { sn -> sn.data?.sets?.map { it.exerciseId }?.toSet().orEmpty() }.distinctUntilChanged()
            .map { ids -> ids.associateWith { c.sessions.previousSets(it) } }
    val ui: StateFlow<LiveUi> = combine(base, dayType, history.onStart { emit(emptyMap()) }) { u, t, h ->
        u.copy(dayType = t, previous = WorkoutOps.previousLabels(u.sets, h))
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LiveUi())

    private val cur: LiveUi get() = ui.value
    private val session get() = snap.value.data?.session
    private val now: Instant get() = c.time.now()

    private fun persistIndex(index: Int, restEndsAt: Instant? = session?.restEndsAt) {
        val s = session ?: return
        viewModelScope.launch { c.sessions.updateSession(s.copy(currentSetIndex = index, restEndsAt = restEndsAt)) }
    }

    /** User-driven focus change (row tap, prev/next). */
    fun focusIndex(index: Int) {
        val set = cur.sets.getOrNull(index) ?: return
        setFocused(set.id); setEdits(null)
        handle["holdStart"] = null
        if (session?.restEndsAt != null) RestAlarm.cancel(c.appContext)
        persistIndex(index, restEndsAt = null)
    }

    fun onWeight(w: String) { cur.focused?.let { f -> setEdits(f.id to cur.input.copy(weight = w)) } }
    fun onValue(v: String) { cur.focused?.let { f -> setEdits(f.id to cur.input.copy(value = v)) } }

    fun startHold() { handle["holdStart"] = now.toEpochMilli() }
    fun cancelHold() { handle["holdStart"] = null }

    /** Ends the running hold and logs it: [auto] = target reached (logs the target), else the elapsed seconds. */
    fun finishHold(auto: Boolean): Boolean {
        val set = cur.focused ?: return false
        val start = cur.holdStartMillis ?: return false
        val elapsed = ((now.toEpochMilli() - start) / 1000).toInt().coerceIn(0, 999)
        val secs = if (auto) (set.targetValue ?: elapsed) else elapsed
        return complete(set, cur.input.copy(value = secs.toString()))
    }

    /** Done / Update on the focused set. Returns false if the input is invalid. */
    fun done(input: SetInput? = null): Boolean {
        val set = cur.focused ?: return false
        return complete(set, input ?: cur.input)
    }

    /** Per-row check (item 14): completes [setId] with its prefilled value (MAX needs typed input, so it is rejected). */
    fun quickDone(setId: String): Boolean {
        val set = cur.sets.firstOrNull { it.id == setId } ?: return false
        val input = if (set.id == cur.focused?.id) cur.input else WorkoutOps.prefill(set)
        return complete(set, input)
    }

    private fun complete(set: SessionSet, input: SetInput): Boolean {
        val u = cur
        val completed = WorkoutOps.completeFromInput(set, input, now) ?: return false
        val s = session ?: return false
        if (busy) return false
        busy = true
        handle["holdStart"] = null
        setEdits(null)
        viewModelScope.launch {
            try {
            if (set.status != SetStatus.DONE) celebrateIfPr(completed, u.sets)
            c.sessions.upsertSet(completed)
            if (set.status == SetStatus.DONE) return@launch      // "Update": stay put, no rest
            val after = u.sets.map { if (it.id == set.id) completed else it }
            val at = u.sets.indexOfFirst { it.id == set.id }
            val next = WorkoutOps.nextPendingIndex(after, at)
            val rest = if (next != null && set.restSeconds > 0) now.plusSeconds(set.restSeconds.toLong()) else null
            c.sessions.updateSession(s.copy(currentSetIndex = next ?: at, restEndsAt = rest))
            if (rest != null) RestAlarm.schedule(c.appContext, rest.toEpochMilli(), next?.let { after[it].exerciseName })
            else RestAlarm.cancel(c.appContext)
            setFocused(next?.let { after[it].id })
            } finally { busy = false }
        }
        return true
    }

    /** F-ACH-3: compare against history before this session + earlier sets of this session. */
    private suspend fun celebrateIfPr(completed: SessionSet, sets: List<SessionSet>) {
        val earlier = sets.filter { it.exerciseId == completed.exerciseId && it.id != completed.id && it.status == SetStatus.DONE }
        val prior = Bests.of(c.achievements.historySets(completed.exerciseId) + earlier)
        val hit = AchievementEngine.liveHits(completed, prior).firstOrNull() ?: return
        val banner = PrBanner(now.toEpochMilli(), hit.bannerText)
        pr.value = banner
        viewModelScope.launch { delay(1500); if (pr.value?.id == banner.id) pr.value = null }
    }

    fun skipFocused() {
        val u = cur
        val set = u.focused ?: return
        setEdits(null); handle["holdStart"] = null
        viewModelScope.launch {
            val skipped = WorkoutOps.skip(set)
            c.sessions.upsertSet(skipped)
            val after = u.sets.map { if (it.id == set.id) skipped else it }
            val next = WorkoutOps.nextPendingIndex(after, u.focusedIndex)
            setFocused(next?.let { after[it].id })
            persistIndex(next ?: u.focusedIndex)
        }
    }

    fun toggleSkip(setId: String) {
        val set = cur.sets.firstOrNull { it.id == setId } ?: return
        viewModelScope.launch {
            c.sessions.upsertSet(if (set.status == SetStatus.SKIPPED) WorkoutOps.unskip(set) else WorkoutOps.skip(set))
        }
    }

    fun applyWeightToRemaining() {
        val u = cur
        val set = u.focused ?: return
        val w = u.input.weight.toDoubleOrNull()
        viewModelScope.launch {
            c.sessions.upsertSet(set.copy(weightKg = w))
            c.sessions.upsertSets(WorkoutOps.applyWeightToRemaining(u.sets, set.id, w))
        }
        setEdits(null)
    }

    fun addSet(exerciseOrder: Int) {
        val n = WorkoutOps.addSet(cur.sets, exerciseOrder)
        viewModelScope.launch { c.sessions.upsertSet(n) }
    }

    /** Removes [setId]; [onRemoved] receives the original rows of that exercise for an undo. */
    fun removeSet(setId: String, onRemoved: (List<SessionSet>) -> Unit) {
        val sets = cur.sets
        val target = sets.firstOrNull { it.id == setId } ?: return
        val original = sets.filter { it.exerciseOrder == target.exerciseOrder }
        val renumbered = WorkoutOps.removeSet(sets, setId)
        viewModelScope.launch {
            c.sessions.deleteSet(setId)
            c.sessions.upsertSets(renumbered)
            onRemoved(original)
        }
    }

    /** Removes the whole exercise block; [onRemoved] receives its rows for an undo. */
    fun removeExercise(exerciseOrder: Int, onRemoved: (List<SessionSet>) -> Unit) {
        val original = cur.sets.filter { it.exerciseOrder == exerciseOrder }
        viewModelScope.launch {
            original.forEach { c.sessions.deleteSet(it.id) }
            onRemoved(original)
        }
    }

    /** PR titles this session would earn (compared with every earlier completed session). */
    suspend fun sessionPrTitles(): List<String> {
        val s = session ?: return emptyList()
        val sets = cur.sets
        val priors = sets.map { it.exerciseId }.distinct().associateWith { Bests.of(c.achievements.historySets(it)) }
        return AchievementEngine.sessionPrs(s, sets.map { it.copy(sessionId = s.id) }, priors).map { it.title }
    }

    fun restore(original: List<SessionSet>) { viewModelScope.launch { c.sessions.upsertSets(original) } }

    fun addExercise(ex: Exercise, count: Int, type: TargetType, value: Int?, weightKg: Double?) {
        val s = session ?: return
        val rows = WorkoutOps.addExercise(s.id, cur.sets, ex, count, type, value, weightKg)
        viewModelScope.launch { c.sessions.upsertSets(rows) }
    }

    fun swapExercise(exerciseOrder: Int, ex: Exercise) {
        val rows = WorkoutOps.swapExercise(cur.sets, exerciseOrder, ex)
        viewModelScope.launch { c.sessions.upsertSets(rows) }
    }

    /** New exercise from the picker: saved to the library, then pickable (exercises flow). */
    fun createExercise(ex: Exercise) { viewModelScope.launch { c.exercises.save(ex) } }

    // ---- rest timer (persisted end timestamp; UI recomputes remaining from it) ----

    fun adjustRest(deltaSec: Int) {
        val s = session ?: return
        val end = s.restEndsAt ?: return
        val n = now
        val next = end.plusSeconds(deltaSec.toLong())
        val newEnd = if (next.isBefore(n)) n else next
        val name = cur.focused?.exerciseName
        viewModelScope.launch {
            c.sessions.updateSession(s.copy(restEndsAt = newEnd))
            RestAlarm.schedule(c.appContext, newEnd.toEpochMilli(), name)
        }
    }

    fun skipRest() {
        val s = session ?: return
        RestAlarm.cancel(c.appContext)
        viewModelScope.launch { c.sessions.updateSession(s.copy(restEndsAt = null)) }
    }

    fun finish(notes: String?, rpe: Int?, onDone: () -> Unit) {
        val s = session ?: return
        leaving = true
        RestAlarm.cancel(c.appContext)
        viewModelScope.launch {
            c.sessions.finish(s.id, notes?.trim()?.ifEmpty { null }, rpe, now)
            try { c.achievements.onSessionFinished(s.id, now) } catch (_: Exception) {}
            onDone()
        }
    }

    fun discard(onDone: () -> Unit) {
        val s = session ?: return
        leaving = true
        RestAlarm.cancel(c.appContext)
        viewModelScope.launch { c.sessions.discard(s.id); onDone() }
    }

    companion object {
        fun factory(c: AppContainer) = viewModelFactory { initializer { LiveWorkoutViewModel(c, createSavedStateHandle()) } }
    }
}
