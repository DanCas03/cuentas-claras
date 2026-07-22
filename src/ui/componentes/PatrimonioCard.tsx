import { useState } from 'react';
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
  const [vistaActiva, setVistaActiva] = useState(0);
  const vistas = [
    { clave: 'disposicion', titulo: 'A disposición', usdMinor: p.aDisposicionUsdMinor },
    { clave: 'neto', titulo: 'Total neto', usdMinor: p.netoUsdMinor },
    { clave: 'total', titulo: 'Total actual', usdMinor: p.totalUsdMinor },
  ] as const;

  const enVista = (usdMinor: number | null): string => {
    if (usdMinor === null) return 'sin tasa';
    if (viewCurrency === 'USD') return formatMoney(usdMinor, 'USD');
    if (!tasa) return 'sin tasa';
    return formatMoney(usdToVesMinor(usdMinor, tasa.vesPerUsd), 'VES');
  };

  return (
    <div className="tarjeta">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h3 style={{ margin: 0 }}>Patrimonio</h3>
        <button className="chip" onClick={onToggle}>{viewCurrency === 'USD' ? 'ver en Bs' : 'ver en $'}</button>
      </div>
      <div
        className="slider-patrimonio"
        onScroll={(e) => {
          const el = e.currentTarget;
          setVistaActiva(Math.min(2, Math.max(0, Math.round(el.scrollLeft / el.clientWidth))));
        }}
      >
        {vistas.map((v) => (
          <div key={v.clave} className="vista">
            <p className="titulo-vista">{v.titulo}</p>
            <p className="cifra-grande" style={{ margin: '4px 0' }}>{enVista(v.usdMinor)}</p>
          </div>
        ))}
      </div>
      <div className="slider-puntos" aria-hidden="true">
        {vistas.map((v, i) => <span key={v.clave} className={i === vistaActiva ? 'activo' : ''} />)}
      </div>
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
