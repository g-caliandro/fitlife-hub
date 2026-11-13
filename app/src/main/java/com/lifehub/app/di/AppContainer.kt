package com.lifehub.app.di

import android.content.Context
import com.lifehub.app.core.time.SystemTimeSource
import com.lifehub.app.core.time.TimeSource
import com.lifehub.app.data.db.LifeHubDatabase
import com.lifehub.app.data.prefs.SettingsRepository
import com.lifehub.app.data.repo.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Context, val time: TimeSource = SystemTimeSource()) {
    val appContext: Context = context.applicationContext
    val db: LifeHubDatabase = LifeHubDatabase.build(appContext)
    val settings = SettingsRepository(appContext)
    val goals: GoalRepository = GoalRepositoryImpl(db)
    val plans: PlanRepository = PlanRepositoryImpl(db)
    val exercises: ExerciseRepository = ExerciseRepositoryImpl(db)
    val sessions: SessionRepository = SessionRepositoryImpl(db)
    val macros: MacroRepository = MacroRepositoryImpl(db)
    val food: FoodRepository = FoodRepositoryImpl(db)
    val off: com.lifehub.app.data.off.OffApi = com.lifehub.app.data.off.OpenFoodFactsClient()
    val body: BodyRepository = BodyRepositoryImpl(db)
    val achievements: AchievementRepository = AchievementRepositoryImpl(db, settings)
    val backup: BackupRepository = BackupRepositoryImpl(db, settings)
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
