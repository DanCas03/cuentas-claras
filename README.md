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

## Privacidad

Los comprobantes reales de la raíz están gitignored y **nunca** se commitean.
Las fixtures de tests son anonimizadas (estructura real, datos inventados).
