import { useEffect, useState } from 'react';

export function avisar(mensaje: string): void {
  window.dispatchEvent(new CustomEvent('toast', { detail: mensaje }));
}

export function Toast() {
  const [msg, setMsg] = useState<string | null>(null);
  useEffect(() => {
    let timer: ReturnType<typeof setTimeout> | undefined;
    const on = (e: Event) => {
      setMsg((e as CustomEvent<string>).detail);
      clearTimeout(timer);
      timer = setTimeout(() => setMsg(null), 2500);
    };
    window.addEventListener('toast', on);
    return () => {
      window.removeEventListener('toast', on);
      clearTimeout(timer);
    };
  }, []);
  if (!msg) return null;
  return <div className="toast" role="status">{msg}</div>;
}
