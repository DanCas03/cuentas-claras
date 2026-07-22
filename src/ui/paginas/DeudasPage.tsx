import { useState } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { db } from '../../db/db';
import type { Debt, DebtDirection } from '../../domain/types';
import { buildDebt } from '../../domain/debts';
import { settleDebt } from '../../db/debtOps';
import { formatMoney, parseVenezuelanAmount } from '../../domain/money';
import { todayIso } from '../../domain/dates';
import { avisar } from '../Toast';

export function DeudasPage() {
  const [pestana, setPestana] = useState<DebtDirection>('porPagar');
  const [editando, setEditando] = useState<Debt | null>(null);
  const cuentas = useLiveQuery(() => db.accounts.filter((a) => !a.archived).toArray(), []) ?? [];
  const deudas = useLiveQuery(() => db.debts.toArray(), []) ?? [];
  const [guardando, setGuardando] = useState(false);

  const monedaDe = (accountId: string) => cuentas.find((c) => c.id === accountId)?.currency ?? 'USD';
  const nombreCuenta = (accountId: string) => cuentas.find((c) => c.id === accountId)?.name ?? '?';
  const delaPestana = deudas.filter((deuda) => deuda.direction === pestana);
  const pendientes = delaPestana.filter((deuda) => deuda.status === 'pendiente')
    .sort((a, b) => b.createdIso.localeCompare(a.createdIso));
  const cerradas = delaPestana.filter((deuda) => deuda.status === 'cerrada')
    .sort((a, b) => (b.settledIso ?? '').localeCompare(a.settledIso ?? ''));

  async function marcarCerrada(deuda: Debt) {
    if (guardando) return;
    const verbo = deuda.direction === 'porPagar' ? 'pagada' : 'cobrada';
    const efecto = deuda.direction === 'porPagar' ? 'el gasto' : 'el ingreso';
    if (!window.confirm(`¿Marcar «${deuda.concepto}» como ${verbo}? Se creará ${efecto} en ${nombreCuenta(deuda.accountId)}.`)) return;
    setGuardando(true);
    try {
      await settleDebt(db, deuda.id, todayIso());
      avisar(`Deuda ${verbo} ✓`);
    } catch (e) {
      avisar((e as Error).message);
    } finally {
      setGuardando(false);
    }
  }

  async function eliminar(deuda: Debt) {
    if (guardando) return;
    if (!window.confirm(deuda.status === 'cerrada'
      ? '¿Eliminar esta deuda cerrada? Su movimiento NO se borra.'
      : '¿Eliminar esta deuda pendiente?')) return;
    setGuardando(true);
    try {
      await db.debts.delete(deuda.id);
      avisar('Deuda eliminada');
    } catch (e) {
      avisar((e as Error).message);
    } finally {
      setGuardando(false);
    }
  }

  const NUEVA = (): Debt => buildDebt({
    direction: pestana, concepto: 'nueva', amountMinor: 1,
    accountId: cuentas[0]?.id ?? '', nowIso: new Date().toISOString(),
  });

  return (
    <div>
      <h2>Deudas</h2>
      <div className="segmentado">
        <button className={pestana === 'porPagar' ? 'activo' : ''} onClick={() => setPestana('porPagar')}>Por pagar</button>
        <button className={pestana === 'porCobrar' ? 'activo' : ''} onClick={() => setPestana('porCobrar')}>Por cobrar</button>
      </div>
      {pestana === 'porCobrar' && (
        <p style={{ fontSize: 12, color: 'var(--texto2)' }}>
          Lo que te deben es informativo: no suma al patrimonio hasta cobrarse.
        </p>
      )}
      {pendientes.length === 0 && (
        <div className="vacio"><span className="icono">🤝</span>Sin deudas {pestana === 'porPagar' ? 'por pagar' : 'por cobrar'}.</div>
      )}
      {pendientes.map((deuda) => (
        <div key={deuda.id} className="tarjeta" style={{ marginTop: 8 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span>{deuda.concepto}</span>
            <span className="cifra">{formatMoney(deuda.amountMinor, monedaDe(deuda.accountId))}</span>
          </div>
          <p style={{ margin: '4px 0', fontSize: 12, color: 'var(--texto2)' }}>
            {nombreCuenta(deuda.accountId)} · registrada el {deuda.createdIso.slice(0, 10)}
          </p>
          <div className="fila-form" style={{ marginTop: 8 }}>
            <button className="boton secundario" onClick={() => setEditando(deuda)} disabled={guardando}>Editar</button>
            <button className="boton peligro" onClick={() => eliminar(deuda)} disabled={guardando}>Eliminar</button>
            <button className="boton" onClick={() => marcarCerrada(deuda)} disabled={guardando}>
              {deuda.direction === 'porPagar' ? 'Marcar pagada' : 'Marcar cobrada'}
            </button>
          </div>
        </div>
      ))}
      <button className="boton secundario" style={{ marginTop: 12 }} onClick={() => cuentas.length ? setEditando(NUEVA()) : avisar('No hay cuentas')}>
        Nueva deuda {pestana === 'porPagar' ? 'por pagar' : 'por cobrar'}
      </button>

      {cerradas.length > 0 && (
        <details style={{ marginTop: 16 }}>
          <summary>Historial ({cerradas.length})</summary>
          {cerradas.map((deuda) => (
            <div key={deuda.id} style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0', color: 'var(--texto2)' }}>
              <span>{deuda.concepto} · {deuda.settledIso}</span>
              <span>
                <span className="cifra">{formatMoney(deuda.amountMinor, monedaDe(deuda.accountId))}</span>
                <button className="chip" style={{ marginLeft: 6 }} aria-label={`Eliminar ${deuda.concepto}`} onClick={() => eliminar(deuda)}>eliminar</button>
              </span>
            </div>
          ))}
        </details>
      )}

      {editando && (
        <EditorDeuda
          original={deudas.some((deuda) => deuda.id === editando.id) ? editando : null}
          borrador={editando}
          cerrar={() => setEditando(null)}
        />
      )}
    </div>
  );
}

function EditorDeuda({ original, borrador, cerrar }: { original: Debt | null; borrador: Debt; cerrar: () => void }) {
  const cuentas = useLiveQuery(() => db.accounts.filter((a) => !a.archived).toArray(), []) ?? [];
  const esNueva = original === null;
  const [concepto, setConcepto] = useState(esNueva ? '' : borrador.concepto);
  const [cuentaId, setCuentaId] = useState(borrador.accountId);
  const [direction, setDirection] = useState<DebtDirection>(borrador.direction);
  const [montoTxt, setMontoTxt] = useState(esNueva ? '' : (borrador.amountMinor / 100).toFixed(2).replace('.', ','));
  const [guardando, setGuardando] = useState(false);
  const moneda = cuentas.find((c) => c.id === cuentaId)?.currency ?? 'USD';

  function cambiarCuenta(nuevaId: string) {
    const monedaNueva = cuentas.find((c) => c.id === nuevaId)?.currency ?? 'USD';
    if (monedaNueva !== moneda && montoTxt !== '') {
      setMontoTxt('');
      avisar('La cuenta elegida usa otra moneda: vuelve a escribir el monto');
    }
    setCuentaId(nuevaId);
  }

  async function guardar() {
    if (guardando) return;
    const minor = parseVenezuelanAmount(montoTxt.replace(/,$/, ''));
    if (minor === null || minor <= 0) { avisar('Monto inválido'); return; }
    if (!concepto.trim()) { avisar('Ponle un concepto a la deuda'); return; }
    if (!cuentas.some((c) => c.id === cuentaId)) { avisar('Elige una cuenta'); return; }
    setGuardando(true);
    try {
      if (esNueva) {
        await db.debts.add(buildDebt({
          direction, concepto, amountMinor: minor, accountId: cuentaId, nowIso: new Date().toISOString(),
        }));
      } else {
        await db.debts.update(borrador.id, {
          direction, concepto: concepto.trim(), amountMinor: minor, accountId: cuentaId,
        });
      }
      avisar('Deuda guardada ✓');
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
        <h3>{esNueva ? 'Nueva deuda' : 'Editar deuda'}</h3>
        <div className="segmentado">
          <button className={direction === 'porPagar' ? 'activo' : ''} onClick={() => setDirection('porPagar')}>Debo</button>
          <button className={direction === 'porCobrar' ? 'activo' : ''} onClick={() => setDirection('porCobrar')}>Me deben</button>
        </div>
        <label>Concepto (persona o descripción)</label>
        <input aria-label="Concepto" value={concepto} onChange={(e) => setConcepto(e.target.value)} placeholder="Juan — préstamo almuerzo" />
        <label>Cuenta para {direction === 'porPagar' ? 'pagarla' : 'cobrarla'} (define la moneda)</label>
        <select aria-label="Cuenta de la deuda" value={cuentaId} onChange={(e) => cambiarCuenta(e.target.value)}>
          {cuentas.map((c) => (
            <option key={c.id} value={c.id}>{c.name} ({c.currency === 'VES' ? 'Bs' : '$'})</option>
          ))}
        </select>
        <label>Monto en {moneda === 'VES' ? 'Bs' : '$'} (coma decimal)</label>
        <input aria-label="Monto de la deuda" inputMode="decimal" value={montoTxt} onChange={(e) => setMontoTxt(e.target.value)} placeholder="0,00" />
        <div className="fila-form" style={{ marginTop: 12 }}>
          <button className="boton secundario" onClick={cerrar} disabled={guardando}>Cancelar</button>
          <button className="boton" onClick={guardar} disabled={guardando}>Guardar deuda</button>
        </div>
      </div>
    </div>
  );
}
