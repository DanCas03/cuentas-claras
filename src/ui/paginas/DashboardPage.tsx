import { useMemo, useState } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { db, getSettings, saveSettings } from '../../db/db';
import { patrimonio } from '../../domain/balances';
import { effectiveRate } from '../../domain/rates';
import { todayIso, monthOf, addDays } from '../../domain/dates';
import { ocurrenciasEnRango } from '../../domain/calendar';
import { PatrimonioCard } from '../componentes/PatrimonioCard';
import { SemanaCard } from '../componentes/SemanaCard';
import { monthSummary, lastMonths, budgetStatus } from '../../domain/stats';
import { GraficasMes } from '../componentes/GraficasMes';
import { DeudasCard } from '../componentes/DeudasCard';
import { txsEfectivas } from '../../domain/groups';

export function DashboardPage() {
  const cuentas = useLiveQuery(() => db.accounts.toArray(), []) ?? [];
  const txs = useLiveQuery(() => db.transactions.toArray(), []) ?? [];
  const rates = useLiveQuery(() => db.rates.toArray(), []) ?? [];
  const settings = useLiveQuery(() => getSettings(db), []);
  const categorias = useLiveQuery(() => db.categories.toArray(), []) ?? [];
  const deudas = useLiveQuery(() => db.debts.toArray(), []) ?? [];
  const grupos = useLiveQuery(() => db.txGroups.toArray(), []) ?? [];
  const calEvents = useLiveQuery(() => db.calEvents.toArray(), []) ?? [];
  const [hoy] = useState(todayIso);
  const [mesVisto, setMesVisto] = useState(() => monthOf(hoy));
  // Inicio muestra 7 días rodantes más las vencidas de los últimos 30 (decisión 13).
  const ocurrenciasSemana = useMemo(
    () => ocurrenciasEnRango({
      eventos: calEvents, txs, accounts: cuentas,
      desde: addDays(hoy, -30), hasta: addDays(hoy, 6), hoy,
    }),
    [calEvents, txs, cuentas, hoy],
  );

  if (!settings) return <h2>Inicio</h2>;
  const tasa = effectiveRate(hoy, settings.rateSource, rates);
  const p = patrimonio(
    cuentas, txs, tasa?.vesPerUsd ?? null,
    deudas.filter((deuda) => deuda.status === 'pendiente' && deuda.direction === 'porPagar'),
  );
  const mesActual = monthOf(hoy);
  const primerMes = txs.length > 0
    ? monthOf(txs.reduce((min, t) => (t.date < min ? t.date : min), txs[0].date))
    : mesActual;
  // El navegador de mes gobierna las TRES tarjetas; nunca sale del rango [primerMes, mesActual].
  const mes = mesVisto > mesActual ? mesActual : mesVisto < primerMes ? primerMes : mesVisto;
  const efectivas = txsEfectivas(txs, cuentas, grupos, categorias);
  const base = { txs: efectivas, accounts: cuentas, rates, activeSource: settings.rateSource };
  const resumen = monthSummary({ ...base, month: mes, viewCurrency: settings.viewCurrency });
  const comparativa = lastMonths(mes, 4)
    .map((m) => monthSummary({ ...base, month: m, viewCurrency: settings.viewCurrency }))
    .reverse();
  const presupuestos = budgetStatus({ ...base, categories: categorias, month: mes });

  return (
    <div>
      <h2>Inicio</h2>
      <PatrimonioCard
        p={p}
        tasa={tasa}
        viewCurrency={settings.viewCurrency}
        onToggle={() => saveSettings(db, { viewCurrency: settings.viewCurrency === 'USD' ? 'VES' : 'USD' })}
      />
      <SemanaCard ocurrencias={ocurrenciasSemana} cuentas={cuentas} categorias={categorias} hoy={hoy} />
      <DeudasCard deudas={deudas} cuentas={cuentas} tasa={tasa} viewCurrency={settings.viewCurrency} />
      <GraficasMes
        resumen={resumen} comparativa={comparativa} presupuestos={presupuestos}
        categorias={categorias} viewCurrency={settings.viewCurrency}
        mes={mes} mesActual={mesActual} primerMes={primerMes} onMes={setMesVisto}
      />
    </div>
  );
}
