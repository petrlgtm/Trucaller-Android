package com.example.trucaller.data.mock

import com.example.trucaller.data.model.IpLog

val mockIpLogs = listOf(
    // IP logs for dev-001 (Apio Grace - Active)
    IpLog("ipl-001", "dev-001", "41.210.145.32", "MTN Uganda", "Kampala", "Uganda", 0.3476, 32.5825, "wifi", "2025-11-28T14:30:00Z"),
    IpLog("ipl-002", "dev-001", "41.210.145.50", "MTN Uganda", "Kampala", "Uganda", 0.3136, 32.5811, "mobile", "2025-11-27T09:00:00Z"),
    IpLog("ipl-003", "dev-001", "41.190.72.10", "Airtel Uganda", "Entebbe", "Uganda", 0.0512, 32.4637, "mobile", "2025-11-25T16:00:00Z"),
    // IP logs for dev-002 (Okello James - Active)
    IpLog("ipl-004", "dev-002", "41.220.98.17", "Airtel Uganda", "Jinja", "Uganda", 0.4244, 33.2041, "wifi", "2025-11-25T09:15:00Z"),
    IpLog("ipl-005", "dev-002", "41.220.98.25", "Airtel Uganda", "Jinja", "Uganda", 0.4250, 33.2050, "mobile", "2025-11-24T12:30:00Z"),
    // IP logs for dev-003 (Nakamya Sarah - Active)
    IpLog("ipl-006", "dev-003", "41.190.72.55", "Airtel Uganda", "Kampala", "Uganda", 0.3476, 32.5825, "wifi", "2025-12-01T16:45:00Z"),
    IpLog("ipl-007", "dev-003", "197.239.10.42", "UTL", "Mukono", "Uganda", 0.3533, 32.7554, "mobile", "2025-11-30T08:00:00Z"),
    // IP logs for dev-007 (Namugera Patricia - STOLEN)
    IpLog("ipl-008", "dev-007", "41.190.33.90", "Airtel Uganda", "Kampala", "Uganda", 0.3080, 32.5690, "mobile", "2025-11-22T18:00:00Z"),
    IpLog("ipl-009", "dev-007", "41.190.45.12", "Airtel Uganda", "Kampala", "Uganda", 0.3100, 32.5720, "mobile", "2025-11-22T10:30:00Z"),
    IpLog("ipl-010", "dev-007", "41.210.88.33", "MTN Uganda", "Kampala", "Uganda", 0.3136, 32.5811, "wifi", "2025-11-21T14:00:00Z"),
    IpLog("ipl-011", "dev-007", "41.210.90.15", "MTN Uganda", "Kampala", "Uganda", 0.3120, 32.5780, "mobile", "2025-11-21T08:00:00Z"),
    IpLog("ipl-012", "dev-007", "197.239.22.67", "UTL", "Jinja", "Uganda", 0.4244, 33.2041, "mobile", "2025-11-23T06:00:00Z"),
    IpLog("ipl-013", "dev-007", "41.220.55.88", "Airtel Uganda", "Mukono", "Uganda", 0.3533, 32.7554, "wifi", "2025-11-23T20:00:00Z"),
    // IP logs for dev-004 (Mugisha Brian - Active)
    IpLog("ipl-014", "dev-004", "197.239.10.88", "UTL", "Gulu", "Uganda", 2.7747, 32.2990, "mobile", "2025-11-30T11:20:00Z"),
    IpLog("ipl-015", "dev-004", "41.210.55.44", "MTN Uganda", "Gulu", "Uganda", 2.7740, 32.3000, "wifi", "2025-11-29T08:00:00Z"),
    // IP logs for dev-010 (Kato Samuel - Flagged)
    IpLog("ipl-016", "dev-010", "41.220.112.45", "Airtel Uganda", "Mbarara", "Uganda", -0.6070, 30.6545, "mobile", "2025-11-30T12:00:00Z"),
    IpLog("ipl-017", "dev-010", "41.220.115.80", "Airtel Uganda", "Mbarara", "Uganda", -0.6080, 30.6550, "wifi", "2025-11-29T18:00:00Z"),
    IpLog("ipl-018", "dev-010", "41.210.200.30", "MTN Uganda", "Fort Portal", "Uganda", 0.6710, 30.2750, "mobile", "2025-11-28T10:00:00Z")
)
