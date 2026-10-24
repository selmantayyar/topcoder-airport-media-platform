import { useEffect, useMemo, useState } from 'react';
import {
  ApiError,
  campaigns,
  errorMessage,
  screens as screensApi,
  type AudienceMatch,
  type BuilderOptions,
  type CampaignDraft,
  type CampaignHistoryRow,
  type CreativeSummary,
  type Haul,
  type ScreenSummary,
  type SpendTier,
} from '../server/api';
import { useClock } from '../server/stream';

interface Props {
  advertiserId: number;
  /** A library creative to reuse; when set, the copy fields are replaced by it. */
  libraryCreative: CreativeSummary | null;
  /** Pre-fill from a past campaign (one-click duplicate). Remount the builder to apply a new one. */
  initialDraft: CampaignDraft | null;
  onClearCreative: () => void;
  onSaved: (row: CampaignHistoryRow) => void;
}

const DAY_MS = 86_400_000;

/**
 * The campaign builder: creative, placement (zones/screens/gates), audience rules, scoring preferences,
 * schedule and budget — with a live audience-match preview computed by the real decision pipeline and a
 * mock-screen render of the ad. Saving makes the campaign RUNNING; the pipeline picks it up next tick.
 */
export function CampaignBuilder({ advertiserId, libraryCreative, initialDraft, onClearCreative, onSaved }: Props) {
  const { clock } = useClock();
  const [options, setOptions] = useState<BuilderOptions | null>(null);
  const [allScreens, setAllScreens] = useState<ScreenSummary[]>([]);
  const [form, setForm] = useState(() => (initialDraft ? fromDraft(initialDraft) : initialForm(clock?.simTime)));
  const [match, setMatch] = useState<AudienceMatch | null>(null);
  const [previewError, setPreviewError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    campaigns.options().then(setOptions).catch(() => undefined);
    screensApi.list().then(setAllScreens).catch(() => undefined);
  }, []);

  const draft: CampaignDraft = useMemo(
    () => toDraft(form, advertiserId, libraryCreative),
    [form, advertiserId, libraryCreative],
  );

  // Live audience-match preview, debounced so typing doesn't flood the pipeline simulation.
  useEffect(() => {
    const timer = setTimeout(() => {
      // Name and copy don't affect the audience estimate, so placeholder them rather than flag
      // validation errors before the retailer has typed anything. Saving validates the real values.
      const previewDraft = {
        ...draft,
        name: draft.name || 'Untitled campaign',
        headline: draft.creativeId != null ? null : draft.headline || 'Preview',
      };
      campaigns
        .preview(previewDraft)
        .then((m) => {
          setMatch(m);
          setPreviewError(null);
        })
        .catch((e: unknown) => setPreviewError(errorMessage(e, 'Preview unavailable')));
    }, 500);
    return () => clearTimeout(timer);
  }, [draft]);

  const set = <K extends keyof FormState>(key: K, value: FormState[K]) => setForm((f) => ({ ...f, [key]: value }));
  const toggle = <T,>(list: T[], value: T) => (list.includes(value) ? list.filter((v) => v !== value) : [...list, value]);

  const save = () => {
    setSaving(true);
    setFieldErrors({});
    campaigns
      .create(draft)
      .then(onSaved)
      .catch((e: unknown) => {
        if (e instanceof ApiError) {
          setFieldErrors(Object.fromEntries(e.body.fieldErrors.map((f) => [f.field, f.message])));
          if (e.body.fieldErrors.length === 0) setFieldErrors({ _: e.body.message });
        }
      })
      .finally(() => setSaving(false));
  };

  const err = (field: string) => fieldErrors[field] && <span className="field-error">{fieldErrors[field]}</span>;

  return (
    <div className="builder">
      <div className="builder__form">
        <fieldset>
          <legend>Campaign</legend>
          <label>Name<input value={form.name} onChange={(e) => set('name', e.target.value)} />{err('name')}</label>
        </fieldset>

        <fieldset>
          <legend>Creative</legend>
          {libraryCreative ? (
            <div className="builder__library-pick">
              <span>Reusing library creative: <strong>{libraryCreative.headline}</strong></span>
              <button type="button" onClick={onClearCreative}>Write new copy instead</button>
            </div>
          ) : (
            <>
              <label>Headline<input value={form.headline} onChange={(e) => set('headline', e.target.value)} /></label>
              <label>Offer<input value={form.offerText} onChange={(e) => set('offerText', e.target.value)} /></label>
              <label>Body<textarea rows={2} value={form.body} onChange={(e) => set('body', e.target.value)} /></label>
              <label>Language
                <select value={form.language} onChange={(e) => set('language', e.target.value)}>
                  <option value="en">English</option><option value="ar">Arabic</option><option value="es">Spanish</option>
                </select>
              </label>
              {err('creativeProvided')}
            </>
          )}
        </fieldset>

        <fieldset>
          <legend>Placement</legend>
          <span className="builder__hint">Leave everything empty to play on any screen.</span>
          <div className="chips">
            {(options?.zones ?? []).map((z) => (
              <button
                key={z.id}
                type="button"
                className={form.zoneIds.includes(z.id) ? 'chip is-on' : 'chip'}
                onClick={() => set('zoneIds', toggle(form.zoneIds, z.id))}
              >
                {z.name}
              </button>
            ))}
          </div>
          <label>Screens
            <select
              multiple
              size={4}
              value={form.screenIds.map(String)}
              onChange={(e) => set('screenIds', Array.from(e.target.selectedOptions, (o) => Number(o.value)))}
            >
              {allScreens.map((s) => (
                <option key={s.id} value={s.id}>{s.code} — {s.zoneName}</option>
              ))}
            </select>
          </label>
          <label>Gates (comma-separated)<input value={form.gates} onChange={(e) => set('gates', e.target.value)} placeholder="40, 41, 42" /></label>
        </fieldset>

        <fieldset>
          <legend>Audience rules</legend>
          <label>Flights
            <select value={form.haul} onChange={(e) => set('haul', e.target.value as Haul | '')}>
              <option value="">Domestic or international</option>
              <option value="INTERNATIONAL">International only</option>
              <option value="DOMESTIC">Domestic only</option>
            </select>
          </label>
          <span className="builder__hint">Destinations (none = any)</span>
          <div className="chips">
            {(options?.destinations ?? []).map((d) => (
              <button
                key={d}
                type="button"
                className={form.destinations.includes(d) ? 'chip is-on' : 'chip'}
                onClick={() => set('destinations', toggle(form.destinations, d))}
              >
                {d}
              </button>
            ))}
          </div>
          <div className="builder__row">
            <label>Min dwell (min)<input type="number" min={0} value={form.minDwellMinutes} onChange={(e) => set('minDwellMinutes', num(e.target.value))} /></label>
            <label>Min delay (min)<input type="number" min={0} value={form.minDelayMinutes} onChange={(e) => set('minDelayMinutes', num(e.target.value))} /></label>
            <label>Min crowd<input type="number" min={0} value={form.minCrowd} onChange={(e) => set('minCrowd', num(e.target.value))} /></label>
          </div>
          <div className="builder__row">
            {(['weightBusiness', 'weightHoliday', 'weightFamily'] as const).map((k) => (
              <label key={k}>{WEIGHT_LABELS[k]} <output>{form[k].toFixed(1)}</output>
                <input type="range" min={0} max={1} step={0.1} value={form[k]} onChange={(e) => set(k, Number(e.target.value))} />
              </label>
            ))}
          </div>
          <label>Spend tier
            <select value={form.spendTarget} onChange={(e) => set('spendTarget', e.target.value as SpendTier)}>
              <option value="LOW">Value</option><option value="MEDIUM">Mid-market</option><option value="HIGH">Premium</option>
            </select>
          </label>
        </fieldset>

        <fieldset>
          <legend>Schedule &amp; budget</legend>
          <div className="builder__row">
            <label>Start (UTC)<input type="datetime-local" value={form.start} onChange={(e) => set('start', e.target.value)} /></label>
            <label>End (UTC)<input type="datetime-local" value={form.end} onChange={(e) => set('end', e.target.value)} />{err('scheduleOrdered')}</label>
          </div>
          <div className="builder__row">
            <label>Budget ($)<input type="number" min={1} value={form.budget} onChange={(e) => set('budget', num(e.target.value))} />{err('budget')}</label>
            <label>Daily cap ($)<input type="number" min={1} value={form.dailyCap} onChange={(e) => set('dailyCap', num(e.target.value))} /></label>
            <label>Plays / screen / hr<input type="number" min={1} max={50} value={form.frequencyCap} onChange={(e) => set('frequencyCap', num(e.target.value))} />{err('frequencyCapPerScreen')}</label>
          </div>
        </fieldset>

        {fieldErrors._ && <p className="field-error">{fieldErrors._}</p>}
        <button type="button" className="builder__save" onClick={save} disabled={saving}>
          {saving ? 'Saving…' : 'Save & launch campaign'}
        </button>
      </div>

      <aside className="builder__side">
        <div className="match">
          <h3>Audience match <span className="match__live">live</span></h3>
          {previewError && <p className="field-error">{previewError}</p>}
          {match ? (
            <>
              <div className="match__big">{match.matchPercent.toFixed(0)}%</div>
              <div className="match__meter"><span style={{ width: `${Math.min(100, match.matchPercent)}%` }} /></div>
              <dl className="match__grid">
                <div><dt>Screens targeted</dt><dd>{match.targetedScreens}</dd></div>
                <div><dt>Eligible now</dt><dd>{match.eligibleScreens}</dd></div>
                <div><dt>Would win</dt><dd>{match.winningScreens}</dd></div>
                <div><dt>Impressions / hr</dt><dd>{match.expectedImpressionsPerHour.toLocaleString()}</dd></div>
                <div><dt>Redemptions / hr</dt><dd>{match.expectedRedemptionsPerHour.toLocaleString()}</dd></div>
              </dl>
              <p className="builder__hint">Estimated as if it ran now, against the live crowd and competing campaigns.</p>
            </>
          ) : (
            !previewError && <p className="builder__hint">Calculating…</p>
          )}
        </div>

        <div className="mock-screen" aria-label="Mock screen preview">
          <div className="ad">
            <span className="ad__brand">Preview</span>
            <h1 className="ad__headline">{libraryCreative?.headline ?? (form.headline || 'Your headline')}</h1>
            {(libraryCreative?.offerText ?? form.offerText) && <p className="ad__offer">{libraryCreative?.offerText ?? form.offerText}</p>}
            {(libraryCreative?.body ?? form.body) && <p className="ad__body">{libraryCreative?.body ?? form.body}</p>}
          </div>
        </div>
      </aside>
    </div>
  );
}

interface FormState {
  name: string;
  headline: string;
  offerText: string;
  body: string;
  language: string;
  zoneIds: number[];
  screenIds: number[];
  gates: string;
  destinations: string[];
  haul: Haul | '';
  minDwellMinutes: number;
  minDelayMinutes: number;
  minCrowd: number;
  weightBusiness: number;
  weightHoliday: number;
  weightFamily: number;
  spendTarget: SpendTier;
  start: string;
  end: string;
  budget: number;
  dailyCap: number;
  frequencyCap: number;
}

const WEIGHT_LABELS = { weightBusiness: 'Business', weightHoliday: 'Holiday', weightFamily: 'Family' } as const;

function initialForm(simTime: string | undefined): FormState {
  const start = simTime ? new Date(simTime) : new Date();
  return {
    name: '',
    headline: '',
    offerText: '',
    body: '',
    language: 'en',
    zoneIds: [],
    screenIds: [],
    gates: '',
    destinations: [],
    haul: '',
    minDwellMinutes: 0,
    minDelayMinutes: 0,
    minCrowd: 0,
    weightBusiness: 0.5,
    weightHoliday: 0.5,
    weightFamily: 0.5,
    spendTarget: 'MEDIUM',
    start: toLocalInput(start),
    end: toLocalInput(new Date(start.getTime() + 7 * DAY_MS)),
    budget: 10000,
    dailyCap: 1000,
    frequencyCap: 4,
  };
}

/** The inverse of {@link toDraft}: a saved campaign's draft back into editable form state. */
function fromDraft(d: CampaignDraft): FormState {
  return {
    name: d.name,
    headline: d.headline ?? '',
    offerText: d.offerText ?? '',
    body: d.body ?? '',
    language: d.language ?? 'en',
    zoneIds: d.zoneIds,
    screenIds: d.screenIds,
    gates: d.gates.join(', '),
    destinations: d.destinations,
    haul: d.haul ?? '',
    minDwellMinutes: d.minDwellMinutes,
    minDelayMinutes: d.minDelayMinutes,
    minCrowd: d.minCrowd,
    weightBusiness: d.weightBusiness,
    weightHoliday: d.weightHoliday,
    weightFamily: d.weightFamily,
    spendTarget: d.spendTarget ?? 'MEDIUM',
    start: toLocalInput(new Date(d.scheduleStart)),
    end: toLocalInput(new Date(d.scheduleEnd)),
    budget: d.budget,
    dailyCap: d.dailyCap ?? 0,
    frequencyCap: d.frequencyCapPerScreen,
  };
}

function toDraft(f: FormState, advertiserId: number, creative: CreativeSummary | null): CampaignDraft {
  return {
    advertiserId,
    name: f.name,
    creativeId: creative?.id ?? null,
    headline: creative ? null : f.headline || null,
    offerText: creative ? null : f.offerText || null,
    body: creative ? null : f.body || null,
    language: creative ? null : f.language,
    gates: f.gates.split(',').map((g) => g.trim()).filter(Boolean),
    zoneIds: f.zoneIds,
    screenIds: f.screenIds,
    destinations: f.destinations,
    haul: f.haul || null,
    minDwellMinutes: f.minDwellMinutes,
    minDelayMinutes: f.minDelayMinutes,
    minCrowd: f.minCrowd,
    weightBusiness: f.weightBusiness,
    weightHoliday: f.weightHoliday,
    weightFamily: f.weightFamily,
    spendTarget: f.spendTarget,
    scheduleStart: fromLocalInput(f.start),
    scheduleEnd: fromLocalInput(f.end),
    budget: f.budget,
    dailyCap: f.dailyCap || null,
    frequencyCapPerScreen: f.frequencyCap,
  };
}

/** datetime-local works in wall time; the simulation runs in UTC, so we treat the field as UTC. */
function toLocalInput(d: Date): string {
  return d.toISOString().slice(0, 16);
}

function fromLocalInput(value: string): string {
  return `${value}:00Z`;
}

function num(value: string): number {
  const n = Number(value);
  return Number.isFinite(n) ? n : 0;
}

