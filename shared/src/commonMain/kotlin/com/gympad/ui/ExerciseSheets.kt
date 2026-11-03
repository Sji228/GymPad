package com.gympad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gympad.data.Exercise
import com.gympad.domain.TemplateSlot
import com.gympad.domain.TemplateVariant
import com.gympad.domain.matching
import com.gympad.ui.strings.LocalStrings
import com.gympad.ui.strings.exerciseName

private const val MAX_VARIANTS = 6
private const val MAX_SETS = 12
private const val NEW_VARIANT = -1L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GymSheet(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outline) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
fun EditExerciseSheet(
    slot: TemplateSlot,
    exercises: List<Exercise>,
    editor: EditorViewModel,
    startAddingVariant: Boolean,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    var editingId by remember { mutableStateOf(if (startAddingVariant) NEW_VARIANT else null) }
    var confirmRemove by remember { mutableStateOf(false) }
    val title = slot.variants.firstOrNull()?.let { strings.exerciseName(it.name) } ?: strings.exerciseFallback

    GymSheet(title = title, onDismiss = onDismiss) {
        SectionLabel(strings.variants)
        Spacer(Modifier.height(4.dp))
        Text(
            strings.variantsExplanation,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            slot.variants.forEach { variant ->
                if (editingId == variant.variantId) {
                    VariantNameField(
                        slot = slot,
                        variant = variant,
                        exercises = exercises,
                        editor = editor,
                        onFinished = { editingId = null },
                    )
                } else {
                    VariantRow(
                        name = strings.exerciseName(variant.name),
                        isMain = variant == slot.variants.first() && slot.variants.size > 1,
                        canMakeMain = variant != slot.variants.first(),
                        canRemove = slot.variants.size > 1,
                        onMakeMain = { editor.makeVariantMain(slot.slotId, variant.variantId) },
                        onRename = { editingId = variant.variantId },
                        onRemove = { editor.removeVariant(variant.variantId) },
                    )
                }
            }
            when {
                editingId == NEW_VARIANT -> VariantNameField(
                    slot = slot,
                    variant = null,
                    exercises = exercises,
                    editor = editor,
                    onFinished = { editingId = null },
                )
                slot.variants.size < MAX_VARIANTS -> QuietButton(
                    text = strings.addVariant,
                    icon = GymIcons.Plus,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { editingId = NEW_VARIANT },
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        SectionLabel(strings.sets)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                strings.plannedSets,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconAction(
                GymIcons.Minus,
                strings.fewerSets,
                { editor.setDefaultSets(slot.slotId, slot.defaultSets - 1) },
                enabled = slot.defaultSets > 1,
            )
            Text(
                slot.defaultSets.toString(),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.width(28.dp),
                textAlign = TextAlign.Center,
            )
            IconAction(
                GymIcons.Plus,
                strings.moreSets,
                { editor.setDefaultSets(slot.slotId, slot.defaultSets + 1) },
                enabled = slot.defaultSets < MAX_SETS,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            if (confirmRemove) strings.removeConfirm else strings.removeFromRoutine,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .clickable {
                    if (confirmRemove) {
                        editor.deleteSlot(slot.slotId)
                        onDismiss()
                    } else {
                        confirmRemove = true
                    }
                }
                .padding(vertical = 14.dp),
        )
    }
}

@Composable
private fun VariantRow(
    name: String,
    isMain: Boolean,
    canMakeMain: Boolean,
    canRemove: Boolean,
    onMakeMain: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
) {
    val strings = LocalStrings.current
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(colors.surfaceContainerHigh)
            .clickable(onClick = onRename)
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (isMain) {
                Text(strings.mainVariant, style = MaterialTheme.typography.bodySmall, color = colors.outline)
            }
        }
        if (canMakeMain) {
            IconAction(GymIcons.ArrowUp, strings.makeMain(name), onMakeMain, tint = colors.onSurfaceVariant, size = 44.dp, iconSize = 18.dp)
        }
        IconAction(GymIcons.Pencil, strings.renameVariant(name), onRename, tint = colors.onSurfaceVariant, size = 44.dp, iconSize = 18.dp)
        if (canRemove) {
            IconAction(GymIcons.Close, strings.removeVariant(name), onRemove, tint = colors.onSurfaceVariant, size = 44.dp, iconSize = 18.dp)
        }
    }
}

/**
 * Renaming keeps the exercise and its history. Typing a name that already exists, in any language,
 * instead links this variant to that exercise's history, so one lift never splits into two records.
 */
@Composable
private fun VariantNameField(
    slot: TemplateSlot,
    variant: TemplateVariant?,
    exercises: List<Exercise>,
    editor: EditorViewModel,
    onFinished: () -> Unit,
) {
    val strings = LocalStrings.current
    val colors = MaterialTheme.colorScheme
    val currentName = variant?.let { strings.exerciseName(it.name) }
    var text by remember { mutableStateOf(currentName.orEmpty()) }
    val focusRequester = remember { FocusRequester() }
    val trimmed = text.trim()
    val match = exercises.matching(trimmed)
    val takenInSlot = match != null && match.id != variant?.exerciseId && slot.variants.any { it.exerciseId == match.id }
    val canSave = trimmed.isNotEmpty() && !takenInSlot
    val linksExisting = match != null && match.id != variant?.exerciseId && !takenInSlot

    fun save() {
        if (!canSave) return
        when {
            variant == null -> editor.addVariant(slot.slotId, trimmed)
            match == null || match.id == variant.exerciseId -> editor.renameExercise(variant.exerciseId, trimmed)
            else -> editor.pointVariantAt(slot.slotId, variant.variantId, trimmed)
        }
        onFinished()
    }

    LaunchedEffect(Unit) {
        withFrameMillis { }
        focusRequester.requestFocus()
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        GymTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = strings.exerciseNamePlaceholder,
            description = if (currentName == null) strings.newVariantName else strings.renameVariant(currentName),
            isError = takenInSlot,
            onDone = ::save,
            modifier = Modifier.focusRequester(focusRequester),
            trailing = {
                Row {
                    IconAction(GymIcons.Close, strings.cancel, onFinished, tint = colors.onSurfaceVariant, size = 44.dp, iconSize = 18.dp)
                    IconAction(GymIcons.Check, strings.saveVariant, ::save, enabled = canSave, size = 44.dp, iconSize = 20.dp)
                }
            },
        )
        when {
            takenInSlot -> Hint(strings.alreadyVariant, colors.error)
            linksExisting -> Hint(strings.linksHistory(strings.exerciseName(match.name)), colors.onSurfaceVariant)
            variant != null -> Hint(strings.renameKeepsHistory, colors.outline)
        }
        if (variant == null) {
            SuggestionList(
                names = suggestions(trimmed, exercises.filter { exercise ->
                    exercise.id != match?.id && slot.variants.none { it.exerciseId == exercise.id }
                }),
                onPick = { text = it },
            )
        }
    }
}

/** Matches the stored name too, so "squat" still finds a built-in shown as "Przysiad ze sztangą". */
@Composable
private fun suggestions(typed: String, candidates: List<Exercise>): List<String> {
    if (typed.isEmpty()) return emptyList()
    val strings = LocalStrings.current
    return candidates
        .filter { strings.exerciseName(it.name).contains(typed, ignoreCase = true) || it.name.contains(typed, ignoreCase = true) }
        .take(4)
        .map { strings.exerciseName(it.name) }
}

@Composable
private fun Hint(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
    )
}

@Composable
fun AddExerciseSheet(
    routineName: String,
    exercises: List<Exercise>,
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val trimmed = text.trim()
    val match = exercises.matching(trimmed)

    LaunchedEffect(Unit) {
        withFrameMillis { }
        focusRequester.requestFocus()
    }

    fun add() {
        if (trimmed.isEmpty()) return
        onAdd(trimmed)
        onDismiss()
    }

    GymSheet(title = strings.addTo(routineName), onDismiss = onDismiss) {
        GymTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = strings.exerciseNamePlaceholder,
            description = strings.newExerciseName,
            onDone = ::add,
            modifier = Modifier.focusRequester(focusRequester),
        )
        SuggestionList(
            names = suggestions(trimmed, exercises.filter { it.id != match?.id }),
            onPick = { text = it },
        )
        Spacer(Modifier.height(12.dp))
        PrimaryButton(strings.addExercise, onClick = ::add, enabled = trimmed.isNotEmpty())
    }
}
