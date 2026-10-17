package com.cuentasclaras.app.ui.theme

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class FuentesTest {
    private val assets = ApplicationProvider.getApplicationContext<Context>().assets

    @Test fun `un id desconocido cae a Bricolage`() = assertEquals("bricolage", fuentePorId("comic").id)

    @Test fun `todas las fuentes del catalogo existen en assets y construyen la tipografia`() {
        FUENTES.forEach { f ->
            assets.open(f.archivo).use { assertTrue(it.read() >= 0, f.archivo) }
            assertNotNull(tipografiaCuentas(assets, f).cuerpo.fontFamily, f.id)
        }
    }

    /** Las cifras usan `tnum` (DESIGN.md §3): toda fuente del catálogo debe traer esa característica en su GSUB. */
    @Test fun `todas las fuentes del catalogo tienen digitos tabulares`() {
        FUENTES.forEach { f ->
            val bytes = assets.open(f.archivo).use { it.readBytes() }
            assertTrue("tnum" in caracteristicasGsub(bytes), "${f.id} no tiene tnum")
        }
    }

    /** Lee las etiquetas de la FeatureList de la tabla GSUB de un TTF (OpenType: directorio de tablas → GSUB → FeatureList). */
    private fun caracteristicasGsub(d: ByteArray): Set<String> {
        fun u16(i: Int) = ((d[i].toInt() and 0xFF) shl 8) or (d[i + 1].toInt() and 0xFF)
        fun u32(i: Int) = (u16(i).toLong() shl 16) or u16(i + 2).toLong()
        val numTablas = u16(4)
        var gsub = -1
        for (i in 0 until numTablas) {
            val base = 12 + 16 * i
            if (String(d, base, 4, Charsets.ISO_8859_1) == "GSUB") gsub = u32(base + 8).toInt()
        }
        if (gsub < 0) return emptySet()
        val lista = gsub + u16(gsub + 6)
        val cuantas = u16(lista)
        return (0 until cuantas).map { String(d, lista + 2 + 6 * it, 4, Charsets.ISO_8859_1) }.toSet()
    }

    @Test fun `una fuente sin opsz se construye sin el eje`() {
        val sinOpsz = FUENTES.first { it.opszTexto == null }
        assertNotNull(tipografiaCuentas(assets, sinOpsz).cifraGrande.fontFamily)
    }
}
