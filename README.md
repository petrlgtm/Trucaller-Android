# TruCaller - Android & Backend

A comprehensive mobile security and communication platform featuring advanced Caller ID, SMS spam filtering, and robust anti-theft protection.

## 📱 Features

### User Features
- **Smart Caller ID**: Real-time identification of unknown callers with spam risk assessment.
- **SMS Messaging**: Integrated SMS client with automatic spam detection and warning system.
- **Anti-Theft Protection**: 
    - **Remote Actions**: Trigger alarms, lock devices, and track location remotely.
    - **Stolen Reports**: File and manage stolen device reports.
    - **Anti-Uninstall**: Uses Device Admin permissions to prevent unauthorized removal of the app.
- **Contact Management**: Seamlessly sync and manage your contacts with cloud backup.
- **Security Dashboard**: Monitor your device's security status and recent activity logs.

### Admin Features
- **Centralized Management**: Manage all registered devices and users from a dedicated dashboard.
- **Global Spam Database**: Update and maintain the master list of spam callers and SMS numbers.
- **Forensic Logs**: Access detailed IP logs, alarm history, and stolen reports for device recovery.
- **System Settings**: Configure global application parameters and security policies.

## 🏗 Architecture

### Android App
- **Pattern**: MVVM (Model-View-ViewModel)
- **UI Framework**: Jetpack Compose for a modern, reactive interface.
- **Local Persistence**: Room Database with 11+ entities for offline-first capability.
- **Dependency Injection**: Custom `AppContainer` for clean dependency management.
- **Background Processing**: Services and Broadcast Receivers for real-time call/SMS monitoring.

### Backend
- **Framework**: Ktor (Kotlin)
- **Database**: MongoDB for scalable, document-oriented storage.
- **Authentication**: JWT-based security with Firebase Phone Auth integration.
- **Real-time**: Firebase Cloud Messaging (FCM) for remote device control.

## 🛠 Tech Stack

**Frontend (Android):**
- Language: Kotlin
- UI: Jetpack Compose
- Networking: Retrofit & OkHttp
- Local DB: Room
- Security: DevicePolicyManager (Device Admin)
- Location: Google Play Services Location API
- Auth: Firebase Phone Authentication

**Backend:**
- Language: Kotlin
- Framework: Ktor
- Database: MongoDB
- Server: Netty
- Serialization: Kotlinx Serialization
- Logging: Logback

## 📂 Project Structure

```text
├── app/                # Android Application
│   ├── src/main/java/  # Source code (MVVM layers)
│   ├── src/main/res/   # UI resources
│   └── build.gradle.kts
├── backend/            # Ktor Backend
│   ├── src/main/kotlin/# API routes, Auth, and Data logic
│   ├── src/resources/  # Configuration and static assets
│   └── Dockerfile      # Containerization support
├── gradle/             # Project-wide dependencies
└── build.gradle.kts    # Root build configuration
```

## 🚀 Getting Started

### Android App
1. Clone the repository.
2. Open in Android Studio (Ladybug or newer).
3. Add your `google-services.json` to the `app/` directory.
4. Sync Gradle and run on a device with API 26 (Android 8.0) or higher.

### Backend
1. Navigate to the `backend/` directory.
2. Configure your `mongo.uri` and JWT settings in `application.conf`.
3. Run using `./gradlew run` or use the provided `Dockerfile`.
4. (Optional) Use `deploy.sh` for automated deployment.

---
*Developed as a high-security communication utility for the modern mobile landscape.*
