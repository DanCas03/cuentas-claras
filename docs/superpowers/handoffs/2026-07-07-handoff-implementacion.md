# Handoff: implementar "Cuentas Claras"

**Para:** el agente que implementará la app.
**Fecha:** 2026-07-07 · **Autor:** agente de planificación (el plan ya está escrito y commiteado).

## Tu misión

Ejecutar de principio a fin el plan de implementación:
`docs/superpowers/plans/2026-07-07-cuentas-claras-implementacion.md` — 26 tareas en 8 fases, con pasos, código completo, tests y commits ya definidos.

**Invoca la skill `superpowers:subagent-driven-development`** (recomendada: un subagente fresco por tarea + revisión entre tareas) o, si prefieres ejecutar en sesión, `superpowers:executing-plans`. Síguela.

Jerarquía ante dudas: la spec (`docs/superpowers/specs/2026-07-07-finanzas-personales-design.md`) manda sobre el plan en el *qué*; los comprobantes reales (gitignored, en la raíz) mandan sobre ambos en los *formatos* — si la realidad difiere, ajusta código **y** fixture (con datos inventados). No reabras decisiones de diseño: Daniel ya las aprobó (listadas en "Restricciones globales" del plan, punto 9).

## Reglas no negociables

1. **Privacidad**: `TXTBNC1.txt`, `TXTBNC2.txt`, `PDFBNC.pdf`, `ExcelBNC.xls` y `Resumen_de_movimientos_*_Zinli.pdf` contienen cédula, cuentas, teléfonos y dirección reales. Están gitignored: **nunca los commitees ni copies ningún dato suyo** a fixtures, docs o código. Las fixtures del plan ya vienen anonimizadas — úsalas tal cual.
2. **Tests SOLO en el núcleo crítico** (dinero, tasas, saldos, stats, reglas, parsers, dedupe, commit transaccional, respaldo), con TDD (skill `superpowers:test-driven-development`). **La UI no lleva tests automáticos**: cada tarea de UI trae pasos de verificación manual bloqueantes — ejecútalos de verdad, no los marques sin mirar. Suite (`npx vitest run`) verde antes de CADA commit; `npm run build` limpio al cerrar cada fase.
3. **`superpowers:verification-before-completion`** antes de declarar cualquier tarea o fase terminada: ejecuta los comandos de verificación del plan y confirma la salida esperada.
4. Commits en español, terminando con:
   `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`
5. UI y docs en **español**. Montos siempre en céntimos enteros (`Bs 13.517,75`, `$84.20`). Ninguna tarea puede degradar el registro rápido (FAB → dígitos → categoría).
6. **El diseño visual es pilar del producto**: tema oscuro premium con sistema de tokens definido en la Tarea 7 — todo color/espaciado/efecto sale de `estilos.css`, nada de valores inventados ni librerías de estilos. La Tarea 24 cierra con un pase de pulido visual por todas las pantallas; las verificaciones visuales de cada tarea de UI son bloqueantes.

## Entorno

Windows 11, working dir `C:\Users\danie\Documents\code\Proyects\AppNueva`. Shell principal PowerShell 5.1 (**no soporta `&&`** en consola; usa `;` o comandos separados — dentro de scripts npm sí funciona). Node v23.3.0, npm como gestor. Rama `main`, **sin remote** (no hay push; el deploy va por wrangler).

## Puntos que requieren a Daniel (pausa y pídeselo)

- **Tarea 25 (deploy)**: `npx wrangler login` es interactivo (navegador + cuenta gratuita Cloudflare). No continúes hasta que confirme.
- **Tarea 26 (validación final)**: checklist en su Android y su PC — tú guías, él ejecuta en el teléfono.
- Las verificaciones manuales de las Tareas 2 (spike CORS en navegador), 16/18 (TXT real), 19/20 (PDF real) y 24 (offline) necesitan navegador y los comprobantes reales locales.

## Avisos que te ahorrarán tropiezos

- El plan ya refleja los **formatos reales verificados**: BNC TXT es UTF-8 sin BOM con CRLF, filas descendentes, par cargo+comisión con misma referencia, artefactos float (`87,6999999999998`) y pseudo-fila "Saldo Inicial" que se ignora; el PDF Zinli usa ligaduras «ﬁ» (por eso se normaliza con NFKC) y trae saldos inicial/final que validan el cuadre.
- En `normalizePdfLine` (Tarea 20) el regex de NBSP lleva un **U+00A0 literal** — el comentario del plan explica la alternativa con escape ASCII si tu copia lo convierte.
- La **Tarea 13 depende de las fixtures que capture la Tarea 2** (respuestas JSON reales de los proveedores de tasas). Si un proveedor no pasó el spike, elimina su parser, fixture y tests, y ajusta la cadena.
- El scaffold (Tarea 1) crea `package.json` a mano — no uses `npm create vite` (el directorio no está vacío y el asistente interactivo se traba).
- La Tarea 7 define el sistema de diseño (tokens + clases en `estilos.css`, tema oscuro premium, Manrope). Todas las tareas de UI se apoyan en él: si necesitas un estilo nuevo, añádelo como token/clase ahí, nunca como valores sueltos inline.
- No commitees `.claude/` (configuración local, ya aparece como untracked).

## Estado del repo al recibir esto

Commits: spec de diseño, handoff de planificación, plan de implementación y este handoff. **No hay código todavía** — la Tarea 1 parte de cero. El plan incluye una tabla "Cobertura spec → tareas" para que verifiques completitud al final.

## Cierre

Cuando la Tarea 26 pase completa, invoca `superpowers:finishing-a-development-branch`.

## Criterio de éxito global

> Daniel instala la app en su Android y su PC, registra un gasto en efectivo en menos de 5 segundos, importa su TXT de BNC y su PDF de Zinli sin duplicados, ve su patrimonio consolidado en USD con separación disposición/ahorro, y guarda un respaldo JSON en su Drive.
