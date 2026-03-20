package com.byron.trucaller.di

import android.content.Context
import androidx.room.Room
import com.byron.trucaller.data.db.TruCallerDatabase
import com.byron.trucaller.data.db.DatabaseSeeder
import com.byron.trucaller.data.preferences.UserPreferences
import com.byron.trucaller.data.repository.AlarmRepository
import com.byron.trucaller.data.repository.BlockedNumberRepository
import com.byron.trucaller.data.repository.CallerIdRepository
import com.byron.trucaller.data.repository.ContactRepository
import com.byron.trucaller.data.repository.DeviceRepository
import com.byron.trucaller.data.repository.StolenReportRepository
import com.byron.trucaller.data.repository.SmsRepository
import com.byron.trucaller.data.repository.UserRepository

class AppContainer(context: Context) {
    val database: TruCallerDatabase = Room.databaseBuilder(
        context.applicationContext,
        TruCallerDatabase::class.java,
        "trucaller_database"
    ).fallbackToDestructiveMigration(true).build()

    val userPreferences = UserPreferences(context)

    val userRepository = UserRepository(database.userDao(), database.adminUserDao())
    val deviceRepository = DeviceRepository(database.deviceDao(), database.ipLogDao())
    val contactRepository = ContactRepository(database.contactDao())
    val callerIdRepository = CallerIdRepository(database.callerIdDao(), database.contactDao(), database.userDao())
    val stolenReportRepository = StolenReportRepository(database.stolenReportDao())
    val alarmRepository = AlarmRepository(database.alarmLogDao())
    val blockedNumberRepository = BlockedNumberRepository(database.blockedNumberDao())
    val smsRepository = SmsRepository(database.smsSpamDao(), callerIdRepository, blockedNumberRepository)
    val contactAliasDao = database.contactAliasDao()

    suspend fun seedDatabaseIfEmpty() {
        if (database.userDao().count() == 0) {
            DatabaseSeeder(database).seed()
        }
    }
}
