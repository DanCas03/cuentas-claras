package com.cuentasclaras.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cuentasclaras.app.AppContainer
import com.cuentasclaras.app.CuentasApp

/** ViewModel con dependencias del AppContainer y los argumentos de la ruta (SavedStateHandle),
 *  con alcance del destino de navegación actual: cada visita a `nuevo?…` crea su propio VM. */
@Composable
inline fun <reified VM : ViewModel> vmCon(crossinline crear: (AppContainer, SavedStateHandle) -> VM): VM {
    val app = LocalContext.current.applicationContext as CuentasApp
    return viewModel(factory = viewModelFactory { initializer { crear(app.container, createSavedStateHandle()) } })
}
