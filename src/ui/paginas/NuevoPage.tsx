import { useEffect, useMemo, useRef, useState } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { db, getSettings, saveSettings } from '../../db/db';
import { buildManualTx } from '../../domain/builders';
import { parseVenezuelanAmount, formatMoney } from '../../domain/money';
import { todayIso, fechaCorta } from '../../domain/dates';
import type { CalEvent } from '../../domain/types';
import type { Ocurrencia } from '../../domain/calendar';
import { aplicarAsignacion, candidatasParaTx, sugerirOcurrencia } from '../../domain/eventMatch';
import { Teclado } from '../componentes/Teclado';
import { CategoriaGrid } from '../componentes/CategoriaGrid';
import { TransferForm } from '../componentes/TransferForm';
import { AjusteForm } from '../componentes/AjusteForm';
import { avisar } from '../Toast';
import { navigate } from '../useHashRoute';

type Modo = 'gasto' | 'ingreso' | 'transferir' | 'ajustar';

export function NuevoPage({ params }: { params: URLSearchParams }) {
  const [modo, setModo] = useState<Modo>(params.get('tipo') === 'ingreso' ? 'ingreso' : 'gasto');
  const [entrada, setEntrada] = useState('');
  const [nota, setNota] = useState('');
  const [cuentaId, setCuentaId] = useState<string | null>(null);
  const cuentas = useLiveQuery(() => db.accounts.filter((a) => !a.archived).toArray(), []) ?? [];
  const categorias = useLiveQuery(() => db.categories.toArray(), []) ?? [];
  const calEvents = useLiveQuery(() => db.calEvents.toArray(), []) ?? [];
  const txsAsignadas = useLiveQuery(() => db.transactions.where('eventId').above('').toArray(), []) ?? [];
  const [hoy] = useState(todayIso);
  const [eventoActivo, setEventoActivo] = useState<Ocurrencia | null>(null);
  const [sugerida, setSugerida] = useState<Ocurrencia | null>(null);
  const prefillHecho = useRef(false);

  // Prefill de captura (v2): solo al montar; lo que el usuario edite después manda.
  useEffect(() => {
    const monto = params.get('monto');
    if (monto && /^\d+$/.test(monto)) {
      setEntrada((Number(monto) / 100).toFixed(2).replace('.', ','));
    }
    const notaParam = params.get('nota');
    if (notaParam) setNota(notaParam);
    const cuentaParam = params.get('cuenta');
    if (cuentaParam) setCuentaId(cuentaParam);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    getSettings(db).then((s) => setCuentaId((prev) => prev ?? s.lastAccountId ?? null));
  }, []);

  // Deep link `?evento&fecha` (SemanaCard / Calendario) y sugerencia tras una captura.
  // Se aplica UNA sola vez, cuando eventos y categorías ya cargaron.
  useEffect(() => {
    if (prefillHecho.current) return;
    if (calEvents.length === 0 || categorias.length === 0 || cuentas.length === 0) return;
    const eventoParam = params.get('evento');
    const fechaParam = params.get('fecha');
    if (eventoParam && fechaParam) {
      const evento = calEvents.find((e) => e.id === eventoParam);
      const kind = evento && categorias.find((c) => c.id === evento.categoryId)?.kind;
      if (evento && kind) {
        prefillHecho.current = true;
        setEventoActivo({ eventId: evento.id, date: fechaParam });
        setModo(kind === 'income' ? 'ingreso' : 'gasto');
        if (evento.accountId) setCuentaId(evento.accountId);
        if (!params.get('monto') && evento.amountMinor !== undefined) {
          setEntrada((evento.amountMinor / 100).toFixed(2).replace('.', ','));
        }
        return;
      }
    }
    // Sin `?evento`: si vino de captura con monto y cuenta, se ofrece la sugerencia (no activa).
    const montoParam = Number(params.get('monto') ?? '0');
    const cuentaParam = params.get('cuenta') ?? cuentas[0]?.id;
    if (montoParam > 0 && cuentaParam) {
      prefillHecho.current = true;
      const o = sugerirOcurrencia(
        {
          type: params.get('tipo') === 'ingreso' ? 'income' : 'expense',
          date: hoy, accountId: cuentaParam, amountMinor: montoParam,
        },
        { eventos: calEvents, txs: txsAsignadas, accounts: cuentas, categories: categorias, hoy },
      );
      if (o) setSugerida({ eventId: o.eventId, date: o.date });
    }
  }, [calEvents, categorias, cuentas, txsAsignadas, params, hoy]);

  const cuenta = cuentas.find((c) => c.id === cuentaId) ?? cuentas[0];
  const minor = parseVenezuelanAmount(entrada.replace(/,$/, ''));
  const [guardando, setGuardando] = useState(false);

  const ctxEventos = useMemo(
    () => ({ eventos: calEvents, txs: txsAsignadas, accounts: cuentas, categories: categorias, hoy }),
    [calEvents, txsAsignadas, cuentas, categorias, hoy],
  );

  const candidatas = useMemo(() => {
    if (!cuenta || (modo !== 'gasto' && modo !== 'ingreso')) return [];
    return candidatasParaTx(
      { type: modo === 'gasto' ? 'expense' : 'income', date: hoy, accountId: cuenta.id, amountMinor: minor ?? 0 },
      ctxEventos,
    ).slice(0, 4);
  }, [cuenta, modo, hoy, minor, ctxEventos]);

  const mismaOcurrencia = (a: Ocurrencia | null, b: Ocurrencia | null) =>
    a !== null && b !== null && a.eventId === b.eventId && a.date === b.date;

  /** Chips a pintar: la sugerida/activa primero, luego las candidatas. Máximo 4 + la activa. */
  const chips = useMemo(() => {
    const lista = candidatas.map((c) => ({
      o: { eventId: c.ocurrencia.eventId, date: c.ocurrencia.date } as Ocurrencia,
      evento: c.ocurrencia.evento,
      vencida: c.ocurrencia.estado === 'vencida',
    }));
    for (const fijada of [sugerida, eventoActivo]) {
      if (!fijada || lista.some((x) => mismaOcurrencia(x.o, fijada))) continue;
      const evento = calEvents.find((e) => e.id === fijada.eventId);
      if (evento) lista.unshift({ o: fijada, evento, vencida: fijada.date < hoy });
    }
    // la sugerida (o la activa) siempre primera
    const primera = eventoActivo ?? sugerida;
    if (primera) {
      const i = lista.findIndex((x) => mismaOcurrencia(x.o, primera));
      if (i > 0) lista.unshift(...lista.splice(i, 1));
    }
    return lista;
  }, [candidatas, sugerida, eventoActivo, calEvents, hoy]);

  function onTecla(t: string) {
    setEntrada((e) => {
      if (t === '⌫') return e.slice(0, -1);
      if (t === ',') return e === '' || e.includes(',') ? e : `${e},`;
      const dec = e.split(',')[1];
      if (dec !== undefined && dec.length >= 2) return e; // máximo 2 decimales
      return e + t;
    });
  }

  async function guardar(categoryId: string, o?: Ocurrencia | null, evento?: CalEvent | null) {
    if (guardando) return;
    if (!cuenta || minor === null || minor <= 0) return;
    setGuardando(true);
    try {
      const base = buildManualTx({
        type: modo === 'gasto' ? 'expense' : 'income',
        accountId: cuenta.id,
        amountMinor: minor,
        categoryId,
        dateIso: todayIso(),
        note: nota.trim() || undefined,
      });
      // La asignación NO altera monto, fecha, cuenta ni tipo.
      const tx = o && evento ? { ...aplicarAsignacion(base, o, evento), categoryId } : base;
      await db.transactions.add(tx);
      await saveSettings(db, { lastAccountId: cuenta.id });
      avisar('Guardado ✓');
      navigate('/');
    } catch (e) {
      avisar((e as Error).message);
    } finally {
      setGuardando(false);
    }
  }

  return (
    <div>
      <div className="segmentado">
        <button className={modo === 'gasto' ? 'activo' : ''} onClick={() => setModo('gasto')}>Gasto</button>
        <button className={modo === 'ingreso' ? 'activo' : ''} onClick={() => setModo('ingreso')}>Ingreso</button>
        <button className={modo === 'transferir' ? 'activo' : ''} onClick={() => setModo('transferir')}>Transferir</button>
        <button className={modo === 'ajustar' ? 'activo' : ''} onClick={() => setModo('ajustar')}>Ajustar</button>
      </div>
      {modo === 'transferir' && <TransferForm />}
      {modo === 'ajustar' && <AjusteForm />}
      {(modo === 'gasto' || modo === 'ingreso') && (
        <>
          <label>Cuenta</label>
          <select aria-label="Cuenta" value={cuenta?.id ?? ''} onChange={(e) => setCuentaId(e.target.value)}>
            {cuentas.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name} ({c.currency === 'VES' ? 'Bs' : '$'})
              </option>
            ))}
          </select>
          <div className={`monto-display ${modo}`}>
            {minor !== null && minor > 0 && cuenta
              ? formatMoney(modo === 'gasto' ? -minor : minor, cuenta.currency)
              : '—'}
          </div>
          <Teclado onTecla={onTecla} />
          <label>Nota (opcional)</label>
          <input aria-label="Nota" value={nota} onChange={(e) => setNota(e.target.value)} placeholder="¿En qué fue?" />
          {chips.length > 0 && (
            <div className="chips-eventos">
              {chips.map(({ o, evento, vencida }) => {
                const activo = mismaOcurrencia(o, eventoActivo);
                const icono = categorias.find((c) => c.id === evento.categoryId)?.icon ?? '📅';
                return (
                  <button
                    key={`${o.eventId}|${o.date}`}
                    type="button"
                    disabled={guardando}
                    className={['chip-evento', activo ? 'activo' : '', mismaOcurrencia(o, sugerida) && !activo ? 'sugerido' : ''].join(' ').trim()}
                    onClick={() => {
                      if (activo) { setEventoActivo(null); return; }        // ✕ desmarca
                      if (minor !== null && minor > 0) {
                        void guardar(evento.categoryId, o, evento);          // 1 toque: guarda y asigna
                      } else {
                        setEventoActivo(o);
                        avisar('Escribe el monto');
                      }
                    }}
                  >
                    <span>{icono} {evento.titulo} · {fechaCorta(o.date)}{vencida ? ' · vencida' : ''}</span>
                    {activo && <span className="cerrar">✕</span>}
                  </button>
                );
              })}
            </div>
          )}
          <label>Toca la categoría para guardar</label>
          <CategoriaGrid
            categorias={categorias.filter((c) => c.kind === (modo === 'gasto' ? 'expense' : 'income'))}
            onElegir={(categoryId) => {
              const activo = eventoActivo ? chips.find((x) => mismaOcurrencia(x.o, eventoActivo)) : undefined;
              void guardar(categoryId, activo?.o ?? null, activo?.evento ?? null);
            }}
          />
        </>
      )}
    </div>
  );
}
