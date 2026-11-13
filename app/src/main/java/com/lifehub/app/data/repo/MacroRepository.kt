package com.lifehub.app.data.repo

import com.lifehub.app.data.db.LifeHubDatabase
import com.lifehub.app.data.model.MacroDay
import com.lifehub.app.domain.autoKcal
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

interface MacroRepository {
    fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<MacroDay>>
    suspend fun get(date: LocalDate): MacroDay?
    /** F-NUT-1/2. kcal null => autoKcal + kcalIsAuto=true. */
    suspend fun upsert(date: LocalDate, proteinG: Int, carbsG: Int, fatG: Int, kcal: Int?, now: Instant)
    suspend fun delete(date: LocalDate)
    /** Most recent logged day strictly before [date] (for "Copy yesterday"). */
    suspend fun latestBefore(date: LocalDate): MacroDay?
}

class MacroRepositoryImpl(db: LifeHubDatabase) : MacroRepository {
    private val dao = db.macroDao()
    override fun observeBetween(from: LocalDate, to: LocalDate) = dao.observeBetween(from, to)
    override suspend fun get(date: LocalDate) = dao.get(date)
    override suspend fun upsert(date: LocalDate, proteinG: Int, carbsG: Int, fatG: Int, kcal: Int?, now: Instant) {
        dao.upsert(
            MacroDay(date, proteinG, carbsG, fatG, kcal ?: autoKcal(proteinG, carbsG, fatG), kcal == null, now)
        )
    }
    override suspend fun delete(date: LocalDate) = dao.delete(date)
    override suspend fun latestBefore(date: LocalDate) = dao.latestBefore(date)
}
