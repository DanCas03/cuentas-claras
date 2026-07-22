import { useEffect, useState } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { db, getSettings, saveSettings } from '../../db/db';
import { buildManualTx } from '../../domain/builders';
import { parseVenezuelanAmount, formatMoney } from '../../domain/money';
import { todayIso } from '../../domain/dates';
import { Teclado } from '../componentes/Teclado';
import { CategoriaGrid } from '../componentes/CategoriaGrid';
import { TransferForm } from '../componentes/TransferForm';
import { AjusteForm } from '../componentes/AjusteForm';
import { avisar } from '../Toast';
import { navigate } from '../useHashRoute';

type Modo = 'gasto' | 'ingreso' | 'transferir' | 'ajustar';

export function NuevoPage({ params }: { params: URLSearchParams }) {
  const [modo, setModo] = useState<Modo>(params.get('tipo') === 'ingreso' ? 'ingreso' : 'gasto');
  const [entrada, setEntrada] = useState('');
  const [nota, setNota] = useState('');
  const [cuentaId, setCuentaId] = useState<string | null>(null);
  const cuentas = useLiveQuery(() => db.accounts.filter((a) => !a.archived).toArray(), []) ?? [];
  const categorias = useLiveQuery(() => db.categories.toArray(), []) ?? [];

  // Prefill de captura (v2): solo al montar; lo que el usuario edite después manda.
  useEffect(() => {
    const monto = params.get('monto');
    if (monto && /^\d+$/.test(monto)) {
      setEntrada((Number(monto) / 100).toFixed(2).replace('.', ','));
    }
    const notaParam = params.get('nota');
    if (notaParam) setNota(notaParam);
    const cuentaParam = params.get('cuenta');
    if (cuentaParam) setCuentaId(cuentaParam);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    getSettings(db).then((s) => setCuentaId((prev) => prev ?? s.lastAccountId ?? null));
  }, []);

  const cuenta = cuentas.find((c) => c.id === cuentaId) ?? cuentas[0];
  const minor = parseVenezuelanAmount(entrada.replace(/,$/, ''));
  const [guardando, setGuardando] = useState(false);

  function onTecla(t: string) {
    setEntrada((e) => {
      if (t === '⌫') return e.slice(0, -1);
      if (t === ',') return e === '' || e.includes(',') ? e : `${e},`;
      const dec = e.split(',')[1];
      if (dec !== undefined && dec.length >= 2) return e; // máximo 2 decimales
      return e + t;
    });
  }

  async function guardar(categoryId: string) {
    if (guardando) return;
    if (!cuenta || minor === null || minor <= 0) return;
    setGuardando(true);
    try {
      const tx = buildManualTx({
        type: modo === 'gasto' ? 'expense' : 'income',
        accountId: cuenta.id,
        amountMinor: minor,
        categoryId,
        dateIso: todayIso(),
        note: nota.trim() || undefined,
      });
      await db.transactions.add(tx);
      await saveSettings(db, { lastAccountId: cuenta.id });
      avisar('Guardado ✓');
      navigate('/');
    } catch (e) {
      avisar((e as Error).message);
    } finally {
      setGuardando(false);
    }
  }

  return (
    <div>
      <div className="segmentado">
        <button className={modo === 'gasto' ? 'activo' : ''} onClick={() => setModo('gasto')}>Gasto</button>
        <button className={modo === 'ingreso' ? 'activo' : ''} onClick={() => setModo('ingreso')}>Ingreso</button>
        <button className={modo === 'transferir' ? 'activo' : ''} onClick={() => setModo('transferir')}>Transferir</button>
        <button className={modo === 'ajustar' ? 'activo' : ''} onClick={() => setModo('ajustar')}>Ajustar</button>
      </div>
      {modo === 'transferir' && <TransferForm />}
      {modo === 'ajustar' && <AjusteForm />}
      {(modo === 'gasto' || modo === 'ingreso') && (
        <>
          <label>Cuenta</label>
          <select aria-label="Cuenta" value={cuenta?.id ?? ''} onChange={(e) => setCuentaId(e.target.value)}>
            {cuentas.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name} ({c.currency === 'VES' ? 'Bs' : '$'})
              </option>
            ))}
          </select>
          <div className={`monto-display ${modo}`}>
            {minor !== null && minor > 0 && cuenta
              ? formatMoney(modo === 'gasto' ? -minor : minor, cuenta.currency)
              : '—'}
          </div>
          <Teclado onTecla={onTecla} />
          <label>Nota (opcional)</label>
          <input aria-label="Nota" value={nota} onChange={(e) => setNota(e.target.value)} placeholder="¿En qué fue?" />
          <label>Toca la categoría para guardar</label>
          <CategoriaGrid
            categorias={categorias.filter((c) => c.kind === (modo === 'gasto' ? 'expense' : 'income'))}
            onElegir={guardar}
          />
        </>
      )}
    </div>
  );
}
