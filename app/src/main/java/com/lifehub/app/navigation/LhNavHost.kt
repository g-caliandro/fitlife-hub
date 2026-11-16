package com.lifehub.app.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import com.lifehub.app.core.designsystem.LhMotion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.lifehub.app.feature.achievements.AchievementsRoute
import com.lifehub.app.feature.body.BodyEditRoute
import com.lifehub.app.feature.body.BodyListRoute
import com.lifehub.app.feature.calendar.CalendarRoute
import com.lifehub.app.feature.copilot.ChatRoute
import com.lifehub.app.feature.food.AddFoodRoute
import com.lifehub.app.feature.food.CustomFoodRoute
import com.lifehub.app.feature.food.FoodDetailRoute
import com.lifehub.app.feature.food.ScanRoute
import com.lifehub.app.feature.goals.GoalDetailRoute
import com.lifehub.app.feature.goals.GoalEditRoute
import com.lifehub.app.feature.goals.GoalsRoute
import com.lifehub.app.feature.home.HomeRoute
import com.lifehub.app.feature.trends.TrendsRoute
import com.lifehub.app.feature.nutrition.MacroEditRoute
import com.lifehub.app.feature.plans.ExerciseEditRoute
import com.lifehub.app.feature.plans.ExercisesRoute
import com.lifehub.app.feature.plans.PlanDetailRoute
import com.lifehub.app.feature.plans.PlanEditRoute
import com.lifehub.app.feature.plans.PlansRoute
import com.lifehub.app.feature.settings.AboutRoute
import com.lifehub.app.feature.settings.DataRoute
import com.lifehub.app.feature.settings.CopilotSettingsRoute
import com.lifehub.app.feature.settings.NotificationsRoute
import com.lifehub.app.feature.settings.SettingsRoute
import com.lifehub.app.feature.settings.TargetsRoute
import com.lifehub.app.feature.you.YouRoute
import com.lifehub.app.feature.workout.LiveWorkoutRoute
import com.lifehub.app.feature.workout.SessionRoute
import com.lifehub.app.feature.workout.WorkoutLogRoute
import com.lifehub.app.feature.workout.WorkoutPreviewRoute
import com.lifehub.app.feature.workout.WorkoutStartRoute
import com.lifehub.app.container
import kotlinx.coroutines.flow.StateFlow

private fun strArg(name: String, nullable: Boolean = true) = navArgument(name) {
    type = NavType.StringType; this.nullable = nullable; if (nullable) defaultValue = null
}
private fun longArg(name: String) = navArgument(name) { type = NavType.LongType; defaultValue = -1L }

/**
 * Single entry point for in-app navigation. Handles the "back" and "replace:<route>" prefix conventions, deep links,
 * and routes every tab root (Home, Diary, Trends, Train) through [navigateToTab] so an in-screen link to another tab
 * ("See diary", streak chip, ...) behaves exactly like tapping that tab in the bottom bar.
 */
fun NavController.lhNavigate(route: String) {
    when {
        route == Routes.BACK -> popBackStack()
        route.startsWith("replace:") -> {
            val target = route.removePrefix("replace:")
            val current = currentBackStackEntry?.destination?.route
            // Entering the live workout drops the whole start/preview chain, so "minimize" (back) returns to the tab
            // instead of the start screen (which would bounce straight back into the active workout).
            val startInStack = target == Routes.WORKOUT_LIVE && runCatching { getBackStackEntry(Routes.WORKOUT_START) }.isSuccess
            navigate(target) {
                when {
                    startInStack -> popUpTo(Routes.WORKOUT_START) { inclusive = true }
                    current != null -> popUpTo(current) { inclusive = true }
                }
            }
        }
        // Pre-Trends notification links ("home?period=1W&offset=1") now open the Trends tab.
        route.startsWith("home?") -> lhNavigate("trends?" + route.substringAfter('?'))
        // End of a flow (e.g. finishing a workout): reset everything to a fresh Home.
        route == Routes.home() -> navigate(route) { popUpTo(graph.id) { inclusive = true } }
        // Trends link carrying a period/offset (notifications): switch tab, but show the requested period, not the saved one.
        route.startsWith("trends?") -> navigateToTab(route, restore = false)
        Routes.tabRootOf(route) != null -> navigateToTab(route)
        else -> navigate(route)
    }
}

/** The tab whose back stack is showing: tab roots other than Home only ever sit directly on top of Home. */
private fun NavController.currentTabRoot(): String =
    Routes.TAB_ROOTS.firstOrNull { it != Routes.HOME_ROOT && runCatching { getBackStackEntry(it) }.isSuccess } ?: Routes.HOME_ROOT

/**
 * Switches to the tab owning [route] exactly like the bottom bar: the current tab's stack is saved, the target tab's
 * saved stack is restored. Re-selecting the current tab (even from deeper inside it) pops back to its root.
 * [restore] = false shows [route] with its own arguments instead of the tab's saved stack.
 */
fun NavController.navigateToTab(route: String, restore: Boolean = true) {
    val root = Routes.tabRootOf(route) ?: route
    if (restore && root == currentTabRoot()) {
        popBackStack(root, inclusive = false)
        return
    }
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = restore
    }
}

@Composable
fun LhNavHost(
    navController: NavHostController,
    pendingRoute: StateFlow<String?>,
    onRouteConsumed: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val route by pendingRoute.collectAsState()
    LaunchedEffect(route) {
        route?.let {
            navController.lhNavigate(it)
            onRouteConsumed()
        }
    }
    val nav: (String) -> Unit = { navController.lhNavigate(it) }
    val back: () -> Unit = { navController.popBackStack() }
    NavHost(
        navController, startDestination = Routes.HOME_ROOT, modifier = modifier.padding(contentPadding),
        enterTransition = { if (Routes.isTabRoute(targetState.destination.route)) fadeIn(tween(LhMotion.short)) else fadeIn(tween(700)) },
        exitTransition = { if (Routes.isTabRoute(targetState.destination.route)) fadeOut(tween(100)) else fadeOut(tween(700)) },
        popEnterTransition = { if (Routes.isTabRoute(targetState.destination.route)) fadeIn(tween(LhMotion.short)) else fadeIn(tween(700)) },
        popExitTransition = { if (Routes.isTabRoute(targetState.destination.route)) fadeOut(tween(100)) else fadeOut(tween(700)) },
    ) {
        composable(Routes.HOME_ROOT) { HomeRoute(nav) }
        composable(
            Routes.TRENDS,
            arguments = listOf(strArg("period"), navArgument("offset") { type = NavType.IntType; defaultValue = 0 }),
        ) { TrendsRoute() }
        composable(Routes.CALENDAR) { CalendarRoute(nav) }
        composable(Routes.GOALS) { GoalsRoute(nav, back) }
        composable(Routes.GOAL, arguments = listOf(strArg("goalId", nullable = false))) { GoalDetailRoute(nav, back) }
        composable(Routes.GOAL_EDIT, arguments = listOf(strArg("goalId"))) { GoalEditRoute(back) }
        composable(Routes.PLANS) { PlansRoute(nav) }
        composable(Routes.PLAN, arguments = listOf(strArg("planId", nullable = false))) { PlanDetailRoute(nav, back) }
        composable(Routes.PLAN_EDIT, arguments = listOf(strArg("planId"), strArg("goalId"))) { PlanEditRoute(back) }
        composable(Routes.EXERCISES) { ExercisesRoute(nav, back) }
        composable(Routes.EXERCISE_EDIT, arguments = listOf(strArg("exerciseId"))) { ExerciseEditRoute(back) }
        composable(Routes.WORKOUT_START, arguments = listOf(strArg("planId"))) { WorkoutStartRoute(nav, back) }
        composable(Routes.WORKOUT_PREVIEW, arguments = listOf(strArg("planId", nullable = false))) { WorkoutPreviewRoute(nav, back) }
        composable(Routes.WORKOUT_LIVE) { LiveWorkoutRoute(nav, back) }
        composable(Routes.WORKOUT_LOG, arguments = listOf(longArg("date"))) { WorkoutLogRoute(back) }
        composable(Routes.SESSION, arguments = listOf(strArg("sessionId", nullable = false))) { SessionRoute(back) }
        composable(Routes.MACRO_EDIT, arguments = listOf(longArg("date"), strArg("id"))) { MacroEditRoute(back) }
        composable(Routes.FOOD_ADD, arguments = listOf(longArg("date"))) { AddFoodRoute(nav, back) }
        composable(Routes.FOOD_DETAIL, arguments = listOf(strArg("foodId", nullable = false), longArg("date"), strArg("entryId"))) { FoodDetailRoute(nav, back) }
        composable(Routes.FOOD_CUSTOM, arguments = listOf(strArg("foodId"), strArg("barcode"))) { CustomFoodRoute(back) }
        composable(Routes.FOOD_SCAN, arguments = listOf(longArg("date"))) { entry ->
            val d = entry.arguments?.getLong("date") ?: -1L
            val ctx = androidx.compose.ui.platform.LocalContext.current
            ScanRoute(if (d < 0) ctx.container.time.today() else java.time.LocalDate.ofEpochDay(d), nav, back)
        }
        composable(Routes.BODY) { BodyListRoute(nav, back) }
        composable(Routes.BODY_EDIT, arguments = listOf(longArg("date"))) { BodyEditRoute(back) }
        composable(Routes.SETTINGS) { SettingsRoute(back) }
        composable(Routes.YOU) { YouRoute(nav, back) }
        composable(Routes.TARGETS) { TargetsRoute(back) }
        composable(Routes.SETTINGS_NOTIFICATIONS) { NotificationsRoute(back) }
        composable(Routes.SETTINGS_COPILOT) { CopilotSettingsRoute(back, onOpenMeasures = { nav(Routes.SETTINGS_COPILOT_MEASURES) }) }
        composable(Routes.SETTINGS_COPILOT_MEASURES) { com.lifehub.app.feature.settings.HouseholdMeasuresRoute(back) }
        composable(Routes.COPILOT, arguments = listOf(strArg("threadId"))) { ChatRoute(nav, back) }
        composable(Routes.COPILOT_INBOX) { com.lifehub.app.feature.copilot.InboxRoute(nav, back) }
        composable(Routes.SETTINGS_DATA) { DataRoute(back) }
        composable(Routes.ABOUT) { AboutRoute(back) }
        composable(Routes.ACHIEVEMENTS) { AchievementsRoute(nav, back) }
    }
}
