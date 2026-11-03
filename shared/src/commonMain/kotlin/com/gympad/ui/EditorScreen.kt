package com.gympad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gympad.domain.BuiltInNames
import com.gympad.domain.TemplateSlot
import com.gympad.ui.strings.LocalStrings
import com.gympad.ui.strings.exerciseName
import com.gympad.ui.strings.routineName

private const val MAX_ROUTINE_NAME = 16

@Composable
fun EditorScreen(viewModel: EditorViewModel, onBack: () -> Unit) {
    val strings = LocalStrings.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editingSlotId by rememberSaveable { mutableStateOf<Long?>(null) }
    var addingExercise by rememberSaveable { mutableStateOf(false) }

    PlatformBackHandler(onBack = onBack)

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        ScreenTopBar(strings.routines, onBack)
        RoutineTabs(
            routines = state.routines,
            selectedId = state.routine?.id,
            loggedIds = emptySet(),
            onSelect = viewModel::selectRoutine,
        )
        val routine = state.routine ?: return@Column
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "name-${routine.id}") {
                var name by remember(routine.id) { mutableStateOf(BuiltInNames.routine(routine.name, strings.language)) }
                Column(Modifier.padding(bottom = 12.dp)) {
                    SectionLabel(strings.routineNameLabel, Modifier.padding(start = 4.dp, bottom = 6.dp))
                    GymTextField(
                        value = name,
                        onValueChange = {
                            name = it.take(MAX_ROUTINE_NAME)
                            viewModel.renameRoutine(routine, name.trim())
                        },
                        placeholder = strings.routinePlaceholder(routine.letter),
                        description = strings.routineNameField,
                    )
                }
            }
            item(key = "exercises-label") {
                SectionLabel(strings.exercisesLabel, Modifier.padding(start = 4.dp))
            }
            itemsIndexed(state.slots, key = { _, slot -> slot.slotId }) { index, slot ->
                SlotRow(
                    slot = slot,
                    canMoveUp = index > 0,
                    canMoveDown = index < state.slots.lastIndex,
                    onOpen = { editingSlotId = slot.slotId },
                    onMove = { delta -> viewModel.moveSlot(slot.slotId, delta) },
                )
            }
            item(key = "add") {
                Spacer(Modifier.height(4.dp))
                PrimaryButton(strings.addExercise, onClick = { addingExercise = true })
            }
        }
    }

    state.allSlots.firstOrNull { it.slotId == editingSlotId }?.let { slot ->
        EditExerciseSheet(
            slot = slot,
            exercises = state.exercises,
            editor = viewModel,
            startAddingVariant = false,
            onDismiss = { editingSlotId = null },
        )
    }
    if (addingExercise) {
        state.routine?.let { routine ->
            AddExerciseSheet(
                routineName = strings.routineName(routine),
                exercises = state.exercises,
                onAdd = { viewModel.addSlot(routine.id, it) },
                onDismiss = { addingExercise = false },
            )
        }
    }
}

@Composable
private fun SlotRow(
    slot: TemplateSlot,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onOpen: () -> Unit,
    onMove: (Int) -> Unit,
) {
    val strings = LocalStrings.current
    val colors = MaterialTheme.colorScheme
    val main = slot.variants.firstOrNull()?.let { strings.exerciseName(it.name) } ?: strings.exerciseFallback
    val others = slot.variants.drop(1).joinToString(", ") { strings.exerciseName(it.name) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colors.surfaceContainer)
            .clickable(onClick = onOpen)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(main, style = MaterialTheme.typography.titleSmall)
            Text(
                strings.setCount(slot.defaultSets) + if (others.isEmpty()) "" else " · ${strings.alternativesPrefix} $others",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconAction(GymIcons.ArrowUp, strings.moveUp(main), { onMove(-1) }, enabled = canMoveUp, tint = colors.onSurfaceVariant, size = 44.dp, iconSize = 20.dp)
        IconAction(GymIcons.ArrowDown, strings.moveDown(main), { onMove(1) }, enabled = canMoveDown, tint = colors.onSurfaceVariant, size = 44.dp, iconSize = 20.dp)
    }
}
