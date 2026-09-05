import { useEffect, useRef, useState } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { db } from '../../db/db';
import type { CalEvent, CategoryKind, Recurrence } from '../../domain/types';
import { buildEvent, esQuincenal, RECURRENCIA_QUINCENAL } from '../../domain/calendar';
import { parseDias } from '../../domain/calendar';
import { guardarEvento, eliminarEvento } from '../../db/eventOps';
import { parseVenezuelanAmount } from '../../domain/money';
import { todayIso, partsOf } from '../../domain/dates';
import { avisar } from '../Toast';

export function EditorEvento({ original, borrador, cerrar }: {
  original: CalEvent | null;   // null = alta
  borrador: CalEvent;          // valores iniciales (startDate = día seleccionado en el calendario)
  cerrar: () => void;
}) {
  const cuentas = useLiveQuery(() => db.accounts.filter((a) => !a.archived).toArray(), []) ?? [];
  const categorias = useLiveQuery(() => db.categories.toArray(), []) ?? [];
  const txsDelEvento = useLiveQuery(() => db.transactions.where('eventId').equals(borrador.id).count(), [borrador.id]) ?? 0;
  const esNuevo = original === null;
  const [titulo, setTitulo] = useState(esNuevo ? '' : borrador.titulo);
  const [kind, setKind] = useState<CategoryKind>('expense');           // se sincroniza en el Paso 2
  const [categoriaId, setCategoriaId] = useState(esNuevo ? '' : borrador.categoryId);
  const [cuentaId, setCuentaId] = useState(borrador.accountId ?? '');
  const [montoTxt, setMontoTxt] = useState(borrador.amountMinor === undefined ? '' : (borrador.amountMinor / 100).toFixed(2).replace('.', ','));
  const [fecha, setFecha] = useState(borrador.startDate);
  const [repetir, setRepetir] = useState<'once' | 'weekly' | 'monthly' | 'quincenal' | 'yearly'>(
    borrador.recurrence.kind === 'monthly'
      ? (esQuincenal(borrador.recurrence) ? 'quincenal' : 'monthly')
      : borrador.recurrence.kind,
  );
  const [diasTxt, setDiasTxt] = useState(
    borrador.recurrence.kind === 'monthly' ? borrador.recurrence.days.join(', ') : String(partsOf(borrador.startDate).d),
  );
  const [fin, setFin] = useState(borrador.endDate ?? '');
  const [guardando, setGuardando] = useState(false);
  const moneda = cuentas.find((c) => c.id === cuentaId)?.currency ?? 'USD';

  const kindFijado = useRef(false);
  useEffect(() => {
    if (kindFijado.current || categorias.length === 0) return;
    kindFijado.current = true;
    setKind(categorias.find((c) => c.id === borrador.categoryId)?.kind ?? 'expense');
  }, [categorias, borrador.categoryId]);

  function cambiarKind(nuevo: CategoryKind) {
    setKind(nuevo);
    if (categorias.find((c) => c.id === categoriaId)?.kind !== nuevo) setCategoriaId('');
  }

  function cambiarCuenta(nuevaId: string) {
    const monedaNueva = cuentas.find((c) => c.id === nuevaId)?.currency ?? 'USD';
    if (monedaNueva !== moneda && montoTxt !== '') {
      setMontoTxt('');
      avisar('La cuenta elegida usa otra moneda: vuelve a escribir el monto');
    }
    setCuentaId(nuevaId);
  }

  function cambiarRepetir(valor: typeof repetir) {
    setRepetir(valor);
    if (valor === 'once') setFin('');
  }

  async function guardar() {
    if (guardando) return;
    const recurrence: Recurrence =
      repetir === 'quincenal' ? RECURRENCIA_QUINCENAL
      : repetir === 'monthly' ? { kind: 'monthly', days: [] }   // se rellena abajo
      : { kind: repetir };
    setGuardando(true);
    try {
      if (repetir === 'monthly') (recurrence as { kind: 'monthly'; days: number[] }).days = parseDias(diasTxt);
      let amountMinor: number | undefined;
      if (montoTxt.trim() !== '') {
        const m = parseVenezuelanAmount(montoTxt.replace(/,$/, ''));
        if (m === null || m <= 0) throw new Error('Monto inválido');
        amountMinor = m;
      }
      const evento = buildEvent({
        titulo, categoryId: categoriaId, accountId: cuentaId || undefined, amountMinor,
        startDate: fecha, endDate: fin || undefined, recurrence,
        omitidas: original?.omitidas,          // editar CONSERVA las omitidas
        nowIso: original?.createdIso ?? new Date().toISOString(),
      });
      await guardarEvento(db, esNuevo ? evento : { ...evento, id: borrador.id, createdIso: borrador.createdIso });
      avisar('Evento guardado ✓');
      cerrar();
    } catch (e) {
      avisar((e as Error).message);
    } finally {
      setGuardando(false);
    }
  }

  async function eliminar() {
    if (guardando) return;
    if (!window.confirm(`¿Eliminar «${borrador.titulo}»? ${txsDelEvento} movimientos quedarán sin evento (conservan su categoría).`)) return;
    setGuardando(true);
    try {
      const n = await eliminarEvento(db, borrador.id);
      avisar(n > 0 ? `Evento eliminado · ${n} movimientos desasignados` : 'Evento eliminado');
      cerrar();
    } catch (e) {
      avisar((e as Error).message);
    } finally {
      setGuardando(false);
    }
  }

  return (
    <div className="modal-fondo" onClick={cerrar}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h3>{esNuevo ? 'Nuevo evento' : 'Editar evento'}</h3>

        <label>Título</label>
        <input aria-label="Título del evento" value={titulo} onChange={(e) => setTitulo(e.target.value)} placeholder="Quincena" />

        <div className="segmentado">
          <button className={kind === 'expense' ? 'activo' : ''} onClick={() => cambiarKind('expense')}>Pago</button>
          <button className={kind === 'income' ? 'activo' : ''} onClick={() => cambiarKind('income')}>Cobro</button>
        </div>

        <label>Categoría</label>
        <select aria-label="Categoría del evento" value={categoriaId} onChange={(e) => setCategoriaId(e.target.value)}>
          <option value="">Elige una categoría</option>
          {categorias.filter((c) => c.kind === kind).map((c) => (
            <option key={c.id} value={c.id}>{c.icon} {c.name}</option>
          ))}
        </select>

        <label>Cuenta esperada (define la moneda)</label>
        <select aria-label="Cuenta del evento" value={cuentaId} onChange={(e) => cambiarCuenta(e.target.value)}>
          <option value="">— sin cuenta —</option>
          {cuentas.map((c) => (
            <option key={c.id} value={c.id}>{c.name} ({c.currency === 'VES' ? 'Bs' : '$'})</option>
          ))}
        </select>

        <label>Monto esperado en {moneda === 'VES' ? 'Bs' : '$'} (opcional)</label>
        <input
          aria-label="Monto esperado" inputMode="decimal" placeholder="0,00" disabled={!cuentaId}
          value={montoTxt} onChange={(e) => setMontoTxt(e.target.value)}
        />
        <p className="ayuda">Sin cuenta no se puede fijar un monto. Déjalo vacío si el monto varía.</p>

        <label>{repetir === 'once' ? 'Fecha' : 'Primera vez'}</label>
        <input aria-label="Fecha del evento" type="date" value={fecha} onChange={(e) => setFecha(e.target.value)} />

        <label>Repetir</label>
        <select aria-label="Repetición" value={repetir} onChange={(e) => cambiarRepetir(e.target.value as typeof repetir)}>
          <option value="once">Una vez</option>
          <option value="weekly">Cada semana</option>
          <option value="monthly">Cada mes</option>
          <option value="quincenal">Quincenal (15 y último)</option>
          <option value="yearly">Cada año</option>
        </select>

        {repetir === 'monthly' && (
          <>
            <label>Días del mes</label>
            <input aria-label="Días del mes" placeholder="15, 31" value={diasTxt} onChange={(e) => setDiasTxt(e.target.value)} />
            <p className="ayuda">31 = último día del mes</p>
          </>
        )}

        {repetir !== 'once' && (
          <>
            <label>Fin (opcional)</label>
            <input aria-label="Fecha de fin" type="date" value={fin} onChange={(e) => setFin(e.target.value)} />
            <button className="chip" onClick={() => setFin(todayIso())}>Terminar hoy</button>
          </>
        )}

        <div className="fila-form" style={{ marginTop: 12 }}>
          <button className="boton secundario" onClick={cerrar} disabled={guardando}>Cancelar</button>
          {!esNuevo && <button className="boton peligro" onClick={eliminar} disabled={guardando}>Eliminar</button>}
          <button className="boton" onClick={guardar} disabled={guardando}>Guardar</button>
        </div>
      </div>
    </div>
  );
}
