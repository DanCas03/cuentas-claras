# Handoff: código completo (T1–T31 y cierres en código); solo queda la cola del teléfono

**Para:** el orquestador de la próxima sesión, que empieza con contexto fresco.
**Fecha:** 2026-10-16 · **Repo:** `C:\Users\danie\Documents\code\Proyects\AppNueva` · **Rama:** `nativa-compose`, HEAD `19af82b` + este handoff. No se ha subido nada (`origin/nativa-compose` va por detrás).
**Método:** `superpowers:subagent-driven-development`. El ledger está en `.superpowers/sdd/ledger-ejecucion.md` (gitignored, solo en esta PC) y el workspace en `.superpowers/sdd/2026-10-15-cuentas-claras-nativa-compose-implementacion/`.
**Por qué se pausa:** el 2026-10-16 Daniel dijo que no tendría el teléfono cerca y pidió dejar para el final todo lo que lo necesite. Eso es exactamente lo único que falta.

## Estado

- **Implementadas, revisadas e integradas:** las tareas T1 a T31 (fases 1 a 6).
- **Cierres en código:** las fases 2, 3+4 y 5 pasaron un code-review de dos ejes (Standards y Spec) con su tanda de arreglos y su re-revisión. La fase 6 entró en la revisión final.
- **Revisión final de toda la rama** (`28310bb..37daf97`, 71 commits, modelo más capaz):
  - Resultado: 0 Critical y 3 Important (`hoy` estancado, lecturas atrasadas al guardar en Nuevo, respaldos de seguridad inaccesibles).
  - Se arreglaron en una tanda única de 26 puntos (`37daf97..19af82b`) con re-revisión: 26/26.
  - Quedan 3 menores aparcados con su ruling (al final del ledger).
- **Build verificado por el orquestador** en `19af82b`: `clean :app:testDebugUnitTest :app:assembleDebug :app:assembleVerif` → BUILD SUCCESSFUL, **58 tests** y 0 avisos.
- **Comprobaciones mecánicas del cierre** (T32, Paso 2), todas OK:
  - sin `kotlin.math.round`;
  - `domain/` sin `android*`;
  - sin Compose en `domain`, `data`, `importar`, `rates`, `backup` ni `capture`;
  - `txsEfectivas` solo en `InicioViewModel`;
  - el esquema `1.json` no cambia desde T5;
  - footers Fable 5.1 en 71 de 71;
  - privacidad y tests dentro de §9.
- **README:** `android/README.md` hecho (T32, Paso 3).
- **No quedan worktrees.** `git worktree list` muestra solo el árbol principal.

## La cola PENDIENTE TELÉFONO (en este orden)

Cada paso tiene su guía en el workspace, con los textos y `contentDescription` exactos.

**Reglas del teléfono** (no cambian):
- Variante `verif` para todo lo automático.
- Sobre `com.cuentasclaras.app` solo `adb install -r` del APK debug.
- **PROHIBIDO manejar selectores SAF por adb.**
- `adb-ui.sh` ya apunta a `verif`.

Un subagente verificador por bloque, con la guía como brief. Abre y revisa cada PNG.

1. **Verificación integrada de la fase 2** en `verif`, claro y oscuro: Inicio, Nuevo, Movimientos y Ajustes.
   - **Nuevo, ≤ 3 toques (geométrico):** `volcar` sin desplazar y comprobar que los `bounds` de las 4 filas de gasto, incluida «Suscripciones», caen dentro de la pantalla. Hazlo dos veces: sin eventos (layout T12: ~796 dp de ~808) y con chips de evento (T30: el grid baja 56 dp y la fila 4 queda ~44 dp por debajo; aceptado por el ruling T12-I1, pero hay que medirlo). Mide también con el banner de captura (T20).
   - Probar «tocar etiqueta → escribir» y el teclado del sistema abierto en Nota, Transferir y Ajustar (insets, E26).
   - Guías: `task-12/13/14/15/16-report.md` y `task-17-report.md` (íconos e insets).
2. **T17 Pasos 1-2:** capturas de todas las pantallas más `redesign-skill` contra `android/DESIGN.md`, también con `wm size 720x1520` (y `wm size reset` al terminar). Solo arreglos visuales, en un commit `style(ui): …`.
3. **Fase 3 (T19, T20, T21, T22):**
   - Guía del listener: «Ronda de arreglos 3 — guía final» de `task-19-report.md`, más el 10-bis de `fases3y4-report.md` (gracia de **12 s**), `task-20-report.md`, `task-21-report.md` y `fases3y4-report.md`. Antes, define `TXT`/`TXT3` en Git Bash. En el paso 9 (`pm revoke`), espera a «Listener conectado» antes de publicar.
   - T22 con Daniel, en este orden:
     - (a) **confirmar el paquete del BNC** (`pm list packages | grep -i bnc`; si no es `bnc.bncnet.mobile2`, preguntárselo);
     - (b) wizard de permisos (`mattpocock-skills:wizard`);
     - (c) **un pago móvil real registrado desde la notificación en 2 toques**;
     - (d) supervivencia: kill y 1 hora con la pantalla apagada.
4. **Fase 4 (T24/T25):**
   - Guía de `task-24-report.md`: solo «pegar texto», E2 y pegar dos veces (9 filas marcadas y 9 desmarcadas).
   - Después, la dedupe con captura (T24, Paso 5).
   - T25 con Daniel: importa su TXT del mes. Pregúntale **qué columna del TXT coincide con la «Ref:»** de la notificación (`Referencia` o `Referencia 2`), si cambian los ceros y si la Ref puede venir truncada.
   - Hoy el código compara con `Referencia` (`ParseBncTxt.kt` `f[2]`, `MapBnc.kt`). Comparar contra ambas columnas son unas 6 líneas más un caso en `MapBncTest`. `bncImportKey` y `pairRef` se quedan con `Referencia`.
5. **Fase 5 (T26, T27, T28):** guías de `task-26-report.md`, `task-27-report.md` (pulsación larga con `input swipe X Y X Y 800`) y `fase5-report.md`. T28 con Daniel: cerrar una deuda real, agrupar un vuelto real y comprobar el cuadre.
6. **Fase 6 (T29, T30, T31):** guías de `task-29-report.md` (la acción de Inicio es `tocar "calendario"`, en minúscula), `task-30-report.md` y `task-31-report.md`.
7. **Respaldos de seguridad nuevos:** en Ajustes, la lista de previos con «Restaurar», en `verif` y sin SAF (`final-report.md`). Comprueba también el «hoy» refrescado al volver a la app y el aviso de «BNC archivada».
8. **Entrega a Daniel (T17 Paso 4, T28 y T32 Paso 4):**
   - `adb install -r` del APK **debug** en `com.cuentasclaras.app`, **sin sembrar**.
   - Daniel valida el criterio de éxito con sus datos:
     - (1) su respaldo sigue íntegro;
     - (2) un gasto en ≤ 3 toques;
     - (3) un pago móvil real en 2 toques;
     - (4) un TXT mensual sin duplicar lo capturado;
     - (5) la «Quincena» en «Esta semana».
   - El export y el restore por SAF los prueba él a mano.
9. **T32 Paso 5:** `superpowers:finishing-a-development-branch`. Presenta a Daniel las opciones para `nativa-compose` (merge, PR o dejarla abierta). Después, borra el workspace del plan y comunica la lista completa de rulings del ledger.

Lo que Daniel reporte se arregla con `superpowers:systematic-debugging`, con un implementador más su revisión.

## Menores aparcados o diferidos para después de la validación

Todos están en el ledger, como líneas `minor (deferred)` y `parked`. Los de más peso:
- Tocar «Inicio» pierde el estado de Importar/Ajustes (`BarraInferior.kt:130`, igual que la web; hay que ajustar el KDoc).
- La dirección del chip se lee de `estado.value.chips` (`NuevoViewModel.kt:401`).
- La lista de respaldos no se refresca tras un restore fallido.
- Editar una deuda escribe la fila entera (M8).
- El restore por SAF sigue con un `AlertDialog` crudo.
- Menores visuales de T20, T21, T27, T29 y T30 (se resuelven en el Paso 2 visual).
- `gradlew` sin bit ejecutable.
- `Double` como `120.0` en el respaldo (ruling vigente).
- Spec §7.2 y §3.7 con texto desfasado: actualizar el spec en el cierre.

## Rulings de esta sesión

La lista completa está en el ledger: todas las líneas `Ruling:` desde «Sesión 2026-10-16». Cada uno lleva su coste si resulta erróneo.

1. **Teléfono al final** (decisión de Daniel): todo lo que necesita el dispositivo va a la cola de arriba. Coste: los defectos de dispositivo aparecen tarde.
2. **E30:** las escrituras idempotentes de una sola preferencia (apariencia y fuente de la tasa) no deshabilitan controles con `guardando`, pero sí llevan try/catch y aviso.
3. **Helper único `ui/Escritura.kt::escribir`** (resuelve E16), con `aviso` propio y variante D19. Todos los VMs lo usan.
4. **Patrón de editores:** la hoja se cierra solo tras guardar con éxito (`alGuardar`) y el guard va antes de la validación. Prevalece sobre las firmas `Boolean` del plan.
5. **T15/E25:** se saca Movimientos con `popBackStack(…, saveState=false)` y `popUpTo(start){saveState=true}`. Verificado con javap contra navigation 2.10.2.
6. **T24:** las filas repetidas en el mismo texto salen desmarcadas pero editables («Repetida en este texto»). La key es por posición y el archivo tiene un tope de 1 MB.
7. **T14:** la barra de presupuestos y la posición de «Este mes» siguen `DESIGN.md`, no la web ni el brief.
8. **Orden de cuentas y categorías:** por orden de inserción, sin `ORDER BY id`.
9. **Captura:**
   - registro todo-o-nada con estado PENDIENTE;
   - la fecha es el día local de la captura;
   - la notificación propia se cancela al resolver;
   - el duplicado inverso solo genera un aviso, no bloquea;
   - `com.android.shell` solo se acepta en `verif`;
   - se filtran las notificaciones group-summary y ongoing;
   - re-enlace con 12 s de gracia y freno de 10 min.
10. **Regex:** nada de `(?U)` ni de invisibles literales, porque ICU4C de Android lo rechaza y tumbó `CalendarKt` en T29. Se añadió un test con NBSP.
11. **Opciones de `SelectEvento`:** solo texto, sin emoji (DESIGN §8).
12. **Columna de la referencia:** la decide Daniel en T25, como manda el plan.
13. **Tanda final:** 26 puntos. La pulsación larga quedó en T27 y los diferidos tras la validación son los de la sección anterior.

## Lecciones (para los briefs futuros)

- Los tests en JVM no ejercitan el motor de regex de Android (ICU4C). Prohibido usar banderas en línea.
- Las herramientas de escritura de los agentes decodifican `\uXXXX`: hay que construir los caracteres con códigos numéricos y verificar con `LC_ALL=C grep $'[\x80-\xFF]'`. `grep -P '\xEF'` no sirve en un locale UTF-8.
- Las guías de verificación también se revisan: T19 necesitó 3 rondas solo de guía (force-stop cancela notificaciones, el re-enlace de AOSP tarda ~10 s, Atrás no destruye la actividad en Android 12+).
- Paralelismo con worktrees: rebasar la rama sobre `nativa-compose` antes de revisarla mantiene los diffs limpios. Integrar con fast-forward o cherry-pick, y con 2 builds como máximo.
