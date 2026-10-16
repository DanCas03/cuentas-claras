package com.cuentasclaras.app.ui.pantallas.nuevo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuentasclaras.app.capture.CapturaRepo
import com.cuentasclaras.app.data.CUENTA_BNC
import com.cuentasclaras.app.data.CuentasRepo
import com.cuentasclaras.app.data.db.EstadoCaptura
import com.cuentasclaras.app.data.db.TipoCaptura
import com.cuentasclaras.app.domain.Account
import com.cuentasclaras.app.domain.Category
import com.cuentasclaras.app.domain.CategoryKind
import com.cuentasclaras.app.domain.TxType
import com.cuentasclaras.app.domain.buildManualTx
import com.cuentasclaras.app.domain.fechaLocalDeInstante
import com.cuentasclaras.app.domain.montoEditable
import com.cuentasclaras.app.domain.parseVenezuelanAmount
import com.cuentasclaras.app.domain.todayIso
import com.cuentasclaras.app.ui.Avisos
import com.cuentasclaras.app.ui.Ocupacion
import com.cuentasclaras.app.ui.escribir
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Modo { GASTO, INGRESO, TRANSFERIR, AJUSTAR }

/** Teclado propio (Teclado.tsx + onTecla de NuevoPage.tsx): coma única, máximo 2 decimales. */
fun aplicarTecla(entrada: String, tecla: String): String = when (tecla) {
    "⌫" -> entrada.dropLast(1)
    "," -> if (entrada.isEmpty() || entrada.contains(',')) entrada else "$entrada,"
    else -> {
        val partes = entrada.split(',')
        if (partes.size == 2 && partes[1].length >= 2) entrada else entrada + tecla
    }
}

class NuevoViewModel(
    private val repo: CuentasRepo,
    private val capturas: CapturaRepo,
    args: SavedStateHandle,
) : ViewModel() {
    data class Formulario(
        val modo: Modo,
        val entrada: String,
        val nota: String,
        val cuentaId: String?,
        val guardando: Boolean = false,
        /** Captura del BNC que este formulario registra (null = movimiento manual). */
        val capturaId: String? = null,
        /** La captura parece ya importada del TXT del BNC (misma referencia, monto y sentido): solo se avisa. */
        val pareceImportada: Boolean = false,
    )

    data class Estado(
        val formulario: Formulario,
        val cuentas: List<Account> = emptyList(),
        val cuenta: Account? = null,
        val categorias: List<Category> = emptyList(),
        /** Céntimos del monto tecleado; null si no es un monto válido. */
        val minor: Long? = null,
    )

    private val formulario = MutableStateFlow(
        Formulario(
            modo = if (args.get<String>("tipo") == "ingreso") Modo.INGRESO else Modo.GASTO,
            // Prefill de captura: solo al crear el VM; lo que el usuario edite después manda.
            entrada = args.get<String>("monto")?.takeIf { it.matches(Regex("^\\d+$")) }
                ?.toLongOrNull()?.let { montoEditable(it) } ?: "",
            nota = args.get<String>("nota") ?: "",
            cuentaId = args.get<String>("cuenta")?.takeIf { it.isNotEmpty() },
            capturaId = args.get<String>("capturaId")?.takeIf { it.isNotEmpty() },
        ),
    )

    private val ocupacion = Ocupacion({ formulario.value.guardando }) { g -> formulario.update { it.copy(guardando = g) } }

    private val _salir = Channel<Unit>(Channel.BUFFERED)
    val salir: Flow<Unit> = _salir.receiveAsFlow()

    val estado: StateFlow<Estado> = combine(formulario, repo.cuentas, repo.categorias) { f, cuentas, categorias ->
        val activas = cuentas.filter { !it.archived }
        val kind = if (f.modo == Modo.INGRESO) CategoryKind.INCOME else CategoryKind.EXPENSE
        Estado(
            formulario = f,
            cuentas = activas,
            cuenta = activas.firstOrNull { it.id == f.cuentaId } ?: activas.firstOrNull(),
            categorias = categorias.filter { it.kind == kind },
            minor = parseVenezuelanAmount(f.entrada.removeSuffix(",")),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Estado(formulario.value))

    init {
        formulario.value.capturaId?.let { cargarCaptura(it) }
        // lastAccountId solo si la ruta no trajo cuenta (igual que NuevoPage.tsx).
        viewModelScope.launch {
            val ultima = repo.settings.first().lastAccountId
            formulario.update { it.copy(cuentaId = it.cuentaId ?: ultima) }
        }
    }

    /**
     * Prellena el formulario desde la captura. La pantalla lo llama cuando cambia el `capturaId` de la ruta:
     * con `launchSingleTop`, tocar la notificación de la captura B estando en Nuevo con la A reutiliza este mismo
     * ViewModel con argumentos nuevos, y el `SavedStateHandle` ya no se actualiza.
     */
    fun abrirCaptura(id: String?) {
        if (id.isNullOrEmpty() || id == formulario.value.capturaId) return
        formulario.update { it.copy(capturaId = id, pareceImportada = false) }
        cargarCaptura(id)
    }

    private fun cargarCaptura(id: String) {
        viewModelScope.launch {
            try {
                val c = capturas.porId(id)
                // Otra captura pudo abrirse mientras tanto: esa gana.
                if (formulario.value.capturaId != id) return@launch
                when {
                    c == null -> Avisos.avisar("La captura ya no existe")
                    c.estado != EstadoCaptura.PENDIENTE -> {
                        Avisos.avisar(
                            if (c.estado == EstadoCaptura.REGISTRADA) "Esta notificación ya se registró" else "Esta notificación se descartó",
                        )
                        _salir.send(Unit)
                    }
                    c.reconocida && c.montoMinor != null -> {
                        val importada = capturas.pareceImportada(c)
                        if (formulario.value.capturaId != id) return@launch
                        formulario.update {
                            it.copy(
                                modo = if (c.tipo == TipoCaptura.INGRESO) Modo.INGRESO else Modo.GASTO,
                                entrada = montoEditable(c.montoMinor),
                                nota = c.nota ?: "",
                                cuentaId = CUENTA_BNC,
                                pareceImportada = importada,
                            )
                        }
                    }
                    else -> formulario.update { it.copy(modo = Modo.GASTO, entrada = "", nota = c.textoCrudo.take(200)) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Avisos.avisar(e.message ?: "No se pudo leer la captura")
            }
        }
    }

    /**
     * Descarta la captura de este formulario y sale a Inicio (D19: tras salir, `guardando` se queda en true). Si ya
     * se registró o descartó desde otra pantalla, `CapturaRepo.descartar` lanza y se avisa sin salir.
     */
    fun descartarCaptura() {
        val id = formulario.value.capturaId ?: return
        escribir(ocupacion, "No se pudo descartar", reponerAlExito = false) {
            capturas.descartar(id)
            Avisos.avisar("Notificación descartada")
            _salir.trySend(Unit)
        }
    }

    fun cambiarModo(m: Modo) = formulario.update { it.copy(modo = m) }
    fun tecla(t: String) = formulario.update { it.copy(entrada = aplicarTecla(it.entrada, t)) }
    fun cambiarNota(n: String) = formulario.update { it.copy(nota = n) }
    fun cambiarCuenta(id: String) = formulario.update { it.copy(cuentaId = id) }

    /**
     * Tocar una categoría guarda (≤ 3 toques). D19: tras guardar bien, `guardando` se queda en true (la pantalla
     * sigue viva ~150 ms mientras se cierra y un segundo toque rápido no debe crear un movimiento duplicado).
     *
     * Desde una captura, la fecha es el día local en que llegó la notificación (un pago de las 23:50 registrado al
     * día siguiente queda en su día y en su mes) y el registro es todo-o-nada en `CapturaRepo.registrarConTx`.
     */
    fun guardar(categoryId: String) {
        val e = estado.value
        val f = formulario.value
        if (ocupacion.activa) return
        val cuenta = e.cuenta ?: return
        val minor = e.minor
        if (minor == null || minor <= 0) {
            Avisos.avisar("Escribe el monto")
            return
        }
        escribir(ocupacion, "No se pudo guardar", reponerAlExito = false) {
            val captura = f.capturaId
            val fecha = captura?.let { capturas.porId(it) }?.let { fechaLocalDeInstante(it.recibidaIso) } ?: todayIso()
            val tx = buildManualTx(
                type = if (f.modo == Modo.GASTO) TxType.EXPENSE else TxType.INCOME,
                accountId = cuenta.id,
                amountMinor = minor,
                categoryId = categoryId,
                dateIso = fecha,
                note = f.nota.trim().takeIf { it.isNotEmpty() },
            )
            if (captura == null) repo.agregarTx(tx) else capturas.registrarConTx(captura, tx)
            // El movimiento ya está guardado: si falla solo recordar la cuenta, se avisa y se sale igual (no reintentar).
            try {
                repo.cambiarSettings { it.copy(lastAccountId = cuenta.id) }
                Avisos.avisar("Guardado ✓")
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Avisos.avisar(ex.message ?: "No se pudo guardar")
            }
            _salir.trySend(Unit)
        }
    }
}
