import type { Rule } from './types';

/** Primera regla cuyo patrón (substring, case-insensitive) aparece en la descripción. */
export function matchCategory(descripcion: string, rules: Rule[]): Rule | null {
  const d = descripcion.toLowerCase();
  return rules.find((r) => {
    const p = r.pattern.trim().toLowerCase();
    return p !== '' && d.includes(p);
  }) ?? null;
}

export function buildRule(pattern: string, categoryId: string, autoCreated = true): Rule {
  const p = pattern.trim();
  if (!p) throw new Error('Patrón vacío');
  return { id: crypto.randomUUID(), pattern: p, categoryId, autoCreated };
}
