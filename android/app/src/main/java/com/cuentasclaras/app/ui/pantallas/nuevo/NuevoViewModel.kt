package com.cuentasclaras.app.ui.pantallas.nuevo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.cuentasclaras.app.capture.CapturaRepo
import com.cuentasclaras.app.data.CUENTA_BNC
import com.cuentasclaras.app.data.CuentasRepo
import com.cuentasclaras.app.data.Instantanea
import com.cuentasclaras.app.data.db.EstadoCaptura
import com.cuentasclaras.app.data.db.TipoCaptura
import com.cuentasclaras.app.data.ops.asignarEvento
import com.cuentasclaras.app.domain.Account
import com.cuentasclaras.app.domain.CalEvent
import com.cuentasclaras.app.domain.Category
import com.cuentasclaras.app.domain.CategoryKind
import com.cuentasclaras.app.domain.CtxSugerencia
import com.cuentasclaras.app.domain.EstadoOcurrencia
import com.cuentasclaras.app.domain.Ocurrencia
import com.cuentasclaras.app.domain.TxParaSugerir
import com.cuentasclaras.app.domain.TxType
import com.cuentasclaras.app.domain.buildManualTx
import com.cuentasclaras.app.domain.candidatasParaTx
import com.cuentasclaras.app.domain.esFechaIso
import com.cuentasclaras.app.domain.fechaLocalDeInstante
import com.cuentasclaras.app.domain.kindDeEvento
import com.cuentasclaras.app.domain.montoEditable
import com.cuentasclaras.app.domain.parseVenezuelanAmount
import com.cuentasclaras.app.domain.sugerirOcurrencia
import com.cuentasclaras.app.domain.todayIso
import com.cuentasclaras.app.ui.Avisos
import com.cuentasclaras.app.ui.Ocupacion
import com.cuentasclaras.app.ui.escribir
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
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

/** Máximo de candidatas que se pintan como chips (sin contar la activa ni la sugerida), como `NuevoPage.tsx`. */
private const val MAX_CHIPS = 4

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
        /** Día local en que llegó la captura: fecha del movimiento y de las candidatas a evento (null = hoy). */
        val fechaCaptura: String? = null,
    )

    /** Chip de una ocurrencia del calendario a la que se puede asignar el movimiento (T30). */
    data class ChipEvento(
        val o: Ocurrencia,
        val evento: CalEvent,
        val vencida: Boolean,
        /** Emoji de la categoría del evento, o «📅» si no existe. */
        val icono: String,
        /** Elegida: tocar una categoría guarda Y asigna; tocar el chip la desmarca. */
        val activo: Boolean,
        /** Propuesta tras una captura o un `?monto`: no está elegida, un toque guarda y asigna. */
        val sugerido: Boolean,
    )

    data class Estado(
        val formulario: Formulario,
        val cuentas: List<Account> = emptyList(),
        val cuenta: Account? = null,
        val categorias: List<Category> = emptyList(),
        /** Céntimos del monto tecleado; null si no es un monto válido. */
        val minor: Long? = null,
        /**
         * La activa o la sugerida primero; luego hasta 4 candidatas. Vacía = sin fila de chips (el camino de
         * ≤ 3 toques queda intacto). Solo en Gasto e Ingreso.
         */
        val chips: List<ChipEvento> = emptyList(),
    )

    /** Ocurrencia elegida (chip activo) y ocurrencia ofrecida (chip sugerido). */
    private data class SeleccionEvento(val activo: Ocurrencia? = null, val sugerida: Ocurrencia? = null)

    private val hoy = todayIso()
    private val argEventId: String? = args.get<String>("eventId")?.takeIf { it.isNotEmpty() }
    private val argEventDate: String? = args.get<String>("eventDate")?.takeIf { esFechaIso(it) }
    private val argMonto: Long? = args.get<String>("monto")?.takeIf { it.matches(Regex("^\\d+$")) }?.toLongOrNull()

    private val formulario = MutableStateFlow(
        Formulario(
            modo = if (args.get<String>("tipo") == "ingreso") Modo.INGRESO else Modo.GASTO,
            // Prefill de captura: solo al crear el VM; lo que el usuario edite después manda.
            entrada = argMonto?.let { montoEditable(it) } ?: "",
            nota = args.get<String>("nota") ?: "",
            cuentaId = args.get<String>("cuenta")?.takeIf { it.isNotEmpty() },
            capturaId = args.get<String>("capturaId")?.takeIf { it.isNotEmpty() },
        ),
    )
    private val seleccion = MutableStateFlow(SeleccionEvento())

    private val ocupacion = Ocupacion({ formulario.value.guardando }) { g -> formulario.update { it.copy(guardando = g) } }

    private val _salir = Channel<Unit>(Channel.BUFFERED)
    val salir: Flow<Unit> = _salir.receiveAsFlow()

    val estado: StateFlow<Estado> = combine(formulario, repo.instantanea, seleccion) { f, i, s -> calcular(f, i, s) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Estado(formulario.value))

    private fun kindDe(m: Modo) = if (m == Modo.INGRESO) CategoryKind.INCOME else CategoryKind.EXPENSE

    private fun calcular(f: Formulario, i: Instantanea, s: SeleccionEvento): Estado {
        val activas = i.cuentas.filter { !it.archived }
        val cuenta = activas.firstOrNull { it.id == f.cuentaId } ?: activas.firstOrNull()
        val minor = parseVenezuelanAmount(f.entrada.removeSuffix(","))
        val chips =
            if (cuenta == null || (f.modo != Modo.GASTO && f.modo != Modo.INGRESO)) emptyList()
            else chipsPara(i, kindDe(f.modo), f.fechaCaptura ?: hoy, cuenta.id, minor, s)
        return Estado(
            formulario = f,
            cuentas = activas,
            cuenta = cuenta,
            categorias = i.categorias.filter { it.kind == kindDe(f.modo) },
            minor = minor,
            chips = chips,
        )
    }

    /**
     * Contexto de sugerencia con las transacciones CRUDAS asignadas a algún evento (E18; la web usa
     * `where('eventId').above('')`). Los eventos con fechas corruptas (un respaldo no las valida) se ignoran para
     * que Nuevo no lance al derivar ocurrencias.
     */
    private fun ctx(i: Instantanea) = CtxSugerencia(
        eventos = i.eventos.filter { esFechaIso(it.startDate) && (it.endDate == null || esFechaIso(it.endDate)) },
        txs = i.txs.filter { it.eventId != null },
        accounts = i.cuentas,
        categories = i.categorias,
        hoy = hoy,
    )

    /** Chips de `NuevoPage.tsx`: candidatas (máx. 4) + la sugerida y la activa fijadas; la activa (o sugerida) primero. */
    private fun chipsPara(
        i: Instantanea,
        kind: CategoryKind,
        fecha: String,
        cuentaId: String,
        minor: Long?,
        s: SeleccionEvento,
    ): List<ChipEvento> {
        if (i.eventos.isEmpty()) return emptyList()
        val c = ctx(i)
        val lista = candidatasParaTx(TxParaSugerir(kind, fecha, cuentaId, minor ?: 0L), c)
            .take(MAX_CHIPS)
            .map { Triple(it.ocurrencia.ocurrencia, it.ocurrencia.evento, it.ocurrencia.estado == EstadoOcurrencia.VENCIDA) }
            .toMutableList()
        for (fijada in listOfNotNull(s.sugerida, s.activo)) {
            if (lista.any { it.first == fijada }) continue
            val evento = c.eventos.firstOrNull { it.id == fijada.eventId } ?: continue
            // Solo si va en la dirección del modo: un cobro elegido no se ofrece en Gasto (y no se asigna).
            if (kindDeEvento(evento, i.categorias) != kind) continue
            lista.add(0, Triple(fijada, evento, fijada.date < hoy))
        }
        val primera = s.activo ?: s.sugerida
        if (primera != null) {
            val idx = lista.indexOfFirst { it.first == primera }
            if (idx > 0) lista.add(0, lista.removeAt(idx))
        }
        return lista.map { (o, evento, vencida) ->
            ChipEvento(
                o = o,
                evento = evento,
                vencida = vencida,
                icono = i.categorias.firstOrNull { it.id == evento.categoryId }?.icon ?: "📅",
                activo = o == s.activo,
                sugerido = o == s.sugerida && o != s.activo,
            )
        }
    }

    init {
        formulario.value.capturaId?.let { cargarCaptura(it) }
        // lastAccountId solo si la ruta no trajo cuenta (igual que NuevoPage.tsx). Después, el prefill de evento.
        viewModelScope.launch {
            try {
                val ultima = repo.settings.first().lastAccountId
                formulario.update { it.copy(cuentaId = it.cuentaId ?: ultima) }
                if (formulario.value.capturaId == null) prefillEvento()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Avisos.avisar(e.message ?: "No se pudo leer el evento")
            }
        }
    }

    /**
     * Deep link `eventId&eventDate` (Esta semana / Calendario): marca la ocurrencia como activa y toma el modo, la
     * cuenta y (si la ruta no trajo `monto`) el monto esperado del evento. Sin evento, con `?monto`, se OFRECE la
     * sugerencia (no se activa). Una sola vez, como la web.
     */
    private suspend fun prefillEvento() {
        val i = repo.instantanea.first()
        val eventId = argEventId
        val fecha = argEventDate
        if (eventId != null && fecha != null) {
            val evento = i.eventos.firstOrNull { it.id == eventId }
            val kind = evento?.let { kindDeEvento(it, i.categorias) }
            if (evento != null && kind != null) {
                seleccion.value = SeleccionEvento(activo = Ocurrencia(evento.id, fecha))
                formulario.update { f ->
                    f.copy(
                        modo = if (kind == CategoryKind.INCOME) Modo.INGRESO else Modo.GASTO,
                        cuentaId = evento.accountId?.takeIf { id -> i.cuentas.any { it.id == id && !it.archived } } ?: f.cuentaId,
                        entrada = if (argMonto == null && evento.amountMinor != null) montoEditable(evento.amountMinor) else f.entrada,
                    )
                }
                return
            }
        }
        if (argMonto != null) ofrecerSugerencia(i)
    }

    /** Tras una captura (o con `?monto`): la ocurrencia que `sugerirOcurrencia` propone queda como chip «sugerido». */
    private fun ofrecerSugerencia(i: Instantanea) {
        val f = formulario.value
        if (f.modo != Modo.GASTO && f.modo != Modo.INGRESO) return
        val monto = parseVenezuelanAmount(f.entrada.removeSuffix(",")) ?: return
        val activas = i.cuentas.filter { !it.archived }
        val cuentaId = (activas.firstOrNull { it.id == f.cuentaId } ?: activas.firstOrNull())?.id ?: return
        if (monto <= 0 || i.eventos.isEmpty()) return
        val o = sugerirOcurrencia(TxParaSugerir(kindDe(f.modo), f.fechaCaptura ?: hoy, cuentaId, monto), ctx(i))
        seleccion.update { it.copy(sugerida = o?.ocurrencia) }
    }

    /**
     * Prellena el formulario desde la captura. La pantalla lo llama cuando cambia el `capturaId` de la ruta:
     * con `launchSingleTop`, tocar la notificación de la captura B estando en Nuevo con la A reutiliza este mismo
     * ViewModel con argumentos nuevos, y el `SavedStateHandle` ya no se actualiza.
     */
    fun abrirCaptura(id: String?) {
        if (id.isNullOrEmpty() || id == formulario.value.capturaId) return
        formulario.update { it.copy(capturaId = id, pareceImportada = false, fechaCaptura = null) }
        // La elección de evento era de la captura anterior.
        seleccion.value = SeleccionEvento()
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
                                fechaCaptura = fechaLocalDeInstante(c.recibidaIso),
                            )
                        }
                        // Sugerencia tras la captura (NuevoPage.tsx): se ofrece, no se activa.
                        val i = repo.instantanea.first()
                        if (formulario.value.capturaId == id) ofrecerSugerencia(i)
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
     * Un toque en un chip de evento (`NuevoPage.tsx`): el activo se desmarca; con monto, guarda con la categoría del
     * evento y asigna; sin monto, marca el chip y pide el monto (tocar después una categoría guarda y asigna).
     */
    fun tocarChip(c: ChipEvento) {
        if (ocupacion.activa) return
        if (c.activo) {
            seleccion.update { it.copy(activo = null) }
            return
        }
        val minor = parseVenezuelanAmount(formulario.value.entrada.removeSuffix(","))
        if (minor != null && minor > 0) {
            guardarCon(c.evento.categoryId, c.o, c.evento)
        } else {
            seleccion.update { it.copy(activo = c.o) }
            Avisos.avisar("Escribe el monto")
        }
    }

    /**
     * Tocar una categoría guarda (≤ 3 toques); si hay un chip activo, además asigna el movimiento a su ocurrencia.
     * D19: tras guardar bien, `guardando` se queda en true (la pantalla sigue viva ~150 ms mientras se cierra y un
     * segundo toque rápido no debe crear un movimiento duplicado).
     */
    fun guardar(categoryId: String) {
        val activo = estado.value.chips.firstOrNull { it.activo }
        guardarCon(categoryId, activo?.o, activo?.evento)
    }

    /**
     * Desde una captura, la fecha es el día local en que llegó la notificación (un pago de las 23:50 registrado al
     * día siguiente queda en su día y en su mes) y el registro es todo-o-nada en `CapturaRepo.registrarConTx`.
     *
     * Con ocurrencia (restricción 8): el alta y la asignación (`asignarEvento` de T6, que valida la dirección y la
     * fecha) van en UNA transacción. La categoría tocada manda sobre la del evento (`NuevoPage.tsx:152`); la
     * asignación NO altera monto, fecha, cuenta ni tipo.
     */
    private fun guardarCon(categoryId: String, o: Ocurrencia?, evento: CalEvent?) {
        val e = estado.value
        val f = formulario.value
        if (ocupacion.activa) return
        val cuenta = e.cuenta ?: return
        val minor = parseVenezuelanAmount(f.entrada.removeSuffix(","))
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
            val asignar: suspend () -> Unit = {
                if (o != null && evento != null) {
                    asignarEvento(repo.db, tx.id, o)
                    if (categoryId != evento.categoryId) {
                        repo.db.transactions().asignarEvento(tx.id, o.eventId, o.date, categoryId)
                    }
                }
            }
            if (captura != null) {
                capturas.registrarConTx(captura, tx, ademas = asignar)
            } else if (o != null && evento != null) {
                repo.db.withTransaction {
                    repo.agregarTx(tx)
                    asignar()
                }
            } else {
                repo.agregarTx(tx)
            }
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
