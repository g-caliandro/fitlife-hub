package com.lifehub.app.copilot.food

import com.lifehub.app.data.model.PortionKind
import kotlinx.serialization.Serializable
import kotlin.math.roundToLong

/** A portion of one food as the copilot sees it (stored `food_portions` row, or the food's legacy serving size). */
@Serializable
data class PortionInfo(val label: String, val grams: Double, val kind: PortionKind, val isDefault: Boolean)

/** A household measure applicable to one food; [specific] = defined for this food (wins over a generic one). */
data class MeasureInfo(val label: String, val grams: Double, val specific: Boolean)

/** What the user said about the amount, as passed by the model (one of them, or nothing). */
sealed interface QuantitySpec {
    data class Grams(val grams: Double) : QuantitySpec
    /** [servings] of the named [portion] ("scatola", "porzione", ...) or of the default portion when null. */
    data class Servings(val servings: Double, val portion: String?) : QuantitySpec
    /** [count] household measures ("cucchiaio", "fetta", "pezzo", ...). */
    data class Measure(val label: String, val count: Double) : QuantitySpec
    /** No amount said: one default portion. */
    data object Unspecified : QuantitySpec
}

sealed interface Quantity {
    /** [grams] computed by the app. [portion]/[servings] or [measure]/[count] tell the card what was used. */
    data class Resolved(
        val grams: Double,
        val servings: Double? = null,
        val portion: PortionInfo? = null,
        val measure: MeasureInfo? = null,
        val count: Double? = null,
        val usedDefaultPortion: Boolean = false,
    ) : Quantity

    data class Missing(val reason: Reason, val message: String) : Quantity

    enum class Reason { NO_DEFAULT_PORTION, NO_PACKAGE_WEIGHT, UNKNOWN_PORTION, UNKNOWN_MEASURE }
}

/**
 * Amount said by the user -> grams (design doc §6). Pure; the app does every multiplication.
 * - grams: as said;
 * - servings: `servings x grams of the named portion` (package words -> the PACKAGE portion) or of the default portion;
 * - household measure: `count x grams`, the food-specific measure winning over the generic one ("cucchiaio" = level);
 * - nothing said: 1 default portion; no default portion -> [Quantity.Missing] (the copilot opens a pending request).
 */
object QuantityNormalizer {
    private val PACKAGE_WORDS = setOf("scatol", "confezion", "pacc", "pacchett", "packag", "pack", "box", "bust", "vaschett", "barattol", "lattin", "bottigl")
    private val SERVING_WORDS = setOf("porzion", "serving")
    /** "raso"/"rasa": the default, ignored when matching labels. */
    private val LEVEL_WORDS = setOf("ras")

    fun labelKey(label: String): Set<String> = TextMatch.tokens(label) - LEVEL_WORDS

    fun isPackageWord(label: String?): Boolean = label != null && labelKey(label).any { it in PACKAGE_WORDS }

    fun defaultPortion(portions: List<PortionInfo>): PortionInfo? = portions.firstOrNull { it.isDefault }

    fun normalize(spec: QuantitySpec, portions: List<PortionInfo>, measures: List<MeasureInfo>): Quantity = when (spec) {
        is QuantitySpec.Grams -> Quantity.Resolved(round1(spec.grams))
        QuantitySpec.Unspecified -> defaultPortion(portions)
            ?.let { Quantity.Resolved(round1(it.grams), servings = 1.0, portion = it, usedDefaultPortion = true) }
            ?: Quantity.Missing(Quantity.Reason.NO_DEFAULT_PORTION, "No amount was said and this food has no default portion.")
        is QuantitySpec.Servings -> servings(spec, portions)
        is QuantitySpec.Measure -> measure(spec, portions, measures)
    }

    private fun servings(spec: QuantitySpec.Servings, portions: List<PortionInfo>): Quantity {
        val label = spec.portion?.takeIf { it.isNotBlank() }
        val portion = when {
            label == null -> defaultPortion(portions)
                ?: return Quantity.Missing(Quantity.Reason.NO_DEFAULT_PORTION, "This food has no default portion.")
            isPackageWord(label) -> portions.firstOrNull { it.kind == PortionKind.PACKAGE }
                ?: return Quantity.Missing(Quantity.Reason.NO_PACKAGE_WEIGHT, "The package weight of this food is not known.")
            labelKey(label).any { it in SERVING_WORDS } -> portions.firstOrNull { it.kind == PortionKind.SERVING } ?: byLabel(label, portions)
            else -> byLabel(label, portions)
        } ?: return Quantity.Missing(Quantity.Reason.UNKNOWN_PORTION, "This food has no portion called '$label'.")
        val usedDefault = label == null
        return Quantity.Resolved(round1(spec.servings * portion.grams), servings = spec.servings, portion = portion, usedDefaultPortion = usedDefault)
    }

    private fun byLabel(label: String, portions: List<PortionInfo>): PortionInfo? {
        val k = labelKey(label)
        return portions.firstOrNull { labelKey(it.label) == k }
    }

    private fun measure(spec: QuantitySpec.Measure, portions: List<PortionInfo>, measures: List<MeasureInfo>): Quantity {
        val k = labelKey(spec.label)
        // A named portion of this very food (e.g. its "fetta") is more specific than any household measure.
        byLabel(spec.label, portions.filter { it.kind != PortionKind.PACKAGE })?.let {
            return Quantity.Resolved(round1(spec.count * it.grams), servings = spec.count, portion = it)
        }
        val m = measures.filter { labelKey(it.label) == k }.maxByOrNull { if (it.specific) 1 else 0 }
            ?: return Quantity.Missing(Quantity.Reason.UNKNOWN_MEASURE, "No household measure '${spec.label}' is defined for this food.")
        return Quantity.Resolved(round1(spec.count * m.grams), measure = m, count = spec.count)
    }

    fun round1(v: Double): Double = (v * 10).roundToLong() / 10.0
}
