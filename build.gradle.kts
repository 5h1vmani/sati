// Root build file. It declares the plugin versions once and applies nothing.
// AGP 9 compiles Kotlin itself, so there is no kotlin-android plugin.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
