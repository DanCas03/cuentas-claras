# Cuentas Claras Nativa (Kotlin + Jetpack Compose) — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Objetivo:** Reescribir Cuentas Claras como app Android nativa en Kotlin + Jetpack Compose, con las mismas funciones que la PWA v3 salvo el import del PDF de Zinli, datos en Room y captura propia de las notificaciones del BNC, entregando un APK instalable en el Xiaomi de Daniel al cierre de cada una de las 6 fases.

**Arquitectura:** Un solo módulo `:app` en `android/`. Capas por paquete: `domain/` (Kotlin puro JVM, portado 1 a 1 de `src/domain/`), `data/` (Room: entidades, DAOs, seeds, operaciones todo-o-nada con `withTransaction`, repositorio), `importar/`, `capture/`, `rates/`, `backup/` (sin Compose), y `ui/` (Compose + Material 3, un `NavHost`, un ViewModel por pantalla con `StateFlow`). Contenedor manual `AppContainer` en `CuentasApp`; sin Hilt, sin WorkManager, sin librería de gráficas. La UI es **nueva**: se diseña desde cero en la fase 2 con una dirección visual que elige Daniel; el comportamiento sale del TSX de `src/ui/`.

**Tech Stack:** Gradle 9.1.0 (Kotlin DSL + catálogo) · AGP 9.0.1 con Kotlin integrado · Kotlin 2.3.20 · KSP 2.3.12 · Compose BOM 2026.06.01 (Compose 1.11.4, Material 3 1.4.0) · Navigation Compose 2.9.8 · Lifecycle 2.10.0 · Room 2.8.5 · DataStore 1.2.1 · kotlinx.serialization 1.11.0 · coroutines 1.11.0 · OkHttp 5.4.0 · JUnit 4.13.2 + kotlin-test + Robolectric 4.17 · compileSdk 36 / minSdk 26 / targetSdk 35 · JBR 21 de Android Studio para Gradle · adb + `uiautomator` para verificar en el Xiaomi.

**Spec:** `docs/superpowers/specs/2026-10-15-cuentas-claras-nativa-compose-design.md` (manda en el *cómo* y el alcance). Handoff con decisiones y contexto: `docs/superpowers/handoffs/2026-10-15-handoff-plan-nativa-compose.md`. Specs v1/v2/v3 (`docs/superpowers/specs/2026-07-07-…`, `2026-07-22-…`, `2026-09-04-…`) mandan en el *qué* de cada función. **El código TypeScript de `src/` es la especificación ejecutable** (salvo `src/import/zinli/` y `ImportZinli.tsx`, fuera de alcance): cada regla de negocio se porta de ahí; si un spec y el código difieren, manda el código. Versiones fijadas con fuentes primarias en `docs/research/2026-10-15-versiones-android-compose.md`.

## Global Constraints

Aplican a TODAS las tareas; cada tarea las incluye implícitamente.

1. **Decisiones cerradas de Daniel (NO reabrir):** Kotlin + Compose puro, nada de WebView/Capacitor · sin import de PDF Zinli (Importar solo ofrece el TXT del BNC) · fases en el orden (1) dominio + Room + respaldo, (2) Inicio/Nuevo/Movimientos/Ajustes, (3) captura + Permisos, (4) import TXT BNC, (5) deudas y grupos, (6) calendario · respaldo JSON v3 idéntico byte a byte en formato, sin migración de IndexedDB · teléfono Xiaomi/Redmi/POCO: las defensas MIUI/HyperOS son alcance obligatorio de la fase 3 · **la UI se diseña desde cero con referencias nuevas (Daniel, 2026-10-15); las funciones son las mismas.**
2. **Montos SIEMPRE `Long` en céntimos.** Formato visual `Bs 13.517,75` / `$84.20` / `Bs -3.500,00` / `-$5.00` exclusivamente vía `formatMoney`. Cifras con dígitos tabulares (`fontFeatureSettings = "tnum"`).
3. **Redondeo:** `Math.round(x)` o `x.roundToLong()` (redondean .5 hacia +∞, igual que `Math.round` de JS). **Prohibido `kotlin.math.round`** (redondea al par y desvía céntimos).
4. **Fechas de calendario locales** como `String` `yyyy-mm-dd`; horas `HH:mm:ss` (o `HH:mm:ss.SSS` del BNC). `java.time.LocalDate`/`YearMonth` solo como herramienta interna; se persiste y se expone `String`. Nunca `Instant`/UTC para una fecha de calendario. Los **instantes** (`createdIso`, `exportedAt`, `recibidaIso`) sí son UTC ISO con milisegundos (`ahoraIso()`), como `new Date().toISOString()` en la web. `hoy` se inyecta (`todayIso()` en la UI, string fijo en tests).
5. **Todo en español:** UI, docs, commits, mensajes de error (los mismos textos que la web).
6. **Registro manual de un gasto en ≤ 3 toques desde Inicio** (FAB «+» → monto en el teclado propio → categoría guarda).
7. **`txsEfectivas()` alimenta SOLO agregaciones** (Este mes, Comparativa, Presupuestos, gráficas); los saldos (`accountBalanceMinor`, `patrimonio`) usan las transacciones crudas. Los ids `virtual-…` jamás se persisten ni se asignan a eventos.
8. **Operaciones compuestas todo-o-nada** (cerrar deuda, agrupar/desagrupar/añadir/quitar, guardar/eliminar/asignar evento, omitir/reanudar, importar respaldo, confirmar import) dentro de `db.withTransaction { }`.
9. **Guard `guardando` en TODO guardado asíncrono:** el ViewModel expone `guardando: Boolean` en su estado; la función de guardado hace `if (guardando) return` → validación → `guardando = true` → trabajo en `try` → `catch` avisa con `e.message` → `finally` `guardando = false`. Todos los botones de acción `enabled = !guardando`.
10. **Capas:** `domain/`, `data/`, `importar/`, `capture/` (salvo el servicio y el notificador), `rates/`, `backup/` **sin imports de `androidx.compose`**. `domain/` además **sin ningún import de `android.*`/`androidx.*`** (JVM puro; sí puede usar `java.*` y `kotlinx.serialization`).
11. **Identificadores portados 1 a 1:** funciones, ids de seeds y nombres de tabla/campo como en la web (`txsEfectivas`, `sugerirOcurrencia`, `parseBncTxt`, `acc-bnc`, `cat-sueldo`, `calEvents`, `amountMinor`).
12. **Tests SOLO de la lista cerrada del spec §9** (dinero, tasas, saldos, grupos, stats, calendario, eventMatch, parser de notificación, parser + mapeo del TXT, respaldo y las operaciones todo-o-nada con Robolectric). Pocos casos por módulo: el normal, el límite y el que históricamente falló. **Nada de tests** para `dates`, `builders`, `filters`, `rules`, seeds, settings, ViewModels ni UI. No se porta la suite de Vitest uno a uno. La cobertura NO es objetivo: el objetivo es que la app funcione bien.
13. Antes de cada commit con Kotlin: `./gradlew :app:testDebugUnitTest` en verde y `./gradlew :app:assembleDebug` compila. Gradle SIEMPRE a través de `ctx_execute` (context-mode) con `tail`/`grep` de la salida; timeouts largos (primer build: minutos).
14. **Esquema Room congelado tras la Tarea 5** (versión 1, con la tabla `capturas` incluida desde el principio). Cualquier cambio posterior exige `Migration` + subir `version` + exportar el esquema; **prohibido `fallbackToDestructiveMigration`** (Daniel tendrá datos reales desde la fase 1).
15. **Privacidad:** los comprobantes reales de la raíz (`TXTBNC1.txt`, `TXTBNC2.txt`, `Screenshot_*.jpg`) y el respaldo real de Daniel NUNCA se copian a fixtures, docs, código ni capturas commiteadas. Fixtures: solo las anonimizadas de `src/**/fixtures/` y `respaldo-demo-v3.json` (inventado, Tarea 7). Ningún keystore en git.
16. **Commits:** mensaje en español; footer exacto como última línea: `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>` (va como segundo `-m`). `git add` SOLO de los archivos de la tarea; nunca `git add -A`. No se commitean `.claude/`, `.superpowers/`, `android/local.properties`, `android/.gradle/`, `android/build/`, `android/app/build/` ni capturas.
17. **Paralelismo (regla de Daniel):** dos tareas solo van en paralelo si sus archivos son disjuntos; cada implementador hace su propio commit.
18. Toda tarea de implementación carga la tasteskill **`output-skill`** (`full-output-enforcement`): sin placeholders, sin código truncado, sin «resto igual».
19. **Pide a Daniel SOLO lo que únicamente él puede hacer, y en el momento:** conectar el Xiaomi por USB, confirmar el paquete del BNC, pasar el archivo del respaldo, elegir la dirección visual, conceder permisos y validar en el teléfono.

## Review Focus

Entradas o fallos que el spec implica, que ningún test de función aislada ejerce y que más probablemente muerdan a Daniel. Los que caen en módulos de la lista de §9 llevan test en su tarea; los demás, por la regla 12, llevan un paso de verificación manual bloqueante en su tarea.

1. **Respaldo exportado por la PWA con claves opcionales ausentes, `vesPerUsd` entero y claves desconocidas** → se importa sin error y sin perder campos. Test en la Tarea 7 (`respaldo-demo-v3.json` escrito al estilo PWA).
2. **Respaldo exportado por la app nativa abierto en la PWA** → sin ningún `null` en el JSON (la PWA usa `t.eventId !== undefined` y un `null` la rompería) y con `recurrence` como `{"kind":"monthly","days":[15,31]}`. Test en la Tarea 7.
3. **Notificación del BNC con espacios no separables (`U+00A0`, `U+202F`), saltos de línea o mayúsculas distintas** → se reconoce igual. Test en la Tarea 18.
4. **Fila del TXT ya registrada por captura cuya referencia viene con ceros a la izquierda distintos, y su comisión con la misma referencia** → la fila del pago sale duplicada «ya registrada por captura»; la comisión NO (tiene otro monto). Test en la Tarea 23.
5. **El sistema mata el proceso entre la captura y el registro, o MIUI reemite la misma notificación** → la captura sigue pendiente en Inicio tras reabrir, y la reemisión no crea una segunda captura. Verificación manual bloqueante en las Tareas 19 y 22 (`am force-stop` + reabrir; reenviar la misma notificación de prueba).

## Decisiones de implementación tomadas con default razonable (ledger)

El revisor de cada fase las valida; Daniel puede vetarlas.

| # | Decisión | Por qué |
|---|---|---|
| D1 | Tipos de dominio = `data class` `@Serializable` en `domain/Types.kt`; entidades Room separadas en `data/db/Entidades.kt` con mapeos `toDomain()`/`toEntity()`. | `domain/` no puede depender de Room (restricción 10). |
| D2 | Enums de dominio en MAYÚSCULAS con `@SerialName` del literal web (`@SerialName("porPagar") POR_PAGAR`). Room los guarda por `name` con su conversor de enums por defecto. | JSON idéntico a la web; la base interna no necesita los literales. |
| D3 | `Recurrence` = `sealed interface` con `@JsonClassDiscriminator("kind")`; en Room, `recurrence` y `omitidas` como JSON string con `TypeConverter`. | Mismo JSON que la web; spec §6.1. |
| D4 | JSON único `JsonCuentas { ignoreUnknownKeys = true; explicitNulls = false }` con `encodeDefaults = false` y **ningún default no nulo en clases `@Serializable`**. | Los opcionales ausentes no se escriben (como `undefined` en `JSON.stringify`). |
| D5 | `parseUsAmount` no se porta. | Solo lo usaba el parser de Zinli (fuera de alcance). |
| D6 | `nuevoId()` = `UUID.randomUUID().toString()`. | Equivale a `crypto.randomUUID()`. |
| D7 | Orden alfabético de títulos con `Collator` español (`compararTexto`). | Equivale a `localeCompare`. |
| D8 | Dedupe captura ↔ import: una fila es «ya registrada por captura» si su referencia (sin ceros a la izquierda, no vacía) coincide con la de una captura `registrada` **y** `|amountMinor|` es igual al monto capturado. | La comisión comparte referencia con su cargo (`pairRef`); exigir el monto evita esconderla. Endurece el spec §7.4. |
| D9 | `actualizarGrupo(db, groupId, nota, categoryId)` reemplaza ambos campos (vacío → se borra). | El editor de grupo siempre envía los dos; YAGNI sobre el patch `null`/`undefined`. |
| D10 | Ruta del calendario `calendario?dia={dia}&nuevo={nuevo}` (no `?mes`). | La web navega con `?dia=` para seleccionar el día (`SemanaCard.tsx:44`) y con `?nuevo=1` para abrir el editor; el mes se deriva del día. |
| D16 | Antes de restaurar un respaldo, el respaldo de seguridad de lo actual se guarda solo en `filesDir/respaldos/` (se conservan 5), sin segundo selector. | La web lo pedía por diálogo; en Android serían dos selectores seguidos (Tarea 9). |
| D17 | La fixture del TXT del BNC se genera desde `bncFixture.ts` con Node (Tarea 23), no se copia a mano. | Los tabuladores y `\r\n` se pierden al copiar. |
| D11 | Las reglas aprendidas se crean en el import (casilla «regla» por fila), no en Nuevo. | Es lo que hace el código (`ImportBnc.tsx:105`); Nuevo no crea reglas. |
| D12 | En builds de depuración, `MainActivity` acepta el extra `respaldoDebug` y carga `filesDir/<archivo>` con `importBackup`. | Sustituye a `window.__db` de la web para sembrar datos en las verificaciones por adb. |
| D13 | Esquema Room exportado a `android/app/schemas/` con el plugin de Room y commiteado. | Base de las migraciones futuras (restricción 14). |
| D14 | Tema único fijo (sin colores dinámicos, sin tema claro/oscuro conmutables). La paleta, la tipografía y si el tema es oscuro o claro salen de la dirección visual que elija Daniel en la Tarea 10. | Spec §3.7 + cambio de Daniel del 2026-10-15 (UI nueva). |
| D15 | Avisos (equivalente de `avisar`) por un `SnackbarHost` raíz alimentado por `Avisos.avisar(texto)` (`MutableSharedFlow`). | Un único canal como `Toast.tsx`. |

## Receta de verificación en el dispositivo (todas las tareas de UI y los cierres de fase)

La UI no tiene tests automáticos: cada tarea de UI termina con esta verificación **bloqueante**. Dispositivo preferido: el Xiaomi de Daniel por USB. En MIUI/HyperOS, `adb shell input` exige activar en Opciones de desarrollador **«Depuración USB (ajustes de seguridad)»** e **«Instalar vía USB»**; si `input tap` falla con `INJECT_EVENTS`, pídeselo a Daniel. Alternativa: emulador (Tarea 9, Paso 6).

Guarda este script como `.superpowers/sdd/adb-ui.sh` (gitignored, NO en el repo) y cárgalo con `source` en Git Bash:

```bash
# .superpowers/sdd/adb-ui.sh — utilidades de verificación por adb (Git Bash)
ADB="/c/Users/danie/AppData/Local/Android/Sdk/platform-tools/adb.exe"
DIR_UI=".superpowers/sdd"
PAQUETE="com.cuentasclaras.app"

instalar() {   # compila e instala el APK de depuración
  (cd android && ./gradlew :app:assembleDebug --console=plain -q) && \
  "$ADB" install -r android/app/build/outputs/apk/debug/app-debug.apk
}

abrir() { "$ADB" shell am start -n "$PAQUETE/.MainActivity" "$@"; }

volcar() {     # vuelca el árbol de accesibilidad a $DIR_UI/ui.xml
  "$ADB" shell uiautomator dump /sdcard/ui.xml >/dev/null && \
  "$ADB" pull /sdcard/ui.xml "$DIR_UI/ui.xml" >/dev/null
}

tocar() {      # tocar "Texto visible o contentDescription" — toca el centro del primer nodo que coincide
  local objetivo="$1"
  volcar
  local nums
  nums=$(grep -o "<node[^>]*\(text\|content-desc\)=\"$objetivo\"[^>]*>" "$DIR_UI/ui.xml" | head -1 \
        | grep -o 'bounds="[^"]*"' | grep -o '[0-9]\+' | tr '\n' ' ')
  set -- $nums
  if [ $# -ne 4 ]; then echo "NO ENCONTRADO: $objetivo"; return 1; fi
  "$ADB" shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
  sleep 1
}

escribir() { "$ADB" shell input text "$(echo "$1" | sed 's/ /%s/g')"; }   # texto sin acentos

captura() {    # captura fase-N nombre → .superpowers/sdd/capturas/fase-N/nombre.png
  mkdir -p "$DIR_UI/capturas/$1"
  "$ADB" exec-out screencap -p > "$DIR_UI/capturas/$1/$2.png"
}

sembrar() {    # sembrar ruta/al/respaldo.json — carga un respaldo en la app de depuración (D12)
  "$ADB" push "$1" /data/local/tmp/respaldo-debug.json >/dev/null && \
  "$ADB" shell run-as "$PAQUETE" cp /data/local/tmp/respaldo-debug.json files/respaldo-debug.json && \
  "$ADB" shell am force-stop "$PAQUETE" && \
  abrir --es respaldoDebug respaldo-debug.json && sleep 3
}

reiniciar() {  # simula que el sistema mata el proceso y Daniel reabre la app
  "$ADB" shell am force-stop "$PAQUETE" && abrir && sleep 3
}
```

Flujo por pantalla: `instalar` → `sembrar android/app/src/test/resources/respaldo-demo-v3.json` (datos inventados) → navegar con `tocar` → `captura fase-N pantalla` → **abrir cada PNG con Read e inspeccionarlo** (texto cortado, cifras sin formato, solapes, contraste). Las capturas viven en `.superpowers/sdd/capturas/` y NUNCA se commitean. Nunca siembres el respaldo real de Daniel con este flujo: ese lo carga él desde la app.

## Estructura de archivos

```
android/
  settings.gradle.kts · build.gradle.kts · gradle.properties · gradle/libs.versions.toml
  gradle/wrapper/gradle-wrapper.properties · gradlew · gradlew.bat · .gitignore
  DESIGN.md                                    ← dirección visual elegida por Daniel (Tarea 10)
  app/build.gradle.kts · app/proguard-rules.pro · app/schemas/…json (D13)
  app/src/main/AndroidManifest.xml
  app/src/main/res/…                           ← ícono, strings, font/ (Tarea 10)
  app/src/main/java/com/cuentasclaras/app/
    CuentasApp.kt                              ← Application + AppContainer
    MainActivity.kt                            ← única Activity, NavHost, re-enlace del listener (fase 3)
    domain/   Types.kt Json.kt Texto.kt Money.kt Dates.kt Rates.kt Balances.kt Stats.kt Rules.kt
              Groups.kt Debts.kt Filters.kt Builders.kt Calendar.kt EventMatch.kt
    data/     db/Entidades.kt db/Convertidores.kt db/Daos.kt db/CuentasDb.kt db/Mapeos.kt
              Seeds.kt CuentasRepo.kt
              ops/CommitImport.kt ops/DebtOps.kt ops/GroupOps.kt ops/EventOps.kt
    backup/   Respaldo.kt
    rates/    Providers.kt
    importar/ bnc/ParseBncTxt.kt bnc/MapBnc.kt
    capture/  ParseNotificacionBnc.kt CapturaRepo.kt BncNotificationListener.kt Notificador.kt
              PermisosXiaomi.kt EstadoListener.kt
    ui/       theme/ (Color.kt Type.kt Shape.kt Theme.kt) · Avisos.kt · nav/ (Rutas.kt CuentasNavHost.kt BarraInferior.kt)
              componentes/ (Tarjeta.kt Boton.kt Segmentado.kt Chip.kt Banner.kt Vacio.kt Campo.kt
                            Selector.kt HojaModal.kt Teclado.kt CategoriaGrid.kt Dona.kt Barras.kt)
              pantallas/inicio/ nuevo/ movimientos/ ajustes/ importar/ deudas/ calendario/ permisos/ respaldo/
  app/src/test/java/com/cuentasclaras/app/   ← SOLO los tests de §9
  app/src/test/resources/                    ← fixtures anonimizadas + respaldo-demo-v3.json
docs/research/2026-10-15-versiones-android-compose.md
docs/research/2026-10-15-notification-listener-miui.md   ← Tarea 18
docs/guia-permisos.md                                    ← Tarea 21 (reemplaza guia-macrodroid.md)
```

## Fases, tareas y dependencias

```
Fase 1  T1 scaffold ─> T2 money/dates/rates ─> T3 saldos/grupos/stats/… ─> T4 calendario/eventMatch
                    └> T5 Room ─> T6 ops todo-o-nada ─> T7 respaldo ─> T8 tasas ─> T9 APK fase 1 + validación
        (T5 solo depende de T2 y toca archivos disjuntos: puede ir en paralelo con T3/T4. T6 necesita T3, T4 y T5.)
Fase 2  T10 dirección visual + tema ─> T11 navegación ─> {T12 Nuevo, T13 Inicio, T15 Movimientos, T16 Ajustes}
        T13 ─> T14 «Este mes» ; todo ─> T17 cierre fase 2
Fase 3  T18 investigación + parser ─> T19 servicio ─> T20 Nuevo con captura + Inicio ─> T21 Permisos ─> T22 cierre
Fase 4  T23 parser + mapeo TXT ─> T24 pantalla Importar ─> T25 cierre
Fase 5  T26 Deudas ─┬─> T28 cierre        T27 grupos en Movimientos ─┘
Fase 6  T29 Calendario ─> T30 Semana real + chips en Nuevo ─> T31 SelectEvento + sugerencia en import ─> T32 cierre final
```

## Criterio de éxito global (spec §9)

> Daniel importa su respaldo, registra un gasto en ≤ 3 toques, recibe un pago móvil real y lo registra desde la notificación en 2 toques, e importa un TXT mensual sin duplicar lo capturado.

---
## Fase 1 — Dominio, datos y respaldo

Entregable de la fase (spec §7.1): APK de depuración con una pantalla mínima «Respaldo» que carga el JSON de Daniel por SAF y muestra los conteos por tabla. Al cerrar la fase: `mattpocock-skills:code-review` (eje Standards contra el spec §4 y las restricciones globales; eje Spec contra el *qué* de v1/v2/v3) con `superpowers:requesting-code-review` / `superpowers:receiving-code-review`.

### Tarea 1: Scaffold del proyecto Android con las versiones verificadas

**Skills:** `mattpocock-skills:research` (ya ejecutada: `docs/research/2026-10-15-versiones-android-compose.md`; esta tarea NO cambia ninguna versión) · `superpowers:verification-before-completion` · `output-skill`.

**Files:**
- Create: `android/.gitignore`, `android/settings.gradle.kts`, `android/build.gradle.kts`, `android/gradle.properties`, `android/gradle/libs.versions.toml`
- Create (generados por `gradle wrapper`): `android/gradlew`, `android/gradlew.bat`, `android/gradle/wrapper/gradle-wrapper.jar`, `android/gradle/wrapper/gradle-wrapper.properties`
- Create: `android/app/build.gradle.kts`, `android/app/proguard-rules.pro`, `android/app/src/main/AndroidManifest.xml`
- Create: `android/app/src/main/res/values/strings.xml`, `android/app/src/main/res/values/themes.xml`
- Create: `android/app/src/main/java/com/cuentasclaras/app/MainActivity.kt` (provisional; la reescribe la Tarea 9)
- Create (NO se commitea): `android/local.properties`

**Interfaces:**
- Consumes: nada.
- Produces: proyecto que compila con `./gradlew :app:assembleDebug` y ejecuta `./gradlew :app:testDebugUnitTest`; aliases del catálogo usados por todas las tareas (`libs.androidx.room.runtime`, etc.); paquete `com.cuentasclaras.app`; plugins Compose, Serialization, KSP y Room aplicados en `:app`; `room { schemaDirectory("$projectDir/schemas") }`.

**Versiones (NO cambiar; verificadas con build real, ver el doc de research):** Gradle 9.1.0 (`-all`, ya en caché) · AGP 9.0.1 con **Kotlin integrado** (NO se aplica `org.jetbrains.kotlin.android`) · Kotlin/KGP 2.3.20 · KSP 2.3.12 · Compose BOM 2026.06.01 · Room 2.8.5 · Navigation Compose 2.9.8 · activity-compose 1.13.0 · Lifecycle 2.10.0 · core-ktx 1.18.0 · DataStore 1.2.1 · kotlinx-serialization-json 1.11.0 · coroutines 1.11.0 · OkHttp 5.4.0 · Robolectric 4.17 · androidx.test core 1.7.0 / ext-junit 1.3.0 · JUnit 4.13.2 · compileSdk 36 / minSdk 26 / targetSdk 35 · bytecode 17 · Gradle corre con el JBR 21 de Android Studio. **Ninguna dependencia nueva sin versión fijada**: todo lo publicado para compileSdk 37 rompe `checkDebugAarMetadata` con AGP 9.0.1.

- [ ] **Paso 1: Generar el wrapper con la Gradle 9.1.0 en caché.** En Git Bash:

```bash
mkdir -p android && cd android
printf 'rootProject.name = "CuentasClaras"\n' > settings.gradle.kts
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" \
  "/c/Users/danie/.gradle/wrapper/dists/gradle-9.1.0-all/7wzd0jkjit61aq2p43wpjgij9/gradle-9.1.0/bin/gradle" \
  wrapper --gradle-version 9.1.0 --distribution-type all
```

Expected: `BUILD SUCCESSFUL`; existen `gradlew`, `gradlew.bat` y `gradle/wrapper/`. Confirma que `gradle/wrapper/gradle-wrapper.properties` contiene `distributionUrl=https\://services.gradle.org/distributions/gradle-9.1.0-all.zip` (la carpeta en caché `7wzd0jkjit61aq2p43wpjgij9` corresponde a esa URL; con `-bin` volvería a descargar).

- [ ] **Paso 2: `android/.gitignore` y `android/local.properties`.**

```gitignore
.gradle/
.kotlin/
build/
app/build/
local.properties
.idea/
*.iml
captures/
```

`local.properties` (NO se commitea):

```properties
sdk.dir=C\:\\Users\\danie\\AppData\\Local\\Android\\Sdk
```

- [ ] **Paso 3: `android/settings.gradle.kts`** (reemplaza el de una línea del Paso 1).

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
rootProject.name = "CuentasClaras"
include(":app")
```

- [ ] **Paso 4: `android/gradle/libs.versions.toml`.**

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
# Sube el KGP que trae AGP 9.0 (2.2.10) a 2.3.20 (forma documentada en las notas de AGP 9.0).
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
androidx-compose-ui-text-google-fonts = { group = "androidx.compose.ui", name = "ui-text-google-fonts" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-compose-material-icons-extended = { group = "androidx.compose.material", name = "material-icons-extended" }

androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
androidx-room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }

androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }

kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerializationJson" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "kotlinxCoroutines" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "kotlinxCoroutines" }

okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }

junit = { group = "junit", name = "junit", version.ref = "junit" }
kotlin-test = { group = "org.jetbrains.kotlin", name = "kotlin-test", version.ref = "kotlin" }
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }
androidx-test-core = { group = "androidx.test", name = "core", version.ref = "androidxTestCore" }
androidx-test-ext-junit = { group = "androidx.test.ext", name = "junit", version.ref = "androidxTestExtJunit" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
# NO declarar org.jetbrains.kotlin.android: AGP 9.0 trae Kotlin integrado y aplicarlo falla.
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
androidx-room = { id = "androidx.room", version.ref = "room" }
```

`ui-text-google-fonts` y `material-icons-extended` no tienen versión propia: las fija el BOM. Si al compilar alguno no resolviera, quítalo del catálogo y decláralo en el reporte (la Tarea 10 decide si hace falta).

- [ ] **Paso 5: `android/build.gradle.kts` y `android/gradle.properties`.**

```kotlin
buildscript {
    dependencies {
        // Sube el KGP que trae AGP 9.0 (2.2.10) a 2.3.20; forma documentada en las notas de AGP 9.0.
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

```properties
org.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8
# Gradle corre con el JDK 21 de Android Studio (el JDK 17 del PATH solo lanza el wrapper).
org.gradle.java.home=C\:/Program Files/Android/Android Studio/jbr
android.useAndroidX=true
kotlin.code.style=official
# NO poner android.builtInKotlin=false ni android.newDsl=false (opt-outs temporales que AGP 10 elimina).
```

- [ ] **Paso 6: `android/app/build.gradle.kts`.**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    // Sin org.jetbrains.kotlin.android: AGP 9.0 compila Kotlin de forma nativa (Kotlin integrado).
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

android {
    namespace = "com.cuentasclaras.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cuentasclaras.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
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
    implementation(libs.androidx.compose.material.icons.extended)
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
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
}
```

`android/app/proguard-rules.pro`: un archivo con la línea `# Sin reglas: el APK de depuración no se minifica.`

- [ ] **Paso 7: Manifiesto, recursos y `MainActivity` provisional.**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:allowBackup="false"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.CuentasClaras">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

`res/values/strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Cuentas Claras</string>
</resources>
```

`res/values/themes.xml` (solo la ventana; los colores reales viven en Compose desde la Tarea 10):

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.CuentasClaras" parent="android:Theme.Material.NoActionBar" />
</resources>
```

`MainActivity.kt` provisional:

```kotlin
package com.cuentasclaras.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Text("Cuentas Claras") } }
    }
}
```

- [ ] **Paso 8: Verificar el build (bloqueante).** Vía `ctx_execute` (shell), timeout ≥ 10 min:

```bash
cd android && ./gradlew :app:assembleDebug --console=plain 2>&1 | tail -15
cd android && ./gradlew :app:testDebugUnitTest --console=plain 2>&1 | tail -15
```

Expected: ambos `BUILD SUCCESSFUL` (el segundo sin tests todavía) y existe `android/app/build/outputs/apk/debug/app-debug.apk`. Si falla `checkDebugAarMetadata`, alguna versión no es la del catálogo: corrígela, no subas compileSdk. Si Gradle intenta descargar otra distribución, el `distributionUrl` no coincide con la caché.

- [ ] **Paso 9: Commit** (sin `local.properties` ni `build/`; el doc de research ya se commiteó junto con este plan).

```bash
git add android/.gitignore android/settings.gradle.kts android/build.gradle.kts android/gradle.properties android/gradle/ android/gradlew android/gradlew.bat android/app/build.gradle.kts android/app/proguard-rules.pro android/app/src/main/AndroidManifest.xml android/app/src/main/res/values/ android/app/src/main/java/com/cuentasclaras/app/MainActivity.kt
git status --short   # NO deben aparecer local.properties ni build/
git commit -m "chore(android): scaffold Kotlin + Compose con AGP 9.0.1, Kotlin 2.3.20, Room 2.8.5 y Gradle 9.1.0" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---
### Tarea 2: Tipos, JSON, dinero, fechas y tasas

**Skills:** `superpowers:test-driven-development` (solo `MoneyTest` y `RatesTest`) · `output-skill`.

**Files:**
- Create: `android/app/src/main/java/com/cuentasclaras/app/domain/Types.kt`
- Create: `android/app/src/main/java/com/cuentasclaras/app/domain/Json.kt`
- Create: `android/app/src/main/java/com/cuentasclaras/app/domain/Texto.kt`
- Create: `android/app/src/main/java/com/cuentasclaras/app/domain/Money.kt`
- Create: `android/app/src/main/java/com/cuentasclaras/app/domain/Dates.kt`
- Create: `android/app/src/main/java/com/cuentasclaras/app/domain/Rates.kt`
- Test: `android/app/src/test/java/com/cuentasclaras/app/domain/MoneyTest.kt`
- Test: `android/app/src/test/java/com/cuentasclaras/app/domain/RatesTest.kt`

Fuentes a portar: `src/domain/types.ts`, `money.ts`, `dates.ts`, `rates.ts` (y sus tests como guía de casos).

**Interfaces:**
- Consumes: scaffold de la Tarea 1 (plugin de serialization aplicado).
- Produces (paquete `com.cuentasclaras.app.domain`):
  - Tipos: `Currency`, `AccountKind`, `Account`, `TxType`, `TxSource`, `Transaction`, `CategoryKind`, `Category`, `RateSource`, `Rate`, `Rule`, `Settings`, `DebtDirection`, `DebtStatus`, `Debt`, `TxGroup`, `Recurrence` (`Once`, `Weekly`, `Monthly(days)`, `Yearly`), `CalEvent`; `fun nuevoId(): String`; `fun TxType.comoKind(): CategoryKind?`.
  - `val JsonCuentas: Json`, `val JsonRespaldo: Json`; `fun compararTexto(a: String, b: String): Int`.
  - `parseVenezuelanAmount(raw: String): Long?`, `formatMoney(minor: Long, currency: Currency): String`, `montoEditable(minor: Long): String`.
  - `todayIso()`, `ahoraIso()`, `instanteIso(i: Instant)`, `isoFromDdMmYyyy(s)`, `monthOf(iso)`, `data class Partes(y,m,d)`, `partsOf`, `isoFromParts`, `daysInMonth`, `esFechaIso`, `addDays`, `diffDias`, `weekdayLunes0`, `mesAnterior`, `mesSiguiente`, `primerDiaMes`, `ultimoDiaMes`, `nombreMes`, `nombreDia`, `fechaCorta`, `diaCorto`, `fechaLarga`, `ddmmyyyy`, `data class Celda(iso, enMes)`, `celdasMes`, `MES_CORTO`, `LETRAS_DIA`.
  - `effectiveRate(dateIso, activeSource, rates): Rate?`, `usdToVesMinor`, `vesToUsdMinor`, `convertMinor`.

- [ ] **Paso 1: `Types.kt`.**

```kotlin
package com.cuentasclaras.app.domain

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import java.util.UUID

// REGLA (D4): ninguna propiedad de estas clases tiene un default NO nulo. Los opcionales
// llevan `= null` para que, con encodeDefaults = false, no se escriban en el JSON (igual
// que `undefined` en JSON.stringify de la web) y para que su ausencia se lea como null.

fun nuevoId(): String = UUID.randomUUID().toString()

@Serializable
enum class Currency { VES, USD }

@Serializable
enum class AccountKind {
    @SerialName("bank") BANK,
    @SerialName("wallet") WALLET,
    @SerialName("snapshot") SNAPSHOT,
    @SerialName("cash") CASH,
}

@Serializable
data class Account(
    val id: String,
    val name: String,
    val currency: Currency,
    val kind: AccountKind,
    val isSavings: Boolean,
    val archived: Boolean,
    /** Estado del último import BNC (solo cuentas kind 'bank') para detectar huecos. */
    val bncLastImportKey: String? = null,
    val bncLastSaldoMinor: Long? = null,
    /** Vista 1 del slider de patrimonio. Default al leer: !isSavings (v2). */
    val enDisposicion: Boolean? = null,
)

@Serializable
enum class TxType {
    @SerialName("expense") EXPENSE,
    @SerialName("income") INCOME,
    @SerialName("transfer") TRANSFER,
    @SerialName("adjustment") ADJUSTMENT,
}

@Serializable
enum class TxSource {
    @SerialName("manual") MANUAL,
    @SerialName("bnc") BNC,
    @SerialName("zinli") ZINLI,
    @SerialName("adjust") ADJUST,
}

/**
 * Invariantes (idénticas a types.ts):
 * - amountMinor es entero en céntimos de la moneda de accountId.
 * - expense/income/transfer: amountMinor > 0 (el tipo da el signo).
 * - adjustment: amountMinor es el DELTA con signo (real − calculado).
 * - transfer: toAccountId obligatorio; toAmountMinor en la moneda destino; rateUsed solo si cruza moneda.
 * - date: 'yyyy-mm-dd'; time: 'HH:mm:ss' o 'HH:mm:ss.SSS' (opcional).
 * - groupId (v2): excluido de agregaciones; cuenta solo el residuo virtual del grupo.
 * - eventId + eventDate (v3): van juntos; eventDate es la fecha NOMINAL de la ocurrencia.
 */
@Serializable
data class Transaction(
    val id: String,
    val type: TxType,
    val accountId: String,
    val amountMinor: Long,
    val date: String,
    val time: String? = null,
    val categoryId: String? = null,
    val note: String? = null,
    val source: TxSource,
    val importKey: String? = null,
    val toAccountId: String? = null,
    val toAmountMinor: Long? = null,
    val rateUsed: Double? = null,
    val groupId: String? = null,
    val eventId: String? = null,
    val eventDate: String? = null,
)

@Serializable
enum class CategoryKind {
    @SerialName("expense") EXPENSE,
    @SerialName("income") INCOME,
}

/** EXPENSE/INCOME de un movimiento como kind de categoría; null para transferencias y ajustes. */
fun TxType.comoKind(): CategoryKind? = when (this) {
    TxType.EXPENSE -> CategoryKind.EXPENSE
    TxType.INCOME -> CategoryKind.INCOME
    else -> null
}

@Serializable
data class Category(
    val id: String,
    val name: String,
    val icon: String,
    val color: String,
    val kind: CategoryKind,
    /** Presupuesto mensual opcional, SIEMPRE en céntimos de USD. */
    val monthlyBudgetMinor: Long? = null,
)

@Serializable
enum class RateSource { BCV, BINANCE, MANUAL }

@Serializable
data class Rate(
    val date: String,
    val source: RateSource,
    /** Tasa (no es dinero: puede tener decimales). */
    val vesPerUsd: Double,
)

@Serializable
data class Rule(
    val id: String,
    /** Substring, case-insensitive. */
    val pattern: String,
    val categoryId: String,
    val autoCreated: Boolean,
)

@Serializable
data class Settings(
    /** Siempre "main". Sin default a propósito (D4): debe escribirse en el JSON. */
    val id: String,
    val viewCurrency: Currency,
    val rateSource: RateSource,
    val lastAccountId: String? = null,
    val lastRatesFetchDate: String? = null,
)

@Serializable
enum class DebtDirection {
    @SerialName("porPagar") POR_PAGAR,
    @SerialName("porCobrar") POR_COBRAR,
}

@Serializable
enum class DebtStatus {
    @SerialName("pendiente") PENDIENTE,
    @SerialName("cerrada") CERRADA,
}

/** Deuda o cuenta por cobrar (v2). Pendiente NO altera ningún saldo; el movimiento real
 *  nace solo al cerrarla. amountMinor vive en la moneda de la cuenta asignada. */
@Serializable
data class Debt(
    val id: String,
    val direction: DebtDirection,
    val concepto: String,
    val amountMinor: Long,
    val accountId: String,
    val status: DebtStatus,
    val createdIso: String,
    val settledTxId: String? = null,
    val settledIso: String? = null,
)

/** Grupo transaccional (v2). El residuo es virtual: se deriva, no se persiste. */
@Serializable
data class TxGroup(
    val id: String,
    val nota: String? = null,
    /** v3: categoría del residuo; se aplica SOLO si su kind coincide con el signo del residuo. */
    val categoryId: String? = null,
    val createdIso: String,
)

/** Reglas de recurrencia (v3). weekly/yearly derivan el día de la semana / mes+día de startDate.
 *  JSON idéntico a la web: {"kind":"once"} · {"kind":"monthly","days":[15,31]}. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("kind")
sealed interface Recurrence {
    @Serializable @SerialName("once") data object Once : Recurrence
    @Serializable @SerialName("weekly") data object Weekly : Recurrence
    /** 1..31, ordenados, sin repetidos; día > días del mes → último día. */
    @Serializable @SerialName("monthly") data class Monthly(val days: List<Int>) : Recurrence
    @Serializable @SerialName("yearly") data object Yearly : Recurrence
}

/** Evento de calendario (v3). NO es dinero: sus ocurrencias se DERIVAN (Calendar.kt). */
@Serializable
data class CalEvent(
    val id: String,
    val titulo: String,
    /** Obligatoria: define pago (expense) o cobro (income) y la categoría que copia la tx asignada. */
    val categoryId: String,
    /** Cuenta esperada; define la moneda de amountMinor. */
    val accountId: String? = null,
    /** Monto esperado (REQUIERE accountId); ausente = variable. */
    val amountMinor: Long? = null,
    val startDate: String,
    /** Inclusive; ausente = sin fin. */
    val endDate: String? = null,
    val recurrence: Recurrence,
    /** Fechas nominales marcadas «omitida». */
    val omitidas: List<String>? = null,
    val createdIso: String,
)
```

- [ ] **Paso 2: `Json.kt` y `Texto.kt`.**

```kotlin
package com.cuentasclaras.app.domain

import kotlinx.serialization.json.Json

/** JSON único de la app (D4): lee respaldos de la PWA con claves de más o de menos y
 *  escribe sin `null` (los opcionales nulos tienen default null y encodeDefaults = false). */
val JsonCuentas: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = false
}

/** Igual que JsonCuentas pero con sangría, para el archivo de respaldo que se guarda. */
val JsonRespaldo: Json = Json(JsonCuentas) { prettyPrint = true }
```

```kotlin
package com.cuentasclaras.app.domain

import java.text.Collator
import java.util.Locale

private val COLLATOR: Collator = Collator.getInstance(Locale.forLanguageTag("es"))

/** Equivalente de `a.localeCompare(b)` de la web para ordenar títulos y nombres. */
fun compararTexto(a: String, b: String): Int = COLLATOR.compare(a, b)
```

- [ ] **Paso 3: Tests RED de dinero.** `MoneyTest.kt`:

```kotlin
package com.cuentasclaras.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MoneyTest {
    @Test fun `parseVenezuelanAmount con decimales, negativo y entero`() {
        assertEquals(1351775L, parseVenezuelanAmount("13517,75"))
        assertEquals(-350000L, parseVenezuelanAmount("-3500"))
        assertEquals(917970L, parseVenezuelanAmount("9179,7"))
        assertEquals(60000L, parseVenezuelanAmount(" 600 "))
    }

    @Test fun `parseVenezuelanAmount redondea el artefacto float del export BNC`() {
        assertEquals(8770L, parseVenezuelanAmount("87,6999999999998"))
        assertEquals(480001L, parseVenezuelanAmount("4800,0099999999998"))
    }

    @Test fun `parseVenezuelanAmount rechaza punto de miles y basura`() {
        assertNull(parseVenezuelanAmount(""))
        assertNull(parseVenezuelanAmount("abc"))
        assertNull(parseVenezuelanAmount("1.234,50"))
    }

    @Test fun `formatMoney en Bs y en dolares, con miles y negativos`() {
        assertEquals("Bs 13.517,75", formatMoney(1351775, Currency.VES))
        assertEquals("Bs -3.500,00", formatMoney(-350000, Currency.VES))
        assertEquals("Bs 1.234.567,89", formatMoney(123456789, Currency.VES))
        assertEquals("$84.20", formatMoney(8420, Currency.USD))
        assertEquals("-$5.00", formatMoney(-500, Currency.USD))
        assertEquals("$1,000.00", formatMoney(100000, Currency.USD))
        assertEquals("Bs 0,00", formatMoney(0, Currency.VES))
    }
}
```

- [ ] **Paso 4: Correr → FAIL** (no compila: `parseVenezuelanAmount` no existe).

Run (vía `ctx_execute`, shell): `cd android && ./gradlew :app:testDebugUnitTest --tests "*MoneyTest" --console=plain 2>&1 | tail -25`
Expected: FAIL con `Unresolved reference: parseVenezuelanAmount`.

- [ ] **Paso 5: `Money.kt`.**

```kotlin
package com.cuentasclaras.app.domain

import kotlin.math.abs

private val MONTO_VE = Regex("""^([+-]?)(\d+)(?:,(\d+))?$""")

/** "13517,75" | "-3500" | "87,6999999999998" → céntimos enteros. null si no es un monto BNC válido. */
fun parseVenezuelanAmount(raw: String): Long? {
    val m = MONTO_VE.find(raw.trim()) ?: return null
    val (signo, entero, fraccion) = m.destructured
    val sign = if (signo == "-") -1L else 1L
    val intMinor = (entero.toLongOrNull() ?: return null) * 100
    // La parte decimal puede traer artefactos float del export ("6999999999998"):
    // se interpreta como fracción y se redondea a céntimos (Math.round = redondeo de JS).
    val fracMinor = if (fraccion.isEmpty()) 0L else Math.round("0.$fraccion".toDouble() * 100)
    return sign * (intMinor + fracMinor)
}

private fun conMiles(unidades: Long, separador: Char): String =
    unidades.toString().reversed().chunked(3).joinToString(separador.toString()).reversed()

/** Formato visual venezolano: `Bs 13.517,75` · `$84.20` · negativos: `Bs -3.500,00` / `-$5.00`. */
fun formatMoney(minor: Long, currency: Currency): String {
    val signo = if (minor < 0) "-" else ""
    val absoluto = abs(minor)
    val unidades = absoluto / 100
    val centimos = (absoluto % 100).toString().padStart(2, '0')
    return if (currency == Currency.USD) {
        "$signo\$${conMiles(unidades, ',')}.$centimos"
    } else {
        "Bs $signo${conMiles(unidades, '.')},$centimos"
    }
}

/** Céntimos positivos → texto para el teclado/campo de monto: 125000 → "1250,00".
 *  Equivale a `(minor / 100).toFixed(2).replace('.', ',')` de la web. */
fun montoEditable(minor: Long): String =
    "${minor / 100},${(minor % 100).toString().padStart(2, '0')}"
```

- [ ] **Paso 6: Correr → PASS** el mismo comando del Paso 4.

- [ ] **Paso 7: `Dates.kt`** (sin tests por la regla 12; los usa `CalendarTest` de forma indirecta).

```kotlin
package com.cuentasclaras.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// Todo LOCAL por componentes. Nunca Instant/UTC para fechas de calendario (restricción 4).

private val FORMATO_INSTANTE: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

/** Fecha local del dispositivo como 'yyyy-mm-dd'. */
fun todayIso(): String = LocalDate.now().toString()

/** Instante como `new Date().toISOString()`: '2026-09-04T14:00:00.000Z'. Solo para createdIso/exportedAt/recibidaIso. */
fun instanteIso(i: Instant): String = FORMATO_INSTANTE.format(i)

fun ahoraIso(): String = instanteIso(Instant.now())

private val DD_MM_YYYY = Regex("""^(\d{2})/(\d{2})/(\d{4})$""")

/** '06/07/2026' → '2026-07-06'. null si no es dd/mm/yyyy plausible. */
fun isoFromDdMmYyyy(s: String): String? {
    val m = DD_MM_YYYY.find(s.trim()) ?: return null
    val (dd, mm, yyyy) = m.destructured
    val dia = dd.toInt()
    val mes = mm.toInt()
    if (mes < 1 || mes > 12 || dia < 1 || dia > 31) return null
    return "$yyyy-$mm-$dd"
}

/** '2026-07-06' → '2026-07' */
fun monthOf(dateIso: String): String = dateIso.substring(0, 7)

private val NOMBRES_MES = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto",
    "septiembre", "octubre", "noviembre", "diciembre",
)
val MES_CORTO = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
private val DIA_CORTO = listOf("lun", "mar", "mié", "jue", "vie", "sáb", "dom")
private val DIA_LARGO = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")

/** Letras de la cabecera del calendario, lunes primero. */
val LETRAS_DIA = listOf("L", "M", "X", "J", "V", "S", "D")

data class Partes(val y: Int, val m: Int, val d: Int)

fun partsOf(iso: String): Partes {
    val (y, m, d) = iso.split("-").map { it.toInt() }
    return Partes(y, m, d)
}

fun isoFromParts(y: Int, m: Int, d: Int): String =
    "$y-${m.toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}"

fun daysInMonth(y: Int, m: Int): Int = YearMonth.of(y, m).lengthOfMonth()

private val FECHA_ISO = Regex("""^\d{4}-\d{2}-\d{2}$""")

/** 'yyyy-mm-dd' con forma estricta y fecha real (30/02 → false). */
fun esFechaIso(s: String): Boolean {
    if (!FECHA_ISO.matches(s)) return false
    val (y, m, d) = partsOf(s)
    return m in 1..12 && d >= 1 && d <= daysInMonth(y, m)
}

fun addDays(iso: String, n: Int): String = LocalDate.parse(iso).plusDays(n.toLong()).toString()

/** b − a en días enteros (inmune a cambios de hora: LocalDate no tiene zona). */
fun diffDias(a: String, b: String): Int = ChronoUnit.DAYS.between(LocalDate.parse(a), LocalDate.parse(b)).toInt()

/** 0 = lunes … 6 = domingo. */
fun weekdayLunes0(iso: String): Int = LocalDate.parse(iso).dayOfWeek.value - 1

fun mesAnterior(month: String): String = YearMonth.parse(month).minusMonths(1).toString()

fun mesSiguiente(month: String): String = YearMonth.parse(month).plusMonths(1).toString()

fun primerDiaMes(month: String): String = "$month-01"

fun ultimoDiaMes(month: String): String {
    val ym = YearMonth.parse(month)
    return isoFromParts(ym.year, ym.monthValue, ym.lengthOfMonth())
}

/** 'Septiembre 2026' */
fun nombreMes(month: String): String {
    val ym = YearMonth.parse(month)
    return "${NOMBRES_MES[ym.monthValue - 1].replaceFirstChar { it.uppercase() }} ${ym.year}"
}

/** 'viernes' */
fun nombreDia(iso: String): String = DIA_LARGO[weekdayLunes0(iso)]

/** '15 sep' */
fun fechaCorta(iso: String): String {
    val p = partsOf(iso)
    return "${p.d} ${MES_CORTO[p.m - 1]}"
}

/** 'vie 4' */
fun diaCorto(iso: String): String = "${DIA_CORTO[weekdayLunes0(iso)]} ${partsOf(iso).d}"

/** 'Viernes 4 de septiembre' */
fun fechaLarga(iso: String): String {
    val p = partsOf(iso)
    return "${nombreDia(iso).replaceFirstChar { it.uppercase() }} ${p.d} de ${NOMBRES_MES[p.m - 1]}"
}

/** '04/09/2026' */
fun ddmmyyyy(iso: String): String {
    val p = partsOf(iso)
    return "${p.d.toString().padStart(2, '0')}/${p.m.toString().padStart(2, '0')}/${p.y}"
}

data class Celda(val iso: String, val enMes: Boolean)

/** Grid mensual de 6 filas × 7 columnas (42 celdas fijas) desde el lunes ≤ día 1. */
fun celdasMes(month: String): List<Celda> {
    val primero = primerDiaMes(month)
    val inicio = addDays(primero, -weekdayLunes0(primero))
    return List(42) { i ->
        val iso = addDays(inicio, i)
        Celda(iso, iso.startsWith(month))
    }
}
```

- [ ] **Paso 8: Tests RED de tasas.** `RatesTest.kt`:

```kotlin
package com.cuentasclaras.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RatesTest {
    private val rates = listOf(
        Rate("2026-07-01", RateSource.BCV, 110.5),
        Rate("2026-07-03", RateSource.BCV, 111.02),
        Rate("2026-07-03", RateSource.BINANCE, 132.4),
        Rate("2026-07-02", RateSource.MANUAL, 120.0),
    )

    @Test fun `effectiveRate usa la fuente activa mas reciente y el override manual exacto gana`() {
        assertEquals(111.02, effectiveRate("2026-07-05", RateSource.BCV, rates)?.vesPerUsd)
        assertEquals(120.0, effectiveRate("2026-07-02", RateSource.BCV, rates)?.vesPerUsd)
        assertEquals(132.4, effectiveRate("2026-07-04", RateSource.BINANCE, rates)?.vesPerUsd)
    }

    @Test fun `effectiveRate sin entrada previa cae a cualquier fuente, luego a la mas antigua futura`() {
        assertEquals(110.5, effectiveRate("2026-07-01", RateSource.BINANCE, rates)?.vesPerUsd)
        assertEquals(110.5, effectiveRate("2026-06-01", RateSource.BCV, rates)?.vesPerUsd)
        assertNull(effectiveRate("2026-07-01", RateSource.BCV, emptyList()))
    }

    @Test fun `convertMinor ida y vuelta redondea como la web`() {
        assertEquals(55510L, convertMinor(500, Currency.USD, Currency.VES, 111.02))
        assertEquals(500L, convertMinor(55510, Currency.VES, Currency.USD, 111.02))
        assertEquals(1L, vesToUsdMinor(100, 111.02))
        assertEquals(1234L, convertMinor(1234, Currency.USD, Currency.USD, 111.0))
    }
}
```

- [ ] **Paso 9: Correr → FAIL.** `./gradlew :app:testDebugUnitTest --tests "*RatesTest" --console=plain 2>&1 | tail -25` → `Unresolved reference: effectiveRate`.

- [ ] **Paso 10: `Rates.kt`.**

```kotlin
package com.cuentasclaras.app.domain

/** Tasa vigente para una fecha según la fuente activa (mismas reglas que rates.ts):
 *  1) MANUAL exacta del día; 2) la más reciente ≤ fecha de la fuente activa;
 *  3) la más reciente ≤ fecha de cualquier fuente; 4) la más antigua posterior; 5) null. */
fun effectiveRate(dateIso: String, activeSource: RateSource, rates: List<Rate>): Rate? {
    rates.firstOrNull { it.source == RateSource.MANUAL && it.date == dateIso }?.let { return it }

    fun masReciente(candidatas: List<Rate>): Rate? =
        candidatas.reduceOrNull { a, b -> if (b.date > a.date) b else a }

    masReciente(rates.filter { it.source == activeSource && it.date <= dateIso })?.let { return it }
    masReciente(rates.filter { it.date <= dateIso })?.let { return it }
    return rates.filter { it.date > dateIso }.reduceOrNull { a, b -> if (b.date < a.date) b else a }
}

fun usdToVesMinor(usdMinor: Long, vesPerUsd: Double): Long = Math.round(usdMinor * vesPerUsd)

fun vesToUsdMinor(vesMinor: Long, vesPerUsd: Double): Long = Math.round(vesMinor / vesPerUsd)

fun convertMinor(amountMinor: Long, from: Currency, to: Currency, vesPerUsd: Double): Long {
    if (from == to) return amountMinor
    return if (from == Currency.USD) usdToVesMinor(amountMinor, vesPerUsd) else vesToUsdMinor(amountMinor, vesPerUsd)
}
```

- [ ] **Paso 11: Correr toda la suite → PASS.** `./gradlew :app:testDebugUnitTest --console=plain 2>&1 | tail -25`. Además `grep -rn "kotlin.math.round\|import android\|import androidx" android/app/src/main/java/com/cuentasclaras/app/domain` → **vacío**.

- [ ] **Paso 12: Commit.**

```bash
git add android/app/src/main/java/com/cuentasclaras/app/domain/Types.kt android/app/src/main/java/com/cuentasclaras/app/domain/Json.kt android/app/src/main/java/com/cuentasclaras/app/domain/Texto.kt android/app/src/main/java/com/cuentasclaras/app/domain/Money.kt android/app/src/main/java/com/cuentasclaras/app/domain/Dates.kt android/app/src/main/java/com/cuentasclaras/app/domain/Rates.kt android/app/src/test/java/com/cuentasclaras/app/domain/MoneyTest.kt android/app/src/test/java/com/cuentasclaras/app/domain/RatesTest.kt
git commit -m "feat(dominio): tipos, JSON, dinero, fechas y tasas portados a Kotlin" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Tarea 3: Saldos, grupos, estadísticas, deudas, reglas, filtros y builders

**Skills:** `superpowers:test-driven-development` (solo `BalancesTest`, `GroupsTest`, `StatsTest`) · `output-skill`.

**Files:**
- Create: `android/app/src/main/java/com/cuentasclaras/app/domain/Balances.kt`
- Create: `android/app/src/main/java/com/cuentasclaras/app/domain/Groups.kt`
- Create: `android/app/src/main/java/com/cuentasclaras/app/domain/Stats.kt`
- Create: `android/app/src/main/java/com/cuentasclaras/app/domain/Debts.kt`
- Create: `android/app/src/main/java/com/cuentasclaras/app/domain/Rules.kt`
- Create: `android/app/src/main/java/com/cuentasclaras/app/domain/Filters.kt`
- Create: `android/app/src/main/java/com/cuentasclaras/app/domain/Builders.kt`
- Test: `android/app/src/test/java/com/cuentasclaras/app/domain/BalancesTest.kt`
- Test: `android/app/src/test/java/com/cuentasclaras/app/domain/GroupsTest.kt`
- Test: `android/app/src/test/java/com/cuentasclaras/app/domain/StatsTest.kt`

Fuentes: `src/domain/balances.ts`, `groups.ts`, `stats.ts`, `debts.ts`, `rules.ts`, `filters.ts`, `builders.ts`.

**Interfaces:**
- Consumes (T2): tipos, `nuevoId`, `vesToUsdMinor`, `convertMinor`, `effectiveRate`, `comoKind`.
- Produces:
  - `accountBalanceMinor(accountId: String, txs: List<Transaction>): Long`; `data class PatrimonioLinea(account, balanceMinor: Long, balanceUsdMinor: Long?)`; `data class Patrimonio(lines, disposicionUsdMinor, ahorroUsdMinor, totalUsdMinor, aDisposicionUsdMinor, netoUsdMinor)` (todos `Long?`); `patrimonio(accounts, txs, vesPerUsd: Double?, deudasPorPagarPendientes: List<Debt> = emptyList()): Patrimonio`.
  - `data class ResiduoMoneda(currency, residuoMinor: Long)`; `residuosDeGrupo(miembros, accounts)`; `categoriaDelResiduo(grupo: TxGroup?, residuoMinor: Long, categories): String`; `txsEfectivas(txs, accounts, grupos = emptyList(), categories = emptyList()): List<Transaction>`.
  - `data class GastoCategoria(categoryId: String?, expenseMinor: Long)`, `IngresoCategoria(categoryId: String?, incomeMinor: Long)`, `PuntoDiario(day: Int, acumuladoMinor: Long)`, `ResumenMes(month, incomeMinor, expenseMinor, byCategory, incomeByCategory, dailyCumulative, omitidasSinTasa: Int)`; `monthSummary(txs, accounts, month, viewCurrency, rates, activeSource): ResumenMes`; `lastMonths(baseMonth, n): List<String>`; `data class EstadoPresupuesto(category, gastoUsdMinor: Long, presupuestoMinor: Long, ratio: Double)`; `budgetStatus(txs, accounts, categories, month, rates, activeSource): List<EstadoPresupuesto>`.
  - `buildDebt(direction, concepto, amountMinor, accountId, nowIso): Debt`; `buildSettleTx(debt, dateIso): Transaction`; `data class TotalesDeudas(debesUsdMinor: Long?, teDebenUsdMinor: Long?)`; `debtTotals(deudasPendientes, accounts, vesPerUsd: Double?)`.
  - `matchCategory(descripcion, rules): Rule?`; `buildRule(pattern, categoryId, autoCreated = true): Rule`.
  - `data class FiltroMovs(q: String? = null, accountId: String? = null, categoryId: String? = null, month: String? = null)`; `filterTxs(txs, f): List<Transaction>`.
  - `buildManualTx(type: TxType, accountId, amountMinor, categoryId, dateIso, note: String? = null)`; `buildTransfer(fromAccount, toAccount, amountMinor, dateIso, vesPerUsd: Double? = null, note: String? = null)`; `buildAdjustment(accountId, currentBalanceMinor, realBalanceMinor, dateIso, note: String? = null)`.
  - Errores de validación: `IllegalArgumentException` con el mensaje en español de la web (la UI muestra `e.message`).

- [ ] **Paso 1: Tests RED.** `BalancesTest.kt`:

```kotlin
package com.cuentasclaras.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BalancesTest {
    private val cuentas = listOf(
        Account("bnc", "BNC", Currency.VES, AccountKind.BANK, isSavings = false, archived = false),
        Account("efectivo", "Efectivo", Currency.USD, AccountKind.CASH, isSavings = false, archived = false),
        Account("binance", "Binance", Currency.USD, AccountKind.SNAPSHOT, isSavings = true, archived = false),
        Account("vieja", "Archivada", Currency.USD, AccountKind.CASH, isSavings = false, archived = true),
    )

    private fun tx(type: TxType, accountId: String, amountMinor: Long, toAccountId: String? = null, toAmountMinor: Long? = null) =
        Transaction(nuevoId(), type, accountId, amountMinor, "2026-07-01", source = TxSource.MANUAL,
            toAccountId = toAccountId, toAmountMinor = toAmountMinor)

    private val txs = listOf(
        tx(TxType.INCOME, "bnc", 500000),
        tx(TxType.EXPENSE, "bnc", 150000),
        tx(TxType.ADJUSTMENT, "binance", 25000),
        tx(TxType.TRANSFER, "efectivo", 1000, toAccountId = "bnc", toAmountMinor = 111000),
        tx(TxType.INCOME, "efectivo", 5000),
    )

    @Test fun `saldo por cuenta con transferencia cruzada y ajuste`() {
        assertEquals(461000L, accountBalanceMinor("bnc", txs))
        assertEquals(4000L, accountBalanceMinor("efectivo", txs))
        assertEquals(25000L, accountBalanceMinor("binance", txs))
        val sinDestino = listOf(tx(TxType.TRANSFER, "efectivo", 700, toAccountId = "binance"))
        assertEquals(700L, accountBalanceMinor("binance", sinDestino))
    }

    @Test fun `patrimonio en USD separa disposicion y ahorro y excluye archivadas`() {
        val p = patrimonio(cuentas, txs, 111.0)
        assertEquals(3, p.lines.size)
        assertEquals(4153L, p.lines.first { it.account.id == "bnc" }.balanceUsdMinor)
        assertEquals(25000L, p.ahorroUsdMinor)
        assertEquals(4153L + 4000L, p.disposicionUsdMinor)
        assertEquals(4153L + 4000L + 25000L, p.totalUsdMinor)
    }

    @Test fun `sin tasa lo no convertible queda null y lo USD sobrevive`() {
        val p = patrimonio(cuentas, txs, null)
        assertNull(p.lines.first { it.account.id == "bnc" }.balanceUsdMinor)
        assertNull(p.disposicionUsdMinor)
        assertEquals(25000L, p.ahorroUsdMinor)
        assertNull(p.totalUsdMinor)
    }

    @Test fun `vista a disposicion usa enDisposicion y el neto descuenta deudas por pagar convertidas`() {
        val flag = listOf(
            Account("usd1", "Efectivo", Currency.USD, AccountKind.CASH, false, false),
            Account("usd2", "Binance", Currency.USD, AccountKind.SNAPSHOT, true, false, enDisposicion = true),
            Account("usd3", "Guardado", Currency.USD, AccountKind.CASH, false, false, enDisposicion = false),
            Account("bs1", "BNC", Currency.VES, AccountKind.BANK, false, false),
        )
        val movs = listOf(tx(TxType.INCOME, "usd1", 1000), tx(TxType.INCOME, "usd2", 2000), tx(TxType.INCOME, "usd3", 4000))
        val deudas = listOf(
            Debt("a", DebtDirection.POR_PAGAR, "x", 500, "usd1", DebtStatus.PENDIENTE, "2026-07-22T00:00:00.000Z"),
            Debt("b", DebtDirection.POR_PAGAR, "y", 73723, "bs1", DebtStatus.PENDIENTE, "2026-07-22T00:00:00.000Z"),
        )
        val p = patrimonio(flag, movs, 737.23, deudas)
        assertEquals(3000L, p.aDisposicionUsdMinor)
        assertEquals(7000L, p.totalUsdMinor)
        assertEquals(7000L - 600L, p.netoUsdMinor)
        assertNull(patrimonio(flag, movs, null, deudas).netoUsdMinor)
    }
}
```

`GroupsTest.kt`:

```kotlin
package com.cuentasclaras.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GroupsTest {
    private val cuentas = listOf(
        Account("acc-efectivo", "Efectivo", Currency.USD, AccountKind.CASH, false, false),
        Account("acc-bnc", "BNC", Currency.VES, AccountKind.BANK, false, false),
    )
    private val categorias = listOf(
        Category("cat-supermercado", "Supermercado", "🛒", "#4ade80", CategoryKind.EXPENSE),
        Category("cat-sueldo", "Sueldo/Pagos", "💼", "#34d399", CategoryKind.INCOME),
        Category("cat-vuelto-ingreso", "Vuelto", "↩️", "#34d399", CategoryKind.INCOME),
        Category("cat-vuelto-gasto", "Vuelto", "↪️", "#f87171", CategoryKind.EXPENSE),
    )

    private fun tx(id: String, type: TxType, amountMinor: Long, groupId: String? = null, accountId: String = "acc-efectivo",
                   date: String = "2026-07-10", time: String? = null, categoryId: String? = null) =
        Transaction(id, type, accountId, amountMinor, date, time = time, categoryId = categoryId,
            source = TxSource.MANUAL, groupId = groupId)

    @Test fun `residuo por moneda firmado y residuo cero desaparece`() {
        assertEquals(listOf(ResiduoMoneda(Currency.USD, 150)),
            residuosDeGrupo(listOf(tx("a", TxType.INCOME, 500, "g1"), tx("b", TxType.EXPENSE, 350, "g1")), cuentas))
        assertEquals(emptyList(),
            residuosDeGrupo(listOf(tx("a", TxType.INCOME, 500, "g1"), tx("b", TxType.EXPENSE, 500, "g1")), cuentas))
        val bimoneda = residuosDeGrupo(listOf(tx("a", TxType.INCOME, 500, "g1"), tx("b", TxType.EXPENSE, 73723, "g1", "acc-bnc")), cuentas)
        assertTrue(ResiduoMoneda(Currency.USD, 500) in bimoneda)
        assertTrue(ResiduoMoneda(Currency.VES, -73723) in bimoneda)
    }

    @Test fun `categoriaDelResiduo exige que el kind coincida con el signo`() {
        val g = { cat: String? -> TxGroup("g1", categoryId = cat, createdIso = "x") }
        assertEquals("cat-supermercado", categoriaDelResiduo(g("cat-supermercado"), -200, categorias))
        assertEquals("cat-vuelto-ingreso", categoriaDelResiduo(g("cat-supermercado"), 150, categorias))
        assertEquals("cat-sueldo", categoriaDelResiduo(g("cat-sueldo"), 150, categorias))
        assertEquals("cat-vuelto-gasto", categoriaDelResiduo(g("cat-borrada"), -200, categorias))
        assertEquals("cat-vuelto-ingreso", categoriaDelResiduo(null, 150, categorias))
        assertEquals("cat-vuelto-gasto", categoriaDelResiduo(g("cat-supermercado"), -200, emptyList()))
    }

    @Test fun `txsEfectivas excluye los miembros y deja un virtual con la fecha del mas reciente`() {
        val txs = listOf(
            tx("s1", TxType.EXPENSE, 999),
            tx("m1", TxType.INCOME, 500, "g1", date = "2026-07-08"),
            tx("m2", TxType.EXPENSE, 350, "g1", date = "2026-07-10", time = "09:30:00"),
        )
        val efectivas = txsEfectivas(txs, cuentas, listOf(TxGroup("g1", nota = "leche", createdIso = "x")))
        assertEquals(listOf("s1", "virtual-g1-USD"), efectivas.map { it.id })
        val virtual = efectivas.last()
        assertEquals(TxType.INCOME, virtual.type)
        assertEquals(150L, virtual.amountMinor)
        assertEquals("cat-vuelto-ingreso", virtual.categoryId)
        assertEquals("2026-07-10", virtual.date)
        assertEquals("09:30:00", virtual.time)
        assertEquals("leche", virtual.note)
    }

    @Test fun `bimoneda evalua la categoria del grupo contra el signo de cada residuo`() {
        val txs = listOf(tx("m1", TxType.INCOME, 500, "g1"), tx("m2", TxType.EXPENSE, 73723, "g1", "acc-bnc"))
        val efectivas = txsEfectivas(txs, cuentas, listOf(TxGroup("g1", categoryId = "cat-supermercado", createdIso = "x")), categorias)
        assertEquals("cat-vuelto-ingreso", efectivas.first { it.id == "virtual-g1-USD" }.categoryId)
        assertEquals("cat-supermercado", efectivas.first { it.id == "virtual-g1-VES" }.categoryId)
    }
}
```

`StatsTest.kt` (incluye «monthSummary con grupos y residuos», spec §9):

```kotlin
package com.cuentasclaras.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class StatsTest {
    private val cuentas = listOf(
        Account("bnc", "BNC", Currency.VES, AccountKind.BANK, false, false),
        Account("efectivo", "Efectivo", Currency.USD, AccountKind.CASH, false, false),
    )
    private val rates = listOf(Rate("2026-07-01", RateSource.BCV, 100.0))
    private fun tx(type: TxType, amountMinor: Long, date: String, accountId: String = "efectivo",
                   categoryId: String? = null, groupId: String? = null) =
        Transaction(nuevoId(), type, accountId, amountMinor, date, categoryId = categoryId,
            source = TxSource.MANUAL, groupId = groupId)

    private val txs = listOf(
        tx(TxType.EXPENSE, 100000, "2026-07-05", "bnc", "cat-a"),
        tx(TxType.EXPENSE, 500, "2026-07-10", categoryId = "cat-a"),
        tx(TxType.EXPENSE, 300, "2026-07-10", categoryId = "cat-b"),
        tx(TxType.INCOME, 2000, "2026-07-03"),
        tx(TxType.TRANSFER, 999, "2026-07-05"),
        tx(TxType.ADJUSTMENT, 999, "2026-07-05"),
        tx(TxType.EXPENSE, 777, "2026-06-30"),
    )

    @Test fun `resume el mes en USD con categorias desc y acumulado diario`() {
        val r = monthSummary(txs, cuentas, "2026-07", Currency.USD, rates, RateSource.BCV)
        assertEquals(2000L, r.incomeMinor)
        assertEquals(1800L, r.expenseMinor)
        assertEquals(listOf(GastoCategoria("cat-a", 1500), GastoCategoria("cat-b", 300)), r.byCategory)
        assertEquals(listOf(IngresoCategoria(null, 2000)), r.incomeByCategory)
        assertEquals(31, r.dailyCumulative.size)
        assertEquals(PuntoDiario(4, 0), r.dailyCumulative[3])
        assertEquals(PuntoDiario(5, 1000), r.dailyCumulative[4])
        assertEquals(PuntoDiario(31, 1800), r.dailyCumulative[30])
    }

    @Test fun `sin tasa omite y cuenta las VES; vista en Bs convierte las USD`() {
        val sinTasa = monthSummary(txs, cuentas, "2026-07", Currency.USD, emptyList(), RateSource.BCV)
        assertEquals(1, sinTasa.omitidasSinTasa)
        assertEquals(800L, sinTasa.expenseMinor)
        assertEquals(180000L, monthSummary(txs, cuentas, "2026-07", Currency.VES, rates, RateSource.BCV).expenseMinor)
    }

    @Test fun `con grupos solo cuenta el residuo y el residuo con categoria consume presupuesto`() {
        val categorias = listOf(
            Category("cat-supermercado", "Supermercado", "🛒", "#fff", CategoryKind.EXPENSE, monthlyBudgetMinor = 10000),
            Category("cat-vuelto-gasto", "Vuelto", "↪️", "#f87171", CategoryKind.EXPENSE),
        )
        val conGrupo = listOf(
            tx(TxType.EXPENSE, 2000, "2026-07-10", categoryId = "cat-otros"),
            tx(TxType.INCOME, 300, "2026-07-10", groupId = "g1"),
            tx(TxType.EXPENSE, 500, "2026-07-10", categoryId = "cat-otros", groupId = "g1"),
        )
        val efectivas = txsEfectivas(conGrupo, cuentas, listOf(TxGroup("g1", categoryId = "cat-supermercado", createdIso = "x")), categorias)
        val r = monthSummary(efectivas, cuentas, "2026-07", Currency.USD, emptyList(), RateSource.BCV)
        assertEquals(2200L, r.expenseMinor)
        assertEquals(0L, r.incomeMinor)
        assertEquals(listOf(GastoCategoria("cat-otros", 2000), GastoCategoria("cat-supermercado", 200)), r.byCategory)
        val presupuesto = budgetStatus(efectivas, cuentas, categorias, "2026-07", emptyList(), RateSource.BCV)
        assertEquals(200L, presupuesto.single().gastoUsdMinor)
        assertEquals(0.02, presupuesto.single().ratio)
        assertEquals(listOf("2026-01", "2025-12", "2025-11"), lastMonths("2026-01", 3))
    }
}
```

- [ ] **Paso 2: Correr → FAIL** (`./gradlew :app:testDebugUnitTest --tests "*BalancesTest" --tests "*GroupsTest" --tests "*StatsTest" --console=plain 2>&1 | tail -30`): referencias sin resolver.

- [ ] **Paso 3: `Balances.kt`.**

```kotlin
package com.cuentasclaras.app.domain

fun accountBalanceMinor(accountId: String, txs: List<Transaction>): Long {
    var saldo = 0L
    for (t in txs) {
        if (t.accountId == accountId) {
            saldo += when (t.type) {
                TxType.INCOME -> t.amountMinor
                TxType.EXPENSE, TxType.TRANSFER -> -t.amountMinor
                TxType.ADJUSTMENT -> t.amountMinor // delta con signo
            }
        }
        if (t.type == TxType.TRANSFER && t.toAccountId == accountId) {
            saldo += t.toAmountMinor ?: t.amountMinor
        }
    }
    return saldo
}

data class PatrimonioLinea(val account: Account, val balanceMinor: Long, val balanceUsdMinor: Long?)

data class Patrimonio(
    val lines: List<PatrimonioLinea>,
    val disposicionUsdMinor: Long?,
    val ahorroUsdMinor: Long?,
    val totalUsdMinor: Long?,
    /** v2 — vista 1 del slider: cuentas con enDisposicion (default !isSavings). */
    val aDisposicionUsdMinor: Long?,
    /** v2 — vista 2: total − deudas por pagar pendientes (convertidas a USD). */
    val netoUsdMinor: Long?,
)

/** Patrimonio consolidado en USD. vesPerUsd null → sin tasa: lo no convertible queda null.
 *  Usa transacciones CRUDAS (nunca txsEfectivas). */
fun patrimonio(
    accounts: List<Account>,
    txs: List<Transaction>,
    vesPerUsd: Double?,
    deudasPorPagarPendientes: List<Debt> = emptyList(),
): Patrimonio {
    val lines = accounts.filter { !it.archived }.map { account ->
        val balanceMinor = accountBalanceMinor(account.id, txs)
        val balanceUsdMinor = when {
            account.currency == Currency.USD -> balanceMinor
            vesPerUsd == null -> null
            else -> vesToUsdMinor(balanceMinor, vesPerUsd)
        }
        PatrimonioLinea(account, balanceMinor, balanceUsdMinor)
    }

    fun suma(grupo: List<PatrimonioLinea>): Long? =
        if (grupo.any { it.balanceUsdMinor == null }) null else grupo.sumOf { it.balanceUsdMinor ?: 0L }

    val disposicionUsdMinor = suma(lines.filter { !it.account.isSavings })
    val ahorroUsdMinor = suma(lines.filter { it.account.isSavings })
    val totalUsdMinor =
        if (disposicionUsdMinor == null || ahorroUsdMinor == null) null else disposicionUsdMinor + ahorroUsdMinor
    val aDisposicionUsdMinor = suma(lines.filter { it.account.enDisposicion ?: !it.account.isSavings })

    val monedaDe = accounts.associate { it.id to it.currency }
    var deudasUsdMinor = 0L
    var deudaSinTasa = false
    for (deuda in deudasPorPagarPendientes) {
        val moneda = monedaDe[deuda.accountId] ?: Currency.VES
        when {
            moneda == Currency.USD -> deudasUsdMinor += deuda.amountMinor
            vesPerUsd == null -> { deudaSinTasa = true; break }
            else -> deudasUsdMinor += vesToUsdMinor(deuda.amountMinor, vesPerUsd)
        }
    }
    val netoUsdMinor = if (totalUsdMinor == null || deudaSinTasa) null else totalUsdMinor - deudasUsdMinor

    return Patrimonio(lines, disposicionUsdMinor, ahorroUsdMinor, totalUsdMinor, aDisposicionUsdMinor, netoUsdMinor)
}
```

- [ ] **Paso 4: `Groups.kt`.**

```kotlin
package com.cuentasclaras.app.domain

import kotlin.math.abs

data class ResiduoMoneda(
    val currency: Currency,
    /** Firmado: + ingreso (vuelto a favor), − gasto. */
    val residuoMinor: Long,
)

/** Σ firmada de los miembros por moneda (ingresos +, gastos −). Transferencias/ajustes se ignoran. */
fun residuosDeGrupo(miembros: List<Transaction>, accounts: List<Account>): List<ResiduoMoneda> {
    val monedaDe = accounts.associate { it.id to it.currency }
    val porMoneda = LinkedHashMap<Currency, Long>()
    for (t in miembros) {
        if (t.type != TxType.INCOME && t.type != TxType.EXPENSE) continue
        val moneda = monedaDe[t.accountId] ?: Currency.USD
        val firmado = if (t.type == TxType.INCOME) t.amountMinor else -t.amountMinor
        porMoneda[moneda] = (porMoneda[moneda] ?: 0L) + firmado
    }
    return porMoneda.filter { it.value != 0L }.map { ResiduoMoneda(it.key, it.value) }
}

private fun masReciente(txs: List<Transaction>): Transaction =
    txs.reduce { a, b -> if ("${b.date} ${b.time ?: ""}" > "${a.date} ${a.time ?: ""}") b else a }

/** Categoría del residuo virtual (v3): la del grupo SOLO si su kind coincide con el signo
 *  (expense ↔ residuo < 0, income ↔ residuo > 0); si no, «Vuelto» del signo (v2). */
fun categoriaDelResiduo(grupo: TxGroup?, residuoMinor: Long, categories: List<Category>): String {
    val necesario = if (residuoMinor > 0) CategoryKind.INCOME else CategoryKind.EXPENSE
    val elegida = grupo?.categoryId?.let { id -> categories.firstOrNull { it.id == id } }
    if (elegida != null && elegida.kind == necesario) return elegida.id
    return if (residuoMinor > 0) "cat-vuelto-ingreso" else "cat-vuelto-gasto"
}

/** Sustituye los movimientos agrupados por su residuo virtual. SOLO para agregaciones;
 *  NUNCA para saldos. Los virtuales (`virtual-…`) jamás se persisten. */
fun txsEfectivas(
    txs: List<Transaction>,
    accounts: List<Account>,
    grupos: List<TxGroup> = emptyList(),
    categories: List<Category> = emptyList(),
): List<Transaction> {
    val grupoDe = grupos.associateBy { it.id }
    val monedaDe = accounts.associate { it.id to it.currency }
    val sueltas = mutableListOf<Transaction>()
    val porGrupo = LinkedHashMap<String, MutableList<Transaction>>()
    for (t in txs) {
        val gid = t.groupId
        if (gid != null) porGrupo.getOrPut(gid) { mutableListOf() }.add(t) else sueltas.add(t)
    }
    val virtuales = mutableListOf<Transaction>()
    for ((groupId, miembros) in porGrupo) {
        val ultimo = masReciente(miembros)
        val grupo = grupoDe[groupId]
        for ((currency, residuoMinor) in residuosDeGrupo(miembros, accounts)) {
            val deLaMoneda = miembros.filter { (monedaDe[it.accountId] ?: Currency.USD) == currency }
            virtuales.add(
                Transaction(
                    id = "virtual-$groupId-${currency.name}",
                    type = if (residuoMinor > 0) TxType.INCOME else TxType.EXPENSE,
                    accountId = masReciente(deLaMoneda).accountId,
                    amountMinor = abs(residuoMinor),
                    date = ultimo.date,
                    time = ultimo.time,
                    categoryId = categoriaDelResiduo(grupo, residuoMinor, categories),
                    note = grupo?.nota?.takeIf { it.isNotEmpty() } ?: "Vuelto de grupo transaccional",
                    source = TxSource.MANUAL,
                    groupId = groupId,
                ),
            )
        }
    }
    return sueltas + virtuales
}
```

- [ ] **Paso 5: `Stats.kt`.**

```kotlin
package com.cuentasclaras.app.domain

import java.time.YearMonth

data class GastoCategoria(val categoryId: String?, val expenseMinor: Long)
data class IngresoCategoria(val categoryId: String?, val incomeMinor: Long)
data class PuntoDiario(val day: Int, val acumuladoMinor: Long)

data class ResumenMes(
    val month: String,
    val incomeMinor: Long,
    val expenseMinor: Long,
    /** SOLO gastos, desc por monto. */
    val byCategory: List<GastoCategoria>,
    /** v3: ingresos por categoría, desc por monto. */
    val incomeByCategory: List<IngresoCategoria>,
    val dailyCumulative: List<PuntoDiario>,
    val omitidasSinTasa: Int,
)

fun monthSummary(
    txs: List<Transaction>,
    accounts: List<Account>,
    month: String,
    viewCurrency: Currency,
    rates: List<Rate>,
    activeSource: RateSource,
): ResumenMes {
    val monedaDe = accounts.associate { it.id to it.currency }
    var incomeMinor = 0L
    var expenseMinor = 0L
    var omitidasSinTasa = 0
    val porCategoria = LinkedHashMap<String?, Long>()
    val porCategoriaIngreso = LinkedHashMap<String?, Long>()
    val porDia = HashMap<Int, Long>()

    for (t in txs) {
        if (!t.date.startsWith(month)) continue
        if (t.type != TxType.EXPENSE && t.type != TxType.INCOME) continue
        val desde = monedaDe[t.accountId] ?: Currency.USD
        var minor = t.amountMinor
        if (desde != viewCurrency) {
            val rate = effectiveRate(t.date, activeSource, rates)
            if (rate == null) {
                omitidasSinTasa++
                continue
            }
            minor = convertMinor(minor, desde, viewCurrency, rate.vesPerUsd)
        }
        if (t.type == TxType.INCOME) {
            incomeMinor += minor
            porCategoriaIngreso[t.categoryId] = (porCategoriaIngreso[t.categoryId] ?: 0L) + minor
        } else {
            expenseMinor += minor
            porCategoria[t.categoryId] = (porCategoria[t.categoryId] ?: 0L) + minor
            val dia = t.date.substring(8, 10).toInt()
            porDia[dia] = (porDia[dia] ?: 0L) + minor
        }
    }

    val diasDelMes = YearMonth.parse(month).lengthOfMonth()
    var acumulado = 0L
    val dailyCumulative = List(diasDelMes) { i ->
        acumulado += porDia[i + 1] ?: 0L
        PuntoDiario(i + 1, acumulado)
    }
    val byCategory = porCategoria.map { GastoCategoria(it.key, it.value) }.sortedByDescending { it.expenseMinor }
    val incomeByCategory = porCategoriaIngreso.map { IngresoCategoria(it.key, it.value) }.sortedByDescending { it.incomeMinor }
    return ResumenMes(month, incomeMinor, expenseMinor, byCategory, incomeByCategory, dailyCumulative, omitidasSinTasa)
}

/** lastMonths('2026-01', 3) → ['2026-01', '2025-12', '2025-11'] */
fun lastMonths(baseMonth: String, n: Int): List<String> {
    val base = YearMonth.parse(baseMonth)
    return List(n) { i -> base.minusMonths(i.toLong()).toString() }
}

data class EstadoPresupuesto(
    val category: Category,
    val gastoUsdMinor: Long,
    val presupuestoMinor: Long,
    val ratio: Double,
)

fun budgetStatus(
    txs: List<Transaction>,
    accounts: List<Account>,
    categories: List<Category>,
    month: String,
    rates: List<Rate>,
    activeSource: RateSource,
): List<EstadoPresupuesto> {
    val resumenUsd = monthSummary(txs, accounts, month, Currency.USD, rates, activeSource)
    return categories
        .filter { it.kind == CategoryKind.EXPENSE && (it.monthlyBudgetMinor ?: 0L) > 0L }
        .map { category ->
            val gasto = resumenUsd.byCategory.firstOrNull { it.categoryId == category.id }?.expenseMinor ?: 0L
            val presupuesto = category.monthlyBudgetMinor ?: 0L
            EstadoPresupuesto(category, gasto, presupuesto, gasto.toDouble() / presupuesto)
        }
}
```

- [ ] **Paso 6: Correr → PASS** (mismo comando del Paso 2).

- [ ] **Paso 7: `Debts.kt`, `Rules.kt`, `Filters.kt`, `Builders.kt`** (sin tests; regla 12).

```kotlin
package com.cuentasclaras.app.domain

fun buildDebt(
    direction: DebtDirection,
    concepto: String,
    amountMinor: Long,
    accountId: String,
    nowIso: String,
): Debt {
    require(amountMinor > 0) { "Monto inválido" }
    require(concepto.isNotBlank()) { "Ponle un concepto a la deuda" }
    return Debt(nuevoId(), direction, concepto.trim(), amountMinor, accountId, DebtStatus.PENDIENTE, nowIso)
}

/** Movimiento real de cierre: gasto (por pagar) o ingreso (por cobrar), en la moneda de la
 *  cuenta asignada, SIN conversión (decisión 2 del spec v2). */
fun buildSettleTx(debt: Debt, dateIso: String): Transaction {
    require(debt.status == DebtStatus.PENDIENTE) { "La deuda ya está cerrada" }
    val porPagar = debt.direction == DebtDirection.POR_PAGAR
    return Transaction(
        id = nuevoId(),
        type = if (porPagar) TxType.EXPENSE else TxType.INCOME,
        accountId = debt.accountId,
        amountMinor = debt.amountMinor,
        date = dateIso,
        categoryId = if (porPagar) "cat-deudas" else "cat-transferencia-recibida",
        note = "${if (porPagar) "Deuda pagada" else "Deuda cobrada"}: ${debt.concepto}",
        source = TxSource.MANUAL,
    )
}

data class TotalesDeudas(val debesUsdMinor: Long?, val teDebenUsdMinor: Long?)

/** Totales de pendientes en USD SOLO para mostrar. Deuda en Bs sin tasa → su total queda null. */
fun debtTotals(deudasPendientes: List<Debt>, accounts: List<Account>, vesPerUsd: Double?): TotalesDeudas {
    val monedaDe = accounts.associate { it.id to it.currency }
    fun sumar(deudas: List<Debt>): Long? {
        var total = 0L
        for (deuda in deudas) {
            val moneda = monedaDe[deuda.accountId] ?: Currency.USD
            total += when {
                moneda == Currency.USD -> deuda.amountMinor
                vesPerUsd == null -> return null
                else -> vesToUsdMinor(deuda.amountMinor, vesPerUsd)
            }
        }
        return total
    }
    return TotalesDeudas(
        debesUsdMinor = sumar(deudasPendientes.filter { it.direction == DebtDirection.POR_PAGAR }),
        teDebenUsdMinor = sumar(deudasPendientes.filter { it.direction == DebtDirection.POR_COBRAR }),
    )
}
```

```kotlin
package com.cuentasclaras.app.domain

/** Primera regla cuyo patrón (substring, case-insensitive) aparece en la descripción. */
fun matchCategory(descripcion: String, rules: List<Rule>): Rule? {
    val d = descripcion.lowercase()
    return rules.firstOrNull { r ->
        val p = r.pattern.trim().lowercase()
        p.isNotEmpty() && d.contains(p)
    }
}

fun buildRule(pattern: String, categoryId: String, autoCreated: Boolean = true): Rule {
    val p = pattern.trim()
    require(p.isNotEmpty()) { "Patrón vacío" }
    return Rule(nuevoId(), p, categoryId, autoCreated)
}
```

```kotlin
package com.cuentasclaras.app.domain

data class FiltroMovs(
    val q: String? = null,
    val accountId: String? = null,
    val categoryId: String? = null,
    /** 'yyyy-mm' */
    val month: String? = null,
)

fun filterTxs(txs: List<Transaction>, f: FiltroMovs): List<Transaction> {
    val q = f.q?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
    return txs
        .filter { f.accountId.isNullOrEmpty() || it.accountId == f.accountId || it.toAccountId == f.accountId }
        .filter { f.categoryId.isNullOrEmpty() || it.categoryId == f.categoryId }
        .filter { f.month.isNullOrEmpty() || it.date.startsWith(f.month) }
        .filter { q == null || (it.note ?: "").lowercase().contains(q) }
        .sortedByDescending { it.date + (it.time ?: "") }
}
```

```kotlin
package com.cuentasclaras.app.domain

fun buildManualTx(
    type: TxType,
    accountId: String,
    amountMinor: Long,
    categoryId: String,
    dateIso: String,
    note: String? = null,
): Transaction {
    require(type == TxType.EXPENSE || type == TxType.INCOME) { "Tipo inválido" }
    require(amountMinor > 0) { "Monto inválido" }
    return Transaction(
        id = nuevoId(), type = type, accountId = accountId, amountMinor = amountMinor,
        date = dateIso, categoryId = categoryId, note = note, source = TxSource.MANUAL,
    )
}

fun buildTransfer(
    fromAccount: Account,
    toAccount: Account,
    amountMinor: Long,
    dateIso: String,
    vesPerUsd: Double? = null,
    note: String? = null,
): Transaction {
    require(fromAccount.id != toAccount.id) { "Origen y destino no pueden ser la misma cuenta" }
    require(amountMinor > 0) { "Monto inválido" }
    val cruzaMoneda = fromAccount.currency != toAccount.currency
    require(!cruzaMoneda || (vesPerUsd != null && vesPerUsd > 0)) { "Falta la tasa para convertir" }
    val toAmountMinor =
        if (cruzaMoneda) convertMinor(amountMinor, fromAccount.currency, toAccount.currency, vesPerUsd!!) else amountMinor
    return Transaction(
        id = nuevoId(),
        type = TxType.TRANSFER,
        accountId = fromAccount.id,
        toAccountId = toAccount.id,
        amountMinor = amountMinor,
        toAmountMinor = toAmountMinor,
        rateUsed = if (cruzaMoneda) vesPerUsd else null,
        date = dateIso,
        note = note,
        source = TxSource.MANUAL,
    )
}

fun buildAdjustment(
    accountId: String,
    currentBalanceMinor: Long,
    realBalanceMinor: Long,
    dateIso: String,
    note: String? = null,
): Transaction {
    val delta = realBalanceMinor - currentBalanceMinor
    require(delta != 0L) { "No hay diferencia que ajustar" }
    return Transaction(
        id = nuevoId(), type = TxType.ADJUSTMENT, accountId = accountId, amountMinor = delta,
        date = dateIso, note = note ?: "Ajuste de saldo", source = TxSource.ADJUST,
    )
}
```

- [ ] **Paso 8: Suite completa → PASS** (`./gradlew :app:testDebugUnitTest --console=plain 2>&1 | tail -25`) y `assembleDebug` compila.

- [ ] **Paso 9: Commit.**

```bash
git add android/app/src/main/java/com/cuentasclaras/app/domain/Balances.kt android/app/src/main/java/com/cuentasclaras/app/domain/Groups.kt android/app/src/main/java/com/cuentasclaras/app/domain/Stats.kt android/app/src/main/java/com/cuentasclaras/app/domain/Debts.kt android/app/src/main/java/com/cuentasclaras/app/domain/Rules.kt android/app/src/main/java/com/cuentasclaras/app/domain/Filters.kt android/app/src/main/java/com/cuentasclaras/app/domain/Builders.kt android/app/src/test/java/com/cuentasclaras/app/domain/BalancesTest.kt android/app/src/test/java/com/cuentasclaras/app/domain/GroupsTest.kt android/app/src/test/java/com/cuentasclaras/app/domain/StatsTest.kt
git commit -m "feat(dominio): saldos, grupos, estadísticas, deudas, reglas, filtros y builders" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Tarea 4: Calendario de eventos y sugerencia de ocurrencias

**Skills:** `superpowers:test-driven-development` (`CalendarTest`, `EventMatchTest`) · `output-skill`.

**Files:**
- Create: `android/app/src/main/java/com/cuentasclaras/app/domain/Calendar.kt`
- Create: `android/app/src/main/java/com/cuentasclaras/app/domain/EventMatch.kt`
- Test: `android/app/src/test/java/com/cuentasclaras/app/domain/CalendarTest.kt`
- Test: `android/app/src/test/java/com/cuentasclaras/app/domain/EventMatchTest.kt`

Fuentes: `src/domain/calendar.ts`, `src/domain/eventMatch.ts`.

**Interfaces:**
- Consumes (T2): tipos, fechas (`addDays`, `daysInMonth`, `ddmmyyyy`, `diffDias`, `esFechaIso`, `isoFromParts`, `mesSiguiente`, `monthOf`, `nombreDia`, `partsOf`, `MES_CORTO`), `compararTexto`, `nuevoId`, `comoKind`.
- Produces:
  - `enum class EstadoOcurrencia { PENDIENTE, HECHA, VENCIDA, OMITIDA }`; `data class Ocurrencia(eventId: String, date: String)`; `data class MontoMoneda(currency, totalMinor: Long)`; `data class OcurrenciaVista(eventId, date, evento: CalEvent, estado, txIds: List<String>, asignadoPorMoneda: List<MontoMoneda>, fueraDeRegla: Boolean)` con `val ocurrencia: Ocurrencia`.
  - `RECURRENCIA_QUINCENAL`, `esQuincenal(r)`, `parseDias(texto): List<Int>`, `validarEvento(e)`, `buildEvent(titulo, categoryId, accountId?, amountMinor?, startDate, endDate?, recurrence, omitidas?, nowIso): CalEvent`, `fechasDeEvento(e, desde, hasta): List<String>`, `kindDeEvento(e, categories): CategoryKind?`, `estadoDe(date, omitida, txIds, hoy)`, `ocurrenciasEnRango(eventos, txs, accounts, desde, hasta, hoy): List<OcurrenciaVista>`, `data class TotalesEsperados(pagar, cobrar)`, `totalesEsperados(ocurrencias, accounts, categories)`, `descripcionRecurrencia(e)`.
  - `data class TxParaSugerir(type: CategoryKind, date, accountId, amountMinor: Long)`; `data class CtxSugerencia(eventos, txs, accounts, categories, hoy)`; `VENTANA_ATRAS_DIAS = 31`, `VENTANA_ADELANTE_DIAS = 7`, `TOLERANCIA_MONTO = 0.10`; `data class Candidata(ocurrencia: OcurrenciaVista, distanciaDias: Int, montoCoincide: Boolean?, sugerible: Boolean)`; `candidatasParaTx(tx, ctx, incluirHechas = false)`; `sugerirOcurrencia(tx, ctx): OcurrenciaVista?`; `sugerirParaLote(txs, ctx): Map<String, Ocurrencia>`; `aplicarAsignacion(tx, o: Ocurrencia?, evento: CalEvent?): Transaction`; `validarAsignacion(txType: TxType, evento, categories)`.

- [ ] **Paso 1: Tests RED.** `CalendarTest.kt` (spec §9: `fechasDeEvento` por recurrencia —mensual con 31 = último día, quincenal— y `estadoDe`):

```kotlin
package com.cuentasclaras.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CalendarTest {
    private val cuentas = listOf(
        Account("acc-bnc", "BNC", Currency.VES, AccountKind.BANK, false, false),
        Account("acc-zinli", "Zinli", Currency.USD, AccountKind.WALLET, false, false),
    )
    private fun ev(id: String = "e1", titulo: String = "Quincena", startDate: String = "2026-01-01",
                   endDate: String? = null, recurrence: Recurrence = RECURRENCIA_QUINCENAL,
                   omitidas: List<String>? = null, categoryId: String = "cat-sueldo") =
        CalEvent(id, titulo, categoryId, startDate = startDate, endDate = endDate,
            recurrence = recurrence, omitidas = omitidas, createdIso = "2026-09-04T10:00:00.000Z")

    @Test fun `quincenal recorta el 31 al ultimo dia del mes, incluido febrero bisiesto`() {
        assertEquals(listOf("2026-01-15", "2026-01-31"), fechasDeEvento(ev(), "2026-01-01", "2026-01-31"))
        assertEquals(listOf("2026-02-15", "2026-02-28"), fechasDeEvento(ev(), "2026-02-01", "2026-02-28"))
        assertEquals(listOf("2026-04-15", "2026-04-30"), fechasDeEvento(ev(), "2026-04-01", "2026-04-30"))
        assertEquals(listOf("2024-02-15", "2024-02-29"), fechasDeEvento(ev(startDate = "2024-01-01"), "2024-02-01", "2024-02-29"))
        assertEquals(listOf("2026-02-28"),
            fechasDeEvento(ev(recurrence = Recurrence.Monthly(listOf(30, 31))), "2026-02-01", "2026-02-28"))
        assertEquals(listOf("2026-09-15"), fechasDeEvento(ev(endDate = "2026-09-15"), "2026-09-01", "2026-09-30"))
        assertEquals(emptyList(),
            fechasDeEvento(ev(startDate = "2026-09-20", recurrence = Recurrence.Monthly(listOf(15))), "2026-09-01", "2026-09-30"))
    }

    @Test fun `semanal salta al primer multiplo, una vez ignora el fin y anual recorta el 29 de febrero`() {
        val semanal = ev(startDate = "2026-09-02", recurrence = Recurrence.Weekly)
        assertEquals(listOf("2026-10-07", "2026-10-14", "2026-10-21", "2026-10-28"), fechasDeEvento(semanal, "2026-10-01", "2026-10-31"))
        assertEquals(emptyList(), fechasDeEvento(semanal, "2026-08-01", "2026-08-31"))
        val once = ev(startDate = "2026-10-20", recurrence = Recurrence.Once, endDate = "2026-01-01")
        assertEquals(listOf("2026-10-20"), fechasDeEvento(once, "2026-10-20", "2026-10-20"))
        val anual = ev(startDate = "2024-02-29", recurrence = Recurrence.Yearly)
        assertEquals(listOf("2025-02-28", "2026-02-28"), fechasDeEvento(anual, "2025-01-01", "2026-12-31"))
    }

    @Test fun `estadoDe hecha gana a omitida gana a vencida y hoy es pendiente`() {
        val hoy = "2026-09-04"
        assertEquals(EstadoOcurrencia.HECHA, estadoDe("2026-08-31", true, listOf("t1"), hoy))
        assertEquals(EstadoOcurrencia.OMITIDA, estadoDe("2026-08-31", true, emptyList(), hoy))
        assertEquals(EstadoOcurrencia.VENCIDA, estadoDe("2026-08-31", false, emptyList(), hoy))
        assertEquals(EstadoOcurrencia.PENDIENTE, estadoDe("2026-09-04", false, emptyList(), hoy))
    }

    @Test fun `ocurrenciasEnRango une generadas y asignadas, suma por moneda y marca fueraDeRegla`() {
        val eventos = listOf(
            ev(omitidas = listOf("2026-09-30")),
            ev(id = "e2", titulo = "Netflix", categoryId = "cat-suscripciones", startDate = "2026-01-05",
                recurrence = Recurrence.Monthly(listOf(5))),
        )
        fun tx(id: String, eventDate: String, accountId: String = "acc-bnc", monto: Long = 800000, eventId: String = "e1") =
            Transaction(id, TxType.INCOME, accountId, monto, "2026-09-15", source = TxSource.MANUAL,
                eventId = eventId, eventDate = eventDate)
        val txs = listOf(
            tx("a", "2026-09-15", monto = 500000), tx("b", "2026-09-15", monto = 300000),
            tx("c", "2026-09-15", "acc-zinli", 1000), tx("d", "2026-09-15", eventId = "fantasma"),
            tx("rara", "2026-09-07"),
        )
        val o = ocurrenciasEnRango(eventos, txs, cuentas, "2026-09-01", "2026-09-30", "2026-09-04")
        assertEquals(
            listOf("2026-09-05 Netflix PENDIENTE", "2026-09-07 Quincena HECHA", "2026-09-15 Quincena HECHA", "2026-09-30 Quincena OMITIDA"),
            o.map { "${it.date} ${it.evento.titulo} ${it.estado}" },
        )
        val quincena = o.first { it.date == "2026-09-15" }
        assertEquals(listOf("a", "b", "c"), quincena.txIds.sorted())
        assertTrue(MontoMoneda(Currency.VES, 800000) in quincena.asignadoPorMoneda)
        assertTrue(MontoMoneda(Currency.USD, 1000) in quincena.asignadoPorMoneda)
        assertEquals(true, o.first { it.date == "2026-09-07" }.fueraDeRegla)
        assertEquals(false, quincena.fueraDeRegla)
    }
}
```

`EventMatchTest.kt` (spec §9: `sugerirOcurrencia` dentro y fuera de ventana y de tolerancia):

```kotlin
package com.cuentasclaras.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class EventMatchTest {
    private val cuentas = listOf(
        Account("acc-bnc", "BNC", Currency.VES, AccountKind.BANK, false, false),
        Account("acc-zinli", "Zinli", Currency.USD, AccountKind.WALLET, false, false),
    )
    private val categorias = listOf(
        Category("cat-sueldo", "Sueldo/Pagos", "💼", "#34d399", CategoryKind.INCOME),
        Category("cat-suscripciones", "Suscripciones", "📺", "#22d3ee", CategoryKind.EXPENSE),
    )
    private val quincena = CalEvent(
        "e-quincena", "Quincena", "cat-sueldo", accountId = "acc-bnc", amountMinor = 800000,
        startDate = "2026-01-01", recurrence = RECURRENCIA_QUINCENAL, createdIso = "2026-09-04T10:00:00.000Z",
    )
    private fun ctx(eventos: List<CalEvent> = listOf(quincena), txs: List<Transaction> = emptyList()) =
        CtxSugerencia(eventos, txs, cuentas, categorias, "2026-09-04")
    private fun ingreso(date: String, monto: Long = 800000, accountId: String = "acc-bnc") =
        TxParaSugerir(CategoryKind.INCOME, date, accountId, monto)

    @Test fun `ventana de 31 dias atras y 7 adelante con bordes inclusivos`() {
        fun fechas(date: String) = candidatasParaTx(ingreso(date), ctx()).map { it.ocurrencia.date }
        assertEquals(true, "2026-09-15" in fechas("2026-10-16"))
        assertEquals(false, "2026-09-15" in fechas("2026-10-17"))
        assertEquals(true, "2026-09-15" in fechas("2026-09-08"))
        assertEquals(false, "2026-09-15" in fechas("2026-09-07"))
        assertEquals(emptyList(), candidatasParaTx(TxParaSugerir(CategoryKind.EXPENSE, "2026-09-15", "acc-bnc", 800000), ctx()))
        assertEquals(emptyList(), candidatasParaTx(ingreso("2026-09-15", accountId = "acc-zinli"), ctx()))
    }

    @Test fun `sugerirOcurrencia respeta la tolerancia de 10 por ciento`() {
        assertEquals("2026-09-15", sugerirOcurrencia(ingreso("2026-09-15", 880000), ctx())?.date)
        assertEquals("2026-09-15", sugerirOcurrencia(ingreso("2026-09-15", 720000), ctx())?.date)
        assertNull(sugerirOcurrencia(ingreso("2026-09-15", 5000), ctx()))
        val c = candidatasParaTx(ingreso("2026-09-15", 880001), ctx()).first { it.ocurrencia.date == "2026-09-15" }
        assertEquals(false, c.montoCoincide)
        assertEquals(true, candidatasParaTx(ingreso("2026-09-15", 0), ctx()).first().sugerible)
    }

    @Test fun `omitidas fuera y hechas solo con incluirHechas`() {
        val hecha = ctx(txs = listOf(Transaction("ya", TxType.INCOME, "acc-bnc", 800000, "2026-09-15",
            source = TxSource.MANUAL, eventId = "e-quincena", eventDate = "2026-09-15")))
        assertEquals(listOf("2026-08-31", "2026-08-15"), candidatasParaTx(ingreso("2026-09-15"), hecha).map { it.ocurrencia.date })
        val omitida = ctx(eventos = listOf(quincena.copy(omitidas = listOf("2026-09-15"))))
        assertEquals(listOf("2026-08-31", "2026-08-15"), candidatasParaTx(ingreso("2026-09-15"), omitida).map { it.ocurrencia.date })
    }

    @Test fun `sugerirParaLote es greedy global y determinista`() {
        fun tx(id: String, date: String, monto: Long) =
            Transaction(id, TxType.INCOME, "acc-bnc", monto, date, source = TxSource.BNC)
        val lote = listOf(tx("amigo", "2026-09-14", 5000), tx("sueldo", "2026-09-15", 800000), tx("otro", "2026-09-16", 790000))
        val m = sugerirParaLote(lote, ctx())
        assertEquals(Ocurrencia("e-quincena", "2026-09-15"), m["sueldo"])
        assertEquals(Ocurrencia("e-quincena", "2026-08-31"), m["otro"])
        assertEquals(false, "amigo" in m)
        assertEquals(m.toList(), sugerirParaLote(lote, ctx()).toList())
    }

    @Test fun `aplicarAsignacion copia la categoria y desasignar la conserva; direccion contraria lanza`() {
        val original = Transaction("t1", TxType.INCOME, "acc-bnc", 800000, "2026-09-15", categoryId = "cat-otros-ingresos", source = TxSource.MANUAL)
        val asignada = aplicarAsignacion(original, Ocurrencia("e-quincena", "2026-09-15"), quincena)
        assertEquals("cat-sueldo", asignada.categoryId)
        assertEquals(original.amountMinor, asignada.amountMinor)
        val suelta = aplicarAsignacion(asignada, null, null)
        assertNull(suelta.eventId)
        assertEquals("cat-sueldo", suelta.categoryId)
        val e = assertFailsWith<IllegalArgumentException> { validarAsignacion(TxType.EXPENSE, quincena, categorias) }
        assertEquals("Este movimiento es un gasto y el evento es un cobro", e.message)
    }
}
```

- [ ] **Paso 2: Correr → FAIL** (`./gradlew :app:testDebugUnitTest --tests "*CalendarTest" --tests "*EventMatchTest" --console=plain 2>&1 | tail -30`).

- [ ] **Paso 3: `Calendar.kt`.**

```kotlin
package com.cuentasclaras.app.domain

import kotlin.math.ceil
import kotlin.math.min

enum class EstadoOcurrencia { PENDIENTE, HECHA, VENCIDA, OMITIDA }

/** Clave de una ocurrencia: el evento + su fecha NOMINAL (no la del movimiento). */
data class Ocurrencia(val eventId: String, val date: String)

data class MontoMoneda(val currency: Currency, val totalMinor: Long)

data class OcurrenciaVista(
    val eventId: String,
    val date: String,
    val evento: CalEvent,
    val estado: EstadoOcurrencia,
    /** Movimientos asignados a esta ocurrencia (pueden ser varios). */
    val txIds: List<String>,
    /** Σ amountMinor de las txs asignadas, por moneda de la cuenta de cada tx. Sin conversión. */
    val asignadoPorMoneda: List<MontoMoneda>,
    /** true si la regla actual del evento NO genera esa fecha. */
    val fueraDeRegla: Boolean,
) {
    val ocurrencia: Ocurrencia get() = Ocurrencia(eventId, date)
}

/** Preset «Quincenal (15 y último)»: 31 se recorta al último día de cada mes. */
val RECURRENCIA_QUINCENAL: Recurrence = Recurrence.Monthly(listOf(15, 31))

fun esQuincenal(r: Recurrence): Boolean = r is Recurrence.Monthly && r.days == listOf(15, 31)

private val SEPARADOR_DIAS = Regex("""[\s,;]+""")
private val DIA_TEXTO = Regex("""^\d{1,2}$""")

/** '15, 31' → [15, 31]. Ordena y deduplica. Lanza si está vacío o hay algo fuera de 1..31. */
fun parseDias(texto: String): List<Int> {
    val dias = sortedSetOf<Int>()
    for (p in texto.split(SEPARADOR_DIAS).filter { it.isNotEmpty() }) {
        require(DIA_TEXTO.matches(p)) { "Días del mes inválidos" }
        val n = p.toInt()
        require(n in 1..31) { "Días del mes inválidos" }
        dias.add(n)
    }
    require(dias.isNotEmpty()) { "Días del mes inválidos" }
    return dias.toList()
}

/** Lanza IllegalArgumentException en español con el primer problema encontrado. */
fun validarEvento(e: CalEvent) {
    require(e.titulo.isNotBlank()) { "Ponle nombre al evento" }
    require(e.categoryId.isNotEmpty()) { "Elige una categoría" }
    if (e.amountMinor != null) {
        require(!e.accountId.isNullOrEmpty()) { "El monto esperado necesita una cuenta" }
        require(e.amountMinor > 0) { "Monto inválido" }
    }
    require(esFechaIso(e.startDate)) { "Fecha inválida" }
    if (e.endDate != null) {
        require(esFechaIso(e.endDate)) { "Fecha de fin inválida" }
        require(e.endDate >= e.startDate) { "El fin no puede ser antes del inicio" }
    }
    val r = e.recurrence
    if (r is Recurrence.Monthly) {
        val d = r.days
        val ok = d.isNotEmpty() && d.all { it in 1..31 } && d.zipWithNext().all { (a, b) -> b > a }
        require(ok) { "Días del mes inválidos" }
    }
    for (o in e.omitidas ?: emptyList()) require(esFechaIso(o)) { "Fecha omitida inválida" }
}

/** Construye un CalEvent válido. Nunca guarda cadenas vacías ni listas vacías (quedan null). */
fun buildEvent(
    titulo: String,
    categoryId: String,
    accountId: String? = null,
    amountMinor: Long? = null,
    startDate: String,
    endDate: String? = null,
    recurrence: Recurrence,
    omitidas: List<String>? = null,
    nowIso: String,
): CalEvent {
    val e = CalEvent(
        id = nuevoId(),
        titulo = titulo.trim(),
        categoryId = categoryId,
        accountId = accountId?.takeIf { it.isNotEmpty() },
        amountMinor = amountMinor,
        startDate = startDate,
        endDate = endDate?.takeIf { it.isNotEmpty() },
        recurrence = recurrence,
        omitidas = omitidas?.takeIf { it.isNotEmpty() }?.toList(),
        createdIso = nowIso,
    )
    validarEvento(e)
    return e
}

/** Fechas NOMINALES del evento dentro de [desde, hasta] (inclusive), ascendentes y sin repetidos.
 *  NUNCA expande sin `hasta`. */
fun fechasDeEvento(e: CalEvent, desde: String, hasta: String): List<String> {
    val r = e.recurrence
    if (r is Recurrence.Once) {
        return if (desde <= e.startDate && e.startDate <= hasta) listOf(e.startDate) else emptyList()
    }
    val desdeEf = if (desde > e.startDate) desde else e.startDate
    val hastaEf = if (e.endDate != null && e.endDate < hasta) e.endDate else hasta
    if (desdeEf > hastaEf) return emptyList()

    return when (r) {
        Recurrence.Weekly -> {
            val salto = ceil(diffDias(e.startDate, desdeEf) / 7.0).toInt()
            val out = mutableListOf<String>()
            var f = addDays(e.startDate, salto * 7)
            while (f <= hastaEf) {
                out.add(f)
                f = addDays(f, 7)
            }
            out
        }
        Recurrence.Yearly -> {
            val (_, m, d) = partsOf(e.startDate)
            val out = sortedSetOf<String>()
            for (y in partsOf(desdeEf).y..partsOf(hastaEf).y) {
                val f = isoFromParts(y, m, min(d, daysInMonth(y, m)))
                if (f >= desdeEf && f <= hastaEf) out.add(f)
            }
            out.toList()
        }
        is Recurrence.Monthly -> {
            val out = sortedSetOf<String>()
            var mes = monthOf(desdeEf)
            while (mes <= monthOf(hastaEf)) {
                val (y, m) = mes.split("-").map { it.toInt() }
                for (dia in r.days) {
                    val f = isoFromParts(y, m, min(dia, daysInMonth(y, m)))
                    if (f >= desdeEf && f <= hastaEf) out.add(f)
                }
                mes = mesSiguiente(mes)
            }
            out.toList()
        }
        Recurrence.Once -> emptyList() // ya tratado arriba
    }
}

/** El kind de la categoría del evento define pago (EXPENSE) o cobro (INCOME). null si no existe. */
fun kindDeEvento(e: CalEvent, categories: List<Category>): CategoryKind? =
    categories.firstOrNull { it.id == e.categoryId }?.kind

/** Precedencia: hecha > omitida > vencida > pendiente. «Hoy» cuenta como pendiente. */
fun estadoDe(date: String, omitida: Boolean, txIds: List<String>, hoy: String): EstadoOcurrencia = when {
    txIds.isNotEmpty() -> EstadoOcurrencia.HECHA
    omitida -> EstadoOcurrencia.OMITIDA
    date < hoy -> EstadoOcurrencia.VENCIDA
    else -> EstadoOcurrencia.PENDIENTE
}

/** UNIÓN de las fechas generadas por cada evento y de las asignaciones existentes en el rango.
 *  Las ocurrencias NO se persisten: esta función las deriva cada vez. */
fun ocurrenciasEnRango(
    eventos: List<CalEvent>,
    txs: List<Transaction>,
    accounts: List<Account>,
    desde: String,
    hasta: String,
    hoy: String,
): List<OcurrenciaVista> {
    val eventoDe = eventos.associateBy { it.id }
    val monedaDe = accounts.associate { it.id to it.currency }
    val generadas = HashSet<String>()
    val pares = LinkedHashMap<String, Pair<CalEvent, String>>()

    for (e in eventos) {
        for (f in fechasDeEvento(e, desde, hasta)) {
            val k = "${e.id}|$f"
            generadas.add(k)
            pares[k] = e to f
        }
    }

    val txsPorPar = HashMap<String, MutableList<Transaction>>()
    for (t in txs) {
        val eventId = t.eventId ?: continue
        val eventDate = t.eventDate ?: continue
        if (eventDate < desde || eventDate > hasta) continue
        val evento = eventoDe[eventId] ?: continue // asignación huérfana: se ignora
        val k = "$eventId|$eventDate"
        if (k !in pares) pares[k] = evento to eventDate
        txsPorPar.getOrPut(k) { mutableListOf() }.add(t)
    }

    val vistas = pares.map { (k, par) ->
        val (evento, date) = par
        val asignadas = txsPorPar[k] ?: emptyList()
        val porMoneda = LinkedHashMap<Currency, Long>()
        for (t in asignadas) {
            val moneda = monedaDe[t.accountId] ?: Currency.USD
            porMoneda[moneda] = (porMoneda[moneda] ?: 0L) + t.amountMinor
        }
        val txIds = asignadas.map { it.id }
        OcurrenciaVista(
            eventId = evento.id,
            date = date,
            evento = evento,
            estado = estadoDe(date, (evento.omitidas ?: emptyList()).contains(date), txIds, hoy),
            txIds = txIds,
            asignadoPorMoneda = porMoneda.map { MontoMoneda(it.key, it.value) },
            fueraDeRegla = k !in generadas,
        )
    }
    return vistas.sortedWith { a, b ->
        val porFecha = a.date.compareTo(b.date)
        if (porFecha != 0) porFecha else compararTexto(a.evento.titulo, b.evento.titulo)
    }
}

data class TotalesEsperados(val pagar: List<MontoMoneda>, val cobrar: List<MontoMoneda>)

/** Lo que falta pagar/cobrar: solo pendientes y vencidas CON monto esperado, por moneda, sin conversión. */
fun totalesEsperados(
    ocurrencias: List<OcurrenciaVista>,
    accounts: List<Account>,
    categories: List<Category>,
): TotalesEsperados {
    val monedaDe = accounts.associate { it.id to it.currency }
    val pagar = LinkedHashMap<Currency, Long>()
    val cobrar = LinkedHashMap<Currency, Long>()
    for (o in ocurrencias) {
        if (o.estado != EstadoOcurrencia.PENDIENTE && o.estado != EstadoOcurrencia.VENCIDA) continue
        val monto = o.evento.amountMinor ?: continue
        val cuenta = o.evento.accountId?.takeIf { it.isNotEmpty() } ?: continue
        val kind = kindDeEvento(o.evento, categories) ?: continue
        val moneda = monedaDe[cuenta] ?: Currency.USD
        val destino = if (kind == CategoryKind.EXPENSE) pagar else cobrar
        destino[moneda] = (destino[moneda] ?: 0L) + monto
    }
    return TotalesEsperados(
        pagar = pagar.map { MontoMoneda(it.key, it.value) },
        cobrar = cobrar.map { MontoMoneda(it.key, it.value) },
    )
}

/** Texto humano de la regla, en español. */
fun descripcionRecurrencia(e: CalEvent): String {
    val fin = e.endDate?.let { " · hasta ${ddmmyyyy(it)}" } ?: ""
    return when (val r = e.recurrence) {
        Recurrence.Once -> "una vez: ${ddmmyyyy(e.startDate)}"
        Recurrence.Weekly -> "semanal: ${nombreDia(e.startDate)}$fin"
        Recurrence.Yearly -> {
            val p = partsOf(e.startDate)
            "anual: ${p.d} ${MES_CORTO[p.m - 1]}$fin"
        }
        is Recurrence.Monthly -> {
            if (esQuincenal(r)) {
                "quincenal (15 y último)$fin"
            } else {
                val dias = r.days.map { if (it == 31) "último" else it.toString() }
                val texto = if (dias.size == 1) dias[0] else "${dias.dropLast(1).joinToString(", ")} y ${dias.last()}"
                "mensual: $texto$fin"
            }
        }
    }
}
```

- [ ] **Paso 4: `EventMatch.kt`.**

```kotlin
package com.cuentasclaras.app.domain

import kotlin.math.abs

data class TxParaSugerir(val type: CategoryKind, val date: String, val accountId: String, val amountMinor: Long)

data class CtxSugerencia(
    val eventos: List<CalEvent>,
    val txs: List<Transaction>,
    val accounts: List<Account>,
    val categories: List<Category>,
    val hoy: String,
)

/** Ventana asimétrica: la ocurrencia puede estar hasta 31 días ANTES del movimiento y hasta 7 DESPUÉS. */
const val VENTANA_ATRAS_DIAS = 31
const val VENTANA_ADELANTE_DIAS = 7
const val TOLERANCIA_MONTO = 0.10

data class Candidata(
    val ocurrencia: OcurrenciaVista,
    /** |diffDias(ocurrencia.date, tx.date)| */
    val distanciaDias: Int,
    /** null = el evento no tiene monto esperado, o la tx aún no tiene monto (amountMinor ≤ 0). */
    val montoCoincide: Boolean?,
    val sugerible: Boolean,
)

fun candidatasParaTx(tx: TxParaSugerir, ctx: CtxSugerencia, incluirHechas: Boolean = false): List<Candidata> {
    val desde = addDays(tx.date, -VENTANA_ATRAS_DIAS)
    val hasta = addDays(tx.date, VENTANA_ADELANTE_DIAS)
    val elegibles = ctx.eventos.filter { e ->
        kindDeEvento(e, ctx.categories) == tx.type && (e.accountId == null || e.accountId == tx.accountId)
    }
    val ocurrencias = ocurrenciasEnRango(elegibles, ctx.txs, ctx.accounts, desde, hasta, ctx.hoy)

    val out = mutableListOf<Candidata>()
    for (o in ocurrencias) {
        if (o.estado == EstadoOcurrencia.OMITIDA) continue
        if (o.estado == EstadoOcurrencia.HECHA && !incluirHechas) continue
        val esperado = o.evento.amountMinor
        val montoCoincide =
            if (esperado == null || tx.amountMinor <= 0) null
            else abs(tx.amountMinor - esperado) <= TOLERANCIA_MONTO * esperado
        out.add(
            Candidata(
                ocurrencia = o,
                distanciaDias = abs(diffDias(o.date, tx.date)),
                montoCoincide = montoCoincide,
                sugerible = (o.estado == EstadoOcurrencia.PENDIENTE || o.estado == EstadoOcurrencia.VENCIDA) &&
                    montoCoincide != false,
            ),
        )
    }
    return out.sortedWith(
        compareByDescending<Candidata> { it.sugerible }
            .thenBy { it.distanciaDias }
            .thenByDescending { it.montoCoincide == true }
            .thenComparator { a, b -> compararTexto(a.ocurrencia.evento.titulo, b.ocurrencia.evento.titulo) },
    )
}

/** Primera candidata sugerible, o null. Es la que Nuevo marca como «sugerida». */
fun sugerirOcurrencia(tx: TxParaSugerir, ctx: CtxSugerencia): OcurrenciaVista? {
    val primera = candidatasParaTx(tx, ctx).firstOrNull() ?: return null
    return if (primera.sugerible) primera.ocurrencia else null
}

/** Emparejamiento greedy GLOBAL para la vista previa de los imports: cada ocurrencia recibe a lo
 *  sumo una tx y cada tx a lo sumo una ocurrencia. Determinista. Clave del mapa: tx.id. */
fun sugerirParaLote(txs: List<Transaction>, ctx: CtxSugerencia): Map<String, Ocurrencia> {
    data class Par(
        val txId: String, val eventId: String, val date: String, val titulo: String,
        val distanciaDias: Int, val deltaMonto: Long, val indice: Int,
    )
    val pares = mutableListOf<Par>()
    txs.forEachIndexed { indice, t ->
        val kind = t.type.comoKind() ?: return@forEachIndexed
        for (c in candidatasParaTx(TxParaSugerir(kind, t.date, t.accountId, t.amountMinor), ctx)) {
            if (!c.sugerible) continue
            val esperado = c.ocurrencia.evento.amountMinor
            pares.add(
                Par(
                    txId = t.id, eventId = c.ocurrencia.eventId, date = c.ocurrencia.date,
                    titulo = c.ocurrencia.evento.titulo, distanciaDias = c.distanciaDias,
                    deltaMonto = if (esperado == null) Long.MAX_VALUE else abs(t.amountMinor - esperado),
                    indice = indice,
                ),
            )
        }
    }
    val ordenados = pares.sortedWith(
        compareBy<Par> { it.distanciaDias }
            .thenBy { it.deltaMonto }
            .thenComparator { a, b -> compararTexto(a.titulo, b.titulo) }
            .thenBy { it.indice },
    )
    val asignadas = LinkedHashMap<String, Ocurrencia>()
    val ocupadas = HashSet<String>()
    for (p in ordenados) {
        val clave = "${p.eventId}|${p.date}"
        if (p.txId in asignadas || clave in ocupadas) continue
        asignadas[p.txId] = Ocurrencia(p.eventId, p.date)
        ocupadas.add(clave)
    }
    return asignadas
}

/** Devuelve una COPIA. Asignar copia la categoría del evento; desasignar la conserva.
 *  Jamás toca amountMinor, date, accountId ni type. */
fun aplicarAsignacion(tx: Transaction, o: Ocurrencia?, evento: CalEvent?): Transaction =
    if (o == null || evento == null) tx.copy(eventId = null, eventDate = null)
    else tx.copy(eventId = o.eventId, eventDate = o.date, categoryId = evento.categoryId)

/** Lanza IllegalArgumentException en español si el movimiento y el evento van en direcciones contrarias. */
fun validarAsignacion(txType: TxType, evento: CalEvent, categories: List<Category>) {
    val kind = kindDeEvento(evento, categories) ?: throw IllegalArgumentException("La categoría del evento no existe")
    if (txType == TxType.EXPENSE && kind == CategoryKind.INCOME) {
        throw IllegalArgumentException("Este movimiento es un gasto y el evento es un cobro")
    }
    if (txType == TxType.INCOME && kind == CategoryKind.EXPENSE) {
        throw IllegalArgumentException("Este movimiento es un ingreso y el evento es un pago")
    }
}
```

- [ ] **Paso 5: Suite completa → PASS** y `assembleDebug` compila. Si un caso del calendario falla, compara contra `src/domain/calendar.test.ts` (es el comportamiento validado por Daniel) antes de tocar el test.

- [ ] **Paso 6: Commit.**

```bash
git add android/app/src/main/java/com/cuentasclaras/app/domain/Calendar.kt android/app/src/main/java/com/cuentasclaras/app/domain/EventMatch.kt android/app/src/test/java/com/cuentasclaras/app/domain/CalendarTest.kt android/app/src/test/java/com/cuentasclaras/app/domain/EventMatchTest.kt
git commit -m "feat(dominio): calendario de eventos con ocurrencias derivadas y sugerencia de asignación" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---
### Tarea 5: Room — entidades, DAOs, base, mapeos y seeds

**Skills:** `mattpocock-skills:codebase-design` (antes de escribir: confirma la costura DAO ↔ operaciones ↔ dominio como módulos profundos; las operaciones reciben `CuentasDb` igual que la web recibe `d: CuentasDB`) · `output-skill`. Sin TDD: seeds y DAOs no llevan tests (regla 12); se ejercitan en los tests de T6/T7.

**Files:**
- Create: `android/app/src/main/java/com/cuentasclaras/app/data/db/Entidades.kt`
- Create: `android/app/src/main/java/com/cuentasclaras/app/data/db/Convertidores.kt`
- Create: `android/app/src/main/java/com/cuentasclaras/app/data/db/Daos.kt`
- Create: `android/app/src/main/java/com/cuentasclaras/app/data/db/CuentasDb.kt`
- Create: `android/app/src/main/java/com/cuentasclaras/app/data/db/Mapeos.kt`
- Create: `android/app/src/main/java/com/cuentasclaras/app/data/Seeds.kt`
- Create (generado por KSP al compilar, se commitea): `android/app/schemas/com.cuentasclaras.app.data.db.CuentasDb/1.json`

Fuente: `src/db/db.ts` (esquema v3, seeds, `seedIfEmpty`, `getSettings`, `saveSettings`) y spec §6.1.

**Interfaces:**
- Consumes (T2): tipos de dominio, `JsonCuentas`, `Recurrence`.
- Produces:
  - Entidades `AccountEntity`, `TransactionEntity`, `CategoryEntity`, `RateEntity`, `RuleEntity`, `SettingsEntity`, `DebtEntity`, `TxGroupEntity`, `CalEventEntity`, `CapturaEntity`; `enum class EstadoCaptura { PENDIENTE, REGISTRADA, DESCARTADA }`; `enum class TipoCaptura { INGRESO, GASTO }`.
  - `abstract class CuentasDb : RoomDatabase` con `accounts()`, `transactions()`, `categories()`, `rates()`, `rules()`, `settings()`, `debts()`, `txGroups()`, `calEvents()`, `capturas()` y `companion fun crear(context: Context): CuentasDb`.
  - Mapeos `XEntity.toDomain()` / `X.toEntity()` para las 9 tablas del respaldo.
  - `suspend fun seedIfEmpty(db: CuentasDb)`, `suspend fun getSettings(db): Settings`, `suspend fun saveSettings(db, cambio: (Settings) -> Settings)`, `val CUENTAS_SEED`, `CATEGORIAS_SEED`, `CATEGORIAS_SEED_V2`, `SETTINGS_SEED`.
  - Los métodos DAO exactos de `Daos.kt` (abajo); T6–T30 solo usan esos.

- [ ] **Paso 1: `Entidades.kt`.**

```kotlin
package com.cuentasclaras.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.cuentasclaras.app.domain.AccountKind
import com.cuentasclaras.app.domain.CategoryKind
import com.cuentasclaras.app.domain.Currency
import com.cuentasclaras.app.domain.DebtDirection
import com.cuentasclaras.app.domain.DebtStatus
import com.cuentasclaras.app.domain.RateSource
import com.cuentasclaras.app.domain.Recurrence
import com.cuentasclaras.app.domain.TxSource
import com.cuentasclaras.app.domain.TxType

// Mismas 9 tablas y campos que src/db/db.ts (v3) + `capturas` (nativa, fuera del respaldo).
// Los enums se guardan por `name` (conversor por defecto de Room); el JSON del respaldo usa
// los literales web vía @SerialName en los tipos de dominio (D2).

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val currency: Currency,
    val kind: AccountKind,
    val isSavings: Boolean,
    val archived: Boolean,
    val bncLastImportKey: String?,
    val bncLastSaldoMinor: Long?,
    val enDisposicion: Boolean?,
)

@Entity(
    tableName = "transactions",
    indices = [
        Index("date"), Index("accountId"), Index("categoryId"),
        Index("importKey"), Index("groupId"), Index("eventId"),
    ],
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val type: TxType,
    val accountId: String,
    val amountMinor: Long,
    val date: String,
    val time: String?,
    val categoryId: String?,
    val note: String?,
    val source: TxSource,
    val importKey: String?,
    val toAccountId: String?,
    val toAmountMinor: Long?,
    val rateUsed: Double?,
    val groupId: String?,
    val eventId: String?,
    val eventDate: String?,
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val icon: String,
    val color: String,
    val kind: CategoryKind,
    val monthlyBudgetMinor: Long?,
)

@Entity(tableName = "rates", primaryKeys = ["date", "source"], indices = [Index("date")])
data class RateEntity(
    val date: String,
    val source: RateSource,
    val vesPerUsd: Double,
)

@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey val id: String,
    val pattern: String,
    val categoryId: String,
    val autoCreated: Boolean,
)

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: String,
    val viewCurrency: Currency,
    val rateSource: RateSource,
    val lastAccountId: String?,
    val lastRatesFetchDate: String?,
)

@Entity(tableName = "debts", indices = [Index("status")])
data class DebtEntity(
    @PrimaryKey val id: String,
    val direction: DebtDirection,
    val concepto: String,
    val amountMinor: Long,
    val accountId: String,
    val status: DebtStatus,
    val createdIso: String,
    val settledTxId: String?,
    val settledIso: String?,
)

@Entity(tableName = "txGroups")
data class TxGroupEntity(
    @PrimaryKey val id: String,
    val nota: String?,
    val categoryId: String?,
    val createdIso: String,
)

@Entity(tableName = "calEvents")
data class CalEventEntity(
    @PrimaryKey val id: String,
    val titulo: String,
    val categoryId: String,
    val accountId: String?,
    val amountMinor: Long?,
    val startDate: String,
    val endDate: String?,
    /** JSON {"kind":…} vía Convertidores (D3). */
    val recurrence: Recurrence,
    /** JSON array de fechas vía Convertidores; null = sin omitidas. */
    val omitidas: List<String>?,
    val createdIso: String,
)

enum class EstadoCaptura { PENDIENTE, REGISTRADA, DESCARTADA }

enum class TipoCaptura { INGRESO, GASTO }

/** Notificación del BNC capturada (spec §7.3). Se guarda ANTES de avisar al usuario.
 *  NO forma parte del respaldo v3. */
@Entity(tableName = "capturas", indices = [Index("estado"), Index("referencia"), Index("hash")])
data class CapturaEntity(
    @PrimaryKey val id: String,
    /** Instante UTC ISO con milisegundos (ahoraIso()). */
    val recibidaIso: String,
    val textoCrudo: String,
    val reconocida: Boolean,
    val tipo: TipoCaptura?,
    val montoMinor: Long?,
    val referencia: String?,
    val nota: String?,
    val estado: EstadoCaptura,
    /** sha256 hex del texto normalizado: deduplica reemisiones en 24 h. */
    val hash: String,
    /** Movimiento creado al registrarla. */
    val txId: String?,
)
```

- [ ] **Paso 2: `Convertidores.kt`.**

```kotlin
package com.cuentasclaras.app.data.db

import androidx.room.TypeConverter
import com.cuentasclaras.app.domain.JsonCuentas
import com.cuentasclaras.app.domain.Recurrence
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

class Convertidores {
    @TypeConverter
    fun recurrenceAJson(r: Recurrence): String = JsonCuentas.encodeToString(Recurrence.serializer(), r)

    @TypeConverter
    fun jsonARecurrence(s: String): Recurrence = JsonCuentas.decodeFromString(Recurrence.serializer(), s)

    @TypeConverter
    fun listaAJson(l: List<String>?): String? =
        l?.let { JsonCuentas.encodeToString(ListSerializer(String.serializer()), it) }

    @TypeConverter
    fun jsonALista(s: String?): List<String>? =
        s?.let { JsonCuentas.decodeFromString(ListSerializer(String.serializer()), it) }
}
```

- [ ] **Paso 3: `Daos.kt`.**

```kotlin
package com.cuentasclaras.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// @Insert sin onConflict = ABORT: un id repetido lanza y revierte la transacción en curso
// (lo exige el todo-o-nada de commitImport e importBackup).

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts") fun observar(): Flow<List<AccountEntity>>
    @Query("SELECT * FROM accounts") suspend fun todas(): List<AccountEntity>
    @Query("SELECT * FROM accounts WHERE id = :id") suspend fun porId(id: String): AccountEntity?
    @Query("SELECT COUNT(*) FROM accounts") suspend fun contar(): Int
    @Insert suspend fun insertar(filas: List<AccountEntity>)
    @Upsert suspend fun guardar(fila: AccountEntity)
    @Query("UPDATE accounts SET bncLastImportKey = :key, bncLastSaldoMinor = :saldo WHERE id = :id")
    suspend fun parcheBnc(id: String, key: String, saldo: Long)
    @Query("DELETE FROM accounts") suspend fun borrarTodo()
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions") fun observar(): Flow<List<TransactionEntity>>
    @Query("SELECT * FROM transactions") suspend fun todas(): List<TransactionEntity>
    @Query("SELECT * FROM transactions WHERE id = :id") suspend fun porId(id: String): TransactionEntity?
    @Query("SELECT * FROM transactions WHERE id IN (:ids)") suspend fun porIds(ids: List<String>): List<TransactionEntity>
    @Query("SELECT COUNT(*) FROM transactions") suspend fun contar(): Int
    @Query("SELECT COUNT(*) FROM transactions WHERE importKey IN (:keys)") suspend fun contarPorImportKeys(keys: List<String>): Int
    @Query("SELECT importKey FROM transactions WHERE importKey IS NOT NULL") suspend fun importKeys(): List<String>
    @Insert suspend fun insertar(fila: TransactionEntity)
    @Insert suspend fun insertarTodas(filas: List<TransactionEntity>)
    @Update suspend fun actualizar(fila: TransactionEntity)
    @Query("DELETE FROM transactions WHERE id = :id") suspend fun borrar(id: String)
    @Query("UPDATE transactions SET groupId = :groupId WHERE id IN (:ids)") suspend fun asignarGrupo(ids: List<String>, groupId: String)
    @Query("UPDATE transactions SET groupId = NULL WHERE id IN (:ids)") suspend fun limpiarGrupo(ids: List<String>)
    @Query("SELECT id FROM transactions WHERE groupId = :groupId") suspend fun idsDelGrupo(groupId: String): List<String>
    @Query("SELECT * FROM transactions WHERE eventId = :eventId") suspend fun delEvento(eventId: String): List<TransactionEntity>
    @Query("SELECT COUNT(*) FROM transactions WHERE eventId = :eventId AND eventDate = :date")
    suspend fun contarDeOcurrencia(eventId: String, date: String): Int
    @Query("UPDATE transactions SET eventId = :eventId, eventDate = :eventDate, categoryId = :categoryId WHERE id = :txId")
    suspend fun asignarEvento(txId: String, eventId: String, eventDate: String, categoryId: String)
    @Query("UPDATE transactions SET eventId = NULL, eventDate = NULL WHERE id = :txId") suspend fun desasignarTx(txId: String)
    @Query("UPDATE transactions SET eventId = NULL, eventDate = NULL WHERE eventId = :eventId")
    suspend fun desasignarDelEvento(eventId: String): Int
    @Query("DELETE FROM transactions") suspend fun borrarTodo()
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories") fun observar(): Flow<List<CategoryEntity>>
    @Query("SELECT * FROM categories") suspend fun todas(): List<CategoryEntity>
    @Query("SELECT * FROM categories WHERE id = :id") suspend fun porId(id: String): CategoryEntity?
    @Query("SELECT COUNT(*) FROM categories") suspend fun contar(): Int
    @Insert suspend fun insertar(fila: CategoryEntity)
    @Insert suspend fun insertarTodas(filas: List<CategoryEntity>)
    @Upsert suspend fun guardar(fila: CategoryEntity)
    @Query("DELETE FROM categories") suspend fun borrarTodo()
}

@Dao
interface RateDao {
    @Query("SELECT * FROM rates") fun observar(): Flow<List<RateEntity>>
    @Query("SELECT * FROM rates") suspend fun todas(): List<RateEntity>
    @Query("SELECT COUNT(*) FROM rates") suspend fun contar(): Int
    @Insert suspend fun insertarTodas(filas: List<RateEntity>)
    @Upsert suspend fun guardarTodas(filas: List<RateEntity>)
    @Query("DELETE FROM rates WHERE date = :date AND source = :source") suspend fun borrar(date: String, source: String)
    @Query("DELETE FROM rates") suspend fun borrarTodo()
}

@Dao
interface RuleDao {
    @Query("SELECT * FROM rules") fun observar(): Flow<List<RuleEntity>>
    @Query("SELECT * FROM rules") suspend fun todas(): List<RuleEntity>
    @Query("SELECT COUNT(*) FROM rules") suspend fun contar(): Int
    @Insert suspend fun insertarTodas(filas: List<RuleEntity>)
    @Query("DELETE FROM rules WHERE id = :id") suspend fun borrar(id: String)
    @Query("DELETE FROM rules") suspend fun borrarTodo()
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = 'main'") fun observar(): Flow<SettingsEntity?>
    @Query("SELECT * FROM settings WHERE id = 'main'") suspend fun principal(): SettingsEntity?
    @Query("SELECT * FROM settings") suspend fun todas(): List<SettingsEntity>
    @Query("SELECT COUNT(*) FROM settings") suspend fun contar(): Int
    @Insert suspend fun insertarTodas(filas: List<SettingsEntity>)
    @Upsert suspend fun guardar(fila: SettingsEntity)
    @Query("DELETE FROM settings") suspend fun borrarTodo()
}

@Dao
interface DebtDao {
    @Query("SELECT * FROM debts") fun observar(): Flow<List<DebtEntity>>
    @Query("SELECT * FROM debts") suspend fun todas(): List<DebtEntity>
    @Query("SELECT * FROM debts WHERE id = :id") suspend fun porId(id: String): DebtEntity?
    @Query("SELECT COUNT(*) FROM debts") suspend fun contar(): Int
    @Insert suspend fun insertar(fila: DebtEntity)
    @Insert suspend fun insertarTodas(filas: List<DebtEntity>)
    @Update suspend fun actualizar(fila: DebtEntity)
    @Query("DELETE FROM debts WHERE id = :id") suspend fun borrar(id: String)
    @Query("DELETE FROM debts") suspend fun borrarTodo()
}

@Dao
interface TxGroupDao {
    @Query("SELECT * FROM txGroups") fun observar(): Flow<List<TxGroupEntity>>
    @Query("SELECT * FROM txGroups") suspend fun todas(): List<TxGroupEntity>
    @Query("SELECT * FROM txGroups WHERE id = :id") suspend fun porId(id: String): TxGroupEntity?
    @Query("SELECT COUNT(*) FROM txGroups") suspend fun contar(): Int
    @Insert suspend fun insertar(fila: TxGroupEntity)
    @Insert suspend fun insertarTodas(filas: List<TxGroupEntity>)
    @Update suspend fun actualizar(fila: TxGroupEntity)
    @Query("DELETE FROM txGroups WHERE id = :id") suspend fun borrar(id: String)
    @Query("DELETE FROM txGroups") suspend fun borrarTodo()
}

@Dao
interface CalEventDao {
    @Query("SELECT * FROM calEvents") fun observar(): Flow<List<CalEventEntity>>
    @Query("SELECT * FROM calEvents") suspend fun todas(): List<CalEventEntity>
    @Query("SELECT * FROM calEvents WHERE id = :id") suspend fun porId(id: String): CalEventEntity?
    @Query("SELECT COUNT(*) FROM calEvents") suspend fun contar(): Int
    @Insert suspend fun insertarTodas(filas: List<CalEventEntity>)
    @Upsert suspend fun guardar(fila: CalEventEntity)
    @Query("DELETE FROM calEvents WHERE id = :id") suspend fun borrar(id: String)
    @Query("DELETE FROM calEvents") suspend fun borrarTodo()
}

@Dao
interface CapturaDao {
    @Query("SELECT * FROM capturas WHERE estado = 'PENDIENTE' ORDER BY recibidaIso DESC")
    fun observarPendientes(): Flow<List<CapturaEntity>>
    @Query("SELECT * FROM capturas WHERE id = :id") suspend fun porId(id: String): CapturaEntity?
    @Query("SELECT COUNT(*) FROM capturas WHERE hash = :hash AND recibidaIso >= :desdeIso")
    suspend fun contarRecientes(hash: String, desdeIso: String): Int
    @Query("SELECT referencia, montoMinor FROM capturas WHERE estado = 'REGISTRADA' AND referencia IS NOT NULL AND montoMinor IS NOT NULL")
    suspend fun registradas(): List<ReferenciaCapturada>
    @Insert suspend fun insertar(fila: CapturaEntity)
    @Query("UPDATE capturas SET estado = 'REGISTRADA', txId = :txId WHERE id = :id") suspend fun marcarRegistrada(id: String, txId: String)
    @Query("UPDATE capturas SET estado = 'DESCARTADA' WHERE id = :id") suspend fun marcarDescartada(id: String)
}

/** Proyección para la deduplicación captura ↔ import (D8). */
data class ReferenciaCapturada(val referencia: String, val montoMinor: Long)
```

- [ ] **Paso 4: `CuentasDb.kt`.**

```kotlin
package com.cuentasclaras.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/** Versión 1 = esquema completo (9 tablas del respaldo v3 + capturas). CONGELADO tras esta tarea:
 *  cualquier cambio exige Migration + version + 1 (restricción 14). Nunca destructive fallback. */
@Database(
    entities = [
        AccountEntity::class, TransactionEntity::class, CategoryEntity::class, RateEntity::class,
        RuleEntity::class, SettingsEntity::class, DebtEntity::class, TxGroupEntity::class,
        CalEventEntity::class, CapturaEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Convertidores::class)
abstract class CuentasDb : RoomDatabase() {
    abstract fun accounts(): AccountDao
    abstract fun transactions(): TransactionDao
    abstract fun categories(): CategoryDao
    abstract fun rates(): RateDao
    abstract fun rules(): RuleDao
    abstract fun settings(): SettingsDao
    abstract fun debts(): DebtDao
    abstract fun txGroups(): TxGroupDao
    abstract fun calEvents(): CalEventDao
    abstract fun capturas(): CapturaDao

    companion object {
        fun crear(context: Context): CuentasDb =
            Room.databaseBuilder(context.applicationContext, CuentasDb::class.java, "cuentas-claras.db").build()
    }
}
```

- [ ] **Paso 5: `Mapeos.kt`.**

```kotlin
package com.cuentasclaras.app.data.db

import com.cuentasclaras.app.domain.Account
import com.cuentasclaras.app.domain.CalEvent
import com.cuentasclaras.app.domain.Category
import com.cuentasclaras.app.domain.Debt
import com.cuentasclaras.app.domain.Rate
import com.cuentasclaras.app.domain.Rule
import com.cuentasclaras.app.domain.Settings
import com.cuentasclaras.app.domain.Transaction
import com.cuentasclaras.app.domain.TxGroup

fun AccountEntity.toDomain() = Account(id, name, currency, kind, isSavings, archived, bncLastImportKey, bncLastSaldoMinor, enDisposicion)
fun Account.toEntity() = AccountEntity(id, name, currency, kind, isSavings, archived, bncLastImportKey, bncLastSaldoMinor, enDisposicion)

fun TransactionEntity.toDomain() = Transaction(
    id, type, accountId, amountMinor, date, time, categoryId, note, source, importKey,
    toAccountId, toAmountMinor, rateUsed, groupId, eventId, eventDate,
)
fun Transaction.toEntity() = TransactionEntity(
    id, type, accountId, amountMinor, date, time, categoryId, note, source, importKey,
    toAccountId, toAmountMinor, rateUsed, groupId, eventId, eventDate,
)

fun CategoryEntity.toDomain() = Category(id, name, icon, color, kind, monthlyBudgetMinor)
fun Category.toEntity() = CategoryEntity(id, name, icon, color, kind, monthlyBudgetMinor)

fun RateEntity.toDomain() = Rate(date, source, vesPerUsd)
fun Rate.toEntity() = RateEntity(date, source, vesPerUsd)

fun RuleEntity.toDomain() = Rule(id, pattern, categoryId, autoCreated)
fun Rule.toEntity() = RuleEntity(id, pattern, categoryId, autoCreated)

fun SettingsEntity.toDomain() = Settings(id, viewCurrency, rateSource, lastAccountId, lastRatesFetchDate)
fun Settings.toEntity() = SettingsEntity(id, viewCurrency, rateSource, lastAccountId, lastRatesFetchDate)

fun DebtEntity.toDomain() = Debt(id, direction, concepto, amountMinor, accountId, status, createdIso, settledTxId, settledIso)
fun Debt.toEntity() = DebtEntity(id, direction, concepto, amountMinor, accountId, status, createdIso, settledTxId, settledIso)

fun TxGroupEntity.toDomain() = TxGroup(id, nota, categoryId, createdIso)
fun TxGroup.toEntity() = TxGroupEntity(id, nota, categoryId, createdIso)

fun CalEventEntity.toDomain() = CalEvent(id, titulo, categoryId, accountId, amountMinor, startDate, endDate, recurrence, omitidas, createdIso)
fun CalEvent.toEntity() = CalEventEntity(id, titulo, categoryId, accountId, amountMinor, startDate, endDate, recurrence, omitidas, createdIso)
```

- [ ] **Paso 6: `Seeds.kt`** (copiar literalmente ids, nombres, emojis y colores de `src/db/db.ts:41-74`).

```kotlin
package com.cuentasclaras.app.data

import androidx.room.withTransaction
import com.cuentasclaras.app.data.db.CuentasDb
import com.cuentasclaras.app.data.db.toDomain
import com.cuentasclaras.app.data.db.toEntity
import com.cuentasclaras.app.domain.Account
import com.cuentasclaras.app.domain.AccountKind
import com.cuentasclaras.app.domain.Category
import com.cuentasclaras.app.domain.CategoryKind
import com.cuentasclaras.app.domain.Currency
import com.cuentasclaras.app.domain.RateSource
import com.cuentasclaras.app.domain.Settings

val CUENTAS_SEED = listOf(
    Account("acc-bnc", "BNC", Currency.VES, AccountKind.BANK, isSavings = false, archived = false),
    Account("acc-zinli", "Zinli", Currency.USD, AccountKind.WALLET, isSavings = false, archived = false),
    Account("acc-binance", "Binance", Currency.USD, AccountKind.SNAPSHOT, isSavings = true, archived = false),
    Account("acc-efectivo", "Efectivo", Currency.USD, AccountKind.CASH, isSavings = false, archived = false),
    Account("acc-efectivo-ahorro", "Efectivo ahorro", Currency.USD, AccountKind.CASH, isSavings = true, archived = false),
)

val CATEGORIAS_SEED = listOf(
    Category("cat-supermercado", "Supermercado", "🛒", "#4ade80", CategoryKind.EXPENSE),
    Category("cat-comida-fuera", "Comida fuera", "🍔", "#fbbf24", CategoryKind.EXPENSE),
    Category("cat-transporte", "Transporte", "🚌", "#60a5fa", CategoryKind.EXPENSE),
    Category("cat-salud", "Salud", "💊", "#f87171", CategoryKind.EXPENSE),
    Category("cat-servicios", "Servicios", "💡", "#facc15", CategoryKind.EXPENSE),
    Category("cat-comisiones", "Comisiones bancarias", "🏦", "#94a3b8", CategoryKind.EXPENSE),
    Category("cat-hogar", "Hogar", "🏠", "#a78bfa", CategoryKind.EXPENSE),
    Category("cat-ocio", "Ocio/Juegos", "🎮", "#f472b6", CategoryKind.EXPENSE),
    Category("cat-suscripciones", "Suscripciones", "📺", "#22d3ee", CategoryKind.EXPENSE),
    Category("cat-ropa", "Ropa", "👕", "#fb923c", CategoryKind.EXPENSE),
    Category("cat-otros-gastos", "Otros gastos", "📦", "#9ca3af", CategoryKind.EXPENSE),
    Category("cat-pago-movil-recibido", "Pago móvil recibido", "📲", "#4ade80", CategoryKind.INCOME),
    Category("cat-transferencia-recibida", "Transferencia recibida", "🔁", "#38bdf8", CategoryKind.INCOME),
    Category("cat-sueldo", "Sueldo/Pagos", "💼", "#34d399", CategoryKind.INCOME),
    Category("cat-otros-ingresos", "Otros ingresos", "➕", "#cbd5e1", CategoryKind.INCOME),
)

/** Seeds v2: se añaden por id si faltan (DB ya sembrada) o junto al seed inicial (DB fresca). */
val CATEGORIAS_SEED_V2 = listOf(
    Category("cat-deudas", "Deudas", "🤝", "#c084fc", CategoryKind.EXPENSE),
    Category("cat-vuelto-ingreso", "Vuelto", "↩️", "#34d399", CategoryKind.INCOME),
    Category("cat-vuelto-gasto", "Vuelto", "↪️", "#f87171", CategoryKind.EXPENSE),
)

val SETTINGS_SEED = Settings(id = "main", viewCurrency = Currency.USD, rateSource = RateSource.BCV, lastAccountId = "acc-bnc")

/** Crea cuentas, categorías y settings iniciales solo si la tabla está vacía. Las categorías v2
 *  además se reponen por id si faltan (add-if-missing, nunca pisa ediciones). */
suspend fun seedIfEmpty(db: CuentasDb) {
    db.withTransaction {
        if (db.accounts().contar() == 0) db.accounts().insertar(CUENTAS_SEED.map { it.toEntity() })
        if (db.categories().contar() == 0) {
            db.categories().insertarTodas((CATEGORIAS_SEED + CATEGORIAS_SEED_V2).map { it.toEntity() })
        } else {
            for (c in CATEGORIAS_SEED_V2) {
                if (db.categories().porId(c.id) == null) db.categories().insertar(c.toEntity())
            }
        }
        if (db.settings().contar() == 0) db.settings().insertarTodas(listOf(SETTINGS_SEED.toEntity()))
    }
}

suspend fun getSettings(db: CuentasDb): Settings = db.settings().principal()?.toDomain() ?: SETTINGS_SEED

/** Equivale a saveSettings(d, patch) de la web: lee, aplica el cambio y guarda. */
suspend fun saveSettings(db: CuentasDb, cambio: (Settings) -> Settings) {
    db.withTransaction {
        db.settings().guardar(cambio(getSettings(db)).copy(id = "main").toEntity())
    }
}
```

- [ ] **Paso 7: Compilar y exportar el esquema.** `cd android && ./gradlew :app:assembleDebug --console=plain 2>&1 | tail -20` → BUILD SUCCESSFUL y existe `android/app/schemas/com.cuentasclaras.app.data.db.CuentasDb/1.json` (si el plugin de Room de la Tarea 1 no lo genera, revisa el bloque `room { schemaDirectory(...) }`). Verifica en el JSON: 10 tablas; `rates` con `primaryKey.columnNames = [date, source]`; `transactions` con 6 índices.

- [ ] **Paso 8: Commit.**

```bash
git add android/app/src/main/java/com/cuentasclaras/app/data/db/ android/app/src/main/java/com/cuentasclaras/app/data/Seeds.kt android/app/schemas/
git commit -m "feat(datos): esquema Room v1 con las 9 tablas del respaldo y capturas, mapeos y seeds" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Tarea 6: Operaciones todo-o-nada (import, deudas, grupos, eventos)

**Skills:** `superpowers:test-driven-development` (tests Robolectric de reversión de §9) · `output-skill`.

**Files:**
- Create: `android/app/src/main/java/com/cuentasclaras/app/data/ops/CommitImport.kt`
- Create: `android/app/src/main/java/com/cuentasclaras/app/data/ops/DebtOps.kt`
- Create: `android/app/src/main/java/com/cuentasclaras/app/data/ops/GroupOps.kt`
- Create: `android/app/src/main/java/com/cuentasclaras/app/data/ops/EventOps.kt`
- Test: `android/app/src/test/java/com/cuentasclaras/app/data/DbDePrueba.kt` (helper)
- Test: `android/app/src/test/java/com/cuentasclaras/app/data/OpsTodoONadaTest.kt`

Fuentes: `src/db/commit.ts`, `debtOps.ts`, `groupOps.ts`, `eventOps.ts`.

**Interfaces:**
- Consumes: T2–T5 (`buildSettleTx`, `validarEvento`, `kindDeEvento`, `validarAsignacion`, `esFechaIso`, `comoKind`, `nuevoId`, `ahoraIso`, DAOs, mapeos, `seedIfEmpty`).
- Produces:
  - `data class ParcheCuentaBnc(id: String, bncLastImportKey: String, bncLastSaldoMinor: Long)`; `suspend fun commitImport(db, txs: List<Transaction>, newRules: List<Rule> = emptyList(), accountPatch: ParcheCuentaBnc? = null)`; `const val MENSAJE_YA_IMPORTADO`.
  - `suspend fun settleDebt(db, debtId: String, dateIso: String)`.
  - `suspend fun agruparTxs(db, txIds: List<String>, nota: String? = null, categoryId: String? = null): String`, `anadirAGrupo(db, groupId, txIds)`, `quitarDeGrupo(db, txIds)`, `desagrupar(db, groupId)`, `actualizarGrupo(db, groupId, nota: String?, categoryId: String?)` (D9).
  - `suspend fun guardarEvento(db, e: CalEvent)`, `eliminarEvento(db, eventId): Int`, `asignarEvento(db, txId, o: Ocurrencia)`, `desasignarEvento(db, txId)`, `omitirOcurrencia(db, eventId, date)`, `reanudarOcurrencia(db, eventId, date)`.
  - Test helper: `fun dbDePrueba(): CuentasDb`, `fun CuentasDb.fallarAntesDe(operacion: String, tabla: String)`.

- [ ] **Paso 1: Helper de tests `DbDePrueba.kt`.** Robolectric + Room en memoria; `allowMainThreadQueries` SOLO aquí. Los fallos «a mitad» se inyectan con un **trigger SQLite** que aborta una escritura concreta: así se prueba la reversión real sin tocar el código de producción.

```kotlin
package com.cuentasclaras.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.cuentasclaras.app.data.db.CuentasDb

fun dbDePrueba(): CuentasDb =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CuentasDb::class.java)
        .allowMainThreadQueries()
        .build()

/** Hace que la próxima `operacion` ('INSERT' | 'UPDATE' | 'DELETE') sobre `tabla` aborte con
 *  «falla inyectada». Simula un fallo a mitad de una operación compuesta. */
fun CuentasDb.fallarAntesDe(operacion: String, tabla: String) {
    openHelper.writableDatabase.execSQL(
        "CREATE TRIGGER falla_${operacion}_$tabla BEFORE $operacion ON $tabla " +
            "BEGIN SELECT RAISE(ABORT, 'falla inyectada'); END",
    )
}
```

- [ ] **Paso 2: Tests RED.** `OpsTodoONadaTest.kt`:

```kotlin
package com.cuentasclaras.app.data

import com.cuentasclaras.app.data.db.CuentasDb
import com.cuentasclaras.app.data.db.toEntity
import com.cuentasclaras.app.data.ops.ParcheCuentaBnc
import com.cuentasclaras.app.data.ops.agruparTxs
import com.cuentasclaras.app.data.ops.asignarEvento
import com.cuentasclaras.app.data.ops.commitImport
import com.cuentasclaras.app.data.ops.desagrupar
import com.cuentasclaras.app.data.ops.eliminarEvento
import com.cuentasclaras.app.data.ops.settleDebt
import com.cuentasclaras.app.domain.CalEvent
import com.cuentasclaras.app.domain.DebtDirection
import com.cuentasclaras.app.domain.DebtStatus
import com.cuentasclaras.app.domain.Ocurrencia
import com.cuentasclaras.app.domain.RECURRENCIA_QUINCENAL
import com.cuentasclaras.app.domain.Rule
import com.cuentasclaras.app.domain.Transaction
import com.cuentasclaras.app.domain.TxSource
import com.cuentasclaras.app.domain.TxType
import com.cuentasclaras.app.domain.buildDebt
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class OpsTodoONadaTest {
    private lateinit var db: CuentasDb

    @Before fun abrir() = runBlocking {
        db = dbDePrueba()
        seedIfEmpty(db)
    }

    @After fun cerrar() = db.close()

    private fun tx(id: String, type: TxType = TxType.EXPENSE, importKey: String? = "bnc:$id", accountId: String = "acc-bnc") =
        Transaction(id, type, accountId, 100, "2026-07-01", source = TxSource.BNC, importKey = importKey)

    @Test fun `commitImport con un id repetido revierte transacciones, reglas y parche`() = runBlocking {
        db.transactions().insertar(tx("a").toEntity())
        assertFailsWith<Exception> {
            commitImport(
                db,
                txs = listOf(tx("b"), tx("a", importKey = "bnc:otra")),
                newRules = listOf(Rule("r1", "x", "cat-ocio", true)),
                accountPatch = ParcheCuentaBnc("acc-bnc", "k", 1),
            )
        }
        assertEquals(1, db.transactions().contar())
        assertEquals(0, db.rules().contar())
        assertNull(db.accounts().porId("acc-bnc")?.bncLastSaldoMinor)
    }

    @Test fun `commitImport aborta si una importKey ya existe (backstop) y no inserta nada`() = runBlocking {
        db.transactions().insertar(tx("otra-instancia").toEntity())
        assertFailsWith<IllegalStateException> {
            commitImport(db, txs = listOf(tx("esta", importKey = "bnc:otra-instancia")))
        }
        assertEquals(1, db.transactions().contar())
    }

    @Test fun `settleDebt revierte el movimiento si falla al cerrar la deuda y es idempotente`() = runBlocking {
        val deuda = buildDebt(DebtDirection.POR_PAGAR, "Juan — préstamo", 500, "acc-efectivo", "2026-07-22T10:00:00.000Z")
        db.debts().insertar(deuda.toEntity())
        db.fallarAntesDe("UPDATE", "debts")
        assertFailsWith<Exception> { settleDebt(db, deuda.id, "2026-07-22") }
        assertEquals(0, db.transactions().contar())
        assertEquals(DebtStatus.PENDIENTE, db.debts().porId(deuda.id)?.status)

        db.openHelper.writableDatabase.execSQL("DROP TRIGGER falla_UPDATE_debts")
        settleDebt(db, deuda.id, "2026-07-22")
        settleDebt(db, deuda.id, "2026-07-23")
        assertEquals(1, db.transactions().contar())
        assertEquals("2026-07-22", db.debts().porId(deuda.id)?.settledIso)
    }

    @Test fun `agruparTxs revierte el grupo si falla al marcar los movimientos`() = runBlocking {
        db.transactions().insertarTodas(listOf(tx("m1", TxType.INCOME, null), tx("m2", TxType.EXPENSE, null)).map { it.toEntity() })
        db.fallarAntesDe("UPDATE", "transactions")
        assertFailsWith<Exception> { agruparTxs(db, listOf("m1", "m2"), "leche") }
        assertEquals(0, db.txGroups().contar())
        assertNull(db.transactions().porId("m1")?.groupId)
    }

    @Test fun `desagrupar revierte la limpieza si falla al borrar el grupo`() = runBlocking {
        db.transactions().insertarTodas(listOf(tx("m1", TxType.INCOME, null), tx("m2", TxType.EXPENSE, null)).map { it.toEntity() })
        val gid = agruparTxs(db, listOf("m1", "m2"), "leche")
        db.fallarAntesDe("DELETE", "txGroups")
        assertFailsWith<Exception> { desagrupar(db, gid) }
        assertEquals(gid, db.transactions().porId("m1")?.groupId)
        assertEquals(1, db.txGroups().contar())
    }

    @Test fun `eliminarEvento revierte la desasignacion si falla al borrar el evento; asignar valida la direccion`() = runBlocking {
        val quincena = CalEvent("e1", "Quincena", "cat-sueldo", accountId = "acc-bnc", startDate = "2026-01-01",
            recurrence = RECURRENCIA_QUINCENAL, createdIso = "2026-09-04T10:00:00.000Z")
        db.calEvents().guardar(quincena.toEntity())
        db.transactions().insertarTodas(listOf(tx("ing", TxType.INCOME, null), tx("gas", TxType.EXPENSE, null)).map { it.toEntity() })
        asignarEvento(db, "ing", Ocurrencia("e1", "2026-09-15"))
        assertEquals("cat-sueldo", db.transactions().porId("ing")?.categoryId)
        val e = assertFailsWith<IllegalArgumentException> { asignarEvento(db, "gas", Ocurrencia("e1", "2026-09-15")) }
        assertEquals("Este movimiento es un gasto y el evento es un cobro", e.message)

        db.fallarAntesDe("DELETE", "calEvents")
        assertFailsWith<Exception> { eliminarEvento(db, "e1") }
        assertEquals("e1", db.transactions().porId("ing")?.eventId)
        assertEquals(1, db.calEvents().contar())
    }
}
```

- [ ] **Paso 3: Correr → FAIL** (`./gradlew :app:testDebugUnitTest --tests "*OpsTodoONadaTest" --console=plain 2>&1 | tail -30`): referencias sin resolver. La primera ejecución de Robolectric descarga su `android-all` (minutos); usa timeout largo.

- [ ] **Paso 4: `CommitImport.kt`.**

```kotlin
package com.cuentasclaras.app.data.ops

import androidx.room.withTransaction
import com.cuentasclaras.app.data.db.CuentasDb
import com.cuentasclaras.app.data.db.toEntity
import com.cuentasclaras.app.domain.Rule
import com.cuentasclaras.app.domain.Transaction

data class ParcheCuentaBnc(val id: String, val bncLastImportKey: String, val bncLastSaldoMinor: Long)

const val MENSAJE_YA_IMPORTADO = "Alguno de estos movimientos ya fue importado. Vuelve a analizar el archivo."

/** Import todo-o-nada: si algo falla, no entra nada. La puerta normal contra duplicados es el
 *  filtro de la vista previa; esto solo revalida DENTRO de la misma transacción (backstop). */
suspend fun commitImport(
    db: CuentasDb,
    txs: List<Transaction>,
    newRules: List<Rule> = emptyList(),
    accountPatch: ParcheCuentaBnc? = null,
) {
    db.withTransaction {
        if (txs.isNotEmpty()) {
            val keys = txs.mapNotNull { it.importKey }
            // Trozos de 500: límite de variables SQL en el SQLite de Android 8 (999).
            val yaExisten = keys.chunked(500).sumOf { db.transactions().contarPorImportKeys(it) }
            check(yaExisten == 0) { MENSAJE_YA_IMPORTADO }
            db.transactions().insertarTodas(txs.map { it.toEntity() })
        }
        if (newRules.isNotEmpty()) db.rules().insertarTodas(newRules.map { it.toEntity() })
        if (accountPatch != null) {
            db.accounts().parcheBnc(accountPatch.id, accountPatch.bncLastImportKey, accountPatch.bncLastSaldoMinor)
        }
    }
}
```

- [ ] **Paso 5: `DebtOps.kt`.**

```kotlin
package com.cuentasclaras.app.data.ops

import androidx.room.withTransaction
import com.cuentasclaras.app.data.db.CuentasDb
import com.cuentasclaras.app.data.db.toDomain
import com.cuentasclaras.app.data.db.toEntity
import com.cuentasclaras.app.domain.DebtStatus
import com.cuentasclaras.app.domain.buildSettleTx

/** Cierra la deuda creando su movimiento real, todo-o-nada e idempotente: si ya está cerrada,
 *  no hace nada (guard dentro de la transacción). */
suspend fun settleDebt(db: CuentasDb, debtId: String, dateIso: String) {
    db.withTransaction {
        val debt = db.debts().porId(debtId)?.toDomain() ?: throw IllegalArgumentException("La deuda no existe")
        if (debt.status != DebtStatus.PENDIENTE) return@withTransaction
        val tx = buildSettleTx(debt, dateIso)
        db.transactions().insertar(tx.toEntity())
        db.debts().actualizar(debt.copy(status = DebtStatus.CERRADA, settledTxId = tx.id, settledIso = dateIso).toEntity())
    }
}
```

- [ ] **Paso 6: `GroupOps.kt`.**

```kotlin
package com.cuentasclaras.app.data.ops

import androidx.room.withTransaction
import com.cuentasclaras.app.data.db.CuentasDb
import com.cuentasclaras.app.data.db.toEntity
import com.cuentasclaras.app.domain.TxGroup
import com.cuentasclaras.app.domain.TxType
import com.cuentasclaras.app.domain.ahoraIso
import com.cuentasclaras.app.domain.nuevoId

private suspend fun validarAgrupables(db: CuentasDb, txIds: List<String>) {
    val txs = db.transactions().porIds(txIds)
    require(txs.size == txIds.distinct().size) { "Algún movimiento ya no existe" }
    for (t in txs) {
        require(t.type == TxType.INCOME || t.type == TxType.EXPENSE) { "Solo se agrupan gastos e ingresos" }
        require(t.groupId == null) { "Algún movimiento ya pertenece a un grupo" }
    }
}

/** Crea un grupo transaccional con ≥ 2 gastos/ingresos sueltos. Todo-o-nada.
 *  `categoryId` opcional = categoría del residuo virtual (ver categoriaDelResiduo). */
suspend fun agruparTxs(db: CuentasDb, txIds: List<String>, nota: String? = null, categoryId: String? = null): String {
    require(txIds.size >= 2) { "Selecciona al menos 2 movimientos" }
    val grupo = TxGroup(
        id = nuevoId(),
        nota = nota?.trim()?.takeIf { it.isNotEmpty() },
        categoryId = categoryId?.takeIf { it.isNotEmpty() },
        createdIso = ahoraIso(),
    )
    db.withTransaction {
        val cat = grupo.categoryId
        if (cat != null) require(db.categories().porId(cat) != null) { "La categoría no existe" }
        validarAgrupables(db, txIds)
        db.txGroups().insertar(grupo.toEntity())
        db.transactions().asignarGrupo(txIds, grupo.id)
    }
    return grupo.id
}

suspend fun anadirAGrupo(db: CuentasDb, groupId: String, txIds: List<String>) {
    require(txIds.isNotEmpty()) { "Selecciona al menos 1 movimiento" }
    db.withTransaction {
        require(db.txGroups().porId(groupId) != null) { "El grupo no existe" }
        validarAgrupables(db, txIds)
        db.transactions().asignarGrupo(txIds, groupId)
    }
}

/** Saca movimientos de sus grupos; un grupo que baja de 2 miembros se disuelve. */
suspend fun quitarDeGrupo(db: CuentasDb, txIds: List<String>) {
    db.withTransaction {
        val gruposTocados = db.transactions().porIds(txIds).mapNotNull { it.groupId }.distinct()
        db.transactions().limpiarGrupo(txIds)
        for (gid in gruposTocados) {
            val restantes = db.transactions().idsDelGrupo(gid)
            if (restantes.size < 2) {
                db.transactions().limpiarGrupo(restantes)
                db.txGroups().borrar(gid)
            }
        }
    }
}

/** Revierte el grupo completo: todos los miembros vuelven a ser movimientos normales. */
suspend fun desagrupar(db: CuentasDb, groupId: String) {
    db.withTransaction {
        db.transactions().limpiarGrupo(db.transactions().idsDelGrupo(groupId))
        db.txGroups().borrar(groupId)
    }
}

/** Reemplaza nota y categoría del grupo (D9). Vacío o null → se borra el campo. */
suspend fun actualizarGrupo(db: CuentasDb, groupId: String, nota: String?, categoryId: String?) {
    db.withTransaction {
        val grupo = db.txGroups().porId(groupId) ?: throw IllegalArgumentException("El grupo no existe")
        val cat = categoryId?.takeIf { it.isNotEmpty() }
        if (cat != null) require(db.categories().porId(cat) != null) { "La categoría no existe" }
        db.txGroups().actualizar(grupo.copy(nota = nota?.trim()?.takeIf { it.isNotEmpty() }, categoryId = cat))
    }
}
```

- [ ] **Paso 7: `EventOps.kt`.**

```kotlin
package com.cuentasclaras.app.data.ops

import androidx.room.withTransaction
import com.cuentasclaras.app.data.db.CuentasDb
import com.cuentasclaras.app.data.db.toDomain
import com.cuentasclaras.app.data.db.toEntity
import com.cuentasclaras.app.domain.CalEvent
import com.cuentasclaras.app.domain.Ocurrencia
import com.cuentasclaras.app.domain.TxType
import com.cuentasclaras.app.domain.comoKind
import com.cuentasclaras.app.domain.esFechaIso
import com.cuentasclaras.app.domain.kindDeEvento
import com.cuentasclaras.app.domain.validarAsignacion
import com.cuentasclaras.app.domain.validarEvento

/** Alta o edición (upsert del objeto COMPLETO: el editor debe traer `omitidas`). Rechaza cambiar
 *  la dirección del evento si eso dejaría movimientos asignados incompatibles. */
suspend fun guardarEvento(db: CuentasDb, e: CalEvent) {
    validarEvento(e)
    db.withTransaction {
        val kind = kindDeEvento(e, db.categories().todas().map { it.toDomain() })
            ?: throw IllegalArgumentException("La categoría no existe")
        val incompatibles = db.transactions().delEvento(e.id).count { it.type.comoKind() != kind }
        require(incompatibles == 0) { "Desasigna primero los $incompatibles movimientos" }
        db.calEvents().guardar(e.toEntity())
    }
}

/** Borra el evento y desasigna sus movimientos en UNA transacción. Los movimientos NUNCA se borran.
 *  Devuelve cuántos se desasignaron. */
suspend fun eliminarEvento(db: CuentasDb, eventId: String): Int =
    db.withTransaction {
        val desasignadas = db.transactions().desasignarDelEvento(eventId)
        db.calEvents().borrar(eventId)
        desasignadas
    }

/** Asigna la tx a la ocurrencia y copia la categoría del evento. No toca monto/fecha/cuenta/tipo. */
suspend fun asignarEvento(db: CuentasDb, txId: String, o: Ocurrencia) {
    db.withTransaction {
        val tx = db.transactions().porId(txId)
        require(tx != null && (tx.type == TxType.INCOME || tx.type == TxType.EXPENSE)) { "Solo se asignan gastos e ingresos" }
        val evento = db.calEvents().porId(o.eventId)?.toDomain() ?: throw IllegalArgumentException("El evento no existe")
        require(esFechaIso(o.date)) { "Fecha inválida" }
        validarAsignacion(tx.type, evento, db.categories().todas().map { it.toDomain() })
        db.transactions().asignarEvento(txId, evento.id, o.date, evento.categoryId)
    }
}

/** Quita la asignación conservando la categoría. Idempotente. */
suspend fun desasignarEvento(db: CuentasDb, txId: String) {
    db.transactions().desasignarTx(txId)
}

/** Silencia una ocurrencia. Idempotente; rechaza si ya tiene movimientos asignados. */
suspend fun omitirOcurrencia(db: CuentasDb, eventId: String, date: String) {
    db.withTransaction {
        val evento = db.calEvents().porId(eventId)?.toDomain() ?: throw IllegalArgumentException("El evento no existe")
        require(esFechaIso(date)) { "Fecha inválida" }
        require(db.transactions().contarDeOcurrencia(eventId, date) == 0) { "Ya tiene movimientos asignados" }
        val omitidas = evento.omitidas ?: emptyList()
        if (date in omitidas) return@withTransaction
        db.calEvents().guardar(evento.copy(omitidas = (omitidas + date).sorted()).toEntity())
    }
}

/** Reactiva una ocurrencia omitida. Si la lista queda vacía, se guarda null. */
suspend fun reanudarOcurrencia(db: CuentasDb, eventId: String, date: String) {
    db.withTransaction {
        val evento = db.calEvents().porId(eventId)?.toDomain() ?: throw IllegalArgumentException("El evento no existe")
        val quedan = (evento.omitidas ?: emptyList()).filter { it != date }
        db.calEvents().guardar(evento.copy(omitidas = quedan.takeIf { it.isNotEmpty() }).toEntity())
    }
}
```

- [ ] **Paso 8: Correr → PASS** (mismo comando del Paso 3) y la suite completa en verde. Si `runBlocking` + `withTransaction` se bloqueara en Robolectric, añade `.setQueryCoroutineContext(Dispatchers.IO)` o `.setTransactionExecutor(Executors.newSingleThreadExecutor())` al builder de `dbDePrueba()` (solo ahí) y documéntalo en el reporte.

- [ ] **Paso 9: Commit.**

```bash
git add android/app/src/main/java/com/cuentasclaras/app/data/ops/ android/app/src/test/java/com/cuentasclaras/app/data/DbDePrueba.kt android/app/src/test/java/com/cuentasclaras/app/data/OpsTodoONadaTest.kt
git commit -m "feat(datos): operaciones todo-o-nada de import, deudas, grupos y eventos con tests de reversión" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Tarea 7: Respaldo JSON v3 (exportar, validar, importar)

**Skills:** `superpowers:test-driven-development` (`RespaldoTest`) · `output-skill`.

**Files:**
- Create: `android/app/src/main/java/com/cuentasclaras/app/backup/Respaldo.kt`
- Create: `android/app/src/test/resources/respaldo-demo-v3.json` (datos INVENTADOS al estilo de un export de la PWA; también se usa para sembrar el dispositivo en las verificaciones)
- Test: `android/app/src/test/java/com/cuentasclaras/app/backup/RespaldoTest.kt`

Fuente: `src/backup/backup.ts` y `backup.test.ts`. `saveFile.ts` NO se porta: guardar/abrir va por SAF en la UI (Tareas 9 y 16).

**Interfaces:**
- Consumes: T2 (`JsonCuentas`, `JsonRespaldo`, tipos, `ahoraIso`), T5 (DAOs, mapeos), T6 (`dbDePrueba`, `fallarAntesDe` en tests).
- Produces: `@Serializable data class BackupFile(schemaVersion: Int, exportedAt, accounts, transactions, categories, rules, rates, settings, debts, txGroups, calEvents)`; `sealed interface ValidacionRespaldo { data class Ok(backup); data class Error(mensaje) }`; `fun validateBackup(data: JsonElement?): ValidacionRespaldo`; `fun leerRespaldo(texto: String): ValidacionRespaldo`; `suspend fun exportBackup(db, exportedAt: String = ahoraIso()): BackupFile`; `fun respaldoAJson(b: BackupFile): String`; `suspend fun importBackup(db, b: BackupFile)`; `fun backupFilename(dateIso: String): String`; `data class Conteos(...)` + `suspend fun contarTablas(db): Conteos`.

- [ ] **Paso 1: Fixture `respaldo-demo-v3.json`.** Escrito como lo exporta la PWA: opcionales ausentes (no `null`), `vesPerUsd` entero en una fila, una clave de nivel superior desconocida (`"origen"`) que debe ignorarse. Copia EXACTA:

```json
{
  "schemaVersion": 3,
  "exportedAt": "2026-10-10T14:00:00.000Z",
  "origen": "pwa-v3",
  "accounts": [
    { "id": "acc-bnc", "name": "BNC", "currency": "VES", "kind": "bank", "isSavings": false, "archived": false, "bncLastImportKey": "bnc:2026-06-19:100000000:09:00:00.000:-10000", "bncLastSaldoMinor": 50000 },
    { "id": "acc-zinli", "name": "Zinli", "currency": "USD", "kind": "wallet", "isSavings": false, "archived": false },
    { "id": "acc-binance", "name": "Binance", "currency": "USD", "kind": "snapshot", "isSavings": true, "archived": false, "enDisposicion": true },
    { "id": "acc-efectivo", "name": "Efectivo", "currency": "USD", "kind": "cash", "isSavings": false, "archived": false },
    { "id": "acc-efectivo-ahorro", "name": "Efectivo ahorro", "currency": "USD", "kind": "cash", "isSavings": true, "archived": false }
  ],
  "transactions": [
    { "id": "tx-demo-01", "type": "income", "accountId": "acc-bnc", "amountMinor": 8000000, "date": "2026-09-30", "categoryId": "cat-sueldo", "note": "Quincena", "source": "manual", "eventId": "evento-quincena", "eventDate": "2026-09-30" },
    { "id": "tx-demo-02", "type": "expense", "accountId": "acc-bnc", "amountMinor": 345079, "date": "2026-10-01", "time": "12:34:56.789", "categoryId": "cat-supermercado", "note": "PANADERIA CENTRAL C A", "source": "bnc", "importKey": "bnc:2026-10-01:100000011:12:34:56.789:-345079" },
    { "id": "tx-demo-03", "type": "expense", "accountId": "acc-bnc", "amountMinor": 360, "date": "2026-10-01", "time": "12:35:00.100", "categoryId": "cat-comisiones", "note": "Comisión Pago Movil", "source": "bnc", "importKey": "bnc:2026-10-01:100000012:12:35:00.100:-360" },
    { "id": "tx-demo-04", "type": "expense", "accountId": "acc-efectivo", "amountMinor": 1250, "date": "2026-10-03", "categoryId": "cat-comida-fuera", "note": "Arepas", "source": "manual" },
    { "id": "tx-demo-05", "type": "transfer", "accountId": "acc-zinli", "toAccountId": "acc-bnc", "amountMinor": 5000, "toAmountMinor": 900000, "rateUsed": 180, "date": "2026-10-04", "source": "manual" },
    { "id": "tx-demo-06", "type": "income", "accountId": "acc-efectivo", "amountMinor": 500, "date": "2026-10-08", "note": "Me pasaron para la leche", "source": "manual", "groupId": "grupo-demo" },
    { "id": "tx-demo-07", "type": "expense", "accountId": "acc-efectivo", "amountMinor": 350, "date": "2026-10-08", "time": "09:30:00", "categoryId": "cat-supermercado", "note": "Leche", "source": "manual", "groupId": "grupo-demo" },
    { "id": "tx-demo-08", "type": "adjustment", "accountId": "acc-binance", "amountMinor": 12000, "date": "2026-10-01", "note": "Ajuste de saldo", "source": "adjust" },
    { "id": "tx-demo-09", "type": "expense", "accountId": "acc-zinli", "amountMinor": 1599, "date": "2026-10-05", "categoryId": "cat-suscripciones", "note": "Netflix", "source": "manual", "eventId": "evento-netflix", "eventDate": "2026-10-05" },
    { "id": "tx-demo-10", "type": "expense", "accountId": "acc-bnc", "amountMinor": 120000, "date": "2026-10-09", "categoryId": "cat-transporte", "note": "Taxi", "source": "manual" },
    { "id": "tx-demo-11", "type": "income", "accountId": "acc-bnc", "amountMinor": 150000, "date": "2026-10-12", "categoryId": "cat-pago-movil-recibido", "note": "Pago móvil de un amigo", "source": "manual" },
    { "id": "tx-demo-12", "type": "expense", "accountId": "acc-efectivo", "amountMinor": 800, "date": "2026-08-20", "categoryId": "cat-ocio", "note": "Cine", "source": "manual" }
  ],
  "categories": [
    { "id": "cat-supermercado", "name": "Supermercado", "icon": "🛒", "color": "#4ade80", "kind": "expense", "monthlyBudgetMinor": 15000 },
    { "id": "cat-comida-fuera", "name": "Comida fuera", "icon": "🍔", "color": "#fbbf24", "kind": "expense", "monthlyBudgetMinor": 8000 },
    { "id": "cat-transporte", "name": "Transporte", "icon": "🚌", "color": "#60a5fa", "kind": "expense" },
    { "id": "cat-salud", "name": "Salud", "icon": "💊", "color": "#f87171", "kind": "expense" },
    { "id": "cat-servicios", "name": "Servicios", "icon": "💡", "color": "#facc15", "kind": "expense" },
    { "id": "cat-comisiones", "name": "Comisiones bancarias", "icon": "🏦", "color": "#94a3b8", "kind": "expense" },
    { "id": "cat-hogar", "name": "Hogar", "icon": "🏠", "color": "#a78bfa", "kind": "expense" },
    { "id": "cat-ocio", "name": "Ocio/Juegos", "icon": "🎮", "color": "#f472b6", "kind": "expense" },
    { "id": "cat-suscripciones", "name": "Suscripciones", "icon": "📺", "color": "#22d3ee", "kind": "expense" },
    { "id": "cat-ropa", "name": "Ropa", "icon": "👕", "color": "#fb923c", "kind": "expense" },
    { "id": "cat-otros-gastos", "name": "Otros gastos", "icon": "📦", "color": "#9ca3af", "kind": "expense" },
    { "id": "cat-pago-movil-recibido", "name": "Pago móvil recibido", "icon": "📲", "color": "#4ade80", "kind": "income" },
    { "id": "cat-transferencia-recibida", "name": "Transferencia recibida", "icon": "🔁", "color": "#38bdf8", "kind": "income" },
    { "id": "cat-sueldo", "name": "Sueldo/Pagos", "icon": "💼", "color": "#34d399", "kind": "income" },
    { "id": "cat-otros-ingresos", "name": "Otros ingresos", "icon": "➕", "color": "#cbd5e1", "kind": "income" },
    { "id": "cat-deudas", "name": "Deudas", "icon": "🤝", "color": "#c084fc", "kind": "expense" },
    { "id": "cat-vuelto-ingreso", "name": "Vuelto", "icon": "↩️", "color": "#34d399", "kind": "income" },
    { "id": "cat-vuelto-gasto", "name": "Vuelto", "icon": "↪️", "color": "#f87171", "kind": "expense" }
  ],
  "rules": [
    { "id": "regla-demo", "pattern": "PANADERIA", "categoryId": "cat-supermercado", "autoCreated": true }
  ],
  "rates": [
    { "date": "2026-10-01", "source": "BCV", "vesPerUsd": 180 },
    { "date": "2026-10-10", "source": "BCV", "vesPerUsd": 185.5 },
    { "date": "2026-10-10", "source": "BINANCE", "vesPerUsd": 230.25 }
  ],
  "settings": [
    { "id": "main", "viewCurrency": "USD", "rateSource": "BCV", "lastAccountId": "acc-bnc", "lastRatesFetchDate": "2026-10-10" }
  ],
  "debts": [
    { "id": "deuda-demo-1", "direction": "porPagar", "concepto": "Préstamo de Ana", "amountMinor": 2000, "accountId": "acc-efectivo", "status": "pendiente", "createdIso": "2026-10-02T12:00:00.000Z" },
    { "id": "deuda-demo-2", "direction": "porCobrar", "concepto": "Almuerzo con Luis", "amountMinor": 150000, "accountId": "acc-bnc", "status": "pendiente", "createdIso": "2026-10-05T12:00:00.000Z" }
  ],
  "txGroups": [
    { "id": "grupo-demo", "nota": "me pasaron para la leche", "categoryId": "cat-supermercado", "createdIso": "2026-10-08T09:00:00.000Z" }
  ],
  "calEvents": [
    { "id": "evento-quincena", "titulo": "Quincena", "categoryId": "cat-sueldo", "accountId": "acc-bnc", "amountMinor": 8000000, "startDate": "2026-01-01", "recurrence": { "kind": "monthly", "days": [15, 31] }, "createdIso": "2026-09-04T10:00:00.000Z" },
    { "id": "evento-netflix", "titulo": "Netflix", "categoryId": "cat-suscripciones", "accountId": "acc-zinli", "amountMinor": 1599, "startDate": "2026-01-05", "recurrence": { "kind": "monthly", "days": [5] }, "omitidas": ["2026-09-05"], "createdIso": "2026-09-04T10:05:00.000Z" }
  ]
}
```

- [ ] **Paso 2: Tests RED.** `RespaldoTest.kt`:

```kotlin
package com.cuentasclaras.app.backup

import com.cuentasclaras.app.data.db.CuentasDb
import com.cuentasclaras.app.data.db.toDomain
import com.cuentasclaras.app.data.dbDePrueba
import com.cuentasclaras.app.data.fallarAntesDe
import com.cuentasclaras.app.data.seedIfEmpty
import com.cuentasclaras.app.domain.Recurrence
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class RespaldoTest {
    private lateinit var db: CuentasDb
    private val demo = javaClass.getResource("/respaldo-demo-v3.json")!!.readText()

    @Before fun abrir() { db = dbDePrueba() }
    @After fun cerrar() = db.close()

    private fun ok(texto: String) = assertIs<ValidacionRespaldo.Ok>(leerRespaldo(texto)).backup
    private fun error(texto: String) = assertIs<ValidacionRespaldo.Error>(leerRespaldo(texto)).mensaje

    @Test fun `un respaldo de la PWA con opcionales ausentes, tasa entera y clave extra se importa completo`() = runBlocking {
        importBackup(db, ok(demo))
        assertEquals(Conteos(5, 12, 18, 1, 3, 1, 2, 1, 2), contarTablas(db))
        assertEquals(180.0, db.rates().todas().first { it.date == "2026-10-01" }.vesPerUsd)
        val netflix = db.calEvents().porId("evento-netflix")!!.toDomain()
        assertEquals(Recurrence.Monthly(listOf(5)), netflix.recurrence)
        assertEquals(listOf("2026-09-05"), netflix.omitidas)
        assertEquals(true, db.accounts().porId("acc-binance")!!.enDisposicion)
        assertEquals(null, db.accounts().porId("acc-zinli")!!.enDisposicion)
    }

    @Test fun `exportar y volver a importar reproduce todo y el JSON no lleva null`() = runBlocking {
        importBackup(db, ok(demo))
        val json = respaldoAJson(exportBackup(db, "2026-10-15T12:00:00.000Z"))
        assertFalse(json.contains("null"), "la PWA rompe con claves en null")
        assertTrue(json.contains("\"kind\": \"monthly\""))
        assertTrue(json.contains("\"direction\": \"porPagar\""))
        val otra = dbDePrueba()
        seedIfEmpty(otra)
        importBackup(otra, ok(json))
        assertEquals(contarTablas(db), contarTablas(otra))
        assertEquals("grupo-demo", otra.transactions().porId("tx-demo-07")!!.groupId)
        otra.close()
    }

    @Test fun `validar acepta v3 exacto, normaliza v2 y rechaza v1, versiones raras y tablas faltantes`() {
        val v2 = """{"schemaVersion":2,"exportedAt":"x","accounts":[],"transactions":[],"categories":[],"rules":[],"rates":[],"settings":[],"debts":[],"txGroups":[]}"""
        assertEquals(emptyList(), ok(v2).calEvents)
        assertEquals(3, ok(v2).schemaVersion)
        assertEquals("Al respaldo le falta la tabla \"calEvents\"", error(v2.replace("\"schemaVersion\":2", "\"schemaVersion\":3")))
        assertTrue(error(v2.replace("\"schemaVersion\":2", "\"schemaVersion\":1")).contains("versión 1"))
        assertEquals("Versión de respaldo no soportada: 4", error(v2.replace("\"schemaVersion\":2", "\"schemaVersion\":4")))
        assertEquals("El archivo no es un respaldo de Cuentas Claras", error("esto no es json"))
        assertEquals("cuentas-claras-respaldo-2026-07-07.json", backupFilename("2026-07-07"))
    }

    @Test fun `importar es todo-o-nada si falla a mitad y deja intactos los datos previos`() = runBlocking {
        seedIfEmpty(db)
        val antes = contarTablas(db)
        db.fallarAntesDe("INSERT", "calEvents")
        assertFailsWith<Exception> { importBackup(db, ok(demo)) }
        assertEquals(antes, contarTablas(db))
    }
}
```

- [ ] **Paso 3: Correr → FAIL** (`./gradlew :app:testDebugUnitTest --tests "*RespaldoTest" --console=plain 2>&1 | tail -30`).

- [ ] **Paso 4: `Respaldo.kt`.**

```kotlin
package com.cuentasclaras.app.backup

import androidx.room.withTransaction
import com.cuentasclaras.app.data.db.CuentasDb
import com.cuentasclaras.app.data.db.toDomain
import com.cuentasclaras.app.data.db.toEntity
import com.cuentasclaras.app.domain.Account
import com.cuentasclaras.app.domain.CalEvent
import com.cuentasclaras.app.domain.Category
import com.cuentasclaras.app.domain.Debt
import com.cuentasclaras.app.domain.JsonCuentas
import com.cuentasclaras.app.domain.JsonRespaldo
import com.cuentasclaras.app.domain.Rate
import com.cuentasclaras.app.domain.Rule
import com.cuentasclaras.app.domain.Settings
import com.cuentasclaras.app.domain.Transaction
import com.cuentasclaras.app.domain.TxGroup
import com.cuentasclaras.app.domain.ahoraIso
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** Mismo formato que la PWA (schemaVersion 3, 9 tablas, camelCase). La tabla `capturas` NO va aquí. */
@Serializable
data class BackupFile(
    val schemaVersion: Int,
    val exportedAt: String,
    val accounts: List<Account>,
    val transactions: List<Transaction>,
    val categories: List<Category>,
    val rules: List<Rule>,
    val rates: List<Rate>,
    val settings: List<Settings>,
    val debts: List<Debt>,
    val txGroups: List<TxGroup>,
    val calEvents: List<CalEvent>,
)

sealed interface ValidacionRespaldo {
    data class Ok(val backup: BackupFile) : ValidacionRespaldo
    data class Error(val mensaje: String) : ValidacionRespaldo
}

private val TABLAS_V2 = listOf("accounts", "transactions", "categories", "rules", "rates", "settings", "debts", "txGroups")
private val TABLAS = TABLAS_V2 + "calEvents"
private const val NO_ES_RESPALDO = "El archivo no es un respaldo de Cuentas Claras"

private fun faltaTabla(b: JsonObject, tablas: List<String>): String? = tablas.firstOrNull { b[it] !is JsonArray }

private fun decodificar(obj: JsonObject): ValidacionRespaldo = try {
    ValidacionRespaldo.Ok(JsonCuentas.decodeFromJsonElement(BackupFile.serializer(), obj))
} catch (e: SerializationException) {
    ValidacionRespaldo.Error("El respaldo tiene datos ilegibles: ${e.message}")
} catch (e: IllegalArgumentException) {
    ValidacionRespaldo.Error("El respaldo tiene datos ilegibles: ${e.message}")
}

/** v3 exige las 9 tablas. v2 (8 tablas) se acepta normalizado con calEvents vacío.
 *  v1 sigue rechazado con el mismo mensaje que la web. */
fun validateBackup(data: JsonElement?): ValidacionRespaldo {
    val b = data as? JsonObject ?: return ValidacionRespaldo.Error(NO_ES_RESPALDO)
    val crudo = b["schemaVersion"]
    val version = (crudo as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
    return when (version) {
        1 -> ValidacionRespaldo.Error("Este respaldo es de la versión 1 y ya no es compatible: re-ingresa o re-importa tus datos")
        2 -> faltaTabla(b, TABLAS_V2)?.let { ValidacionRespaldo.Error("Al respaldo le falta la tabla \"$it\"") }
            ?: decodificar(JsonObject(b + mapOf("schemaVersion" to JsonPrimitive(3), "calEvents" to JsonArray(emptyList()))))
        3 -> faltaTabla(b, TABLAS)?.let { ValidacionRespaldo.Error("Al respaldo le falta la tabla \"$it\"") }
            ?: decodificar(b)
        else -> ValidacionRespaldo.Error("Versión de respaldo no soportada: ${crudo?.toString() ?: "undefined"}")
    }
}

/** Texto del archivo → validación. Cualquier JSON roto es «no es un respaldo». */
fun leerRespaldo(texto: String): ValidacionRespaldo {
    val elemento = try {
        JsonCuentas.parseToJsonElement(texto.removePrefix("﻿"))
    } catch (e: SerializationException) {
        return ValidacionRespaldo.Error(NO_ES_RESPALDO)
    }
    return validateBackup(elemento)
}

suspend fun exportBackup(db: CuentasDb, exportedAt: String = ahoraIso()): BackupFile =
    db.withTransaction {
        BackupFile(
            schemaVersion = 3,
            exportedAt = exportedAt,
            accounts = db.accounts().todas().map { it.toDomain() },
            transactions = db.transactions().todas().map { it.toDomain() },
            categories = db.categories().todas().map { it.toDomain() },
            rules = db.rules().todas().map { it.toDomain() },
            rates = db.rates().todas().map { it.toDomain() },
            settings = db.settings().todas().map { it.toDomain() },
            debts = db.debts().todas().map { it.toDomain() },
            txGroups = db.txGroups().todas().map { it.toDomain() },
            calEvents = db.calEvents().todas().map { it.toDomain() },
        )
    }

fun respaldoAJson(b: BackupFile): String = JsonRespaldo.encodeToString(BackupFile.serializer(), b)

/** Importar reemplaza TODO el contenido de las 9 tablas en una sola transacción (todo-o-nada).
 *  La tabla `capturas` no se toca. */
suspend fun importBackup(db: CuentasDb, b: BackupFile) {
    db.withTransaction {
        db.accounts().borrarTodo()
        db.transactions().borrarTodo()
        db.categories().borrarTodo()
        db.rules().borrarTodo()
        db.rates().borrarTodo()
        db.settings().borrarTodo()
        db.debts().borrarTodo()
        db.txGroups().borrarTodo()
        db.calEvents().borrarTodo()
        db.accounts().insertar(b.accounts.map { it.toEntity() })
        db.transactions().insertarTodas(b.transactions.map { it.toEntity() })
        db.categories().insertarTodas(b.categories.map { it.toEntity() })
        db.rules().insertarTodas(b.rules.map { it.toEntity() })
        db.rates().insertarTodas(b.rates.map { it.toEntity() })
        db.settings().insertarTodas(b.settings.map { it.toEntity() })
        db.debts().insertarTodas(b.debts.map { it.toEntity() })
        db.txGroups().insertarTodas(b.txGroups.map { it.toEntity() })
        db.calEvents().insertarTodas(b.calEvents.map { it.toEntity() })
    }
}

fun backupFilename(dateIso: String): String = "cuentas-claras-respaldo-$dateIso.json"

data class Conteos(
    val accounts: Int, val transactions: Int, val categories: Int, val rules: Int, val rates: Int,
    val settings: Int, val debts: Int, val txGroups: Int, val calEvents: Int,
)

suspend fun contarTablas(db: CuentasDb): Conteos = Conteos(
    db.accounts().contar(), db.transactions().contar(), db.categories().contar(), db.rules().contar(),
    db.rates().contar(), db.settings().contar(), db.debts().contar(), db.txGroups().contar(), db.calEvents().contar(),
)
```

- [ ] **Paso 5: Correr → PASS** y suite completa en verde. Si `json.contains("null")` falla, revisa la regla D4 (algún campo nullable sin `= null` o `encodeDefaults` mal configurado); NO relajes el test.

- [ ] **Paso 6: Commit.**

```bash
git add android/app/src/main/java/com/cuentasclaras/app/backup/Respaldo.kt android/app/src/test/resources/respaldo-demo-v3.json android/app/src/test/java/com/cuentasclaras/app/backup/RespaldoTest.kt
git commit -m "feat(respaldo): exportar, validar e importar el JSON v3 compatible con la PWA" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Tarea 8: Tasas en línea (dolarapi → vcoud) y refresco diario

**Skills:** `superpowers:test-driven-development` (solo los dos parsers, un caso cada uno) · `output-skill`.

**Files:**
- Create: `android/app/src/main/java/com/cuentasclaras/app/rates/Providers.kt`
- Create: `android/app/src/test/resources/rates/dolarapi-oficial.json` (copia tal cual de `src/rates/fixtures/dolarapi-oficial.json`)
- Create: `android/app/src/test/resources/rates/criptodolar-vcoud.json` (copia tal cual de `src/rates/fixtures/criptodolar-vcoud.json`)
- Test: `android/app/src/test/java/com/cuentasclaras/app/rates/ProvidersTest.kt`

Fuentes: `src/rates/providers.ts`; URLs y campos en `docs/spikes/2026-07-07-cors-tasas.md` (el CORS ya no aplica).

**Interfaces:**
- Consumes: T2 (`JsonCuentas`, `Rate`, `RateSource`), T5 (`getSettings`, `saveSettings`, DAOs).
- Produces: `data class FetchedRates(bcv: Double? = null, binance: Double? = null)`; `fun interface RateProvider { suspend fun fetch(): FetchedRates }`; `parseDolarApiOficial(json: JsonElement): FetchedRates`; `parseCriptoDolarVcoud(json: JsonElement): FetchedRates`; `fun proveedoresPorDefecto(http: OkHttpClient): List<RateProvider>`; `suspend fun fetchRatesWithFallback(providers): FetchedRates`; `suspend fun storeFetchedRates(db, hoyIso, r): Int`; `suspend fun refreshRatesOncePerDay(db, hoyIso, providers)`; `fun crearHttp(): OkHttpClient` (timeout 8 s).

- [ ] **Paso 1: Copiar las fixtures** a `android/app/src/test/resources/rates/` con `cp` (son datos públicos de tasas, sin datos personales).

- [ ] **Paso 2: Test RED.** `ProvidersTest.kt`:

```kotlin
package com.cuentasclaras.app.rates

import com.cuentasclaras.app.domain.JsonCuentas
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProvidersTest {
    private fun fixture(nombre: String) =
        JsonCuentas.parseToJsonElement(javaClass.getResource("/rates/$nombre")!!.readText())

    @Test fun `dolarapi trae el BCV en promedio`() {
        assertEquals(674.9305, parseDolarApiOficial(fixture("dolarapi-oficial.json")).bcv)
    }

    @Test fun `vcoud trae el BCV en el item dolar-bcv`() {
        val bcv = parseCriptoDolarVcoud(fixture("criptodolar-vcoud.json")).bcv
        assertTrue(bcv != null && bcv > 0)
    }
}
```

- [ ] **Paso 3: Correr → FAIL** (`--tests "*ProvidersTest"`).

- [ ] **Paso 4: `Providers.kt`.**

```kotlin
package com.cuentasclaras.app.rates

import com.cuentasclaras.app.data.db.CuentasDb
import com.cuentasclaras.app.data.db.toEntity
import com.cuentasclaras.app.data.getSettings
import com.cuentasclaras.app.data.saveSettings
import com.cuentasclaras.app.domain.JsonCuentas
import com.cuentasclaras.app.domain.Rate
import com.cuentasclaras.app.domain.RateSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

data class FetchedRates(
    val bcv: Double? = null,
    /** Reservado: ningún proveedor online lo trae; Binance sale del override manual. */
    val binance: Double? = null,
)

fun interface RateProvider {
    suspend fun fetch(): FetchedRates
}

private fun numeroPositivo(e: JsonElement?): Double? =
    (e as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull?.takeIf { it > 0 }

/** ve.dolarapi.com/v1/dolares/oficial → BCV en `promedio`. */
fun parseDolarApiOficial(json: JsonElement): FetchedRates =
    FetchedRates(bcv = numeroPositivo((json as? JsonObject)?.get("promedio")))

/** exchange.vcoud.com/coins/latest → array; BCV = item con `slug == "dolar-bcv"`, valor en `price`. */
fun parseCriptoDolarVcoud(json: JsonElement): FetchedRates {
    val item = (json as? JsonArray)?.firstOrNull {
        ((it as? JsonObject)?.get("slug") as? JsonPrimitive)?.content == "dolar-bcv"
    } as? JsonObject
    return FetchedRates(bcv = numeroPositivo(item?.get("price")))
}

fun crearHttp(): OkHttpClient = OkHttpClient.Builder().callTimeout(8, TimeUnit.SECONDS).build()

private suspend fun fetchJson(http: OkHttpClient, url: String): JsonElement = withContext(Dispatchers.IO) {
    http.newCall(Request.Builder().url(url).build()).execute().use { res ->
        if (!res.isSuccessful) throw IOException("HTTP ${res.code}")
        JsonCuentas.parseToJsonElement(res.body?.string() ?: throw IOException("Respuesta vacía"))
    }
}

/** Cadena solo-BCV: dolarapi (primario) → vcoud (fallback). URLs de docs/spikes/2026-07-07-cors-tasas.md. */
fun proveedoresPorDefecto(http: OkHttpClient): List<RateProvider> = listOf(
    RateProvider { parseDolarApiOficial(fetchJson(http, "https://ve.dolarapi.com/v1/dolares/oficial")) },
    RateProvider { parseCriptoDolarVcoud(fetchJson(http, "https://exchange.vcoud.com/coins/latest")) },
)

/** Recorre la cadena; en cuanto un proveedor da el BCV, para. Los errores pasan al siguiente. */
suspend fun fetchRatesWithFallback(providers: List<RateProvider>): FetchedRates {
    var out = FetchedRates()
    for (p in providers) {
        if (out.bcv != null) break
        try {
            val r = p.fetch()
            out = FetchedRates(bcv = out.bcv ?: r.bcv, binance = out.binance ?: r.binance)
        } catch (e: Exception) {
            // siguiente proveedor; el último recurso siempre es la tasa manual
        }
    }
    return out
}

suspend fun storeFetchedRates(db: CuentasDb, hoyIso: String, r: FetchedRates): Int {
    val filas = buildList {
        r.bcv?.let { add(Rate(hoyIso, RateSource.BCV, it)) }
        r.binance?.let { add(Rate(hoyIso, RateSource.BINANCE, it)) }
    }
    if (filas.isNotEmpty()) db.rates().guardarTodas(filas.map { it.toEntity() })
    return filas.size
}

/** Una vez al día al abrir la app (sin WorkManager). Sin red: silencioso. */
suspend fun refreshRatesOncePerDay(db: CuentasDb, hoyIso: String, providers: List<RateProvider>) {
    if (getSettings(db).lastRatesFetchDate == hoyIso) return
    val guardadas = storeFetchedRates(db, hoyIso, fetchRatesWithFallback(providers))
    if (guardadas > 0) saveSettings(db) { it.copy(lastRatesFetchDate = hoyIso) }
}
```

- [ ] **Paso 5: Correr → PASS** y suite completa en verde.

- [ ] **Paso 6: Commit.**

```bash
git add android/app/src/main/java/com/cuentasclaras/app/rates/Providers.kt android/app/src/test/resources/rates/ android/app/src/test/java/com/cuentasclaras/app/rates/ProvidersTest.kt
git commit -m "feat(tasas): proveedores dolarapi y vcoud con OkHttp y refresco una vez al día" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---
### Tarea 9: App mínima de la fase 1 — contenedor, arranque y pantalla Respaldo + APK y validación

**Skills:** `ui-ux-pro-max:ui-ux-pro-max` (pantalla mínima, con Material 3 por defecto: la identidad visual llega en la Tarea 10) · `mattpocock-skills:wizard` (pasos que solo Daniel puede hacer en el Xiaomi) · `superpowers:verification-before-completion` · al cierre `mattpocock-skills:code-review` + `superpowers:requesting-code-review` · `output-skill`.

**Files:**
- Create: `android/app/src/main/java/com/cuentasclaras/app/CuentasApp.kt`
- Modify: `android/app/src/main/java/com/cuentasclaras/app/MainActivity.kt` (reescritura completa)
- Create: `android/app/src/main/java/com/cuentasclaras/app/ui/Avisos.kt`
- Create: `android/app/src/main/java/com/cuentasclaras/app/ui/Fabrica.kt`
- Create: `android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/respaldo/RespaldoViewModel.kt`
- Create: `android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/respaldo/RespaldoSeccion.kt`
- Modify: `android/app/src/main/AndroidManifest.xml` (`android:name=".CuentasApp"`)

Fuentes: `src/ui/App.tsx` (arranque, pantalla de error), `src/ui/componentes/RespaldoSection.tsx` (flujo de restaurar con confirmación y respaldo de seguridad previo), `src/ui/Toast.tsx`.

**Interfaces:**
- Consumes: T5 (`CuentasDb`, `seedIfEmpty`), T7 (respaldo completo), T8 (`crearHttp`, `proveedoresPorDefecto`, `refreshRatesOncePerDay`), T2 (`todayIso`).
- Produces:
  - `class AppContainer(context: Context)` con `val db: CuentasDb`, `val http: OkHttpClient` (T11 añade `repo`; T19 añade `capturas` y `estadoListener`); `class CuentasApp : Application` con `lateinit var container: AppContainer`.
  - `object Avisos { val mensajes: SharedFlow<String>; fun avisar(texto: String) }` (D15).
  - `@Composable inline fun <reified VM : ViewModel> vmCon(crossinline crear: (AppContainer, SavedStateHandle) -> VM): VM`.
  - `RespaldoViewModel(db, dirSeguridad: File)` y `@Composable fun RespaldoSeccion()` (la reutiliza Ajustes en la Tarea 16).
  - Extra de depuración `respaldoDebug` (D12).

**Decisión adicional D16 (ledger):** antes de restaurar, el respaldo de seguridad de lo actual se escribe **automáticamente** en `filesDir/respaldos/cuentas-claras-respaldo-previo-<fecha>.json` (se conservan los 5 más recientes), sin abrir un segundo selector. La web obligaba a guardarlo por diálogo; en Android eso son dos selectores seguidos y Daniel podría cancelar el segundo sin querer.

- [ ] **Paso 1: `CuentasApp.kt`.**

```kotlin
package com.cuentasclaras.app

import android.app.Application
import android.content.Context
import com.cuentasclaras.app.data.db.CuentasDb
import com.cuentasclaras.app.rates.crearHttp
import okhttp3.OkHttpClient

/** Contenedor manual de dependencias (sin Hilt, spec §3.4). Una instancia por proceso. */
class AppContainer(context: Context) {
    val db: CuentasDb = CuentasDb.crear(context)
    val http: OkHttpClient = crearHttp()
}

class CuentasApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
```

- [ ] **Paso 2: `ui/Avisos.kt` y `ui/Fabrica.kt`.**

```kotlin
package com.cuentasclaras.app.ui

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** Equivalente de `avisar()` de Toast.tsx: un único canal que pinta el SnackbarHost raíz (D15). */
object Avisos {
    private val _mensajes = MutableSharedFlow<String>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val mensajes: SharedFlow<String> = _mensajes

    fun avisar(texto: String) {
        _mensajes.tryEmit(texto)
    }
}
```

```kotlin
package com.cuentasclaras.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cuentasclaras.app.AppContainer
import com.cuentasclaras.app.CuentasApp

/** ViewModel con dependencias del AppContainer y los argumentos de la ruta (SavedStateHandle),
 *  con alcance del destino de navegación actual: cada visita a `nuevo?…` crea su propio VM. */
@Composable
inline fun <reified VM : ViewModel> vmCon(crossinline crear: (AppContainer, SavedStateHandle) -> VM): VM {
    val app = LocalContext.current.applicationContext as CuentasApp
    return viewModel(factory = viewModelFactory { initializer { crear(app.container, createSavedStateHandle()) } })
}
```

- [ ] **Paso 3: `RespaldoViewModel.kt`.** Patrón obligatorio de todos los ViewModels del plan: `data class Estado` inmutable + `MutableStateFlow` privado + guard `guardando` (restricción 9).

```kotlin
package com.cuentasclaras.app.ui.pantallas.respaldo

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuentasclaras.app.backup.BackupFile
import com.cuentasclaras.app.backup.Conteos
import com.cuentasclaras.app.backup.ValidacionRespaldo
import com.cuentasclaras.app.backup.backupFilename
import com.cuentasclaras.app.backup.contarTablas
import com.cuentasclaras.app.backup.exportBackup
import com.cuentasclaras.app.backup.importBackup
import com.cuentasclaras.app.backup.leerRespaldo
import com.cuentasclaras.app.backup.respaldoAJson
import com.cuentasclaras.app.data.db.CuentasDb
import com.cuentasclaras.app.domain.todayIso
import com.cuentasclaras.app.ui.Avisos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class RespaldoViewModel(private val db: CuentasDb, private val dirSeguridad: File) : ViewModel() {
    data class Pendiente(val backup: BackupFile, val resumen: String)
    data class Estado(
        val conteos: Conteos? = null,
        val pendiente: Pendiente? = null,
        val guardando: Boolean = false,
    )

    private val _estado = MutableStateFlow(Estado())
    val estado: StateFlow<Estado> = _estado.asStateFlow()

    init { refrescarConteos() }

    private fun refrescarConteos() {
        viewModelScope.launch { _estado.update { it.copy(conteos = contarTablas(db)) } }
    }

    fun nombreSugerido(): String = backupFilename(todayIso())

    fun exportarA(resolver: ContentResolver, uri: Uri) {
        if (_estado.value.guardando) return
        _estado.update { it.copy(guardando = true) }
        viewModelScope.launch {
            try {
                val json = respaldoAJson(exportBackup(db))
                withContext(Dispatchers.IO) {
                    resolver.openOutputStream(uri, "wt")!!.bufferedWriter().use { it.write(json) }
                }
                Avisos.avisar("Respaldo guardado ✓")
            } catch (e: Exception) {
                Avisos.avisar("No se pudo exportar: ${e.message}")
            } finally {
                _estado.update { it.copy(guardando = false) }
            }
        }
    }

    fun elegirArchivo(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            val texto = try {
                withContext(Dispatchers.IO) { resolver.openInputStream(uri)!!.bufferedReader().use { it.readText() } }
            } catch (e: Exception) {
                Avisos.avisar("No se pudo leer el archivo: ${e.message}")
                return@launch
            }
            when (val v = leerRespaldo(texto)) {
                is ValidacionRespaldo.Error -> Avisos.avisar(v.mensaje)
                is ValidacionRespaldo.Ok -> {
                    val b = v.backup
                    val resumen = "${b.transactions.size} movimientos, ${b.accounts.size} cuentas, " +
                        "${b.categories.size} categorías, ${b.debts.size} deudas, ${b.calEvents.size} eventos"
                    _estado.update { it.copy(pendiente = Pendiente(b, resumen)) }
                }
            }
        }
    }

    fun cancelar() = _estado.update { it.copy(pendiente = null) }

    /** Reemplaza TODO. Antes guarda un respaldo de seguridad de lo actual (D16). */
    fun restaurar() {
        val pendiente = _estado.value.pendiente ?: return
        if (_estado.value.guardando) return
        _estado.update { it.copy(guardando = true) }
        viewModelScope.launch {
            try {
                val previo = respaldoAJson(exportBackup(db))
                withContext(Dispatchers.IO) {
                    dirSeguridad.mkdirs()
                    File(dirSeguridad, backupFilename("previo-${todayIso()}")).writeText(previo)
                    dirSeguridad.listFiles()?.sortedByDescending { it.lastModified() }?.drop(5)?.forEach { it.delete() }
                }
                importBackup(db, pendiente.backup)
                _estado.update { it.copy(pendiente = null) }
                Avisos.avisar("Datos restaurados ✓")
                refrescarConteos()
            } catch (e: Exception) {
                Avisos.avisar("No se restauró nada: ${e.message}")
            } finally {
                _estado.update { it.copy(guardando = false) }
            }
        }
    }
}
```

- [ ] **Paso 4: `RespaldoSeccion.kt`.** Contenido funcional (layout libre, Material 3 por defecto; la Tarea 16 lo re-estiliza con la identidad nueva):
  1. Texto de ayuda: «Exporta tu respaldo y guárdalo en Google Drive desde el selector de archivos.»
  2. Botón «Exportar respaldo» → `rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json"))` lanzado con `vm.nombreSugerido()`; el resultado llama `vm.exportarA(context.contentResolver, uri)`.
  3. Botón «Restaurar desde un respaldo» → `ActivityResultContracts.OpenDocument()` con `arrayOf("application/json", "text/plain", "application/octet-stream")` → `vm.elegirArchivo(...)`.
  4. Si `pendiente != null`: aviso visible «Vas a **reemplazar TODOS los datos actuales** por: {resumen}. Antes se guardará un respaldo de seguridad de lo actual.» con «Cancelar» y «Sí, reemplazar todo» (`enabled = !guardando`).
  5. Tabla «Datos en la app» con los 9 conteos (`accounts` → «Cuentas», `transactions` → «Movimientos», `categories` → «Categorías», `rules` → «Reglas», `rates` → «Tasas», `settings` → «Ajustes», `debts` → «Deudas», `txGroups` → «Grupos», `calEvents` → «Eventos»).
  - Obtén el VM con `val dir = File(LocalContext.current.filesDir, "respaldos")` y `val vm = vmCon { c, _ -> RespaldoViewModel(c.db, dir) }`; el estado con `val estado by vm.estado.collectAsStateWithLifecycle()`.

- [ ] **Paso 5: `MainActivity.kt` (reescritura).** Arranque como `App.tsx`: sembrar → si falla, pantalla de error; si va bien, refrescar tasas en segundo plano en silencio. Raíz con `Scaffold` + `SnackbarHost` alimentado por `Avisos`.

```kotlin
package com.cuentasclaras.app

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cuentasclaras.app.backup.ValidacionRespaldo
import com.cuentasclaras.app.backup.importBackup
import com.cuentasclaras.app.backup.leerRespaldo
import com.cuentasclaras.app.data.seedIfEmpty
import com.cuentasclaras.app.domain.todayIso
import com.cuentasclaras.app.rates.proveedoresPorDefecto
import com.cuentasclaras.app.rates.refreshRatesOncePerDay
import com.cuentasclaras.app.ui.Avisos
import com.cuentasclaras.app.ui.pantallas.respaldo.RespaldoSeccion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class Arranque { CARGANDO, LISTO, ERROR }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as CuentasApp).container
        setContent {
            MaterialTheme {
                var arranque by remember { mutableStateOf(Arranque.CARGANDO) }
                LaunchedEffect(Unit) {
                    arranque = try {
                        seedIfEmpty(container.db)
                        cargarRespaldoDebug(container)
                        Arranque.LISTO
                    } catch (e: Exception) {
                        Log.e("CuentasClaras", "No se pudo iniciar la base de datos", e)
                        Arranque.ERROR
                    }
                    if (arranque == Arranque.LISTO) {
                        launch {
                            // Sin red o sin proveedor: silencioso; la UI muestra la tasa vieja.
                            runCatching { refreshRatesOncePerDay(container.db, todayIso(), proveedoresPorDefecto(container.http)) }
                        }
                    }
                }
                when (arranque) {
                    Arranque.CARGANDO -> Unit
                    Arranque.ERROR -> PantallaErrorInicio()
                    Arranque.LISTO -> RaizApp()
                }
            }
        }
    }

    /** D12: solo en builds depurables. `adb … am start … --es respaldoDebug archivo.json`
     *  carga filesDir/archivo.json (puesto con `run-as`) y lo borra. */
    private suspend fun cargarRespaldoDebug(container: AppContainer) {
        val depurable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val nombre = intent.getStringExtra("respaldoDebug") ?: return
        if (!depurable) return
        val archivo = File(filesDir, nombre)
        if (!archivo.exists()) return
        val texto = withContext(Dispatchers.IO) { archivo.readText() }
        when (val v = leerRespaldo(texto)) {
            is ValidacionRespaldo.Ok -> importBackup(container.db, v.backup)
            is ValidacionRespaldo.Error -> Log.w("CuentasClaras", "respaldoDebug inválido: ${v.mensaje}")
        }
        archivo.delete()
    }
}

@Composable
private fun PantallaErrorInicio() {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text("No se pudo iniciar la base de datos local. Cierra y vuelve a abrir la app.")
    }
}

@Composable
private fun RaizApp() {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { Avisos.mensajes.collect { snackbar.showSnackbar(it) } }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { relleno ->
        Box(Modifier.padding(relleno).padding(16.dp)) { RespaldoSeccion() }
    }
}
```

Añade `android:name=".CuentasApp"` al `<application>` del manifiesto.

- [ ] **Paso 6: Dispositivo (bloqueante; pregunta a Daniel SOLO ahora).** Con `mattpocock-skills:wizard`, genera un asistente de terminal que guíe a Daniel por: (1) activar Opciones de desarrollador en el Xiaomi (tocar 7 veces «Versión de MIUI/HyperOS»), (2) activar «Depuración USB», «Instalar vía USB» y «Depuración USB (ajustes de seguridad)» (pide cuenta Mi y SIM), (3) conectar por USB y aceptar la huella RSA, (4) correr `adb devices` y ver `device`. Pregúntale con `AskUserQuestion`: «¿Conectas el Xiaomi por USB ahora (recomendado) o prefieres que cree un emulador (~1 GB de descarga)?». Si elige emulador: `"$LOCALAPPDATA/Android/Sdk/emulator/emulator" -accel-check` debe decir que WHPX/Hyper-V está disponible; luego `sdkmanager "system-images;android-35;google_apis;x86_64"`, `avdmanager create avd -n cuentas35 -k "system-images;android-35;google_apis;x86_64" -d pixel_7`, `emulator -avd cuentas35 -no-snapshot &` (en segundo plano) y `adb wait-for-device`. Si el acelerador no está, NO sigas con el emulador: vuelve a pedir el teléfono.

- [ ] **Paso 7: Verificación en el dispositivo.** Con `.superpowers/sdd/adb-ui.sh` (receta global): `instalar` → `abrir` → `captura fase-1 inicio-vacio` (conteos de seed: 5 cuentas, 18 categorías, 1 ajuste, 0 en el resto) → `sembrar android/app/src/test/resources/respaldo-demo-v3.json` → `captura fase-1 demo-sembrado` (conteos 5/12/18/1/3/1/2/1/2) → `tocar "Exportar respaldo"` y guardar en Descargas desde el selector → `captura fase-1 exportado`. Abre cada PNG con Read. Además `reiniciar` y confirma que los conteos persisten.

- [ ] **Paso 8: Suite, build y commit.** `./gradlew :app:testDebugUnitTest :app:assembleDebug` en verde.

```bash
git add android/app/src/main/java/com/cuentasclaras/app/CuentasApp.kt android/app/src/main/java/com/cuentasclaras/app/MainActivity.kt android/app/src/main/java/com/cuentasclaras/app/ui/Avisos.kt android/app/src/main/java/com/cuentasclaras/app/ui/Fabrica.kt android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/respaldo/ android/app/src/main/AndroidManifest.xml
git commit -m "feat(app): arranque con seed y tasas, avisos y pantalla de respaldo con restauración segura" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

- [ ] **Paso 9: Cierre de la fase 1 (bloqueante).**
  1. `mattpocock-skills:code-review` desde el commit del spec (`git merge-base HEAD main`) hasta HEAD. Standards: restricciones globales 2–4, 8, 10–16. Spec: v1/v2/v3 para dinero, saldos, grupos, calendario y respaldo. Aplica los hallazgos con `superpowers:receiving-code-review` (verificar antes de aceptar).
  2. Pide a Daniel: «Instala el APK (ya está en tu teléfono), toca *Restaurar desde un respaldo*, elige tu respaldo v3 y dime si los conteos cuadran con lo que tenías en la PWA». Él carga su archivo: **nunca** lo copies al PC ni al repo.
  3. Si los conteos no cuadran o hay error, `superpowers:systematic-debugging` con el mensaje exacto (sin pedir el archivo; si hace falta, pídele que te diga qué tabla falla).
  4. Fase 1 cerrada solo con el «cuadra» de Daniel.

---

## Fase 2 — Pantallas principales con identidad visual nueva

Entregable (spec §7.2): APK de uso diario sin captura ni imports. **Cambio de Daniel (2026-10-15): la UI se diseña desde cero con referencias nuevas; las funciones son las mismas.** El TSX de `src/ui/` es la especificación del *comportamiento* (qué datos, qué acciones, qué validaciones, qué textos de error), NO del aspecto: nada de copiar layout, clases ni paleta de `estilos.css`. Cada tarea de UI describe el contrato funcional de la pantalla; la composición visual sale de `android/DESIGN.md`.

Reglas de UI para toda la fase (y las siguientes):
- Lee `android/DESIGN.md` antes de escribir cualquier composable; solo se usan sus tokens (`CuentasTema.colores`, `.tipografia`, `.formas`, `.espacios`) y los componentes base de `ui/componentes/`. Prohibido hex sueltos o `dp` mágicos en pantallas (los colores de categoría son dato del usuario: se pintan con `Color(android.graphics.Color.parseColor(c.color))`).
- Todo monto con `formatMoney` y estilo de cifra tabular del tema.
- Toda lista larga con `LazyColumn` y `key = { it.id }`.
- Estados vacíos con el componente `Vacio` (ícono + frase), nunca pantallas en blanco.
- Botones de acción con `enabled = !estado.guardando`.
- Diálogos y hojas: `ModalBottomSheet` (hojas de edición) o `AlertDialog` (confirmaciones destructivas).
- Accesibilidad: cada ícono suelto con `contentDescription` en español; objetivos táctiles ≥ 48 dp. Los textos visibles y `contentDescription` son los que `tocar` usa en la verificación.

### Tarea 10: Dirección visual nueva (elige Daniel), `DESIGN.md`, tema y componentes base

**Skills:** `ui-ux-pro-max:ui-ux-pro-max` (producto «mobile app / personal finance», stack Jetpack Compose: estilos, paletas, pares tipográficos y guías UX como materia prima) · `imagegen-frontend-mobile` (SOLO si la sesión tiene herramienta de generación de imágenes: un concepto de la pantalla Inicio por dirección; sin herramienta, se omite sin sustituto) · `stitch-skill` (`stitch-design-taste`: escribe `android/DESIGN.md` de la dirección elegida) · `soft-skill` (`high-end-visual-design`: SOLO §2 anti-patrones y §4 tarjetas/botones/ritmo, traducidos a Compose; ignora su «variance mandate», navbars, GSAP y Tailwind) · `ui-ux-pro-max:design-system` (alternativa si `DESIGN.md` no cuaja) · `output-skill`.

**Files:**
- Create: `android/DESIGN.md`
- Create: `android/app/src/main/res/font/<fuente_elegida>.ttf` (variable, licencia OFL, descargada de `https://github.com/google/fonts/tree/main/ofl/<familia>`; nombre de recurso en minúsculas con `_`)
- Create: `android/app/src/main/java/com/cuentasclaras/app/ui/theme/Color.kt`, `Type.kt`, `Shape.kt`, `Theme.kt`
- Create: `android/app/src/main/java/com/cuentasclaras/app/ui/componentes/Tarjeta.kt`, `Boton.kt`, `Segmentado.kt`, `Chip.kt`, `Banner.kt`, `Vacio.kt`, `Campo.kt`, `Selector.kt`, `HojaModal.kt`, `CampoFecha.kt`, `Teclado.kt`, `CategoriaGrid.kt`
- Create: `android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` + `drawable/ic_launcher_foreground.xml` (ícono adaptable coherente con la dirección elegida) y `android:icon`/`android:roundIcon` en el manifiesto
- Create (temporal, NO se commitea): `android/app/src/debug/java/com/cuentasclaras/app/MuestrarioDirecciones.kt`
- Modify: `android/app/src/main/java/com/cuentasclaras/app/MainActivity.kt` (`MaterialTheme` → `CuentasTema`)

**Interfaces:**
- Consumes: nada de dominio salvo `formatMoney` en los ejemplos del muestrario.
- Produces (todas las pantallas de las fases 2–6 las usan; nombres EXACTOS):
  - `@Composable fun CuentasTema(content: @Composable () -> Unit)` y `object CuentasTema { val colores: Colores; val tipografia: Tipografia; val formas: Formas; val espacios: Espacios }` (leídos con `@Composable @ReadOnlyComposable get()` desde `CompositionLocal`s).
  - `data class Colores(fondo, superficie, superficieElevada, borde, primario, sobrePrimario, ingreso, gasto, aviso, error, texto, textoSecundario: Color)`; `data class Tipografia(titulo, subtitulo, cuerpo, etiqueta, cifraGrande, cifra: TextStyle)` (las dos `cifra*` con `fontFeatureSettings = "tnum"`); `data class Formas(tarjeta, control, pildora: Shape)`; `data class Espacios(xs, s, m, l, xl: Dp)`.
  - Componentes: `Tarjeta(modifier, titulo: String? = null, accion: (@Composable () -> Unit)? = null, contenido)`, `BotonPrincipal(texto, onClick, enabled = true, modifier)`, `BotonSecundario(...)`, `BotonPeligro(...)`, `Segmentado(opciones: List<String>, seleccion: Int, onSeleccion: (Int) -> Unit)`, `Chip(texto, onClick: (() -> Unit)? = null, activo = false)`, `Banner(texto, tipo: TipoBanner /* OK, AVISO, ERROR */, accion: (@Composable () -> Unit)? = null)`, `Vacio(icono: String, texto: String)`, `CampoTexto(valor, onCambio, etiqueta, placeholder = "", teclado: KeyboardType = KeyboardType.Text)`, `Selector<T>(etiqueta, opciones: List<T>, seleccion: T?, texto: (T) -> String, onSeleccion: (T) -> Unit)`, `HojaModal(titulo, onCerrar, contenido)`, `CampoFecha(etiqueta, iso: String, onCambio: (String) -> Unit)`, `Teclado(onTecla: (String) -> Unit)`, `CategoriaGrid(categorias: List<Category>, enabled: Boolean, onElegir: (String) -> Unit)`, `Monto(minor: Long, currency: Currency, signo: Signo = Signo.NINGUNO, grande: Boolean = false)` en `Tarjeta.kt`.
  - `fun isoDesdeDatePicker(millis: Long): String` y `fun millisParaDatePicker(iso: String): Long` en `CampoFecha.kt`.

- [ ] **Paso 1: Investigación con `ui-ux-pro-max`.** Ejecuta su búsqueda para «personal finance mobile app, bimonetario (Bs y USD), uso diario rápido, Android» y reúne: 3 estilos candidatos, sus paletas, pares tipográficos (solo familias OFL de Google Fonts con versión variable `.ttf`) y las guías UX de apps de finanzas (jerarquía de la cifra principal, contraste de ingreso/gasto apto para daltonismo —el signo y un ícono acompañan siempre al color—, objetivos táctiles ≥ 48 dp, legibilidad de cifras). Restricciones para las 3 direcciones: tema ÚNICO (D14; oscuro o claro lo decide la dirección), contraste WCAG AA del texto, cifras tabulares, Material 3 como base de componentes, nada que dependa de colores dinámicos. **No partas de `src/ui/estilos.css`**: es la identidad de la PWA que Daniel quiere dejar atrás.

- [ ] **Paso 2: Tres direcciones visuales.** Escribe en `.superpowers/sdd/direcciones-visuales.md` (gitignored) A, B y C, cada una con: nombre, concepto en una frase, tema (oscuro/claro), tabla de tokens (`fondo`, `superficie`, `superficieElevada`, `borde`, `primario`, `sobrePrimario`, `ingreso`, `gasto`, `aviso`, `error`, `texto`, `textoSecundario` en hex), familia tipográfica + escala (sp), radios (tarjeta, control, píldora), densidad y una descripción de cómo se ve Inicio (cifra de patrimonio, tarjetas, barra inferior con el botón «+»). Si hay generador de imágenes, aplica `imagegen-frontend-mobile` para un concepto de Inicio por dirección (marco de teléfono Android, mismos datos de ejemplo: patrimonio `$1,234.56`, «Este mes» con gastos `−$310.20`).

- [ ] **Paso 3: Muestrario en el teléfono.** Crea `android/app/src/debug/java/com/cuentasclaras/app/MuestrarioDirecciones.kt`: un composable con `HorizontalPager` de 3 páginas; cada página pinta, con los tokens y la fuente de esa dirección, una maqueta estática de Inicio (tarjeta de patrimonio con `formatMoney(123456, Currency.USD)`, tarjeta «Este mes» con ingresos/gastos, dos filas de movimientos y una barra inferior con el «+»). Muéstralo temporalmente en `RaizApp` cuando la app se abre con el extra de depuración `--ez muestrario true`. `instalar` → `abrir --ez muestrario true` → `captura fase-2 direccion-a` (desliza con `"$ADB" shell input swipe 900 1200 100 1200` para B y C). Inspecciona las capturas con Read.

- [ ] **Paso 4: Daniel elige (bloqueante).** `AskUserQuestion` con una pregunta «¿Qué dirección visual quieres para la app?», una opción por dirección (`label` = nombre, `description` = concepto + tema, `preview` = tabla de tokens y escala tipográfica en texto) y una nota: «Puedes verlas en tu teléfono ahora mismo: abre Cuentas Claras y desliza». Si Daniel pide mezclar («la paleta de A con la fuente de C»), ajusta y vuelve a enseñar el muestrario antes de seguir.

- [ ] **Paso 5: `android/DESIGN.md` con `stitch-skill`.** Para la dirección elegida, adaptado a Compose (dp/sp, `MaterialTheme`, `Modifier`), con estas secciones: 1) Concepto; 2) Tokens (tabla con los nombres EXACTOS de `Colores`, `Tipografia`, `Formas`, `Espacios` y sus valores); 3) Tipografía (familia, pesos, escala, `tnum` en cifras); 4) Componentes (Tarjeta, botones, Segmentado, Chip, Banner, Vacío, campos, hoja modal, teclado numérico, grid de categorías, barra inferior con «+» central) con medidas; 5) Movimiento (`spring()` y `tween` con easing propio; nada `LinearEasing`; duraciones 150–300 ms); 6) Reglas de `soft-skill` §2 (sin bordes grises de 1 px genéricos, sin sombras duras) y §4 (tarjetas con doble bisel, botones píldora, ritmo de espacios) traducidas a Compose; 7) Gráficas (paleta de series, grosores; la dona usa los colores de categoría del usuario); 8) Prohibiciones (hex sueltos en pantallas, `kotlin.math.round`, colores dinámicos, layouts distintos para pantallas equivalentes: las pantallas deben ser consistentes entre sí). `DESIGN.md` es la ÚNICA fuente visual que leen los subagentes de UI.

- [ ] **Paso 6: Fuente.** Descarga el `.ttf` variable de la familia elegida desde `github.com/google/fonts` (`ofl/<familia>/<Familia>[wght].ttf`) a `res/font/<familia>_variable.ttf` y verifica su licencia OFL en el `OFL.txt` de esa carpeta (anótalo en `DESIGN.md`). Si la familia no tiene versión variable, descarga los estáticos de los pesos usados.

- [ ] **Paso 7: Tema.** `Color.kt` (`data class Colores` + `val ColoresCuentas = Colores(...)` con los hex de `DESIGN.md`), `Type.kt` (`FontFamily` con `Font(R.font.<familia>_variable, FontWeight.W400, variationSettings = FontVariation.Settings(FontVariation.weight(400)))` y lo mismo para cada peso usado; `data class Tipografia` + instancia), `Shape.kt` (`Formas`, `Espacios`), `Theme.kt`:

```kotlin
package com.cuentasclaras.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalColores = staticCompositionLocalOf { ColoresCuentas }
private val LocalTipografia = staticCompositionLocalOf { TipografiaCuentas }
private val LocalFormas = staticCompositionLocalOf { FormasCuentas }
private val LocalEspacios = staticCompositionLocalOf { EspaciosCuentas }

/** Tema ÚNICO de la app (D14). Material 3 recibe un esquema derivado de los tokens para que sus
 *  componentes (TextField, DatePicker, ModalBottomSheet, Snackbar) hereden la identidad. */
@Composable
fun CuentasTema(content: @Composable () -> Unit) {
    val c = ColoresCuentas
    val base = if (TEMA_OSCURO) darkColorScheme() else lightColorScheme()
    val esquema = base.copy(
        primary = c.primario, onPrimary = c.sobrePrimario,
        background = c.fondo, onBackground = c.texto,
        surface = c.superficie, onSurface = c.texto,
        surfaceVariant = c.superficieElevada, onSurfaceVariant = c.textoSecundario,
        surfaceContainer = c.superficie, surfaceContainerHigh = c.superficieElevada,
        outline = c.borde, error = c.error,
    )
    CompositionLocalProvider(
        LocalColores provides ColoresCuentas,
        LocalTipografia provides TipografiaCuentas,
        LocalFormas provides FormasCuentas,
        LocalEspacios provides EspaciosCuentas,
    ) {
        MaterialTheme(colorScheme = esquema, typography = TipografiaCuentas.comoMaterial(), content = content)
    }
}

object CuentasTema {
    val colores: Colores @Composable @ReadOnlyComposable get() = LocalColores.current
    val tipografia: Tipografia @Composable @ReadOnlyComposable get() = LocalTipografia.current
    val formas: Formas @Composable @ReadOnlyComposable get() = LocalFormas.current
    val espacios: Espacios @Composable @ReadOnlyComposable get() = LocalEspacios.current
}
```

`TEMA_OSCURO` es una `const val` en `Color.kt` según la dirección elegida; `Tipografia.comoMaterial()` (en `Type.kt`) mapea `titulo → headlineSmall`, `subtitulo → titleMedium`, `cuerpo → bodyLarge`/`bodyMedium`, `etiqueta → labelLarge`. Ajusta también `themes.xml` (`android:windowBackground` y barras del sistema con el color de `fondo`) para que no haya destello blanco al abrir.

- [ ] **Paso 8: Componentes base** con las firmas EXACTAS de *Produces*, solo tokens del tema, cada uno con un `@Preview`. Comportamiento obligatorio:
  - `Teclado`: 4 × 3 teclas `1 2 3 / 4 5 6 / 7 8 9 / , 0 ⌫`; `contentDescription` «coma» y «borrar» en las dos especiales; cada tecla ≥ 56 dp de alto; `onTecla` recibe `","`, `"⌫"` o el dígito. Compacto: junto con el selector de cuenta y la cifra, el grid de categorías debe empezar visible sin desplazar en un teléfono de 6,5" (registro en ≤ 3 toques).
  - `CategoriaGrid`: grid de 4 columnas (emoji grande + nombre en 2 líneas máx.), `enabled` deshabilita todo mientras se guarda.
  - `Monto`: `formatMoney` + estilo `cifra`/`cifraGrande`; `Signo.POSITIVO` antepone `+` y usa `ingreso`, `Signo.NEGATIVO` antepone `−` y usa `gasto`.
  - `CampoFecha`: abre `DatePickerDialog` de Material 3. **Trampa conocida:** `DatePickerState.selectedDateMillis` es medianoche **UTC**; convertir con la zona local corre el día en Venezuela (UTC−4). Usa exactamente:

```kotlin
fun isoDesdeDatePicker(millis: Long): String =
    java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()

fun millisParaDatePicker(iso: String): Long =
    java.time.LocalDate.parse(iso).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
```

  - `Selector`: `ExposedDropdownMenuBox` de Material 3 con `TextField` de solo lectura.
  - `HojaModal`: `ModalBottomSheet` con título, contenido desplazable y `imePadding()`.

- [ ] **Paso 9: Ícono y aplicación del tema.** Ícono adaptable (`ic_launcher.xml` con `<adaptive-icon>` + primer plano vectorial sencillo en el color primario sobre `fondo`); `android:icon="@mipmap/ic_launcher"` y `android:roundIcon="@mipmap/ic_launcher"` en el manifiesto. En `MainActivity`, `MaterialTheme { … }` → `CuentasTema { … }`.

- [ ] **Paso 10: Verificación.** `instalar` → `captura fase-2 tema-respaldo` (la pantalla de respaldo ya con la identidad nueva) → Read. Borra `MuestrarioDirecciones.kt` y su enganche (no se commitea). `./gradlew :app:testDebugUnitTest :app:assembleDebug` en verde.

- [ ] **Paso 11: Commit.**

```bash
git add android/DESIGN.md android/app/src/main/res/font/ android/app/src/main/res/mipmap-anydpi-v26/ android/app/src/main/res/drawable/ android/app/src/main/res/values/themes.xml android/app/src/main/AndroidManifest.xml android/app/src/main/java/com/cuentasclaras/app/ui/theme/ android/app/src/main/java/com/cuentasclaras/app/ui/componentes/ android/app/src/main/java/com/cuentasclaras/app/MainActivity.kt
git commit -m "feat(ui): identidad visual nueva elegida por Daniel, DESIGN.md, tema y componentes base" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Tarea 11: Repositorio, navegación y barra inferior

**Skills:** `ui-ux-pro-max:ui-ux-pro-max` (navegación inferior con acción central) · `output-skill`.

**Files:**
- Create: `android/app/src/main/java/com/cuentasclaras/app/data/CuentasRepo.kt`
- Modify: `android/app/src/main/java/com/cuentasclaras/app/CuentasApp.kt` (`val repo = CuentasRepo(db)` en `AppContainer`)
- Create: `android/app/src/main/java/com/cuentasclaras/app/ui/nav/Rutas.kt`
- Create: `android/app/src/main/java/com/cuentasclaras/app/ui/nav/CuentasNavHost.kt`
- Create: `android/app/src/main/java/com/cuentasclaras/app/ui/nav/BarraInferior.kt`
- Create: `android/app/src/main/java/com/cuentasclaras/app/ui/componentes/Proximamente.kt`
- Modify: `android/app/src/main/java/com/cuentasclaras/app/MainActivity.kt` (`RaizApp` monta el NavHost)

Fuentes: `src/ui/App.tsx`, `Nav.tsx`, `useHashRoute.ts`; spec §6.2.

**Interfaces:**
- Consumes: T5 (DAOs, mapeos, `saveSettings`, `SETTINGS_SEED`), T8 (`storeFetchedRates`, `fetchRatesWithFallback`, `proveedoresPorDefecto`), T10 (tema y componentes).
- Produces:
  - `class CuentasRepo(val db: CuentasDb)` con flows `cuentas`, `txs`, `categorias`, `tasas`, `reglas`, `settings: Flow<Settings>`, `deudas`, `grupos`, `eventos`, `instantanea: Flow<Instantanea>`; `data class Instantanea(cuentas, txs, categorias, tasas, settings, deudas, grupos, eventos, reglas)`; comandos `agregarTx`, `actualizarTx`, `borrarTx`, `guardarCuenta`, `guardarCategoria`, `guardarTasa`, `borrarRegla`, `cambiarSettings`, `actualizarBcvAhora(http): Int`.
  - `object Rutas` con constantes de patrón y constructores `Rutas.nuevo(...)`, `Rutas.movimientos(...)`, `Rutas.calendario(dia)`.
  - `@Composable fun CuentasNavHost(nav: NavHostController, modifier: Modifier)`; `@Composable fun BarraInferior(nav: NavHostController)`; `@Composable fun Proximamente(texto: String)`.

- [ ] **Paso 1: `CuentasRepo.kt`.**

```kotlin
package com.cuentasclaras.app.data

import com.cuentasclaras.app.data.db.CuentasDb
import com.cuentasclaras.app.data.db.toDomain
import com.cuentasclaras.app.data.db.toEntity
import com.cuentasclaras.app.domain.Account
import com.cuentasclaras.app.domain.CalEvent
import com.cuentasclaras.app.domain.Category
import com.cuentasclaras.app.domain.Debt
import com.cuentasclaras.app.domain.Rate
import com.cuentasclaras.app.domain.Rule
import com.cuentasclaras.app.domain.Settings
import com.cuentasclaras.app.domain.Transaction
import com.cuentasclaras.app.domain.TxGroup
import com.cuentasclaras.app.domain.todayIso
import com.cuentasclaras.app.rates.fetchRatesWithFallback
import com.cuentasclaras.app.rates.proveedoresPorDefecto
import com.cuentasclaras.app.rates.storeFetchedRates
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient

/** Todas las tablas a la vez: equivalente a los useLiveQuery de una página de la web. */
data class Instantanea(
    val cuentas: List<Account>,
    val txs: List<Transaction>,
    val categorias: List<Category>,
    val tasas: List<Rate>,
    val settings: Settings,
    val deudas: List<Debt>,
    val grupos: List<TxGroup>,
    val eventos: List<CalEvent>,
    val reglas: List<Rule>,
)

/** Lecturas reactivas (Flow de Room) y escrituras simples. Las operaciones compuestas viven en
 *  data/ops y se llaman con `repo.db`. */
class CuentasRepo(val db: CuentasDb) {
    val cuentas: Flow<List<Account>> = db.accounts().observar().map { l -> l.map { it.toDomain() } }
    val txs: Flow<List<Transaction>> = db.transactions().observar().map { l -> l.map { it.toDomain() } }
    val categorias: Flow<List<Category>> = db.categories().observar().map { l -> l.map { it.toDomain() } }
    val tasas: Flow<List<Rate>> = db.rates().observar().map { l -> l.map { it.toDomain() } }
    val reglas: Flow<List<Rule>> = db.rules().observar().map { l -> l.map { it.toDomain() } }
    val settings: Flow<Settings> = db.settings().observar().map { it?.toDomain() ?: SETTINGS_SEED }
    val deudas: Flow<List<Debt>> = db.debts().observar().map { l -> l.map { it.toDomain() } }
    val grupos: Flow<List<TxGroup>> = db.txGroups().observar().map { l -> l.map { it.toDomain() } }
    val eventos: Flow<List<CalEvent>> = db.calEvents().observar().map { l -> l.map { it.toDomain() } }

    private data class Parte1(val cuentas: List<Account>, val txs: List<Transaction>, val categorias: List<Category>,
                              val tasas: List<Rate>, val settings: Settings)
    private data class Parte2(val deudas: List<Debt>, val grupos: List<TxGroup>, val eventos: List<CalEvent>, val reglas: List<Rule>)

    val instantanea: Flow<Instantanea> = combine(
        combine(cuentas, txs, categorias, tasas, settings) { a, b, c, d, e -> Parte1(a, b, c, d, e) },
        combine(deudas, grupos, eventos, reglas) { a, b, c, d -> Parte2(a, b, c, d) },
    ) { p1, p2 -> Instantanea(p1.cuentas, p1.txs, p1.categorias, p1.tasas, p1.settings, p2.deudas, p2.grupos, p2.eventos, p2.reglas) }

    suspend fun agregarTx(tx: Transaction) = db.transactions().insertar(tx.toEntity())
    suspend fun actualizarTx(tx: Transaction) = db.transactions().actualizar(tx.toEntity())
    suspend fun borrarTx(id: String) = db.transactions().borrar(id)
    suspend fun guardarCuenta(a: Account) = db.accounts().guardar(a.toEntity())
    suspend fun guardarCategoria(c: Category) = db.categories().guardar(c.toEntity())
    suspend fun guardarTasa(r: Rate) = db.rates().guardarTodas(listOf(r.toEntity()))
    suspend fun borrarRegla(id: String) = db.rules().borrar(id)
    suspend fun cambiarSettings(cambio: (Settings) -> Settings) = saveSettings(db, cambio)

    /** «Actualizar BCV ahora» de Ajustes → Tasas. Devuelve cuántas tasas guardó (0 = sin red/proveedor). */
    suspend fun actualizarBcvAhora(http: OkHttpClient): Int =
        storeFetchedRates(db, todayIso(), fetchRatesWithFallback(proveedoresPorDefecto(http)))
}
```

- [ ] **Paso 2: `Rutas.kt`.** Parámetros de `nuevo` con los mismos valores que la web (`tipo=gasto|ingreso`, `monto` en céntimos) y los nombres del spec §6.2; calendario con `?dia` (D10).

```kotlin
package com.cuentasclaras.app.ui.nav

import android.net.Uri

object Rutas {
    const val INICIO = "inicio"
    const val NUEVO = "nuevo?tipo={tipo}&monto={monto}&cuenta={cuenta}&nota={nota}&eventId={eventId}&eventDate={eventDate}&capturaId={capturaId}"
    const val MOVIMIENTOS = "movimientos?categoria={categoria}&mes={mes}&cuenta={cuenta}"
    const val IMPORTAR = "importar"
    const val AJUSTES = "ajustes"
    const val DEUDAS = "deudas"
    const val CALENDARIO = "calendario?dia={dia}&nuevo={nuevo}"
    const val PERMISOS = "permisos"

    val ARGS_NUEVO = listOf("tipo", "monto", "cuenta", "nota", "eventId", "eventDate", "capturaId")
    val ARGS_MOVIMIENTOS = listOf("categoria", "mes", "cuenta")
    val ARGS_CALENDARIO = listOf("dia", "nuevo")

    private fun conParametros(base: String, pares: List<Pair<String, String?>>): String {
        val q = pares.filter { it.second != null }.joinToString("&") { "${it.first}=${Uri.encode(it.second)}" }
        return if (q.isEmpty()) base else "$base?$q"
    }

    fun nuevo(
        tipo: String = "gasto", monto: Long? = null, cuenta: String? = null, nota: String? = null,
        eventId: String? = null, eventDate: String? = null, capturaId: String? = null,
    ): String = conParametros(
        "nuevo",
        listOf("tipo" to tipo, "monto" to monto?.toString(), "cuenta" to cuenta, "nota" to nota,
            "eventId" to eventId, "eventDate" to eventDate, "capturaId" to capturaId),
    )

    fun movimientos(categoria: String? = null, mes: String? = null, cuenta: String? = null): String =
        conParametros("movimientos", listOf("categoria" to categoria, "mes" to mes, "cuenta" to cuenta))

    /** `nuevo = true` abre el editor de evento al entrar (como `#/calendario?nuevo=1` de la web). */
    fun calendario(dia: String? = null, nuevo: Boolean = false): String =
        conParametros("calendario", listOf("dia" to dia, "nuevo" to if (nuevo) "1" else null))
}
```

- [ ] **Paso 3: `CuentasNavHost.kt`.** Un `NavHost(startDestination = Rutas.INICIO)`; cada patrón con `arguments = Rutas.ARGS_X.map { navArgument(it) { type = NavType.StringType; nullable = true; defaultValue = null } }`. Destinos: `INICIO → InicioPantalla(nav)` (T13), `NUEVO → NuevoPantalla(nav)` (T12), `MOVIMIENTOS → MovimientosPantalla(nav)` (T15), `AJUSTES → AjustesPantalla(nav)` (T16), `IMPORTAR → Proximamente("El import del TXT del BNC llega en la fase 4")`, `DEUDAS → Proximamente("Deudas llega en la fase 5")`, `CALENDARIO → Proximamente("El calendario llega en la fase 6")`, `PERMISOS → Proximamente("Permisos de captura llega en la fase 3")`. Mientras T12/T13/T15/T16 no existan, usa `Proximamente` en su lugar y que cada una de esas tareas cambie SOLO su línea.

- [ ] **Paso 4: `BarraInferior.kt`.** Cinco posiciones como `Nav.tsx`: Inicio, Movs, «+» central (acción principal, `contentDescription = "Nuevo"`, navega a `Rutas.nuevo("gasto")`), Importar, Ajustes. Pestañas con el patrón estándar `nav.navigate(ruta) { popUpTo(nav.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true }`. Pestaña activa según `currentBackStackEntryAsState()`; se oculta en `nuevo` (la pantalla de registro usa todo el alto).

- [ ] **Paso 5: `RaizApp` en `MainActivity`.** `Scaffold(bottomBar = { BarraInferior(nav) }, snackbarHost = …) { CuentasNavHost(nav, Modifier.padding(it)) }`; la `RespaldoSeccion` sale de la raíz (vuelve en Ajustes, T16).

- [ ] **Paso 6: Verificación.** `instalar` → `sembrar …respaldo-demo-v3.json` → `tocar "Movs"`, `tocar "Importar"`, `tocar "Ajustes"`, `tocar "Nuevo"` y atrás (`"$ADB" shell input keyevent 4`), con una `captura fase-2 nav-<pestaña>` por paso → Read. Comprueba que el botón atrás desde una pestaña vuelve a Inicio y que la pestaña activa se resalta.

- [ ] **Paso 7: Commit.**

```bash
git add android/app/src/main/java/com/cuentasclaras/app/data/CuentasRepo.kt android/app/src/main/java/com/cuentasclaras/app/CuentasApp.kt android/app/src/main/java/com/cuentasclaras/app/ui/nav/ android/app/src/main/java/com/cuentasclaras/app/ui/componentes/Proximamente.kt android/app/src/main/java/com/cuentasclaras/app/MainActivity.kt
git commit -m "feat(ui): repositorio reactivo, rutas, NavHost y barra inferior con acción central" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Tarea 12: Nuevo — gasto/ingreso en ≤ 3 toques, transferir y ajustar

**Skills:** `ui-ux-pro-max:ui-ux-pro-max` (flujo de registro rápido) · `mattpocock-skills:prototype` (solo si el teclado o el flujo ≤ 3 toques generan dudas de estado) · `output-skill`.

**Files:**
- Create: `android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/nuevo/NuevoViewModel.kt`
- Create: `android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/nuevo/NuevoPantalla.kt`
- Create: `android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/nuevo/TransferirViewModel.kt`
- Create: `android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/nuevo/TransferirFormulario.kt`
- Create: `android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/nuevo/AjustarViewModel.kt`
- Create: `android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/nuevo/AjustarFormulario.kt`
- Modify: `android/app/src/main/java/com/cuentasclaras/app/ui/nav/CuentasNavHost.kt` (solo la línea de `NUEVO`)

Fuentes (comportamiento): `src/ui/paginas/NuevoPage.tsx` (sin los chips de evento, que llegan en la Tarea 30), `src/ui/componentes/TransferForm.tsx`, `AjusteForm.tsx`, `Teclado.tsx`, `CategoriaGrid.tsx`.

**Interfaces:**
- Consumes: T2/T3 (`parseVenezuelanAmount`, `formatMoney`, `montoEditable`, `buildManualTx`, `buildTransfer`, `buildAdjustment`, `accountBalanceMinor`, `effectiveRate`, `convertMinor`, `todayIso`), T11 (`CuentasRepo`, `Rutas`), T10 (componentes).
- Produces: `enum class Modo { GASTO, INGRESO, TRANSFERIR, AJUSTAR }`; `fun aplicarTecla(entrada: String, tecla: String): String`; `NuevoViewModel(repo, args: SavedStateHandle)` con `estado: StateFlow<NuevoViewModel.Estado>`, `salir: Flow<Unit>`, `cambiarModo`, `tecla`, `cambiarNota`, `cambiarCuenta`, `guardar(categoryId)`. La Tarea 20 añade `capturaId` y la Tarea 30 los chips; ambas extienden este VM.

- [ ] **Paso 1: `NuevoViewModel.kt`.**

```kotlin
package com.cuentasclaras.app.ui.pantallas.nuevo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuentasclaras.app.data.CuentasRepo
import com.cuentasclaras.app.domain.Account
import com.cuentasclaras.app.domain.Category
import com.cuentasclaras.app.domain.CategoryKind
import com.cuentasclaras.app.domain.TxType
import com.cuentasclaras.app.domain.buildManualTx
import com.cuentasclaras.app.domain.montoEditable
import com.cuentasclaras.app.domain.parseVenezuelanAmount
import com.cuentasclaras.app.domain.todayIso
import com.cuentasclaras.app.ui.Avisos
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Modo { GASTO, INGRESO, TRANSFERIR, AJUSTAR }

/** Teclado propio (Teclado.tsx + onTecla de NuevoPage.tsx): coma única, máximo 2 decimales. */
fun aplicarTecla(entrada: String, tecla: String): String = when (tecla) {
    "⌫" -> entrada.dropLast(1)
    "," -> if (entrada.isEmpty() || entrada.contains(',')) entrada else "$entrada,"
    else -> {
        val partes = entrada.split(',')
        if (partes.size == 2 && partes[1].length >= 2) entrada else entrada + tecla
    }
}

class NuevoViewModel(private val repo: CuentasRepo, args: SavedStateHandle) : ViewModel() {
    data class Formulario(
        val modo: Modo,
        val entrada: String,
        val nota: String,
        val cuentaId: String?,
        val guardando: Boolean = false,
    )

    data class Estado(
        val formulario: Formulario,
        val cuentas: List<Account> = emptyList(),
        val cuenta: Account? = null,
        val categorias: List<Category> = emptyList(),
        /** Céntimos del monto tecleado; null si no es un monto válido. */
        val minor: Long? = null,
    )

    private val formulario = MutableStateFlow(
        Formulario(
            modo = if (args.get<String>("tipo") == "ingreso") Modo.INGRESO else Modo.GASTO,
            // Prefill de captura: solo al crear el VM; lo que el usuario edite después manda.
            entrada = args.get<String>("monto")?.takeIf { it.matches(Regex("^\\d+$")) }?.let { montoEditable(it.toLong()) } ?: "",
            nota = args.get<String>("nota") ?: "",
            cuentaId = args.get<String>("cuenta"),
        ),
    )

    private val _salir = Channel<Unit>(Channel.BUFFERED)
    val salir: Flow<Unit> = _salir.receiveAsFlow()

    val estado: StateFlow<Estado> = combine(formulario, repo.cuentas, repo.categorias) { f, cuentas, categorias ->
        val activas = cuentas.filter { !it.archived }
        val kind = if (f.modo == Modo.INGRESO) CategoryKind.INCOME else CategoryKind.EXPENSE
        Estado(
            formulario = f,
            cuentas = activas,
            cuenta = activas.firstOrNull { it.id == f.cuentaId } ?: activas.firstOrNull(),
            categorias = categorias.filter { it.kind == kind },
            minor = parseVenezuelanAmount(f.entrada.removeSuffix(",")),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Estado(formulario.value))

    init {
        // lastAccountId solo si la ruta no trajo cuenta (igual que NuevoPage.tsx).
        viewModelScope.launch {
            val ultima = repo.settings.first().lastAccountId
            formulario.update { it.copy(cuentaId = it.cuentaId ?: ultima) }
        }
    }

    fun cambiarModo(m: Modo) = formulario.update { it.copy(modo = m) }
    fun tecla(t: String) = formulario.update { it.copy(entrada = aplicarTecla(it.entrada, t)) }
    fun cambiarNota(n: String) = formulario.update { it.copy(nota = n) }
    fun cambiarCuenta(id: String) = formulario.update { it.copy(cuentaId = id) }

    /** Tocar una categoría guarda (≤ 3 toques). */
    fun guardar(categoryId: String) {
        val e = estado.value
        val f = formulario.value
        if (f.guardando) return
        val cuenta = e.cuenta ?: return
        val minor = e.minor
        if (minor == null || minor <= 0) {
            Avisos.avisar("Escribe el monto")
            return
        }
        formulario.update { it.copy(guardando = true) }
        viewModelScope.launch {
            try {
                val tx = buildManualTx(
                    type = if (f.modo == Modo.GASTO) TxType.EXPENSE else TxType.INCOME,
                    accountId = cuenta.id,
                    amountMinor = minor,
                    categoryId = categoryId,
                    dateIso = todayIso(),
                    note = f.nota.trim().takeIf { it.isNotEmpty() },
                )
                repo.agregarTx(tx)
                repo.cambiarSettings { it.copy(lastAccountId = cuenta.id) }
                Avisos.avisar("Guardado ✓")
                _salir.send(Unit)
            } catch (ex: Exception) {
                Avisos.avisar(ex.message ?: "No se pudo guardar")
            } finally {
                formulario.update { it.copy(guardando = false) }
            }
        }
    }
}
```

- [ ] **Paso 2: `TransferirViewModel.kt`** (porta `TransferForm.tsx`: origen por defecto `acc-efectivo`, destino `acc-bnc`; tasa propuesta = `effectiveRate(hoy, settings.rateSource, tasas)`; si el usuario escribe una tasa, manda; «Destino recibirá …» cuando cruza moneda).

```kotlin
package com.cuentasclaras.app.ui.pantallas.nuevo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuentasclaras.app.data.CuentasRepo
import com.cuentasclaras.app.domain.Account
import com.cuentasclaras.app.domain.Rate
import com.cuentasclaras.app.domain.buildTransfer
import com.cuentasclaras.app.domain.convertMinor
import com.cuentasclaras.app.domain.effectiveRate
import com.cuentasclaras.app.domain.parseVenezuelanAmount
import com.cuentasclaras.app.domain.todayIso
import com.cuentasclaras.app.ui.Avisos
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TransferirViewModel(private val repo: CuentasRepo) : ViewModel() {
    data class Formulario(
        val origenId: String = "acc-efectivo",
        val destinoId: String = "acc-bnc",
        val montoTxt: String = "",
        val tasaTxt: String = "",
        val nota: String = "",
        val guardando: Boolean = false,
    )

    data class Estado(
        val f: Formulario = Formulario(),
        val cuentas: List<Account> = emptyList(),
        val origen: Account? = null,
        val destino: Account? = null,
        val monto: Long? = null,
        val cruza: Boolean = false,
        val tasaDelDia: Rate? = null,
        val tasa: Double? = null,
        val destinoMinor: Long? = null,
    ) {
        val puedeGuardar: Boolean
            get() = !f.guardando && monto != null && monto > 0 && (!cruza || (tasa != null && tasa > 0))
    }

    private val form = MutableStateFlow(Formulario())
    private val _salir = Channel<Unit>(Channel.BUFFERED)
    val salir: Flow<Unit> = _salir.receiveAsFlow()

    val estado: StateFlow<Estado> = combine(form, repo.cuentas, repo.tasas, repo.settings) { f, cuentas, tasas, settings ->
        val activas = cuentas.filter { !it.archived }
        val origen = activas.firstOrNull { it.id == f.origenId }
        val destino = activas.firstOrNull { it.id == f.destinoId }
        val monto = parseVenezuelanAmount(f.montoTxt.removeSuffix(","))
        val cruza = origen != null && destino != null && origen.currency != destino.currency
        val tasaDelDia = effectiveRate(todayIso(), settings.rateSource, tasas)
        val tasa = if (f.tasaTxt.isNotBlank()) f.tasaTxt.replace(',', '.').toDoubleOrNull() else tasaDelDia?.vesPerUsd
        val destinoMinor =
            if (cruza && monto != null && monto > 0 && tasa != null && tasa > 0) convertMinor(monto, origen!!.currency, destino!!.currency, tasa)
            else null
        Estado(f, activas, origen, destino, monto, cruza, tasaDelDia, tasa, destinoMinor)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Estado())

    fun cambiar(cambio: (Formulario) -> Formulario) = form.update(cambio)

    fun guardar() {
        val e = estado.value
        if (e.f.guardando) return
        val origen = e.origen ?: return
        val destino = e.destino ?: return
        val monto = e.monto ?: return
        if (monto <= 0) return
        form.update { it.copy(guardando = true) }
        viewModelScope.launch {
            try {
                repo.agregarTx(
                    buildTransfer(origen, destino, monto, todayIso(), if (e.cruza) e.tasa else null,
                        e.f.nota.trim().takeIf { it.isNotEmpty() }),
                )
                Avisos.avisar("Transferencia guardada ✓")
                _salir.send(Unit)
            } catch (ex: Exception) {
                Avisos.avisar(ex.message ?: "No se pudo guardar")
            } finally {
                form.update { it.copy(guardando = false) }
            }
        }
    }
}
```

- [ ] **Paso 3: `AjustarViewModel.kt`** (porta `AjusteForm.tsx`: cuenta por defecto `acc-binance`; muestra el saldo según la app; el delta se rotula «Variación/rendimiento» en cuentas `SNAPSHOT` y «Diferencia» en el resto).

```kotlin
package com.cuentasclaras.app.ui.pantallas.nuevo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuentasclaras.app.data.CuentasRepo
import com.cuentasclaras.app.domain.Account
import com.cuentasclaras.app.domain.AccountKind
import com.cuentasclaras.app.domain.accountBalanceMinor
import com.cuentasclaras.app.domain.buildAdjustment
import com.cuentasclaras.app.domain.parseVenezuelanAmount
import com.cuentasclaras.app.domain.todayIso
import com.cuentasclaras.app.ui.Avisos
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AjustarViewModel(private val repo: CuentasRepo) : ViewModel() {
    data class Formulario(val cuentaId: String = "acc-binance", val realTxt: String = "", val guardando: Boolean = false)

    data class Estado(
        val f: Formulario = Formulario(),
        val cuentas: List<Account> = emptyList(),
        val cuenta: Account? = null,
        val actual: Long = 0,
        val real: Long? = null,
        val delta: Long? = null,
    ) {
        val esSnapshot: Boolean get() = cuenta?.kind == AccountKind.SNAPSHOT
        val puedeGuardar: Boolean get() = !f.guardando && real != null && delta != null && delta != 0L
    }

    private val form = MutableStateFlow(Formulario())
    private val _salir = Channel<Unit>(Channel.BUFFERED)
    val salir: Flow<Unit> = _salir.receiveAsFlow()

    val estado: StateFlow<Estado> = combine(form, repo.cuentas, repo.txs) { f, cuentas, txs ->
        val activas = cuentas.filter { !it.archived }
        val cuenta = activas.firstOrNull { it.id == f.cuentaId }
        val actual = cuenta?.let { accountBalanceMinor(it.id, txs) } ?: 0L
        val real = parseVenezuelanAmount(f.realTxt.removeSuffix(","))
        val delta = if (real != null && real >= 0) real - actual else null
        Estado(f, activas, cuenta, actual, real, delta)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Estado())

    fun cambiar(cambio: (Formulario) -> Formulario) = form.update(cambio)

    fun guardar() {
        val e = estado.value
        if (e.f.guardando) return
        val cuenta = e.cuenta ?: return
        val real = e.real ?: return
        if (e.delta == null || e.delta == 0L) return
        form.update { it.copy(guardando = true) }
        viewModelScope.launch {
            try {
                repo.agregarTx(buildAdjustment(cuenta.id, e.actual, real, todayIso()))
                Avisos.avisar("Saldo ajustado ✓")
                _salir.send(Unit)
            } catch (ex: Exception) {
                Avisos.avisar(ex.message ?: "No se pudo ajustar")
            } finally {
                form.update { it.copy(guardando = false) }
            }
        }
    }
}
```

- [ ] **Paso 4: `NuevoPantalla.kt` + formularios.** Contrato funcional (composición según `DESIGN.md`):
  1. `Segmentado` con «Gasto», «Ingreso», «Transferir», «Ajustar».
  2. Gasto/Ingreso: `Selector` «Cuenta» (`Nombre (Bs|$)`, solo activas); cifra grande con `formatMoney(±minor, cuenta.currency)` o «—» si no hay monto (color de gasto o ingreso según el modo); `Teclado` → `vm.tecla`; `CampoTexto` «Nota (opcional)» con placeholder «¿En qué fue?»; rótulo «Toca la categoría para guardar» + `CategoriaGrid(categorias, enabled = !guardando) { vm.guardar(it) }`.
  3. Transferir (`TransferirFormulario`, VM propio con `vmCon { c, _ -> TransferirViewModel(c.repo) }`): «Desde» y «Hacia» (cada uno excluye la cuenta elegida en el otro), «Monto en Bs|$» (teclado decimal del sistema), si cruza moneda: «Tasa (Bs por $) — propuesta {valor} del {fecha}» o «no disponible» + aviso OK «Destino recibirá {formatMoney}»; «Nota (opcional)»; botón «Transferir» con `enabled = estado.puedeGuardar`.
  4. Ajustar (`AjustarFormulario`): «Cuenta», «Saldo según la app: {formatMoney}», «Saldo real actual», aviso con «Variación/rendimiento» o «Diferencia» + `formatMoney(delta)` (tipo OK si delta > 0, AVISO si < 0), botón «Ajustar saldo» con `enabled = estado.puedeGuardar`.
  5. Los tres VMs exponen `salir`: `LaunchedEffect(Unit) { vm.salir.collect { nav.popBackStack(Rutas.INICIO, inclusive = false) } }`.
  6. Monta `NUEVO → NuevoPantalla(nav)` en el NavHost; dentro, `val vm = vmCon { c, h -> NuevoViewModel(c.repo, h) }` (la Tarea 20 cambia a `NuevoViewModel(c.repo, c.capturas, h)`). Del mismo modo: Inicio `vmCon { c, _ -> InicioViewModel(c.repo) }`, Movimientos `vmCon { c, h -> MovimientosViewModel(c.repo, h) }`.

- [ ] **Paso 5: Verificación (bloqueante).** `instalar` → `sembrar …demo` → en Inicio: `tocar "Nuevo"` (toque 1) → `tocar "1"`, `tocar "2"`, `tocar "coma"`, `tocar "5"` → `captura fase-2 nuevo-monto` (cifra `−Bs 12,50` o `−$12.50` según la cuenta) → `tocar "Supermercado"` (guarda) → `captura fase-2 nuevo-guardado` (aviso «Guardado ✓» y vuelta a Inicio). Cuenta los toques: «+», monto, categoría = flujo ≤ 3 toques del spec (el monto cuenta como un paso). Doble toque rápido en la categoría (`tocar` dos veces sin pausa) → un solo movimiento nuevo (compruébalo en Movimientos tras la Tarea 15 o con `exportar` y contando). Transferir de Zinli a BNC con tasa propuesta → `captura fase-2 transferir`. Ajustar Binance → `captura fase-2 ajustar`. Read de cada PNG.

- [ ] **Paso 6: Commit.**

```bash
git add android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/nuevo/ android/app/src/main/java/com/cuentasclaras/app/ui/nav/CuentasNavHost.kt
git commit -m "feat(nuevo): registro de gasto e ingreso en 3 toques, transferencias y ajustes de saldo" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Tarea 13: Inicio — patrimonio, deudas y tarjeta semanal (placeholder)

**Skills:** `ui-ux-pro-max:ui-ux-pro-max` (jerarquía de la cifra principal, slider de 3 vistas) · `output-skill`.

**Files:**
- Create: `android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/inicio/InicioViewModel.kt`
- Create: `android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/inicio/InicioPantalla.kt`
- Create: `android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/inicio/PatrimonioTarjeta.kt`
- Create: `android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/inicio/DeudasTarjeta.kt`
- Create: `android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/inicio/SemanaTarjeta.kt`
- Modify: `android/app/src/main/java/com/cuentasclaras/app/ui/nav/CuentasNavHost.kt` (solo la línea de `INICIO`)

Fuentes: `src/ui/paginas/DashboardPage.tsx`, `src/ui/componentes/PatrimonioCard.tsx`, `DeudasCard.tsx`.

**Interfaces:**
- Consumes: T3 (`patrimonio`, `debtTotals`, `txsEfectivas`, `monthSummary`, `lastMonths`, `budgetStatus`), T2 (`effectiveRate`, `usdToVesMinor`, `todayIso`, `monthOf`), T11.
- Produces: `InicioViewModel(repo)` con `estado: StateFlow<InicioViewModel.Estado>`, `irAMes(m: String)`, `alternarMoneda()`. `Estado` incluye `mes`, `mesActual`, `primerMes`, `resumen`, `comparativa`, `presupuestos`, `categorias` (los consume `EsteMesSeccion` de la Tarea 14). Las Tareas 20 (capturas) y 30 (semana real) extienden este VM.

- [ ] **Paso 1: `InicioViewModel.kt`.**

```kotlin
package com.cuentasclaras.app.ui.pantallas.inicio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuentasclaras.app.data.CuentasRepo
import com.cuentasclaras.app.data.Instantanea
import com.cuentasclaras.app.domain.Account
import com.cuentasclaras.app.domain.Category
import com.cuentasclaras.app.domain.Currency
import com.cuentasclaras.app.domain.Debt
import com.cuentasclaras.app.domain.DebtDirection
import com.cuentasclaras.app.domain.DebtStatus
import com.cuentasclaras.app.domain.EstadoPresupuesto
import com.cuentasclaras.app.domain.Patrimonio
import com.cuentasclaras.app.domain.Rate
import com.cuentasclaras.app.domain.ResumenMes
import com.cuentasclaras.app.domain.TotalesDeudas
import com.cuentasclaras.app.domain.budgetStatus
import com.cuentasclaras.app.domain.debtTotals
import com.cuentasclaras.app.domain.effectiveRate
import com.cuentasclaras.app.domain.lastMonths
import com.cuentasclaras.app.domain.monthOf
import com.cuentasclaras.app.domain.monthSummary
import com.cuentasclaras.app.domain.patrimonio
import com.cuentasclaras.app.domain.todayIso
import com.cuentasclaras.app.domain.txsEfectivas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class InicioViewModel(private val repo: CuentasRepo) : ViewModel() {
    data class Estado(
        val cargado: Boolean = false,
        val hoy: String,
        val viewCurrency: Currency = Currency.USD,
        val tasa: Rate? = null,
        val patrimonio: Patrimonio? = null,
        val cuentas: List<Account> = emptyList(),
        val categorias: List<Category> = emptyList(),
        val totalesDeudas: TotalesDeudas = TotalesDeudas(0, 0),
        /** Las 3 pendientes más recientes (createdIso desc). */
        val deudasRecientes: List<Debt> = emptyList(),
        /** Mes que gobierna Este mes, Comparativa y Presupuestos; acotado a [primerMes, mesActual]. */
        val mes: String,
        val mesActual: String,
        val primerMes: String,
        val resumen: ResumenMes? = null,
        val comparativa: List<ResumenMes> = emptyList(),
        val presupuestos: List<EstadoPresupuesto> = emptyList(),
    )

    private val hoy = todayIso()
    private val mesVisto = MutableStateFlow(monthOf(hoy))

    val estado: StateFlow<Estado> = combine(repo.instantanea, mesVisto) { i, visto -> calcular(i, visto) }
        .flowOn(Dispatchers.Default)
        .stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5_000),
            Estado(hoy = hoy, mes = monthOf(hoy), mesActual = monthOf(hoy), primerMes = monthOf(hoy)),
        )

    private fun calcular(i: Instantanea, visto: String): Estado {
        val s = i.settings
        val tasa = effectiveRate(hoy, s.rateSource, i.tasas)
        val pendientes = i.deudas.filter { it.status == DebtStatus.PENDIENTE }
        val p = patrimonio(i.cuentas, i.txs, tasa?.vesPerUsd, pendientes.filter { it.direction == DebtDirection.POR_PAGAR })
        val mesActual = monthOf(hoy)
        val primerMes = i.txs.minOfOrNull { it.date }?.let { monthOf(it) } ?: mesActual
        val mes = when {
            visto > mesActual -> mesActual
            visto < primerMes -> primerMes
            else -> visto
        }
        // txsEfectivas SOLO para agregaciones; el patrimonio de arriba usa las crudas (restricción 7).
        val efectivas = txsEfectivas(i.txs, i.cuentas, i.grupos, i.categorias)
        val resumen = monthSummary(efectivas, i.cuentas, mes, s.viewCurrency, i.tasas, s.rateSource)
        val comparativa = lastMonths(mes, 4)
            .map { monthSummary(efectivas, i.cuentas, it, s.viewCurrency, i.tasas, s.rateSource) }
            .reversed()
        val presupuestos = budgetStatus(efectivas, i.cuentas, i.categorias, mes, i.tasas, s.rateSource)
        return Estado(
            cargado = true, hoy = hoy, viewCurrency = s.viewCurrency, tasa = tasa, patrimonio = p,
            cuentas = i.cuentas, categorias = i.categorias,
            totalesDeudas = debtTotals(pendientes, i.cuentas, tasa?.vesPerUsd),
            deudasRecientes = pendientes.sortedByDescending { it.createdIso }.take(3),
            mes = mes, mesActual = mesActual, primerMes = primerMes,
            resumen = resumen, comparativa = comparativa, presupuestos = presupuestos,
        )
    }

    fun irAMes(m: String) {
        mesVisto.value = m
    }

    fun alternarMoneda() {
        viewModelScope.launch {
            repo.cambiarSettings { it.copy(viewCurrency = if (it.viewCurrency == Currency.USD) Currency.VES else Currency.USD) }
        }
    }
}
```

- [ ] **Paso 2: `PatrimonioTarjeta.kt`** (contrato de `PatrimonioCard.tsx`): título «Patrimonio» + chip «ver en Bs»/«ver en $» (`alternarMoneda`); `HorizontalPager` de 3 vistas «A disposición» (`aDisposicionUsdMinor`), «Total neto» (`netoUsdMinor`), «Total actual» (`totalUsdMinor`) con indicador de puntos; cada cifra con la regla `enVista`: `null` → «sin tasa»; USD → `formatMoney(usd, USD)`; VES → `formatMoney(usdToVesMinor(usd, tasa.vesPerUsd), VES)` o «sin tasa» si no hay tasa. Línea de tasa «Tasa {source} del {date}: {vesPerUsd} Bs/$» + chip «tasa vieja» si `tasa.date != hoy`; sin tasa → `Banner(AVISO)` «Sin tasa configurada — regístrala en Ajustes → Tasas.». Lista de cuentas activas: candado si `isSavings`, nombre, `formatMoney(balanceMinor, currency)` y, en cuentas VES, «≈ {formatMoney(balanceUsdMinor, USD)}» o «≈ —».

- [ ] **Paso 3: `DeudasTarjeta.kt`** (contrato de `DeudasCard.tsx`): «Deudas» + acción «ver todas» → `nav.navigate(Rutas.DEUDAS)` (en la fase 2 lleva a `Proximamente`; la Tarea 26 lo activa); «Debes {enVista(debesUsdMinor)} · Te deben {enVista(teDebenUsdMinor)}»; hasta 3 pendientes recientes con flecha de salida (por pagar) o entrada (por cobrar), concepto y `formatMoney(amountMinor, moneda de su cuenta)`; sin pendientes → «Sin deudas pendientes.».

- [ ] **Paso 4: `SemanaTarjeta.kt` (placeholder de la fase 2, spec §7.2):** título «Esta semana», `Vacio("📅", "Sin eventos")` y acción «Calendario» deshabilitada. La Tarea 30 la reemplaza por la real.

- [ ] **Paso 5: `InicioPantalla.kt`:** `LazyColumn` con título «Inicio», `PatrimonioTarjeta`, `SemanaTarjeta`, `DeudasTarjeta` y un hueco para `EsteMesSeccion` (Tarea 14). Mientras `!estado.cargado`, nada (sin parpadeos). Monta `INICIO → InicioPantalla(nav)` en el NavHost.

- [ ] **Paso 6: Verificación.** `instalar` → `sembrar …demo` → `captura fase-2 inicio-usd` → desliza el pager (`input swipe`) dos veces con captura por vista → `tocar "ver en Bs"` → `captura fase-2 inicio-bs` → Read. Comprueba con el demo: la línea de BNC en Bs y su `≈ $`; «Debes $20.00» (deuda-demo-1); tasa «BCV del 2026-10-10: 185.5 Bs/$» con chip «tasa vieja» (si hoy ≠ 2026-10-10 y no hubo red; con red verás la de hoy).

- [ ] **Paso 7: Commit.**

```bash
git add android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/inicio/ android/app/src/main/java/com/cuentasclaras/app/ui/nav/CuentasNavHost.kt
git commit -m "feat(inicio): patrimonio con 3 vistas, deudas y tarjeta semanal provisional" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Tarea 14: «Este mes», comparativa y presupuestos con gráficas en Canvas

**Skills:** `dataviz` (cárgala ANTES de dibujar: forma, color, ejes y legibilidad) · `ui-ux-pro-max:ui-ux-pro-max` · `output-skill`.

**Files:**
- Create: `android/app/src/main/java/com/cuentasclaras/app/ui/componentes/Dona.kt`
- Create: `android/app/src/main/java/com/cuentasclaras/app/ui/componentes/Barras.kt`
- Create: `android/app/src/main/java/com/cuentasclaras/app/ui/componentes/CurvaAcumulada.kt`
- Create: `android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/inicio/EsteMesSeccion.kt`
- Modify: `android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/inicio/InicioPantalla.kt` (monta `EsteMesSeccion`)

Fuente: `src/ui/componentes/GraficasMes.tsx` (sin librería de gráficas, spec §3.5).

**Interfaces:**
- Consumes: T13 (`InicioViewModel.Estado`, `irAMes`), T2 (`formatMoney`, `nombreMes`, `mesAnterior`, `mesSiguiente`), T11 (`Rutas.movimientos`).
- Produces: `@Composable fun Dona(segmentos: List<SegmentoDona>, modifier)` con `data class SegmentoDona(etiqueta: String, valor: Long, color: Color)`; `@Composable fun Barras(grupos: List<GrupoBarras>, colores: List<Color>, formato: (Long) -> String, modifier)` con `data class GrupoBarras(etiqueta: String, valores: List<Long>)`; `@Composable fun CurvaAcumulada(puntos: List<PuntoDiario>, formato: (Long) -> String, modifier)`; `@Composable fun EsteMesSeccion(estado: InicioViewModel.Estado, onMes: (String) -> Unit, onCategoria: (categoryId: String, mes: String) -> Unit)`.

- [ ] **Paso 1: Gráficas en `Canvas`** siguiendo `dataviz` y `DESIGN.md` §7: `Dona` (arcos con `drawArc`, hueco central con el total del mes, sin etiquetas que se recorten: leyenda debajo en dos columnas con nombre y %), `Barras` (ingresos vs gastos de 4 meses, etiquetas `mm` bajo cada grupo, valor máximo a escala, `contentDescription` con los importes), `CurvaAcumulada` (gasto acumulado diario del mes, eje X de días). Todas con `Modifier.semantics { contentDescription = … }` en español con los importes formateados.

- [ ] **Paso 2: `EsteMesSeccion.kt`** (contrato de `GraficasMes.tsx`):
  1. Navegación de mes: «‹» (`contentDescription = "Mes anterior"`, deshabilitado si `mes <= primerMes`), título «Este mes» si `mes == mesActual` o `nombreMes(mes)`, «›» («Mes siguiente», deshabilitado si `mes >= mesActual`) y chip «hoy» si `mes != mesActual`. Gobierna las TRES tarjetas.
  2. «Ingresos +{formatMoney}» · «Gastos −{formatMoney}» y «Balance: {formatMoney(ingresos − gastos)}» (color según signo).
  3. Si `omitidasSinTasa > 0`: `Banner(AVISO)` «{n} movimiento(s) sin tasa quedaron fuera del resumen.».
  4. `Dona` de `byCategory` (color = color de la categoría; «Sin categoría» en `textoSecundario`) y `CurvaAcumulada`.
  5. Chip «Ver todas las categorías» / «Ocultar»: lista «Gastos» (`byCategory`) e «Ingresos» (`incomeByCategory`) con ícono, nombre, barra proporcional, monto y `%` = `Math.round(minor * 100.0 / total)`. Tocar una categoría existente → `onCategoria(id, mes)` → `nav.navigate(Rutas.movimientos(categoria = id, mes = mes))`; «Sin categoría» no es tocable.
  6. Tarjeta «Comparativa mensual»: `Barras` de `comparativa` (ingresos en color `ingreso`, gastos en `gasto`) con leyenda.
  7. Tarjeta «Presupuestos (USD) · {nombreMes(mes)}» solo si hay: por categoría, ícono + nombre, «{formatMoney(gasto, USD)} / {formatMoney(presupuesto, USD)}» (en color `gasto` si `ratio > 1`) y barra de progreso al `min(100, ratio * 100)` % (color `gasto` si `ratio > 1`, si no `primario`).

- [ ] **Paso 3: Verificación.** `instalar` → `sembrar …demo` → desplaza Inicio (`input swipe 540 1800 540 600`) → `captura fase-2 este-mes` → `tocar "Ver todas las categorías"` → `captura fase-2 este-mes-lista` → `tocar "Mes anterior"` hasta agosto (el demo tiene «Cine» el 2026-08-20) → `captura fase-2 mes-anterior` → `tocar "Ocio/Juegos"` → `captura fase-2 drill-down` (debe abrir Movimientos; el filtro se ve aplicado tras la Tarea 15). Comprueba en octubre: el grupo «leche» aporta solo su vuelto (+$1.50 en «Vuelto», no $3.50 en Supermercado) y la transferencia no suma. Read de cada PNG.

- [ ] **Paso 4: Commit.**

```bash
git add android/app/src/main/java/com/cuentasclaras/app/ui/componentes/Dona.kt android/app/src/main/java/com/cuentasclaras/app/ui/componentes/Barras.kt android/app/src/main/java/com/cuentasclaras/app/ui/componentes/CurvaAcumulada.kt android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/inicio/EsteMesSeccion.kt android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/inicio/InicioPantalla.kt
git commit -m "feat(inicio): «Este mes» con navegación de mes, dona, comparativa y presupuestos en Canvas" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Tarea 15: Movimientos — lista con filtros, grupos visibles y editor

**Skills:** `ui-ux-pro-max:ui-ux-pro-max` (lista densa con filtros) · `output-skill`.

**Files:**
- Create: `android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/movimientos/MovimientosViewModel.kt`
- Create: `android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/movimientos/MovimientosPantalla.kt`
- Create: `android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/movimientos/EditorMovimiento.kt`
- Modify: `android/app/src/main/java/com/cuentasclaras/app/ui/nav/CuentasNavHost.kt` (solo la línea de `MOVIMIENTOS`)

Fuente: `src/ui/paginas/MovimientosPage.tsx` (lista, filtros, modelo «sueltas + grupos», drill-down por categoría del residuo, `EditorMovimiento`). La selección múltiple y las acciones de grupo llegan en la Tarea 27; `SelectEvento` en la Tarea 30.

**Interfaces:**
- Consumes: T3 (`filterTxs`, `FiltroMovs`, `residuosDeGrupo`), T2, T11, T10 (`CampoFecha`).
- Produces: `sealed interface ItemMov { data class Suelta(t: Transaction); data class Grupo(gid: String, visibles: List<Transaction>, todos: List<Transaction>, residuos: List<ResiduoMoneda>, grupo: TxGroup?) }`; `fun construirItems(txs, grupos, accounts, filtro: FiltroMovs): List<ItemMov>`; `MovimientosViewModel(repo, args)` con `estado`, `cambiarFiltro(FiltroMovs)`, `alternarGrupo(gid)`, `editar(t)`, `cerrarEditor()`, `guardarEdicion(...)`, `eliminar(...)`. La Tarea 27 añade selección; la 30, el evento en el editor.

- [ ] **Paso 1: `MovimientosViewModel.kt`.** `construirItems` porta líneas 136–176 de `MovimientosPage.tsx` (incluido el drill-down: con filtro de categoría, un grupo cuya categoría del residuo es la filtrada se muestra con sus miembros filtrados por texto/cuenta/mes aunque ninguno lleve esa categoría).

```kotlin
package com.cuentasclaras.app.ui.pantallas.movimientos

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuentasclaras.app.data.CuentasRepo
import com.cuentasclaras.app.domain.Account
import com.cuentasclaras.app.domain.CalEvent
import com.cuentasclaras.app.domain.Category
import com.cuentasclaras.app.domain.FiltroMovs
import com.cuentasclaras.app.domain.ResiduoMoneda
import com.cuentasclaras.app.domain.Transaction
import com.cuentasclaras.app.domain.TxGroup
import com.cuentasclaras.app.domain.TxType
import com.cuentasclaras.app.domain.esFechaIso
import com.cuentasclaras.app.domain.filterTxs
import com.cuentasclaras.app.domain.parseVenezuelanAmount
import com.cuentasclaras.app.domain.residuosDeGrupo
import com.cuentasclaras.app.ui.Avisos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ItemMov {
    val clave: String
    data class Suelta(val t: Transaction) : ItemMov {
        override val clave: String get() = "${t.date} ${t.time ?: ""}"
    }
    data class Grupo(
        val gid: String,
        val visibles: List<Transaction>,
        val todos: List<Transaction>,
        val residuos: List<ResiduoMoneda>,
        val grupo: TxGroup?,
    ) : ItemMov {
        override val clave: String get() = visibles.maxOf { "${it.date} ${it.time ?: ""}" }
    }
}

fun construirItems(txs: List<Transaction>, grupos: List<TxGroup>, accounts: List<Account>, filtro: FiltroMovs): List<ItemMov> {
    val visibles = filterTxs(txs, filtro)
    val gruposVisibles = LinkedHashMap<String, List<Transaction>>()
    val sueltas = mutableListOf<Transaction>()
    for (t in visibles) {
        val gid = t.groupId
        if (gid != null) gruposVisibles[gid] = (gruposVisibles[gid] ?: emptyList()) + t else sueltas.add(t)
    }
    if (!filtro.categoryId.isNullOrEmpty()) {
        val sinFiltroDeCategoria = filterTxs(txs, filtro.copy(categoryId = null))
        for (g in grupos) {
            if (g.categoryId != filtro.categoryId || g.id in gruposVisibles) continue
            val miembros = sinFiltroDeCategoria.filter { it.groupId == g.id }
            if (miembros.isNotEmpty()) gruposVisibles[g.id] = miembros
        }
    }
    val grupoDe = grupos.associateBy { it.id }
    val items: List<ItemMov> = sueltas.map { ItemMov.Suelta(it) } + gruposVisibles.map { (gid, vis) ->
        val todos = txs.filter { it.groupId == gid }
        ItemMov.Grupo(gid, vis, todos, residuosDeGrupo(todos, accounts), grupoDe[gid])
    }
    return items.sortedByDescending { it.clave }
}

class MovimientosViewModel(private val repo: CuentasRepo, args: SavedStateHandle) : ViewModel() {
    data class Ui(
        val filtro: FiltroMovs,
        val expandidos: Set<String> = emptySet(),
        val editando: Transaction? = null,
        val guardando: Boolean = false,
    )

    data class Estado(
        val ui: Ui,
        val items: List<ItemMov> = emptyList(),
        /** Meses con movimientos, desc, para el filtro. */
        val meses: List<String> = emptyList(),
        val cuentas: List<Account> = emptyList(),
        val categorias: List<Category> = emptyList(),
        val eventos: List<CalEvent> = emptyList(),
    )

    private val ui = MutableStateFlow(
        Ui(FiltroMovs(categoryId = args.get<String>("categoria"), month = args.get<String>("mes"), accountId = args.get<String>("cuenta"))),
    )

    val estado: StateFlow<Estado> = combine(ui, repo.instantanea) { u, i ->
        Estado(
            ui = u,
            items = construirItems(i.txs, i.grupos, i.cuentas, u.filtro),
            meses = i.txs.map { it.date.take(7) }.distinct().sortedDescending(),
            cuentas = i.cuentas,
            categorias = i.categorias,
            eventos = i.eventos,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Estado(ui.value))

    fun cambiarFiltro(f: FiltroMovs) = ui.update { it.copy(filtro = f) }
    fun alternarGrupo(gid: String) = ui.update { it.copy(expandidos = if (gid in it.expandidos) it.expandidos - gid else it.expandidos + gid) }
    fun editar(t: Transaction) = ui.update { it.copy(editando = t) }
    fun cerrarEditor() = ui.update { it.copy(editando = null) }

    /** Transferencias y ajustes: solo nota y fecha son editables (el monto afecta dos saldos o es un delta). */
    fun guardarEdicion(original: Transaction, montoTxt: String, categoryId: String?, fecha: String, nota: String) {
        if (ui.value.guardando) return
        if (!esFechaIso(fecha)) { Avisos.avisar("Fecha inválida"); return }
        var cambiado = original.copy(note = nota.trim().takeIf { it.isNotEmpty() }, date = fecha)
        if (original.type == TxType.EXPENSE || original.type == TxType.INCOME) {
            val m = parseVenezuelanAmount(montoTxt.removeSuffix(","))
            if (m == null || m <= 0) { Avisos.avisar("Monto inválido"); return }
            cambiado = cambiado.copy(amountMinor = m, categoryId = categoryId?.takeIf { it.isNotEmpty() })
        }
        ui.update { it.copy(guardando = true) }
        viewModelScope.launch {
            try {
                repo.actualizarTx(cambiado)
                Avisos.avisar("Actualizado ✓")
                ui.update { it.copy(editando = null) }
            } catch (e: Exception) {
                Avisos.avisar(e.message ?: "No se pudo actualizar")
            } finally {
                ui.update { it.copy(guardando = false) }
            }
        }
    }

    /** La UI pide confirmación ANTES de llamar aquí. */
    fun eliminar(t: Transaction) {
        if (ui.value.guardando) return
        ui.update { it.copy(guardando = true) }
        viewModelScope.launch {
            try {
                repo.borrarTx(t.id)
                Avisos.avisar("Eliminado")
                ui.update { it.copy(editando = null) }
            } catch (e: Exception) {
                Avisos.avisar(e.message ?: "No se pudo eliminar")
            } finally {
                ui.update { it.copy(guardando = false) }
            }
        }
    }
}
```

`esFechaIso` (T2) es más estricto que el regex de la web (rechaza 30/02); es lo correcto para el editor.

- [ ] **Paso 2: `MovimientosPantalla.kt`** (contrato): título «Movimientos»; `CampoTexto` «Buscar en notas…»; tres `Selector` en fila: cuenta («Todas las cuentas» + cuentas), categoría («Todas las categorías» + categorías) y mes («Todos los meses» + `meses` con `nombreMes`); `LazyColumn` de `items`:
  - Fila suelta: ícono (🔁 transferencia, 🎯 ajuste, si no el emoji de la categoría o 📦), descripción (`"Origen → Destino"` en transferencias; si no `note` ‖ nombre de categoría ‖ «Ajuste de saldo»/«Sin nota»), meta «{date} · {cuenta}{ · importado si importKey}{ · 📅 título si eventId existe}», monto con signo y color (`+` ingreso, `−` gasto, ajuste con su signo, `↔` transferencia). Toque → `vm.editar(t)`.
  - Fila de grupo: ícono de la categoría del grupo o 🔁, nota del grupo o «Grupo transaccional», meta «{n} movimientos · vuelto{ · categoría}», un renglón por residuo (`+`/`−` y `formatMoney(abs, currency)`) o «sin residuo». Toque → expande/contrae los miembros visibles (indentados).
  - Vacío: `Vacio("🔍", "No hay movimientos con estos filtros")`.
- [ ] **Paso 3: `EditorMovimiento.kt`** (`HojaModal` «Editar movimiento»): si es gasto/ingreso, «Monto» (inicial `montoEditable(amountMinor)`) y «Categoría» («Sin categoría» + las del mismo kind); siempre «Fecha» (`CampoFecha`) y «Nota»; botones «Cancelar», «Eliminar» (abre `AlertDialog` «¿Eliminar este movimiento?» → `vm.eliminar`) y «Guardar». Todos con `enabled = !guardando`.
- [ ] **Paso 4: Monta `MOVIMIENTOS → MovimientosPantalla(nav)`.**
- [ ] **Paso 5: Verificación.** `instalar` → `sembrar …demo` → `tocar "Movs"` → `captura fase-2 movs` → toca la fila «me pasaron para la leche» → `captura fase-2 movs-grupo` (2 miembros, `+$1.50`) → toca «Taxi» → cambia la fecha al día 15 con el DatePicker → `captura fase-2 editor-fecha` y comprueba en la lista que quedó **15** (no 14: trampa UTC del Paso 8 de la Tarea 10) → desde Inicio repite el drill-down de la Tarea 14 y comprueba que llega con categoría y mes ya filtrados → `captura fase-2 drill-down-filtros`. Read.
- [ ] **Paso 6: Commit.**

```bash
git add android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/movimientos/ android/app/src/main/java/com/cuentasclaras/app/ui/nav/CuentasNavHost.kt
git commit -m "feat(movimientos): lista con filtros, grupos con residuo y editor de movimiento" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Tarea 16: Ajustes — cuentas, categorías, tasas, reglas y respaldo

**Skills:** `ui-ux-pro-max:ui-ux-pro-max` (pantalla de ajustes por secciones) · `output-skill`.

**Files:**
- Create: `android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/ajustes/AjustesViewModel.kt`
- Create: `android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/ajustes/AjustesPantalla.kt`
- Create: `android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/ajustes/CuentasSeccion.kt`
- Create: `android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/ajustes/CategoriasSeccion.kt`
- Create: `android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/ajustes/TasasSeccion.kt`
- Create: `android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/ajustes/ReglasSeccion.kt`
- Modify: `android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/respaldo/RespaldoSeccion.kt` (solo estilos: componentes del tema)
- Modify: `android/app/src/main/java/com/cuentasclaras/app/ui/nav/CuentasNavHost.kt` (solo la línea de `AJUSTES`)

Fuentes: `src/ui/paginas/AjustesPage.tsx`, `CuentasSection.tsx`, `CategoriasSection.tsx`, `TasasSection.tsx`, `ReglasSection.tsx`, `RespaldoSection.tsx`.

**Interfaces:**
- Consumes: T11 (`CuentasRepo`, `AppContainer.http`), T9 (`RespaldoSeccion`), T2/T3.
- Produces: `AjustesViewModel(repo, http)` con `estado`, `guardarCuenta(Account)`, `guardarCategoria(Category, presupuestoTxt: String)`, `cambiarFuente(RateSource)`, `guardarTasaManual(fecha, fuente, valorTxt)`, `actualizarBcvAhora()`, `borrarRegla(id)`; enlace «Permisos de captura» (la Tarea 21 lo activa).

- [ ] **Paso 1: `AjustesViewModel.kt`.**

```kotlin
package com.cuentasclaras.app.ui.pantallas.ajustes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuentasclaras.app.data.CuentasRepo
import com.cuentasclaras.app.data.Instantanea
import com.cuentasclaras.app.domain.Account
import com.cuentasclaras.app.domain.Category
import com.cuentasclaras.app.domain.Rate
import com.cuentasclaras.app.domain.RateSource
import com.cuentasclaras.app.domain.effectiveRate
import com.cuentasclaras.app.domain.esFechaIso
import com.cuentasclaras.app.domain.todayIso
import com.cuentasclaras.app.ui.Avisos
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class AjustesViewModel(private val repo: CuentasRepo, private val http: OkHttpClient) : ViewModel() {
    data class Ocupado(val guardando: Boolean = false, val buscando: Boolean = false)
    data class Estado(
        val datos: Instantanea? = null,
        /** Calculada sobre el histórico COMPLETO (no sobre la tabla de 14 filas). */
        val vigente: Rate? = null,
        val recientes: List<Rate> = emptyList(),
        val ocupado: Ocupado = Ocupado(),
    )

    private val ocupado = MutableStateFlow(Ocupado())

    val estado: StateFlow<Estado> = combine(repo.instantanea, ocupado) { i, o ->
        Estado(
            datos = i,
            vigente = effectiveRate(todayIso(), i.settings.rateSource, i.tasas),
            recientes = i.tasas.sortedByDescending { it.date }.take(14),
            ocupado = o,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Estado())

    private fun guardarCon(exito: String, trabajo: suspend () -> Unit) {
        if (ocupado.value.guardando) return
        ocupado.update { it.copy(guardando = true) }
        viewModelScope.launch {
            try {
                trabajo()
                Avisos.avisar(exito)
            } catch (e: Exception) {
                Avisos.avisar(e.message ?: "No se pudo guardar")
            } finally {
                ocupado.update { it.copy(guardando = false) }
            }
        }
    }

    /** Devuelve false (y avisa) si la validación falla, para que la hoja no se cierre. */
    fun guardarCuenta(a: Account): Boolean {
        if (a.name.isBlank()) { Avisos.avisar("Ponle nombre a la cuenta"); return false }
        guardarCon("Cuenta guardada ✓") { repo.guardarCuenta(a.copy(name = a.name.trim())) }
        return true
    }

    fun guardarCategoria(c: Category, presupuestoTxt: String): Boolean {
        if (c.name.isBlank()) { Avisos.avisar("Ponle nombre a la categoría"); return false }
        val limpio = presupuestoTxt.trim().replace(',', '.')
        val presupuesto: Long? = if (limpio.isEmpty()) null else {
            val v = limpio.toDoubleOrNull()
            if (v == null || !v.isFinite() || v <= 0) { Avisos.avisar("Presupuesto inválido"); return false }
            Math.round(v * 100)
        }
        guardarCon("Categoría guardada ✓") { repo.guardarCategoria(c.copy(name = c.name.trim(), monthlyBudgetMinor = presupuesto)) }
        return true
    }

    fun cambiarFuente(f: RateSource) {
        viewModelScope.launch { repo.cambiarSettings { it.copy(rateSource = f) } }
    }

    fun guardarTasaManual(fecha: String, fuente: RateSource, valorTxt: String): Boolean {
        val v = valorTxt.replace(',', '.').toDoubleOrNull()
        if (v == null || !v.isFinite() || v <= 0 || !esFechaIso(fecha)) { Avisos.avisar("Tasa o fecha inválida"); return false }
        guardarCon("Tasa guardada ✓") { repo.guardarTasa(Rate(fecha, fuente, v)) }
        return true
    }

    fun actualizarBcvAhora() {
        if (ocupado.value.buscando) return
        ocupado.update { it.copy(buscando = true) }
        viewModelScope.launch {
            try {
                val n = repo.actualizarBcvAhora(http)
                Avisos.avisar(if (n > 0) "Tasa BCV actualizada ✓" else "No se pudo obtener el BCV ahora — usa la tasa manual")
            } catch (e: Exception) {
                Avisos.avisar(e.message ?: "No se pudo actualizar")
            } finally {
                ocupado.update { it.copy(buscando = false) }
            }
        }
    }

    fun borrarRegla(id: String) {
        viewModelScope.launch { repo.borrarRegla(id) }
    }
}
```

- [ ] **Paso 2: Secciones** (contrato; `AjustesPantalla` es un `LazyColumn` de tarjetas: Cuentas, Categorías, Tasas, Reglas, Respaldo, Permisos):
  - `CuentasSeccion`: lista (nombre, chip moneda Bs/$, chip «ahorro», chip «archivada», texto atenuado si archivada) + «editar» por fila (`contentDescription = "Editar {nombre}"`) + «Nueva cuenta» (plantilla: `Account(nuevoId(), "", USD, WALLET, isSavings = false, archived = false, enDisposicion = true)`). Hoja «Nueva cuenta»/«Editar cuenta»: «Nombre», «Moneda (no convierte movimientos existentes)» (USD ($) / Bolívares (Bs)), «Tipo» (Banco, Billetera, Solo saldo (ajustes manuales), Efectivo), interruptores «Cuenta de ahorro (no cuenta como disposición)», «A disposición (cuenta en la vista «A disposición» del patrimonio)» (valor inicial `enDisposicion ?: !isSavings`), «Archivada (oculta del dashboard y formularios)»; «Cancelar» / «Guardar cuenta» (cierra solo si `guardarCuenta` devuelve true).
  - `CategoriasSeccion`: lista (emoji, nombre, chip gasto/ingreso, chip «presup. {formatMoney(USD)}») + «editar» + «Nueva categoría» (plantilla `Category(nuevoId(), "", "📦", "#6b7280", EXPENSE)`). Hoja «Categoría»: «Nombre», «Icono (emoji)», «Color» (paleta de 16 muestras: los colores de los seeds + `#6b7280`, más campo hex validado `^#[0-9a-fA-F]{6}$`), «Tipo» (Gasto/Ingreso), «Presupuesto mensual en $ (vacío = sin presupuesto)» (inicial `String.format(java.util.Locale.ROOT, "%.2f", minor / 100.0)`: con punto como la web, sin depender del idioma del teléfono); «Cancelar» / «Guardar categoría».
  - `TasasSeccion`: «Vigente hoy: {vesPerUsd} Bs/$ ({source} {date})» o «sin tasa»; `Selector` «Fuente activa para conversiones» (BCV, Binance USDT/VES, Manual) → `cambiarFuente`; botón «Actualizar BCV ahora» (texto «Buscando…» mientras `buscando`); el párrafo explicativo de la web («Solo el BCV se actualiza online…»); «Registrar/corregir tasa (Bs por $)»: `CampoFecha` (hoy por defecto), `Selector` de fuente (Binance USDT/VES por defecto, BCV, Manual (override del día)), «Valor de tasa» (placeholder «ej. 111,02») y «Guardar» (limpia el valor si guardó); tabla «Histórico reciente» (Fecha, Fuente, Bs/$) de `recientes`.
  - `ReglasSeccion`: vacía → `Vacio("🪄", "Aún no hay reglas. Se crean al categorizar en los imports.")`; si no, ««{pattern}» → {nombre de categoría o ?}» + «eliminar».
  - Respaldo: `RespaldoSeccion()` (T9), re-estilizada SOLO con componentes del tema.
  - Permisos: fila «Permisos de captura» con texto «Disponible en la fase 3» (deshabilitada); la Tarea 21 la enlaza a `Rutas.PERMISOS`.
- [ ] **Paso 3: Monta `AJUSTES → AjustesPantalla(nav)`** con `vmCon { c, _ -> AjustesViewModel(c.repo, c.http) }`.
- [ ] **Paso 4: Verificación.** `instalar` → `sembrar …demo` → `tocar "Ajustes"` → `captura fase-2 ajustes` → crea una cuenta «Prueba» USD → `captura fase-2 cuenta-nueva` → edita «Supermercado» con presupuesto `150` → en Inicio la tarjeta Presupuestos muestra `/ $150.00` → registra una tasa Binance `230,5` → «Vigente hoy» cambia al elegir fuente Binance → `captura fase-2 tasas` → exporta un respaldo y comprueba los conteos en la tabla. Read.
- [ ] **Paso 5: Commit.**

```bash
git add android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/ajustes/ android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/respaldo/RespaldoSeccion.kt android/app/src/main/java/com/cuentasclaras/app/ui/nav/CuentasNavHost.kt
git commit -m "feat(ajustes): cuentas, categorías con presupuesto, tasas, reglas y respaldo" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Tarea 17: Cierre de la fase 2 — auditoría visual, revisión y validación de Daniel

**Skills:** `redesign-skill` (`redesign-existing-projects`: auditoría de capturas buscando patrones genéricos de IA; SOLO arreglos que no toquen comportamiento) · `mattpocock-skills:code-review` + `superpowers:requesting-code-review` / `superpowers:receiving-code-review` · `superpowers:verification-before-completion`.

**Files:** solo los composables que la auditoría corrija (lista exacta en el reporte).

- [ ] **Paso 1: Pasada completa de capturas.** `instalar` → `sembrar …demo` → una captura por pantalla y estado relevante (Inicio arriba y abajo, Nuevo en los 4 modos, Movimientos con grupo abierto, editor, Ajustes por sección) en `.superpowers/sdd/capturas/fase-2/final-*.png`.
- [ ] **Paso 2: `redesign-skill`** sobre esas capturas contra `android/DESIGN.md`: consistencia entre pantallas, jerarquía, ritmo de espacios, contraste, cifras tabulares, textos cortados en pantallas angostas (`"$ADB" shell wm size 720x1520` para simular un teléfono pequeño y `wm size reset` al terminar). Aplica solo arreglos visuales; commit `style(ui): …` con la lista de archivos.
- [ ] **Paso 3: Revisión de código de la fase** (`mattpocock-skills:code-review`, desde el commit de cierre de la fase 1): Standards (restricciones 2–10, 13, 16, 18) y Spec (v1/v2/v3: Nuevo, Inicio, Movimientos, Ajustes). Recorre además: `grep -rn "txsEfectivas" android/app/src/main/java` → solo en `InicioViewModel`; `grep -rn "Color(0x" android/app/src/main/java/com/cuentasclaras/app/ui/pantallas` → vacío; `grep -rn "kotlin.math.round" android/app/src/main` → vacío.
- [ ] **Paso 4: APK y validación de Daniel (bloqueante).** Instala en su teléfono SIN sembrar (conserva sus datos reales de la fase 1). Pídele: registrar un gasto real en ≤ 3 toques desde Inicio, una transferencia, revisar que patrimonio y «Este mes» cuadran con lo que esperaba, y opinar del diseño. Lo que reporte se arregla antes de pasar a la fase 3 (`superpowers:systematic-debugging` para bugs).

---

## Fase 3 — Captura de notificaciones del BNC y Permisos

Entregable (spec §7.3): APK que Daniel valida en su Xiaomi con una notificación real del BNC (y con «Probar captura» mientras no llega ninguna). **Nunca se guarda un movimiento sin la confirmación de Daniel en Nuevo.**

### Tarea 18: Investigación del listener en MIUI/HyperOS, parser de notificaciones y repositorio de capturas

**Skills:** `mattpocock-skills:research` (agente en segundo plano con fuentes primarias) · `superpowers:test-driven-development` (`ParseNotificacionBncTest`) · `output-skill`.

**Files:**
- Create: `docs/research/2026-10-15-notification-listener-miui.md`
- Create: `android/app/src/main/java/com/cuentasclaras/app/capture/ConfigCaptura.kt`
- Create: `android/app/src/main/java/com/cuentasclaras/app/capture/ParseNotificacionBnc.kt`
- Create: `android/app/src/main/java/com/cuentasclaras/app/capture/CapturaRepo.kt`
- Create: `android/app/src/test/resources/capture/notificaciones-bnc.txt` (las dos fixtures de `src/capture/fixtures/notificacionesBnc.ts`, una por línea, tal cual)
- Test: `android/app/src/test/java/com/cuentasclaras/app/capture/ParseNotificacionBncTest.kt`

**Interfaces:**
- Consumes: T2 (`parseVenezuelanAmount`, `nuevoId`, `instanteIso`), T5 (`CapturaEntity`, `CapturaDao`, `EstadoCaptura`, `TipoCaptura`).
- Produces:
  - `const val PAQUETE_BNC = "bnc.bncnet.mobile2"` (constante ÚNICA; se confirma en la Tarea 22) y `fun paquetesCapturables(context: Context): Set<String>` (en builds depurables añade `com.android.shell` para las pruebas con `cmd notification post`).
  - `data class CapturaBnc(tipo: TipoCaptura, montoMinor: Long, nota: String, referencia: String)`; `fun normalizarTexto(texto: String): String`; `fun parseNotificacionBnc(texto: String): CapturaBnc?`.
  - `class CapturaRepo(db: CuentasDb)` con `val pendientes: Flow<List<CapturaEntity>>`, `suspend fun registrar(texto: String, ahora: Instant = Instant.now()): CapturaEntity?` (null si vacía o reemisión en 24 h), `suspend fun porId(id): CapturaEntity?`, `suspend fun descartar(id)`.

- [ ] **Paso 1: Investigación (en segundo plano mientras sigues).** Con `mattpocock-skills:research`, un agente responde contra developer.android.com, el código de AOSP y la documentación de Xiaomi/dontkillmyapp.com, y escribe `docs/research/2026-10-15-notification-listener-miui.md` con fuentes por afirmación: (1) ciclo de vida de `NotificationListenerService` (`onListenerConnected`/`onListenerDisconnected`, cuándo el sistema re-enlaza solo); (2) `requestRebind(ComponentName)` y el truco de alternar el componente con `setComponentEnabledSetting`: requisitos y efectos; (3) `POST_NOTIFICATIONS` con targetSdk 35 y el canal de importancia alta; (4) intents de MIUI/HyperOS para autoinicio (`com.miui.securitycenter/com.miui.permcenter.autostart.AutoStartManagementActivity`), ahorro de batería por app y bloqueo en recientes, y si son detectables; (5) si `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` es aceptable para una app que no va a Play Store; (6) sintaxis de `adb shell cmd notification post` para simular notificaciones. Si el hallazgo contradice algo de las Tareas 19–21, el implementador de esa tarea ajusta y lo declara en su reporte.

- [ ] **Paso 2: `ConfigCaptura.kt`.**

```kotlin
package com.cuentasclaras.app.capture

import android.content.Context
import android.content.pm.ApplicationInfo

/** Paquete de la app del BNC (sale de una captura de pantalla de Daniel). Constante ÚNICA:
 *  la Tarea 22 lo confirma con `adb shell pm list packages | grep -i bnc`. */
const val PAQUETE_BNC = "bnc.bncnet.mobile2"

/** En builds depurables también se escucha a `com.android.shell` para poder simular
 *  notificaciones con `adb shell cmd notification post`. En release, solo el BNC. */
fun paquetesCapturables(context: Context): Set<String> {
    val depurable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    return if (depurable) setOf(PAQUETE_BNC, "com.android.shell") else setOf(PAQUETE_BNC)
}
```

- [ ] **Paso 3: Test RED.** `ParseNotificacionBncTest.kt` (fixtures completas + Review Focus 3):

```kotlin
package com.cuentasclaras.app.capture

import com.cuentasclaras.app.data.db.TipoCaptura
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ParseNotificacionBncTest {
    private val fixtures = javaClass.getResource("/capture/notificaciones-bnc.txt")!!.readText().lines().filter { it.isNotBlank() }
    private val pagoMovil = fixtures[0]
    private val compra = fixtures[1]

    @Test fun `pago movil recibido es ingreso en Bs con referencia`() {
        assertEquals(CapturaBnc(TipoCaptura.INGRESO, 123456, "Pago móvil recibido · ref 123456789", "123456789"), parseNotificacionBnc(pagoMovil))
    }

    @Test fun `compra POS es gasto en Bs con referencia`() {
        assertEquals(CapturaBnc(TipoCaptura.GASTO, 789012, "Compra POS · ref 654321", "654321"), parseNotificacionBnc(compra))
    }

    @Test fun `monto sin miles con muchas cifras, saltos de linea, espacios no separables y mayusculas`() {
        assertEquals(25000000L, parseNotificacionBnc(pagoMovil.replace("Bs.1234,56", "Bs.250000,00"))?.montoMinor)
        assertEquals(123456L, parseNotificacionBnc(pagoMovil.replace(" Dia:", "\nDia:").replace(" Ref:", "\nRef:"))?.montoMinor)
        assertEquals(123456L, parseNotificacionBnc(pagoMovil.replace(" ", " "))?.montoMinor)
        assertEquals(123456L, parseNotificacionBnc(pagoMovil.replace(" Telf", " Telf"))?.montoMinor)
        assertEquals(123456L, parseNotificacionBnc(pagoMovil.uppercase())?.montoMinor)
    }

    @Test fun `textos no reconocidos devuelven null`() {
        assertNull(parseNotificacionBnc(""))
        assertNull(parseNotificacionBnc("hola mundo"))
        assertNull(parseNotificacionBnc("BNC te informa algo sin monto"))
    }
}
```

`notificaciones-bnc.txt` (copia exacta de las constantes de `src/capture/fixtures/notificacionesBnc.ts`, son INVENTADAS):

```text
BNC Pago Movil Recibido Bs.1234,56 Telf.0412***9999 Dia:15/07/26-10:31 Ref:123456789 Llamar al 0500-2625000 si no realizo esta Operacion
BNC Compra POS Bs.7890,12 Tarj:9999 Dia:15/07/26-12:45 Ref:654321 Llamar al 0500-2625000 si no realizo esta Operacion
```

- [ ] **Paso 4: Correr → FAIL** (`--tests "*ParseNotificacionBncTest"`).

- [ ] **Paso 5: `ParseNotificacionBnc.kt`.**

```kotlin
package com.cuentasclaras.app.capture

import com.cuentasclaras.app.data.db.TipoCaptura
import com.cuentasclaras.app.domain.parseVenezuelanAmount
import java.text.Normalizer

data class CapturaBnc(
    val tipo: TipoCaptura,
    /** Céntimos de Bs (las notificaciones del BNC siempre son en Bs). */
    val montoMinor: Long,
    /** Nota lista para el movimiento: tipo de operación + referencia. */
    val nota: String,
    /** Dígitos de «Ref:»; alimenta la deduplicación con el import del TXT (D8). */
    val referencia: String,
)

private data class Reconocedor(val tipo: TipoCaptura, val regex: Regex, val nota: (String) -> String)

// Estructura real (captura de Daniel 2026-07-22; valores enmascarados):
//   BNC Pago Movil Recibido Bs.<monto> Telf.04XX***NNNN Dia:dd/mm/aa-HH:MM Ref:<dígitos> Llamar al …
//   BNC Compra POS Bs.<monto> Tarj:<4 dígitos> Dia:dd/mm/aa-HH:MM Ref:<dígitos> Llamar al …
private val RECONOCEDORES = listOf(
    Reconocedor(
        TipoCaptura.INGRESO,
        Regex("""BNC Pago Movil Recibido Bs\.(\d+,\d{2})\b.*?\bRef:(\d+)""", RegexOption.IGNORE_CASE),
    ) { "Pago móvil recibido · ref $it" },
    Reconocedor(
        TipoCaptura.GASTO,
        Regex("""BNC Compra POS Bs\.(\d+,\d{2})\b.*?\bRef:(\d+)""", RegexOption.IGNORE_CASE),
    ) { "Compra POS · ref $it" },
)

// Tras NFKC, los espacios Unicode raros ya son U+0020; la clase explícita cubre los que NFKC
// no toca (U+2028/U+2029/U+FEFF). Sin (?U): el motor de regex de Android no lo garantiza.
private val ESPACIOS = Regex("[\\s\\u00A0\\u2007\\u202F\\u2028\\u2029\\uFEFF]+")

/** Mismo criterio que la web: NFKC + espacios colapsados + trim. También es la base del hash. */
fun normalizarTexto(texto: String): String =
    Normalizer.normalize(texto, Normalizer.Form.NFKC).replace(ESPACIOS, " ").trim()

/** Reconoce pago móvil recibido y compra con tarjeta del BNC. Cualquier otra cosa → null. */
fun parseNotificacionBnc(texto: String): CapturaBnc? {
    val limpio = normalizarTexto(texto)
    if (limpio.isEmpty()) return null
    for (r in RECONOCEDORES) {
        val m = r.regex.find(limpio) ?: continue
        val montoMinor = parseVenezuelanAmount(m.groupValues[1]) ?: continue
        if (montoMinor <= 0) continue
        val referencia = m.groupValues[2]
        return CapturaBnc(r.tipo, montoMinor, r.nota(referencia), referencia)
    }
    return null
}
```

- [ ] **Paso 6: Correr → PASS.**

- [ ] **Paso 7: `CapturaRepo.kt`** (sin tests: fuera de la lista de §9; se verifica en la Tarea 22 con reemisiones reales).

```kotlin
package com.cuentasclaras.app.capture

import androidx.room.withTransaction
import com.cuentasclaras.app.data.db.CapturaEntity
import com.cuentasclaras.app.data.db.CuentasDb
import com.cuentasclaras.app.data.db.EstadoCaptura
import com.cuentasclaras.app.domain.instanteIso
import com.cuentasclaras.app.domain.nuevoId
import kotlinx.coroutines.flow.Flow
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant

private fun sha256(texto: String): String =
    MessageDigest.getInstance("SHA-256").digest(texto.toByteArray()).joinToString("") { "%02x".format(it) }

class CapturaRepo(private val db: CuentasDb) {
    val pendientes: Flow<List<CapturaEntity>> = db.capturas().observarPendientes()

    /** Persiste la notificación ANTES de avisar (spec §3.8). Devuelve null si el texto está vacío o
     *  si la misma notificación ya se capturó en las últimas 24 h (el BNC y MIUI reemiten). */
    suspend fun registrar(texto: String, ahora: Instant = Instant.now()): CapturaEntity? {
        val limpio = normalizarTexto(texto)
        if (limpio.isEmpty()) return null
        val hash = sha256(limpio)
        return db.withTransaction {
            if (db.capturas().contarRecientes(hash, instanteIso(ahora.minus(Duration.ofHours(24)))) > 0) {
                null
            } else {
                val p = parseNotificacionBnc(limpio)
                val captura = CapturaEntity(
                    id = nuevoId(),
                    recibidaIso = instanteIso(ahora),
                    textoCrudo = texto.take(2000),
                    reconocida = p != null,
                    tipo = p?.tipo,
                    montoMinor = p?.montoMinor,
                    referencia = p?.referencia,
                    nota = p?.nota,
                    estado = EstadoCaptura.PENDIENTE,
                    hash = hash,
                    txId = null,
                )
                db.capturas().insertar(captura)
                captura
            }
        }
    }

    suspend fun porId(id: String): CapturaEntity? = db.capturas().porId(id)

    suspend fun descartar(id: String) = db.capturas().marcarDescartada(id)
}
```

- [ ] **Paso 8: Suite y commit.**

```bash
git add docs/research/2026-10-15-notification-listener-miui.md android/app/src/main/java/com/cuentasclaras/app/capture/ConfigCaptura.kt android/app/src/main/java/com/cuentasclaras/app/capture/ParseNotificacionBnc.kt android/app/src/main/java/com/cuentasclaras/app/capture/CapturaRepo.kt android/app/src/test/resources/capture/ android/app/src/test/java/com/cuentasclaras/app/capture/ParseNotificacionBncTest.kt
git commit -m "feat(captura): parser de notificaciones del BNC, repositorio con dedupe de 24 h e investigación MIUI" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Tarea 19: Servicio `NotificationListenerService`, notificación propia y re-enlace

**Skills:** `superpowers:systematic-debugging` (si el listener no dispara) · `mattpocock-skills:diagnosing-bugs` (si el fallo es intermitente) · `output-skill`.

**Files:**
- Create: `android/app/src/main/java/com/cuentasclaras/app/capture/BncNotificationListener.kt`
- Create: `android/app/src/main/java/com/cuentasclaras/app/capture/Notificador.kt`
- Create: `android/app/src/main/java/com/cuentasclaras/app/capture/EstadoListener.kt`
- Create: `android/app/src/main/res/drawable/ic_notificacion.xml` (vector monocromo, 24 dp)
- Modify: `android/app/src/main/AndroidManifest.xml` (permisos, servicio, `launchMode="singleTop"`)
- Modify: `android/app/src/main/java/com/cuentasclaras/app/CuentasApp.kt` (`capturas`, `estadoListener`; canal en `onCreate`)
- Modify: `android/app/src/main/java/com/cuentasclaras/app/MainActivity.kt` (re-enlace en `onResume`; intent de captura → Nuevo)

**Interfaces:**
- Consumes: T18 (`CapturaRepo`, `paquetesCapturables`), T11 (`Rutas.nuevo`), T2 (`formatMoney`).
- Produces: `object Notificador { const val CANAL; const val EXTRA_CAPTURA_ID = "capturaId"; fun crearCanal(ctx); fun publicar(ctx, c: CapturaEntity) }`; `class EstadoListener(context)` con `fun accesoConcedido(): Boolean`, `suspend fun latido()`, `suspend fun desconectado()`, `suspend fun reenlazarSiHaceFalta()`; `AppContainer.capturas: CapturaRepo`, `AppContainer.estadoListener: EstadoListener`; `MainActivity.capturaPedida: MutableStateFlow<String?>` consumido por `RaizApp`.

- [ ] **Paso 1: Manifiesto.** Añade:

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
```

En `<activity android:name=".MainActivity">` añade `android:launchMode="singleTop"`. Dentro de `<application>`:

```xml
<service
    android:name=".capture.BncNotificationListener"
    android:exported="true"
    android:label="@string/app_name"
    android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
    <intent-filter>
        <action android:name="android.service.notification.NotificationListenerService" />
    </intent-filter>
</service>
```

- [ ] **Paso 2: `EstadoListener.kt`.** El «latido» se actualiza al conectar y, como mucho cada 15 min, al ver cualquier notificación; si el acceso está concedido pero no hay latido en 6 h (o el servicio se declaró desconectado), se re-enlaza (spec §7.3, re-enlace).

```kotlin
package com.cuentasclaras.app.capture

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.almacenListener by preferencesDataStore(name = "estado_listener")
private val ULTIMO_LATIDO = longPreferencesKey("ultimoLatidoMillis")
private const val SIN_LATIDO_MAX_MS = 6 * 60 * 60 * 1000L

class EstadoListener(private val context: Context) {
    private val componente = ComponentName(context, BncNotificationListener::class.java)

    fun accesoConcedido(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    suspend fun latido() {
        context.almacenListener.edit { it[ULTIMO_LATIDO] = System.currentTimeMillis() }
    }

    suspend fun desconectado() {
        context.almacenListener.edit { it[ULTIMO_LATIDO] = 0L }
    }

    /** Llamado en MainActivity.onResume. Primer recurso: requestRebind; segundo: alternar el componente. */
    suspend fun reenlazarSiHaceFalta() {
        if (!accesoConcedido()) return
        val ultimo = context.almacenListener.data.first()[ULTIMO_LATIDO] ?: 0L
        if (ultimo != 0L && System.currentTimeMillis() - ultimo < SIN_LATIDO_MAX_MS) return
        Log.w("CuentasClaras", "Listener sin latido reciente: re-enlazando")
        NotificationListenerService.requestRebind(componente)
        val pm = context.packageManager
        pm.setComponentEnabledSetting(componente, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        pm.setComponentEnabledSetting(componente, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
    }
}
```

- [ ] **Paso 3: `BncNotificationListener.kt`.** Toda excepción se captura y registra (spec §8): el servicio nunca muere por un texto raro.

```kotlin
package com.cuentasclaras.app.capture

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.cuentasclaras.app.CuentasApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class BncNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var ultimoLatidoMs = 0L
    private val container get() = (application as CuentasApp).container

    override fun onListenerConnected() {
        scope.launch { runCatching { container.estadoListener.latido() } }
    }

    override fun onListenerDisconnected() {
        scope.launch { runCatching { container.estadoListener.desconectado() } }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            val ahora = System.currentTimeMillis()
            if (ahora - ultimoLatidoMs > 15 * 60 * 1000L) {
                ultimoLatidoMs = ahora
                scope.launch { runCatching { container.estadoListener.latido() } }
            }
            if (sbn.packageName !in paquetesCapturables(this)) return
            val extras = sbn.notification.extras
            val texto = listOfNotNull(
                extras.getCharSequence(Notification.EXTRA_TITLE),
                extras.getCharSequence(Notification.EXTRA_TEXT),
                extras.getCharSequence(Notification.EXTRA_BIG_TEXT),
            ).joinToString(" ")
            if (texto.isBlank()) return
            scope.launch {
                try {
                    val captura = container.capturas.registrar(texto) ?: return@launch
                    Notificador.publicar(this@BncNotificationListener, captura)
                } catch (e: Exception) {
                    Log.w("CuentasClaras", "No se pudo guardar la captura", e)
                }
            }
        } catch (e: Exception) {
            Log.w("CuentasClaras", "Notificación ignorada por error", e)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
```

- [ ] **Paso 4: `Notificador.kt`.**

```kotlin
package com.cuentasclaras.app.capture

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.cuentasclaras.app.MainActivity
import com.cuentasclaras.app.R
import com.cuentasclaras.app.data.db.CapturaEntity
import com.cuentasclaras.app.data.db.TipoCaptura
import com.cuentasclaras.app.domain.Currency
import com.cuentasclaras.app.domain.formatMoney

object Notificador {
    const val CANAL = "capturas_bnc"
    const val EXTRA_CAPTURA_ID = "capturaId"

    fun crearCanal(ctx: Context) {
        val canal = NotificationChannelCompat.Builder(CANAL, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName("Capturas BNC")
            .setDescription("Pagos y compras del BNC listos para registrar")
            .build()
        NotificationManagerCompat.from(ctx).createNotificationChannel(canal)
    }

    fun titulo(c: CapturaEntity): String {
        val monto = c.montoMinor
        return when {
            !c.reconocida || monto == null -> "Notificación del BNC sin reconocer"
            c.tipo == TipoCaptura.INGRESO -> "Pago móvil recibido ${formatMoney(monto, Currency.VES)}"
            else -> "Compra POS ${formatMoney(monto, Currency.VES)}"
        }
    }

    /** Sin permiso de notificaciones no se publica nada, pero la captura YA está en Room y
     *  aparece en la tarjeta «Capturas pendientes» de Inicio. */
    fun publicar(ctx: Context, c: CapturaEntity) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val intent = Intent(ctx, MainActivity::class.java).apply {
            putExtra(EXTRA_CAPTURA_ID, c.id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendiente = PendingIntent.getActivity(
            ctx, c.id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notificacion = NotificationCompat.Builder(ctx, CANAL)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle(titulo(c))
            .setContentText("Toca para registrar")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendiente)
            .build()
        NotificationManagerCompat.from(ctx).notify(c.id.hashCode(), notificacion)
    }
}
```

- [ ] **Paso 5: Contenedor y actividad.** En `AppContainer`: `val capturas = CapturaRepo(db)` y `val estadoListener = EstadoListener(context)`; en `CuentasApp.onCreate`: `Notificador.crearCanal(this)`. En `MainActivity`:
  - `val capturaPedida = MutableStateFlow<String?>(null)`; en `onCreate` y en `onNewIntent(intent)` (con `setIntent(intent)`): `intent.getStringExtra(Notificador.EXTRA_CAPTURA_ID)?.let { capturaPedida.value = it }`.
  - `override fun onResume() { super.onResume(); lifecycleScope.launch { runCatching { container.estadoListener.reenlazarSiHaceFalta() } } }`.
  - En `RaizApp`, pasa `capturaPedida` y: `LaunchedEffect(Unit) { capturaPedida.filterNotNull().collect { id -> nav.navigate(Rutas.nuevo(capturaId = id)); capturaPedida.value = null } }`.

- [ ] **Paso 6: Verificación del servicio (sin el BNC todavía).** `instalar`. Concede el acceso a mano: `"$ADB" shell cmd notification allow_listener com.cuentasclaras.app/com.cuentasclaras.app.capture.BncNotificationListener` y `"$ADB" shell pm grant com.cuentasclaras.app android.permission.POST_NOTIFICATIONS`. Simula una notificación (sintaxis confirmada por la investigación de la Tarea 18; la habitual es):

```bash
"$ADB" shell "cmd notification post -S bigtext -t 'BNC' prueba1 'BNC Pago Movil Recibido Bs.1234,56 Telf.0412***9999 Dia:15/07/26-10:31 Ref:123456789 Llamar al 0500-2625000 si no realizo esta Operacion'"
```

Expected: aparece la notificación propia «Pago móvil recibido Bs 1.234,56 — Toca para registrar» → `captura fase-3 notificacion-propia`. Reenvía el MISMO texto con otra etiqueta (`prueba2`) → NO aparece una segunda notificación propia (dedupe 24 h; Review Focus 5). Tócala (`"$ADB" shell cmd statusbar expand-notifications` y `tocar "Pago móvil recibido Bs 1.234,56"`) → abre la app en Nuevo (pre-llenado llega en la Tarea 20). Logs: `"$ADB" logcat -d -s CuentasClaras` sin excepciones.

- [ ] **Paso 7: Commit.**

```bash
git add android/app/src/main/java/com/cuentasclaras/app/capture/BncNotificationListener.kt android/app/src/main/java/com/cuentasclaras/app/capture/Notificador.kt android/app/src/main/java/com/cuentasclaras/app/capture/EstadoListener.kt android/app/src/main/res/drawable/ic_notificacion.xml android/app/src/main/AndroidManifest.xml android/app/src/main/java/com/cuentasclaras/app/CuentasApp.kt android/app/src/main/java/com/cuentasclaras/app/MainActivity.kt
git commit -m "feat(captura): listener de notificaciones del BNC, notificación propia y re-enlace" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Tarea 20: Nuevo desde una captura y tarjeta «Capturas pendientes» en Inicio

**Skills:** `ui-ux-pro-max:ui-ux-pro-max` · `output-skill`.

**Files:**
- Modify: `android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/nuevo/NuevoViewModel.kt`
- Modify: `android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/nuevo/NuevoPantalla.kt`
- Modify: `android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/inicio/InicioViewModel.kt`
- Modify: `android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/inicio/InicioPantalla.kt`
- Create: `android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/inicio/CapturasTarjeta.kt`
- Modify: `android/app/src/main/java/com/cuentasclaras/app/ui/nav/CuentasNavHost.kt` (los VMs de Nuevo e Inicio reciben `c.capturas`)

Fuente: `src/ui/paginas/CapturaPage.tsx` (prefill; sin reconocer → Nuevo en blanco con el texto crudo en la nota, máx. 200) y spec §7.3.

**Interfaces:**
- Consumes: T18 (`CapturaRepo`), T19, T12, T13.
- Produces: `NuevoViewModel(repo, capturas: CapturaRepo, args)` con `descartarCaptura()`; registrar marca la captura `REGISTRADA` con `txId` **en la misma transacción** que inserta el movimiento; `InicioViewModel(repo, capturas)` con `Estado.capturasPendientes: List<CapturaEntity>` y `descartarCaptura(id)`.

- [ ] **Paso 1: `NuevoViewModel` con `capturaId`.** Cambia la firma a `NuevoViewModel(private val repo: CuentasRepo, private val capturas: CapturaRepo, args: SavedStateHandle)`; añade `val capturaId: String? = args.get("capturaId")` y en `init` (antes de leer `lastAccountId`):

```kotlin
        capturaId?.let { id ->
            viewModelScope.launch {
                val c = capturas.porId(id)
                when {
                    c == null -> Avisos.avisar("La captura ya no existe")
                    c.estado != EstadoCaptura.PENDIENTE -> {
                        Avisos.avisar(if (c.estado == EstadoCaptura.REGISTRADA) "Esta notificación ya se registró" else "Esta notificación se descartó")
                        _salir.send(Unit)
                    }
                    c.reconocida && c.montoMinor != null -> formulario.update {
                        it.copy(
                            modo = if (c.tipo == TipoCaptura.INGRESO) Modo.INGRESO else Modo.GASTO,
                            entrada = montoEditable(c.montoMinor),
                            nota = c.nota ?: "",
                            cuentaId = "acc-bnc",
                        )
                    }
                    else -> formulario.update { it.copy(modo = Modo.GASTO, nota = c.textoCrudo.take(200)) }
                }
            }
        }
```

En `guardar`, reemplaza `repo.agregarTx(tx)` por:

```kotlin
                val id = capturaId
                if (id == null) {
                    repo.agregarTx(tx)
                } else {
                    // Todo-o-nada: el movimiento y la marca de la captura van juntos (nunca registrada sin tx).
                    repo.db.withTransaction {
                        repo.agregarTx(tx)
                        repo.db.capturas().marcarRegistrada(id, tx.id)
                    }
                }
```

Y añade:

```kotlin
    fun descartarCaptura() {
        val id = capturaId ?: return
        if (formulario.value.guardando) return
        viewModelScope.launch {
            capturas.descartar(id)
            Avisos.avisar("Notificación descartada")
            _salir.send(Unit)
        }
    }
```

Imports nuevos: `androidx.room.withTransaction`, `com.cuentasclaras.app.capture.CapturaRepo`, `com.cuentasclaras.app.data.db.EstadoCaptura`, `com.cuentasclaras.app.data.db.TipoCaptura`.

- [ ] **Paso 2: `NuevoPantalla`:** si `vm.capturaId != null`, muestra arriba un `Banner(OK)` «Desde una notificación del BNC» y un botón «Descartar» (`enabled = !guardando`) que llama `vm.descartarCaptura()`. Flujo en 2 toques: tocar la notificación → tocar la categoría.

- [ ] **Paso 3: Inicio.** `InicioViewModel(repo, capturas)`: combina también `capturas.pendientes` y expone `capturasPendientes`; `fun descartarCaptura(id: String) = viewModelScope.launch { capturas.descartar(id) }`. `CapturasTarjeta` (solo si `n > 0`, primera tarjeta de Inicio): «Capturas pendientes (n)» y por cada una `Notificador.titulo(c)` + hora local de `recibidaIso` (`Instant.parse(it).atZone(ZoneId.systemDefault())` formateada `dd/MM HH:mm`; es un instante, no una fecha de calendario) + «Registrar» (`nav.navigate(Rutas.nuevo(capturaId = c.id))`) y «Descartar». Banner de acceso perdido: en `InicioPantalla`, `var acceso by remember { mutableStateOf(true) }` + `LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { acceso = container.estadoListener.accesoConcedido() }`; si `!acceso`: `Banner(ERROR)` «La app perdió el acceso a notificaciones» con acción «Arreglar» → `nav.navigate(Rutas.PERMISOS)`.

- [ ] **Paso 4: Verificación.** `instalar` → simula una notificación nueva (texto con otra `Ref:` para que no sea reemisión) → toca la notificación propia → `captura fase-3 nuevo-desde-captura` (Ingreso, `Bs 1.234,56`, BNC, nota con la ref) → `tocar "Pago móvil recibido"` (categoría) → vuelve a Inicio sin la captura pendiente. Simula otra y **no** la toques: `reiniciar` (force-stop + abrir) → la tarjeta «Capturas pendientes (1)» sigue ahí (Review Focus 5) → `captura fase-3 pendiente-tras-reinicio` → «Descartar» → desaparece. Toca de nuevo la notificación ya registrada (si sigue en la bandeja) → aviso «Esta notificación ya se registró» y vuelta a Inicio, sin segundo movimiento. Simula un texto no reconocido (`'BNC Aviso de prueba sin monto'`) → Nuevo en blanco con ese texto en la nota. Revoca el acceso (`cmd notification disallow_listener …`) → banner rojo en Inicio → `captura fase-3 banner-acceso`. Read.

- [ ] **Paso 5: Commit.**

```bash
git add android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/nuevo/NuevoViewModel.kt android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/nuevo/NuevoPantalla.kt android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/inicio/ android/app/src/main/java/com/cuentasclaras/app/ui/nav/CuentasNavHost.kt
git commit -m "feat(captura): registrar o descartar capturas desde Nuevo e Inicio, con aviso de acceso perdido" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Tarea 21: Pantalla Permisos (defensas Xiaomi) y guía

**Skills:** `ui-ux-pro-max:ui-ux-pro-max` (lista de verificación con estados) · `output-skill`.

**Files:**
- Create: `android/app/src/main/java/com/cuentasclaras/app/capture/PermisosXiaomi.kt`
- Create: `android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/permisos/PermisosPantalla.kt`
- Create: `android/app/src/main/res/drawable/esquema_bloquear_recientes.xml` (vector simple: tarjeta de recientes con un candado)
- Modify: `android/app/src/main/java/com/cuentasclaras/app/ui/nav/CuentasNavHost.kt` (línea de `PERMISOS`)
- Modify: `android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/ajustes/AjustesPantalla.kt` (la fila «Permisos de captura» navega a `Rutas.PERMISOS`)
- Create: `docs/guia-permisos.md`
- Modify: `docs/guia-macrodroid.md` (una línea al inicio: «Obsoleta para la app nativa: ver `guia-permisos.md`.»)

**Interfaces:**
- Consumes: T19 (`EstadoListener.accesoConcedido`), T18 (`parseNotificacionBnc`), T10 (componentes).
- Produces: `object PermisosXiaomi { fun bateriaSinRestricciones(ctx): Boolean; fun notificacionesPermitidas(ctx): Boolean; fun abrirAccesoNotificaciones(ctx); fun abrirBateria(ctx); fun abrirAutoinicio(ctx): Boolean }`.

- [ ] **Paso 1: `PermisosXiaomi.kt`.**

```kotlin
package com.cuentasclaras.app.capture

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

object PermisosXiaomi {
    fun bateriaSinRestricciones(ctx: Context): Boolean =
        (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)

    fun notificacionesPermitidas(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun abrirAccesoNotificaciones(ctx: Context) {
        ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    @SuppressLint("BatteryLife") // App personal fuera de Play Store: pedirlo directamente es lo correcto aquí.
    fun abrirBateria(ctx: Context) {
        val directo = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
        try {
            ctx.startActivity(directo.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            ctx.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /** Best-effort: la pantalla de autoinicio de MIUI. false si no existe (la UI muestra instrucciones). */
    fun abrirAutoinicio(ctx: Context): Boolean = try {
        ctx.startActivity(
            Intent().setComponent(
                ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (e: Exception) {
        false
    }
}
```

- [ ] **Paso 2: `PermisosPantalla.kt`** (spec §7.3, lista de verificación; los estados se releen en cada `ON_RESUME` con `LifecycleEventEffect`):
  1. «Acceso a notificaciones» — estado detectado (✓ concedido / ✗ falta) + «Abrir ajuste».
  2. «Batería sin restricciones» — detectado con `bateriaSinRestricciones` + «Abrir ajuste». Texto de ayuda: «En Xiaomi elige *Sin restricciones* en Ahorro de batería».
  3. «Autoinicio (Xiaomi)» — «no detectable» + «Abrir ajuste» (si `abrirAutoinicio` devuelve false: aviso «Ábrelo a mano: Seguridad → Permisos → Autoinicio → Cuentas Claras»).
  4. «Bloquear la app en Recientes» — «no detectable» + instrucciones («Abre Recientes, mantén pulsada Cuentas Claras y toca el candado») + `Image(painterResource(R.drawable.esquema_bloquear_recientes))`.
  5. «Permiso para avisarte» (`POST_NOTIFICATIONS`, Android 13+) — detectado + botón que lanza `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())`.
  6. «Probar captura» — `CampoTexto` multilínea «Pega aquí una notificación del BNC» + botón «Probar» → muestra «Reconocida: Ingreso/Gasto · {formatMoney(monto, VES)} · {nota}» o «No reconocida». No guarda nada.
- [ ] **Paso 3: Ajustes** — la fila «Permisos de captura» deja de estar deshabilitada y navega a `Rutas.PERMISOS`; monta `PERMISOS → PermisosPantalla(nav)`.
- [ ] **Paso 4: `docs/guia-permisos.md`** — guía en español para Daniel: qué hace la captura, los 5 ajustes en su Xiaomi con la ruta exacta de menús de MIUI/HyperOS (de la investigación de la Tarea 18), cómo saber que funciona («Probar captura» y el banner de Inicio) y qué hacer si deja de capturar. Sin datos reales.
- [ ] **Paso 5: Verificación.** `instalar` → `tocar "Ajustes"` → `tocar "Permisos de captura"` → `captura fase-3 permisos` → «Probar captura» con la fixture de compra POS → `captura fase-3 probar-captura` (Gasto · Bs 7.890,12) → abre cada ajuste y vuelve atrás comprobando que el estado se refresca. Read.
- [ ] **Paso 6: Commit.**

```bash
git add android/app/src/main/java/com/cuentasclaras/app/capture/PermisosXiaomi.kt android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/permisos/ android/app/src/main/res/drawable/esquema_bloquear_recientes.xml android/app/src/main/java/com/cuentasclaras/app/ui/nav/CuentasNavHost.kt android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/ajustes/AjustesPantalla.kt docs/guia-permisos.md docs/guia-macrodroid.md
git commit -m "feat(permisos): lista de verificación con defensas de Xiaomi, probador de captura y guía" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Tarea 22: Cierre de la fase 3 — paquete del BNC, notificación real y revisión

**Skills:** `mattpocock-skills:wizard` (pasos de Daniel en el Xiaomi) · `superpowers:systematic-debugging` · `mattpocock-skills:code-review` + `superpowers:requesting-code-review` · `superpowers:verification-before-completion`.

**Files:** posible `Modify: android/app/src/main/java/com/cuentasclaras/app/capture/ConfigCaptura.kt` (solo `PAQUETE_BNC` si difiere).

- [ ] **Paso 1: Confirmar el paquete del BNC (bloqueante).** Con el Xiaomi conectado: `"$ADB" shell pm list packages | grep -i bnc`. Si sale exactamente `package:bnc.bncnet.mobile2`, no hay cambio. Si sale otro, pregunta a Daniel con `AskUserQuestion` cuál es la app del BNC (opciones = paquetes listados), actualiza SOLO `PAQUETE_BNC` y commitea `fix(captura): paquete real del BNC`.
- [ ] **Paso 2: Wizard de permisos para Daniel.** Con `mattpocock-skills:wizard`, guíalo por la pantalla Permisos de la app (los 5 ajustes) hasta que todos los detectables estén en ✓ y haya hecho autoinicio y bloqueo en recientes. `captura fase-3 permisos-daniel`.
- [ ] **Paso 3: Notificación real (bloqueante, con Daniel).** Pídele que reciba un pago móvil real (o una compra POS) y te diga qué vio: notificación propia → tocarla → Nuevo pre-llenado → categoría → registrado. Si no llega la notificación propia: `"$ADB" logcat -d -s CuentasClaras` y `superpowers:systematic-debugging` (¿paquete?, ¿formato del texto distinto? — compara con «Probar captura» pegando el texto que Daniel ve, sin guardarlo en ningún archivo).
- [ ] **Paso 4: Supervivencia (Review Focus 5).** Con permisos dados: `"$ADB" shell am kill com.cuentasclaras.app` y `reiniciar`; simula una notificación con `cmd notification post` → se captura igual. Pide a Daniel dejar el teléfono 1 hora con la pantalla apagada y luego simula otra → se captura. Si MIUI mató el listener, revisa el re-enlace (`EstadoListener`) con `mattpocock-skills:diagnosing-bugs`.
- [ ] **Paso 5: Revisión de la fase** (`mattpocock-skills:code-review` desde el cierre de la fase 2): Standards (restricciones 9, 10, 15; spec §8: el listener nunca muere) y Spec (§7.3 completo). Aplica hallazgos.
- [ ] **Paso 6:** Fase 3 cerrada solo cuando Daniel confirme «registré un pago real desde la notificación en 2 toques».

---

## Fase 4 — Import del TXT del BNC

Entregable (spec §7.4): APK donde Daniel importa su TXT mensual del BNC sin duplicar lo que ya registró por captura. La pantalla Importar solo ofrece BNC (sin Zinli, decisión cerrada).

### Tarea 23: `parseBncTxt` y `mapBncRows` con deduplicación por importKey y por captura

**Skills:** `superpowers:test-driven-development` (`ParseBncTxtTest`, `MapBncTest`) · `output-skill`.

**Files:**
- Create: `android/app/src/main/java/com/cuentasclaras/app/importar/bnc/ParseBncTxt.kt`
- Create: `android/app/src/main/java/com/cuentasclaras/app/importar/bnc/MapBnc.kt`
- Create (generado, ver Paso 1): `android/app/src/test/resources/importar/bnc-fixture.txt`
- Test: `android/app/src/test/java/com/cuentasclaras/app/importar/ParseBncTxtTest.kt`
- Test: `android/app/src/test/java/com/cuentasclaras/app/importar/MapBncTest.kt`

Fuentes: `src/import/bnc/parseBncTxt.ts`, `mapBnc.ts`, sus tests y `fixtures/bncFixture.ts`.

**Interfaces:**
- Consumes: T2 (`parseVenezuelanAmount`, `isoFromDdMmYyyy`, `nuevoId`), T3 (`matchCategory`), T5 (`ReferenciaCapturada`).
- Produces: `data class BncMeta`, `data class BncRow(date, time, referencia, codTransaccion, tipoTransaccion, tipOperacion, descripcion, amountMinor: Long, saldoMinor: Long, referencia2)`, `data class BncUnparsed(lineNumber, raw, reason)`, `data class BncParseResult(meta, rows, unparsed)`, `parseBncTxt(text)`, `validateChain(rows): List<Int>`; `bncImportKey(row)`, `normalizarReferencia(ref)`, `data class BncDraft(tx, saldoMinor, duplicate, motivoDuplicado: String?, isCommission, pairRef: String?, matchedRuleId: String?)`, `MOTIVO_YA_IMPORTADO`, `MOTIVO_POR_CAPTURA`, `mapBncRows(rows, accountId, existingKeys, rules, categories, capturadas: List<ReferenciaCapturada> = emptyList())`, `enum class GapStatus { PRIMERA_IMPORTACION, SOLAPADO, CONTINUO, HUECO }`, `detectGap(rows, previoImportKey: String?, previoSaldoMinor: Long?)`, `newestRow(rows): BncRow?`.

- [ ] **Paso 1: Fixture byte a byte.** La fixture lleva tabuladores y `\r\n` que se pierden al copiar a mano; genérala desde la fuente TypeScript (Node ≥ 23.6 ejecuta `.ts` sin flags):

```bash
mkdir -p android/app/src/test/resources/importar
node --input-type=module -e "import { BNC_FIXTURE } from './src/import/bnc/fixtures/bncFixture.ts'; import { writeFileSync } from 'node:fs'; writeFileSync('android/app/src/test/resources/importar/bnc-fixture.txt', BNC_FIXTURE);"
grep -c $'\t' android/app/src/test/resources/importar/bnc-fixture.txt   # Expected: 11 (cabecera + Saldo Inicial + 9 filas)
file android/app/src/test/resources/importar/bnc-fixture.txt            # Expected: … with CRLF line terminators
```

Si tu Node no ejecuta `.ts`, usa `node --experimental-strip-types …` con el mismo código. Es la fixture anonimizada (datos inventados); nunca la sustituyas por un TXT real.

- [ ] **Paso 2: Tests RED.** `ParseBncTxtTest.kt`:

```kotlin
package com.cuentasclaras.app.importar

import com.cuentasclaras.app.importar.bnc.BncMeta
import com.cuentasclaras.app.importar.bnc.parseBncTxt
import com.cuentasclaras.app.importar.bnc.validateChain
import kotlin.test.Test
import kotlin.test.assertEquals

class ParseBncTxtTest {
    private val fixture = javaClass.getResource("/importar/bnc-fixture.txt")!!.readText()

    @Test fun `metadatos y 9 filas, sin cabecera ni Saldo Inicial, en orden de archivo`() {
        val r = parseBncTxt(fixture)
        assertEquals(BncMeta("01910000000000001234", "PEREZ RODRIGUEZ MARIA JOSE", "25 ULTIMOS MOVIMIENTOS"), r.meta)
        assertEquals(9, r.rows.size)
        assertEquals(0, r.unparsed.size)
        val primera = r.rows[0]
        assertEquals("2026-07-02", primera.date)
        assertEquals("16:20:30.900", primera.time)
        assertEquals("100000007", primera.referencia)
        assertEquals(150000L, primera.amountMinor)
        assertEquals(630001L, primera.saldoMinor)
    }

    @Test fun `debe y haber con signo, artefacto float del saldo y tipo de transaccion vacio`() {
        val r = parseBncTxt(fixture)
        assertEquals(-34579L, r.rows[1].amountMinor)
        assertEquals(480001L, r.rows[1].saldoMinor)
        assertEquals(-10L, r.rows[2].amountMinor)
        assertEquals("", r.rows[1].tipoTransaccion)
    }

    @Test fun `una linea corrupta va a unparsed sin tumbar el resto y la cadena de saldos cuadra`() {
        val roto = "$fixture\r\nesto no es una fila\r\n03/07/2026\t99:99\tx\t1\t2\t3\t4\tno-numero\t0\t0\t0"
        val r = parseBncTxt(roto)
        assertEquals(9, r.rows.size)
        assertEquals(2, r.unparsed.size)
        assertEquals("esto no es una fila", r.unparsed[0].raw)
        assertEquals(emptyList(), validateChain(parseBncTxt(fixture).rows))
        val alteradas = parseBncTxt(fixture).rows.mapIndexed { i, x -> if (i == 3) x.copy(saldoMinor = x.saldoMinor + 100) else x }
        assertEquals(listOf(2, 3), validateChain(alteradas))
    }
}
```

`MapBncTest.kt` (incluye la deduplicación nueva por captura y el Review Focus 4):

```kotlin
package com.cuentasclaras.app.importar

import com.cuentasclaras.app.data.db.ReferenciaCapturada
import com.cuentasclaras.app.domain.Category
import com.cuentasclaras.app.domain.CategoryKind
import com.cuentasclaras.app.domain.Rule
import com.cuentasclaras.app.domain.TxType
import com.cuentasclaras.app.importar.bnc.GapStatus
import com.cuentasclaras.app.importar.bnc.MOTIVO_POR_CAPTURA
import com.cuentasclaras.app.importar.bnc.MOTIVO_YA_IMPORTADO
import com.cuentasclaras.app.importar.bnc.bncImportKey
import com.cuentasclaras.app.importar.bnc.detectGap
import com.cuentasclaras.app.importar.bnc.mapBncRows
import com.cuentasclaras.app.importar.bnc.parseBncTxt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class MapBncTest {
    private val rows = parseBncTxt(javaClass.getResource("/importar/bnc-fixture.txt")!!.readText()).rows
    private val categorias = listOf(
        Category("cat-comisiones", "Comisiones bancarias", "🏦", "#000", CategoryKind.EXPENSE),
        Category("cat-ocio", "Ocio/Juegos", "🎮", "#000", CategoryKind.EXPENSE),
        Category("cat-pago-movil-recibido", "Pago móvil recibido", "📲", "#000", CategoryKind.INCOME),
    )
    private val reglas = listOf(Rule("r1", "JUEGAENLINEA", "cat-ocio", true))
    private fun mapear(existentes: Set<String> = emptySet(), capturadas: List<ReferenciaCapturada> = emptyList()) =
        mapBncRows(rows, "acc-bnc", existentes, reglas, categorias, capturadas)

    @Test fun `mapea a borradores con importKey que incluye la fecha y propone categorias`() {
        val d = mapear()
        assertEquals(9, d.size)
        assertEquals(TxType.INCOME, d[0].tx.type)
        assertEquals("bnc:2026-07-02:100000007:16:20:30.900:150000", d[0].tx.importKey)
        assertEquals(34579L, d[1].tx.amountMinor)
        assertEquals("cat-pago-movil-recibido", d[0].tx.categoryId)
        assertEquals("cat-comisiones", d[2].tx.categoryId)
        assertEquals("cat-ocio", d[6].tx.categoryId)
        assertEquals("r1", d[6].matchedRuleId)
        assertNull(d[1].tx.categoryId)
        assertEquals(2, d.count { it.pairRef == "100000004" })
        val junio = rows.first { it.referencia == "0" }
        assertNotEquals(bncImportKey(junio), bncImportKey(junio.copy(date = "2026-07-30")))
    }

    @Test fun `duplicados por importKey ya existente`() {
        val d = mapear(existentes = setOf(bncImportKey(rows[0]), bncImportKey(rows[3])))
        assertEquals(2, d.count { it.duplicate })
        assertEquals(MOTIVO_YA_IMPORTADO, d[0].motivoDuplicado)
    }

    @Test fun `fila ya registrada por captura aunque cambien los ceros, pero no su comision`() {
        val d = mapear(capturadas = listOf(
            ReferenciaCapturada("0100000007", 150000),   // abono pago móvil, ref con un cero de más
            ReferenciaCapturada("100000004", 120000),    // cargo de 1.200,00; su comisión (3,60) comparte ref
        ))
        assertEquals(MOTIVO_POR_CAPTURA, d[0].motivoDuplicado)
        assertEquals(true, d[0].duplicate)
        val cargo = d.first { it.pairRef == "100000004" && it.tx.amountMinor == 120000L }
        val comision = d.first { it.pairRef == "100000004" && it.tx.amountMinor == 360L }
        assertEquals(true, cargo.duplicate)
        assertEquals(false, comision.duplicate)
        assertEquals(false, d.first { it.tx.amountMinor == 10L }.duplicate) // ref "0" nunca coincide
    }

    @Test fun `detectGap distingue los cuatro estados`() {
        assertEquals(GapStatus.PRIMERA_IMPORTACION, detectGap(rows, null, null))
        assertEquals(GapStatus.SOLAPADO, detectGap(rows, bncImportKey(rows[4]), 999))
        assertEquals(GapStatus.CONTINUO, detectGap(rows, "bnc:viejo:x:1", 50000))
        assertEquals(GapStatus.HUECO, detectGap(rows, "bnc:viejo:x:1", 123))
    }
}
```

- [ ] **Paso 3: Correr → FAIL** (`--tests "*ParseBncTxtTest" --tests "*MapBncTest"`).

- [ ] **Paso 4: `ParseBncTxt.kt`.**

```kotlin
package com.cuentasclaras.app.importar.bnc

import com.cuentasclaras.app.domain.isoFromDdMmYyyy
import com.cuentasclaras.app.domain.parseVenezuelanAmount

data class BncMeta(val accountNumber: String, val holder: String, val title: String)

data class BncRow(
    val date: String,
    val time: String,
    val referencia: String,
    val codTransaccion: String,
    val tipoTransaccion: String,
    val tipOperacion: String,
    val descripcion: String,
    /** Debe + Haber con signo, en céntimos de Bs. */
    val amountMinor: Long,
    val saldoMinor: Long,
    val referencia2: String,
)

data class BncUnparsed(val lineNumber: Int, val raw: String, val reason: String)

data class BncParseResult(val meta: BncMeta?, val rows: List<BncRow>, val unparsed: List<BncUnparsed>)

private val HORA_RE = Regex("""^\d{2}:\d{2}:\d{2}(\.\d+)?$""")
private val SALTO = Regex("\r?\n")

fun parseBncTxt(text: String): BncParseResult {
    var meta: BncMeta? = null
    val rows = mutableListOf<BncRow>()
    val unparsed = mutableListOf<BncUnparsed>()

    text.removePrefix("﻿").split(SALTO).forEachIndexed { idx, linea ->
        val lineNumber = idx + 1
        val raw = linea.trimEnd()
        if (raw.isBlank()) return@forEachIndexed

        // Metadatos: única línea sin tabs con separador '@'
        if (!raw.contains('\t') && raw.contains('@')) {
            val partes = raw.split('@')
            if (partes.size >= 3 && meta == null) {
                meta = BncMeta(partes[0], partes[1], partes.drop(2).joinToString("@"))
            } else {
                unparsed.add(BncUnparsed(lineNumber, raw, "metadatos irreconocibles"))
            }
            return@forEachIndexed
        }

        if (raw.startsWith("Fecha\t")) return@forEachIndexed // cabecera

        val f = raw.split('\t')
        if (f.size < 10) {
            unparsed.add(BncUnparsed(lineNumber, raw, "columnas insuficientes (${f.size})"))
            return@forEachIndexed
        }
        val fecha = f[0]
        val hora = f[1]
        val referencia = f[2]
        val tipOperacion = f[5]
        val debe = f[7]
        val haber = f[8]
        val saldo = f[9]

        // Pseudo-fila "Saldo Inicial": se reconoce y se descarta (semántica no confiable)
        if (tipOperacion.trim() == "Saldo Inicial" && referencia.trim().isEmpty()) return@forEachIndexed

        val date = isoFromDdMmYyyy(fecha)
        if (date == null) {
            unparsed.add(BncUnparsed(lineNumber, raw, "fecha inválida: \"$fecha\""))
            return@forEachIndexed
        }
        if (!HORA_RE.matches(hora.trim())) {
            unparsed.add(BncUnparsed(lineNumber, raw, "hora inválida: \"$hora\""))
            return@forEachIndexed
        }
        val debeMinor = parseVenezuelanAmount(if (debe.isBlank()) "0" else debe)
        val haberMinor = parseVenezuelanAmount(if (haber.isBlank()) "0" else haber)
        val saldoMinor = parseVenezuelanAmount(saldo)
        if (debeMinor == null || haberMinor == null || saldoMinor == null) {
            unparsed.add(BncUnparsed(lineNumber, raw, "monto ilegible"))
            return@forEachIndexed
        }

        rows.add(
            BncRow(
                date = date,
                time = hora.trim(),
                referencia = referencia.trim(),
                codTransaccion = f[3].trim(),
                tipoTransaccion = f[4].trim(),
                tipOperacion = tipOperacion.trim(),
                descripcion = f[6].trim(),
                amountMinor = debeMinor + haberMinor,
                saldoMinor = saldoMinor,
                referencia2 = f.getOrElse(10) { "" }.trim(),
            ),
        )
    }
    return BncParseResult(meta, rows, unparsed)
}

/** Con filas en orden de archivo (descendente), verifica saldo[i] − monto[i] == saldo[i+1].
 *  Devuelve los índices i donde NO cuadra. */
fun validateChain(rows: List<BncRow>): List<Int> =
    (0 until rows.size - 1).filter { i -> rows[i].saldoMinor - rows[i].amountMinor != rows[i + 1].saldoMinor }
```

- [ ] **Paso 5: `MapBnc.kt`.**

```kotlin
package com.cuentasclaras.app.importar.bnc

import com.cuentasclaras.app.data.db.ReferenciaCapturada
import com.cuentasclaras.app.domain.Category
import com.cuentasclaras.app.domain.Rule
import com.cuentasclaras.app.domain.Transaction
import com.cuentasclaras.app.domain.TxSource
import com.cuentasclaras.app.domain.TxType
import com.cuentasclaras.app.domain.matchCategory
import com.cuentasclaras.app.domain.nuevoId
import kotlin.math.abs

/** Incluye la fecha: comisiones recurrentes idénticas de distinto mes no colisionan. */
fun bncImportKey(row: BncRow): String = "bnc:${row.date}:${row.referencia}:${row.time}:${row.amountMinor}"

const val MOTIVO_YA_IMPORTADO = "ya importado"
const val MOTIVO_POR_CAPTURA = "ya registrada por captura"

/** "0100000007" y "100000007" son la misma referencia; "0" o vacía no identifican nada. */
fun normalizarReferencia(ref: String): String = ref.trim().trimStart('0')

data class BncDraft(
    val tx: Transaction,
    val saldoMinor: Long,
    val duplicate: Boolean,
    val motivoDuplicado: String?,
    val isCommission: Boolean,
    val pairRef: String?,
    val matchedRuleId: String?,
)

private val COMISION = Regex("comisi[oó]n", RegexOption.IGNORE_CASE)
private val PAGO_MOVIL = Regex("pago m[oó]vil", RegexOption.IGNORE_CASE)

fun mapBncRows(
    rows: List<BncRow>,
    accountId: String,
    existingKeys: Set<String>,
    rules: List<Rule>,
    categories: List<Category>,
    capturadas: List<ReferenciaCapturada> = emptyList(),
): List<BncDraft> {
    val porReferencia = rows.filter { it.referencia != "" && it.referencia != "0" }.groupingBy { it.referencia }.eachCount()
    val idComisiones = categories.firstOrNull { it.id == "cat-comisiones" }?.id
    val idPagoMovil = categories.firstOrNull { it.id == "cat-pago-movil-recibido" }?.id
    // D8: referencia normalizada + monto absoluto exacto (la comisión comparte referencia con su cargo).
    val capturadasPorRef = capturadas
        .filter { normalizarReferencia(it.referencia).isNotEmpty() }
        .map { normalizarReferencia(it.referencia) to it.montoMinor }
        .toSet()

    return rows.filter { it.amountMinor != 0L }.map { r ->
        val texto = "${r.tipOperacion} ${r.descripcion}"
        val isCommission = COMISION.containsMatchIn(r.tipOperacion)
        val regla = matchCategory(texto, rules)
        val esIngreso = r.amountMinor > 0
        var categoryId: String? = regla?.categoryId
        if (categoryId == null && isCommission && idComisiones != null) categoryId = idComisiones
        if (categoryId == null && esIngreso && idPagoMovil != null && PAGO_MOVIL.containsMatchIn(texto)) categoryId = idPagoMovil

        val importKey = bncImportKey(r)
        val refNorm = normalizarReferencia(r.referencia)
        val porImportKey = importKey in existingKeys
        val porCaptura = refNorm.isNotEmpty() && (refNorm to abs(r.amountMinor)) in capturadasPorRef
        BncDraft(
            tx = Transaction(
                id = nuevoId(),
                type = if (esIngreso) TxType.INCOME else TxType.EXPENSE,
                accountId = accountId,
                amountMinor = abs(r.amountMinor),
                date = r.date,
                time = r.time,
                categoryId = categoryId,
                note = r.descripcion,
                source = TxSource.BNC,
                importKey = importKey,
            ),
            saldoMinor = r.saldoMinor,
            duplicate = porImportKey || porCaptura,
            motivoDuplicado = when {
                porImportKey -> MOTIVO_YA_IMPORTADO
                porCaptura -> MOTIVO_POR_CAPTURA
                else -> null
            },
            isCommission = isCommission,
            pairRef = if ((porReferencia[r.referencia] ?: 0) >= 2) r.referencia else null,
            matchedRuleId = regla?.id,
        )
    }
}

enum class GapStatus { PRIMERA_IMPORTACION, SOLAPADO, CONTINUO, HUECO }

/** rows en orden de archivo (descendente). El previo viene de Account.bncLastImportKey/bncLastSaldoMinor. */
fun detectGap(rows: List<BncRow>, previoImportKey: String?, previoSaldoMinor: Long?): GapStatus {
    if (previoImportKey.isNullOrEmpty() || previoSaldoMinor == null) return GapStatus.PRIMERA_IMPORTACION
    if (rows.any { bncImportKey(it) == previoImportKey }) return GapStatus.SOLAPADO
    val masVieja = rows.lastOrNull() ?: return GapStatus.HUECO
    val saldoAntes = masVieja.saldoMinor - masVieja.amountMinor
    return if (saldoAntes == previoSaldoMinor) GapStatus.CONTINUO else GapStatus.HUECO
}

fun newestRow(rows: List<BncRow>): BncRow? = rows.firstOrNull()
```

- [ ] **Paso 6: Correr → PASS** y suite completa.

- [ ] **Paso 7: Commit.**

```bash
git add android/app/src/main/java/com/cuentasclaras/app/importar/ android/app/src/test/resources/importar/ android/app/src/test/java/com/cuentasclaras/app/importar/
git commit -m "feat(importar): parser del TXT del BNC y mapeo con duplicados por importKey y por captura" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Tarea 24: Pantalla Importar (TXT por SAF o pegado, vista previa y confirmación)

**Skills:** `ui-ux-pro-max:ui-ux-pro-max` (vista previa editable) · `run` (instalar y recorrer la app por adb) · `output-skill`.

**Files:**
- Create: `android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/importar/ImportarViewModel.kt`
- Create: `android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/importar/ImportarPantalla.kt`
- Modify: `android/app/src/main/java/com/cuentasclaras/app/ui/nav/CuentasNavHost.kt` (línea de `IMPORTAR`)

Fuente: `src/ui/componentes/ImportBnc.tsx` completo (fases, banners, guards Important #1 y #6) e `ImportarPage.tsx` (solo la parte BNC).

**Interfaces:**
- Consumes: T23, T6 (`commitImport`, `ParcheCuentaBnc`), T3 (`buildRule`, `buildAdjustment`, `accountBalanceMinor`), T11.
- Produces: `ImportarViewModel(repo)` con `estado`, `cambiarTexto`, `cargarArchivo(resolver, uri)`, `analizar()`, `actualizar(i, cambio: (DraftUI) -> DraftUI)`, `confirmar()`, `crearAjuste()`, `reiniciar()`; `data class DraftUI(draft: BncDraft, tx: Transaction, incluir, crearRegla, patron, sugerido)` (la Tarea 30 rellena `sugerido`).

- [ ] **Paso 1: `ImportarViewModel.kt`.**

```kotlin
package com.cuentasclaras.app.ui.pantallas.importar

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuentasclaras.app.data.CuentasRepo
import com.cuentasclaras.app.data.db.toDomain
import com.cuentasclaras.app.data.ops.ParcheCuentaBnc
import com.cuentasclaras.app.data.ops.commitImport
import com.cuentasclaras.app.domain.Account
import com.cuentasclaras.app.domain.Category
import com.cuentasclaras.app.domain.Transaction
import com.cuentasclaras.app.domain.accountBalanceMinor
import com.cuentasclaras.app.domain.buildAdjustment
import com.cuentasclaras.app.domain.buildRule
import com.cuentasclaras.app.domain.todayIso
import com.cuentasclaras.app.importar.bnc.BncDraft
import com.cuentasclaras.app.importar.bnc.BncParseResult
import com.cuentasclaras.app.importar.bnc.GapStatus
import com.cuentasclaras.app.importar.bnc.bncImportKey
import com.cuentasclaras.app.importar.bnc.detectGap
import com.cuentasclaras.app.importar.bnc.mapBncRows
import com.cuentasclaras.app.importar.bnc.newestRow
import com.cuentasclaras.app.importar.bnc.parseBncTxt
import com.cuentasclaras.app.importar.bnc.validateChain
import com.cuentasclaras.app.ui.Avisos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** El TXT del BNC siempre va a la cuenta bancaria en Bs del seed. */
const val CUENTA_BNC = "acc-bnc"

data class DraftUI(
    val draft: BncDraft,
    val tx: Transaction,
    val incluir: Boolean,
    val crearRegla: Boolean,
    val patron: String,
    /** true si sugerirParaLote propuso la asignación y el usuario no la cambió (Tarea 30). */
    val sugerido: Boolean = false,
)

enum class FaseImport { ENTRADA, PREVIA, HECHO }

class ImportarViewModel(private val repo: CuentasRepo) : ViewModel() {
    data class Ui(
        val texto: String = "",
        val fase: FaseImport = FaseImport.ENTRADA,
        val resultado: BncParseResult? = null,
        val drafts: List<DraftUI> = emptyList(),
        val gap: GapStatus = GapStatus.PRIMERA_IMPORTACION,
        val rotos: List<Int> = emptyList(),
        val saldoTxt: Long? = null,
        val sinResultados: Boolean = false,
        /** Important #6: oculta el banner tras crear el ajuste (evita un segundo ajuste). */
        val ajusteHecho: Boolean = false,
        val guardando: Boolean = false,
    )

    data class Estado(
        val ui: Ui = Ui(),
        val categorias: List<Category> = emptyList(),
        val cuentas: List<Account> = emptyList(),
        val saldoApp: Long = 0,
        /** Descuadre ofrecible como ajuste: null si no aplica (Important #1). */
        val descuadre: Long? = null,
        val rawMismatch: Boolean = false,
        val hayTxsPosteriores: Boolean = false,
    ) {
        val nuevas: List<DraftUI> get() = ui.drafts.filter { it.incluir && !it.draft.duplicate }
        val duplicadas: Int get() = ui.drafts.count { it.draft.duplicate }
    }

    private val ui = MutableStateFlow(Ui())

    val estado: StateFlow<Estado> = combine(ui, repo.txs, repo.categorias, repo.cuentas) { u, txs, categorias, cuentas ->
        val saldoApp = accountBalanceMinor(CUENTA_BNC, txs)
        val rawMismatch = u.fase == FaseImport.HECHO && u.saldoTxt != null && saldoApp != u.saldoTxt
        // Important #1: si ya hay movimientos posteriores al último renglón del TXT, el delta ya no
        // representa «movimientos previos al primer export» y ofrecer el ajuste podría corromper el saldo.
        val finArchivo = u.resultado?.let { newestRow(it.rows)?.date }
        val hayPosteriores = finArchivo != null && txs.any { it.accountId == CUENTA_BNC && it.date > finArchivo }
        Estado(
            ui = u, categorias = categorias, cuentas = cuentas, saldoApp = saldoApp,
            descuadre = if (rawMismatch && !hayPosteriores) u.saldoTxt!! - saldoApp else null,
            rawMismatch = rawMismatch, hayTxsPosteriores = hayPosteriores,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Estado())

    fun cambiarTexto(t: String) = ui.update { it.copy(texto = t) }

    fun cargarArchivo(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            try {
                val texto = withContext(Dispatchers.IO) { resolver.openInputStream(uri)!!.bufferedReader().use { it.readText() } }
                ui.update { it.copy(texto = texto) }
            } catch (e: Exception) {
                Avisos.avisar("No se pudo leer el archivo: ${e.message}")
            }
        }
    }

    fun analizar() {
        val texto = ui.value.texto
        viewModelScope.launch {
            val r = parseBncTxt(texto)
            if (r.rows.isEmpty()) {
                ui.update { it.copy(sinResultados = true) }
                Avisos.avisar("No se encontraron movimientos en el texto")
                return@launch
            }
            val db = repo.db
            val reglas = db.rules().todas().map { it.toDomain() }
            val existentes = db.transactions().importKeys().toSet()
            val capturadas = db.capturas().registradas()
            val categorias = db.categories().todas().map { it.toDomain() }
            val cuenta = db.accounts().porId(CUENTA_BNC)
            val mapeados = mapBncRows(r.rows, CUENTA_BNC, existentes, reglas, categorias, capturadas)
            ui.update {
                it.copy(
                    sinResultados = false, ajusteHecho = false, resultado = r,
                    rotos = validateChain(r.rows),
                    gap = detectGap(r.rows, cuenta?.bncLastImportKey, cuenta?.bncLastSaldoMinor),
                    saldoTxt = newestRow(r.rows)?.saldoMinor,
                    drafts = mapeados.map { d -> DraftUI(d, d.tx, incluir = !d.duplicate, crearRegla = false, patron = d.tx.note ?: "") },
                    fase = FaseImport.PREVIA,
                )
            }
        }
    }

    fun actualizar(i: Int, cambio: (DraftUI) -> DraftUI) =
        ui.update { u -> u.copy(drafts = u.drafts.mapIndexed { j, d -> if (j == i) cambio(d) else d }) }

    fun confirmar() {
        val u = ui.value
        if (u.guardando) return
        val resultado = u.resultado ?: return
        val nuevas = u.drafts.filter { it.incluir && !it.draft.duplicate }
        val duplicadas = u.drafts.count { it.draft.duplicate }
        ui.update { it.copy(guardando = true) }
        viewModelScope.launch {
            try {
                val reglasNuevas = nuevas
                    .filter { it.crearRegla && it.tx.categoryId != null && it.patron.isNotBlank() }
                    .map { buildRule(it.patron, it.tx.categoryId!!) }
                val fila = newestRow(resultado.rows)
                commitImport(
                    repo.db,
                    txs = nuevas.map { it.tx },
                    newRules = reglasNuevas,
                    accountPatch = fila?.let { ParcheCuentaBnc(CUENTA_BNC, bncImportKey(it), it.saldoMinor) },
                )
                Avisos.avisar("${nuevas.size} importados, $duplicadas duplicados omitidos ✓")
                ui.update { it.copy(fase = FaseImport.HECHO) }
            } catch (e: Exception) {
                Avisos.avisar("No se importó nada: ${e.message}")
            } finally {
                ui.update { it.copy(guardando = false) }
            }
        }
    }

    fun crearAjuste() {
        val e = estado.value
        if (e.ui.guardando) return
        val saldoTxt = e.ui.saldoTxt ?: return
        if (e.descuadre == null) return
        ui.update { it.copy(guardando = true) }
        viewModelScope.launch {
            try {
                repo.agregarTx(buildAdjustment(CUENTA_BNC, e.saldoApp, saldoTxt, todayIso(), "Cuadre tras import BNC"))
                Avisos.avisar("Saldo cuadrado con el banco ✓")
                ui.update { it.copy(ajusteHecho = true) }
            } catch (ex: Exception) {
                Avisos.avisar(ex.message ?: "No se pudo crear el ajuste")
            } finally {
                ui.update { it.copy(guardando = false) }
            }
        }
    }

    fun reiniciar() = ui.update { Ui() }
}
```

- [ ] **Paso 2: `ImportarPantalla.kt`** (contrato de `ImportBnc.tsx`):
  - **Entrada:** texto «Exporta desde BNC los "25 últimos movimientos" en TXT y súbelo o pega su contenido.»; botón «Elegir archivo TXT» (`ActivityResultContracts.OpenDocument()` con `arrayOf("text/plain", "application/octet-stream", "*/*")` → `vm.cargarArchivo`); «O pega el contenido» (`CampoTexto` multilínea, 6 líneas); «Analizar» (`enabled = texto.isNotBlank()`); si `sinResultados`: `Vacio("🔍", "No se reconoció ningún movimiento en el texto. Revisa que sea el export completo de BNC.")`.
  - **Previa:** «{n} movimientos en el archivo de la cuenta ••••{últimos 4}»; banner por `gap`: SOLAPADO (OK) «El archivo solapa con lo ya importado: los repetidos se omiten solos.», CONTINUO (OK) «El archivo conecta exactamente con el último import. Sin huecos.», HUECO (AVISO) «⚠ Hay movimientos NO exportados entre tu último import y este archivo (el saldo no encadena). Exporta desde BNC un rango que cubra el hueco e importa de nuevo.»; si `rotos`: ERROR «El encadenado de saldos no cuadra dentro del archivo (filas {i+1, …}). Revisa que el TXT esté completo.»; si `unparsed`: ERROR «{n} línea(s) irreconocibles — se importa el resto y estas quedan fuera (añádelas a mano si hacían falta):» + «línea {n} ({motivo}): «{raw.take(60)}…»». Lista (`LazyColumn`) por fila: casilla «Incluir fila {i+1}» (deshabilitada si duplicada; fila atenuada), nota, «{date} {time.take(8)}», chips «cargo+comisión» si `pairRef` y «{motivoDuplicado}» si duplicada; si no es duplicada: `Selector` de categoría («Sin categoría» + las del kind de la fila), casilla «regla» y, si está marcada, campo «Patrón» (placeholder «texto que identifica este comercio»); monto `+`/`−` `formatMoney(…, VES)`. Pie: «Cancelar» (`reiniciar`) y «Confirmar ({nuevas})» (`enabled = nuevas > 0 && !guardando`).
  - **Hecho:** OK «Import terminado. Saldo según la app: {formatMoney(saldoApp, VES)}.»; si `rawMismatch && hayTxsPosteriores`: AVISO «El TXT termina en {saldoTxt} pero la app calcula {saldoApp} — hay movimientos más recientes en esta cuenta, así que este descuadre no es fiable para crear un ajuste. Revísalo a mano si hace falta.»; si `descuadre != null && !ajusteHecho`: AVISO «El TXT termina en {saldoTxt} pero la app calcula {saldoApp} (movimientos previos al primer export).» con botón «Crear ajuste» (`enabled = !guardando`); botón «Importar otro archivo» (`reiniciar`).
- [ ] **Paso 3: Monta `IMPORTAR → ImportarPantalla(nav)`.**
- [ ] **Paso 4: Verificación.** `"$ADB" push android/app/src/test/resources/importar/bnc-fixture.txt /sdcard/Download/bnc-fixture.txt` → `instalar` → `sembrar …demo` → `tocar "Importar"` → «Elegir archivo TXT» → elige `bnc-fixture.txt` en el selector (`tocar "bnc-fixture.txt"`) → «Analizar» → `captura fase-4 previa` (9 filas, «cargo+comisión» en el par 100000004, banner HUECO por el `bncLastSaldoMinor` del demo) → cambia la categoría de «MERCADO LA ESQUINA» a Supermercado y marca «regla» → «Confirmar (9)» → `captura fase-4 hecho` (banner de descuadre con «Crear ajuste») → «Crear ajuste» → el banner desaparece → «Importar otro archivo», mismo TXT → todas «ya importado», «Confirmar (0)» deshabilitado → `captura fase-4 reimport`. En Ajustes → Reglas aparece la regla nueva. Read.
- [ ] **Paso 5: Dedupe con captura en el dispositivo.** Simula con `cmd notification post` un «BNC Pago Movil Recibido Bs.1500,00 … Ref:100000007 …» y regístralo desde la notificación (Tarea 20). Importa la fixture en una base limpia (`sembrar` el demo sin la fila importada) → la fila del abono de 1.500,00 sale «ya registrada por captura» → `captura fase-4 dedupe-captura`.
- [ ] **Paso 6: Commit.**

```bash
git add android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/importar/ android/app/src/main/java/com/cuentasclaras/app/ui/nav/CuentasNavHost.kt
git commit -m "feat(importar): pantalla de import del TXT del BNC con vista previa, reglas, huecos y cuadre" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Tarea 25: Cierre de la fase 4 — TXT real de Daniel

**Skills:** `mattpocock-skills:code-review` + `superpowers:requesting-code-review` · `superpowers:systematic-debugging` · `superpowers:verification-before-completion`.

- [ ] **Paso 1: Revisión** (`mattpocock-skills:code-review` desde el cierre de la fase 3): Standards (restricciones 2, 8, 9, 12, 15) y Spec (§7.4 + v1 del import BNC).
- [ ] **Paso 2: Columna de la referencia (bloqueante).** No sabemos si el `Ref:` de la notificación coincide con la columna `Referencia` o con `Referencia 2` del TXT real. Pide a Daniel: «Importa tu TXT de este mes en la app; ¿los pagos móviles que ya registraste desde la notificación salen como *ya registrada por captura*?». Si NO salen (y el monto coincide), pídele que compare a ojo la `Ref:` de una notificación con las dos columnas del TXT y te diga cuál coincide y si cambian los ceros; ajusta `mapBncRows` para comparar contra esa columna (o ambas), añade el caso a `MapBncTest` con datos inventados y commitea `fix(importar): …`. Nunca copies su TXT ni sus referencias reales.
- [ ] **Paso 3:** Fase 4 cerrada cuando Daniel confirme «importé el TXT del mes sin duplicar lo capturado».

---

## Fase 5 — Deudas y grupos transaccionales

Entregable (spec §7.5): APK con la pantalla Deudas completa y los grupos transaccionales operables desde Movimientos. Las operaciones de base (`settleDebt`, `agruparTxs`, `anadirAGrupo`, `quitarDeGrupo`, `desagrupar`, `actualizarGrupo`) ya existen con sus tests (Tarea 6); estas tareas son UI. T26 y T27 tocan archivos disjuntos: pueden ir en paralelo.

### Tarea 26: Pantalla Deudas

**Skills:** `ui-ux-pro-max:ui-ux-pro-max` · `output-skill`.

**Files:**
- Create: `android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/deudas/DeudasViewModel.kt`
- Create: `android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/deudas/DeudasPantalla.kt`
- Create: `android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/deudas/EditorDeuda.kt`
- Modify: `android/app/src/main/java/com/cuentasclaras/app/ui/nav/CuentasNavHost.kt` (línea de `DEUDAS`)

Fuente: `src/ui/paginas/DeudasPage.tsx` (pestañas, cierre con confirmación, eliminar, historial, `EditorDeuda` con cambio de moneda).

**Interfaces:**
- Consumes: T6 (`settleDebt`), T3 (`buildDebt`), T5 (DAOs vía `repo.db`), T11.
- Produces: `DeudasViewModel(repo)` con `estado`, `cambiarPestana(DebtDirection)`, `marcarCerrada(debt)`, `eliminar(debt)`, `guardar(original: Debt?, direction, concepto, cuentaId, montoTxt): Boolean`.

- [ ] **Paso 1: `DeudasViewModel.kt`.**

```kotlin
package com.cuentasclaras.app.ui.pantallas.deudas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuentasclaras.app.data.CuentasRepo
import com.cuentasclaras.app.data.db.toEntity
import com.cuentasclaras.app.data.ops.settleDebt
import com.cuentasclaras.app.domain.Account
import com.cuentasclaras.app.domain.Debt
import com.cuentasclaras.app.domain.DebtDirection
import com.cuentasclaras.app.domain.DebtStatus
import com.cuentasclaras.app.domain.ahoraIso
import com.cuentasclaras.app.domain.buildDebt
import com.cuentasclaras.app.domain.parseVenezuelanAmount
import com.cuentasclaras.app.domain.todayIso
import com.cuentasclaras.app.ui.Avisos
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DeudasViewModel(private val repo: CuentasRepo) : ViewModel() {
    data class Ui(val pestana: DebtDirection = DebtDirection.POR_PAGAR, val guardando: Boolean = false)
    data class Estado(
        val ui: Ui = Ui(),
        val cuentas: List<Account> = emptyList(),
        /** Pendientes de la pestaña, createdIso desc. */
        val pendientes: List<Debt> = emptyList(),
        /** Cerradas de la pestaña, settledIso desc. */
        val cerradas: List<Debt> = emptyList(),
    )

    private val ui = MutableStateFlow(Ui())

    val estado: StateFlow<Estado> = combine(ui, repo.cuentas, repo.deudas) { u, cuentas, deudas ->
        val delaPestana = deudas.filter { it.direction == u.pestana }
        Estado(
            ui = u,
            cuentas = cuentas.filter { !it.archived },
            pendientes = delaPestana.filter { it.status == DebtStatus.PENDIENTE }.sortedByDescending { it.createdIso },
            cerradas = delaPestana.filter { it.status == DebtStatus.CERRADA }.sortedByDescending { it.settledIso ?: "" },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Estado())

    fun cambiarPestana(d: DebtDirection) = ui.update { it.copy(pestana = d) }

    private fun conGuard(exito: String, trabajo: suspend () -> Unit) {
        if (ui.value.guardando) return
        ui.update { it.copy(guardando = true) }
        viewModelScope.launch {
            try {
                trabajo()
                Avisos.avisar(exito)
            } catch (e: Exception) {
                Avisos.avisar(e.message ?: "No se pudo completar")
            } finally {
                ui.update { it.copy(guardando = false) }
            }
        }
    }

    /** La UI confirma ANTES con «¿Marcar «concepto» como pagada/cobrada? Se creará el gasto/ingreso en {cuenta}.» */
    fun marcarCerrada(d: Debt) {
        val verbo = if (d.direction == DebtDirection.POR_PAGAR) "pagada" else "cobrada"
        conGuard("Deuda $verbo ✓") { settleDebt(repo.db, d.id, todayIso()) }
    }

    /** Cerrada: su movimiento NO se borra (la UI lo dice en la confirmación). */
    fun eliminar(d: Debt) = conGuard("Deuda eliminada") { repo.db.debts().borrar(d.id) }

    /** Devuelve false si la validación falla (la hoja sigue abierta). */
    fun guardar(original: Debt?, direction: DebtDirection, concepto: String, cuentaId: String, montoTxt: String): Boolean {
        val minor = parseVenezuelanAmount(montoTxt.removeSuffix(","))
        if (minor == null || minor <= 0) { Avisos.avisar("Monto inválido"); return false }
        if (concepto.isBlank()) { Avisos.avisar("Ponle un concepto a la deuda"); return false }
        if (estado.value.cuentas.none { it.id == cuentaId }) { Avisos.avisar("Elige una cuenta"); return false }
        conGuard("Deuda guardada ✓") {
            if (original == null) {
                repo.db.debts().insertar(buildDebt(direction, concepto, minor, cuentaId, ahoraIso()).toEntity())
            } else {
                repo.db.debts().actualizar(
                    original.copy(direction = direction, concepto = concepto.trim(), amountMinor = minor, accountId = cuentaId).toEntity(),
                )
            }
        }
        return true
    }
}
```

- [ ] **Paso 2: `DeudasPantalla.kt` + `EditorDeuda.kt`** (contrato de `DeudasPage.tsx`): título «Deudas»; `Segmentado` «Por pagar» / «Por cobrar»; en «Por cobrar», la ayuda «Lo que te deben es informativo: no suma al patrimonio hasta cobrarse.»; sin pendientes → `Vacio("🤝", "Sin deudas por pagar.")` (o «por cobrar»); por pendiente: concepto, `formatMoney(amountMinor, moneda de su cuenta)`, «{cuenta} · registrada el {createdIso.take(10)}», botones «Editar», «Eliminar» (`AlertDialog` «¿Eliminar esta deuda pendiente?») y «Marcar pagada»/«Marcar cobrada» (`AlertDialog` «¿Marcar «{concepto}» como pagada? Se creará el gasto en {cuenta}.» / «…cobrada? Se creará el ingreso en {cuenta}.»); botón «Nueva deuda por pagar»/«por cobrar» (si no hay cuentas: aviso «No hay cuentas»); sección plegable «Historial ({n})» con «{concepto} · {settledIso}», monto y «eliminar» (`AlertDialog` «¿Eliminar esta deuda cerrada? Su movimiento NO se borra.»). `EditorDeuda` (`HojaModal` «Nueva deuda»/«Editar deuda»): `Segmentado` «Debo»/«Me deben»; «Concepto (persona o descripción)» (placeholder «Juan — préstamo almuerzo»); «Cuenta para pagarla/cobrarla (define la moneda)»; al cambiar a una cuenta de otra moneda con monto escrito: limpia el monto y avisa «La cuenta elegida usa otra moneda: vuelve a escribir el monto»; «Monto en Bs|$ (coma decimal)» (inicial `montoEditable` al editar); «Cancelar» / «Guardar deuda» (cierra solo si `guardar` devuelve true). Todos los botones `enabled = !guardando`.
- [ ] **Paso 3:** Monta `DEUDAS → DeudasPantalla(nav)`; `DeudasTarjeta` de Inicio («ver todas») ya navega a `Rutas.DEUDAS`.
- [ ] **Paso 4: Verificación.** `instalar` → `sembrar …demo` → Inicio → «ver todas» → `captura fase-5 deudas` → «Marcar pagada» en «Préstamo de Ana» → confirmar → aparece en Historial y en Movimientos un gasto «Deuda pagada: Préstamo de Ana» en Efectivo → doble toque rápido en «Marcar cobrada» de la otra (con confirmación) → un solo ingreso (idempotencia) → crea una deuda en Bs y cambia la cuenta a una en $ con monto escrito → aviso de moneda → `captura fase-5 editor-deuda`. Inicio: «Total neto» del patrimonio cambia al cerrar la deuda por pagar. Read.
- [ ] **Paso 5: Commit.**

```bash
git add android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/deudas/ android/app/src/main/java/com/cuentasclaras/app/ui/nav/CuentasNavHost.kt
git commit -m "feat(deudas): pantalla de deudas con cierre transaccional, historial y editor" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Tarea 27: Grupos transaccionales en Movimientos (selección múltiple y editor de grupo)

**Skills:** `ui-ux-pro-max:ui-ux-pro-max` (selección múltiple y barra contextual) · `output-skill`.

**Files:**
- Modify: `android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/movimientos/MovimientosViewModel.kt`
- Modify: `android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/movimientos/MovimientosPantalla.kt`
- Create: `android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/movimientos/EditorGrupo.kt`

Fuente: `src/ui/paginas/MovimientosPage.tsx:66-135` (selección), `:240-285` (acciones de grupo), `:300-360` (barra y hoja «Agrupar»), `:472-535` (`EditorGrupo`).

**Interfaces:**
- Consumes: T6 (`agruparTxs`, `anadirAGrupo`, `quitarDeGrupo`, `desagrupar`, `actualizarGrupo`), T15.
- Produces: en `MovimientosViewModel.Ui`: `seleccion: Set<String>?` (null = sin modo selección), `destinoGrupoId: String?`, `hojaAgrupar: Boolean`, `editandoGrupo: TxGroup?`; funciones `iniciarSeleccion(t)`, `alternarSeleccion(t)`, `cancelarSeleccion()`, `anadirAlGrupo(gid)`, `pedirAgrupar()`, `confirmarAgrupar(nota, categoryId)`, `quitarDelGrupo(txId)`, `desagruparGrupo(gid)`, `editarGrupo(g)`, `guardarGrupo(gid, nota, categoryId)`.

- [ ] **Paso 1: Estado y acciones en `MovimientosViewModel`.** Añade a `Ui`:

```kotlin
        /** null = sin modo selección. */
        val seleccion: Set<String>? = null,
        /** Con valor: la selección se añade a ese grupo existente. */
        val destinoGrupoId: String? = null,
        val hojaAgrupar: Boolean = false,
        val editandoGrupo: TxGroup? = null,
```

y estas funciones (imports: `com.cuentasclaras.app.data.ops.*` de grupos):

```kotlin
    private fun esAgrupable(t: Transaction) = (t.type == TxType.INCOME || t.type == TxType.EXPENSE) && t.groupId == null

    /** Pulsación larga sobre una fila suelta (spec §7.2: «selección por pulsación larga»). */
    fun iniciarSeleccion(t: Transaction) =
        ui.update { it.copy(destinoGrupoId = null, seleccion = if (esAgrupable(t)) setOf(t.id) else emptySet()) }

    fun alternarSeleccion(t: Transaction) {
        if (!esAgrupable(t)) { Avisos.avisar("Solo se agrupan gastos e ingresos sueltos"); return }
        ui.update { u ->
            val s = u.seleccion ?: emptySet()
            u.copy(seleccion = if (t.id in s) s - t.id else s + t.id)
        }
    }

    fun cancelarSeleccion() = ui.update { it.copy(seleccion = null, destinoGrupoId = null, hojaAgrupar = false) }

    fun anadirAlGrupo(gid: String) {
        ui.update { it.copy(destinoGrupoId = gid, seleccion = emptySet()) }
        Avisos.avisar("Toca los movimientos a añadir")
    }

    /** Botón de la barra: con destino añade directo; sin destino abre la hoja «Agrupar». */
    fun pedirAgrupar() {
        val u = ui.value
        if (u.destinoGrupoId != null) confirmarAgrupar("", "") else ui.update { it.copy(hojaAgrupar = true) }
    }

    private fun conGuard(exito: String, trabajo: suspend () -> Unit) {
        if (ui.value.guardando) return
        ui.update { it.copy(guardando = true) }
        viewModelScope.launch {
            try {
                trabajo()
                Avisos.avisar(exito)
            } catch (e: Exception) {
                Avisos.avisar(e.message ?: "No se pudo completar")
            } finally {
                ui.update { it.copy(guardando = false) }
            }
        }
    }

    fun confirmarAgrupar(nota: String, categoryId: String) {
        val u = ui.value
        val ids = u.seleccion?.toList() ?: return
        val destino = u.destinoGrupoId
        if (destino != null) {
            conGuard("Añadidos al grupo ✓") { anadirAGrupo(repo.db, destino, ids); cancelarSeleccion() }
        } else {
            conGuard("Grupo transaccional creado ✓") {
                agruparTxs(repo.db, ids, nota, categoryId.takeIf { it.isNotEmpty() })
                cancelarSeleccion()
            }
        }
    }

    fun quitarDelGrupo(txId: String) = conGuard("Quitado del grupo ✓") { quitarDeGrupo(repo.db, listOf(txId)) }

    /** La UI confirma ANTES: «¿Desagrupar? Todos los movimientos vuelven a contarse normal.» */
    fun desagruparGrupo(gid: String) = conGuard("Grupo desagrupado ✓") { desagrupar(repo.db, gid) }

    fun editarGrupo(g: TxGroup?) = ui.update { it.copy(editandoGrupo = g) }

    fun guardarGrupo(gid: String, nota: String, categoryId: String) =
        conGuard("Grupo actualizado ✓") {
            actualizarGrupo(repo.db, gid, nota, categoryId)
            ui.update { it.copy(editandoGrupo = null) }
        }
```

`guardarEdicion`/`eliminar` (T15) siguen igual; reemplaza su guard local por `conGuard` si simplifica, sin cambiar sus mensajes.

- [ ] **Paso 2: UI.** En `MovimientosPantalla`:
  - Fila suelta: `Modifier.combinedClickable(onClick = { if (seleccion != null) vm.alternarSeleccion(t) else vm.editar(t) }, onLongClick = { if (seleccion == null) vm.iniciarSeleccion(t) })`; seleccionada → resaltada con el token de selección de `DESIGN.md`; en modo selección, las no agrupables se atenúan. Las filas dentro de un grupo no inician selección con pulsación larga.
  - Grupo expandido: cada miembro con botón «Quitar del grupo» (`contentDescription`), y debajo «Editar grupo», «Añadir movimientos» (deshabilitado si ya hay selección) y «Desagrupar» (con `AlertDialog` de confirmación).
  - Barra contextual anclada abajo dentro de la pantalla (flota sobre la barra de navegación mientras `seleccion != null`; el botón atrás del sistema llama `cancelarSeleccion` con `BackHandler(enabled = seleccion != null)`): «Cancelar» y «Agrupar como transaccional ({n})» o «Añadir al grupo ({n})»; habilitado si `n >= 2` (crear) o `n >= 1` (añadir) y `!guardando`.
  - Hoja «Agrupar como transaccional»: ayuda «Los movimientos agrupados no cuentan en tus métricas: solo su vuelto.»; «Nota del grupo (opcional)» (placeholder «me pasaron para la leche»); «Categoría del vuelto (opcional)» con «Sin categoría (Vuelto)» + secciones Gastos e Ingresos; ayuda «Se aplica al vuelto del grupo solo si el signo coincide; si no, cuenta como Vuelto.»; «Cancelar» / «Agrupar».
- [ ] **Paso 3: `EditorGrupo.kt`** (`HojaModal` «Editar grupo»): «Nota del grupo», «Categoría del vuelto» (igual que arriba); por cada residuo cuyo signo no case con el kind elegido: «Con el residuo actual ({±formatMoney}) esta categoría de gasto/ingreso no aplica; contará como Vuelto.»; ayuda «Los movimientos del grupo conservan su propia categoría.»; «Cancelar» / «Guardar» → `vm.guardarGrupo`.
- [ ] **Paso 4: Verificación.** `instalar` → `sembrar …demo` → Movs → pulsación larga en «Arepas» (`"$ADB" shell input swipe X Y X Y 800` sobre su centro) → toca «Taxi» → `captura fase-5 seleccion` → «Agrupar como transaccional (2)» → nota «prueba», categoría Supermercado → Agrupar → `captura fase-5 grupo-creado` (una fila de grupo con dos residuos, uno en $ y otro en Bs) → Inicio: «Este mes» ya no suma Arepas ni Taxi; saldos de Efectivo y BNC SIN cambios (restricción 7) → expande el grupo → «Quitar del grupo» en uno → el grupo se disuelve (baja de 2) → `captura fase-5 disuelto`. Read.
- [ ] **Paso 5: Commit.**

```bash
git add android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/movimientos/
git commit -m "feat(grupos): selección múltiple, agrupar, añadir, quitar, desagrupar y editor de grupo" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Tarea 28: Cierre de la fase 5

**Skills:** `mattpocock-skills:code-review` + `superpowers:requesting-code-review` / `superpowers:receiving-code-review` · `superpowers:verification-before-completion`.

- [ ] **Paso 1: Revisión** desde el cierre de la fase 4: Standards (restricciones 7, 8, 9) y Spec (v2: deudas y grupos; v3: categoría del grupo).
- [ ] **Paso 2: APK y validación de Daniel** (sin sembrar, con sus datos): cerrar una deuda real, agrupar un vuelto real y confirmar que «Este mes» y el patrimonio cuadran. Fase cerrada con su visto bueno.

---

## Fase 6 — Calendario de eventos

Entregable (spec §7.6): APK con el calendario completo, la tarjeta «Esta semana» real, los chips de evento en Nuevo (guardar y asignar en un toque), el evento en el editor de movimientos y la sugerencia de evento en el import (gana a la regla aprendida). El dominio (`Calendar.kt`, `EventMatch.kt`) y las operaciones (`EventOps.kt`) ya existen con tests (Tareas 4 y 6).

### Tarea 29: Pantalla Calendario y editor de evento

**Skills:** `ui-ux-pro-max:ui-ux-pro-max` (cuadrícula de mes con puntos de estado) · `output-skill`.

**Files:**
- Create: `android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/calendario/CalendarioViewModel.kt`
- Create: `android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/calendario/CalendarioPantalla.kt`
- Create: `android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/calendario/EditorEvento.kt`
- Modify: `android/app/src/main/java/com/cuentasclaras/app/ui/nav/CuentasNavHost.kt` (línea de `CALENDARIO`)
- Modify: `android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/inicio/SemanaTarjeta.kt` (solo habilitar la acción «Calendario»)

Fuentes: `src/ui/paginas/CalendarioPage.tsx`, `src/ui/componentes/EditorEvento.tsx`; README v3 «Calendario y eventos» (en `docs/superpowers/plans/2026-09-04-cuentas-claras-v3-implementacion.md`, Tarea 15).

**Interfaces:**
- Consumes: T4 (`ocurrenciasEnRango`, `buildEvent`, `parseDias`, `esQuincenal`, `RECURRENCIA_QUINCENAL`, `descripcionRecurrencia`, `kindDeEvento`), T6 (`guardarEvento`, `eliminarEvento`, `omitirOcurrencia`, `reanudarOcurrencia`, `desasignarEvento`), T2 (fechas), T11.
- Produces: `enum class Repetir { ONCE, WEEKLY, MONTHLY, QUINCENAL, YEARLY }`; `data class BorradorEvento(...)`; `CalendarioViewModel(repo, args)` con `estado`, `cambiarMes`, `irAHoy`, `seleccionarDia`, `nuevoEvento`, `editarEvento`, `cerrarEditor`, `cambiarBorrador`, `guardarBorrador`, `eliminarEvento`, `omitir`, `reanudar`, `desasignar`.

- [ ] **Paso 1: `CalendarioViewModel.kt`.**

```kotlin
package com.cuentasclaras.app.ui.pantallas.calendario

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuentasclaras.app.data.CuentasRepo
import com.cuentasclaras.app.data.Instantanea
import com.cuentasclaras.app.data.ops.desasignarEvento
import com.cuentasclaras.app.data.ops.eliminarEvento as eliminarEventoOp
import com.cuentasclaras.app.data.ops.guardarEvento
import com.cuentasclaras.app.data.ops.omitirOcurrencia
import com.cuentasclaras.app.data.ops.reanudarOcurrencia
import com.cuentasclaras.app.domain.CalEvent
import com.cuentasclaras.app.domain.CategoryKind
import com.cuentasclaras.app.domain.OcurrenciaVista
import com.cuentasclaras.app.domain.RECURRENCIA_QUINCENAL
import com.cuentasclaras.app.domain.Recurrence
import com.cuentasclaras.app.domain.ahoraIso
import com.cuentasclaras.app.domain.buildEvent
import com.cuentasclaras.app.domain.esQuincenal
import com.cuentasclaras.app.domain.kindDeEvento
import com.cuentasclaras.app.domain.monthOf
import com.cuentasclaras.app.domain.montoEditable
import com.cuentasclaras.app.domain.nuevoId
import com.cuentasclaras.app.domain.ocurrenciasEnRango
import com.cuentasclaras.app.domain.parseDias
import com.cuentasclaras.app.domain.parseVenezuelanAmount
import com.cuentasclaras.app.domain.partsOf
import com.cuentasclaras.app.domain.primerDiaMes
import com.cuentasclaras.app.domain.todayIso
import com.cuentasclaras.app.domain.ultimoDiaMes
import com.cuentasclaras.app.ui.Avisos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Repetir { ONCE, WEEKLY, MONTHLY, QUINCENAL, YEARLY }

/** Estado del editor de evento (equivale a los useState de EditorEvento.tsx). */
data class BorradorEvento(
    val original: CalEvent?,
    val id: String,
    val createdIso: String,
    val titulo: String,
    /** Solo filtra el selector de categoría: Pago (EXPENSE) o Cobro (INCOME). */
    val kind: CategoryKind,
    val categoriaId: String,
    val cuentaId: String,
    val montoTxt: String,
    val fecha: String,
    val repetir: Repetir,
    val diasTxt: String,
    val fin: String,
)

class CalendarioViewModel(private val repo: CuentasRepo, args: SavedStateHandle) : ViewModel() {
    data class Ui(
        val mes: String,
        val diaSel: String,
        val editando: BorradorEvento? = null,
        val guardando: Boolean = false,
    )

    data class Estado(
        val ui: Ui,
        val hoy: String,
        val datos: Instantanea? = null,
        val porFecha: Map<String, List<OcurrenciaVista>> = emptyMap(),
    )

    private val hoy = todayIso()

    fun borradorNuevo(fecha: String) = BorradorEvento(
        original = null, id = nuevoId(), createdIso = ahoraIso(), titulo = "", kind = CategoryKind.EXPENSE,
        categoriaId = "", cuentaId = "", montoTxt = "", fecha = fecha, repetir = Repetir.ONCE,
        diasTxt = partsOf(fecha).d.toString(), fin = "",
    )

    private fun borradorDe(e: CalEvent, i: Instantanea): BorradorEvento {
        val r = e.recurrence
        return BorradorEvento(
            original = e, id = e.id, createdIso = e.createdIso, titulo = e.titulo,
            kind = kindDeEvento(e, i.categorias) ?: CategoryKind.EXPENSE,
            categoriaId = e.categoryId, cuentaId = e.accountId ?: "",
            montoTxt = e.amountMinor?.let { montoEditable(it) } ?: "",
            fecha = e.startDate,
            repetir = when {
                r is Recurrence.Monthly && esQuincenal(r) -> Repetir.QUINCENAL
                r is Recurrence.Monthly -> Repetir.MONTHLY
                r == Recurrence.Weekly -> Repetir.WEEKLY
                r == Recurrence.Yearly -> Repetir.YEARLY
                else -> Repetir.ONCE
            },
            diasTxt = if (r is Recurrence.Monthly) r.days.joinToString(", ") else partsOf(e.startDate).d.toString(),
            fin = e.endDate ?: "",
        )
    }

    private val ui: MutableStateFlow<Ui> = run {
        val pedido = args.get<String>("dia")
        val mes = monthOf(pedido ?: hoy)
        val dia = pedido ?: if (hoy.startsWith(mes)) hoy else primerDiaMes(mes)
        MutableStateFlow(Ui(mes, dia, editando = if (args.get<String>("nuevo") == "1") borradorNuevo(hoy) else null))
    }

    val estado: StateFlow<Estado> = combine(ui, repo.instantanea) { u, i ->
        val ocurrencias = ocurrenciasEnRango(i.eventos, i.txs, i.cuentas, primerDiaMes(u.mes), ultimoDiaMes(u.mes), hoy)
        Estado(u, hoy, i, ocurrencias.groupBy { it.date })
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Estado(ui.value, hoy))

    fun cambiarMes(m: String) = ui.update { it.copy(mes = m, diaSel = if (monthOf(hoy) == m) hoy else primerDiaMes(m)) }
    fun irAHoy() = ui.update { it.copy(mes = monthOf(hoy), diaSel = hoy) }
    fun seleccionarDia(iso: String) = ui.update { it.copy(diaSel = iso) }
    fun nuevoEvento() = ui.update { it.copy(editando = borradorNuevo(it.diaSel)) }
    fun editarEvento(e: CalEvent) {
        val datos = estado.value.datos ?: return
        ui.update { it.copy(editando = borradorDe(e, datos)) }
    }
    fun cerrarEditor() = ui.update { it.copy(editando = null) }
    fun cambiarBorrador(cambio: (BorradorEvento) -> BorradorEvento) = ui.update { u -> u.copy(editando = u.editando?.let(cambio)) }

    private fun conGuard(exito: String, trabajo: suspend () -> Unit) {
        if (ui.value.guardando) return
        ui.update { it.copy(guardando = true) }
        viewModelScope.launch {
            try {
                trabajo()
                Avisos.avisar(exito)
            } catch (e: Exception) {
                Avisos.avisar(e.message ?: "No se pudo completar")
            } finally {
                ui.update { it.copy(guardando = false) }
            }
        }
    }

    fun guardarBorrador() {
        val b = ui.value.editando ?: return
        conGuard("Evento guardado ✓") {
            val recurrence: Recurrence = when (b.repetir) {
                Repetir.QUINCENAL -> RECURRENCIA_QUINCENAL
                Repetir.MONTHLY -> Recurrence.Monthly(parseDias(b.diasTxt))
                Repetir.WEEKLY -> Recurrence.Weekly
                Repetir.YEARLY -> Recurrence.Yearly
                Repetir.ONCE -> Recurrence.Once
            }
            val amountMinor = if (b.montoTxt.isBlank()) null
            else parseVenezuelanAmount(b.montoTxt.removeSuffix(","))?.takeIf { it > 0 } ?: throw IllegalArgumentException("Monto inválido")
            val e = buildEvent(
                titulo = b.titulo, categoryId = b.categoriaId, accountId = b.cuentaId, amountMinor = amountMinor,
                startDate = b.fecha, endDate = b.fin, recurrence = recurrence,
                omitidas = b.original?.omitidas, // editar CONSERVA las omitidas
                nowIso = b.createdIso,
            )
            guardarEvento(repo.db, e.copy(id = b.id, createdIso = b.createdIso))
            ui.update { it.copy(editando = null) }
        }
    }

    /** La UI confirma ANTES: «¿Eliminar «titulo»? N movimientos quedarán sin evento (conservan su categoría).» */
    fun eliminarEvento(id: String) {
        if (ui.value.guardando) return
        ui.update { it.copy(guardando = true) }
        viewModelScope.launch {
            try {
                val n = eliminarEventoOp(repo.db, id)
                Avisos.avisar(if (n > 0) "Evento eliminado · $n movimientos desasignados" else "Evento eliminado")
                ui.update { it.copy(editando = null) }
            } catch (e: Exception) {
                Avisos.avisar(e.message ?: "No se pudo eliminar")
            } finally {
                ui.update { it.copy(guardando = false) }
            }
        }
    }

    fun omitir(o: OcurrenciaVista) = conGuard("Ocurrencia omitida") { omitirOcurrencia(repo.db, o.eventId, o.date) }
    fun reanudar(o: OcurrenciaVista) = conGuard("Ocurrencia reanudada") { reanudarOcurrencia(repo.db, o.eventId, o.date) }
    fun desasignar(txId: String) = conGuard("Movimiento desasignado") { desasignarEvento(repo.db, txId) }
}
```

- [ ] **Paso 2: `CalendarioPantalla.kt`** (contrato de `CalendarioPage.tsx`): título «Calendario»; navegación «‹ {nombreMes} ›» («Mes anterior»/«Mes siguiente», sin tope) + chip «hoy» si el mes no es el actual; cabecera `LETRAS_DIA`; cuadrícula de `celdasMes(mes)` (6 × 7): número del día, atenuado fuera del mes, marca de hoy y del seleccionado, hasta 3 puntos con el color de la categoría del evento (sin pintar las omitidas; hecha y vencida con su variante de `DESIGN.md`) y «+n» si hay más; tocar una celda la selecciona. Debajo, `fechaLarga(diaSel)` y la lista del día (vacía → `Vacio("🗓️", "Nada este día")`): ícono de categoría, título, «{diaCorto} · {cuenta}», cifra (hecha: lo asignado por moneda unido con « · »; si no, el monto esperado o nada), estado (pendiente/hecha/vencida/omitida) y acciones: pendiente/vencida → «registrar» (`nav.navigate(Rutas.nuevo(eventId = o.eventId, eventDate = o.date))`) y «omitir»; omitida → «reanudar»; hecha → por cada tx asignada, chip «{nota o movimiento} · {monto}» con «quitar» (`desasignar`); `fueraDeRegla` → chip «fuera de la regla». Botón «Nuevo evento». Sección plegable «Todos los eventos ({n})» con ícono, título, `descripcionRecurrencia(e)` y «editar».
- [ ] **Paso 3: `EditorEvento.kt`** (`HojaModal`, contrato de `EditorEvento.tsx`, en este orden): título «Nuevo evento»/«Editar evento»; «Título» (placeholder «Quincena»); `Segmentado` «Pago»/«Cobro» (al cambiar, si la categoría elegida es del otro kind se vacía); «Categoría» («Elige una categoría» + las del kind); «Cuenta esperada (define la moneda)» («— sin cuenta —» + activas): al quitar la cuenta con monto escrito, borra el monto y avisa «Sin cuenta no hay monto esperado: se borró el monto escrito»; al cambiar a otra moneda con monto escrito, lo borra y avisa «La cuenta elegida usa otra moneda: vuelve a escribir el monto»; «Monto esperado en Bs|$ (opcional)» deshabilitado sin cuenta + ayuda «Sin cuenta no se puede fijar un monto. Déjalo vacío si el monto varía.»; `CampoFecha` «Fecha» (una vez) o «Primera vez»; «Repetir»: Una vez · Cada semana · Cada mes · Quincenal (15 y último) · Cada año (al elegir «Una vez» se vacía el fin); si «Cada mes»: «Días del mes» (placeholder «15, 31») + ayuda «31 = último día del mes»; si no es «Una vez»: «Fin (opcional)» + chip «Terminar hoy» (pone `todayIso()`); «Cancelar», «Eliminar» (solo al editar; `AlertDialog` con el conteo de movimientos asignados `datos.txs.count { it.eventId == b.id }`) y «Guardar». Todo `enabled = !guardando`.
- [ ] **Paso 4:** Monta `CALENDARIO → CalendarioPantalla(nav)` con `vmCon { c, h -> CalendarioViewModel(c.repo, h) }`, y en el placeholder `SemanaTarjeta` (T13) habilita la acción «Calendario» → `nav.navigate(Rutas.calendario())` (una línea; la Tarea 30 reemplaza la tarjeta entera).
- [ ] **Paso 5: Verificación.** `instalar` → `sembrar …demo` → en Inicio `tocar "Calendario"` → `captura fase-6 calendario-mes` (puntos de la Quincena el 15 y el 31, Netflix el 5) → «Nuevo evento»: «Netflix prueba», Pago, Suscripciones, Zinli, `15,99`, Cada mes, días `5` → `captura fase-6 editor-evento` → guarda → en la cuadrícula aparece un segundo punto el día 5 → toca el día 5 → «omitir» una ocurrencia pendiente → estado «omitida» y sin punto → «reanudar» → vuelve → edita «Netflix prueba» → «Eliminar» con el conteo de movimientos → `captura fase-6 calendario`. Read.
- [ ] **Paso 6: Commit.**

```bash
git add android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/calendario/ android/app/src/main/java/com/cuentasclaras/app/ui/nav/CuentasNavHost.kt
git commit -m "feat(calendario): cuadrícula de mes con ocurrencias derivadas, editor de evento y omitir/reanudar" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Tarea 30: Tarjeta «Esta semana» real y chips de evento en Nuevo (un toque)

**Skills:** `ui-ux-pro-max:ui-ux-pro-max` (tira semanal y chips) · `output-skill`.

**Files:**
- Modify: `android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/inicio/InicioViewModel.kt` (`ocurrenciasSemana`, `totalesSemana`)
- Modify: `android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/inicio/SemanaTarjeta.kt` (reemplazo completo del placeholder)
- Modify: `android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/nuevo/NuevoViewModel.kt` (reemplazo completo con la versión de abajo)
- Modify: `android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/nuevo/NuevoPantalla.kt` (fila de chips)

Fuentes: `src/ui/componentes/SemanaCard.tsx`, `src/ui/paginas/DashboardPage.tsx:24-34`, `src/ui/paginas/NuevoPage.tsx` (prefill de evento, sugerencia tras captura, chips).

**Interfaces:**
- Consumes: T4 (`candidatasParaTx`, `sugerirOcurrencia`, `aplicarAsignacion`, `ocurrenciasEnRango`, `totalesEsperados`, `kindDeEvento`), T29 (`Rutas.calendario`), T20 (captura en Nuevo).
- Produces: `InicioViewModel.Estado.ocurrenciasSemana: List<OcurrenciaVista>` y `totalesSemana: TotalesEsperados`; `NuevoViewModel.ChipEvento(o, evento, vencida, icono, activo, sugerido)`, `Estado.chips`, `tocarChip(c)`.

- [ ] **Paso 1: Inicio.** En `InicioViewModel.calcular` añade (decisión 13 del spec v3: 7 días rodantes más las vencidas de los últimos 30):

```kotlin
        val ocurrenciasSemana = ocurrenciasEnRango(i.eventos, i.txs, i.cuentas, addDays(hoy, -30), addDays(hoy, 6), hoy)
```

y en `Estado`: `val ocurrenciasSemana: List<OcurrenciaVista> = emptyList()`, `val totalesSemana: TotalesEsperados = TotalesEsperados(emptyList(), emptyList())` (rellena con `totalesEsperados(ocurrenciasSemana, i.cuentas, i.categorias)`).

- [ ] **Paso 2: `SemanaTarjeta.kt` real** (contrato de `SemanaCard.tsx`): «Esta semana» + acción «calendario» → `Rutas.calendario()`; tira de 7 días desde hoy (letra `LETRAS_DIA[weekdayLunes0(d)]`, número, hasta 3 puntos de color de categoría sin las omitidas; hoy marcado; tocar → `Rutas.calendario(dia = d)`); si hay totales: «Por pagar {lista} — Por cobrar {lista}» (cada lista `formatMoney` por moneda unidos con « · »); lista = vencidas + las de `[hoy, hoy+6]`, 3 visibles y chip «ver {n} más»/«ver menos»; cada fila: ícono, título, «{diaCorto} · {cuenta}», cifra (hecha: asignado por moneda; si no, monto esperado), estado; tocar pendiente/vencida → `Rutas.nuevo(eventId = …, eventDate = …)`, si no → `Rutas.calendario(dia = o.date)`. Vacía → `Vacio("🗓️", "Sin pagos ni cobros esta semana")` + chip «crear evento» → `Rutas.calendario(nuevo = true)`.

- [ ] **Paso 3: `NuevoViewModel.kt` — versión final completa** (incluye lo de las Tareas 12 y 20):

```kotlin
package com.cuentasclaras.app.ui.pantallas.nuevo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.cuentasclaras.app.capture.CapturaRepo
import com.cuentasclaras.app.data.CuentasRepo
import com.cuentasclaras.app.data.Instantanea
import com.cuentasclaras.app.data.db.EstadoCaptura
import com.cuentasclaras.app.data.db.TipoCaptura
import com.cuentasclaras.app.domain.Account
import com.cuentasclaras.app.domain.CalEvent
import com.cuentasclaras.app.domain.Category
import com.cuentasclaras.app.domain.CategoryKind
import com.cuentasclaras.app.domain.CtxSugerencia
import com.cuentasclaras.app.domain.EstadoOcurrencia
import com.cuentasclaras.app.domain.Ocurrencia
import com.cuentasclaras.app.domain.TxParaSugerir
import com.cuentasclaras.app.domain.TxType
import com.cuentasclaras.app.domain.aplicarAsignacion
import com.cuentasclaras.app.domain.buildManualTx
import com.cuentasclaras.app.domain.candidatasParaTx
import com.cuentasclaras.app.domain.kindDeEvento
import com.cuentasclaras.app.domain.montoEditable
import com.cuentasclaras.app.domain.parseVenezuelanAmount
import com.cuentasclaras.app.domain.sugerirOcurrencia
import com.cuentasclaras.app.domain.todayIso
import com.cuentasclaras.app.ui.Avisos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Modo { GASTO, INGRESO, TRANSFERIR, AJUSTAR }

/** Teclado propio (Teclado.tsx + onTecla de NuevoPage.tsx): coma única, máximo 2 decimales. */
fun aplicarTecla(entrada: String, tecla: String): String = when (tecla) {
    "⌫" -> entrada.dropLast(1)
    "," -> if (entrada.isEmpty() || entrada.contains(',')) entrada else "$entrada,"
    else -> {
        val partes = entrada.split(',')
        if (partes.size == 2 && partes[1].length >= 2) entrada else entrada + tecla
    }
}

class NuevoViewModel(
    private val repo: CuentasRepo,
    private val capturas: CapturaRepo,
    args: SavedStateHandle,
) : ViewModel() {
    data class Formulario(
        val modo: Modo,
        val entrada: String,
        val nota: String,
        val cuentaId: String?,
        val guardando: Boolean = false,
    )

    data class ChipEvento(
        val o: Ocurrencia,
        val evento: CalEvent,
        val vencida: Boolean,
        val icono: String,
        val activo: Boolean,
        val sugerido: Boolean,
    )

    data class Estado(
        val formulario: Formulario,
        val cuentas: List<Account> = emptyList(),
        val cuenta: Account? = null,
        val categorias: List<Category> = emptyList(),
        val minor: Long? = null,
        /** La sugerida/activa primero; máximo 4 candidatas + las fijadas. Vacía = sin fila (≤ 3 toques intacto). */
        val chips: List<ChipEvento> = emptyList(),
    )

    private val hoy = todayIso()
    val capturaId: String? = args.get<String>("capturaId")
    private val argEventId: String? = args.get<String>("eventId")
    private val argEventDate: String? = args.get<String>("eventDate")
    private val argMonto: String? = args.get<String>("monto")?.takeIf { it.matches(Regex("^\\d+$")) }

    private val formulario = MutableStateFlow(
        Formulario(
            modo = if (args.get<String>("tipo") == "ingreso") Modo.INGRESO else Modo.GASTO,
            entrada = argMonto?.let { montoEditable(it.toLong()) } ?: "",
            nota = args.get<String>("nota") ?: "",
            cuentaId = args.get<String>("cuenta"),
        ),
    )
    private val eventoActivo = MutableStateFlow<Ocurrencia?>(null)
    private val sugerida = MutableStateFlow<Ocurrencia?>(null)

    private val _salir = Channel<Unit>(Channel.BUFFERED)
    val salir: Flow<Unit> = _salir.receiveAsFlow()

    val estado: StateFlow<Estado> = combine(formulario, repo.instantanea, eventoActivo, sugerida) { f, i, activo, sug ->
        calcular(f, i, activo, sug)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Estado(formulario.value))

    private fun kindDe(m: Modo) = if (m == Modo.INGRESO) CategoryKind.INCOME else CategoryKind.EXPENSE

    private fun calcular(f: Formulario, i: Instantanea, activo: Ocurrencia?, sug: Ocurrencia?): Estado {
        val activas = i.cuentas.filter { !it.archived }
        val cuenta = activas.firstOrNull { it.id == f.cuentaId } ?: activas.firstOrNull()
        val minor = parseVenezuelanAmount(f.entrada.removeSuffix(","))
        val chips =
            if (cuenta == null || (f.modo != Modo.GASTO && f.modo != Modo.INGRESO)) emptyList()
            else chipsPara(i, kindDe(f.modo), cuenta.id, minor, activo, sug)
        return Estado(f, activas, cuenta, i.categorias.filter { it.kind == kindDe(f.modo) }, minor, chips)
    }

    private fun ctx(i: Instantanea) = CtxSugerencia(i.eventos, i.txs.filter { it.eventId != null }, i.cuentas, i.categorias, hoy)

    private fun chipsPara(i: Instantanea, kind: CategoryKind, cuentaId: String, minor: Long?, activo: Ocurrencia?, sug: Ocurrencia?): List<ChipEvento> {
        val lista = candidatasParaTx(TxParaSugerir(kind, hoy, cuentaId, minor ?: 0), ctx(i)).take(4)
            .map { Triple(it.ocurrencia.ocurrencia, it.ocurrencia.evento, it.ocurrencia.estado == EstadoOcurrencia.VENCIDA) }
            .toMutableList()
        for (fijada in listOfNotNull(sug, activo)) {
            if (lista.any { it.first == fijada }) continue
            val evento = i.eventos.firstOrNull { it.id == fijada.eventId } ?: continue
            lista.add(0, Triple(fijada, evento, fijada.date < hoy))
        }
        val primera = activo ?: sug
        if (primera != null) {
            val idx = lista.indexOfFirst { it.first == primera }
            if (idx > 0) lista.add(0, lista.removeAt(idx))
        }
        return lista.map { (o, evento, vencida) ->
            ChipEvento(
                o = o, evento = evento, vencida = vencida,
                icono = i.categorias.firstOrNull { it.id == evento.categoryId }?.icon ?: "📅",
                activo = o == activo,
                sugerido = o == sug && o != activo,
            )
        }
    }

    init {
        viewModelScope.launch {
            capturaId?.let { if (!cargarCaptura(it)) return@launch }
            val ultima = repo.settings.first().lastAccountId
            formulario.update { it.copy(cuentaId = it.cuentaId ?: ultima) }
            prefillEventos()
        }
    }

    /** false si la captura no se puede usar (ya registrada/descartada/inexistente). */
    private suspend fun cargarCaptura(id: String): Boolean {
        val c = capturas.porId(id)
        when {
            c == null -> {
                Avisos.avisar("La captura ya no existe")
                return true
            }
            c.estado != EstadoCaptura.PENDIENTE -> {
                Avisos.avisar(if (c.estado == EstadoCaptura.REGISTRADA) "Esta notificación ya se registró" else "Esta notificación se descartó")
                _salir.send(Unit)
                return false
            }
            c.reconocida && c.montoMinor != null -> formulario.update {
                it.copy(
                    modo = if (c.tipo == TipoCaptura.INGRESO) Modo.INGRESO else Modo.GASTO,
                    entrada = montoEditable(c.montoMinor),
                    nota = c.nota ?: "",
                    cuentaId = "acc-bnc",
                )
            }
            else -> formulario.update { it.copy(modo = Modo.GASTO, nota = c.textoCrudo.take(200)) }
        }
        return true
    }

    /** Deep link `eventId&eventDate` (Semana/Calendario) o sugerencia tras una captura. Una sola vez. */
    private suspend fun prefillEventos() {
        val i = repo.instantanea.first()
        if (i.eventos.isEmpty()) return
        if (argEventId != null && argEventDate != null) {
            val evento = i.eventos.firstOrNull { it.id == argEventId }
            val kind = evento?.let { kindDeEvento(it, i.categorias) }
            if (evento != null && kind != null) {
                eventoActivo.value = Ocurrencia(evento.id, argEventDate)
                formulario.update { f ->
                    f.copy(
                        modo = if (kind == CategoryKind.INCOME) Modo.INGRESO else Modo.GASTO,
                        cuentaId = evento.accountId ?: f.cuentaId,
                        entrada = if (argMonto == null && evento.amountMinor != null) montoEditable(evento.amountMinor) else f.entrada,
                    )
                }
                return
            }
        }
        // Sin evento: si vino de captura o con ?monto, se OFRECE la sugerencia (no se activa).
        val f = formulario.value
        val monto = parseVenezuelanAmount(f.entrada.removeSuffix(",")) ?: 0L
        val cuentaId = f.cuentaId ?: i.cuentas.firstOrNull { !it.archived }?.id
        if ((capturaId != null || argMonto != null) && monto > 0 && cuentaId != null) {
            sugerirOcurrencia(TxParaSugerir(kindDe(f.modo), hoy, cuentaId, monto), ctx(i))?.let { sugerida.value = it.ocurrencia }
        }
    }

    fun cambiarModo(m: Modo) = formulario.update { it.copy(modo = m) }
    fun tecla(t: String) = formulario.update { it.copy(entrada = aplicarTecla(it.entrada, t)) }
    fun cambiarNota(n: String) = formulario.update { it.copy(nota = n) }
    fun cambiarCuenta(id: String) = formulario.update { it.copy(cuentaId = id) }

    /** Un toque: si hay monto guarda y asigna; si no, marca el chip y pide el monto; el activo se desmarca. */
    fun tocarChip(c: ChipEvento) {
        if (c.activo) {
            eventoActivo.value = null
            return
        }
        val minor = estado.value.minor
        if (minor != null && minor > 0) {
            guardarCon(c.evento.categoryId, c.o, c.evento)
        } else {
            eventoActivo.value = c.o
            Avisos.avisar("Escribe el monto")
        }
    }

    /** Tocar una categoría guarda; si hay un chip activo, además asigna (con la categoría tocada). */
    fun guardar(categoryId: String) {
        val activo = estado.value.chips.firstOrNull { it.activo }
        guardarCon(categoryId, activo?.o, activo?.evento)
    }

    private fun guardarCon(categoryId: String, o: Ocurrencia?, evento: CalEvent?) {
        val e = estado.value
        val f = formulario.value
        if (f.guardando) return
        val cuenta = e.cuenta ?: return
        val minor = e.minor
        if (minor == null || minor <= 0) {
            Avisos.avisar("Escribe el monto")
            return
        }
        formulario.update { it.copy(guardando = true) }
        viewModelScope.launch {
            try {
                val base = buildManualTx(
                    type = if (f.modo == Modo.GASTO) TxType.EXPENSE else TxType.INCOME,
                    accountId = cuenta.id,
                    amountMinor = minor,
                    categoryId = categoryId,
                    dateIso = todayIso(),
                    note = f.nota.trim().takeIf { it.isNotEmpty() },
                )
                // La asignación NO altera monto, fecha, cuenta ni tipo.
                val tx = if (o != null && evento != null) aplicarAsignacion(base, o, evento).copy(categoryId = categoryId) else base
                val id = capturaId
                if (id == null) {
                    repo.agregarTx(tx)
                } else {
                    repo.db.withTransaction {
                        repo.agregarTx(tx)
                        repo.db.capturas().marcarRegistrada(id, tx.id)
                    }
                }
                repo.cambiarSettings { it.copy(lastAccountId = cuenta.id) }
                Avisos.avisar("Guardado ✓")
                _salir.send(Unit)
            } catch (ex: Exception) {
                Avisos.avisar(ex.message ?: "No se pudo guardar")
            } finally {
                formulario.update { it.copy(guardando = false) }
            }
        }
    }

    fun descartarCaptura() {
        val id = capturaId ?: return
        if (formulario.value.guardando) return
        viewModelScope.launch {
            capturas.descartar(id)
            Avisos.avisar("Notificación descartada")
            _salir.send(Unit)
        }
    }
}
```

- [ ] **Paso 4: Fila de chips en `NuevoPantalla`** (solo gasto/ingreso y solo si `chips` no está vacía: el flujo de 3 toques sin eventos queda intacto): un `Chip` por elemento con texto «{icono} {titulo} · {fechaCorta(o.date)}{ · vencida}», estilo «activo» (con «✕» para desmarcar) o «sugerido» según `DESIGN.md`, `enabled = !guardando`, `onClick = { vm.tocarChip(it) }`. Va entre la nota y el grid de categorías.
- [ ] **Paso 5: Verificación.** `instalar` → `sembrar …demo` → Inicio: tarjeta «Esta semana» con la tira y, si hoy cae cerca del 15 o del 31, la quincena → `captura fase-6 semana` → toca una ocurrencia pendiente → Nuevo abre en Ingreso, BNC, monto `80000,00` y el chip «Quincena · …» activo → toca «Sueldo/Pagos» → guardado y asignado; en el calendario la ocurrencia pasa a «hecha» → `captura fase-6 asignada`. Simula una captura con `Bs.80000,00` (otra Ref) en fecha cercana a una quincena → en Nuevo, el chip «Quincena» aparece primero como «sugerido» → un toque en el chip guarda y asigna (flujo en 2 toques desde la notificación). Sin eventos aplicables (Nuevo de gasto en Efectivo) → no hay fila de chips. Read.
- [ ] **Paso 6: Commit.**

```bash
git add android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/inicio/InicioViewModel.kt android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/inicio/SemanaTarjeta.kt android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/nuevo/NuevoViewModel.kt android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/nuevo/NuevoPantalla.kt
git commit -m "feat(calendario): tarjeta «Esta semana» real y chips de evento en Nuevo con guardado en un toque" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Tarea 31: `SelectEvento` en el editor de movimientos y sugerencia de evento en el import

**Skills:** `ui-ux-pro-max:ui-ux-pro-max` · `output-skill`.

**Files:**
- Create: `android/app/src/main/java/com/cuentasclaras/app/ui/componentes/SelectEvento.kt`
- Modify: `android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/movimientos/EditorMovimiento.kt`
- Modify: `android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/movimientos/MovimientosViewModel.kt` (`guardarEdicion` recibe la ocurrencia)
- Modify: `android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/importar/ImportarViewModel.kt` (sugerencias en `analizar`)
- Modify: `android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/importar/ImportarPantalla.kt` (selector por fila + chip «sugerido»)

Fuentes: `src/ui/componentes/SelectEvento.tsx`, `MovimientosPage.tsx:366-470`, `ImportBnc.tsx:56-90` y `:240-270`.

**Interfaces:**
- Consumes: T4 (`candidatasParaTx` con `incluirHechas = true`, `sugerirParaLote`, `aplicarAsignacion`), T15, T24.
- Produces: `@Composable fun SelectEvento(tx: TxParaSugerir, actual: Ocurrencia?, ctx: CtxSugerencia, etiqueta: String?, descripcion: String, onCambio: (Ocurrencia?, CalEvent?) -> Unit)` (no pinta nada si no hay candidatas ni asignación actual); `MovimientosViewModel.guardarEdicion(original, montoTxt, categoryId, fecha, nota, ocurrencia: Ocurrencia?)`.

- [ ] **Paso 1: `SelectEvento.kt`.** Opciones = `candidatasParaTx(tx, ctx, incluirHechas = true)` con etiqueta «📅 {titulo} · {dd/mm} · {estado}»; si `actual` no está entre ellas y su evento existe, se antepone «📅 {titulo} · {dd/mm} · (asignado)» (la asignación vigente SIEMPRE se lista); primera opción «Sin evento» → `onCambio(null, null)`; sin opciones → no pinta nada. Usa `Selector` (T10) con `contentDescription = descripcion`.
- [ ] **Paso 2: Editor de movimiento.** En `EditorMovimiento`, solo gasto/ingreso, debajo de la categoría: `SelectEvento(etiqueta = "Evento", descripcion = "Evento del movimiento", tx = TxParaSugerir(kind, fecha, tx.accountId, parseVenezuelanAmount(montoTxt.removeSuffix(",")) ?: 0), actual = ocurrencia, ctx = CtxSugerencia(eventos, txs asignadas, cuentas, categorias, todayIso()))`; al elegir un evento, la categoría pasa a la del evento (editable). En `MovimientosViewModel.guardarEdicion` añade el parámetro `ocurrencia: Ocurrencia?` y, para gasto/ingreso, `cambiado = cambiado.copy(eventId = ocurrencia?.eventId, eventDate = ocurrencia?.date)` (desasignar conserva la categoría). Para tener `txs` asignadas en el editor, expón `txsAsignadas` en `MovimientosViewModel.Estado` desde `repo.instantanea` (`i.txs.filter { it.eventId != null }`).
- [ ] **Paso 3: Import.** En `ImportarViewModel.analizar`, tras `mapBncRows` (el evento sugerido GANA a la regla aprendida y a la heurística; decisión 5 del spec v3):

```kotlin
            val eventos = db.calEvents().todas().map { it.toDomain() }
            val cuentas = db.accounts().todas().map { it.toDomain() }
            val asignadas = db.transactions().todas().map { it.toDomain() }.filter { it.eventId != null }
            val sugerencias = sugerirParaLote(
                mapeados.filter { !it.duplicate }.map { it.tx },
                CtxSugerencia(eventos, asignadas, cuentas, categorias, todayIso()),
            )
```

y al construir cada `DraftUI`:

```kotlin
                    drafts = mapeados.map { d ->
                        val o = sugerencias[d.tx.id]
                        val evento = o?.let { oc -> eventos.firstOrNull { it.id == oc.eventId } }
                        DraftUI(
                            draft = d,
                            tx = if (o != null && evento != null) aplicarAsignacion(d.tx, o, evento) else d.tx,
                            incluir = !d.duplicate, crearRegla = false, patron = d.tx.note ?: "",
                            sugerido = o != null && evento != null,
                        )
                    },
```

En `ImportarPantalla`, por fila no duplicada: `SelectEvento(descripcion = "Evento fila {i+1}", …)` cuyo `onCambio` hace `vm.actualizar(i) { it.copy(tx = aplicarAsignacion(it.tx, o, evento), sugerido = false) }`, y un chip «sugerido» si `sugerido && tx.eventId != null`. El `Selector` de categoría sigue visible y corregible.
- [ ] **Paso 4: Verificación.** `instalar` → `sembrar …demo` → edita «Netflix» (asignado) en Movimientos → el selector «Evento» muestra «Netflix · 05/10 · hecha» seleccionado → cámbialo a «Sin evento» → guarda → la meta 📅 desaparece y la categoría se conserva → `captura fase-6 editor-evento-mov`. Importa la fixture del TXT con un evento de cobro creado para el 02/07/2026 con monto `1500,00` en BNC → la fila del abono sale con el evento sugerido y el chip «sugerido» → `captura fase-6 import-sugerido`. Read.
- [ ] **Paso 5: Commit.**

```bash
git add android/app/src/main/java/com/cuentasclaras/app/ui/componentes/SelectEvento.kt android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/movimientos/ android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/importar/
git commit -m "feat(calendario): evento en el editor de movimientos y sugerencia de evento en el import" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Tarea 32: Cierre final — auditoría visual, revisión completa, README y validación del criterio de éxito

**Skills:** `redesign-skill` · `mattpocock-skills:code-review` + `superpowers:requesting-code-review` / `superpowers:receiving-code-review` · `superpowers:verification-before-completion` · `superpowers:finishing-a-development-branch`.

**Files:**
- Create: `android/README.md`
- Modify: los composables que corrija la auditoría (lista en el reporte)

- [ ] **Paso 1: Auditoría visual final** con `redesign-skill` sobre capturas de TODAS las pantallas (incluidas Calendario, Deudas, Importar, Permisos) contra `android/DESIGN.md`; solo arreglos visuales; commit `style(ui): …`.
- [ ] **Paso 2: Revisión completa de la rama** (`mattpocock-skills:code-review` desde `git merge-base HEAD main`): Standards (las 19 restricciones globales) y Spec (spec nativo §7 completo + v1/v2/v3). Comprobaciones mecánicas:
  - `grep -rn "kotlin.math.round" android/app/src/main` → vacío.
  - `grep -rln "import android\.\|import androidx\." android/app/src/main/java/com/cuentasclaras/app/domain` → vacío.
  - `grep -rln "androidx.compose" android/app/src/main/java/com/cuentasclaras/app/{domain,data,importar,rates,backup}` → vacío.
  - `grep -rn "txsEfectivas" android/app/src/main/java` → solo `InicioViewModel`.
  - `grep -rn "fallbackToDestructiveMigration" android/` → vacío; `android/app/schemas/…/1.json` sin cambios desde la Tarea 5 (o con su migración).
  - `git log --format=%B main..HEAD | grep -c "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"` = número de commits de la rama posteriores al plan.
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug` en verde.
- [ ] **Paso 3: `android/README.md`** (español): qué es, requisitos (Android Studio 2026.1, JBR 21, Gradle 9.1.0 en caché), compilar (`./gradlew :app:assembleDebug`), instalar (`adb install -r …`), correr los tests (`./gradlew :app:testDebugUnitTest`) y qué cubren (lista de §9), estructura de paquetes, respaldo compatible con la PWA, captura y `docs/guia-permisos.md`. Commit `docs(android): README de la app nativa`.
- [ ] **Paso 4: Validación del criterio de éxito con Daniel (bloqueante).** En su Xiaomi, con sus datos: (1) su respaldo importado (fase 1) sigue íntegro; (2) registra un gasto en ≤ 3 toques; (3) recibe un pago móvil real y lo registra desde la notificación en 2 toques; (4) importa un TXT mensual sin duplicar lo capturado; (5) crea la «Quincena» y la ve en «Esta semana». Entrega el reporte con las desviaciones declaradas (ledger D1–D17 y lo que haya surgido) y los avisos abiertos de cada tarea.
- [ ] **Paso 5: Integración.** Con `superpowers:finishing-a-development-branch`, presenta a Daniel las opciones para integrar `nativa-compose` (merge a `main`, PR o dejarla abierta). Sin firma de release ni Play Store (fuera de alcance).
