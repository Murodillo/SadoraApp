rootProject.name = "Sadora"

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
}

include(":contract")
include(":server")

// The server image copies only `contract` and `server` into its build stage. The mobile
// modules are included only where their sources are present, so a backend build never
// has to configure the Android plugin — which needs an SDK the image does not have.
if (file("shared").isDirectory) include(":shared")
if (file("androidApp").isDirectory) include(":androidApp")