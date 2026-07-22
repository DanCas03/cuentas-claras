import { parseNotificacionBnc } from './parseNotificacionBnc';
import { NOTIFICACION_PAGO_MOVIL, NOTIFICACION_COMPRA } from './fixtures/notificacionesBnc';

test('pago móvil recibido → ingreso en Bs con monto exacto y referencia en la nota', () => {
  const r = parseNotificacionBnc(NOTIFICACION_PAGO_MOVIL);
  expect(r).toEqual({
    tipo: 'ingreso',
    montoMinor: 123456, // Bs.1234,56
    nota: 'Pago móvil recibido · ref 123456789',
  });
});

test('compra POS → gasto en Bs con monto exacto y referencia en la nota', () => {
  const r = parseNotificacionBnc(NOTIFICACION_COMPRA);
  expect(r).toEqual({
    tipo: 'gasto',
    montoMinor: 789012, // Bs.7890,12
    nota: 'Compra POS · ref 654321',
  });
});

test('monto sin separador de miles con muchas cifras (formato real del BNC)', () => {
  const r = parseNotificacionBnc(
    'BNC Pago Movil Recibido Bs.250000,00 Telf.0412***9999 Dia:15/07/26-10:31 Ref:987654321 Llamar al 0500-2625000 si no realizo esta Operacion',
  );
  expect(r?.montoMinor).toBe(25000000);
});

test('el texto puede llegar con saltos de línea (notificación multilínea) y se reconoce igual', () => {
  const partido = NOTIFICACION_PAGO_MOVIL.replace(' Dia:', '\nDia:').replace(' Ref:', '\nRef:');
  expect(parseNotificacionBnc(partido)?.montoMinor).toBe(123456);
});

test('textos no reconocidos → null', () => {
  expect(parseNotificacionBnc('')).toBeNull();
  expect(parseNotificacionBnc('hola mundo')).toBeNull();
  expect(parseNotificacionBnc('BNC te informa algo sin monto')).toBeNull();
});
