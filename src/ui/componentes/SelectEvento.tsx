import type { CalEvent } from '../../domain/types';
import type { Ocurrencia } from '../../domain/calendar';
import { candidatasParaTx, type CtxSugerencia, type TxParaSugerir } from '../../domain/eventMatch';
import { ddmmyyyy } from '../../domain/dates';

interface Opcion { valor: string; etiqueta: string; evento: CalEvent; o: Ocurrencia }

/** Select compartido por el editor de movimientos y la vista previa de los imports.
 *  Si no hay candidatas ni asignación actual devuelve null (no ocupa espacio). */
export function SelectEvento({ tx, actual, ctx, ariaLabel, etiqueta, onChange }: {
  tx: TxParaSugerir;
  actual: Ocurrencia | null;
  ctx: CtxSugerencia;
  ariaLabel: string;
  etiqueta?: string;
  onChange: (o: Ocurrencia | null, evento: CalEvent | null) => void;
}) {
  const diaMes = (iso: string) => ddmmyyyy(iso).slice(0, 5); // '15/09'
  const opciones: Opcion[] = candidatasParaTx(tx, ctx, { incluirHechas: true }).map((c) => ({
    valor: `${c.ocurrencia.eventId}|${c.ocurrencia.date}`,
    etiqueta: `📅 ${c.ocurrencia.evento.titulo} · ${diaMes(c.ocurrencia.date)} · ${c.ocurrencia.estado}`,
    evento: c.ocurrencia.evento,
    o: { eventId: c.ocurrencia.eventId, date: c.ocurrencia.date },
  }));

  const valorActual = actual ? `${actual.eventId}|${actual.date}` : '';
  if (actual && !opciones.some((op) => op.valor === valorActual)) {
    // La asignación vigente SIEMPRE se lista, aunque haya quedado fuera de la ventana.
    const evento = ctx.eventos.find((e) => e.id === actual.eventId);
    if (evento) {
      opciones.unshift({
        valor: valorActual,
        etiqueta: `📅 ${evento.titulo} · ${diaMes(actual.date)} · (asignado)`,
        evento,
        o: actual,
      });
    }
  }
  if (opciones.length === 0) return null;

  return (
    <>
      {etiqueta && <label>{etiqueta}</label>}
      <select
        aria-label={ariaLabel}
        value={valorActual}
        onChange={(e) => {
          const op = opciones.find((x) => x.valor === e.target.value);
          onChange(op ? op.o : null, op ? op.evento : null);
        }}
      >
        <option value="">Sin evento</option>
        {opciones.map((op) => <option key={op.valor} value={op.valor}>{op.etiqueta}</option>)}
      </select>
    </>
  );
}
