/** Fecha local del dispositivo como 'yyyy-mm-dd' (la app es 100% local; no usar UTC). */
export function todayIso(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/** '06/07/2026' → '2026-07-06'. null si no es dd/mm/yyyy plausible. */
export function isoFromDdMmYyyy(s: string): string | null {
  const m = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(s.trim());
  if (!m) return null;
  const [, dd, mm, yyyy] = m;
  const dia = Number(dd), mes = Number(mm);
  if (mes < 1 || mes > 12 || dia < 1 || dia > 31) return null;
  return `${yyyy}-${mm}-${dd}`;
}

/** '2026-07-06' → '2026-07' */
export function monthOf(dateIso: string): string {
  return dateIso.slice(0, 7);
}

// ---- v3: calendario (todo LOCAL por componentes; nunca new Date('yyyy-mm-dd'), que parsea UTC) ----

const NOMBRES_MES = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre'];
export const MES_CORTO = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'] as const;
const DIA_CORTO = ['lun', 'mar', 'mié', 'jue', 'vie', 'sáb', 'dom'];
const DIA_LARGO = ['lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado', 'domingo'];
/** Letras de la cabecera del calendario, lunes primero. */
export const LETRAS_DIA = ['L', 'M', 'X', 'J', 'V', 'S', 'D'] as const;

export function partsOf(iso: string): { y: number; m: number; d: number } {
  const [y, m, d] = iso.split('-').map(Number);
  return { y, m, d };
}

export function isoFromParts(y: number, m: number, d: number): string {
  return `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
}

export function daysInMonth(y: number, m: number): number {
  return new Date(y, m, 0).getDate();
}

/** 'yyyy-mm-dd' con forma estricta y fecha real (30/02 → false). */
export function esFechaIso(s: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(s)) return false;
  const { y, m, d } = partsOf(s);
  return m >= 1 && m <= 12 && d >= 1 && d <= daysInMonth(y, m);
}

export function addDays(iso: string, n: number): string {
  const { y, m, d } = partsOf(iso);
  const t = new Date(y, m - 1, d + n);
  return isoFromParts(t.getFullYear(), t.getMonth() + 1, t.getDate());
}

/** b − a en días enteros. Date.UTC sobre las partes: inmune a cambios de hora. */
export function diffDias(a: string, b: string): number {
  const A = partsOf(a);
  const B = partsOf(b);
  return Math.round((Date.UTC(B.y, B.m - 1, B.d) - Date.UTC(A.y, A.m - 1, A.d)) / 86_400_000);
}

/** 0 = lunes … 6 = domingo. */
export function weekdayLunes0(iso: string): number {
  const { y, m, d } = partsOf(iso);
  return (new Date(y, m - 1, d).getDay() + 6) % 7;
}

export function mesAnterior(month: string): string {
  const [y, m] = month.split('-').map(Number);
  return m === 1 ? `${y - 1}-12` : `${y}-${String(m - 1).padStart(2, '0')}`;
}

export function mesSiguiente(month: string): string {
  const [y, m] = month.split('-').map(Number);
  return m === 12 ? `${y + 1}-01` : `${y}-${String(m + 1).padStart(2, '0')}`;
}

export function primerDiaMes(month: string): string {
  return `${month}-01`;
}

export function ultimoDiaMes(month: string): string {
  const [y, m] = month.split('-').map(Number);
  return isoFromParts(y, m, daysInMonth(y, m));
}

/** 'Septiembre 2026' */
export function nombreMes(month: string): string {
  const [y, m] = month.split('-').map(Number);
  const nombre = NOMBRES_MES[m - 1];
  return `${nombre[0].toUpperCase()}${nombre.slice(1)} ${y}`;
}

/** 'viernes' */
export function nombreDia(iso: string): string {
  return DIA_LARGO[weekdayLunes0(iso)];
}

/** '15 sep' */
export function fechaCorta(iso: string): string {
  const { m, d } = partsOf(iso);
  return `${d} ${MES_CORTO[m - 1]}`;
}

/** 'vie 4' */
export function diaCorto(iso: string): string {
  return `${DIA_CORTO[weekdayLunes0(iso)]} ${partsOf(iso).d}`;
}

/** 'Viernes 4 de septiembre' */
export function fechaLarga(iso: string): string {
  const { m, d } = partsOf(iso);
  const dia = nombreDia(iso);
  return `${dia[0].toUpperCase()}${dia.slice(1)} ${d} de ${NOMBRES_MES[m - 1]}`;
}

/** '04/09/2026' */
export function ddmmyyyy(iso: string): string {
  const { y, m, d } = partsOf(iso);
  return `${String(d).padStart(2, '0')}/${String(m).padStart(2, '0')}/${y}`;
}

/** Grid mensual de 6 filas × 7 columnas (42 celdas fijas) desde el lunes ≤ día 1. */
export function celdasMes(month: string): { iso: string; enMes: boolean }[] {
  const primero = primerDiaMes(month);
  const inicio = addDays(primero, -weekdayLunes0(primero));
  return Array.from({ length: 42 }, (_, i) => {
    const iso = addDays(inicio, i);
    return { iso, enMes: iso.startsWith(month) };
  });
}
