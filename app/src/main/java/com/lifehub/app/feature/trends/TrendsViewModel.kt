package com.lifehub.app.feature.trends

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lifehub.app.core.time.TimeSource
import com.lifehub.app.core.time.todayFlow
import com.lifehub.app.core.ui.charts.ChartPoint
import com.lifehub.app.data.model.BodyEntry
import com.lifehub.app.data.prefs.SettingsRepository
import com.lifehub.app.data.db.dao.BodyDao
import com.lifehub.app.data.repo.FoodRepository
import com.lifehub.app.data.repo.PlanRepository
import com.lifehub.app.data.repo.SessionRepository
import com.lifehub.app.di.AppContainer
import com.lifehub.app.domain.DateRange
import com.lifehub.app.domain.InsightInput
import com.lifehub.app.domain.Insights
import com.lifehub.app.domain.MacroAverages
import com.lifehub.app.domain.Periods
import com.lifehub.app.domain.asMacroDays
import com.lifehub.app.domain.computeInsights
import com.lifehub.app.domain.macroAverages
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/** Trends tab: training stats, calorie + protein charts, daily macro average and body chart for a 2W/4W period. */
data class TrendsUiState(
    val today: LocalDate,
    val period: TrendsPeriod,
    val offset: Int,
    val range: DateRange,
    val insights: Insights,
    val proteinTargetG: Int,
    val macroPoints: List<ChartPoint>,   // kcal per day
    val proteinPoints: List<ChartPoint>,
    /** Body entries in the current period (sparse; muscle/fat may be null). */
    val body: List<BodyEntry>,
    val kcalTarget: Int? = null,
    val carbsTargetG: Int? = null,
    val fatTargetG: Int? = null,
    /** ONT "Daily average" for the selected period (logged days only). */
    val averages: MacroAverages? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TrendsViewModel(
    private val settings: SettingsRepository,
    plans: PlanRepository,
    sessions: SessionRepository,
    food: FoodRepository,
    body: BodyDao,
    time: TimeSource,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val linkPeriod = savedState.get<String?>("period")
    private val offset = MutableStateFlow(TrendsPeriod.deepLinkOffset(linkPeriod, (savedState.get<Any?>("offset") as? Int) ?: 0))

    init {
        // Deep links (e.g. the weekly protein notification, "period=1W&offset=1") may preselect the period.
        TrendsPeriod.parse(linkPeriod)?.let { settings.setHomePeriod(it.toStored()) }
    }

    private val periodAndOffset =
        combine(settings.settings.map { TrendsPeriod.fromStored(it.homePeriod) }.distinctUntilChanged(), offset) { p, o -> p to o }

    val state: StateFlow<TrendsUiState?> = combine(periodAndOffset, time.todayFlow()) { po, t -> po to t }.flatMapLatest { (po, today) ->
        val (period, off) = po
        val cur = Periods.period(today, period.days, off)
        val prev = Periods.previous(cur)
        val rangeData = combine(
            sessions.observeCompletedWithSets(prev.start, cur.end),
            food.observeNutrition(prev.start, cur.end).map { it.asMacroDays() },
            body.observeBetween(prev.start, cur.end),
        ) { s, m, b -> Triple(s, m, b) }
        combine(rangeData, plans.observePlans(), settings.settings) { (s, m, b), p, st ->
            val planList = p.map { it.plan }.filter { !it.isArchived }
            val cm = m.filter { it.date in cur }
            val cb = b.filter { it.date in cur }
            TrendsUiState(
                today, period, off, cur,
                computeInsights(InsightInput(today, cur, prev, planList, s, m, b, st.proteinTargetG)), st.proteinTargetG,
                cm.map { ChartPoint(it.date, it.kcal.toDouble()) },
                // Protein bars only where the day's protein is fully known (same rule as the averages); others get no bar.
                cm.filter { it.proteinExact }.map { ChartPoint(it.date, it.proteinG.toDouble()) },
                cb,
                kcalTarget = st.kcalTarget, carbsTargetG = st.carbsTargetG, fatTargetG = st.fatTargetG,
                averages = macroAverages(m, cur),
            ) as TrendsUiState?
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setPeriod(p: TrendsPeriod) = settings.setHomePeriod(p.toStored())
    fun previous() { offset.value = offset.value + 1 }
    fun next() { if (offset.value > 0) offset.value = offset.value - 1 }
    fun jumpToCurrent() { offset.value = 0 }

    companion object {
        fun factory(c: AppContainer) = viewModelFactory {
            initializer { TrendsViewModel(c.settings, c.plans, c.sessions, c.food, c.body, c.time, createSavedStateHandle()) }
        }
    }
}
