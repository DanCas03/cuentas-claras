# Handoff: ejecución de Cuentas Claras Nativa, pausa a mitad de la fase 2

**Para:** el agente orquestador de la próxima sesión, que empieza con contexto fresco.
**Fecha:** 2026-10-15 · **Repo:** `C:\Users\danie\Documents\code\Proyects\AppNueva` · **Rama:** `nativa-compose`. HEAD `794ab21`.
**Método:** `superpowers:subagent-driven-development`. El orquestador coordina; un subagente implementa cada tarea y otro la revisa, en dos ejes: spec y calidad. Hay rondas de arreglos con re-revisión acotada.
**Motivo de la pausa:** la pidió Daniel. Las tareas en curso se cerraron en un estado limpio y no queda nada a medio escribir.

## Qué leer primero (en orden)

1. Este handoff completo.
2. El ledger de ejecución: `.superpowers/sdd/ledger-ejecucion.md`. Está en una carpeta gitignored de esta PC. Tiene una línea por despacho, revisión, ronda, ruling y menor diferido. Si un hallazgo te sorprende, búscalo primero ahí.
3. El workspace del plan: `.superpowers/sdd/2026-10-15-cuentas-claras-nativa-compose-implementacion/`.
   - `brief.sh N`: extrae la Tarea N del plan a `task-N-brief.md`. El script de la skill no sirve, porque busca «Task» y el plan dice «Tarea».
   - `implementador-base.md`: reglas comunes de los implementadores, incluido el footer.
   - `ui-comun.md`: reglas de las pantallas.
   - `revisor-base.md` y `rerevisor-base.md`: plantillas de revisión.
   - `contexto-global.md`: copia literal de las Global Constraints 1-19, D1-D17 y la receta adb del plan.
   - `enmiendas.md`: enmiendas E1-E29, que prevalecen sobre el plan. Hay una copia íntegra al final de este documento.
   - `task-N-report.md`: reportes de cada tarea, con las firmas reales y las guías de verificación.
   - `review-*.diff`: paquetes de revisión.
4. El plan (`docs/superpowers/plans/2026-10-15-cuentas-claras-nativa-compose-implementacion.md`) y el spec (`docs/superpowers/specs/2026-10-15-cuentas-claras-nativa-compose-design.md`). Del plan, lee solo las secciones que necesites; no lo cargues entero.
5. La memoria del proyecto se carga sola. Las entradas clave son `migracion-nativa-compose`, `ui-nativa-desde-cero` (con la elección visual de Daniel) y `no-selector-saf-telefono-daniel`.

## Estado por tarea

| Tarea | Estado | Dónde |
|---|---|---|
| T1–T9 (fase 1) | **Cerrada y validada por Daniel.** Restauró su respaldo real y los conteos cuadran. | `nativa-compose` (`31eacc7`…`7d88f9d`) |
| T10 Dirección visual, tema y componentes | Completa y revisada | `nativa-compose` (`f1eb618`, `66febe7`) |
| T11 Repo, navegación y barra | Completa y revisada | `nativa-compose` (`c596927`) |
| T13 Inicio | Completa y revisada (1 ronda), integrada con su línea en el NavHost | `nativa-compose` (`3a3a904`, `15fa521`, `794ab21`) |
| T18 Parser de notificaciones, CapturaRepo e investigación MIUI (adelantada de la fase 3) | Completa y revisada, integrada | `nativa-compose` (`2ecf574`) |
| T23 Parser y mapeo del TXT del BNC (adelantada de la fase 4) | Completa y revisada (1 ronda, E24), integrada | `nativa-compose` (`a57c77e`, `d2c00c7`) |
| **T12 Nuevo** | Implementada, revisada (Needs fixes) y con la ronda de arreglos 1 hecha. **Falta la re-revisión de esa ronda.** | rama `sdd-t12` (`e6c368e` + `9412532`), worktree `../AppNueva-t12` |
| **T15 Movimientos** | Implementada, **sin revisar**. La revisión se detuvo a pedido de Daniel. | rama `sdd-t15` (`98975af`), worktree `../AppNueva-t15` |
| **T16 Ajustes** | Implementada, **sin revisar** | rama `sdd-t16` (`e912deb`), worktree `../AppNueva-t16` |
| T14 «Este mes», comparativa y presupuestos | Pendiente (depende de T13, que ya está) | — |
| T17 Cierre de la fase 2 | Pendiente | — |
| T19–T22, T24–T32 | Pendientes | — |

Suite actual en `nativa-compose`: 53 tests en verde, y `assembleDebug` compila.

## Siguiente sesión: por dónde empezar (en este orden)

1. **Revisar T15 y T16** en paralelo, con dos revisores Opus y la plantilla `revisor-base.md`:
   - T15: `bash <skill>/scripts/review-package PLAN 2ecf574 98975af`.
   - T16: `bash <skill>/scripts/review-package PLAN 2ecf574 e912deb`.
   - Los reportes ya escritos (`task-15-report.md`, `task-16-report.md`) traen sus desviaciones.
   - En T15 hay que mirar en especial E25: `restoreState = false` y `launchSingleTop = false` en la pestaña Movs, y si eso apila entradas.
   - En T16, la sección «Apariencia» (E22), y que `guardarCuenta`/`guardarCategoria` devuelven true antes de terminar de guardar.
   - Si salen hallazgos, se reanuda o relanza al implementador en su worktree para las rondas de arreglos.
2. **Re-revisión acotada de la ronda 1 de T12:** `review-package PLAN e6c368e 9412532` con `rerevisor-base.md`. Los hallazgos a verificar están en el ledger, en la línea «Task 12: review → Needs fixes» y siguientes.
3. **Integrar T12, T15 y T16** en `nativa-compose` con cherry-pick (los objetos son compartidos). Después hay que tocar `ui/nav/CuentasNavHost.kt`, cosa que hace el orquestador:
   - NUEVO: `composable(Rutas.NUEVO, arguments = argumentos(Rutas.ARGS_NUEVO)) { NuevoPantalla(nav) }` + import `ui.pantallas.nuevo.NuevoPantalla`.
   - MOVIMIENTOS: `composable(Rutas.MOVIMIENTOS, arguments = argumentos(Rutas.ARGS_MOVIMIENTOS)) { MovimientosPantalla(nav) }` + import.
   - AJUSTES: `composable(Rutas.AJUSTES) { AjustesPantalla(nav) }` + import. Además hay que **borrar la función `AjustesProvisional`** y los imports que queden sin uso (`Box`, `padding`, `CuentasTema`, `RespaldoSeccion`).
   - Conflicto probable: T15 modificó `ui/nav/BarraInferior.kt` (E25) y T12 modificó componentes compartidos (`Teclado`, `CategoriaGrid`, `CampoTexto`). Ninguna otra rama toca esos archivos, pero compila después de cada cherry-pick.
   - `./gradlew :app:testDebugUnitTest :app:assembleDebug` en verde. Luego, un commit de integración del NavHost y borrar los worktrees (`rm -rf ../AppNueva-tNN`, porque `git worktree remove` falla en Windows con rutas largas; después `git worktree prune` y `git branch -D sdd-tNN`).
4. **Verificación integrada en el Xiaomi** de Inicio, Nuevo, Movimientos y Ajustes, en claro y oscuro, con un subagente verificador y la variante verif. Cada `task-N-report.md` trae su guía. Dos puntos críticos:
   - **Los ≤ 3 toques de Nuevo** se verifican geométricamente: `volcar` sin desplazar y comprobar que los `bounds` de las 4 filas de categorías de gasto (incluida «Suscripciones») caen dentro de la pantalla. La captura sola no basta (memoria `verificacion-headless-geometrica`).
   - Probar «tocar etiqueta → escribir» en un campo (menor diferido de T10).
5. **T14** («Este mes», comparativa y presupuestos, con gráficas en Canvas; cargar `dataviz` antes). Después, **T17**, el cierre de la fase 2:
   - auditoría visual con `redesign-skill`;
   - `mattpocock-skills:code-review` desde `7d88f9d` (inicio de la fase 2);
   - instalar el APK debug en `com.cuentasclaras.app` y que Daniel lo valide en su teléfono.
6. Luego la fase 3 (T19, T20, T21 y T22, con E17, E28 y E29), la fase 4 (T24 y T25, con E2 y E19), la 5 (T26 ‖ T27, T28) y la 6 (T29-T32).

## Reglas operativas aprendidas en esta sesión (cúmplelas)

- **Teléfono:**
  - El Xiaomi de Daniel (modelo 2602BPC18G, Android 16, HyperOS, 1280×2772, serie `BUPV89TORKTKGQDA`) ya está autorizado para adb. Pídele que lo conecte solo cuando haga falta.
  - **Toda verificación por adb usa la variante `verif`** (`com.cuentasclaras.app.verif`, etiqueta «Cuentas Claras (verif)», build type `verif` en Gradle). `.superpowers/sdd/adb-ui.sh` ya apunta a ella, con `MSYS_NO_PATHCONV=1`.
  - La app de uso diario de Daniel es `com.cuentasclaras.app` (APK debug) y **tiene sus datos reales**: nunca `sembrar`, `pm clear`, `run-as` ni `force-stop` sobre ella. Solo `adb install -r` del APK debug cuando toque entregarle una versión.
  - **PROHIBIDO manejar por adb los selectores de archivos del sistema (SAF).** Abren su Google Drive real; un verificador ya sobrescribió así un archivo suyo. El export y el restore por SAF los prueba Daniel a mano.
  - El extra `respaldoDebug` solo funciona en el build type `verif`.
- **Paralelismo:**
  - Daniel lo pidió y está autorizado. Las tareas independientes van en worktrees (`git worktree add ../AppNueva-tNN -b sdd-tNN HEAD` y copiar `android/local.properties`), y se integran por cherry-pick.
  - **Como máximo dos builds de Gradle a la vez.** Hay 32 GB de RAM y cada daemon usa `-Xmx3g`.
  - Los implementadores en worktree **no tocan `CuentasNavHost.kt` ni el teléfono**: el orquestador integra las líneas de rutas y verifica una sola vez.
- **Footer de commit:** `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`. Un implementador puso otro modelo y se corrigió con amend, y la regla quedó reforzada en `implementador-base.md`. Compruébalo en cada revisión.
- **Gradle:** siempre por `ctx_execute` con `grep`/`tail`. El hook bloquea `./gradlew` en Bash.
- **Antes de lanzar revisiones o verificaciones,** limpia los archivos sueltos que dejen los subagentes en la raíz del repo. Un revisor dejó `am.xml`/`nsc.java` de AOSP y se borraron.
- **Daniel puede escribir directamente a un subagente.** En T10 lo hizo para pedir más paletas. Confirma con él cualquier cambio de alcance que te llegue por esa vía.

## Decisiones de Daniel en esta sesión (no reabrir)

- **Fase 1 validada:** restauró su respaldo real y los conteos cuadran.
- **Dirección visual (sustituye D14):**
  - Estructura y fuente de la dirección «A · Libreta» (Bricolage Grotesque, barra de progreso en «Este mes»), con colores por **paletas conmutables**.
  - Paletas oscuras: Grafito menta, Grafito lima, Noche lima (por defecto), Como antes, Cacao y Pizarra cobalto.
  - Paletas claras: Menta claro, Lima claro, Noche claro (por defecto), Libreta y Cobalto claro.
  - La paleta clara y la oscura se eligen por separado.
  - El modo sigue al sistema, con opción manual en Ajustes y un botón sol/luna en Inicio.
  - La preferencia se guarda en DataStore, no en Room.
  - Todo está en `android/DESIGN.md`, que es la única fuente visual.
- **Personalizar la barra inferior y la fuente** queda fuera de alcance: issue https://github.com/DanCas03/cuentas-claras/issues/2.
- «La UI no debe inspirarse en la PWA, pero si se parece en algo no pasa nada».
- Pausa con T15 y T16 sin revisar: la próxima sesión empieza por ahí.

## Rulings del orquestador (resumen; la lista completa, unas 50 líneas `Ruling:`, está en el ledger)

Cada uno lleva su coste si resulta erróneo en el ledger. Los de más peso:

1. **E1-E16, tras el escaneo previo del plan** (tabla de 133 filas en `preflight-scan.md`): correcciones de nombres de test, expectativas y texto, la serie T12/T13/T15/T16, firmas ampliadas de componentes, try/catch en las escrituras, tests que de verdad prueban la reversión e indentación de 2 espacios en el respaldo.
2. **Formato del respaldo:** los `Double` enteros salen como `120.0` (la web escribe `120`). Se aceptó como benigno: la PWA lo lee igual.
3. **Tests de respaldo reforzados** (T7): igualdad completa e inclusión de las formas reales de la PWA. Eran plan-mandated y se ampliaron porque es el módulo donde perder datos sale más caro.
4. **Protección de datos** (T9):
   - el respaldo previo lleva fecha y hora, así que nunca se sobrescribe;
   - `restaurarConPrevio` es la única vía para importar;
   - existe la variante `verif`;
   - `respaldoDebug` solo funciona en verif.
5. **Deduplicación captura ↔ TXT** (E19 y E24): solo cuenta una captura si su `txId` existe; compara referencia, |monto| y **sentido**; cada captura marca como mucho una fila. Así se evita esconder un reverso del BNC.
6. **Paletas y DataStore** (E20-E23) y **fuentes desde assets** con `opsz`. En el Xiaomi, `Font(R.font…, variationSettings)` no aplicaba la variación.
7. **D19:** tras un guardado correcto que navega fuera, `guardando` no se repone. Así se evita el doble guardado durante el fade.
8. **Nuevo compacto** (T12): separaciones `e.s`, nota como chip, teclado de 52 dp y sin cabecera. El objetivo es que las 4 filas de gasto se vean sin desplazar, y está **pendiente de verificar en el teléfono**.
9. **Paralelismo con worktrees y cherry-pick:** T18 y T23 se adelantaron (no cambia el orden de entrega de las fases); T12, T13, T15 y T16 van de dos en dos.

## Menores diferidos relevantes para el cierre de la fase 2

Están en el ledger como `minor (deferred)`. Que el revisor de T17 los repase y decida cuáles se arreglan antes de entregar. Entre otros:
- `gradlew` sin bit ejecutable.
- `Stats.kt` lanza una excepción con una fecha corrupta.
- `montoEditable` falla con negativos.
- `SEPARADOR_DIAS` usa `\s` ASCII.
- La etiqueta clicable de los campos mide menos de 48 dp.
- Los respaldos previos solo son accesibles por adb (sugerencia: una lista en Ajustes).
- Faltan algunos tests de invariantes.

---

## Anexo: enmiendas E1-E29 (copia íntegra de `enmiendas.md`)

Prevalecen sobre el texto del plan/brief en lo que tocan. Cada despacho cita las que le afectan.

- **E1 (T3, T4, T6):** nombres de test entre backticks NO pueden contener `;` (ni `. [ ] / < > : \`) en JVM. Sustituye `;` por `,` (plan l. 1322 StatsTest, l. 1958 EventMatchTest, l. 3076 OpsTodoONadaTest).
- **E2 (T24 Paso 4):** con `respaldo-demo-v3.json` sembrado (`bncLastSaldoMinor = 50000`) el banner esperado es CONTINUO y, al haber movimientos BNC posteriores, el aviso «no es fiable», nunca «Crear ajuste». «Crear ajuste» se verifica con datos limpios (borrar datos de la app → importar fixture → descuadre +500,00).
- **E3 (T12):** la expectativa de monto negativo sigue a la web (`NuevoPage.tsx:186`) y a `formatMoney`: «Bs -12,50» / «-$12.50», no «−Bs 12,50».
- **E4 (T12, T13, T15, T16):** se ejecutan EN SERIE (todas editan `ui/nav/CuentasNavHost.kt`).
- **E5 (T29):** el `git add` incluye también `SemanaTarjeta.kt`, que la tarea modifica.
- **E6 (T10):** firmas de componentes ampliadas para usos posteriores, con defaults que no cambian los usos de T10: `Chip(…, enabled: Boolean = true, sugerido: Boolean = false)` (T30), `Selector(…, contentDescription: String? = null)` (T31), `CampoTexto(…, singleLine: Boolean = true, minLines: Int = 1)` (T21, T24).
- **E7 (T13, T16, T20, T24, T30):** restricción 9: toda operación de escritura async del ViewModel lleva `try/catch` que avisa con `e.message` (vía `Avisos.avisar`) — en concreto `alternarMoneda`, `cambiarFuente`, `borrarRegla`, `descartarCaptura` (las tres apariciones) y `analizar`. Donde haya botón asociado, también el guard `guardando`.
- **E8 (T17, T18/cierres):** el grep de cierre «`txsEfectivas` solo en InicioViewModel» se limita a `app/src/main/java/com/cuentasclaras/app/ui/`.
- **E9a (T6):** el test de reversión de `commitImport` provoca el fallo DESPUÉS de escribir reglas y antes del parche de cuenta (p. ej. `fallarAntesDe("UPDATE", "accounts")`), para que las aserciones sobre reglas y parche prueben algo.
- **E9b (T23):** el caso «ref "0" nunca coincide» incluye una captura `ReferenciaCapturada("0", 10)` (monto de la fila) para que el caso pruebe algo.
- **E10 (T7):** el respaldo se exporta con indentación de 2 espacios como `JSON.stringify(x, null, 2)` de la web: `JsonRespaldo` usa `prettyPrintIndent = "  "` (con `@OptIn(ExperimentalSerializationApi::class)`). El objetivo es compatibilidad total con la PWA en ambos sentidos; el orden de claves sigue el de las clases (como los tipos TS).
- **E11 (T20):** no tocar `CuentasNavHost.kt`; `InicioPantalla` obtiene el contenedor con `(LocalContext.current.applicationContext as CuentasApp).container` (o el patrón que ya use la pantalla en el código real; compruébalo con grep).
- **E12 (T6):** `MENSAJE_YA_IMPORTADO` se queda con el texto del plan (la web añade «(en otra pestaña/ventana)», que no aplica en nativo). Registrado como D18.
- **E13:** los casos de test extra del plan dentro de módulos de §9 (`ocurrenciasEnRango`, `sugerirParaLote`, `budgetStatus`, `detectGap`, exportación) se aceptan tal cual.
- **E14 (T23 Paso 1):** Node local es v23.3.0: ejecuta el script con `node --experimental-strip-types …`.
- **E15:** D10 confirmada (`calendario?dia={dia}&nuevo={nuevo}`).
- **E16 (T16, T26, T27, T29):** el bloque guard duplicado en 4 ViewModels se acepta como mandato del plan; si la revisión lo marca, se valora extraer un helper entonces.
- **E17 (T19):** en el listener, `contarRecientes(hash)` + `insertar(captura)` van dentro de un mismo `db.withTransaction { }` para que dos reemisiones casi simultáneas no creen dos capturas (Review Focus 5). El revisor de T19 debe comprobarlo.
- **E18 (T13, T29, T30, T31, T24):** `ocurrenciasEnRango`, `candidatasParaTx`, `sugerirOcurrencia` y `sugerirParaLote` reciben las transacciones CRUDAS, nunca `txsEfectivas()` (la web: `DashboardPage.tsx:28-29`). Restricción 7.
- **E19 (T19, T23):** tras restaurar un respaldo, la tabla `capturas` (fuera del respaldo) puede tener capturas REGISTRADA cuyo `txId` ya no existe. La deduplicación captura ↔ import (D8) solo cuenta una captura registrada si su `txId` existe en `transactions` (consulta con JOIN/EXISTS en el DAO de capturas, sin cambiar el esquema). Así un respaldo antiguo no hace que el TXT esconda movimientos reales.
- **E20 (D14 vetada por Daniel, 2026-10-15; afecta T10, T11, T13, T16 y todas las pantallas):** ya NO hay tema único fijo. Estructura, fuente (Bricolage Grotesque), radios y densidad de la dirección A comunes a todo; colores por **paletas**. Paletas oscuras: M1 Grafito menta, M2 Grafito lima, M3 Noche lima, M4 Como antes (+ las nuevas que explore T10). Paletas claras: Menta claro, Lima claro, Noche claro (+ nuevas). La paleta clara y la oscura se eligen POR SEPARADO. Modo: Sistema (por defecto) / Claro / Oscuro. Por defecto: oscuro = M3 Noche lima, claro = Noche claro. Persistencia en DataStore Preferences (ya en el catálogo; NO se toca el esquema Room). Las pantallas solo leen `CuentasTema.colores` (tokens de nombre idéntico en todas las paletas), así que no cambian.
- **E21 (T13):** botón «al alcance» para alternar claro/oscuro en la cabecera de Inicio (ícono sol/luna, `contentDescription` «Cambiar a modo claro»/«Cambiar a modo oscuro»); fija el modo manual opuesto al que se ve.
- **E22 (T16):** en Ajustes, sección «Apariencia»: Modo (Sistema/Claro/Oscuro con `Segmentado`), «Paleta oscura» y «Paleta clara» (selector con muestra de color de cada paleta).
- **E23 (T10 fuentes):** `Font(R.font…, variationSettings)` ignora la variación en el Xiaomi (Android 16): la fuente se carga desde `assets/` con `Font(ruta, assets, weight, variationSettings)` (verificado en el teléfono), fijando `opsz` 14 para texto y ~40 para títulos/cifras grandes.
- **E24 (T23, D8 endurecida; T18/T19):** una fila del TXT es «ya registrada por captura» si referencia normalizada (no vacía) + |monto| + SENTIDO coinciden (captura INGRESO ↔ fila con monto > 0; GASTO ↔ monto < 0), y cada captura marca COMO MUCHO una fila (se consume). `ReferenciaCapturada` lleva el tipo (proyección del DAO; sin cambio de esquema). Las capturas guardan `montoMinor` en POSITIVO (T18/T19 deben garantizarlo).
- **E25 (T15):** la pestaña «Movs» abre Movimientos SIN filtros, como la web (`#/movimientos`): en `BarraInferior.irAPestana` usa `restoreState = false` para Movimientos (o limpia la entrada con filtro), y los filtros que llegan por ruta se muestran como chips borrables.
- **E26 (T12):** `NuevoPantalla` NO añade `systemBarsPadding()`: el `Scaffold` raíz ya pasa los insets en `relleno`; si necesita `imePadding()`, primero `consumeWindowInsets(relleno)` para no duplicar el relleno. Verificar en el teléfono con el teclado del sistema abierto (nota) y cerrado.
- **E27 (todas las UI):** DESIGN.md §1 (barra de 64 dp, franja de 14 dp, píldora de tinte 56×32 dp) e `ic_importar` quedan aceptados como parte de la identidad (cambio funcional de T11).
- **E28 (T19, T21, T22 — hallazgos de la investigación T18, `docs/research/2026-10-15-notification-listener-miui.md`):** `requestRebind` solo deshace un `requestUnbind` propio (no es un remedio real); el re-enlace efectivo es alternar el componente con `setComponentEnabledSetting` + `DONT_KILL_APP`, las dos llamadas seguidas sin nada entre medias. El canal «Capturas BNC» nace con importancia HIGH (no se puede subir después). El listener llama a `CapturaRepo.registrar` (ya hace dedupe+insert en una transacción, E17). T21: autoinicio y bloqueo en Recientes no detectables; intent de autoinicio MIUI best-effort. T22: verificar que Android 15+ no redacta el texto del BNC (OTP) y si el listener vuelve tras matar la app.
- **E29 (T19, T22):** el hash de dedupe depende de qué extras concatena el listener: si una reemisión trae `EXTRA_BIG_TEXT` y otra no, los hashes difieren. T19 debe construir el texto de forma estable (p. ej. preferir BIG_TEXT si existe y si no TEXT, sin duplicar contenido; título + cuerpo) y la verificación de T22 reenvía la notificación de prueba variando extras, no solo el mismo tag. Ojo: en `cmd notification post`, `-S bigtext` va ANTES de `--bigtext`.
