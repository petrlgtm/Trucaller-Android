package com.byron.trucaller.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.byron.trucaller.data.model.ContactAlias
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactAliasDao {
    @Query("SELECT * FROM contact_aliases WHERE phoneNumber = :phone")
    fun getAliasesByPhone(phone: String): Flow<List<ContactAlias>>

    @Query("SELECT * FROM contact_aliases WHERE phoneNumber = :phone")
    suspend fun getAliasesByPhoneOnce(phone: String): List<ContactAlias>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alias: ContactAlias)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(aliases: List<ContactAlias>)

    @Query("DELETE FROM contact_aliases WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT DISTINCT phoneNumber FROM contact_aliases GROUP BY phoneNumber HAVING COUNT(*) >= :minCount")
    suspend fun getPhonesWithMultipleNames(minCount: Int = 2): List<String>
}
