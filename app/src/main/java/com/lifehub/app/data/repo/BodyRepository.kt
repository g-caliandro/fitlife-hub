package com.lifehub.app.data.repo

import com.lifehub.app.data.db.LifeHubDatabase
import com.lifehub.app.data.model.BodyEntry
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface BodyRepository {
    fun observeAll(): Flow<List<BodyEntry>>
    fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<BodyEntry>>
    suspend fun get(date: LocalDate): BodyEntry?
    suspend fun upsert(entry: BodyEntry)
    suspend fun delete(date: LocalDate)
    suspend fun latestDate(): LocalDate?
}

class BodyRepositoryImpl(db: LifeHubDatabase) : BodyRepository {
    private val dao = db.bodyDao()
    override fun observeAll() = dao.observeAll()
    override fun observeBetween(from: LocalDate, to: LocalDate) = dao.observeBetween(from, to)
    override suspend fun get(date: LocalDate) = dao.get(date)
    override suspend fun upsert(entry: BodyEntry) = dao.upsert(entry)
    override suspend fun delete(date: LocalDate) = dao.delete(date)
    override suspend fun latestDate() = dao.latestDate()
}
