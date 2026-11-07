package com.gympad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gympad.domain.Macros
import com.gympad.domain.Meal
import com.gympad.ui.strings.AppStrings
import com.gympad.ui.strings.LocalStrings
import com.gympad.ui.strings.foodName
import com.gympad.ui.strings.fullDateText
import com.gympad.ui.strings.fullDayText
import com.gympad.ui.strings.gramsText
import com.gympad.ui.strings.kcalText
import com.gympad.ui.strings.mealName
import com.gympad.ui.strings.weekdayName
import com.gympad.ui.theme.PartialYellow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun FoodScreen(
    viewModel: FoodViewModel,
    mealPhoto: MealPhotoViewModel,
    drawerContent: @Composable (closeDrawer: () -> Unit) -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val removed by viewModel.removed.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var calendarOpen by rememberSaveable { mutableStateOf(false) }
    var addingTo by rememberSaveable { mutableStateOf<Meal?>(null) }
    var editingEntry by remember { mutableStateOf<FoodEntryUi?>(null) }
    var editingGoals by rememberSaveable { mutableStateOf(false) }
    val cameraFor by mealPhoto.camera.collectAsStateWithLifecycle()
    val photoStep by mealPhoto.step.collectAsStateWithLifecycle()

    // Empty meals offer to copy the same meal from the last day that had it.
    val emptyMeals = state.meals.filter { it.entries.isEmpty() }.map { it.meal }
    var copySources by remember { mutableStateOf<Map<Meal, Long>>(emptyMap()) }
    LaunchedEffect(state.selectedDay, emptyMeals) {
        copySources = emptyMeals.mapNotNull { meal -> viewModel.previousMealDay(meal)?.let { meal to it } }.toMap()
    }

    PlatformBackHandler(enabled = calendarOpen) { calendarOpen = false }
    PlatformBackHandler(enabled = !calendarOpen, onBack = onBack)

    val days = DayRange(state.firstDay, state.lastDay, state.today, state.selectedDay, state.statuses)

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = { drawerContent { scope.launch { drawerState.close() } } },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .safeDrawingPadding(),
        ) {
            Column(Modifier.fillMaxSize()) {
                FoodTopBar(
                    selectedDay = state.selectedDay,
                    today = state.today,
                    calendarOpen = calendarOpen,
                    onMenu = { scope.launch { drawerState.open() } },
                    onToggleCalendar = { calendarOpen = !calendarOpen },
                    onBackToToday = { viewModel.selectDay(state.today) },
                )
                if (calendarOpen) {
                    // A month ahead is enough to plan; the ribbon reaches further for the rare long plan.
                    val calendarEnd = LocalDate.ofEpochDay(maxOf(state.today, state.selectedDay)).plusMonths(1)
                    YearView(
                        state = days.copy(lastDay = calendarEnd.withDayOfMonth(calendarEnd.lengthOfMonth()).toEpochDay()),
                        legend = LegendLabels(strings.legendOnTarget, strings.legendOffTarget, strings.legendNothingLogged),
                        onSelectDay = {
                            viewModel.selectDay(it)
                            calendarOpen = false
                        },
                    )
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 96.dp),
                    ) {
                        item(key = "ribbon") {
                            WeekRibbon(state = days, onSelectDay = viewModel::selectDay)
                        }
                        // Pinned below the top bar, so the day's totals stay in view while scrolling to supper.
                        stickyHeader(key = "summary") {
                            DaySummary(
                                state = state,
                                onEditGoals = { editingGoals = true },
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.background)
                                    .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 10.dp),
                            )
                        }
                        items(state.meals, key = { it.meal.name }) { meal ->
                            MealCard(
                                meal = meal,
                                copyFrom = copySources[meal.meal]?.let { copyLabel(strings, it, state.selectedDay) },
                                onAdd = { addingTo = meal.meal },
                                onPhoto = { mealPhoto.requestPhoto(meal.meal) },
                                onCopy = { copySources[meal.meal]?.let { viewModel.copyMeal(it, meal.meal) } },
                                onEntry = { editingEntry = it },
                                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                            )
                        }
                    }
                }
            }
            removed?.let { entry ->
                UndoBar(
                    text = strings.removedEntry(strings.foodName(entry.item)),
                    onUndo = viewModel::undoDelete,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                )
                LaunchedEffect(entry) {
                    delay(5_000)
                    viewModel.dismissUndo()
                }
            }
        }
    }

    addingTo?.let { meal ->
        AddFoodSheet(meal = meal, viewModel = viewModel, onDismiss = { addingTo = null })
    }
    editingEntry?.let { entry ->
        EditEntrySheet(entry = entry, viewModel = viewModel, onDismiss = { editingEntry = null })
    }
    if (editingGoals) {
        GoalsSheet(goals = state.goals, onSave = viewModel::setGoals, onDismiss = { editingGoals = false })
    }
    photoStep?.let { step ->
        MealPhotoSheet(step = step, viewModel = mealPhoto, onAdd = { mealPhoto.add(state.selectedDay, it) })
    }
    if (cameraFor != null) {
        LocalMealCamera.current(mealPhoto::photoTaken, mealPhoto::closeCamera)
    }
}

private fun copyLabel(strings: AppStrings, fromDay: Long, selectedDay: Long): String =
    strings.copyMealFrom(
        if (fromDay == selectedDay - 1) strings.yesterday else strings.fullDateText(LocalDate.ofEpochDay(fromDay)).substringBeforeLast(' '),
    )

@Composable
private fun FoodTopBar(
    selectedDay: Long,
    today: Long,
    calendarOpen: Boolean,
    onMenu: () -> Unit,
    onToggleCalendar: () -> Unit,
    onBackToToday: () -> Unit,
) {
    val strings = LocalStrings.current
    val colors = MaterialTheme.colorScheme
    val date = LocalDate.ofEpochDay(selectedDay)
    val isToday = selectedDay == today
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconAction(GymIcons.Menu, strings.menu, onMenu)
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(
                when {
                    calendarOpen -> strings.history
                    isToday -> strings.today
                    else -> strings.weekdayName(date)
                },
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            if (!calendarOpen) {
                Text(
                    if (isToday) strings.fullDayText(date) else strings.fullDateText(date),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        if (!isToday && !calendarOpen) {
            Box(
                Modifier
                    .height(36.dp)
                    .clip(CircleShape)
                    .background(colors.primary)
                    .focusProperties { canFocus = false }
                    .clickable(onClick = onBackToToday)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(strings.today, style = MaterialTheme.typography.labelLarge, color = colors.onPrimary)
            }
        }
        IconAction(
            if (calendarOpen) GymIcons.Close else GymIcons.Calendar,
            if (calendarOpen) strings.closeHistory else strings.openHistory,
            onToggleCalendar,
        )
    }
}

@Composable
private fun DaySummary(state: FoodUiState, onEditGoals: () -> Unit, modifier: Modifier) {
    val strings = LocalStrings.current
    val colors = MaterialTheme.colorScheme
    val goals = state.goals
    val totals = state.totals
    val kcalGoal = goals.kcal?.takeIf { it > 0 }
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colors.surfaceContainer)
            .clickable(role = Role.Button, onClickLabel = strings.goalsTitle, onClick = onEditGoals)
            .padding(16.dp),
    ) {
        // The bars always show; without a goal they stay empty and the card offers to set one.
        Row(verticalAlignment = Alignment.Bottom) {
            Text(totals.kcal.roundToInt().toString(), style = MaterialTheme.typography.headlineSmall)
            Text(
                if (kcalGoal != null) " / $kcalGoal ${strings.kcal}" else " ${strings.kcal}",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 3.dp),
            )
            Spacer(Modifier.weight(1f))
            when {
                kcalGoal == null -> Text(strings.setGoals, style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                totals.kcal <= kcalGoal -> Text(
                    strings.kcalLeft(strings.kcalText(kcalGoal - totals.kcal)),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
                else -> Text(
                    strings.kcalOver(strings.kcalText(totals.kcal - kcalGoal)),
                    style = MaterialTheme.typography.labelMedium,
                    color = PartialYellow,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Progress(totals.kcal, kcalGoal?.toDouble(), height = 6)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MacroColumn(strings.protein, totals.protein, goals.protein, Modifier.weight(1f))
            MacroColumn(strings.fat, totals.fat, goals.fat, Modifier.weight(1f))
            MacroColumn(strings.carbs, totals.carbs, goals.carbs, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MacroColumn(label: String, grams: Double, goal: Int?, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val target = goal?.takeIf { it > 0 }
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
        Text(
            if (target != null) "${grams.roundToInt()} / $target g" else "${grams.roundToInt()} g",
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
        Spacer(Modifier.height(6.dp))
        Progress(grams, target?.toDouble(), height = 4)
    }
}

@Composable
private fun Progress(value: Double, goal: Double?, height: Int) {
    val colors = MaterialTheme.colorScheme
    val over = goal != null && value > goal * 1.1
    Box(
        Modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(CircleShape)
            .background(colors.surfaceContainerHighest),
    ) {
        Box(
            Modifier
                .fillMaxWidth(if (goal == null || goal <= 0) 0f else (value / goal).coerceIn(0.0, 1.0).toFloat())
                .height(height.dp)
                .clip(CircleShape)
                .background(if (over) PartialYellow else colors.primary),
        )
    }
}

@Composable
private fun MealCard(
    meal: MealUi,
    copyFrom: String?,
    onAdd: () -> Unit,
    onPhoto: () -> Unit,
    onCopy: () -> Unit,
    onEntry: (FoodEntryUi) -> Unit,
    modifier: Modifier,
) {
    val strings = LocalStrings.current
    val colors = MaterialTheme.colorScheme
    val name = strings.mealName(meal.meal)
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colors.surfaceContainer),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                if (meal.entries.isNotEmpty()) {
                    Text(macroLine(strings, meal.totals), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
            IconAction(
                GymIcons.Camera,
                strings.mealPhoto(name),
                onPhoto,
                tint = colors.onSurfaceVariant,
                size = 44.dp,
                iconSize = 20.dp,
            )
            Box(
                Modifier
                    .padding(start = 2.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceContainerHighest)
                    .focusProperties { canFocus = false }
                    .clickable(role = Role.Button, onClick = onAdd)
                    .semantics { contentDescription = strings.addFoodTo(name) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(GymIcons.Plus, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
        meal.entries.forEach { entry ->
            EntryRow(entry, onClick = { onEntry(entry) })
        }
        if (meal.entries.isEmpty() && copyFrom != null) {
            QuietButton(
                text = copyFrom,
                icon = GymIcons.Copy,
                onClick = onCopy,
                height = 44.dp,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun EntryRow(entry: FoodEntryUi, onClick: () -> Unit) {
    val strings = LocalStrings.current
    val colors = MaterialTheme.colorScheme
    val name = strings.foodName(entry.item)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = strings.editEntry(name), onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(entry.item.brand, strings.gramsText(entry.grams)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(strings.kcalText(entry.macros.kcal), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
    }
}

/** "420 kcal · P 30 · C 41 · F 12", the shape every total takes. */
fun macroLine(strings: AppStrings, macros: Macros): String =
    "${strings.kcalText(macros.kcal)} · ${strings.proteinShort} ${macros.protein.roundToInt()} · " +
        "${strings.carbsShort} ${macros.carbs.roundToInt()} · ${strings.fatShort} ${macros.fat.roundToInt()}"

@Composable
private fun UndoBar(text: String, onUndo: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(colors.surfaceContainerHighest)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        QuietButton(text = LocalStrings.current.undo, icon = null, onClick = onUndo, height = 48.dp)
    }
}
