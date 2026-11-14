plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
}

apply(from = rootProject.file("gradle/release-signing.gradle.kts"))
@Suppress("UNCHECKED_CAST")
val releaseKeystore = extra["releaseKeystore"] as Map<String, String>?

android {
    namespace = "com.askew137.gcwatch.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.askew137.gcwatch"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        releaseKeystore?.let { ks ->
            create("release") {
                storeFile = file(ks.getValue("file"))
                storePassword = ks.getValue("storePassword")
                keyAlias = ks.getValue("keyAlias")
                keyPassword = ks.getValue("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Falls back to the debug key when no release keystore is configured.
            signingConfig = signingConfigs.getByName(if (releaseKeystore != null) "release" else "debug")
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Compose BOM
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.extended)

    // Lifecycle + Activity
    implementation(libs.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.core.ktx)

    // Wearable
    implementation(libs.play.services.wearable)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play)
    implementation(libs.androidx.navigation.compose)

    // Serialization
    implementation(libs.kotlinx.serialization.proto)

    // HTTP (GitHub update check)
    implementation(libs.okhttp)

    // Logging
    implementation(libs.timber)

    // Offline routing (vendored BRouter, MIT – third_party/brouter)
    implementation(project(":brouter-core"))

    // Core modules
    implementation(project(":core:model"))
    implementation(project(":core:communication"))

    testImplementation(libs.junit)

    debugImplementation(libs.compose.ui.tooling.preview)
}
