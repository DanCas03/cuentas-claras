import { useState } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { db } from '../../db/db';
import { commitImport } from '../../db/commit';
import { parseBncTxt, validateChain, type BncParseResult } from '../../import/bnc/parseBncTxt';
import { mapBncRows, detectGap, bncImportKey, newestRow, type BncDraft, type GapStatus } from '../../import/bnc/mapBnc';
import { buildRule } from '../../domain/rules';
import { buildAdjustment } from '../../domain/builders';
import { accountBalanceMinor } from '../../domain/balances';
import { formatMoney } from '../../domain/money';
import { todayIso } from '../../domain/dates';
import { aplicarAsignacion, sugerirParaLote } from '../../domain/eventMatch';
import { avisar } from '../Toast';
import { SelectEvento } from './SelectEvento';

interface DraftUI extends BncDraft {
  incluir: boolean;
  crearRegla: boolean;
  patron: string;
  /** true si `sugerirParaLote` propuso esta asignación y el usuario no la ha cambiado. */
  sugerido: boolean;
}

const CUENTA_BNC = 'acc-bnc'; // el TXT de BNC siempre va a la cuenta bancaria en Bs del seed

const TEXTO_GAP: Record<GapStatus, { clase: string; texto: string } | null> = {
  'primera-importacion': null,
  solapado: { clase: 'ok', texto: 'El archivo solapa con lo ya importado: los repetidos se omiten solos.' },
  continuo: { clase: 'ok', texto: 'El archivo conecta exactamente con el último import. Sin huecos.' },
  hueco: { clase: 'aviso', texto: '⚠ Hay movimientos NO exportados entre tu último import y este archivo (el saldo no encadena). Exporta desde BNC un rango que cubra el hueco e importa de nuevo.' },
};

export function ImportBnc() {
  const [texto, setTexto] = useState('');
  const [drafts, setDrafts] = useState<DraftUI[] | null>(null);
  const [resultado, setResultado] = useState<BncParseResult | null>(null);
  const [gap, setGap] = useState<GapStatus>('primera-importacion');
  const [rotos, setRotos] = useState<number[]>([]);
  const [saldoTxt, setSaldoTxt] = useState<number | null>(null);
  const [fase, setFase] = useState<'entrada' | 'previa' | 'hecho'>('entrada');
  const [ajusteHecho, setAjusteHecho] = useState(false); // Important #6: oculta el banner tras crear el ajuste
  const [guardando, setGuardando] = useState(false);
  const [sinResultados, setSinResultados] = useState(false);

  const categorias = useLiveQuery(() => db.categories.toArray(), []) ?? [];
  const cuenta = useLiveQuery(() => db.accounts.get(CUENTA_BNC), []);
  const txs = useLiveQuery(() => db.transactions.toArray(), []) ?? [];
  const calEvents = useLiveQuery(() => db.calEvents.toArray(), []) ?? [];
  const cuentas = useLiveQuery(() => db.accounts.toArray(), []) ?? [];
  const [hoy] = useState(todayIso);

  async function analizar() {
    const r = parseBncTxt(texto);
    if (r.rows.length === 0) {
      setSinResultados(true);
      avisar('No se encontraron movimientos en el texto');
      return;
    }
    setSinResultados(false);
    setAjusteHecho(false);
    const reglas = await db.rules.toArray();
    const existentes = new Set(
      (await db.transactions.filter((t) => t.importKey !== undefined).toArray()).map((t) => t.importKey as string),
    );
    const mapeados = mapBncRows(r.rows, { accountId: CUENTA_BNC, existingKeys: existentes, rules: reglas, categories: categorias });
    setResultado(r);
    setRotos(validateChain(r.rows));
    setGap(detectGap(r.rows, { importKey: cuenta?.bncLastImportKey, saldoMinor: cuenta?.bncLastSaldoMinor }));
    setSaldoTxt(newestRow(r.rows)?.saldoMinor ?? null);
    // El evento sugerido GANA a la heurística por defecto y a la regla aprendida
    // (decisión 5 del spec v3): aplicarAsignacion fija eventId/eventDate y la categoría
    // del evento. El select de categoría sigue visible y corregible antes de confirmar.
    const asignadas = txs.filter((t) => t.eventId !== undefined);
    const sugerencias = sugerirParaLote(
      mapeados.filter((d) => !d.duplicate).map((d) => d.tx),
      { eventos: calEvents, txs: asignadas, accounts: cuentas, categories: categorias, hoy },
    );
    setDrafts(mapeados.map((d) => {
      const o = sugerencias.get(d.tx.id);
      const evento = o ? calEvents.find((e) => e.id === o.eventId) ?? null : null;
      return {
        ...d,
        tx: o && evento ? aplicarAsignacion(d.tx, o, evento) : d.tx,
        incluir: !d.duplicate,
        crearRegla: false,
        patron: d.tx.note ?? '',
        sugerido: Boolean(o && evento),
      };
    }));
    setFase('previa');
  }

  function actualizar(i: number, patch: Partial<DraftUI>) {
    setDrafts((ds) => ds!.map((d, j) => (j === i ? { ...d, ...patch, tx: patch.tx ?? d.tx } : d)));
  }

  const nuevas = (drafts ?? []).filter((d) => d.incluir && !d.duplicate);
  const duplicadas = (drafts ?? []).filter((d) => d.duplicate).length;

  async function confirmar() {
    if (guardando) return;
    if (!drafts || !resultado) return;
    const reglasNuevas = drafts
      .filter((d) => d.incluir && !d.duplicate && d.crearRegla && d.tx.categoryId && d.patron.trim() !== '')
      .map((d) => buildRule(d.patron, d.tx.categoryId as string));
    const fila = newestRow(resultado.rows);
    setGuardando(true);
    try {
      await commitImport(db, {
        txs: nuevas.map((d) => d.tx),
        newRules: reglasNuevas,
        accountPatch: fila ? { id: CUENTA_BNC, bncLastImportKey: bncImportKey(fila), bncLastSaldoMinor: fila.saldoMinor } : undefined,
      });
      avisar(`${nuevas.length} importados, ${duplicadas} duplicados omitidos ✓`);
      setFase('hecho');
    } catch (e) {
      avisar(`No se importó nada: ${(e as Error).message}`);
    } finally {
      setGuardando(false);
    }
  }

  function reiniciar() {
    setTexto('');
    setDrafts(null);
    setResultado(null);
    setSinResultados(false);
    setAjusteHecho(false);
    setFase('entrada');
  }

  // Cuadre post-import: saldo calculado por la app vs saldo final del TXT
  const saldoApp = accountBalanceMinor(CUENTA_BNC, txs);
  const rawMismatch = fase === 'hecho' && saldoTxt !== null && saldoApp !== saldoTxt;
  // Important #1 (aplicado también en BNC por consistencia, riesgo menor que en Zinli): si
  // ya hay movimientos posteriores al último renglón del TXT, el delta ya no representa
  // "movimientos previos al primer export" y ofrecer el ajuste podría corromper el saldo.
  const finArchivoIso = resultado ? (newestRow(resultado.rows)?.date ?? null) : null;
  const hayTxsPosteriores =
    finArchivoIso !== null && txs.some((t) => t.accountId === CUENTA_BNC && t.date > finArchivoIso);
  const descuadre = rawMismatch && !hayTxsPosteriores ? (saldoTxt as number) - saldoApp : null;

  async function crearAjuste() {
    if (guardando) return;
    if (descuadre === null || saldoTxt === null) return;
    setGuardando(true);
    try {
      await db.transactions.add(
        buildAdjustment({
          accountId: CUENTA_BNC, currentBalanceMinor: saldoApp, realBalanceMinor: saldoTxt,
          dateIso: todayIso(), note: 'Cuadre tras import BNC',
        }),
      );
      avisar('Saldo cuadrado con el banco ✓');
      setAjusteHecho(true); // Important #6: evita que un segundo tap duplique el ajuste
    } catch (e) {
      avisar((e as Error).message);
    } finally {
      setGuardando(false);
    }
  }

  if (fase === 'entrada') {
    return (
      <div>
        <p>Exporta desde BNC los "25 últimos movimientos" en TXT y súbelo o pega su contenido.</p>
        <input
          aria-label="Archivo TXT"
          type="file"
          accept=".txt,text/plain,application/octet-stream"
          onChange={async (e) => {
            const f = e.target.files?.[0];
            if (f) setTexto(await f.text());
          }}
        />
        <label>O pega el contenido</label>
        <textarea aria-label="Texto del TXT" rows={6} value={texto} onChange={(e) => setTexto(e.target.value)} />
        <button className="boton" style={{ marginTop: 8 }} onClick={analizar} disabled={texto.trim() === ''}>
          Analizar
        </button>
        {sinResultados && (
          <div className="vacio">
            <span className="icono">🔍</span>
            No se reconoció ningún movimiento en el texto. Revisa que sea el export completo de BNC.
          </div>
        )}
      </div>
    );
  }

  if (fase === 'hecho') {
    return (
      <div>
        <div className="banner ok">Import terminado. Saldo según la app: {formatMoney(saldoApp, 'VES')}.</div>
        {rawMismatch && hayTxsPosteriores && (
          <div className="banner aviso">
            El TXT termina en {formatMoney(saldoTxt as number, 'VES')} pero la app calcula {formatMoney(saldoApp, 'VES')}
            {' '}— hay movimientos más recientes en esta cuenta, así que este descuadre no es fiable para crear un ajuste. Revísalo a mano si hace falta.
          </div>
        )}
        {descuadre !== null && !ajusteHecho && (
          <div className="banner aviso">
            El TXT termina en {formatMoney(saldoTxt as number, 'VES')} pero la app calcula {formatMoney(saldoApp, 'VES')}
            {' '}(movimientos previos al primer export).
            <button className="boton secundario" style={{ marginTop: 8 }} onClick={crearAjuste} disabled={guardando}>Crear ajuste</button>
          </div>
        )}
        <button className="boton" onClick={reiniciar}>Importar otro archivo</button>
      </div>
    );
  }

  const aviso = TEXTO_GAP[gap];
  return (
    <div>
      <p>
        <strong>{drafts?.length ?? 0} movimientos</strong> en el archivo
        {resultado?.meta ? ` de la cuenta ${resultado.meta.accountNumber.slice(-4).padStart(8, '•')}` : ''}.
      </p>
      {aviso && <div className={`banner ${aviso.clase}`}>{aviso.texto}</div>}
      {rotos.length > 0 && (
        <div className="banner error">El encadenado de saldos no cuadra dentro del archivo (filas {rotos.map((i) => i + 1).join(', ')}). Revisa que el TXT esté completo.</div>
      )}
      {resultado && resultado.unparsed.length > 0 && (
        <div className="banner error">
          {resultado.unparsed.length} línea(s) irreconocibles — se importa el resto y estas quedan fuera (añádelas a mano si hacían falta):
          <ul style={{ margin: '4px 0 0 16px', padding: 0 }}>
            {resultado.unparsed.map((u) => (
              <li key={u.lineNumber}>línea {u.lineNumber} ({u.reason}): «{u.raw.slice(0, 60)}…»</li>
            ))}
          </ul>
        </div>
      )}
      {drafts?.map((d, i) => (
        <div key={d.tx.importKey} className="fila-mov" style={{ opacity: d.duplicate ? 0.45 : 1, alignItems: 'flex-start' }}>
          <input
            type="checkbox"
            style={{ width: 'auto', marginTop: 4 }}
            aria-label={`Incluir fila ${i + 1}`}
            checked={d.incluir}
            disabled={d.duplicate}
            onChange={(e) => actualizar(i, { incluir: e.target.checked })}
          />
          <span className="detalle">
            <span className="nota">{d.tx.note}</span>
            <span className="meta">
              {d.tx.date} {d.tx.time?.slice(0, 8)}
              {d.pairRef && <span className="chip" style={{ marginLeft: 4 }}>cargo+comisión</span>}
              {d.duplicate && <span className="chip" style={{ marginLeft: 4 }}>ya importado</span>}
            </span>
            {!d.duplicate && (
              <span style={{ display: 'flex', gap: 6, marginTop: 4, alignItems: 'center' }}>
                <select
                  aria-label={`Categoría fila ${i + 1}`}
                  value={d.tx.categoryId ?? ''}
                  onChange={(e) => actualizar(i, { tx: { ...d.tx, categoryId: e.target.value || undefined } })}
                >
                  <option value="">Sin categoría</option>
                  {categorias.filter((c) => c.kind === d.tx.type).map((c) => (
                    <option key={c.id} value={c.id}>{c.icon} {c.name}</option>
                  ))}
                </select>
                <SelectEvento
                  ariaLabel={`Evento fila ${i + 1}`}
                  tx={{
                    type: d.tx.type as 'income' | 'expense',
                    date: d.tx.date,
                    accountId: d.tx.accountId,
                    amountMinor: d.tx.amountMinor,
                  }}
                  actual={d.tx.eventId && d.tx.eventDate ? { eventId: d.tx.eventId, date: d.tx.eventDate } : null}
                  ctx={{ eventos: calEvents, txs: txs.filter((t) => t.eventId !== undefined), accounts: cuentas, categories: categorias, hoy }}
                  onChange={(o, evento) => actualizar(i, { tx: aplicarAsignacion(d.tx, o, evento), sugerido: false })}
                />
                {d.sugerido && d.tx.eventId && <span className="chip">sugerido</span>}
                <label style={{ display: 'flex', alignItems: 'center', gap: 4, margin: 0, whiteSpace: 'nowrap' }}>
                  <input
                    type="checkbox"
                    style={{ width: 'auto' }}
                    aria-label={`Crear regla fila ${i + 1}`}
                    checked={d.crearRegla}
                    onChange={(e) => actualizar(i, { crearRegla: e.target.checked })}
                  />
                  regla
                </label>
              </span>
            )}
            {d.crearRegla && !d.duplicate && (
              <input
                aria-label={`Patrón fila ${i + 1}`}
                value={d.patron}
                onChange={(e) => actualizar(i, { patron: e.target.value })}
                placeholder="texto que identifica este comercio"
              />
            )}
          </span>
          <span className={d.tx.type === 'income' ? 'monto-pos' : 'monto-neg'}>
            {d.tx.type === 'income' ? '+' : '−'}{formatMoney(d.tx.amountMinor, 'VES')}
          </span>
        </div>
      ))}
      <div className="fila-form" style={{ marginTop: 12 }}>
        <button className="boton secundario" onClick={reiniciar}>Cancelar</button>
        <button className="boton" onClick={confirmar} disabled={nuevas.length === 0 || guardando}>
          Confirmar ({nuevas.length})
        </button>
      </div>
    </div>
  );
}
