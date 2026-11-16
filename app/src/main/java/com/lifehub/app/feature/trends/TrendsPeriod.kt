package com.lifehub.app.feature.trends

import com.lifehub.app.data.model.HomePeriod

/**
 * Trends period lengths: 2W (default) and 4W. Previous-period comparisons use the same length.
 *
 * Persisted through the existing [HomePeriod] setting: the stored ONE_WEEK (also the setting's default) means 2W and
 * TWO_WEEKS means 4W, so no data/prefs migration is needed.
 */
enum class TrendsPeriod(val days: Int, val label: String) {
    TWO_WEEKS(14, "2W"),
    FOUR_WEEKS(28, "4W");

    fun toStored(): HomePeriod = if (this == FOUR_WEEKS) HomePeriod.TWO_WEEKS else HomePeriod.ONE_WEEK

    companion object {
        fun fromStored(p: HomePeriod): TrendsPeriod = if (p == HomePeriod.TWO_WEEKS) FOUR_WEEKS else TWO_WEEKS

        /** Deep-link `period` arg: "2W" / "4W"; the retired "1W" maps to 2W. Null for absent/unknown. */
        fun parse(s: String?): TrendsPeriod? = when (s) {
            "1W", "2W" -> TWO_WEEKS
            "4W" -> FOUR_WEEKS
            else -> null
        }

        /**
         * Offset for a deep link. Old 1W links count single weeks back; a 2W period holds two weeks (the current
         * one covers this week and last week), so week k back lives in 2W period k / 2.
         */
        fun deepLinkOffset(period: String?, offset: Int): Int = (if (period == "1W") offset / 2 else offset).coerceAtLeast(0)
    }
}
