import type { Category } from '../../domain/types';

export function CategoriaGrid({ categorias, onElegir }: { categorias: Category[]; onElegir: (id: string) => void }) {
  return (
    <div className="grid-categorias">
      {categorias.map((c) => (
        <button key={c.id} type="button" onClick={() => onElegir(c.id)}>
          <span className="icono">{c.icon}</span>
          {c.name}
        </button>
      ))}
    </div>
  );
}
