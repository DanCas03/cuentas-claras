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
