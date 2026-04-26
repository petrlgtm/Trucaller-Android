package com.byron.trucaller.data.db

import androidx.room.TypeConverter
import com.byron.trucaller.data.model.AlarmResult
import com.byron.trucaller.data.model.AlarmType
import com.byron.trucaller.data.model.CallDirection
import com.byron.trucaller.data.model.DeviceStatus
import com.byron.trucaller.data.model.GeofenceTransitionType
import com.byron.trucaller.data.model.ReportStatus
import com.byron.trucaller.data.model.ScheduleBlockType
import com.byron.trucaller.data.model.SpamCategory
import com.byron.trucaller.data.model.SmsCategory
import com.byron.trucaller.data.model.SmsRuleType
import com.byron.trucaller.data.model.TrustLevel
import com.byron.trucaller.data.model.NearbyDevice
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromNearbyDeviceList(list: List<NearbyDevice>): String {
        return gson.toJson(list)
    }

    @TypeConverter
    fun toNearbyDeviceList(value: String): List<NearbyDevice> {
        val type = object : TypeToken<List<NearbyDevice>>() {}.type
        return gson.fromJson(value, type) ?: emptyList()
    }
    @TypeConverter
    fun fromDeviceStatus(status: DeviceStatus): String = status.name
    @TypeConverter
    fun toDeviceStatus(value: String): DeviceStatus = try {
        DeviceStatus.valueOf(value)
    } catch (_: IllegalArgumentException) { DeviceStatus.ACTIVE }

    @TypeConverter
    fun fromSpamCategory(category: SpamCategory): String = category.name
    @TypeConverter
    fun toSpamCategory(value: String): SpamCategory = try {
        SpamCategory.valueOf(value)
    } catch (_: IllegalArgumentException) {
        SpamCategory.SAFE
    }

    @TypeConverter
    fun fromReportStatus(status: ReportStatus): String = status.name
    @TypeConverter
    fun toReportStatus(value: String): ReportStatus = try {
        ReportStatus.valueOf(value)
    } catch (_: IllegalArgumentException) { ReportStatus.PENDING }

    @TypeConverter
    fun fromAlarmType(type: AlarmType): String = type.name
    @TypeConverter
    fun toAlarmType(value: String): AlarmType = try {
        AlarmType.valueOf(value)
    } catch (_: IllegalArgumentException) { AlarmType.REMOTE_ALARM }

    @TypeConverter
    fun fromAlarmResult(result: AlarmResult): String = result.name
    @TypeConverter
    fun toAlarmResult(value: String): AlarmResult = try {
        AlarmResult.valueOf(value)
    } catch (_: IllegalArgumentException) {
        AlarmResult.PENDING
    }

    @TypeConverter
    fun fromGeofenceTransitionType(type: GeofenceTransitionType): String = type.name
    @TypeConverter
    fun toGeofenceTransitionType(value: String): GeofenceTransitionType = try {
        GeofenceTransitionType.valueOf(value)
    } catch (_: IllegalArgumentException) { GeofenceTransitionType.ENTER }

    @TypeConverter
    fun fromSmsRuleType(type: SmsRuleType): String = type.name
    @TypeConverter
    fun toSmsRuleType(value: String): SmsRuleType = try {
        SmsRuleType.valueOf(value)
    } catch (_: IllegalArgumentException) { SmsRuleType.KEYWORD_MATCH }

    @TypeConverter
    fun fromSmsCategory(category: SmsCategory): String = category.name
    @TypeConverter
    fun toSmsCategory(value: String): SmsCategory = try {
        SmsCategory.valueOf(value)
    } catch (_: IllegalArgumentException) { SmsCategory.PERSONAL }

    @TypeConverter
    fun fromCallDirection(direction: CallDirection): String = direction.name
    @TypeConverter
    fun toCallDirection(value: String): CallDirection = try {
        CallDirection.valueOf(value)
    } catch (_: IllegalArgumentException) { CallDirection.INCOMING }

    @TypeConverter
    fun fromTrustLevel(level: TrustLevel): String = level.name
    @TypeConverter
    fun toTrustLevel(value: String): TrustLevel = try {
        TrustLevel.valueOf(value)
    } catch (_: IllegalArgumentException) {
        TrustLevel.NEW
    }

    @TypeConverter
    fun fromScheduleBlockType(type: ScheduleBlockType): String = type.name
    @TypeConverter
    fun toScheduleBlockType(value: String): ScheduleBlockType = try {
        ScheduleBlockType.valueOf(value)
    } catch (_: IllegalArgumentException) {
        ScheduleBlockType.ALL_SPAM
    }
}
