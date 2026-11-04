plugins {
    alias(libs.plugins.android.application)
    // Sin org.jetbrains.kotlin.android: AGP 9.x compila Kotlin de forma nativa (Kotlin integrado).
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

android {
    namespace = "com.cuentasclaras.app"
    compileSdk = 37 // resuelve a platforms;android-37.0

    defaultConfig {
        applicationId = "com.cuentasclaras.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        manifestPlaceholders["etiquetaApp"] = "Cuentas Claras"
    }

    // Firma de release: las cuatro propiedades viven en ~/.gradle/gradle.properties (nunca en el repo).
    // Sin ellas, el APK release sale sin firmar (sirve para comprobar que compila, no para instalar).
    val firmaRelease = listOf("CUENTAS_KEYSTORE", "CUENTAS_KEYSTORE_PASS", "CUENTAS_KEY_ALIAS", "CUENTAS_KEY_PASS")
        .mapNotNull { k -> (project.findProperty(k) as String?)?.let { k to it } }.toMap()
        .takeIf { it.size == 4 }
    if (firmaRelease != null) {
        signingConfigs {
            create("release") {
                storeFile = file(firmaRelease.getValue("CUENTAS_KEYSTORE"))
                storePassword = firmaRelease.getValue("CUENTAS_KEYSTORE_PASS")
                keyAlias = firmaRelease.getValue("CUENTAS_KEY_ALIAS")
                keyPassword = firmaRelease.getValue("CUENTAS_KEY_PASS")
            }
        }
    }

    buildTypes {
        // Build para verificaciones por adb: otro paquete y otra etiqueta, para no tocar los datos reales de la app normal.
        create("verif") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".verif"
            matchingFallbacks += listOf("debug")
            manifestPlaceholders["etiquetaApp"] = "Cuentas Claras (verif)"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (firmaRelease != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
}
