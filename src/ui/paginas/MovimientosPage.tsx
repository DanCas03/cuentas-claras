import { useState } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { db } from '../../db/db';
import type { Transaction } from '../../domain/types';
import { filterTxs } from '../../domain/filters';
import { formatMoney, parseVenezuelanAmount } from '../../domain/money';
import { avisar } from '../Toast';

export function MovimientosPage() {
  const [q, setQ] = useState('');
  const [cuentaId, setCuentaId] = useState('');
  const [categoriaId, setCategoriaId] = useState('');
  const [mes, setMes] = useState('');
  const [editando, setEditando] = useState<Transaction | null>(null);

  const cuentas = useLiveQuery(() => db.accounts.toArray(), []) ?? [];
  const categorias = useLiveQuery(() => db.categories.toArray(), []) ?? [];
  const txs = useLiveQuery(() => db.transactions.toArray(), []) ?? [];

  const visibles = filterTxs(txs, {
    q: q || undefined,
    accountId: cuentaId || undefined,
    categoryId: categoriaId || undefined,
    month: mes || undefined,
  });
  const meses = [...new Set(txs.map((t) => t.date.slice(0, 7)))].sort().reverse();
  const nombreCuenta = (id?: string) => cuentas.find((c) => c.id === id)?.name ?? '?';
  const cat = (id?: string) => categorias.find((c) => c.id === id);

  function descripcionFila(t: Transaction): string {
    if (t.type === 'transfer') return `${nombreCuenta(t.accountId)} → ${nombreCuenta(t.toAccountId)}`;
    return t.note || cat(t.categoryId)?.name || (t.type === 'adjustment' ? 'Ajuste de saldo' : 'Sin nota');
  }

  function montoFila(t: Transaction) {
    const moneda = cuentas.find((c) => c.id === t.accountId)?.currency ?? 'USD';
    if (t.type === 'income') return <span className="monto-pos">+{formatMoney(t.amountMinor, moneda)}</span>;
    if (t.type === 'expense') return <span className="monto-neg">−{formatMoney(t.amountMinor, moneda)}</span>;
    if (t.type === 'adjustment')
      return <span className={t.amountMinor >= 0 ? 'monto-pos' : 'monto-neg'}>{formatMoney(t.amountMinor, moneda)}</span>;
    return <span>↔ {formatMoney(t.amountMinor, moneda)}</span>;
  }

  return (
    <div>
      <h2>Movimientos</h2>
      <input aria-label="Buscar" placeholder="Buscar en notas…" value={q} onChange={(e) => setQ(e.target.value)} />
      <div className="fila-form" style={{ marginTop: 8 }}>
        <select aria-label="Filtrar cuenta" value={cuentaId} onChange={(e) => setCuentaId(e.target.value)}>
          <option value="">Todas las cuentas</option>
          {cuentas.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
        </select>
        <select aria-label="Filtrar categoría" value={categoriaId} onChange={(e) => setCategoriaId(e.target.value)}>
          <option value="">Todas las categorías</option>
          {categorias.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
        </select>
        <select aria-label="Filtrar mes" value={mes} onChange={(e) => setMes(e.target.value)}>
          <option value="">Todos los meses</option>
          {meses.map((m) => <option key={m} value={m}>{m}</option>)}
        </select>
      </div>
      <div style={{ marginTop: 8 }}>
        {visibles.length === 0 && (
          <div className="vacio"><span className="icono">🗂️</span>No hay movimientos que coincidan.</div>
        )}
        {visibles.map((t) => (
          <button key={t.id} className="fila-mov" onClick={() => setEditando(t)}>
            <span style={{ fontSize: 22 }}>
              {t.type === 'transfer' ? '🔁' : t.type === 'adjustment' ? '🎯' : cat(t.categoryId)?.icon ?? '📦'}
            </span>
            <span className="detalle">
              <span className="nota">{descripcionFila(t)}</span>
              <span className="meta">{t.date} · {nombreCuenta(t.accountId)}{t.importKey ? ' · importado' : ''}</span>
            </span>
            {montoFila(t)}
          </button>
        ))}
      </div>
      {editando && <EditorMovimiento tx={editando} cerrar={() => setEditando(null)} />}
    </div>
  );
}

function EditorMovimiento({ tx, cerrar }: { tx: Transaction; cerrar: () => void }) {
  const categorias = useLiveQuery(() => db.categories.toArray(), []) ?? [];
  const [categoriaId, setCategoriaId] = useState(tx.categoryId ?? '');
  const [nota, setNota] = useState(tx.note ?? '');
  const [fecha, setFecha] = useState(tx.date);
  const [montoTxt, setMontoTxt] = useState((tx.amountMinor / 100).toFixed(2).replace('.', ','));
  const [guardando, setGuardando] = useState(false);
  // Transferencias y ajustes: solo nota y fecha son editables (el monto afecta dos saldos o es un delta).
  const montoEditable = tx.type === 'expense' || tx.type === 'income';

  async function guardar() {
    if (guardando) return;
    const cambios: Partial<Transaction> = { note: nota.trim() || undefined, date: fecha };
    if (montoEditable) {
      const m = parseVenezuelanAmount(montoTxt.replace(/,$/, ''));
      if (m === null || m <= 0) { avisar('Monto inválido'); return; }
      cambios.amountMinor = m;
      cambios.categoryId = categoriaId || undefined;
    }
    setGuardando(true);
    try {
      await db.transactions.update(tx.id, cambios);
      avisar('Actualizado ✓');
      cerrar();
    } catch (e) {
      avisar((e as Error).message);
    } finally {
      setGuardando(false);
    }
  }

  async function eliminar() {
    if (guardando) return;
    if (!window.confirm('¿Eliminar este movimiento?')) return;
    setGuardando(true);
    try {
      await db.transactions.delete(tx.id);
      avisar('Eliminado');
      cerrar();
    } catch (e) {
      avisar((e as Error).message);
    } finally {
      setGuardando(false);
    }
  }

  return (
    <div className="modal-fondo" onClick={cerrar}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h3>Editar movimiento</h3>
        {montoEditable && (
          <>
            <label>Monto</label>
            <input aria-label="Editar monto" inputMode="decimal" value={montoTxt} onChange={(e) => setMontoTxt(e.target.value)} />
            <label>Categoría</label>
            <select aria-label="Editar categoría" value={categoriaId} onChange={(e) => setCategoriaId(e.target.value)}>
              <option value="">Sin categoría</option>
              {categorias.filter((c) => c.kind === tx.type).map((c) => (
                <option key={c.id} value={c.id}>{c.icon} {c.name}</option>
              ))}
            </select>
          </>
        )}
        <label>Fecha</label>
        <input aria-label="Editar fecha" type="date" value={fecha} onChange={(e) => setFecha(e.target.value)} />
        <label>Nota</label>
        <input aria-label="Editar nota" value={nota} onChange={(e) => setNota(e.target.value)} />
        <div className="fila-form" style={{ marginTop: 12 }}>
          <button className="boton secundario" onClick={cerrar} disabled={guardando}>Cancelar</button>
          <button className="boton peligro" onClick={eliminar} disabled={guardando}>Eliminar</button>
          <button className="boton" onClick={guardar} disabled={guardando}>Guardar</button>
        </div>
      </div>
    </div>
  );
}
