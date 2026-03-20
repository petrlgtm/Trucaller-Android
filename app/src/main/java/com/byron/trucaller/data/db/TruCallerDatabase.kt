package com.byron.trucaller.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.byron.trucaller.data.dao.AdminUserDao
import com.byron.trucaller.data.dao.AlarmLogDao
import com.byron.trucaller.data.dao.BlockedNumberDao
import com.byron.trucaller.data.dao.BlockingScheduleDao
import com.byron.trucaller.data.dao.CallerIdDao
import com.byron.trucaller.data.dao.ContactDao
import com.byron.trucaller.data.dao.DeviceDao
import com.byron.trucaller.data.dao.IpLogDao
import com.byron.trucaller.data.dao.StolenReportDao
import com.byron.trucaller.data.dao.SmsSpamDao
import com.byron.trucaller.data.dao.ContactAliasDao
import com.byron.trucaller.data.dao.GeofenceDao
import com.byron.trucaller.data.dao.GeofenceEventDao
import com.byron.trucaller.data.dao.CallRecordingDao
import com.byron.trucaller.data.dao.FamilyGroupDao
import com.byron.trucaller.data.dao.FamilyMemberDao
import com.byron.trucaller.data.dao.SmsRuleDao
import com.byron.trucaller.data.dao.UserDao
import com.byron.trucaller.data.model.AdminUser
import com.byron.trucaller.data.model.AlarmLog
import com.byron.trucaller.data.model.BlockedNumber
import com.byron.trucaller.data.model.BlockingSchedule
import com.byron.trucaller.data.model.CallerIdEntry
import com.byron.trucaller.data.model.Contact
import com.byron.trucaller.data.model.Device
import com.byron.trucaller.data.model.Geofence
import com.byron.trucaller.data.model.GeofenceEvent
import com.byron.trucaller.data.model.IpLog
import com.byron.trucaller.data.model.CallRecording
import com.byron.trucaller.data.model.ContactAlias
import com.byron.trucaller.data.model.FamilyGroup
import com.byron.trucaller.data.model.FamilyMember
import com.byron.trucaller.data.model.SmsRule
import com.byron.trucaller.data.model.SmsSpamReport
import com.byron.trucaller.data.model.StolenReport
import com.byron.trucaller.data.model.User

@Database(
    entities = [
        User::class,
        Device::class,
        IpLog::class,
        Contact::class,
        CallerIdEntry::class,
        StolenReport::class,
        AlarmLog::class,
        AdminUser::class,
        BlockedNumber::class,
        SmsSpamReport::class,
        ContactAlias::class,
        Geofence::class,
        GeofenceEvent::class,
        SmsRule::class,
        CallRecording::class,
        BlockingSchedule::class,
        FamilyGroup::class,
        FamilyMember::class
    ],
    version = 14,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class TruCallerDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun deviceDao(): DeviceDao
    abstract fun ipLogDao(): IpLogDao
    abstract fun contactDao(): ContactDao
    abstract fun callerIdDao(): CallerIdDao
    abstract fun stolenReportDao(): StolenReportDao
    abstract fun alarmLogDao(): AlarmLogDao
    abstract fun adminUserDao(): AdminUserDao
    abstract fun blockedNumberDao(): BlockedNumberDao
    abstract fun smsSpamDao(): SmsSpamDao
    abstract fun contactAliasDao(): ContactAliasDao
    abstract fun geofenceDao(): GeofenceDao
    abstract fun geofenceEventDao(): GeofenceEventDao
    abstract fun callRecordingDao(): CallRecordingDao
    abstract fun smsRuleDao(): SmsRuleDao
    abstract fun blockingScheduleDao(): BlockingScheduleDao
    abstract fun familyGroupDao(): FamilyGroupDao
    abstract fun familyMemberDao(): FamilyMemberDao

    companion object {
        /** Migration 9 -> 10: add trustScore and trustLevel columns to users table. */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE users ADD COLUMN trustScore INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE users ADD COLUMN trustLevel TEXT NOT NULL DEFAULT 'NEW'")
            }
        }

        /** Migration 10 -> 11: create sms_rules table for user-customizable SMS classification rules. */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS sms_rules (
                        id TEXT NOT NULL PRIMARY KEY,
                        userId TEXT NOT NULL,
                        ruleType TEXT NOT NULL,
                        pattern TEXT NOT NULL,
                        targetCategory TEXT NOT NULL,
                        priority INTEGER NOT NULL DEFAULT 5,
                        isActive INTEGER NOT NULL DEFAULT 1,
                        createdAt TEXT NOT NULL
                    )"""
                )
            }
        }

        /** Migration 11 -> 12: create call_recordings table for local call recording storage. */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS call_recordings (
                        id TEXT NOT NULL PRIMARY KEY,
                        userId TEXT NOT NULL,
                        phoneNumber TEXT NOT NULL,
                        contactName TEXT,
                        callDirection TEXT NOT NULL,
                        startTime INTEGER NOT NULL,
                        duration INTEGER NOT NULL DEFAULT 0,
                        filePath TEXT NOT NULL,
                        fileSize INTEGER NOT NULL DEFAULT 0,
                        isStarred INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL DEFAULT 0
                    )"""
                )
            }
        }

        /** Migration 12 -> 13: create blocking_schedules table for scheduled call blocking. */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS blocking_schedules (
                        id TEXT NOT NULL PRIMARY KEY,
                        userId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        isActive INTEGER NOT NULL DEFAULT 1,
                        startTimeMinutes INTEGER NOT NULL,
                        endTimeMinutes INTEGER NOT NULL,
                        daysOfWeek INTEGER NOT NULL,
                        blockType TEXT NOT NULL,
                        createdAt TEXT NOT NULL
                    )"""
                )
            }
        }

        /** Migration 13 -> 14: create family_groups and family_members tables. */
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS family_groups (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        description TEXT NOT NULL DEFAULT '',
                        ownerId TEXT NOT NULL,
                        inviteCode TEXT NOT NULL,
                        memberCount INTEGER NOT NULL DEFAULT 1,
                        createdAt TEXT NOT NULL,
                        maxMembers INTEGER NOT NULL DEFAULT 10
                    )"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS family_members (
                        id TEXT NOT NULL PRIMARY KEY,
                        groupId TEXT NOT NULL,
                        userId TEXT NOT NULL,
                        displayName TEXT NOT NULL,
                        phoneNumber TEXT NOT NULL,
                        role TEXT NOT NULL DEFAULT 'MEMBER',
                        joinedAt TEXT NOT NULL
                    )"""
                )
            }
        }
    }
}
