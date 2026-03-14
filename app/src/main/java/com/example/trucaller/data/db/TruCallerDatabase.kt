package com.example.trucaller.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.trucaller.data.dao.AdminUserDao
import com.example.trucaller.data.dao.AlarmLogDao
import com.example.trucaller.data.dao.BlockedNumberDao
import com.example.trucaller.data.dao.CallerIdDao
import com.example.trucaller.data.dao.ContactDao
import com.example.trucaller.data.dao.DeviceDao
import com.example.trucaller.data.dao.IpLogDao
import com.example.trucaller.data.dao.StolenReportDao
import com.example.trucaller.data.dao.UserDao
import com.example.trucaller.data.model.AdminUser
import com.example.trucaller.data.model.AlarmLog
import com.example.trucaller.data.model.BlockedNumber
import com.example.trucaller.data.model.CallerIdEntry
import com.example.trucaller.data.model.Contact
import com.example.trucaller.data.model.Device
import com.example.trucaller.data.model.IpLog
import com.example.trucaller.data.model.StolenReport
import com.example.trucaller.data.model.User

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
        BlockedNumber::class
    ],
    version = 2,
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
}
