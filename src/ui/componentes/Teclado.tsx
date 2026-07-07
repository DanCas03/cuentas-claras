const TECLAS = ['1', '2', '3', '4', '5', '6', '7', '8', '9', ',', '0', '⌫'] as const;

export function Teclado({ onTecla }: { onTecla: (t: string) => void }) {
  return (
    <div className="teclado">
      {TECLAS.map((t) => (
        <button
          key={t}
          type="button"
          aria-label={t === '⌫' ? 'borrar' : t === ',' ? 'coma' : t}
          onClick={() => onTecla(t)}
        >
          {t}
        </button>
      ))}
    </div>
  );
}
