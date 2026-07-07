import { useLiveQuery } from 'dexie-react-hooks';
import { db, getSettings, saveSettings } from '../../db/db';
import { patrimonio } from '../../domain/balances';
import { effectiveRate } from '../../domain/rates';
import { todayIso } from '../../domain/dates';
import { PatrimonioCard } from '../componentes/PatrimonioCard';

export function DashboardPage() {
  const cuentas = useLiveQuery(() => db.accounts.toArray(), []) ?? [];
  const txs = useLiveQuery(() => db.transactions.toArray(), []) ?? [];
  const rates = useLiveQuery(() => db.rates.toArray(), []) ?? [];
  const settings = useLiveQuery(() => getSettings(db), []);

  if (!settings) return <h2>Inicio</h2>;
  const tasa = effectiveRate(todayIso(), settings.rateSource, rates);
  const p = patrimonio(cuentas, txs, tasa?.vesPerUsd ?? null);

  return (
    <div>
      <h2>Inicio</h2>
      <PatrimonioCard
        p={p}
        tasa={tasa}
        viewCurrency={settings.viewCurrency}
        onToggle={() => saveSettings(db, { viewCurrency: settings.viewCurrency === 'USD' ? 'VES' : 'USD' })}
      />
      {/* La Tarea 12 añade aquí <GraficasMes /> */}
    </div>
  );
}
