package com.example.trucaller.data.mock

import com.example.trucaller.data.model.CallerIdEntry
import com.example.trucaller.data.model.SpamCategory

val mockCallerIdEntries = listOf(
    // SAFE entries
    CallerIdEntry("cid-001", "+256771100001", "MTN Customer Care", 2, 0, SpamCategory.SAFE, "2025-11-01T10:00:00Z"),
    CallerIdEntry("cid-002", "+256782200002", "Airtel Customer Support", 1, 0, SpamCategory.SAFE, "2025-11-02T11:00:00Z"),
    CallerIdEntry("cid-003", "+256700300003", "Kampala Hospital Reception", 0, 0, SpamCategory.SAFE, "2025-11-03T12:00:00Z"),
    CallerIdEntry("cid-004", "+256774400004", "Stanbic Bank Uganda", 3, 1, SpamCategory.SAFE, "2025-11-04T09:00:00Z"),
    CallerIdEntry("cid-005", "+256785500005", "Safe Boda Support", 5, 0, SpamCategory.SAFE, "2025-11-05T14:00:00Z"),
    CallerIdEntry("cid-006", "+256706600006", "Jumia Delivery", 4, 1, SpamCategory.SAFE, "2025-11-06T15:00:00Z"),
    CallerIdEntry("cid-007", "+256777700007", "URA Tax Office", 2, 0, SpamCategory.SAFE, "2025-11-07T10:00:00Z"),
    CallerIdEntry("cid-008", "+256758800008", "Mulago Hospital", 1, 0, SpamCategory.SAFE, "2025-11-08T08:00:00Z"),
    CallerIdEntry("cid-009", "+256709900009", "NWSC Water Utility", 3, 0, SpamCategory.SAFE, "2025-11-09T09:00:00Z"),
    CallerIdEntry("cid-010", "+256780100010", "Umeme Power", 5, 1, SpamCategory.SAFE, "2025-11-10T10:00:00Z"),
    // SUSPECTED_SPAM entries
    CallerIdEntry("cid-011", "+256771200011", "Unknown Caller - Kampala", 35, 8, SpamCategory.SUSPECTED_SPAM, "2025-11-11T10:00:00Z"),
    CallerIdEntry("cid-012", "+256782300012", "Telemarketer", 42, 12, SpamCategory.SUSPECTED_SPAM, "2025-11-12T11:00:00Z"),
    CallerIdEntry("cid-013", "+256703400013", "Survey Company", 28, 5, SpamCategory.SUSPECTED_SPAM, "2025-11-13T12:00:00Z"),
    CallerIdEntry("cid-014", "+256774500014", "Insurance Sales", 38, 10, SpamCategory.SUSPECTED_SPAM, "2025-11-14T09:00:00Z"),
    CallerIdEntry("cid-015", "+256785600015", "Real Estate Agent", 25, 4, SpamCategory.SUSPECTED_SPAM, "2025-11-15T14:00:00Z"),
    // SPAM entries
    CallerIdEntry("cid-016", "+256706700016", "MTN Prize Winner Scam", 72, 45, SpamCategory.SPAM, "2025-11-16T10:00:00Z"),
    CallerIdEntry("cid-017", "+256777800017", "Airtel Promotion Center", 68, 38, SpamCategory.SPAM, "2025-11-17T11:00:00Z"),
    CallerIdEntry("cid-018", "+256758900018", "Quick Loan Offer", 75, 52, SpamCategory.SPAM, "2025-11-18T12:00:00Z"),
    CallerIdEntry("cid-019", "+256709100019", "WhatsApp Verification Scam", 65, 30, SpamCategory.SPAM, "2025-11-19T09:00:00Z"),
    CallerIdEntry("cid-020", "+256780200020", "Mobile Money Fraud", 78, 60, SpamCategory.SPAM, "2025-11-20T14:00:00Z"),
    // FRAUD entries
    CallerIdEntry("cid-021", "+256771300021", "Bank Account Phishing", 92, 150, SpamCategory.FRAUD, "2025-11-21T10:00:00Z"),
    CallerIdEntry("cid-022", "+256782400022", "SIM Swap Scammer", 88, 120, SpamCategory.FRAUD, "2025-11-22T11:00:00Z"),
    CallerIdEntry("cid-023", "+256703500023", "Mobile Money Theft Ring", 95, 280, SpamCategory.FRAUD, "2025-11-23T12:00:00Z"),
    CallerIdEntry("cid-024", "+256774600024", "Identity Theft Network", 90, 200, SpamCategory.FRAUD, "2025-11-24T09:00:00Z"),
    CallerIdEntry("cid-025", "+256785700025", "Investment Ponzi Scheme", 85, 95, SpamCategory.FRAUD, "2025-11-25T14:00:00Z")
)
