package com.lifehub.app.navigation

import com.lifehub.app.data.model.HomePeriod
import java.time.LocalDate

/** Route patterns + builders (architecture 6.1). Dates are epochDay Long query params. */
object Routes {
    // patterns (used by NavHost)
    const val HOME_ROOT = "home"
    const val TRENDS = "trends?period={period}&offset={offset}"
    const val CALENDAR = "calendar"
    const val GOALS = "goals"
    const val GOAL = "goal/{goalId}"
    const val GOAL_EDIT = "goal/edit?goalId={goalId}"
    const val PLANS = "plans"
    const val PLAN = "plan/{planId}"
    const val PLAN_EDIT = "plan/edit?planId={planId}&goalId={goalId}"
    const val EXERCISES = "exercises"
    const val EXERCISE_EDIT = "exercise/edit?exerciseId={exerciseId}"
    const val WORKOUT_START = "workout/start?planId={planId}"
    const val WORKOUT_PREVIEW = "workout/preview/{planId}"
    const val WORKOUT_LIVE = "workout/live"
    const val WORKOUT_LOG = "workout/log?date={date}"
    const val SESSION = "session/{sessionId}"
    const val MACRO_EDIT = "macros/edit?date={date}&id={id}"
    const val FOOD_ADD = "food/add?date={date}"
    const val FOOD_DETAIL = "food/detail/{foodId}?date={date}&entryId={entryId}"
    const val FOOD_CUSTOM = "food/custom?foodId={foodId}&barcode={barcode}"
    const val FOOD_SCAN = "food/scan?date={date}&pick={pick}"
    const val BODY = "body"
    const val BODY_EDIT = "body/edit?date={date}"
    const val SETTINGS = "settings"
    const val ACHIEVEMENTS = "achievements"

    /** Profile + settings hub; reached from the avatar in Home's header (no longer a tab). */
    const val YOU = "you"
    const val TARGETS = "settings/targets"
    const val SETTINGS_NOTIFICATIONS = "settings/notifications"
    const val SETTINGS_DATA = "settings/data"
    const val SETTINGS_COPILOT = "settings/copilot"
    const val SETTINGS_COPILOT_MEASURES = "settings/copilot/measures"
    /** Copilot chat; no threadId = the most recent chat thread (a new one is created on the first message). */
    const val COPILOT = "copilot?threadId={threadId}"
    const val COPILOT_INBOX = "copilot/inbox"
    const val ABOUT = "about"

    val TAB_ROOTS = listOf(HOME_ROOT, CALENDAR, TRENDS, PLANS)

    // builders
    fun home() = "home"
    fun trends(period: HomePeriod? = null, offset: Int = 0): String {
        val p = when (period) { HomePeriod.ONE_WEEK -> "1W"; HomePeriod.TWO_WEEKS -> "2W"; null -> null }
        return if (p == null) "trends?offset=$offset" else "trends?period=$p&offset=$offset"
    }
    fun goal(id: String) = "goal/$id"
    fun goalEdit(id: String? = null) = if (id == null) "goal/edit" else "goal/edit?goalId=$id"
    fun plan(id: String) = "plan/$id"
    fun planEdit(planId: String? = null, goalId: String? = null): String {
        val q = listOfNotNull(planId?.let { "planId=$it" }, goalId?.let { "goalId=$it" })
        return if (q.isEmpty()) "plan/edit" else "plan/edit?" + q.joinToString("&")
    }
    fun exerciseEdit(id: String? = null) = if (id == null) "exercise/edit" else "exercise/edit?exerciseId=$id"
    fun workoutStart(planId: String? = null) = if (planId == null) "workout/start" else "workout/start?planId=$planId"
    fun workoutPreview(planId: String) = "workout/preview/$planId"
    fun workoutLog(date: LocalDate) = "workout/log?date=${date.toEpochDay()}"
    fun session(id: String) = "session/$id"
    /** Quick-add form: a new entry on [date], or edit the entry [id]. */
    fun macroEdit(date: LocalDate, id: String? = null) = "macros/edit?date=${date.toEpochDay()}" + (id?.let { "&id=$it" } ?: "")
    fun foodAdd(date: LocalDate) = "food/add?date=${date.toEpochDay()}"
    fun foodDetail(foodId: String, date: LocalDate, entryId: String? = null) =
        "food/detail/$foodId?date=${date.toEpochDay()}" + (entryId?.let { "&entryId=$it" } ?: "")
    fun foodCustom(foodId: String? = null, barcode: String? = null): String {
        val q = listOfNotNull(foodId?.let { "foodId=$it" }, barcode?.let { "barcode=$it" })
        return if (q.isEmpty()) "food/custom" else "food/custom?" + q.joinToString("&")
    }
    fun foodScan(date: LocalDate) = "food/scan?date=${date.toEpochDay()}"
    /** The scanner in "pick" mode: the product goes back to the copilot chat as an attachment. */
    fun foodScanPick() = "food/scan?pick=true"
    fun bodyEdit(date: LocalDate) = "body/edit?date=${date.toEpochDay()}"
    fun copilot(threadId: String? = null) = if (threadId == null) "copilot" else "copilot?threadId=$threadId"

    /** onNavigate prefix conventions handled by LhNavHost. */
    const val BACK = "back"
    fun replace(route: String) = "replace:$route"

    /** Maps a NavDestination route pattern to the bottom-bar tab it belongs to (or null). */
    fun isTabRoute(pattern: String?): Boolean = pattern != null && pattern in TAB_ROOTS

    /** The tab root pattern a concrete route opens ("trends?offset=1" -> [TRENDS]), or null if it is not a tab root. */
    fun tabRootOf(route: String): String? = TAB_ROOTS.firstOrNull { it.substringBefore('?') == route.substringBefore('?') }
}
