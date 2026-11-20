plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.nobi.nobistream2"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nobi.nobistream2"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.1"
        // Telegram API credentials are injected ONLY from CI secrets at build
        // time (env NOBISTREAM2_API_ID / NOBISTREAM2_API_HASH). They are never
        // written in source. Without them the fields are 0 / empty and the
        // user enters the credentials in the app's Settings screen instead.
        val telegramApiId = System.getenv("NOBISTREAM2_API_ID")?.toIntOrNull() ?: 0
        val telegramApiHash = System.getenv("NOBISTREAM2_API_HASH") ?: ""
        buildConfigField("int", "TELEGRAM_API_ID", telegramApiId.toString())
        buildConfigField(
            "String",
            "TELEGRAM_API_HASH",
            "\"" + telegramApiHash.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        )
    }

    signingConfigs {
        getByName("debug") {
            val keystorePath = System.getenv("NOBISTREAM2_KEYSTORE_FILE")
            if (!keystorePath.isNullOrBlank()) {
                val keystoreFile = file(keystorePath)
                if (keystoreFile.exists()) {
                    storeFile = keystoreFile
                    storeType = "PKCS12"
                    storePassword = System.getenv("NOBISTREAM2_KEYSTORE_PASSWORD") ?: ""
                    keyAlias = System.getenv("NOBISTREAM2_KEY_ALIAS") ?: "nobistream2"
                    keyPassword = System.getenv("NOBISTREAM2_KEY_PASSWORD") ?: ""
                }
            }
        }
        create("release") {
            val keystorePath = System.getenv("NOBISTREAM2_KEYSTORE_FILE")
            if (!keystorePath.isNullOrBlank()) {
                val keystoreFile = file(keystorePath)
                if (keystoreFile.exists()) {
                    storeFile = keystoreFile
                    storeType = "PKCS12"
                    storePassword = System.getenv("NOBISTREAM2_KEYSTORE_PASSWORD") ?: ""
                    keyAlias = System.getenv("NOBISTREAM2_KEY_ALIAS") ?: "nobistream2"
                    keyPassword = System.getenv("NOBISTREAM2_KEY_PASSWORD") ?: ""
                }
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
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

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.material)

    // Jetpack Compose (BOM-managed) + Material 3 Expressive styling.
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.navigation.compose)

    // Media3 (ExoPlayer) for video playback.
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.common)

    // Room for playback positions, saved groups, Watch Later, downloads.
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore for settings, EncryptedSharedPreferences for api_id / api_hash.
    implementation(libs.datastore.preferences)
    implementation(libs.security.crypto)

    // Image loading for video thumbnails from local files.
    implementation(libs.coil)
    implementation(libs.coil.compose)

    implementation(libs.kotlinx.coroutines.android)

    // TDLib (MTProto) prebuilt AARs from Maven Central.
    // TDLib 1.8.64, all 4 ABIs, 16KB page-size ready. No local NDK build needed.
    implementation(libs.tdlib.core)
    implementation(libs.tdlib.ktx)

    testImplementation(libs.junit)
}
