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

    @Test fun `una fuente sin opsz se construye sin el eje`() {
        val sinOpsz = FUENTES.first { it.opszTexto == null }
        assertNotNull(tipografiaCuentas(assets, sinOpsz).cifraGrande.fontFamily)
    }
}
