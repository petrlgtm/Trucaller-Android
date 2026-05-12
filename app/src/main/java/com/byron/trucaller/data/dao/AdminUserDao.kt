package com.byron.trucaller.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.byron.trucaller.data.model.AdminUser
import kotlinx.coroutines.flow.Flow

@Dao
interface AdminUserDao {
    @Query("SELECT * FROM admin_users WHERE phoneNumber = :phoneNumber AND password = :password")
    suspend fun getByPhoneAndPassword(phoneNumber: String, password: String): AdminUser?

    @Query("SELECT * FROM admin_users WHERE email = :email AND password = :password")
    suspend fun getByEmailAndPassword(email: String, password: String): AdminUser?

    @Query("SELECT * FROM admin_users WHERE id = :id")
    suspend fun getById(id: String): AdminUser?

    @Query("SELECT * FROM admin_users")
    fun getAll(): Flow<List<AdminUser>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(adminUser: AdminUser)

    @Update
    suspend fun update(adminUser: AdminUser)
}
