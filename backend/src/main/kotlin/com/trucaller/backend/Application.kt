package com.trucaller.backend

import com.trucaller.backend.auth.UnauthorizedException
import com.trucaller.backend.auth.JwtConfig
import com.trucaller.backend.auth.adminAuthRoutes
import com.trucaller.backend.auth.authRoutes
import com.trucaller.backend.data.MongoDB
import com.trucaller.backend.plugins.RateLimiterPlugin
import com.trucaller.backend.plugins.RequestSizeLimiterPlugin
import com.trucaller.backend.plugins.SecurityLogger
import com.trucaller.backend.service.FcmService
import com.trucaller.backend.routes.adminRoutes
import com.trucaller.backend.routes.alarmRoutes
import com.trucaller.backend.routes.callerIdRoutes
import com.trucaller.backend.routes.contactRoutes
import com.trucaller.backend.routes.deviceRoutes
import com.trucaller.backend.routes.blockedRoutes
import com.trucaller.backend.routes.familyGroupRoutes
import com.trucaller.backend.routes.geofenceRoutes
import com.trucaller.backend.routes.smsRoutes
import com.trucaller.backend.routes.analyticsRoutes
import com.trucaller.backend.routes.stolenReportRoutes
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

@Serializable
data class HealthResponse(
    val status: String,
    val timestamp: String = java.time.Instant.now().toString(),
    val version: String = "1.0.0"
)

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {
    val logger = LoggerFactory.getLogger("Application")

    // ── Content Negotiation ──────────────────────────────────────────────
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }

    // ── CORS — configurable allowed origins ──────────────────────────────
    val allowedOriginsStr = environment.config.propertyOrNull("security.cors.allowedOrigins")?.getString()
    val allowedOrigins = allowedOriginsStr
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?: emptyList()

    install(CORS) {
        if (allowedOrigins.isEmpty()) {
            anyHost() // Fallback for development when no origins configured
            logger.warn("CORS: No allowed origins configured — using anyHost(). Set CORS_ALLOWED_ORIGINS in production.")
        } else {
            allowedOrigins.forEach { origin -> allowHost(origin.removePrefix("https://").removePrefix("http://"), schemes = listOf("https", "http")) }
            logger.info("CORS: Allowed origins = $allowedOrigins")
        }
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
    }

    // ── Rate Limiter (in-memory sliding window) ──────────────────────────
    val authRateLimit = environment.config.propertyOrNull("security.rateLimit.authRequestsPerMin")?.getString()?.toIntOrNull() ?: 100
    val unauthRateLimit = environment.config.propertyOrNull("security.rateLimit.unauthRequestsPerMin")?.getString()?.toIntOrNull() ?: 10

    install(RateLimiterPlugin) {
        authLimitPerMin = authRateLimit
        unauthLimitPerMin = unauthRateLimit
    }

    // ── Request Body Size Limit ──────────────────────────────────────────
    val maxBodyBytes = environment.config.propertyOrNull("security.maxRequestBodyBytes")?.getString()?.toLongOrNull() ?: 1_048_576L

    install(RequestSizeLimiterPlugin) {
        maxBytes = maxBodyBytes
    }

    // ── Exception Handling ──────────────────────────────────────────────
    install(StatusPages) {
        exception<UnauthorizedException> { call, cause ->
            SecurityLogger.logAuthFailure(
                phoneNumber = "unknown",
                ip = call.request.local.remoteAddress,
                reason = cause.message ?: "Unauthorized"
            )
            call.respond(
                HttpStatusCode.Unauthorized,
                mapOf("success" to false, "error" to (cause.message ?: "Authentication required"))
            )
        }
        exception<IllegalAccessException> { call, cause ->
            SecurityLogger.logSuspiciousActivity(
                ip = call.request.local.remoteAddress,
                detail = "Forbidden: ${cause.message}"
            )
            call.respond(
                HttpStatusCode.Forbidden,
                mapOf("success" to false, "error" to (cause.message ?: "Access denied"))
            )
        }
        exception<io.ktor.server.plugins.BadRequestException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("success" to false, "error" to (cause.message ?: "Bad request"))
            )
        }
        exception<Throwable> { call, cause ->
            logger.error("Unhandled exception on ${call.request.local.uri}", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("success" to false, "error" to "Internal server error")
            )
        }
    }

    // ── JWT Authentication ───────────────────────────────────────────────
    JwtConfig.init(environment)
    JwtConfig.configureAuth(this)

    // ── Firebase Admin (FCM push) ───────────────────────────────────────
    FcmService.initialize()

    // ── MongoDB connection ───────────────────────────────────────────────
    val mongoUri = environment.config.property("mongo.uri").getString()
    environment.monitor.subscribe(ApplicationStarted) { app ->
        app.launch {
            MongoDB.connect(mongoUri)
        }
    }
    environment.monitor.subscribe(ApplicationStopped) {
        MongoDB.close()
    }

    // ── Routing ──────────────────────────────────────────────────────────
    routing {
        // ── Health Check ────────────────────────────────────────────────
        get("/api/health") {
            call.respond(HttpStatusCode.OK, HealthResponse(status = "ok"))
        }

        get("/") {
            call.resolveResource("static/index.html")?.let {
                call.respond(it)
            } ?: call.respond(HttpStatusCode.OK, HealthResponse(status = "ok"))
        }

        get("/privacy-policy") {
            call.resolveResource("static/privacy-policy.html")?.let {
                call.respond(it)
            } ?: call.respond(HttpStatusCode.NotFound, "Page not found")
        }

        get("/terms-of-service") {
            call.resolveResource("static/terms-of-service.html")?.let {
                call.respond(it)
            } ?: call.respond(HttpStatusCode.NotFound, "Page not found")
        }

        // ── API Routes ──────────────────────────────────────────────────
        authRoutes()
        adminAuthRoutes()
        callerIdRoutes()
        contactRoutes()
        deviceRoutes()
        stolenReportRoutes()
        alarmRoutes()
        smsRoutes()
        blockedRoutes()
        geofenceRoutes()
        familyGroupRoutes()
        analyticsRoutes()
        adminRoutes()
    }
}
