package com.byron.trucaller.data.db

import androidx.room.TypeConverter
import com.byron.trucaller.data.model.AlarmResult
import com.byron.trucaller.data.model.AlarmType
import com.byron.trucaller.data.model.DeviceStatus
import com.byron.trucaller.data.model.ReportStatus
import com.byron.trucaller.data.model.SpamCategory

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
}
