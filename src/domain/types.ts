export type Currency = 'VES' | 'USD';
export type AccountKind = 'bank' | 'wallet' | 'snapshot' | 'cash';

export interface Account {
  id: string;
  name: string;
  currency: Currency;
  kind: AccountKind;
  isSavings: boolean;
  archived: boolean;
  /** Estado del último import BNC (solo cuentas kind 'bank') para detectar huecos. */
  bncLastImportKey?: string;
  bncLastSaldoMinor?: number;
  /** Vista 1 del slider de patrimonio. Default al leer: !isSavings (v2). */
  enDisposicion?: boolean;
}

export type TxType = 'expense' | 'income' | 'transfer' | 'adjustment';
export type TxSource = 'manual' | 'bnc' | 'zinli' | 'adjust';

/**
 * Invariantes:
 * - amountMinor es entero en céntimos de la moneda de accountId.
 * - expense/income/transfer: amountMinor > 0 (el tipo da el signo).
 * - adjustment: amountMinor es el DELTA con signo (real − calculado).
 * - transfer: toAccountId obligatorio; toAmountMinor en la moneda destino;
 *   rateUsed (VES por USD) solo si cruza moneda.
 * - date: 'yyyy-mm-dd'; time: 'HH:mm:ss' o 'HH:mm:ss.SSS' (opcional).
 */
export interface Transaction {
  id: string;
  type: TxType;
  accountId: string;
  amountMinor: number;
  date: string;
  time?: string;
  categoryId?: string;
  note?: string;
  source: TxSource;
  importKey?: string;
  toAccountId?: string;
  toAmountMinor?: number;
  rateUsed?: number;
  /** Grupo transaccional (v2). Con groupId, el movimiento se excluye de las
   *  agregaciones y solo cuenta el residuo virtual del grupo. Los saldos NO cambian. */
  groupId?: string;
  /** v3: ocurrencia (eventId, eventDate) a la que se asignó. Van juntos: ambos o ninguno.
   *  eventDate es la fecha NOMINAL de la ocurrencia (p. ej. '2026-09-15' para la quincena),
   *  no necesariamente = date. No altera monto/fecha/cuenta/tipo; no afecta saldos ni agregaciones. */
  eventId?: string;
  eventDate?: string;
}

export type CategoryKind = 'expense' | 'income';

export interface Category {
  id: string;
  name: string;
  icon: string;   // emoji
  color: string;  // hex
  kind: CategoryKind;
  /** Presupuesto mensual opcional, SIEMPRE en céntimos de USD. */
  monthlyBudgetMinor?: number;
}

export type RateSource = 'BCV' | 'BINANCE' | 'MANUAL';

export interface Rate {
  date: string;        // yyyy-mm-dd
  source: RateSource;
  vesPerUsd: number;   // tasa (no es dinero: puede tener decimales)
}

export interface Rule {
  id: string;
  pattern: string;     // substring, case-insensitive
  categoryId: string;
  autoCreated: boolean;
}

export interface Settings {
  id: 'main';
  viewCurrency: Currency;      // moneda del dashboard (defecto USD)
  rateSource: RateSource;      // fuente activa (defecto BCV)
  lastAccountId?: string;
  lastRatesFetchDate?: string; // yyyy-mm-dd del último fetch exitoso
}

export type DebtDirection = 'porPagar' | 'porCobrar';
export type DebtStatus = 'pendiente' | 'cerrada';

/** Deuda o cuenta por cobrar (v2). Pendiente NO altera ningún saldo;
 *  el movimiento real nace solo al cerrarla. amountMinor vive en la
 *  moneda de la cuenta asignada (accountId). */
export interface Debt {
  id: string;
  direction: DebtDirection;
  concepto: string;
  amountMinor: number;
  accountId: string;
  status: DebtStatus;
  createdIso: string;
  settledTxId?: string;
  settledIso?: string;
}

/** Grupo de movimientos transaccionales (v2). El residuo es virtual: se deriva, no se persiste. */
export interface TxGroup {
  id: string;
  nota?: string;
  /** v3: categoría del residuo virtual. Se aplica SOLO si su kind coincide con el signo del
   *  residuo (expense ↔ residuo < 0, income ↔ residuo > 0); si no, cae en «Vuelto». */
  categoryId?: string;
  createdIso: string;
}

/** Reglas de recurrencia (v3). weekly/yearly derivan el día de la semana / mes+día de startDate. */
export type Recurrence =
  | { kind: 'once' }
  | { kind: 'weekly' }
  | { kind: 'monthly'; days: number[] }   // 1..31, ordenados, sin repetidos; día > días del mes → último día
  | { kind: 'yearly' };

/** Evento de calendario (v3): fecha en que hay que pagar o cobrar algo. NO es dinero:
 *  no altera saldos ni agregaciones. Sus ocurrencias se DERIVAN (calendar.ts). */
export interface CalEvent {
  id: string;
  titulo: string;          // «Quincena», «Netflix», «Condominio»
  categoryId: string;      // obligatoria: define pago (kind expense) o cobro (kind income) y la categoría que copia la tx asignada
  accountId?: string;      // cuenta esperada; define la moneda de amountMinor
  amountMinor?: number;    // monto esperado en céntimos de la moneda de accountId (REQUIERE accountId); ausente = variable
  startDate: string;       // 'yyyy-mm-dd' local; ancla y primera fecha posible
  endDate?: string;        // 'yyyy-mm-dd' inclusive; ausente = sin fin
  recurrence: Recurrence;
  omitidas?: string[];     // fechas nominales marcadas «omitida»
  createdIso: string;
}
