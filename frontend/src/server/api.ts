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

// --- Decision pipeline + passenger screen (ticket 06/07) --------------------

export interface CandidateScore {
  campaignId: number;
  campaignName: string;
  advertiserName: string;
  advertiserKind: string;
  eligible: boolean;
  dropReason: string | null;
  crowdFit: number;
  boardingProximity: number;
  dwellFit: number;
  spendMatch: number;
  engagementPrior: number;
  baseScore: number;
  ruleNotes: string[];
  ruleMultiplier: number;
  finalScore: number;
}

export interface DecisionResult {
  screenId: number;
  zoneId: number | null;
  decidedAt: string;
  headcount: number;
  profile: CrowdProfile | null;
  candidates: CandidateScore[];
  winnerCampaignId: number | null;
  winnerName: string | null;
  reason: string;
  fallback: boolean;
}

export interface ScreenSummary {
  id: number;
  code: string;
  name: string;
  zoneId: number | null;
  zoneName: string | null;
  nearestGates: string | null;
  orientation: string;
  status: string;
}

export interface FidsEntry {
  flightNumber: string;
  airlineName: string;
  destination: string;
  gate: string;
  scheduledTime: string;
  estimatedTime: string | null;
  status: string;
  minutesToDeparture: number;
}

export interface CreativeView {
  advertiserName: string;
  headline: string;
  body: string | null;
  offerText: string | null;
  language: string;
  templateKey: string;
}

export interface PromoPlayback {
  code: string;
  validUntil: string;
  secondsRemaining: number;
  claimPath: string;
}

export interface PlaybackView {
  screen: ScreenSummary;
  at: string;
  nearbyFlights: FidsEntry[];
  decision: DecisionResult;
  creative: CreativeView | null;
  promo: PromoPlayback | null;
}

export interface PromoInfo {
  code: string;
  campaignId: number | null;
  campaignName: string;
  headline: string;
  offerText: string;
  validUntil: string;
  redeemed: boolean;
  expired: boolean;
  secondsRemaining: number;
}

export interface ClaimResult {
  status: 'CLAIMED' | 'ALREADY_CLAIMED' | 'EXPIRED';
  code: string;
  campaignId: number | null;
  campaignName: string;
  offerText: string;
  validUntil: string;
  redeemedAt: string | null;
  segment: string | null;
  message: string;
}

export const screens = {
  list: (gate?: string, zone?: string) => {
    const params = new URLSearchParams();
    if (gate) params.set('gate', gate);
    if (zone) params.set('zone', zone);
    const query = params.toString();
    return get<ScreenSummary[]>(`/api/screens${query ? `?${query}` : ''}`);
  },
  playback: (id: number) => get<PlaybackView>(`/api/screens/${id}/playback`),
};

export const promo = {
  info: (code: string) => get<PromoInfo>(`/api/promo/${encodeURIComponent(code)}`),
  claim: (code: string) => post<ClaimResult>(`/api/promo/${encodeURIComponent(code)}/claim`),
};

// The QR must encode an address a phone on the same LAN can reach — not localhost.
// The operator sets it once; it persists in localStorage. Defaults to this origin.
const LAN_KEY = 'amp.lanBaseUrl';

export const lan = {
  base(): string {
    return localStorage.getItem(LAN_KEY) ?? window.location.origin;
  },
  set(value: string) {
    if (value.trim()) {
      localStorage.setItem(LAN_KEY, value.trim().replace(/\/$/, ''));
    } else {
      localStorage.removeItem(LAN_KEY);
    }
  },
};
