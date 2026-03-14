package com.example.trucaller.di

import android.content.Context
import androidx.room.Room
import com.example.trucaller.data.db.TruCallerDatabase
import com.example.trucaller.data.db.DatabaseSeeder
import com.example.trucaller.data.preferences.UserPreferences
import com.example.trucaller.data.repository.AlarmRepository
import com.example.trucaller.data.repository.BlockedNumberRepository
import com.example.trucaller.data.repository.CallerIdRepository
import com.example.trucaller.data.repository.ContactRepository
import com.example.trucaller.data.repository.DeviceRepository
import com.example.trucaller.data.repository.StolenReportRepository
import com.example.trucaller.data.repository.UserRepository

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

    suspend fun seedDatabaseIfEmpty() {
        if (database.userDao().count() == 0) {
            DatabaseSeeder(database).seed()
        }
    }
}
