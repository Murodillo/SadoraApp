import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * Sadora on Wear OS: an app, a Tile and complications, drawing from widgetcore like the
 * phone's widgets. The application id is the phone app's own — the Wearable Data Layer
 * only connects two apps with the same package and signing key.
 */
plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

android {
    namespace = "uz.sadora.wear"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "uz.sadora.app"
        // Wear OS 3, the first on which Play installs apps beside a phone's.
        minSdk = 30
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = (project.findProperty("sadora.versionCode") as String?)?.toInt() ?: 1
        versionName = (project.findProperty("sadora.versionName") as String?) ?: "1.0"
    }
    buildFeatures {
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
}

dependencies {
    implementation(project(":widgetcore"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.tiles)
    implementation(libs.concurrent.futures)
    implementation(libs.wear.protolayout)
    implementation(libs.wear.protolayout.material)
    implementation(libs.wear.complications)
    implementation(libs.play.wearable)
    implementation(libs.kotlinx.coroutines.play)
}
