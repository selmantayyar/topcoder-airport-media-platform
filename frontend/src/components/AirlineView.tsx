import { useCallback, useEffect, useState } from 'react';
import {
  airlines as airlinesApi,
  errorMessage,
  type AirlineContentRow,
  type AirlineFlightRow,
  type AirlineSummary,
  type SponsoredContentType,
} from '../server/api';
import { isInjectedEvent } from '../server/events';
import { useServerEvents, type ServerEvent } from '../server/stream';
import { hhmm } from '../format';
import { useI18n } from '../i18n/I18nProvider';

const CONTENT_TYPES: SponsoredContentType[] = ['UPGRADE_OFFER', 'LOUNGE_PASS', 'DESTINATION_PARTNER'];

/**
 * Airline Representative (a Peripheral Role: plain but fully functional): today's departures for the chosen
 * airline, the campaigns it runs at its gates (sponsored content and automatic delay vouchers), and a form to
 * schedule sponsored content for a flight's boarding window.
 */
export function AirlineView() {
  const { t, label } = useI18n();
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
      if (event.type === 'goodwill' || isInjectedEvent(event)) load();
    }, [load]),
  );

  return (
    <section className="mm">
      <header className="retail__head">
        <h2>{t('role.airline')}</h2>
        <label className="retail__picker">
          <span>{t('airline.airline')}</span>
          <select
            value={airlineId ?? ''}
            onChange={(e) => {
              setNotice(null);
              setAirlineId(Number(e.target.value));
            }}
            aria-label={t('airline.select')}
          >
            {companies.map((a) => (
              <option key={a.id} value={a.id}>{a.name} ({a.iataCode}{a.lowCost ? ` · ${t('airline.lowCost')}` : ''})</option>
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
            setNotice(t('airline.scheduled', { name: row.name, gate: row.gate ?? '?', start: hhmm(row.scheduleStart), end: hhmm(row.scheduleEnd) }));
            load();
          }}
          onError={setNotice}
        />
      )}

      <div className="retail__panel">
        <h3>{t('airline.myFlights')}</h3>
        <table className="retail__table">
          <thead>
            <tr>
              <th>{t('common.flight')}</th><th>{t('airline.to')}</th><th>{t('common.gate')}</th><th>{t('airline.scheduledCol')}</th>
              <th>{t('airline.departs')}</th><th>{t('common.status')}</th><th className="num">{t('airline.delay')}</th><th className="num">{t('airline.load')}</th>
            </tr>
          </thead>
          <tbody>
            {flights.map((f) => (
              <tr key={f.flightId} className={f.status === 'DEPARTED' ? 'airline__departed' : ''}>
                <td className="airline__fn">{f.flightNumber}</td>
                <td>{f.destination}</td>
                <td>{f.gate ?? '—'}</td>
                <td>{hhmm(f.scheduledTime)}</td>
                <td>{hhmm(f.departureTime)}</td>
                <td><span className={`airline__status airline__status--${f.status.toLowerCase().replace(' ', '-')}`}>{label('flightStatus', f.status)}</span></td>
                <td className="num">{f.delayMinutes > 0 ? `+${f.delayMinutes}m` : '—'}</td>
                <td className="num">
                  <span className="airline__load"><span style={{ width: `${Math.min(100, f.loadFactorPct)}%` }} /></span>
                  {f.loadFactorPct.toFixed(0)}% <span className="airline__seats">({f.paxCount}/{f.seats})</span>
                </td>
              </tr>
            ))}
            {flights.length === 0 && <tr><td colSpan={8} className="retail__empty">{t('airline.noFlights')}</td></tr>}
          </tbody>
        </table>
      </div>

      <div className="retail__panel">
        <h3>{t('airline.myContent')}</h3>
        <table className="retail__table">
          <thead>
            <tr>
              <th>{t('common.campaign')}</th><th>{t('airline.type')}</th><th>{t('common.flight')}</th><th>{t('common.gate')}</th>
              <th>{t('airline.window')}</th><th>{t('common.status')}</th><th>{t('airline.coFunded')}</th><th>{t('common.creative')}</th>
            </tr>
          </thead>
          <tbody>
            {content.map((c) => (
              <tr key={c.campaignId}>
                <td>{c.name}</td>
                <td>{c.purpose === 'GOODWILL' ? <span className="tag tag--pending_approval">{t('airline.voucher')}</span> : <span className="tag">{t('airline.sponsored')}</span>}</td>
                <td>{c.flightNumber ?? '—'}</td>
                <td>{c.gate ?? '—'}</td>
                <td className="history__dates">{hhmm(c.scheduleStart)}–{hhmm(c.scheduleEnd)}</td>
                <td><span className={`tag tag--${c.status.toLowerCase()}`}>{label('campaignStatus', c.status)}</span></td>
                <td>{c.coFunderName ?? '—'}</td>
                <td>{c.headline}{c.offerText ? ` · ${c.offerText}` : ''}</td>
              </tr>
            ))}
            {content.length === 0 && <tr><td colSpan={8} className="retail__empty">{t('airline.nothing')}</td></tr>}
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
  const { t } = useI18n();
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
      .catch((e: unknown) => onError(errorMessage(e, t('airline.scheduleFailed'))));
  };

  return (
    <div className="retail__panel">
      <h3>{t('airline.scheduleTitle')}</h3>
      <p className="builder__hint">{t('airline.scheduleHint')}</p>
      <div className="airline__form">
        <label>{t('common.flight')}
          <select value={chosen ?? ''} onChange={(e) => setFlightId(Number(e.target.value))}>
            {flights.map((f) => (
              <option key={f.flightId} value={f.flightId}>
                {t('airline.flightOption', { flight: f.flightNumber, dest: f.destination, gate: f.gate ?? '?', time: hhmm(f.departureTime) })}
              </option>
            ))}
          </select>
        </label>
        <label>{t('airline.contentField')}
          <select value={type} onChange={(e) => setType(e.target.value as SponsoredContentType)}>
            {CONTENT_TYPES.map((c) => <option key={c} value={c}>{t(`airline.content.${c}`)}</option>)}
          </select>
        </label>
        <label>{t('airline.headlineOpt')}<input value={headline} maxLength={60} onChange={(e) => setHeadline(e.target.value)} placeholder={t('airline.headlinePh')} /></label>
        <label>{t('airline.offerOpt')}<input value={offerText} maxLength={80} onChange={(e) => setOfferText(e.target.value)} placeholder={t('airline.offerPh')} /></label>
        <button type="button" className="builder__save" onClick={submit} disabled={chosen == null}>{t('airline.schedule')}</button>
      </div>
    </div>
  );
}
