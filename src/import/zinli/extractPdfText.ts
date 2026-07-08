/** Reconstruye las líneas visuales del PDF agrupando items de texto por su coordenada Y. */
export async function extractPdfLines(data: ArrayBuffer): Promise<string[]> {
  const pdfjs = await import('pdfjs-dist');
  const worker = await import('pdfjs-dist/build/pdf.worker.min.mjs?url');
  pdfjs.GlobalWorkerOptions.workerSrc = worker.default;

  const doc = await pdfjs.getDocument({ data }).promise;
  const lineas: string[] = [];
  try {
    for (let p = 1; p <= doc.numPages; p++) {
      const page = await doc.getPage(p);
      const contenido = await page.getTextContent();
      const porY: { y: number; items: { x: number; str: string }[] }[] = [];
      for (const item of contenido.items) {
        if (!('str' in item) || item.str.trim() === '') continue;
        const x = item.transform[4] as number;
        const y = item.transform[5] as number;
        const linea = porY.find((l) => Math.abs(l.y - y) <= 2); // misma baseline ±2pt
        if (linea) linea.items.push({ x, str: item.str });
        else porY.push({ y, items: [{ x, str: item.str }] });
      }
      porY.sort((a, b) => b.y - a.y); // Y crece hacia arriba en PDF → mayor Y = más arriba
      for (const l of porY) {
        l.items.sort((a, b) => a.x - b.x);
        lineas.push(l.items.map((i) => i.str).join(' ').replace(/\s+/g, ' ').trim());
      }
    }
  } finally {
    // En pdfjs-dist v6, destroy() vive en PDFDocumentLoadingTask (ya no en PDFDocumentProxy).
    await doc.loadingTask.destroy();
  }
  return lineas;
}
