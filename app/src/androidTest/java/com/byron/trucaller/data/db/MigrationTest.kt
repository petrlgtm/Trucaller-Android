package com.byron.trucaller.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.google.common.truth.Truth.assertThat

/**
 * Instrumented tests for Room database schema migrations.
 *
 * Uses [MigrationTestHelper] to validate that each migration correctly transforms
 * the database schema without data loss. Each test creates a database at the
 * starting version, inserts test data, runs the migration, and verifies the
 * schema and data in the target version.
 *
 * Requires an Android device or emulator to run.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val testDb = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TruCallerDatabase::class.java
    )

    // ── Migration 9 -> 10: add trustScore and trustLevel to users ────────

    @Test
    fun migrate9To10_addsTrustColumns() {
        // Create database at version 9
        val db = helper.createDatabase(testDb, 9)

        // Insert a user row at version 9 (before trust columns exist)
        db.execSQL(
            """INSERT INTO users (id, fullName, phoneNumber, passwordHash, createdAt)
               VALUES ('user-1', 'Test User', '+256700123456', 'hash123', '2026-01-01T00:00:00Z')"""
        )
        db.close()

        // Run migration 9 -> 10
        val migratedDb = helper.runMigrationsAndValidate(
            testDb, 10, true,
            TruCallerDatabase.MIGRATION_9_10
        )

        // Verify the new columns exist with default values
        val cursor = migratedDb.query("SELECT trustScore, trustLevel FROM users WHERE id = 'user-1'")
        assertThat(cursor.moveToFirst()).isTrue()
        assertThat(cursor.getInt(cursor.getColumnIndexOrThrow("trustScore"))).isEqualTo(0)
        assertThat(cursor.getString(cursor.getColumnIndexOrThrow("trustLevel"))).isEqualTo("NEW")
        cursor.close()
        migratedDb.close()
    }

    // ── Migration 10 -> 11: create sms_rules table ───────────────────────

    @Test
    fun migrate10To11_createsSmsRulesTable() {
        val db = helper.createDatabase(testDb, 10)
        db.close()

        val migratedDb = helper.runMigrationsAndValidate(
            testDb, 11, true,
            TruCallerDatabase.MIGRATION_10_11
        )

        // Verify the sms_rules table exists by inserting and querying
        migratedDb.execSQL(
            """INSERT INTO sms_rules (id, userId, ruleType, pattern, targetCategory, priority, isActive, createdAt)
               VALUES ('rule-1', 'user-1', 'KEYWORD', 'loan', 'SPAM', 5, 1, '2026-03-20T00:00:00Z')"""
        )

        val cursor = migratedDb.query("SELECT * FROM sms_rules WHERE id = 'rule-1'")
        assertThat(cursor.moveToFirst()).isTrue()
        assertThat(cursor.getString(cursor.getColumnIndexOrThrow("ruleType"))).isEqualTo("KEYWORD")
        assertThat(cursor.getString(cursor.getColumnIndexOrThrow("pattern"))).isEqualTo("loan")
        assertThat(cursor.getInt(cursor.getColumnIndexOrThrow("priority"))).isEqualTo(5)
        cursor.close()
        migratedDb.close()
    }

    // ── Migration 11 -> 12: create call_recordings table ─────────────────

    @Test
    fun migrate11To12_createsCallRecordingsTable() {
        val db = helper.createDatabase(testDb, 11)
        db.close()

        val migratedDb = helper.runMigrationsAndValidate(
            testDb, 12, true,
            TruCallerDatabase.MIGRATION_11_12
        )

        // Verify the call_recordings table exists
        migratedDb.execSQL(
            """INSERT INTO call_recordings (id, userId, phoneNumber, callDirection, startTime, duration, filePath, fileSize, isStarred, createdAt)
               VALUES ('rec-1', 'user-1', '+256700123456', 'INCOMING', 1710900000, 120, '/storage/rec1.mp3', 1024000, 0, 1710900000)"""
        )

        val cursor = migratedDb.query("SELECT * FROM call_recordings WHERE id = 'rec-1'")
        assertThat(cursor.moveToFirst()).isTrue()
        assertThat(cursor.getString(cursor.getColumnIndexOrThrow("phoneNumber"))).isEqualTo("+256700123456")
        assertThat(cursor.getString(cursor.getColumnIndexOrThrow("callDirection"))).isEqualTo("INCOMING")
        assertThat(cursor.getInt(cursor.getColumnIndexOrThrow("duration"))).isEqualTo(120)
        cursor.close()
        migratedDb.close()
    }

    // ── Migration 12 -> 13: create blocking_schedules table ──────────────

    @Test
    fun migrate12To13_createsBlockingSchedulesTable() {
        val db = helper.createDatabase(testDb, 12)
        db.close()

        val migratedDb = helper.runMigrationsAndValidate(
            testDb, 13, true,
            TruCallerDatabase.MIGRATION_12_13
        )

        // Verify the blocking_schedules table exists
        migratedDb.execSQL(
            """INSERT INTO blocking_schedules (id, userId, name, isActive, startTimeMinutes, endTimeMinutes, daysOfWeek, blockType, createdAt)
               VALUES ('sched-1', 'user-1', 'Night Block', 1, 1320, 360, 127, 'ALL_CALLS', '2026-03-20T00:00:00Z')"""
        )

        val cursor = migratedDb.query("SELECT * FROM blocking_schedules WHERE id = 'sched-1'")
        assertThat(cursor.moveToFirst()).isTrue()
        assertThat(cursor.getString(cursor.getColumnIndexOrThrow("name"))).isEqualTo("Night Block")
        assertThat(cursor.getInt(cursor.getColumnIndexOrThrow("startTimeMinutes"))).isEqualTo(1320)
        assertThat(cursor.getInt(cursor.getColumnIndexOrThrow("endTimeMinutes"))).isEqualTo(360)
        cursor.close()
        migratedDb.close()
    }

    // ── Migration 13 -> 14: create family_groups and family_members ──────

    @Test
    fun migrate13To14_createsFamilyTables() {
        val db = helper.createDatabase(testDb, 13)
        db.close()

        val migratedDb = helper.runMigrationsAndValidate(
            testDb, 14, true,
            TruCallerDatabase.MIGRATION_13_14
        )

        // Verify family_groups table
        migratedDb.execSQL(
            """INSERT INTO family_groups (id, name, description, ownerId, inviteCode, memberCount, createdAt, maxMembers)
               VALUES ('grp-1', 'My Family', 'Our family group', 'user-1', 'ABC123', 1, '2026-03-20T00:00:00Z', 10)"""
        )
        val groupCursor = migratedDb.query("SELECT * FROM family_groups WHERE id = 'grp-1'")
        assertThat(groupCursor.moveToFirst()).isTrue()
        assertThat(groupCursor.getString(groupCursor.getColumnIndexOrThrow("name"))).isEqualTo("My Family")
        assertThat(groupCursor.getString(groupCursor.getColumnIndexOrThrow("inviteCode"))).isEqualTo("ABC123")
        assertThat(groupCursor.getInt(groupCursor.getColumnIndexOrThrow("maxMembers"))).isEqualTo(10)
        groupCursor.close()

        // Verify family_members table
        migratedDb.execSQL(
            """INSERT INTO family_members (id, groupId, userId, displayName, phoneNumber, role, joinedAt)
               VALUES ('mem-1', 'grp-1', 'user-1', 'Dad', '+256700123456', 'ADMIN', '2026-03-20T00:00:00Z')"""
        )
        val memberCursor = migratedDb.query("SELECT * FROM family_members WHERE id = 'mem-1'")
        assertThat(memberCursor.moveToFirst()).isTrue()
        assertThat(memberCursor.getString(memberCursor.getColumnIndexOrThrow("displayName"))).isEqualTo("Dad")
        assertThat(memberCursor.getString(memberCursor.getColumnIndexOrThrow("role"))).isEqualTo("ADMIN")
        memberCursor.close()

        migratedDb.close()
    }

    // ── Full migration chain ─────────────────────────────────────────────

    @Test
    fun migrateAll_9To14_succeeds() {
        val db = helper.createDatabase(testDb, 9)
        db.close()

        // Run all migrations in sequence
        val migratedDb = helper.runMigrationsAndValidate(
            testDb, 14, true,
            TruCallerDatabase.MIGRATION_9_10,
            TruCallerDatabase.MIGRATION_10_11,
            TruCallerDatabase.MIGRATION_11_12,
            TruCallerDatabase.MIGRATION_12_13,
            TruCallerDatabase.MIGRATION_13_14
        )

        // Verify all new tables exist by querying table names
        val cursor = migratedDb.query(
            "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name"
        )
        val tables = mutableListOf<String>()
        while (cursor.moveToNext()) {
            tables.add(cursor.getString(0))
        }
        cursor.close()

        assertThat(tables).contains("sms_rules")
        assertThat(tables).contains("call_recordings")
        assertThat(tables).contains("blocking_schedules")
        assertThat(tables).contains("family_groups")
        assertThat(tables).contains("family_members")

        migratedDb.close()
    }
}
