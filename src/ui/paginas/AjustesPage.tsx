import { TasasSection } from '../componentes/TasasSection';
import { CuentasSection } from '../componentes/CuentasSection';
import { CategoriasSection } from '../componentes/CategoriasSection';
import { ReglasSection } from '../componentes/ReglasSection';
import { RespaldoSection } from '../componentes/RespaldoSection';

export function AjustesPage() {
  return (
    <div>
      <h2>Ajustes</h2>
      <div className="tarjeta">
        <h3>Tasas de cambio</h3>
        <TasasSection />
      </div>
      <div className="tarjeta">
        <h3>Cuentas</h3>
        <CuentasSection />
      </div>
      <div className="tarjeta">
        <h3>Categorías</h3>
        <CategoriasSection />
      </div>
      <div className="tarjeta">
        <h3>Reglas aprendidas</h3>
        <ReglasSection />
      </div>
      <div className="tarjeta"><h3>Respaldo (Google Drive)</h3><RespaldoSection /></div>
    </div>
  );
}
