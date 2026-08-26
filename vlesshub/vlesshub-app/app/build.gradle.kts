plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Release signing — dedicated VlessHub keystore. Credentials live in
// keystore.properties (gitignored) at the vlesshub-app root; storeFile points
// at keystore/vlesshub-release.jks. Missing file = unsigned release build.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties: Map<String, String> = if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.readLines()
        .filter { it.contains('=') && !it.trimStart().startsWith("#") }
        .associate { line ->
            val (k, v) = line.split("=", limit = 2)
            k.trim() to v.trim()
        }
} else {
    emptyMap()
}

android {
    namespace = "com.chobgroup.vlesshub"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.chobgroup.vlesshub"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getValue("storeFile"))
                storePassword = keystoreProperties.getValue("storePassword")
                keyAlias = keystoreProperties.getValue("keyAlias")
                keyPassword = keystoreProperties.getValue("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // ── Core / lifecycle ──
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

    // ── Compose (BOM) ──
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    debugImplementation(libs.compose.ui.tooling)

    // ── Monetization — Adivery ONLY (rewarded video refresh gate + banner).
    //    AdMob removed; placement IDs pending from the user. ──
    implementation(libs.adivery.sdk)
    implementation(libs.okhttp)
}
