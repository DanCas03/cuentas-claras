# Handoff: escribir el plan de implementación de Cuentas Claras Nativa (Kotlin + Compose)

**Para:** el próximo agente (contexto fresco), que escribirá el plan.
**Fecha:** 2026-10-15 · **Autor:** agente del brainstorming de la migración a nativa.
**Rama:** `nativa-compose` (HEAD = spec `7674bb2` + este handoff).

## Tu misión

Escribir el plan de implementación **invocando la skill `superpowers:writing-plans`** a partir del spec aprobado:
**`docs/superpowers/specs/2026-10-15-cuentas-claras-nativa-compose-design.md`**. Léelo COMPLETO primero. Daniel ya aprobó el diseño en brainstorming (con un cambio ya incorporado: sin import de PDF Zinli), así que **no hay gate de spec pendiente**: pasa directo a escribir el plan.

El plan se ejecutará después, probablemente en varias sesiones, con `superpowers:subagent-driven-development`. Escríbelo con el nivel de detalle de `docs/superpowers/plans/2026-09-04-cuentas-claras-v3-implementacion.md` (formato de referencia): tareas pequeñas con código Kotlin verbatim, pasos de verificación bloqueantes, comandos de commit con footer. Organízalo por las 6 fases del spec y haz que **cada fase termine con un APK instalable** y una validación en el teléfono de Daniel.

## Jerarquía de fuentes ante dudas

1. Spec nativa (`2026-10-15-cuentas-claras-nativa-compose-design.md`) manda en el *cómo* y en el alcance.
2. Specs v1/v2/v3 (`docs/superpowers/specs/2026-07-07-…`, `2026-07-22-…`, `2026-09-04-…`) mandan en el *qué* de cada función. Las «decisiones cerradas» de esos specs siguen vigentes (por ejemplo, ocurrencias derivadas y nunca persistidas; `eventDate` nominal; el evento sugerido gana a la regla en imports).
3. **El código TypeScript de `src/` es la especificación ejecutable.** Cada regla de negocio se porta de ahí; los tests de Vitest son la guía de casos límite, no se portan uno a uno (ver decisión 8). Cuando un spec y el código difieran, manda el código (es lo que Daniel usó y validó).
4. Los comprobantes reales de la raíz (`TXTBNC1.txt`, `TXTBNC2.txt`, `Screenshot_*.jpg`) están gitignored y mandan en formatos. **Privacidad estricta:** nunca copiar datos reales a fixtures, docs ni código; usar las fixtures anonimizadas de `src/**/fixtures/`.

## Decisiones de Daniel (NO reabrir, NO volver a preguntar)

1. **Kotlin + Jetpack Compose puro.** Nada de WebView. La alternativa Capacitor quedó documentada en la rama `nativa-capacitor` y no se desarrolla.
2. **Sin import de PDF de Zinli.** Importar solo ofrece el TXT del BNC. No portar `src/import/zinli/` ni `ImportZinli.tsx`.
3. **Fases en este orden:** (1) dominio + Room + respaldo, (2) Inicio/Nuevo/Movimientos/Ajustes, (3) captura de notificaciones + Permisos, (4) import TXT BNC, (5) deudas y grupos, (6) calendario.
4. **Datos:** Daniel tiene un respaldo JSON v3 de hace ~1 mes y acepta empezar de cero. El formato de respaldo es el mismo byte a byte; no hay migración de IndexedDB.
5. **Teléfono:** Xiaomi/Redmi/POCO. Las defensas MIUI/HyperOS (autoinicio, batería sin restricciones, bloqueo en recientes, re-enlace del listener) son alcance obligatorio de la fase 3.
6. **Autonomía:** decide con el default razonable y documéntalo en el ledger; pregunta a Daniel SOLO bloqueos genuinos: conectar el teléfono por USB, confirmar el paquete del BNC, pasar el archivo de respaldo, validar en el dispositivo.
7. Todo en español (UI, docs, commits). Footer exacto de commit: `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`. No commitear `.claude/` ni `.superpowers/` (gitignorados).
8. **Tests solo los importantes** (dicho por Daniel al cerrar el brainstorming): «no es un objetivo tener una cobertura completa sino que simplemente la app funcione bien». La lista cerrada de qué lleva tests y qué no está en el spec §9. No portes la suite de Vitest uno a uno; pocos casos por módulo (normal, límite, el que falló históricamente). Si una tarea del plan pide TDD, que sea solo para los módulos de esa lista.

## Estado del repo

- `main` = PWA v3 en `28310bb`, suite Vitest 137+ verde. **La PWA no está desplegada** (Cloudflare caído); no intentes desplegar nada.
- `nativa-compose`: spec en `7674bb2`. Todavía **no existe `android/`**: el plan empieza por el scaffold.
- `nativa-capacitor`: solo su spec (`d1fd63e`).
- `core.autocrlf=true`: los avisos «LF will be replaced by CRLF» son inofensivos.

## Entorno verificado en la PC de Daniel (2026-10-15)

| Pieza | Estado |
|---|---|
| Android Studio | build `AI-261.25134.95` (serie 2026.1). Determina la versión de AGP/Kotlin/Compose BOM compatibles leyendo la documentación o un proyecto recién generado por el asistente; **no inventes versiones**. |
| SDK | `%LOCALAPPDATA%\Android\Sdk`: platforms 34, 35, 36, 36.1, 37.1; build-tools 36.0.0 y 37.0.0; `cmdline-tools/latest`; NDK 28 (no se usa). |
| JDK | JDK 21 en `C:\Program Files\Android\Android Studio\jbr`; JDK 17 Adoptium en el PATH. Usa el JBR 21 vía `org.gradle.java.home` en `android/gradle.properties` o `JAVA_HOME` en cada comando. |
| Gradle | `gradle-9.1.0-all` ya en caché del wrapper. Apunta el wrapper a 9.1.0 para no descargar otra versión. |
| adb | Operativo (`%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`). **Sin dispositivos conectados** al escribir esto. |
| Emulador | Binario presente, **sin AVD y sin system-images**. Crear uno exige descargar una imagen (`sdkmanager "system-images;android-35;google_apis;x86_64"`, ~1 GB) y aceleración WHPX/Hyper-V activa en Windows; verifica `emulator -accel-check` antes de contar con él. Alternativa preferida: el Xiaomi de Daniel por USB con depuración activada (pídeselo). |
| Keystore | `~/.android/debug.keystore` existe: el APK de depuración se firma solo. Nada de release por ahora. |
| Shell | PowerShell por defecto; en Bash (Git Bash) usa `./gradlew` y rutas `/c/...`. Los builds de Gradle son lentos la primera vez (minutos); pon timeouts largos y corre en segundo plano. |

## Contexto técnico que el plan DEBE incorporar (fácil de omitir)

- **Paquete del BNC:** `bnc.bncnet.mobile2` sale del nombre de una captura de pantalla de Daniel; el plan debe tener un paso «confirmar con `adb shell pm list packages | grep -i bnc`» antes de cerrar la fase 3, y el filtro debe ser configurable (constante única).
- **Formato de las notificaciones del BNC** (valores enmascarados): `BNC Pago Movil Recibido Bs.<monto> Telf.04XX***NNNN Dia:dd/mm/aa-HH:MM Ref:<dígitos> Llamar al …` y `BNC Compra POS Bs.<monto> Tarj:<4 dígitos> Dia:dd/mm/aa-HH:MM Ref:<dígitos> …`. Monto venezolano sin miles y con coma decimal. El parser portado debe pasar las fixtures de `src/capture/fixtures/notificacionesBnc.ts`.
- **Deduplicación captura ↔ import** es nueva (spec §7.3/§7.4): la tabla `capturas` guarda `referencia`; `mapBncRows` marca duplicada la fila cuya referencia ya esté registrada por captura. `bncImportKey` sigue siendo `bnc:<date>:<referencia>:<time>:<amountMinor>` y `commitImport` conserva el backstop dentro de la transacción.
- **Montos** `Long` en céntimos; **fechas** `String` `yyyy-mm-dd` locales (`java.time.LocalDate` solo interno; nunca `Instant`/UTC para fechas de calendario). El formato visual es `Bs 13.517,75` / `$84.20` (portar `formatMoney` con sus tests).
- **Room:** 9 tablas + `capturas`. `recurrence` y `omitidas` como JSON string con `TypeConverter` de kotlinx.serialization. `rates` con PK compuesta `(date, source)`. Índices de `transactions`: `date, accountId, categoryId, importKey, groupId, eventId`.
- **Seeds acoplados a tests:** ids fijos (`acc-bnc`, `acc-zinli`, `cat-comisiones`, `cat-pago-movil-recibido`, `cat-vuelto-ingreso`, `cat-vuelto-gasto`…). 5 cuentas, 18 categorías. `seedIfEmpty` siembra si vacío y repone por id las 3 categorías v2 si faltan. Los tests de respaldo que cuentan tablas deben usar esos números.
- **Respaldo** v3 exacto, v2 normalizado con `calEvents: []`, v1 rechazado con el mismo mensaje. `importBackup` reemplaza TODO en una transacción. Exportar/importar por SAF (`ActivityResultContracts.CreateDocument("application/json")` / `OpenDocument`).
- **Tasas:** cadena dolarapi (`promedio`) → vcoud (`price` del item `slug == "dolar-bcv"`), timeout 8 s, una vez al día por `lastRatesFetchDate`. Binance solo manual. Fixtures en `src/rates/fixtures/*.json`.
- **Patrones de UI heredados:** guard `guardando` en todo guardado; registro de gasto en ≤ 3 toques; teclado numérico propio; `lastAccountId`; chips de evento con guardado en 1 toque (fase 6); «Este mes» con navegación de mes acotada entre el mes más antiguo con movimientos y el actual.
- **Tests JVM, solo la lista del spec §9:** dominio de dinero/saldos/grupos/stats/calendario/eventMatch con JUnit; parsers del BNC (notificación y TXT) y respaldo con fixtures; operaciones todo-o-nada con Robolectric + Room en memoria (`allowMainThreadQueries` solo en tests). Nada de tests para `dates`, `builders`, `filters`, `rules`, seeds, ViewModels ni UI. Copiar las fixtures anonimizadas a `android/app/src/test/resources/` (TXT del BNC como texto; notificaciones y JSON de tasas tal cual).
- **Verificación de UI por fase:** `./gradlew :app:assembleDebug` → `adb install -r` → navegar → `adb exec-out screencap -p > .superpowers/sdd/capturas/<fase>/<pantalla>.png` → inspeccionar la imagen con Read. Las capturas no se commitean.
- **Paleta y fuente:** `src/ui/estilos.css` bloque `:root` (fondo `#0a0f1c`, tarjeta `#121a2b`, primario `#2dd4bf`, ingreso `#34d399`, gasto `#f87171`, aviso `#fbbf24`, texto `#e8edf6`, texto2 `#8b96ab`, radio 16/10). Manrope: descargar la variable OFL de Google Fonts a `res/font/` (no está en el repo; `@fontsource-variable/manrope` en `node_modules` trae los `.woff2`, que Android no lee: hace falta el `.ttf`).

## Lo que Daniel tendrá que hacer él (pídeselo en el momento, no antes)

- Conectar el Xiaomi por USB con depuración activada, o aceptar que se cree un emulador.
- Confirmar el paquete del BNC con el comando de arriba (o pasarte la salida).
- Pasar el archivo del respaldo JSON (fase 1) y validar los conteos.
- Validar la captura con una notificación real del BNC (fase 3) y conceder los permisos desde la pantalla Permisos.

## Pendientes conocidos fuera de este plan

- Firma de release y distribución: cuando Daniel lo pida.
- Rama `nativa-capacitor`: solo si la reescritura se atasca.
- La guía `docs/guia-macrodroid.md` queda obsoleta para la app nativa; el plan puede incluir una tarea de docs que la reemplace por la guía de Permisos.
