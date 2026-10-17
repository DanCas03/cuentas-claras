package com.cuentasclaras.app.ui.pantallas.permisos

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavHostController
import com.cuentasclaras.app.CuentasApp
import com.cuentasclaras.app.R
import com.cuentasclaras.app.capture.PermisosXiaomi
import com.cuentasclaras.app.capture.parseNotificacionBnc
import com.cuentasclaras.app.data.db.TipoCaptura
import com.cuentasclaras.app.domain.Currency
import com.cuentasclaras.app.domain.formatMoney
import com.cuentasclaras.app.ui.Avisos
import com.cuentasclaras.app.ui.componentes.Banner
import com.cuentasclaras.app.ui.componentes.BotonPrincipal
import com.cuentasclaras.app.ui.componentes.BotonSecundario
import com.cuentasclaras.app.ui.componentes.CampoTexto
import com.cuentasclaras.app.ui.componentes.IndicadorEstado
import com.cuentasclaras.app.ui.componentes.Tarjeta
import com.cuentasclaras.app.ui.componentes.TipoBanner
import com.cuentasclaras.app.ui.componentes.visibleSobreElTeclado
import com.cuentasclaras.app.ui.theme.CuentasTema

/** Lo que se sabe de un ajuste del sistema. */
private enum class Estado(val texto: String) {
    CONCEDIDO("Concedido"),
    FALTA("Falta"),
    NO_DETECTABLE("No detectable"),
}

private fun estadoDe(concedido: Boolean) = if (concedido) Estado.CONCEDIDO else Estado.FALTA

/** Resultado de «Probar captura»: sin probar, reconocida (texto listo) o no reconocida. */
private sealed interface Prueba {
    data class Reconocida(val texto: String) : Prueba
    data object NoReconocida : Prueba
}

/**
 * Permisos de captura (spec §7.3): lista de verificación con las defensas de Xiaomi. Los estados detectables se
 * releen en cada `ON_RESUME` (al volver de un ajuste del sistema). «Probar captura» solo ejecuta el parser:
 * no guarda nada. No hay escrituras, así que no hay estado `guardando`. `nav` no se usa (el atrás del sistema
 * vuelve a Ajustes); se conserva por la firma común de las pantallas.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun PermisosPantalla(nav: NavHostController) {
    val ctx = LocalContext.current
    val listener = remember { (ctx.applicationContext as CuentasApp).container.estadoListener }
    val e = CuentasTema.espacios

    // V13: el primer frame ya muestra la lectura real (son consultas síncronas y baratas); antes nacían en `false`
    // y «Falta» parpadeaba unos 300 ms en permisos concedidos. `releer()` sigue en cada ON_RESUME.
    var acceso by remember { mutableStateOf(listener.accesoConcedido()) }
    var bateria by remember { mutableStateOf(PermisosXiaomi.bateriaSinRestricciones(ctx)) }
    var avisos by remember { mutableStateOf(PermisosXiaomi.notificacionesPermitidas(ctx)) }
    var faltaPermisoAvisos by remember { mutableStateOf(PermisosXiaomi.faltaPermisoDeAvisos(ctx)) }
    var avisoAutoinicio by remember { mutableStateOf(false) }
    var avisoPermisoDenegado by remember { mutableStateOf(false) }
    var textoPrueba by remember { mutableStateOf("") }
    var prueba by remember { mutableStateOf<Prueba?>(null) }

    fun releer() {
        acceso = listener.accesoConcedido()
        bateria = PermisosXiaomi.bateriaSinRestricciones(ctx)
        avisos = PermisosXiaomi.notificacionesPermitidas(ctx)
        faltaPermisoAvisos = PermisosXiaomi.faltaPermisoDeAvisos(ctx)
    }

    /** Las aperturas nunca lanzan: si ni el ajuste ni los detalles de la app se abren, se avisa. */
    fun abrirOAvisar(abierto: Boolean) {
        if (!abierto) Avisos.avisar("No se pudo abrir el ajuste. Búscalo en Ajustes → Apps → Cuentas Claras.")
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { releer() }
    // D5: al ir a un ajuste del sistema, «Probar captura» suelta el foco; si no, al volver reaparece el teclado.
    val foco = LocalFocusManager.current
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { foco.clearFocus() }

    val pedirAvisos = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        avisoPermisoDenegado = !concedido
        releer()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(horizontal = e.m, vertical = e.m),
        verticalArrangement = Arrangement.spacedBy(e.m),
    ) {
        item(key = "titulo") {
            Text(
                "Permisos de captura",
                style = CuentasTema.tipografia.titulo,
                color = CuentasTema.colores.texto,
                modifier = Modifier.semantics { heading() },
            )
        }
        item(key = "acceso") {
            Permiso(
                titulo = "Acceso a notificaciones",
                estado = estadoDe(acceso),
                ayuda = "Permite que la app lea los avisos del BNC. Busca Cuentas Claras en la lista y actívala.",
            ) {
                BotonSecundario(
                    "Abrir ajuste",
                    onClick = { abrirOAvisar(PermisosXiaomi.abrirAccesoNotificaciones(ctx)) },
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Abrir ajuste de acceso a notificaciones" },
                )
            }
        }
        item(key = "bateria") {
            Permiso(
                titulo = "Batería sin restricciones",
                estado = estadoDe(bateria),
                ayuda = "En Xiaomi elige Sin restricciones en Ahorro de batería.",
            ) {
                BotonSecundario(
                    "Abrir ajuste",
                    onClick = { abrirOAvisar(PermisosXiaomi.abrirBateria(ctx)) },
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Abrir ajuste de batería" },
                )
            }
        }
        item(key = "autoinicio") {
            Permiso(
                titulo = "Autoinicio (Xiaomi)",
                estado = Estado.NO_DETECTABLE,
                ayuda = "Deja que la app arranque sola si el sistema la cierra. La app no puede comprobarlo: revísalo tú.",
            ) {
                BotonSecundario(
                    "Abrir ajuste",
                    onClick = { avisoAutoinicio = !PermisosXiaomi.abrirAutoinicio(ctx) },
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Abrir ajuste de autoinicio" },
                )
                if (avisoAutoinicio) {
                    Banner("Ábrelo a mano: Seguridad → Permisos → Autoinicio → Cuentas Claras", TipoBanner.AVISO)
                }
            }
        }
        item(key = "recientes") {
            Permiso(
                titulo = "Bloquear la app en Recientes",
                estado = Estado.NO_DETECTABLE,
                ayuda = "Abre Recientes, mantén pulsada Cuentas Claras y toca el candado. Así el sistema no la cierra al limpiar.",
            ) {
                Image(
                    painterResource(R.drawable.esquema_bloquear_recientes),
                    contentDescription = "Esquema: la tarjeta de Cuentas Claras en Recientes con un candado",
                    colorFilter = ColorFilter.tint(CuentasTema.colores.textoSecundario),
                    modifier = Modifier.fillMaxWidth(0.6f).align(Alignment.CenterHorizontally),
                )
            }
        }
        item(key = "avisos") {
            Permiso(
                titulo = "Permiso para avisarte",
                estado = estadoDe(avisos),
                ayuda = when {
                    // Permiso concedido (o no hace falta) pero la app o el canal están apagados en el sistema.
                    !avisos && !faltaPermisoAvisos ->
                        "Las notificaciones de la app o el canal «Capturas BNC» están apagados. Actívalos en el ajuste."
                    Build.VERSION.SDK_INT >= 33 -> "Para mostrar «Toca para registrar» cuando llegue un aviso del BNC."
                    else -> "En esta versión de Android no hace falta pedirlo."
                },
            ) {
                if (faltaPermisoAvisos) {
                    BotonSecundario(
                        "Permitir avisos",
                        onClick = { pedirAvisos.launch(Manifest.permission.POST_NOTIFICATIONS) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (avisoPermisoDenegado) {
                        Banner(
                            "Si no aparece la ventana, actívalo en Ajustes → Apps → Cuentas Claras → Notificaciones.",
                            TipoBanner.AVISO,
                        )
                    }
                } else if (!avisos) {
                    BotonSecundario(
                        "Abrir ajuste",
                        onClick = { abrirOAvisar(PermisosXiaomi.abrirAjusteNotificaciones(ctx)) },
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Abrir ajuste de notificaciones de la app" },
                    )
                }
            }
        }
        item(key = "probar") {
            Tarjeta(titulo = "Probar captura") {
                Column(verticalArrangement = Arrangement.spacedBy(e.s)) {
                    // N1: con el campo enfocado, el campo y «Probar» se mantienen juntos sobre el teclado (la lista se
                    // encoge con `imePadding()`; la raíz ya consumió la barra inferior).
                    Column(Modifier.visibleSobreElTeclado(), verticalArrangement = Arrangement.spacedBy(e.s)) {
                        CampoTexto(
                            valor = textoPrueba,
                            onCambio = { textoPrueba = it; prueba = null },
                            etiqueta = "Pega aquí una notificación del BNC",
                            singleLine = false,
                            minLines = 3,
                        )
                        BotonPrincipal(
                            "Probar",
                            onClick = {
                                foco.clearFocus()
                                prueba = parseNotificacionBnc(textoPrueba)?.let {
                                    val tipo = if (it.tipo == TipoCaptura.INGRESO) "Ingreso" else "Gasto"
                                    Prueba.Reconocida("Reconocida: $tipo · ${formatMoney(it.montoMinor, Currency.VES)} · ${it.nota}")
                                } ?: Prueba.NoReconocida
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    when (val p = prueba) {
                        null -> Unit
                        Prueba.NoReconocida -> Banner("No reconocida", TipoBanner.ERROR)
                        is Prueba.Reconocida -> Banner(p.texto, TipoBanner.OK)
                    }
                    Text(
                        "Solo comprueba el texto: no se guarda nada.",
                        style = CuentasTema.tipografia.etiqueta,
                        color = CuentasTema.colores.textoSecundario,
                    )
                }
            }
        }
    }
}

/**
 * Tarjeta de un ajuste: el estado va junto al título (o debajo, si el título no cabe a su lado) con texto e ícono,
 * no solo color (V12): «Concedido» con tinte `ingreso` e `ic_ok`, «Falta» con tinte `aviso` e `ic_aviso`, «No
 * detectable» neutro. Ya no usa el chip activo en `primario`, que competía con la acción principal.
 */
@Composable
private fun Permiso(
    titulo: String,
    estado: Estado,
    ayuda: String,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    val tipo = when (estado) {
        Estado.CONCEDIDO -> TipoBanner.OK
        Estado.FALTA -> TipoBanner.AVISO
        Estado.NO_DETECTABLE -> null
    }
    Tarjeta(titulo = titulo, accion = { IndicadorEstado(estado.texto, tipo) }) {
        Column(verticalArrangement = Arrangement.spacedBy(CuentasTema.espacios.s)) {
            Text(ayuda, style = CuentasTema.tipografia.cuerpo, color = CuentasTema.colores.textoSecundario)
            contenido()
        }
    }
}
