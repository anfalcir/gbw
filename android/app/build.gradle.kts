val releaseStorePath = System.getenv("GBW_RELEASE_KEYSTORE_PATH")
val releaseStorePassword = System.getenv("GBW_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = System.getenv("GBW_RELEASE_KEY_ALIAS")
val releaseKeyPassword = System.getenv("GBW_RELEASE_KEY_PASSWORD")
val releaseSigningAvailable =
    listOf(releaseStorePath, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
        .all { !it.isNullOrBlank() }

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.gbw.android"
    compileSdk = 37
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.gbw.android"
        minSdk = 28
        targetSdk = 36
        versionCode = 23
        versionName = "6.0.0-rc2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    signingConfigs {
        create("homologation") {
            storeFile = file("gbw-homologation.p12")
            storePassword = "gbw-homologation"
            keyAlias = "gbw-homologation"
            keyPassword = "gbw-homologation"
            storeType = "PKCS12"
        }
        if (releaseSigningAvailable) {
            create("production") {
                storeFile = file(requireNotNull(releaseStorePath))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
                storeType = "PKCS12"
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("homologation")
        }
        release {
            signingConfig = signingConfigs.findByName("production")
            isMinifyEnabled = false
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

    buildFeatures {
        compose = true
        buildConfig = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
        jniLibs.useLegacyPackaging = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")

    // Audio-only maintained FFmpegKit fork. Pinned; no dynamic versions.
    implementation("dev.ffmpegkit-maintained:ffmpeg-kit-audio:8.1.7")
    // FFmpegKitConfig initializes Smart Exception classes at runtime. The alpha6
    // APK proved that the maintained artifact metadata did not place these classes
    // in the final DEX, so keep both runtime jars explicit and verify them in CI.
    implementation("com.arthenica:smart-exception-java:0.2.1")
    implementation("com.arthenica:smart-exception-common:0.2.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
