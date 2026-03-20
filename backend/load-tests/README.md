# Backend Load Tests

Kotlin coroutine-based load testing script for the TruCaller backend.

## Prerequisites

- Kotlin 1.9+ (`kotlinc` on PATH)
- Backend running at the target URL

## Usage

```bash
# Default: 50 concurrent users, 20 requests each against localhost:8080
kotlin LoadTest.kt

# Custom target
kotlin LoadTest.kt https://trucaller-backend.onrender.com

# Custom concurrency and request count
kotlin LoadTest.kt http://localhost:8080 100 50
```

## Arguments

| Position | Description         | Default                |
|----------|---------------------|------------------------|
| 1        | Base URL            | `http://localhost:8080` |
| 2        | Concurrent users    | `50`                   |
| 3        | Requests per user   | `20`                   |

## Endpoints Tested

- `GET /api/health` — health check
- `GET /` — root/index
- `POST /api/auth/send-otp` — OTP flow
- `POST /api/auth/login` — authentication
- `GET /api/caller-id/lookup/:phone` — caller ID lookup

## Output

The script prints a summary including:
- Total time, request counts, success/failure counts
- Latency statistics (min/avg/max)
- Throughput (requests/second)
- HTTP status code distribution
- Pass/warn verdict (95% success threshold)
