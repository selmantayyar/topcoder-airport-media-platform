import { useCallback, useEffect, useState } from 'react';
import {
  campaigns,
  errorMessage,
  lan,
  retailers,
  type BuilderOptions,
  type CampaignHistoryRow,
  type CreativeSummary,
  type PromoIssued,
  type SlotRecommendations,
} from '../server/api';
import { CreativeAssistant } from './CreativeAssistant';
import { day, hhmm, money } from '../format';

interface Props {
  advertiserId: number;
  onUseCreative: (creative: CreativeSummary) => void;
  /** Opens the builder pre-filled with a copy of this campaign. */
  onDuplicate: (campaignId: number) => void;
  /** Bumped by the parent after a save so the list refreshes. */
  refreshKey: number;
}

/**
 * Campaign history (spend, impressions, redemptions, ROI) with one-click "run again" and "duplicate" (opens
 * a pre-filled copy in the builder),
 * time-boxed promo-code issuing (optionally tied to a flight's boarding close), and the creative library.
 */
export function CampaignHistory({ advertiserId, onUseCreative, onDuplicate, refreshKey }: Props) {
  const [rows, setRows] = useState<CampaignHistoryRow[]>([]);
  const [library, setLibrary] = useState<CreativeSummary[]>([]);
  const [options, setOptions] = useState<BuilderOptions | null>(null);
  const [promoFor, setPromoFor] = useState<number | null>(null);
  const [slotsFor, setSlotsFor] = useState<number | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(() => {
    retailers.campaigns(advertiserId).then(setRows).catch(() => undefined);
    retailers.creatives(advertiserId).then(setLibrary).catch(() => undefined);
  }, [advertiserId]);

  useEffect(() => {
    load();
  }, [load, refreshKey]);

  useEffect(() => {
    campaigns.options().then(setOptions).catch(() => undefined);
  }, []);

  const runAgain = (row: CampaignHistoryRow) =>
    campaigns
      .runAgain(row.campaignId)
      .then((c) => {
        setNotice(`“${c.name}” was re-submitted for approval, scheduled from now.`);
        load();
      })
      .catch((e: unknown) => setNotice(errorMessage(e, 'Could not re-run this campaign')));

  return (
    <div className="history">
      {notice && <p className="history__notice">{notice}</p>}

      <div className="retail__panel">
        <h3>Campaign history</h3>
        <table className="retail__table">
          <thead>
            <tr>
              <th>Campaign</th><th>Status</th><th>Schedule</th><th>Spend</th><th>Impr.</th>
              <th>Redeem</th><th>Revenue</th><th>ROI</th><th />
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.campaignId}>
                <td>{r.name}</td>
                <td><span className={`tag tag--${r.status.toLowerCase()}`}>{r.status}</span></td>
                <td className="history__dates">{day(r.scheduleStart)} → {day(r.scheduleEnd)}</td>
                <td className="num">{money(r.spend)}</td>
                <td className="num">{r.impressions.toLocaleString()}</td>
                <td className="num">{r.redemptions.toLocaleString()}</td>
                <td className="num">{money(r.revenue)}</td>
                <td className={`num ${r.roi >= 0 ? 'roi--up' : 'roi--down'}`}>{(r.roi * 100).toFixed(0)}%</td>
                <td className="history__actions">
                  <button type="button" onClick={() => runAgain(r)}>Run again</button>
                  <button type="button" onClick={() => onDuplicate(r.campaignId)}>Duplicate</button>
                  <button type="button" onClick={() => setPromoFor(promoFor === r.campaignId ? null : r.campaignId)}>
                    Promo code
                  </button>
                  <button type="button" onClick={() => setSlotsFor(slotsFor === r.campaignId ? null : r.campaignId)}>
                    Best slots tomorrow
                  </button>
                </td>
              </tr>
            ))}
            {rows.length === 0 && <tr><td colSpan={9} className="retail__empty">No campaigns yet.</td></tr>}
          </tbody>
        </table>
        {promoFor != null && <PromoIssuer campaignId={promoFor} options={options} />}
        {slotsFor != null && <SlotAdvisor key={slotsFor} campaignId={slotsFor} />}
      </div>

      <div className="retail__panel">
        <h3>Creative library</h3>
        <CreativeAssistant advertiserId={advertiserId} onGenerated={load} onUseCreative={onUseCreative} />
        <div className="library">
          {library.map((c) => (
            <div key={c.id} className="library__card">
              <strong>{c.headline}</strong>
              {c.offerText && c.offerText !== c.headline && <span className="library__offer">{c.offerText}</span>}
              <span className="library__meta">{c.language.toUpperCase()}{c.campaignName ? ` · from ${c.campaignName}` : ''}</span>
              <button type="button" onClick={() => onUseCreative(c)}>Use in new campaign</button>
            </div>
          ))}
          {library.length === 0 && <p className="retail__empty">No creatives yet — build a campaign to add one.</p>}
        </div>
      </div>
    </div>
  );
}

/** "Best 3 slots for this campaign tomorrow", predicted by replaying tomorrow through the decision pipeline. */
function SlotAdvisor({ campaignId }: { campaignId: number }) {
  const [result, setResult] = useState<SlotRecommendations | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    campaigns
      .recommendedSlots(campaignId)
      .then(setResult)
      .catch((e: unknown) => setError(errorMessage(e, 'Could not compute recommendations')));
  }, [campaignId]);

  if (error) return <p className="field-error">{error}</p>;
  if (!result) return <p className="builder__hint slots">Simulating tomorrow…</p>;
  return (
    <div className="slots">
      <h4>Best slots for “{result.campaignName}” on {day(result.day)}</h4>
      {result.slots.length === 0 ? (
        <p className="retail__empty">This campaign’s targeting fits no screen tomorrow.</p>
      ) : (
        <ol className="slots__list">
          {result.slots.map((s) => (
            <li key={`${s.zoneId}-${s.start}`} className="slots__item">
              <span className="slots__rank">#{s.rank}</span>
              <span className="slots__when">{hhmm(s.start)}–{hhmm(s.end)} UTC · <strong>{s.zoneName}</strong></span>
              <span className="slots__impr">{s.predictedImpressions.toLocaleString()} predicted impressions</span>
              <span className="slots__meta">
                {s.winningScreens} screens · crowd up to {s.peakHeadcount.toLocaleString()} · avg score {s.averageScore.toFixed(1)}
              </span>
            </li>
          ))}
        </ol>
      )}
      <p className="builder__hint">{result.method}</p>
    </div>
  );
}

function PromoIssuer({ campaignId, options }: { campaignId: number; options: BuilderOptions | null }) {
  const [flightId, setFlightId] = useState<number | null>(null);
  const [validMinutes, setValidMinutes] = useState(120);
  const [issued, setIssued] = useState<PromoIssued | null>(null);
  const [error, setError] = useState<string | null>(null);

  const issue = () =>
    campaigns
      .issuePromo(campaignId, flightId, validMinutes)
      .then((p) => {
        setIssued(p);
        setError(null);
      })
      .catch((e: unknown) => setError(errorMessage(e, 'Could not issue a code')));

  return (
    <div className="promo-issuer">
      <label>Expires with flight
        <select value={flightId ?? ''} onChange={(e) => setFlightId(e.target.value ? Number(e.target.value) : null)}>
          <option value="">No flight — time box only</option>
          {(options?.upcomingFlights ?? []).map((f) => (
            <option key={f.id} value={f.id}>{f.flightNumber} → {f.destination} · G{f.gate} · {hhmm(f.departure)}</option>
          ))}
        </select>
      </label>
      <label>Valid for (min)
        <input type="number" min={5} max={1440} value={validMinutes} onChange={(e) => setValidMinutes(Number(e.target.value))} />
      </label>
      <button type="button" onClick={issue}>Issue code</button>
      {error && <span className="field-error">{error}</span>}
      {issued && (
        <div className="promo-issuer__result">
          <span className="promo-issuer__code">{issued.code}</span>
          <span>expires {hhmm(issued.expiresAt)} UTC{issued.linkedFlightNumber ? ` (boarding close of ${issued.linkedFlightNumber})` : ''}</span>
          <a href={`${lan.base()}${issued.claimPath}`} target="_blank" rel="noreferrer">claim link</a>
        </div>
      )}
    </div>
  );
}
