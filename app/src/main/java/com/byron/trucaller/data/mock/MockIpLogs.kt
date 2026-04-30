package com.byron.trucaller.data.mock

import com.byron.trucaller.data.model.IpLog

val mockIpLogs = listOf(
    // dev-001 (Apio Grace) — Kampala session: 3 days
    IpLog("ipl-001", "dev-001", "41.210.145.32", "MTN Uganda", "Kampala", "Uganda", 0.3476, 32.5825, "wifi", "2025-11-26T09:00:00Z", startTime = "2025-11-26T09:00:00Z", lastSeen = "2025-11-28T14:30:00Z"),
    // dev-001 — Entebbe trip: 1 day
    IpLog("ipl-003", "dev-001", "41.190.72.10", "Airtel Uganda", "Entebbe", "Uganda", 0.0512, 32.4637, "mobile", "2025-11-25T10:00:00Z", startTime = "2025-11-25T10:00:00Z", lastSeen = "2025-11-25T22:00:00Z"),

    // dev-002 (Okello James) — Jinja session: 2 days
    IpLog("ipl-004", "dev-002", "41.220.98.17", "Airtel Uganda", "Jinja", "Uganda", 0.4244, 33.2041, "wifi", "2025-11-24T09:15:00Z", startTime = "2025-11-24T09:15:00Z", lastSeen = "2025-11-25T18:00:00Z"),

    // dev-003 (Nakamya Sarah) — Kampala: 2 days
    IpLog("ipl-006", "dev-003", "41.190.72.55", "Airtel Uganda", "Kampala", "Uganda", 0.3476, 32.5825, "wifi", "2025-11-30T08:00:00Z", startTime = "2025-11-30T08:00:00Z", lastSeen = "2025-12-01T16:45:00Z"),
    // dev-003 — Mukono: 1 day
    IpLog("ipl-007", "dev-003", "197.239.10.42", "UTL", "Mukono", "Uganda", 0.3533, 32.7554, "mobile", "2025-11-29T06:00:00Z", startTime = "2025-11-29T06:00:00Z", lastSeen = "2025-11-29T20:00:00Z"),

    // dev-007 (Namugera Patricia - STOLEN) — Jinja then back to Kampala (tracking movement)
    IpLog("ipl-012", "dev-007", "197.239.22.67", "UTL", "Jinja", "Uganda", 0.4244, 33.2041, "mobile", "2025-11-23T06:00:00Z", startTime = "2025-11-23T06:00:00Z", lastSeen = "2025-11-23T20:00:00Z"),
    IpLog("ipl-008", "dev-007", "41.190.33.90", "Airtel Uganda", "Kampala", "Uganda", 0.3080, 32.5690, "mobile", "2025-11-21T08:00:00Z", startTime = "2025-11-21T08:00:00Z", lastSeen = "2025-11-22T18:00:00Z"),
    IpLog("ipl-013", "dev-007", "41.220.55.88", "Airtel Uganda", "Mukono", "Uganda", 0.3533, 32.7554, "wifi", "2025-11-23T20:00:00Z", startTime = "2025-11-23T20:00:00Z", lastSeen = "2025-11-24T06:00:00Z"),

    // dev-004 (Mugisha Brian) — Gulu: 2 days
    IpLog("ipl-014", "dev-004", "197.239.10.88", "UTL", "Gulu", "Uganda", 2.7747, 32.2990, "wifi", "2025-11-29T08:00:00Z", startTime = "2025-11-29T08:00:00Z", lastSeen = "2025-11-30T11:20:00Z"),

    // dev-010 (Kato Samuel - Flagged) — Mbarara: 2 days, then Fort Portal: 1 day
    IpLog("ipl-016", "dev-010", "41.220.112.45", "Airtel Uganda", "Mbarara", "Uganda", -0.6070, 30.6545, "wifi", "2025-11-29T18:00:00Z", startTime = "2025-11-29T18:00:00Z", lastSeen = "2025-11-30T12:00:00Z"),
    IpLog("ipl-018", "dev-010", "41.210.200.30", "MTN Uganda", "Fort Portal", "Uganda", 0.6710, 30.2750, "mobile", "2025-11-28T10:00:00Z", startTime = "2025-11-28T10:00:00Z", lastSeen = "2025-11-28T22:00:00Z")
)
