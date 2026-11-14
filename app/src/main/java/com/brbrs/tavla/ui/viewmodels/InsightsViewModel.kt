package com.brbrs.tavla.ui.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brbrs.tavla.data.Board
import com.brbrs.tavla.data.DeckRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** One of the last 8 weeks, oldest first. [label] is "8 wks ago" .. "This week". */
data class WeekBucket(val label: String, val count: Int)

data class ListLoad(val stackTitle: String, val openCount: Int)

data class InsightsUiState(
    val board: Board? = null,
    val isLoading: Boolean = true,
    /** True once there's at least one card with a usable createdAt+doneAt pair. */
    val hasTimingData: Boolean = false,
    val totalCards: Int = 0,
    val doneCards: Int = 0,
    val overdueNow: Int = 0,
    val dueSoonCount: Int = 0,
    val avgDaysToComplete: Double? = null,
    val doneByWeek: List<WeekBucket> = emptyList(),
    val overdueByWeek: List<WeekBucket> = emptyList(),
    val busiestLists: List<ListLoad> = emptyList(),
)

/**
 * Everything here is computed on-device from what's already synced -- no extra network calls,
 * and nothing is sent anywhere. Because Tavla only has today's snapshot (not a change history),
 * "trend" numbers are necessarily approximations built from current data:
 *  - doneByWeek buckets cards by when they were marked done (Deck's own `done` timestamp).
 *  - overdueByWeek buckets currently-still-open cards by the week their due date fell in -- it
 *    shows which weeks' work is backing up, not a historical count of what was overdue on a given day.
 */
@HiltViewModel
class InsightsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: DeckRepository,
) : ViewModel() {

    private val boardId: Long = checkNotNull(savedStateHandle.get<Long>("boardId"))
    private val weekMs = TimeUnit.DAYS.toMillis(7)

    val uiState: StateFlow<InsightsUiState> = combine(
        repository.observeBoard(boardId),
        repository.observeStacks(boardId),
        repository.observeCards(boardId),
    ) { board, stacks, cards ->
        val now = System.currentTimeMillis()
        val open = cards.filterNot { it.archived }
        val done = open.filter { it.doneAt != null }
        val overdue = open.filter { it.doneAt == null && it.dueDate != null && it.dueDate < now }
        val dueSoon = open.filter { it.doneAt == null && it.dueDate != null && it.dueDate in now..(now + weekMs) }

        fun weekLabel(weeksAgo: Int) = when (weeksAgo) {
            0 -> "This week"
            1 -> "Last week"
            else -> "$weeksAgo wks ago"
        }

        // Buckets 0..7 "weeks ago" relative to now; anything older than 8 weeks is dropped from
        // the chart (it's still counted in the headline totals above).
        fun bucketByWeeksAgo(timestamps: List<Long>): List<WeekBucket> {
            val counts = IntArray(8)
            timestamps.forEach { ts ->
                val weeksAgo = ((now - ts) / weekMs).toInt()
                if (weeksAgo in 0..7) counts[weeksAgo]++
            }
            return (7 downTo 0).map { weeksAgo -> WeekBucket(weekLabel(weeksAgo), counts[weeksAgo]) }
        }

        val doneByWeek = bucketByWeeksAgo(done.mapNotNull { it.doneAt })
        val overdueByWeek = bucketByWeeksAgo(overdue.mapNotNull { it.dueDate })

        val completionTimes = done.mapNotNull { c ->
            val created = c.createdAt ?: return@mapNotNull null
            val finished = c.doneAt ?: return@mapNotNull null
            (finished - created).toDouble().takeIf { it > 0 }
        }
        val avgDays = completionTimes.takeIf { it.isNotEmpty() }
            ?.average()?.div(TimeUnit.DAYS.toMillis(1).toDouble())

        val busiest = stacks
            .map { stack -> ListLoad(stack.title, open.count { it.stackId == stack.id }) }
            .filter { it.openCount > 0 }
            .sortedByDescending { it.openCount }
            .take(6)

        InsightsUiState(
            board = board,
            isLoading = false,
            hasTimingData = completionTimes.isNotEmpty(),
            totalCards = open.size,
            doneCards = done.size,
            overdueNow = overdue.size,
            dueSoonCount = dueSoon.size,
            avgDaysToComplete = avgDays,
            doneByWeek = doneByWeek,
            overdueByWeek = overdueByWeek,
            busiestLists = busiest,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsUiState())

    /** A light local recompute is enough most of the time; this pulls fresh data from Nextcloud first. */
    fun refresh() {
        viewModelScope.launch { runCatching { repository.refreshBoard(boardId) } }
    }
}
