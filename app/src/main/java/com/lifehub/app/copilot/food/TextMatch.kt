package com.lifehub.app.copilot.food

import java.text.Normalizer

/**
 * Tolerant food-name matching for the copilot (design doc §6 "match tollerante su nome e sinonimi").
 * Lower case, no accents, punctuation as spaces, Italian/English stop words dropped, a crude stem that makes singular
 * and plural / masculine and feminine equal ("gratinato" = "gratinati" = "gratinata"), and the raw/cooked words removed
 * (state is a separate argument).
 */
object TextMatch {
    private val STOP = setOf(
        "di", "del", "dello", "della", "dei", "degli", "delle", "d", "al", "allo", "alla", "ai", "agli", "alle", "con", "e", "ed",
        "il", "lo", "la", "i", "gli", "le", "l", "un", "uno", "una", "in", "da", "per", "su", "a", "the", "of", "with", "and",
        "solito", "solita", "soliti", "solite", "mio", "mia", "miei", "mie",
    )
    /** Stems of crudo/cruda/crudi/crude and cotto/cotta/cotti/cotte: state words, not part of the name. */
    private val STATE_WORDS = setOf("crud", "cott", "raw", "cooked")

    fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()
            .replace(Regex("[^a-z0-9%]+"), " ").trim()

    /** Drops trailing vowels while longer than 3 letters: cucchiaio / cucchiai -> "cucch", uovo / uova -> "uov". */
    fun stem(t: String): String {
        var s = t
        while (s.length > 3 && s.last() in "aeiou") s = s.dropLast(1)
        return s
    }

    fun tokens(s: String?): Set<String> = if (s.isNullOrBlank()) emptySet() else
        normalize(s).split(' ').asSequence().filter { it.isNotEmpty() && it !in STOP }.map(::stem).filter { it !in STATE_WORDS }.toSet()

    /** True when the text names the cooked state ("cotto", "cotta", ...). */
    fun saysCooked(s: String): Boolean = normalize(s).split(' ').any { stem(it) == "cott" }

    fun splitAliases(aliases: String?): List<String> = aliases?.split('|')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

    /**
     * How well [query] names a food called [name] (+ [brand], [aliases]).
     * - exact: the query's words are exactly the words of the name, the name + brand, or one alias;
     * - strong: exact, or every query word is in the food (name, brand, aliases) and the query covers at least 2/3 of
     *   the words of the name or of one alias (so "pollo" is not a strong match for "Petto di pollo");
     * - plausible: at least half of the query words appear in the food.
     */
    fun score(query: String, name: String, brand: String? = null, aliases: List<String> = emptyList()): Match {
        val q = tokens(query)
        if (q.isEmpty()) return Match.NONE
        val nameT = tokens(name)
        val brandT = tokens(brand)
        val aliasT = aliases.map(::tokens).filter { it.isNotEmpty() }
        val all = nameT + brandT + aliasT.flatten()
        val common = (q intersect all).size
        val exact = q == nameT || (brandT.isNotEmpty() && q == nameT + brandT) || aliasT.any { it == q }
        val coverage = (listOf(nameT) + aliasT).filter { it.isNotEmpty() }.maxOfOrNull { (it intersect q).size.toDouble() / it.size } ?: 0.0
        val strong = exact || (q.all { it in all } && coverage >= 2.0 / 3 - 1e-9)
        val plausible = common >= maxOf(1, (q.size + 1) / 2)
        return Match(exact, strong, plausible, coverage, common)
    }

    data class Match(val exact: Boolean, val strong: Boolean, val plausible: Boolean, val coverage: Double, val common: Int) : Comparable<Match> {
        override fun compareTo(other: Match): Int = compareValuesBy(this, other, { it.exact }, { it.strong }, { it.coverage }, { it.common })
        companion object { val NONE = Match(false, false, false, 0.0, 0) }
    }
}
