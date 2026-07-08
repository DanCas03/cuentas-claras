# Handoff: continuar "Cuentas Claras" desde la Tarea 19 (Fase 6)

**Para:** el próximo agente orquestador.
**Fecha:** 2026-07-07 · **Autor:** orquestador de las Tareas 1-18 (contexto agotado; Daniel pidió relevo).

## Tu misión

Continuar la ejecución del plan `docs/superpowers/plans/2026-07-07-cuentas-claras-implementacion.md` desde la **Tarea 19** hasta la **26**, más la revisión final de rama. **Invoca la skill `superpowers:subagent-driven-development`** y síguela, igual que hasta ahora: un implementador fresco por tarea, paquete de diff, revisor por tarea, cierre en ledger, gate de fase (`npx vitest run` + `npm run build`) al cerrar cada fase.

Jerarquía ante dudas: spec (`docs/superpowers/specs/2026-07-07-finanzas-personales-design.md`) manda sobre plan en el *qué*; los comprobantes reales (gitignored, raíz) mandan en *formatos*. El handoff original (`2026-07-07-handoff-implementacion.md`) sigue vigente en todo lo no dicho aquí.

## Estado exacto al recibir esto

- **Tareas 1-18 completas y revisadas** (Fases 0-5 cerradas con gates). HEAD = `b5d0231`. Suite **86/86** verde, build limpio.
- **Ledger (fuente de verdad del progreso): `.superpowers/sdd/progress.md`** (gitignored, local). Contiene: rango de commits por tarea, hallazgos Minor pendientes para el triaje final, y notas para tareas futuras. Léelo COMPLETO antes de despachar nada; tras cada tarea aprobada, añade su línea.
- Briefs por tarea ya extraídos en `.superpowers/sdd/task-N-brief.md` (regenerables: `bash .superpowers/sdd/tarea-brief.sh PLAN N` — el script de la skill busca "Task" en inglés y el plan usa "Tarea", por eso existe el helper). Restricciones globales verbatim en `.superpowers/sdd/restricciones-globales.md`.
- El plan en `docs/` ya incluye TODAS las enmiendas (pre-flight `f323c08` + realidad del spike en Tarea 13 `30d01d4`). Los briefs derivan del plan enmendado. No re-enmendar.
- Reportes de implementadores en `.superpowers/sdd/task-N-report.md`; capturas en `.superpowers/sdd/capturas/task-N/`.

## Decisiones de Daniel ya tomadas (NO reabrir, NO volver a preguntar)

1. **"Omite las verificaciones con chrome"** — la extensión claude-in-chrome falla en su PC. Verificación de UI: Playwright headless (chromium YA instalado; `npx -y playwright@latest screenshot ...` o scripts con `npx -y -p playwright node script.mjs`).
2. **"Sigue con la implementación y hazme las preguntas que hacen falta"** — autonomía: decide con el default razonable y documenta en el ledger; pregúntale SOLO bloqueos genuinos (wrangler login T25, validación en sus dispositivos T26).
3. **Cuadre Zinli = oferta «Crear ajuste»** post-import (paridad BNC) — ya enmendado en la Tarea 21 del plan.
4. **Duplicación parcial deliberada** de vista previa BNC/Zinli — NO extraer componente compartido. `ImportBnc.tsx` (T18) es la referencia de patrón.

## Proceso por tarea (como se vino haciendo)

1. `TaskUpdate` a in_progress. Registra el BASE (`git rev-parse HEAD`).
2. Despacha implementador (**sonnet** por defecto; el plan trae código verbatim) con: 1 línea de contexto, ruta del brief + restricciones, interfaces de tareas previas relevantes, protocolo de verificación, y contrato de reporte (archivo `.superpowers/sdd/task-N-report.md`; mensaje final ≤15 líneas con Status/commits/resumen/concerns).
3. DONE → `bash ~/.claude/plugins/cache/claude-plugins-official/superpowers/6.1.1/skills/subagent-driven-development/scripts/review-package BASE HEAD .superpowers/sdd/review-task-N.diff` → revisor (**opus**) con brief + reporte + diff + riesgos nombrados concretos. Verifica tú el footer del commit (`git log -1 SHA --format=%B | tail -2`).
4. UI: doble puerta visual (implementador inspecciona sus PNG; tú re-inspeccionas las capturas clave con Read).
5. Hallazgos Important → fix subagent + re-review. Minors → ledger. Plan-mandated con default claro → decide tú (preferencia de autonomía de Daniel).
6. BLOCKED/muerte por error de API → `SendMessage` al MISMO agente con el estado real del árbol (`git status`) para reanudar sin repetir trabajo.

## Patrones obligatorios ya establecidos en el código

- Guard `guardando` en TODO guardado async: check → validación → `setGuardando(true)` → await en try (`avisar` + navegación solo en éxito) → catch `avisar((e as Error).message)` → finally reset. El brief puede no traerlo (enmienda posterior): intégralo sin cambiar nada más y decláralo.
- Estilos SOLO por tokens/clases de `estilos.css`. Jerarquía z: contenido (auto) < nav (30) < modal (40) < toast (50).
- Hooks: ningún `useLiveQuery` tras un return condicional.
- `commitImport` NO deduplica: la puerta anti-duplicación es SIEMPRE el filtro de la UI (`incluir && !duplicate`) — se replica en la T21.

## Protocolo de privacidad para comprobantes reales (T19/T20/T21)

`PDFBNC.pdf` y `Resumen_de_movimientos_*_Zinli.pdf` (raíz, gitignored) tienen datos personales reales. Verificación en DOS rondas: (1) sintética con fixture anonimizada — capturable en screenshots; (2) real — SIN capturas, solo veredictos/conteos programáticos al reporte, y limpiar la IndexedDB de prueba después. PROHIBIDO copiar datos reales a fixtures/código/docs/reportes. Los tests temporales que lean archivos reales se borran antes de commitear (confirmar con `git status`).

## Avisos concretos para las tareas restantes

- **T19 (pdf.js)**: está instalado `pdfjs-dist@^6` (el plan asumía ^5) — valida la API v6 contra el código del plan y ajusta si difiere (declarándolo). El import del worker `pdf.worker.min.mjs?url` con vite 8 está verificado (repro del pre-flight).
- **T20 (parser Zinli)**: el regex de NBSP lleva **U+00A0 literal** (el plan explica la alternativa escapada); normalización NFKC por ligaduras «ﬁ»; el PDF trae saldos inicial/final que validan el cuadre.
- **T21 (mapper+UI Zinli)**: incluye el cuadre post-import con `buildAdjustment` (enmienda por decisión de Daniel). Misma puerta anti-duplicación que T18.
- **T24 (PWA + pulido)**: `mjs` YA está en los globPatterns del plan (enmienda); la verificación exige el asset `.mjs` de ~1.2 MB en el precache (NO basta el chunk envoltorio `.js` de 0.06 kB). Chunk único de 706 kB (Recharts): warning consultivo, evalúa code-splitting. El pase de pulido debe triar los candidatos del ledger (p. ej. `.catch` de seedIfEmpty, `:active` de `.chip`, `eliminar()` sin guard). Recharts se congela en Chromium headless: usar Clock API de Playwright (documentado en `task-12-report.md`).
- **T25 (deploy)**: PAUSA — `npx wrangler login` es interactivo; sugiérele a Daniel teclear `! npx wrangler login` en el prompt. No continúes sin su confirmación.
- **T26 (validación final)**: PAUSA — checklist en su Android y su PC; tú guías, él ejecuta.
- **Cierre**: revisión broad de TODA la implementación (rango `2567d54..HEAD`; usa `scripts/review-package` y el template de `superpowers:requesting-code-review`, modelo más capaz disponible) + triaje de los Minors del ledger + `superpowers:finishing-a-development-branch` cuando la T26 pase. Verifica la tabla «Cobertura spec → tareas» del plan.

## Entorno (recordatorios que ahorraron tropiezos)

Windows 11, PowerShell 5.1 sin `&&` (el tool Bash/Git Bash funciona bien y fue el caballo de batalla). Node v23.3.0 (vitest avisa EBADENGINE — inofensivo). Los implementadores deben arrancar SU PROPIO dev server y matarlo al final (hubo huérfanos en 5173/5174: mátalos si reaparecen tras verificar con `Get-CimInstance` que son vite de este proyecto). Commits en español con footer exacto `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`. No commitear `.claude/` ni `.superpowers/` (ya gitignorados).

## Criterio de éxito global (sin cambios)

> Daniel instala la app en su Android y su PC, registra un gasto en efectivo en menos de 5 segundos, importa su TXT de BNC y su PDF de Zinli sin duplicados, ve su patrimonio consolidado en USD con separación disposición/ahorro, y guarda un respaldo JSON en su Drive.
