buildscript {
    dependencies {
        // Sube el KGP que trae AGP 9.3 (2.2.10) a 2.4.21; forma documentada para Kotlin integrado.
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.androidx.room) apply false
}
