package com.lifehub.app.copilot.food

import com.lifehub.app.data.model.PortionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuantityNormalizerTest {
    private val box = PortionInfo("package", 400.0, PortionKind.PACKAGE, isDefault = true)
    private val serving = PortionInfo("serving", 150.0, PortionKind.SERVING, isDefault = false)
    private val slice = PortionInfo("fetta", 22.0, PortionKind.PIECE, isDefault = false)
    private val generic = listOf(MeasureInfo("cucchiaio", 10.0, false), MeasureInfo("cucchiaino", 5.0, false), MeasureInfo("fetta", 30.0, false))
    private val oil = generic + listOf(MeasureInfo("cucchiaio", 9.0, true), MeasureInfo("cucchiaio colmo", 15.0, true))

    private fun ok(q: Quantity) = q as Quantity.Resolved
    private fun missing(q: Quantity) = (q as Quantity.Missing).reason

    @Test fun gramsAreKeptAsSaid() {
        assertEquals(120.0, ok(QuantityNormalizer.normalize(QuantitySpec.Grams(120.0), listOf(box), generic)).grams, 0.0)
    }

    @Test fun fractionOfTheNamedPortionIsComputedByTheApp() {
        val half = ok(QuantityNormalizer.normalize(QuantitySpec.Servings(0.5, "scatola"), listOf(box, serving), generic))
        assertEquals(200.0, half.grams, 0.0); assertEquals(box, half.portion); assertEquals(0.5, half.servings!!, 0.0)
        // Italian/English package words all mean the PACKAGE portion.
        listOf("confezione", "box", "pacco", "Scatole").forEach {
            assertEquals(it, 400.0 * 2, ok(QuantityNormalizer.normalize(QuantitySpec.Servings(2.0, it), listOf(box, serving), generic)).grams, 0.0)
        }
        assertEquals(50.0, ok(QuantityNormalizer.normalize(QuantitySpec.Servings(1.0 / 3, "porzione"), listOf(box, serving), generic)).grams, 0.0)
    }

    @Test fun servingsWithoutPortionUseTheDefault() {
        val q = ok(QuantityNormalizer.normalize(QuantitySpec.Servings(0.5, null), listOf(serving, box), generic))
        assertEquals(200.0, q.grams, 0.0); assertTrue(q.usedDefaultPortion)
    }

    @Test fun noQuantityMeansOneDefaultPortionShownInTheOutcome() {
        val q = ok(QuantityNormalizer.normalize(QuantitySpec.Unspecified, listOf(serving, box), generic))
        assertEquals(400.0, q.grams, 0.0); assertEquals(1.0, q.servings!!, 0.0); assertTrue(q.usedDefaultPortion)
    }

    @Test fun missingDefaultPortionOpensARequest() {
        assertEquals(Quantity.Reason.NO_DEFAULT_PORTION, missing(QuantityNormalizer.normalize(QuantitySpec.Unspecified, listOf(serving), generic)))
        assertEquals(Quantity.Reason.NO_DEFAULT_PORTION, missing(QuantityNormalizer.normalize(QuantitySpec.Unspecified, emptyList(), generic)))
        assertEquals(Quantity.Reason.NO_DEFAULT_PORTION, missing(QuantityNormalizer.normalize(QuantitySpec.Servings(2.0, null), emptyList(), generic)))
    }

    @Test fun missingPackageWeightIsItsOwnReason() {
        assertEquals(Quantity.Reason.NO_PACKAGE_WEIGHT, missing(QuantityNormalizer.normalize(QuantitySpec.Servings(0.5, "scatola"), listOf(serving), generic)))
        assertEquals(Quantity.Reason.UNKNOWN_PORTION, missing(QuantityNormalizer.normalize(QuantitySpec.Servings(1.0, "vassoio"), listOf(serving), generic)))
    }

    @Test fun householdMeasureGenericAndFoodSpecific() {
        // Generic: no specific row for this food.
        assertEquals(20.0, ok(QuantityNormalizer.normalize(QuantitySpec.Measure("cucchiai", 2.0), emptyList(), generic)).grams, 0.0)
        // Food-specific wins; unqualified spoon = level ("raso" is the same measure).
        assertEquals(9.0, ok(QuantityNormalizer.normalize(QuantitySpec.Measure("cucchiaio", 1.0), emptyList(), oil)).grams, 0.0)
        assertEquals(9.0, ok(QuantityNormalizer.normalize(QuantitySpec.Measure("cucchiaio raso", 1.0), emptyList(), oil)).grams, 0.0)
        assertEquals(15.0, ok(QuantityNormalizer.normalize(QuantitySpec.Measure("cucchiaio colmo", 1.0), emptyList(), oil)).grams, 0.0)
        // Generic cucchiaino still applies to the oil (no specific one in this list).
        assertEquals(5.0, ok(QuantityNormalizer.normalize(QuantitySpec.Measure("cucchiaino", 1.0), emptyList(), oil)).grams, 0.0)
        // A named portion of the food itself beats the generic measure.
        assertEquals(44.0, ok(QuantityNormalizer.normalize(QuantitySpec.Measure("fette", 2.0), listOf(slice), generic)).grams, 0.0)
    }

    @Test fun unknownMeasureIsMissing() {
        assertEquals(Quantity.Reason.UNKNOWN_MEASURE, missing(QuantityNormalizer.normalize(QuantitySpec.Measure("pezzo", 1.0), emptyList(), generic)))
    }

    @Test fun gramsAreRoundedToOneDecimal() {
        assertEquals(133.3, ok(QuantityNormalizer.normalize(QuantitySpec.Servings(1.0 / 3, "package"), listOf(box), generic)).grams, 0.0)
    }
}
