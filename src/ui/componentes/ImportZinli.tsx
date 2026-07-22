import { useState } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { db } from '../../db/db';
import { commitImport } from '../../db/commit';
import { extractPdfLines } from '../../import/zinli/extractPdfText';
import { parseZinliLines, type ZinliParseResult } from '../../import/zinli/parseZinli';
import { mapZinliMovements, type ZinliDraft } from '../../import/zinli/mapZinli';
import { buildRule } from '../../domain/rules';
import { buildAdjustment } from '../../domain/builders';
import { accountBalanceMinor } from '../../domain/balances';
import { formatMoney } from '../../domain/money';
import { todayIso } from '../../domain/dates';
import { avisar } from '../Toast';

interface DraftUI extends ZinliDraft {
  incluir: boolean;
  crearRegla: boolean;
  patron: string;
}

const CUENTA_ZINLI = 'acc-zinli';

export function ImportZinli() {
  const [lineas, setLineas] = useState<string[] | null>(null);
  const [resultado, setResultado] = useState<ZinliParseResult | null>(null);
  const [drafts, setDrafts] = useState<DraftUI[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [hecho, setHecho] = useState(false);
  const [saldoFinal, setSaldoFinal] = useState<number | null>(null); // "Saldo final del mes" del PDF, para el cuadre post-import
  const [finPeriodoIso, setFinPeriodoIso] = useState<string | null>(null); // fecha máxima de movimientos del PDF (Important #1)
  const [ajusteHecho, setAjusteHecho] = useState(false); // Important #6: oculta el banner tras crear el ajuste
  const [guardando, setGuardando] = useState(false);
  const categorias = useLiveQuery(() => db.categories.toArray(), []) ?? [];
  const txs = useLiveQuery(() => db.transactions.toArray(), []) ?? [];

  async function analizar(f: File) {
    try {
      setError(null);
      setHecho(false);
      setAjusteHecho(false);
      const lin = await extractPdfLines(await f.arrayBuffer());
      const r = parseZinliLines(lin);
      if (r.movements.length === 0) {
        setError('No se reconocieron movimientos en el PDF. Revisa el texto extraído abajo.');
        setLineas(lin);
        setResultado(null);
        setSaldoFinal(null);
        setFinPeriodoIso(null);
        setDrafts(null);
        return;
      }
      const reglas = await db.rules.toArray();
      const existentes = new Set(
        (await db.transactions.filter((t) => t.importKey !== undefined).toArray()).map((t) => t.importKey as string),
      );
      const mapeados = mapZinliMovements(r.movements, {
        accountId: CUENTA_ZINLI, existingKeys: existentes, rules: reglas, categories: categorias,
      });
      setLineas(lin);
      setResultado(r);
      setSaldoFinal(r.saldoFinalMinor);
      setFinPeriodoIso(r.movements.reduce((max, m) => (m.date > max ? m.date : max), r.movements[0].date));
      setDrafts(mapeados.map((d) => ({ ...d, incluir: !d.duplicate, crearRegla: false, patron: d.tx.note ?? '' })));
    } catch (err) {
      setError((err as Error).message);
    }
  }

  function actualizar(i: number, patch: Partial<DraftUI>) {
    setDrafts((ds) => ds!.map((d, j) => (j === i ? { ...d, ...patch, tx: patch.tx ?? d.tx } : d)));
  }

  const nuevas = (drafts ?? []).filter((d) => d.incluir && !d.duplicate);
  const duplicadas = (drafts ?? []).filter((d) => d.duplicate).length;

  async function confirmar() {
    if (guardando) return;
    if (!drafts) return;
    const reglasNuevas = drafts
      .filter((d) => d.incluir && !d.duplicate && d.crearRegla && d.tx.categoryId && d.patron.trim() !== '')
      .map((d) => buildRule(d.patron, d.tx.categoryId as string));
    setGuardando(true);
    try {
      await commitImport(db, { txs: nuevas.map((d) => d.tx), newRules: reglasNuevas });
      avisar(`${nuevas.length} importados, ${duplicadas} duplicados omitidos ✓`);
      setHecho(true);
      setDrafts(null);
      setResultado(null);
    } catch (e) {
      avisar(`No se importó nada: ${(e as Error).message}`);
    } finally {
      setGuardando(false);
    }
  }

  // Cuadre post-import (mismo mecanismo que BNC): saldo calculado por la app vs
  // "Saldo final del mes" del PDF. En la primera importación faltan los meses
  // previos / saldo inicial y no coinciden, así que se ofrece crear un ajuste.
  const saldoApp = accountBalanceMinor(CUENTA_ZINLI, txs);
  const rawMismatch = hecho && saldoFinal !== null && saldoApp !== saldoFinal;
  // Important #1: si ya hay movimientos posteriores al período del PDF (p. ej. se importó
  // un mes ANTERIOR después de uno más nuevo), el delta ya no representa "saldo inicial
  // faltante" — ofrecer "Crear ajuste" aquí corrompería el saldo. Se degrada a advertencia.
  const hayTxsPosteriores =
    finPeriodoIso !== null && txs.some((t) => t.accountId === CUENTA_ZINLI && t.date > finPeriodoIso);
  const descuadre = rawMismatch && !hayTxsPosteriores ? (saldoFinal as number) - saldoApp : null;

  async function crearAjuste() {
    if (guardando) return;
    if (descuadre === null || saldoFinal === null) return;
    setGuardando(true);
    try {
      await db.transactions.add(
        buildAdjustment({
          accountId: CUENTA_ZINLI, currentBalanceMinor: saldoApp, realBalanceMinor: saldoFinal,
          dateIso: todayIso(), note: 'Cuadre tras import Zinli',
        }),
      );
      avisar('Saldo cuadrado con Zinli ✓');
      setAjusteHecho(true); // Important #6: evita que un segundo tap duplique el ajuste
    } catch (e) {
      avisar((e as Error).message);
    } finally {
      setGuardando(false);
    }
  }

  return (
    <div>
      <p>Sube el "Resumen de movimientos" mensual de Zinli (PDF).</p>
      <input
        aria-label="Archivo PDF"
        type="file"
        accept=".pdf,application/pdf"
        onChange={(e) => {
          const f = e.target.files?.[0];
          if (f) analizar(f);
        }}
      />
      {error && <div className="banner error">{error}</div>}
      {hecho && <div className="banner ok">Import terminado ✓</div>}
      {rawMismatch && hayTxsPosteriores && (
        <div className="banner aviso">
          El PDF termina en {formatMoney(saldoFinal as number, 'USD')} pero la app calcula {formatMoney(saldoApp, 'USD')}
          {' '}— hay movimientos más recientes en esta cuenta, así que este descuadre no es fiable para crear un ajuste. Revísalo a mano si hace falta.
        </div>
      )}
      {descuadre !== null && !ajusteHecho && (
        <div className="banner aviso">
          El PDF termina en {formatMoney(saldoFinal as number, 'USD')} pero la app calcula {formatMoney(saldoApp, 'USD')}
          {' '}(faltan los meses previos / saldo inicial).
          <button className="boton secundario" style={{ marginTop: 8 }} onClick={crearAjuste} disabled={guardando}>Crear ajuste</button>
        </div>
      )}

      {resultado && drafts && (
        <>
          <p>
            <strong>{drafts.length} movimientos</strong>
            {resultado.periodo ? ` · ${resultado.periodo}` : ''}
          </p>
          {resultado.cuadra === true && (
            <div className="banner ok">
              Cuadra: {formatMoney(resultado.saldoInicialMinor as number, 'USD')} inicial + movimientos ={' '}
              {formatMoney(resultado.saldoFinalMinor as number, 'USD')} final ✓
            </div>
          )}
          {resultado.cuadra === false && (
            <div className="banner error">
              ⚠ NO cuadra: inicial {formatMoney(resultado.saldoInicialMinor as number, 'USD')} + movimientos ≠ final{' '}
              {formatMoney(resultado.saldoFinalMinor as number, 'USD')}. Faltan movimientos por reconocer — revisa las líneas de abajo antes de confiar en este import.
            </div>
          )}
          {resultado.cuadra === null && (
            <div className="banner aviso">No se encontraron los saldos inicial/final del mes: no se puede validar el cuadre.</div>
          )}
          {resultado.unparsed.length > 0 && (
            <div className="banner error">
              {resultado.unparsed.length} fragmento(s) sin cerrar: {resultado.unparsed.map((u) => `«${u.slice(0, 50)}…»`).join(' · ')}
            </div>
          )}

          {drafts.map((d, i) => (
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
                  {d.tx.date}
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
                {d.tx.type === 'income' ? '+' : '−'}{formatMoney(d.tx.amountMinor, 'USD')}
              </span>
            </div>
          ))}
          <button className="boton" style={{ marginTop: 12 }} onClick={confirmar} disabled={nuevas.length === 0 || guardando}>
            Confirmar ({nuevas.length})
          </button>
        </>
      )}

      {lineas && (
        <details style={{ marginTop: 12 }}>
          <summary>Texto extraído ({lineas.length} líneas)</summary>
          <textarea aria-label="Texto extraído" readOnly rows={10} value={lineas.join('\n')} />
        </details>
      )}
    </div>
  );
}
