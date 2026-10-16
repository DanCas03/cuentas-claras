package com.cuentasclaras.app.ui.pantallas.importar

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuentasclaras.app.data.CUENTA_BNC
import com.cuentasclaras.app.data.CuentasRepo
import com.cuentasclaras.app.data.db.toDomain
import com.cuentasclaras.app.data.ops.ParcheCuentaBnc
import com.cuentasclaras.app.data.ops.commitImport
import com.cuentasclaras.app.domain.Account
import com.cuentasclaras.app.domain.Category
import com.cuentasclaras.app.domain.Transaction
import com.cuentasclaras.app.domain.accountBalanceMinor
import com.cuentasclaras.app.domain.buildAdjustment
import com.cuentasclaras.app.domain.buildRule
import com.cuentasclaras.app.domain.todayIso
import com.cuentasclaras.app.importar.bnc.BncDraft
import com.cuentasclaras.app.importar.bnc.BncParseResult
import com.cuentasclaras.app.importar.bnc.GapStatus
import com.cuentasclaras.app.importar.bnc.bncImportKey
import com.cuentasclaras.app.importar.bnc.detectGap
import com.cuentasclaras.app.importar.bnc.mapBncRows
import com.cuentasclaras.app.importar.bnc.newestRow
import com.cuentasclaras.app.importar.bnc.parseBncTxt
import com.cuentasclaras.app.importar.bnc.validateChain
import com.cuentasclaras.app.ui.Avisos
import com.cuentasclaras.app.ui.ERROR_DESCONOCIDO
import com.cuentasclaras.app.ui.Ocupacion
import com.cuentasclaras.app.ui.escribir
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_BYTES = 1_000_000

data class DraftUI(
    val draft: BncDraft,
    val tx: Transaction,
    val incluir: Boolean,
    val crearRegla: Boolean,
    val patron: String,
    /** true si sugerirParaLote propuso la asignación y el usuario no la cambió (Tarea 30). */
    val sugerido: Boolean = false,
    /** Misma importKey que una fila anterior del mismo texto: nace desmarcada pero se puede marcar a mano. */
    val repetida: Boolean = false,
)

enum class FaseImport { ENTRADA, PREVIA, HECHO }

class ImportarViewModel(private val repo: CuentasRepo) : ViewModel() {
    data class Ui(
        val texto: String = "",
        val fase: FaseImport = FaseImport.ENTRADA,
        val resultado: BncParseResult? = null,
        val drafts: List<DraftUI> = emptyList(),
        val gap: GapStatus = GapStatus.PRIMERA_IMPORTACION,
        val rotos: List<Int> = emptyList(),
        val saldoTxt: Long? = null,
        val sinResultados: Boolean = false,
        /** Oculta el banner tras crear el ajuste: un segundo toque crearía otro ajuste sobre un saldo ya cuadrado. */
        val ajusteHecho: Boolean = false,
        val guardando: Boolean = false,
    )

    data class Estado(
        val ui: Ui = Ui(),
        val categorias: List<Category> = emptyList(),
        val cuentas: List<Account> = emptyList(),
        val saldoApp: Long = 0,
        /** Descuadre ofrecible como ajuste: null si no hay descuadre o si hay movimientos posteriores al TXT. */
        val descuadre: Long? = null,
        val rawMismatch: Boolean = false,
        val hayTxsPosteriores: Boolean = false,
    ) {
        val nuevas: List<DraftUI> get() = ui.drafts.filter { it.incluir && !it.draft.duplicate }
        val duplicadas: Int get() = ui.drafts.count { it.draft.duplicate }
    }

    private val ui = MutableStateFlow(Ui())
    private val ocupacion = Ocupacion({ ui.value.guardando }) { g -> ui.update { it.copy(guardando = g) } }

    // Saldos con las transacciones CRUDAS (restricción 7): nunca txsEfectivas().
    val estado: StateFlow<Estado> = combine(ui, repo.txs, repo.categorias, repo.cuentas) { u, txs, categorias, cuentas ->
        val saldoApp = accountBalanceMinor(CUENTA_BNC, txs)
        val rawMismatch = u.fase == FaseImport.HECHO && u.saldoTxt != null && saldoApp != u.saldoTxt
        // Si ya hay movimientos posteriores al último renglón del TXT, el delta ya no
        // representa «movimientos previos al primer export» y ofrecer el ajuste podría corromper el saldo.
        val finArchivo = u.resultado?.let { newestRow(it.rows)?.date }
        val hayPosteriores = finArchivo != null && txs.any { it.accountId == CUENTA_BNC && it.date > finArchivo }
        Estado(
            ui = u, categorias = categorias, cuentas = cuentas, saldoApp = saldoApp,
            descuadre = if (rawMismatch && !hayPosteriores) u.saldoTxt!! - saldoApp else null,
            rawMismatch = rawMismatch, hayTxsPosteriores = hayPosteriores,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Estado())

    fun cambiarTexto(t: String) = ui.update { it.copy(texto = t) }

    fun cargarArchivo(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            try {
                val texto = withContext(Dispatchers.IO) {
                    val entrada = resolver.openInputStream(uri) ?: error("no se pudo abrir")
                    entrada.use { leerConTope(it) }
                }
                if (texto == null) {
                    Avisos.avisar("El archivo es demasiado grande para ser un export del BNC (máximo 1 MB)")
                    return@launch
                }
                ui.update { it.copy(texto = texto) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Avisos.avisar("No se pudo leer el archivo: ${e.message}")
            }
        }
    }

    /** Lee como mucho 1 MB (un export del BNC pesa unos KB); null si el archivo lo supera. */
    private fun leerConTope(entrada: java.io.InputStream): String? {
        val salida = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = entrada.read(buf)
            if (n < 0) break
            salida.write(buf, 0, n)
            if (salida.size() > MAX_BYTES) return null
        }
        return String(salida.toByteArray(), Charsets.UTF_8)
    }

    fun analizar() {
        if (ocupacion.activa) return
        val texto = ui.value.texto
        escribir(ocupacion, "No se pudo analizar", aviso = { "No se pudo analizar: ${it.message ?: ERROR_DESCONOCIDO}" }) {
            val r = parseBncTxt(texto)
            if (r.rows.isEmpty()) {
                ui.update { it.copy(sinResultados = true) }
                Avisos.avisar("No se encontraron movimientos en el texto")
                return@escribir
            }
            val db = repo.db
            val reglas = db.rules().todas().map { it.toDomain() }
            val existentes = db.transactions().importKeys().toSet()
            // E19/E24: solo capturas REGISTRADA cuyo movimiento existe, con su sentido (DAO de capturas).
            val capturadas = db.capturas().registradas()
            val categorias = db.categories().todas().map { it.toDomain() }
            val cuenta = db.accounts().porId(CUENTA_BNC)
            val mapeados = mapBncRows(r.rows, CUENTA_BNC, existentes, reglas, categorias, capturadas)
            val vistas = mutableSetOf<String>()
            val borradores = mapeados.map { d ->
                // Fila repetida dentro del mismo texto: solo la primera entra marcada. TODA fila se anota en
                // `vistas`, también la duplicada por captura: si no, la segunda copia de un pago ya capturado
                // (export pegado dos veces) no se vería repetida y se importaría.
                val nueva = vistas.add(d.tx.importKey ?: d.tx.id)
                val repetida = !d.duplicate && !nueva
                DraftUI(d, d.tx, incluir = !d.duplicate && !repetida, crearRegla = false, patron = d.tx.note ?: "", repetida = repetida)
            }
            ui.update {
                it.copy(
                    sinResultados = false, ajusteHecho = false, resultado = r,
                    rotos = validateChain(r.rows),
                    gap = detectGap(r.rows, cuenta?.bncLastImportKey, cuenta?.bncLastSaldoMinor),
                    saldoTxt = newestRow(r.rows)?.saldoMinor,
                    drafts = borradores,
                    fase = FaseImport.PREVIA,
                )
            }
        }
    }

    fun actualizar(i: Int, cambio: (DraftUI) -> DraftUI) =
        ui.update { u -> u.copy(drafts = u.drafts.mapIndexed { j, d -> if (j == i) cambio(d) else d }) }

    fun confirmar() {
        if (ocupacion.activa) return
        // `Estado.nuevas` es la única definición de qué filas se importan (la misma que pinta la previa); se
        // calcula sobre la `Ui` actual para no depender de que `estado` ya haya combinado el último cambio.
        val e = Estado(ui = ui.value)
        val resultado = e.ui.resultado ?: return
        val nuevas = e.nuevas
        val duplicadas = e.duplicadas
        escribir(
            ocupacion,
            "No se importó nada",
            aviso = { "No se importó nada: ${it.message ?: ERROR_DESCONOCIDO}" },
            alTerminar = {
                Avisos.avisar("${nuevas.size} importados, $duplicadas duplicados omitidos ✓")
                ui.update { it.copy(fase = FaseImport.HECHO) }
            },
        ) {
            val reglasNuevas = nuevas
                .filter { it.crearRegla && it.tx.categoryId != null && it.patron.isNotBlank() }
                .map { buildRule(it.patron, it.tx.categoryId!!) }
            val fila = newestRow(resultado.rows)
            commitImport(
                repo.db,
                txs = nuevas.map { it.tx },
                newRules = reglasNuevas,
                accountPatch = fila?.let { ParcheCuentaBnc(CUENTA_BNC, bncImportKey(it), it.saldoMinor) },
            )
        }
    }

    fun crearAjuste() {
        if (ocupacion.activa) return
        val e = estado.value
        val saldoTxt = e.ui.saldoTxt ?: return
        if (e.descuadre == null) return
        escribir(
            ocupacion,
            "No se pudo crear el ajuste",
            alTerminar = {
                Avisos.avisar("Saldo cuadrado con el banco ✓")
                ui.update { it.copy(ajusteHecho = true) }
            },
        ) { repo.agregarTx(buildAdjustment(CUENTA_BNC, e.saldoApp, saldoTxt, todayIso(), "Cuadre tras import BNC")) }
    }

    fun reiniciar() = ui.update { Ui() }
}
