# Handoff: escribir el plan de implementación de "Cuentas Claras" v2

**Para:** el próximo agente orquestador (contexto fresco).
**Fecha:** 2026-07-22 · **Autor:** orquestador de las Tareas 19-26 de la v1 + cierre + brainstorming v2 (Daniel pidió relevo para el plan).

## Tu misión

Escribir el plan de implementación de la v2 **invocando la skill `superpowers:writing-plans`** a partir del spec aprobado en brainstorming: **`docs/superpowers/specs/2026-07-22-cuentas-claras-v2-design.md`** (commit `39a3312`). Léelo COMPLETO primero: contiene las 7 decisiones cerradas de Daniel, las 4 funcionalidades, el bug del selector TXT y el alcance de testing. El plan se ejecutará después (probablemente otra sesión) con `superpowers:subagent-driven-development`, así que escríbelo con el mismo nivel de detalle que el plan v1 (`docs/superpowers/plans/2026-07-07-cuentas-claras-implementacion.md` — úsalo como referencia de formato: código verbatim por tarea, pasos de verificación bloqueantes, comandos de commit con footer).

Gate previo: Daniel dejó pendiente su revisión formal del spec. Si en la conversación no consta su aprobación explícita, confírmala antes de empezar a escribir (una sola pregunta, sin re-litigar lo decidido).

## Jerarquía de fuentes ante dudas

1. Spec v2 (`2026-07-22-cuentas-claras-v2-design.md`) manda en el *qué* de la v2.
2. Spec v1 (`docs/superpowers/specs/2026-07-07-finanzas-personales-design.md`) y las restricciones globales (`.superpowers/sdd/restricciones-globales.md`) siguen vigentes en todo lo no contradicho por el spec v2.
3. El ledger `.superpowers/sdd/progress.md` (gitignored, local) es la historia completa: tareas 1-26, hallazgos, decisiones, minors triados. Léelo antes de planificar.
4. Los comprobantes reales (gitignored, raíz: `TXTBNC1.txt`, `PDFBNC.pdf`, `Resumen_de_movimientos_*_Zinli.pdf`) mandan en formatos. **Privacidad estricta**: nunca commitear ni copiar datos reales a fixtures/docs/código.

## Estado del repo al escribir esto (verifica el ledger por si avanzó)

- **v1 COMPLETA y en producción**: 26/26 tareas, criterio de éxito validado por Daniel en su Android y su PC (2026-07-22). URL real: **https://cuentas-claras-r2f.pages.dev** (Cloudflare Pages, proyecto `cuentas-claras`, wrangler ya autenticado en esta máquina con la cuenta de Daniel).
- **HEAD = `39a3312`** (spec v2). Suite **104/104** verde, build limpio (warning consultivo de chunk ~742 kB aceptado y documentado).
- **Cierre v1 en vuelo**: la revisión broad final (informe: `.superpowers/sdd/revision-final-informe.md`) dio 0 Critical / 7 Important + 1 triaje; la ola de fixes los cerró 8/8 (commits `8912169..d750f85`, reporte: `.superpowers/sdd/fix-wave-final-report.md`); la **re-revisión estaba corriendo** al escribir esto. Pendiente tras su aprobación: posible limpieza de «compat muerta» (ver abajo), **redeploy a producción** (`npm run build` + `npx wrangler pages deploy dist --project-name cuentas-claras`) y `superpowers:finishing-a-development-branch`. **Consulta el ledger**: si ese cierre no consta como hecho, termínalo ANTES de ejecutar el plan v2 (escribir el plan puede hacerse en paralelo).

## Decisiones vigentes de Daniel (NO reabrir, NO volver a preguntar)

1. Las 7 decisiones del spec v2 (§«Decisiones cerradas») — en especial la **7: cero retrocompatibilidad**: Daniel re-ingresa/re-importa sus datos tras actualizar. El plan NO debe incluir migración de contenido ni compat con respaldos v1. La re-revisión del fix-wave inventarió «compat muerta» (shims de clave legacy en el código de los fixes): si el ledger dice que no se limpió, añade una tarea temprana de limpieza al plan.
2. **«Omite las verificaciones con chrome»** — la extensión claude-in-chrome falla en su PC. TODA verificación de UI: Playwright headless (chromium YA instalado; `npx -y -p playwright node script.mjs`), capturas a `.superpowers/sdd/capturas/task-N/`, dev server propio por implementador y matarlo al final (hubo huérfanos en 5173/5174).
3. **Autonomía**: decide con el default razonable y documenta en el ledger; pregunta a Daniel SOLO bloqueos genuinos (aprobación del spec, los textos de notificaciones, validación en sus dispositivos).
4. Todo en español (UI, docs, commits); footer exacto de commit: `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`; no commitear `.claude/` ni `.superpowers/` (gitignorados).

## Contexto técnico que el plan DEBE incorporar (fácil de omitir)

- **Stack real** (difiere de lo que asumirías): vite 8 + rolldown, vitest 4 (entorno `node`, fake-indexeddb, setup en `src/test/setup.ts`), dexie 4.4.4 (**`d.transaction` con ARRAY de tablas si son >5** — el variádico topa en 5), pdfjs-dist 6 (`doc.loadingTask.destroy()`, no `doc.destroy()`), vite-plugin-pwa 1.3.0, TypeScript 6, Node 23 (aviso EBADENGINE inofensivo). Recharts se congela en Chromium headless → Clock API de Playwright (receta en `.superpowers/sdd/task-12-report.md`).
- **Patrones obligatorios del código** (el revisor los exige): guard `guardando` en TODO guardado async (check → validación → `setGuardando(true)` → await en try con éxito-solo → catch `avisar` → finally reset, botones `disabled`); ningún `useLiveQuery` tras un return condicional; jerarquía z contenido < nav(30) < modal(40) < toast(50); estilos SOLO por tokens/clases de `estilos.css`; montos SIEMPRE enteros en céntimos; formato visual venezolano (`Bs 13.517,75`, `$84.20`).
- **Contrato anti-duplicación (actualizado por el fix-wave)**: la puerta sigue siendo el filtro de la UI (`incluir && !duplicate`), pero `commitImport` ahora tiene un **backstop** que revalida importKeys dentro de la transacción y aborta. No revertir ni debilitar ninguno de los dos.
- **El fix-wave también cambió** (no revertir): `bncImportKey` ahora incluye la fecha; «Vigente hoy» en Tasas usa el histórico completo; el banner «Crear ajuste» post-import tiene guard contra meses viejos y doble-tap; `EditorMovimiento` valida la fecha; la regla default de Zinli re-matchea.
- **Seeds y sus tests acoplados**: ids fijos (`acc-bnc`, `acc-zinli`, `cat-transferencia-recibida`…); hoy 5 cuentas / 15 categorías. La v2 añade categorías seed («Deudas» gasto, «Vuelto» ingreso y gasto) → **los tests de backup que asertan `accounts.count()===5` y `categories.count()===15` DEBEN actualizarse en la misma tarea que toque los seeds** (detalle que rompe la suite si se omite).
- **Siembra de seeds nuevos**: `seedIfEmpty` solo puebla con DB vacía. Con la decisión 7 (Daniel resetea) basta en la práctica, pero el plan debe decidir explícitamente el mecanismo (p. ej. seed check-por-id además del empty-check, o documentar «requiere DB fresca») y declararlo — no dejarlo al azar del implementador.
- **Dexie v2**: `db.version(2)` aditiva (tablas `debts`, `txGroups`; índice `groupId` en transactions; campo opcional `enDisposicion` en accounts — los campos opcionales no necesitan índice salvo que se consulte por ellos). `BackupFile` a `schemaVersion: 2`, `validateBackup` exige exactamente 2.
- **Bug del selector TXT (spec §5)**: hipótesis principal = atributo `accept` de `ImportBnc.tsx` vs MIME real del TXT en Android (`application/octet-stream` típico). Planifícalo como tarea temprana con `superpowers:systematic-debugging` (confirmar accept actual → ampliar/eliminar → verificar en desktop headless que la carga sigue → validación de Daniel en su Android como paso bloqueante diferido). Revisar el input de Zinli por el mismo patrón.
- **Prerrequisito de la captura (spec §4)**: 2-3 textos reales ANONIMIZADOS de notificaciones del BNC (pago móvil recibido y compra con tarjeta) que Daniel debe entregar. Ordena el plan para que esa pieza vaya al final y pídelos al arrancar (si no llegan, la tarea queda bloqueada sin frenar el resto).
- **Métricas y agregaciones (spec §3)**: los puntos de cálculo a tocar viven en el dominio y en DashboardPage/GraficasMes/comparativa/presupuestos. El residuo «Vuelto» es VIRTUAL (no crea transacciones); trázalo como funciones puras testeables (TDD) antes de tocar la UI.
- **Slider de patrimonio (spec §2)**: CSS `scroll-snap`, sin librerías. El default de `enDisposicion` al crear cuentas es `!ahorro`.

## Proceso recomendado para el plan

- Formato del plan v1: tareas numeradas con archivos exactos, código verbatim, interfaces consumidas/producidas, pasos de verificación bloqueantes y comando de commit. Fases con gate (`npx vitest run` + `npm run build`).
- Orden sugerido por dependencias (ajústalo si ves mejor): Fase 0 (bug TXT + Dexie v2 + seeds + tests de backup actualizados) → Deudas (lógica TDD → UI) → Slider patrimonio (depende de deudas) → Transaccionales (lógica TDD → UI Movs) → Captura (parser TDD con fixtures de Daniel → ruta + prefill → guía MacroDroid) → Respaldo v2 + redeploy + validación de Daniel en dispositivos.
- El ledger sigue siendo `.superpowers/sdd/progress.md`: abre una sección «v2» al arrancar la ejecución. El helper `bash .superpowers/sdd/tarea-brief.sh PLAN N` existe porque el script de la skill busca "Task" y los planes usan "Tarea".
- Entorno: Windows 11; el tool Bash (Git Bash) es el caballo de batalla. Commits en español con el footer exacto.

## Criterio de éxito de la v2 (propuesto — el plan debe abrirlo con esto)

> Daniel registra una deuda con su amigo asignada a Efectivo y la ve descontada en la vista «Total neto»; la marca pagada y el gasto aparece en Efectivo; desliza el patrimonio entre sus 3 vistas; agrupa los $5 que le pasaron con la compra de la leche y su mes solo refleja el vuelto; toca la notificación capturada del BNC y confirma el pago móvil en 1 toque; y selecciona su TXT de BNC en Android sin pelear con el selector.
