package com.brbrs.tavla.contactlinks.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Persistent store for card <-> Vinci-contact links, part of the suite's cross-app
 * integration (see cross-app-linking notes). Deliberately separate from the Deck cache
 * (tavla.db), which uses destructive migration -- these links have no other home, so
 * this database uses real migrations and is never wiped on a schema change.
 */
@Entity(tableName = "card_contact_link", primaryKeys = ["cardId", "contactUid"])
data class CardContactLinkEntity(
    val cardId: Long,
    /** Vinci's cardavUid -- the Nextcloud address book's CardDAV UID, stable across devices. */
    val contactUid: String,
)

@Dao
interface ContactLinksDao {
    @Query("SELECT contactUid FROM card_contact_link WHERE cardId = :cardId")
    fun observeForCard(cardId: Long): Flow<List<String>>

    @Query("SELECT contactUid FROM card_contact_link WHERE cardId = :cardId")
    suspend fun getForCard(cardId: Long): List<String>

    @Query("SELECT * FROM card_contact_link")
    suspend fun getAll(): List<CardContactLinkEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(link: CardContactLinkEntity)

    @Query("DELETE FROM card_contact_link WHERE cardId = :cardId AND contactUid = :contactUid")
    suspend fun delete(cardId: Long, contactUid: String)

    @Query("DELETE FROM card_contact_link WHERE cardId = :cardId")
    suspend fun deleteAllForCard(cardId: Long)
}

@Database(entities = [CardContactLinkEntity::class], version = 1)
abstract class ContactLinksDatabase : RoomDatabase() {
    abstract fun contactLinksDao(): ContactLinksDao
}
