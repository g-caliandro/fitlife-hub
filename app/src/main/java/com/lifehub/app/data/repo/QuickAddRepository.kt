package com.lifehub.app.data.repo

import com.lifehub.app.data.db.LifeHubDatabase
import com.lifehub.app.data.model.QuickAddEntry
import com.lifehub.app.domain.autoKcal
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Quick-add entries: kcal (required) + optional protein/carbs/fat (null = unknown) + optional name; many per day. */
interface QuickAddRepository {
    fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<QuickAddEntry>>
    suspend fun getDay(date: LocalDate): List<QuickAddEntry>
    suspend fun get(id: String): QuickAddEntry?
    /** Most recent quick add strictly before [date] (for "Copy ..."). */
    suspend fun latestBefore(date: LocalDate): QuickAddEntry?
    /**
     * Creates ([id] null) or updates an entry. kcal null => 4·P + 4·C + 9·F, which needs all three macros
     * (otherwise IllegalArgumentException). Returns the stored entry.
     */
    suspend fun save(
        id: String?, date: LocalDate, name: String?, kcal: Int?, proteinG: Int?, carbsG: Int?, fatG: Int?, now: Instant,
    ): QuickAddEntry
    /** Puts back an exact entry (undo / copy). */
    suspend fun restore(e: QuickAddEntry)
    suspend fun delete(id: String)
}

class QuickAddRepositoryImpl(db: LifeHubDatabase) : QuickAddRepository {
    private val dao = db.quickAddDao()
    override fun observeBetween(from: LocalDate, to: LocalDate) = dao.observeBetween(from, to)
    override suspend fun getDay(date: LocalDate) = dao.getDay(date)
    override suspend fun get(id: String) = dao.get(id)
    override suspend fun latestBefore(date: LocalDate) = dao.latestBefore(date)

    override suspend fun save(
        id: String?, date: LocalDate, name: String?, kcal: Int?, proteinG: Int?, carbsG: Int?, fatG: Int?, now: Instant,
    ): QuickAddEntry {
        val auto = kcal == null
        val k = kcal ?: run {
            require(proteinG != null && carbsG != null && fatG != null) { "kcal is required unless protein, carbs and fat are all known" }
            autoKcal(proteinG, carbsG, fatG)
        }
        val prev = id?.let { dao.get(it) }
        val e = QuickAddEntry(
            id ?: UUID.randomUUID().toString(), date, name?.trim()?.takeIf { it.isNotEmpty() }, k, proteinG, carbsG, fatG, auto,
            // Keep the position in the day's list when editing on the same date.
            prev?.takeIf { it.date == date }?.createdAt ?: now,
        )
        dao.upsert(e)
        return e
    }

    override suspend fun restore(e: QuickAddEntry) = dao.upsert(e)
    override suspend fun delete(id: String) = dao.delete(id)
}
