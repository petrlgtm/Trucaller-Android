#!/usr/bin/env kotlin

/**
 * Kotlin coroutine-based load test script for the TruCaller backend.
 *
 * Usage:
 *   kotlinc -script LoadTest.kt [BASE_URL] [CONCURRENT_USERS] [REQUESTS_PER_USER]
 *
 * Defaults:
 *   BASE_URL          = http://localhost:8080
 *   CONCURRENT_USERS  = 50
 *   REQUESTS_PER_USER = 20
 *
 * Prerequisites:
 *   - Kotlin 1.9+ installed (kotlinc available on PATH)
 *   - Backend running at the target URL
 *
 * Alternatively, run with `kotlin LoadTest.kt` if using Kotlin scripting.
 */

@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.measureTimeMillis

// ── Configuration ───────────────────────────────────────────────────────

val baseUrl = args.getOrElse(0) { "http://localhost:8080" }.trimEnd('/')
val concurrentUsers = args.getOrElse(1) { "50" }.toInt()
val requestsPerUser = args.getOrElse(2) { "20" }.toInt()

// ── Endpoints to test ───────────────────────────────────────────────────

data class Endpoint(val method: String, val path: String, val body: String? = null)

val endpoints = listOf(
    Endpoint("GET", "/api/health"),
    Endpoint("GET", "/"),
    Endpoint("POST", "/api/auth/send-otp", """{"phoneNumber":"+256700000000","purpose":"registration"}"""),
    Endpoint("POST", "/api/auth/login", """{"phoneNumber":"+256700000001","password":"testpass123"}"""),
    Endpoint("GET", "/api/caller-id/lookup/+256700000000"),
)

// ── Metrics ─────────────────────────────────────────────────────────────

val totalRequests = AtomicInteger(0)
val successCount = AtomicInteger(0)
val failCount = AtomicInteger(0)
val totalLatencyMs = AtomicLong(0)
val maxLatencyMs = AtomicLong(0)
val minLatencyMs = AtomicLong(Long.MAX_VALUE)
val statusCodes = java.util.concurrent.ConcurrentHashMap<Int, AtomicInteger>()

fun recordLatency(ms: Long) {
    totalLatencyMs.addAndGet(ms)
    maxLatencyMs.updateAndGet { maxOf(it, ms) }
    minLatencyMs.updateAndGet { minOf(it, ms) }
}

fun recordStatus(code: Int) {
    statusCodes.computeIfAbsent(code) { AtomicInteger(0) }.incrementAndGet()
}

// ── HTTP client ─────────────────────────────────────────────────────────

fun executeRequest(endpoint: Endpoint): Pair<Int, Long> {
    val url = URL("$baseUrl${endpoint.path}")
    val latency = measureTimeMillis {
        try {
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = endpoint.method
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Content-Type", "application/json")

            if (endpoint.body != null && endpoint.method == "POST") {
                conn.doOutput = true
                conn.outputStream.use { it.write(endpoint.body.toByteArray()) }
            }

            val code = conn.responseCode
            // Read response body to completion
            try {
                conn.inputStream.bufferedReader().readText()
            } catch (_: Exception) {
                try { conn.errorStream?.bufferedReader()?.readText() } catch (_: Exception) {}
            }
            conn.disconnect()
            return Pair(code, 0) // latency filled by measureTimeMillis below
        } catch (e: Exception) {
            return Pair(-1, 0)
        }
    }
    return Pair(-1, latency) // fallback; actual impl below
}

suspend fun runRequest(endpoint: Endpoint) {
    var code = -1
    val latency = measureTimeMillis {
        try {
            val url = URL("$baseUrl${endpoint.path}")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = endpoint.method
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Content-Type", "application/json")

            if (endpoint.body != null && endpoint.method == "POST") {
                conn.doOutput = true
                conn.outputStream.use { it.write(endpoint.body.toByteArray()) }
            }

            code = conn.responseCode
            try {
                conn.inputStream.bufferedReader().readText()
            } catch (_: Exception) {
                try { conn.errorStream?.bufferedReader()?.readText() } catch (_: Exception) {}
            }
            conn.disconnect()
        } catch (e: Exception) {
            code = -1
        }
    }

    totalRequests.incrementAndGet()
    recordLatency(latency)
    recordStatus(code)

    if (code in 200..299) {
        successCount.incrementAndGet()
    } else {
        failCount.incrementAndGet()
    }
}

// ── Main ────────────────────────────────────────────────────────────────

println("=".repeat(60))
println("TruCaller Backend Load Test")
println("=".repeat(60))
println("Target:            $baseUrl")
println("Concurrent users:  $concurrentUsers")
println("Requests/user:     $requestsPerUser")
println("Total requests:    ${concurrentUsers * requestsPerUser}")
println("Endpoints:         ${endpoints.size}")
println("-".repeat(60))
println()

// Warm-up: single health check
println("Warming up...")
try {
    val warmup = URL("$baseUrl/api/health").openConnection() as HttpURLConnection
    warmup.connectTimeout = 5_000
    warmup.readTimeout = 5_000
    val warmupCode = warmup.responseCode
    warmup.disconnect()
    println("Health check: HTTP $warmupCode")
} catch (e: Exception) {
    println("WARNING: Health check failed: ${e.message}")
    println("Make sure the backend is running at $baseUrl")
}

println()
println("Running load test...")
println()

val totalTime = measureTimeMillis {
    runBlocking {
        val jobs = (1..concurrentUsers).map { userId ->
            launch(Dispatchers.IO) {
                repeat(requestsPerUser) { reqIdx ->
                    val endpoint = endpoints[reqIdx % endpoints.size]
                    runRequest(endpoint)
                }
            }
        }
        jobs.forEach { it.join() }
    }
}

// ── Results ─────────────────────────────────────────────────────────────

println("=".repeat(60))
println("RESULTS")
println("=".repeat(60))
println()
println("Total time:        ${totalTime}ms (${totalTime / 1000.0}s)")
println("Total requests:    ${totalRequests.get()}")
println("Successful (2xx):  ${successCount.get()}")
println("Failed:            ${failCount.get()}")
println()

val avgLatency = if (totalRequests.get() > 0) totalLatencyMs.get() / totalRequests.get() else 0
println("Latency:")
println("  Min:             ${minLatencyMs.get()}ms")
println("  Avg:             ${avgLatency}ms")
println("  Max:             ${maxLatencyMs.get()}ms")
println()

val rps = if (totalTime > 0) (totalRequests.get() * 1000.0 / totalTime) else 0.0
println("Throughput:        ${"%.1f".format(rps)} req/s")
println()

println("Status codes:")
statusCodes.toSortedMap().forEach { (code, count) ->
    val label = when (code) {
        -1 -> "CONNECTION_ERROR"
        else -> "HTTP $code"
    }
    println("  $label: ${count.get()}")
}

println()
println("=".repeat(60))

val successRate = if (totalRequests.get() > 0) (successCount.get() * 100.0 / totalRequests.get()) else 0.0
if (successRate >= 95.0) {
    println("PASS - Success rate: ${"%.1f".format(successRate)}%")
} else {
    println("WARN - Success rate: ${"%.1f".format(successRate)}% (below 95% threshold)")
}
println("=".repeat(60))
