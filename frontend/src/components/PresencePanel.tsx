import { useEffect, useState } from 'react';
import { presence, type CrowdProfile, type ZonePresence } from '../server/api';
import { useClock } from '../server/stream';

const AGE_LABELS: Record<string, string> = {
  A18_24: '18-24',
  A25_44: '25-44',
  A45_64: '45-64',
  A65_PLUS: '65+',
};

/** The three trip-purpose segments, in a stable order with stable colours. */
const PURPOSE_COLORS: Record<string, string> = {
  BUSINESS: 'var(--accent)',
  LEISURE: 'var(--ok)',
  VFR: 'var(--warn, #c98a00)',
};

function dominant(dist: Record<string, number>): string {
  return Object.entries(dist).reduce((best, cur) => (cur[1] > best[1] ? cur : best))[0];
}

function pretty(key: string): string {
  return AGE_LABELS[key] ?? key.charAt(0) + key.slice(1).toLowerCase();
}

/**
 * Live read view of ZonePresence + Crowd Profile. Re-fetches on every clock tick, so the
 * head counts and profile mixes visibly change as the simulation clock advances.
 */
export function PresencePanel() {
  const { clock } = useClock();
  const [zones, setZones] = useState<ZonePresence[]>([]);
  const [error, setError] = useState<string | null>(null);

  const simTime = clock?.simTime ?? null;

  useEffect(() => {
    let cancelled = false;
    presence
      .zones()
      .then((data) => {
        if (!cancelled) {
          setZones(data);
          setError(null);
        }
      })
      .catch((e: unknown) => !cancelled && setError(String(e)));
    return () => {
      cancelled = true;
    };
  }, [simTime]);

  const populated = zones.filter((z) => z.estimatedHeadcount > 0);
  const total = zones.reduce((sum, z) => sum + z.estimatedHeadcount, 0);

  return (
    <section className="presence">
      <header className="presence__head">
        <h2>Zone presence &amp; crowd profile</h2>
        <p className="presence__sub">
          Estimated passengers near each zone in the current 5-minute window, with the
          head-count-weighted crowd profile. Updates as the clock advances.
        </p>
        <div className="presence__totals">
          <span><strong>{total.toLocaleString()}</strong> pax across the terminal</span>
          <span>{populated.length} active zones</span>
        </div>
        {error && <p className="presence__error">Could not load presence: {error}</p>}
      </header>

      <div className="presence__grid">
        {populated.slice(0, 12).map((zone) => (
          <ZoneCard key={zone.zoneId} zone={zone} />
        ))}
        {populated.length === 0 && !error && (
          <p className="presence__empty">No passengers near any zone at this time — advance the clock toward a boarding wave.</p>
        )}
      </div>
    </section>
  );
}

function ZoneCard({ zone }: { zone: ZonePresence }) {
  const profile = zone.profile;
  return (
    <article className="zone-card">
      <div className="zone-card__top">
        <div>
          <h3>{zone.zoneName}</h3>
          <span className="zone-card__kind">{pretty(zone.zoneKind)}</span>
        </div>
        <div className="zone-card__count">
          <strong>{zone.estimatedHeadcount.toLocaleString()}</strong>
          <span>pax</span>
        </div>
      </div>

      {profile ? <ProfileBody profile={profile} /> : (
        <p className="zone-card__noprofile">Queue-driven zone — no boarding flights.</p>
      )}

      {zone.flights.length > 0 && (
        <ul className="zone-card__flights">
          {zone.flights.slice(0, 3).map((f) => (
            <li key={f.flightId}>
              <span className="zone-card__fnum">{f.flightNumber}</span>
              <span>→ {f.destination}</span>
              <span className="zone-card__gate">gate {f.gate}</span>
              <span className="zone-card__eta">
                {f.minutesToDeparture >= 0 ? `T-${f.minutesToDeparture}m` : `+${-f.minutesToDeparture}m`}
              </span>
              <span className="zone-card__fpax">{f.passengers}</span>
            </li>
          ))}
        </ul>
      )}
    </article>
  );
}

function ProfileBody({ profile }: { profile: CrowdProfile }) {
  return (
    <>
      <div className="profile-bar" role="img" aria-label="Trip purpose mix">
        {Object.entries(profile.tripPurpose).map(([key, pct]) => (
          <span
            key={key}
            className="profile-bar__seg"
            style={{ width: `${pct}%`, background: PURPOSE_COLORS[key] ?? 'var(--border)' }}
            title={`${pretty(key)} ${pct}%`}
          />
        ))}
      </div>
      <dl className="profile-grid">
        <div><dt>Purpose</dt><dd>{pretty(dominant(profile.tripPurpose))}</dd></div>
        <div><dt>Party</dt><dd>{pretty(dominant(profile.partyType))}</dd></div>
        <div><dt>Age</dt><dd>{pretty(dominant(profile.ageBand))}</dd></div>
        <div><dt>Spend</dt><dd>{pretty(dominant(profile.spendTier))}</dd></div>
        <div><dt>Language</dt><dd>{profile.language}</dd></div>
        <div><dt>Dwell</dt><dd>{profile.dwellMinutes}m{profile.delayed ? ` (+${profile.delayMinutes})` : ''}</dd></div>
      </dl>
    </>
  );
}
