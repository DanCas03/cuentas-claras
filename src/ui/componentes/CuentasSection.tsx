import { useState } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { db } from '../../db/db';
import type { Account, AccountKind, Currency } from '../../domain/types';
import { avisar } from '../Toast';

const NUEVA = (): Account => ({
  id: crypto.randomUUID(), name: '', currency: 'USD', kind: 'wallet',
  isSavings: false, archived: false, enDisposicion: true,
});

const TIPOS: { valor: AccountKind; texto: string }[] = [
  { valor: 'bank', texto: 'Banco' },
  { valor: 'wallet', texto: 'Billetera' },
  { valor: 'snapshot', texto: 'Solo saldo (ajustes manuales)' },
  { valor: 'cash', texto: 'Efectivo' },
];

export function CuentasSection() {
  const cuentas = useLiveQuery(() => db.accounts.toArray(), []) ?? [];
  const [editando, setEditando] = useState<Account | null>(null);
  const [guardando, setGuardando] = useState(false);

  async function guardar() {
    if (guardando || !editando) return;
    if (!editando.name.trim()) { avisar('Ponle nombre a la cuenta'); return; }
    setGuardando(true);
    try {
      await db.accounts.put({ ...editando, name: editando.name.trim() });
      setEditando(null);
      avisar('Cuenta guardada ✓');
    } catch (e) {
      avisar((e as Error).message);
    } finally {
      setGuardando(false);
    }
  }

  return (
    <div>
      {cuentas.map((c) => (
        <div key={c.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '6px 0' }}>
          <span style={{ color: c.archived ? 'var(--texto2)' : undefined }}>
            {c.name} <span className="chip">{c.currency === 'VES' ? 'Bs' : '$'}</span>
            {c.isSavings && <span className="chip" style={{ marginLeft: 4 }}>ahorro</span>}
            {c.archived && <span className="chip" style={{ marginLeft: 4 }}>archivada</span>}
          </span>
          <button className="chip" aria-label={`Editar ${c.name}`} onClick={() => setEditando(c)}>editar</button>
        </div>
      ))}
      <button className="boton secundario" onClick={() => setEditando(NUEVA())}>Nueva cuenta</button>

      {editando && (
        <div className="modal-fondo" onClick={() => setEditando(null)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <h3>{cuentas.some((c) => c.id === editando.id) ? 'Editar cuenta' : 'Nueva cuenta'}</h3>
            <label>Nombre</label>
            <input aria-label="Nombre de cuenta" value={editando.name} onChange={(e) => setEditando({ ...editando, name: e.target.value })} />
            <label>Moneda (no convierte movimientos existentes)</label>
            <select aria-label="Moneda" value={editando.currency} onChange={(e) => setEditando({ ...editando, currency: e.target.value as Currency })}>
              <option value="USD">USD ($)</option>
              <option value="VES">Bolívares (Bs)</option>
            </select>
            <label>Tipo</label>
            <select aria-label="Tipo" value={editando.kind} onChange={(e) => setEditando({ ...editando, kind: e.target.value as AccountKind })}>
              {TIPOS.map((t) => <option key={t.valor} value={t.valor}>{t.texto}</option>)}
            </select>
            <label style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <input type="checkbox" style={{ width: 'auto' }} aria-label="Es ahorro" checked={editando.isSavings} onChange={(e) => setEditando({ ...editando, isSavings: e.target.checked })} />
              Cuenta de ahorro (no cuenta como disposición)
            </label>
            <label style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <input
                type="checkbox"
                style={{ width: 'auto' }}
                aria-label="A disposición"
                checked={editando.enDisposicion ?? !editando.isSavings}
                onChange={(e) => setEditando({ ...editando, enDisposicion: e.target.checked })}
              />
              A disposición (cuenta en la vista «A disposición» del patrimonio)
            </label>
            <label style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <input type="checkbox" style={{ width: 'auto' }} aria-label="Archivada" checked={editando.archived} onChange={(e) => setEditando({ ...editando, archived: e.target.checked })} />
              Archivada (oculta del dashboard y formularios)
            </label>
            <div className="fila-form" style={{ marginTop: 12 }}>
              <button className="boton secundario" onClick={() => setEditando(null)}>Cancelar</button>
              <button className="boton" onClick={guardar} disabled={guardando}>Guardar cuenta</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
