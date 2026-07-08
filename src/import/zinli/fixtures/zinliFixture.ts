/**
 * Líneas como las devuelve extractPdfLines sobre un resumen mensual (datos inventados).
 *
 * Desvío deliberado frente al brief (Tarea 20, reality-fix de la Tarea 19): la línea de
 * "Saldo final del mes" NO trae la ligadura Unicode «ﬁ» (U+FB01) como carácter único.
 * pdfjs-dist v6 decodifica esa ligadura como un TextItem SEPARADO de "nal", y
 * extractPdfLines une items adyacentes con un espacio forzado, así que la línea real
 * llega como "Saldo fi nal del mes: $84.20" (con espacio de por medio). Se reproduce esa
 * estructura real aquí en vez de la ligadura de un solo carácter que asumía el brief.
 * Las demás ligaduras de cabecera (identiﬁcación, Ediﬁcio, conﬁdencial) se dejan con
 * U+FB01 tal como en el brief: siguen ejercitando el NFKC de normalizePdfLine.
 */
export const ZINLI_LINES: string[] = [
  'Resumen de movimientos:',
  'Desde el 01 de junio al 30 de junio de 2026',
  'Nombre: Maria Jose Perez Rodriguez Límite: $1,000.00',
  'Tarjeta número: 400000*****0002 Saldo inicial del mes: $10.19',
  'Email: ejemplo@correo.com Saldo fi nal del mes: $84.20',
  'Teléfono: +584140000001 En Zinli desde: Abril de 2023',
  'Documento de identiﬁcación: 000000001 Estado de la billetera: Activa',
  'Dirección: Calle Ficticia, Ediﬁcio Ejemplo, Caracas, Venezuela',
  'Fecha Motivo Descripción Referencia Monto',
  '01/06/2026 Pagaste CUOTA MENSUAL MANT. Y USO',
  'T.VISA JUNIO 373700000001 -$0.99',
  '19/06/2026 Pagaste TIENDA EJEMPLO +14150000000 US 950100000002 -$5.00',
  '28/06/2026 Recibiste Desde Banco Ejemplo',
  'ejemplo@correo.com 871300000003 $100.00',
  '29/06/2026 Pagaste SERVICIO EJEMPLO* SUSCRIP',
  '+14150000000 US 314000000004 -$20.00',
  '1/1',
  'Tu dinero Zin límite',
  'Todos los derechos reservados. | Zinli.com',
  'La información conﬁdencial no podrá ser reproducida por ningún medio ni en ningún formato por el Receptor',
];
