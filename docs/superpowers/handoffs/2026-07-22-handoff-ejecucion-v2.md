# Handoff: EJECUTAR el plan de implementación de «Cuentas Claras» v2

**Para:** el agente orquestador de la ejecución v2 (contexto fresco).
**Fecha:** 2026-07-22 · **Autor:** orquestador que escribió el plan v2 (sesión de planificación, Daniel pidió relevo).

## Tu misión

Ejecutar COMPLETO el plan **`docs/superpowers/plans/2026-07-22-cuentas-claras-v2-implementacion.md`** (16 tareas / 6 fases) usando la skill **`superpowers:subagent-driven-development`**: un subagente implementador fresco por tarea + revisión en dos etapas (cumplimiento de spec y calidad de código) entre tareas. El plan tiene código verbatim, pasos TDD RED→GREEN, verificaciones visuales bloqueantes y comandos de commit — los implementadores deben seguirlo literal y declarar cualquier desviación para que el revisor la valide.

Daniel aprobó el spec y el plan; ya eligió ejecución subagent-driven. No re-preguntes nada de eso.

## Jerarquía de fuentes ante dudas

1. Plan v2 (`docs/superpowers/plans/2026-07-22-cuentas-claras-v2-implementacion.md`) manda en el *cómo*. Léelo COMPLETO antes de despachar nada.
2. Spec v2 (`docs/superpowers/specs/2026-07-22-cuentas-claras-v2-design.md`) manda en el *qué*; sus 7 «Decisiones cerradas» de Daniel NO se reabren (en especial la 7: cero retrocompatibilidad de datos).
3. Spec v1 (`docs/superpowers/specs/2026-07-07-finanzas-personales-design.md`) y `.superpowers/sdd/restricciones-globales.md` siguen vigentes en todo lo no contradicho.
4. El ledger **`.superpowers/sdd/progress.md`** (gitignored, local) es la historia completa (v1 entera + cierre + notas). Léelo antes de arrancar; abre en él la sección «v2» y registra ahí cada tarea con el formato `Task N: complete (commits <base7>..<head7>, review clean)` más hallazgos/desviaciones/minors triados.
5. Los comprobantes reales (gitignored, raíz): `TXTBNC1.txt`, `PDFBNC.pdf`, `Resumen_de_movimientos_*_Zinli.pdf` y **`Screenshot_2026-07-22-…_bnc….jpg`** (captura con las notificaciones reales del BNC) mandan en formatos. **PRIVACIDAD ESTRICTA: jamás commitear ni copiar montos/referencias/teléfonos/tarjetas reales a fixtures, docs, código, mensajes de commit ni al ledger.**

## Estado del repo al escribir esto

- **HEAD = `b7f7260`** (`main`, árbol limpio salvo la captura gitignored). Suite **102/102** verde, build limpio (warning consultivo de chunk ~742 kB aceptado).
- **v1 SELLADA y en producción** con los 8 fixes del fix-wave: https://cuentas-claras-r2f.pages.dev (Cloudflare Pages, proyecto `cuentas-claras`, wrangler autenticado en esta máquina). Remote de respaldo: repo PRIVADO https://github.com/DanCas03/cuentas-claras (`origin/main`).
- **Prerrequisitos del plan: TODOS resueltos** (sección «Prerrequisitos» del plan). Puedes despachar la Tarea 1 de inmediato.
- La captura de notificaciones BNC ya fue entregada por Daniel → la Fase 4 NO está bloqueada; la Tarea 13 tiene fixtures anonimizadas y regex concretos, más una verificación local contra la captura real (efímera, nada al repo).

## Directivas vigentes de Daniel (NO reabrir)

1. **Autonomía**: decide con el default razonable y documenta en el ledger; pregúntale SOLO bloqueos genuinos. Sus intervenciones previstas: validación final en sus dispositivos (Tarea 16) y cualquier correción que él reporte.
2. **Verificación de UI**: estándar = Playwright headless (`npx -y -p playwright node script.mjs`, chromium ya instalado; receta en el plan). Capturas a `.superpowers/sdd/capturas/task-N/`; dev server propio por implementador y **matarlo al final** (hubo huérfanos en 5173/5174). Recharts se congela en headless → Clock API (receta en `.superpowers/sdd/task-12-report.md`). La extensión claude-in-chrome **vuelve a estar operativa** como complemento puntual, con una condición dura: debe abrir **Google Chrome** (verifica con `list_connected_browsers`/`select_browser`) — el navegador predeterminado del sistema NO tiene la extensión. Si falla, vuelve al headless sin insistir.
3. Todo en español (UI, docs, commits). Footer EXACTO de commit: `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`. No commitear `.claude/` ni `.superpowers/` (gitignorados) ni `Screenshot_*.jpg`.
4. Entorno: Windows 11; el tool Bash (Git Bash) es el caballo de batalla. `npx vitest run` antes de cada commit; `npm run build` al cerrar cada fase.

## Contexto técnico que los briefs deben trasladar (fácil de omitir)

- Stack real: vite 8 + rolldown, vitest 4 (entorno `node`, fake-indexeddb), dexie 4.4.4 (**`d.transaction` con ARRAY si son >5 tablas**), TypeScript 6, Node 23 (EBADENGINE inofensivo), pdfjs-dist 6.
- Patrones que el revisor exige: guard `guardando` en todo guardado async; ningún `useLiveQuery` tras un return condicional; z contenido < nav(30) < modal(40) < toast(50); estilos solo por tokens de `estilos.css`; montos enteros en céntimos; formato `Bs 13.517,75` / `$84.20`.
- **No tocar** el contrato anti-duplicación del import (filtro UI `incluir && !duplicate` + backstop de `commitImport`) ni los demás fixes del fix-wave (lista en la restricción global 5 del plan).
- **`txsEfectivas()` SOLO alimenta agregaciones; los saldos usan txs crudas** — confundirlo corrompe los saldos visibles. Los ids `virtual-…` jamás se persisten.
- Seeds v2 acoplados a tests: al añadir las 3 categorías, los asserts de conteo pasan 15→18 en `db.test.ts` y `backup.test.ts` **en la misma tarea** (el plan ya lo secuencia: Tareas 2-3).
- El helper `bash .superpowers/sdd/tarea-brief.sh <RUTA-PLAN> N` extrae la tarea N del plan (los planes usan «Tarea», el script de la skill busca «Task»).
- Decisiones de diseño ya tomadas y documentadas en el plan (no re-decidir): residuo «Vuelto» por moneda; seeds con check-por-id; grupo que baja de 2 miembros se disuelve; fix del accept BNC = añadir `application/octet-stream`.

## Modelo y esfuerzo por subagente (directiva de Daniel: no escatimar, pero que quepa en una sesión)

Presupuesto de referencia: ~16 implementadores + ~18-20 revisores/gates en una sesión larga. La palanca de ahorro es el modelo del implementador (el plan ya trae el código); la de calidad es el revisor. Especifica el modelo con el parámetro `model` del Agent tool; el esfuerzo, indícalo en el prompt del brief («razona con esfuerzo alto/medio en X») y aplícalo tú con `/effort` cuando revises inline.

| Rol / Tareas | Modelo | Esfuerzo | Por qué |
|---|---|---|---|
| Implementador T1 (fix accept + debugging) | sonnet | medium | Fix de una línea con verificación guiada. |
| Implementadores T2, T3, T4, 7, 9, 10 (núcleo TDD) | sonnet | medium | Código y tests verbatim en el plan; trabajo mecánico-cuidadoso. |
| Implementadores T6, 8, 11, 14 (UI acotada / integración) | sonnet | medium | Componentes completos en el plan + verificación headless. |
| Implementador T5 (DeudasPage completa) | sonnet | **high** | Página nueva grande con editor modal y flujos de cierre. |
| Implementador T12 (Movimientos: selección + grupos) | **opus** | medium | La tarea más compleja del plan (pulsación larga, modelo de vista mixto, 6 flujos a verificar). |
| Implementador T13 (parser + verificación real) | sonnet | medium | Regex y aserciones ya definidos; la verificación real exige disciplina de privacidad — recálcaselo en el brief. |
| Implementador T15 (guía MacroDroid) | haiku | medium | Documento verbatim en el plan. |
| Implementador T16 (deploy + smoke) | sonnet | medium | Comandos dados; el juicio lo pones tú y Daniel. |
| **Revisor de spec-compliance** (por tarea) | opus | medium | Coteja plan/spec vs diff; criterio > volumen. |
| **Revisor de código** (por tarea) | opus | medium | Vigila los patrones obligatorios y el blast radius. |
| Revisión de gates de fase (6) y pase final pre-deploy | opus | **high** | Barato en cantidad, caro en criterio: es donde se cazan los cross-cutting. |
| Doble puerta visual (inspección de capturas de T5/6/8/11/12/14) | tú mismo (orquestador) | — | Inspecciona las capturas contra el checklist del plan; no delegues tu rol de controlador. |

Reglas de gasto: si un revisor opus devuelve «clean» dos tareas seguidas de la misma fase en tareas mecánicas (T2-T4), puedes fusionar los dos revisores en uno con ambos mandatos para las siguientes de esa fase. No bajes NUNCA el modelo del revisor por debajo de opus. Si una tarea rebota 2 veces en revisión, sube el implementador un nivel (sonnet→opus) en el reintento.

## Proceso

1. Lee plan completo + este handoff + ledger. Abre la sección «v2» del ledger.
2. Por tarea: brief con `tarea-brief.sh` + contexto técnico de arriba → implementador → dos revisiones → arreglos si aplica → entrada en el ledger → siguiente. Commits los hace el implementador según el plan.
3. Gates de fase: corre tú `npx vitest run` y `npm run build` (no te fíes del reporte del implementador) y despacha la revisión de gate.
4. Fase 5 / Tarea 16: deploy, smoke de producción y **validación de Daniel en sus dispositivos (bloqueante, es el criterio de éxito)** — checklist de 7 puntos en el plan. Al aprobar: `superpowers:finishing-a-development-branch` + `git push origin main`.
5. Si la sesión se agota a medias: deja el ledger impecable y escribe un handoff de continuación como este (patrón ya usado dos veces en el proyecto).

## Criterio de éxito (del plan, propuesto por el spec)

> Daniel registra una deuda con su amigo asignada a Efectivo y la ve descontada en «Total neto»; la marca pagada y el gasto aparece en Efectivo; desliza el patrimonio entre sus 3 vistas; agrupa los $5 que le pasaron con la compra de la leche y su mes solo refleja el vuelto; toca la notificación capturada del BNC y confirma el pago móvil en 1 toque; y selecciona su TXT de BNC en Android sin pelear con el selector.
