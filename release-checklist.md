# Release Checklist - TruCaller v1.0.0

## Pre-Release

### Code Quality
- [ ] All unit tests pass (`./gradlew test`)
- [ ] All instrumented tests pass (`./gradlew connectedAndroidTest`)
- [ ] No lint errors (`./gradlew lint`)
- [ ] ProGuard/R8 rules verified (no runtime crashes in release build)
- [ ] No hardcoded secrets in source code
- [ ] API base URL points to production in release buildType

### Build Configuration
- [ ] `versionCode` incremented (currently: 1)
- [ ] `versionName` set to "1.0.0"
- [ ] `minSdk` set to 29 (Android 10)
- [ ] `targetSdk` set to 35
- [ ] Release signing config uses keystore.properties (not hardcoded)
- [ ] `isMinifyEnabled = true` for release
- [ ] `isShrinkResources = true` for release
- [ ] `buildConfig` feature enabled
- [ ] `LOG_HTTP_REQUESTS` set to `false` in release

### Signing
- [ ] Release keystore file (`release-keystore.jks`) is secure and backed up
- [ ] `keystore.properties` file is present and NOT committed to git
- [ ] Keystore password, key alias, and key password are documented securely
- [ ] APK/AAB is signed with the release key

### Backend
- [ ] Backend deployed to production (Render)
- [ ] MongoDB production database is provisioned and indexed
- [ ] JWT secret is set via environment variable (not default)
- [ ] CORS is restricted to production domains
- [ ] FCM service account key is configured
- [ ] DELETE /api/auth/delete-account endpoint is deployed
- [ ] Privacy policy and terms of service pages are accessible

## Build & Test

### Release Build
- [ ] Generate release AAB: `./gradlew bundleRelease`
- [ ] Generate release APK: `./gradlew assembleRelease`
- [ ] Test release APK on physical device
- [ ] Verify caller ID lookup works
- [ ] Verify login/register flow
- [ ] Verify contact sync
- [ ] Verify spam reporting
- [ ] Verify anti-theft features (stolen report, remote alarm)
- [ ] Verify family group creation and alerts
- [ ] Verify call recording (if enabled)
- [ ] Verify account deletion flow
- [ ] Verify deep links and navigation

### Compatibility
- [ ] Test on Android 10 (API 29)
- [ ] Test on Android 14 (API 34)
- [ ] Test on Android 15 (API 35)
- [ ] Test on small screen (5-inch)
- [ ] Test on large screen (6.7-inch)
- [ ] Test in dark mode
- [ ] Test in light mode

## Store Listing

### Assets
- [ ] App icon (512x512) finalized
- [ ] Feature graphic (1024x500) created
- [ ] Phone screenshots captured (minimum 2, recommended 8)
- [ ] Short description (80 chars max) reviewed
- [ ] Full description reviewed
- [ ] Changelog for version 1 written

### Play Console
- [ ] App category set (Communication)
- [ ] Content rating questionnaire completed
- [ ] Target audience and content declarations filled
- [ ] Data safety form completed (see store-listing/data-safety/README.md)
- [ ] Privacy policy URL set (https://trucaller.co.ug/privacy-policy)
- [ ] Contact email set (admin@trucaller.co.ug)

### Legal
- [ ] Privacy policy published and accessible at /privacy-policy
- [ ] Terms of service published and accessible at /terms-of-service
- [ ] Account deletion mechanism available (DELETE /api/auth/delete-account)
- [ ] Data safety declarations match actual data collection
- [ ] GDPR/data portability rights documented

## Post-Release

- [ ] Monitor crash reports (Firebase Crashlytics / Play Console)
- [ ] Monitor ANR rate
- [ ] Monitor user reviews and ratings
- [ ] Verify backend performance under load
- [ ] Backup release keystore and credentials securely
- [ ] Tag release in git: `git tag v1.0.0`
