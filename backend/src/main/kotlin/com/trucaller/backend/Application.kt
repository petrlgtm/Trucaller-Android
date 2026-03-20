package com.trucaller.backend

import com.trucaller.backend.auth.UnauthorizedException
import com.trucaller.backend.auth.JwtConfig
import com.trucaller.backend.auth.adminAuthRoutes
import com.trucaller.backend.auth.authRoutes
import com.trucaller.backend.data.MongoDB
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

@Serializable
data class HealthResponse(val status: String)

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {
    // ── Content Negotiation ──────────────────────────────────────────────
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }

    // ── CORS ──────────────────────────────────────────────────────────────
    install(CORS) {
        anyHost() // For development — restrict in production
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
    }

    // ── Exception Handling ──────────────────────────────────────────────
    install(StatusPages) {
        exception<UnauthorizedException> { call, cause ->
            call.respond(
                HttpStatusCode.Unauthorized,
                mapOf("success" to false, "error" to (cause.message ?: "Authentication required"))
            )
        }
        exception<IllegalAccessException> { call, cause ->
            call.respond(
                HttpStatusCode.Forbidden,
                mapOf("success" to false, "error" to (cause.message ?: "Access denied"))
            )
        }
        exception<Throwable> { call, cause ->
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
        adminRoutes()
    }
}
