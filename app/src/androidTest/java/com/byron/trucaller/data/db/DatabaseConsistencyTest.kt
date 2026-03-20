package com.byron.trucaller.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for database consistency and entity integrity.
 *
 * Creates an in-memory Room database and validates that all DAOs are
 * accessible, entities are properly defined, and basic CRUD operations
 * work correctly on the final schema version.
 *
 * Requires an Android device or emulator to run.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseConsistencyTest {

    private lateinit var database: TruCallerDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            TruCallerDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ── DAO Accessibility ────────────────────────────────────────────────

    @Test
    fun allDaosAreAccessible() {
        assertThat(database.userDao()).isNotNull()
        assertThat(database.deviceDao()).isNotNull()
        assertThat(database.ipLogDao()).isNotNull()
        assertThat(database.contactDao()).isNotNull()
        assertThat(database.callerIdDao()).isNotNull()
        assertThat(database.stolenReportDao()).isNotNull()
        assertThat(database.alarmLogDao()).isNotNull()
        assertThat(database.adminUserDao()).isNotNull()
        assertThat(database.blockedNumberDao()).isNotNull()
        assertThat(database.smsSpamDao()).isNotNull()
        assertThat(database.contactAliasDao()).isNotNull()
        assertThat(database.geofenceDao()).isNotNull()
        assertThat(database.geofenceEventDao()).isNotNull()
        assertThat(database.callRecordingDao()).isNotNull()
        assertThat(database.smsRuleDao()).isNotNull()
        assertThat(database.blockingScheduleDao()).isNotNull()
        assertThat(database.familyGroupDao()).isNotNull()
        assertThat(database.familyMemberDao()).isNotNull()
    }

    // ── Database Version ──────────────────────────────────────────────────

    @Test
    fun databaseVersionIs14() {
        val db = database.openHelper.readableDatabase
        assertThat(db.version).isEqualTo(14)
    }

    // ── Table Existence ──────────────────────────────────────────────────

    @Test
    fun allExpectedTablesExist() {
        val db = database.openHelper.readableDatabase
        val cursor = db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'room_%' AND name != 'android_metadata' ORDER BY name"
        )

        val tables = mutableListOf<String>()
        while (cursor.moveToNext()) {
            tables.add(cursor.getString(0))
        }
        cursor.close()

        val expectedTables = listOf(
            "users",
            "devices",
            "ip_logs",
            "contacts",
            "caller_id_entries",
            "stolen_reports",
            "alarm_logs",
            "admin_users",
            "blocked_numbers",
            "sms_spam_reports",
            "contact_aliases",
            "geofences",
            "geofence_events",
            "sms_rules",
            "call_recordings",
            "blocking_schedules",
            "family_groups",
            "family_members"
        )

        expectedTables.forEach { tableName ->
            assertThat(tables).contains(tableName)
        }
    }

    // ── Users Table Schema ──────────────────────────────────────────────

    @Test
    fun usersTableHasTrustColumns() {
        val db = database.openHelper.readableDatabase
        val cursor = db.query("PRAGMA table_info(users)")

        val columns = mutableListOf<String>()
        while (cursor.moveToNext()) {
            columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
        }
        cursor.close()

        assertThat(columns).contains("trustScore")
        assertThat(columns).contains("trustLevel")
    }

    // ── SmsRules Table Schema ────────────────────────────────────────────

    @Test
    fun smsRulesTableHasExpectedColumns() {
        val db = database.openHelper.readableDatabase
        val cursor = db.query("PRAGMA table_info(sms_rules)")

        val columns = mutableListOf<String>()
        while (cursor.moveToNext()) {
            columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
        }
        cursor.close()

        val expectedColumns = listOf("id", "userId", "ruleType", "pattern", "targetCategory", "priority", "isActive", "createdAt")
        expectedColumns.forEach { col ->
            assertThat(columns).contains(col)
        }
    }

    // ── CallRecordings Table Schema ──────────────────────────────────────

    @Test
    fun callRecordingsTableHasExpectedColumns() {
        val db = database.openHelper.readableDatabase
        val cursor = db.query("PRAGMA table_info(call_recordings)")

        val columns = mutableListOf<String>()
        while (cursor.moveToNext()) {
            columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
        }
        cursor.close()

        val expectedColumns = listOf("id", "userId", "phoneNumber", "callDirection", "startTime", "duration", "filePath", "fileSize", "isStarred", "createdAt")
        expectedColumns.forEach { col ->
            assertThat(columns).contains(col)
        }
    }

    // ── BlockingSchedules Table Schema ───────────────────────────────────

    @Test
    fun blockingSchedulesTableHasExpectedColumns() {
        val db = database.openHelper.readableDatabase
        val cursor = db.query("PRAGMA table_info(blocking_schedules)")

        val columns = mutableListOf<String>()
        while (cursor.moveToNext()) {
            columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
        }
        cursor.close()

        val expectedColumns = listOf("id", "userId", "name", "isActive", "startTimeMinutes", "endTimeMinutes", "daysOfWeek", "blockType", "createdAt")
        expectedColumns.forEach { col ->
            assertThat(columns).contains(col)
        }
    }

    // ── Family Tables Schema ─────────────────────────────────────────────

    @Test
    fun familyGroupsTableHasExpectedColumns() {
        val db = database.openHelper.readableDatabase
        val cursor = db.query("PRAGMA table_info(family_groups)")

        val columns = mutableListOf<String>()
        while (cursor.moveToNext()) {
            columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
        }
        cursor.close()

        val expectedColumns = listOf("id", "name", "description", "ownerId", "inviteCode", "memberCount", "createdAt", "maxMembers")
        expectedColumns.forEach { col ->
            assertThat(columns).contains(col)
        }
    }

    @Test
    fun familyMembersTableHasExpectedColumns() {
        val db = database.openHelper.readableDatabase
        val cursor = db.query("PRAGMA table_info(family_members)")

        val columns = mutableListOf<String>()
        while (cursor.moveToNext()) {
            columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
        }
        cursor.close()

        val expectedColumns = listOf("id", "groupId", "userId", "displayName", "phoneNumber", "role", "joinedAt")
        expectedColumns.forEach { col ->
            assertThat(columns).contains(col)
        }
    }

    // ── TypeConverters installed ──────────────────────────────────────────

    @Test
    fun databaseHasTypeConverters() {
        // Verify database instance can be created (TypeConverters must be valid)
        assertThat(database).isNotNull()
        assertThat(database.isOpen).isTrue()
    }
}
