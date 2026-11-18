package com.lifehub.app.feature.body

import com.lifehub.app.data.db.dao.BodyDao
import com.lifehub.app.data.model.BodyEntry
import java.time.LocalDate

/** Pure validation for the body form (F-BODY-1). Errors are keys, mapped to strings in UI. */
enum class BodyError { WeightRange, MuscleExceeds, FatExceeds, SumExceeds }

data class BodyValidation(
    val weight: Double?, val muscle: Double?, val fat: Double?,
    val weightError: BodyError? = null, val muscleError: BodyError? = null, val fatError: BodyError? = null,
) {
    val ok get() = weight != null && weightError == null && muscleError == null && fatError == null
}

fun parseKg(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()

fun validateBody(weightS: String, muscleS: String, fatS: String): BodyValidation {
    val w = parseKg(weightS)
    val m = parseKg(muscleS)
    val f = parseKg(fatS)
    val wErr = if (w == null || w < 20.0 || w > 300.0) BodyError.WeightRange else null
    var mErr: BodyError? = null
    var fErr: BodyError? = null
    if (wErr == null && w != null) {
        if (m != null && (m < 0 || m > w)) mErr = BodyError.MuscleExceeds
        if (f != null && (f < 0 || f > w)) fErr = BodyError.FatExceeds
        if (mErr == null && fErr == null && m != null && f != null && m + f > w + 1e-9) fErr = BodyError.SumExceeds
    }
    return BodyValidation(w, m, f, wErr, mErr, fErr)
}

/** Live derived body fat %, null if not computable. */
fun liveFatPct(weight: Double?, fat: Double?): Double? =
    if (weight != null && weight > 0 && fat != null) fat / weight * 100 else null

/** Undo for a save/delete: put [prev] back, or remove the row if there was none. */
suspend fun restoreBody(dao: BodyDao, date: LocalDate, prev: BodyEntry?) {
    if (prev == null) dao.delete(date) else dao.upsert(prev)
}
