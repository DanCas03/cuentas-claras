> Obsoleta para la app nativa: ver `guia-permisos.md`.

# Capturar notificaciones del BNC con MacroDroid

Esta guía configura tu Android para que, al tocar una acción de MacroDroid tras
una notificación del BNC, se abra Cuentas Claras con el movimiento pre-llenado.
Solo confirmas la categoría: 1 toque.

## Requisitos

- MacroDroid instalado (la versión gratis alcanza: esta es 1 macro).
- Cuentas Claras instalada como PWA desde https://cuentas-claras-r2f.pages.dev
- Permiso de acceso a notificaciones concedido a MacroDroid
  (Ajustes → Notificaciones → Acceso a notificaciones → MacroDroid).

## La macro, paso a paso

1. Abre MacroDroid → **Añadir macro**.
2. **Disparador**: `Notificación` → `Notificación recibida` → elige
   **la app del BNC** en la lista de aplicaciones. Acepta.
3. **Acción**: `Abrir sitio web / URL` y pega EXACTAMENTE:

   `https://cuentas-claras-r2f.pages.dev/#/captura?texto={not_text_urlencoded}`

   (`{not_text_urlencoded}` es una "magic variable" de MacroDroid: el texto de
   la notificación ya codificado para URL. Escríbela con el selector de
   variables `…` del campo, no a mano, para evitar errores de tipeo.)
4. **Restricciones**: ninguna.
5. Ponle nombre (p. ej. «BNC → Cuentas Claras») y guarda.

## Cómo se usa

- Llega un pago móvil o una compra → llega la notificación del BNC → MacroDroid
  abre Cuentas Claras con el ingreso/gasto pre-llenado en la cuenta BNC.
- Tocas la categoría → guardado. Si no quieres registrarlo, cierras y ya:
  **nada se guarda sin tu confirmación**.
- Si la notificación no se reconoce, se abre Nuevo en blanco con el texto en la
  nota, para que lo registres a mano.

## Ojo con los duplicados (papercut aceptado en v2)

Si registras un movimiento por captura y LUEGO importas el TXT mensual del BNC,
el import puede volver a traer ese mismo movimiento: el detector de duplicados
del import compara contra movimientos importados, no contra los capturados.
Regla práctica: al revisar la vista previa del import mensual, desmarca las
filas que ya registraste por captura (la referencia en la nota te ayuda a
ubicarlas). La deduplicación automática captura↔import queda para la v3.
