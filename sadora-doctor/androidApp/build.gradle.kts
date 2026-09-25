import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.activity.compose)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

/**
 * Set with `-Psadora.devHost=<host>` when building for a physical device — the same knob
 * as sadora-client's.
 *
 * `localhost` is a phone reaching the backend through `adb reverse tcp:8080 tcp:8080`.
 * Otherwise prefer the machine's mDNS name over its address — `-Psadora.devHost=$(scutil
 * --get LocalHostName).local` on macOS — because a DHCP lease moves the IP and the name
 * follows it. A whole `https://` URL points the build at a tunnel. Empty means the
 * emulator, which reaches the host at 10.0.2.2:8080.
 */
val devHost: String = (project.findProperty("sadora.devHost") as String?).orEmpty()

/**
 * Set with `-Psadora.apiUrl=https://<host>` to point a release build somewhere other than
 * production — a test build aimed at staging. Empty means production.
 */
val apiUrl: String = (project.findProperty("sadora.apiUrl") as String?).orEmpty()

/** `-Psadora.versionCode=12 -Psadora.versionName=1.2.0`, so a release does not need a commit. */
val sadoraVersionCode: Int = (project.findProperty("sadora.versionCode") as String?)?.toInt() ?: 1
val sadoraVersionName: String = (project.findProperty("sadora.versionName") as String?) ?: "1.0"

android {
    namespace = "uz.sadora.doctor"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "uz.sadora.doctor"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = sadoraVersionCode
        versionName = sadoraVersionName

        // MainActivity reads these to pick the API: DEV_HOST in a debug build, API_URL in
        // a release one, each empty unless the build passed the property above.
        buildConfigField("String", "DEV_HOST", "\"$devHost\"")
        buildConfigField("String", "API_URL", "\"$apiUrl\"")
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        // MainActivity picks the API environment from DEBUG and DEV_HOST, and reports
        // VERSION_NAME to the backend, so the generated BuildConfig has to exist.
        buildConfig = true
    }
}
