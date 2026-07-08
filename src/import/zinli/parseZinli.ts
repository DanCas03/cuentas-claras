import { parseUsAmount } from '../../domain/money';
import { isoFromDdMmYyyy } from '../../domain/dates';

export interface ZinliMovement {
  date: string;
  motivo: string;
  descripcion: string;
  referencia: string;
  amountMinor: number;
}

export interface ZinliParseResult {
  periodo: string | null;
  saldoInicialMinor: number | null;
  saldoFinalMinor: number | null;
  movements: ZinliMovement[];
  unparsed: string[];
  cuadra: boolean | null;
}

/**
 * NFKC expande ligaduras del PDF («ﬁ» → «fi») y normaliza espacios.
 * NBSP: se usa `new RegExp('\\u00a0', 'g')` en vez de un carácter U+00A0 literal en el
 * código fuente, tal como ofrece el brief como alternativa — evita el riesgo de que el
 * carácter se transcriba como un espacio normal al escribir el archivo.
 */
export function normalizePdfLine(s: string): string {
  return s.normalize('NFKC').replace(new RegExp('\\u00a0', 'g'), ' ').replace(/\s+/g, ' ').trim();
}

const INICIO_MOV = /^(\d{2}\/\d{2}\/\d{4})\s+(\S+)(?:\s+(.*))?$/;
const CIERRE = /(\d{6,})\s+([+-]?\$[\d,]+\.\d{2})$/;

/**
 * Reality-fix (Tarea 20, hallazgo de la Tarea 19): pdfjs-dist v6 decodifica la ligadura
 * «ﬁ» de "Saldo ﬁnal del mes" como un TextItem separado de "nal", y extractPdfLines une
 * items adyacentes con un espacio forzado. La línea real llega como "Saldo fi nal del
 * mes: $…" (espacio de por medio), no como el carácter único U+FB01 que NFKC expandiría.
 * El patrón tolera ambas formas: "Saldo final del mes:" (ligadura ya expandida por NFKC,
 * p. ej. si algún otro PDF la trae como un único carácter) y "Saldo fi nal del mes:"
 * (ítem partido, caso real observado). No se aplicó la misma tolerancia a "Saldo inicial"
 * porque esa palabra no contiene el par "fi" propenso a ligadura tipográfica (i-n-i-c-i-a-l).
 */
const SALDO_FINAL_RE = /Saldo fi ?nal del mes:\s*([+-]?\$[\d,]+\.\d{2})/;

export function parseZinliLines(rawLines: string[]): ZinliParseResult {
  const result: ZinliParseResult = {
    periodo: null, saldoInicialMinor: null, saldoFinalMinor: null,
    movements: [], unparsed: [], cuadra: null,
  };

  let abierto: { date: string; motivo: string; partes: string[]; raw: string[] } | null = null;
  const abandonar = () => {
    if (abierto) {
      result.unparsed.push(abierto.raw.join(' '));
      abierto = null;
    }
  };

  for (const cruda of rawLines) {
    const linea = normalizePdfLine(cruda);
    if (linea === '') continue;

    const ini = /Saldo inicial del mes:\s*([+-]?\$[\d,]+\.\d{2})/.exec(linea);
    if (ini) result.saldoInicialMinor = parseUsAmount(ini[1]);
    const fin = SALDO_FINAL_RE.exec(linea);
    if (fin) result.saldoFinalMinor = parseUsAmount(fin[1]);
    const per = /^Desde el (.+)$/.exec(linea);
    if (per && result.periodo === null) result.periodo = per[1];
    if (ini || fin) continue; // línea de cabecera: nunca es parte de un movimiento

    const inicio = INICIO_MOV.exec(linea);
    const fecha = inicio ? isoFromDdMmYyyy(inicio[1]) : null;
    if (inicio && fecha) {
      abandonar(); // el movimiento anterior nunca cerró
      abierto = { date: fecha, motivo: inicio[2], partes: inicio[3] ? [inicio[3]] : [], raw: [linea] };
    } else if (abierto) {
      abierto.partes.push(linea);
      abierto.raw.push(linea);
    } else {
      continue; // cabecera, tabla, pie o texto legal fuera de un movimiento
    }

    const cuerpo = abierto.partes.join(' ');
    const m = CIERRE.exec(cuerpo);
    if (m) {
      const amountMinor = parseUsAmount(m[2]);
      if (amountMinor !== null) {
        result.movements.push({
          date: abierto.date,
          motivo: abierto.motivo,
          descripcion: cuerpo.slice(0, m.index).trim(),
          referencia: m[1],
          amountMinor,
        });
        abierto = null;
      }
    }
  }
  abandonar();

  if (result.saldoInicialMinor !== null && result.saldoFinalMinor !== null) {
    const suma = result.movements.reduce((acc, m) => acc + m.amountMinor, 0);
    result.cuadra = result.saldoInicialMinor + suma === result.saldoFinalMinor;
  }
  return result;
}
