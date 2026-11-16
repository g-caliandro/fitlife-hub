package com.lifehub.app.export

import com.lifehub.app.data.repo.DataSnapshot
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** CSV zip export (spec 6.2). RFC 4180 quoting, "\n" line endings, empty cell = null. */
object CsvExporter {
    fun cell(v: Any?): String {
        val s = when (v) { null -> ""; is Double -> v.toString(); else -> v.toString() }
        return if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }
    fun csv(vararg cells: Any?): String = cells.joinToString(",") { cell(it) } + "\n"

    fun files(s: DataSnapshot, exportedAt: ZonedDateTime): Map<String, String> {
        val zone: ZoneId = exportedAt.zone
        fun ts(i: Instant?) = i?.atZone(zone)?.toOffsetDateTime()?.toString()
        val exName = s.exercises.associate { it.id to it.name }
        val sessionDate = s.sessions.associate { it.id to it.date }
        val out = linkedMapOf<String, String>()
        fun table(name: String, header: String, rows: List<String>) {
            out[name] = header + "\n" + rows.joinToString("") 
        }
        table("goals.csv", "id,name,notes,startDate,deadline,state,achievedAt,createdAt",
            s.goals.map { csv(it.id, it.name, it.notes, it.startDate, it.deadline, it.state, it.achievedAt, ts(it.createdAt)) })
        table("goal_plan_links.csv", "goalId,planId,sessionsNeeded", s.links.map { csv(it.goalId, it.planId, it.sessionsNeeded) })
        table("plans.csv", "id,name,weekdays,notes,isArchived,createdAt,dayType",
            s.plans.map { csv(it.id, it.name, it.weekdays.sorted().joinToString(";"), it.notes, it.isArchived, ts(it.createdAt), it.dayType) })
        table("plan_exercises.csv", "id,planId,orderIndex,exerciseId,exerciseName,sets,targetType,targetValue,weightKg,restSeconds",
            s.planExercises.map { csv(it.id, it.planId, it.orderIndex, it.exerciseId, exName[it.exerciseId], it.sets, it.targetType, it.targetValue, it.weightKg, it.restSeconds) })
        table("exercises.csv", "id,name,category,defaultTargetType,isCustom,isHidden",
            s.exercises.map { csv(it.id, it.name, it.category, it.defaultTargetType, it.isCustom, it.isHidden) })
        table("sessions.csv", "id,planId,planName,date,startedAt,endedAt,durationSec,status,rpe,notes,isManual",
            s.sessions.map { csv(it.id, it.planId, it.planName, it.date, ts(it.startedAt), ts(it.endedAt), it.durationSec, it.status, it.rpe, it.notes, it.isManual) })
        table("session_sets.csv", "id,sessionId,sessionDate,exerciseId,exerciseName,exerciseOrder,setIndex,targetType,targetValue,actualReps,actualSeconds,weightKg,status",
            s.sets.map { csv(it.id, it.sessionId, sessionDate[it.sessionId], it.exerciseId, it.exerciseName, it.exerciseOrder, it.setIndex, it.targetType, it.targetValue, it.actualReps, it.actualSeconds, it.weightKg, it.status) })
        // Empty proteinG/carbsG/fatG cell = unknown (not zero).
        table("quick_adds.csv", "id,date,name,kcal,proteinG,carbsG,fatG,kcalIsAuto,createdAt",
            s.quickAdds.sortedWith(compareBy({ it.date }, { it.createdAt }, { it.id })).map {
                csv(it.id, it.date, it.name, it.kcal, it.proteinG, it.carbsG, it.fatG, it.kcalIsAuto, ts(it.createdAt))
            })
        table("body_entries.csv", "date,weightKg,muscleKg,fatKg,fatPct",
            s.bodyEntries.sortedBy { it.date }.map { csv(it.date, it.weightKg, it.muscleKg, it.fatKg, it.fatKg?.let { f -> f / it.weightKg * 100 }) })
        table("achievements.csv", "id,kind,big,title,detail,value,unit,exerciseId,goalId,sessionId,achievedOn,createdAt,dedupeKey",
            s.achievements.sortedWith(compareBy({ it.achievedOn }, { it.dedupeKey })).map {
                csv(it.id, it.kind, it.kind.isBig, it.title, it.detail, it.value, it.unit, it.exerciseId, it.goalId, it.sessionId, it.achievedOn, ts(it.createdAt), it.dedupeKey)
            })
        table("foods.csv", "id,source,barcode,name,brand,kcal100,protein100,carbs100,fat100,servingSizeG,favorite,lastUsedAt",
            s.foods.sortedBy { it.name.lowercase() }.map { csv(it.id, it.source, it.barcode, it.name, it.brand, it.kcal100, it.protein100, it.carbs100, it.fat100, it.servingSizeG, it.favorite, ts(it.lastUsedAt)) })
        table("food_entries.csv", "id,date,foodId,name,brand,grams,kcal,proteinG,carbsG,fatG,createdAt",
            s.foodEntries.sortedWith(compareBy({ it.date }, { it.createdAt })).map { csv(it.id, it.date, it.foodId, it.name, it.brand, it.grams, it.kcal, it.proteinG, it.carbsG, it.fatG, ts(it.createdAt)) })
        val st = s.settings
        table("settings.csv", "key,value", listOf(
            csv("proteinTargetG", st.proteinTargetG), csv("kcalTarget", st.kcalTarget), csv("carbsTargetG", st.carbsTargetG), csv("fatTargetG", st.fatTargetG), csv("notifyTrain", st.notifyTrain), csv("notifyProtein", st.notifyProtein),
            csv("notifyWeight", st.notifyWeight), csv("notifyAchievements", st.notifyAchievements), csv("notifyStreak", st.notifyStreak), csv("dailyCheckTime", String.format("%02d:%02d", st.dailyCheckTime.hour, st.dailyCheckTime.minute)),
            csv("homePeriod", st.homePeriod), csv("timezone", st.timezone),
        ))
        out["README.txt"] = JsonCodec.README + "\n\nschemaVersion: ${JsonCodec.SCHEMA_VERSION}\nexportedAt: " +
            exportedAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) + "\n"
        return out
    }

    fun writeZip(s: DataSnapshot, exportedAt: ZonedDateTime, out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            files(s, exportedAt).forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray(Charsets.UTF_8)); zip.closeEntry()
            }
        }
    }
}
