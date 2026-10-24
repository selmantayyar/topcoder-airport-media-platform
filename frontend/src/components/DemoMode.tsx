import { useEffect, useState } from 'react';
import { screens, type ScreenSummary } from '../server/api';
import { labelForRole, type RoleId } from '../roles';
import { PassengerScreenView } from './PassengerScreen';
import { RoleDashboard } from './RoleDashboard';

/** The Gate 42 worked example's screen — the demo's natural opening shot. */
export const DEMO_DEFAULT_SCREEN_CODE = 'SCR-GATE42';

interface Props {
  /** The dashboard on the right; the passenger screen always sits on the left. */
  role: RoleId;
  screenId: number | null;
  onScreenChange: (screenId: number) => void;
}

/**
 * Split-screen demo: a live Passenger Screen beside any role dashboard. Both panes sit under the app's one
 * SSE connection and the shared control bar, so they move off the same server clock and the same events.
 */
export function DemoMode({ role, screenId, onScreenChange }: Props) {
  const [screenList, setScreenList] = useState<ScreenSummary[]>([]);

  useEffect(() => {
    screens.list().then(setScreenList).catch(() => undefined);
  }, []);

  useEffect(() => {
    if (screenId == null && screenList.length > 0) {
      onScreenChange((screenList.find((s) => s.code === DEMO_DEFAULT_SCREEN_CODE) ?? screenList[0]).id);
    }
  }, [screenList, screenId, onScreenChange]);

  return (
    <div className="demo">
      <section className="demo__pane demo__pane--screen" aria-label="Passenger screen">
        <header className="demo__head">
          <h2>Passenger Screen</h2>
          <select
            value={screenId ?? ''}
            onChange={(e) => onScreenChange(Number(e.target.value))}
            aria-label="Screen shown in the demo"
          >
            {screenList.map((s) => (
              <option key={s.id} value={s.id}>
                {s.code} · {s.zoneName ?? '—'}{s.nearestGates ? ` · gates ${s.nearestGates}` : ''}
              </option>
            ))}
          </select>
        </header>
        <div className="demo__screen">
          {screenId != null && <PassengerScreenView key={screenId} screenId={screenId} embedded />}
        </div>
        <p className="demo__hint">Control-bar injections target this screen.</p>
      </section>

      <section className="demo__pane demo__pane--role" aria-label={labelForRole(role)}>
        <RoleDashboard role={role} />
      </section>
    </div>
  );
}
