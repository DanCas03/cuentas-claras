import { useState } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { db, getSettings } from '../../db/db';
import { buildTransfer } from '../../domain/builders';
import { effectiveRate, convertMinor } from '../../domain/rates';
import { parseVenezuelanAmount, formatMoney } from '../../domain/money';
import { todayIso } from '../../domain/dates';
import { avisar } from '../Toast';
import { navigate } from '../useHashRoute';

export function TransferForm() {
  const cuentas = useLiveQuery(() => db.accounts.filter((a) => !a.archived).toArray(), []) ?? [];
  const rates = useLiveQuery(() => db.rates.toArray(), []) ?? [];
  const settings = useLiveQuery(() => getSettings(db), []);
  const [origenId, setOrigenId] = useState('acc-efectivo');
  const [destinoId, setDestinoId] = useState('acc-bnc');
  const [montoTxt, setMontoTxt] = useState('');
  const [tasaTxt, setTasaTxt] = useState('');
  const [nota, setNota] = useState('');
  const [guardando, setGuardando] = useState(false);

  const origen = cuentas.find((c) => c.id === origenId);
  const destino = cuentas.find((c) => c.id === destinoId);
  const monto = parseVenezuelanAmount(montoTxt.replace(/,$/, ''));
  const cruza = !!origen && !!destino && origen.currency !== destino.currency;
  const tasaDelDia = settings ? effectiveRate(todayIso(), settings.rateSource, rates) : null;
  const tasa = tasaTxt.trim() !== '' ? Number(tasaTxt.replace(',', '.')) : tasaDelDia?.vesPerUsd;
  const destinoMinor =
    cruza && monto !== null && monto > 0 && tasa && tasa > 0 && origen && destino
      ? convertMinor(monto, origen.currency, destino.currency, tasa)
      : null;

  async function guardar() {
    if (guardando) return;
    if (!origen || !destino || monto === null || monto <= 0) return;
    setGuardando(true);
    try {
      const tx = buildTransfer({
        fromAccount: origen, toAccount: destino, amountMinor: monto,
        dateIso: todayIso(), vesPerUsd: cruza ? tasa : undefined,
        note: nota.trim() || undefined,
      });
      await db.transactions.add(tx);
      avisar('Transferencia guardada ✓');
      navigate('/');
    } catch (e) {
      avisar((e as Error).message);
    } finally {
      setGuardando(false);
    }
  }

  const opciones = (excluir: string) =>
    cuentas.filter((c) => c.id !== excluir).map((c) => (
      <option key={c.id} value={c.id}>{c.name} ({c.currency === 'VES' ? 'Bs' : '$'})</option>
    ));

  return (
    <div>
      <label>Desde</label>
      <select aria-label="Cuenta origen" value={origenId} onChange={(e) => setOrigenId(e.target.value)}>
        {opciones(destinoId)}
      </select>
      <label>Hacia</label>
      <select aria-label="Cuenta destino" value={destinoId} onChange={(e) => setDestinoId(e.target.value)}>
        {opciones(origenId)}
      </select>
      <label>Monto en {origen?.currency === 'VES' ? 'Bs' : '$'}</label>
      <input aria-label="Monto" inputMode="decimal" value={montoTxt} onChange={(e) => setMontoTxt(e.target.value)} placeholder="0,00" />
      {cruza && (
        <>
          <label>Tasa (Bs por $) — propuesta {tasaDelDia ? `${tasaDelDia.vesPerUsd} del ${tasaDelDia.date}` : 'no disponible'}</label>
          <input
            aria-label="Tasa"
            inputMode="decimal"
            value={tasaTxt}
            onChange={(e) => setTasaTxt(e.target.value)}
            placeholder={tasaDelDia ? String(tasaDelDia.vesPerUsd) : 'ej. 111,02'}
          />
          {destinoMinor !== null && destino && (
            <div className="banner ok">Destino recibirá {formatMoney(destinoMinor, destino.currency)}</div>
          )}
        </>
      )}
      <label>Nota (opcional)</label>
      <input aria-label="Nota de transferencia" value={nota} onChange={(e) => setNota(e.target.value)} />
      <button className="boton" style={{ marginTop: 12 }} onClick={guardar} disabled={monto === null || monto <= 0 || (cruza && (!tasa || tasa <= 0)) || guardando}>
        Transferir
      </button>
    </div>
  );
}
