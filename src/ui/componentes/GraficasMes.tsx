import { useState } from 'react';
import { Area, AreaChart, Bar, BarChart, Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import type { Category, Currency } from '../../domain/types';
import type { EstadoPresupuesto, ResumenMes } from '../../domain/stats';
import { formatMoney } from '../../domain/money';
import { mesAnterior, mesSiguiente, nombreMes } from '../../domain/dates';

// Estilos de gráficas alineados a los tokens del sistema (Recharts no lee CSS vars en todos los props)
const ESTILO_TOOLTIP = {
  background: '#182338',
  border: '1px solid #223049',
  borderRadius: 12,
  color: '#e8edf6',
} as const;
const EJE = { tick: { fill: '#8b96ab', fontSize: 11 }, axisLine: { stroke: '#223049' }, tickLine: false } as const;

export function GraficasMes({ resumen, comparativa, presupuestos, categorias, viewCurrency, mes, mesActual, primerMes, onMes }: {
  resumen: ResumenMes;
  comparativa: ResumenMes[];
  presupuestos: EstadoPresupuesto[];
  categorias: Category[];
  viewCurrency: Currency;
  /** Mes que gobierna las TRES tarjetas (Este mes, Comparativa, Presupuestos). */
  mes: string;
  mesActual: string;
  /** Mes más antiguo con movimientos: tope del botón «‹». */
  primerMes: string;
  onMes: (m: string) => void;
}) {
  const [expandido, setExpandido] = useState(false);
  const cat = (id: string | null) => categorias.find((c) => c.id === id);
  const donut = resumen.byCategory.map((b) => ({
    name: cat(b.categoryId)?.name ?? 'Sin categoría',
    value: b.expenseMinor / 100,
    color: cat(b.categoryId)?.color ?? '#8b96ab', // gris = --texto2 (mismo patrón que EJE: hex fijo alineado al token)
  }));
  const balance = resumen.incomeMinor - resumen.expenseMinor;
  const dinero = (minor: number) => formatMoney(minor, viewCurrency);
  const filaCategoria = (categoryId: string | null, minor: number, total: number, grupo: 'gasto' | 'ingreso') => {
    const c = cat(categoryId);
    const pct = total > 0 ? Math.round((minor / total) * 100) : 0;
    const contenido = (
      <>
        <span className="icono">{c?.icon ?? '📦'}</span>
        <span>
          <span className="nombre">{c?.name ?? 'Sin categoría'}</span>
          <span className="barra-categoria">
            <div style={{ height: '100%', width: `${pct}%`, background: c?.color ?? '#8b96ab' }} />
          </span>
        </span>
        <span className="cifras">
          <span className="monto">{dinero(minor)}</span>
          <span className="pct">{pct} %</span>
        </span>
      </>
    );
    // «Sin categoría» no tiene drill-down posible: se pinta gris y sin enlace.
    return c
      ? <a key={`${categoryId}`} className="fila-categoria" href={`#/movimientos?cat=${categoryId}&mes=${mes}`}>{contenido}</a>
      : <div key={`sin-categoria-${grupo}`} className="fila-categoria" style={{ color: 'var(--texto2)' }}>{contenido}</div>;
  };

  return (
    <>
      <div className="tarjeta">
        <div className="tarjeta-cabecera">
          <div className="nav-mes">
            <button aria-label="Mes anterior" disabled={mes <= primerMes} onClick={() => onMes(mesAnterior(mes))}>‹</button>
            <h3 className="titulo">{mes === mesActual ? 'Este mes' : nombreMes(mes)}</h3>
            <button aria-label="Mes siguiente" disabled={mes >= mesActual} onClick={() => onMes(mesSiguiente(mes))}>›</button>
            {mes !== mesActual && <button className="chip" onClick={() => onMes(mesActual)}>hoy</button>}
          </div>
        </div>
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
              <PieChart margin={{ top: 12, right: 10, bottom: 0, left: 10 }}>
                <Pie
                  data={donut}
                  dataKey="value"
                  nameKey="name"
                  innerRadius={55}
                  outerRadius={70}
                  stroke="#0a0f1c"
                  strokeWidth={2}
                  label={(e) => {
                    // Línea de etiqueta más corta que el offsetRadius por defecto de Recharts (20px)
                    // y fuente en la escala de EJE.tick, para que el texto quepa dentro del viewport
                    // del SVG en tarjetas móviles angostas — evita el recorte arriba/derecha.
                    const RADIAN = Math.PI / 180;
                    const midAngle = e.midAngle ?? 0;
                    const r = e.outerRadius + 3;
                    const x = e.cx + Math.cos(-RADIAN * midAngle) * r;
                    const y = e.cy + Math.sin(-RADIAN * midAngle) * r;
                    return (
                      <text className="recharts-pie-label-text" x={x} y={y} textAnchor={e.textAnchor} alignmentBaseline="middle" fontSize={11} fill={e.fill}>
                        {e.name}
                      </text>
                    );
                  }}
                >
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
        <button className="chip" style={{ marginTop: 8 }} onClick={() => setExpandido(!expandido)}>
          {expandido ? 'Ocultar' : 'Ver todas las categorías'}
        </button>
        {expandido && (
          <div className="lista-categorias">
            {resumen.byCategory.length > 0 && <div className="subtitulo">Gastos</div>}
            {resumen.byCategory.map((b) => filaCategoria(b.categoryId, b.expenseMinor, resumen.expenseMinor, 'gasto'))}
            {resumen.incomeByCategory.length > 0 && <div className="subtitulo">Ingresos</div>}
            {resumen.incomeByCategory.map((b) => filaCategoria(b.categoryId, b.incomeMinor, resumen.incomeMinor, 'ingreso'))}
          </div>
        )}
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
          <h3>Presupuestos (USD) · {nombreMes(mes)}</h3>
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
