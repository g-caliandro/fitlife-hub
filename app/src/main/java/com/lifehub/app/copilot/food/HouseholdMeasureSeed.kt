package com.lifehub.app.copilot.food

import com.lifehub.app.data.model.HouseholdMeasure
import java.time.Instant

/**
 * DEFAULT HOUSEHOLD MEASURES — PROVISIONAL, NOT YET VALIDATED BY THE OWNER (decision 20).
 *
 * This list is the ONLY place the default values live: change a number here and both fresh installs (DB onCreate) and the
 * v9 migration use it. The user can edit every row afterwards in Copilot settings > Household measures (the table is
 * seeded once; later changes here do not overwrite rows the user already has).
 *
 * Source: docs/copilot/phase0-research.md §2 (LARN spoon weights via dica33.it; other values are common kitchen
 * estimates marked "da verificare" there). Convention: "cucchiaio" / "cucchiaino" without qualifier = LEVEL (raso);
 * "colmo" only when said. Pieces are the EDIBLE weight, because the food values are per 100 g of edible part.
 * Food-specific rows point at base table ids (`sr-<USDA fdc id>`, see assets/copilot/base_foods.json).
 */
object HouseholdMeasureSeed {
    data class Row(val label: String, val grams: Double, val baseFoodIds: List<String> = emptyList())

    private const val OLIVE_OIL = "sr-171413"
    private const val SUGAR = "sr-169655"
    private const val HONEY = "sr-169640"
    private val RICE_RAW = listOf("sr-169756", "sr-168881", "sr-169703")
    private const val FLOUR = "sr-168894"
    private const val JAM = "sr-169641"
    private const val MAYO = "sr-171009"
    private const val COCOA = "sr-169593"
    private const val PARMESAN = "sr-170848"
    private const val BUTTER = "sr-173430"
    private const val PEANUT_BUTTER = "sr-172470"
    private val MILK = listOf("sr-171265", "sr-171267", "sr-171269")
    private val YOGURT = listOf("sr-171284", "sr-170886", "sr-171304", "sr-170903", "sr-170894")
    private val EGG = listOf("sr-171287", "sr-173424", "sr-173423")
    private val MOZZARELLA = listOf("sr-170845", "sr-170847")
    private val BREAKFAST_CEREALS = listOf("sr-174648", "sr-171646")

    val rows: List<Row> = listOf(
        // Generic (used when the food has no specific row).
        Row("cucchiaio", 10.0),
        Row("cucchiaino", 5.0),
        Row("bicchiere", 200.0),
        Row("tazza", 250.0),
        Row("tazzina", 40.0),
        Row("pugno", 30.0),
        Row("fetta", 30.0),
        // Food-specific.
        Row("cucchiaio", 9.0, listOf(OLIVE_OIL)),
        Row("cucchiaino", 5.0, listOf(OLIVE_OIL)),
        Row("cucchiaio", 9.0, listOf(SUGAR)), Row("cucchiaio colmo", 16.0, listOf(SUGAR)),
        Row("cucchiaino", 5.0, listOf(SUGAR)), Row("cucchiaino colmo", 10.0, listOf(SUGAR)),
        Row("cucchiaio", 14.0, listOf(HONEY)), Row("cucchiaio colmo", 22.0, listOf(HONEY)),
        Row("cucchiaino", 7.0, listOf(HONEY)), Row("cucchiaino colmo", 8.0, listOf(HONEY)),
        Row("cucchiaio", 10.0, RICE_RAW), Row("cucchiaio colmo", 17.0, RICE_RAW),
        Row("cucchiaino", 6.0, RICE_RAW), Row("cucchiaino colmo", 9.0, RICE_RAW), Row("bicchiere", 150.0, RICE_RAW),
        Row("cucchiaio", 7.0, listOf(FLOUR)), Row("cucchiaio colmo", 15.0, listOf(FLOUR)),
        Row("cucchiaino", 4.0, listOf(FLOUR)), Row("cucchiaino colmo", 7.0, listOf(FLOUR)),
        Row("cucchiaio colmo", 27.0, listOf(JAM)), Row("cucchiaino colmo", 10.0, listOf(JAM)),
        Row("cucchiaio", 9.0, listOf(MAYO)), Row("cucchiaio colmo", 18.0, listOf(MAYO)),
        Row("cucchiaino", 5.0, listOf(MAYO)), Row("cucchiaino colmo", 9.0, listOf(MAYO)),
        Row("cucchiaio", 5.0, listOf(COCOA)), Row("cucchiaio colmo", 9.0, listOf(COCOA)),
        Row("cucchiaino", 2.0, listOf(COCOA)), Row("cucchiaino colmo", 4.0, listOf(COCOA)),
        Row("cucchiaio", 7.0, listOf(PARMESAN)), Row("cucchiaio colmo", 10.0, listOf(PARMESAN)),
        Row("cucchiaino", 3.0, listOf(PARMESAN)), Row("cucchiaino colmo", 4.0, listOf(PARMESAN)),
        Row("cucchiaio", 12.0, listOf(BUTTER)), Row("cucchiaio colmo", 20.0, listOf(BUTTER)),
        Row("cucchiaino", 5.0, listOf(BUTTER)), Row("noce", 10.0, listOf(BUTTER)),
        Row("cucchiaio", 15.0, listOf(PEANUT_BUTTER)), Row("cucchiaio colmo", 20.0, listOf(PEANUT_BUTTER)),
        Row("cucchiaino", 7.0, listOf(PEANUT_BUTTER)),
        Row("cucchiaio", 15.0, MILK), Row("cucchiaino", 5.0, MILK), Row("bicchiere", 200.0, MILK), Row("tazza", 250.0, MILK),
        Row("cucchiaio", 20.0, YOGURT), Row("cucchiaino", 7.0, YOGURT), Row("vasetto", 125.0, YOGURT),
        Row("fetta", 25.0, listOf("sr-174924")),            // pane in cassetta
        Row("fetta", 45.0, listOf("sr-174913", "sr-172688", "sr-172684")), // pane comune / integrale / segale
        Row("panino", 50.0, listOf("sr-174913")),
        Row("pezzo", 50.0, EGG),
        Row("pezzo", 32.0, listOf("sr-172183")),           // albume
        Row("pezzo", 18.0, listOf("sr-172184")),           // tuorlo
        Row("pezzo", 130.0, listOf("sr-171688")),          // mela, edibile
        Row("pezzo", 130.0, listOf("sr-169118")),          // pera, edibile
        Row("pezzo", 120.0, listOf("sr-173944")),          // banana sbucciata
        Row("fetta", 15.0, listOf("sr-173864")),           // prosciutto cotto
        Row("pezzo", 125.0, MOZZARELLA),
        Row("ciotola", 40.0, BREAKFAST_CEREALS),
    )

    /** Deterministic ids, so a re-seed never duplicates rows. */
    fun measures(now: Instant = Instant.EPOCH): List<HouseholdMeasure> = rows.flatMap { r ->
        val slug = r.label.replace(' ', '-')
        if (r.baseFoodIds.isEmpty()) listOf(HouseholdMeasure("hm-$slug", r.label, r.grams, null, null, now))
        else r.baseFoodIds.map { HouseholdMeasure("hm-$slug-$it", r.label, r.grams, null, it, now) }
    }

    /** INSERT OR IGNORE statements for the migration and the onCreate callback (no Room DAO available there). */
    fun insertSql(): List<String> = measures().map { m ->
        "INSERT OR IGNORE INTO household_measures (id, label, grams, foodId, baseFoodId, updatedAt) VALUES " +
            "('${m.id}', '${m.label.replace("'", "''")}', ${m.grams}, NULL, ${m.baseFoodId?.let { "'$it'" } ?: "NULL"}, 0)"
    }
}
