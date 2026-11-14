package com.brbrs.tavla.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * A mutation that could not be sent to the server (device was offline) and should be replayed
 * when connectivity returns. Each row carries enough context to replay one API call.
 *
 * [actionType] is one of the constants in [PendingEditType]. [payloadJson] is the serialised
 * arguments for that particular call (board id, card id, title, etc.). The replay worker
 * processes rows oldest-first and deletes each one after a successful API call.
 */
@Entity(tableName = "pending_edits")
data class PendingEditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val actionType: String,
    val payloadJson: String,
    val createdAt: Long = System.currentTimeMillis(),
    /** Incremented on each failed attempt so we can give up after too many. */
    val attempts: Int = 0,
)

object PendingEditType {
    const val CREATE_BOARD = "create_board"
    const val UPDATE_BOARD = "update_board"
    const val DELETE_BOARD = "delete_board"
    const val CREATE_STACK = "create_stack"
    const val RENAME_STACK = "rename_stack"
    const val REORDER_STACK = "reorder_stack"
    const val DELETE_STACK = "delete_stack"
    const val CREATE_CARD = "create_card"
    const val UPDATE_CARD = "update_card"
    const val MOVE_CARD = "move_card"
    const val DELETE_CARD = "delete_card"
    const val SET_CARD_DONE = "set_card_done"
    const val SET_LABEL = "set_label"
    const val SET_ASSIGNEE = "set_assignee"
}

@Dao
interface PendingEditDao {
    @Query("SELECT * FROM pending_edits ORDER BY createdAt ASC")
    suspend fun getAll(): List<PendingEditEntity>

    @Query("SELECT COUNT(*) FROM pending_edits")
    suspend fun count(): Int

    @Insert
    suspend fun insert(edit: PendingEditEntity): Long

    @Query("DELETE FROM pending_edits WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE pending_edits SET attempts = attempts + 1 WHERE id = :id")
    suspend fun incrementAttempts(id: Long)

    @Query("DELETE FROM pending_edits WHERE attempts >= :maxAttempts")
    suspend fun deleteStale(maxAttempts: Int = 10)

    @Query("DELETE FROM pending_edits")
    suspend fun deleteAll()
}
