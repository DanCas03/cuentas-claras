# Design System: Cuentas Claras (nativa, Jetpack Compose)

> Única fuente visual de la app. Toda pantalla nueva lee este archivo antes de escribir un composable.
> Código de referencia: `app/src/main/java/com/cuentasclaras/app/ui/theme/` (tokens) y
> `app/src/main/java/com/cuentasclaras/app/ui/componentes/` (componentes base).

## 1. Concepto

**Una libreta de cuentas bien llevada, en el teléfono.** Es la dirección «A · Libreta» elegida por Daniel: la
cifra de patrimonio manda, escrita grande como el total al pie de la libreta. Debajo van tarjetas tranquilas que
se apilan en una sola columna, y al fondo una barra inferior con el «+» siempre a mano. La estructura, la fuente,
los radios y la densidad son comunes a toda la app; **el color sale de paletas** que Daniel elige (E20).

- **Atmósfera:** app diaria y equilibrada (densidad 5/10), simétrica y predecible (variación 2/10: es una
  herramienta, no una revista), movimiento contenido (4/10: resortes cortos, nada decorativo en bucle).
- **Prioridad absoluta:** registrar un gasto en ≤ 3 toques desde Inicio (FAB «+» → monto en el teclado propio →
  categoría guarda). Ninguna decisión visual puede alargar ese camino.
- **Bimoneda:** Bs y USD conviven. Toda cifra pasa por `formatMoney` y usa estilo tabular.

### Estructura común (todas las pantallas)
- Una columna, márgenes laterales `espacios.m` (16 dp), separación entre bloques `espacios.m`, entre secciones
  `espacios.l` (24 dp).
- Cabecera: fecha pequeña en `etiqueta` + `textoSecundario` y el título de la pantalla en `titulo`.
- Inicio: rótulo «PATRIMONIO» (`etiqueta`, mayúsculas, tracking 0,12 em, `textoSecundario`), cifra en
  `cifraGrande` **sin caja**, equivalencia en la otra moneda en `cuerpo` + `textoSecundario`. Luego `Tarjeta`
  «Este mes» con dos columnas (ingresos / gastos, cada una con flecha y signo) y **la línea de progreso** debajo
  (pista `fondo`, relleno `gasto`, 6 dp, píldora) con la frase «Gastaste el N % de lo que entró». Después la
  `Tarjeta` de movimientos recientes.
- Barra inferior: franja en `superficieElevada` con hasta 4 destinos (ícono + texto, activo en `primario`) y,
  al centro, el «+» como disco de 56 dp en `primario` con el ícono en `sobrePrimario`, que sobresale 14 dp hacia
  arriba (`offset(y = (-14).dp)`). Se reserva el alto de la barra con `navigationBarsPadding()`.
- Pantallas equivalentes usan **el mismo layout** (todas las listas iguales, todas las hojas de edición iguales).

## 2. Tokens

Se leen SIEMPRE desde `CuentasTema` (`CuentasTema.colores`, `.tipografia`, `.formas`, `.espacios`). Nombres exactos:

| Grupo | Tipo | Campos |
|---|---|---|
| Colores | `data class Colores` | `fondo`, `superficie`, `superficieElevada`, `borde`, `primario`, `sobrePrimario`, `ingreso`, `gasto`, `aviso`, `error`, `texto`, `textoSecundario` |
| Tipografía | `data class Tipografia` | `titulo`, `subtitulo`, `cuerpo`, `etiqueta`, `cifraGrande`, `cifra` |
| Formas | `data class Formas` | `tarjeta` = 20 dp · `control` = 12 dp · `pildora` = 50 % |
| Espacios | `data class Espacios` | `xs` 4 · `s` 8 · `m` 16 · `l` 24 · `xl` 32 dp |

Roles de color:
- `fondo`: lienzo de la pantalla. `superficie`: carcasa exterior de tarjetas, hojas modales, círculos de estado vacío.
  `superficieElevada`: núcleo de las tarjetas, teclas del teclado, menús, barra inferior.
- `borde`: trazos decorativos **y relleno de controles** (campos, chips, botón secundario, pista del segmentado).
  Nunca es el único indicador de estado o de foco.
- `primario` / `sobrePrimario`: la acción principal, el estado activo y el foco. Un único acento por paleta.
- `ingreso` / `gasto`: montos con signo; `aviso` / `error`: banners y validación.
- `texto` / `textoSecundario`: texto principal y metadatos.
- Texto sobre `error` (botón de peligro): `Colores.sobreError()` (el `fondo` en paletas oscuras, blanco en las claras).

### 2.1 Paletas (E20)

Catálogo cerrado en `PALETAS` (`ui/theme/Color.kt`): `Paleta(id, nombre, esOscura, colores)`. El `id` es estable
porque se guarda en DataStore. Todas pasan WCAG AA (≥ 4,5:1) para `texto`, `textoSecundario`, `ingreso`, `gasto`,
`aviso`, `error` y `primario` sobre `fondo`, `superficie` y `superficieElevada`, y para `sobrePrimario` sobre
`primario`, `texto` sobre `borde`, `textoSecundario` sobre `borde` (placeholder) y `sobreError()` sobre `error`.

**Oscuras**

| Token | Grafito menta `grafito_menta` | Grafito lima `grafito_lima` | **Noche lima** `noche_lima` ★ | Como antes `como_antes` | Cacao `cacao` | Pizarra cobalto `pizarra_cobalto` |
|---|---|---|---|---|---|---|
| fondo | `#0F110F` | `#0F110F` | `#0A0F1C` | `#0A0F1C` | `#1B1411` | `#0E1117` |
| superficie | `#181B18` | `#181B18` | `#121A2B` | `#121A2B` | `#251C17` | `#161B24` |
| superficieElevada | `#222622` | `#222622` | `#182338` | `#182338` | `#30251F` | `#1E2530` |
| borde | `#2F342E` | `#2F342E` | `#223049` | `#223049` | `#43352C` | `#2A3341` |
| primario | `#2DD4BF` | `#C5F05A` | `#C5F05A` | `#2DD4BF` | `#FFA24C` | `#8AB4FF` |
| sobrePrimario | `#06251F` | `#142008` | `#142008` | `#06251F` | `#2B1605` | `#0B1A3D` |
| ingreso | `#34D399` | `#62C6F5` | `#34D399` | `#34D399` | `#9ED67C` | `#4ADE80` |
| gasto | `#F87171` | `#FF8F5E` | `#F87171` | `#F87171` | `#F58A92` | `#FF8A65` |
| aviso | `#FBBF24` | `#F0C24B` | `#FBBF24` | `#FBBF24` | `#F3C75A` | `#FACC15` |
| error | `#FB7185` | `#FF6B72` | `#FB7185` | `#FB7185` | `#FF6B5E` | `#FB7185` |
| texto | `#EEF1EA` | `#EEF1EA` | `#E8EDF6` | `#E8EDF6` | `#F7EEE6` | `#E9EDF5` |
| textoSecundario | `#A2A99D` | `#A2A99D` | `#939EB3` | `#939EB3` | `#BCAA9B` | `#9AA4B5` |
| peor ratio | gasto 5,55 | error 5,55 | gasto 5,68 | gasto 5,68 | error 5,33 | error 5,73 |

**Claras**

| Token | Menta claro `menta_claro` | Lima claro `lima_claro` | **Noche claro** `noche_claro` ★ | Libreta `libreta` | Cobalto claro `cobalto_claro` |
|---|---|---|---|---|---|
| fondo | `#F3F5F2` | `#F3F5F2` | `#F1F4F9` | `#F3EFE6` | `#F4F4F5` |
| superficie | `#FAFBF9` | `#FAFBF9` | `#F8FAFD` | `#FBF8F2` | `#FAFAFA` |
| superficieElevada | `#FFFFFF` | `#FFFFFF` | `#FFFFFF` | `#FFFFFF` | `#FFFFFF` |
| borde | `#DDE2DB` | `#DDE2DB` | `#D9E0EC` | `#E2DACB` | `#E4E4E7` |
| primario | `#0F766E` | `#4D7C0F` | `#18233A` | `#24357A` | `#1D4ED8` |
| sobrePrimario | `#FFFFFF` | `#FFFFFF` | `#C5F05A` | `#FBF8F2` | `#FFFFFF` |
| ingreso | `#047857` | `#0369A1` | `#047857` | `#1C6E47` | `#047857` |
| gasto | `#B91C1C` | `#C2410C` | `#B91C1C` | `#B0362A` | `#B91C1C` |
| aviso | `#92400E` | `#854D0E` | `#92400E` | `#855400` | `#92400E` |
| error | `#BE123C` | `#BE123C` | `#BE123C` | `#A1203B` | `#BE123C` |
| texto | `#121512` | `#121512` | `#0A0F1C` | `#1D1B16` | `#18181B` |
| textoSecundario | `#586058` | `#586058` | `#55607A` | `#625C50` | `#52525B` |
| peor ratio | primario 4,99 | primario 4,55 | ingreso 4,97 | gasto 5,37 | ingreso 4,99 |

★ = por defecto (`ID_PALETA_OSCURA_POR_DEFECTO = "noche_lima"`, `ID_PALETA_CLARA_POR_DEFECTO = "noche_claro"`).
«Como antes» es la paleta de la PWA (alto contraste); su pareja clara sugerida es Noche claro. Parejas
sugeridas (la elección es libre): Grafito menta ↔ Menta claro, Grafito lima ↔ Lima claro, Noche lima / Como
antes ↔ Noche claro, Pizarra cobalto ↔ Cobalto claro, Cacao ↔ Libreta.

Regla de las claras: `primario` sobre `borde` baja de 4,5:1 en Menta claro (4,16) y Lima claro (3,80). **Nunca se
pone texto `primario` sobre relleno de controles**; un chip o segmento activo usa `primario` de fondo con
`sobrePrimario`.

### 2.2 Modo y selección (E20, E21, E22)

- Preferencias en DataStore (`data/AparienciaRepo.kt`, expuesto como `AppContainer.apariencia`): `Apariencia(modo,
  idPaletaOscura, idPaletaClara)` con `Flow` y `cambiarModo` / `cambiarPaletaOscura` / `cambiarPaletaClara`.
- `ModoTema.SISTEMA` (por defecto) sigue `isSystemInDarkTheme()`; `CLARO` / `OSCURO` lo fijan. La paleta clara y
  la oscura se eligen **por separado**; el modo decide cuál se ve.
- `CuentasTema { … }` lee todo eso solo; las pantallas nunca deciden colores por su cuenta. Para pintar una
  muestra de otra paleta (selector de Ajustes) se usa `CuentasTemaConPaleta(paleta) { … }`.
- Barras del sistema transparentes (edge-to-edge) con íconos acordes al modo activo (`enableEdgeToEdge` con
  `SystemBarStyle.dark/light`). La ventana de arranque usa `@color/fondo_arranque` (`values` / `values-night`):
  con modo manual opuesto al del sistema hay un destello breve, aceptado.
- Inicio (T13, E21): botón sol/luna en la cabecera, `contentDescription` «Cambiar a modo claro» / «Cambiar a modo
  oscuro» (íconos `ic_sol` / `ic_luna`); fija el modo manual opuesto al que se ve.
- Ajustes (T16, E22): sección «Apariencia» con Modo (`Segmentado`: Sistema / Claro / Oscuro), «Paleta oscura» y
  «Paleta clara» (lista de `PALETAS_OSCURAS` / `PALETAS_CLARAS` con muestra de `fondo`, `superficieElevada` y
  `primario` de cada una).

## 3. Tipografía

**Bricolage Grotesque** (variable `opsz,wdth,wght`; Ateliertriay, SIL Open Font License 1.1 sin Reserved Font
Name; licencia en `app/src/main/assets/fuentes/OFL.txt`, origen `github.com/google/fonts/tree/main/ofl/bricolagegrotesque`).
Grotesca con ejes ópticos: sobria en texto, con carácter en tamaños grandes.

- **Carga (E23):** desde `assets/fuentes/bricolage_grotesque_variable.ttf` con
  `Font(path, assetManager, weight, variationSettings = FontVariation.Settings(FontVariation.weight(p), FontVariation.Setting("opsz", x)))`.
  `Font(R.font…, variationSettings)` **no sirve**: en el Xiaomi de Daniel (Android 16) ignora la variación y pinta
  la instancia por defecto (wght 800, opsz 96). Verificado en el teléfono.
- **Dos familias:** texto (`opsz` 14; pesos 400/500/600/700) y display (`opsz` 40; pesos 500/600/700).
- Ninguna otra fuente entra en el APK.

| Estilo | Familia | sp | Peso | Interlínea | Extra |
|---|---|---|---|---|---|
| `titulo` | display | 24 | 700 | 30 | — |
| `subtitulo` | texto | 17 | 600 | 22 | — |
| `cuerpo` | texto | 15 | 400 | 21 | — |
| `etiqueta` | texto | 13 | 500 | 18 | — |
| `cifraGrande` | display | 40 | 700 | 46 | `tnum`, tracking −0,02 em |
| `cifra` | texto | 17 | 600 | 22 | `tnum` |

- Toda cifra de dinero usa `cifra` o `cifraGrande` (dígitos tabulares: las columnas no bailan).
- Rótulos de sección en mayúsculas: `etiqueta` con `letterSpacing = 0.12.em` y `textoSecundario`.
- Material 3 recibe `Tipografia.comoMaterial()`: `titulo → headlineSmall`, `subtitulo → titleMedium`,
  `cuerpo → bodyLarge/bodyMedium`, `etiqueta → labelLarge`; los demás roles conservan su escala con la familia propia.
- Jerarquía por peso y color, no por tamaños gritones. Nada por debajo de 11 sp (solo los rótulos de la barra inferior).

## 4. Componentes (`ui/componentes/`)

Firmas exactas; usar siempre estos componentes en lugar de los de Material directamente.

- **`Tarjeta(modifier, titulo: String? = null, accion: (@Composable () -> Unit)? = null, contenido)`**: doble bisel.
  Carcasa `superficie`, radio 20 dp, bisel de 4 dp; núcleo `superficieElevada`, radio 16 dp (concéntrico), relleno
  16 dp, filo superior con degradado `texto` al 6 % → transparente. Título en `subtitulo` (encabezado accesible),
  acción a la derecha. Sin sombras.
- **`Monto(minor, currency, signo = Signo.NINGUNO, grande = false)`** (en `Tarjeta.kt`): `formatMoney` + `cifra` /
  `cifraGrande`. `POSITIVO` → «+» y `ingreso`; `NEGATIVO` → «−» (U+2212) y `gasto`; `NINGUNO` → `formatMoney` tal
  cual en `texto`. En filas de movimientos el monto va acompañado SIEMPRE de un ícono `ic_entra` / `ic_sale` (16 dp,
  mismo color): el color nunca es la única señal.
- **Botones** (`BotonPrincipal`, `BotonSecundario`, `BotonPeligro`; `(texto, onClick, enabled = true, modifier)`):
  píldora, alto mínimo 52 dp, relleno horizontal 24 dp, texto `cuerpo` 600. Principal `primario`/`sobrePrimario`;
  secundario `borde`/`texto`; peligro `error`/`sobreError()`. Al presionar se hunden a escala 0,97 con resorte;
  deshabilitados al 38 %. Un solo principal por pantalla. Acción con guardado: `enabled = !estado.guardando`.
- **`Segmentado(opciones, seleccion, onSeleccion)`**: pista píldora en `borde` con holgura de 4 dp; cada segmento
  ≥ 48 dp de alto; activo en `primario`/`sobrePrimario`, inactivo con `texto`; color animado con la curva propia;
  semántica de pestañas.
- **`Chip(texto, onClick = null, activo = false, enabled = true, sugerido = false)`** (E6): píldora de 36 dp de alto
  visual (área táctil 48 dp si es tocable), texto `etiqueta` 600. Normal `borde`/`texto`; activo
  `primario`/`sobrePrimario`; sugerido: tinte `primario` al 14 % + contorno `primario` de 1 dp + `texto`.
- **`Banner(texto, tipo: TipoBanner, accion = null)`** (`OK`, `AVISO`, `ERROR`): radio `control`, fondo con tinte del
  12 % del color del tipo (`ingreso`, `aviso`, `error`) sobre `superficieElevada`, ícono `ic_ok` / `ic_aviso` /
  `ic_error` en círculo de 32 dp con descripción «Correcto» / «Aviso» / «Error», texto `cuerpo` en `texto`.
  Región viva educada (lo anuncia el lector de pantalla).
- **`Vacio(icono: String, texto: String)`**: emoji de 32 sp en un círculo de 72 dp en `superficie` y la frase en
  `cuerpo` + `textoSecundario`, centrados. Toda lista vacía lo usa.
- **Campos** (`CampoTexto(valor, onCambio, etiqueta, placeholder = "", teclado = KeyboardType.Text, singleLine = true, minLines = 1)`
  — E6 —, `Selector<T>(etiqueta, opciones, seleccion, texto, onSeleccion, contentDescription: String? = null)` — E6 —,
  `CampoFecha(etiqueta, iso, onCambio)`): etiqueta ENCIMA en `etiqueta` + `textoSecundario` (nunca flotante), campo
  de ≥ 56 dp con relleno `borde`, radio `control`, sin línea inferior; foco = anillo de 2 dp en `primario`. La
  etiqueta pasa como `contentDescription` del campo. Error de validación: `Banner` de tipo `ERROR` justo debajo
  del campo. `Selector` usa `ExposedDropdownMenuBox` con menú en `superficieElevada`. `CampoFecha` muestra
  «jueves 15 de octubre de 2026» con `ic_calendario` y abre el `DatePickerDialog` de Material 3; conversión con
  `isoDesdeDatePicker` / `millisParaDatePicker` (UTC, nunca la zona local).
- **`HojaModal(titulo, onCerrar, contenido)`**: `ModalBottomSheet` siempre expandida, `superficie`, radio superior
  28 dp, asa en `borde`, título en `titulo` + botón «Cerrar» (`ic_cerrar`), contenido desplazable con
  `imePadding()` y márgenes de 16 dp. Para editar. Las confirmaciones destructivas van en `AlertDialog`.
- **`Teclado(onTecla)`**: 4 × 3 (`1 2 3 / 4 5 6 / 7 8 9 / , 0 ⌫`), teclas de 56 dp de alto separadas 8 dp
  (248 dp en total), fondo `superficieElevada`, radio `control`, dígitos en `cifra` a 24 sp, «⌫» con `ic_borrar`.
  `contentDescription` «coma» y «borrar». Vibración corta de tecla.
- **`CategoriaGrid(categorias, enabled, onElegir)`**: 4 columnas sin scroll propio; cada celda: cuadro de 48 dp
  con el emoji de la categoría (24 sp) sobre el color de la categoría al 18 %, nombre en `etiqueta` a 2 líneas.
  `enabled = false` al 38 % mientras se guarda. El color de la categoría es dato del usuario:
  `colorDeCategoria(hex, respaldo)`.
- **Barra inferior con «+»**: ver §1. Íconos `ic_inicio`, `ic_movimientos`, `ic_calendario`, `ic_ajustes` (24 dp)
  y `ic_mas` (28 dp) dentro del disco.
- **Íconos**: vector drawables propios en `res/drawable/ic_*.xml` (trazos de Material Icons, Apache 2.0) con
  `painterResource` y `tint` del tema. Tamaños: 16 (en línea con cifras), 20 (campos), 24 (navegación y acciones).
  No se añade `material-icons-extended`.
- **Ícono de la app**: adaptable (`mipmap-anydpi-v26/ic_launcher.xml`): «C» abierta con un visto en lima `#C5F05A`
  sobre `#0A0F1C` (Noche lima), con capa monocroma.

## 5. Movimiento (`ui/theme/Movimiento.kt`)

- Curva propia `Movimiento.suave = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)`; **prohibido `LinearEasing`**.
- Duraciones: `CORTO` 150 ms (foco, anillos), `MEDIO` 220 ms (colores de selección, chips), `LARGO` 300 ms
  (entradas de contenido). La salida dura ~2/3 de la entrada.
- `Movimiento.resorte()` = `spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)` para presiones
  (botones a escala 0,97) y cambios de estado físicos.
- `Movimiento.transicion(duracion)` = `tween` con la curva propia, para colores y opacidad.
- Solo se animan `graphicsLayer` (escala, alfa, traslación) y colores; nunca alto o ancho. Nada en bucle
  infinito. Las animaciones nunca bloquean la entrada.

## 6. Reglas de forma (soft-skill §2 y §4 traducidas a Compose)

- **Sin bordes grises genéricos de 1 px** como separador por defecto: los planos se separan por tono (`fondo` →
  `superficie` → `superficieElevada`) y por espacio. El único trazo de 1 dp permitido es el filo iluminado del
  núcleo de la tarjeta, el contorno del chip sugerido y el divisor de 1 dp de la barra inferior (`borde`).
- **Sin sombras duras**: `elevation = null` en botones; ninguna `shadow()` con negro. La profundidad es tonal.
- **Doble bisel** en tarjetas (ver `Tarjeta`): carcasa + núcleo concéntrico (radio exterior 20, bisel 4,
  interior 16).
- **Botones píldora** con relleno generoso; una acción principal por pantalla; las secundarias son tonales.
- **Ritmo de espacios** en la escala 4/8/16/24/32: dentro de un componente `s`–`m`, entre componentes `m`, entre
  secciones `l`, márgenes de pantalla `m`. Nada de valores sueltos.
- **Objetivos táctiles** ≥ 48 dp con ≥ 8 dp entre ellos; teclas del teclado ≥ 56 dp.
- **Accesibilidad**: todo ícono suelto con `contentDescription` en español; los textos visibles y
  `contentDescription` son los que usa `tocar` en la verificación por adb.

## 7. Gráficas

- Dona de gastos por categoría: **usa los colores de categoría del usuario** (`colorDeCategoria`), grosor de anillo
  20 dp, separación entre porciones de 2° en el color `superficieElevada`; centro con el total en `cifra`.
  Máximo 6 porciones + «Otras» en `textoSecundario`. Leyenda debajo, en filas con el color, el nombre y el monto.
- Series (barras/líneas de comparativa): serie actual en `primario`, serie anterior en `textoSecundario` al 60 %;
  ingreso/gasto siempre en `ingreso`/`gasto` con signo en la etiqueta. Líneas de 2,5 dp con extremos redondeados;
  barras con radio superior de 6 dp. Rejilla de 1 dp en `borde`, sin ejes pesados.
- La línea de progreso de «Este mes» (§1) y la de presupuestos: pista `fondo` (o `borde` dentro de `superficie`),
  6 dp, píldora; relleno `gasto` (o `aviso` cuando el presupuesto pasa del 80 %, `error` si se excede).
- Toda gráfica tiene alternativa en texto (`contentDescription` con el resumen) y estado vacío con `Vacio`.

## 8. Prohibiciones

- Hex sueltos o `Color(…)` en pantallas (salvo el color de categoría, que es dato del usuario y pasa por
  `colorDeCategoria`). `dp` mágicos en pantallas: solo `CuentasTema.espacios` y las medidas de los componentes.
- `kotlin.math.round` (redondea al par): solo `Math.round` / `roundToLong`.
- Colores dinámicos (`dynamicLightColorScheme` / `dynamicDarkColorScheme`) y cualquier paleta fuera de `PALETAS`.
- `MaterialTheme` directo en pantallas: siempre `CuentasTema`; no leer `MaterialTheme.colorScheme` en código propio.
- `Font(R.font…)` para la fuente de la app (ver E23) y cualquier fuente distinta de Bricolage Grotesque.
- Layouts distintos para pantallas equivalentes: todas las listas, hojas y formularios se ven igual.
- `LinearEasing`, animaciones de más de 300 ms en la interfaz, sombras negras, gradientes de texto, brillos neón.
- Emojis como íconos de interfaz (navegación, acciones): solo vectores `ic_*`. Los emojis son dato del usuario
  (categorías) o el ícono de `Vacio`.
- Color como única señal de ingreso/gasto/estado: siempre signo y/o ícono.
- Texto `primario` sobre relleno de controles en paletas claras (§2.1).
