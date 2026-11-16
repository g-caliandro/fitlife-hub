package com.lifehub.app.copilot.food

import com.lifehub.app.copilot.FakeOff
import com.lifehub.app.data.model.BaseFood
import com.lifehub.app.data.model.EntrySource
import com.lifehub.app.data.model.Food
import com.lifehub.app.data.model.FoodPortion
import com.lifehub.app.data.model.FoodSource
import com.lifehub.app.data.model.FoodState
import com.lifehub.app.data.model.HouseholdMeasure
import com.lifehub.app.data.model.PortionKind
import com.lifehub.app.data.off.OffProduct
import com.lifehub.app.data.off.OffSearchMapper
import com.lifehub.app.data.off.SearchaliciousClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class FoodResolverTest {
    private class FakeCatalog(
        var foods: List<Food> = emptyList(),
        var base: List<BaseFood> = emptyList(),
        var portions: List<FoodPortion> = emptyList(),
    ) : FoodCatalog {
        override suspend fun historyFoods() = foods.filter { it.lastUsedAt != null || it.favorite || it.source == FoodSource.CUSTOM || it.source == FoodSource.ESTIMATE }
        override suspend fun food(id: String) = foods.firstOrNull { it.id == id }
        override suspend fun foodByBarcode(code: String) = foods.firstOrNull { it.barcode == code }
        override suspend fun foodByBaseId(baseId: String) = foods.firstOrNull { it.baseFoodId == baseId }
        override suspend fun portions(foodId: String) = portions.filter { it.foodId == foodId }
        override suspend fun baseFoods() = base
        override suspend fun baseFood(id: String) = base.firstOrNull { it.id == id }
        override suspend fun measures() = emptyList<HouseholdMeasure>()
    }

    private fun food(id: String, name: String, brand: String? = null, used: Long? = 1000, source: FoodSource = FoodSource.OFF, state: FoodState? = null, kcal: Double = 100.0, aliases: String? = null) =
        Food(id, source, null, name, brand, kcal, 10.0, 10.0, 1.0, null, false, used?.let(Instant::ofEpochMilli), state = state, aliases = aliases)

    private fun base(id: String, name: String, aliases: String? = null, state: FoodState? = null) = BaseFood(id, name, aliases, state, 100.0, 5.0, 10.0, 1.0, null)
    private val baseTable = listOf(
        base("rice", "Riso bianco", "riso|riso basmati", FoodState.RAW),
        base("rice-c", "Riso bianco cotto", "riso|riso basmati", FoodState.COOKED),
        base("chicken", "Petto di pollo", "petto di pollo|pollo petto", FoodState.RAW),
        base("chicken-c", "Petto di pollo arrosto", "petto di pollo", FoodState.COOKED),
        base("carrot", "Carote", "carota|carote", FoodState.RAW),
        base("oil", "Olio d'oliva", "olio|olio d'oliva"),
        base("seed-oil", "Olio di semi di girasole", "olio di semi"),
        base("y1", "Yogurt greco intero"), base("y2", "Yogurt greco 2%"),
    )
    private val off = FakeOff()
    private val cache = OffCandidateCache()
    private fun resolver(c: FakeCatalog) = FoodResolver(c, off, cache)
    private fun product(code: String, name: String, brand: String?, protein: Double? = 10.0, pkg: Double? = 400.0) =
        OffProduct(code, name, brand, 150.0, protein, 9.0, 7.0, pkg, null)

    @Test fun historyStrongMatchWinsAndStopsTheSearch() = runBlocking {
        val c = FakeCatalog(foods = listOf(food("f1", "Petto di pollo", kcal = 110.0, source = FoodSource.CUSTOM)), base = baseTable)
        val r = resolver(c).resolve("petto di pollo")
        assertEquals(ResolveStatus.RESOLVED, r.status)
        assertEquals(EntrySource.HISTORY, r.food!!.source); assertEquals("food:f1", r.food!!.ref); assertEquals(110.0, r.food!!.kcal100, 0.0)
        assertTrue("OFF must not be queried when an earlier step resolved", off.queries.isEmpty())
    }

    @Test fun mostRecentlyUsedWinsAmongStrongMatches() = runBlocking {
        val older = food("old", "Merluzzo gratinato", "Findus", used = 1_000, kcal = 140.0)
        val newer = food("new", "Merluzzo gratinato", "Findus", used = 9_000, kcal = 150.0)
        val r = resolver(FakeCatalog(foods = listOf(older, newer))).resolve("merluzzo Findus gratinato")
        assertEquals("food:new", r.food!!.ref)
    }

    @Test fun weakHistoryMatchNeverWinsAndFallsThroughToTheBaseTable() = runBlocking {
        // "Riso" in history is only a weak match for "riso basmati": the base table entry is used instead.
        val c = FakeCatalog(foods = listOf(food("f-rice", "Riso", source = FoodSource.CUSTOM)), base = baseTable)
        val r = resolver(c).resolve("riso basmati")
        assertEquals(ResolveStatus.RESOLVED, r.status); assertEquals("base:rice", r.food!!.ref); assertEquals(EntrySource.BASE_TABLE, r.food!!.source)
    }

    @Test fun weakMatchesOnlyGiveAPendingChoiceNotAWinner() = runBlocking {
        // "pollo" is a weak match for both (2/3 coverage rule): nothing is picked, candidates go to the user.
        val c = FakeCatalog(foods = listOf(food("a", "Petto di pollo", used = 5), food("b", "Cosce di pollo", used = 9)))
        val r = resolver(c).resolve("pollo")
        assertEquals(ResolveStatus.AMBIGUOUS, r.status); assertNull(r.food)
        assertEquals(setOf("food:a", "food:b"), r.candidates.map { it.ref }.toSet())
    }

    @Test fun baseTableRawIsTheDefaultAndCookedOnRequest() = runBlocking {
        val r = resolver(FakeCatalog(base = baseTable))
        assertEquals("base:rice", r.resolve("riso basmati").food!!.ref)
        assertEquals("base:rice-c", r.resolve("riso basmati", FoodState.COOKED).food!!.ref)
        assertEquals("base:rice-c", r.resolve("riso basmati cotto").food!!.ref)   // state said inside the name
        assertEquals("base:chicken", r.resolve("petto di pollo").food!!.ref)
        assertEquals("base:oil", r.resolve("olio").food!!.ref)
    }

    @Test fun cookedRequestedButOnlyRawExistsIsFlagged() = runBlocking {
        val f = resolver(FakeCatalog(base = baseTable)).resolve("carote", FoodState.COOKED).food!!
        assertEquals("base:carrot", f.ref); assertTrue(f.stateMismatch)
    }

    @Test fun equallyGoodBaseMatchesAreAmbiguous() = runBlocking {
        val r = resolver(FakeCatalog(base = baseTable)).resolve("yogurt greco")
        assertEquals(ResolveStatus.AMBIGUOUS, r.status)
        assertEquals(setOf("base:y1", "base:y2"), r.candidates.map { it.ref }.take(2).toSet())
    }

    @Test fun openFoodFactsSingleStrongCompleteProductResolves() = runBlocking {
        off.products = listOf(product("800", "Merluzzo gratinato", "Findus"), product("801", "Bastoncini", "Findus"))
        val r = resolver(FakeCatalog(base = baseTable)).resolve("merluzzo gratinato Findus")
        assertEquals(ResolveStatus.RESOLVED, r.status)
        val f = r.food!!
        assertEquals("off:800", f.ref); assertEquals(EntrySource.OPEN_FOOD_FACTS, f.source)
        assertEquals(listOf(PortionInfo("package", 400.0, PortionKind.PACKAGE, true)), f.portions)
        assertEquals(f, cache.get("800"))
    }

    @Test fun incompleteProductIsFlaggedAndNeverResolvedAutomatically() = runBlocking {
        off.products = listOf(product("900", "Merluzzo gratinato", "Findus", protein = null))
        val r = resolver(FakeCatalog()).resolve("merluzzo gratinato Findus")
        assertEquals(ResolveStatus.AMBIGUOUS, r.status)
        val c = r.candidates.single()
        assertTrue(c.incomplete); assertEquals(0.0, c.protein100, 0.0)
    }

    @Test fun severalPlausibleProductsAreAmbiguous() = runBlocking {
        off.products = listOf(product("1", "I gratinati", "Findus"), product("2", "Merluzzo gratinato", "Crai"))
        val r = resolver(FakeCatalog()).resolve("merluzzo Findus gratinato")
        assertEquals(ResolveStatus.AMBIGUOUS, r.status); assertEquals(2, r.candidates.size)
    }

    @Test fun unknownFoodIsNotFoundAndOffErrorsAreReported() = runBlocking {
        off.fail = true
        val r = resolver(FakeCatalog(base = baseTable)).resolve("polpette della nonna Pina")
        assertEquals(ResolveStatus.NOT_FOUND, r.status); assertTrue(r.offUnavailable); assertNull(r.food)
    }

    @Test fun stepOrderHistoryThenBaseThenOff() = runBlocking {
        off.products = listOf(product("5", "Olio d'oliva", "Brand"))
        // Base wins over OFF for an unpackaged food...
        assertEquals("base:oil", resolver(FakeCatalog(base = baseTable)).resolve("olio d'oliva").food!!.ref)
        assertTrue(off.queries.isEmpty())
        // ...and history wins over base.
        val mine = food("mine", "Olio d'oliva", source = FoodSource.CUSTOM)
        assertEquals("food:mine", resolver(FakeCatalog(foods = listOf(mine), base = baseTable)).resolve("olio d'oliva").food!!.ref)
        // Without either, OFF is asked.
        assertEquals("off:5", resolver(FakeCatalog()).resolve("olio d'oliva").food!!.ref)
        assertEquals(1, off.queries.size)
    }

    @Test fun historyStateMustFit() = runBlocking {
        val cooked = food("rc", "Riso basmati", state = FoodState.COOKED, source = FoodSource.CUSTOM)
        val r = resolver(FakeCatalog(foods = listOf(cooked), base = baseTable)).resolve("riso basmati")
        assertEquals("base:rice", r.food!!.ref)
    }

    @Test fun legacyServingSizeBecomesTheDefaultPortion() {
        val f = food("s", "Yogurt").copy(servingSizeG = 125.0)
        assertEquals(listOf(PortionInfo("serving", 125.0, PortionKind.SERVING, true)), Candidates.portionsOf(f, emptyList()))
        val pkg = FoodPortion("p", "s", "package", 500.0, true, PortionKind.PACKAGE, Instant.EPOCH)
        assertEquals(listOf(PortionInfo("package", 500.0, PortionKind.PACKAGE, true), PortionInfo("serving", 125.0, PortionKind.SERVING, false)),
            Candidates.portionsOf(f, listOf(pkg)))
    }

    @Test fun textMatchIsTolerant() {
        assertTrue(TextMatch.score("merluzzo gratinati findus", "Merluzzo gratinato", "Findus").exact)
        assertTrue(TextMatch.score("Petto  di Pollo", "petto di pollo").exact)
        assertTrue(TextMatch.score("caffè", "Caffe").exact)
        assertFalse(TextMatch.score("pollo", "Petto di pollo").strong)
        assertTrue(TextMatch.score("pollo", "Petto di pollo").plausible)
        assertTrue(TextMatch.score("filetti merluzzo gratinati", "Filetti di merluzzo gratinati alla mediterranea").plausible)
        assertFalse(TextMatch.score("uova", "Pane").plausible)
    }

    @Test fun searchaliciousHitsAreParsed() {
        val body = """{"hits":[
            {"code":"1","product_name":"Merluzzo gratinato","brands":["Findus","X"],"product_quantity":"400","product_quantity_unit":"g",
             "serving_quantity":200,"nutriments":{"energy-kcal_100g":152,"proteins_100g":11.1,"carbohydrates_100g":9.4,"fat_100g":7.6}},
            {"code":"2","product_name_it":"Latte","product_name":"Milk","brands":"Granarolo, Y","product_quantity":1000,"product_quantity_unit":"ml",
             "nutriments":{"energy-kj_100g":209.2,"proteins_100g":3.2,"carbohydrates_100g":4.8}},
            {"product_name":"no code"}
        ]}"""
        val p = OffSearchMapper.parseHits(body)
        assertEquals(2, p.size)
        assertEquals(OffProduct("1", "Merluzzo gratinato", "Findus", 152.0, 11.1, 9.4, 7.6, 400.0, 200.0), p[0])
        assertEquals("Latte", p[1].name); assertEquals("Granarolo", p[1].brand); assertEquals(50.0, p[1].kcal100!!, 1e-9)
        assertNull("ml is not grams", p[1].packageG); assertNull(p[1].fat100); assertTrue(p[1].incomplete)
    }

    @Test fun searchClientRateLimitsLocally() {
        var now = 0L
        val client = SearchaliciousClient(maxPerMinute = 2, clock = { now })
        assertTrue(client.acquire()); assertTrue(client.acquire())
        assertFalse("third search in the same minute is refused before any request", client.acquire())
        now = 61_000
        assertTrue(client.acquire())
    }
}
