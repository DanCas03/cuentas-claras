package com.cuentasclaras.app.domain

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

/** JSON único de la app (D4): lee respaldos de la PWA con claves de más o de menos y
 *  escribe sin `null` (los opcionales nulos tienen default null y encodeDefaults = false). */
val JsonCuentas: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = false
}

/** Igual que JsonCuentas pero con sangría de 2 espacios, como `JSON.stringify(x, null, 2)` de la web
 *  (E10), para el archivo de respaldo que se guarda. */
@OptIn(ExperimentalSerializationApi::class)
val JsonRespaldo: Json = Json(JsonCuentas) {
    prettyPrint = true
    prettyPrintIndent = "  "
}
