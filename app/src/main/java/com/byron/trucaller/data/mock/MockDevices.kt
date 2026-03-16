package com.byron.trucaller.data.mock

import com.byron.trucaller.data.model.Device
import com.byron.trucaller.data.model.DeviceStatus

val mockDevices = listOf(
    Device(
        id = "dev-001", userId = "usr-001", model = "Galaxy A54", manufacturer = "Samsung",
        osVersion = "Android 14", deviceId = "SA54-GRACE-001",
        status = DeviceStatus.ACTIVE, lastIp = "41.210.145.32",
        lastSeen = "2025-11-28T14:30:00Z", registeredAt = "2024-03-15T10:05:00Z"
    ),
    Device(
        id = "dev-002", userId = "usr-002", model = "Spark 10 Pro", manufacturer = "Tecno",
        osVersion = "Android 13", deviceId = "TS10P-JAMES-002",
        status = DeviceStatus.ACTIVE, lastIp = "41.220.98.17",
        lastSeen = "2025-11-25T09:15:00Z", registeredAt = "2024-04-20T08:10:00Z"
    ),
    Device(
        id = "dev-003", userId = "usr-003", model = "Hot 30i", manufacturer = "Infinix",
        osVersion = "Android 13", deviceId = "IH30I-SARAH-003",
        status = DeviceStatus.ACTIVE, lastIp = "41.190.72.55",
        lastSeen = "2025-12-01T16:45:00Z", registeredAt = "2024-05-10T12:05:00Z"
    ),
    Device(
        id = "dev-004", userId = "usr-004", model = "Redmi Note 12", manufacturer = "Xiaomi",
        osVersion = "Android 14", deviceId = "XRN12-BRIAN-004",
        status = DeviceStatus.ACTIVE, lastIp = "197.239.10.88",
        lastSeen = "2025-11-30T11:20:00Z", registeredAt = "2024-06-01T09:05:00Z"
    ),
    Device(
        id = "dev-005", userId = "usr-005", model = "Galaxy A14", manufacturer = "Samsung",
        osVersion = "Android 13", deviceId = "SA14-FAITH-005",
        status = DeviceStatus.INACTIVE, lastIp = "41.210.200.14",
        lastSeen = "2025-10-15T08:00:00Z", registeredAt = "2024-06-15T14:05:00Z"
    ),
    Device(
        id = "dev-006", userId = "usr-006", model = "Pixel 7a", manufacturer = "Google",
        osVersion = "Android 14", deviceId = "GP7A-DAVID-006",
        status = DeviceStatus.ACTIVE, lastIp = "41.220.55.120",
        lastSeen = "2025-11-29T13:10:00Z", registeredAt = "2024-07-01T10:05:00Z"
    ),
    Device(
        id = "dev-007", userId = "usr-007", model = "Galaxy S23", manufacturer = "Samsung",
        osVersion = "Android 14", deviceId = "SS23-PATRICIA-007",
        status = DeviceStatus.STOLEN, lastIp = "41.190.33.90",
        lastSeen = "2025-11-20T22:30:00Z", registeredAt = "2024-07-15T11:05:00Z"
    ),
    Device(
        id = "dev-008", userId = "usr-008", model = "itel S23+", manufacturer = "itel",
        osVersion = "Android 12", deviceId = "IS23P-JOSEPH-008",
        status = DeviceStatus.ACTIVE, lastIp = "197.239.45.67",
        lastSeen = "2025-11-26T15:00:00Z", registeredAt = "2024-08-01T08:35:00Z"
    ),
    Device(
        id = "dev-009", userId = "usr-009", model = "Camon 20", manufacturer = "Tecno",
        osVersion = "Android 13", deviceId = "TC20-ESTHER-009",
        status = DeviceStatus.ACTIVE, lastIp = "41.210.78.201",
        lastSeen = "2025-12-02T08:45:00Z", registeredAt = "2024-08-20T09:05:00Z"
    ),
    Device(
        id = "dev-010", userId = "usr-010", model = "Galaxy A34", manufacturer = "Samsung",
        osVersion = "Android 14", deviceId = "SA34-SAMUEL-010",
        status = DeviceStatus.FLAGGED, lastIp = "41.220.112.45",
        lastSeen = "2025-11-30T12:00:00Z", registeredAt = "2024-09-01T10:05:00Z"
    )
)
