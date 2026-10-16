package com.cuentasclaras.app.ui.pantallas.nuevo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuentasclaras.app.data.CuentasRepo
import com.cuentasclaras.app.domain.Account
import com.cuentasclaras.app.domain.Category
import com.cuentasclaras.app.domain.CategoryKind
import com.cuentasclaras.app.domain.TxType
import com.cuentasclaras.app.domain.buildManualTx
import com.cuentasclaras.app.domain.montoEditable
import com.cuentasclaras.app.domain.parseVenezuelanAmount
import com.cuentasclaras.app.domain.todayIso
import com.cuentasclaras.app.ui.Avisos
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

class NuevoViewModel(private val repo: CuentasRepo, args: SavedStateHandle) : ViewModel() {
    data class Formulario(
        val modo: Modo,
        val entrada: String,
        val nota: String,
        val cuentaId: String?,
        val guardando: Boolean = false,
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
        ),
    )

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
        // lastAccountId solo si la ruta no trajo cuenta (igual que NuevoPage.tsx).
        viewModelScope.launch {
            val ultima = repo.settings.first().lastAccountId
            formulario.update { it.copy(cuentaId = it.cuentaId ?: ultima) }
        }
    }

    fun cambiarModo(m: Modo) = formulario.update { it.copy(modo = m) }
    fun tecla(t: String) = formulario.update { it.copy(entrada = aplicarTecla(it.entrada, t)) }
    fun cambiarNota(n: String) = formulario.update { it.copy(nota = n) }
    fun cambiarCuenta(id: String) = formulario.update { it.copy(cuentaId = id) }

    /** Tocar una categoría guarda (≤ 3 toques). */
    fun guardar(categoryId: String) {
        val e = estado.value
        val f = formulario.value
        if (f.guardando) return
        val cuenta = e.cuenta ?: return
        val minor = e.minor
        if (minor == null || minor <= 0) {
            Avisos.avisar("Escribe el monto")
            return
        }
        formulario.update { it.copy(guardando = true) }
        viewModelScope.launch {
            // Tras guardar bien, `guardando` se queda en true: la pantalla sigue viva ~150 ms mientras se
            // cierra y un segundo toque rápido no debe crear un movimiento duplicado.
            var guardado = false
            try {
                val tx = buildManualTx(
                    type = if (f.modo == Modo.GASTO) TxType.EXPENSE else TxType.INCOME,
                    accountId = cuenta.id,
                    amountMinor = minor,
                    categoryId = categoryId,
                    dateIso = todayIso(),
                    note = f.nota.trim().takeIf { it.isNotEmpty() },
                )
                repo.agregarTx(tx)
                guardado = true
                repo.cambiarSettings { it.copy(lastAccountId = cuenta.id) }
                Avisos.avisar("Guardado ✓")
                _salir.send(Unit)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Avisos.avisar(ex.message ?: "No se pudo guardar")
                // Si el movimiento ya se guardó (falló solo recordar la cuenta), se sale igual: no reintentar.
                if (guardado) _salir.trySend(Unit)
            } finally {
                if (!guardado) formulario.update { it.copy(guardando = false) }
            }
        }
    }
}
