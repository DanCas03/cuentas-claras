import type { Account, Currency, Debt, Rate } from '../../domain/types';
import { debtTotals } from '../../domain/debts';
import { formatMoney } from '../../domain/money';
import { usdToVesMinor } from '../../domain/rates';

export function DeudasCard({ deudas, cuentas, tasa, viewCurrency }: {
  deudas: Debt[];
  cuentas: Account[];
  tasa: Rate | null;
  viewCurrency: Currency;
}) {
  const pendientes = deudas.filter((deuda) => deuda.status === 'pendiente');
  const totales = debtTotals(pendientes, cuentas, tasa?.vesPerUsd ?? null);
  const monedaDe = (accountId: string) => cuentas.find((c) => c.id === accountId)?.currency ?? 'USD';

  const enVista = (usdMinor: number | null): string => {
    if (usdMinor === null) return 'sin tasa';
    if (viewCurrency === 'USD') return formatMoney(usdMinor, 'USD');
    if (!tasa) return 'sin tasa';
    return formatMoney(usdToVesMinor(usdMinor, tasa.vesPerUsd), 'VES');
  };

  const recientes = [...pendientes].sort((a, b) => b.createdIso.localeCompare(a.createdIso)).slice(0, 3);

  return (
    <div className="tarjeta" style={{ marginTop: 12 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h3 style={{ margin: 0 }}>Deudas</h3>
        <a className="chip" href="#/deudas">ver todas</a>
      </div>
      <p style={{ margin: '8px 0 4px', color: 'var(--texto2)' }}>
        Debes <strong className="cifra">{enVista(totales.debesUsdMinor)}</strong>
        {' · '}Te deben <strong className="cifra">{enVista(totales.teDebenUsdMinor)}</strong>
      </p>
      {recientes.length === 0 ? (
        <p style={{ margin: '4px 0', fontSize: 12, color: 'var(--texto2)' }}>Sin deudas pendientes.</p>
      ) : (
        recientes.map((deuda) => (
          <div key={deuda.id} style={{ display: 'flex', justifyContent: 'space-between', padding: '4px 0' }}>
            <span>{deuda.direction === 'porPagar' ? '📤' : '📥'} {deuda.concepto}</span>
            <span className="cifra">{formatMoney(deuda.amountMinor, monedaDe(deuda.accountId))}</span>
          </div>
        ))
      )}
    </div>
  );
}
