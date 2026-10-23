import type { ClockSnapshot } from './stream';

async function post<T>(path: string): Promise<T> {
  const res = await fetch(path, { method: 'POST' });
  if (!res.ok) {
    throw new Error(`${path} -> ${res.status}`);
  }
  return res.json() as Promise<T>;
}

async function get<T>(path: string): Promise<T> {
  const res = await fetch(path);
  if (!res.ok) {
    throw new Error(`${path} -> ${res.status}`);
  }
  return res.json() as Promise<T>;
}

export interface CrowdProfile {
  crowdSize: number;
  tripPurpose: Record<string, number>;
  partyType: Record<string, number>;
  ageBand: Record<string, number>;
  femalePct: number;
  spendTier: Record<string, number>;
  language: string;
  region: string;
  dwellMinutes: number;
  delayed: boolean;
  delayMinutes: number;
}

export interface FlightContribution {
  flightId: number;
  flightNumber: string;
  gate: string;
  destination: string;
  minutesToDeparture: number;
  passengers: number;
}

export interface ZonePresence {
  zoneId: number;
  zoneName: string;
  zoneKind: string;
  windowStart: string;
  windowMinutes: number;
  estimatedHeadcount: number;
  flights: FlightContribution[];
  profile: CrowdProfile | null;
}

export const presence = {
  zones: () => get<ZonePresence[]>('/api/presence'),
};

export const sim = {
  play: () => post<ClockSnapshot>('/api/sim/play'),
  pause: () => post<ClockSnapshot>('/api/sim/pause'),
  speed: (value: number) => post<ClockSnapshot>(`/api/sim/speed/${value}`),
  jump: (isoTime: string) => post<ClockSnapshot>(`/api/sim/jump?time=${encodeURIComponent(isoTime)}`),
  injectDelay: () => post('/api/sim/inject/delay'),
  injectGateChange: () => post('/api/sim/inject/gate-change'),
  injectScreenOffline: () => post('/api/sim/inject/screen-offline'),
};
