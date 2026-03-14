package com.example.trucaller.data.mock

import com.example.trucaller.data.model.LastKnownLocation
import com.example.trucaller.data.model.ReportStatus
import com.example.trucaller.data.model.StolenReport

val mockStolenReports = listOf(
    StolenReport(
        id = "sr-001", deviceId = "dev-007", reportedBy = "usr-007",
        reportedAt = "2025-11-20T23:00:00Z", status = ReportStatus.VERIFIED,
        description = "Phone snatched by boda-boda rider on Kampala Road near City Square. Was using phone for directions when rider grabbed it.",
        pinVerified = true, lastKnownIp = "41.190.33.90",
        lastKnownLocation = LastKnownLocation(0.3136, 32.5811, "Kampala")
    ),
    StolenReport(
        id = "sr-002", deviceId = "dev-007", reportedBy = "usr-007",
        reportedAt = "2025-11-22T10:00:00Z", status = ReportStatus.ESCALATED,
        description = "Follow-up: Phone was tracked to Kisenyi area. Police notified. Device still showing activity on mobile data.",
        pinVerified = true, lastKnownIp = "41.190.45.12",
        lastKnownLocation = LastKnownLocation(0.3080, 32.5690, "Kampala")
    ),
    StolenReport(
        id = "sr-003", deviceId = "dev-005", reportedBy = "usr-005",
        reportedAt = "2025-10-10T18:00:00Z", status = ReportStatus.RESOLVED,
        description = "Phone stolen from bag at Owino Market. Was shopping when someone cut bag strap and ran.",
        pinVerified = true, lastKnownIp = "41.210.200.14",
        lastKnownLocation = LastKnownLocation(0.3120, 32.5780, "Kampala")
    ),
    StolenReport(
        id = "sr-004", deviceId = "dev-010", reportedBy = "usr-010",
        reportedAt = "2025-11-28T20:00:00Z", status = ReportStatus.PENDING,
        description = "Phone lost at a bar in Mbarara. Suspects it was taken while charging at counter.",
        pinVerified = false, lastKnownIp = "41.220.112.45",
        lastKnownLocation = LastKnownLocation(-0.6070, 30.6545, "Mbarara")
    )
)
