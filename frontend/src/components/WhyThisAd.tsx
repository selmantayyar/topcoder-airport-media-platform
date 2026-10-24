import type { CrowdProfile, DecisionResult } from '../server/api';

/** The "Why this ad?" overlay: crowd-profile bars, the top-3 candidate scores, and the winning rule. */
export function WhyThisAd({ decision, onClose }: { decision: DecisionResult; onClose: () => void }) {
  const ranked = [...decision.candidates].sort((a, b) => b.finalScore - a.finalScore).slice(0, 3);
  const winner = decision.candidates.find((c) => c.campaignId === decision.winnerCampaignId);
  const winningRules = winner?.ruleNotes ?? [];

  return (
    <div className="why" role="dialog" aria-label="Why this ad?">
      <div className="why__panel">
        <header className="why__head">
          <h3>Why this ad?</h3>
          <button type="button" className="why__close" onClick={onClose} aria-label="Close">✕</button>
        </header>

        <p className="why__reason">{decision.reason}</p>
        {decision.fallback && <p className="why__fallback">Rule-based fallback (inputs not trusted)</p>}
        {winningRules.length > 0 && (
          <div className="why__rules">
            <span className="why__rules-label">Winning rules</span>
            {winningRules.map((note) => (
              <span key={note} className="why__rule-chip">{note}</span>
            ))}
          </div>
        )}

        {decision.profile && (
          <div className="why__profile">
            <h4>Crowd profile · {decision.headcount.toLocaleString()} pax · {decision.profile.region}</h4>
            <ProfileBars label="Trip purpose" dist={decision.profile.tripPurpose} />
            <ProfileBars label="Party" dist={decision.profile.partyType} />
            <ProfileBars label="Spend" dist={decision.profile.spendTier} />
          </div>
        )}

        <div className="why__candidates">
          <h4>Top candidates</h4>
          <table>
            <thead>
              <tr>
                <th>Campaign</th>
                <th>Crowd</th>
                <th>Boarding</th>
                <th>Dwell</th>
                <th>Spend</th>
                <th>Score</th>
              </tr>
            </thead>
            <tbody>
              {ranked.map((c) => (
                <tr key={c.campaignId} className={c.campaignId === decision.winnerCampaignId ? 'is-winner' : ''}>
                  <td>{c.advertiserName}</td>
                  <td>{fmt(c.crowdFit)}</td>
                  <td>{fmt(c.boardingProximity)}</td>
                  <td>{fmt(c.dwellFit)}</td>
                  <td>{fmt(c.spendMatch)}</td>
                  <td className="why__score">{c.finalScore.toFixed(2)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

function ProfileBars({ label, dist }: { label: string; dist: CrowdProfile['tripPurpose'] }) {
  const entries = Object.entries(dist).sort((a, b) => b[1] - a[1]);
  return (
    <div className="why__bars">
      <span className="why__bars-label">{label}</span>
      <div className="why__bars-rows">
        {entries.map(([key, pct]) => (
          <div key={key} className="why__bar-row">
            <span className="why__bar-key">{pretty(key)}</span>
            <span className="why__bar-track">
              <span className="why__bar-fill" style={{ width: `${pct}%` }} />
            </span>
            <span className="why__bar-pct">{pct}%</span>
          </div>
        ))}
      </div>
    </div>
  );
}

function fmt(v: number): string {
  return v.toFixed(2);
}

function pretty(key: string): string {
  return key.charAt(0) + key.slice(1).toLowerCase();
}
