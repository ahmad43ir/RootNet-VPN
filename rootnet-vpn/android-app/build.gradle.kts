// RootNet clone — root build file
// Spec §14: AGP 9.0.1, Kotlin 2.3.20, Gradle 9.1.0, compileSdk 36.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // Firebase: google-services (FCM) + Crashlytics — spec §14 pins 4.5.0 / 3.0.7
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.crashlytics) apply false
}
