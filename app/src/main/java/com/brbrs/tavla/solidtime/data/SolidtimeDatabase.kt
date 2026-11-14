package com.brbrs.tavla.solidtime.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Persistent store for the Solidtime <-> Deck links. This is deliberately a SEPARATE database from
 * the Deck cache (tavla.db), which uses destructive migration because it only mirrors server data.
 * These links have no other home, so this database uses real migrations and is never wiped on a
 * schema change -- losing them would orphan a card's tracked time.
 */

/** A board linked to one Solidtime project, plus that board's per-board timer options. */
@Entity(tableName = "board_link")
data class BoardLinkEntity(
    @PrimaryKey val boardId: Long,
    val projectId: String,
    val projectName: String,
    val projectColor: String,
    val clientName: String?,
    /** The project's default billable flag at link time; new entries default to this. */
    val projectBillable: Boolean,
    /** Opt-in: also create a Solidtime task per card and attach entries to it. */
    val createTasks: Boolean,
    /** Opt-in: tag each entry with the card's list (stack) name. */
    val useListTag: Boolean,
    /** Optional: moving a card into this stack auto-stops its running timer. */
    val doneStackId: Long?,
)

/** Maps a Solidtime time entry back to the Deck card it was tracked on. */
@Entity(tableName = "card_entry")
data class CardEntryEntity(
    @PrimaryKey val entryId: String,
    val cardId: Long,
    val boardId: Long,
)

/** Remembers the Solidtime task created for a card, so it's reused rather than duplicated. */
@Entity(tableName = "card_task")
data class CardTaskEntity(
    @PrimaryKey val cardId: Long,
    val taskId: String,
)

/**
 * Per-card override: routes this card's time entries to a different Solidtime project than its
 * board's default. When present, [startTimer]/[cardTime]/[addManualEntry] use this instead of
 * the board link's projectId.
 */
@Entity(tableName = "card_project")
data class CardProjectEntity(
    @PrimaryKey val cardId: Long,
    val projectId: String,
    val projectName: String,
    val projectColor: String,
    val clientName: String?,
    val projectBillable: Boolean,
)

/** A start/stop that failed while offline, to be replayed by the sync worker. */
@Entity(tableName = "pending_op")
data class PendingOpEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val opJson: String,
    val createdAt: Long,
)

@Dao
interface SolidtimeDao {

    // -- Board links
    @Query("SELECT * FROM board_link WHERE boardId = :boardId")
    fun observeBoardLink(boardId: Long): Flow<BoardLinkEntity?>

    @Query("SELECT * FROM board_link WHERE boardId = :boardId")
    suspend fun getBoardLink(boardId: Long): BoardLinkEntity?

    @Query("SELECT * FROM board_link")
    fun observeAllBoardLinks(): Flow<List<BoardLinkEntity>>

    @Query("SELECT * FROM board_link")
    suspend fun getAllBoardLinks(): List<BoardLinkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBoardLink(link: BoardLinkEntity)

    @Query("DELETE FROM board_link WHERE boardId = :boardId")
    suspend fun deleteBoardLink(boardId: Long)

    // -- Card project overrides
    @Query("SELECT * FROM card_project WHERE cardId = :cardId")
    fun observeCardProject(cardId: Long): Flow<CardProjectEntity?>

    @Query("SELECT * FROM card_project WHERE cardId = :cardId")
    suspend fun getCardProject(cardId: Long): CardProjectEntity?

    @Query("SELECT * FROM card_project")
    suspend fun getAllCardProjects(): List<CardProjectEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCardProject(entity: CardProjectEntity)

    @Query("DELETE FROM card_project WHERE cardId = :cardId")
    suspend fun deleteCardProject(cardId: Long)

    @Query("DELETE FROM card_project")
    suspend fun clearCardProjects()

    // -- Card <-> entry
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCardEntry(row: CardEntryEntity)

    @Query("SELECT entryId FROM card_entry WHERE cardId = :cardId")
    suspend fun entryIdsForCard(cardId: Long): List<String>

    @Query("SELECT cardId FROM card_entry WHERE entryId = :entryId")
    suspend fun cardForEntry(entryId: String): Long?

    @Query("SELECT * FROM card_entry WHERE entryId = :entryId")
    suspend fun getCardEntry(entryId: String): CardEntryEntity?

    @Query("DELETE FROM card_entry WHERE entryId = :entryId")
    suspend fun deleteCardEntry(entryId: String)

    @Query("SELECT * FROM card_entry")
    suspend fun getAllCardEntries(): List<CardEntryEntity>

    // -- Card <-> task
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCardTask(row: CardTaskEntity)

    @Query("SELECT taskId FROM card_task WHERE cardId = :cardId")
    suspend fun taskForCard(cardId: Long): String?

    @Query("SELECT * FROM card_task")
    suspend fun getAllCardTasks(): List<CardTaskEntity>

    // -- Pending ops (offline queue)
    @Insert
    suspend fun insertPendingOp(op: PendingOpEntity): Long

    @Query("SELECT * FROM pending_op ORDER BY createdAt ASC")
    suspend fun pendingOps(): List<PendingOpEntity>

    @Query("SELECT COUNT(*) FROM pending_op")
    fun observePendingCount(): Flow<Int>

    @Query("DELETE FROM pending_op WHERE id = :id")
    suspend fun deletePendingOp(id: Long)

    // -- Wipe everything (on disconnect)
    @Query("DELETE FROM board_link")
    suspend fun clearBoardLinks()

    @Query("DELETE FROM card_entry")
    suspend fun clearCardEntries()

    @Query("DELETE FROM card_task")
    suspend fun clearCardTasks()

    @Query("DELETE FROM pending_op")
    suspend fun clearPendingOps()
}

@Database(
    entities = [BoardLinkEntity::class, CardEntryEntity::class, CardTaskEntity::class, PendingOpEntity::class, CardProjectEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class SolidtimeDatabase : RoomDatabase() {
    abstract fun solidtimeDao(): SolidtimeDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `card_project` (
                        `cardId` INTEGER NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `projectName` TEXT NOT NULL,
                        `projectColor` TEXT NOT NULL,
                        `clientName` TEXT,
                        `projectBillable` INTEGER NOT NULL,
                        PRIMARY KEY(`cardId`)
                    )"""
                )
            }
        }
    }
}
