import { useCallback, useState } from 'react';
import { useI18n } from '../i18n/I18nProvider';
import type { MessageKey } from '../i18n/messages';
import { sim } from '../server/api';
import { useClock, useServerEvents, type ServerEvent } from '../server/stream';

const SPEEDS = [1, 10, 60];
const DELAYS = [45, 90, 150];

// The simulated day; the jump input supplies the time-of-day (UTC).
const SIM_DAY = '2026-10-23';
const WORKED_EXAMPLE_TIME = `${SIM_DAY}T14:10:00Z`;

interface Props {
  /** In split-screen demo, injections land on this screen (or a flight at its gates) so both panes react. */
  focusScreenId?: number;
}

export function SimControlBar({ focusScreenId }: Props) {
  const { clock } = useClock();
  const { t, label } = useI18n();
  const [lastEvent, setLastEvent] = useState<string | null>(null);
  const [jumpTime, setJumpTime] = useState('14:10');
  const [delayMinutes, setDelayMinutes] = useState(45);

  useServerEvents(
    useCallback((event: ServerEvent) => {
      const described = describe(event, label);
      setLastEvent(described ? t(...described) : event.type);
      const timer = setTimeout(() => setLastEvent(null), 6000);
      return () => clearTimeout(timer);
    }, [t, label]),
  );

  const running = clock?.running ?? false;
  const speed = clock?.speed ?? 1;

  return (
    <div className="sim-bar">
      <button
        type="button"
        className="sim-bar__play"
        onClick={() => (running ? sim.pause() : sim.play())}
      >
        {running ? t('sim.pause') : t('sim.play')}
      </button>

      <div className="sim-bar__speeds" role="group" aria-label={t('sim.speed')}>
        {SPEEDS.map((s) => (
          <button
            key={s}
            type="button"
            className={s === speed ? 'sim-bar__speed is-active' : 'sim-bar__speed'}
            onClick={() => sim.speed(s)}
          >
            {s}x
          </button>
        ))}
      </div>

      <div className="sim-bar__jump" role="group" aria-label={t('sim.jumpGroup')}>
        <input
          type="time"
          aria-label={t('sim.jumpInput')}
          value={jumpTime}
          onChange={(e) => setJumpTime(e.target.value)}
        />
        <button type="button" onClick={() => sim.jump(`${SIM_DAY}T${jumpTime}:00Z`)}>
          {t('sim.jump')}
        </button>
        <button type="button" onClick={() => sim.jump(WORKED_EXAMPLE_TIME)}>
          {t('sim.gate42')}
        </button>
      </div>

      <div className="sim-bar__inject" role="group" aria-label={t('sim.injectGroup')}>
        <span className="sim-bar__label">{t('sim.inject')}</span>
        <button type="button" onClick={() => sim.injectDelay(delayMinutes, focusScreenId)}>{t('sim.delay')}</button>
        <select
          value={delayMinutes}
          onChange={(e) => setDelayMinutes(Number(e.target.value))}
          aria-label={t('sim.delayLength')}
        >
          {DELAYS.map((m) => <option key={m} value={m}>{t('common.minutes', { n: m })}</option>)}
        </select>
        <button type="button" onClick={() => sim.injectGateChange(focusScreenId)}>{t('sim.gateChange')}</button>
        <button type="button" onClick={() => sim.injectScreenOffline(focusScreenId)}>{t('sim.screenOffline')}</button>
        <button type="button" onClick={() => sim.injectScreenOnline(focusScreenId)}>{t('sim.screenOnline')}</button>
      </div>

      {lastEvent && <div className="sim-bar__toast">{lastEvent}</div>}
    </div>
  );
}

/** The toast for a live event, as a catalog key and its values. */
function describe(
  event: ServerEvent,
  label: (group: string, value: string) => string,
): [MessageKey, Record<string, string | number>] | null {
  const data = event.data as Record<string, string | number>;
  switch (event.type) {
    case 'flight-delayed':
      return ['event.flightDelayed', { flight: data.flightNumber, minutes: data.delayMinutes }];
    case 'gate-changed':
      return ['event.gateChanged', { flight: data.flightNumber, gate: data.newGate }];
    case 'screen-offline':
      return ['event.screenOffline', { code: data.code }];
    case 'screen-online':
      return ['event.screenOnline', { code: data.code }];
    case 'redemption':
      return ['event.redemption', { code: data.code }];
    case 'scan':
      return ['event.scan', { code: data.code }];
    case 'incident':
      return ['event.incident', {}];
    case 'goodwill':
      return ['event.goodwill', { flight: data.flightNumber, voucher: label('voucher', String(data.voucher)), partner: data.partner, gate: data.gate }];
    default:
      return null;
  }
}
