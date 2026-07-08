import { TasasSection } from '../componentes/TasasSection';

export function AjustesPage() {
  return (
    <div>
      <h2>Ajustes</h2>
      <div className="tarjeta">
        <h3>Tasas de cambio</h3>
        <TasasSection />
      </div>
      {/* Tarea 14: <CuentasSection /> <CategoriasSection /> <ReglasSection /> */}
      {/* Tarea 23: <RespaldoSection /> */}
    </div>
  );
}
