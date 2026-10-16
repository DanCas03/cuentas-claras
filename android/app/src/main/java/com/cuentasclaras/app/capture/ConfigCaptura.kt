package com.cuentasclaras.app.capture

import android.content.Context
import android.content.pm.ApplicationInfo

/** Paquete de la app del BNC (sale de una captura de pantalla de Daniel). Constante ÚNICA:
 *  la Tarea 22 lo confirma con `adb shell pm list packages | grep -i bnc`. */
const val PAQUETE_BNC = "bnc.bncnet.mobile2"

/** En builds depurables también se escucha a `com.android.shell` para poder simular
 *  notificaciones con `adb shell cmd notification post`. En release, solo el BNC. */
fun paquetesCapturables(context: Context): Set<String> {
    val depurable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    return if (depurable) setOf(PAQUETE_BNC, "com.android.shell") else setOf(PAQUETE_BNC)
}
