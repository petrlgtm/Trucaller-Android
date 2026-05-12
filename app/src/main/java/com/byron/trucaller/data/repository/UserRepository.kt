package com.byron.trucaller.data.repository

import com.byron.trucaller.data.dao.AdminUserDao
import com.byron.trucaller.data.dao.UserDao
import com.byron.trucaller.data.model.AdminUser
import com.byron.trucaller.data.model.User
import com.byron.trucaller.util.hashPassword
import kotlinx.coroutines.flow.Flow

class UserRepository(
    private val userDao: UserDao,
    private val adminUserDao: AdminUserDao
) {
    fun getAllUsers(): Flow<List<User>> = userDao.getAll()
    fun getUserCount(): Flow<Int> = userDao.countFlow()

    suspend fun getUserById(id: String): User? = userDao.getById(id)
    suspend fun getUserByPhone(phone: String): User? = userDao.getByPhone(phone)
    suspend fun insertUser(user: User) = userDao.insert(user)
    suspend fun updateUser(user: User) = userDao.update(user)
    suspend fun deleteUser(user: User) = userDao.delete(user)

    suspend fun deactivateUser(userId: String) = userDao.setActive(userId, false)
    suspend fun reactivateUser(userId: String) = userDao.setActive(userId, true)

    suspend fun getAdminByPhoneCredentials(phoneNumber: String, password: String): AdminUser? =
        adminUserDao.getByPhoneAndPassword(phoneNumber, hashPassword(password))

    suspend fun getAdminByCredentials(email: String, password: String): AdminUser? =
        adminUserDao.getByEmailAndPassword(email, hashPassword(password))
    suspend fun getAdminById(id: String): AdminUser? = adminUserDao.getById(id)
    fun getAllAdmins(): Flow<List<AdminUser>> = adminUserDao.getAll()
    suspend fun updateAdmin(admin: AdminUser) = adminUserDao.update(admin)
    suspend fun insertAdminUser(admin: AdminUser) = adminUserDao.insert(admin)
}
