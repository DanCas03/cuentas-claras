# Cómo contribuir a Cuentas Claras

Gracias por el interés. Cuentas Claras es una app personal de finanzas para la realidad
venezolana (Bs + USD, varias cuentas, varias monedas). Toda contribución entra por
**pull request desde un fork**; no hace falta ser colaborador del repo.

## Qué hay en el repo

| Carpeta | Qué es |
|---|---|
| `android/` | La app nativa (Kotlin + Jetpack Compose). Es donde está el desarrollo activo. |
| `src/` | La PWA original (TypeScript + Vite). Ya no se despliega, pero sus tests siguen vivos y sirven de referencia para las reglas de negocio. |
| `docs/` | Guías de uso, investigación y los specs/planes con los que se construyó la app. |

Lee `README.md`, `android/README.md` y `android/DESIGN.md` antes de tocar código. Ahí están
los requisitos, las variantes de compilación y las reglas visuales.

## Antes de empezar

- **Para bugs**, abre un issue con la plantilla «Reporte de bug». Incluye la versión de Android
  y el fabricante del teléfono: el comportamiento del listener de notificaciones cambia mucho
  entre marcas (MIUI, One UI, etc.).
- **Para ideas o cambios de comportamiento**, abre un issue con la plantilla «Idea o mejora» antes
  de programar. Así evitamos trabajo que luego no encaje con la dirección del proyecto.
- **Para arreglos pequeños y evidentes** (typos, un cálculo mal, un test roto) manda el PR directo.

## Flujo de trabajo

1. Haz fork del repo y clónalo.
2. Crea una rama a partir de `main` con un nombre descriptivo: `fix/redondeo-tasa`, `feat/exportar-csv`.
3. Haz tus cambios con commits pequeños.
4. Comprueba que compila y que los tests pasan (ver abajo).
5. Abre el pull request contra `main` y explica **qué cambia y por qué**. Si cierra un issue, menciónalo (`Cierra #12`).

Los PRs se revisan en el orden en que llegan. Es normal recibir comentarios y pedir ajustes.

## Compilar y probar

### App Android

Requisitos: Android Studio reciente con su JDK (JBR 21) y el SDK `android-37`. Las versiones
exactas están en `android/gradle/libs.versions.toml` y en `android/README.md`.

```bash
cd android
./gradlew :app:assembleDebug        # compila la variante debug
./gradlew :app:testDebugUnitTest    # tests unitarios (JVM, sin emulador)
```

`android/gradle.properties` fija `org.gradle.java.home` a la ruta del JBR en Windows. Si tu
instalación está en otro sitio, sobreescríbela en tu `~/.gradle/gradle.properties` en vez de
cambiar el archivo del repo.

Para probar en un teléfono: `adb install -r app/build/outputs/apk/debug/app-debug.apk`. La
variante `verif` existe solo para pruebas automatizadas por adb y usa datos aparte.

### PWA (`src/`)

```bash
npm install
npm run test:run   # Vitest
npm run build      # typecheck + build
```

## Reglas del proyecto

- **Idioma.** Código, comentarios, mensajes de commit, issues y PRs en **español**. Los nombres de
  clases, funciones y variables también van en español (`calcularSaldo`, `RespaldoViewModel`).
- **Commits.** Formato `tipo(ámbito): descripción` en minúsculas, por ejemplo
  `fix(tasas): la tasa BCV del fin de semana usa el viernes`. Tipos habituales: `feat`, `fix`,
  `refactor`, `test`, `docs`, `chore`.
- **Tests.** Las reglas de negocio (`domain/`) son código puro y llevan test. Si cambias un
  cálculo de dinero, tasas, saldos, deudas, grupos o calendario, actualiza o agrega el test
  correspondiente. No se piden tests de UI.
- **Diseño.** `android/DESIGN.md` es la única fuente visual. Usa los tokens del tema
  (`CuentasTema.colores`, `.tipografia`, `.formas`, `.espacios`) y los componentes de
  `ui/componentes/`. Nada de colores hexadecimales sueltos ni `dp` mágicos en pantallas.
- **Privacidad.** Es una app que maneja movimientos bancarios reales. Nunca subas comprobantes,
  notificaciones, respaldos ni capturas con datos tuyos o de terceros. Los fixtures de prueba
  son sintéticos (nombres inventados, teléfonos y cuentas con ceros) y deben seguir así.
- **Dependencias.** Antes de agregar una librería, explica en el PR qué problema resuelve y por
  qué no se puede hacer con lo que ya hay.

## Qué pasa después

El mantenedor revisa el PR, puede pedir cambios y lo mezcla cuando esté listo. Si un PR queda sin
respuesta un tiempo, un comentario amable recordándolo es bienvenido.
