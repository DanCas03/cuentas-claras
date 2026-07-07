# Cuentas Claras — App personal de finanzas multi-cuenta y multi-moneda

**Fecha:** 2026-07-07 · **Estado:** Aprobada por Daniel (diseño conversacional) · **Plataforma:** PWA instalable (Android + Windows)

## 1. Objetivo

App de uso personal para controlar dinero repartido entre bolívares y dólares en la realidad venezolana: cuenta bancaria BNC (Bs), billetera Zinli (USD), ahorros en Binance (USD) y efectivo físico en USD. Registro manual ultrarrápido, importación de comprobantes reales (TXT de BNC y PDF mensual de Zinli), tasa de cambio BCV y Binance USDT, y dashboard consolidado. 100% offline-first, datos locales, sin cuentas ni servidores propios.

## 2. Plataforma y stack

- **PWA instalable**: icono propio y pantalla completa en Android; instalable también en Windows desde el navegador. Funciona offline (app shell precacheada).
- **Hosting**: para instalarse en Android la app debe servirse por HTTPS → hosting estático gratuito (Cloudflare Pages / GitHub Pages / Vercel). Solo se publica el código; los datos financieros nunca salen del dispositivo.
- **Stack**: React + TypeScript + Vite · Dexie (IndexedDB) · Recharts (gráficas) · pdf.js (extracción de texto del PDF Zinli en el navegador) · vite-plugin-pwa · Vitest (tests).
- **Moneda y formato**: importes almacenados como enteros en céntimos (nunca float). Formato visual venezolano: `Bs 13.517,75` y `$84.20`.
- **Diseño visual (pilar del producto, junto a la facilidad UX)**: tema oscuro premium — fondo azul-negro, acentos teal/menta, tipografía Manrope con números tabulares, micro-interacciones con feedback táctil. Sistema de diseño propio en CSS puro (tokens), sin librerías de estilos. *(Añadido el 2026-07-07 a petición de Daniel.)*

## 3. Modelo de datos (Dexie/IndexedDB)

- **Account**: `id, name, currency ('VES'|'USD'), kind ('bank'|'wallet'|'snapshot'|'cash'), isSavings (bool), archived`.
  - Cuentas iniciales: BNC (VES, bank) · Zinli (USD, wallet) · Binance (USD, snapshot, isSavings) · Efectivo (USD, cash) · Efectivo ahorro (USD, cash, isSavings). El usuario puede crear/editar/archivar cuentas.
- **Transaction**: `id, type ('expense'|'income'|'transfer'|'adjustment'), accountId, amountMinor, date, time?, categoryId?, note?, source ('manual'|'bnc'|'zinli'|'adjust'), importKey? (dedupe), toAccountId?, toAmountMinor?, rateUsed?` — las transferencias son un solo registro con origen y destino; si cruzan moneda guardan `toAmountMinor` y `rateUsed`.
- **Category**: `id, name, icon, color, kind ('expense'|'income'), monthlyBudgetMinor?`. Set inicial: Supermercado, Comida fuera, Transporte, Salud, Servicios, Comisiones bancarias, Hogar, Ocio/Juegos, Suscripciones, Ropa, Otros gastos · Ingresos: Pago móvil recibido, Transferencia recibida, Sueldo/Pagos, Otros ingresos. Editables.
- **Rate**: `date, source ('BCV'|'BINANCE'|'MANUAL'), vesPerUsd`. Histórico completo; cada conversión usa la tasa vigente de su fecha según la fuente activa.
- **Rule**: `id, pattern (substring, case-insensitive), categoryId, autoCreated (bool)` — reglas de categorización aprendidas en los imports.
- **Settings**: moneda de vista del dashboard (USD por defecto, alternable a Bs), fuente de tasa activa (BCV | Binance | Manual), última cuenta usada, etc.

## 4. Funcionalidades

### 4.1 Registro rápido (prioridad UX #1)
Botón `+` en pantalla principal → teclado numérico grande → categoría (grid de iconos) → guardar. Fecha hoy, última cuenta usada preseleccionada, nota opcional. Toggle gasto/ingreso. Objetivo: ≤ 3 toques + importe.

Además, **atajos de app** (manifest `shortcuts`): toque largo sobre el icono en Android → "➕ Nuevo gasto" abre directo el teclado numérico.

### 4.2 Transferencias y depósitos
Tipo "transferencia": cuenta origen → cuenta destino. Cubre depósito de efectivo al banco, mover efectivo disposición↔ahorro, recargas Zinli, etc. Si las monedas difieren, propone la tasa del día (editable en el momento) y guarda ambos importes.

### 4.3 Ajuste de saldo
Para Binance (solo saldos) y para cuadrar efectivo: introduces el saldo real actual y la app crea una transacción `adjustment` con la diferencia. En Binance la diferencia se muestra como rendimiento/variación.

### 4.4 Tasas de cambio
- Fetch automático (cuando hay internet) de **BCV** y **Binance USDT/VES**, vía API pública gratuita (candidata: pyDolarVenezuela; alternativas: ve.dolarapi.com, ExchangeMonitor). *Riesgo técnico abierto: CORS desde el navegador — se resuelve con spike en implementación probando los endpoints candidatos; siempre existe el fallback manual.*
- El usuario elige la fuente activa para las conversiones (BCV, Binance o Manual) y puede sobrescribir la tasa de cualquier día a mano.
- Offline: se usa la última tasa conocida, mostrando su fecha.

### 4.5 Importador BNC (TXT tabulado — formato elegido)
Formato: TXT de "25 últimos movimientos", tab-separated con cabeceras `Fecha, Hora, Referencia, Cod. Transacción, Tipo Transacción, Tip. Operación, Descripción, Debe, Haber, Saldo, Referencia 2`. Números formato venezolano (coma decimal).
- Subes o pegas el TXT → vista previa con categorías propuestas → confirmas → import transaccional (todo o nada).
- **Dedupe**: `importKey = referencia + hora + monto`; reimportar rangos solapados no duplica nada.
- **Reglas aprendidas**: al categorizar un movimiento nuevo en la vista previa se ofrece crear regla por texto de descripción (p. ej. "JUEGAENLINEA" → Ocio/Juegos); los siguientes imports categorizan solos.
- **Pares cargo+comisión** de pago móvil (misma referencia) se muestran agrupados y la comisión se categoriza como "Comisiones bancarias" automáticamente.
- **Aviso de hueco**: si el saldo encadenado del import no conecta con el último saldo conocido de la cuenta, la app avisa que hay movimientos perdidos entre exports (los formatos BNC solo traen 25 movimientos → conviene importar semanalmente).

### 4.6 Importador Zinli (PDF mensual)
pdf.js extrae el texto del "Resumen de movimientos". Patrón por movimiento: `fecha (dd/mm/yyyy) + motivo (Pagaste/Recibiste/...) + descripción (puede ocupar varias líneas) + referencia (dígitos) + monto ($ con signo)`. El resumen trae saldo inicial y final del mes → el import **valida que la suma cuadre** y avisa si no. Dedupe por referencia. Mismas reglas de categorización que BNC.

### 4.7 Dashboard
- **Patrimonio total** consolidado en USD (alternable a Bs) con desglose por cuenta y separación **a disposición vs ahorro** (ahorro = cuentas `isSavings`: Binance + efectivo ahorro).
- Mes actual: balance (ingresos − gastos), donut de gastos por categoría, evolución diaria acumulada, comparativa con meses anteriores.
- Presupuestos opcionales por categoría con barra de progreso.
- Lista de movimientos con búsqueda y filtros (cuenta, categoría, mes).

### 4.8 Respaldo (Google Drive sin API)
Export/import de **todo** como JSON versionado (`schemaVersion, exportedAt, accounts, transactions, categories, rules, rates, settings`).
- Android: botón Exportar → selector del sistema → eliges Drive (2 toques). Importar abre desde Drive igual.
- Windows: File System Access API para guardar directo en la carpeta de Google Drive.
- Importar reemplaza todo, con confirmación y **auto-export de seguridad previo**.

## 5. Manejo de errores

- Parsers: línea irreconocible → se muestra en la vista previa marcada, importas el resto y decides sobre ella (nunca import silenciosamente incompleto).
- Import transaccional: o entra todo lo confirmado o no entra nada.
- Validación Zinli contra saldo inicial/final; aviso de hueco en BNC por encadenado de saldos.
- API de tasas caída/CORS → fallback a siguiente proveedor → última tasa conocida con su fecha visible → manual.

## 6. Testing

Vitest para: parser BNC TXT y parser Zinli (fixtures anonimizadas derivadas de los comprobantes reales), dedupe, encadenado de saldos, motor de reglas, conversión de moneda con histórico de tasas, cálculo de saldos por cuenta y export/import JSON (round-trip). **Solo ese núcleo crítico lleva tests automatizados; la UI se valida con verificación manual guiada.** *(Alcance ajustado el 2026-07-07 a petición de Daniel.)*

## 7. Fuera de alcance v1 (candidatas a v2)

- **Widget nativo de Android de botones** (acordado como v2): envolver la PWA con Bubblewrap/TWA en un APK que incluya un widget con accesos "➕ Gasto / ➕ Ingreso" con deep links a la app. Requiere Android SDK y sideload. Widget con datos en vivo (saldo) queda descartado: exigiría almacenamiento nativo.
- Sincronización automática con Drive API (si el flujo manual pesa).
- Import del PDF/XLS/TXT2 de BNC y CSV genérico de otros bancos.
- Conexión directa a Binance API (el saldo se registra a mano).
- Notificaciones push.
