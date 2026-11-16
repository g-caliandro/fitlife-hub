package com.lifehub.app.data.repo

import androidx.room.withTransaction
import com.lifehub.app.copilot.food.HouseholdMeasureSeed
import com.lifehub.app.data.db.LifeHubDatabase
import com.lifehub.app.data.model.HouseholdMeasure
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

/** Household measures table (Copilot settings). Defaults come only from [HouseholdMeasureSeed]. */
class HouseholdMeasureRepository(private val db: LifeHubDatabase) {
    private val dao = db.portionDao()
    fun observe(): Flow<List<HouseholdMeasure>> = dao.observeMeasures()
    suspend fun all(): List<HouseholdMeasure> = dao.measures()

    suspend fun save(id: String?, label: String, grams: Double, foodId: String?, baseFoodId: String?, now: Instant): HouseholdMeasure {
        val m = HouseholdMeasure(id ?: UUID.randomUUID().toString(), label.trim().lowercase(), grams, foodId, baseFoodId, now)
        dao.upsertMeasure(m)
        return m
    }

    suspend fun delete(id: String) = dao.deleteMeasure(id)

    /** Puts every default row back to its default value (rows the user added are kept). */
    suspend fun restoreDefaults(now: Instant) = db.withTransaction {
        HouseholdMeasureSeed.measures(now).forEach { dao.upsertMeasure(it) }
    }
}
