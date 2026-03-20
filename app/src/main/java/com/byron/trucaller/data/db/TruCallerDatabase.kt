package com.byron.trucaller.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.byron.trucaller.data.dao.AdminUserDao
import com.byron.trucaller.data.dao.AlarmLogDao
import com.byron.trucaller.data.dao.BlockedNumberDao
import com.byron.trucaller.data.dao.CallerIdDao
import com.byron.trucaller.data.dao.ContactDao
import com.byron.trucaller.data.dao.DeviceDao
import com.byron.trucaller.data.dao.IpLogDao
import com.byron.trucaller.data.dao.StolenReportDao
import com.byron.trucaller.data.dao.SmsSpamDao
import com.byron.trucaller.data.dao.ContactAliasDao
import com.byron.trucaller.data.dao.GeofenceDao
import com.byron.trucaller.data.dao.GeofenceEventDao
import com.byron.trucaller.data.dao.UserDao
import com.byron.trucaller.data.model.AdminUser
import com.byron.trucaller.data.model.AlarmLog
import com.byron.trucaller.data.model.BlockedNumber
import com.byron.trucaller.data.model.CallerIdEntry
import com.byron.trucaller.data.model.Contact
import com.byron.trucaller.data.model.Device
import com.byron.trucaller.data.model.Geofence
import com.byron.trucaller.data.model.GeofenceEvent
import com.byron.trucaller.data.model.IpLog
import com.byron.trucaller.data.model.ContactAlias
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
        GeofenceEvent::class
    ],
    version = 9,
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
}
