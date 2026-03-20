package com.byron.trucaller.di

import android.content.Context
import androidx.room.Room
import com.byron.trucaller.data.db.TruCallerDatabase
import com.byron.trucaller.data.db.DatabaseSeeder
import com.byron.trucaller.data.preferences.UserPreferences
import com.byron.trucaller.data.repository.AlarmRepository
import com.byron.trucaller.data.repository.BlockedNumberRepository
import com.byron.trucaller.data.repository.BlockingScheduleRepository
import com.byron.trucaller.data.repository.CallerIdRepository
import com.byron.trucaller.data.repository.CallRecordingRepository
import com.byron.trucaller.data.repository.ContactRepository
import com.byron.trucaller.data.repository.DeviceRepository
import com.byron.trucaller.data.repository.GeofenceRepository
import com.byron.trucaller.data.repository.StolenReportRepository
import com.byron.trucaller.data.repository.SmsRepository
import com.byron.trucaller.data.repository.SmsRuleRepository
import com.byron.trucaller.data.repository.UserRepository

class AppContainer(context: Context) {
    val database: TruCallerDatabase = Room.databaseBuilder(
        context.applicationContext,
        TruCallerDatabase::class.java,
        "trucaller_database"
    )
        .addMigrations(TruCallerDatabase.MIGRATION_9_10, TruCallerDatabase.MIGRATION_10_11, TruCallerDatabase.MIGRATION_11_12, TruCallerDatabase.MIGRATION_12_13)
        .fallbackToDestructiveMigration(true)
        .build()

    val userPreferences = UserPreferences(context)

    val userRepository = UserRepository(database.userDao(), database.adminUserDao())
    val deviceRepository = DeviceRepository(database.deviceDao(), database.ipLogDao())
    val contactRepository = ContactRepository(database.contactDao())
    val contactAliasDao = database.contactAliasDao()
    val callerIdRepository = CallerIdRepository(database.callerIdDao(), database.contactDao(), database.userDao(), contactAliasDao)
    val stolenReportRepository = StolenReportRepository(database.stolenReportDao())
    val alarmRepository = AlarmRepository(database.alarmLogDao())
    val blockedNumberRepository = BlockedNumberRepository(database.blockedNumberDao())
    val geofenceRepository = GeofenceRepository(database.geofenceDao(), database.geofenceEventDao())
    val smsRuleRepository = SmsRuleRepository(database.smsRuleDao())
    val callRecordingRepository = CallRecordingRepository(database.callRecordingDao())
    val blockingScheduleRepository = BlockingScheduleRepository(database.blockingScheduleDao())
    val smsRepository = SmsRepository(database.smsSpamDao(), callerIdRepository, blockedNumberRepository, smsRuleRepository)

    suspend fun seedDatabaseIfEmpty() {
        if (database.userDao().count() == 0) {
            DatabaseSeeder(database).seed()
        }
    }
}
