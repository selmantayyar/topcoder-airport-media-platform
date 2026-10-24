import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { QRCodeSVG } from 'qrcode.react';
import { lan, screens, type PlaybackView } from '../server/api';
import { isInjectedEvent } from '../server/events';
import { useClock, useServerEvents, type ServerEvent } from '../server/stream';
import { WhyThisAd } from './WhyThisAd';

/** The full-screen kiosk route: /screen/:id. */
export function PassengerScreen() {
  const { id } = useParams();
  return <PassengerScreenView screenId={Number(id)} />;
}

interface ViewProps {
  screenId: number;
  /** Rendered inside the split-screen demo instead of filling the window (no kiosk back link). */
  embedded?: boolean;
}

/**
 * Render of one physical screen (9:16 or 16:9). It plays whatever the decision pipeline currently chooses,
 * refreshing as the simulation clock advances and immediately on any injected event, with a FIDS context
 * strip, a promo QR + live countdown, and a "Why this ad?" overlay.
 */
export function PassengerScreenView({ screenId, embedded = false }: ViewProps) {
  const { clock } = useClock();
  const [view, setView] = useState<PlaybackView | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [showWhy, setShowWhy] = useState(false);
  const timer = useRef<number | null>(null);

  const refresh = useCallback(() => {
    screens
      .playback(screenId)
      .then((v) => {
        setView(v);
        setError(null);
      })
      .catch((e: unknown) => setError(String(e)));
  }, [screenId]);

  useEffect(() => {
    refresh();
    timer.current = window.setInterval(refresh, 2000);
    return () => {
      if (timer.current) window.clearInterval(timer.current);
    };
  }, [refresh]);

  useServerEvents(
    useCallback((event: ServerEvent) => {
      if (isInjectedEvent(event)) refresh();
    }, [refresh]),
  );

  const embeddedClass = embedded ? ' kiosk--embedded' : '';

  if (error) {
    return (
      <div className={`kiosk kiosk--error${embeddedClass}`}>
        <p>Could not load screen {screenId}: {error}</p>
        {!embedded && <Link to="/">← Back to chooser</Link>}
      </div>
    );
  }
  if (!view) {
    return <div className={`kiosk kiosk--loading${embeddedClass}`}>Loading screen {screenId}…</div>;
  }

  const portrait = view.screen.orientation === 'PORTRAIT';
  const { creative, promo, decision } = view;
  const secondsRemaining = promo ? countdown(promo.validUntil, clock?.simTime) : 0;
  const claimUrl = promo ? `${lan.base()}${promo.claimPath}` : '';

  return (
    <div className={`kiosk ${portrait ? 'kiosk--portrait' : 'kiosk--landscape'}${embeddedClass}`}>
      <div className="kiosk__chrome">
        {!embedded && <Link to="/" className="kiosk__back">← Screens</Link>}
        <span className="kiosk__id">{view.screen.code} · {view.screen.zoneName}</span>
        {view.screen.status !== 'ONLINE' && <span className="kiosk__status">{view.screen.status}</span>}
        <button type="button" className="kiosk__why" onClick={() => setShowWhy(true)}>Why this ad?</button>
      </div>

      <Fids flights={view.nearbyFlights} />

      <div className="kiosk__stage">
        {view.screen.status === 'OFFLINE' ? (
          <div className="ad ad--offline">
            <h1>No signal</h1>
            <p>This screen is offline — the platform has raised an incident.</p>
          </div>
        ) : creative ? (
          <div className={`ad ad--${creative.templateKey}`}>
            <span className="ad__brand">{creative.advertiserName}</span>
            <h1 className="ad__headline">{creative.headline}</h1>
            {creative.offerText && <p className="ad__offer">{creative.offerText}</p>}
            {creative.body && <p className="ad__body">{creative.body}</p>}
          </div>
        ) : (
          <div className="ad ad--empty">
            <h1>Welcome to Amberport International</h1>
            <p>{decision.reason}</p>
          </div>
        )}

        {promo && view.screen.status !== 'OFFLINE' && (
          <aside className="promo">
            <QRCodeSVG value={claimUrl} size={portrait ? 220 : 180} includeMargin />
            <div className="promo__meta">
              <span className="promo__code">{promo.code}</span>
              <span className="promo__count">{formatCountdown(secondsRemaining)} left</span>
              <span className="promo__hint">Scan to claim</span>
            </div>
          </aside>
        )}
      </div>

      {showWhy && <WhyThisAd decision={decision} onClose={() => setShowWhy(false)} />}
    </div>
  );
}

function Fids({ flights }: { flights: PlaybackView['nearbyFlights'] }) {
  if (flights.length === 0) {
    return <div className="fids fids--empty">No departures near this screen right now.</div>;
  }
  return (
    <div className="fids" aria-label="Nearby flights">
      <span className="fids__title">Departures</span>
      <div className="fids__rows">
        {flights.map((f) => (
          <div key={f.flightNumber} className={`fids__row fids__row--${f.status.toLowerCase().replace(' ', '-')}`}>
            <span className="fids__flight">{f.flightNumber}</span>
            <span className="fids__dest">{f.destination}</span>
            <span className="fids__gate">G{f.gate}</span>
            <span className="fids__time">{time(f.estimatedTime ?? f.scheduledTime)}</span>
            <span className="fids__status">{f.status}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

function countdown(validUntil: string, simTime: string | undefined): number {
  const end = new Date(validUntil).getTime();
  const now = simTime ? new Date(simTime).getTime() : Date.now();
  return Math.max(0, Math.floor((end - now) / 1000));
}

function formatCountdown(seconds: number): string {
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = seconds % 60;
  return h > 0 ? `${h}h ${m}m` : `${m}m ${String(s).padStart(2, '0')}s`;
}

function time(iso: string): string {
  return new Date(iso).toLocaleTimeString('en-GB', { timeZone: 'UTC', hour: '2-digit', minute: '2-digit' });
}
