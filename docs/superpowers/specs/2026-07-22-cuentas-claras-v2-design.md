# Cuentas Claras v2 — Deudas, patrimonio en 3 vistas, movimientos transaccionales y captura de notificaciones

**Fecha:** 2026-07-22 · **Estado:** aprobado en brainstorming con Daniel (esta sesión).
**Base:** v1 desplegada en https://cuentas-claras-r2f.pages.dev (rama `main`, revisión final con fixes en curso).

## Contexto y objetivo

La v1 cumplió su criterio de éxito (validada por Daniel en Android y Windows el 2026-07-22). La v2 añade cuatro capacidades pedidas tras el uso real, más un bug encontrado en el selector de archivos. Se mantienen TODAS las restricciones globales de la v1: montos como enteros en céntimos, todo en español, tests solo en el núcleo crítico (TDD), UI sin tests automáticos pero con verificación manual bloqueante, estilos solo por tokens de `estilos.css`, patrón `guardando` en todo guardado async, jerarquía z contenido < nav(30) < modal(40) < toast(50), privacidad de comprobantes reales.

## Decisiones cerradas (Daniel, esta sesión — NO reabrir)

1. Una deuda pendiente **no altera ningún saldo**; el gasto/ingreso real nace solo al marcarla pagada/cobrada.
2. La deuda vive en **la moneda de la cuenta asignada** para pagarla/cobrarla; sin conversiones al cerrar. Si se cambia la cuenta asignada a una de otra moneda, la app pide reescribir el monto.
3. Recordatorio = **solo lista visible** (sin fechas límite, sin notificaciones).
4. Cuentas por cobrar = **espejo exacto informativo**: no suman en ninguna vista del patrimonio hasta cobrarse.
5. Vista 1 del slider = **flag configurable por cuenta** (`enDisposicion`), no una lista fija de cuentas.
6. Captura de notificaciones del banco vía **MacroDroid → URL de la PWA** (sin backend, sin app nativa).
7. **Sin retrocompatibilidad de datos**: Daniel re-ingresa/re-importa manualmente tras actualizar. No se escribe lógica de compatibilidad con respaldos v1 ni con claves/registros del esquema anterior (la subida de versión de Dexie se hace igualmente porque es el mecanismo para añadir tablas, pero es aditiva y sin código de migración de contenido).

## 1. Deudas y cuentas por cobrar

**Modelo.** Nueva tabla Dexie `debts`:

```ts
interface Debt {
  id: string;              // crypto.randomUUID()
  direction: 'porPagar' | 'porCobrar';
  concepto: string;        // persona o descripción («Juan — préstamo almuerzo»)
  amountMinor: number;     // céntimos, en la moneda de la cuenta asignada
  accountId: string;       // cuenta asignada para pagar/cobrar (define la moneda)
  status: 'pendiente' | 'cerrada';
  createdIso: string;      // fecha de registro
  settledTxId?: string;    // transacción creada al cerrar
  settledIso?: string;
}
```

**UI.**
- Tarjeta «Deudas» en Inicio, debajo del patrimonio: totales «Debes $X · Te deben $Y» (por moneda, consolidado a la moneda de vista con tasa efectiva SOLO para mostrar el total de la tarjeta) y las 3 más recientes pendientes; enlace a la vista completa.
- Vista de gestión (ruta propia, p. ej. `#/deudas`): pestañas Por pagar / Por cobrar; alta con formulario (concepto, monto en la moneda de la cuenta elegida, cuenta asignada, dirección); editar y eliminar pendientes; historial de cerradas plegado.
- **Marcar pagada/cobrada** (botón por fila, con confirmación y guard `guardando`): crea la transacción real — gasto (deuda por pagar) o ingreso (por cobrar) — en la cuenta asignada, con fecha del día, nota `«Deuda pagada: {concepto}»` / `«Deuda cobrada: {concepto}»`, categoría seed nueva **«Deudas»** (kind expense) para pagos y **«Transferencia recibida»** para cobros; guarda `settledTxId` y pasa a `cerrada`. Borrar una deuda cerrada NO borra su transacción.

**Reglas.** El alta/edición valida monto > 0 y cuenta existente. El cierre es idempotente (guard + status check dentro de una transacción Dexie). Las deudas no aparecen en Movimientos ni en métricas: solo su transacción de cierre, que es un movimiento normal.

## 2. Patrimonio en 3 vistas (slider)

La tarjeta «Patrimonio total» del dashboard se convierte en un slider horizontal (CSS `scroll-snap`, sin librerías nuevas) con indicadores de puntos y la misma cifra protagonista:

1. **«A disposición»** — suma de cuentas con `enDisposicion: true`.
2. **«Total neto»** — patrimonio total − Σ deudas `porPagar` pendientes (cada deuda convertida desde la moneda de su cuenta a la moneda de vista con la tasa efectiva del día).
3. **«Total actual»** — el patrimonio total de la v1, intacto.

- Nuevo campo opcional `enDisposicion?: boolean` en `Account`, editable con un check en el editor de cuentas de Ajustes. Default al crear/migrar: `!ahorro` (Daniel luego marca Binance y desmarca Efectivo a su gusto).
- El toggle USD/Bs existente aplica a las 3 vistas. La línea «A disposición / Ahorro» actual se sustituye por el slider; el desglose por cuentas queda igual debajo.
- «Te deben $Y» aparece como línea informativa en la tarjeta de Deudas, nunca sumada al patrimonio.

## 3. Movimientos transaccionales («me pasaron plata para comprar algo»)

**Modelo.** Campo opcional `groupId?: string` en `Transaction` (con índice) + tabla `txGroups { id, nota?, createdIso }`.

**Flujo.** En Movimientos, mantener pulsada una fila activa el modo selección (checkboxes); con ≥2 seleccionados aparece «Agrupar como transaccional». El grupo se muestra plegado con insignia 🔁, su nota opcional y su residuo; se puede expandir, añadir/quitar movimientos y desagrupar (revierte todo).

**Semántica de métricas (el corazón).** Los saldos de cuentas NO cambian (los movimientos reales ya existen). En TODAS las agregaciones (ingresos/gastos del mes, balance del mes, comparativa mensual, gráficas, presupuestos):
- Se excluyen los movimientos con `groupId`.
- Por cada grupo se suma solo su **residuo** = Σ firmada de sus movimientos (ingresos +, gastos −). Residuo positivo → ingreso virtual con categoría seed **«Vuelto» (ingreso)**; negativo → gasto virtual con categoría seed **«Vuelto» (gasto)»; cero → nada.
- El residuo se fecha con el movimiento más reciente del grupo y se recalcula al editar el grupo o sus movimientos.
- Las transferencias/ajustes no pueden agruparse (solo income/expense).

## 4. Captura desde notificaciones del BNC (MacroDroid)

- Nueva ruta `#/captura?texto=<urlencoded>`: recibe el texto crudo de una notificación, lo pasa por `parseNotificacionBnc(texto)` (módulo nuevo, TDD) que reconoce al menos: **pago móvil recibido** (→ ingreso en BNC) y **compra con tarjeta** (→ gasto en BNC), extrayendo monto Bs y referencia/comercio para la nota.
- Resultado reconocido → abre la pantalla de Nuevo pre-llenada (tipo, monto, cuenta BNC, nota con referencia) para **confirmar con 1 toque** (categoría elegible ahí mismo). No reconocido → Nuevo en blanco con el texto en la nota. Nunca se guarda nada sin confirmación.
- Guía paso a paso para Daniel (en README o doc aparte): macro de MacroDroid con disparador «notificación de la app BNC» y acción «abrir URL» `https://cuentas-claras-r2f.pages.dev/#/captura?texto={not_text_urlencoded}`.
- **Prerrequisito de implementación:** Daniel entrega 2-3 textos reales de notificaciones (anonimizados: sin nombres/cédulas/números completos) para el parser y sus fixtures. Sin ellos, esta pieza se implementa al final.
- Deduplicación: si la notificación trae referencia, la nota la incluye; el import mensual del TXT seguirá detectando su duplicado por `importKey` **solo si** el movimiento capturado se registró vía captura con referencia coincidente — fuera de alcance automatizarlo en v2: el usuario es responsable de no duplicar (papercut aceptado, documentado en la guía).

## 5. Bug: el TXT de BNC no se deja seleccionar en Android

Síntoma (Daniel, Android, selector desde Descargas/Archivos): el TXT aparece pero tocarlo no hace nada. Hipótesis principal: el atributo `accept` del input de ImportBnc filtra por `.txt,text/plain` y Android cataloga el archivo del banco con otro MIME (típicamente `application/octet-stream`), dejándolo deshabilitado en el selector. Trabajo: investigar con `superpowers:systematic-debugging` (confirmar el accept actual, reproducir criterios de filtrado de Android), corregir (ampliar/eliminar el accept del input BNC; revisar el de Zinli por el mismo patrón), verificar en desktop headless que la carga sigue intacta, y validación final de Daniel en su teléfono.

## 6. Datos (Dexie v2 y respaldo)

- `db.version(2)`: añade `debts`, `txGroups`, índice `groupId` en transactions, y los seeds nuevos (categorías «Deudas», «Vuelto» ingreso/gasto) vía `seedIfEmpty` ampliado o upgrade aditivo. **Sin migración de contenido ni retrocompatibilidad** (decisión 7).
- `BackupFile` sube a `schemaVersion: 2` con las tablas nuevas; `validateBackup` exige exactamente la versión 2 (los respaldos v1 se rechazan con mensaje claro — decisión 7).

## 7. Testing (núcleo crítico, TDD)

- Cierre de deudas: transacción correcta (tipo, cuenta, monto, categoría), idempotencia del cierre, moneda = la de la cuenta.
- Residuo de grupos: positivo/negativo/cero, exclusión en cada agregación (mes, comparativa, gráficas, presupuestos), fecha del residuo, desagrupar restaura.
- `parseNotificacionBnc`: fixtures anonimizadas de pago móvil y compra, formatos no reconocidos → null.
- Respaldo v2: round-trip con deudas y grupos; rechazo de v1.
- UI (deudas, slider, modo selección, captura): verificación manual headless + validación de Daniel en dispositivo.

## Fuera de alcance (v3+)

Notificaciones push de vencimientos, fechas límite y abonos parciales de deudas, app companion nativa, deduplicación automática captura↔import TXT, lectura de notificaciones de Zinli/Binance.
