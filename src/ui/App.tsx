import { useEffect, useState } from 'react';
import { db, seedIfEmpty } from '../db/db';
import { useHashRoute } from './useHashRoute';
import { Nav } from './Nav';
import { Toast } from './Toast';
import { DashboardPage } from './paginas/DashboardPage';
import { NuevoPage } from './paginas/NuevoPage';
import { MovimientosPage } from './paginas/MovimientosPage';
import { ImportarPage } from './paginas/ImportarPage';
import { AjustesPage } from './paginas/AjustesPage';
import { refreshRatesOncePerDay } from '../rates/providers';
import { todayIso } from '../domain/dates';

export function App() {
  const [listo, setListo] = useState(false);
  const { path, params } = useHashRoute();

  useEffect(() => {
    seedIfEmpty(db)
      .then(() => setListo(true))
      .then(() => refreshRatesOncePerDay(db, todayIso()))
      .catch(() => {}); // sin red o sin proveedor: silencioso, la UI muestra la tasa vieja
  }, []);

  if (!listo) return null;
  return (
    <div className="app">
      <main className="contenido">
        {path === '/' && <DashboardPage />}
        {path === '/nuevo' && <NuevoPage params={params} />}
        {path === '/movimientos' && <MovimientosPage />}
        {path === '/importar' && <ImportarPage />}
        {path === '/ajustes' && <AjustesPage />}
      </main>
      <Nav path={path} />
      <Toast />
    </div>
  );
}
