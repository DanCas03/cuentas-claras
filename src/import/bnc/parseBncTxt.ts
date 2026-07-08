import { parseVenezuelanAmount } from '../../domain/money';
import { isoFromDdMmYyyy } from '../../domain/dates';

export interface BncMeta {
  accountNumber: string;
  holder: string;
  title: string;
}

export interface BncRow {
  date: string;
  time: string;
  referencia: string;
  codTransaccion: string;
  tipoTransaccion: string;
  tipOperacion: string;
  descripcion: string;
  amountMinor: number;
  saldoMinor: number;
  referencia2: string;
}

export interface BncUnparsed {
  lineNumber: number;
  raw: string;
  reason: string;
}

export interface BncParseResult {
  meta: BncMeta | null;
  rows: BncRow[];
  unparsed: BncUnparsed[];
}

const HORA_RE = /^\d{2}:\d{2}:\d{2}(\.\d+)?$/;

export function parseBncTxt(text: string): BncParseResult {
  const lineas = text.split(/\r?\n/);
  const result: BncParseResult = { meta: null, rows: [], unparsed: [] };

  lineas.forEach((linea, idx) => {
    const lineNumber = idx + 1;
    const raw = linea.replace(/\s+$/, '');
    if (raw.trim() === '') return;

    // Metadatos: única línea sin tabs con separador '@'
    if (!raw.includes('\t') && raw.includes('@')) {
      const partes = raw.split('@');
      if (partes.length >= 3 && result.meta === null) {
        result.meta = { accountNumber: partes[0], holder: partes[1], title: partes.slice(2).join('@') };
      } else {
        result.unparsed.push({ lineNumber, raw, reason: 'metadatos irreconocibles' });
      }
      return;
    }

    if (raw.startsWith('Fecha\t')) return; // cabecera

    const f = raw.split('\t');
    if (f.length < 10) {
      result.unparsed.push({ lineNumber, raw, reason: `columnas insuficientes (${f.length})` });
      return;
    }
    const [fecha, hora, referencia, codTransaccion, tipoTransaccion, tipOperacion, descripcion, debe, haber, saldo, referencia2 = ''] = f;

    // Pseudo-fila "Saldo Inicial": se reconoce y se descarta (semántica no confiable)
    if (tipOperacion.trim() === 'Saldo Inicial' && referencia.trim() === '') return;

    const date = isoFromDdMmYyyy(fecha);
    if (!date) {
      result.unparsed.push({ lineNumber, raw, reason: `fecha inválida: "${fecha}"` });
      return;
    }
    if (!HORA_RE.test(hora.trim())) {
      result.unparsed.push({ lineNumber, raw, reason: `hora inválida: "${hora}"` });
      return;
    }
    const debeMinor = parseVenezuelanAmount(debe.trim() === '' ? '0' : debe);
    const haberMinor = parseVenezuelanAmount(haber.trim() === '' ? '0' : haber);
    const saldoMinor = parseVenezuelanAmount(saldo);
    if (debeMinor === null || haberMinor === null || saldoMinor === null) {
      result.unparsed.push({ lineNumber, raw, reason: 'monto ilegible' });
      return;
    }

    result.rows.push({
      date,
      time: hora.trim(),
      referencia: referencia.trim(),
      codTransaccion: codTransaccion.trim(),
      tipoTransaccion: tipoTransaccion.trim(),
      tipOperacion: tipOperacion.trim(),
      descripcion: descripcion.trim(),
      amountMinor: debeMinor + haberMinor,
      saldoMinor,
      referencia2: referencia2.trim(),
    });
  });

  return result;
}

/**
 * Con filas en orden de archivo (descendente), verifica saldo[i] − monto[i] === saldo[i+1].
 * Devuelve los índices i donde NO cuadra.
 */
export function validateChain(rows: BncRow[]): number[] {
  const rotos: number[] = [];
  for (let i = 0; i < rows.length - 1; i++) {
    if (rows[i].saldoMinor - rows[i].amountMinor !== rows[i + 1].saldoMinor) rotos.push(i);
  }
  return rotos;
}
