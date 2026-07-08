import { useLiveQuery } from 'dexie-react-hooks';
import { db, getSettings, saveSettings } from '../../db/db';
import { patrimonio } from '../../domain/balances';
import { effectiveRate } from '../../domain/rates';
import { todayIso } from '../../domain/dates';
import { PatrimonioCard } from '../componentes/PatrimonioCard';
import { monthSummary, lastMonths, budgetStatus } from '../../domain/stats';
import { monthOf } from '../../domain/dates';
import { GraficasMes } from '../componentes/GraficasMes';

export function DashboardPage() {
  const cuentas = useLiveQuery(() => db.accounts.toArray(), []) ?? [];
  const txs = useLiveQuery(() => db.transactions.toArray(), []) ?? [];
  const rates = useLiveQuery(() => db.rates.toArray(), []) ?? [];
  const settings = useLiveQuery(() => getSettings(db), []);
  const categorias = useLiveQuery(() => db.categories.toArray(), []) ?? [];

  if (!settings) return <h2>Inicio</h2>;
  const tasa = effectiveRate(todayIso(), settings.rateSource, rates);
  const p = patrimonio(cuentas, txs, tasa?.vesPerUsd ?? null);
  const mesActual = monthOf(todayIso());
  const base = { txs, accounts: cuentas, rates, activeSource: settings.rateSource };
  const resumen = monthSummary({ ...base, month: mesActual, viewCurrency: settings.viewCurrency });
  const comparativa = lastMonths(mesActual, 4)
    .map((m) => monthSummary({ ...base, month: m, viewCurrency: settings.viewCurrency }))
    .reverse();
  const presupuestos = budgetStatus({ ...base, categories: categorias, month: mesActual });

  return (
    <div>
      <h2>Inicio</h2>
      <PatrimonioCard
        p={p}
        tasa={tasa}
        viewCurrency={settings.viewCurrency}
        onToggle={() => saveSettings(db, { viewCurrency: settings.viewCurrency === 'USD' ? 'VES' : 'USD' })}
      />
      <GraficasMes resumen={resumen} comparativa={comparativa} presupuestos={presupuestos} categorias={categorias} viewCurrency={settings.viewCurrency} />
    </div>
  );
}
