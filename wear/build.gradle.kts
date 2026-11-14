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
    namespace = "com.askew137.gcwatch.wear"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.askew137.gcwatch"
        minSdk = 30
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
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    // Wear OS Compose
    implementation(libs.wear.compose.material)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.navigation)

    // Horologist
    implementation(libs.horologist.compose.layout)
    implementation(libs.horologist.compose.material)

    // Mapsforge
    implementation(libs.mapsforge.map.android)
    implementation(libs.mapsforge.themes)

    // Wearable
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Serialization
    implementation(libs.kotlinx.serialization.proto)
    implementation(libs.kotlinx.serialization.json)

    // Logging
    implementation(libs.timber)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.compose)

    // Core
    implementation(libs.androidx.core.ktx)

    // Activity / Compose
    implementation(libs.activity.compose)

    // Material icons extended (for Rounded icons)
    implementation(libs.compose.icons.extended)

    // Internal modules
    implementation(project(":core:model"))
    implementation(project(":core:communication"))
}
