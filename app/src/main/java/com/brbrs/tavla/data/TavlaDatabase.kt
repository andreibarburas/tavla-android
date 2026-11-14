package com.brbrs.tavla.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

// Labels, users and assignees are stored as the raw JSON arrays the Deck API returns.
// They're only ever read as a whole alongside their parent row, so separate tables would
// add joins without buying anything.

@Entity(tableName = "boards")
data class BoardEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val color: String,
    val archived: Boolean,
    val ownerUid: String,
    val labelsJson: String,
    val usersJson: String,
    val canEdit: Boolean,
    val canManage: Boolean,
    val isShared: Boolean,
    val lastModified: Long,
)

@Entity(tableName = "stacks")
data class StackEntity(
    @PrimaryKey val id: Long,
    val boardId: Long,
    val title: String,
    val sortOrder: Int,
)

@Entity(tableName = "cards")
data class CardEntity(
    @PrimaryKey val id: Long,
    val boardId: Long,
    val stackId: Long,
    val title: String,
    val description: String,
    val sortOrder: Int,
    val dueDate: Long?,
    val doneAt: Long?,
    val createdAt: Long?,
    val archived: Boolean,
    val ownerUid: String,
    val labelsJson: String,
    val assigneesJson: String,
    val commentsCount: Int,
    val attachmentCount: Int,
    val lastModified: Long,
)

data class DueCardRow(
    val id: Long,
    val boardId: Long,
    val stackId: Long,
    val title: String,
    val description: String,
    val sortOrder: Int,
    val dueDate: Long?,
    val doneAt: Long?,
    val createdAt: Long?,
    val archived: Boolean,
    val ownerUid: String,
    val labelsJson: String,
    val assigneesJson: String,
    val commentsCount: Int,
    val attachmentCount: Int,
    val lastModified: Long,
    val boardTitle: String,
    val boardColor: String,
)

data class BoardCardCount(val boardId: Long, val cardCount: Int, val dueCount: Int)

// No foreign keys on purpose: REPLACE on a parent with ON DELETE CASCADE would silently wipe
// its children. Refreshes replace a board's stacks/cards wholesale inside a transaction instead.
@Dao
abstract class DeckDao {

    // -- Boards
    @Query("SELECT * FROM boards ORDER BY archived ASC, title COLLATE NOCASE ASC")
    abstract fun observeBoards(): Flow<List<BoardEntity>>

    @Query("SELECT * FROM boards WHERE id = :id")
    abstract fun observeBoard(id: Long): Flow<BoardEntity?>

    @Query("SELECT * FROM boards WHERE id = :id")
    abstract suspend fun getBoard(id: Long): BoardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertBoards(boards: List<BoardEntity>)

    @Query("DELETE FROM boards WHERE id NOT IN (:keepIds)")
    abstract suspend fun deleteBoardsNotIn(keepIds: List<Long>)

    @Query("DELETE FROM boards")
    abstract suspend fun deleteAllBoards()

    @Query("DELETE FROM boards WHERE id = :id")
    abstract suspend fun deleteBoard(id: Long)

    @Transaction
    open suspend fun replaceBoards(boards: List<BoardEntity>) {
        if (boards.isEmpty()) {
            deleteAllBoards()
            deleteAllStacks()
            deleteAllCards()
        } else {
            val ids = boards.map { it.id }
            deleteBoardsNotIn(ids)
            deleteStacksForBoardsNotIn(ids)
            deleteCardsForBoardsNotIn(ids)
            upsertBoards(boards)
        }
    }

    // -- Stacks
    @Query("SELECT * FROM stacks WHERE boardId = :boardId ORDER BY sortOrder ASC, id ASC")
    abstract fun observeStacks(boardId: Long): Flow<List<StackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertStacks(stacks: List<StackEntity>)

    @Query("DELETE FROM stacks WHERE boardId = :boardId")
    abstract suspend fun deleteStacksForBoard(boardId: Long)

    @Query("DELETE FROM stacks WHERE boardId NOT IN (:keepBoardIds)")
    abstract suspend fun deleteStacksForBoardsNotIn(keepBoardIds: List<Long>)

    @Query("DELETE FROM stacks")
    abstract suspend fun deleteAllStacks()

    @Query("DELETE FROM stacks WHERE id = :id")
    abstract suspend fun deleteStack(id: Long)

    // -- Cards
    // Includes archived cards -- BoardViewModel filters them in/out per CardFilter (All excludes
    // them, the Archived filter is the only one that shows them) so they can be browsed and
    // restored instead of being invisible once archived.
    @Query("SELECT * FROM cards WHERE boardId = :boardId ORDER BY sortOrder ASC, id ASC")
    abstract fun observeCardsForBoard(boardId: Long): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE id = :id")
    abstract fun observeCard(id: Long): Flow<CardEntity?>

    @Query("SELECT * FROM cards WHERE id = :id")
    abstract suspend fun getCard(id: Long): CardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertCards(cards: List<CardEntity>)

    @Query("DELETE FROM cards WHERE boardId = :boardId")
    abstract suspend fun deleteCardsForBoard(boardId: Long)

    @Query("DELETE FROM cards WHERE boardId NOT IN (:keepBoardIds)")
    abstract suspend fun deleteCardsForBoardsNotIn(keepBoardIds: List<Long>)

    @Query("DELETE FROM cards")
    abstract suspend fun deleteAllCards()

    @Query("DELETE FROM cards WHERE id = :id")
    abstract suspend fun deleteCard(id: Long)

    @Query("DELETE FROM cards WHERE stackId = :stackId")
    abstract suspend fun deleteCardsForStack(stackId: Long)

    @Transaction
    open suspend fun replaceBoardContent(boardId: Long, stacks: List<StackEntity>, cards: List<CardEntity>) {
        deleteStacksForBoard(boardId)
        deleteCardsForBoard(boardId)
        upsertStacks(stacks)
        upsertCards(cards)
    }

    @Query(
        """
        SELECT c.*, b.title AS boardTitle, b.color AS boardColor
        FROM cards c INNER JOIN boards b ON b.id = c.boardId
        WHERE c.archived = 0 AND b.archived = 0 AND c.doneAt IS NULL
              AND c.dueDate IS NOT NULL AND c.dueDate <= :until
        ORDER BY c.dueDate ASC
        LIMIT 20
        """
    )
    abstract fun observeDueCards(until: Long): Flow<List<DueCardRow>>

    // #01: every open card across every board, for the "search everything" box on the boards
    // screen. No due-date requirement (unlike observeDueCards) and no row cap, since a search can
    // legitimately match a lot of cards; the UI itself limits how many it renders.
    @Query(
        """
        SELECT c.*, b.title AS boardTitle, b.color AS boardColor
        FROM cards c INNER JOIN boards b ON b.id = c.boardId
        WHERE c.archived = 0 AND b.archived = 0
        ORDER BY c.lastModified DESC
        """
    )
    abstract fun observeAllOpenCards(): Flow<List<DueCardRow>>

    @Query(
        """
        SELECT boardId,
               COUNT(*) AS cardCount,
               SUM(CASE WHEN doneAt IS NULL AND dueDate IS NOT NULL AND dueDate <= :until THEN 1 ELSE 0 END) AS dueCount
        FROM cards WHERE archived = 0 GROUP BY boardId
        """
    )
    abstract fun observeCardCounts(until: Long): Flow<List<BoardCardCount>>

    /** Replace boards AND their stacks/cards in one transaction, so Room observers see a
     *  single consistent update (board list + card counts change together, no flash). */
    @Transaction
    open suspend fun replaceAllContent(
        boards: List<BoardEntity>,
        boardContents: Map<Long, Pair<List<StackEntity>, List<CardEntity>>>,
    ) {
        // Update boards first
        if (boards.isEmpty()) {
            deleteAllBoards(); deleteAllStacks(); deleteAllCards()
        } else {
            val ids = boards.map { it.id }
            deleteBoardsNotIn(ids)
            deleteStacksForBoardsNotIn(ids)
            deleteCardsForBoardsNotIn(ids)
            upsertBoards(boards)
        }
        // Then replace stacks/cards for each fetched board
        for ((boardId, content) in boardContents) {
            deleteStacksForBoard(boardId)
            deleteCardsForBoard(boardId)
            upsertStacks(content.first)
            upsertCards(content.second)
        }
    }

    @Transaction
    open suspend fun clearAll() {
        deleteAllBoards()
        deleteAllStacks()
        deleteAllCards()
    }

    // -- Bulk queries for local backup export --

    @Query("SELECT * FROM boards ORDER BY id ASC")
    abstract suspend fun getAllBoardsOnce(): List<BoardEntity>

    @Query("SELECT * FROM stacks ORDER BY id ASC")
    abstract suspend fun getAllStacksOnce(): List<StackEntity>

    @Query("SELECT * FROM cards ORDER BY id ASC")
    abstract suspend fun getAllCardsOnce(): List<CardEntity>
}

// Bumped to 4 to add PendingEditEntity for the offline edit queue. This db is a pure cache of
// Deck data (see AppModule's fallbackToDestructiveMigration), so a version bump just
// redownloads it. Pending edits are lost too, but that's fine: they only matter while offline
// and the queue is typically empty after a successful sync.
@Database(
    entities = [BoardEntity::class, StackEntity::class, CardEntity::class, PendingEditEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class TavlaDatabase : RoomDatabase() {
    abstract fun deckDao(): DeckDao
    abstract fun pendingEditDao(): PendingEditDao
}
