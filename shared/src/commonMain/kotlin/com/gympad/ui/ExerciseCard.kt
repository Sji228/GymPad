package com.gympad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gympad.domain.ExerciseCardUi
import com.gympad.domain.LiftValue
import com.gympad.domain.SetRowUi
import com.gympad.domain.parseReps
import com.gympad.domain.parseWeight
import com.gympad.domain.sanitizeReps
import com.gympad.domain.sanitizeWeight
import com.gympad.ui.strings.LocalStrings
import com.gympad.ui.strings.exerciseName
import com.gympad.ui.strings.liftText
import com.gympad.ui.strings.localizeDecimal
import com.gympad.ui.strings.recordText
import com.gympad.ui.strings.shortDateText
import com.gympad.ui.strings.weightText
import com.gympad.ui.strings.weightWithUnit
import com.gympad.ui.theme.DoneGreen
import com.gympad.ui.theme.DoneTint
import com.gympad.ui.theme.OnStatus
import java.time.LocalDate

private const val WEIGHT_STEP = 2.5

private val ControlHeight = 48.dp
private val SetColumnWidth = 26.dp
private val RepsColumnWidth = 58.dp
private val CheckColumnWidth = 50.dp
private val StepButtonWidth = 40.dp
private val ColumnGap = 6.dp

@Composable
fun ExerciseCard(
    card: ExerciseCardUi,
    readOnly: Boolean,
    onSelectVariant: (Long) -> Unit,
    onAddVariant: () -> Unit,
    onEdit: () -> Unit,
    onWeight: (Int, Double?) -> Unit,
    onReps: (Int, Int?) -> Unit,
    onToggle: (SetRowUi) -> Unit,
    onChangeSetCount: (Int) -> Unit,
) {
    val strings = LocalStrings.current
    var expanded by rememberSaveable { mutableStateOf(true) }
    val skipped = readOnly && card.completedSets == 0
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        CardHeader(card, expanded, readOnly, skipped, onEdit, onToggleExpanded = { expanded = !expanded })
        if (!expanded || skipped) return@Column
        if (!readOnly) {
            VariantPills(card, onSelectVariant, onAddVariant)
            ContextStrip(card)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        if (readOnly) {
            ReadOnlySets(card)
        } else {
            Column(
                Modifier.padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SetTableHeader()
                card.sets.forEach { row ->
                    key(card.exerciseId, row.index) {
                        EditableSetRow(
                            exerciseName = strings.exerciseName(card.exerciseName),
                            row = row,
                            onWeight = { onWeight(row.index, it) },
                            onReps = { onReps(row.index, it) },
                            onToggle = { onToggle(row) },
                        )
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    QuietButton(
                        text = strings.removeSet,
                        icon = GymIcons.Minus,
                        enabled = card.sets.size > 1,
                        height = 44.dp,
                        modifier = Modifier.weight(1f),
                        onClick = { onChangeSetCount(-1) },
                    )
                    QuietButton(
                        text = strings.addSet,
                        icon = GymIcons.Plus,
                        height = 44.dp,
                        modifier = Modifier.weight(1f),
                        onClick = { onChangeSetCount(1) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CardHeader(
    card: ExerciseCardUi,
    expanded: Boolean,
    readOnly: Boolean,
    skipped: Boolean,
    onEdit: () -> Unit,
    onToggleExpanded: () -> Unit,
) {
    val strings = LocalStrings.current
    val name = strings.exerciseName(card.exerciseName)
    val colors = MaterialTheme.colorScheme
    val finished = card.completedSets >= card.plannedSets
    Row(
        Modifier
            .fillMaxWidth()
            .focusProperties { canFocus = false }
            .clickable(enabled = !skipped, onClick = onToggleExpanded)
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
            .heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(
                name,
                style = MaterialTheme.typography.titleMedium,
                color = if (skipped) colors.onSurfaceVariant else colors.onSurface,
            )
            if (!expanded && card.completedSets > 0) {
                Text(
                    card.sets.filter { it.completed }.joinToString(" · ") { row ->
                        strings.liftText(LiftValue(row.weightKg ?: 0.0, row.reps ?: 0))
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        if (skipped) {
            Text(
                strings.skipped,
                style = MaterialTheme.typography.labelMedium,
                color = colors.outline,
                modifier = Modifier.padding(end = 12.dp),
            )
            return@Row
        }
        Text(
            "${card.completedSets}/${card.plannedSets}",
            style = MaterialTheme.typography.labelMedium,
            color = if (finished) DoneGreen else colors.outline,
        )
        if (!readOnly) {
            IconAction(
                GymIcons.More,
                strings.editExercise(name),
                onEdit,
                tint = colors.onSurfaceVariant,
                size = 44.dp,
            )
        } else {
            Spacer(Modifier.width(8.dp))
        }
        Icon(
            if (expanded) GymIcons.ChevronUp else GymIcons.ChevronDown,
            contentDescription = if (expanded) strings.collapse else strings.expand,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.padding(end = 10.dp).size(22.dp),
        )
    }
}

/** The title already names the current variant, so only the ones to swap to appear here. */
@Composable
private fun VariantPills(
    card: ExerciseCardUi,
    onSelect: (Long) -> Unit,
    onAdd: () -> Unit,
) {
    val strings = LocalStrings.current
    val colors = MaterialTheme.colorScheme
    val others = card.variants.filter { it.exerciseId != card.exerciseId }
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (others.isNotEmpty()) {
            Icon(GymIcons.Swap, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.outline)
            Spacer(Modifier.width(2.dp))
        }
        others.forEach { variant ->
            Box(
                Modifier
                    .height(40.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceContainerHigh)
                    .focusProperties { canFocus = false }
                    .clickable { onSelect(variant.exerciseId) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    strings.exerciseName(variant.name),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurface,
                    maxLines = 1,
                )
            }
        }
        Row(
            Modifier
                .height(40.dp)
                .clip(CircleShape)
                .border(1.dp, colors.outlineVariant, CircleShape)
                .focusProperties { canFocus = false }
                .clickable(onClick = onAdd)
                .padding(horizontal = if (others.isEmpty()) 14.dp else 10.dp)
                .semantics { contentDescription = strings.addVariantTo(strings.exerciseName(card.exerciseName)) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(GymIcons.Plus, contentDescription = null, modifier = Modifier.size(18.dp), tint = colors.onSurfaceVariant)
            if (others.isEmpty()) {
                Spacer(Modifier.width(6.dp))
                Text(strings.addVariant, style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ContextStrip(card: ExerciseCardUi) {
    val record = card.personalRecord
    if (record == null) {
        Spacer(Modifier.height(12.dp))
        return
    }
    val strings = LocalStrings.current
    val colors = MaterialTheme.colorScheme
    val lastLabel = card.lastDay?.let { "${strings.last} · ${strings.shortDateText(LocalDate.ofEpochDay(it))}" } ?: strings.last
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            SectionLabel(lastLabel)
            Spacer(Modifier.height(2.dp))
            Text(
                card.lastSets.takeIf { it.isNotEmpty() }?.joinToString(" · ", transform = strings::liftText) ?: "—",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            SectionLabel(strings.personalRecord)
            Spacer(Modifier.height(2.dp))
            Text(
                strings.recordText(record),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onSurface,
            )
        }
    }
}

@Composable
private fun SetTableHeader() {
    val strings = LocalStrings.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .padding(bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(ColumnGap),
    ) {
        ColumnTitle(strings.columnSet, Modifier.width(SetColumnWidth))
        ColumnTitle("kg  ±${strings.weightText(WEIGHT_STEP)}", Modifier.weight(1f))
        ColumnTitle(strings.columnReps, Modifier.width(RepsColumnWidth))
        Spacer(Modifier.width(CheckColumnWidth))
    }
}

@Composable
private fun ColumnTitle(text: String, modifier: Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = modifier,
    )
}

/** Selecting on focus lets a new number replace the old one without clearing it first. */
private class NumberFieldState(initial: String) {
    var value by mutableStateOf(TextFieldValue(initial))
    var focused by mutableStateOf(false)
    val text: String get() = value.text

    fun setText(text: String) {
        value = TextFieldValue(text, TextRange(text.length))
    }
}

@Composable
private fun EditableSetRow(
    exerciseName: String,
    row: SetRowUi,
    onWeight: (Double?) -> Unit,
    onReps: (Int?) -> Unit,
    onToggle: () -> Unit,
) {
    val strings = LocalStrings.current
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    val focusManager = LocalFocusManager.current
    val haptics = LocalHapticFeedback.current
    val weight = remember(strings.decimalSeparator) { NumberFieldState(strings.weightText(row.weightKg)) }
    val reps = remember { NumberFieldState(row.reps?.toString().orEmpty()) }
    var repsMissing by remember { mutableStateOf(false) }
    val repsFocus = remember { FocusRequester() }
    val setNumber = row.index + 1

    // While focused the text wins: a database echo mid-typing is always a keystroke behind.
    LaunchedEffect(row.weightKg, weight.focused) {
        if (!weight.focused && parseWeight(weight.text) != row.weightKg) weight.setText(strings.weightText(row.weightKg))
    }
    LaunchedEffect(row.reps, reps.focused) {
        if (!reps.focused && parseReps(reps.text) != row.reps) reps.setText(row.reps?.toString().orEmpty())
    }

    fun stepWeight(delta: Double) {
        val base = parseWeight(weight.text) ?: row.ghost.weightKg ?: 0.0
        val next = (base + delta).coerceAtLeast(0.0)
        weight.setText(strings.weightText(next))
        onWeight(next)
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (row.completed) DoneTint else Color.Transparent)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ColumnGap),
    ) {
        Text(
            "${row.index + 1}",
            style = MaterialTheme.typography.labelMedium,
            color = if (row.completed) DoneGreen else colors.outline,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(SetColumnWidth),
        )
        Row(
            Modifier
                .weight(1f)
                .height(ControlHeight)
                .clip(shape)
                .background(if (row.completed) Color.Transparent else colors.surfaceContainerHigh)
                .then(if (weight.focused && !row.completed) Modifier.border(1.5.dp, colors.primary, shape) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepButton(GymIcons.Minus, strings.decreaseWeight(exerciseName, setNumber), visible = !row.completed) { stepWeight(-WEIGHT_STEP) }
            NumberInput(
                state = weight,
                ghost = strings.weightText(row.ghost.weightKg),
                description = strings.weightField(exerciseName, setNumber),
                keyboardType = KeyboardType.Decimal,
                readOnly = row.completed,
                modifier = Modifier.weight(1f),
                sanitize = { strings.localizeDecimal(sanitizeWeight(it)) },
                onTextChange = { onWeight(parseWeight(it)) },
            )
            StepButton(GymIcons.Plus, strings.increaseWeight(exerciseName, setNumber), visible = !row.completed) { stepWeight(WEIGHT_STEP) }
        }
        Box(
            Modifier
                .width(RepsColumnWidth)
                .height(ControlHeight)
                .clip(shape)
                .background(if (row.completed) Color.Transparent else colors.surfaceContainerHigh)
                .then(
                    when {
                        repsMissing -> Modifier.border(1.5.dp, colors.error, shape)
                        reps.focused && !row.completed -> Modifier.border(1.5.dp, colors.primary, shape)
                        else -> Modifier
                    },
                ),
        ) {
            NumberInput(
                state = reps,
                ghost = row.ghost.reps?.toString().orEmpty(),
                description = strings.repsField(exerciseName, setNumber),
                keyboardType = KeyboardType.Number,
                readOnly = row.completed,
                modifier = Modifier.fillMaxWidth().focusRequester(repsFocus),
                sanitize = ::sanitizeReps,
                onTextChange = {
                    repsMissing = false
                    onReps(parseReps(it))
                },
            )
        }
        val checkDescription =
            if (row.completed) strings.undoSet(exerciseName, setNumber) else strings.completeSet(exerciseName, setNumber)
        CheckButton(row.completed, checkDescription) {
            if (!row.completed && (parseReps(reps.text) ?: row.ghost.reps ?: 0) <= 0) {
                repsMissing = true
                repsFocus.requestFocus()
            } else {
                repsMissing = false
                focusManager.clearFocus()
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onToggle()
            }
        }
    }
}

@Composable
private fun NumberInput(
    state: NumberFieldState,
    ghost: String,
    description: String,
    keyboardType: KeyboardType,
    readOnly: Boolean,
    modifier: Modifier,
    sanitize: (String) -> String,
    onTextChange: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val numberStyle = MaterialTheme.typography.titleLarge

    LaunchedEffect(state.focused) {
        if (state.focused) {
            // A tap places the cursor in the same frame it grants focus, so select on the next one.
            withFrameMillis { }
            state.value = state.value.copy(selection = TextRange(0, state.text.length))
        }
    }

    BasicTextField(
        value = state.value,
        onValueChange = { next ->
            val clean = sanitize(next.text)
            val changed = clean != state.text
            state.value = if (clean == next.text) next else TextFieldValue(clean, TextRange(clean.length))
            if (changed) onTextChange(clean)
        },
        readOnly = readOnly,
        singleLine = true,
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
        textStyle = numberStyle.copy(color = colors.onSurface, textAlign = TextAlign.Center),
        modifier = modifier
            .fillMaxHeight()
            .onFocusChanged { state.focused = it.isFocused }
            .semantics { contentDescription = description },
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth().fillMaxHeight(), contentAlignment = Alignment.Center) {
                if (state.text.isEmpty()) {
                    Text(
                        ghost,
                        style = numberStyle.copy(fontWeight = FontWeight.Normal),
                        color = colors.outline,
                        textAlign = TextAlign.Center,
                    )
                }
                inner()
            }
        },
    )
}

@Composable
private fun StepButton(icon: ImageVector, description: String, visible: Boolean, onClick: () -> Unit) {
    if (!visible) {
        Spacer(Modifier.width(StepButtonWidth))
        return
    }
    Box(
        Modifier
            .width(StepButtonWidth)
            .fillMaxHeight()
            .focusProperties { canFocus = false }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CheckButton(completed: Boolean, description: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(width = CheckColumnWidth, height = ControlHeight)
            .clip(MaterialTheme.shapes.medium)
            .background(if (completed) DoneGreen else colors.surfaceContainerHigh)
            .focusProperties { canFocus = false }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            GymIcons.Check,
            contentDescription = description,
            modifier = Modifier.size(22.dp),
            tint = if (completed) OnStatus else colors.outline,
        )
    }
}

@Composable
private fun ReadOnlySets(card: ExerciseCardUi) {
    val strings = LocalStrings.current
    val colors = MaterialTheme.colorScheme
    val done = card.sets.filter { it.completed }
    val missed = card.plannedSets - done.size
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp)) {
        done.forEach { row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${row.index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.outline,
                    modifier = Modifier.width(28.dp),
                )
                Text(
                    strings.weightWithUnit(row.weightKg),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    strings.repsValue(row.reps ?: 0),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Icon(GymIcons.Check, contentDescription = strings.completed, modifier = Modifier.size(18.dp), tint = DoneGreen)
            }
        }
        if (missed > 0) {
            Text(
                strings.setsNotDone(missed),
                style = MaterialTheme.typography.bodySmall,
                color = colors.outline,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
