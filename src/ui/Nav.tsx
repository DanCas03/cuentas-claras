import { navigate } from './useHashRoute';

export function Nav({ path }: { path: string }) {
  const enlace = (ruta: string, icono: string, etiqueta: string) => (
    <a href={`#${ruta}`} className={path === ruta ? 'activo' : ''}>
      <span className="icono">{icono}</span>
      {etiqueta}
    </a>
  );
  return (
    <nav className="nav">
      {enlace('/', '📊', 'Inicio')}
      {enlace('/movimientos', '📋', 'Movs')}
      <div>
        <button className="fab" aria-label="Nuevo" onClick={() => navigate('/nuevo?tipo=gasto')}>+</button>
      </div>
      {enlace('/importar', '📥', 'Importar')}
      {enlace('/ajustes', '⚙️', 'Ajustes')}
    </nav>
  );
}
