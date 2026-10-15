# Cuentas Claras Nativa — App Android en Kotlin + Jetpack Compose

**Fecha:** 2026-10-15 · **Estado:** diseño aprobado por Daniel en brainstorming (con un cambio: sin import de PDF de Zinli).
**Rama:** `nativa-compose` (desde `main` en `28310bb`, v3 de la PWA). El proyecto Android vive en `android/`; el código web se conserva en la rama como **referencia a portar**, no se ejecuta ni se mantiene aquí.
**Rama hermana:** `nativa-capacitor` contiene el diseño de la alternativa (cascarón nativo sobre la PWA). No se desarrolla por ahora.

## 1. Por qué

La PWA en Cloudflare Pages (`https://cuentas-claras-r2f.pages.dev`) dejó de responder (lo más probable: la cuenta universitaria donde vivía el proyecto se desactivó; Cloudflare Pages no vence por tiempo). La captura de notificaciones del BNC vía MacroDroid funcionó una semana y dejó de hacerlo (patrón típico de MIUI/HyperOS matando procesos en segundo plano o revocando el acceso a notificaciones).

Dos problemas, una solución: una app Android nativa que

1. **no depende de ningún hosting** (el código va en el APK, los datos en Room del dispositivo), y
2. **captura las notificaciones del BNC sin terceros** con un `NotificationListenerService` propio, con defensas específicas para Xiaomi.

Daniel eligió **Kotlin + Jetpack Compose puro** sobre Capacitor sabiendo que es una reescritura de ~5.400 líneas de TypeScript (más ~2.000 de tests), porque quiere una app Android de verdad y le interesa probar hasta dónde llega Claude Code en Android nativo.

## 2. Decisiones cerradas (Daniel, 2026-10-15 — NO reabrir)

1. **Kotlin + Compose puro.** Nada de WebView, nada de Capacitor en esta rama.
2. **Dos ramas, dos documentos.** `nativa-compose` (este spec, se desarrolla primero) y `nativa-capacitor` (solo diseño).
3. **Mismas funciones que la PWA v3, EXCEPTO el import del PDF de Zinli**, que queda fuera por decisión explícita («no lo voy a usar tanto»). El import del TXT del BNC sí se mantiene.
4. **Orden de fases:** (1) dominio + datos + respaldo, (2) Inicio/Nuevo/Movimientos/Ajustes, (3) captura de notificaciones, (4) import TXT BNC, (5) deudas y grupos, (6) calendario. Cada fase termina con un APK instalable en el teléfono de Daniel.
5. **Datos:** Daniel tiene un respaldo JSON v3 de hace ~1 mes y no le importa empezar de cero. La app importa ese formato tal cual; no hay migración de IndexedDB ni rescate de la PWA.
6. **Teléfono objetivo:** Xiaomi/Redmi/POCO (MIUI/HyperOS). Las defensas de batería y autoinicio son parte del alcance, no un extra.
7. **Tests solo los importantes.** La cobertura completa NO es un objetivo; el objetivo es que la app funcione bien. No se porta la suite de Vitest uno a uno. Se escriben tests únicamente donde un fallo cuesta dinero o datos (ver §9).

## 3. Decisiones de diseño (tomadas por el agente con defaults razonables; Daniel puede vetarlas)

1. **Proyecto en `android/` dentro del mismo repo**, para que el implementador cruce cada archivo Kotlin con su original TypeScript sin cambiar de rama.
2. **Identificadores portados 1 a 1.** Funciones, tipos, ids de seeds y nombres de tabla se llaman igual que en la web (`txsEfectivas`, `sugerirOcurrencia`, `parseBncTxt`, `acc-bnc`, `cat-sueldo`). Convención heredada: dominio en inglés donde la web lo tenía en inglés, UI y operaciones en español donde la web las tenía en español.
3. **Formato de respaldo idéntico** (`schemaVersion: 3`, 9 tablas, mismos nombres de campo en camelCase). El JSON que exporta la app nativa lo lee la PWA y viceversa. Acepta v2 normalizado y rechaza v1, igual que `backup.ts`.
4. **Sin Hilt.** Contenedor manual `AppContainer` creado en `Application`. ViewModels reciben repositorios por constructor vía `viewModelFactory`.
5. **Sin librería de gráficas.** Dona y barras con `Canvas` de Compose.
6. **Sin WorkManager.** Las tasas se refrescan una vez al día al abrir la app, como en la web (`refreshRatesOncePerDay`).
7. **Tema oscuro fijo** con la paleta de `estilos.css` y Manrope empaquetada como `FontFamily`. Material 3 para componentes; identidad visual propia, no colores dinámicos.
8. **Captura persistente, no un deep link.** La notificación se guarda en Room ANTES de avisar al usuario. Si la app o el sistema matan el proceso, la captura ya está en disco.
9. **Deduplicación captura ↔ import cerrada:** la captura guarda la referencia del BNC y el import marca duplicada toda fila cuya referencia coincida con un movimiento ya registrado desde captura (ver §7.4).
10. **Tests mínimos en JVM** (JUnit; Robolectric para Room solo donde haga falta), limitados a la lista de §9. UI sin tests automáticos; verificación por capturas de emulador o teléfono por USB.

## 4. Restricciones vigentes (heredadas de v1/v2/v3)

- Montos **siempre enteros en céntimos** (`Long`). Formato visual venezolano: `Bs 13.517,75` / `$84.20`. Dígitos tabulares en cifras.
- Fechas **locales** como `String` `yyyy-mm-dd`; horas `HH:mm:ss`. Nunca convertir a UTC. `java.time.LocalDate` solo como herramienta interna; persistir y exponer strings.
- Todo en español: UI, docs, commits, mensajes de error.
- Registro manual de un gasto en **≤ 3 toques** desde Inicio.
- `txsEfectivas()` alimenta SOLO agregaciones; los saldos usan transacciones crudas. Ids `virtual-…` jamás se persisten.
- Operaciones compuestas (cerrar deuda, agrupar, asignar evento, importar respaldo, confirmar import) son **todo-o-nada** en una transacción de Room (`@Transaction` o `withTransaction`).
- Guard contra doble guardado en TODO guardado asíncrono (estado `guardando` en el ViewModel, botones deshabilitados).
- Capas `domain/`, `data/`, `import/`, `capture/`, `rates/`, `backup/` **sin dependencia de Compose**. `domain/` además sin dependencia de Android (JVM puro).
- Privacidad: los comprobantes reales de la raíz están gitignored. Las fixtures son las anonimizadas de `src/**/fixtures/`, copiadas a `android/app/src/test/resources/`. Ningún dato real en código, tests ni docs. Ningún keystore en git.

## 5. Stack y entorno

| Pieza | Elección | Nota |
|---|---|---|
| Lenguaje / build | Kotlin 2.x, Gradle Kotlin DSL, catálogo `gradle/libs.versions.toml`, AGP compatible con el Android Studio instalado | KSP para Room |
| UI | Jetpack Compose (BOM), Material 3, Navigation Compose | Un solo `Activity` |
| Datos | Room 2.7+ | 9 entidades + tabla `capturas` |
| Serialización | kotlinx.serialization | Respaldo JSON, `recurrence`, `omitidas`, respuestas de tasas |
| Red | OkHttp | Sin CORS. Timeout 8 s como en la web |
| Archivos | Storage Access Framework (`ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT`) | Respaldo y TXT del BNC. Sin permisos de almacenamiento |
| Notificaciones | `NotificationListenerService` + `NotificationCompat` | Canal propio «Capturas BNC» |
| Tests | JUnit 4 + kotlin.test, Robolectric para Room, Truth o asserts de kotlin.test | `./gradlew :app:test` sin emulador |
| minSdk / targetSdk | 26 / 35 | `java.time` nativo sin desugaring |
| Paquete | `com.cuentasclaras.app` | |

Entorno verificado en la PC de Daniel (2026-10-15): SDK en `%LOCALAPPDATA%\Android\Sdk` con plataformas 34–37.1 y build-tools 36.0.0/37.0.0; JDK 21 del Android Studio (`jbr`) y JDK 17 Adoptium en el PATH; `adb` operativo; **no hay AVD creado ni teléfono conectado** (el plan debe crear el emulador o pedir el teléfono por USB).

## 6. Arquitectura

```
android/app/src/main/java/com/cuentasclaras/app/
  domain/      tipos, money, dates, rates, balances, stats, rules, groups, debts,
               calendar, eventMatch, filters, builders     ← Kotlin puro, JVM
  data/        Room: entidades, DAOs, CuentasDb, seeds, ops (debtOps, groupOps,
               eventOps, commitImport), repositorios
  importar/    bnc/parseBncTxt, bnc/mapBnc
  capture/     parseNotificacionBnc, BncNotificationListener, CapturaRepo,
               notificador propio, PermisosXiaomi
  rates/       providers (dolarapi → vcoud), refreshRatesOncePerDay
  backup/      BackupFile, exportar, validar, importar
  ui/          theme/, nav/, componentes/, pantallas/ (una carpeta por pantalla
               con su ViewModel)
  CuentasApp.kt (Application + AppContainer), MainActivity.kt
```

Flujo: pantalla → ViewModel (`StateFlow` de estado de UI) → repositorio → DAO. Los DAOs exponen `Flow` para lecturas reactivas (equivalente de `useLiveQuery`). El dominio recibe listas ya cargadas y devuelve valores; nunca toca la base.

### 6.1 Modelo de datos (Room)

Mismas 9 tablas y campos que `src/domain/types.ts` y `src/db/db.ts`:

| Tabla | PK | Índices | Notas de mapeo |
|---|---|---|---|
| `accounts` | `id` | — | `kind` y `currency` como `String` con los mismos literales |
| `transactions` | `id` | `date`, `accountId`, `categoryId`, `importKey`, `groupId`, `eventId` | `amountMinor: Long`, opcionales nullables |
| `categories` | `id` | — | `monthlyBudgetMinor` siempre en céntimos USD |
| `rates` | `(date, source)` | `date` | `vesPerUsd: Double` |
| `rules` | `id` | — | |
| `settings` | `id` ('main') | — | |
| `debts` | `id` | `status` | |
| `txGroups` | `id` | — | |
| `calEvents` | `id` | — | `recurrence` y `omitidas` como JSON string (kotlinx.serialization) |
| **`capturas`** (nueva, nativa) | `id` | `estado`, `referencia` | No forma parte del respaldo v3. Ver §7.3 |

Seeds idénticos a `db.ts` (5 cuentas, 15 + 3 categorías, settings) con la misma regla: sembrar si vacío, y reponer por id las categorías v2 si faltan.

### 6.2 Navegación

Un `NavHost` con rutas: `inicio`, `nuevo?tipo&monto&cuenta&nota&eventId&eventDate&capturaId`, `movimientos?categoria&mes&cuenta`, `importar`, `ajustes`, `deudas`, `calendario?mes`, `permisos`. Barra inferior con Inicio, Movs, FAB «+», Importar, Ajustes (misma `Nav.tsx`). Deudas, Calendario y Permisos se alcanzan desde tarjetas de Inicio o desde Ajustes, como hoy.

## 7. Funcionalidades

### 7.1 Fase 1 — Dominio, datos y respaldo

- Portar íntegro `domain/` (`money`, `dates`, `rates`, `balances`, `stats`, `rules`, `groups`, `debts`, `calendar`, `eventMatch`, `filters`, `builders`). Los tests de Vitest sirven como guía de casos límite al portar, pero solo se escriben tests para los módulos de la lista de §9.
- Room con el esquema de §6.1, seeds, `getSettings`/`saveSettings`.
- Operaciones todo-o-nada: `settleDebt`, `agruparTxs`/`anadirAGrupo`/`quitarDeGrupo`/`desagrupar`/`actualizarGrupo`, `guardarEvento`/`eliminarEvento`/`asignarEvento`/`desasignarEvento`/`omitirOcurrencia`/`reanudarOcurrencia`, `commitImport` con su backstop de `importKey`.
- Respaldo: `exportBackup`, `validateBackup` (v3 exacto, v2 normalizado, v1 rechazado), `importBackup` (reemplaza TODO en una transacción), `backupFilename`. SAF para guardar y abrir.
- Entregable: APK de depuración con una pantalla mínima «Importar respaldo» que carga el JSON de Daniel y muestra conteos por tabla.

### 7.2 Fase 2 — Pantallas principales

- **Inicio:** patrimonio con slider de 3 vistas (a disposición / ahorro / total neto con deudas), «Este mes» expandible con navegación de mes y lista de todas las categorías, Comparativa de últimos meses, Presupuestos, tarjeta Deudas (totales y recientes), tarjeta «Esta semana» (placeholder hasta la fase 6; muestra «Sin eventos» y enlace deshabilitado).
- **Nuevo:** gasto/ingreso/transferencia/ajuste, teclado numérico propio, grid de categorías, selector de cuenta con `lastAccountId`, prefill por parámetros de ruta. Reglas aprendidas al guardar (misma lógica `buildRule`/`autoCreated`).
- **Movimientos:** lista con filtros (categoría, mes, cuenta), editor de movimiento, selección por pulsación larga (prepara la fase 5), grupos visibles como una fila con residuo.
- **Ajustes:** cuentas (alta, archivar, `isSavings`, `enDisposicion`), categorías (alta, color, emoji, presupuesto), tasas (BCV online, Binance manual, override, «vigente hoy»), reglas, respaldo (exportar/importar), enlace a Permisos.
- Entregable: APK de uso diario sin captura ni imports.

### 7.3 Fase 3 — Captura de notificaciones del BNC

**Servicio.** `BncNotificationListener : NotificationListenerService`. En `onNotificationPosted`, si `packageName == "bnc.bncnet.mobile2"` (paquete tomado de la captura de pantalla de Daniel; **el plan debe incluir un paso para confirmarlo con `adb shell pm list packages | grep -i bnc`** en su teléfono), concatena `EXTRA_TITLE`, `EXTRA_TEXT` y `EXTRA_BIG_TEXT`, y:

1. Calcula `hash = sha256(texto normalizado)`. Si ya existe una captura con ese hash en las últimas 24 h, ignora (el BNC y MIUI pueden reemitir la misma notificación).
2. Corre `parseNotificacionBnc` (portado de `src/capture/parseNotificacionBnc.ts` con sus fixtures). Reconoce «Pago Movil Recibido» (ingreso) y «Compra POS» (gasto); el resto queda como `reconocida = false`.
3. Inserta en `capturas`: `id`, `recibidaIso`, `textoCrudo`, `reconocida`, `tipo?`, `montoMinor?`, `referencia?`, `nota?`, `estado = 'pendiente'`, `hash`.
4. Publica una notificación propia en el canal «Capturas BNC»: título «Pago móvil recibido Bs 1.250,00» (o «Compra POS…», o «Notificación del BNC sin reconocer»), texto «Toca para registrar», `PendingIntent` a `nuevo?capturaId=…`. Prioridad alta, no persistente.

**Nunca guarda un movimiento por sí mismo.** Registrar exige la confirmación en Nuevo.

**Pantalla Nuevo con `capturaId`.** Pre-llena tipo, monto, cuenta `acc-bnc` y nota (`Pago móvil recibido · ref 123456`). Al guardar, marca la captura `estado = 'registrada'` y guarda `txId`. Botón «Descartar» → `estado = 'descartada'`. Si no fue reconocida, abre Nuevo en blanco con el texto crudo en la nota (máx. 200 caracteres), igual que `CapturaPage.tsx`.

**Inicio.** Tarjeta «Capturas pendientes (n)» cuando `n > 0`, con la lista y acceso directo a registrar o descartar cada una. Banner rojo «La app perdió el acceso a notificaciones» cuando `NotificationManagerCompat.getEnabledListenerPackages` no incluye la app, con botón a Permisos.

**Pantalla Permisos** (lista de verificación con estado detectado donde sea detectable y botón que abre el ajuste):

1. Acceso a notificaciones → `ACTION_NOTIFICATION_LISTENER_SETTINGS`. Detectable.
2. Batería «Sin restricciones» → `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` con el paquete. Detectable con `PowerManager.isIgnoringBatteryOptimizations`.
3. Autoinicio (MIUI) → intento best-effort de abrir `com.miui.securitycenter/com.miui.permcenter.autostart.AutoStartManagementActivity`; si falla, instrucciones manuales. No detectable.
4. Bloquear la app en Recientes → instrucciones con imagen esquemática. No detectable.
5. Permiso `POST_NOTIFICATIONS` (Android 13+) → `requestPermission`. Detectable.
6. Botón «Probar captura»: campo de texto donde pegar una notificación y ver qué reconoce el parser, sin guardar nada.

**Re-enlace.** En `MainActivity.onResume`, si el acceso está concedido pero el servicio no reporta conexión reciente (timestamp en DataStore actualizado en `onListenerConnected`), llamar a `NotificationListenerService.requestRebind` con el `ComponentName` del servicio y, como segundo recurso, alternar el estado del componente con `PackageManager.setComponentEnabledSetting`.

Entregable: APK que Daniel valida en su Xiaomi con una notificación real del BNC (y con «Probar captura» mientras no llega ninguna).

### 7.4 Fase 4 — Import del TXT del BNC

- Portar `parseBncTxt` (meta, filas, no parseadas, `validateChain`), `mapBncRows` (`bncImportKey`, comisiones, `pairRef`, reglas, categoría por defecto) y `detectGap`/`newestRow` con las fixtures anonimizadas.
- Pantalla Importar: abrir TXT por SAF, vista previa con checkbox por fila, select de categoría, marca de duplicado, banner de hueco respecto al último import, banner «Crear ajuste» post-import con los mismos guards de la web.
- **Deduplicación nueva:** antes de mapear, cargar las referencias de las capturas con `estado = 'registrada'`; toda fila cuya `referencia` coincida se marca `duplicate = true` con motivo «ya registrada por captura». Confirmar el import mantiene el backstop de `importKey` dentro de la transacción.
- Fuera: Zinli PDF (decisión cerrada 3). La pantalla Importar solo ofrece BNC.

### 7.5 Fase 5 — Deudas y grupos transaccionales

- Deudas: pestañas por pagar / por cobrar / historial, alta, cierre transaccional idempotente (`settleDebt`), tarjeta en Inicio, total neto con deudas en el patrimonio.
- Grupos: agrupar desde la selección múltiple de Movimientos, editor de grupo (nota, categoría del vuelto), añadir/quitar/desagrupar, residuo virtual por moneda en las agregaciones (`residuosDeGrupo`, `categoriaDelResiduo`, `txsEfectivas`).

### 7.6 Fase 6 — Calendario de eventos

- Pantalla Calendario (`celdasMes`, ocurrencias derivadas con `ocurrenciasEnRango` y estados hecha > omitida > vencida > pendiente), editor de evento (`EditorEvento`: título, categoría obligatoria, cuenta y monto opcionales, fecha, repetición una vez/semanal/mensual/quincenal/anual, fin, «Terminar hoy»), omitir/reanudar ocurrencias.
- Tarjeta «Esta semana» real en Inicio. Chips de eventos en Nuevo con guardado y asignación en un toque. `SelectEvento` en el editor de movimientos. Sugerencia de evento en la vista previa del import (gana a la regla aprendida).

## 8. Manejo de errores

- Fallo al abrir Room al arrancar: pantalla de error con «Cierra y vuelve a abrir la app», como `App.tsx`.
- Sin red o proveedor caído: silencioso; Ajustes muestra la tasa vigente con su fecha.
- Respaldo inválido: mensaje con la tabla faltante o la versión no soportada; nada se toca.
- Permiso de notificaciones perdido: banner en Inicio, nunca un crash.
- Toda excepción en el listener se captura y registra (`Log.w`); el servicio nunca muere por un texto raro.

## 9. Testing y verificación

**Regla (decisión cerrada 7):** la cobertura no es un objetivo. Se escriben tests solo donde un fallo cuesta dinero o datos, con pocos casos cada uno (el caso normal, el límite y el que históricamente falló). Todo lo demás se verifica usando la app.

**Con tests (lista cerrada):**

| Módulo | Qué se prueba | Por qué |
|---|---|---|
| `money` | `parseVenezuelanAmount`, `formatMoney` (Bs y USD) | Un error de coma o de miles altera montos |
| `rates` | `convertMinor` ida y vuelta, `effectiveRate` con override | Convierte dinero |
| `balances` | saldo por cuenta con transferencias cruzadas y ajustes; patrimonio en 3 vistas | Es el número principal de Inicio |
| `groups` | `residuosDeGrupo`, `categoriaDelResiduo`, `txsEfectivas` excluye agrupadas | Doble conteo si falla |
| `stats` | `monthSummary` con grupos y residuos | Alimenta «Este mes» |
| `calendar` | `fechasDeEvento` para cada recurrencia (mensual con 31 = último día, quincenal), `estadoDe` | Fechas erróneas = pagos olvidados |
| `eventMatch` | `sugerirOcurrencia` dentro y fuera de ventana y tolerancia | Asigna movimientos solo |
| `parseNotificacionBnc` | las fixtures anonimizadas completas | Es la pieza nativa clave |
| `parseBncTxt` + `mapBnc` | fixture anonimizada, `bncImportKey`, duplicados por `importKey` y por referencia de captura | Duplicar movimientos cuesta dinero |
| `backup` | validar v3/v2/v1, importar reemplaza todo y es todo-o-nada ante JSON corrupto | Perder datos |
| `commitImport`, `settleDebt`, `agruparTxs`/`desagrupar`, `asignarEvento`/`eliminarEvento` | la transacción revierte entera ante un fallo a mitad (Robolectric + Room en memoria) | Estados a medias |

**Sin tests:** `dates` (helpers), `builders`, `filters`, `rules`, seeds, settings, `rates/providers` (solo los dos parsers de JSON con fixture, un caso cada uno), ViewModels y toda la UI.

- **Fixtures:** copiadas de `src/import/bnc/fixtures/bncFixture.ts` y `src/capture/fixtures/notificacionesBnc.ts` a recursos de test (texto plano o JSON).
- **UI:** sin tests automáticos. Verificación bloqueante por fase: `./gradlew :app:assembleDebug`, instalar con `adb install -r`, capturas con `adb exec-out screencap -p` guardadas en `.superpowers/sdd/capturas/`. Emulador: el plan incluye crear un AVD (Pixel, API 35, imagen x86_64) con `sdkmanager` + `avdmanager`; si Daniel conecta el Xiaomi por USB con depuración, se prefiere el teléfono.
- **Criterio de éxito global:** Daniel importa su respaldo, registra un gasto en ≤ 3 toques, recibe un pago móvil real y lo registra desde la notificación en 2 toques, e importa un TXT mensual sin duplicar lo capturado.

## 10. Fuera de alcance

Import de PDF Zinli (decisión de Daniel), Play Store, firma de release (se añade cuando Daniel lo pida; APK de depuración por ahora), widgets, tema claro, sincronización en nube, refresco de tasas en segundo plano, tasa Binance online, iOS.

## 11. Referencias en el repo (para quien escriba el plan)

- Specs anteriores: `docs/superpowers/specs/2026-07-07-finanzas-personales-design.md` (v1), `2026-07-22-cuentas-claras-v2-design.md` (v2), `2026-09-04-cuentas-claras-v3-design.md` (v3). Mandan en el *qué* de cada función; este spec manda en el *cómo* nativo.
- Planes anteriores (formato de referencia): `docs/superpowers/plans/2026-09-04-cuentas-claras-v3-implementacion.md`.
- Código a portar: todo `src/` salvo `src/import/zinli/` y `src/ui/componentes/ImportZinli.tsx`.
- Formatos del BNC: `src/import/bnc/parseBncTxt.ts` y su fixture; notificaciones en `src/capture/fixtures/notificacionesBnc.ts`.
- Tasas: `docs/spikes/2026-07-07-cors-tasas.md` (URLs y campos; el CORS ya no aplica).
- Paleta y tipografía: `src/ui/estilos.css` (bloque `:root`).
