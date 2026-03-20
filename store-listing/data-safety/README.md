# Data Safety Declarations

This document outlines the data types collected and shared by TruCaller, as required by the Google Play Data Safety section.

## Data Collected

### Personal Information
| Data Type | Collected | Purpose | Required |
|-----------|-----------|---------|----------|
| Name | Yes | Account creation, caller ID | Yes |
| Phone number | Yes | Account identity, caller ID | Yes |
| Email address | No | Not collected | No |

### Device or Other IDs
| Data Type | Collected | Purpose | Required |
|-----------|-----------|---------|----------|
| Device ID (Android ID) | Yes | Device registration, anti-theft | Yes |
| Firebase Installation ID | Yes | Push notifications | Yes |

### Contacts
| Data Type | Collected | Purpose | Required |
|-----------|-----------|---------|----------|
| Contact names | Yes | Caller ID, cloud backup | Yes |
| Contact phone numbers | Yes | Caller ID, cloud backup | Yes |

### Location
| Data Type | Collected | Purpose | Required |
|-----------|-----------|---------|----------|
| Approximate location (IP-based) | Yes | Security logging, anti-theft | Yes |
| Precise location (GPS) | Yes | Anti-theft device recovery | Optional |

### App Activity
| Data Type | Collected | Purpose | Required |
|-----------|-----------|---------|----------|
| Call logs (blocked/spam) | Yes | Spam detection, analytics | Yes |
| In-app actions | Yes | Analytics, service improvement | No |

### App Info and Performance
| Data Type | Collected | Purpose | Required |
|-----------|-----------|---------|----------|
| Crash logs | Yes | Bug fixing, stability | No |
| Device model/OS | Yes | Compatibility, anti-theft | Yes |

## Data Shared

| Data Type | Shared With | Purpose |
|-----------|------------|---------|
| Phone numbers (caller ID contributions) | All app users | Caller identification |
| Spam reports | All app users | Community spam detection |
| Device info (stolen reports) | Law enforcement (on request) | Stolen device recovery |

## Data NOT Shared
- Passwords (hashed, never transmitted in plaintext after initial registration)
- Contact lists (not shared with other users)
- Location data (not shared with third parties except IP geolocation service)
- Call recordings (stored locally on device, never uploaded)

## Security Practices
- Data encrypted in transit via HTTPS/TLS
- Passwords hashed with bcrypt (cost factor 12)
- Authentication via JWT tokens with expiry
- Users can request full account deletion at any time

## Data Retention
- Account data: retained while account is active, deleted within 30 days of account deletion
- IP logs: retained for up to 12 months for security auditing
- OTP codes: auto-deleted after 10 minutes (MongoDB TTL index)
- Rate limit records: auto-deleted after 1 hour

## Account Deletion
Users can delete their account and all associated data through:
1. The "Delete My Account" option in the Profile screen of the app
2. Contacting admin@trucaller.co.ug

Upon deletion, the following data is removed:
- User profile and authentication data
- Device registrations and IP logs
- Contacts and backup data
- Blocked numbers and spam reports
- Family group memberships
- Stolen device reports
- Geofences and geofence events
