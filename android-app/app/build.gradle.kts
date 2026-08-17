plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // Firebase — FCM push (§13) + Crashlytics (§11.6). google-services reads
    // app/google-services.json; Crashlytics uploads mapping files in release.
    alias(libs.plugins.google.services)
    alias(libs.plugins.crashlytics)
}

// Release signing — credentials live in keystore.properties (gitignored),
// never checked in. Missing file = unsigned release build.
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
    namespace = "com.chobgroup.rootnet"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.chobgroup.rootnet"
        minSdk = 23
        targetSdk = 36
        // v2.1 — File tab (Linky/File), slimmer cards, protocol labels, lock
        // overlay + rewarded-gated file/refresh flows, combined 3rd-tap
        // interstitial. Keep app_config in sync (latest_build=102,
        // latest_version=2.1.0) or the version gate misbehaves.
        versionCode = 102
        versionName = "2.1.0"
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
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

    testOptions {
        unitTests.isIncludeAndroidResources = false
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
    // Explicit modern fragment — Unity Ads drags in fragment 1.1.0 transitively,
    // which trips lint's InvalidFragmentVersionForActivityResult (needs ≥1.3.0).
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)

    // ── Compose (BOM) ──
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    // Core icons only (lightweight) — extended is ~40 MB of unused glyphs.
    implementation(libs.compose.material.icons.core)
    debugImplementation(libs.compose.ui.tooling)

    // ── Networking (Phase 1: pinned HTTP client) ──
    implementation(libs.okhttp)

    // ── Monetization (v2.2) — Adivery ONLY (Iranian ad network): interstitial
    //    (copy gate), rewarded video (export + refresh gate), banner. AdMob and
    //    Unity Ads were removed at the user's request. ──
    implementation(libs.adivery.sdk)

    // ── Firebase — Crashlytics only (FCM push was removed with the engine) ──
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)

    // ── Tests ──
    testImplementation(libs.junit)
    // Real org.json for local unit tests (Android stub otherwise throws)
    testImplementation(libs.org.json)
}
