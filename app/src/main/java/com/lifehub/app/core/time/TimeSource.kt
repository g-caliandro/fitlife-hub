package com.lifehub.app.core.time

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

interface TimeSource {
    val zone: ZoneId
    fun now(): Instant
    fun today(): LocalDate = now().atZone(zone).toLocalDate()
}

class SystemTimeSource : TimeSource {
    override val zone: ZoneId get() = ZoneId.systemDefault()
    override fun now(): Instant = Instant.now()
}

class FixedTimeSource(var instant: Instant, override val zone: ZoneId = ZoneOffset.UTC) : TimeSource {
    override fun now() = instant
}

/** Emits today's date, and again whenever it changes (checked every minute). */
fun TimeSource.todayFlow(): Flow<LocalDate> = flow {
    while (true) { emit(today()); delay(60_000) }
}.distinctUntilChanged()
