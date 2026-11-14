package com.brbrs.tavla.solidtime.timer

/**
 * The single active Solidtime timer, as Tavla understands it. Mirrors the server's one running
 * entry, enriched with the Deck card it belongs to when we know it (from our local entry->card
 * map, or because we started it here). A locally-started timer that hasn't reached the server yet
 * has an [entryId] beginning with "local-".
 */
data class RunningTimer(
    val entryId: String,
    val startMs: Long,
    val description: String,
    val billable: Boolean,
    val projectId: String?,
    val taskId: String?,
    val tagIds: List<String>,
    /** The Deck card this timer is tracking, if known. */
    val cardId: Long?,
    val boardId: Long?,
    /** For the notification/banner: the board's project name, and the card title if we have it. */
    val projectName: String?,
    val cardTitle: String?,
) {
    val isLocalOnly: Boolean get() = entryId.startsWith("local-")

    companion object
}
