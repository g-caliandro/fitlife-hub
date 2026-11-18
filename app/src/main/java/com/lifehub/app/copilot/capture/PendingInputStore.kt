package com.lifehub.app.copilot.capture

import com.lifehub.app.copilot.food.FoodCandidate
import com.lifehub.app.data.db.LifeHubDatabase
import com.lifehub.app.data.model.FoodState
import com.lifehub.app.data.model.PendingInput
import com.lifehub.app.data.model.PendingKind
import com.lifehub.app.data.model.PendingStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt
import java.time.Instant
import java.util.UUID

/** Values per 100 g of a model estimate (only ever shown as an option marked "estimate"). */
@Serializable
data class Per100(val kcal: Double, val protein: Double, val carbs: Double, val fat: Double)

/** One choice of a pending request: a known food ([ref] + [food] snapshot) or the model's [estimate]. */
@Serializable
data class PendingOption(val label: String, val ref: String? = null, val estimate: Per100? = null, val food: FoodCandidate? = null) {
    val isEstimate: Boolean get() = estimate != null
}

/** The meal item waiting for the answer (quantity as the user said it). */
@Serializable
data class PendingItem(
    val name: String,
    val grams: Double? = null,
    val servings: Double? = null,
    val portion: String? = null,
    val measure: String? = null,
    val count: Double? = null,
    val state: FoodState? = null,
    /** package_weight: snapshot of the product whose weight is asked. */
    val food: FoodCandidate? = null,
)

/** [option] = index of the chosen option; [text] = free-text reply (or both null when discarded). */
@Serializable
data class PendingAnswer(val option: Int? = null, val text: String? = null)

object PendingCodec {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = false; explicitNulls = false }
    fun options(p: PendingInput): List<PendingOption> = runCatching { json.decodeFromString(ListSerializer(PendingOption.serializer()), p.optionsJson) }.getOrDefault(emptyList())
    fun item(p: PendingInput): PendingItem? = p.itemJson?.let { runCatching { json.decodeFromString(PendingItem.serializer(), it) }.getOrNull() }
    fun answer(p: PendingInput): PendingAnswer? = p.answerJson?.let { runCatching { json.decodeFromString(PendingAnswer.serializer(), it) }.getOrNull() }
    fun encodeOptions(o: List<PendingOption>) = json.encodeToString(ListSerializer(PendingOption.serializer()), o)
    fun encodeItem(i: PendingItem) = json.encodeToString(PendingItem.serializer(), i)
    fun encodeAnswer(a: PendingAnswer) = json.encodeToString(PendingAnswer.serializer(), a)
}

/**
 * Open requests to the user (design doc "PendingInputStore"). Persistent, no expiry, independent from the chat UI and from
 * notifications: the chat (now), the inbox and the notification actions (phase 4) all answer through [answer]/[discard],
 * so answering in one place closes the request everywhere.
 */
class PendingInputStore(db: LifeHubDatabase) {
    private val dao = db.pendingInputDao()

    suspend fun create(
        captureId: String, threadId: String?, kind: PendingKind, prompt: String, options: List<PendingOption>,
        item: PendingItem?, foodRef: String?, now: Instant, id: String = UUID.randomUUID().toString(),
    ): PendingInput {
        val p = PendingInput(id, captureId, threadId, kind, prompt, PendingCodec.encodeOptions(options), item?.let(PendingCodec::encodeItem),
            foodRef, PendingStatus.OPEN, null, now, null)
        dao.insert(p)
        return p
    }

    suspend fun get(id: String) = dao.get(id)
    suspend fun byCapture(captureId: String) = dao.byCapture(captureId)
    fun observeOpen() = dao.observeOpen()
    fun observeOpenCount() = dao.observeOpenCount()
    fun observeByThread(threadId: String) = dao.observeByThread(threadId)

    /** OPEN -> ANSWERED. Throws [IllegalTransitionException] if already answered/discarded, or the option is out of range. */
    suspend fun answer(id: String, answer: PendingAnswer, now: Instant): PendingInput {
        val p = dao.get(id) ?: throw IllegalTransitionException("Unknown request $id")
        if (!PendingStateMachine.canMove(p.status, PendingStatus.ANSWERED)) throw IllegalTransitionException("Request already ${p.status.name.lowercase()}")
        val n = PendingCodec.options(p).size
        if (answer.option != null && answer.option !in 0 until n) throw IllegalTransitionException("Option ${answer.option} out of range")
        if (answer.option == null && answer.text.isNullOrBlank()) throw IllegalTransitionException("Empty answer")
        val next = p.copy(status = PendingStatus.ANSWERED, answerJson = PendingCodec.encodeAnswer(answer), resolvedAt = now)
        dao.update(next)
        return next
    }

    /** Sets `notifiedAt` once; true only the first time (a request's notification is never posted twice). */
    suspend fun markNotified(id: String, now: Instant): Boolean = dao.markNotified(id, now) > 0

    /** OPEN -> DISCARDED (no-op if already discarded). */
    suspend fun discard(id: String, now: Instant): PendingInput {
        val p = dao.get(id) ?: throw IllegalTransitionException("Unknown request $id")
        if (p.status == PendingStatus.DISCARDED) return p
        if (!PendingStateMachine.canMove(p.status, PendingStatus.DISCARDED)) throw IllegalTransitionException("Request already ${p.status.name.lowercase()}")
        val next = p.copy(status = PendingStatus.DISCARDED, resolvedAt = now)
        dao.update(next)
        return next
    }
}

/** The chat card of a pending request, rebuilt from the stored row (chat tool result, inbox). */
object PendingCards {
    fun from(p: PendingInput): com.lifehub.app.copilot.agent.PendingInputCard = com.lifehub.app.copilot.agent.PendingInputCard(
        p.id, p.captureId, p.kind.name.lowercase(), p.prompt,
        PendingCodec.options(p).map { o ->
            com.lifehub.app.copilot.agent.PendingOptionView(
                o.label, o.isEstimate,
                o.estimate?.let { "≈ ${it.kcal.roundToInt()} kcal / 100 g" }
                    ?: o.food?.let { "${it.source.wire.replace('_', ' ')} · ${it.kcal100.roundToInt()} kcal / 100 g" },
            )
        },
        PendingCodec.item(p)?.name,
    )
}
