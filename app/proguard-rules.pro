# ── TruCaller ProGuard Rules ──────────────────────────────────────────────

# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Room Database ────────────────────────────────────────────────────────
-keep class com.byron.trucaller.data.model.** { *; }
-keep class com.byron.trucaller.data.dao.** { *; }
-keep class com.byron.trucaller.data.db.** { *; }

# ── Gson serialization ──────────────────────────────────────────────────
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class com.byron.trucaller.service.ApiResult { *; }
-keep class com.byron.trucaller.service.TokenResponse { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

# ── OkHttp ───────────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ── Firebase ─────────────────────────────────────────────────────────────
-keep class com.google.firebase.** { *; }
-keep class com.byron.trucaller.service.TruCallerMessagingService { *; }

# ── Google APIs (Drive, Auth, Location) ──────────────────────────────────
-keep class com.google.api.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.api.client.**
-dontwarn com.google.common.**

# ── BroadcastReceivers ───────────────────────────────────────────────────
-keep class com.byron.trucaller.service.SmsReceiver { *; }
-keep class com.byron.trucaller.service.AntiUninstallReceiver { *; }
-keep class com.byron.trucaller.service.BootCompletedReceiver { *; }
-keep class com.byron.trucaller.service.GeofenceBroadcastReceiver { *; }
-keep class com.byron.trucaller.service.PhoneStateReceiver { *; }

# ── Services (Phase 6: geofence, family, recording, etc.) ────────────────
-keep class com.byron.trucaller.service.GeofencingService { *; }
-keep class com.byron.trucaller.service.CallRecordingService { *; }
-keep class com.byron.trucaller.service.LocationService { *; }
-keep class com.byron.trucaller.service.CallerIdOverlayService { *; }
-keep class com.byron.trucaller.service.TruCallerCallScreeningService { *; }
-keep class com.byron.trucaller.service.DeviceRegistrationService { *; }
-keep class com.byron.trucaller.service.DriveSyncService { *; }
-keep class com.byron.trucaller.service.AlarmSoundManager { *; }

# ── Security utilities ──────────────────────────────────────────────────
-keep class com.byron.trucaller.util.SecurityUtils { *; }

# ── Certificate Pinning (OkHttp) ─────────────────────────────────────────
-keep class okhttp3.CertificatePinner { *; }
-keep class okhttp3.CertificatePinner$Builder { *; }

# ── Compose ──────────────────────────────────────────────────────────────
-dontwarn androidx.compose.**

# ── Kotlin coroutines ────────────────────────────────────────────────────
-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.** { *; }

# ── OSMDroid (maps) ──────────────────────────────────────────────────────
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# ── Coil (image loading) ────────────────────────────────────────────────
-dontwarn coil3.**

# ── Suppress common warnings ────────────────────────────────────────────
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn java.lang.invoke.**
-dontwarn javax.annotation.**
-dontwarn sun.misc.Unsafe
