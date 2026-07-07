# Handoff: escribir el plan de implementación de "Cuentas Claras"

**Para:** el agente que escribirá el plan de implementación.
**Fecha:** 2026-07-07 · **Autor:** agente de brainstorming/diseño (sesión con Daniel).

## Tu misión

Escribir el plan de implementación detallado de la app descrita en la spec aprobada:
`docs/superpowers/specs/2026-07-07-finanzas-personales-design.md`

**Invoca la skill `superpowers:writing-plans`** y síguela. La spec es la fuente de verdad del *qué*; tu plan define el *cómo* y el orden. No reabras decisiones de diseño (listadas abajo): el usuario ya las aprobó explícitamente.

Guarda el plan en `docs/superpowers/plans/` y commitéalo. Documentación y textos de UI en **español** (el usuario es venezolano y se comunica en español). Mensajes de commit en español, terminando con `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`.

## Qué es la app (una línea)

PWA instalable (Android + Windows), offline-first con datos 100% locales, para finanzas personales bi-monetarias (Bs + USD): cuentas BNC/Zinli/Binance/efectivo, registro de gastos en 3 toques, importadores de comprobantes reales, tasas BCV y Binance USDT, dashboard consolidado y respaldo JSON a Drive vía selector de archivos.

## Estado del repo

- Rama `main`, sin remote. Solo existen `.gitignore`, la spec y este handoff. **No hay código aún**: el plan parte de un scaffold desde cero.
- El `.gitignore` excluye los comprobantes personales de la raíz (contienen cédula, número de cuenta y dirección). **Nunca deben commitearse**, y ningún dato personal de ellos debe copiarse a docs, fixtures ni código. Las fixtures de tests se crean **anonimizadas** (misma estructura, datos inventados).

## Decisiones cerradas (no reabrir)

1. PWA con React + TypeScript + Vite, Dexie (IndexedDB), Recharts, pdf.js, vite-plugin-pwa, Vitest.
2. Importador BNC usa **solo el formato TXT tabulado** (los formatos XLS, TXT ancho fijo y PDF fueron evaluados y descartados; ver spec §4.5).
3. Respaldo por **selector de archivos** (Drive en Android vía selector del sistema; File System Access API en Windows). Sin Drive API en v1.
4. Tasas: fetch automático de **BCV y Binance USDT/VES** con override manual e histórico. Fuente activa elegible por el usuario.
5. Efectivo físico **solo en USD** (dos cuentas: disposición y ahorro). Binance es cuenta de solo-saldo (ajustes manuales).
6. Widget nativo de Android queda para v2 (documentado en spec §7); la v1 incluye `shortcuts` del manifest.
7. Montos como **enteros en céntimos**, nunca float. Formato visual venezolano (`Bs 13.517,75`, `$84.20`).

## Archivos de muestra en la raíz del proyecto (léelos, están gitignored)

- `TXTBNC1.txt` — **el formato a implementar.** Tab-separated con cabeceras: `Fecha, Hora, Referencia, Cod. Transacción, Tipo Transacción, Tip. Operación, Descripción, Debe, Haber, Saldo, Referencia 2`. Primera línea: metadatos de cuenta con `@` como separador. Números con coma decimal y sin separador de miles; `Debe` negativo, `Haber` positivo; `Hora` con milisegundos (`15:08:30.638`). Trae solo los últimos 25 movimientos, en orden descendente. Detalles clave: los pagos móviles generan **dos filas con la misma `Referencia`** (cargo + comisión); el dedupe propuesto en la spec es `referencia + hora + monto`; el encadenado de la columna `Saldo` permite detectar huecos entre imports.
- `Resumen_de_movimientos_Junio_2026_Zinli.pdf` — resumen mensual Zinli (USD). Texto extraíble con pdf.js. Cabecera con `Saldo inicial del mes` y `Saldo final del mes` (usar para validar el import). Movimientos con patrón `dd/mm/yyyy + motivo (Pagaste/Recibiste) + descripción (puede partirse en varias líneas) + referencia (dígitos largos) + monto ($ con signo, formato US)`. Ojo: el PDF usa ligaduras («ﬁ») y mezcla líneas legales al final — el parser debe acotar la sección de movimientos.
- `PDFBNC.pdf`, `ExcelBNC.xls`, `TXTBNC2.txt` — formatos BNC descartados; útiles solo como referencia. (El `.xls` además está levemente malformado.)

## Riesgos técnicos abiertos que el plan debe cubrir

1. **CORS de las APIs de tasas** (spec §4.4): el plan debe incluir un spike temprano probando desde navegador los candidatos gratuitos — `pydolarve.org` (BCV + Binance en una llamada), `ve.dolarapi.com`, ExchangeMonitor. Diseñar `RateProvider` como interfaz con cadena de fallbacks y siempre el modo manual como último recurso.
2. **pdf.js en Vite**: configuración del worker (import de `pdfjs-dist` con `?url` o similar) — verificar que funcione offline una vez precacheado.
3. **File System Access API** solo existe en Chromium; en Android el flujo es descarga/share-sheet e `<input type="file">`. Prever ambos caminos.
4. **Hosting**: la instalación en Android requiere HTTPS (Cloudflare Pages / GitHub Pages / Vercel). El plan debe incluir la fase de deploy y prueba de instalación real en el teléfono del usuario.

## Entorno de desarrollo

Windows 11, working dir `C:\Users\danie\Documents\code\Proyects\AppNueva`. Shell principal PowerShell 5.1 (**no soporta `&&`**; usar `;` o Bash). Disponibles: Node v23.3.0, Bun 1.3.14, Python 3.12 (`python` en PATH), git configurado.

## Expectativas de proceso para la implementación (que el plan las incorpore)

- **TDD** (skill `superpowers:test-driven-development`) para parsers (BNC TXT, Zinli PDF), motor de reglas, dedupe, encadenado de saldos, conversión de monedas y export/import JSON.
- Fases pequeñas y verificables, cada una con criterio de éxito observable y tests en verde antes de avanzar.
- Orden sugerido (ajústalo si tienes mejor criterio): scaffold + modelo de datos → registro rápido + transferencias/ajustes → dashboard → tasas (con el spike CORS al inicio) → importador BNC → importador Zinli → respaldo → PWA/manifest/shortcuts → deploy e instalación real.
- La prioridad UX #1 es el registro manual en ≤ 3 toques; ninguna fase debe degradarla.

## Criterio de éxito global

Daniel instala la app en su Android y su PC, registra un gasto en efectivo en menos de 5 segundos, importa su TXT de BNC y su PDF de Zinli sin duplicados, ve su patrimonio consolidado en USD con separación disposición/ahorro, y guarda un respaldo JSON en su Drive.
