import type { ClockSnapshot } from './stream';

async function post<T>(path: string): Promise<T> {
  const res = await fetch(path, { method: 'POST' });
  if (!res.ok) {
    throw new Error(`${path} -> ${res.status}`);
  }
  return res.json() as Promise<T>;
}

export const sim = {
  play: () => post<ClockSnapshot>('/api/sim/play'),
  pause: () => post<ClockSnapshot>('/api/sim/pause'),
  speed: (value: number) => post<ClockSnapshot>(`/api/sim/speed/${value}`),
  jump: (isoTime: string) => post<ClockSnapshot>(`/api/sim/jump?time=${encodeURIComponent(isoTime)}`),
  injectDelay: () => post('/api/sim/inject/delay'),
  injectGateChange: () => post('/api/sim/inject/gate-change'),
  injectScreenOffline: () => post('/api/sim/inject/screen-offline'),
};
