import { num } from '../format';
import { useI18n } from '../i18n/I18nProvider';
import type { MessageKey } from '../i18n/messages';
import type { CrowdProfile, DecisionResult } from '../server/api';

/**
 * The "Why this ad?" overlay: crowd-profile bars, the top-3 candidate scores, and the winning rule. The reason
 * and rule notes are the decision log's own words, shown as logged.
 */
export function WhyThisAd({ decision, onClose }: { decision: DecisionResult; onClose: () => void }) {
  const { t, label } = useI18n();
  const ranked = [...decision.candidates].sort((a, b) => b.finalScore - a.finalScore).slice(0, 3);
  const winner = decision.candidates.find((c) => c.campaignId === decision.winnerCampaignId);
  const winningRules = winner?.ruleNotes ?? [];

  return (
    <div className="why" role="dialog" aria-label={t('screen.why')}>
      <div className="why__panel">
        <header className="why__head">
          <h3>{t('screen.why')}</h3>
          <button type="button" className="why__close" onClick={onClose} aria-label={t('why.close')}>✕</button>
        </header>

        <p className="why__reason">{decision.reason}</p>
        {decision.fallback && <p className="why__fallback">{t('why.fallback')}</p>}
        {winningRules.length > 0 && (
          <div className="why__rules">
            <span className="why__rules-label">{t('why.winningRules')}</span>
            {winningRules.map((note) => (
              <span key={note} className="why__rule-chip">{note}</span>
            ))}
          </div>
        )}

        {decision.profile && (
          <div className="why__profile">
            <h4>{t('why.crowd', { n: num(decision.headcount), region: label('region', decision.profile.region) })}</h4>
            <ProfileBars title="why.tripPurpose" dist={decision.profile.tripPurpose} />
            <ProfileBars title="why.party" dist={decision.profile.partyType} />
            <ProfileBars title="why.spend" dist={decision.profile.spendTier} />
          </div>
        )}

        <div className="why__candidates">
          <h4>{t('why.top')}</h4>
          <table>
            <thead>
              <tr>
                <th>{t('common.campaign')}</th>
                <th>{t('why.colCrowd')}</th>
                <th>{t('why.colBoarding')}</th>
                <th>{t('why.colDwell')}</th>
                <th>{t('why.colSpend')}</th>
                <th>{t('why.colScore')}</th>
              </tr>
            </thead>
            <tbody>
              {ranked.map((c) => (
                <tr key={c.campaignId} className={c.campaignId === decision.winnerCampaignId ? 'is-winner' : ''}>
                  <td title={c.campaignName}>{c.campaignName}</td>
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

function ProfileBars({ title, dist }: { title: MessageKey; dist: CrowdProfile['tripPurpose'] }) {
  const { t, label } = useI18n();
  const entries = Object.entries(dist).sort((a, b) => b[1] - a[1]);
  return (
    <div className="why__bars">
      <span className="why__bars-label">{t(title)}</span>
      <div className="why__bars-rows">
        {entries.map(([key, pct]) => (
          <div key={key} className="why__bar-row">
            <span className="why__bar-key">{label('profile', key)}</span>
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
