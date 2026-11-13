package com.lifehub.app.core.designsystem

import com.lifehub.app.data.model.SessionSet
import com.lifehub.app.data.model.SetStatus
import com.lifehub.app.data.model.TargetType
import com.lifehub.app.domain.DateRange
import java.time.LocalDate
import java.time.Year
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val DASH = "—"
private const val MINUS = "−"
private val EN = Locale.ENGLISH
private val fmtShortDay = DateTimeFormatter.ofPattern("EEE d MMM", EN)
private val fmtFullDay = DateTimeFormatter.ofPattern("EEE d MMM yyyy", EN)

fun fmtKg(v: Double?): String = if (v == null) DASH else String.format(EN, "%.1f", v)

/** "+0.6 kg", "−0.6 kg" (U+2212), "0.0 kg", "—". Unit is optional. */
fun fmtDelta(delta: Double?, decimals: Int = 1, unit: String? = null): String {
    if (delta == null) return DASH
    val s = String.format(EN, "%.${decimals}f", kotlin.math.abs(delta))
    val isZero = s.trim('0', '.').isEmpty()
    val sign = when {
        isZero -> ""
        delta > 0 -> "+"
        else -> MINUS
    }
    return sign + s + (unit?.let { " $it" } ?: "")
}

fun fmtInt(v: Int?): String = if (v == null) DASH else String.format(EN, "%,d", v)

/** Weight without trailing ".0": 20 -> "20", 2.5 -> "2.5". */
fun fmtWeight(kg: Double): String =
    if (kg % 1.0 == 0.0) kg.toLong().toString() else String.format(EN, "%.1f", kg)

/** "Wed 12 Nov" (+ " 2025" if not the current year). [currentYear] injectable for tests. */
// DECISION: Year.now() default is the only wall-clock read in the formatters; pass currentYear in tests.
fun fmtDate(d: LocalDate, currentYear: Int = Year.now().value): String =
    if (d.year == currentYear) d.format(fmtShortDay) else d.format(fmtShortDay) + " " + d.year

/** Always includes the year: "Wed 12 Nov 2026". */
fun fmtDateFull(d: LocalDate): String = d.format(fmtFullDay)

fun fmtRange(r: DateRange): String = "${r.start.format(fmtShortDay)} – ${r.end.format(fmtShortDay)}"

fun fmtDuration(sec: Int?): String {
    if (sec == null) return DASH
    val min = sec / 60
    return if (min < 60) "$min min" else String.format(EN, "%d h %02d min", min / 60, min % 60)
}

fun fmtClock(sec: Int): String {
    val s = sec.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val ss = s % 60
    return if (h > 0) String.format(EN, "%d:%02d:%02d", h, m, ss) else String.format(EN, "%d:%02d", m, ss)
}

fun fmtTarget(type: TargetType, value: Int?): String = when (type) {
    TargetType.REPS -> value?.toString() ?: DASH
    TargetType.MAX -> "MAX"
    TargetType.SECONDS -> if (value == null) DASH else "$value s"
}

/** DONE: "6 × 20 kg" / "6" / "30 s"; SKIPPED: "skipped"; PENDING: target. */
fun fmtSet(set: SessionSet): String = when (set.status) {
    SetStatus.SKIPPED -> "skipped"
    SetStatus.PENDING -> fmtTarget(set.targetType, set.targetValue)
    SetStatus.DONE -> when (set.targetType) {
        TargetType.SECONDS -> set.actualSeconds?.let { "$it s" } ?: DASH
        else -> {
            val reps = set.actualReps?.toString() ?: DASH
            val w = set.weightKg
            if (w != null && w > 0) "$reps × ${fmtWeight(w)} kg" else reps
        }
    }
}
