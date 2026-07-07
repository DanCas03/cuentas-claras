import type { Currency, Rate } from '../../domain/types';
import type { Patrimonio } from '../../domain/balances';
import { formatMoney } from '../../domain/money';
import { usdToVesMinor } from '../../domain/rates';
import { todayIso } from '../../domain/dates';

export function PatrimonioCard({ p, tasa, viewCurrency, onToggle }: {
  p: Patrimonio;
  tasa: Rate | null;
  viewCurrency: Currency;
  onToggle: () => void;
}) {
  const enVista = (usdMinor: number | null): string => {
    if (usdMinor === null) return 'sin tasa';
    if (viewCurrency === 'USD') return formatMoney(usdMinor, 'USD');
    if (!tasa) return 'sin tasa';
    return formatMoney(usdToVesMinor(usdMinor, tasa.vesPerUsd), 'VES');
  };

  return (
    <div className="tarjeta">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h3 style={{ margin: 0 }}>Patrimonio total</h3>
        <button className="chip" onClick={onToggle}>{viewCurrency === 'USD' ? 'ver en Bs' : 'ver en $'}</button>
      </div>
      <p className="cifra-grande" style={{ margin: '8px 0' }}>{enVista(p.totalUsdMinor)}</p>
      <p style={{ margin: '4px 0', color: 'var(--texto2)' }}>
        A disposición: <strong>{enVista(p.disposicionUsdMinor)}</strong> · Ahorro: <strong>{enVista(p.ahorroUsdMinor)}</strong>
      </p>
      {tasa ? (
        <p style={{ fontSize: 12, color: 'var(--texto2)' }}>
          Tasa {tasa.source} del {tasa.date}: {tasa.vesPerUsd} Bs/$
          {tasa.date !== todayIso() && <span className="chip" style={{ marginLeft: 6 }}>tasa vieja</span>}
        </p>
      ) : (
        <div className="banner aviso">Sin tasa configurada — regístrala en Ajustes → Tasas.</div>
      )}
      <div style={{ marginTop: 8 }}>
        {p.lines.map((l) => (
          <div key={l.account.id} style={{ display: 'flex', justifyContent: 'space-between', padding: '4px 0' }}>
            <span>{l.account.isSavings ? '🔒 ' : ''}{l.account.name}</span>
            <span>
              {formatMoney(l.balanceMinor, l.account.currency)}
              {l.account.currency === 'VES' && (
                <span style={{ color: 'var(--texto2)' }}>
                  {' '}≈ {l.balanceUsdMinor === null ? '—' : formatMoney(l.balanceUsdMinor, 'USD')}
                </span>
              )}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}
