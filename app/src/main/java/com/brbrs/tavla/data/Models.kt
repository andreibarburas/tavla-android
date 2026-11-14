package com.brbrs.tavla.data

import androidx.compose.runtime.Immutable

@Immutable
data class DeckLabel(val id: Long, val title: String, val color: String)

@Immutable
data class DeckUser(val uid: String, val displayName: String)

@Immutable
data class Board(
    val id: Long,
    val title: String,
    val color: String,
    val archived: Boolean,
    val ownerUid: String,
    val labels: List<DeckLabel>,
    val users: List<DeckUser>,
    val canEdit: Boolean,
    val canManage: Boolean,
    val isShared: Boolean,
)

@Immutable
data class Stack(val id: Long, val boardId: Long, val title: String, val order: Int)

@Immutable
data class Card(
    val id: Long,
    val boardId: Long,
    val stackId: Long,
    val title: String,
    val description: String,
    val order: Int,
    /** Epoch millis, or null when no due date is set. */
    val dueDate: Long?,
    /** Epoch millis the card was marked done, or null. Newer Deck versions only (#09); harmless elsewhere. */
    val doneAt: Long?,
    /** Epoch millis the card was created, or null if Deck didn't report one. Used by board insights. */
    val createdAt: Long?,
    val archived: Boolean,
    val ownerUid: String,
    val labels: List<DeckLabel>,
    val assignees: List<DeckUser>,
    val commentsCount: Int,
    val attachmentCount: Int,
)

@Immutable
data class DeckComment(
    val id: Long,
    val message: String,
    val actorId: String,
    val actorDisplayName: String,
    /** Epoch millis. */
    val createdAt: Long,
)

/** A card plus the names of its board and stack, for cross-board lists like "Due soon". */
@Immutable
data class CardWithContext(val card: Card, val boardTitle: String, val boardColor: String)

/** A Deck file attachment (#08). [extension] drives the icon shown before it's downloaded. */
@Immutable
data class Attachment(
    val id: Long,
    val fileName: String,
    val extension: String,
    val sizeBytes: Long,
    val createdAt: Long,
)
