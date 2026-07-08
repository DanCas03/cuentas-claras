import { useState } from 'react';
import { extractPdfLines } from '../../import/zinli/extractPdfText';

export function ImportZinli() {
  const [lineas, setLineas] = useState<string[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  return (
    <div>
      <p>Sube el "Resumen de movimientos" mensual de Zinli (PDF).</p>
      <input
        aria-label="Archivo PDF"
        type="file"
        accept=".pdf,application/pdf"
        onChange={async (e) => {
          const f = e.target.files?.[0];
          if (!f) return;
          try {
            setError(null);
            setLineas(await extractPdfLines(await f.arrayBuffer()));
          } catch (err) {
            setError((err as Error).message);
          }
        }}
      />
      {error && <div className="banner error">No se pudo leer el PDF: {error}</div>}
      {lineas && (
        <details open>
          <summary>Texto extraído ({lineas.length} líneas)</summary>
          <textarea aria-label="Texto extraído" readOnly rows={12} value={lineas.join('\n')} />
        </details>
      )}
    </div>
  );
}
