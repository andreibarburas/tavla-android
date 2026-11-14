package com.brbrs.tavla.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * A pending destructive action that can be undone within [UNDO_TIMEOUT_MS].
 * Screens show a Snackbar with "Undo" while [pending] is non-null.
 */
data class PendingUndo(
    val label: String,
    val execute: suspend () -> Unit,
)

private const val UNDO_TIMEOUT_MS = 4_000L

/**
 * Small state holder shared between ViewModels. Manages a single undo-able action at a time.
 * Call [schedule] to post a destructive action; [cancel] if the user taps Undo.
 */
class UndoController(private val scope: CoroutineScope) {
    private val _pending = MutableStateFlow<PendingUndo?>(null)
    val pending: StateFlow<PendingUndo?> = _pending

    private var timerJob: Job? = null

    fun schedule(label: String, execute: suspend () -> Unit) {
        // If there was an earlier pending action, fire it immediately before replacing.
        timerJob?.cancel()
        _pending.value?.let { prev -> scope.launch { runCatching { prev.execute() } } }

        _pending.value = PendingUndo(label, execute)
        timerJob = scope.launch {
            delay(UNDO_TIMEOUT_MS)
            commit()
        }
    }

    /** User tapped Undo — cancel the pending action. */
    fun cancel() {
        timerJob?.cancel()
        _pending.value = null
    }

    /** Fire the pending action now (timeout expired). */
    private suspend fun commit() {
        val action = _pending.value ?: return
        _pending.value = null
        runCatching { action.execute() }
    }

    /** Called when the ViewModel clears, to flush any pending action. */
    fun flush() {
        timerJob?.cancel()
        _pending.value?.let { action -> scope.launch { runCatching { action.execute() } } }
        _pending.value = null
    }
}
