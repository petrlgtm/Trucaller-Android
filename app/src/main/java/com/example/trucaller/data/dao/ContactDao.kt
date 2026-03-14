package com.example.trucaller.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.trucaller.data.model.Contact
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

    @Query("SELECT COUNT(*) FROM contacts WHERE userId = :userId")
    fun getCountByUser(userId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM contacts")
    fun getTotalCount(): Flow<Int>

    @Query("SELECT * FROM contacts WHERE phoneNumber = :phone LIMIT 1")
    suspend fun getByPhone(phone: String): Contact?

    @Query("SELECT * FROM contacts WHERE phoneNumber LIKE '%' || :query || '%' OR name LIKE '%' || :query || '%'")
    suspend fun search(query: String): List<Contact>

    @Query("SELECT * FROM contacts ORDER BY name ASC")
    fun getAllContacts(): Flow<List<Contact>>

    @Delete
    suspend fun delete(contact: Contact)

    @Query("SELECT * FROM contacts WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): Contact?
}
