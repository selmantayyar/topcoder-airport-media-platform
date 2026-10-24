import { useI18n } from '../i18n/I18nProvider';
import { admin, errorMessage, type IncidentStatus, type IncidentView } from '../server/api';

/**
 * Auto-raised incidents with severity, owner and status. Operators acknowledge, resolve or reassign;
 * the monitor resolves an incident by itself once its condition clears. Titles and details are the monitor's
 * own logged words.
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
  const { t, label } = useI18n();
  const change = (i: IncidentView, update: { status?: IncidentStatus; owner?: string }) =>
    admin.updateIncident(i.id, update).then(onChanged).catch((e: unknown) => onError(errorMessage(e, t('common.failed'))));

  const active = incidents.filter((i) => i.status !== 'RESOLVED');
  const resolved = incidents.filter((i) => i.status === 'RESOLVED');

  return (
    <div className="incidents">
      <h3>{t('inc.title')} <span className="incidents__count">{t('inc.active', { n: active.length })}</span></h3>
      {active.length === 0 && <p className="incidents__empty">{t('inc.allClear')}</p>}
      <ul>
        {active.map((i) => (
          <li key={i.id} className={`incident incident--${i.severity.toLowerCase()}`}>
            <div className="incident__head">
              <span className={`sev sev--${i.severity.toLowerCase()}`}>{label('severity', i.severity)}</span>
              <span className="incident__type">{label('incidentType', i.type)}</span>
              <span className="incident__age">{age(i.raisedAt, simTime)}</span>
            </div>
            <strong className="incident__title">{i.title}</strong>
            {i.detail && <span className="incident__detail">{i.detail}</span>}
            <div className="incident__controls">
              <select
                value={i.owner}
                onChange={(e) => change(i, { owner: e.target.value })}
                aria-label={t('inc.owner', { id: i.id })}
              >
                {[...new Set([i.owner, ...owners])].map((t) => <option key={t} value={t}>{t}</option>)}
              </select>
              <span className={`istatus istatus--${i.status.toLowerCase()}`}>{i.status === 'OPEN' ? t('inc.open') : t('inc.acknowledged')}</span>
              {i.status === 'OPEN' && <button type="button" onClick={() => change(i, { status: 'ACKNOWLEDGED' })}>{t('inc.acknowledge')}</button>}
              <button type="button" onClick={() => change(i, { status: 'RESOLVED' })}>{t('inc.resolve')}</button>
            </div>
          </li>
        ))}
      </ul>
      {resolved.length > 0 && (
        <>
          <h4>{t('inc.recent')}</h4>
          <ul className="incidents__resolved">
            {resolved.map((i) => (
              <li key={i.id}><span className="incident__type">{label('incidentType', i.type)}</span> {i.title}</li>
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
