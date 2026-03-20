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

class Converters {
    @TypeConverter
    fun fromDeviceStatus(status: DeviceStatus): String = status.name
    @TypeConverter
    fun toDeviceStatus(value: String): DeviceStatus = DeviceStatus.valueOf(value)

    @TypeConverter
    fun fromSpamCategory(category: SpamCategory): String = category.name
    @TypeConverter
    fun toSpamCategory(value: String): SpamCategory = SpamCategory.valueOf(value)

    @TypeConverter
    fun fromReportStatus(status: ReportStatus): String = status.name
    @TypeConverter
    fun toReportStatus(value: String): ReportStatus = ReportStatus.valueOf(value)

    @TypeConverter
    fun fromAlarmType(type: AlarmType): String = type.name
    @TypeConverter
    fun toAlarmType(value: String): AlarmType = AlarmType.valueOf(value)

    @TypeConverter
    fun fromAlarmResult(result: AlarmResult): String = result.name
    @TypeConverter
    fun toAlarmResult(value: String): AlarmResult = AlarmResult.valueOf(value)

    @TypeConverter
    fun fromGeofenceTransitionType(type: GeofenceTransitionType): String = type.name
    @TypeConverter
    fun toGeofenceTransitionType(value: String): GeofenceTransitionType = GeofenceTransitionType.valueOf(value)

    @TypeConverter
    fun fromSmsRuleType(type: SmsRuleType): String = type.name
    @TypeConverter
    fun toSmsRuleType(value: String): SmsRuleType = SmsRuleType.valueOf(value)

    @TypeConverter
    fun fromSmsCategory(category: SmsCategory): String = category.name
    @TypeConverter
    fun toSmsCategory(value: String): SmsCategory = SmsCategory.valueOf(value)

    @TypeConverter
    fun fromCallDirection(direction: CallDirection): String = direction.name
    @TypeConverter
    fun toCallDirection(value: String): CallDirection = CallDirection.valueOf(value)

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
    fun toScheduleBlockType(value: String): ScheduleBlockType = ScheduleBlockType.valueOf(value)
}
