package com.lifehub.app.data.off

import com.lifehub.app.data.model.FoodSource
import org.junit.Assert.*
import org.junit.Test

class OpenFoodFactsTest {
    private val ids = generateSequence(1) { it + 1 }.iterator()
    private val nid = { "id${ids.next()}" }

    private val searchJson = """
      {"count":3,"page":1,"products":[
        {"code":"3017620422003","product_name":"Nutella","brands":"Ferrero,Nutella","serving_size":"15 g",
         "image_url":"https://images.openfoodfacts.org/n/front_en.400.jpg","image_front_small_url":"https://images.openfoodfacts.org/n/front_en.200.jpg",
         "nutriments":{"energy-kcal_100g":539,"proteins_100g":6.3,"carbohydrates_100g":57.5,"fat_100g":30.9,"salt_100g":0.1}},
        {"code":"111","product_name":"Kj only","brands":"","serving_size":"1 slice (30g)",
         "nutriments":{"energy_100g":418.4,"proteins_100g":"10","carbohydrates_100g":"20,5","fat_100g":2}},
        {"code":"222","product_name":"","nutriments":{"energy-kcal_100g":50}},
        {"code":"333","product_name":"No data","nutriments":{}},
        {"code":"444","product_name":"Macros only","nutriments":{"proteins_100g":10,"carbohydrates_100g":10,"fat_100g":10}}
      ]}"""

    @Test fun mapsSearchResults() {
        val r = OffMapper.parseSearch(searchJson, nid)
        assertEquals(listOf("Nutella", "Kj only", "Macros only"), r.map { it.name })
        val n = r[0]
        assertEquals(FoodSource.OFF, n.source); assertEquals("3017620422003", n.barcode); assertEquals("Ferrero", n.brand)
        assertEquals(539.0, n.kcal100, 0.0); assertEquals(6.3, n.protein100, 0.0); assertEquals(57.5, n.carbs100, 0.0); assertEquals(30.9, n.fat100, 0.0)
        assertEquals(15.0, n.servingSizeG!!, 0.0)
        assertFalse(n.favorite); assertNull(n.lastUsedAt)
        assertEquals("https://images.openfoodfacts.org/n/front_en.200.jpg", n.imageUrl)
        assertNull(r[1].imageUrl)
    }

    @Test fun kjFallbackAndStringNumbers() {
        val k = OffMapper.parseSearch(searchJson, nid)[1]
        assertEquals(100.0, k.kcal100, 0.01)   // 418.4 kJ / 4.184
        assertEquals(10.0, k.protein100, 0.0); assertEquals(20.5, k.carbs100, 0.0)
        assertNull(k.brand); assertEquals(30.0, k.servingSizeG!!, 0.0)
    }

    @Test fun kcalComputedWhenMissing() {
        val m = OffMapper.parseSearch(searchJson, nid)[2]
        assertEquals(4 * 10 + 4 * 10 + 9 * 10.0, m.kcal100, 1e-9); assertNull(m.servingSizeG)
    }

    @Test fun servingParsing() {
        assertEquals(30.0, OffMapper.parseServing("30 g")!!, 0.0); assertEquals(250.0, OffMapper.parseServing("250ml")!!, 0.0)
        assertEquals(12.5, OffMapper.parseServing("1 portion (12,5 g)")!!, 0.0)
        assertNull(OffMapper.parseServing("1 slice")); assertNull(OffMapper.parseServing(null)); assertNull(OffMapper.parseServing("0 g"))
    }

    @Test fun barcodeProduct() {
        val ok = """{"code":"5449000000996","status":1,"product":{"product_name":"Coca-Cola","brands":"Coca-Cola",
            "nutriments":{"energy-kcal_100g":42,"proteins_100g":0,"carbohydrates_100g":10.6,"fat_100g":0},"serving_size":"330 ml"}}"""
        val f = OffMapper.parseProduct(ok, "5449000000996", nid)!!
        assertEquals("5449000000996", f.barcode); assertEquals("Coca-Cola", f.name); assertEquals(42.0, f.kcal100, 0.0); assertEquals(330.0, f.servingSizeG!!, 0.0)
        assertNull(OffMapper.parseProduct("""{"code":"1","status":0,"status_verbose":"product not found"}""", "1", nid))
        assertNull(OffMapper.parseProduct("""{"status":1,"product":{"product_name":"X","nutriments":{}}}""", "1", nid))
    }

    @Test fun emptyOrUnexpectedBodies() {
        assertTrue(OffMapper.parseSearch("""{"count":0,"products":[]}""", nid).isEmpty())
        assertTrue(OffMapper.parseSearch("""{"foo":1}""", nid).isEmpty())
    }
}
