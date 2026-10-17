# Cuentas Claras (app nativa)

App Android nativa de finanzas personales para un contexto bi-monetario (bolívares y dólares), escrita en
Kotlin con Jetpack Compose. Es el port de la PWA que vive en `src/`: las reglas de negocio son las mismas
(montos en céntimos, tasas, saldos, grupos, deudas, calendario), pero la interfaz es nueva y se añaden tres cosas
que una web no puede hacer: capturar las notificaciones del BNC, importar el TXT del BNC y guardar los datos en
una base Room local.

## Diferencias con la PWA

- Interfaz propia en Compose; no copia `estilos.css`. La fuente visual es `android/DESIGN.md`.
- Base de datos Room en el teléfono, sin servidor ni despliegue.
- Captura de pagos móviles a partir de las notificaciones del BNC.
- Import del TXT mensual del BNC, sin duplicar lo ya capturado.
- Respaldo JSON compatible con la PWA en ambos sentidos.
- La PWA ya no se despliega; esta app es la que se usa a diario.

## Requisitos

| Pieza | Versión |
| --- | --- |
| Android Studio | 2026.1.2 |
| JBR (JDK de Android Studio) | 21 (`org.gradle.java.home` en `android/gradle.properties`) |
| Gradle | 9.7.1 (lo descarga el wrapper) |
| Plugin de Android (AGP) | 9.3.3 |
| Kotlin | 2.4.21 |
| Plataforma | `android-37.0` (`compileSdk = 37`), `minSdk = 26`, `targetSdk = 35` |

Las versiones están centralizadas en `android/gradle/libs.versions.toml`. El código compila a bytecode Java 17.
Si Android Studio está instalado en otra ruta, ajusta `org.gradle.java.home` en tu `gradle.properties` local.

## Variantes

| Variante | Paquete | Uso |
| --- | --- | --- |
| `debug` | `com.cuentasclaras.app` | La de uso diario, con los datos reales. |
| `verif` | `com.cuentasclaras.app.verif` («Cuentas Claras (verif)») | Solo para verificaciones por adb, con datos aparte. Es la única que acepta el extra `respaldoDebug` (carga un JSON puesto en `filesDir`) y la única que además escucha notificaciones de `com.android.shell`, para simularlas. |
| `release` | `com.cuentasclaras.app` | Existe, sin minificar y sin firma de release. Fuera de alcance: no hay Play Store. |

## Compilar, instalar y probar

Desde `android/`:

```bash
./gradlew :app:assembleDebug          # compila la variante debug
./gradlew :app:testDebugUnitTest      # corre los tests unitarios
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Para la variante de verificación: `./gradlew :app:assembleVerif` y el APK queda en
`app/build/outputs/apk/verif/`.

### Qué cubren los tests

Lista cerrada (spec nativo §9), unos 50 casos entre el caso normal, el límite y el que históricamente falló.
No hay tests de fechas, builders, filtros, reglas, seeds, ajustes, ViewModels ni UI.

Archivos en `android/app/src/test/java/com/cuentasclaras/app/`:

- `domain/MoneyTest`, `RatesTest`, `BalancesTest`, `GroupsTest`, `StatsTest`, `CalendarTest`, `EventMatchTest`:
  dinero y redondeo, tasas, saldos, grupos, estadísticas, calendario y emparejamiento de eventos.
- `rates/ProvidersTest`: proveedores de tasas.
- `capture/ParseNotificacionBncTest`: parser de la notificación del BNC.
- `importar/ParseBncTxtTest` y `MapBncTest`: parser del TXT y su mapeo a movimientos.
- `backup/RespaldoTest`: exportar, validar e importar el respaldo.
- `data/OpsTodoONadaTest` (Robolectric, con `DbDePrueba`): las operaciones que deben ser todo o nada.
- `data/AparienciaRepoTest`, `data/IconoAppTest` y `ui/theme/FuentesTest` (Robolectric): las preferencias de
  apariencia y sus valores por defecto, el alias del ícono habilitado, y que cada fuente del catálogo existe y construye la tipografía.

## Estructura de paquetes

En `android/app/src/main/java/com/cuentasclaras/app/`:

- `domain/`: reglas de negocio puras (dinero, tasas, saldos, grupos, deudas, calendario, estadísticas). Sin Android ni Compose.
- `data/`: repositorios (`CuentasRepo`, `AparienciaRepo`) y datos iniciales.
  - `data/db/`: base Room (entidades, DAOs, mapeos, convertidores). El esquema está congelado; cualquier cambio exige `Migration`.
  - `data/ops/`: operaciones compuestas todo o nada (deudas, eventos, grupos, import).
- `backup/`: exportar e importar el respaldo JSON y el respaldo de seguridad previo.
- `rates/`: proveedores de tasas de cambio.
- `importar/` y `importar/bnc/`: parser del TXT del BNC y su mapeo a movimientos.
- `capture/`: servicio de escucha de notificaciones, parser, cola de capturas, avisos y guía de permisos de Xiaomi.
- `ui/theme/`: paletas, tipografía, formas, medidas y movimiento.
- `ui/componentes/`: piezas comunes (botones, campos, tarjetas, hojas modales, gráficos, diálogos).
- `ui/nav/`: rutas, barra inferior y `CuentasNavHost`.
- `ui/pantallas/`: una carpeta por pantalla, cada una con su ViewModel: `inicio`, `movimientos`, `nuevo`,
  `calendario`, `deudas`, `importar`, `ajustes`, `respaldo` y `permisos`.

## Respaldo compatible con la PWA

El respaldo es un JSON `schemaVersion` 3 (9 tablas, camelCase), el mismo de la PWA, en ambos sentidos: un
respaldo exportado por la PWA se importa en la app nativa y al revés. La tabla de capturas no va en el respaldo.

Antes de restaurar, la app valida el archivo y escribe un respaldo de seguridad del estado actual
(`cuentas-claras-respaldo-previo-<fecha>-<hora>.json`), del que conserva los 5 más recientes. Si ese respaldo
previo falla, la restauración no se hace.

Los respaldos de seguridad se ven en Ajustes → Respaldo → «Respaldos de seguridad», con su fecha y hora y un
botón «Restaurar» en cada uno (pide confirmación y, antes, guarda otro respaldo de seguridad de lo actual).

La app tiene `allowBackup="false"`: Android no copia sus datos a la nube. Al cambiar de teléfono o reinstalar,
solo se conservan los datos con el JSON exportado desde Ajustes → Respaldo → «Exportar respaldo».

## Captura de notificaciones del BNC

Un `NotificationListenerService` lee las notificaciones del BNC, extrae el pago móvil y lo deja como captura
pendiente en Inicio; el usuario la registra con dos toques. Requiere acceso a notificaciones, batería sin
restricciones, autoinicio (Xiaomi) y permiso de avisos en Android 13 o superior. La pantalla «Permisos» de la
app guía cada paso, y el detalle está en [`docs/guia-permisos.md`](../docs/guia-permisos.md).

## Import del TXT del BNC

Desde la pantalla «Importar» se elige el TXT mensual que exporta el BNC. La app lo parsea, lo mapea a
movimientos y muestra una vista previa antes de confirmar. Lo que ya llegó por captura de notificación no se
duplica. La confirmación se guarda de forma todo o nada.

## Diseño

`android/DESIGN.md` es la única fuente visual: tokens, radios, densidad, componentes y reglas de movimiento.
Desde Ajustes > Apariencia (pantalla propia) se eligen:

- **Modo** (Sistema, Claro u Oscuro) y una **paleta** oscura y otra clara del catálogo cerrado `PALETAS`
  (`ui/theme/Color.kt`). Por defecto `noche_lima` y `noche_claro`. Sin colores dinámicos ni paletas fuera del catálogo.
- **Fuente**, del catálogo `FUENTES` (`ui/theme/Fuentes.kt`): seis variables OFL en `assets/fuentes/`
  (Bricolage Grotesque por defecto, Inter, Manrope, Space Grotesk, DM Sans, Fraunces).
- **Diseño**, del catálogo `DISENOS` (`ui/theme/Diseno.kt`): Libreta (por defecto), Tinta, Isla y Aurora. Cambian
  formas, tarjetas, barra inferior, fondo y movimiento; solo los componentes base leen `CuentasTema.diseno`.
- **Nombres en la barra** (íconos solos si se apaga) e **ícono de la app** (Visto, Monedas o Libreta), coloreado con la
  paleta oscura elegida mediante `activity-alias` generados por `scripts/generar-iconos-app.py`.

Todo se guarda en el DataStore `apariencia` (`data/AparienciaRepo.kt`), fuera de Room y del respaldo.

## Privacidad

El repositorio no contiene datos reales: ni respaldos, ni TXT del BNC, ni capturas de pantalla. Los fixtures de
los tests son sintéticos.
