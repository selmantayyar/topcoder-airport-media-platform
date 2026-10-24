import { useCallback, useState } from 'react';
import { sim } from '../server/api';
import { useClock, useServerEvents, type ServerEvent } from '../server/stream';

const SPEEDS = [1, 10, 60];

// The simulated day; the jump input supplies the time-of-day (UTC).
const SIM_DAY = '2026-10-23';
const WORKED_EXAMPLE_TIME = `${SIM_DAY}T14:10:00Z`;

interface Props {
  /** In split-screen demo, injections land on this screen (or a flight at its gates) so both panes react. */
  focusScreenId?: number;
}

export function SimControlBar({ focusScreenId }: Props) {
  const { clock } = useClock();
  const [lastEvent, setLastEvent] = useState<string | null>(null);
  const [jumpTime, setJumpTime] = useState('14:10');
  const [delayMinutes, setDelayMinutes] = useState(45);

  useServerEvents(
    useCallback((event: ServerEvent) => {
      setLastEvent(describe(event));
      const timer = setTimeout(() => setLastEvent(null), 6000);
      return () => clearTimeout(timer);
    }, []),
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
        {running ? '⏸ Pause' : '▶ Play'}
      </button>

      <div className="sim-bar__speeds" role="group" aria-label="Speed">
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

      <div className="sim-bar__jump" role="group" aria-label="Jump to time">
        <input
          type="time"
          aria-label="Jump to time (UTC)"
          value={jumpTime}
          onChange={(e) => setJumpTime(e.target.value)}
        />
        <button type="button" onClick={() => sim.jump(`${SIM_DAY}T${jumpTime}:00Z`)}>
          Jump
        </button>
        <button type="button" onClick={() => sim.jump(WORKED_EXAMPLE_TIME)}>
          → 14:10 (Gate 42)
        </button>
      </div>

      <div className="sim-bar__inject" role="group" aria-label="Inject event">
        <span className="sim-bar__label">Inject:</span>
        <button type="button" onClick={() => sim.injectDelay(delayMinutes, focusScreenId)}>Delay flight</button>
        <select
          value={delayMinutes}
          onChange={(e) => setDelayMinutes(Number(e.target.value))}
          aria-label="Delay length"
        >
          <option value={45}>45 min</option>
          <option value={90}>90 min</option>
          <option value={150}>150 min</option>
        </select>
        <button type="button" onClick={() => sim.injectGateChange(focusScreenId)}>Gate change</button>
        <button type="button" onClick={() => sim.injectScreenOffline(focusScreenId)}>Screen offline</button>
        <button type="button" onClick={() => sim.injectScreenOnline(focusScreenId)}>Screen online</button>
      </div>

      {lastEvent && <div className="sim-bar__toast">{lastEvent}</div>}
    </div>
  );
}

function describe(event: ServerEvent): string {
  const data = event.data as Record<string, unknown>;
  switch (event.type) {
    case 'flight-delayed':
      return `✈ ${data.flightNumber} delayed ${data.delayMinutes} min`;
    case 'gate-changed':
      return `🚪 ${data.flightNumber} moved to gate ${data.newGate}`;
    case 'screen-offline':
      return `🖥 Screen ${data.code} went offline`;
    case 'screen-online':
      return `🖥 Screen ${data.code} is back online`;
    case 'redemption':
      return `🎟 Offer claimed (${data.code})`;
    case 'scan':
      return `📱 QR scanned (${data.code})`;
    case 'incident':
      return '⚠ Incidents updated';
    case 'goodwill':
      return `🎁 ${data.flightNumber}: ${String(data.voucher).toLowerCase()} voucher with ${data.partner} at gate ${data.gate}`;
    default:
      return event.type;
  }
}
