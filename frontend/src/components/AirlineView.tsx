import { useCallback, useEffect, useState } from 'react';
import {
  airlines as airlinesApi,
  errorMessage,
  type AirlineContentRow,
  type AirlineFlightRow,
  type AirlineSummary,
  type SponsoredContentType,
} from '../server/api';
import { useServerEvents, type ServerEvent } from '../server/stream';
import { hhmm } from '../format';

const CONTENT_TYPES: { value: SponsoredContentType; label: string }[] = [
  { value: 'UPGRADE_OFFER', label: 'Upgrade offer' },
  { value: 'LOUNGE_PASS', label: 'Lounge pass' },
  { value: 'DESTINATION_PARTNER', label: 'Destination partner' },
];

/**
 * Airline Representative (a Peripheral Role: plain but fully functional): today's departures for the chosen
 * airline, the campaigns it runs at its gates (sponsored content and automatic delay vouchers), and a form to
 * schedule sponsored content for a flight's boarding window.
 */
export function AirlineView() {
  const [companies, setCompanies] = useState<AirlineSummary[]>([]);
  const [airlineId, setAirlineId] = useState<number | null>(null);
  const [flights, setFlights] = useState<AirlineFlightRow[]>([]);
  const [content, setContent] = useState<AirlineContentRow[]>([]);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    airlinesApi.list().then((list) => {
      setCompanies(list);
      setAirlineId((cur) => cur ?? list[0]?.id ?? null);
    }).catch(() => undefined);
  }, []);

  const load = useCallback(() => {
    if (airlineId == null) return;
    airlinesApi.flights(airlineId).then(setFlights).catch(() => undefined);
    airlinesApi.content(airlineId).then(setContent).catch(() => undefined);
  }, [airlineId]);

  useEffect(() => {
    load();
    const timer = setInterval(load, 5000);
    return () => clearInterval(timer);
  }, [load]);

  // Delays and the vouchers they trigger change this view without anyone touching it.
  useServerEvents(
    useCallback((event: ServerEvent) => {
      if (event.type === 'goodwill' || event.type === 'flight-delayed' || event.type === 'gate-changed') load();
    }, [load]),
  );

  return (
    <section className="mm">
      <header className="retail__head">
        <h2>Airline Representative</h2>
        <label className="retail__picker">
          <span>Airline</span>
          <select
            value={airlineId ?? ''}
            onChange={(e) => {
              setNotice(null);
              setAirlineId(Number(e.target.value));
            }}
            aria-label="Select airline"
          >
            {companies.map((a) => (
              <option key={a.id} value={a.id}>{a.name} ({a.iataCode}{a.lowCost ? ' · low-cost' : ''})</option>
            ))}
          </select>
        </label>
      </header>
      {notice && <p className="history__notice">{notice}</p>}

      {airlineId != null && (
        <ScheduleForm
          key={airlineId}
          airlineId={airlineId}
          flights={flights.filter((f) => f.minutesToDeparture > 0)}
          onScheduled={(row) => {
            setNotice(`Scheduled “${row.name}” on gate ${row.gate}, ${hhmm(row.scheduleStart)}–${hhmm(row.scheduleEnd)} UTC — live now, with airline priority while the flight boards.`);
            load();
          }}
          onError={setNotice}
        />
      )}

      <div className="retail__panel">
        <h3>My flights today</h3>
        <table className="retail__table">
          <thead>
            <tr><th>Flight</th><th>To</th><th>Gate</th><th>Scheduled</th><th>Departs</th><th>Status</th><th>Delay</th><th>Load factor</th></tr>
          </thead>
          <tbody>
            {flights.map((f) => (
              <tr key={f.flightId} className={f.status === 'DEPARTED' ? 'airline__departed' : ''}>
                <td className="airline__fn">{f.flightNumber}</td>
                <td>{f.destination}</td>
                <td>{f.gate ?? '—'}</td>
                <td>{hhmm(f.scheduledTime)}</td>
                <td>{hhmm(f.departureTime)}</td>
                <td><span className={`airline__status airline__status--${f.status.toLowerCase().replace(' ', '-')}`}>{f.status}</span></td>
                <td className="num">{f.delayMinutes > 0 ? `+${f.delayMinutes}m` : '—'}</td>
                <td className="num">
                  <span className="airline__load"><span style={{ width: `${Math.min(100, f.loadFactorPct)}%` }} /></span>
                  {f.loadFactorPct.toFixed(0)}% <span className="airline__seats">({f.paxCount}/{f.seats})</span>
                </td>
              </tr>
            ))}
            {flights.length === 0 && <tr><td colSpan={8} className="retail__empty">No departures today.</td></tr>}
          </tbody>
        </table>
      </div>

      <div className="retail__panel">
        <h3>My gate content</h3>
        <table className="retail__table">
          <thead>
            <tr><th>Campaign</th><th>Type</th><th>Flight</th><th>Gate</th><th>Window (UTC)</th><th>Status</th><th>Co-funded by</th><th>Creative</th></tr>
          </thead>
          <tbody>
            {content.map((c) => (
              <tr key={c.campaignId}>
                <td>{c.name}</td>
                <td>{c.purpose === 'GOODWILL' ? <span className="tag tag--pending_approval">Delay voucher (auto)</span> : <span className="tag">Sponsored</span>}</td>
                <td>{c.flightNumber ?? '—'}</td>
                <td>{c.gate ?? '—'}</td>
                <td className="history__dates">{hhmm(c.scheduleStart)}–{hhmm(c.scheduleEnd)}</td>
                <td><span className={`tag tag--${c.status.toLowerCase()}`}>{c.status}</span></td>
                <td>{c.coFunderName ?? '—'}</td>
                <td>{c.headline}{c.offerText ? ` · ${c.offerText}` : ''}</td>
              </tr>
            ))}
            {content.length === 0 && <tr><td colSpan={8} className="retail__empty">Nothing scheduled yet.</td></tr>}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function ScheduleForm({
  airlineId,
  flights,
  onScheduled,
  onError,
}: {
  airlineId: number;
  flights: AirlineFlightRow[];
  onScheduled: (row: AirlineContentRow) => void;
  onError: (message: string) => void;
}) {
  const [flightId, setFlightId] = useState<number | null>(null);
  const [type, setType] = useState<SponsoredContentType>('UPGRADE_OFFER');
  const [headline, setHeadline] = useState('');
  const [offerText, setOfferText] = useState('');
  const chosen = flightId ?? flights[0]?.flightId ?? null;

  const submit = () => {
    if (chosen == null) return;
    airlinesApi
      .schedule(airlineId, { flightId: chosen, type, headline: headline || null, offerText: offerText || null })
      .then((row) => {
        setHeadline('');
        setOfferText('');
        onScheduled(row);
      })
      .catch((e: unknown) => onError(errorMessage(e, 'Could not schedule content')));
  };

  return (
    <div className="retail__panel">
      <h3>Schedule sponsored content</h3>
      <p className="builder__hint">Plays on the flight's gate screens during its boarding window (40 minutes before departure), boosted by airline priority.</p>
      <div className="airline__form">
        <label>Flight
          <select value={chosen ?? ''} onChange={(e) => setFlightId(Number(e.target.value))}>
            {flights.map((f) => (
              <option key={f.flightId} value={f.flightId}>
                {f.flightNumber} → {f.destination} · gate {f.gate ?? '?'} · {hhmm(f.departureTime)}
              </option>
            ))}
          </select>
        </label>
        <label>Content
          <select value={type} onChange={(e) => setType(e.target.value as SponsoredContentType)}>
            {CONTENT_TYPES.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
          </select>
        </label>
        <label>Headline (optional)<input value={headline} maxLength={60} onChange={(e) => setHeadline(e.target.value)} placeholder="Generated from the flight" /></label>
        <label>Offer (optional)<input value={offerText} maxLength={80} onChange={(e) => setOfferText(e.target.value)} placeholder="Generated from the type" /></label>
        <button type="button" className="builder__save" onClick={submit} disabled={chosen == null}>Schedule</button>
      </div>
    </div>
  );
}
