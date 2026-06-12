package com.byron.trucaller.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.byron.trucaller.data.dao.*
import com.byron.trucaller.data.model.*

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
    version = 19,
    exportSchema = true
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

        /** Migration 14 -> 15: add startTime and lastSeen columns to ip_logs for location-session tracking. */
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ip_logs ADD COLUMN startTime TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE ip_logs ADD COLUMN lastSeen TEXT NOT NULL DEFAULT ''")
                // Backfill: set startTime and lastSeen to existing timestamp
                db.execSQL("UPDATE ip_logs SET startTime = timestamp, lastSeen = timestamp WHERE startTime = ''")
            }
        }

        /** Migration 15 -> 16: add nearbyDevices column to ip_logs. */
        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ip_logs ADD COLUMN nearbyDevices TEXT NOT NULL DEFAULT '[]'")
            }
        }

        /** Migration 16 -> 17: add phoneNumber column to admin_users for phone-based admin login. */
        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE admin_users ADD COLUMN phoneNumber TEXT NOT NULL DEFAULT ''")
            }
        }

        /** Migration 17 -> 18: add userId column to contact_aliases for user-scoped alias privacy. */
        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE contact_aliases ADD COLUMN userId TEXT")
            }
        }

        /** Migration 18 -> 19: flag call recordings whose audio file is encrypted at rest. */
        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE call_recordings ADD COLUMN encrypted INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
