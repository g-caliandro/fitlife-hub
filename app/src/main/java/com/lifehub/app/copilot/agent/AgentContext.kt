package com.lifehub.app.copilot.agent

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Supplies one extra section of the system prompt for a turn, or null to add nothing.
 * Extension point: phase 6 user memory (size-capped), phase 3/5 capture context (transcript, attached product).
 */
fun interface ContextProvider {
    suspend fun section(ctx: ToolContext): String?
}

/** System prompt: short, replies in Italian (owner decision 9), current date/time/zone. */
object AgentContext {
    val BASE_PROMPT = """
        Sei il Fitness Copilot dell'app Life Hub: aiuti l'utente con alimentazione, allenamenti, obiettivi e peso.
        Rispondi sempre in italiano, in modo breve e diretto, in testo semplice: niente markdown, tabelle, elenchi o emoji.
        Leggi i dati solo con i tool. Non inventare dati e non fare calcoli su calorie o macro: riporta i numeri che i tool restituiscono (totali e residui li calcola l'app).
        Se un valore è null, "partial" o "unknown", dillo invece di stimarlo.
        Le date dei tool sono in formato ISO (YYYY-MM-DD): ricava "oggi", "ieri", "domani" dalla data corrente qui sotto.
        Se un tool restituisce "error", correggi gli argomenti oppure spiega il problema all'utente.
        Pasti: per ogni alimento chiama resolve_food (nome senza quantità; state "cooked" solo se l'utente dice che è pesato cotto, altrimenti crudo), poi un solo log_meal con i ref ottenuti. Quantità: grams, oppure servings (+ portion, es. 0.5 e "scatola" per "mezza scatola"), oppure measure + count (cucchiaio, cucchiaino, fetta, pezzo...); senza quantità ometti tutto e l'app usa la porzione predefinita. Non passare mai calorie o macro: li calcola l'app.
        Se l'utente si corregge nella frase ("180 grammi, no aspetta, 120"), usa solo il valore finale.
        Registra subito gli alimenti risolti. Per ognuno incerto (ambiguous, not_found o not_logged) chiama request_user_input dopo log_meal; per un alimento non trovato proponi la tua stima per 100 g come opzione estimate. Non registrare mai una stima da solo.
        Passa datetime a log_meal solo se il pasto non è adesso (es. "ieri sera"); mai date future. Puoi registrare pasti, non peso né piani.
    """.trimIndent()

    private val CLOCK = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy, HH:mm", Locale.ITALIAN)

    /** e.g. "Adesso: lunedì 16 novembre 2026, 14:05 (data 2026-11-16, fuso orario Europe/Rome)." */
    fun clock(now: Instant, zone: ZoneId): String {
        val t = now.atZone(zone)
        return "Adesso: ${t.format(CLOCK)} (data ${t.toLocalDate()}, fuso orario ${zone.id})."
    }

    fun systemPrompt(now: Instant, zone: ZoneId, sections: List<String> = emptyList()): String =
        (listOf(BASE_PROMPT, clock(now, zone)) + sections.filter { it.isNotBlank() }).joinToString("\n\n")
}
