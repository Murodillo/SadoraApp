plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    application
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.time.ExperimentalTime",
            "-opt-in=kotlin.uuid.ExperimentalUuidApi",
        )
    }
}

application {
    mainClass.set("uz.sadora.server.ApplicationKt")
}

dependencies {
    implementation(project(":contract"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.contentNegotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.authJwt)
    implementation(libs.ktor.server.statusPages)
    implementation(libs.ktor.server.callLogging)
    implementation(libs.ktor.server.callId)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.rateLimit)
    implementation(libs.ktor.server.defaultHeaders)
    implementation(libs.ktor.server.compression)
    implementation(libs.ktor.server.requestValidation)
    implementation(libs.ktor.server.configYaml)
    implementation(libs.ktor.server.swagger)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.contentNegotiation)

    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.kotlinDatetime)
    implementation(libs.exposed.json)
    implementation(libs.postgresql)
    implementation(libs.hikari)
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.postgresql)

    implementation(libs.lettuce)
    implementation(libs.bcrypt)
    implementation(libs.kotlinx.coroutines.core)
    runtimeOnly(libs.logback)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.ktor.server.testHost)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.logback)
}

tasks.test {
    useJUnitPlatform()
}
