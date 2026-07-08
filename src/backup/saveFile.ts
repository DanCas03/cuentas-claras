export type SaveStrategy = 'picker' | 'share' | 'download';

export function pickSaveStrategy(caps: { hasPicker: boolean; canShareFiles: boolean }): SaveStrategy {
  if (caps.hasPicker) return 'picker';
  if (caps.canShareFiles) return 'share';
  return 'download';
}

interface FsWindow {
  showSaveFilePicker?: (opts: {
    suggestedName: string;
    types: { description: string; accept: Record<string, string[]> }[];
  }) => Promise<{ createWritable(): Promise<{ write(b: Blob): Promise<void>; close(): Promise<void> }> }>;
}

/**
 * Guarda el JSON por el mejor camino disponible:
 * - Windows/Chromium: File System Access API (elige carpeta, p. ej. la de Google Drive).
 * - Android: share sheet con archivo (elige Drive) — Chrome Android no tiene showSaveFilePicker.
 * - Resto: descarga clásica.
 * Lanza AbortError si el usuario cancela el diálogo.
 */
export async function saveJsonFile(json: string, filename: string): Promise<SaveStrategy> {
  const archivo = new File([json], filename, { type: 'application/json' });
  const w = window as unknown as FsWindow;
  const estrategia = pickSaveStrategy({
    hasPicker: typeof w.showSaveFilePicker === 'function',
    canShareFiles: typeof navigator.canShare === 'function' && navigator.canShare({ files: [archivo] }),
  });
  if (estrategia === 'picker') {
    const handle = await w.showSaveFilePicker!({
      suggestedName: filename,
      types: [{ description: 'Respaldo JSON', accept: { 'application/json': ['.json'] } }],
    });
    const writable = await handle.createWritable();
    await writable.write(archivo);
    await writable.close();
  } else if (estrategia === 'share') {
    await navigator.share({ files: [archivo], title: filename });
  } else {
    const url = URL.createObjectURL(archivo);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
  }
  return estrategia;
}
