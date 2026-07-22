import { parseVenezuelanAmount } from '../domain/money';

export interface CapturaBnc {
  tipo: 'ingreso' | 'gasto';
  /** céntimos de Bs (las notificaciones del BNC siempre son en Bs). */
  montoMinor: number;
  /** nota lista para el movimiento: tipo de operación + referencia. */
  nota: string;
}

interface Reconocedor {
  tipo: CapturaBnc['tipo'];
  /** Grupo 1: monto venezolano (sin miles, coma decimal). Grupo 2: referencia. */
  regex: RegExp;
  nota: (ref: string) => string;
}

// Estructura real de las notificaciones del BNC (captura de Daniel 2026-07-22; valores enmascarados):
//   BNC Pago Movil Recibido Bs.<monto> Telf.04XX***NNNN Dia:dd/mm/aa-HH:MM Ref:<dígitos> Llamar al …
//   BNC Compra POS Bs.<monto> Tarj:<4 dígitos> Dia:dd/mm/aa-HH:MM Ref:<dígitos> Llamar al …
const RECONOCEDORES: Reconocedor[] = [
  {
    tipo: 'ingreso',
    regex: /BNC Pago Movil Recibido Bs\.(\d+,\d{2})\b.*?\bRef:(\d+)/i,
    nota: (ref) => `Pago móvil recibido · ref ${ref}`,
  },
  {
    tipo: 'gasto',
    regex: /BNC Compra POS Bs\.(\d+,\d{2})\b.*?\bRef:(\d+)/i,
    nota: (ref) => `Compra POS · ref ${ref}`,
  },
];

/** Reconoce pago móvil recibido y compra con tarjeta del BNC. Cualquier otra cosa → null. */
export function parseNotificacionBnc(texto: string): CapturaBnc | null {
  const limpio = texto.normalize('NFKC').replace(/\s+/g, ' ').trim();
  if (!limpio) return null;
  for (const r of RECONOCEDORES) {
    const m = r.regex.exec(limpio);
    if (!m) continue;
    const montoMinor = parseVenezuelanAmount(m[1]);
    if (montoMinor === null || montoMinor <= 0) continue;
    return { tipo: r.tipo, montoMinor, nota: r.nota(m[2]) };
  }
  return null;
}
