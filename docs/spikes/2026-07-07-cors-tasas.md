# Spike: CORS de APIs de tasas (BCV / Binance USDT-VES)

**Fecha:** 2026-07-07 · **Origen probado:** http://localhost:5173

| Proveedor | URL exacta | ¿CORS OK? | ¿Trae BCV? | ¿Trae Binance? | Campo del valor |
|---|---|---|---|---|---|
| pydolarve | `https://pydolarve.org/api/v2/dollar?page=criptodolar` | No verificable: dominio caído | — | — | — |
| ve.dolarapi.com | `https://ve.dolarapi.com/v1/dolares/oficial` | Sí (`Access-Control-Allow-Origin: *`, HTTP 200) | Sí (674,9305 Bs, coincide con BCV del día) | No (en `/v1/dolares` solo existen `oficial` y `paralelo`) | `promedio` |
| ExchangeMonitor | `https://exchangemonitor.net/api/dolar` | Sí (`Access-Control-Allow-Origin: *`, `Access-Control-Allow-Methods: POST, GET`) pero **HTTP 400: requiere cabecera `Authorization`** — API privada, inutilizable sin key | inaccesible | inaccesible | — |
| CriptoDolar (vcoud) | `https://exchange.vcoud.com/coins/latest` | Sí (`Access-Control-Allow-Origin: *`, HTTP 200) | Sí (item `"name": "Dólar BCV"`, `"slug": "dolar-bcv"`, 674,93) | No (el único "Dólar Binance" del listado es el argentino, `"currency": "ARS"`) | `price` del elemento del array con `slug === "dolar-bcv"` |

Método: verificado por cabeceras `Access-Control-Allow-Origin` vía HTTP directo (sin navegador, decisión de Daniel 2026-07-07); los fetch de la app son GET simples sin preflight, por lo que ACAO decide. Pendiente re-verificar desde el origen desplegado en la Tarea 24 (CORS puede diferir por dominio).

## Detalle por candidato

### A — pydolarve: dominio muerto a fecha de hoy

- `pydolarve.org` no tiene registro A/AAAA (verificado con nslookup contra DNS local, 8.8.8.8 y 1.1.1.1; la zona responde pero sin dirección). `docs.pydolarve.org` da NXDOMAIN.
- `curl` termina en `(6) Could not resolve host: pydolarve.org` — es imposible emitir veredicto CORS.
- El repo original de GitHub (`fcoagz/pydolarvenezuela`) devuelve 404 en la API de GitHub; solo quedan forks abandonados y su antiguo despliegue en Vercel (`pydolarvenezuela-api.vercel.app`) responde `DEPLOYMENT_NOT_FOUND`.
- Siguiendo la pista de su fuente de datos ("criptodolar"), se encontró vivo su upstream: `exchange.vcoud.com` (fila CriptoDolar de la tabla).
- **Sin fixture** → la Tarea 13 lo excluye de la cadena.

### B — ve.dolarapi.com: funciona, solo BCV

- `GET https://ve.dolarapi.com/v1/dolares/oficial` → HTTP 200, `Content-Type: application/json`, `Access-Control-Allow-Origin: *`. Se repitió enviando `Origin: http://localhost:5173` y la cabecera se mantiene en `*`.
- Valor BCV en `promedio` (674.9305), fecha en `fechaActualizacion`. `compra`/`venta` vienen `null`.
- `GET https://ve.dolarapi.com/v1/dolares` (también ACAO `*`) lista únicamente `oficial` y `paralelo` (EnParaleloVzla, 760.488625): **no hay monitor Binance USDT/VES** en este proveedor. El `paralelo` NO es Binance y la spec fija BCV + Binance, así que no se usa.
- Fixture: `src/rates/fixtures/dolarapi-oficial.json` (respuesta real sin editar).

### C — ExchangeMonitor: API privada; su ecosistema público es vcoud

- `GET https://exchangemonitor.net/api/dolar` → HTTP 400 con cuerpo `{"success":false,"error":"Error 400. Solicitud incorrecta, falta el encabezado de autorización o es inválido."}`. Aunque el CORS es permisivo, exige `Authorization` (key privada) → **sin endpoint JSON público utilizable**.
- Investigando el ecosistema se encontró `https://exchange.vcoud.com/coins/latest` (backend de CriptoDolar, la misma fuente que pyDolarVenezuela consultaba para su página `criptodolar`): HTTP 200, ACAO `*` (también enviando `Origin: http://localhost:5173`), array JSON de 148 monitores.
  - Trae "Dólar BCV" (`slug: "dolar-bcv"`, `price: 674.93`, `currency: "VES"`) — coincide con el BCV de dolarapi.
  - **No trae Binance USDT/VES**: se listaron los 9 monitores con `currency === "VES"` o `type === "bolivar"` (BCV + 8 tasas bancarias) y se probó `?type=bolivar`; el único "Dólar Binance" del listado completo es el de Argentina (`currency: "ARS"`).
- Fixture: `src/rates/fixtures/criptodolar-vcoud.json` (respuesta real sin editar).

## Decisión

Cadena de proveedores para la Tarea 13 (solo BCV, en este orden):

1. **ve.dolarapi.com** — `GET https://ve.dolarapi.com/v1/dolares/oficial`, valor en `promedio`.
2. **CriptoDolar (vcoud)** — `GET https://exchange.vcoud.com/coins/latest`, valor en `price` del elemento con `slug === "dolar-bcv"` (respaldo si dolarapi falla).

pydolarve queda **excluido** (dominio muerto, sin fixture). ExchangeMonitor queda **excluido** (requiere API key).

**Binance USDT/VES no está disponible en ningún proveedor probado con CORS público a fecha de hoy.** La tasa Binance queda cubierta por el **override manual** (que por diseño siempre existe y ya está fijado en la spec); si pydolarve resucita o aparece un proveedor público con USDT/VES, se re-evalúa. Hallazgo relacionado: según su documentación (`dolarvzla.com/dev`), DolarVZLA publica BCV como JSON estático sin key en `rates.dolarvzla.com` (el CDN existe y responde con ACAO `*`, pero la ruta exacta del JSON no se verificó en este spike), y sus endpoints de USDT/Binance (`api.dolarvzla.com`) requieren API key, así que tampoco sirve para Binance.

La Tarea 13 debe tratar "sin proveedor de Binance" como estado normal, no como error.
