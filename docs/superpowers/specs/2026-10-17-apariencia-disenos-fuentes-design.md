# Apariencia ampliada: Ajustes plegable, gráficas de ingresos, fuentes, diseños e íconos

**Fecha:** 2026-10-17 · **Estado:** diseño aprobado por Daniel en brainstorming (chat), con dos añadidos suyos: íconos de app elegibles que siguen el color de la paleta oscura, y verificación mínima.
**Rama:** `nativa-compose`. Cierra el issue #2 («Nativa: personalizar la barra de navegación y la fuente desde Ajustes»).
**Spec padre:** `2026-10-15-cuentas-claras-nativa-compose-design.md` (sigue mandando en todo lo que aquí no se toca). Fuente visual: `android/DESIGN.md`, que esta entrega enmienda (§7 de este documento).

## 1. Qué pidió Daniel

1. En Ajustes, todas las secciones **plegables** para no tener que desplazarse hasta el final. Apariencia sale a **una pantalla propia**.
2. En «Este mes», al tocar **Ingresos** se ven también **gráficas de los ingresos** (hoy solo hay dona y curva de gastos).
3. En Apariencia, lo del issue #2: **elegir la fuente** y tener **distintos diseños**, conmutables **dentro de la app**. Entre los diseños: uno hecho **antes** de invocar las skills de diseño, otro **después**, y uno con **animaciones extravagantes** «que realmente impresione».
4. **Un par de íconos de app más** para elegir, y que el ícono **cambie de color con la paleta oscura** elegida.
5. Verificación **estrictamente necesaria**: compilar, tests JVM existentes y una sola pasada en el teléfono del flujo tocado. Daniel valida el resto a mano.

## 2. Decisiones cerradas (NO reabrir)

1. Fuente y diseño son **independientes**: un diseño no cambia la fuente ni la paleta.
2. Todas las preferencias nuevas van al **mismo DataStore `apariencia`**. Room y el respaldo JSON no se tocan.
3. **Libreta** (el diseño actual) queda intacto como diseño por defecto. Nada de lo que ya se ve cambia si Daniel no toca Apariencia.
4. El diseño **Aurora** rompe a propósito las prohibiciones de DESIGN.md §5 y §8 (bucles, brillos, animaciones > 300 ms). Es una excepción declarada, no un descuido.
5. El ícono de la app cambia por **alias de actividad** (`activity-alias`), uno por combinación ícono × paleta oscura. Android puede tardar unos segundos en refrescar el lanzador y Xiaomi puede mostrar un aviso al cambiarlo; aceptado.
6. Fuentes **solo variables y OFL**, empaquetadas en `assets/fuentes/` y cargadas con `Font(path, assetManager, …)` (lección E23: `Font(R.font…)` ignora la variación en el Xiaomi).

## 3. Ajustes plegable y pantalla Apariencia

- Componente nuevo **`SeccionPlegable(titulo, abierta, onAlternar, contenido)`** en `ui/componentes/`: una `Tarjeta` cuya cabecera entera es el objetivo táctil (≥ 48 dp, `Role.Button`, `stateDescription` «desplegada» / «plegada»), con `ic_expandir` a la derecha que gira 180° (`Movimiento.transicion(MEDIO)`). El contenido aparece con alfa + traslación vertical de `espacios.s` (`Movimiento.LARGO`), nunca animando el alto. Cerrada no compone el contenido.
- `AjustesPantalla`: Cuentas, Categorías, Tasas, Reglas, Respaldo y Permisos pasan a `SeccionPlegable`, **todas cerradas al entrar**; el estado de cada una es `rememberSaveable` con clave por sección (sobrevive a la rotación, no al reinicio: no se persiste).
- La primera fila de Ajustes es **«Apariencia ›»** (`FilaConFinal` con `ic_expandir` girado −90°, objetivo de 48 dp), que navega a `Rutas.APARIENCIA = "apariencia"`.
- **`AparienciaPantalla(nav)`** en `ui/pantallas/apariencia/`: cabecera con botón atrás (el mismo patrón que `PermisosPantalla`) y título «Apariencia»; `LazyColumn` con estos grupos, cada uno en su `Tarjeta`: **Modo**, **Paleta oscura**, **Paleta clara** (los tres movidos tal cual de `AparienciaSeccion`), **Fuente**, **Diseño**, **Barra** e **Ícono**. `AparienciaSeccion.kt` desaparece; `AparienciaViewModel` crece con los métodos nuevos.

## 4. Gráficas de ingresos («Este mes»)

- Dominio: `ResumenMes` gana **`dailyCumulativeIncome: List<PuntoDiario>`**, calculado en `monthSummary` igual que `dailyCumulative` pero con `TxType.INCOME` (un ingreso con fecha corrupta se salta sin tumbar el resumen, como los gastos). Test en `StatsTest`: un mes con dos ingresos en días distintos da el acumulado esperado y no toca el de gastos.
- `Dona` y `CurvaAcumulada` reciben **`descripcion: String`** (el prefijo del `contentDescription`, hoy fijo: «Gráfica de dona de gastos por categoría», «Gráfica de gasto acumulado del mes») para que la variante de ingresos se anuncie bien.
- UI: las dos columnas **«Ingresos» / «Gastos»** de `TarjetaEsteMes` pasan a ser **pestañas** (`selectable`, `Role.Tab`, `selectableGroup`): la activa lleva tinte `primario` al 14 % y radio `control`; el estado es `rememberSaveable`, por defecto **Gastos**. Con Gastos activo todo sigue igual. Con Ingresos activo: rótulos «INGRESO POR CATEGORÍA» y «INGRESO ACUMULADO», dona de `incomeByCategory` y curva de `dailyCumulativeIncome`, y el vacío «Sin ingresos este mes» / «Sin ingresos en {mes}» (`Vacio("💰", …)`). La lista desplegable «Ver todas las categorías» no cambia.
- La línea «Gastaste el N %» y el balance no dependen de la pestaña.

## 5. Fuentes

`ui/theme/Fuentes.kt`: catálogo cerrado `FUENTES: List<Fuente>` con `Fuente(id, nombre, archivo, opszTexto: Float?, opszDisplay: Float?, pesosTexto, pesosDisplay)`. `fuentePorId(id?)` devuelve Bricolage si el id no existe (como `paletaPorId`).

| id | Nombre | Archivo en `assets/fuentes/` | Ejes |
|---|---|---|---|
| `bricolage` ★ | Bricolage Grotesque | `bricolage_grotesque_variable.ttf` (ya existe) | opsz 14 / 40, wght |
| `inter` | Inter | `inter_variable.ttf` | opsz 14 / 32, wght |
| `manrope` | Manrope | `manrope_variable.ttf` | wght |
| `space_grotesk` | Space Grotesk | `space_grotesk_variable.ttf` | wght |
| `dm_sans` | DM Sans | `dm_sans_variable.ttf` | opsz 14 / 40, wght |
| `fraunces` | Fraunces (serif) | `fraunces_variable.ttf` | opsz 14 / 72, wght |

- Origen: `github.com/google/fonts/tree/main/ofl/<familia>` (archivos `[…wght].ttf`). Cada licencia se guarda como `assets/fuentes/OFL-<id>.txt`. Si en la implementación un archivo no está disponible o pesa más de 1 MB, se omite esa fuente de la tabla y se anota en DESIGN.md; el catálogo final puede tener 4-6 fuentes, nunca menos de 4.
- `tipografiaCuentas(assets, fuente)` construye las dos familias (texto / display) con los `opsz` de la tabla cuando la fuente tiene el eje, y sin él cuando no. Los tamaños, pesos e interlíneas de DESIGN.md §3 no cambian por fuente. `cifra` y `cifraGrande` mantienen `tnum`.
- Selector en Apariencia: una fila por fuente con el nombre pintado **en esa misma fuente** y, debajo, «Bs 13.517,75 · $84.20» en su `cifra`, para ver los dígitos. Fila seleccionable con visto, como las paletas.
- Material 3 hereda la fuente elegida vía `comoMaterial()`.

## 6. Diseños

### 6.1 Tokens

`ui/theme/Diseno.kt`:

```kotlin
enum class EstiloTarjeta { BISEL, PLANA, CONTORNO, AURORA }
enum class EstiloBarra { PILDORA, SUBRAYADO, FLOTANTE, AURORA }
enum class FormaMas { DISCO, CUADRADO, GOTA }
enum class EstiloFondo { LISO, AURORA }
enum class NivelMovimiento { CONTENIDO, EXPRESIVO }

data class Diseno(
    val id: String, val nombre: String, val descripcion: String,
    val formas: Formas,
    val tarjeta: EstiloTarjeta, val barra: EstiloBarra, val botonMas: FormaMas,
    val fondo: EstiloFondo, val movimiento: NivelMovimiento,
    val escalaPresion: Float,          // 0,97 en Libreta
)
val DISENOS: List<Diseno>; fun disenoPorId(id: String?): Diseno  // Libreta si no existe
```

- `CuentasTema` expone **`CuentasTema.diseno`** y `LocalFormas` pasa a salir de `diseno.formas`. `Movimiento` gana `resorte(nivel)` y `transicion(duracion, nivel)`: con `EXPRESIVO` el resorte es más rebotón (`dampingRatio 0.45`) y las duraciones suben un 50 %.
- Quién lee `diseno`: `Tarjeta` (estilo), `BarraInferior` (estilo, forma del «+», etiquetas), `BotonBase` y `Chip` (escala de presión y resorte), `Segmentado` (forma), `RaizApp` (fondo), `HojaModal` (radio superior sale de `formas`). Ninguna pantalla lee `diseno` directamente.

### 6.2 Catálogo

| id | Nombre | Cuándo se diseña | Tarjeta | Barra | «+» | Fondo | Movimiento |
|---|---|---|---|---|---|---|---|
| `libreta` ★ | Libreta | ya existe | BISEL (doble bisel, 20/16 dp) | PILDORA (actual) | DISCO | LISO | CONTENIDO, 0,97 |
| `tinta` | Tinta | **antes** de invocar skills | PLANA: sin bisel, `superficie` plana con radio 8 dp y un filete de 1 dp en `borde` solo abajo | SUBRAYADO: sin píldora; subrayado de 2 dp en `primario` bajo el ícono activo, texto siempre visible | CUADRADO (radio 12 dp) | LISO | CONTENIDO, 0,98 |
| `estudio` (id provisional) | se nombra tras las skills | **después** de invocar `high-end-visual-design` y `ui-ux-pro-max` (mobile) | CONTORNO: superficie con contorno de 1 dp degradado (`texto` 10 % → 0) y radio 24 dp, sin bisel | FLOTANTE: barra despegada del borde (margen `espacios.m`, píldora completa, `superficieElevada`) | GOTA (disco que se integra en la barra flotante) | LISO | CONTENIDO, 0,96 |
| `aurora` | Aurora | el extravagante | AURORA: contorno de degradado de barrido que **gira en bucle** (8 s) con `primario`/`ingreso`/`aviso` | AURORA: píldora activa que **se desliza** entre pestañas y «+» con **halo que respira** (escala 1 → 1,25, alfa 0,35 → 0, 2,4 s en bucle) | DISCO con halo | AURORA: tres manchas radiales de la paleta (`primario`, `ingreso`, `aviso` al 18 %) que **derivan en bucle lento** (20-30 s) tras el contenido, con `blur` en API ≥ 31 | EXPRESIVO, 0,90; las tarjetas **entran en cascada** (alfa + traslación, 60 ms de retardo por tarjeta) |

- Lo que el implementador aprende de las skills para `estudio` (tipografía, espaciado, jerarquía) se aplica **solo** a ese diseño y se resume en 5 líneas en DESIGN.md §2.3, para que Daniel vea qué cambió por usarlas. Si la skill recomienda algo que rompe DESIGN.md (sombras negras, texto `primario` sobre relleno), no se aplica.
- Aurora respeta la accesibilidad base: contraste del texto intacto (las manchas van al 18 % y detrás de las tarjetas), objetivos táctiles iguales, nada parpadea más de 3 veces por segundo, y las animaciones **nunca bloquean la entrada**. Con «Quitar animaciones» del sistema (`Settings.Global.ANIMATOR_DURATION_SCALE = 0`) los bucles se detienen.
- Selector en Apariencia: una tarjeta por diseño con **miniatura** (mini tarjeta + mini barra pintadas con los tokens del diseño, `CuentasTemaConDiseno`), nombre, descripción de una línea y visto.

### 6.3 Barra

- Interruptor **«Nombres en la barra»** (`etiquetasBarra: Boolean`, por defecto `true`). Con `false` la barra solo muestra íconos (los `contentDescription` de las pestañas pasan a llevar el nombre). Vale para todos los diseños.
- Lo demás de la barra (estilo, forma del «+») lo decide el diseño, no se elige aparte. Esto cumple el issue #2 sin multiplicar combinaciones.

## 7. Íconos de la app

- Tres formas, vectores en `res/drawable/`: **`check`** (la «C» con visto actual), **`moneda`** (dos monedas superpuestas: un círculo con «$» y detrás otro con «Bs», trazos de 6-7 dp en la zona segura de 66 dp) y **`libreta`** (una libreta cerrada con el lomo a la izquierda y una cifra sugerida por tres rayas, la última más corta).
- **Color por paleta oscura:** fondo = `fondo` de la paleta, trazo = `primario` de la paleta. Un `mipmap-anydpi-v26/ic_launcher_<forma>_<paleta>.xml` por combinación (3 × 6 = 18), con su `@color/launcher_fondo_<paleta>` y un vector `ic_launcher_<forma>_<paleta>.xml` generado por script (`scripts/generar-iconos-app.py`, que también escribe `colors.xml` de los lanzadores), más una capa monocroma por forma. El script es la fuente de verdad; se versiona.
- Manifest: `MainActivity` pierde el `intent-filter` LAUNCHER; se añaden 18 `activity-alias` (`.Lanzador_<forma>_<paleta>`, `targetActivity=".MainActivity"`, `exported=true`, mismo `label`), solo `Lanzador_check_noche_lima` con `android:enabled="true"`.
- `data/IconoAppRepo` (o función en `AparienciaRepo`): `aplicarIcono(forma, idPaletaOscura)` habilita el alias objetivo y deshabilita los demás con `PackageManager.setComponentEnabledSetting(…, DONT_KILL_APP)`. Se invoca al cambiar el ícono **y** al cambiar la paleta oscura. Preferencia `idIcono` (por defecto `check`).
- La notificación propia y el listener no cambian. El ícono adaptable sigue con capa `monochrome`.
- Selector en Apariencia: tres cuadros de 64 dp con el ícono pintado en los colores de la paleta oscura **actual** y el nombre debajo; al cambiar la paleta oscura, las muestras se repintan.

## 8. Persistencia

`Apariencia` pasa a `Apariencia(modo, idPaletaOscura, idPaletaClara, idFuente = "bricolage", idDiseno = "libreta", etiquetasBarra = true, idIcono = "check")`. Claves nuevas en `AparienciaRepo`: `fuente`, `diseno`, `etiquetas_barra`, `icono`; métodos `cambiarFuente`, `cambiarDiseno`, `cambiarEtiquetasBarra`, `cambiarIcono`. Un id desconocido (versión vieja, respaldo) cae al valor por defecto sin fallar. Test JVM (Robolectric, como `OpsTodoONadaTest`) que escribe las cuatro preferencias y las lee, y que un id desconocido devuelve el defecto.

## 9. Enmiendas a `android/DESIGN.md` y README

- §2: `Formas` deja de ser fija; sale de `CuentasTema.diseno.formas`. Nueva **§2.3 Diseños** con el catálogo de §6.2 y las 5 líneas de lo aprendido de las skills.
- §3: ya no «Ninguna otra fuente entra en el APK»: catálogo de §5 con sus `opsz`.
- §4: `SeccionPlegable`; `Tarjeta` y barra con sus variantes por diseño.
- §5 y §8: las prohibiciones de bucles, brillos y > 300 ms llevan «salvo el diseño Aurora (§2.3)».
- Ícono de la app: tres formas y color por paleta.
- README: sección «Diseño» menciona fuentes, diseños e íconos, y `scripts/generar-iconos-app.py`.

## 10. Verificación (lo estrictamente necesario)

1. `./gradlew :app:assembleDebug` y `./gradlew :app:testDebugUnitTest` en verde (incluye los dos tests nuevos).
2. **Una** pasada en el Xiaomi (variante `verif` por adb, un ancho, un tema): Ajustes → plegar/desplegar una sección → Apariencia → cambiar fuente, diseño (los cuatro, una captura de Inicio con cada uno), «Nombres en la barra» e ícono → Inicio → pestaña Ingresos. Sin barridos de ancho, sin revisores extra.
3. Lo menor que aparezca se anota en el informe final; Daniel valida a mano.

## 11. Fuera de alcance

Paletas nuevas; cambiar la fuente del ícono de la app o de la ventana de arranque; que el ícono siga la paleta clara; animaciones nuevas en Libreta/Tinta/estudio más allá de lo descrito; tests de UI.
