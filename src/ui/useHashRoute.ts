import { useEffect, useState } from 'react';

export function navigate(to: string): void {
  window.location.hash = to;
}

export function useHashRoute(): { path: string; params: URLSearchParams } {
  const [hash, setHash] = useState(() => window.location.hash);
  useEffect(() => {
    const onChange = () => setHash(window.location.hash);
    window.addEventListener('hashchange', onChange);
    return () => window.removeEventListener('hashchange', onChange);
  }, []);
  const limpio = hash.startsWith('#') ? hash.slice(1) : hash;
  const [path = '/', query = ''] = limpio.split('?');
  return { path: path || '/', params: new URLSearchParams(query) };
}
