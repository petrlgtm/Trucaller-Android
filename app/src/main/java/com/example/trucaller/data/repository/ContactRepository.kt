package com.example.trucaller.data.repository

import com.example.trucaller.data.dao.ContactDao
import com.example.trucaller.data.model.Contact
import kotlinx.coroutines.flow.Flow

class ContactRepository(private val contactDao: ContactDao) {
    fun getContactsByUser(userId: String): Flow<List<Contact>> = contactDao.getByUserId(userId)
    fun getAllContacts(): Flow<List<Contact>> = contactDao.getAllContacts()
    fun getContactCountByUser(userId: String): Flow<Int> = contactDao.getCountByUser(userId)
    fun getTotalContactCount(): Flow<Int> = contactDao.getTotalCount()

    suspend fun insertContact(contact: Contact) = contactDao.insert(contact)
    suspend fun insertContacts(contacts: List<Contact>) = contactDao.insertAll(contacts)
    suspend fun updateContact(contact: Contact) = contactDao.update(contact)
    suspend fun updateAllBackupStatus(userId: String, backed: Boolean) = contactDao.updateAllBackupStatus(userId, backed)
    suspend fun getContactByPhone(phone: String): Contact? = contactDao.getByPhone(phone)
    suspend fun searchContacts(query: String): List<Contact> = contactDao.search(query)
    suspend fun deleteContact(contact: Contact) = contactDao.delete(contact)
    suspend fun getContactById(id: String): Contact? = contactDao.getById(id)
}
