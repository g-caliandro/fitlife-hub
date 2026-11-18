package com.lifehub.app.data.repo

import com.lifehub.app.data.db.LifeHubDatabase
import com.lifehub.app.data.model.Exercise
import kotlinx.coroutines.flow.Flow

sealed interface ExerciseSaveResult { data object Ok : ExerciseSaveResult; data object DuplicateName : ExerciseSaveResult }
sealed interface ExerciseDeleteResult { data object Ok : ExerciseDeleteResult; data object InUse : ExerciseDeleteResult }

class ExerciseRepository(db: LifeHubDatabase) {
    private val dao = db.exerciseDao()

    /** includes hidden; pickers filter !isHidden */
    fun observeAll(): Flow<List<Exercise>> = dao.observeAll()
    suspend fun get(id: String): Exercise? = dao.get(id)

    /** name trimmed; unique NOCASE excluding self */
    suspend fun save(exercise: Exercise): ExerciseSaveResult {
        val e = exercise.copy(name = exercise.name.trim())
        val existing = dao.findByName(e.name)
        if (existing != null && existing.id != e.id) return ExerciseSaveResult.DuplicateName
        dao.upsert(e)
        return ExerciseSaveResult.Ok
    }

    suspend fun delete(id: String): ExerciseDeleteResult {
        dao.get(id) ?: return ExerciseDeleteResult.Ok
        if (dao.referenceCount(id) > 0) return ExerciseDeleteResult.InUse
        dao.delete(id)
        return ExerciseDeleteResult.Ok
    }
}
