import { useState } from 'react';
import type { Account, Category } from '../../domain/types';
import type { OcurrenciaVista } from '../../domain/calendar';
import { totalesEsperados } from '../../domain/calendar';
import { addDays, diaCorto, partsOf, weekdayLunes0, LETRAS_DIA } from '../../domain/dates';
import { formatMoney } from '../../domain/money';
import { navigate } from '../useHashRoute';

export function SemanaCard({ ocurrencias, cuentas, categorias, hoy }: {
  ocurrencias: OcurrenciaVista[];   // ya viene del rango [hoy−30, hoy+6] que calcula el llamador
  cuentas: Account[];
  categorias: Category[];
  hoy: string;
}) {
  const [todo, setTodo] = useState(false);

  const monedaDe = (accountId?: string) => cuentas.find((c) => c.id === accountId)?.currency ?? 'USD';
  const nombreCuenta = (accountId?: string) => cuentas.find((c) => c.id === accountId)?.name;
  const categoriaDe = (categoryId: string) => categorias.find((c) => c.id === categoryId);

  const dias = Array.from({ length: 7 }, (_, i) => addDays(hoy, i));

  const { pagar, cobrar } = totalesEsperados(ocurrencias, cuentas, categorias);
  const lista = (m: { currency: 'VES' | 'USD'; totalMinor: number }[]) =>
    m.map((x) => formatMoney(x.totalMinor, x.currency)).join(' · ');

  const vencidas = ocurrencias.filter((o) => o.estado === 'vencida');
  const semana = ocurrencias.filter((o) => o.date >= hoy && o.date <= addDays(hoy, 6));
  const listaOcurrencias = [...vencidas, ...semana];
  const mostrar = todo ? listaOcurrencias : listaOcurrencias.slice(0, 3);

  return (
    <div className="tarjeta" style={{ marginTop: 12 }}>
      <div className="tarjeta-cabecera">
        <h3>Esta semana</h3>
        <a className="chip" href="#/calendario">calendario</a>
      </div>

      <div className="semana-tira">
        {dias.map((d) => {
          // Las omitidas no se pintan como punto: el usuario ya las canceló a propósito.
          const delDia = ocurrencias.filter((o) => o.date === d && o.estado !== 'omitida');
          return (
            <button key={d} className={`semana-dia${d === hoy ? ' hoy' : ''}`} onClick={() => navigate(`/calendario?dia=${d}`)}>
              <span className="letra">{LETRAS_DIA[weekdayLunes0(d)]}</span>
              <span className="num">{partsOf(d).d}</span>
              <span className="puntos">
                {delDia.slice(0, 3).map((o) => (
                  <span
                    key={`${o.eventId}|${o.date}`}
                    className={`punto${o.estado === 'hecha' ? ' hecha' : ''}${o.estado === 'vencida' ? ' vencida' : ''}`}
                    style={{ background: categoriaDe(o.evento.categoryId)?.color ?? '#8b96ab' }}
                  />
                ))}
              </span>
            </button>
          );
        })}
      </div>

      {(pagar.length > 0 || cobrar.length > 0) && (
        <p className="ayuda">
          {pagar.length > 0 && `Por pagar ${lista(pagar)}`}
          {pagar.length > 0 && cobrar.length > 0 && ' — '}
          {cobrar.length > 0 && `Por cobrar ${lista(cobrar)}`}
        </p>
      )}

      {listaOcurrencias.length === 0 && (
        <>
          <div className="vacio"><span className="icono">🗓️</span>Sin pagos ni cobros esta semana</div>
          <a className="chip" href="#/calendario?nuevo=1">crear evento</a>
        </>
      )}

      {listaOcurrencias.length > 0 && (
        <div>
          {mostrar.map((o) => (
            <button
              key={`${o.eventId}|${o.date}`}
              className={`fila-ocurrencia${o.estado === 'hecha' ? ' hecha' : ''}`}
              onClick={() => (o.estado === 'pendiente' || o.estado === 'vencida')
                ? navigate(`/nuevo?evento=${o.eventId}&fecha=${o.date}`)
                : navigate(`/calendario?dia=${o.date}`)}
            >
              <span style={{ fontSize: 22 }}>{categoriaDe(o.evento.categoryId)?.icon ?? '📅'}</span>
              <span className="detalle">
                <span className="nota">{o.evento.titulo}</span>
                <span className="meta">
                  {diaCorto(o.date)}{o.evento.accountId ? ` · ${nombreCuenta(o.evento.accountId)}` : ''}
                </span>
              </span>
              <span className="cifra">
                {o.estado === 'hecha'
                  ? o.asignadoPorMoneda.map((m) => formatMoney(m.totalMinor, m.currency)).join(' · ')
                  : (o.evento.amountMinor !== undefined ? formatMoney(o.evento.amountMinor, monedaDe(o.evento.accountId)) : '')}
              </span>
              <span className={`estado estado-${o.estado}`}>{o.estado}</span>
            </button>
          ))}
          {listaOcurrencias.length > 3 && (
            <button className="chip" onClick={() => setTodo(!todo)}>
              {todo ? 'ver menos' : `ver ${listaOcurrencias.length - 3} más`}
            </button>
          )}
        </div>
      )}
    </div>
  );
}
