import { useState } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { db, getSettings, saveSettings } from '../../db/db';
import type { RateSource } from '../../domain/types';
import { effectiveRate } from '../../domain/rates';
import { todayIso } from '../../domain/dates';
import { fetchRatesWithFallback, storeFetchedRates } from '../../rates/providers';
import { avisar } from '../Toast';

export function TasasSection() {
  // Important #4: "Vigente hoy" debe calcularse sobre el histórico COMPLETO (igual que
  // Dashboard/stats/TransferForm) — la ventana de 14 filas es solo para la tabla de abajo.
  // Binance solo se alimenta por override manual, así que tras ~2 semanas su fila sale de
  // esa ventana y "vigente" caía al fallback "cualquiera" mostrando un BCV que no es la
  // tasa con la que el resto de la app convierte.
  const ratesRecientes = useLiveQuery(() => db.rates.orderBy('date').reverse().limit(14).toArray(), []) ?? [];
  const ratesTodas = useLiveQuery(() => db.rates.toArray(), []) ?? [];
  const settings = useLiveQuery(() => getSettings(db), []);
  const [fecha, setFecha] = useState(todayIso());
  const [valorTxt, setValorTxt] = useState('');
  const [fuenteManual, setFuenteManual] = useState<RateSource>('BINANCE');
  const [buscando, setBuscando] = useState(false);
  const [guardando, setGuardando] = useState(false);
  if (!settings) return null;

  const vigente = effectiveRate(todayIso(), settings.rateSource, ratesTodas);

  async function actualizarAhora() {
    if (buscando) return;
    setBuscando(true);
    try {
      const n = await storeFetchedRates(db, todayIso(), await fetchRatesWithFallback());
      avisar(n > 0 ? 'Tasa BCV actualizada ✓' : 'No se pudo obtener el BCV ahora — usa la tasa manual');
    } catch (e) {
      avisar((e as Error).message);
    } finally {
      setBuscando(false);
    }
  }

  async function guardarManual() {
    if (guardando) return;
    const v = Number(valorTxt.replace(',', '.'));
    if (!Number.isFinite(v) || v <= 0 || !/^\d{4}-\d{2}-\d{2}$/.test(fecha)) {
      avisar('Tasa o fecha inválida');
      return;
    }
    setGuardando(true);
    try {
      await db.rates.put({ date: fecha, source: fuenteManual, vesPerUsd: v });
      setValorTxt('');
      avisar('Tasa guardada ✓');
    } catch (e) {
      avisar((e as Error).message);
    } finally {
      setGuardando(false);
    }
  }

  return (
    <div>
      <p>
        Vigente hoy: <strong>{vigente ? `${vigente.vesPerUsd} Bs/$ (${vigente.source} ${vigente.date})` : 'sin tasa'}</strong>
      </p>
      <label>Fuente activa para conversiones</label>
      <select
        aria-label="Fuente de tasa"
        value={settings.rateSource}
        onChange={(e) => saveSettings(db, { rateSource: e.target.value as RateSource })}
      >
        <option value="BCV">BCV</option>
        <option value="BINANCE">Binance USDT/VES</option>
        <option value="MANUAL">Manual</option>
      </select>
      <button className="boton secundario" style={{ marginTop: 8 }} onClick={actualizarAhora} disabled={buscando}>
        {buscando ? 'Buscando…' : 'Actualizar BCV ahora'}
      </button>
      <p style={{ marginTop: 8 }}>
        Solo el <strong>BCV</strong> se actualiza online. <strong>Binance USDT/VES no tiene proveedor automático</strong>:
        fíjalo tú abajo (fuente «Binance USDT/VES») y quedará guardado en el histórico.
      </p>
      <label>Registrar/corregir tasa (Bs por $)</label>
      <div className="fila-form">
        <input aria-label="Fecha de tasa" type="date" value={fecha} onChange={(e) => setFecha(e.target.value)} />
        <select
          aria-label="Fuente de la tasa manual"
          value={fuenteManual}
          onChange={(e) => setFuenteManual(e.target.value as RateSource)}
        >
          <option value="BINANCE">Binance USDT/VES</option>
          <option value="BCV">BCV</option>
          <option value="MANUAL">Manual (override del día)</option>
        </select>
      </div>
      <div className="fila-form" style={{ marginTop: 8 }}>
        <input aria-label="Valor de tasa" inputMode="decimal" placeholder="ej. 111,02" value={valorTxt} onChange={(e) => setValorTxt(e.target.value)} />
        <button className="boton" onClick={guardarManual} disabled={guardando}>Guardar</button>
      </div>
      <h4 style={{ margin: '12px 0 4px' }}>Histórico reciente</h4>
      <table className="tabla">
        <thead><tr><th>Fecha</th><th>Fuente</th><th>Bs/$</th></tr></thead>
        <tbody>
          {ratesRecientes.map((r) => (
            <tr key={`${r.date}-${r.source}`}>
              <td>{r.date}</td>
              <td><span className="chip">{r.source}</span></td>
              <td>{r.vesPerUsd}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
