package com.brbrs.tavla.solidtime

import androidx.compose.runtime.Immutable

/**
 * Plain models for the Solidtime REST API (v1). Everything here mirrors the JSON the server
 * returns; IDs are UUID strings. See [SolidtimeApi] for the endpoints and [SolidtimeJson] for
 * parsing. The integration model, agreed with Andrei:
 *  - a Deck board links to ONE Solidtime project (a project optionally has a client),
 *  - a card does NOT map to a task by default; starting a timer on a card creates a time entry
 *    whose description is the card title,
 *  - tasks per card are an opt-in per-board setting.
 */
@Immutable
data class STOrganization(val id: String, val name: String, val currency: String?)

/** A membership ties the signed-in user to an organization; [memberId] is what time entries need. */
@Immutable
data class STMembership(
    val memberId: String,
    val organizationId: String,
    val organizationName: String,
    val role: String?,
)

@Immutable
data class STClient(val id: String, val name: String, val archived: Boolean)

@Immutable
data class STProject(
    val id: String,
    val name: String,
    /** Hex color without '#', e.g. "3BB8C8". May be blank. */
    val color: String,
    val clientId: String?,
    val isBillable: Boolean,
    val isArchived: Boolean,
    val isPublic: Boolean,
)

@Immutable
data class STTask(val id: String, val name: String, val projectId: String, val isDone: Boolean)

@Immutable
data class STTag(val id: String, val name: String)

/**
 * A Solidtime time entry. A running timer is one with [end] == null. Durations come straight
 * from the server in seconds; while running we compute elapsed time from [start].
 */
@Immutable
data class STTimeEntry(
    val id: String,
    /** Epoch millis. */
    val start: Long,
    /** Epoch millis, or null while the timer is running. */
    val end: Long?,
    val durationSeconds: Int,
    val description: String,
    val projectId: String?,
    val taskId: String?,
    val billable: Boolean,
    val tagIds: List<String>,
    val type: String,
) {
    val isRunning: Boolean get() = end == null
}
