package com.brbrs.tavla.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.brbrs.tavla.data.CardWithContext
import com.brbrs.tavla.ui.components.DueBadge
import com.brbrs.tavla.ui.components.EmptyState
import com.brbrs.tavla.ui.components.ScreenBackground
import com.brbrs.tavla.ui.components.TopBarIcon
import com.brbrs.tavla.ui.components.colorFromHex
import com.brbrs.tavla.ui.theme.LocalIsDark
import com.brbrs.tavla.ui.theme.tavlaCard
import com.brbrs.tavla.ui.theme.tavlaChip
import com.brbrs.tavla.ui.viewmodels.CalendarMode
import com.brbrs.tavla.ui.viewmodels.CalendarViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToInt

/**
 * #03: a week/month view of every due card across every board (BoardScreen/BoardsScreen are
 * per-board or due-soon-only; this is the cross-board planning view). Tap a day to see its cards
 * below the grid; drag a card's handle onto another day to move its due date there.
 */
@Composable
fun CalendarScreen(
    onBack: () -> Unit,
    onCardClick: (boardId: Long, cardId: Long) -> Unit,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDark = LocalIsDark.current
    val accent = MaterialTheme.colorScheme.primary
    val haptics = LocalHapticFeedback.current

    // Drag state, shared between the day grid (drop targets) and the selected day's card list
    // (drag sources). Window coordinates so hit-testing works regardless of scroll position.
    var dragCard by remember { mutableStateOf<CardWithContext?>(null) }
    var dragWindowPos by remember { mutableStateOf(Offset.Zero) }
    var hoveredDate by remember { mutableStateOf<LocalDate?>(null) }
    val dayRects = remember { mutableStateMapOf<LocalDate, Rect>() }
    var rootWindowOrigin by remember { mutableStateOf(Offset.Zero) }

    val days = remember(uiState.mode, uiState.anchorMonth, uiState.anchorWeekStart) {
        when (uiState.mode) {
            CalendarMode.Month -> {
                val firstGridDay = uiState.anchorMonth.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                (0 until 42).map { firstGridDay.plusDays(it.toLong()) }
            }
            CalendarMode.Week -> (0 until 7).map { uiState.anchorWeekStart.plusDays(it.toLong()) }
        }
    }
    val periodLabel = remember(uiState.mode, uiState.anchorMonth, uiState.anchorWeekStart) {
        when (uiState.mode) {
            CalendarMode.Month -> uiState.anchorMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + uiState.anchorMonth.year
            CalendarMode.Week -> {
                val end = uiState.anchorWeekStart.plusDays(6)
                if (uiState.anchorWeekStart.month == end.month) {
                    "${uiState.anchorWeekStart.dayOfMonth}–${end.dayOfMonth} ${end.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${end.year}"
                } else {
                    "${uiState.anchorWeekStart.dayOfMonth} ${uiState.anchorWeekStart.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} – " +
                        "${end.dayOfMonth} ${end.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())}"
                }
            }
        }
    }
    val today = remember { LocalDate.now() }
    val selectedCards = uiState.cardsByDay[uiState.selectedDate].orEmpty()
        .sortedBy { it.card.dueDate }

    ScreenBackground {
        Box(
            modifier = Modifier.fillMaxSize().onGloballyPositioned { rootWindowOrigin = it.localToWindow(Offset.Zero) },
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // -- Header --
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 20.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TopBarIcon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") { onBack() }
                    Text(
                        "Calendar",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                    )
                    TopBarIcon(Icons.Outlined.Today, "Jump to today") { viewModel.today() }
                }

                // -- Month/Week toggle --
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CalendarMode.entries.forEach { m ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .tavlaChip(isDark, selected = uiState.mode == m, selectedColor = accent)
                                .clickable { viewModel.setMode(m) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (m == CalendarMode.Month) "Month" else "Week",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (uiState.mode == m) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // -- Period navigation --
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TopBarIcon(Icons.Outlined.ChevronLeft, "Previous") { viewModel.previousPeriod() }
                    Text(periodLabel, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    TopBarIcon(Icons.Outlined.ChevronRight, "Next") { viewModel.nextPeriod() }
                }

                // -- Weekday header --
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    DayOfWeek.entries.forEach { dow ->
                        Text(
                            dow.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // -- Day grid (drop targets) --
                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    userScrollEnabled = false,
                ) {
                    items(days) { date ->
                        DayCell(
                            date = date,
                            inCurrentPeriod = uiState.mode == CalendarMode.Week || date.month == uiState.anchorMonth.month,
                            isSelected = date == uiState.selectedDate,
                            isToday = date == today,
                            isHovered = date == hoveredDate && dragCard != null,
                            dueCount = uiState.cardsByDay[date]?.size ?: 0,
                            accent = accent,
                            isDark = isDark,
                            onClick = { viewModel.selectDate(date) },
                            onRect = { rect -> dayRects[date] = rect },
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // -- Selected day's cards (drag sources) --
                Text(
                    uiState.selectedDate.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
                if (selectedCards.isEmpty()) {
                    EmptyState(
                        icon = Icons.Outlined.Event,
                        title = "No cards due",
                        body = "Nothing is due on this day across any of your boards.",
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(selectedCards, key = { it.card.id }) { item ->
                            CalendarCardRow(
                                item = item,
                                isDark = isDark,
                                onClick = { onCardClick(item.card.boardId, item.card.id) },
                                onDragStart = { handleOrigin, start ->
                                    dragCard = item
                                    dragWindowPos = handleOrigin + start
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDrag = { amount ->
                                    dragWindowPos += amount
                                    hoveredDate = dayRects.entries.firstOrNull { (_, r) -> r.contains(dragWindowPos) }?.key
                                },
                                onDragEnd = {
                                    hoveredDate?.let { target -> viewModel.rescheduleCard(item.card, target) }
                                    dragCard = null
                                    hoveredDate = null
                                },
                                onDragCancel = { dragCard = null; hoveredDate = null },
                            )
                        }
                    }
                }
            }

            // -- Floating drag preview, following the finger --
            dragCard?.let { dc ->
                val localPos = dragWindowPos - rootWindowOrigin
                Box(
                    modifier = Modifier
                        .offset { IntOffset(localPos.x.roundToInt() - 70, localPos.y.roundToInt() - 20) }
                        .zIndex(10f)
                        .width(140.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colorFromHex(dc.boardColor).copy(alpha = 0.9f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Text(
                        dc.card.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    inCurrentPeriod: Boolean,
    isSelected: Boolean,
    isToday: Boolean,
    isHovered: Boolean,
    dueCount: Int,
    accent: Color,
    isDark: Boolean,
    onClick: () -> Unit,
    onRect: (Rect) -> Unit,
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .onGloballyPositioned { onRect(it.boundsInWindow()) }
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    isHovered  -> accent.copy(alpha = 0.30f)
                    isSelected -> accent.copy(alpha = 0.16f)
                    else       -> Color.Transparent
                }
            )
            .border(
                width = if (isToday) 1.5.dp else 0.dp,
                color = if (isToday) accent else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "${date.dayOfMonth}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (inCurrentPeriod) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
            if (dueCount > 0) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (isSelected || isHovered) accent else accent.copy(alpha = 0.6f)),
                )
            }
        }
    }
}

@Composable
private fun CalendarCardRow(
    item: CardWithContext,
    isDark: Boolean,
    onClick: () -> Unit,
    onDragStart: (handleWindowOrigin: Offset, startOffset: Offset) -> Unit,
    onDrag: (amount: Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    var handleWindowOrigin by remember { mutableStateOf(Offset.Zero) }
    val boardColor = colorFromHex(item.boardColor)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tavlaCard(isDark, cornerRadius = 14.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Outlined.DragIndicator,
            "Drag to another day to reschedule",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier
                .onGloballyPositioned { handleWindowOrigin = it.localToWindow(Offset.Zero) }
                .pointerInputDrag(onDragStart, onDrag, onDragEnd, onDragCancel) { handleWindowOrigin },
        )
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(boardColor))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                item.card.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (item.card.doneAt != null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                item.boardTitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        item.card.dueDate?.let { DueBadge(it) }
    }
}

/** A small [Modifier] helper wiring [detectDragGestures] to the callback shape [CalendarCardRow] wants. */
private fun Modifier.pointerInputDrag(
    onDragStart: (handleWindowOrigin: Offset, startOffset: Offset) -> Unit,
    onDrag: (amount: Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    handleWindowOrigin: () -> Offset,
): Modifier = this.then(
    Modifier.padding(4.dp).let { base ->
        base.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { start -> onDragStart(handleWindowOrigin(), start) },
                onDrag = { change, amount -> change.consume(); onDrag(amount) },
                onDragEnd = onDragEnd,
                onDragCancel = onDragCancel,
            )
        }
    }
)
