import { useState } from 'react';
import { ImportBnc } from '../componentes/ImportBnc';
import { ImportZinli } from '../componentes/ImportZinli';

export function ImportarPage() {
  const [tab, setTab] = useState<'bnc' | 'zinli'>('bnc');
  return (
    <div>
      <h2>Importar</h2>
      <div className="segmentado">
        <button className={tab === 'bnc' ? 'activo' : ''} onClick={() => setTab('bnc')}>BNC (TXT)</button>
        <button className={tab === 'zinli' ? 'activo' : ''} onClick={() => setTab('zinli')}>Zinli (PDF)</button>
      </div>
      {tab === 'bnc' ? <ImportBnc /> : <ImportZinli />}
    </div>
  );
}
