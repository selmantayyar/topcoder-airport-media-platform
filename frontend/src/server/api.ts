import type { ClockSnapshot } from './stream';

async function post<T>(path: string): Promise<T> {
  const res = await fetch(path, { method: 'POST' });
  if (!res.ok) {
    throw new Error(`${path} -> ${res.status}`);
  }
  return res.json() as Promise<T>;
}

/** The backend's shared error shape; `fieldErrors` is set for validation failures. */
export interface ErrorResponse {
  status: number;
  error: string;
  message: string;
  fieldErrors: { field: string; message: string }[];
}

export class ApiError extends Error {
  readonly body: ErrorResponse;

  constructor(body: ErrorResponse) {
    super(body.message);
    this.body = body;
  }
}

/** The human-readable reason from any thrown value: first field error, else the server message. */
export function errorMessage(e: unknown, fallback = 'Something went wrong'): string {
  if (e instanceof ApiError) return e.body.fieldErrors[0]?.message ?? e.body.message;
  return fallback;
}

async function toApiError(res: Response, path: string): Promise<ApiError> {
  const fallback: ErrorResponse = { status: res.status, error: res.statusText, message: `${path} -> ${res.status}`, fieldErrors: [] };
  return new ApiError(await res.json().catch(() => fallback));
}

async function postJson<T>(path: string, body?: unknown): Promise<T> {
  return sendJson<T>('POST', path, body);
}

async function sendJson<T>(method: 'POST' | 'PUT' | 'PATCH', path: string, body?: unknown): Promise<T> {
  const res = await fetch(path, {
    method,
    headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!res.ok) throw await toApiError(res, path);
  return res.json() as Promise<T>;
}

async function get<T>(path: string): Promise<T> {
  const res = await fetch(path);
  if (!res.ok) throw await toApiError(res, path);
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
  /** With a screenId, the injection lands on a flight (or the screen) the split-screen demo is showing. */
  injectDelay: (minutes?: number, screenId?: number) =>
    post(`/api/sim/inject/delay${query({ minutes, screenId })}`),
  injectGateChange: (screenId?: number) => post(`/api/sim/inject/gate-change${query({ screenId })}`),
  injectScreenOffline: (screenId?: number) => post(`/api/sim/inject/screen-offline${query({ screenId })}`),
  injectScreenOnline: (screenId?: number) => post(`/api/sim/inject/screen-online${query({ screenId })}`),
};

function query(params: Record<string, number | undefined>): string {
  const set = Object.entries(params).filter(([, v]) => v != null);
  return set.length ? `?${set.map(([k, v]) => `${k}=${v}`).join('&')}` : '';
}

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
  scan: (code: string) =>
    fetch(`/api/promo/${encodeURIComponent(code)}/scan`, { method: 'POST' }).catch(() => undefined),
  share: (code: string) => postJson<PromoIssued>(`/api/promo/${encodeURIComponent(code)}/share`),
};

export interface PromoIssued {
  code: string;
  parentCode: string | null;
  campaignId: number | null;
  validUntil: string;
  expiresAt: string;
  linkedFlightNumber: string | null;
  claimPath: string;
}

// --- Retailer dashboard (ticket 08) -----------------------------------------

export interface RetailerSummary {
  id: number;
  name: string;
  kind: string;
  outletCount: number;
}

export interface CampaignMetric {
  campaignId: number;
  name: string;
  status: string;
  impressions: number;
  scans: number;
  redemptions: number;
  revenue: number;
}

export interface DashboardMetrics {
  advertiserId: number;
  advertiserName: string;
  impressions: number;
  scans: number;
  redemptions: number;
  conversionRate: number;
  attributedRevenue: number;
  campaigns: CampaignMetric[];
}

export interface LabelCount {
  label: string;
  value: number;
}

export interface FlowWindow {
  minutes: number;
  footfall: number;
  topDestinations: LabelCount[];
  topNationalities: LabelCount[];
  /** Delayed departures in the window and their passengers — a captive crowd near the stores. */
  delayedDepartures: number;
  delayedPassengers: number;
}

export interface PassengerFlow {
  next60: FlowWindow;
  next120: FlowWindow;
}

export interface OutletSummary {
  id: number;
  name: string;
  type: string;
  zoneName: string | null;
  terminalCode: string | null;
}

export const retailers = {
  list: () => get<RetailerSummary[]>('/api/retailers'),
  campaigns: (id: number) => get<CampaignHistoryRow[]>(`/api/retailers/${id}/campaigns`),
  creatives: (id: number) => get<CreativeSummary[]>(`/api/retailers/${id}/creatives`),
  outlets: (id: number) => get<OutletSummary[]>(`/api/retailers/${id}/outlets`),
  metrics: (id: number) => get<DashboardMetrics>(`/api/retailers/${id}/metrics`),
  /** Brief in, ad page out: English + Spanish pages saved to the library (ticket 13). */
  generateCreative: (id: number, brief: string) =>
    postJson<GeneratedCreative>(`/api/retailers/${id}/creatives/assistant`, { brief }),
  flow: (id: number, outletId?: number | null) =>
    get<PassengerFlow>(`/api/retailers/${id}/flow${outletId ? `?outletId=${outletId}` : ''}`),
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

// --- Campaign builder, library, history (ticket 09) -------------------------

export type Haul = 'DOMESTIC' | 'INTERNATIONAL';
export type SpendTier = 'LOW' | 'MEDIUM' | 'HIGH';

export interface CampaignDraft {
  advertiserId: number;
  name: string;
  creativeId: number | null;
  headline: string | null;
  offerText: string | null;
  body: string | null;
  language: string | null;
  gates: string[];
  zoneIds: number[];
  screenIds: number[];
  destinations: string[];
  haul: Haul | null;
  minDwellMinutes: number;
  minDelayMinutes: number;
  minCrowd: number;
  weightBusiness: number;
  weightHoliday: number;
  weightFamily: number;
  spendTarget: SpendTier | null;
  scheduleStart: string;
  scheduleEnd: string;
  budget: number;
  dailyCap: number | null;
  frequencyCapPerScreen: number;
}

export interface AudienceMatch {
  targetedScreens: number;
  eligibleScreens: number;
  winningScreens: number;
  matchPercent: number;
  expectedImpressionsPerHour: number;
  expectedRedemptionsPerHour: number;
}

export interface CampaignHistoryRow {
  campaignId: number;
  name: string;
  status: string;
  scheduleStart: string;
  scheduleEnd: string;
  budget: number;
  spend: number;
  impressions: number;
  redemptions: number;
  revenue: number;
  roi: number;
}

export interface CreativeSummary {
  id: number;
  campaignId: number | null;
  campaignName: string | null;
  headline: string;
  offerText: string | null;
  body: string | null;
  language: string;
  templateKey: string;
}

export interface AdPage {
  creativeId: number;
  language: string;
  templateKey: string;
  headline: string;
  offerText: string | null;
  body: string | null;
}

export interface GeneratedCreative {
  brief: string;
  /** Which assistant wrote the saved copy: "mock" (offline) or "llm". */
  provider: string;
  pages: AdPage[];
  /** Why any copy was rejected on the way, e.g. LLM copy citing a fact not in the brief. */
  notes: string[];
}

export interface SlotRecommendation {
  rank: number;
  zoneId: number;
  zoneName: string;
  start: string;
  end: string;
  predictedImpressions: number;
  winningScreens: number;
  peakHeadcount: number;
  averageScore: number;
}

export interface SlotRecommendations {
  campaignId: number;
  campaignName: string;
  day: string;
  slots: SlotRecommendation[];
  method: string;
}

export interface BuilderOptions {
  zones: { id: number; name: string; kind: string; terminalCode: string | null }[];
  destinations: string[];
  upcomingFlights: { id: number; flightNumber: string; destination: string; gate: string; departure: string }[];
}

export const campaigns = {
  options: () => get<BuilderOptions>('/api/campaigns/options'),
  preview: (draft: CampaignDraft) => postJson<AudienceMatch>('/api/campaigns/preview', draft),
  create: (draft: CampaignDraft) => postJson<CampaignHistoryRow>('/api/campaigns', draft),
  /** A past campaign as a pre-filled builder draft (one-click duplicate). */
  draft: (id: number) => get<CampaignDraft>(`/api/campaigns/${id}/draft`),
  runAgain: (id: number) => postJson<CampaignHistoryRow>(`/api/campaigns/${id}/run-again`),
  issuePromo: (id: number, flightId: number | null, validMinutes: number) =>
    postJson<PromoIssued>(`/api/campaigns/${id}/promo-codes`, { flightId, validMinutes }),
  /** The best 3 zone-hours for this campaign tomorrow, from the decision pipeline (ticket 13). */
  recommendedSlots: (id: number) => get<SlotRecommendations>(`/api/campaigns/${id}/recommended-slots`),
};

// --- Platform Admin console (ticket 10) --------------------------------------

export interface AdminKpis {
  screensOnlinePct: number;
  screensTotal: number;
  adPlaysToday: number;
  audienceImpressionsToday: number;
  revenueToday: number;
  currency: string;
  activeCampaigns: number;
  averageLatencyMs: number;
  openIncidents: number;
  delayedDepartures: number;
}

export interface ScreenDot {
  id: number;
  code: string;
  status: 'ONLINE' | 'DEGRADED' | 'OFFLINE';
  latencyMs: number;
  statusSince: string | null;
}

export interface ZoneMap {
  id: number;
  name: string;
  kind: string;
  headcount: number;
  heat: number;
  screens: ScreenDot[];
}

export interface TerminalMap {
  id: number;
  code: string;
  name: string;
  zones: ZoneMap[];
}

export type IncidentStatus = 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED';

export interface IncidentView {
  id: number;
  type: 'SCREEN_OFFLINE' | 'FEED_LAG' | 'CREATIVE_FAILED';
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  owner: string;
  status: IncidentStatus;
  title: string;
  detail: string | null;
  raisedAt: string;
  updatedAt: string;
  resolvedAt: string | null;
}

export interface AdminOverview {
  kpis: AdminKpis;
  terminals: TerminalMap[];
  incidents: IncidentView[];
  /** Teams an incident can be assigned to, as published by the backend. */
  incidentOwners: string[];
}

export type FieldType = 'TEXT' | 'NUMBER' | 'BOOLEAN' | 'ENUM' | 'REF';

export interface MasterDataField {
  name: string;
  label: string;
  type: FieldType;
  required: boolean;
  maxLength: number | null;
  min: number | null;
  max: number | null;
  options: { value: string; label: string }[];
}

export interface MasterDataSchema {
  type: string;
  label: string;
  fields: MasterDataField[];
}

export type MasterDataRow = Record<string, unknown> & { id: number };

export const admin = {
  overview: () => get<AdminOverview>('/api/admin/overview'),
  updateIncident: (id: number, change: { status?: IncidentStatus; owner?: string }) =>
    sendJson<IncidentView>('PATCH', `/api/admin/incidents/${id}`, change),
  schemas: () => get<MasterDataSchema[]>('/api/admin/master'),
  rows: (type: string) => get<MasterDataRow[]>(`/api/admin/master/${type}`),
  create: (type: string, values: Record<string, unknown>) =>
    postJson<MasterDataRow>(`/api/admin/master/${type}`, values),
  update: (type: string, id: number, values: Record<string, unknown>) =>
    sendJson<MasterDataRow>('PUT', `/api/admin/master/${type}/${id}`, values),
};

// --- Media Manager (ticket 11) ----------------------------------------------

export interface InventorySlot {
  hour: number;
  booked: boolean;
  premium: boolean;
  footfall: number;
  price: number;
}

export interface InventoryView {
  day: string;
  hours: number[];
  screens: { screenId: number; code: string; zoneId: number | null; zoneName: string; slots: InventorySlot[] }[];
  occupancyPct: number;
  basePricePerHour: number;
  premiumFootfallThreshold: number;
  forecast: { date: string; occupancyPct: number; revenue: number }[];
  forecastTotal: number;
}

export interface ApprovalItem {
  campaignId: number;
  name: string;
  advertiserName: string;
  createdAt: string;
  scheduleStart: string;
  scheduleEnd: string;
  budget: number;
  headline: string | null;
  offerText: string | null;
  language: string | null;
  creativeProblems: string[];
}

export interface AdvertiserOverviewRow {
  advertiserId: number;
  name: string;
  kind: string;
  spend: number;
  impressions: number;
  redemptions: number;
  runningCampaigns: number;
  underDelivering: { campaignId: number; name: string; deliveredPct: number; expectedPct: number; pacePct: number }[];
}

export const media = {
  inventory: () => get<InventoryView>('/api/media/inventory'),
  approvals: () => get<ApprovalItem[]>('/api/media/approvals'),
  approve: (id: number) => postJson<ApprovalItem>(`/api/media/approvals/${id}/approve`),
  reject: (id: number) => postJson<ApprovalItem>(`/api/media/approvals/${id}/reject`),
  advertisers: () => get<AdvertiserOverviewRow[]>('/api/media/advertisers'),
};

// --- Airline Representative (ticket 12) --------------------------------------

export interface AirlineSummary {
  id: number;
  iataCode: string;
  name: string;
  lowCost: boolean;
}

export interface AirlineFlightRow {
  flightId: number;
  flightNumber: string;
  destination: string;
  gate: string | null;
  scheduledTime: string;
  departureTime: string;
  status: string;
  delayMinutes: number;
  paxCount: number;
  seats: number;
  loadFactorPct: number;
  minutesToDeparture: number;
}

export interface AirlineContentRow {
  campaignId: number;
  name: string;
  purpose: 'SPONSORED' | 'GOODWILL';
  status: string;
  flightNumber: string | null;
  gate: string | null;
  scheduleStart: string;
  scheduleEnd: string;
  coFunderName: string | null;
  headline: string | null;
  offerText: string | null;
}

export type SponsoredContentType = 'UPGRADE_OFFER' | 'LOUNGE_PASS' | 'DESTINATION_PARTNER';

export const airlines = {
  list: () => get<AirlineSummary[]>('/api/airlines'),
  flights: (id: number) => get<AirlineFlightRow[]>(`/api/airlines/${id}/flights`),
  content: (id: number) => get<AirlineContentRow[]>(`/api/airlines/${id}/content`),
  schedule: (id: number, request: { flightId: number; type: SponsoredContentType; headline: string | null; offerText: string | null }) =>
    postJson<AirlineContentRow>(`/api/airlines/${id}/content`, request),
};
