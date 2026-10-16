package com.cuentasclaras.app.capture

import com.cuentasclaras.app.BuildConfig

/** Paquete de la app del BNC (sale de una captura de pantalla de Daniel). Constante ÚNICA:
 *  la Tarea 22 lo confirma con `adb shell pm list packages | grep -i bnc`. */
const val PAQUETE_BNC = "bnc.bncnet.mobile2"

/** Solo en la variante `verif` se escucha también a `com.android.shell`, para simular notificaciones con
 *  `adb shell cmd notification post`. En `debug` (el APK diario de Daniel) y en release, solo el BNC: si no,
 *  cualquier notificación publicada por shell en su teléfono crearía una captura. */
fun paquetesCapturables(): Set<String> =
    if (BuildConfig.BUILD_TYPE == "verif") setOf(PAQUETE_BNC, "com.android.shell") else setOf(PAQUETE_BNC)
