import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.compose")
}

// Release signing is read from a gitignored android/keystore.properties (so the repo builds
// without secrets; release builds are signed only when that file + the keystore are present).
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) FileInputStream(keystorePropsFile).use { load(it) }
}

kotlin {
    androidTarget {
        compilations.all { kotlinOptions { jvmTarget = "17" } }
    }
    jvm("desktop") {
        compilations.all { kotlinOptions { jvmTarget = "17" } }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(compose.ui)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
            }
        }
        // Shared by BOTH JVM targets (android + desktop): app logic + UI that uses
        // JVM-only libs (OkHttp, JSch, org.json). expect/actual platform glue lives in
        // commonMain (above) with actuals under androidMain / desktopMain.
        val jvmShared by creating {
            dependsOn(commonMain)
            dependencies {
                implementation("com.squareup.okhttp3:okhttp:4.12.0")
                implementation("com.github.mwiede:jsch:0.2.18")
                implementation("com.halilibo.compose-richtext:richtext-commonmark:0.20.0")
                implementation("com.halilibo.compose-richtext:richtext-ui-material3:0.20.0")
            }
        }
        val androidMain by getting {
            dependsOn(jvmShared)
            dependencies {
                implementation("androidx.core:core-ktx:1.13.1")
                implementation("androidx.appcompat:appcompat:1.7.0")
                implementation("com.google.android.material:material:1.12.0") // XML app theme only
                implementation("androidx.activity:activity-compose:1.9.2")
                implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
                implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
            }
        }
        val desktopMain by getting {
            dependsOn(jvmShared)
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
                implementation("org.json:json:20240303")
            }
        }
    }
}

android {
    namespace = "com.openclaw.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.openclaw.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 10
        versionName = "1.1"
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        if (keystorePropsFile.exists()) create("release") {
            storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
            storePassword = keystoreProps.getProperty("storePassword")
            keyAlias = keystoreProps.getProperty("keyAlias")
            keyPassword = keystoreProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (keystorePropsFile.exists()) signingConfig = signingConfigs.getByName("release")
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.openclaw.app.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "ClawNest"
            packageVersion = "1.0.0"
        }
    }
}
