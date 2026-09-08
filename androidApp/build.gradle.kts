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
 * Set with `-Psadora.devHost=<host>` when building for a physical device.
 *
 * Prefer the machine's mDNS name over its address — `-Psadora.devHost=$(scutil --get
 * LocalHostName).local` on macOS. A DHCP lease renews and the IP moves, and the APK on
 * the phone then talks to a machine that is no longer there; the name follows it.
 */
val devHost: String = (project.findProperty("sadora.devHost") as String?).orEmpty()

android {
    namespace = "uz.sadora.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "uz.sadora.app"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"

        // A debug build normally talks to 10.0.2.2, which only exists inside the
        // emulator. Pass -Psadora.devHost=<host> to point it at this machine over Wi-Fi
        // instead, so the APK also works on a physical phone. Empty means "emulator".
        buildConfigField("String", "DEV_HOST", "\"$devHost\"")
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
        // MainActivity picks the API environment from DEBUG and reports VERSION_NAME to
        // the backend, so the generated BuildConfig has to exist.
        buildConfig = true
    }
}