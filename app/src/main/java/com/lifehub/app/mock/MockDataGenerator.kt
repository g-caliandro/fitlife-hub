package com.lifehub.app.mock

import com.lifehub.app.data.model.*
import com.lifehub.app.data.prefs.Settings
import com.lifehub.app.data.repo.DataSnapshot
import com.lifehub.app.domain.AchievementEngine
import com.lifehub.app.domain.AchievementHistory
import com.lifehub.app.domain.DateRange
import com.lifehub.app.domain.Periods
import com.lifehub.app.domain.autoKcal
import com.lifehub.app.domain.asMacroDays
import com.lifehub.app.domain.macrosFor
import com.lifehub.app.domain.mergeNutrition
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

/** Spec section 7. Pure + deterministic (Random(42), UUIDs derived from names). */
object MockDataGenerator {
    private const val SPAN = 70
    /** Daily kcal target of the sample data (a minority of days goes over it). */
    const val MOCK_KCAL_TARGET = 2600
    /** Every 8th day (by epoch day) is a logged cheat day, well above 115 % of the kcal target (~12 % of days). */
    private const val CHEAT_EVERY = 8L
    private fun isCheatDay(d: LocalDate) = Math.floorMod(d.toEpochDay(), CHEAT_EVERY) == 5L

    private fun uuid(name: String) = UUID.nameUUIDFromBytes(name.toByteArray()).toString()

    /** [name] -> id "ex-<slug>" (stable, deterministic); exercises are the user's own (no built-in library). */
    private class Ex(val name: String, val sets: Int, val type: TargetType, val target: Int?, val rest: Int,
                     val from: Double, val to: Double, val weight: Double? = null, val weightTo: Double? = null) {
        val id = "ex-" + name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
    }

    private val pullEx = listOf(
        Ex("Pull-up", 4, TargetType.REPS, 6, 120, 6.0, 10.0),
        Ex("Muscle-up negative", 3, TargetType.REPS, 3, 150, 3.0, 5.0),
        Ex("Straight-bar dip transition", 3, TargetType.REPS, 5, 120, 5.0, 8.0),
        Ex("Explosive pull-up", 3, TargetType.MAX, null, 120, 4.0, 8.0),
        Ex("Tuck front lever", 3, TargetType.SECONDS, 10, 90, 10.0, 22.0),
        Ex("Dead hang", 2, TargetType.SECONDS, 30, 60, 30.0, 50.0),
    )
    private val pushEx = listOf(
        Ex("Dip", 4, TargetType.REPS, 8, 90, 8.0, 12.0),
        Ex("Pseudo-planche push-up", 3, TargetType.REPS, 8, 90, 8.0, 12.0),
        Ex("Pike push-up", 3, TargetType.REPS, 8, 90, 8.0, 10.0),
        Ex("Push-up", 3, TargetType.MAX, null, 60, 15.0, 25.0),
        Ex("Triceps pushdown", 3, TargetType.REPS, 12, 60, 12.0, 12.0, weight = 20.0, weightTo = 25.0),
    )
    private val legsEx = listOf(
        Ex("Squat", 4, TargetType.REPS, 15, 90, 15.0, 22.0),
        Ex("Pistol squat", 3, TargetType.REPS, 3, 120, 3.0, 6.0),
        Ex("Nordic curl", 3, TargetType.REPS, 4, 120, 4.0, 7.0),
    )
    private val coreEx = listOf(
        Ex("Dragon flag negative", 4, TargetType.REPS, 3, 120, 3.0, 6.0),
        Ex("Tuck dragon flag", 3, TargetType.REPS, 5, 90, 5.0, 8.0),
        Ex("Hanging leg raise", 3, TargetType.REPS, 8, 90, 8.0, 12.0),
        Ex("Hollow body hold", 3, TargetType.SECONDS, 30, 60, 30.0, 45.0),
        Ex("L-sit", 3, TargetType.SECONDS, 10, 60, 10.0, 20.0),
    )

    /** The sample exercise library (user-created, one category per plan day type). */
    val exercises: List<Exercise> = listOf(ExerciseCategory.PULL to pullEx, ExerciseCategory.PUSH to pushEx,
        ExerciseCategory.LEGS to legsEx, ExerciseCategory.CORE to coreEx).flatMap { (cat, exs) ->
        exs.map { Exercise(it.id, it.name, cat, if (it.type == TargetType.SECONDS) TargetType.SECONDS else TargetType.REPS, isCustom = true, isHidden = false) }
    }

    private fun gauss(r: Random): Double {
        val u1 = r.nextDouble().coerceAtLeast(1e-9); val u2 = r.nextDouble()
        return sqrt(-2 * ln(u1)) * cos(2 * Math.PI * u2)
    }

    private fun round1(v: Double) = (v * 10).roundToInt() / 10.0

    fun generate(today: LocalDate, zone: ZoneId): DataSnapshot {
        val r = Random(42)
        val start = today.minusDays(SPAN.toLong())
        fun at(d: LocalDate, t: LocalTime): Instant = d.atTime(t).atZone(zone).toInstant().let { Instant.ofEpochMilli(it.toEpochMilli()) }
        fun startOfDay(d: LocalDate) = at(d, LocalTime.MIDNIGHT)

        val goalMu = Goal(uuid("goal-muscle-up"), "Muscle Up", "Clean bar muscle-up, no kipping", start, start.plusDays(84),
            true, GoalState.ACTIVE, null, startOfDay(start))
        val goalDf = Goal(uuid("goal-dragon-flag"), "Dragon Flag (clean)", "Full dragon flag, 3 reps", today.minusDays(63), today.plusDays(21),
            true, GoalState.ACTIVE, null, startOfDay(today.minusDays(63)))
        val goalPu = Goal(uuid("goal-strict-pull-ups"), "10 strict pull-ups", "10 clean reps, dead hang start", today.minusDays(120), today.minusDays(60),
            false, GoalState.ACHIEVED, today.minusDays(63), startOfDay(today.minusDays(120)))
        // Reached recently, so the Diary shows a gold star in the current (or, early in a month, the previous) month.
        val goalLs = Goal(uuid("goal-l-sit"), "L-sit 20 s", "Parallettes, legs straight", today.minusDays(56), today.minusDays(2),
            false, GoalState.ACHIEVED, today.minusDays(9), startOfDay(today.minusDays(56)))
        val planA = Plan(uuid("plan-a"), "Pull", setOf(1), null, false, startOfDay(start), DayType.PULL)
        val planPush = Plan(uuid("plan-push"), "Push", setOf(3), null, false, startOfDay(start), DayType.PUSH)
        val planLegs = Plan(uuid("plan-legs"), "Legs", setOf(5), null, false, startOfDay(start), DayType.LEGS)
        val planB = Plan(uuid("plan-b"), "Core", setOf(6), null, false, startOfDay(start), DayType.CORE)
        val plans = listOf(planA, planPush, planLegs, planB)
        val planEx = mapOf(planA to pullEx, planPush to pushEx, planLegs to legsEx, planB to coreEx)

        val planExercises = mutableListOf<PlanExercise>()
        for ((plan, exs) in planEx.toList()) {
            exs.forEachIndexed { i, e ->
                planExercises += PlanExercise(uuid("pe-${plan.id}-$i"), plan.id, e.id, i, e.sets, e.type, e.target, e.weight, e.rest)
            }
        }
        val links = listOf(GoalPlanLink(goalMu.id, planA.id, 24), GoalPlanLink(goalMu.id, planPush.id, 12),
            GoalPlanLink(goalDf.id, planB.id, 24), GoalPlanLink(goalPu.id, planA.id, 30))
        val exNames = exercises.associate { it.id to it.name }

        // F-ACH-6: guarantee a perfect training month (two months back, so the Diary's current and previous month show a few
        // missed workouts) and a protein week (3 weeks back).
        val perfectMonth = YearMonth.from(today).minusMonths(2)
        val proteinWeekStart = Periods.weekStart(today).minusDays(21)
        val proteinWeek = DateRange(proteinWeekStart, proteinWeekStart.plusDays(6))

        val sessions = mutableListOf<WorkoutSession>()
        val sets = mutableListOf<SessionSet>()
        var n = 0
        for (off in 0 until SPAN) { // never on today
            val d = start.plusDays(off.toLong())
            val plan = plans.firstOrNull { d.dayOfWeek.value in it.weekdays } ?: continue
            if (r.nextDouble() >= 0.85 && YearMonth.from(d) != perfectMonth) continue
            val frac = off / SPAN.toDouble()
            val exs = planEx.getValue(plan)
            val sid = uuid("session-${n++}")
            val startedAt = at(d, LocalTime.of(18, 0)).plusSeconds(r.nextInt(0, 91) * 60L)
            val dur = r.nextInt(2700, 4201)
            sessions += WorkoutSession(sid, plan.id, plan.name, d, startedAt, startedAt.plusSeconds(dur.toLong()), dur,
                SessionStatus.COMPLETED, r.nextInt(6, 10), if (r.nextDouble() < 0.2) listOf("felt strong", "shoulder tight")[r.nextInt(2)] else null,
                false, exs.sumOf { it.sets }, null)
            var clock = startedAt
            exs.forEachIndexed { ei, e ->
                val base = (e.from + (e.to - e.from) * frac + r.nextInt(-1, 2)).roundToInt().coerceAtLeast(1)
                val w = e.weight?.let { w0 -> ((w0 + (e.weightTo!! - w0) * frac) * 2).roundToInt() / 2.0 }
                for (si in 0 until e.sets) {
                    var actual = base
                    if (si == e.sets - 1 && r.nextDouble() < 0.4) actual -= if (e.type == TargetType.SECONDS) 5 else 1
                    actual = actual.coerceAtLeast(1)
                    val skipped = r.nextDouble() < 0.05
                    clock = clock.plusSeconds(60L + e.rest / 2)
                    sets += SessionSet(uuid("set-$sid-$ei-$si"), sid, e.id, exNames[e.id] ?: e.id, ei, si, e.type, e.target,
                        if (!skipped && e.type != TargetType.SECONDS) actual else null,
                        if (!skipped && e.type == TargetType.SECONDS) actual else null,
                        w, if (skipped) SetStatus.SKIPPED else SetStatus.DONE, if (skipped) null else clock, e.rest)
                }
            }
        }

        // Daily intake: food entries for most days (derived from the same protein/carbs/fat draws as before so the
        // levels stay near the old totals), plus a few quick adds: whole days logged as one named entry, and small add-ons (some with kcal only = macros n/a).
        val quickAdds = mutableListOf<QuickAddEntry>()
        fun quick(d: LocalDate, n: Int, name: String?, kcal: Int?, p: Int?, c: Int?, f: Int?, time: LocalTime) {
            quickAdds += QuickAddEntry(uuid("qa-$d-$n"), d, name, kcal ?: autoKcal(p!!, c!!, f!!), p, c, f, kcal == null, at(d, time))
        }
        val addOns = listOf<(LocalDate) -> Unit>(
            { d -> quick(d, 1, "Protein bar", null, 12, 25, 4, LocalTime.of(16, 30)) },
            { d -> quick(d, 1, "Snack", 220, null, null, null, LocalTime.of(17, 0)) },
            { d -> quick(d, 1, null, 140, null, null, null, LocalTime.of(22, 0)) },
            { d -> quick(d, 1, "Cappuccino", 120, 6, null, null, LocalTime.of(10, 30)) },
        )
        val foodEntries = mutableListOf<FoodEntry>()
        val fr = Random(4242)
        val foodCatalog = MockFoods.all
        for (off in 0..SPAN) {
            val d = start.plusDays(off.toLong())
            val cheat = isCheatDay(d) && d != today
            if (r.nextDouble() >= 0.85 && d !in proteinWeek && !cheat) continue
            val daysAgo = SPAN - off
            val bias = if (daysAgo in 8..14) -12.0 else 0.0
            val protein = (166 + 14 * gauss(r) + bias).roundToInt().coerceIn(110, 210).let { if (d in proteinWeek) maxOf(it, 150) else it }
            val carbs = r.nextInt(180, 321); val fat = r.nextInt(55, 96)
            val auto = autoKcal(protein, carbs, fat)
            val manual = r.nextDouble() < 0.10
            val kcal = if (manual) auto + r.nextInt(-80, 81) else auto
            val quickOnly = fr.nextDouble() < 0.08 && d !in proteinWeek && d != today.minusDays(1) && !cheat
            if (quickOnly) {
                quick(d, 0, "Logged in another app", if (manual) kcal else null, protein, carbs, fat, LocalTime.of(21, 0))
                continue
            }
            val items = MockFoods.dayMenu(fr, protein, carbs, fat, minProtein = if (d in proteinWeek) 150 else 0).toMutableList()
            if (cheat) {
                // Big dinner out: extra pasta, bread and almonds until clearly above the cheat threshold (~120-130 % of target).
                fun dayKcal() = items.sumOf { foodCatalog.getValue(it.first).macrosFor(it.second).kcal }
                val goal = MOCK_KCAL_TARGET * (1.20 + 0.02 * Math.floorMod(d.toEpochDay(), 5L))
                val extras = listOf("pasta" to 150.0, "bread" to 80.0, "almonds" to 40.0)
                var i = 0
                while (dayKcal() < goal && i < 12) {
                    val (key, g) = extras[i++ % extras.size]
                    val at = items.indexOfFirst { it.first == key }
                    if (at >= 0) items[at] = key to items[at].second + g else items += key to g
                }
            }
            var t = LocalTime.of(8, 0)
            for ((idx, it) in items.withIndex()) {
                val f = foodCatalog.getValue(it.first)
                val m = f.macrosFor(it.second)
                fun r1(v: Double) = (v * 10).roundToInt() / 10.0
                foodEntries += FoodEntry(uuid("fe-$d-$idx"), d, f.id, f.name, f.brand, it.second, r1(m.kcal), r1(m.proteinG), r1(m.carbsG), r1(m.fatG),
                    at(d, t))
                t = t.plusMinutes(75)
                if (t.hour > 21) t = LocalTime.of(21, 30)
            }
            if (d == today) {
                // Added after the loop (also when today has no food yet).
            } else if (d == today.minusDays(1)) {
                quick(d, 1, "Pizza out", 1050, null, null, null, LocalTime.of(20, 30))
            } else if (fr.nextDouble() < 0.15) {
                addOns[Math.floorMod(d.toEpochDay(), addOns.size.toLong()).toInt()](d)
            }
        }
        // Today shows both kinds in the Diary: a full quick add and one with kcal only (macros n/a).
        quick(today, 1, "Protein bar", null, 12, 25, 4, LocalTime.of(16, 30))
        quick(today, 2, "Snack", 220, null, null, null, LocalTime.of(17, 0))
        val foods = foodCatalog.values.sortedBy { it.id }.map { f ->
            val used = foodEntries.filter { it.foodId == f.id }.maxOfOrNull { it.createdAt }
            f.copy(lastUsedAt = used, favorite = f.id in MockFoods.favorites)
        }
        val allDays = mergeNutrition(quickAdds, foodEntries).asMacroDays()

        val bodyDates = (0..SPAN).map { start.plusDays(it.toLong()) }
            .filter { it.dayOfWeek == DayOfWeek.SUNDAY || it == today.minusDays(1) }
        val body = bodyDates.map { d ->
            val f = (d.toEpochDay() - start.toEpochDay()) / SPAN.toDouble()
            BodyEntry(d, round1(78.4 + (76.9 - 78.4) * f + (r.nextDouble() * 0.6 - 0.3)),
                round1(36.4 + (37.3 - 36.4) * f), round1(14.2 + (12.5 - 14.2) * f), at(d, LocalTime.of(8, 0)))
        }

        val settings = Settings(timezone = zone.id, kcalTarget = MOCK_KCAL_TARGET)
        val goals = listOf(goalMu, goalDf, goalPu, goalLs)
        val achievements = AchievementEngine.backfill(
            AchievementHistory(today, sessions, sets, plans, allDays, goals, settings.proteinTargetG, zone),
        ).sortedWith(compareBy({ it.achievedOn }, { it.dedupeKey }))
            .map { it.toEntity(uuid("ach-${it.dedupeKey}"), at(it.achievedOn, LocalTime.of(20, 0))) }

        return DataSnapshot(
            settings = settings,
            goals = goals, links = links, plans = plans, planExercises = planExercises,
            exercises = exercises, sessions = sessions, sets = sets, quickAdds = quickAdds.sortedWith(compareBy({ it.date }, { it.createdAt }, { it.id })), bodyEntries = body,
            achievements = achievements, foods = foods,
            foodEntries = foodEntries.sortedWith(compareBy({ it.date }, { it.createdAt }, { it.id })),
        )
    }
}
