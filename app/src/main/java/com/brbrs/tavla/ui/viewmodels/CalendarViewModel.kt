package com.brbrs.tavla.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brbrs.tavla.data.Card
import com.brbrs.tavla.data.CardWithContext
import com.brbrs.tavla.data.DeckRepository
import com.brbrs.tavla.reminders.ReminderScheduler
import com.brbrs.tavla.ui.components.friendlyError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

enum class CalendarMode { Month, Week }

data class CalendarUiState(
    val mode: CalendarMode = CalendarMode.Month,
    val anchorMonth: YearMonth = YearMonth.now(),
    /** The week shown in Week mode -- the Monday it starts on. */
    val anchorWeekStart: LocalDate = LocalDate.now().with(java.time.DayOfWeek.MONDAY),
    val selectedDate: LocalDate = LocalDate.now(),
    /** Every card with a due date, across every open board, grouped by the local calendar day it falls on. */
    val cardsByDay: Map<LocalDate, List<CardWithContext>> = emptyMap(),
    val message: String? = null,
)

/**
 * #03: a week/month view of every due card across every board, computed from the same
 * already-synced data the boards screen uses -- no extra network calls. Tapping a day shows its
 * cards below the grid; dragging a card onto another day (handled by [CalendarScreen]'s own drag
 * gesture, which calls [rescheduleCard]) moves its due date there, keeping the original time of day.
 */
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val repository: DeckRepository,
    private val reminderScheduler: ReminderScheduler,
) : ViewModel() {

    private data class Local(
        val mode: CalendarMode = CalendarMode.Month,
        val anchorMonth: YearMonth = YearMonth.now(),
        val anchorWeekStart: LocalDate = LocalDate.now().with(java.time.DayOfWeek.MONDAY),
        val selectedDate: LocalDate = LocalDate.now(),
        val message: String? = null,
    )

    private val local = MutableStateFlow(Local())
    private val zone = ZoneId.systemDefault()

    val uiState: StateFlow<CalendarUiState> = combine(
        repository.observeAllOpenCards(),
        local,
    ) { allCards, l ->
        val withDue = allCards.filter { it.card.dueDate != null }
        val byDay = withDue.groupBy { Instant.ofEpochMilli(it.card.dueDate!!).atZone(zone).toLocalDate() }
        CalendarUiState(
            mode = l.mode,
            anchorMonth = l.anchorMonth,
            anchorWeekStart = l.anchorWeekStart,
            selectedDate = l.selectedDate,
            cardsByDay = byDay,
            message = l.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    fun setMode(mode: CalendarMode) = local.update { it.copy(mode = mode) }
    fun selectDate(date: LocalDate) = local.update { it.copy(selectedDate = date) }
    fun consumeMessage() = local.update { it.copy(message = null) }

    fun previousPeriod() = local.update {
        when (it.mode) {
            CalendarMode.Month -> it.copy(anchorMonth = it.anchorMonth.minusMonths(1))
            CalendarMode.Week  -> it.copy(anchorWeekStart = it.anchorWeekStart.minusWeeks(1))
        }
    }

    fun nextPeriod() = local.update {
        when (it.mode) {
            CalendarMode.Month -> it.copy(anchorMonth = it.anchorMonth.plusMonths(1))
            CalendarMode.Week  -> it.copy(anchorWeekStart = it.anchorWeekStart.plusWeeks(1))
        }
    }

    fun today() = local.update {
        it.copy(
            anchorMonth = YearMonth.now(),
            anchorWeekStart = LocalDate.now().with(java.time.DayOfWeek.MONDAY),
            selectedDate = LocalDate.now(),
        )
    }

    /** Moves [card]'s due date to [newDate], keeping its existing time of day (noon if it had none). */
    fun rescheduleCard(card: Card, newDate: LocalDate) {
        val oldTime = card.dueDate?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() } ?: LocalTime.NOON
        val newMs = newDate.atTime(oldTime).atZone(zone).toInstant().toEpochMilli()
        if (card.dueDate == newMs) return
        viewModelScope.launch {
            val r = runCatching {
                repository.updateCard(card, dueDate = newMs)
                reminderScheduler.reschedule(card.copy(dueDate = newMs))
            }
            r.exceptionOrNull()?.let { e -> local.update { s -> s.copy(message = friendlyError(e)) } }
        }
    }
}
