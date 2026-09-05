import { useMemo, useState } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { db } from '../../db/db';
import type { CalEvent } from '../../domain/types';
import type { OcurrenciaVista } from '../../domain/calendar';
import { descripcionRecurrencia, ocurrenciasEnRango } from '../../domain/calendar';
import { desasignarEvento, omitirOcurrencia, reanudarOcurrencia } from '../../db/eventOps';
import { formatMoney } from '../../domain/money';
import {
  celdasMes, diaCorto, fechaLarga, LETRAS_DIA, mesAnterior, mesSiguiente,
  monthOf, nombreMes, partsOf, primerDiaMes, todayIso, ultimoDiaMes,
} from '../../domain/dates';
import { avisar } from '../Toast';
import { EditorEvento } from '../componentes/EditorEvento';

const NUEVO = (startDate: string): CalEvent => ({
  id: crypto.randomUUID(), titulo: '', categoryId: '', startDate,
  recurrence: { kind: 'once' }, createdIso: new Date().toISOString(),
});

export function CalendarioPage({ params }: { params: URLSearchParams }) {
  const calEvents = useLiveQuery(() => db.calEvents.toArray(), []) ?? [];
  const txs = useLiveQuery(() => db.transactions.toArray(), []) ?? [];
  const cuentas = useLiveQuery(() => db.accounts.toArray(), []) ?? [];
  const categorias = useLiveQuery(() => db.categories.toArray(), []) ?? [];
  const [hoy] = useState(todayIso);
  const [mes, setMes] = useState(params.get('mes') ?? monthOf(params.get('dia') ?? hoy));
  const [diaSel, setDiaSel] = useState(() => {
    const pedido = params.get('dia');
    if (pedido && pedido.startsWith(mes)) return pedido;
    return hoy.startsWith(mes) ? hoy : primerDiaMes(mes);
  });
  const [editando, setEditando] = useState<CalEvent | null>(params.get('nuevo') === '1' ? NUEVO(hoy) : null);
  const [guardando, setGuardando] = useState(false);

  const ocurrencias = useMemo(
    () => ocurrenciasEnRango({
      eventos: calEvents, txs, accounts: cuentas,
      desde: primerDiaMes(mes), hasta: ultimoDiaMes(mes), hoy,
    }),
    [calEvents, txs, cuentas, mes, hoy],
  );
  const porFecha = useMemo(() => {
    const m = new Map<string, OcurrenciaVista[]>();
    for (const o of ocurrencias) m.set(o.date, [...(m.get(o.date) ?? []), o]);
    return m;
  }, [ocurrencias]);

  const monedaDe = (accountId?: string) => cuentas.find((c) => c.id === accountId)?.currency ?? 'USD';
  const nombreCuenta = (accountId?: string) => cuentas.find((c) => c.id === accountId)?.name;
  const categoriaDe = (categoryId: string) => categorias.find((c) => c.id === categoryId);

  function cambiarMes(m: string) {
    setMes(m);
    setDiaSel(monthOf(hoy) === m ? hoy : primerDiaMes(m));
  }

  async function accion(fn: () => Promise<unknown>, exito: string) {
    if (guardando) return;
    setGuardando(true);
    try { await fn(); avisar(exito); }
    catch (e) { avisar((e as Error).message); }
    finally { setGuardando(false); }
  }

  const ocurrenciasDelDia = porFecha.get(diaSel) ?? [];

  return (
    <div>
      <h2>Calendario</h2>

      <div className="nav-mes">
        <button aria-label="Mes anterior" onClick={() => cambiarMes(mesAnterior(mes))}>‹</button>
        <h3 className="titulo">{nombreMes(mes)}</h3>
        <button aria-label="Mes siguiente" onClick={() => cambiarMes(mesSiguiente(mes))}>›</button>
        {mes !== monthOf(hoy) && (
          <button className="chip" onClick={() => { setMes(monthOf(hoy)); setDiaSel(hoy); }}>hoy</button>
        )}
      </div>

      <div className="cal-cabecera">{LETRAS_DIA.map((l, i) => <span key={i}>{l}</span>)}</div>

      <div className="cal-grid">
        {celdasMes(mes).map(({ iso, enMes }) => {
          // Las omitidas no se pintan como punto: el usuario ya las canceló a propósito.
          const delDia = (porFecha.get(iso) ?? []).filter((o) => o.estado !== 'omitida');
          return (
            <button
              key={iso}
              className={['cal-dia', enMes ? '' : 'otro-mes', iso === hoy ? 'hoy' : '', iso === diaSel ? 'seleccionado' : ''].join(' ').trim()}
              onClick={() => setDiaSel(iso)}
            >
              <span className="num">{partsOf(iso).d}</span>
              <span className="puntos">
                {delDia.slice(0, 3).map((o) => (
                  <span
                    key={`${o.eventId}|${o.date}`}
                    className={`punto${o.estado === 'hecha' ? ' hecha' : ''}${o.estado === 'vencida' ? ' vencida' : ''}`}
                    style={{ background: categoriaDe(o.evento.categoryId)?.color ?? '#8b96ab' }}
                  />
                ))}
              </span>
              {delDia.length > 3 && <span className="mas">+{delDia.length - 3}</span>}
            </button>
          );
        })}
      </div>

      <div className="cal-lista">
        <h3>{fechaLarga(diaSel)}</h3>
        {ocurrenciasDelDia.length === 0 && (
          <div className="vacio"><span className="icono">🗓️</span>Nada este día</div>
        )}
        {ocurrenciasDelDia.map((o) => (
          <div key={`${o.eventId}|${o.date}`}>
            <div className={`fila-ocurrencia${o.estado === 'hecha' ? ' hecha' : ''}`}>
              <span style={{ fontSize: 22 }}>{categoriaDe(o.evento.categoryId)?.icon ?? '📅'}</span>
              <span className="detalle">
                <span className="nota">{o.evento.titulo}</span>
                <span className="meta">
                  {diaCorto(o.date)}{o.evento.accountId ? ` · ${nombreCuenta(o.evento.accountId)}` : ''}
                </span>
              </span>
              <span className="cifra">
                {o.estado === 'hecha'
                  ? o.asignadoPorMoneda.map((m) => formatMoney(m.totalMinor, m.currency)).join(' · ')
                  : (o.evento.amountMinor !== undefined ? formatMoney(o.evento.amountMinor, monedaDe(o.evento.accountId)) : '')}
              </span>
              <span className={`estado estado-${o.estado}`}>{o.estado}</span>
            </div>
            <div className="acciones">
              {(o.estado === 'pendiente' || o.estado === 'vencida') && (
                <>
                  <a className="chip" href={`#/nuevo?evento=${o.eventId}&fecha=${o.date}`}>registrar</a>
                  <button
                    className="chip" disabled={guardando}
                    onClick={() => accion(() => omitirOcurrencia(db, o.eventId, o.date), 'Ocurrencia omitida')}
                  >omitir</button>
                </>
              )}
              {o.estado === 'omitida' && (
                <button
                  className="chip" disabled={guardando}
                  onClick={() => accion(() => reanudarOcurrencia(db, o.eventId, o.date), 'Ocurrencia reanudada')}
                >reanudar</button>
              )}
              {o.estado === 'hecha' && o.txIds.map((txId) => {
                const tx = txs.find((t) => t.id === txId);
                return (
                  <span key={txId} className="chip" style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                    {tx ? `${tx.note ?? 'movimiento'} · ${formatMoney(tx.amountMinor, monedaDe(tx.accountId))}` : 'movimiento'}
                    <button
                      className="chip" disabled={guardando}
                      onClick={() => accion(() => desasignarEvento(db, txId), 'Movimiento desasignado')}
                    >quitar</button>
                  </span>
                );
              })}
              {o.fueraDeRegla && <span className="chip">fuera de la regla</span>}
            </div>
          </div>
        ))}
      </div>

      <button className="boton" style={{ marginTop: 12 }} onClick={() => setEditando(NUEVO(diaSel))}>Nuevo evento</button>

      <details style={{ marginTop: 16 }}>
        <summary>Todos los eventos ({calEvents.length})</summary>
        {calEvents.map((e) => (
          <div key={e.id} style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '6px 0' }}>
            <span style={{ fontSize: 20 }}>{categoriaDe(e.categoryId)?.icon ?? '📅'}</span>
            <span className="nota" style={{ flex: 1 }}>{e.titulo}</span>
            <span className="meta">{descripcionRecurrencia(e)}</span>
            <button className="chip" onClick={() => setEditando(e)}>editar</button>
          </div>
        ))}
      </details>

      {editando && (
        <EditorEvento
          original={calEvents.find((e) => e.id === editando.id) ?? null}
          borrador={editando}
          cerrar={() => setEditando(null)}
        />
      )}
    </div>
  );
}
