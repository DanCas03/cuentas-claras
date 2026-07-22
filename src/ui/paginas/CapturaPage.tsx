import { useEffect } from 'react';
import { parseNotificacionBnc } from '../../capture/parseNotificacionBnc';
import { navigate } from '../useHashRoute';

/** Recibe el texto crudo de una notificación (MacroDroid) y redirige a Nuevo
 *  pre-llenado. Nunca guarda nada por sí misma. */
export function CapturaPage({ params }: { params: URLSearchParams }) {
  useEffect(() => {
    const texto = params.get('texto') ?? '';
    const r = parseNotificacionBnc(texto);
    const destino = r
      ? new URLSearchParams({
          tipo: r.tipo, monto: String(r.montoMinor), cuenta: 'acc-bnc', nota: r.nota,
        })
      : new URLSearchParams({ tipo: 'gasto', nota: texto.slice(0, 200) });
    navigate(`/nuevo?${destino.toString()}`);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  return <div className="vacio"><span className="icono">📨</span>Procesando notificación…</div>;
}
