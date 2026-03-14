package com.trucaller.backend

import com.trucaller.backend.auth.JwtConfig
import com.trucaller.backend.auth.adminAuthRoutes
import com.trucaller.backend.auth.authRoutes
import com.trucaller.backend.data.MongoDB
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class HealthResponse(val status: String)

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    // ── Content Negotiation ──────────────────────────────────────────────
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }

    // ── JWT Authentication ───────────────────────────────────────────────
    JwtConfig.init(environment)
    JwtConfig.configureAuth(this)

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
            call.respond(HttpStatusCode.OK, HealthResponse(status = "ok"))
        }

        authRoutes()
        adminAuthRoutes()
    }
}
