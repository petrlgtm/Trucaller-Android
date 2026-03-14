package com.example.trucaller.data.mock

import com.example.trucaller.data.model.AlarmLog
import com.example.trucaller.data.model.AlarmResult
import com.example.trucaller.data.model.AlarmType

val mockAlarmLogs = listOf(
    AlarmLog(
        id = "alm-001", deviceId = "dev-007", triggeredBy = "usr-007",
        triggeredByName = "Namugera Patricia", triggeredByRole = "user",
        triggeredAt = "2025-11-21T00:15:00Z", type = AlarmType.REMOTE_ALARM,
        result = AlarmResult.SUCCESS, notes = "Alarm sounded for 30 seconds"
    ),
    AlarmLog(
        id = "alm-002", deviceId = "dev-007", triggeredBy = "adm-001",
        triggeredByName = "Admin Mugisha", triggeredByRole = "admin",
        triggeredAt = "2025-11-21T08:30:00Z", type = AlarmType.LOCATION_REQUEST,
        result = AlarmResult.SUCCESS, notes = "Location pinged: Kisenyi, Kampala"
    ),
    AlarmLog(
        id = "alm-003", deviceId = "dev-007", triggeredBy = "usr-007",
        triggeredByName = "Namugera Patricia", triggeredByRole = "user",
        triggeredAt = "2025-11-21T14:00:00Z", type = AlarmType.LOCK_DEVICE,
        result = AlarmResult.SUCCESS, notes = "Device remotely locked"
    ),
    AlarmLog(
        id = "alm-004", deviceId = "dev-007", triggeredBy = "adm-002",
        triggeredByName = "Mod Nakato", triggeredByRole = "admin",
        triggeredAt = "2025-11-22T09:00:00Z", type = AlarmType.REMOTE_ALARM,
        result = AlarmResult.FAILED, notes = "Device offline, alarm queued"
    ),
    AlarmLog(
        id = "alm-005", deviceId = "dev-010", triggeredBy = "usr-010",
        triggeredByName = "Kato Samuel", triggeredByRole = "user",
        triggeredAt = "2025-11-28T20:30:00Z", type = AlarmType.REMOTE_ALARM,
        result = AlarmResult.PENDING, notes = "Attempting to reach device"
    ),
    AlarmLog(
        id = "alm-006", deviceId = "dev-010", triggeredBy = "usr-010",
        triggeredByName = "Kato Samuel", triggeredByRole = "user",
        triggeredAt = "2025-11-29T10:00:00Z", type = AlarmType.LOCATION_REQUEST,
        result = AlarmResult.SUCCESS, notes = "Last location: Mbarara"
    )
)
