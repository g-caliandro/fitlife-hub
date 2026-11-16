package com.lifehub.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lifehub.app.R
import com.lifehub.app.container
import com.lifehub.app.core.designsystem.LhSize
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType
import com.lifehub.app.core.designsystem.fmtClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import java.time.Duration

/** Global snackbar host for undo snackbars etc. */
val LocalSnackbarHostState = compositionLocalOf<SnackbarHostState> { error("No SnackbarHostState provided") }

enum class Tab(val route: String, val labelRes: Int, val icon: ImageVector, val selectedIcon: ImageVector) {
    Home(Routes.HOME_ROOT, R.string.tab_home, Icons.Outlined.Home, Icons.Filled.Home),
    Diary(Routes.CALENDAR, R.string.tab_diary, Icons.Outlined.Book, Icons.Filled.Book),
    Trends(Routes.TRENDS, R.string.tab_trends, Icons.Outlined.Insights, Icons.Filled.Insights),
    Train(Routes.PLANS, R.string.tab_train, Icons.Outlined.FitnessCenter, Icons.Filled.FitnessCenter),
}

/** The date the Diary tab currently shows (set by Diary); (+) actions use it, else today. */
val LocalDiaryDate = androidx.compose.runtime.staticCompositionLocalOf<androidx.compose.runtime.MutableState<java.time.LocalDate?>> {
    androidx.compose.runtime.mutableStateOf(null)
}

private val BarHeight = 64.dp

@Composable
fun AppScaffold(pendingRoute: StateFlow<String?>, onRouteConsumed: () -> Unit) {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val pattern = entry?.destination?.route
    val isTab = Routes.isTabRoute(pattern)
    val snackbar = remember { SnackbarHostState() }
    val container = LocalContext.current.container
    val inProgress by remember { container.sessions.observeInProgress() }.collectAsState(initial = null)

    // Snackbars are scoped to a destination. The listener fires synchronously inside navigate()/popBackStack(),
    // so a SnackbarBus message posted right after popping back is shown on the destination it was posted for
    // and is dismissed by the NEXT destination change (it never leaks onto the following screen).
    val navGeneration = remember { java.util.concurrent.atomic.AtomicInteger(0) }
    androidx.compose.runtime.DisposableEffect(navController) {
        val l = androidx.navigation.NavController.OnDestinationChangedListener { _, _, _ ->
            navGeneration.incrementAndGet()
            snackbar.currentSnackbarData?.dismiss()
        }
        navController.addOnDestinationChangedListener(l)
        onDispose { navController.removeOnDestinationChangedListener(l) }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        com.lifehub.app.core.designsystem.SnackbarBus.events.collect { m ->
            val gen = navGeneration.get()
            snackbar.currentSnackbarData?.dismiss()
            launch {
                if (navGeneration.get() != gen) return@launch // user already left the destination
                val r = snackbar.showSnackbar(m.message, m.actionLabel, duration = androidx.compose.material3.SnackbarDuration.Long)
                if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) m.onAction?.invoke()
            }
        }
    }
    val bottomInset = remember { androidx.compose.runtime.mutableStateOf(0.dp) }
    val diaryDate = remember { androidx.compose.runtime.mutableStateOf<java.time.LocalDate?>(null) }
    var showAdd by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val navBottom = androidx.compose.foundation.layout.WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val elapsedNow by produceState(0, inProgress?.session?.startedAt) {
        while (true) {
            value = inProgress?.let { Duration.between(it.session.startedAt, container.time.now()).seconds.toInt() } ?: 0
            delay(1000)
        }
    }
    val barH = BarHeight + navBottom

    CompositionLocalProvider(
        LocalSnackbarHostState provides snackbar, LocalDiaryDate provides diaryDate,
        com.lifehub.app.core.designsystem.components.LocalDockedBarHeight provides bottomInset,
        com.lifehub.app.core.designsystem.components.LocalTabBottomPadding provides 16.dp,
    ) {
        Scaffold(
            containerColor = LhTheme.colors.background,
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
            // Snackbars sit above the bar and the raised (+) FAB on tabs.
            snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = if (isTab) barH + FabOverhang + 8.dp else bottomInset.value)) },
        ) { padding ->
            TabShell(
                banner = inProgress?.takeIf { isTab }?.let { s ->
                    { ActiveWorkoutBar(s.session.planName, elapsedNow, onResume = { navController.lhNavigate(Routes.WORKOUT_LIVE) }) }
                },
                bottomBar = if (!isTab) null else {
                    {
                        LhBottomBar(currentPattern = pattern, onAdd = { showAdd = true }, addOpen = showAdd) { tab ->
                            navController.navigateToTab(tab.route)
                        }
                    }
                },
                modifier = Modifier.padding(padding),
            ) { bottom ->
                LhNavHost(navController, pendingRoute, onRouteConsumed, androidx.compose.foundation.layout.PaddingValues(bottom = bottom), Modifier.fillMaxSize())
            }
        }
        if (showAdd) {
            val date = (if (pattern == Routes.CALENDAR) diaryDate.value else null) ?: container.time.today()
            val active = inProgress?.let { "${it.session.planName} · ${(elapsedNow / 60).coerceAtLeast(0)} min" }
            AddActionSheet(
                onDismiss = { showAdd = false }, activeWorkout = active,
                onAddFood = {
                    showAdd = false
                    navController.lhNavigate(Routes.foodAdd(date))
                },
                onQuickMacros = { showAdd = false; navController.lhNavigate(Routes.macroEdit(date)) },
                onStartWorkout = { showAdd = false; navController.lhNavigate(if (active != null) Routes.WORKOUT_LIVE else Routes.workoutStart()) },
                onLogWeight = { showAdd = false; navController.lhNavigate(Routes.bodyEdit(container.time.today())) },
                onAskCopilot = { showAdd = false; navController.lhNavigate(Routes.copilot()) },
            )
        }
    }
}

/**
 * Tab-screen chrome. [banner] (the active-workout strip) sits at the top, right below the status bar; it takes over
 * the status-bar inset, so the screens' own headers ([com.lifehub.app.core.designsystem.components.LhScreenHeader])
 * start right under it instead of padding again. [bottomBar] overlays the bottom; [content] gets the bottom padding
 * that keeps it clear of the bar (64dp + nav inset) and the raised (+) FAB ([FabOverhang]).
 */
@Composable
fun TabShell(
    banner: (@Composable () -> Unit)?, bottomBar: (@Composable () -> Unit)?, modifier: Modifier = Modifier,
    content: @Composable (bottomPadding: Dp) -> Unit,
) {
    val navBottom = androidx.compose.foundation.layout.WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(modifier.fillMaxSize()) {
        if (banner != null) {
            Box(Modifier.fillMaxWidth().background(LhTheme.colors.background).statusBarsPadding()) { banner() }
        }
        Box(
            Modifier.weight(1f).fillMaxWidth()
                .then(if (banner != null) Modifier.consumeWindowInsets(androidx.compose.foundation.layout.WindowInsets.statusBars) else Modifier),
        ) {
            content(if (bottomBar != null) BarHeight + navBottom + FabOverhang else 0.dp)
            if (bottomBar != null) Box(Modifier.align(Alignment.BottomCenter)) { bottomBar() }
        }
    }
}

/** Stateless full-width banner shown at the top of tab screens: "Workout · <plan> · mm:ss   Resume >" (taps resume). */
@Composable
fun ActiveWorkoutBar(planName: String, elapsedSec: Int, onResume: () -> Unit, modifier: Modifier = Modifier) {
    val c = LhTheme.colors
    Column(modifier.fillMaxWidth().testTag("active-workout-banner")) {
    Row(
        Modifier.fillMaxWidth().height(44.dp).background(c.accentMuted).clickable(onClick = onResume).padding(start = 16.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(c.accent, LhThemeDot))
        Spacer(Modifier.width(8.dp))
        // Only the plan name ellipsizes; the running clock always stays visible.
        val clock = fmtClock(elapsedSec)
        val full = stringResource(R.string.workout_in_progress, planName, clock)
        Row(Modifier.weight(1f).padding(end = 8.dp)) {
            if (full.endsWith(clock)) {
                Text(full.removeSuffix(clock), style = LhType.label, color = c.accent, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Text(clock, style = LhType.tnum(LhType.label), color = c.accent, maxLines = 1)
            } else {
                Text(full, style = LhType.label, color = c.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(stringResource(R.string.resume), style = LhType.label, color = c.accent)
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = c.accent, modifier = Modifier.size(20.dp))
    }
    HorizontalDivider(color = c.border, thickness = 1.dp)
    }
}

private val LhThemeDot = com.lifehub.app.core.designsystem.LhRadius.fullShape

/** Cradled (+) FAB geometry: 56dp FAB, center on the bar's top edge at the bar's horizontal center, 6dp gap. */
val FabSize = 56.dp
private val FabGap = 6.dp
/**
 * Width reserved between Diary and Train for the FAB + cradle. The notch (cradle + 10dp shoulders) spans ~86dp; the
 * rest keeps the neighbouring labels clear of the shoulders.
 */
val FabSlot = 104.dp
/** How far the FAB rises above the bar's top edge (half its height). */
val FabOverhang = FabSize / 2
private val BarShape = CradleBarShape(cradleRadius = FabSize / 2 + FabGap, shoulderRadius = 10.dp)

/**
 * Stateless bar: Home · Diary, a centered (+) FAB cradled in a notch cut into the bar's top edge, Trends · Train.
 * Total height = [FabOverhang] + 64dp + nav inset; the top [FabOverhang] is transparent except for the FAB.
 */
@Composable
fun LhBottomBar(
    currentPattern: String?, modifier: Modifier = Modifier,
    onAdd: () -> Unit = {}, addOpen: Boolean = false, onTab: (Tab) -> Unit,
) {
    val c = LhTheme.colors
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val rot by androidx.compose.animation.core.animateFloatAsState(if (addOpen) 45f else 0f, com.lifehub.app.core.designsystem.lhTween(200), label = "fabRot")
    Box(modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(top = FabOverhang).fillMaxWidth()
                .background(c.card, BarShape)
                .drawWithCache {
                    val edge = BarShape.topEdgePath(size, layoutDirection, this)
                    val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                    onDrawWithContent {
                        drawContent()
                        drawPath(edge, c.border, style = stroke)
                    }
                }
                .navigationBarsPadding().height(BarHeight),
        ) {
            Tab.entries.forEachIndexed { i, tab ->
                if (i == Tab.entries.size / 2) Spacer(Modifier.width(FabSlot))
                val selected = tab.route == currentPattern
                val color = if (selected) c.primary else c.mutedForeground
                Column(
                    Modifier.weight(1f).fillMaxSize().testTag("tab-${tab.name}").clickable { onTab(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Icon(if (selected) tab.selectedIcon else tab.icon, null, tint = color, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(tab.labelRes), style = LhType.labelSmall, color = color, maxLines = 1)
                }
            }
        }
        Box(
            Modifier.align(Alignment.TopCenter)
                .size(FabSize)
                .com_press(src)
                .shadow(6.dp, CircleShape, ambientColor = c.primary, spotColor = c.primary)
                .background(c.primary, CircleShape).clip(CircleShape)
                .testTag("fab-add")
                .clickable(interactionSource = src, indication = null, onClickLabel = "Add", role = androidx.compose.ui.semantics.Role.Button, onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Add, "Add", tint = c.primaryForeground, modifier = Modifier.size(28.dp).rotate(rot))
        }
    }
}

@Composable
private fun Modifier.com_press(src: androidx.compose.foundation.interaction.MutableInteractionSource): Modifier {
    val pressed by src.collectIsPressedAsState()
    val s by androidx.compose.animation.core.animateFloatAsState(
        if (pressed) 0.92f else 1f,
        androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = androidx.compose.animation.core.Spring.StiffnessMedium), label = "fabPress",
    )
    return this.scale(s)
}
