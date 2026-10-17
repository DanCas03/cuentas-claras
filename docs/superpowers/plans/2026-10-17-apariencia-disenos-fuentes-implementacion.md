# Apariencia ampliada (Ajustes plegable, gráficas de ingresos, fuentes, diseños, íconos) — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Objetivo:** Que Daniel pueda, desde una pantalla Apariencia propia, elegir fuente, diseño (Libreta, Tinta, uno hecho con skills, Aurora), nombres en la barra e ícono de la app (coloreado con su paleta oscura); que Ajustes sea plegable; y que «Este mes» muestre las gráficas de ingresos al tocar «Ingresos».

**Arquitectura:** Todo lo nuevo se persiste en el DataStore `apariencia` existente (`AparienciaRepo`) y se expone por `CuentasTema` como tokens (`CuentasTema.diseno`, tipografía construida con la fuente elegida). Solo los componentes base (`Tarjeta`, botones, `Chip`, `Segmentado`, `HojaModal`, `BarraInferior`, `RaizApp`) leen `diseno`; las pantallas no. El ícono de la app se cambia habilitando un `activity-alias` por combinación ícono × paleta oscura, generado por script.

**Tech Stack:** Kotlin 2.4.21 · Compose BOM 2026.09.00 (Material 3 1.4.0) · DataStore Preferences 1.2.1 · Robolectric 4.17 · JUnit 4 + kotlin-test · Python 3 (solo `scripts/generar-iconos-app.py`) · Gradle 9.7.1 (`./gradlew` desde `android/`).

**Spec:** `docs/superpowers/specs/2026-10-17-apariencia-disenos-fuentes-design.md` (manda). Fuente visual: `android/DESIGN.md`. Contexto heredado: `docs/superpowers/specs/2026-10-15-cuentas-claras-nativa-compose-design.md`.

## Global Constraints

1. **Libreta queda intacto**: con las preferencias por defecto (`bricolage`, `libreta`, `etiquetasBarra = true`, `check`) la app se ve y se mueve exactamente igual que antes.
2. **Fuente y diseño independientes**; un diseño nunca cambia fuente ni paleta.
3. **Preferencias nuevas solo en DataStore `apariencia`**; Room y el respaldo JSON no se tocan. Un id desconocido cae al valor por defecto sin fallar.
4. **Fuentes solo variables y OFL**, en `app/src/main/assets/fuentes/`, cargadas con `Font(path, assetManager, weight, variationSettings)`. **Prohibido `Font(R.font…)`** (E23).
5. **Aurora es la única excepción** a DESIGN.md §5/§8 (bucles, brillos, > 300 ms). Los otros tres diseños cumplen DESIGN.md entero: nada de `LinearEasing`, sombras negras, hex sueltos ni `dp` mágicos en pantallas.
6. Todo en español: UI, docs, commits, `contentDescription`. Commits con pie `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.
7. Las pantallas leen solo `CuentasTema.*`; `diseno` lo leen únicamente los componentes de `ui/componentes/`, `ui/nav/BarraInferior.kt` y `RaizApp`.
8. Accesibilidad base en todos los diseños: contraste de texto intacto, objetivos ≥ 48 dp, nada parpadea > 3 veces/s, las animaciones nunca bloquean la entrada.
9. **Verificación mínima (Daniel, 2026-10-17):** `assembleDebug` + `testDebugUnitTest` en verde por tarea; una sola pasada en el teléfono al final (Task 12). Sin revisores extra ni barridos de ancho.
10. Comandos desde `android/`: `./gradlew :app:testDebugUnitTest --tests "<clase>"` y `./gradlew :app:assembleDebug`. En PowerShell, `.\gradlew.bat`.

## Review Focus

1. **Respaldo o DataStore con un id de fuente/diseño/ícono que ya no existe** → la app abre con el valor por defecto, sin crash (test en Task 2).
2. **Mes con ingresos pero sin gastos, o al revés** → la pestaña activa muestra su `Vacio` y la otra sus gráficas; el acumulado de ingresos no mezcla gastos (test en Task 1).
3. **Cambiar la paleta oscura con un ícono distinto de `check`** → el alias habilitado pasa a ser el de la nueva paleta y solo queda uno habilitado (test en Task 10).
4. **Fuente sin eje `opsz` (Manrope, Space Grotesk)** → se construye sin ese ajuste y no se lanza excepción al cargar (test en Task 3).
5. **Animaciones del sistema desactivadas** → los bucles de Aurora quedan quietos y la app sigue usable (comprobación manual en Task 12; Compose respeta `ANIMATOR_DURATION_SCALE` por sí mismo en `rememberInfiniteTransition`).

---

### Task 1: Acumulado diario de ingresos en el dominio

**Files:**
- Modify: `android/app/src/main/java/com/cuentasclaras/app/domain/Stats.kt:9-71`
- Test: `android/app/src/test/java/com/cuentasclaras/app/domain/StatsTest.kt`

**Interfaces:**
- Produces: `ResumenMes.dailyCumulativeIncome: List<PuntoDiario>` (un punto por día del mes, acumulado de `TxType.INCOME` en la moneda de vista).

- [ ] **Step 1: Test que falla**

Añadir a `StatsTest` (dentro de la clase):

```kotlin
@Test fun `acumula los ingresos por dia sin mezclar gastos`() {
    // Bs 1.000,00 en la cuenta bnc → a 100 Bs/USD son $10.00 = 1000 minor
    val conIngresos = txs + tx(TxType.INCOME, 100000, "2026-07-20", "bnc")
    val r = monthSummary(conIngresos, cuentas, "2026-07", Currency.USD, rates, RateSource.BCV)
    assertEquals(31, r.dailyCumulativeIncome.size)
    assertEquals(PuntoDiario(2, 0), r.dailyCumulativeIncome[1])
    assertEquals(PuntoDiario(3, 2000), r.dailyCumulativeIncome[2])
    assertEquals(PuntoDiario(19, 2000), r.dailyCumulativeIncome[18])
    assertEquals(PuntoDiario(31, 3000), r.dailyCumulativeIncome[30])
    assertEquals(PuntoDiario(31, 1800), r.dailyCumulative[30]) // los gastos no cambian
}
```

- [ ] **Step 2: Correr y ver que falla**

Run: `./gradlew :app:testDebugUnitTest --tests "com.cuentasclaras.app.domain.StatsTest"`
Expected: FAIL de compilación («Unresolved reference: dailyCumulativeIncome»).

- [ ] **Step 3: Implementar**

En `Stats.kt`:

```kotlin
data class ResumenMes(
    val month: String,
    val incomeMinor: Long,
    val expenseMinor: Long,
    /** SOLO gastos, desc por monto. */
    val byCategory: List<GastoCategoria>,
    /** v3: ingresos por categoría, desc por monto. */
    val incomeByCategory: List<IngresoCategoria>,
    /** Gasto acumulado por día del mes. */
    val dailyCumulative: List<PuntoDiario>,
    /** Ingreso acumulado por día del mes (gráficas de ingresos de «Este mes»). */
    val dailyCumulativeIncome: List<PuntoDiario>,
    val omitidasSinTasa: Int,
)
```

En `monthSummary`: añadir `val porDiaIngreso = HashMap<Int, Long>()`; en la rama `INCOME`, calcular `dia` igual que en gastos (`?: continue` si la fecha es corrupta) **antes** de sumar, y `porDiaIngreso[dia] = (porDiaIngreso[dia] ?: 0L) + minor`. Extraer el acumulado a una función local:

```kotlin
fun acumular(porDia: Map<Int, Long>): List<PuntoDiario> {
    var acumulado = 0L
    return List(diasDelMes) { i ->
        acumulado += porDia[i + 1] ?: 0L
        PuntoDiario(i + 1, acumulado)
    }
}
return ResumenMes(month, incomeMinor, expenseMinor, byCategory, incomeByCategory, acumular(porDia), acumular(porDiaIngreso), omitidasSinTasa)
```

Buscar otros constructores de `ResumenMes` (`grep -rn "ResumenMes(" app/src`) y añadir el argumento (p. ej. en `InicioViewModel` si construye uno vacío).

- [ ] **Step 4: Correr tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.cuentasclaras.app.domain.StatsTest"`
Expected: PASS (los 3 tests previos y el nuevo).

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/cuentasclaras/app/domain/Stats.kt android/app/src/test/java/com/cuentasclaras/app/domain/StatsTest.kt
git commit -m "feat(dominio): ResumenMes acumula también los ingresos por día"
```

---

### Task 2: Preferencias nuevas de apariencia (fuente, diseño, etiquetas de barra, ícono)

**Files:**
- Modify: `android/app/src/main/java/com/cuentasclaras/app/data/AparienciaRepo.kt`
- Modify: `android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/ajustes/AparienciaViewModel.kt`
- Test: `android/app/src/test/java/com/cuentasclaras/app/data/AparienciaRepoTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  const val ID_FUENTE_POR_DEFECTO = "bricolage"; const val ID_DISENO_POR_DEFECTO = "libreta"; const val ID_ICONO_POR_DEFECTO = "check"
  data class Apariencia(modo, idPaletaOscura, idPaletaClara, idFuente = ID_FUENTE_POR_DEFECTO, idDiseno = ID_DISENO_POR_DEFECTO, etiquetasBarra: Boolean = true, idIcono = ID_ICONO_POR_DEFECTO)
  suspend fun cambiarFuente(id: String); cambiarDiseno(id: String); cambiarEtiquetasBarra(mostrar: Boolean); cambiarIcono(id: String)
  ```
  `AparienciaViewModel` expone `cambiarFuente`, `cambiarDiseno`, `cambiarEtiquetasBarra`, `cambiarIcono` (mismo helper `escribir(null, MENSAJE_ERROR)`).

- [ ] **Step 1: Test que falla**

```kotlin
package com.cuentasclaras.app.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class AparienciaRepoTest {
    private val contexto: Context = ApplicationProvider.getApplicationContext()
    private val archivo = File(contexto.filesDir, "apariencia-test-${System.nanoTime()}.preferences_pb")
    private val almacen = PreferenceDataStoreFactory.create(produceFile = { archivo })
    private val repo = AparienciaRepo(almacen)

    @Test fun `por defecto Bricolage, Libreta, etiquetas y check`() = runBlocking {
        val a = repo.apariencia.first()
        assertEquals(Apariencia(), a)
        assertEquals("bricolage", a.idFuente); assertEquals("libreta", a.idDiseno)
        assertEquals(true, a.etiquetasBarra); assertEquals("check", a.idIcono)
    }

    @Test fun `escribe y lee las cuatro preferencias nuevas`() = runBlocking {
        repo.cambiarFuente("inter"); repo.cambiarDiseno("aurora"); repo.cambiarEtiquetasBarra(false); repo.cambiarIcono("moneda")
        val a = repo.apariencia.first()
        assertEquals("inter", a.idFuente); assertEquals("aurora", a.idDiseno)
        assertEquals(false, a.etiquetasBarra); assertEquals("moneda", a.idIcono)
    }

    @Test fun `un modo desconocido cae al valor por defecto`() = runBlocking {
        almacen.edit { it[stringPreferencesKey("modo")] = "SEPIA" }
        assertEquals(ModoTema.SISTEMA, repo.apariencia.first().modo)
    }
}
```

Nota: los ids de fuente/diseño/ícono se guardan como texto y los resuelven `fuentePorId` / `disenoPorId` / `iconoPorId` (Tasks 3, 4, 10) con fallback; el repo no valida ids. Si `androidx.test:core` no está en el catálogo, usar `RuntimeEnvironment.getApplication()` de Robolectric en lugar de `ApplicationProvider`.

- [ ] **Step 2: Correr y ver que falla**

Run: `./gradlew :app:testDebugUnitTest --tests "com.cuentasclaras.app.data.AparienciaRepoTest"`
Expected: FAIL de compilación («Unresolved reference: idFuente»).

- [ ] **Step 3: Implementar en `AparienciaRepo.kt`**

```kotlin
const val ID_FUENTE_POR_DEFECTO = "bricolage"
const val ID_DISENO_POR_DEFECTO = "libreta"
const val ID_ICONO_POR_DEFECTO = "check"

data class Apariencia(
    val modo: ModoTema = ModoTema.SISTEMA,
    val idPaletaOscura: String = ID_PALETA_OSCURA_POR_DEFECTO,
    val idPaletaClara: String = ID_PALETA_CLARA_POR_DEFECTO,
    val idFuente: String = ID_FUENTE_POR_DEFECTO,
    val idDiseno: String = ID_DISENO_POR_DEFECTO,
    val etiquetasBarra: Boolean = true,
    val idIcono: String = ID_ICONO_POR_DEFECTO,
)
```

En el `map`: `idFuente = p[CLAVE_FUENTE] ?: ID_FUENTE_POR_DEFECTO`, `idDiseno = p[CLAVE_DISENO] ?: ID_DISENO_POR_DEFECTO`, `etiquetasBarra = p[CLAVE_ETIQUETAS_BARRA] ?: true`, `idIcono = p[CLAVE_ICONO] ?: ID_ICONO_POR_DEFECTO`. Claves: `stringPreferencesKey("fuente")`, `stringPreferencesKey("diseno")`, `booleanPreferencesKey("etiquetas_barra")`, `stringPreferencesKey("icono")`. Cuatro `suspend fun cambiarX` con `almacen.edit { it[CLAVE] = valor }`.

En `AparienciaViewModel`, cuatro métodos con el mismo patrón que `cambiarModo`.

- [ ] **Step 4: Correr tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.cuentasclaras.app.data.AparienciaRepoTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/cuentasclaras/app/data/AparienciaRepo.kt android/app/src/main/java/com/cuentasclaras/app/ui/pantallas/ajustes/AparienciaViewModel.kt android/app/src/test/java/com/cuentasclaras/app/data/AparienciaRepoTest.kt
git commit -m "feat(apariencia): preferencias de fuente, diseño, etiquetas de barra e ícono en DataStore"
```

---

### Task 3: Catálogo de fuentes y tipografía por fuente

**Files:**
- Create: `android/app/src/main/java/com/cuentasclaras/app/ui/theme/Fuentes.kt`
- Modify: `android/app/src/main/java/com/cuentasclaras/app/ui/theme/Type.kt`
- Modify: `android/app/src/main/java/com/cuentasclaras/app/ui/theme/Theme.kt:70-91`
- Create (binarios): `android/app/src/main/assets/fuentes/{inter,manrope,space_grotesk,dm_sans,fraunces}_variable.ttf` y `OFL-<id>.txt`
- Test: `android/app/src/test/java/com/cuentasclaras/app/ui/theme/FuentesTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  data class Fuente(val id: String, val nombre: String, val archivo: String, val opszTexto: Float?, val opszDisplay: Float?, val serif: Boolean = false)
  val FUENTES: List<Fuente>; fun fuentePorId(id: String?): Fuente   // Bricolage si no existe
  fun tipografiaCuentas(assets: AssetManager, fuente: Fuente = fuentePorId(null)): Tipografia
  fun CuentasTemaConPaleta(paleta: Paleta, fuente: Fuente = fuentePorId(null), content)   // Task 4 añade `diseno`
  ```

- [ ] **Step 1: Descargar las fuentes (Bash desde la raíz del repo)**

```bash
cd android/app/src/main/assets/fuentes
B=https://raw.githubusercontent.com/google/fonts/main/ofl
curl -fsSL -o inter_variable.ttf         "$B/inter/Inter%5Bopsz%2Cwght%5D.ttf"
curl -fsSL -o manrope_variable.ttf       "$B/manrope/Manrope%5Bwght%5D.ttf"
curl -fsSL -o space_grotesk_variable.ttf "$B/spacegrotesk/SpaceGrotesk%5Bwght%5D.ttf"
curl -fsSL -o dm_sans_variable.ttf       "$B/dmsans/DMSans%5Bopsz%2Cwght%5D.ttf"
curl -fsSL -o fraunces_variable.ttf      "$B/fraunces/Fraunces%5BSOFT%2CWONK%2Copsz%2Cwght%5D.ttf"
for f in inter manrope spacegrotesk dmsans fraunces; do curl -fsSL -o "OFL-$f.txt" "$B/$f/OFL.txt"; done
ls -la
```

Si una descarga falla (404), probar el nombre listado en `https://github.com/google/fonts/tree/main/ofl/<familia>`; si no existe versión variable o el archivo pesa > 1 MB, omitir esa fuente del catálogo y anotarlo en el commit. Mínimo 4 fuentes. Renombrar `OFL-spacegrotesk.txt` → `OFL-space_grotesk.txt` y `OFL-dmsans.txt` → `OFL-dm_sans.txt`.

- [ ] **Step 2: Test que falla**

```kotlin
package com.cuentasclaras.app.ui.theme

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class FuentesTest {
    private val assets = ApplicationProvider.getApplicationContext<Context>().assets

    @Test fun `un id desconocido cae a Bricolage`() = assertEquals("bricolage", fuentePorId("comic").id)

    @Test fun `todas las fuentes del catalogo existen en assets y construyen la tipografia`() {
        FUENTES.forEach { f ->
            assets.open(f.archivo).use { assertTrue(it.read() >= 0, f.archivo) }
            assertNotNull(tipografiaCuentas(assets, f).cuerpo.fontFamily, f.id)
        }
    }

    @Test fun `una fuente sin opsz se construye sin el eje`() {
        val sinOpsz = FUENTES.first { it.opszTexto == null }
        assertNotNull(tipografiaCuentas(assets, sinOpsz).cifraGrande.fontFamily)
    }
}
```

- [ ] **Step 3: Correr y ver que falla**

Run: `./gradlew :app:testDebugUnitTest --tests "com.cuentasclaras.app.ui.theme.FuentesTest"`
Expected: FAIL de compilación («Unresolved reference: FUENTES»).

- [ ] **Step 4: Implementar `Fuentes.kt`**

```kotlin
package com.cuentasclaras.app.ui.theme

import com.cuentasclaras.app.data.ID_FUENTE_POR_DEFECTO

/** Una fuente elegible en Apariencia. Variable y OFL; `archivo` relativo a `assets/`. `opsz*` null = la fuente no tiene el eje. */
data class Fuente(
    val id: String,
    val nombre: String,
    val archivo: String,
    val opszTexto: Float?,
    val opszDisplay: Float?,
    val serif: Boolean = false,
)

/** Catálogo cerrado (spec 2026-10-17 §5). `id` es estable: se guarda en DataStore. */
val FUENTES: List<Fuente> = listOf(
    Fuente("bricolage", "Bricolage Grotesque", "fuentes/bricolage_grotesque_variable.ttf", 14f, 40f),
    Fuente("inter", "Inter", "fuentes/inter_variable.ttf", 14f, 32f),
    Fuente("manrope", "Manrope", "fuentes/manrope_variable.ttf", null, null),
    Fuente("space_grotesk", "Space Grotesk", "fuentes/space_grotesk_variable.ttf", null, null),
    Fuente("dm_sans", "DM Sans", "fuentes/dm_sans_variable.ttf", 14f, 40f),
    Fuente("fraunces", "Fraunces", "fuentes/fraunces_variable.ttf", 14f, 72f, serif = true),
)

fun fuentePorId(id: String?): Fuente = FUENTES.firstOrNull { it.id == id } ?: FUENTES.first { it.id == ID_FUENTE_POR_DEFECTO }
```

Quitar del catálogo las que no se descargaron en el Step 1.

- [ ] **Step 5: Generalizar `Type.kt`**

Sustituir `RUTA_BRICOLAGE`, `OPSZ_*` y `familiaBricolage` por:

```kotlin
private fun familia(assets: AssetManager, fuente: Fuente, opsz: Float?, vararg pesos: Int): FontFamily = FontFamily(
    pesos.map { peso ->
        val ajustes = buildList {
            add(FontVariation.weight(peso))
            if (opsz != null) add(FontVariation.Setting("opsz", opsz))
        }
        Font(path = fuente.archivo, assetManager = assets, weight = FontWeight(peso), variationSettings = FontVariation.Settings(*ajustes.toTypedArray()))
    }
)

/** Tipografía real de la app con la fuente elegida. Se construye una vez por fuente en `CuentasTema`. */
fun tipografiaCuentas(assets: AssetManager, fuente: Fuente = fuentePorId(null)): Tipografia = construirTipografia(
    texto = familia(assets, fuente, fuente.opszTexto, 400, 500, 600, 700),
    display = familia(assets, fuente, fuente.opszDisplay, 500, 600, 700),
)
```

Tamaños, pesos e interlíneas de `construirTipografia` no cambian. Actualizar el KDoc de E23.

- [ ] **Step 6: Enchufar en `Theme.kt`**

`CuentasTemaConPaleta(paleta, fuente: Fuente = fuentePorId(null), content)`: `remember(assets, fuente) { tipografiaCuentas(assets, fuente) }`. En `CuentasTema`: `val fuente = fuentePorId(apariencia?.idFuente)` y pasarla.

- [ ] **Step 7: Correr tests y compilar**

Run: `./gradlew :app:testDebugUnitTest --tests "com.cuentasclaras.app.ui.theme.FuentesTest" :app:assembleDebug`
Expected: PASS y BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add android/app/src/main/assets/fuentes android/app/src/main/java/com/cuentasclaras/app/ui/theme android/app/src/test/java/com/cuentasclaras/app/ui/theme/FuentesTest.kt
git commit -m "feat(tipografia): catálogo de fuentes OFL variables y tipografía construida con la fuente elegida"
```

---

### Task 4: Tokens de diseño en el tema (solo Libreta, sin cambio visual)

**Files:**
- Create: `android/app/src/main/java/com/cuentasclaras/app/ui/theme/Diseno.kt`
- Modify: `android/app/src/main/java/com/cuentasclaras/app/ui/theme/Theme.kt`
- Modify: `android/app/src/main/java/com/cuentasclaras/app/ui/theme/Shape.kt` (`Formas` gana `hoja: Dp` y `radioTarjeta: Dp`, `biselTarjeta: Dp`)
- Modify: `android/app/src/main/java/com/cuentasclaras/app/ui/theme/Movimiento.kt`
- Modify: `android/app/src/main/java/com/cuentasclaras/app/ui/componentes/HojaModal.kt:69`, `Tarjeta.kt` (usa `formas.radioTarjeta/biselTarjeta` en vez de las constantes)

**Interfaces:**
- Produces:
  ```kotlin
  enum class EstiloTarjeta { BISEL, PLANA, CONTORNO, AURORA }
  enum class EstiloBarra { PILDORA, SUBRAYADO, FLOTANTE, AURORA }
  enum class FormaMas { DISCO, CUADRADO, GOTA }
  enum class EstiloFondo { LISO, AURORA }
  enum class NivelMovimiento { CONTENIDO, EXPRESIVO }
  data class Diseno(id, nombre, descripcion, formas: Formas, tarjeta, barra, botonMas, fondo, movimiento, escalaPresion: Float)
  val DISENOS: List<Diseno>; fun disenoPorId(id: String?): Diseno
  CuentasTema.diseno: Diseno
  fun CuentasTemaConPaleta(paleta, fuente = …, diseno: Diseno = disenoPorId(null), content)
  Movimiento.resorte(nivel = CONTENIDO); Movimiento.transicion(duracion = MEDIO, nivel = CONTENIDO)
  data class Formas(tarjeta, control, pildora, radioTarjeta: Dp, biselTarjeta: Dp, hoja: Dp)
  ```

- [ ] **Step 1: `Diseno.kt` con solo Libreta**

```kotlin
package com.cuentasclaras.app.ui.theme

import com.cuentasclaras.app.data.ID_DISENO_POR_DEFECTO

enum class EstiloTarjeta { BISEL, PLANA, CONTORNO, AURORA }
enum class EstiloBarra { PILDORA, SUBRAYADO, FLOTANTE, AURORA }
enum class FormaMas { DISCO, CUADRADO, GOTA }
enum class EstiloFondo { LISO, AURORA }
enum class NivelMovimiento { CONTENIDO, EXPRESIVO }

/** Un diseño elegible en Apariencia (spec 2026-10-17 §6). `id` es estable: se guarda en DataStore. */
data class Diseno(
    val id: String,
    val nombre: String,
    val descripcion: String,
    val formas: Formas,
    val tarjeta: EstiloTarjeta,
    val barra: EstiloBarra,
    val botonMas: FormaMas,
    val fondo: EstiloFondo,
    val movimiento: NivelMovimiento,
    /** Escala del botón o chip al presionarlo (0,97 en Libreta). */
    val escalaPresion: Float,
)

val DISENO_LIBRETA = Diseno(
    id = "libreta", nombre = "Libreta", descripcion = "Tarjetas con doble bisel y barra con píldora. El de siempre.",
    formas = FormasCuentas, tarjeta = EstiloTarjeta.BISEL, barra = EstiloBarra.PILDORA, botonMas = FormaMas.DISCO,
    fondo = EstiloFondo.LISO, movimiento = NivelMovimiento.CONTENIDO, escalaPresion = 0.97f,
)

val DISENOS: List<Diseno> = listOf(DISENO_LIBRETA)

fun disenoPorId(id: String?): Diseno = DISENOS.firstOrNull { it.id == id } ?: DISENOS.first { it.id == ID_DISENO_POR_DEFECTO }
```

- [ ] **Step 2: `Formas` con radios explícitos**

En `Shape.kt`: `data class Formas(val tarjeta: Shape, val control: Shape, val pildora: Shape, val radioTarjeta: Dp, val biselTarjeta: Dp, val hoja: Dp)`; `FormasCuentas = Formas(RoundedCornerShape(RADIO_TARJETA), RoundedCornerShape(RADIO_CONTROL), RoundedCornerShape(percent = 50), RADIO_TARJETA, BISEL_TARJETA, RADIO_HOJA)`. Añadir `fun formasMaterial(f: Formas): Shapes` que use `f.control`, `f.tarjeta` y `RoundedCornerShape(f.hoja)`. `Tarjeta.kt` usa `CuentasTema.formas.radioTarjeta - CuentasTema.formas.biselTarjeta` y `.padding(formas.biselTarjeta)`; `HojaModal.kt:69` usa `CuentasTema.formas.hoja`.

- [ ] **Step 3: `Movimiento` con nivel**

```kotlin
fun <T> resorte(nivel: NivelMovimiento = NivelMovimiento.CONTENIDO): FiniteAnimationSpec<T> = when (nivel) {
    NivelMovimiento.CONTENIDO -> spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
    NivelMovimiento.EXPRESIVO -> spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)
}
fun <T> transicion(duracion: Int = MEDIO, nivel: NivelMovimiento = NivelMovimiento.CONTENIDO): FiniteAnimationSpec<T> =
    tween(durationMillis = if (nivel == NivelMovimiento.EXPRESIVO) duracion * 3 / 2 else duracion, easing = suave)
```

- [ ] **Step 4: `Theme.kt`**

`private val LocalDiseno = staticCompositionLocalOf { DISENO_LIBRETA }`; `CuentasTema.diseno` getter; `CuentasTemaConPaleta(paleta, fuente, diseno = disenoPorId(null), content)` provee `LocalDiseno provides diseno`, `LocalFormas provides diseno.formas`, y `MaterialTheme(shapes = formasMaterial(diseno.formas))`. `CuentasTema` resuelve `disenoPorId(apariencia?.idDiseno)`.

- [ ] **Step 5: Compilar y correr todos los tests**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, todos en verde.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/com/cuentasclaras/app/ui
git commit -m "feat(tema): tokens de diseño (Diseno) expuestos por CuentasTema; Libreta sin cambios visuales"
```

---

### Task 5: Los componentes leen el diseño + diseño «Tinta» (antes de invocar skills)

**Files:**
- Modify: `ui/componentes/Tarjeta.kt`, `ui/componentes/Boton.kt:60-80`, `ui/componentes/Chip.kt`, `ui/componentes/Segmentado.kt`, `ui/nav/BarraInferior.kt`, `MainActivity.kt:198-225` (`RaizApp`), `ui/theme/Diseno.kt`
- Create: `ui/componentes/FondoDiseno.kt` (`@Composable fun FondoDiseno(modifier, content)`: por ahora solo pinta `fondo` liso; Aurora lo amplía en Task 8)

**Interfaces:**
- Consumes: `CuentasTema.diseno` (Task 4), `Apariencia.etiquetasBarra` (Task 2).
- Produces: `BarraInferior(nav, etiquetas: Boolean)`; `Tarjeta` pinta según `diseno.tarjeta`; `BotonBase`/`Chip` usan `diseno.escalaPresion` y `Movimiento.resorte(diseno.movimiento)`; `DISENO_TINTA` en `DISENOS`.

**No invocar ninguna skill de diseño en esta tarea** (Daniel quiere comparar un diseño hecho sin skills con otro hecho con ellas).

- [ ] **Step 1: `Tarjeta` por estilo**

```kotlin
val d = CuentasTema.diseno
val f = CuentasTema.formas
when (d.tarjeta) {
    EstiloTarjeta.BISEL -> /* el bloque actual: carcasa superficie + núcleo superficieElevada con filo */
    EstiloTarjeta.PLANA -> Column(modifier.fillMaxWidth().clip(f.tarjeta).background(c.superficie)
        .drawBehind { drawLine(c.borde, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
        .padding(e.m)) { cabecera(); contenido() }
    EstiloTarjeta.CONTORNO, EstiloTarjeta.AURORA -> /* Task 6 y 7; mientras tanto, igual que BISEL */
}
```

Extraer la cabecera (título + acción) a una función privada `CabeceraTarjeta(titulo, accion)` para no repetirla por estilo.

- [ ] **Step 2: `BotonBase`, `Chip`, `Segmentado`**

`val d = CuentasTema.diseno`; `animateFloatAsState(if (presionado) d.escalaPresion else 1f, Movimiento.resorte(d.movimiento))`. En `Chip` añadir la misma escala al presionar si no la tiene (hoy no la tiene: añadir `MutableInteractionSource` + `collectIsPressedAsState` + `graphicsLayer`, solo cuando `onClick != null`). `Segmentado` y `Chip` usan `Movimiento.transicion(nivel = d.movimiento)`.

- [ ] **Step 3: `BarraInferior(nav, etiquetas)`**

Firmar `fun BarraInferior(nav: NavHostController, etiquetas: Boolean = true)`. `ItemPestana(pestana, activa, etiquetas, onClick)`: con `etiquetas = false` no pinta el `Text` y la `Column` lleva `semantics { contentDescription = pestana.texto }`. Estilos:

- `PILDORA`: el actual.
- `SUBRAYADO`: sin píldora; bajo el ícono, `Box(Modifier.width(ANCHO_INDICADOR).height(2.dp).clip(pildora).background(indicadorColor))` donde `indicadorColor` anima `primario` ↔ transparente; ícono en `primario`/`textoSecundario`.
- `FLOTANTE` y `AURORA`: por ahora igual que `PILDORA` (Tasks 6 y 7).

`BotonNuevo(modifier, forma: FormaMas, onClick)`: `DISCO` = `CircleShape`; `CUADRADO` = `RoundedCornerShape(RADIO_CONTROL)`; `GOTA` = por ahora `CircleShape` (Task 6). Constantes locales de la barra (`DISCO_MAS`, etc.) siguen siendo las medidas del componente (permitidas por DESIGN.md §8).

En `RaizApp`: leer `etiquetasBarra` del mismo flujo que `CuentasTema` (`(LocalContext.current.applicationContext as CuentasApp).container.apariencia.apariencia.collectAsState(initial = Apariencia())`) y pasar `BarraInferior(nav, etiquetas = apariencia.etiquetasBarra)`. Envolver el `Scaffold` en `FondoDiseno { … }` con `containerColor = Color.Transparent` en el `Scaffold`.

- [ ] **Step 4: `DISENO_TINTA`**

```kotlin
private val FORMAS_TINTA = Formas(
    tarjeta = RoundedCornerShape(8.dp), control = RoundedCornerShape(8.dp), pildora = RoundedCornerShape(8.dp),
    radioTarjeta = 8.dp, biselTarjeta = 0.dp, hoja = 16.dp,
)
val DISENO_TINTA = Diseno(
    id = "tinta", nombre = "Tinta", descripcion = "Plano y editorial: esquinas de 8 dp, filetes finos, barra con subrayado.",
    formas = FORMAS_TINTA, tarjeta = EstiloTarjeta.PLANA, barra = EstiloBarra.SUBRAYADO, botonMas = FormaMas.CUADRADO,
    fondo = EstiloFondo.LISO, movimiento = NivelMovimiento.CONTENIDO, escalaPresion = 0.98f,
)
val DISENOS = listOf(DISENO_LIBRETA, DISENO_TINTA)
```

- [ ] **Step 5: Compilar, tests, y probar Tinta a mano una vez**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest` → verde. Para ver Tinta antes de que exista el selector (Task 9), cambiar temporalmente `ID_DISENO_POR_DEFECTO` a `"tinta"`, instalar en el teléfono, mirar Inicio y Ajustes, y **revertir** antes del commit.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/com/cuentasclaras/app
git commit -m "feat(diseno): los componentes base leen CuentasTema.diseno; diseño Tinta (plano, subrayado, + cuadrado)"
```

---

### Task 6: Diseño hecho con las skills de diseño

**Files:**
- Modify: `ui/theme/Diseno.kt`, `ui/componentes/Tarjeta.kt` (estilo `CONTORNO`), `ui/nav/BarraInferior.kt` (estilo `FLOTANTE`, forma `GOTA`)
- Create: `docs/research/2026-10-17-diseno-con-skills.md` (5-15 líneas: qué recomendaron las skills y qué se aplicó)

**Interfaces:**
- Produces: tercer `Diseno` en `DISENOS` con id definitivo (sustituye al provisional `estudio` del spec; el id se fija aquí y nada lo ha persistido aún).

- [ ] **Step 1: Invocar las skills** `high-end-visual-design` (soft-skill) y `ui-ux-pro-max` (pedirle guía para «mobile app, finance, dark/light palettes, card + bottom nav») y anotar en `docs/research/2026-10-17-diseno-con-skills.md` 5-15 líneas con lo que recomiendan para tarjetas, barra, radios, espaciado y movimiento. Descartar explícitamente lo que rompa DESIGN.md (sombras negras, texto `primario` sobre relleno de control, gradientes de texto).

- [ ] **Step 2: Definir el diseño con lo aprendido**

Punto de partida (ajustar con lo que digan las skills, sin salirse de los enums existentes):

```kotlin
private val FORMAS_ESTUDIO = Formas(RoundedCornerShape(24.dp), RoundedCornerShape(14.dp), RoundedCornerShape(percent = 50), 24.dp, 0.dp, 28.dp)
val DISENO_ESTUDIO = Diseno(
    id = "<id elegido>", nombre = "<Nombre elegido>", descripcion = "<una línea>",
    formas = FORMAS_ESTUDIO, tarjeta = EstiloTarjeta.CONTORNO, barra = EstiloBarra.FLOTANTE, botonMas = FormaMas.GOTA,
    fondo = EstiloFondo.LISO, movimiento = NivelMovimiento.CONTENIDO, escalaPresion = 0.96f,
)
```

- [ ] **Step 3: `Tarjeta` estilo `CONTORNO`**: `superficieElevada` con `border(1.dp, Brush.verticalGradient(listOf(c.texto.copy(alpha = 0.10f), Color.Transparent)), f.tarjeta)`, radio 24 dp, sin carcasa, relleno `e.m + e.xs`.

- [ ] **Step 4: Barra `FLOTANTE` y «+» `GOTA`**: la barra va en una píldora con margen horizontal `e.m` y abajo `e.s` (sobre `navigationBarsPadding()`), fondo `superficieElevada`, sin divisor; el «+» es un disco de `DISCO_MAS` dentro de la píldora (no sobresale: `SOBRESALE_MAS` = 0 en este estilo), con el ícono en `sobrePrimario`.

- [ ] **Step 5: Compilar y mirar una vez en el teléfono** (cambio temporal del defecto como en Task 5, revertido antes del commit).

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/com/cuentasclaras/app docs/research/2026-10-17-diseno-con-skills.md
git commit -m "feat(diseno): diseño <Nombre> hecho con las skills de diseño (contorno, barra flotante)"
```

---

### Task 7: Diseño «Aurora» (animaciones extravagantes)

**Files:**
- Modify: `ui/theme/Diseno.kt`, `ui/componentes/FondoDiseno.kt`, `ui/componentes/Tarjeta.kt` (estilo `AURORA`), `ui/nav/BarraInferior.kt` (estilo `AURORA` + halo del «+»)

**Interfaces:**
- Produces: `DISENO_AURORA` en `DISENOS`.

- [ ] **Step 1: Definición**

```kotlin
val DISENO_AURORA = Diseno(
    id = "aurora", nombre = "Aurora", descripcion = "Luces de la paleta que se mueven de fondo, filos que giran y un «+» que respira.",
    formas = Formas(RoundedCornerShape(28.dp), RoundedCornerShape(16.dp), RoundedCornerShape(percent = 50), 28.dp, 0.dp, 32.dp),
    tarjeta = EstiloTarjeta.AURORA, barra = EstiloBarra.AURORA, botonMas = FormaMas.DISCO,
    fondo = EstiloFondo.AURORA, movimiento = NivelMovimiento.EXPRESIVO, escalaPresion = 0.90f,
)
```

- [ ] **Step 2: Fondo con manchas en bucle (`FondoDiseno`)**

```kotlin
@Composable
fun FondoDiseno(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = CuentasTema.colores
    val d = CuentasTema.diseno
    Box(modifier.fillMaxSize().background(c.fondo)) {
        if (d.fondo == EstiloFondo.AURORA) {
            val t = rememberInfiniteTransition(label = "aurora")
            val fase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(24_000, easing = Movimiento.suave), RepeatMode.Reverse), label = "fase")
            val manchas = listOf(c.primario, c.ingreso, c.aviso)
            Canvas(Modifier.fillMaxSize().then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(48.dp) else Modifier)) {
                manchas.forEachIndexed { i, color ->
                    val ang = (fase + i / 3f) * 2f * PI.toFloat()
                    val centro = Offset(size.width * (0.5f + 0.35f * cos(ang)), size.height * (0.4f + 0.3f * sin(ang * 0.7f)))
                    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.18f), Color.Transparent), centro, size.minDimension * 0.45f), size.minDimension * 0.45f, centro)
                }
            }
        }
        content()
    }
}
```

- [ ] **Step 3: `Tarjeta` estilo `AURORA`**: contorno de 1,5 dp con `Brush.sweepGradient(listOf(c.primario, c.ingreso, c.aviso, c.primario))` dibujado en un `drawBehind`/`drawWithContent` con `rotate(angulo)` donde `angulo` es un `rememberInfiniteTransition().animateFloat(0f, 360f, infiniteRepeatable(tween(8_000, easing = LinearEasing)))` — en Aurora, y solo aquí, se permite `LinearEasing` (un giro continuo no puede tener aceleración). Fondo `superficieElevada`. **Entrada en cascada:** un `CompositionLocal` `LocalOrdenCascada` (contador `remember { AtomicInteger() }` en `FondoDiseno`, reiniciado por pantalla con `key(ruta)`) da a cada `Tarjeta` un índice; la tarjeta anima `alpha` 0 → 1 y `translationY` `e.m` → 0 con `tween(LARGO * 3 / 2, delayMillis = 60 * indice, easing = suave)` solo en su primera composición (`remember { Animatable(0f) }` + `LaunchedEffect(Unit)`).

- [ ] **Step 4: Barra `AURORA`**: la píldora activa es **una sola** `Box` que se desliza con `animateDpAsState(offsetX, Movimiento.resorte(EXPRESIVO))` entre las posiciones de las pestañas (medidas con `onGloballyPositioned`), bajo la fila de íconos. El «+» tiene detrás un halo: `Box(Modifier.size(DISCO_MAS).graphicsLayer { scaleX = s; scaleY = s; alpha = a }.clip(CircleShape).background(c.primario))` con `s` 1 → 1,25 y `a` 0,35 → 0 en `infiniteRepeatable(tween(2_400, easing = suave), RepeatMode.Restart)`.

- [ ] **Step 5: Compilar, tests y una mirada en el teléfono** (defecto temporal `"aurora"`, revertido). Comprobar que con «Quitar animaciones» del sistema (Ajustes → Accesibilidad) la app sigue usable.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/com/cuentasclaras/app
git commit -m "feat(diseno): diseño Aurora con fondo en movimiento, filos que giran, píldora deslizante y halo del «+»"
```

---

### Task 8: Pestañas Ingresos / Gastos con gráficas de ingresos

**Files:**
- Modify: `ui/componentes/Dona.kt:74-90`, `ui/componentes/CurvaAcumulada.kt:48-61` (parámetro `descripcion: String`)
- Modify: `ui/pantallas/inicio/EsteMesSeccion.kt:95-185`

**Interfaces:**
- Consumes: `ResumenMes.dailyCumulativeIncome` (Task 1).
- Produces: `Dona(segmentos, formato, descripcion: String = "Gráfica de dona de gastos por categoría", modifier)`, `CurvaAcumulada(puntos, formato, descripcion: String = "Gráfica de gasto acumulado del mes", modifier)`.

- [ ] **Step 1: Parametrizar la descripción** en ambos componentes: el `buildString` empieza con `append(descripcion); append(". ")` (ajustar el «. Total » de la dona para que siga leyéndose «…por categoría. Total $x.»).

- [ ] **Step 2: Pestañas en `TarjetaEsteMes`**

```kotlin
enum class FlujoMes { GASTOS, INGRESOS }
var flujo by rememberSaveable { mutableStateOf(FlujoMes.GASTOS) }
Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(e.m)) {
    Flujo("Ingresos", R.drawable.ic_entra, c.ingreso, resumen.incomeMinor, estado.viewCurrency, Signo.POSITIVO,
        activo = flujo == FlujoMes.INGRESOS, onClick = { flujo = FlujoMes.INGRESOS }, Modifier.weight(1f))
    Flujo("Gastos", R.drawable.ic_sale, c.gasto, resumen.expenseMinor, estado.viewCurrency, Signo.NEGATIVO,
        activo = flujo == FlujoMes.GASTOS, onClick = { flujo = FlujoMes.GASTOS }, Modifier.weight(1f))
}
```

`Flujo` gana `activo: Boolean, onClick: () -> Unit`; su `Column` lleva `.clip(CuentasTema.formas.control).background(if (activo) c.primario.copy(alpha = 0.14f) else Color.Transparent).selectable(selected = activo, role = Role.Tab, onClick = onClick).heightIn(min = CuentasTema.medidas.tactilMinimo).padding(e.s)` y conserva `semantics(mergeDescendants = true)`.

Bloque de gráficas:

```kotlin
when (flujo) {
    FlujoMes.GASTOS -> if (resumen.expenseMinor <= 0L) Vacio("🧾", …sin gastos…) else { Rotulo("Gasto por categoría"); Dona(…byCategory…); Rotulo("Gasto acumulado"); CurvaAcumulada(resumen.dailyCumulative, dinero) }
    FlujoMes.INGRESOS -> if (resumen.incomeMinor <= 0L) Vacio("💰", if (estado.mes == estado.mesActual) "Sin ingresos este mes" else "Sin ingresos en ${nombreMes(estado.mes)}") else {
        Rotulo("Ingreso por categoría")
        Dona(resumen.incomeByCategory.map { b -> val cat = categoria(b.categoryId); SegmentoDona(cat?.name ?: "Sin categoría", b.incomeMinor, cat?.let { colorDeCategoria(it.color, c.textoSecundario) } ?: c.textoSecundario) }, dinero, descripcion = "Gráfica de dona de ingresos por categoría")
        Rotulo("Ingreso acumulado")
        CurvaAcumulada(resumen.dailyCumulativeIncome, dinero, descripcion = "Gráfica de ingreso acumulado del mes")
    }
}
```

La línea «Gastaste el N %», el balance, el banner de sin tasa y «Ver todas las categorías» no dependen de `flujo`.

- [ ] **Step 3: Compilar y tests**: `./gradlew :app:assembleDebug :app:testDebugUnitTest` → verde.

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/com/cuentasclaras/app/ui
git commit -m "feat(inicio): «Ingresos» y «Gastos» son pestañas; con Ingresos se ven la dona y el acumulado de ingresos"
```

---

### Task 9: `SeccionPlegable`, Ajustes plegable y pantalla Apariencia (modo, paletas, fuente, diseño, barra)

**Files:**
- Create: `ui/componentes/SeccionPlegable.kt`
- Modify: `ui/pantallas/ajustes/AjustesPantalla.kt`, `ui/nav/Rutas.kt` (`const val APARIENCIA = "apariencia"`), `ui/nav/CuentasNavHost.kt` (`composable(Rutas.APARIENCIA) { AparienciaPantalla(nav) }`)
- Create: `ui/pantallas/apariencia/AparienciaPantalla.kt`, `ui/pantallas/apariencia/GrupoFuentes.kt`, `ui/pantallas/apariencia/GrupoDisenos.kt`
- Move: `ui/pantallas/ajustes/AparienciaViewModel.kt` → `ui/pantallas/apariencia/AparienciaViewModel.kt`; borrar `ui/pantallas/ajustes/AparienciaSeccion.kt` (sus `GrupoPaletas`, `FilaPaleta`, `MuestraPaleta`, `RotuloGrupo` se mudan a `AparienciaPantalla.kt`)

**Interfaces:**
- Produces: `@Composable fun SeccionPlegable(titulo: String, abierta: Boolean, onAlternar: () -> Unit, contenido: @Composable ColumnScope.() -> Unit)`; `Rutas.APARIENCIA`; `AparienciaPantalla(nav)`. El grupo «Ícono» lo añade Task 10.

- [ ] **Step 1: `SeccionPlegable`**

```kotlin
@Composable
fun SeccionPlegable(titulo: String, abierta: Boolean, onAlternar: () -> Unit, contenido: @Composable ColumnScope.() -> Unit) {
    val c = CuentasTema.colores; val e = CuentasTema.espacios; val d = CuentasTema.diseno
    val giro by animateFloatAsState(if (abierta) 180f else 0f, Movimiento.transicion(Movimiento.MEDIO, d.movimiento), label = "chevron")
    val estado = if (abierta) "desplegada" else "plegada"
    Tarjeta {
        Row(
            Modifier.fillMaxWidth().heightIn(min = CuentasTema.medidas.tactilMinimo).clip(CuentasTema.formas.control)
                .clickable(role = Role.Button, onClick = onAlternar).semantics { stateDescription = estado },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(titulo, style = CuentasTema.tipografia.subtitulo, color = c.texto, modifier = Modifier.weight(1f).semantics { heading() })
            Icon(painterResource(R.drawable.ic_expandir), contentDescription = null, tint = c.textoSecundario, modifier = Modifier.graphicsLayer { rotationZ = giro })
        }
        AnimatedVisibility(abierta, enter = fadeIn(Movimiento.transicion(Movimiento.LARGO, d.movimiento)) + slideInVertically(Movimiento.transicion(Movimiento.LARGO, d.movimiento)) { -it / 8 }, exit = fadeOut(Movimiento.transicion(Movimiento.MEDIO, d.movimiento))) {
            Column(Modifier.padding(top = e.s), content = contenido)
        }
    }
}
```

Nota: `Tarjeta` pinta su propio título cuando se le pasa `titulo`; aquí no se pasa, la cabecera la pinta la sección. `AnimatedVisibility` cambia el alto del contenedor al entrar; es el alto de la lista lo que cambia, no una animación de alto explícita: aceptado (la alternativa, componer siempre el contenido oculto, rompe «cerrada no compone»).

- [ ] **Step 2: `AjustesPantalla`**

Primera fila tras el título: `FilaApariencia(onAbrir = { nav.navigate(Rutas.APARIENCIA) })` = `Tarjeta { Row(clickable, Role.Button, ≥ 48 dp) { Text("Apariencia", subtitulo, weight 1f); Icon(ic_expandir, rotationZ = -90f, contentDescription = null) } }` con `semantics { contentDescription = "Apariencia" }` en la fila. Luego, cada sección:

```kotlin
item(key = "cuentas") {
    var abierta by rememberSaveable { mutableStateOf(false) }
    SeccionPlegable("Cuentas", abierta, { abierta = !abierta }) { CuentasSeccion(…) }
}
```

Igual para Categorías, Tasas, Reglas, Respaldo y Permisos. Como `CuentasSeccion` y las demás ya pintan su propia `Tarjeta(titulo = …)`, **quitar esa `Tarjeta` exterior de cada sección** (dejar solo su `Column` de contenido) para no tener tarjeta dentro de tarjeta; lo mismo con `RespaldoSeccion` y `FilaPermisos`. Verificar que ninguna sección dependía del `accion` de su `Tarjeta` (si alguna lo usaba —p. ej. un chip «+ nueva»—, moverlo a la primera fila del contenido).

- [ ] **Step 3: `AparienciaPantalla`**

`LazyColumn` con: fila de cabecera (`BotonChevron("Volver", haciaLaIzquierda = true, onClick = { nav.popBackStack() })` + `Text("Apariencia", titulo, heading)`), y después `Tarjeta(titulo = "Modo") { Segmentado(…) }`, `Tarjeta(titulo = "Paleta oscura") { GrupoPaletas(…) }`, `Tarjeta(titulo = "Paleta clara") { … }`, `Tarjeta(titulo = "Fuente") { GrupoFuentes(actual.idFuente, vm::cambiarFuente) }`, `Tarjeta(titulo = "Diseño") { GrupoDisenos(actual.idDiseno, vm::cambiarDiseno) }`, `Tarjeta(titulo = "Barra") { FilaInterruptor("Nombres en la barra", actual.etiquetasBarra, vm::cambiarEtiquetasBarra) }` (un `Switch` de Material con `Role.Switch`, texto `cuerpo`, fila ≥ 48 dp).

- [ ] **Step 4: `GrupoFuentes`**

Por fuente, una fila seleccionable como `FilaPaleta` (`Role.RadioButton`, tinte 14 % si elegida, visto `ic_ok`). El nombre va **en esa fuente**: `CuentasTemaConPaleta(paletaActual, fuente = f) { Text(f.nombre, style = CuentasTema.tipografia.subtitulo); Text("Bs 13.517,75 · $84.20", style = CuentasTema.tipografia.cifra, color = textoSecundario) }`. La paleta actual se obtiene de `paletaPorId(if (CuentasTema.oscuro) actual.idPaletaOscura else actual.idPaletaClara, CuentasTema.oscuro)`.

- [ ] **Step 5: `GrupoDisenos`**

Por diseño, una fila seleccionable con **miniatura** a la izquierda (`Box` de 72×56 dp): dentro, `CuentasTemaConPaleta(paletaActual, fuente, diseno = d) { Column { Tarjeta(Modifier.height(28.dp)) {}; Spacer; MiniBarra() } }` donde `MiniBarra` es una `Row` de 4 puntos de 6 dp en `textoSecundario` con el «+» de 10 dp en `primario` (con la forma de `d.botonMas`), fondo `superficieElevada` y radio según `d.barra` (`FLOTANTE`/`AURORA` → píldora con margen; `PILDORA`/`SUBRAYADO` → rectángulo). A la derecha: `d.nombre` (`subtitulo`) y `d.descripcion` (`etiqueta`, `textoSecundario`, 2 líneas). Visto si elegida.

- [ ] **Step 6: Compilar y tests**: `./gradlew :app:assembleDebug :app:testDebugUnitTest` → verde. Instalar y abrir Ajustes → Apariencia; cambiar fuente y diseño y ver que la app cambia en vivo.

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/java/com/cuentasclaras/app
git commit -m "feat(ajustes): secciones plegables y pantalla Apariencia con fuente, diseño y nombres en la barra"
```

---

### Task 10: Íconos de la app elegibles y coloreados con la paleta oscura

**Files:**
- Create: `scripts/generar-iconos-app.py`
- Generate: `android/app/src/main/res/drawable/ic_launcher_{check,moneda,libreta}_{paleta}.xml` (18), `ic_launcher_mono_{check,moneda,libreta}.xml` (3), `android/app/src/main/res/mipmap-anydpi-v26/ic_launcher_{forma}_{paleta}.xml` (18), `android/app/src/main/res/values/colores_lanzador.xml`
- Modify: `android/app/src/main/AndroidManifest.xml`, `android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` (queda como ícono genérico para `android:icon` de `<application>`)
- Create: `android/app/src/main/java/com/cuentasclaras/app/data/IconoApp.kt`
- Modify: `ui/pantallas/apariencia/AparienciaViewModel.kt`, `ui/pantallas/apariencia/AparienciaPantalla.kt` (grupo «Ícono»), `CuentasApp.kt` (`AppContainer.iconoApp`)
- Test: `android/app/src/test/java/com/cuentasclaras/app/data/IconoAppTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  data class IconoApp(val id: String, val nombre: String)
  val ICONOS_APP: List<IconoApp>; fun iconoPorId(id: String?): IconoApp
  class IconoAppRepo(context) { fun aliasDe(idIcono: String, idPaletaOscura: String): ComponentName; fun aplicar(idIcono: String, idPaletaOscura: String) }
  ```

- [ ] **Step 1: Script generador**

`scripts/generar-iconos-app.py` (Python 3, sin dependencias; se ejecuta con `python -I scripts/generar-iconos-app.py` desde la raíz del repo). Contiene:
- `PALETAS_OSCURAS = {"grafito_menta": ("#0F110F", "#2DD4BF"), "grafito_lima": ("#0F110F", "#C5F05A"), "noche_lima": ("#0A0F1C", "#C5F05A"), "como_antes": ("#0A0F1C", "#2DD4BF"), "cacao": ("#1B1411", "#FFA24C"), "pizarra_cobalto": ("#0E1117", "#8AB4FF")}` (fondo, primario; copiados de DESIGN.md §2.1).
- `FORMAS`: diccionario id → lista de `<path>` con `{color}` como marcador de `strokeColor`:
  - `check`: los dos paths actuales de `ic_launcher_foreground.xml`.
  - `moneda`: `M69,54 A17,17 0 1,1 35,54 A17,17 0 1,1 69,54` (moneda delantera, trazo 6) + `M52,43 L52,65 M46,48 L58,48 M46,60 L58,60` (el «$» sugerido con dos barras, trazo 5) + arco trasero `M62,36 A17,17 0 0,1 76,62` (trazo 5, alpha 0,6 via color `#99{rgb}`).
  - `libreta`: rectángulo `M36,32 H72 A4,4 0 0,1 76,36 V72 A4,4 0 0,1 72,76 H36 Z` (trazo 6) + lomo `M42,32 V76` (trazo 4) + tres rayas `M50,46 H68 M50,54 H68 M50,62 H60` (trazo 4).
- Escribe: por (forma, paleta) el vector `drawable/ic_launcher_<forma>_<paleta>.xml` (108 dp, viewport 108, `{color}` → primario en ARGB); por forma el monocromo `drawable/ic_launcher_mono_<forma>.xml` (color `#FFFFFFFF`); por (forma, paleta) el adaptable `mipmap-anydpi-v26/ic_launcher_<forma>_<paleta>.xml` con `<background android:drawable="@color/lanzador_fondo_<paleta>"/>`, `<foreground>` y `<monochrome>`; y `values/colores_lanzador.xml` con `lanzador_fondo_<paleta>`.
- Imprime cuántos archivos escribió. Cabecera en cada XML: `<!-- Generado por scripts/generar-iconos-app.py; no editar a mano. -->`.

Ejecutarlo. Dejar `ic_launcher.xml` y `ic_launcher_foreground.xml` actuales como ícono genérico de `<application android:icon>`.

- [ ] **Step 2: Manifest**

Quitar el `<intent-filter>` de `.MainActivity` (dejar `exported="true"`, `launchMode`, `windowSoftInputMode`). Añadir 18 alias, uno por combinación, con esta plantilla (el script también puede imprimir este bloque; copiarlo):

```xml
<activity-alias
    android:name=".Lanzador_check_noche_lima"
    android:targetActivity=".MainActivity"
    android:enabled="true"
    android:exported="true"
    android:icon="@mipmap/ic_launcher_check_noche_lima"
    android:roundIcon="@mipmap/ic_launcher_check_noche_lima"
    android:label="${etiquetaApp}">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity-alias>
```

Solo `Lanzador_check_noche_lima` lleva `android:enabled="true"`; los otros 17, `"false"`.

- [ ] **Step 3: Test que falla (`IconoAppTest`, Robolectric)**

```kotlin
@RunWith(RobolectricTestRunner::class)
class IconoAppTest {
    private val contexto: Context = ApplicationProvider.getApplicationContext()
    private val repo = IconoAppRepo(contexto)
    private fun habilitados() = ICONOS_APP.flatMap { i -> PALETAS_OSCURAS.map { p -> repo.aliasDe(i.id, p.id) } }
        .filter { contexto.packageManager.getComponentEnabledSetting(it) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED }

    @Test fun `aplicar deja un solo alias habilitado, el de la forma y la paleta`() {
        repo.aplicar("moneda", "cacao")
        assertEquals(listOf(repo.aliasDe("moneda", "cacao")), habilitados())
        repo.aplicar("moneda", "pizarra_cobalto")
        assertEquals(listOf(repo.aliasDe("moneda", "pizarra_cobalto")), habilitados())
    }

    @Test fun `un id de icono desconocido cae a check`() = assertEquals("check", iconoPorId("estrella").id)
}
```

Run: `./gradlew :app:testDebugUnitTest --tests "com.cuentasclaras.app.data.IconoAppTest"` → FAIL de compilación.

- [ ] **Step 4: `IconoApp.kt`**

```kotlin
package com.cuentasclaras.app.data

data class IconoApp(val id: String, val nombre: String)

val ICONOS_APP = listOf(IconoApp("check", "Visto"), IconoApp("moneda", "Monedas"), IconoApp("libreta", "Libreta"))
fun iconoPorId(id: String?): IconoApp = ICONOS_APP.firstOrNull { it.id == id } ?: ICONOS_APP.first { it.id == ID_ICONO_POR_DEFECTO }

/** Cambia el ícono del lanzador habilitando el `activity-alias` de la forma × paleta oscura y deshabilitando los demás. */
class IconoAppRepo(private val context: Context) {
    fun aliasDe(idIcono: String, idPaletaOscura: String) = ComponentName(context, "com.cuentasclaras.app.Lanzador_${idIcono}_$idPaletaOscura")

    fun aplicar(idIcono: String, idPaletaOscura: String) {
        val pm = context.packageManager
        val objetivo = aliasDe(idIcono, idPaletaOscura)
        for (icono in ICONOS_APP) for (paleta in PALETAS_OSCURAS) {
            val alias = aliasDe(icono.id, paleta.id)
            val estado = if (alias == objetivo) COMPONENT_ENABLED_STATE_ENABLED else COMPONENT_ENABLED_STATE_DISABLED
            if (pm.getComponentEnabledSetting(alias) != estado) pm.setComponentEnabledSetting(alias, estado, DONT_KILL_APP)
        }
    }
}
```

Ojo: el nombre de clase del alias usa el **paquete del manifest** (`com.cuentasclaras.app`), no el `applicationId` (`verif` añade sufijo al id, no al paquete de clases): `ComponentName(context.packageName, "com.cuentasclaras.app.Lanzador_…")`. `PALETAS_OSCURAS` vive en `ui/theme/Color.kt`; para no depender de `ui` desde `data`, mover `PALETAS`/`Paleta`/`Colores` no es necesario: importar `com.cuentasclaras.app.ui.theme.PALETAS_OSCURAS` es aceptable aquí (solo ids). Añadir `val iconoApp = IconoAppRepo(context)` en `AppContainer`.

En `AparienciaViewModel(repo, iconoApp)`: `cambiarIcono(id)` escribe la preferencia y luego `iconoApp.aplicar(id, actual.idPaletaOscura)`; `cambiarPaletaOscura(id)` escribe y luego `iconoApp.aplicar(actual.idIcono, id)`. (`actual` = último valor de `apariencia.value`, o leer `repo.apariencia.first()` tras escribir.)

- [ ] **Step 5: Grupo «Ícono» en Apariencia**

`Tarjeta(titulo = "Ícono de la app") { Row(spacedBy(e.m), selectableGroup) { ICONOS_APP.forEach { i -> Column(selectable RadioButton, ≥ 48 dp, centrado) { Box(64 dp, clip CircleShape, background = paletaOscuraActual.colores.fondo) { Icon(painterResource(idDrawable("ic_launcher_mono_${i.id}")), tint = paletaOscuraActual.colores.primario, 40 dp) }; Text(i.nombre, etiqueta); if (elegida) Icon(ic_ok) } } }`. `idDrawable` = `context.resources.getIdentifier(nombre, "drawable", context.packageName)`; aceptable aquí porque los nombres los genera el script. Nota bajo la fila: «El lanzador puede tardar unos segundos en mostrar el cambio.» (`etiqueta`, `textoSecundario`).

- [ ] **Step 6: Tests, compilar, instalar y comprobar en el teléfono** que elegir «Monedas» y cambiar la paleta oscura a «Cacao» cambia el ícono (puede tardar; en Xiaomi a veces hay que volver al escritorio).

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug` → verde.

- [ ] **Step 7: Commit**

```bash
git add scripts/generar-iconos-app.py android/app/src/main/res android/app/src/main/AndroidManifest.xml android/app/src/main/java/com/cuentasclaras/app android/app/src/test/java/com/cuentasclaras/app/data/IconoAppTest.kt
git commit -m "feat(icono): tres íconos de app elegibles, coloreados con la paleta oscura mediante alias de actividad"
```

---

### Task 11: DESIGN.md, README e issue

**Files:**
- Modify: `android/DESIGN.md` (§2 tabla de `Formas`; nueva §2.3 Diseños; §3 fuentes; §4 `SeccionPlegable`, variantes de `Tarjeta` y barra; §5 y §8 «salvo Aurora»; ícono de la app), `android/README.md` (sección «Diseño» + `scripts/generar-iconos-app.py` + lista de tests nuevos en «Qué cubren los tests»).

- [ ] **Step 1: Editar DESIGN.md** siguiendo el spec §9. En §2.3 incluir la tabla del spec §6.2 con el nombre definitivo del diseño de Task 6 y las 5 líneas de `docs/research/2026-10-17-diseno-con-skills.md`. En §3, la tabla de fuentes de Task 3 (solo las que quedaron) con sus `opsz`.
- [ ] **Step 2: Editar README** («Diseño», tests: `data/AparienciaRepoTest`, `data/IconoAppTest`, `ui/theme/FuentesTest`).
- [ ] **Step 3: Commit**

```bash
git add android/DESIGN.md android/README.md
git commit -m "docs(diseño): fuentes, diseños, SeccionPlegable e íconos de la app en DESIGN.md y README"
```

---

### Task 12: Verificación única en el teléfono y cierre

- [ ] **Step 1:** `./gradlew :app:assembleVerif` e instalar en el Xiaomi (`adb install -r app/build/outputs/apk/verif/app-verif.apk`). **No abrir selectores SAF por adb** (memoria `no-selector-saf-telefono-daniel`).
- [ ] **Step 2: Una sola pasada** (un ancho, un tema): Ajustes → desplegar «Cuentas» y volver a plegar → «Apariencia» → cambiar fuente (Fraunces) → cambiar diseño a cada uno de los cuatro, con una captura de Inicio por diseño → apagar «Nombres en la barra» → elegir ícono «Monedas» → volver → Inicio → tocar «Ingresos» y ver la dona y la curva de ingresos → tocar «Gastos». Dejar el teléfono con Libreta, Bricolage, etiquetas y «Visto».
- [ ] **Step 3:** Anotar en el informe final lo menor que aparezca, sin abrir otra ronda. Cerrar el issue #2 con `gh issue close 2 --comment "<resumen de 3 líneas>"`.
