package com.cuentasclaras.app.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class AparienciaRepoTest {
    private val contexto: Context = ApplicationProvider.getApplicationContext()
    private val archivo = File(contexto.filesDir, "apariencia-test-${System.nanoTime()}.preferences_pb")
    private val almacen = PreferenceDataStoreFactory.create(produceFile = { archivo })
    private val repo = AparienciaRepo(almacen)

    @Test fun `por defecto Bricolage, Libreta, etiquetas y check`() = runBlocking {
        val a = repo.apariencia.first()
        assertEquals(Apariencia(), a)
        assertEquals("bricolage", a.idFuente)
        assertEquals("libreta", a.idDiseno)
        assertEquals(true, a.etiquetasBarra)
        assertEquals("check", a.idIcono)
    }

    @Test fun `escribe y lee las cuatro preferencias nuevas`() = runBlocking {
        repo.cambiarFuente("inter")
        repo.cambiarDiseno("aurora")
        repo.cambiarEtiquetasBarra(false)
        repo.cambiarIcono("moneda")
        val a = repo.apariencia.first()
        assertEquals("inter", a.idFuente)
        assertEquals("aurora", a.idDiseno)
        assertEquals(false, a.etiquetasBarra)
        assertEquals("moneda", a.idIcono)
    }

    @Test fun `un modo desconocido cae al valor por defecto`() = runBlocking {
        almacen.edit { it[stringPreferencesKey("modo")] = "SEPIA" }
        assertEquals(ModoTema.SISTEMA, repo.apariencia.first().modo)
    }
}
