package com.byron.trucaller.data.db

import com.byron.trucaller.data.model.AlarmResult
import com.byron.trucaller.data.model.AlarmType
import com.byron.trucaller.data.model.CallDirection
import com.byron.trucaller.data.model.DeviceStatus
import com.byron.trucaller.data.model.GeofenceTransitionType
import com.byron.trucaller.data.model.ReportStatus
import com.byron.trucaller.data.model.ScheduleBlockType
import com.byron.trucaller.data.model.SmsCategory
import com.byron.trucaller.data.model.SmsRuleType
import com.byron.trucaller.data.model.SpamCategory
import com.byron.trucaller.data.model.TrustLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    // ---- DeviceStatus roundtrip ----

    @Test
    fun `DeviceStatus roundtrip for all values`() {
        DeviceStatus.entries.forEach { status ->
            val serialized = converters.fromDeviceStatus(status)
            assertEquals(status.name, serialized)
            assertEquals(status, converters.toDeviceStatus(serialized))
        }
    }

    // ---- SpamCategory roundtrip ----

    @Test
    fun `SpamCategory roundtrip for all values`() {
        SpamCategory.entries.forEach { category ->
            val serialized = converters.fromSpamCategory(category)
            assertEquals(category.name, serialized)
            assertEquals(category, converters.toSpamCategory(serialized))
        }
    }

    // ---- ReportStatus roundtrip ----

    @Test
    fun `ReportStatus roundtrip for all values`() {
        ReportStatus.entries.forEach { status ->
            val serialized = converters.fromReportStatus(status)
            assertEquals(status.name, serialized)
            assertEquals(status, converters.toReportStatus(serialized))
        }
    }

    // ---- AlarmType roundtrip ----

    @Test
    fun `AlarmType roundtrip for all values`() {
        AlarmType.entries.forEach { type ->
            val serialized = converters.fromAlarmType(type)
            assertEquals(type.name, serialized)
            assertEquals(type, converters.toAlarmType(serialized))
        }
    }

    // ---- AlarmResult roundtrip ----

    @Test
    fun `AlarmResult roundtrip for all values`() {
        AlarmResult.entries.forEach { result ->
            val serialized = converters.fromAlarmResult(result)
            assertEquals(result.name, serialized)
            assertEquals(result, converters.toAlarmResult(serialized))
        }
    }

    // ---- GeofenceTransitionType roundtrip ----

    @Test
    fun `GeofenceTransitionType roundtrip for all values`() {
        GeofenceTransitionType.entries.forEach { type ->
            val serialized = converters.fromGeofenceTransitionType(type)
            assertEquals(type.name, serialized)
            assertEquals(type, converters.toGeofenceTransitionType(serialized))
        }
    }

    // ---- SmsRuleType roundtrip ----

    @Test
    fun `SmsRuleType roundtrip for all values`() {
        SmsRuleType.entries.forEach { type ->
            val serialized = converters.fromSmsRuleType(type)
            assertEquals(type.name, serialized)
            assertEquals(type, converters.toSmsRuleType(serialized))
        }
    }

    // ---- SmsCategory roundtrip ----

    @Test
    fun `SmsCategory roundtrip for all values`() {
        SmsCategory.entries.forEach { category ->
            val serialized = converters.fromSmsCategory(category)
            assertEquals(category.name, serialized)
            assertEquals(category, converters.toSmsCategory(serialized))
        }
    }

    // ---- CallDirection roundtrip ----

    @Test
    fun `CallDirection roundtrip for all values`() {
        CallDirection.entries.forEach { direction ->
            val serialized = converters.fromCallDirection(direction)
            assertEquals(direction.name, serialized)
            assertEquals(direction, converters.toCallDirection(serialized))
        }
    }

    // ---- TrustLevel roundtrip ----

    @Test
    fun `TrustLevel roundtrip for all values`() {
        TrustLevel.entries.forEach { level ->
            val serialized = converters.fromTrustLevel(level)
            assertEquals(level.name, serialized)
            assertEquals(level, converters.toTrustLevel(serialized))
        }
    }

    @Test
    fun `TrustLevel falls back to NEW for unknown value`() {
        assertEquals(TrustLevel.NEW, converters.toTrustLevel("UNKNOWN_VALUE"))
    }

    // ---- ScheduleBlockType roundtrip ----

    @Test
    fun `ScheduleBlockType roundtrip for all values`() {
        ScheduleBlockType.entries.forEach { type ->
            val serialized = converters.fromScheduleBlockType(type)
            assertEquals(type.name, serialized)
            assertEquals(type, converters.toScheduleBlockType(serialized))
        }
    }

    // ---- Specific value checks ----

    @Test
    fun `SpamCategory FRAUD serializes to FRAUD string`() {
        assertEquals("FRAUD", converters.fromSpamCategory(SpamCategory.FRAUD))
    }

    @Test
    fun `AlarmResult PENDING deserializes correctly`() {
        assertEquals(AlarmResult.PENDING, converters.toAlarmResult("PENDING"))
    }

    @Test
    fun `DeviceStatus STOLEN roundtrip`() {
        val serialized = converters.fromDeviceStatus(DeviceStatus.STOLEN)
        assertEquals("STOLEN", serialized)
        assertEquals(DeviceStatus.STOLEN, converters.toDeviceStatus(serialized))
    }

    @Test
    fun `toSpamCategory falls back to SAFE for invalid value`() {
        assertEquals(SpamCategory.SAFE, converters.toSpamCategory("INVALID"))
    }

    @Test
    fun `toAlarmResult falls back to PENDING for invalid value`() {
        assertEquals(AlarmResult.PENDING, converters.toAlarmResult("INVALID"))
    }

    @Test
    fun `toScheduleBlockType falls back to ALL_SPAM for invalid value`() {
        assertEquals(ScheduleBlockType.ALL_SPAM, converters.toScheduleBlockType("INVALID"))
    }
}
