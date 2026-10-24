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
import { num } from '../format';
import { useI18n } from '../i18n/I18nProvider';
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
 * mock-screen render of the ad (in the UI language when the copy has it). Saving submits it to the Media Manager's approval queue; once approved,
 * the pipeline picks it up on its next tick.
 */
export function CampaignBuilder({ advertiserId, libraryCreative, initialDraft, onClearCreative, onSaved }: Props) {
  const { clock } = useClock();
  const { t, label, lang } = useI18n();
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
        .catch((e: unknown) => setPreviewError(errorMessage(e, t('builder.previewFailed'))));
    }, 500);
    return () => clearTimeout(timer);
  }, [draft, t]);

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

  const secondLanguage = form.language === 'es' ? 'en' : 'es';
  // The mock screen shows what a screen in the current UI language would: the second-language copy when the
  // retailer wrote it, else the original.
  const preview = libraryCreative
    ? { headline: libraryCreative.headline, offerText: libraryCreative.offerText, body: libraryCreative.body }
    : lang === secondLanguage && form.trHeadline
      ? { headline: form.trHeadline, offerText: form.trOfferText, body: form.trBody }
      : { headline: form.headline, offerText: form.offerText, body: form.body };

  const err = (field: string) => fieldErrors[field] && <span className="field-error">{fieldErrors[field]}</span>;

  return (
    <div className="builder">
      <div className="builder__form">
        <fieldset>
          <legend>{t('builder.campaign')}</legend>
          <label>{t('builder.name')}<input value={form.name} onChange={(e) => set('name', e.target.value)} />{err('name')}</label>
        </fieldset>

        <fieldset>
          <legend>{t('common.creative')}</legend>
          {libraryCreative ? (
            <div className="builder__library-pick">
              <span>{t('builder.reusing')} <strong>{libraryCreative.headline}</strong> ({libraryCreative.languages.map((l) => l.toUpperCase()).join(' · ')})</span>
              <button type="button" onClick={onClearCreative}>{t('builder.writeNew')}</button>
            </div>
          ) : (
            <>
              <label>{t('common.headline')}<input value={form.headline} onChange={(e) => set('headline', e.target.value)} /></label>
              <label>{t('common.offer')}<input value={form.offerText} onChange={(e) => set('offerText', e.target.value)} /></label>
              <label>{t('common.body')}<textarea rows={2} value={form.body} onChange={(e) => set('body', e.target.value)} /></label>
              <label>{t('builder.language')}
                <select value={form.language} onChange={(e) => set('language', e.target.value)}>
                  {COPY_LANGUAGES.map((l) => <option key={l} value={l}>{label('language', l)}</option>)}
                </select>
              </label>
              {err('creativeProvided')}
              <div className="builder__translation">
                <span className="builder__subhead">{t('builder.translation', { language: label('language', secondLanguage) })}</span>
                <span className="builder__hint">{t('builder.translationHint', { language: label('language', secondLanguage) })}</span>
                <label>{t('common.headline')}<input lang={secondLanguage} maxLength={200} value={form.trHeadline} onChange={(e) => set('trHeadline', e.target.value)} /></label>
                <label>{t('common.offer')}<input lang={secondLanguage} maxLength={200} value={form.trOfferText} onChange={(e) => set('trOfferText', e.target.value)} /></label>
                <label>{t('common.body')}<textarea lang={secondLanguage} maxLength={1000} rows={2} value={form.trBody} onChange={(e) => set('trBody', e.target.value)} /></label>
                {err('translations')}
              </div>
            </>
          )}
        </fieldset>

        <fieldset>
          <legend>{t('builder.placement')}</legend>
          <span className="builder__hint">{t('builder.placementHint')}</span>
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
          <label>{t('builder.screens')}
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
          <label>{t('builder.gates')}<input value={form.gates} onChange={(e) => set('gates', e.target.value)} placeholder="40, 41, 42" /></label>
        </fieldset>

        <fieldset>
          <legend>{t('builder.audience')}</legend>
          <label>{t('builder.flights')}
            <select value={form.haul} onChange={(e) => set('haul', e.target.value as Haul | '')}>
              <option value="">{t('builder.haul.any')}</option>
              <option value="INTERNATIONAL">{t('builder.haul.INTERNATIONAL')}</option>
              <option value="DOMESTIC">{t('builder.haul.DOMESTIC')}</option>
            </select>
          </label>
          <span className="builder__hint">{t('builder.destinations')}</span>
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
            <label>{t('builder.minDwell')}<input type="number" min={0} value={form.minDwellMinutes} onChange={(e) => set('minDwellMinutes', parseNum(e.target.value))} /></label>
            <label>{t('builder.minDelay')}<input type="number" min={0} value={form.minDelayMinutes} onChange={(e) => set('minDelayMinutes', parseNum(e.target.value))} /></label>
            <label>{t('builder.minCrowd')}<input type="number" min={0} value={form.minCrowd} onChange={(e) => set('minCrowd', parseNum(e.target.value))} /></label>
          </div>
          <div className="builder__row">
            {(['weightBusiness', 'weightHoliday', 'weightFamily'] as const).map((k) => (
              <label key={k}>{t(`builder.weight.${k}`)} <output>{form[k].toFixed(1)}</output>
                <input type="range" min={0} max={1} step={0.1} value={form[k]} onChange={(e) => set(k, Number(e.target.value))} />
              </label>
            ))}
          </div>
          <label>{t('builder.spendTier')}
            <select value={form.spendTarget} onChange={(e) => set('spendTarget', e.target.value as SpendTier)}>
              {SPEND_TIERS.map((tier) => <option key={tier} value={tier}>{t(`builder.tier.${tier}`)}</option>)}
            </select>
          </label>
        </fieldset>

        <fieldset>
          <legend>{t('builder.scheduleBudget')}</legend>
          <div className="builder__row">
            <label>{t('builder.start')}<input type="datetime-local" value={form.start} onChange={(e) => set('start', e.target.value)} /></label>
            <label>{t('builder.end')}<input type="datetime-local" value={form.end} onChange={(e) => set('end', e.target.value)} />{err('scheduleOrdered')}</label>
          </div>
          <div className="builder__row">
            <label>{t('builder.budget')}<input type="number" min={1} value={form.budget} onChange={(e) => set('budget', parseNum(e.target.value))} />{err('budget')}</label>
            <label>{t('builder.dailyCap')}<input type="number" min={1} value={form.dailyCap} onChange={(e) => set('dailyCap', parseNum(e.target.value))} /></label>
            <label>{t('builder.frequency')}<input type="number" min={1} max={50} value={form.frequencyCap} onChange={(e) => set('frequencyCap', parseNum(e.target.value))} />{err('frequencyCapPerScreen')}</label>
          </div>
        </fieldset>

        {fieldErrors._ && <p className="field-error">{fieldErrors._}</p>}
        <button type="button" className="builder__save" onClick={save} disabled={saving}>
          {saving ? t('builder.submitting') : t('builder.submit')}
        </button>
      </div>

      <aside className="builder__side">
        <div className="match">
          <h3>{t('builder.match')} <span className="match__live">{t('builder.live')}</span></h3>
          {previewError && <p className="field-error">{previewError}</p>}
          {match ? (
            <>
              <div className="match__big">{match.matchPercent.toFixed(0)}%</div>
              <div className="match__meter"><span style={{ width: `${Math.min(100, match.matchPercent)}%` }} /></div>
              <dl className="match__grid">
                <div><dt>{t('builder.targeted')}</dt><dd>{match.targetedScreens}</dd></div>
                <div><dt>{t('builder.eligible')}</dt><dd>{match.eligibleScreens}</dd></div>
                <div><dt>{t('builder.wouldWin')}</dt><dd>{match.winningScreens}</dd></div>
                <div><dt>{t('builder.imprPerHour')}</dt><dd>{num(match.expectedImpressionsPerHour)}</dd></div>
                <div><dt>{t('builder.redeemPerHour')}</dt><dd>{num(match.expectedRedemptionsPerHour)}</dd></div>
              </dl>
              <p className="builder__hint">{t('builder.matchHint')}</p>
            </>
          ) : (
            !previewError && <p className="builder__hint">{t('builder.calculating')}</p>
          )}
        </div>

        <div className="mock-screen" aria-label={t('builder.mockScreen')}>
          <div className="ad">
            <span className="ad__brand">{t('builder.preview')}</span>
            <h1 className="ad__headline">{preview.headline || t('builder.yourHeadline')}</h1>
            {preview.offerText && <p className="ad__offer">{preview.offerText}</p>}
            {preview.body && <p className="ad__body">{preview.body}</p>}
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
  /** Optional copy in the second language (Spanish, or English when the original is Spanish). */
  trHeadline: string;
  trOfferText: string;
  trBody: string;
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

const COPY_LANGUAGES = ['en', 'es', 'ar'];
const SPEND_TIERS: SpendTier[] = ['LOW', 'MEDIUM', 'HIGH'];

function initialForm(simTime: string | undefined): FormState {
  const start = simTime ? new Date(simTime) : new Date();
  return {
    name: '',
    headline: '',
    offerText: '',
    body: '',
    language: 'en',
    trHeadline: '',
    trOfferText: '',
    trBody: '',
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
    trHeadline: d.translations?.[0]?.headline ?? '',
    trOfferText: d.translations?.[0]?.offerText ?? '',
    trBody: d.translations?.[0]?.body ?? '',
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
    translations: creative || !f.trHeadline
      ? null
      : [{ language: f.language === 'es' ? 'en' : 'es', headline: f.trHeadline, offerText: f.trOfferText || null, body: f.trBody || null }],
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

function parseNum(value: string): number {
  const n = Number(value);
  return Number.isFinite(n) ? n : 0;
}

