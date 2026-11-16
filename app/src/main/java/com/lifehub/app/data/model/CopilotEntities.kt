package com.lifehub.app.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/** Where a chat thread was started: the chat screen, or a quick capture (phase 4: one new thread per capture). */
enum class ChatOrigin { CHAT, QUICK }

/** Message author. TOOL rows hold one tool result (and the card it produced, if any). */
enum class ChatRole { USER, ASSISTANT, TOOL }

/** A copilot conversation (DB v8). Not part of the JSON export (owner decision 7). */
@Entity(tableName = "chat_threads", indices = [Index("updatedAt")])
data class ChatThread(
    @PrimaryKey val id: String,
    val title: String,
    val origin: ChatOrigin,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/**
 * One message of a thread, ordered by [seq] (0, 1, 2, ... per thread).
 * - USER: [text].
 * - ASSISTANT: [text] (may be null when the model only requested tools) and/or [toolCallsJson]
 *   (JSON list of `{id, name, arguments}`, arguments = the raw string the model produced).
 * - TOOL: [toolCallId] + [toolName], [text] = the result JSON sent back to the model; [cardType]/[cardJson] = the native
 *   card drawn from that result, persisted so the card redraws identically when the thread is reopened.
 * [captureId] links the message to a capture (phase 3+), null for plain chat.
 */
@Entity(
    tableName = "chat_messages",
    foreignKeys = [ForeignKey(entity = ChatThread::class, parentColumns = ["id"], childColumns = ["threadId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["threadId", "seq"], unique = true), Index("captureId")],
)
data class ChatMessage(
    @PrimaryKey val id: String,
    val threadId: String,
    val seq: Int,
    val role: ChatRole,
    val text: String?,
    val toolCallsJson: String? = null,
    val toolCallId: String? = null,
    val toolName: String? = null,
    val cardType: String? = null,
    val cardJson: String? = null,
    val captureId: String? = null,
    val createdAt: Instant,
)

// ---------------------------------------------------------------------------------------------------------------
// v9 (phase 3): captures, writes for Undo, pending input requests, portions, household measures, base foods.
// ---------------------------------------------------------------------------------------------------------------

/**
 * Capture life cycle (design doc §4). RECORDED -> PROCESSING -> DONE | AWAITING_INPUT | FAILED;
 * AWAITING_INPUT -> PROCESSING (an answer) or DONE (every open request discarded); FAILED -> RECORDED / PROCESSING (Retry);
 * any non-final state -> CANCELLED (Undo). CANCELLED is final. Transitions: [com.lifehub.app.copilot.capture.CaptureStateMachine].
 */
enum class CaptureStatus { RECORDED, PROCESSING, AWAITING_INPUT, DONE, FAILED, CANCELLED }

/**
 * One user instruction (typed in the chat now, dictated in phase 4) and everything the copilot did for it.
 * [mealAt] = date and time of the meal written by this capture (set by the first log, reused by the later ones, so
 * items resolved after a pending request join the same meal). [outcome] = the copilot's final reply (notification text in
 * phase 4). [audioPath]/[transcript]/[attempts]/[errorCode] are for the phase 4 worker. Not exported (decision 7).
 */
@Entity(tableName = "captures", indices = [Index("status"), Index("threadId")])
data class Capture(
    @PrimaryKey val id: String,
    val threadId: String?,
    val origin: ChatOrigin,
    val status: CaptureStatus,
    val inputText: String?,
    val audioPath: String? = null,
    val transcript: String? = null,
    val attachedFoodId: String? = null,
    val zoneId: String,
    val mealAt: Instant? = null,
    val outcome: String? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val attempts: Int = 0,
    val createdAt: Instant,
    val updatedAt: Instant,
)

enum class WriteOp { INSERT, UPDATE }

/**
 * One record a capture created (INSERT) or changed (UPDATE, with the previous values in [prevJson] and the values it set
 * in [newJson]). Undo reverses exactly these. [idemKey] makes meal items idempotent per capture (a re-run finds the key
 * and does not write again).
 */
@Entity(
    tableName = "capture_writes",
    foreignKeys = [ForeignKey(entity = Capture::class, parentColumns = ["id"], childColumns = ["captureId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["captureId", "tableName", "recordId"], unique = true), Index(value = ["captureId", "idemKey"])],
)
data class CaptureWrite(
    @PrimaryKey val id: String,
    val captureId: String,
    val idemKey: String?,
    val tableName: String,
    val recordId: String,
    val op: WriteOp,
    val prevJson: String?,
    val newJson: String?,
    val createdAt: Instant,
)

/** Design doc §7 request kinds: choose between candidates, use an estimate, missing quantity, missing package weight. */
enum class PendingKind { CHOOSE_FOOD, USE_ESTIMATE, QUANTITY, PACKAGE_WEIGHT }

enum class PendingStatus { OPEN, ANSWERED, DISCARDED }

/**
 * An open question to the user, persisted until answered or discarded (no expiry). Independent from the chat UI and from
 * notifications: [com.lifehub.app.copilot.capture.PendingInputStore] is the only way to answer or discard it.
 * [optionsJson] = list of options (label, ref, estimate values, food snapshot); [itemJson] = the meal item to log once
 * answered; [foodRef] = the product whose package weight is asked; [answerJson] = chosen option index and/or free text.
 */
@Entity(
    tableName = "pending_inputs",
    foreignKeys = [ForeignKey(entity = Capture::class, parentColumns = ["id"], childColumns = ["captureId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("status"), Index("captureId")],
)
data class PendingInput(
    @PrimaryKey val id: String,
    val captureId: String,
    val threadId: String?,
    val kind: PendingKind,
    val prompt: String,
    val optionsJson: String,
    val itemJson: String?,
    val foodRef: String?,
    val status: PendingStatus,
    val answerJson: String?,
    val createdAt: Instant,
    val resolvedAt: Instant?,
    val notifiedAt: Instant? = null,
)

/** PACKAGE = the whole box/pack ("scatola"); SERVING = the label's serving; PIECE / USER = other named portions. */
enum class PortionKind { PACKAGE, SERVING, PIECE, USER }

/** A named portion of a food in grams; at most one [isDefault] per food (used when no quantity is said). */
@Entity(
    tableName = "food_portions",
    foreignKeys = [ForeignKey(entity = Food::class, parentColumns = ["id"], childColumns = ["foodId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("foodId")],
)
data class FoodPortion(
    @PrimaryKey val id: String,
    val foodId: String,
    val label: String,
    val grams: Double,
    val isDefault: Boolean,
    val kind: PortionKind,
    val createdAt: Instant,
)

/**
 * Household measure ("cucchiaio" = 10 g). Generic when [foodId] and [baseFoodId] are null; otherwise specific to one
 * user food or one base food. Editable in Copilot settings; defaults in [com.lifehub.app.copilot.food.HouseholdMeasureSeed].
 */
@Entity(tableName = "household_measures")
data class HouseholdMeasure(
    @PrimaryKey val id: String,
    val label: String,
    val grams: Double,
    val foodId: String? = null,
    val baseFoodId: String? = null,
    val updatedAt: Instant,
)

/** Read-only reference food (bundled asset `copilot/base_foods.json`, USDA SR Legacy, Italian names). Values per 100 g. */
@Entity(tableName = "base_foods")
data class BaseFood(
    @PrimaryKey val id: String,
    val name: String,
    /** `|`-separated synonyms. */
    val aliases: String?,
    val state: FoodState?,
    val kcal100: Double,
    val protein100: Double,
    val carbs100: Double,
    val fat100: Double,
    val sourceRef: String?,
)
