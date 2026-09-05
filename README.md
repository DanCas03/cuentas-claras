# Cuentas Claras

PWA personal de finanzas multi-cuenta y multi-moneda (Bs + USD) para la realidad venezolana.
Offline-first: todos los datos viven en IndexedDB del dispositivo; el hosting solo sirve código.

- Spec: `docs/superpowers/specs/2026-07-07-finanzas-personales-design.md`
- Plan: `docs/superpowers/plans/2026-07-07-cuentas-claras-implementacion.md`

## Comandos

- `npm run dev` — servidor de desarrollo
- `npm run test` / `npm run test:run` — tests (Vitest)
- `npm run build` — typecheck + build de producción
- `npm run preview` — sirve el build (necesario para probar la PWA/offline)
- `npm run iconos` — regenera los íconos PNG desde SVG

## Deploy

Producción en Cloudflare Pages (HTTPS, requisito para instalar la PWA en Android): **https://cuentas-claras-r2f.pages.dev**

```powershell
npm run build
npx wrangler pages deploy dist --project-name cuentas-claras
```

## Privacidad

Los comprobantes reales de la raíz están gitignored y **nunca** se commitean.
Las fixtures de tests son anonimizadas (estructura real, datos inventados).

## Calendario y eventos (v3)

Un **evento** es una fecha en la que hay que pagar o cobrar algo (la quincena, Netflix, el
condominio). No es dinero: no altera saldos ni estadísticas. Sus **ocurrencias** (la quincena
del 15 de septiembre) se derivan de la regla de repetición cada vez que se dibujan; lo único
que se guarda es la definición del evento y, en cada movimiento asignado, el par
`eventId` + `eventDate`.

- **Dónde**: `#/calendario` (se llega desde la tarjeta «Esta semana» de Inicio, no desde la barra).
- **Crear**: «Nuevo evento» → Pago o Cobro, categoría (obligatoria: define la dirección),
  cuenta esperada y monto opcionales, fecha y repetición
  (Una vez · Cada semana · Cada mes · Quincenal (15 y último) · Cada año).
  En «Cada mes», `31` significa **último día del mes**.
- **Terminar una suscripción**: pon una fecha de Fin (chip «Terminar hoy»).
- **Estados**: hecha (tiene movimientos) > omitida > vencida > pendiente. Hoy cuenta como pendiente.
- **Asignar un movimiento**: en Nuevo, el chip del evento guarda y asigna en **un toque**
  (copia la categoría del evento, editable antes de guardar); en el editor de un movimiento,
  con el select «Evento»; en la vista previa de los imports, el evento sugerido gana a la
  regla aprendida y queda corregible. Desasignar conserva la categoría.
- **Sugerencia**: la ocurrencia puede estar hasta 31 días antes del movimiento o 7 después,
  y el monto esperado debe coincidir ±10 % (si el evento tiene monto).
- **Eliminar un evento** nunca borra movimientos: los desasigna y conservan su categoría.

## Grupos transaccionales con categoría (v3)

Un grupo puede llevar una **categoría del vuelto**. Se aplica solo si su tipo coincide con el
signo del residuo (una categoría de gasto para un residuo negativo, de ingreso para uno
positivo); si no coincide, el residuo cuenta como «Vuelto», igual que en v2. Los movimientos
del grupo conservan su propia categoría.

## «Este mes» expandible (v3)

La tarjeta «Este mes» tiene navegación de mes («‹ › hoy», acotada entre el mes más antiguo con
movimientos y el actual) que gobierna también Comparativa y Presupuestos, y un botón
«Ver todas las categorías» con la lista completa de gastos e ingresos; tocar una categoría
abre Movimientos ya filtrado por esa categoría y ese mes.

## Respaldos

`schemaVersion: 3` (9 tablas, incluye `calEvents`). Los respaldos **v2 se aceptan** y se
normalizan al importarlos; los v1 siguen rechazados.
