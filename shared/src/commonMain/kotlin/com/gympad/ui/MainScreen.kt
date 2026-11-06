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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gympad.ui.strings.LocalStrings
import com.gympad.ui.strings.fullDateText
import com.gympad.ui.strings.fullDayText
import com.gympad.ui.strings.routineName
import com.gympad.ui.strings.weekdayName
import kotlinx.coroutines.launch
import java.time.LocalDate

private data class SlotSheet(val slotId: Long, val startAddingVariant: Boolean)

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    editor: EditorViewModel,
    drawerContent: @Composable (closeDrawer: () -> Unit) -> Unit,
) {
    val strings = LocalStrings.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val editorState by editor.uiState.collectAsStateWithLifecycle()
    // Edge swipes must not open the drawer while swiping the ribbon, so only the menu button opens it.
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var historyOpen by rememberSaveable { mutableStateOf(false) }
    var slotSheet by remember { mutableStateOf<SlotSheet?>(null) }
    var addingExercise by rememberSaveable { mutableStateOf(false) }

    PlatformBackHandler(enabled = historyOpen) { historyOpen = false }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = { drawerContent { scope.launch { drawerState.close() } } },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .safeDrawingPadding(),
        ) {
            MainTopBar(
                state = state,
                historyOpen = historyOpen,
                onMenu = { scope.launch { drawerState.open() } },
                onToggleHistory = { historyOpen = !historyOpen },
                onBackToToday = { viewModel.selectDay(state.today) },
            )
            if (historyOpen) {
                YearView(
                    state = state.days,
                    legend = LegendLabels(strings.legendComplete, strings.legendIncomplete, strings.legendRest),
                    onSelectDay = {
                        viewModel.selectDay(it)
                        historyOpen = false
                    },
                )
            } else {
                DayContent(
                    state = state,
                    viewModel = viewModel,
                    onEdit = { slotSheet = SlotSheet(it, startAddingVariant = false) },
                    onAddVariant = { slotSheet = SlotSheet(it, startAddingVariant = true) },
                    onAddExercise = { addingExercise = true },
                )
            }
        }
    }

    slotSheet?.let { sheet ->
        editorState.allSlots.firstOrNull { it.slotId == sheet.slotId }?.let { slot ->
            EditExerciseSheet(
                slot = slot,
                exercises = editorState.exercises,
                editor = editor,
                startAddingVariant = sheet.startAddingVariant,
                onDismiss = { slotSheet = null },
            )
        }
    }
    if (addingExercise) {
        state.routines.firstOrNull { it.id == state.routineId }?.let { routine ->
            AddExerciseSheet(
                routineName = strings.routineName(routine),
                exercises = editorState.exercises,
                onAdd = { editor.addSlot(routine.id, it) },
                onDismiss = { addingExercise = false },
            )
        }
    }
}

@Composable
private fun MainTopBar(
    state: MainUiState,
    historyOpen: Boolean,
    onMenu: () -> Unit,
    onToggleHistory: () -> Unit,
    onBackToToday: () -> Unit,
) {
    val strings = LocalStrings.current
    val colors = MaterialTheme.colorScheme
    val date = LocalDate.ofEpochDay(state.selectedDay)
    val isToday = state.selectedDay == state.today
    val title = when {
        historyOpen -> strings.history
        isToday -> strings.today
        else -> strings.weekdayName(date)
    }
    val subtitle = when {
        historyOpen -> null
        isToday -> strings.fullDayText(date)
        else -> strings.fullDateText(date)
    }
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
                title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            if (subtitle != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isToday) {
                        Icon(
                            GymIcons.Lock,
                            contentDescription = strings.readOnly,
                            modifier = Modifier.padding(end = 4.dp).size(12.dp),
                            tint = colors.onSurfaceVariant,
                        )
                    }
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
        }
        if (!isToday && !historyOpen) {
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
            if (historyOpen) GymIcons.Close else GymIcons.Calendar,
            if (historyOpen) strings.closeHistory else strings.openHistory,
            onToggleHistory,
        )
    }
}

@Composable
private fun DayContent(
    state: MainUiState,
    viewModel: MainViewModel,
    onEdit: (Long) -> Unit,
    onAddVariant: (Long) -> Unit,
    onAddExercise: () -> Unit,
) {
    // The ribbon and tabs scroll away so a workout in progress gets the whole screen for its sets.
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(key = "ribbon") {
            WeekRibbon(state = state.days, onSelectDay = viewModel::selectDay)
        }
        if (!state.isReadOnly || state.loggedRoutineIds.isNotEmpty()) {
            item(key = "tabs") {
                RoutineTabs(
                    routines = state.routines,
                    selectedId = state.routineId,
                    loggedIds = state.loggedRoutineIds,
                    onSelect = viewModel::selectRoutine,
                    modifier = Modifier.padding(top = 10.dp, bottom = 12.dp),
                )
            }
        }
        if (state.cards.isEmpty()) {
            item(key = "empty") {
                EmptyDay(state, onAddExercise, Modifier.fillParentMaxHeight(0.6f))
            }
        }
        items(state.cards, key = { it.key }) { card ->
            Box(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp)) {
                ExerciseCard(
                    card = card,
                    readOnly = state.isReadOnly,
                    onSelectVariant = { exerciseId -> card.slotId?.let { viewModel.selectVariant(it, exerciseId) } },
                    onAddVariant = { card.slotId?.let(onAddVariant) },
                    onEdit = { card.slotId?.let(onEdit) },
                    onWeight = { index, value -> viewModel.setWeight(card, index, value) },
                    onReps = { index, value -> viewModel.setReps(card, index, value) },
                    onToggle = { row -> viewModel.toggleSet(card, row) },
                    onChangeSetCount = { delta -> viewModel.changeSetCount(card, delta) },
                )
            }
        }
        if (!state.isReadOnly && state.cards.isNotEmpty()) {
            item(key = "add-exercise") {
                QuietButton(
                    text = LocalStrings.current.addExercise,
                    icon = GymIcons.Plus,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    onClick = onAddExercise,
                )
            }
        }
    }
}

@Composable
private fun EmptyDay(state: MainUiState, onAddExercise: () -> Unit, modifier: Modifier) {
    val strings = LocalStrings.current
    val colors = MaterialTheme.colorScheme
    val routine = state.routines.firstOrNull { it.id == state.routineId }?.let(strings::routineName).orEmpty()
    val (title, detail) = when {
        !state.isReadOnly -> strings.emptyRoutineTitle(routine) to strings.emptyRoutineDetail
        state.loggedRoutineIds.isEmpty() -> strings.restDayTitle to strings.restDayDetail
        else -> strings.notTrainedTitle to strings.notTrainedDetail(routine)
    }
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            detail,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (!state.isReadOnly) {
            Spacer(Modifier.height(20.dp))
            PrimaryButton(strings.addExercise, onClick = onAddExercise)
        }
    }
}
