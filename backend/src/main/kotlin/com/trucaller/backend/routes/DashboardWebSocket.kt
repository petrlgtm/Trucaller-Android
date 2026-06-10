package com.trucaller.backend.routes

import com.mongodb.client.model.Filters
import com.trucaller.backend.auth.JwtConfig
import com.trucaller.backend.data.Collections
import com.trucaller.backend.data.models.ApiResponse
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val wsJson = Json { encodeDefaults = true; ignoreUnknownKeys = true }

/**
 * WebSocket endpoint for realtime admin dashboard stats.
 *
 * Connect: ws(s)://host/api/admin/ws/dashboard?token=<access_jwt>
 *
 * - Authenticates the connecting client via the `token` query parameter.
 * - Only SUPER_ADMIN and MODERATOR roles are accepted.
 * - Sends the current [DashboardStats] JSON frame immediately on connect,
 *   then pushes an updated frame every 5 seconds until the client disconnects.
 */
fun Route.adminDashboardWebSocket() {
    webSocket("/api/admin/ws/dashboard") {
        val token = call.request.queryParameters["token"]
        if (token.isNullOrBlank()) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Missing token"))
            return@webSocket
        }

        val decoded = JwtConfig.verifyAccessToken(token)
        if (decoded == null) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid or expired token"))
            return@webSocket
        }

        val role = decoded.getClaim("role")?.asString()
        if (role != "SUPER_ADMIN" && role != "MODERATOR") {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Admin access required"))
            return@webSocket
        }

        while (isActive) {
            try {
                val stats = DashboardStats(
                    userCount = Collections.users.countDocuments(),
                    deviceCount = Collections.devices.countDocuments(),
                    reportCount = Collections.stolenReports.countDocuments(),
                    alarmCount = Collections.alarmLogs.countDocuments(),
                    callerIdCount = Collections.callerIds.countDocuments(),
                    smsSpamReportCount = Collections.smsSpamReports.countDocuments(),
                    pendingReports = Collections.stolenReports.countDocuments(
                        Filters.eq("status", "PENDING")
                    ),
                    activeDevices = Collections.devices.countDocuments(
                        Filters.eq("status", "ACTIVE")
                    ),
                    verifiedReports = Collections.stolenReports.countDocuments(
                        Filters.eq("status", "VERIFIED")
                    ),
                    resolvedReports = Collections.stolenReports.countDocuments(
                        Filters.eq("status", "RESOLVED")
                    )
                )
                val frame = wsJson.encodeToString(
                    ApiResponse(success = true, data = stats, message = "stats")
                )
                send(Frame.Text(frame))
            } catch (_: Exception) {
                break
            }
            delay(5_000L)
        }
    }
}
