package com.gympad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gympad.domain.DayStatus
import com.gympad.ui.strings.LocalStrings
import com.gympad.ui.strings.fullDateText
import com.gympad.ui.strings.weekdayLetter
import com.gympad.ui.theme.DoneGreen
import com.gympad.ui.theme.OnStatus
import com.gympad.ui.theme.PartialYellow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

@Composable
private fun statusColor(status: DayStatus?): Color = when (status) {
    DayStatus.COMPLETE -> DoneGreen
    DayStatus.PARTIAL -> PartialYellow
    DayStatus.REST, null -> MaterialTheme.colorScheme.surfaceContainerHighest
}

private fun weekStart(epochDay: Long): LocalDate =
    LocalDate.ofEpochDay(epochDay).with(DayOfWeek.MONDAY)

@Composable
fun WeekRibbon(state: MainUiState, onSelectDay: (Long) -> Unit) {
    val strings = LocalStrings.current
    val firstWeek = weekStart(state.firstDay)
    val weekCount = ChronoUnit.WEEKS.between(firstWeek, weekStart(state.today)).toInt() + 1
    val pagerState = rememberPagerState(initialPage = weekCount - 1) { weekCount }

    // Follows the selection (history, "Today") without undoing a manual swipe.
    LaunchedEffect(state.selectedDay, weekCount) {
        pagerState.scrollToPage(ChronoUnit.WEEKS.between(firstWeek, weekStart(state.selectedDay)).toInt())
    }

    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { page ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            for (offset in 0L until 7L) {
                val date = firstWeek.plusWeeks(page.toLong()).plusDays(offset)
                val epochDay = date.toEpochDay()
                val selectable = epochDay <= state.today
                Column(
                    Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.medium)
                        .focusProperties { canFocus = false }
                        .clickable(enabled = selectable) { onSelectDay(epochDay) }
                        .padding(vertical = 4.dp)
                        .semantics(mergeDescendants = true) { contentDescription = strings.fullDateText(date) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        strings.weekdayLetter(date),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (epochDay == state.today) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                    )
                    Spacer(Modifier.height(4.dp))
                    DayCircle(
                        date = date,
                        status = state.statuses[epochDay],
                        selected = epochDay == state.selectedDay,
                        isToday = epochDay == state.today,
                        isFuture = !selectable,
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCircle(date: LocalDate, status: DayStatus?, selected: Boolean, isToday: Boolean, isFuture: Boolean) {
    val colors = MaterialTheme.colorScheme
    val hasStatus = status == DayStatus.COMPLETE || status == DayStatus.PARTIAL
    Box(
        Modifier
            .size(44.dp)
            .then(
                when {
                    selected -> Modifier.border(2.dp, colors.primary, CircleShape)
                    isToday -> Modifier.border(1.5.dp, colors.outline, CircleShape)
                    else -> Modifier
                },
            )
            .padding(4.dp)
            .clip(CircleShape)
            .background(if (isFuture) Color.Transparent else statusColor(status)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (isToday || hasStatus) FontWeight.Bold else FontWeight.Medium,
            ),
            color = when {
                isFuture -> colors.outline
                hasStatus -> OnStatus
                isToday || selected -> colors.onSurface
                else -> colors.onSurfaceVariant
            },
        )
    }
}

private sealed interface YearItem {
    data class Header(val year: Int) : YearItem
    data class Month(val month: YearMonth) : YearItem
}

@Composable
fun YearView(state: MainUiState, onSelectDay: (Long) -> Unit) {
    val items = remember(state.firstDay, state.today) {
        val first = YearMonth.from(LocalDate.ofEpochDay(state.firstDay))
        val last = YearMonth.from(LocalDate.ofEpochDay(state.today))
        buildList<YearItem> {
            var month = last
            var year = -1
            while (month >= first) {
                if (month.year != year) {
                    year = month.year
                    add(YearItem.Header(year))
                }
                add(YearItem.Month(month))
                month = month.minusMonths(1)
            }
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { Legend() }
        items(items, span = { item ->
            GridItemSpan(if (item is YearItem.Header) maxLineSpan else 1)
        }) { item ->
            when (item) {
                is YearItem.Header -> Text(item.year.toString(), style = MaterialTheme.typography.titleMedium)
                is YearItem.Month -> MonthGrid(item.month, state, onSelectDay)
            }
        }
    }
}

@Composable
private fun Legend() {
    val strings = LocalStrings.current
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendItem(DoneGreen, strings.legendComplete)
        LegendItem(PartialYellow, strings.legendIncomplete)
        LegendItem(MaterialTheme.colorScheme.surfaceContainerHighest, strings.legendRest)
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.size(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MonthGrid(month: YearMonth, state: MainUiState, onSelectDay: (Long) -> Unit) {
    val strings = LocalStrings.current
    Column {
        Text(
            strings.monthNames[month.monthValue - 1],
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = 2.dp, bottom = 6.dp),
        )
        Row(Modifier.fillMaxWidth().padding(bottom = 2.dp)) {
            strings.weekdayLetters.forEach { letter ->
                Text(
                    letter,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        val leadingBlanks = month.atDay(1).dayOfWeek.value - 1
        val cells = List(leadingBlanks) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                for (i in 0 until 7) {
                    val date = week.getOrNull(i)
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp)) {
                        if (date != null) YearDayDot(date, state, onSelectDay)
                    }
                }
            }
        }
    }
}

@Composable
private fun YearDayDot(date: LocalDate, state: MainUiState, onSelectDay: (Long) -> Unit) {
    val strings = LocalStrings.current
    val colors = MaterialTheme.colorScheme
    val epochDay = date.toEpochDay()
    val inRange = epochDay in state.firstDay..state.today
    Box(
        Modifier
            .fillMaxSize()
            .clip(CircleShape)
            .background(if (inRange) statusColor(state.statuses[epochDay]) else colors.surfaceContainerLow)
            .then(if (epochDay == state.selectedDay) Modifier.border(1.5.dp, colors.primary, CircleShape) else Modifier)
            .focusProperties { canFocus = false }
            .clickable(enabled = inRange) { onSelectDay(epochDay) }
            .semantics { contentDescription = strings.fullDateText(date) },
    )
}
