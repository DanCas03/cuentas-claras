import { useState } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { db } from '../../db/db';
import type { Category, CategoryKind } from '../../domain/types';
import { formatMoney } from '../../domain/money';
import { avisar } from '../Toast';

const NUEVA = (): Category => ({
  id: crypto.randomUUID(), name: '', icon: '📦', color: '#6b7280', kind: 'expense',
});

export function CategoriasSection() {
  const categorias = useLiveQuery(() => db.categories.toArray(), []) ?? [];
  const [editando, setEditando] = useState<Category | null>(null);
  const [presupuestoTxt, setPresupuestoTxt] = useState('');
  const [guardando, setGuardando] = useState(false);

  function abrir(c: Category) {
    setEditando(c);
    setPresupuestoTxt(c.monthlyBudgetMinor ? (c.monthlyBudgetMinor / 100).toFixed(2) : '');
  }

  async function guardar() {
    if (guardando || !editando) return;
    if (!editando.name.trim()) { avisar('Ponle nombre a la categoría'); return; }
    const limpio = presupuestoTxt.trim().replace(',', '.');
    let monthlyBudgetMinor: number | undefined;
    if (limpio !== '') {
      const v = Number(limpio);
      if (!Number.isFinite(v) || v <= 0) { avisar('Presupuesto inválido'); return; }
      monthlyBudgetMinor = Math.round(v * 100);
    }
    setGuardando(true);
    try {
      await db.categories.put({ ...editando, name: editando.name.trim(), monthlyBudgetMinor });
      setEditando(null);
      avisar('Categoría guardada ✓');
    } catch (e) {
      avisar((e as Error).message);
    } finally {
      setGuardando(false);
    }
  }

  return (
    <div>
      {categorias.map((c) => (
        <div key={c.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '6px 0' }}>
          <span>
            {c.icon} {c.name} <span className="chip">{c.kind === 'expense' ? 'gasto' : 'ingreso'}</span>
            {c.monthlyBudgetMinor ? <span className="chip" style={{ marginLeft: 4 }}>presup. {formatMoney(c.monthlyBudgetMinor, 'USD')}</span> : null}
          </span>
          <button className="chip" aria-label={`Editar ${c.name}`} onClick={() => abrir(c)}>editar</button>
        </div>
      ))}
      <button className="boton secundario" onClick={() => abrir(NUEVA())}>Nueva categoría</button>

      {editando && (
        <div className="modal-fondo" onClick={() => setEditando(null)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <h3>Categoría</h3>
            <label>Nombre</label>
            <input aria-label="Nombre de categoría" value={editando.name} onChange={(e) => setEditando({ ...editando, name: e.target.value })} />
            <div className="fila-form">
              <div>
                <label>Icono (emoji)</label>
                <input aria-label="Icono" value={editando.icon} onChange={(e) => setEditando({ ...editando, icon: e.target.value })} />
              </div>
              <div>
                <label>Color</label>
                <input aria-label="Color" type="color" value={editando.color} onChange={(e) => setEditando({ ...editando, color: e.target.value })} />
              </div>
            </div>
            <label>Tipo</label>
            <select aria-label="Tipo de categoría" value={editando.kind} onChange={(e) => setEditando({ ...editando, kind: e.target.value as CategoryKind })}>
              <option value="expense">Gasto</option>
              <option value="income">Ingreso</option>
            </select>
            <label>Presupuesto mensual en $ (vacío = sin presupuesto)</label>
            <input aria-label="Presupuesto" inputMode="decimal" placeholder="ej. 50.00" value={presupuestoTxt} onChange={(e) => setPresupuestoTxt(e.target.value)} />
            <div className="fila-form" style={{ marginTop: 12 }}>
              <button className="boton secundario" onClick={() => setEditando(null)}>Cancelar</button>
              <button className="boton" onClick={guardar} disabled={guardando}>Guardar categoría</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
