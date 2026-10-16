# Guía de permisos: que Cuentas Claras capture las notificaciones del BNC

La app lee los avisos que el BNC te manda al teléfono ("Pago Movil Recibido", "Compra POS") y los guarda como
capturas pendientes. Te avisa con "Toca para registrar" y tú solo eliges la categoría. Para que funcione con la
pantalla apagada y días después, Xiaomi (MIUI/HyperOS) exige cinco ajustes. Todo esto se revisa en
**Ajustes → Permisos de captura** dentro de la app.

> Las rutas de menú de Xiaomi cambian un poco entre versiones de MIUI y HyperOS. Si no ves el nombre exacto,
> busca la palabra clave en el buscador de Ajustes del teléfono.

## 1. Acceso a notificaciones

Sin esto la app no ve nada.

1. En la app: **Ajustes → Permisos de captura → Acceso a notificaciones → Abrir ajuste**.
2. Si el teléfono no te lleva directo: **Ajustes del teléfono → Notificaciones y barra de estado → Acceso a
   notificaciones** (en algunas versiones: **Ajustes → Aplicaciones → Permisos → Acceso especial → Acceso a
   notificaciones**).
3. Activa **Cuentas Claras** y acepta el aviso del sistema.

En la app, la tarjeta pasa a **Concedido**.

## 2. Batería sin restricciones

Evita que el ahorro de batería duerma la app.

1. En la app: **Batería sin restricciones → Abrir ajuste** y acepta "Permitir".
2. Además, en MIUI/HyperOS: **Ajustes → Aplicaciones → Administrar aplicaciones → Cuentas Claras → Ahorro de
   batería → Sin restricciones**. (Si tu versión lo tiene en otra parte: **Seguridad → Batería → Ahorro de
   batería de apps → Cuentas Claras → Sin restricciones**.)

La app solo puede comprobar la exención estándar de Android. Que MIUI muestre "Sin restricciones" revísalo tú.

## 3. Autoinicio

Deja que la app arranque sola si el sistema la cerró. **La app no puede comprobarlo**: la tarjeta dice
"No detectable".

1. En la app: **Autoinicio (Xiaomi) → Abrir ajuste**.
2. Si no se abre, ve a mano: **Seguridad → Permisos → Autoinicio → Cuentas Claras** (activado). En MIUI 14:
   **Ajustes → Aplicaciones → Administrar aplicaciones → Cuentas Claras → Permisos → Inicio automático** (o
   "Autoinicio en segundo plano").

## 4. Bloquear la app en Recientes

Si limpias Recientes con la app sin bloquear, Xiaomi la cierra y deja de capturar. **No es detectable.**

1. Abre Recientes (el gesto o el botón de aplicaciones recientes).
2. Mantén pulsada la tarjeta de **Cuentas Claras** (o arrástrala un poco hacia abajo) hasta que salga el menú.
3. Toca el **candado**. La tarjeta muestra un candado cuando quedó bloqueada.

## 5. Permiso para avisarte (Android 13 o superior)

Sin él la captura se guarda igual, pero no suena ni se ve "Toca para registrar".

1. En la app: **Permiso para avisarte → Permitir avisos** y toca "Permitir".
2. Si el teléfono ya no muestra la ventana (la rechazaste dos veces): **Ajustes → Aplicaciones → Administrar
   aplicaciones → Cuentas Claras → Notificaciones** y actívalas.

## Cómo saber que funciona

- **Probar captura:** en la misma pantalla, pega el texto de un aviso del BNC y toca **Probar**. Si lo entiende,
  dice por ejemplo "Reconocida: Gasto · Bs 7.890,12 · Compra POS · ref 654321"; si no, "No reconocida". Esta
  prueba no guarda nada.
- **Banner de Inicio:** cuando Inicio avise de que falta el acceso a notificaciones, te lleva de vuelta a esta
  pantalla. Con todo en orden no aparece.
- **De verdad:** la próxima vez que el BNC te mande un aviso, debe salir "Toca para registrar" y quedar una
  captura pendiente.

## Si deja de capturar

1. Abre **Ajustes → Permisos de captura**: ¿el acceso a notificaciones o la batería dicen **Falta**? Vuelve a
   concederlos. Tras actualizar la app o reiniciar el teléfono, Xiaomi a veces los quita.
2. Revisa a mano lo que la app no puede ver: **Autoinicio** y el **candado en Recientes**.
3. Abre la app una vez: al abrirla intenta volver a conectar el servicio de lectura.
4. Si sigue sin capturar, quita y vuelve a poner el acceso a notificaciones (paso 1) y ábrela de nuevo.
5. En **Ajustes del teléfono → Opciones de desarrollador**, si está activa la opción **Optimización de MIUI**,
   puede romper las tareas en segundo plano. Desactivarla es el último recurso (el teléfono se reinicia).
