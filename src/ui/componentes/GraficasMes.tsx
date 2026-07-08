import { Area, AreaChart, Bar, BarChart, Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import type { Category, Currency } from '../../domain/types';
import type { EstadoPresupuesto, ResumenMes } from '../../domain/stats';
import { formatMoney } from '../../domain/money';

// Estilos de gráficas alineados a los tokens del sistema (Recharts no lee CSS vars en todos los props)
const ESTILO_TOOLTIP = {
  background: '#182338',
  border: '1px solid #223049',
  borderRadius: 12,
  color: '#e8edf6',
} as const;
const EJE = { tick: { fill: '#8b96ab', fontSize: 11 }, axisLine: { stroke: '#223049' }, tickLine: false } as const;

export function GraficasMes({ resumen, comparativa, presupuestos, categorias, viewCurrency }: {
  resumen: ResumenMes;
  comparativa: ResumenMes[];
  presupuestos: EstadoPresupuesto[];
  categorias: Category[];
  viewCurrency: Currency;
}) {
  const cat = (id: string | null) => categorias.find((c) => c.id === id);
  const donut = resumen.byCategory.map((b) => ({
    name: cat(b.categoryId)?.name ?? 'Sin categoría',
    value: b.expenseMinor / 100,
    color: cat(b.categoryId)?.color ?? '#8b96ab', // gris = --texto2 (mismo patrón que EJE: hex fijo alineado al token)
  }));
  const balance = resumen.incomeMinor - resumen.expenseMinor;
  const dinero = (minor: number) => formatMoney(minor, viewCurrency);

  return (
    <>
      <div className="tarjeta">
        <h3>Este mes ({resumen.month})</h3>
        <p>
          Ingresos <span className="monto-pos">+{dinero(resumen.incomeMinor)}</span>
          {' · '}Gastos <span className="monto-neg">−{dinero(resumen.expenseMinor)}</span>
        </p>
        <p className="cifra" style={{ fontSize: 22, fontWeight: 800 }}>
          Balance: <span className={balance >= 0 ? 'monto-pos' : 'monto-neg'}>{dinero(balance)}</span>
        </p>
        {resumen.omitidasSinTasa > 0 && (
          <div className="banner aviso">{resumen.omitidasSinTasa} movimiento(s) sin tasa quedaron fuera del resumen.</div>
        )}
        {donut.length > 0 && (
          <div style={{ height: 220 }}>
            <ResponsiveContainer>
              <PieChart>
                <Pie data={donut} dataKey="value" nameKey="name" innerRadius={55} outerRadius={85} stroke="#0a0f1c" strokeWidth={2} label={(e) => e.name}>
                  {donut.map((d) => <Cell key={d.name} fill={d.color} />)}
                </Pie>
                <Tooltip contentStyle={ESTILO_TOOLTIP} formatter={(v) => dinero(Math.round(Number(v) * 100))} />
              </PieChart>
            </ResponsiveContainer>
          </div>
        )}
        <div style={{ height: 160 }}>
          <ResponsiveContainer>
            <AreaChart data={resumen.dailyCumulative.map((d) => ({ ...d, gasto: d.acumuladoMinor / 100 }))}>
              <XAxis dataKey="day" {...EJE} />
              <YAxis width={50} {...EJE} />
              <Tooltip contentStyle={ESTILO_TOOLTIP} formatter={(v) => dinero(Math.round(Number(v) * 100))} />
              <Area type="monotone" dataKey="gasto" stroke="#2dd4bf" strokeWidth={2} fill="rgba(45, 212, 191, 0.18)" name="Gasto acumulado" />
            </AreaChart>
          </ResponsiveContainer>
        </div>
      </div>

      <div className="tarjeta">
        <h3>Comparativa mensual</h3>
        <div style={{ height: 180 }}>
          <ResponsiveContainer>
            <BarChart data={comparativa.map((r) => ({
              mes: r.month.slice(5), ingresos: r.incomeMinor / 100, gastos: r.expenseMinor / 100,
            }))}>
              <XAxis dataKey="mes" {...EJE} />
              <YAxis width={50} {...EJE} />
              <Tooltip contentStyle={ESTILO_TOOLTIP} formatter={(v) => dinero(Math.round(Number(v) * 100))} />
              <Legend />
              <Bar dataKey="ingresos" fill="#34d399" name="Ingresos" radius={[4, 4, 0, 0]} />
              <Bar dataKey="gastos" fill="#f87171" name="Gastos" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </div>

      {presupuestos.length > 0 && (
        <div className="tarjeta">
          <h3>Presupuestos (USD)</h3>
          {presupuestos.map((e) => (
            <div key={e.category.id} style={{ marginBottom: 10 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 14 }}>
                <span>{e.category.icon} {e.category.name}</span>
                <span className={e.ratio > 1 ? 'monto-neg' : ''}>
                  {formatMoney(e.gastoUsdMinor, 'USD')} / {formatMoney(e.presupuestoMinor, 'USD')}
                </span>
              </div>
              <div className="progreso">
                <div style={{ width: `${Math.min(100, e.ratio * 100)}%`, background: e.ratio > 1 ? 'var(--gasto)' : 'var(--primario)' }} />
              </div>
            </div>
          ))}
        </div>
      )}
    </>
  );
}
