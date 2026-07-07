import { useState } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { db } from '../../db/db';
import { buildAdjustment } from '../../domain/builders';
import { accountBalanceMinor } from '../../domain/balances';
import { parseVenezuelanAmount, formatMoney } from '../../domain/money';
import { todayIso } from '../../domain/dates';
import { avisar } from '../Toast';
import { navigate } from '../useHashRoute';

export function AjusteForm() {
  const cuentas = useLiveQuery(() => db.accounts.filter((a) => !a.archived).toArray(), []) ?? [];
  const txs = useLiveQuery(() => db.transactions.toArray(), []) ?? [];
  const [cuentaId, setCuentaId] = useState('acc-binance');
  const [realTxt, setRealTxt] = useState('');
  const [guardando, setGuardando] = useState(false);

  const cuenta = cuentas.find((c) => c.id === cuentaId);
  const actual = cuenta ? accountBalanceMinor(cuenta.id, txs) : 0;
  const real = parseVenezuelanAmount(realTxt.replace(/,$/, ''));
  const delta = real !== null && real >= 0 ? real - actual : null;
  const esSnapshot = cuenta?.kind === 'snapshot';

  async function guardar() {
    if (guardando) return;
    if (!cuenta || real === null || delta === null || delta === 0) return;
    setGuardando(true);
    try {
      const tx = buildAdjustment({
        accountId: cuenta.id, currentBalanceMinor: actual, realBalanceMinor: real, dateIso: todayIso(),
      });
      await db.transactions.add(tx);
      avisar('Saldo ajustado ✓');
      navigate('/');
    } catch (e) {
      avisar((e as Error).message);
    } finally {
      setGuardando(false);
    }
  }

  return (
    <div>
      <label>Cuenta</label>
      <select aria-label="Cuenta a ajustar" value={cuentaId} onChange={(e) => setCuentaId(e.target.value)}>
        {cuentas.map((c) => (
          <option key={c.id} value={c.id}>{c.name} ({c.currency === 'VES' ? 'Bs' : '$'})</option>
        ))}
      </select>
      {cuenta && (
        <p>
          Saldo según la app: <strong>{formatMoney(actual, cuenta.currency)}</strong>
        </p>
      )}
      <label>Saldo real actual</label>
      <input aria-label="Saldo real" inputMode="decimal" value={realTxt} onChange={(e) => setRealTxt(e.target.value)} placeholder="0,00" />
      {cuenta && delta !== null && delta !== 0 && (
        <div className={`banner ${delta > 0 ? 'ok' : 'aviso'}`}>
          {esSnapshot ? 'Variación/rendimiento' : 'Diferencia'}: {formatMoney(delta, cuenta.currency)}
        </div>
      )}
      <button className="boton" style={{ marginTop: 12 }} onClick={guardar} disabled={real === null || delta === null || delta === 0 || guardando}>
        Ajustar saldo
      </button>
    </div>
  );
}
