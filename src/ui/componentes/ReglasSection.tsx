import { useLiveQuery } from 'dexie-react-hooks';
import { db } from '../../db/db';

export function ReglasSection() {
  const reglas = useLiveQuery(() => db.rules.toArray(), []) ?? [];
  const categorias = useLiveQuery(() => db.categories.toArray(), []) ?? [];
  const nombre = (id: string) => categorias.find((c) => c.id === id)?.name ?? '?';

  if (reglas.length === 0) {
    return <div className="vacio"><span className="icono">🪄</span>Aún no hay reglas. Se crean al categorizar en los imports.</div>;
  }
  return (
    <div>
      {reglas.map((r) => (
        <div key={r.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '6px 0' }}>
          <span>«{r.pattern}» → {nombre(r.categoryId)}</span>
          <button className="chip" onClick={() => db.rules.delete(r.id)}>eliminar</button>
        </div>
      ))}
    </div>
  );
}
