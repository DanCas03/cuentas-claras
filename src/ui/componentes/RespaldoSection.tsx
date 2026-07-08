import { useState } from 'react';
import { db } from '../../db/db';
import { backupFilename, exportBackup, importBackup, validateBackup, type BackupFile } from '../../backup/backup';
import { saveJsonFile } from '../../backup/saveFile';
import { todayIso } from '../../domain/dates';
import { avisar } from '../Toast';

export function RespaldoSection() {
  const [pendiente, setPendiente] = useState<{ backup: BackupFile; resumen: string } | null>(null);
  const [guardando, setGuardando] = useState(false);

  async function exportar() {
    if (guardando) return;
    setGuardando(true);
    try {
      const b = await exportBackup(db);
      const via = await saveJsonFile(JSON.stringify(b, null, 2), backupFilename(todayIso()));
      avisar(via === 'download' ? 'Respaldo descargado — súbelo a tu Drive' : 'Respaldo guardado ✓');
    } catch (e) {
      const err = e as Error;
      if (err.name !== 'AbortError') avisar(`No se pudo exportar: ${err.message}`);
    } finally {
      setGuardando(false);
    }
  }

  async function elegirArchivo(f: File) {
    try {
      const v = validateBackup(JSON.parse(await f.text()));
      if (!v.ok) {
        avisar(v.error);
        return;
      }
      setPendiente({
        backup: v.backup,
        resumen: `${v.backup.transactions.length} movimientos, ${v.backup.accounts.length} cuentas, exportado el ${v.backup.exportedAt.slice(0, 10)}`,
      });
    } catch {
      avisar('El archivo no es un JSON válido');
    }
  }

  async function restaurar() {
    if (guardando) return;
    if (!pendiente) return;
    setGuardando(true);
    try {
      // Auto-respaldo de seguridad ANTES de reemplazar (spec §4.8)
      const previo = await exportBackup(db);
      await saveJsonFile(JSON.stringify(previo, null, 2), backupFilename(`previo-${todayIso()}`));
      await importBackup(db, pendiente.backup);
      setPendiente(null);
      avisar('Datos restaurados ✓');
    } catch (e) {
      const err = e as Error;
      if (err.name === 'AbortError') avisar('Restauración cancelada: guarda primero el respaldo de seguridad');
      else avisar(`No se restauró nada: ${err.message}`);
    } finally {
      setGuardando(false);
    }
  }

  return (
    <div>
      <p style={{ color: 'var(--texto2)', fontSize: 13 }}>
        En Android: Exportar abre el selector/compartir — elige Drive. En Windows: elige directamente tu carpeta de Google Drive.
      </p>
      <button className="boton" onClick={exportar} disabled={guardando}>Exportar respaldo</button>
      <label>Restaurar desde un respaldo</label>
      <input
        aria-label="Archivo de respaldo"
        type="file"
        accept=".json,application/json"
        onChange={(e) => {
          const f = e.target.files?.[0];
          if (f) elegirArchivo(f);
          e.target.value = '';
        }}
      />
      {pendiente && (
        <div className="banner aviso">
          <p style={{ margin: '0 0 8px' }}>
            Vas a <strong>reemplazar TODOS los datos actuales</strong> por: {pendiente.resumen}.
            Antes se guardará un respaldo de seguridad de lo actual.
          </p>
          <div className="fila-form">
            <button className="boton secundario" onClick={() => setPendiente(null)} disabled={guardando}>Cancelar</button>
            <button className="boton peligro" onClick={restaurar} disabled={guardando}>Sí, reemplazar todo</button>
          </div>
        </div>
      )}
    </div>
  );
}
