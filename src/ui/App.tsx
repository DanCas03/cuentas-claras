import { useEffect, useState } from 'react';
import { db, seedIfEmpty } from '../db/db';
import { useHashRoute } from './useHashRoute';
import { Nav } from './Nav';
import { Toast } from './Toast';
import { DashboardPage } from './paginas/DashboardPage';
import { NuevoPage } from './paginas/NuevoPage';
import { CapturaPage } from './paginas/CapturaPage';
import { MovimientosPage } from './paginas/MovimientosPage';
import { ImportarPage } from './paginas/ImportarPage';
import { AjustesPage } from './paginas/AjustesPage';
import { DeudasPage } from './paginas/DeudasPage';
import { refreshRatesOncePerDay } from '../rates/providers';
import { todayIso } from '../domain/dates';

export function App() {
  const [listo, setListo] = useState(false);
  const [errorInicio, setErrorInicio] = useState(false);
  const { path, params } = useHashRoute();

  useEffect(() => {
    seedIfEmpty(db)
      .then(() => {
        setListo(true);
        return refreshRatesOncePerDay(db, todayIso()).catch(() => {}); // sin red o sin proveedor: silencioso, la UI muestra la tasa vieja
      })
      .catch(() => setErrorInicio(true)); // fallo real de IndexedDB al sembrar: no dejar la pantalla en blanco sin aviso
  }, []);

  if (errorInicio) {
    return (
      <div className="vacio" style={{ paddingTop: 80 }}>
        <span className="icono">⚠️</span>
        No se pudo iniciar la base de datos local. Cierra y vuelve a abrir la app.
      </div>
    );
  }
  if (!listo) return null;
  return (
    <div className="app">
      <main className="contenido">
        {path === '/' && <DashboardPage />}
        {path === '/nuevo' && <NuevoPage params={params} />}
        {path === '/captura' && <CapturaPage params={params} />}
        {path === '/movimientos' && <MovimientosPage />}
        {path === '/importar' && <ImportarPage />}
        {path === '/ajustes' && <AjustesPage />}
        {path === '/deudas' && <DeudasPage />}
      </main>
      <Nav path={path} />
      <Toast />
    </div>
  );
}
