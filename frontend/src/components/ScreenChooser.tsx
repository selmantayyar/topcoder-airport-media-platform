import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { useI18n } from '../i18n/I18nProvider';
import { lan, screens, type ScreenSummary } from '../server/api';

/**
 * Passenger-role landing: pick a physical screen by code, gate, or zone and open its full-screen
 * render. Also sets the LAN base URL that promo QR codes encode, so a phone can reach the claim page.
 */
export function ScreenChooser() {
  const { t, label, lang } = useI18n();
  const [all, setAll] = useState<ScreenSummary[]>([]);
  const [filter, setFilter] = useState('');
  const [lanBase, setLanBase] = useState(lan.base());
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    screens
      .list()
      .then(setAll)
      .catch((e: unknown) => setError(String(e)));
  }, []);

  const matches = useMemo(() => {
    const q = filter.trim().toLowerCase();
    if (!q) return all;
    return all.filter((s) =>
      [s.code, s.name, s.zoneName ?? '', s.nearestGates ?? '']
        .join(' ')
        .toLowerCase()
        .includes(q),
    );
  }, [all, filter]);

  const isLoopback = (url: string) => /\/\/(localhost|127\.|\[::1\])/.test(url);

  return (
    <section className="chooser">
      <header className="chooser__head">
        <h2>{t('chooser.title')}</h2>
        <p className="chooser__sub">{t('chooser.sub')}</p>
      </header>

      <div className="chooser__controls">
        <input
          type="search"
          placeholder={t('chooser.filter')}
          value={filter}
          onChange={(e) => setFilter(e.target.value)}
          aria-label={t('chooser.filterAria')}
        />
        <label className="chooser__lan">
          <span>{t('chooser.lan')}</span>
          <input
            type="text"
            value={lanBase}
            onChange={(e) => {
              setLanBase(e.target.value);
              lan.set(e.target.value);
            }}
            placeholder="http://192.168.1.50"
            aria-label={t('chooser.lanAria')}
          />
          {isLoopback(lanBase) && (
            <span className="chooser__lan-warn">
              {t('chooser.lanWarn', { url: lanBase })}
            </span>
          )}
        </label>
      </div>

      {error && <p className="chooser__error">{t('chooser.loadError', { error })}</p>}

      <div className="chooser__grid">
        {matches.slice(0, 60).map((s) => (
          <Link key={s.id} to={`/screen/${s.id}?lang=${lang}`} className={`screen-chip screen-chip--${s.status.toLowerCase()}`}>
            <span className="screen-chip__code">{s.code}</span>
            <span className="screen-chip__zone">{s.zoneName ?? '—'}</span>
            <span className="screen-chip__meta">
              {s.orientation === 'PORTRAIT' ? '9:16' : '16:9'}
              {s.nearestGates ? ` · ${t('common.gates', { gates: s.nearestGates })}` : ''}
            </span>
            <span className="screen-chip__status">{label('screenStatus', s.status)}</span>
          </Link>
        ))}
        {matches.length === 0 && !error && <p>{t('chooser.noMatch', { q: filter })}</p>}
      </div>
    </section>
  );
}
