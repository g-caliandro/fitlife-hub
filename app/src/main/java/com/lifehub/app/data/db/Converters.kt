package com.lifehub.app.data.db

import androidx.room.TypeConverter
import com.lifehub.app.data.model.DayType
import java.time.Instant
import java.time.LocalDate

class Converters {
    @TypeConverter fun dateToLong(d: LocalDate?): Long? = d?.toEpochDay()
    @TypeConverter fun longToDate(v: Long?): LocalDate? = v?.let(LocalDate::ofEpochDay)
    @TypeConverter fun instantToLong(i: Instant?): Long? = i?.toEpochMilli()
    @TypeConverter fun longToInstant(v: Long?): Instant? = v?.let(Instant::ofEpochMilli)
    @TypeConverter fun dayTypeToString(t: DayType?): String? = t?.name
    @TypeConverter fun stringToDayType(v: String?): DayType? = v?.let { runCatching { DayType.valueOf(it) }.getOrNull() }
    @TypeConverter fun weekdaysToString(s: Set<Int>): String = s.sorted().joinToString(",")
    @TypeConverter fun stringToWeekdays(v: String): Set<Int> =
        if (v.isBlank()) emptySet() else v.split(",").map(String::toInt).toSet()
}
