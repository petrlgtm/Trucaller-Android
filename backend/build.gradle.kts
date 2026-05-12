val ktorVersion = "2.3.13"
val logbackVersion = "1.4.14"
val mongodbVersion = "5.3.1"

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.shadow)
    application
}

tasks.shadowJar {
    archiveFileName.set("trucaller-backend.jar")
    mergeServiceFiles()
    manifest {
        attributes("Main-Class" to "com.trucaller.backend.ApplicationKt")
    }
}

application {
    mainClass.set("com.trucaller.backend.ApplicationKt")
}

dependencies {
    // Ktor Server
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion")
    implementation("io.ktor:ktor-server-cors:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")

    // MongoDB Kotlin Coroutine Driver
    implementation("org.mongodb:mongodb-driver-kotlin-coroutine:$mongodbVersion")

    // BCrypt for password hashing
    implementation("at.favre.lib:bcrypt:0.10.2")

    // Ktor Client (outbound HTTP calls, e.g. IP geolocation)
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")

    // Firebase Admin SDK (FCM push notifications)
    implementation("com.google.firebase:firebase-admin:9.4.3")

    // Redis cache (graceful degradation when REDIS_URL not set)
    implementation("io.lettuce:lettuce-core:6.4.0.RELEASE")

    // Logging
    implementation("ch.qos.logback:logback-classic:$logbackVersion")

    // Testing
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("com.google.truth:truth:1.4.4")
    testImplementation("app.cash.turbine:turbine:1.2.0")
}
