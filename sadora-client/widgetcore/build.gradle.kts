import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * What a home-screen widget and a watch show, and how they talk to the server.
 *
 * Its own module, without Compose, because three processes that are not the app link it:
 * the iOS widget extension (which iOS gives ~30 MB), the watchOS app and the Wear OS app.
 * The app links it too and writes the snapshot they all read, so a widget and the Today
 * screen never disagree about a number.
 */
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
        watchosArm64(),
        watchosDeviceArm64(),
        watchosSimulatorArm64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = "SadoraKit"
            isStatic = true
        }
    }

    android {
        namespace = "uz.sadora.widget"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            api("uz.sadora:contract")
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        appleMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
