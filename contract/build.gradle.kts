import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * Wire-format contract shared by the mobile apps and the Ktor backend.
 *
 * This module holds DTOs and nothing else — no Compose, no Ktor, no database types.
 * Both sides compile against the same declarations, so a field the backend renames
 * breaks the mobile build instead of failing at runtime.
 */
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm()

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Contract"
            isStatic = true
        }
    }

    android {
        namespace = "uz.sadora.contract"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
