import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

/**
 * Firebase's description of this app — project, sender id, API key — which the
 * google-services plugin turns into resources the messaging library reads at start.
 *
 * The file is gitignored and the plugin applied only when it is there, so a fresh clone
 * and CI still build. Such an APK simply has no push: `PushRegistration` finds no
 * Firebase app at runtime and skips the token rather than crashing.
 */
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.googleServices.get().pluginId)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    // Analytics collection starts off (see the manifest) and follows her consent.
    implementation(libs.firebase.analytics)

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

/**
 * Set with `-Psadora.apiUrl=https://<host>` to point a release build somewhere other than
 * production — a Play internal-testing build aimed at staging while the production
 * domain does not exist yet. Empty means production.
 */
val apiUrl: String = (project.findProperty("sadora.apiUrl") as String?).orEmpty()

/**
 * The upload key, read from `androidApp/keystore.properties` when that file exists.
 *
 * The file is gitignored: a signing key in the repository is a signing key anyone who
 * clones it can publish with. When it is absent — a laptop that never ships, CI running
 * the debug build — the release type is simply left unsigned rather than failing the
 * build, because Play refuses an unsigned upload anyway and a broken `assembleDebug` on
 * every fresh clone would cost more than it saves.
 *
 * Expected keys: `storeFile` (path, relative to androidApp/), `storePassword`,
 * `keyAlias`, `keyPassword`.
 */
val keystoreProperties: Properties? = file("keystore.properties")
    .takeIf { it.exists() }
    ?.let { file -> Properties().apply { file.inputStream().use(::load) } }

/**
 * The key debug builds are signed with, when `-Psadora.debugKeystore=<path>` names one.
 *
 * CI's staging APK has to be signed by the same key every time, or a phone refuses to
 * update the installed build. The Android Gradle Plugin's own lookup of debug.keystore
 * depends on environment variables a hosted runner sets differently, and silently makes
 * a fresh key when it finds none — so staging names the file outright. It is a debug key
 * with the standard debug credentials; the upload key above is a different matter.
 */
val debugKeystore: File? = (project.findProperty("sadora.debugKeystore") as String?)
    ?.takeIf { it.isNotBlank() }
    ?.let { path -> file(path).also { require(it.isFile) { "sadora.debugKeystore: no file at $path" } } }

/**
 * Version, overridable from the command line so a release does not need a commit:
 * `-Psadora.versionCode=12 -Psadora.versionName=1.2.0`. Play rejects a versionCode it
 * has already seen, so it is the one number that has to move on every upload.
 */
val sadoraVersionCode: Int = (project.findProperty("sadora.versionCode") as String?)?.toInt() ?: 1
val sadoraVersionName: String = (project.findProperty("sadora.versionName") as String?) ?: "1.0"

android {
    namespace = "uz.sadora.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "uz.sadora.app"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = sadoraVersionCode
        versionName = sadoraVersionName

        // A debug build normally talks to 10.0.2.2, which only exists inside the
        // emulator. Pass -Psadora.devHost=<host> to point it at this machine over Wi-Fi
        // instead, so the APK also works on a physical phone. Empty means "emulator".
        buildConfigField("String", "DEV_HOST", "\"$devHost\"")
        buildConfigField("String", "API_URL", "\"$apiUrl\"")
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    signingConfigs {
        if (debugKeystore != null) {
            getByName("debug") {
                storeFile = debugKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
        if (keystoreProperties != null) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            // R8 both shrinks and obfuscates. The rules it needs are in
            // proguard-rules.pro, and `assembleRelease` is what proves them — a
            // reflection-driven serializer that R8 stripped fails at runtime, not here.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
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