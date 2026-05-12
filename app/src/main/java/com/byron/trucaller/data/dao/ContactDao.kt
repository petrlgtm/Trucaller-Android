package com.byron.trucaller.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.byron.trucaller.data.model.Contact
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts WHERE userId = :userId ORDER BY name ASC")
    fun getByUserId(userId: String): Flow<List<Contact>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contact: Contact)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(contacts: List<Contact>)

    @Update
    suspend fun update(contact: Contact)

    @Query("UPDATE contacts SET isBackedUp = :backed WHERE userId = :userId")
    suspend fun updateAllBackupStatus(userId: String, backed: Boolean)

    @Query("UPDATE contacts SET syncedAt = :syncedAt, isBackedUp = 1 WHERE userId = :userId")
    suspend fun markAllSynced(userId: String, syncedAt: String)

    @Query("SELECT COUNT(*) FROM contacts WHERE userId = :userId")
    fun getCountByUser(userId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM contacts")
    fun getTotalCount(): Flow<Int>

    @Query("SELECT * FROM contacts WHERE phoneNumber = :phone LIMIT 1")
    suspend fun getByPhone(phone: String): Contact?

    // Privacy-safe variant: only returns a contact if it belongs to the requesting user.
    @Query("SELECT * FROM contacts WHERE phoneNumber = :phone AND userId = :userId LIMIT 1")
    suspend fun getByPhoneAndUser(phone: String, userId: String): Contact?

    @Query("SELECT * FROM contacts WHERE phoneNumber LIKE '%' || :query || '%' OR name LIKE '%' || :query || '%' LIMIT 500")
    suspend fun search(query: String): List<Contact>

    @Query("SELECT * FROM contacts ORDER BY name ASC LIMIT 500")
    fun getAllContacts(): Flow<List<Contact>>

    @Delete
    suspend fun delete(contact: Contact)

    @Query("SELECT * FROM contacts WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): Contact?

    @Query("SELECT * FROM contacts WHERE userId = :userId AND isFavourite = 1 ORDER BY name ASC")
    fun getFavouritesByUser(userId: String): Flow<List<Contact>>

    @Query("SELECT * FROM contacts WHERE userId = :userId AND isFavourite = 1 AND favouriteSegment = :segment ORDER BY name ASC")
    fun getFavouritesBySegment(userId: String, segment: String): Flow<List<Contact>>

    @Query("SELECT DISTINCT favouriteSegment FROM contacts WHERE userId = :userId AND isFavourite = 1 AND favouriteSegment IS NOT NULL")
    fun getSegmentsByUser(userId: String): Flow<List<String>>

    @Query("UPDATE contacts SET isFavourite = :isFavourite, favouriteSegment = :segment WHERE id = :id")
    suspend fun updateFavourite(id: String, isFavourite: Boolean, segment: String?)

    @Query("UPDATE contacts SET note = :note WHERE id = :id")
    suspend fun updateNote(id: String, note: String?)

    @Query("UPDATE contacts SET photoUri = :photoUri WHERE id = :id")
    suspend fun updatePhoto(id: String, photoUri: String?)
}
