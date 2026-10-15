# Versiones fijadas: app Android Kotlin + Compose (Gradle 9.1.0)

Fecha: 2026-10-15. Rama `nativa-compose`.

**Conclusión principal.** Gradle 9.1.0 solo admite **AGP 9.0.x** (AGP 9.1 ya exige Gradle 9.3.1). AGP 9.0 soporta como máximo la API 36.1. Por eso hay que quedarse en las líneas de AndroidX que todavía compilan contra API 36. Las más nuevas (Compose 1.12, Lifecycle 2.11, Navigation 2.10, Core 1.19, OkHttp 5.5) declaran en su AAR `minCompileSdk=37` y, en el caso de AndroidX, `minAndroidGradlePluginVersion=9.1.0`, así que no sirven con Gradle 9.1.0.

**Verificado empíricamente.** Monté un proyecto de prueba con exactamente esta configuración en el scratchpad. Lo compilé con la Gradle 9.1.0 de la caché del wrapper y el JBR 21 de Android Studio:

- `:app:assembleDebug` terminó con BUILD SUCCESSFUL.
- `:app:testDebugUnitTest` pasó 3 de 3 tests con Robolectric: Room en memoria vía KSP, kotlinx.serialization y un `createComposeRule`.
- El plugin Room exportó `schemas/.../1.json`.

El primer intento con OkHttp 5.5.0 falló en `checkDebugAarMetadata` porque `okhttp-android:5.5.0` exige compileSdk 37. Por eso se fija OkHttp 5.4.0.

---

## 1. Resumen de versiones

| Componente | Versión | Coordenadas / id | Fuente | Nota de compatibilidad |
|---|---|---|---|---|
| Gradle | **9.1.0** (`-all`) | wrapper | `~/.gradle/wrapper/dists/gradle-9.1.0-all/7wzd0jkjit61aq2p43wpjgij9/` (el hash coincide con el MD5 base 36 de `https://services.gradle.org/distributions/gradle-9.1.0-all.zip`) | Ya está en caché |
| AGP | **9.0.1** | `com.android.tools.build:gradle:9.0.1` / `com.android.application` | [about-agp](https://developer.android.com/build/releases/about-agp): «9.0 → Gradle mínima 9.1.0», «9.1 → 9.3.1». [AGP 9.0.1 notes](https://developer.android.com/build/releases/agp-9-0-0-release-notes): «maximum API level … 36.1», build tools 36.0.0, JDK 17, KGP 2.2.10. Ya en caché: `~/.gradle/caches/modules-2/files-2.1/com.android.tools.build/gradle/9.0.1` | Es el último AGP estable compatible con Gradle 9.1.0. Android Studio 2026.1.2 (Quail 2, `dataDirectoryName: AndroidStudio2026.1.2` en `product-info.json`) admite AGP 7.1–9.3 ([about-agp](https://developer.android.com/build/releases/about-agp)) |
| Kotlin (KGP) | **2.3.20** | `org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.20` | [Tabla KGP](https://kotlinlang.org/docs/gradle-configure-project.html): 2.3.20–2.3.21 → Gradle 7.6.3–9.3.0, AGP 8.2.2–9.0.0. Ya en caché (`org.jetbrains.kotlin/kotlin-gradle-plugin/2.3.20`) | Built-in Kotlin de AGP 9.0: **no** se aplica `org.jetbrains.kotlin.android` (ver §3) |
| Compose compiler plugin | **2.3.20** | `org.jetbrains.kotlin.plugin.compose` | Igual versión que Kotlin. Verificado: `compose-compiler-gradle-plugin:2.3.20` en `buildEnvironment` | Se sigue aplicando aunque haya built-in Kotlin |
| Serialization plugin | **2.3.20** | `org.jetbrains.kotlin.plugin.serialization` | Igual versión que Kotlin | Se sigue aplicando |
| KSP | **2.3.12** | `com.google.devtools.ksp` (`com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.12`) | [Maven Central metadata](https://repo1.maven.org/maven2/com/google/devtools/ksp/com.google.devtools.ksp.gradle.plugin/maven-metadata.xml) (release 2.3.12). [KSP 2.3.0 release](https://github.com/google/ksp/releases/tag/2.3.0): «KSP version is no longer tied to the Kotlin compiler version». 2.3.1 añadió soporte para AGP 9.0 y built-in Kotlin; 2.3.10 corrigió las clases R con built-in Kotlin | Desde 2.3.0 el formato `<kotlin>-<ksp>` desapareció. AGP 9.0 sube cualquier KSP inferior a 2.2.10-2.0.2. Verificado: `symbol-processing-gradle-plugin:2.2.10-2.0.2 -> 2.3.12` |
| Compose BOM | **2026.06.01** | `androidx.compose:compose-bom:2026.06.01` → ui/foundation/runtime 1.11.4, material3 1.4.0 | [POM del BOM](https://dl.google.com/android/maven2/androidx/compose/compose-bom/2026.06.01/compose-bom-2026.06.01.pom). AAR `ui-android:1.11.4`: `minCompileSdk=35`, `minAGP=8.6.0` | 2026.08.00 y 2026.09.00 traen Compose 1.12.x, que exige `minCompileSdk=37` y `minAGP=9.1.0`, así que quedan descartados |
| Room | **2.8.5** | `androidx.room:room-runtime|room-ktx|room-compiler|room-testing:2.8.5`, plugin `androidx.room` | [maven-metadata](https://dl.google.com/android/maven2/androidx/room/room-runtime/maven-metadata.xml) (release 2.8.5). AAR `room-runtime-android:2.8.5`: `minCompileSdk=34` | Estable más reciente. `room-compiler` por KSP verificado |
| Navigation Compose | **2.9.8** | `androidx.navigation:navigation-compose:2.9.8` | [maven-metadata](https://dl.google.com/android/maven2/androidx/navigation/navigation-compose/maven-metadata.xml). AAR 2.9.8: `minCompileSdk=35`, `minAGP=8.6.0` | 2.10.x exige compileSdk 37 y AGP 9.1.0 |
| activity-compose | **1.13.0** | `androidx.activity:activity-compose:1.13.0` | [maven-metadata](https://dl.google.com/android/maven2/androidx/activity/activity-compose/maven-metadata.xml). AAR: `minCompileSdk=36`, `minAGP=8.9.1` | Obliga a usar **compileSdk 36** |
| Lifecycle | **2.10.0** | `androidx.lifecycle:lifecycle-runtime-ktx|lifecycle-runtime-compose|lifecycle-viewmodel-compose:2.10.0` | [maven-metadata](https://dl.google.com/android/maven2/androidx/lifecycle/lifecycle-viewmodel-compose/maven-metadata.xml). AAR `*-compose-android:2.10.0`: `minCompileSdk=35`, `minAGP=8.6.0` | 2.11.0 exige compileSdk 37 y AGP 9.1.0 |
| core-ktx | **1.18.0** | `androidx.core:core-ktx:1.18.0` | [maven-metadata](https://dl.google.com/android/maven2/androidx/core/core-ktx/maven-metadata.xml). AAR `core:1.18.0`: `minCompileSdk=36`, `minAGP=8.9.1` | 1.19.x exige compileSdk 37 y AGP 9.1.0 |
| DataStore Preferences | **1.2.1** | `androidx.datastore:datastore-preferences:1.2.1` | [maven-metadata](https://dl.google.com/android/maven2/androidx/datastore/datastore-preferences/maven-metadata.xml) (1.3.0 sigue en rc01). AAR: `minCompileSdk=34` | Estable más reciente |
| kotlinx.serialization-json | **1.11.0** | `org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0` | [Release v1.11.0](https://github.com/Kotlin/kotlinx.serialization/releases/tag/v1.11.0): «based on Kotlin 2.3.20» | Coincide exactamente con Kotlin 2.3.20. La 1.12.0-RC se basa en Kotlin 2.4.10 |
| kotlinx-coroutines | **1.11.0** | `org.jetbrains.kotlinx:kotlinx-coroutines-core|-android|-test:1.11.0` | [Maven Central metadata](https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-core/maven-metadata.xml). El POM `-core-jvm:1.11.0` depende de kotlin-stdlib 2.2.20 | Compatible con Kotlin 2.3.20 |
| OkHttp | **5.4.0** | `com.squareup.okhttp3:okhttp:5.4.0` (+ `mockwebserver3:5.4.0` para tests) | [Maven Central metadata](https://repo1.maven.org/maven2/com/squareup/okhttp3/okhttp/maven-metadata.xml). AAR `okhttp-android:5.4.0`: `minCompileSdk=36`; `okhttp-android:5.5.0`: `minCompileSdk=37` | 5.5.0 rompe `checkDebugAarMetadata` con AGP 9.0.1 (probado) |
| Robolectric | **4.17** | `org.robolectric:robolectric:4.17` | [Release 4.17](https://github.com/robolectric/robolectric/releases/tag/robolectric-4.17): «supports SDK 37» | Cubre targetSdk 35 y compileSdk 36. Con JDK 21 funciona (verificado) |
| androidx.test core | **1.7.0** | `androidx.test:core:1.7.0` (`core-ktx` opcional) | [maven-metadata](https://dl.google.com/android/maven2/androidx/test/core/maven-metadata.xml) (la 1.8.0 está en alpha01) | — |
| androidx.test.ext junit | **1.3.0** | `androidx.test.ext:junit:1.3.0` | [maven-metadata](https://dl.google.com/android/maven2/androidx/test/ext/junit/maven-metadata.xml) | Aporta el runner `AndroidJUnit4` para Robolectric |
| JUnit 4 | **4.13.2** | `junit:junit:4.13.2` | [Maven Central metadata](https://repo1.maven.org/maven2/junit/junit/maven-metadata.xml) | — |
| kotlin-test | **2.3.20** | `org.jetbrains.kotlin:kotlin-test` (ligado a `kotlin`) | Misma versión que Kotlin | Built-in Kotlin elige la variante JUnit 4 automáticamente (verificado) |
| compileSdk / minSdk / targetSdk | **36 / 26 / 35** | — | AGP 9.0 soporta como máximo 36.1. `activity 1.13.0` y `core 1.18.0` exigen como mínimo 36. Platform 36 y build-tools 36.0.0 ya están instalados | No se descarga ningún SDK |
| JDK | 21 (JBR) para ejecutar Gradle; bytecode Java/Kotlin **17** | — | AGP 9.0: JDK mínimo 17 | Verificado con `C:\Program Files\Android\Android Studio\jbr` |

**Evidencia local adicional:**

- `C:\Program Files\Android\Android Studio\build.txt` contiene `AI-261.25134.95.2612.15822958`.
- `plugins/android/lib/libagp-version.jar!/com/android/version.properties` trae `buildVersion = 9.3.0` y `lastStableBuildVersion = 9.2.1`. Esa es la AGP que conoce y propondría el asistente de esta build, y con Gradle 9.1.0 no funciona (ver §5).
- El plugin Kotlin del IDE es 2.3.10 (`plugins/Kotlin/kotlinc/build.txt`), así que el IDE entiende sin problema el código Kotlin 2.3.20.

---

## 2. `gradle/libs.versions.toml`

```toml
[versions]
agp = "9.0.1"
kotlin = "2.3.20"
ksp = "2.3.12"
composeBom = "2026.06.01"
room = "2.8.5"
navigationCompose = "2.9.8"
activityCompose = "1.13.0"
lifecycle = "2.10.0"
coreKtx = "1.18.0"
kotlinxSerializationJson = "1.11.0"
kotlinxCoroutines = "1.11.0"
okhttp = "5.4.0"
datastore = "1.2.1"
robolectric = "4.17"
androidxTestCore = "1.7.0"
androidxTestExtJunit = "1.3.0"
junit = "4.13.2"

[libraries]
# Plugin de Kotlin en el classpath del buildscript (forma documentada por AGP 9.0 para subir KGP sobre 2.2.10)
kotlin-gradle-plugin = { group = "org.jetbrains.kotlin", name = "kotlin-gradle-plugin", version.ref = "kotlin" }

androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }

androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
androidx-compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }

androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
androidx-room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }

androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }

kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerializationJson" }
kotlinx-coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "kotlinxCoroutines" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "kotlinxCoroutines" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "kotlinxCoroutines" }

okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
okhttp-mockwebserver = { group = "com.squareup.okhttp3", name = "mockwebserver3", version.ref = "okhttp" }

junit = { group = "junit", name = "junit", version.ref = "junit" }
kotlin-test = { group = "org.jetbrains.kotlin", name = "kotlin-test", version.ref = "kotlin" }
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }
androidx-test-core = { group = "androidx.test", name = "core", version.ref = "androidxTestCore" }
androidx-test-core-ktx = { group = "androidx.test", name = "core-ktx", version.ref = "androidxTestCore" }
androidx-test-ext-junit = { group = "androidx.test.ext", name = "junit", version.ref = "androidxTestExtJunit" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
# NO declarar org.jetbrains.kotlin.android: AGP 9.0 trae built-in Kotlin y aplicarlo falla.
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
androidx-room = { id = "androidx.room", version.ref = "room" }
```

---

## 3. Archivos de build (AGP 9.0.1 con built-in Kotlin)

### Qué cambia con built-in Kotlin (fuente: [AGP 9.0 release notes](https://developer.android.com/build/releases/agp-9-0-0-release-notes) y [Migrate to built-in Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin))

- AGP 9.0 activa `android.builtInKotlin=true` por defecto y compila Kotlin sin el plugin `org.jetbrains.kotlin.android`. Si se aplica, falla con «Cannot add extension with name 'kotlin'» o «The 'org.jetbrains.kotlin.android' plugin is no longer required for Kotlin support since AGP 9.0».
- AGP 9.0 tiene una dependencia de runtime sobre KGP 2.2.10. Para usar un KGP más alto, la doc oficial dice: «add the following to your top-level build file: `buildscript { dependencies { classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:KGP_VERSION") } }`». Eso es lo que hace el `build.gradle.kts` raíz de abajo.
  - Verificado: `kotlin-gradle-plugin:2.2.10 -> 2.3.20`.
  - Sin el bloque `buildscript` también resolvió a 2.3.20, porque el plugin compose 2.3.20 lo arrastra. Aun así conviene dejarlo explícito, que es la vía documentada.
- Los plugins **Compose** (`org.jetbrains.kotlin.plugin.compose`) y **Serialization** (`org.jetbrains.kotlin.plugin.serialization`) se siguen aplicando como siempre, con la misma versión que Kotlin. Built-in Kotlin solo sustituye a `kotlin-android`.
- `android.kotlinOptions {}` ya no existe. Si hace falta, se usa `kotlin { compilerOptions { … } }`. Con built-in Kotlin, `jvmTarget` toma por defecto `android.compileOptions.targetCompatibility`, así que no hace falta declararlo.
- Directorios extra de fuentes: solo `android.sourceSets.named("main") { kotlin.directories += "..." }`.
- `kapt` no está soportado tal cual; Room va por KSP.

### `settings.gradle.kts`

```kotlin
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "AppNueva"
include(":app")
```

### `build.gradle.kts` (raíz)

```kotlin
buildscript {
    dependencies {
        // Sube el KGP que trae AGP 9.0 (2.2.10) a 2.3.20; forma documentada en las release notes de AGP 9.0.
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
```

### `gradle.properties`

```properties
org.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
# NO poner android.builtInKotlin=false ni android.newDsl=false (son opt-outs temporales que AGP 10 elimina).
```

### `app/build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.application)
    // Sin org.jetbrains.kotlin.android: AGP 9.0 compila Kotlin de forma nativa (built-in Kotlin).
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

android {
    namespace = "com.example.appnueva" // ajustar
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.appnueva" // ajustar
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

// Opcional: solo si se quieren opciones extra del compilador. jvmTarget se hereda de compileOptions.
// kotlin {
//     compilerOptions {
//         freeCompilerArgs.add("-Xannotation-default-target=param-property")
//     }
// }

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
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.okhttp.mockwebserver)
}
```

Nota sobre los tests: con Compose 1.11, `androidx.compose.ui.test.junit4.createComposeRule` aparece como deprecado. Conviene usar `androidx.compose.ui.test.junit4.v2.createComposeRule`, que usa `StandardTestDispatcher`. Es solo un warning de compilación (visto en la verificación).

---

## 4. `gradle/wrapper/gradle-wrapper.properties`

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-9.1.0-all.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

La carpeta en caché `~/.gradle/wrapper/dists/gradle-9.1.0-all/7wzd0jkjit61aq2p43wpjgij9` corresponde exactamente al MD5 en base 36 de esa URL. La variante `-bin` daría `9agqghryom9wkf8r80qlhnts3` y obligaría a descargar de nuevo. Los scripts `gradlew`/`gradlew.bat` y `gradle-wrapper.jar` se pueden generar con `<gradle-9.1.0>/bin/gradle wrapper --gradle-version 9.1.0 --distribution-type all`, o copiarlos de cualquier proyecto que ya los tenga.

---

## 5. Riesgos y alternativas

1. **Quedarse en Gradle 9.1.0 congela AndroidX en las líneas anteriores a API 37.** Todo lo publicado desde mediados de 2026 exige AGP ≥ 9.1.0 y compileSdk 37. Afecta a Compose 1.12 (BOM ≥ 2026.08.00), Lifecycle 2.11, Navigation 2.10, Core 1.19 y OkHttp 5.5. Lo comprueban los `aar-metadata.properties` de cada AAR (Google Maven / Maven Central). En la práctica es aceptable para empezar: son versiones estables recientes y el build está verificado. El problema es que cualquier `implementation` nueva sin versión fijada, o un BOM nuevo, romperá en `checkDebugAarMetadata`.

2. **La alternativa de subir Gradle (recomendada en cuanto se acepte descargar otra distribución de Gradle)** no está verificada con build, solo con tablas oficiales:
   - Gradle **9.5.0** con AGP **9.3.3** ([about-agp](https://developer.android.com/build/releases/about-agp): «9.3 → 9.5.0»). Android Studio 2026.1.2 (Quail 2) admite hasta AGP 9.3, y es la línea que trae el propio IDE (`buildVersion = 9.3.0`).
   - Con AGP 9.3 se puede usar compileSdk 37 (instalada como 37.1; API 37 exige AGP ≥ 9.1.1 según la tabla «Minimum versions of tools for Android API level»).
   - Kotlin tiene que pasar a **2.4.21**: KGP 2.3.20 solo está probado hasta AGP 9.0.0, mientras que 2.4.20–2.4.21 cubre Gradle 7.6.3–9.7.0 y AGP 8.5.2–9.3.1 ([tabla KGP](https://kotlinlang.org/docs/gradle-configure-project.html)).
   - Eso permite Compose BOM 2026.09.00, Lifecycle 2.11.0, Navigation 2.10.2, core-ktx 1.19.1 y OkHttp 5.5.0, con KSP 2.3.12 sin cambios.
   - AGP 9.4 queda descartado con esta build de Studio: necesita Gradle 9.6.0 y Studio Quail 4 o posterior.

3. **El asistente de Android Studio propondrá AGP 9.3.x/9.2.1**, que no funcionan con Gradle 9.1.0. Si se crea el proyecto con el asistente, hay que sustituir su `libs.versions.toml` por el de §2. Los AGP 9.1–9.4 exigen Gradle 9.3.1–9.6.0.

4. **AGP 9.0.1 con compileSdk 37 o 36.1:** está fuera de lo soportado (máximo 36.1) y no aporta nada. Se queda en 36.

5. **KGP 2.3.20 sobre AGP 9.0.1:** la tabla de Kotlin indica «AGP 8.2.2–9.0.0». 9.0.1 es un parche de la misma serie y el build pasó, así que el riesgo es bajo. Si apareciera algún problema, Kotlin 2.4.21 también cubre AGP 9.0.x (8.5.2–9.3.1), pero entonces habría que subir kotlinx-serialization a la 1.12.x (aún RC). Con 2.3.20 queda todo alineado: serialization 1.11.0 está basada en Kotlin 2.3.20.

6. **Robolectric:** con targetSdk 35 usa `android-all-instrumented:15-robolectric-*`, que se descarga la primera vez. Robolectric 4.17 soporta hasta SDK 37, así que subir targetSdk después no obliga a cambiarlo.

7. **Opt-outs:** `android.builtInKotlin=false` y `android.newDsl=false` existen en AGP 9.x pero desaparecen en AGP 10. No usarlos en un proyecto nuevo.
