// VlessHub â€” root build file.
// Same toolchain as RootNet: AGP 9.0.1, Kotlin 2.3.20, Gradle 9.1.0, compileSdk 36.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
