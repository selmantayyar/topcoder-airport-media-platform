import { admin, errorMessage, type IncidentStatus, type IncidentView } from '../server/api';

const TYPE_LABEL: Record<IncidentView['type'], string> = {
  SCREEN_OFFLINE: 'Screen offline',
  FEED_LAG: 'Feed lag',
  CREATIVE_FAILED: 'Creative failed',
};

/**
 * Auto-raised incidents with severity, owner and status. Operators acknowledge, resolve or reassign;
 * the monitor resolves an incident by itself once its condition clears.
 */
export function IncidentTracker({
  incidents,
  owners,
  simTime,
  onChanged,
  onError,
}: {
  incidents: IncidentView[];
  owners: string[];
  simTime: string | undefined;
  onChanged: () => void;
  onError: (message: string) => void;
}) {
  const change = (i: IncidentView, update: { status?: IncidentStatus; owner?: string }) =>
    admin.updateIncident(i.id, update).then(onChanged).catch((e: unknown) => onError(errorMessage(e)));

  const active = incidents.filter((i) => i.status !== 'RESOLVED');
  const resolved = incidents.filter((i) => i.status === 'RESOLVED');

  return (
    <div className="incidents">
      <h3>Incidents <span className="incidents__count">{active.length} active</span></h3>
      {active.length === 0 && <p className="incidents__empty">All clear.</p>}
      <ul>
        {active.map((i) => (
          <li key={i.id} className={`incident incident--${i.severity.toLowerCase()}`}>
            <div className="incident__head">
              <span className={`sev sev--${i.severity.toLowerCase()}`}>{i.severity}</span>
              <span className="incident__type">{TYPE_LABEL[i.type]}</span>
              <span className="incident__age">{age(i.raisedAt, simTime)}</span>
            </div>
            <strong className="incident__title">{i.title}</strong>
            {i.detail && <span className="incident__detail">{i.detail}</span>}
            <div className="incident__controls">
              <select
                value={i.owner}
                onChange={(e) => change(i, { owner: e.target.value })}
                aria-label={`Owner of incident ${i.id}`}
              >
                {[...new Set([i.owner, ...owners])].map((t) => <option key={t} value={t}>{t}</option>)}
              </select>
              <span className={`istatus istatus--${i.status.toLowerCase()}`}>{i.status === 'OPEN' ? 'Open' : 'Acknowledged'}</span>
              {i.status === 'OPEN' && <button type="button" onClick={() => change(i, { status: 'ACKNOWLEDGED' })}>Acknowledge</button>}
              <button type="button" onClick={() => change(i, { status: 'RESOLVED' })}>Resolve</button>
            </div>
          </li>
        ))}
      </ul>
      {resolved.length > 0 && (
        <>
          <h4>Recently resolved</h4>
          <ul className="incidents__resolved">
            {resolved.map((i) => (
              <li key={i.id}><span className="incident__type">{TYPE_LABEL[i.type]}</span> {i.title}</li>
            ))}
          </ul>
        </>
      )}
    </div>
  );
}

function age(raisedAt: string, simTime: string | undefined): string {
  const minutes = Math.max(0, Math.round(((simTime ? Date.parse(simTime) : Date.now()) - Date.parse(raisedAt)) / 60_000));
  return minutes < 60 ? `${minutes}m` : `${Math.floor(minutes / 60)}h ${minutes % 60}m`;
}
