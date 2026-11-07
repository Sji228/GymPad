package com.gympad.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gympad.domain.BodyMeasure
import com.gympad.domain.BodyPoint
import com.gympad.domain.accepts
import com.gympad.domain.formatAmount
import com.gympad.domain.parseAmount
import com.gympad.ui.strings.AppStrings
import com.gympad.ui.strings.LocalStrings
import com.gympad.ui.strings.amountText
import com.gympad.ui.strings.fullDateText
import com.gympad.ui.strings.fullDayText
import com.gympad.ui.strings.localizeDecimal
import com.gympad.ui.strings.measureName
import com.gympad.ui.strings.measureText
import com.gympad.ui.strings.shortDateText
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs

/** An open add-or-edit sheet: a new entry, a past one being fixed, or the height behind BMI. */
private data class MeasureEdit(val measure: BodyMeasure, val point: BodyPoint?)

@Composable
fun MeScreen(
    viewModel: MeViewModel,
    drawerContent: @Composable (closeDrawer: () -> Unit) -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<MeasureEdit?>(null) }
    val measure = state.selected

    PlatformBackHandler(onBack = onBack)

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
            Row(
                Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconAction(GymIcons.Menu, strings.menu, { scope.launch { drawerState.open() } })
                Spacer(Modifier.width(4.dp))
                Text(strings.me, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 32.dp)) {
                item(key = "chips") {
                    MeasureChips(selected = measure, onSelect = viewModel::select)
                    Spacer(Modifier.height(16.dp))
                }
                item(key = "panel") {
                    MeasurePanel(
                        state = state,
                        onAdd = { editing = MeasureEdit(measure, null) },
                        onHeight = { editing = MeasureEdit(BodyMeasure.HEIGHT, null) },
                    )
                }
                if (state.points.isNotEmpty()) {
                    item(key = "history") {
                        SectionLabel(strings.history, Modifier.padding(start = 4.dp, top = 20.dp, bottom = 4.dp))
                    }
                    items(state.points.asReversed(), key = { "${it.epochDay}" }) { point ->
                        HistoryRow(
                            measure = measure,
                            point = point,
                            onClick = if (measure.computed) null else { { editing = MeasureEdit(measure, point) } },
                        )
                    }
                }
            }
        }
    }

    editing?.let { edit ->
        val point = edit.point
        MeasureSheet(
            measure = edit.measure,
            initialValue = point?.value ?: state.latest[edit.measure],
            initialDay = point?.epochDay ?: state.today,
            today = state.today,
            onSave = { day, value -> viewModel.save(edit.measure, day, value, replacingId = point?.id) },
            onDelete = point?.id?.let { id -> { viewModel.delete(id) } },
            onDismiss = { editing = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MeasureChips(selected: BodyMeasure, onSelect: (BodyMeasure) -> Unit) {
    val strings = LocalStrings.current
    val colors = MaterialTheme.colorScheme
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BodyMeasure.chips.forEach { measure ->
            val isSelected = measure == selected
            Box(
                Modifier
                    .height(42.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) colors.primary else colors.surfaceContainerHigh)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(measure) })
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    strings.measureName(measure),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) colors.onPrimary else colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MeasurePanel(state: MeUiState, onAdd: () -> Unit, onHeight: () -> Unit) {
    val strings = LocalStrings.current
    val colors = MaterialTheme.colorScheme
    val measure = state.selected
    val points = state.points
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colors.surfaceContainer)
            .padding(16.dp),
    ) {
        Text(strings.measureName(measure), style = MaterialTheme.typography.titleSmall, color = colors.onSurfaceVariant)
        val last = points.lastOrNull()
        if (last != null) {
            Text(strings.measureText(measure, last.value), style = MaterialTheme.typography.headlineSmall)
            val first = points.first()
            if (points.size > 1) {
                Text(
                    strings.changeSince(
                        signed(strings, measure, last.value - first.value),
                        strings.shortDateText(LocalDate.ofEpochDay(first.epochDay)),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))
            MeasureChart(points)
        } else {
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    !measure.computed -> strings.emptyMeasure
                    state.height == null -> strings.bmiNeedsHeight
                    else -> strings.bmiNeedsWeight
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(16.dp))
        if (measure.computed) {
            val height = state.height
            if (height != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        strings.heightValue(strings.measureText(BodyMeasure.HEIGHT, height)),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    QuietButton(strings.changeHeight, icon = GymIcons.Pencil, onClick = onHeight, height = 44.dp)
                }
            } else {
                PrimaryButton(strings.setHeight, onClick = onHeight)
            }
        } else {
            PrimaryButton(strings.add, onClick = onAdd)
        }
    }
}

private fun signed(strings: AppStrings, measure: BodyMeasure, change: Double): String {
    val sign = when {
        change > 0.05 -> "+"
        change < -0.05 -> "−"
        else -> "±"
    }
    return sign + strings.measureText(measure, abs(change))
}

/** Time runs left to right at true spacing, so a gap in logging shows as a gap. */
@Composable
private fun MeasureChart(points: List<BodyPoint>) {
    val strings = LocalStrings.current
    val colors = MaterialTheme.colorScheme
    val low = points.minOf { it.value }
    val high = points.maxOf { it.value }
    val pad = if (high - low < 0.5) 0.5 else (high - low) * 0.15
    val bottom = low - pad
    val top = high + pad
    val firstDay = points.first().epochDay
    val lastDay = points.last().epochDay
    val line = colors.primary
    val grid = colors.outlineVariant

    Row(Modifier.fillMaxWidth().height(180.dp)) {
        Column(
            Modifier.width(44.dp).fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf(top, (top + bottom) / 2, bottom).forEach {
                Text(
                    strings.localizeDecimal(formatAmount(it)),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.outline,
                )
            }
        }
        Canvas(Modifier.weight(1f).fillMaxHeight().padding(vertical = 7.dp)) {
            listOf(0f, 0.5f, 1f).forEach { fraction ->
                val y = size.height * fraction
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
            fun position(point: BodyPoint): Offset {
                val x = if (lastDay == firstDay) size.width / 2 else size.width * (point.epochDay - firstDay) / (lastDay - firstDay).toFloat()
                val y = size.height * (1 - ((point.value - bottom) / (top - bottom)).toFloat())
                return Offset(x, y)
            }
            val offsets = points.map(::position)
            if (offsets.size > 1) {
                val path = Path().apply {
                    moveTo(offsets.first().x, offsets.first().y)
                    offsets.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(path, line, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            offsets.forEachIndexed { i, offset ->
                drawCircle(line, radius = (if (i == offsets.lastIndex) 5.dp else 3.5.dp).toPx(), center = offset)
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(start = 44.dp, top = 6.dp)) {
        Text(
            strings.shortDateText(LocalDate.ofEpochDay(firstDay)),
            style = MaterialTheme.typography.labelSmall,
            color = colors.outline,
            modifier = Modifier.weight(1f),
        )
        if (lastDay != firstDay) {
            Text(
                strings.shortDateText(LocalDate.ofEpochDay(lastDay)),
                style = MaterialTheme.typography.labelSmall,
                color = colors.outline,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun HistoryRow(measure: BodyMeasure, point: BodyPoint, onClick: (() -> Unit)?) {
    val strings = LocalStrings.current
    val date = strings.fullDateText(LocalDate.ofEpochDay(point.epochDay))
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClickLabel = strings.editMeasurement(strings.measureName(measure), date), onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 4.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(date, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(strings.measureText(measure, point.value), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun MeasureSheet(
    measure: BodyMeasure,
    initialValue: Double?,
    initialDay: Long,
    today: Long,
    onSave: (day: Long, value: Double) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    val colors = MaterialTheme.colorScheme
    var text by remember { mutableStateOf(initialValue?.let(strings::amountText).orEmpty()) }
    var day by remember { mutableStateOf(initialDay) }
    val value = parseAmount(text)?.takeIf(measure::accepts)

    GymSheet(title = strings.measureName(measure), onDismiss = onDismiss) {
        GymTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = "",
            description = strings.measureName(measure),
            isError = text.isNotBlank() && value == null,
            keyboardType = KeyboardType.Decimal,
            onDone = {
                if (value != null) {
                    onSave(day, value)
                    onDismiss()
                }
            },
            trailing = {
                Text(measure.unit, style = MaterialTheme.typography.bodyLarge, color = colors.outline, modifier = Modifier.padding(end = 12.dp))
            },
        )
        Spacer(Modifier.height(14.dp))
        SectionLabel(strings.dateLabel, Modifier.padding(start = 4.dp, bottom = 4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconAction(GymIcons.ChevronLeft, strings.previousDay, { day -= 1 })
            val date = LocalDate.ofEpochDay(day)
            Text(
                if (day == today) "${strings.today}, ${strings.fullDayText(date).substringAfter(", ")}" else strings.fullDayText(date),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            // Measurements are of the past and present; there's nothing to plan ahead.
            IconAction(GymIcons.ChevronRight, strings.nextDay, { day += 1 }, enabled = day < today)
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            strings.save,
            enabled = value != null,
            onClick = {
                if (value != null) {
                    onSave(day, value)
                    onDismiss()
                }
            },
        )
        if (onDelete != null) {
            QuietButton(
                strings.removeEntry,
                icon = GymIcons.Close,
                onClick = {
                    onDelete()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
