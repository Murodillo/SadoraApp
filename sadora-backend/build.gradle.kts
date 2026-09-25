plugins {
    // Declared once here so the subprojects share one classloader for them.
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.kover) apply false
}
