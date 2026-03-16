package com.byron.trucaller.data.db

import com.byron.trucaller.data.mock.mockAdminUsers
import com.byron.trucaller.data.mock.mockAlarmLogs
import com.byron.trucaller.data.mock.mockCallerIdEntries
import com.byron.trucaller.data.mock.mockContacts
import com.byron.trucaller.data.mock.mockDevices
import com.byron.trucaller.data.mock.mockIpLogs
import com.byron.trucaller.data.mock.mockStolenReports
import com.byron.trucaller.data.mock.mockUsers

class DatabaseSeeder(private val db: TruCallerDatabase) {
    suspend fun seed() {
        val userDao = db.userDao()
        val deviceDao = db.deviceDao()
        val ipLogDao = db.ipLogDao()
        val contactDao = db.contactDao()
        val callerIdDao = db.callerIdDao()
        val stolenReportDao = db.stolenReportDao()
        val alarmLogDao = db.alarmLogDao()
        val adminUserDao = db.adminUserDao()

        mockUsers.forEach { userDao.insert(it) }
        mockDevices.forEach { deviceDao.insert(it) }
        ipLogDao.insertAll(mockIpLogs)
        contactDao.insertAll(mockContacts)
        callerIdDao.insertAll(mockCallerIdEntries)
        mockStolenReports.forEach { stolenReportDao.insert(it) }
        alarmLogDao.insertAll(mockAlarmLogs)
        mockAdminUsers.forEach { adminUserDao.insert(it) }
    }
}
