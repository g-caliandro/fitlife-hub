package com.lifehub.app.copilot.capture

import com.lifehub.app.copilot.food.Candidates
import com.lifehub.app.copilot.food.FoodCatalog
import com.lifehub.app.copilot.tools.candidateJson
import com.lifehub.app.copilot.tools.fmt
import com.lifehub.app.data.repo.ScannedProduct

/**
 * The model-facing section for a product scanned in the composer and attached to a capture (design doc §8): rebuilt from the
 * cached food on every run, so a retry or a resume after an app restart has the same information. Same shape as the
 * `resolve_food` / `get_product_by_barcode` results, with the `ref` `log_meal` needs.
 */
class AttachedProductContext(private val catalog: FoodCatalog) {
    suspend fun section(foodId: String): String? {
        val food = catalog.food(foodId) ?: return null
        val c = Candidates.fromScanned(ScannedProduct(food, catalog.portions(foodId), cached = true))
        val measures = Candidates.measuresFor(c, catalog.measures()).filter { it.specific }.map { "${it.label} ${fmt(it.grams)} g" }
        return buildString {
            append("Il messaggio dell'utente ha un prodotto allegato (scansionato con il codice a barre${food.barcode?.let { " $it" } ?: ""}): ")
            append(candidateJson(c, measures).toString())
            append("\nSe l'utente parla di \"questo prodotto\", \"ne ho mangiati 30 grammi\" o simili, si riferisce a questo prodotto: registralo con log_meal usando questo ref, senza resolve_food. ")
            append("Quantità: grams, oppure servings con la porzione indicata in portions (es. mezza confezione = servings 0.5, portion \"package\").")
            if (c.incomplete) append(" Mancano alcuni macro su Open Food Facts: dillo all'utente, il log sarà segnato come incompleto.")
            if (c.portions.none { it.kind == com.lifehub.app.data.model.PortionKind.PACKAGE }) append(" Il peso della confezione non è noto: per frazioni di confezione usa request_user_input kind package_weight con food_ref.")
        }
    }
}
