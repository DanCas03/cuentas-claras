package com.cuentasclaras.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.cuentasclaras.app.data.db.CuentasDb

fun dbDePrueba(): CuentasDb =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CuentasDb::class.java)
        .allowMainThreadQueries()
        .build()

/** Hace que la próxima `operacion` ('INSERT' | 'UPDATE' | 'DELETE') sobre `tabla` aborte con
 *  «falla inyectada». Simula un fallo a mitad de una operación compuesta. */
fun CuentasDb.fallarAntesDe(operacion: String, tabla: String) {
    openHelper.writableDatabase.execSQL(
        "CREATE TRIGGER falla_${operacion}_$tabla BEFORE $operacion ON $tabla " +
            "BEGIN SELECT RAISE(ABORT, 'falla inyectada'); END",
    )
}
