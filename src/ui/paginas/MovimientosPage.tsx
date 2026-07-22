import { useRef, useState } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { db } from '../../db/db';
import type { Transaction } from '../../domain/types';
import { filterTxs } from '../../domain/filters';
import { formatMoney, parseVenezuelanAmount } from '../../domain/money';
import { residuosDeGrupo } from '../../domain/groups';
import { agruparTxs, anadirAGrupo, quitarDeGrupo, desagrupar } from '../../db/groupOps';
import { avisar } from '../Toast';

export function MovimientosPage() {
  const [q, setQ] = useState('');
  const [cuentaId, setCuentaId] = useState('');
  const [categoriaId, setCategoriaId] = useState('');
  const [mes, setMes] = useState('');
  const [editando, setEditando] = useState<Transaction | null>(null);
  const [seleccion, setSeleccion] = useState<Set<string> | null>(null); // null = sin modo selección
  const [destinoGrupoId, setDestinoGrupoId] = useState<string | null>(null); // añadir a grupo existente
  const [modalNota, setModalNota] = useState(false);
  const [notaGrupo, setNotaGrupo] = useState('');
  const [expandidos, setExpandidos] = useState<Set<string>>(new Set());
  const [guardando, setGuardando] = useState(false);
  const timerRef = useRef<number | null>(null);
  const largaRef = useRef(false);

  const cuentas = useLiveQuery(() => db.accounts.toArray(), []) ?? [];
  const categorias = useLiveQuery(() => db.categories.toArray(), []) ?? [];
  const txs = useLiveQuery(() => db.transactions.toArray(), []) ?? [];
  const grupos = useLiveQuery(() => db.txGroups.toArray(), []) ?? [];

  const visibles = filterTxs(txs, {
    q: q || undefined,
    accountId: cuentaId || undefined,
    categoryId: categoriaId || undefined,
    month: mes || undefined,
  });
  const meses = [...new Set(txs.map((t) => t.date.slice(0, 7)))].sort().reverse();
  const nombreCuenta = (id?: string) => cuentas.find((c) => c.id === id)?.name ?? '?';
  const cat = (id?: string) => categorias.find((c) => c.id === id);
  const notaDeGrupo = (gid: string) => grupos.find((g) => g.id === gid)?.nota;

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

  // ---- modo selección ----
  const esAgrupable = (t: Transaction) =>
    (t.type === 'income' || t.type === 'expense') && !t.groupId;

  function iniciarSeleccion(t: Transaction, destino: string | null = null) {
    setDestinoGrupoId(destino);
    setSeleccion(new Set(t && esAgrupable(t) ? [t.id] : []));
  }

  function toggleSeleccion(t: Transaction) {
    if (!esAgrupable(t)) { avisar('Solo se agrupan gastos e ingresos sueltos'); return; }
    setSeleccion((s) => {
      const nueva = new Set(s);
      if (nueva.has(t.id)) nueva.delete(t.id); else nueva.add(t.id);
      return nueva;
    });
  }

  function cancelarSeleccion() {
    setSeleccion(null);
    setDestinoGrupoId(null);
    setModalNota(false);
    setNotaGrupo('');
  }

  function alPresionar(t: Transaction) {
    largaRef.current = false;
    timerRef.current = window.setTimeout(() => {
      largaRef.current = true;
      if (seleccion === null) iniciarSeleccion(t);
    }, 500);
  }

  function alSoltar() {
    if (timerRef.current !== null) { clearTimeout(timerRef.current); timerRef.current = null; }
  }

  function alClickFila(t: Transaction) {
    if (largaRef.current) { largaRef.current = false; return; }
    if (seleccion !== null) toggleSeleccion(t);
    else setEditando(t);
  }

  async function confirmarAgrupar() {
    if (guardando || seleccion === null) return;
    setGuardando(true);
    try {
      if (destinoGrupoId) {
        await anadirAGrupo(db, destinoGrupoId, [...seleccion]);
        avisar('Añadidos al grupo ✓');
      } else {
        await agruparTxs(db, [...seleccion], notaGrupo);
        avisar('Grupo transaccional creado ✓');
      }
      cancelarSeleccion();
    } catch (e) {
      avisar((e as Error).message);
    } finally {
      setGuardando(false);
    }
  }

  async function accionGrupo(fn: () => Promise<void>, exito: string) {
    if (guardando) return;
    setGuardando(true);
    try {
      await fn();
      avisar(exito);
    } catch (e) {
      avisar((e as Error).message);
    } finally {
      setGuardando(false);
    }
  }

  // ---- modelo de vista: sueltas + grupos, ordenados por fecha desc ----
  const clave = (t: Transaction) => `${t.date} ${t.time ?? ''}`;
  const gruposVisibles = new Map<string, Transaction[]>();
  const sueltasVisibles: Transaction[] = [];
  for (const t of visibles) {
    if (t.groupId) {
      const lista = gruposVisibles.get(t.groupId) ?? [];
      lista.push(t);
      gruposVisibles.set(t.groupId, lista);
    } else {
      sueltasVisibles.push(t);
    }
  }
  type Item = { clave: string } & ({ tipo: 'tx'; t: Transaction } | { tipo: 'grupo'; gid: string; visibles: Transaction[] });
  const items: Item[] = [
    ...sueltasVisibles.map((t) => ({ tipo: 'tx' as const, t, clave: clave(t) })),
    ...[...gruposVisibles.entries()].map(([gid, vis]) => ({
      tipo: 'grupo' as const, gid, visibles: vis,
      clave: clave(vis.reduce((a, b) => (clave(b) > clave(a) ? b : a))),
    })),
  ].sort((a, b) => b.clave.localeCompare(a.clave));

  const filaTx = (t: Transaction, dentroDeGrupo: boolean) => (
    <button
      key={t.id}
      className={[
        'fila-mov',
        seleccion?.has(t.id) ? 'seleccionada' : '',
        seleccion !== null && !esAgrupable(t) ? 'no-seleccionable' : '',
      ].join(' ').trim()}
      onPointerDown={() => !dentroDeGrupo && alPresionar(t)}
      onPointerUp={alSoltar}
      onPointerLeave={alSoltar}
      onPointerCancel={alSoltar}
      onContextMenu={(e) => e.preventDefault()}
      onClick={() => alClickFila(t)}
    >
      <span style={{ fontSize: 22 }}>
        {t.type === 'transfer' ? '🔁' : t.type === 'adjustment' ? '🎯' : cat(t.categoryId)?.icon ?? '📦'}
      </span>
      <span className="detalle">
        <span className="nota">{descripcionFila(t)}</span>
        <span className="meta">{t.date} · {nombreCuenta(t.accountId)}{t.importKey ? ' · importado' : ''}</span>
      </span>
      {montoFila(t)}
    </button>
  );

  const filaGrupo = (gid: string, visiblesDelGrupo: Transaction[]) => {
    const todos = txs.filter((t) => t.groupId === gid);
    const residuos = residuosDeGrupo(todos, cuentas);
    const abierto = expandidos.has(gid);
    return (
      <div key={`grupo-${gid}`}>
        <button
          className="fila-mov"
          onClick={() => setExpandidos((s) => {
            const nueva = new Set(s);
            if (nueva.has(gid)) nueva.delete(gid); else nueva.add(gid);
            return nueva;
          })}
        >
          <span style={{ fontSize: 22 }}>🔁</span>
          <span className="detalle">
            <span className="nota">{notaDeGrupo(gid) || 'Grupo transaccional'}</span>
            <span className="meta">{todos.length} movimientos · vuelto</span>
          </span>
          <span>
            {residuos.length === 0
              ? <span className="meta">sin residuo</span>
              : residuos.map((r) => (
                  <span key={r.currency} className={r.residuoMinor >= 0 ? 'monto-pos' : 'monto-neg'} style={{ display: 'block' }}>
                    {r.residuoMinor >= 0 ? '+' : '−'}{formatMoney(Math.abs(r.residuoMinor), r.currency)}
                  </span>
                ))}
          </span>
        </button>
        {abierto && (
          <div className="grupo-miembros">
            {visiblesDelGrupo.map((t) => (
              <div key={t.id} style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                <div style={{ flex: 1 }}>{filaTx(t, true)}</div>
                <button
                  className="chip"
                  aria-label="Quitar del grupo"
                  disabled={guardando}
                  onClick={() => accionGrupo(() => quitarDeGrupo(db, [t.id]), 'Quitado del grupo ✓')}
                >
                  quitar
                </button>
              </div>
            ))}
            <div className="fila-form" style={{ margin: '8px 0' }}>
              <button
                className="boton secundario"
                disabled={guardando || seleccion !== null}
                onClick={() => { setDestinoGrupoId(gid); setSeleccion(new Set()); avisar('Toca los movimientos a añadir'); }}
              >
                Añadir movimientos
              </button>
              <button
                className="boton peligro"
                disabled={guardando}
                onClick={() => {
                  if (window.confirm('¿Desagrupar? Todos los movimientos vuelven a contarse normal.')) {
                    void accionGrupo(() => desagrupar(db, gid), 'Grupo desagrupado ✓');
                  }
                }}
              >
                Desagrupar
              </button>
            </div>
          </div>
        )}
      </div>
    );
  };

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
        {items.length === 0 && (
          <div className="vacio"><span className="icono">🗂️</span>No hay movimientos que coincidan.</div>
        )}
        {items.map((item) => item.tipo === 'tx' ? filaTx(item.t, false) : filaGrupo(item.gid, item.visibles))}
      </div>

      {seleccion !== null && (
        <div className="barra-seleccion">
          <button className="boton secundario" onClick={cancelarSeleccion} disabled={guardando}>Cancelar</button>
          <button
            className="boton"
            disabled={guardando || seleccion.size < (destinoGrupoId ? 1 : 2)}
            onClick={() => destinoGrupoId ? void confirmarAgrupar() : setModalNota(true)}
          >
            {destinoGrupoId ? `Añadir al grupo (${seleccion.size})` : `Agrupar como transaccional (${seleccion.size})`}
          </button>
        </div>
      )}

      {modalNota && (
        <div className="modal-fondo" onClick={() => setModalNota(false)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <h3>Agrupar como transaccional</h3>
            <p style={{ fontSize: 12, color: 'var(--texto2)' }}>
              Los movimientos agrupados no cuentan en tus métricas: solo su vuelto.
            </p>
            <label>Nota del grupo (opcional)</label>
            <input aria-label="Nota del grupo" value={notaGrupo} onChange={(e) => setNotaGrupo(e.target.value)} placeholder="me pasaron para la leche" />
            <div className="fila-form" style={{ marginTop: 12 }}>
              <button className="boton secundario" onClick={() => setModalNota(false)} disabled={guardando}>Cancelar</button>
              <button className="boton" onClick={() => void confirmarAgrupar()} disabled={guardando}>Agrupar</button>
            </div>
          </div>
        </div>
      )}

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
    // Important #5: el input type="date" se puede vaciar (borrado nativo / edición parcial)
    // y sin este chequeo se persistía date:'' — la tx seguía sumando en accountBalanceMinor
    // pero desaparecía silenciosamente de monthSummary y del filtro por mes.
    if (!/^\d{4}-\d{2}-\d{2}$/.test(fecha)) { avisar('Fecha inválida'); return; }
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
