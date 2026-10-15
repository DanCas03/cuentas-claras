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

## Skills recomendadas por etapa (petición de Daniel: el plan debe nombrarlas en cada tarea)

Plugins verificados en la máquina el 2026-10-15: `superpowers` 6.4.1, `mattpocock-skills` 1.2.3, `ui-ux-pro-max` 2.6.2, `context-mode`. Además, Daniel copió el grupo **tasteskills** a `.claude/skills/` del proyecto (gitignored; disponible en cualquier sesión abierta en este repo en su PC; `llms.txt` describe cada una). `/ask-matt` y `/handoff` solo los invoca Daniel.

**Selección de tasteskills para esta app** (hecha en el brainstorming; el resto del grupo se descarta):

| Skill | Cuándo | Cómo usarla aquí |
|---|---|---|
| `output-skill` (`full-output-enforcement`) | TODA tarea de implementación, todas las fases | Prohíbe placeholders y código truncado. Cárgala en cada subagente que escriba Kotlin. |
| `stitch-skill` (`stitch-design-taste`) | Primera tarea de la fase 2 | Producir `android/DESIGN.md` a partir de los tokens de `src/ui/estilos.css` (tema oscuro, teal `#2dd4bf`, Manrope, radios 16/10, tabular-nums), adaptado a Compose (dp/sp en vez de px, `MaterialTheme` en vez de CSS). Es la única fuente visual que leen los subagentes de UI. |
| `soft-skill` (`high-end-visual-design`) | Fase 2, al escribir `DESIGN.md` y los componentes base | Usar SOLO sus §2 (anti-patrones: sin bordes gris 1px genéricos, sin sombras duras, sin `linear`) y §4 (tarjetas con doble bisel, botones píldora, ritmo de espacios) traducidos a Compose (`spring()` y `tween` con easing personalizado). Ignorar su «variance mandate» (las pantallas deben ser consistentes entre sí) y todo lo de navbars, GSAP y Tailwind. |
| `imagegen-frontend-mobile` | Fase 2, antes de codificar Inicio, Nuevo y Movimientos | SOLO si la sesión tiene herramienta de generación de imágenes. Genera conceptos de pantalla fintech Android (modo oscuro, paleta de `DESIGN.md`) como referencia visual; no escribe código. Sin herramienta de imágenes, se omite sin sustituto. |
| `redesign-skill` (`redesign-existing-projects`) | Cierre de la fase 2 y de la fase 6 | Auditoría de las capturas del emulador/teléfono buscando patrones genéricos de IA; aplicar solo arreglos que no toquen comportamiento. |

Descartadas y por qué: `taste-skill` v1/v2/experimental (su propia descripción: «not for dashboards or app screens»), `gpt-tasteskill` e `image-to-code-skill` (webs con GSAP), `imagegen-frontend-web` (webs), `brandkit` (identidad de marca; el ícono ya existe en `public/iconos`), `minimalist-skill` (monocromo cálido sin gradientes: choca con la identidad oscura teal), `brutalist-skill` (estética incompatible).

| Etapa | Skill principal | Skills de apoyo | Para qué |
|---|---|---|---|
| **Escribir el plan** (esta sesión) | `superpowers:writing-plans` | `mattpocock-skills:research` | El plan a partir del spec. `research` para fijar versiones reales de AGP, Kotlin, Compose BOM, Room y KSP contra fuentes primarias antes de escribir el scaffold (regla «no inventes versiones»); guarda el hallazgo en `docs/research/`. |
| **Ejecución de todas las fases** | `superpowers:subagent-driven-development` | `superpowers:dispatching-parallel-agents`, `superpowers:verification-before-completion`, `context-mode` | Un subagente por tarea; paralelo solo con archivos disjuntos (regla de Daniel). Verificación con evidencia antes de marcar hecho. Gradle siempre a través de `ctx_execute` con `tail`/`grep` para no inundar el contexto. |
| **Cierre de cada fase** | `mattpocock-skills:code-review` | `superpowers:requesting-code-review`, `superpowers:receiving-code-review` | Revisión en dos ejes (Standards contra las restricciones del spec §4; Spec contra el *qué* de v1/v2/v3) antes del APK de la fase. |
| **Fase 1** dominio + Room + respaldo | `superpowers:test-driven-development` (solo módulos de §9) | `mattpocock-skills:codebase-design` | TDD limitado a dinero, saldos, grupos, stats, calendario, eventMatch, respaldo y ops todo-o-nada. `codebase-design` para decidir las costuras (DAO ↔ repositorio ↔ dominio) como módulos profundos antes de escribir Room. |
| **Fase 2** pantallas | `ui-ux-pro-max:ui-ux-pro-max` + `stitch-skill` | `soft-skill` (§2 y §4), `imagegen-frontend-mobile` (si hay generador de imágenes), `dataviz`, `mattpocock-skills:prototype`, `redesign-skill` al cierre | Orden: (1) `stitch-skill` escribe `android/DESIGN.md` desde `estilos.css` con las reglas de `soft-skill` adaptadas a Compose; (2) `ui-ux-pro-max` con producto «mobile app / finance, dark mode» para guías UX y jerarquía de cada pantalla; (3) opcional `imagegen-frontend-mobile` para referencias visuales de Inicio/Nuevo/Movimientos; (4) `dataviz` ANTES de dibujar la dona y las barras en Canvas; (5) `prototype` si el teclado numérico o el flujo de Nuevo en ≤ 3 toques generan dudas de estado; (6) `redesign-skill` audita las capturas al cerrar la fase. `ui-ux-pro-max:design-system` queda como alternativa a `stitch-skill` si `DESIGN.md` no cuaja. |
| **Fase 3** captura de notificaciones | `mattpocock-skills:research` | `mattpocock-skills:wizard`, `superpowers:systematic-debugging` | `research` sobre `NotificationListenerService`, `requestRebind`, `POST_NOTIFICATIONS` y los intents de autoinicio de MIUI/HyperOS contra docs de Android y fuentes primarias. `wizard` para los pasos que solo Daniel puede hacer en el Xiaomi (depuración USB, confirmar el paquete del BNC, conceder permisos). `systematic-debugging` cuando el listener no dispare. |
| **Fase 4** import TXT BNC | `superpowers:test-driven-development` | `run` | TDD con la fixture anonimizada (parser, `bncImportKey`, duplicados por referencia de captura). `run` para instalar y recorrer la app por adb y capturar la vista previa. |
| **Fase 5** deudas y grupos | `superpowers:test-driven-development` | `ui-ux-pro-max:ui-ux-pro-max` | TDD en `settleDebt` y las ops de grupo (todo-o-nada). UX de la selección múltiple y del editor de grupo. |
| **Fase 6** calendario | `superpowers:test-driven-development` | `ui-ux-pro-max:ui-ux-pro-max` | TDD en recurrencias y `sugerirOcurrencia`. UX de la cuadrícula de mes y los chips de evento en Nuevo. |
| **Cualquier bug** | `superpowers:systematic-debugging` | `mattpocock-skills:diagnosing-bugs` | La primera para bugs normales; la segunda para los difíciles o de rendimiento (builds, listener, Room). |
| **Final de la rama** | `superpowers:finishing-a-development-branch` | | Decidir integración a `main` cuando las 6 fases estén validadas por Daniel. |

No uses `superpowers:brainstorming` ni `mattpocock-skills:grilling` para reabrir el diseño: ya está aprobado. Si Daniel quiere estresar una decisión concreta, él lanzará `/mattpocock-skills:grilling` o `/ask-matt`.

## Lo que Daniel tendrá que hacer él (pídeselo en el momento, no antes)

- Conectar el Xiaomi por USB con depuración activada, o aceptar que se cree un emulador.
- Confirmar el paquete del BNC con el comando de arriba (o pasarte la salida).
- Pasar el archivo del respaldo JSON (fase 1) y validar los conteos.
- Validar la captura con una notificación real del BNC (fase 3) y conceder los permisos desde la pantalla Permisos.

## Pendientes conocidos fuera de este plan

- Firma de release y distribución: cuando Daniel lo pida.
- Rama `nativa-capacitor`: solo si la reescritura se atasca.
- La guía `docs/guia-macrodroid.md` queda obsoleta para la app nativa; el plan puede incluir una tarea de docs que la reemplace por la guía de Permisos.
