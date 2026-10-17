package com.cuentasclaras.app.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.cuentasclaras.app.ui.theme.PALETAS_OSCURAS
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class IconoAppTest {
    private val contexto: Context = ApplicationProvider.getApplicationContext()
    private val repo = IconoAppRepo(contexto)

    private fun habilitados(): List<ComponentName> =
        ICONOS_APP.flatMap { i -> PALETAS_OSCURAS.map { p -> repo.aliasDe(i.id, p.id) } }
            .filter { contexto.packageManager.getComponentEnabledSetting(it) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED }

    @Test fun `aplicar deja un solo alias habilitado, el de la forma y la paleta`() {
        repo.aplicar("moneda", "cacao")
        assertEquals(listOf(repo.aliasDe("moneda", "cacao")), habilitados())
        repo.aplicar("moneda", "pizarra_cobalto")
        assertEquals(listOf(repo.aliasDe("moneda", "pizarra_cobalto")), habilitados())
    }

    @Test fun `una paleta desconocida cae a la paleta por defecto y nunca deja la app sin lanzador`() {
        repo.aplicar("moneda", "cacao")
        repo.aplicar("moneda", "sepia_inexistente")
        assertEquals(listOf(repo.aliasDe("moneda", ID_PALETA_OSCURA_POR_DEFECTO)), habilitados())
    }

    @Test fun `un id de icono desconocido cae a check`() = assertEquals("check", iconoPorId("estrella").id)
}
