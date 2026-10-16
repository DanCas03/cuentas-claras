# NotificationListenerService en Xiaomi (MIUI/HyperOS): ciclo de vida, re-enlace y defensas

Fecha: 2026-10-15. Rama `nativa-compose` (Tarea 18). Alimenta las Tareas 19 (servicio y re-enlace),
21 (pantalla Permisos y guía) y 22 (verificación en el Xiaomi). App: `targetSdk = 35`, `minSdk = 26`,
uso personal, fuera de Play Store.

**Cómo leer las fuentes.** Cada afirmación lleva su fuente entre corchetes. Las del código de AOSP
vienen de la rama `main` del espejo `aosp-mirror/platform_frameworks_base`, descargada el 2026-10-15.
Los números de línea son de esa descarga y pueden moverse, así que se cita también el método.

- [NLS] https://developer.android.com/reference/android/service/notification/NotificationListenerService
- [NM] https://developer.android.com/reference/android/app/NotificationManager
- [MS] AOSP `services/core/java/com/android/server/notification/ManagedServices.java`
  (https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/services/core/java/com/android/server/notification/ManagedServices.java)
- [NMS] AOSP `services/core/java/com/android/server/notification/NotificationManagerService.java` (misma ruta base)
- [PMS] AOSP `services/core/java/com/android/server/pm/PackageManagerService.java` (misma ruta base)
- [SHELL] AOSP `services/core/java/com/android/server/notification/NotificationShellCmd.java` (misma ruta base)
- [MANIFEST] AOSP `core/res/AndroidManifest.xml` (misma ruta base)
- [PERM] https://developer.android.com/develop/ui/views/notifications/notification-permission
- [CANALES] https://developer.android.com/develop/ui/views/notifications/channels
- [DOZE] https://developer.android.com/training/monitoring-device-state/doze-standby
- [SETTINGS] https://developer.android.com/reference/android/provider/Settings#ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
- [A15] https://developer.android.com/about/versions/15/behavior-changes-all
- [DKMA] https://dontkillmyapp.com/xiaomi y su fuente
  https://github.com/urbandroid-team/dont-kill-my-app/blob/master/_vendors-content/xiaomi/user.md
- [TERCEROS-MIUI] Fuentes no oficiales sobre componentes internos de MIUI (Xiaomi no los documenta):
  https://github.com/Jeferson505/AndroidAutoStartPlugin (README) y el hilo de B4X
  https://www.b4x.com/android/forum/threads/open-auto-start-page.129180/ sobre
  `AutoStartManagementActivity`; el gist https://gist.github.com/1fexd/a71eb273670d2ff2434f80e026fd344c
  sobre la operación AppOps 10008; la biblioteca https://github.com/XomaDev/MIUI-autostart (enlazada
  desde [DKMA]).

Xiaomi no publica documentación para desarrolladores sobre autoinicio, ahorro de batería por app ni
bloqueo en Recientes. Lo de MIUI/HyperOS sale de [DKMA], una fuente comunitaria (la mejor que hay), y
de [TERCEROS-MIUI]. **Todo lo de MIUI se confirma en el teléfono en la Tarea 22.**

---

## 1. Ciclo de vida: `onListenerConnected` / `onListenerDisconnected` y cuándo re-enlaza el sistema

- `onListenerConnected()` avisa de que el listener está habilitado y conectado al gestor de
  notificaciones. Las llamadas al gestor (por ejemplo `getActiveNotifications()`) deben esperar a
  ese evento. [NLS]
- `onListenerDisconnected()`: después de esta llamada no llega ningún evento más, y en ese momento lo
  único que se puede llamar es `requestRebind(ComponentName)`. [NLS]
- Un listener sin la meta-data `android.service.notification.default_autobind_listenerservice` se
  enlaza automáticamente: si falta, el valor por defecto es `true`. Con `false` solo se enlaza a
  demanda con `requestRebind`. [NLS, constante `META_DATA_DEFAULT_AUTOBIND`, API 34] → **No hay que
  declararla.**
- El sistema enlaza con `BIND_AUTO_CREATE | BIND_FOREGROUND_SERVICE | BIND_ALLOW_WHITELIST_MANAGEMENT`.
  [MS, `getBindFlags()`, ~l. 339]
- **Si muere el proceso de la app**, `binderDied()` quita el servicio de la lista sin desenlazar. El
  comentario del código dice: «The system will bring the service back up, and the onServiceConnected
  handler will read the service with the new binding». Es decir, el enlace `BIND_AUTO_CREATE` vuelve a
  crear el servicio solo. [MS, `ManagedServiceInfo.binderDied()`, ~l. 1963]
- **Si el enlace «muere»** (`onBindingDied`), el sistema desenlaza y programa **un único** reintento
  a los 10 s (`ON_BINDING_DIED_REBIND_DELAY_MS = 10000`). Si ya hay un reintento en curso, no hace
  otro. [MS, `registerServiceLocked` → `onBindingDied`, ~l. 1693–1707; constante ~l. 107]
- `onServiceDisconnected` solo registra «connection lost»; no hace nada más. [MS, ~l. 1688]
- El sistema vuelve a ejecutar `rebindServices(...)` en estos casos:
  - cambia un paquete con servicios aprobados (`onPackagesChanged`, por ejemplo al actualizar o al
    cambiar el estado de un componente; ver §2);
  - se desbloquea el usuario (`onUserUnlocked`);
  - cambia el usuario (`onUserSwitched`, que fuerza el re-enlace);
  - cambia la lista de aprobados.
  [MS, ~l. 1040–1076 y ~l. 322]
- **En MIUI/HyperOS** el sistema puede impedir que el proceso vuelva a arrancar en segundo plano si
  el «Autoinicio» está desactivado. Por eso [DKMA] pide activar Autoinicio, «Sin restricciones» en el
  ahorro de batería y bloquear la app en Recientes (ver §4). AOSP no puede demostrar este bloqueo
  porque es código propietario de Xiaomi: **se verifica en la Tarea 22** con `am force-stop` y
  reapertura (Review Focus 5).

## 2. `requestRebind(ComponentName)` y el truco de alternar el componente

### `requestRebind`

- La documentación dice: «Request that the listener be rebound, **after a previous call to
  `requestUnbind()`**» y «This method will fail for listeners that have not been granted the
  permission by the user». [NLS, `requestRebind`, API 24]
- En el código, `requestBindListener(component)` comprueba que el llamador sea el mismo paquete y
  llama a `ManagedServices.setComponentState(component, userId, true)`. [NMS, `requestBindListener`,
  ~l. 5379]
- `setComponentState` **no hace nada si el componente no estaba «snoozed»**. Solo lo está si antes se
  llamó a `requestUnbind`: `boolean previous = !mSnoozing.contains(userId, component); if (previous
  == enabled) return;`. [MS, `setComponentState`, ~l. 1157–1163]
- **Conclusión:** si el listener se desconectó porque murió el proceso o porque MIUI lo mató,
  `requestRebind` **no hace nada**. Solo deshace un `requestUnbind` propio. No es dañino, pero no es
  un «primer recurso» real.

### Alternar el componente (`setComponentEnabledSetting` DISABLED → ENABLED)

- Con `PackageManager.DONT_KILL_APP`, el cambio de estado del componente no emite
  `ACTION_PACKAGE_CHANGED` en el momento. Lo apunta en `mPendingBroadcasts` y programa un único
  `SEND_PENDING_BROADCAST` con retraso: `BROADCAST_DELAY` = 1 s, o 10 s
  (`BROADCAST_DELAY_DURING_STARTUP`) si el sistema acaba de arrancar. Si ya hay uno programado, no
  programa otro. Sin `DONT_KILL_APP`, el broadcast sale enseguida y **mata la app**. [PMS,
  `setEnabledSettings`, ~l. 4062–4105; constantes ~l. 944–945]
- Por eso, dos llamadas seguidas (DISABLED y luego ENABLED, las dos con `DONT_KILL_APP`) se juntan en
  **un solo** `PACKAGE_CHANGED`, y cuando se emite el componente ya está habilitado de nuevo.
- `NotificationManagerService` escucha `ACTION_PACKAGE_CHANGED` y llama a
  `mListeners.onPackagesChanged(...)`. [NMS, ~l. 2028, 2696 y 10314] Si el paquete tiene servicios
  aprobados, ejecuta `rebindServices(false, USER_ALL)` («make sure we're still bound to any of our
  services who may have just upgraded»). [MS, `onPackagesChanged`, ~l. 1016–1053]
- `rebindServices(false, …)` solo desenlaza lo que ya no está aprobado y enlaza lo aprobado. Si el
  componente ya está enlazado, `registerServiceLocked` sale con «Not registering … is already bound».
  [MS, `rebindServices` ~l. 1454 y `registerServiceLocked` ~l. 1615–1619] → **Si el listener ya
  estaba conectado, el truco es inocuo. Si no lo estaba, lo vuelve a enlazar.** Esta es la vía real
  de re-enlace.
- **Riesgo (de AOSP):** antes de re-enlazar, `onPackagesChanged` ejecuta
  `trimApprovedListsForInvalidServices`. Esta función **quita la aprobación** de cualquier componente
  aprobado cuyo `getServiceInfo` no se resuelva. `getServiceInfo` no usa `MATCH_DISABLED_COMPONENTS`,
  así que no encuentra un componente deshabilitado. [MS, `trimApprovedListsForInvalidServices`,
  `componentHasBindPermission` y `getServiceInfo`]
  - Si el `PACKAGE_CHANGED` se procesa mientras el componente sigue DESHABILITADO, Daniel **pierde el
    acceso a notificaciones** y tiene que concederlo de nuevo. Puede pasar si las dos llamadas
    quedan separadas por el retraso del broadcast o si el proceso muere entre ellas.
  - Con las dos llamadas una detrás de otra (como en el plan) la ventana es de microsegundos frente
    al retraso de 1 s, así que el riesgo es mínimo pero existe.
- No verifiqué en AOSP si `ActivityManager` para el servicio al ver el componente deshabilitado
  durante esa ventana. Si lo hiciera, se re-enlazaría con el mismo `rebindServices`.

## 3. `POST_NOTIFICATIONS` con targetSdk 35 y el canal de importancia alta

- Desde Android 13, una app recién instalada tiene las **notificaciones desactivadas por defecto** y
  no puede publicar hasta que el usuario conceda `POST_NOTIFICATIONS`. [PERM, «Effects on
  newly-installed apps»]
- Con target 13 o superior (como el nuestro, 35), la app decide cuándo aparece el diálogo; el sistema
  no lo muestra solo. [PERM] Hay que declarar
  `<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>`. [PERM, «Declare the
  permission»]
- Hacen falta las dos cosas: el permiso **y** el canal. `IMPORTANCE_HIGH` es el nivel «Urgent»: «Makes
  a sound and appears as a heads-up notification». [CANALES, «Set the importance level»]
  `IMPORTANCE_MAX` está reservado al sistema; las apps llegan como mucho a `IMPORTANCE_HIGH`. [NM,
  `IMPORTANCE_MAX`]
- Una vez creado el canal, **la app ya no puede cambiar su importancia ni su comportamiento**; el
  usuario pasa a tener el control total. Solo se pueden cambiar el nombre y la descripción.
  [CANALES] → Si algún día hace falta otra importancia, se necesita **otro id de canal**.
- `NotificationManager.isNotificationListenerAccessGranted(ComponentName)` (API 27) comprueba el
  acceso de un listener propio. Para pedirlo se abre `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`.
  [NM]
- **Android 15, redacción de OTP:** Android impide a las apps «no confiables» que implementan un
  `NotificationListenerService` leer el contenido sin redactar de las notificaciones en las que
  detecta un código de un solo uso (OTP). [A15, «OTP Redaction»] Los avisos de movimientos del BNC no
  son OTP, pero llevan muchos dígitos («Ref:…»). **Hay que verificar en la Tarea 22** que el texto
  real llega completo y no como contenido redactado. Si llegara redactado, la captura quedaría
  «no reconocida», sin pérdida de datos, porque el texto crudo se guarda igual.

## 4. MIUI/HyperOS: autoinicio, ahorro de batería por app, bloqueo en Recientes y si se detectan

| Defensa | Ruta / intent | ¿Detectable? | Fuente |
|---|---|---|---|
| Autoinicio | *Seguridad → Permisos → Autoinicio* (MIUI). En MIUI 14: *Ajustes → Apps → la app → Permisos → Autoinicio en segundo plano*. Intent no oficial: `ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")`. | **No con API pública.** Solo con reflexión sobre `AppOpsManager.checkOpNoThrow` usando la operación privada de MIUI `10008` (`OP_AUTO_START`); es lo que hace la biblioteca XomaDev/MIUI-autostart, probada por sus autores hasta MIUI 14. Frágil y sin soporte en HyperOS confirmado. | [DKMA] «Autostart» y «Solution for devs»; [TERCEROS-MIUI] |
| Ahorro de batería por app | *Ajustes → Apps → la app → Ahorro de batería → Sin restricciones* (en [DKMA]: «Security → Battery → App Battery Saver → your app → No restriction»). | La exención de Doze sí es detectable: `PowerManager.isIgnoringBatteryOptimizations(pkg)`. La opción propia de MIUI («Sin restricciones») **no** tiene API pública; en la práctica, conceder la exención estándar suele mostrarse como «Sin restricciones», pero eso hay que **comprobarlo en la Tarea 22**. | [DOZE]; [DKMA] «App battery saver» |
| Bloqueo en Recientes | Abrir Recientes, mantener pulsada la app (o arrastrarla hacia abajo) y tocar el candado. | **No detectable.** | [DKMA] «App pinning / App locking» |
| «Optimizaciones de MIUI» (opciones de desarrollador) | *Opciones de desarrollador → Optimizaciones de MIUI*. Pueden romper las tareas en segundo plano. | No detectable. | [DKMA] «MIUI Optimizations» |

- El intent del autoinicio **no está documentado por Xiaomi** y puede cambiar entre versiones. Antes
  de lanzarlo hay que resolverlo (o envolver `startActivity` en `try/catch` de
  `ActivityNotFoundException`) y, si falla, mostrar las instrucciones manuales. [TERCEROS-MIUI] El
  plan (Tarea 21, `abrirAutoinicio` con `false` y aviso) ya lo hace.
- No encontré ninguna fuente primaria ni comunitaria fiable para un intent directo a la pantalla de
  ahorro de batería por app de MIUI (`com.miui.powerkeeper/...`). Lo robusto es
  `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` con `package:` y que Daniel entre en «Ahorro de
  batería». Así lo hace el *fallback* de la Tarea 21.

## 5. ¿Es aceptable `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` fuera de Play Store?

- **En la plataforma, sí.** `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` tiene `protectionLevel="normal"`:
  se concede al instalar, sin diálogo de permiso. [MANIFEST]
- Para usar `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, la app debe tener ese permiso, y
  el intent lleva el URI `package:<paquete>`. La documentación avisa de que «most applications should
  not use this». [SETTINGS]
- **La restricción es una política de Google Play**, no de Android: «Google Play policies prohibit apps
  from requesting direct exemption … unless the core function of the app is adversely affected».
  [DOZE, «Support for other use cases»] Una app que no se publica en Play no está sujeta a esa
  política. Además, capturar las notificaciones bancarias en segundo plano es justo la función
  principal que Doze rompería.
- La alternativa sin permiso es `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` (la lista general).
  [DOZE] → **Veredicto:** el plan (Tarea 21: permiso en el manifiesto, intent directo y
  `@SuppressLint("BatteryLife")`) es correcto para esta app.

## 6. Simular notificaciones: `adb shell cmd notification post`

- Uso (literal del código): `cmd notification post [flags] <tag> <text>`, con flags `-h|--help`,
  `-v|--verbose`, `-t|--title <text>`, `-i|--icon <iconspec>`, `-I|--large-icon <iconspec>`,
  `-S|--style <style> [styleargs]` y `-c|--content-intent <intentspec>`. Los estilos son `bigtext`,
  `bigpicture --picture <iconspec>`, `inbox --line <text> …`, `messaging --conversation <title>
  --message <who>:<text> …` y `media`. [SHELL, `NOTIFY_USAGE`]
- `<tag>` y `<text>` son **un argumento cada uno** (`getNextArg()`). El texto va a `setContentText`
  (`EXTRA_TEXT`). [SHELL, `doNotify` ~l. 667–674] `-S bigtext` sin `--bigText <texto>` solo aplica el
  estilo; el texto largo va con `--bigText`. [SHELL, ~l. 575 y 606–609]
- La publica el **paquete del llamador**, `com.android.shell` cuando se lanza por `adb shell`, en el
  canal `shell_cmd` («Shell command», `IMPORTANCE_DEFAULT`), con id fijo `2020` y el tag dado. [SHELL,
  constantes ~l. 113–116, `ensureChannel` y `enqueueNotificationWithTag`] → Coincide con
  `paquetesCapturables()` (debug añade `com.android.shell`). Volver a publicar **con el mismo tag**
  reemplaza la notificación y vuelve a disparar `onNotificationPosted`, así que sirve para probar la
  reemisión y la deduplicación de 24 h.
- Comando probado en su forma (el texto entre comillas simples dentro de la cadena remota, para que el
  shell del teléfono no expanda `***` ni parta el texto):

  ```bash
  "$ADB" shell "cmd notification post -t 'BNC' prueba1 'BNC Pago Movil Recibido Bs.1234,56 Telf.0412***9999 Dia:15/07/26-10:31 Ref:123456789 Llamar al 0500-2625000 si no realizo esta Operacion'"
  ```

- Conceder o quitar el acceso al listener sin pasar por la UI: `cmd notification allow_listener
  COMPONENT [user_id]` y `disallow_listener COMPONENT [user_id]`, con `COMPONENT` en formato
  `paquete/clase`. [SHELL, ~l. 65–66 y 216–240] Ejemplo:
  `adb shell cmd notification allow_listener com.cuentasclaras.app/com.cuentasclaras.app.capture.BncNotificationListener`.

---

## Impacto en las Tareas 19–21 (para sus implementadores)

1. **Tarea 19, `EstadoListener.reenlazarSiHaceFalta`:** `NotificationListenerService.requestRebind`
   **no re-enlaza** un listener desconectado porque murió el proceso; solo deshace un `requestUnbind`
   (§2). Se puede dejar porque es inocuo, pero la vía real es alternar el componente. Hay que mantener
   **las dos llamadas seguidas y las dos con `DONT_KILL_APP`**, sin nada que pueda suspender o lanzar
   entre ellas: si el `PACKAGE_CHANGED` llegara con el componente deshabilitado, AOSP quitaría la
   aprobación del listener (§2, riesgo). Si se toca, conviene corregir el comentario «Primer recurso:
   requestRebind».
2. **Tarea 19, latido:** con el componente ya enlazado, alternar es inocuo (`registerServiceLocked`
   no hace nada). Un latido «viejo» por una noche sin notificaciones solo provoca un re-enlace
   innecesario y sin efecto.
3. **Tarea 19, texto:** `cmd notification post -S bigtext` sin `--bigText` no rellena
   `EXTRA_BIG_TEXT`. El plan concatena `EXTRA_TITLE`, `EXTRA_TEXT` y `EXTRA_BIG_TEXT`: con `-t 'BNC'`
   el texto queda «BNC BNC Pago Movil…», que el parser reconoce igual porque la regex usa `find`. El
   comando del plan es válido tal cual.
4. **Tarea 19, canal:** como la importancia no se puede cambiar después (§3), el id de `Notificador.CANAL`
   debe crearse ya con `IMPORTANCE_HIGH` la primera vez. Si cambia, hay que usar otro id.
5. **Tarea 21:** «Autoinicio» y «Bloquear en Recientes» como «no detectable» es correcto: no hay API
   pública (§4). La batería se detecta con `isIgnoringBatteryOptimizations`, pero que MIUI muestre
   «Sin restricciones» al conceder la exención estándar hay que **verificarlo en la Tarea 22**.
6. **Tarea 22:**
   - verificar que la notificación real del BNC no llega redactada por la protección de OTP de
     Android 15 (§3);
   - verificar que tras `am force-stop` o tras matar desde Recientes el listener vuelve (§1) y con
     qué defensas activadas;
   - confirmar el intent del autoinicio en el HyperOS de Daniel (§4).
