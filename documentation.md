# Trucaller Android — Project Documentation

**Date:** 2026-06-10  
**Version:** 1.0.1 (versionCode 2)  
**Client:** Byron  
**Repository:** https://github.com/petrlgtm/Trucaller-Android

---

## Overview

Trucaller Android is a full-featured caller ID, spam detection, and device-security app for Android. It consists of two components that live in the same monorepo:

| Component | Stack | Deployed |
|---|---|---|
| Android app | Kotlin, Jetpack Compose, Room, MVVM | Device / Play Store |
| Backend API | Kotlin, Ktor, MongoDB Atlas | Render.com (free tier) |

---

## Architecture

### Android App

```
app/src/main/java/com/byron/trucaller/
├── data/
│   ├── dao/            Room DAOs (18 tables)
│   ├── db/             Room database, converters, seeder
│   ├── mock/           Mock data for previews/testing
│   ├── model/          Entity data classes
│   ├── preferences/    UserPreferences (DataStore)
│   └── repository/     Repository layer + BackendMappers
├── di/                 AppContainer (manual DI)
├── service/            Background services, receivers, workers
├── ui/
│   ├── admin/          Admin panel screens
│   ├── auth/           Login, register, OTP, splash
│   ├── components/     Shared Compose components
│   ├── family/         Family group map screen
│   ├── main/           Main app screens
│   ├── navigation/     NavGraph
│   ├── stolen/         Anti-theft screens
│   └── theme/          Color, type, spacing, theme
├── util/               Helpers (phone, format, security, SMS, image)
├── viewmodel/          One ViewModel per feature (20+)
├── MainActivity.kt
└── TruCallerApplication.kt
```

**Pattern:** MVVM + Repository. Room is the single source of truth locally; the backend is synced via repositories. Manual DI via `AppContainer` (no Hilt).

### Backend

```
backend/src/main/kotlin/com/trucaller/backend/
├── auth/               JWT config, auth routes, admin auth routes
├── data/
│   ├── models/         MongoDB document models
│   ├── Collections.kt  Collection name constants
│   ├── MongoDB.kt      Connection + database accessor
│   └── AdminSeeder.kt  Seeds the default admin account
├── plugins/            Rate limiter, request size limiter, security logger, input validator
├── routes/             One file per resource (15 route files)
├── service/            Email (SMTP), FCM push, Redis cache, IP geolocation,
│                       background jobs, SMS, caller ID logic, phone normalizer
└── Application.kt      Server bootstrap, health endpoints, route wiring
```

---

## Features

### User-facing
- **Caller ID overlay** — shows caller name/spam rating on incoming calls via `CallerIdOverlayService`
- **Spam detection** — community-sourced spam numbers synced via `SpamSyncWorker`
- **Call recording** — `CallRecordingService`, recordings managed in `CallRecordingsScreen`
- **Contact aliases** — per-user nicknames for numbers
- **Number blocking** — with optional scheduling via `BlockingScheduleViewModel`
- **SMS rules** — pattern-based SMS filtering (`SmsPatternEngine`, `SmsRulesScreen`)
- **Family groups** — invite members, view live device map (`FamilyDeviceMapScreen`, osmdroid)
- **Geofencing** — create zones, receive enter/exit events (`GeofencingService`)
- **Analytics** — call/SMS statistics dashboard

### Anti-theft / Security
- **Stolen device reporting** — report via `ReportStolenScreen`, admin can track
- **Remote alarm** — trigger loud alarm remotely via push (`AlarmSoundManager`)
- **Remote lock / wipe** — via Device Admin API (`DeviceAdminHelper`)
- **Bluetooth forensics** — log nearby BT devices (`BluetoothForensicsService`)
- **Network forensics** — IP log with geolocation (`NetworkForensicsScreen`)
- **Anti-uninstall receiver** — `AntiUninstallReceiver` fires on package removal

### Admin panel (in-app)
- Dashboard with live stats via WebSocket
- User management, device management, stolen report tracking
- Caller ID database CRUD
- Alarm log viewer
- Settings (spam sync interval, etc.)

---

## Backend API

**Base URL:** `https://trucaller-backend-sh3t.onrender.com`

### Health
| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/health` | None | Server liveness |
| GET | `/api/health/db` | None | MongoDB ping |

### Authentication
| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/auth/register` | None | Register user (sends email OTP) |
| POST | `/api/auth/verify` | None | Verify OTP, returns JWT |
| POST | `/api/auth/login` | None | Login with phone + OTP |
| POST | `/api/admin/login` | None | Admin login (phone + password) |
| GET | `/api/auth/me` | JWT | Current user profile |
| PUT | `/api/auth/fcm-token` | JWT | Update FCM push token |

### Caller ID
| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/caller-id/lookup/{phoneNumber}` | JWT | Look up a number |
| GET | `/api/caller-id/spam-numbers` | JWT | Fetch spam list |
| GET | `/api/caller-id/spam-sync` | JWT | Sync since timestamp |
| GET | `/api/caller-id/verify/{phoneNumber}` | JWT | Check registration status |
| POST | `/api/caller-ids` | Admin | Add caller ID entry |
| PUT | `/api/caller-ids/{id}` | Admin | Update entry |
| DELETE | `/api/caller-ids/{id}` | Admin | Delete entry |

### Contacts
| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/contacts/upload` | JWT | Bulk upload device contacts |
| GET | `/api/contacts/sync` | JWT | Fetch updated contacts |
| GET | `/api/contacts/check/{phoneNumber}` | JWT | Check if number is registered |

### Devices
| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/devices/register` | JWT | Register device |
| GET | `/api/devices` | JWT | List user's devices |
| PUT | `/api/devices/{deviceId}/status` | JWT | Update device status |
| GET | `/api/devices/{deviceId}/ip-logs` | JWT | IP log for device |
| GET | `/api/devices/{deviceId}/forensics` | JWT | BT/network forensics |
| PUT | `/api/devices/{deviceId}/recover` | JWT | Mark device recovered |

### Stolen Reports
| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/stolen/report` | JWT | File stolen report |
| GET | `/api/stolen/reports` | JWT | List own reports |

### Alarms
| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/alarms/trigger` | JWT | Trigger remote alarm |
| GET | `/api/alarms/logs/{deviceId}` | JWT | Fetch alarm log |
| PUT | `/api/alarms/logs/{logId}/result` | JWT | Update alarm result |

### SMS
| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/sms/report` | JWT | Report spam SMS |
| POST | `/api/sms/report/bulk` | JWT | Bulk report |
| GET | `/api/sms/sms-spam-reports` | JWT | Fetch spam reports |

### Blocked Numbers
| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/blocked/add` | JWT | Block a number |
| GET | `/api/blocked/check/{number}` | JWT | Check if blocked |

### Geofences
| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/geofences` | JWT | Create geofence |
| GET | `/api/geofences/{deviceId}` | JWT | List geofences |
| GET | `/api/geofences/detail/{id}` | JWT | Get single geofence |
| PUT | `/api/geofences/{id}` | JWT | Update geofence |
| DELETE | `/api/geofences/{id}` | JWT | Delete geofence |
| POST | `/api/geofence-events` | JWT | Log enter/exit event |
| GET | `/api/geofence-events/{deviceId}` | JWT | Events for device |
| GET | `/api/geofence-events/fence/{geofenceId}` | JWT | Events for fence |

### Family Groups
| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/family-groups/create` | JWT | Create group |
| GET | `/api/family-groups/my-groups` | JWT | List my groups |
| GET | `/api/family-groups/{groupId}` | JWT | Group details |
| GET | `/api/family-groups/{groupId}/devices` | JWT | Devices in group |
| POST | `/api/family-groups/{groupId}/invite` | JWT | Invite member |
| POST | `/api/family-groups/join` | JWT | Join via invite code |
| PUT | `/api/family-groups/{groupId}/members/{memberId}/role` | JWT | Change role |
| DELETE | `/api/family-groups/{groupId}/members/{memberId}` | JWT | Remove member |
| DELETE | `/api/family-groups/{groupId}` | JWT | Delete group |
| POST | `/api/family-groups/{groupId}/alert` | JWT | Send alert to group |

### Analytics
| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/analytics/dashboard` | JWT | Aggregated stats |
| GET | `/api/analytics/me/history` | JWT | User activity history |

### Admin
| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/admin/users` | Admin | List all users |
| GET | `/api/admin/users/{userId}` | Admin | User detail |
| GET | `/api/admin/users/{userId}/trust` | Admin | Trust score |
| GET | `/api/admin/devices` | Admin | All devices |
| GET | `/api/admin/stolen-reports` | Admin | All stolen reports |
| PUT | `/api/admin/stolen/reports/{id}/status` | Admin | Update report status |
| GET | `/api/admin/alarm-logs` | Admin | All alarm logs |
| GET | `/api/admin/sms-spam-reports` | Admin | All spam reports |
| GET | `/api/admin/audit/logins` | Admin | Login audit log |
| GET | `/api/admin/caller-ids` | Admin | Full caller ID list |
| WS | `/api/admin/ws/dashboard` | Admin | Live dashboard WebSocket |

---

## Tech Stack

### Android
| Library | Version | Purpose |
|---|---|---|
| Kotlin | 2.2.10 | Language |
| Jetpack Compose BOM | 2024.09.00 | UI |
| Navigation Compose | 2.8.4 | Screen routing |
| Room | 2.8.4 | Local SQLite ORM |
| DataStore | 1.1.1 | Preferences |
| WorkManager | 2.10.0 | Background sync (spam) |
| OkHttp | 4.12.0 | HTTP client |
| Firebase BOM | 34.10.0 | FCM push, Analytics |
| Play Services Location | 21.3.0 | GPS + Geofencing |
| osmdroid | 6.1.20 | Offline maps |
| Coil | 3.1.0 | Image loading |
| LeakCanary | 2.14 | Memory leak detection (debug) |

### Backend
| Library | Purpose |
|---|---|
| Ktor (Netty) | HTTP server |
| MongoDB Kotlin driver | Database |
| JWT (java-jwt) | Token auth |
| Jedis / Redis | Caching (optional) |
| Jakarta Mail / SMTP | Email OTP delivery |
| Firebase Admin SDK | FCM push notifications |
| Sentry | Error tracking |
| ip-api.com | IP geolocation |

---

## Build & CI

### Local build
```bash
./gradlew :app:assembleDebug          # Android APK
./gradlew :app:testDebugUnitTest      # Android unit tests (328 tests)
./gradlew :backend:test               # Backend tests
```

### GitHub Actions (`.github/workflows/`)

| Workflow | Trigger | What it does |
|---|---|---|
| `ci.yml` | push/PR to main | Compiles backend + runs tests; compiles Android APK + lint |
| `build-backend.yml` | push to main | Builds + deploys backend to Render |
| `keepalive.yml` | cron every 14 min | Pings `/api/health` to prevent Render cold start |
| `keepalive-db.yml` | cron | Pings `/api/health/db` to keep Atlas connection warm |

**JDK:** 21 (Temurin) on all CI jobs.  
**Note:** CI generates a fresh debug keystore before the Android build because the debug build uses the `release` signing config which falls back to `~/.android/debug.keystore`.

### OTP / Auth flow
- Registration and login use **email OTP** (SMTP via Gmail) — Firebase Phone Auth was removed.
- Africa's Talking SMS gateway is wired as a fallback path for SMS OTP.
- JWT tokens are issued after OTP verification and stored in DataStore.

---

## Infrastructure

| Service | What |
|---|---|
| Render.com (free tier) | Backend hosting — sleeps after 15 min idle, takes ~45 s cold start |
| MongoDB Atlas | Primary database |
| Redis (optional) | Caller ID lookup cache — gracefully skipped if not configured |
| Firebase | FCM push + Analytics |
| Sentry | Backend error tracking (set `SENTRY_DSN` env var) |
| Africa's Talking | SMS gateway |

### Environment variables (backend)
```
MONGO_URI              MongoDB Atlas connection string
JWT_SECRET             JWT signing key
SMTP_HOST/PORT/USER/PASS  Email OTP delivery
AT_API_KEY / AT_SENDER   Africa's Talking SMS
FIREBASE_CREDENTIALS   Path to Firebase service account JSON
SENTRY_DSN             Sentry error tracking (optional)
REDIS_URL              Redis connection string (optional)
CORS_ALLOWED_ORIGINS   Comma-separated allowed origins (optional, defaults to anyHost)
```

---

## Test Suite

- **328 Android unit tests** — ViewModels, repositories, Room DAOs, utilities
- **Backend tests** — Ktor route tests, service unit tests
- `MongoAtlasTest` — live Atlas integration test, **skipped automatically** when `MONGO_URI` is not set (uses `Assume.assumeNotNull`)

---

## Key Files

| File | Purpose |
|---|---|
| `app/build.gradle.kts` | Android build config, signing, feature flags |
| `app/src/main/java/.../service/ApiClient.kt` | OkHttp client, auth interceptor, base URL |
| `app/src/main/java/.../service/CallerIdOverlayService.kt` | Incoming call overlay |
| `app/src/main/java/.../service/SpamSyncWorker.kt` | Periodic spam DB sync |
| `app/src/main/java/.../ui/navigation/NavGraph.kt` | Full screen navigation graph |
| `app/src/main/java/.../di/AppContainer.kt` | Dependency wiring |
| `backend/src/main/kotlin/.../Application.kt` | Ktor server entry point |
| `backend/src/main/kotlin/.../data/MongoDB.kt` | DB connection singleton |
| `backend/src/main/kotlin/.../service/EmailService.kt` | OTP email sending |
| `gradle/libs.versions.toml` | All dependency versions (version catalog) |
| `.github/workflows/ci.yml` | CI pipeline |
| `render.yaml` | Render.com deploy config |
| `release-checklist.md` | Pre-release checklist |

---

## Current Status (2026-06-10)

- Android app builds and installs successfully (debug APK ~84 MB)
- All 328 unit tests passing
- Backend live at `https://trucaller-backend-sh3t.onrender.com`
- MongoDB Atlas connected and healthy
- CI pipeline green after fixing: hardcoded `java.home` in `gradle.properties`, JDK 17→21 mismatch, missing debug keystore on runner, MongoAtlasTest failing when no URI present
- Keep-alive crons prevent Render cold starts during active usage
