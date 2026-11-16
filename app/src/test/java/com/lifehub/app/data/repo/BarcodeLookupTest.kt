package com.lifehub.app.data.repo

import com.lifehub.app.copilot.CopilotFixture
import com.lifehub.app.copilot.dumpTables
import com.lifehub.app.data.model.Food
import com.lifehub.app.data.model.FoodPortion
import com.lifehub.app.data.model.FoodSource
import com.lifehub.app.data.model.PortionKind
import com.lifehub.app.data.off.OffBarcodeMapper
import com.lifehub.app.data.off.OffProduct
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Barcode lookup extracted from the scanner screen (phase 5): the app flow is unchanged, the copilot flow adds portions. */
@RunWith(RobolectricTestRunner::class)
class BarcodeLookupTest {
    private val f = CopilotFixture(seed = false)
    @After fun tearDown() = f.close()

    private val bar = "8001505005707"
    private fun product(kcal: Double? = 539.0, p: Double? = 6.3, c: Double? = 57.5, fat: Double? = 30.9, pack: Double? = 350.0, serving: Double? = 15.0) =
        OffProduct(bar, "Nocciolata", "Rigoni", kcal, p, c, fat, pack, serving)

    @Test fun cacheHitNeverCallsOpenFoodFactsAndKeepsItsPortions() = runBlocking {
        val food = Food("f-noc", FoodSource.OFF, bar, "Nocciolata", "Rigoni", 539.0, 6.3, 57.5, 30.9, null, false, null)
        f.food.saveFood(food)
        f.db.portionDao().upsertPortion(FoodPortion("p1", "f-noc", "package", 350.0, true, PortionKind.PACKAGE, f.now))
        val r = f.barcodes.findProduct(bar) as ProductLookup.Found
        assertTrue(r.product.cached)
        assertEquals("f-noc", r.product.food.id)
        assertEquals(listOf(350.0), r.product.portions.map { it.grams })
        assertTrue(f.offBarcodes.codes.isEmpty())
    }

    @Test fun offHitBecomesPortionsAndWritesNothingUntilCached() = runBlocking {
        f.offBarcodes.products = mapOf(bar to product())
        val before = dumpTables(f.db)
        val r = f.barcodes.findProduct(bar) as ProductLookup.Found
        assertFalse(r.product.cached)
        assertEquals(before, dumpTables(f.db))
        val byKind = r.product.portions.associateBy { it.kind }
        assertEquals(15.0, byKind[PortionKind.SERVING]!!.grams, 0.0); assertTrue(byKind[PortionKind.SERVING]!!.isDefault)
        assertEquals(350.0, byKind[PortionKind.PACKAGE]!!.grams, 0.0); assertFalse(byKind[PortionKind.PACKAGE]!!.isDefault)
        assertFalse(r.product.food.incomplete)
        assertEquals(539.0, r.product.food.kcal100, 0.0)

        val stored = f.barcodes.cache(r.product)
        assertEquals(stored, f.db.foodDao().getByBarcode(bar))
        assertEquals(2, f.db.portionDao().portions(stored.id).size)
        // Next time it is a cache hit with the same portions and no second OFF call.
        val again = f.barcodes.findProduct(bar) as ProductLookup.Found
        assertTrue(again.product.cached); assertEquals(stored.id, again.product.food.id)
        assertEquals(1, f.offBarcodes.codes.size)
        // Caching twice (two scans) never duplicates the product.
        f.barcodes.cache(r.product)
        assertEquals(1, f.db.foodDao().getAll().size); assertEquals(2, f.db.portionDao().allPortions().size)
    }

    @Test fun packageAloneIsTheDefaultPortionAndNoPortionsWhenOffHasNone() = runBlocking {
        f.offBarcodes.products = mapOf(bar to product(serving = null), "111111111111" to product(pack = null, serving = null).copy(code = "111111111111"))
        val a = (f.barcodes.findProduct(bar) as ProductLookup.Found).product.portions.single()
        assertEquals(PortionKind.PACKAGE, a.kind); assertTrue(a.isDefault)
        assertTrue((f.barcodes.findProduct("111111111111") as ProductLookup.Found).product.portions.isEmpty())
    }

    @Test fun missingMacrosAreFlaggedIncompleteNotZero() = runBlocking {
        f.offBarcodes.products = mapOf(bar to product(kcal = null, p = null, c = 12.0, fat = null))
        val food = (f.barcodes.findProduct(bar) as ProductLookup.Found).product.food
        assertTrue(food.incomplete)
        assertEquals(12.0, food.carbs100, 0.0)
        // Stored flagged, so it stays flagged from the cache too.
        f.barcodes.cache((f.barcodes.findProduct(bar) as ProductLookup.Found).product)
        assertTrue((f.barcodes.findProduct(bar) as ProductLookup.Found).product.food.incomplete)
    }

    @Test fun unknownBarcodeIsNotFoundAndANetworkFailureIsFailed() = runBlocking {
        assertEquals(ProductLookup.NotFound, f.barcodes.findProduct("5000000000000"))
        f.offBarcodes.fail = true
        assertTrue(f.barcodes.findProduct("5000000000000") is ProductLookup.Failed)
        // A cached product is still found with the network down.
        f.food.saveFood(Food("f1", FoodSource.OFF, bar, "Nocciolata", null, 539.0, 6.3, 57.5, 30.9, null, false, null))
        assertTrue(f.barcodes.findProduct(bar) is ProductLookup.Found)
    }

    // ---- the scanner screen's own flow (findFood) behaves exactly as before ----

    @Test fun scannerFlowLocalHitWinsWithoutAnyOffCall() = runBlocking {
        f.food.saveFood(Food("f1", FoodSource.OFF, bar, "Nocciolata", null, 539.0, 6.3, 57.5, 30.9, null, false, null))
        assertEquals("f1", (f.barcodes.findFood(bar) as FoodLookup.Found).food.id)
        assertTrue(f.offApi.codes.isEmpty())
    }

    @Test fun scannerFlowCachesTheOffProductAndOpensIt() = runBlocking {
        val off = Food("new-id", FoodSource.OFF, bar, "Nocciolata", "Rigoni", 539.0, 6.3, 57.5, 30.9, 15.0, false, null)
        f.offApi.byCode = mapOf(bar to off)
        val found = (f.barcodes.findFood(bar) as FoodLookup.Found).food
        assertEquals(off, found)
        assertEquals(off, f.food.getByBarcode(bar))
        assertTrue("the scanner flow does not touch portions", f.db.portionDao().allPortions().isEmpty())
        assertTrue("copilot lookup is not used by the scanner flow", f.offBarcodes.codes.isEmpty())
    }

    @Test fun scannerFlowNotFoundAndFailed() = runBlocking {
        assertEquals(FoodLookup.NotFound, f.barcodes.findFood(bar))
        f.offApi.fail = true
        assertEquals(FoodLookup.Failed("offline"), f.barcodes.findFood(bar))
        assertNull(f.food.getByBarcode(bar))
    }

    @Test fun v3ResponseIsMappedWithPackageAndServingAndMlIsNotGrams() {
        val body = """{"code":"$bar","status":"success","product":{"code":"$bar","product_name":"Nocciolata","brands":"Rigoni,Altro",
            "product_quantity":"350","product_quantity_unit":"g","serving_quantity":15,"serving_quantity_unit":"g",
            "nutriments":{"energy-kcal_100g":539,"proteins_100g":6.3,"carbohydrates_100g":57.5,"fat_100g":30.9}}}"""
        val p = OffBarcodeMapper.parse(body, bar)!!
        assertEquals("Rigoni", p.brand); assertEquals(350.0, p.packageG!!, 0.0); assertEquals(15.0, p.servingG!!, 0.0); assertFalse(p.incomplete)
        val ml = OffBarcodeMapper.parse("""{"product":{"product_name":"Latte","product_quantity":1000,"product_quantity_unit":"ml","nutriments":{}}}""", "123")!!
        assertNull(ml.packageG); assertTrue(ml.incomplete); assertEquals("123", ml.code)
        assertNull(OffBarcodeMapper.parse("""{"status":"failure"}""", bar))
        assertNotNull(OffBarcodeMapper.parse(body, bar))
    }
}
