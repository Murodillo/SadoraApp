// The wire-format contract as a standalone build. sadora-backend, sadora-client and
// sadora-doctor all include it with `includeBuild`, and depend on it as
// `uz.sadora:contract`; Gradle substitutes this project for the coordinate.
rootProject.name = "contract"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
    // The backend's catalog: the contract is the backend's API, and its Kotlin and
    // Android plugin versions must be the ones every including build uses too
    // (tools/ci/check_versions.py holds the three catalogs to that).
    versionCatalogs {
        create("libs") { from(files("../gradle/libs.versions.toml")) }
    }
}
