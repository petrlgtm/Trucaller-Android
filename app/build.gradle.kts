plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

// Load signing credentials from local.properties, keystore.properties, or environment variables.
// Priority: keystore.properties > local.properties > environment variables.
val localProps = java.util.Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) localFile.inputStream().use { load(it) }
}
val keystoreProps = java.util.Properties().apply {
    val ksFile = rootProject.file("keystore.properties")
    if (ksFile.exists()) ksFile.inputStream().use { load(it) }
}

fun signingProp(key: String): String =
    keystoreProps.getProperty(key)
        ?: localProps.getProperty(key)
        ?: System.getenv(key)
        ?: ""

android {
    namespace = "com.byron.trucaller"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.byron.trucaller"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val storeFilePath = signingProp("RELEASE_STORE_FILE").ifBlank { signingProp("storeFile") }
            if (storeFilePath.isNotBlank()) {
                storeFile = file(storeFilePath)
                storePassword = signingProp("RELEASE_STORE_PASSWORD").ifBlank { signingProp("storePassword") }
                keyAlias = signingProp("RELEASE_KEY_ALIAS").ifBlank { signingProp("keyAlias") }
                keyPassword = signingProp("RELEASE_KEY_PASSWORD").ifBlank { signingProp("keyPassword") }
            }
        }
    }
    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"https://trucaller-backend.onrender.com\"")
            buildConfigField("Boolean", "ENABLE_CALL_RECORDING", "true")
            buildConfigField("Boolean", "ENABLE_ANTI_THEFT", "true")
            buildConfigField("Boolean", "ENABLE_FAMILY_GROUPS", "true")
            buildConfigField("Boolean", "ENABLE_ANALYTICS", "true")
            buildConfigField("Boolean", "ENABLE_GEOFENCING", "true")
            buildConfigField("Boolean", "LOG_HTTP_REQUESTS", "true")
        }
        release {
            buildConfigField("String", "API_BASE_URL", "\"https://trucaller-backend.onrender.com\"")
            buildConfigField("Boolean", "ENABLE_CALL_RECORDING", "true")
            buildConfigField("Boolean", "ENABLE_ANTI_THEFT", "true")
            buildConfigField("Boolean", "ENABLE_FAMILY_GROUPS", "true")
            buildConfigField("Boolean", "ENABLE_ANALYTICS", "true")
            buildConfigField("Boolean", "ENABLE_GEOFENCING", "true")
            buildConfigField("Boolean", "LOG_HTTP_REQUESTS", "false")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/license.txt"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
            excludes += "META-INF/notice.txt"
            excludes += "META-INF/INDEX.LIST"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.truth)
    testImplementation(libs.arch.core.testing)
    testImplementation(libs.room.testing)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.truth)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.play.services.auth)
    implementation(libs.play.services.location)
    implementation(libs.google.api.client.android)
    implementation(libs.google.api.services.drive)
    implementation(libs.gson)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.messaging)
    implementation(libs.osmdroid.android)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.coil.compose)
    implementation(libs.profileinstaller)
    debugImplementation(libs.leakcanary)
}
