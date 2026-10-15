package com.cuentasclaras.app.data.db

import androidx.room.TypeConverter
import com.cuentasclaras.app.domain.JsonCuentas
import com.cuentasclaras.app.domain.Recurrence
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

class Convertidores {
    @TypeConverter
    fun recurrenceAJson(r: Recurrence): String = JsonCuentas.encodeToString(Recurrence.serializer(), r)

    @TypeConverter
    fun jsonARecurrence(s: String): Recurrence = JsonCuentas.decodeFromString(Recurrence.serializer(), s)

    @TypeConverter
    fun listaAJson(l: List<String>?): String? =
        l?.let { JsonCuentas.encodeToString(ListSerializer(String.serializer()), it) }

    @TypeConverter
    fun jsonALista(s: String?): List<String>? =
        s?.let { JsonCuentas.decodeFromString(ListSerializer(String.serializer()), it) }
}
