import { useCallback, useEffect, useState } from 'react';
import { money, num } from '../format';
import { useI18n } from '../i18n/I18nProvider';
import { admin, type AdminKpis, type AdminOverview, type MasterDataSchema } from '../server/api';
import { isInjectedEvent } from '../server/events';
import { useClock, useServerEvents, type ServerEvent } from '../server/stream';
import { IncidentTracker } from './IncidentTracker';
import { MasterDataTable } from './MasterDataTable';
import { TerminalMap } from './TerminalMap';

/**
 * The Platform Admin control room: live KPI strip, terminal floor plan (screen status + passenger heat),
 * the incident tracker, and master-data management through one generic list + form component.
 */
export function AdminConsole() {
  const { clock } = useClock();
  const { t, label } = useI18n();
  const [overview, setOverview] = useState<AdminOverview | null>(null);
  const [schemas, setSchemas] = useState<MasterDataSchema[]>([]);
  const [masterTab, setMasterTab] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const refresh = useCallback(() => {
    admin.overview().then(setOverview).catch(() => undefined);
  }, []);

  useEffect(() => {
    refresh();
    const timer = setInterval(refresh, 2000);
    return () => clearInterval(timer);
  }, [refresh]);

  useEffect(() => {
    admin.schemas().then((s) => {
      setSchemas(s);
      setMasterTab((cur) => cur ?? s[0]?.type ?? null);
    }).catch(() => undefined);
  }, []);

  useServerEvents(
    useCallback((event: ServerEvent) => {
      if (event.type === 'incident' || isInjectedEvent(event)) refresh();
    }, [refresh]),
  );

  const schema = schemas.find((s) => s.type === masterTab);

  return (
    <section className="console">
      {overview ? <KpiStrip kpis={overview.kpis} /> : <p className="console__loading">{t('admin.loading')}</p>}
      {notice && <p className="field-error">{notice}</p>}

      <div className="console__grid">
        <div className="console__panel">
          <h3>{t('admin.map')}</h3>
          {overview && <TerminalMap terminals={overview.terminals} />}
        </div>
        <div className="console__panel">
          {overview && (
            <IncidentTracker
              incidents={overview.incidents}
              owners={overview.incidentOwners}
              simTime={clock?.simTime}
              onChanged={refresh}
              onError={setNotice}
            />
          )}
        </div>
      </div>

      <div className="console__panel">
        <h3>{t('admin.masterData')}</h3>
        <nav className="console__tabs" aria-label={t('admin.masterTypes')}>
          {schemas.map((s) => (
            <button key={s.type} type="button" className={s.type === masterTab ? 'is-active' : ''} onClick={() => setMasterTab(s.type)}>
              {label('md.type', s.type)}
            </button>
          ))}
        </nav>
        {schema && <MasterDataTable key={schema.type} schema={schema} onChanged={refresh} />}
      </div>
    </section>
  );
}

function KpiStrip({ kpis }: { kpis: AdminKpis }) {
  const { t } = useI18n();
  return (
    <div className="kpi-row kpi-row--console">
      <Tile label={t('kpi.screensOnline')} value={`${kpis.screensOnlinePct.toFixed(1)}%`} sub={t('kpi.of', { n: kpis.screensTotal })} />
      <Tile label={t('kpi.adPlays')} value={num(kpis.adPlaysToday)} />
      <Tile label={t('kpi.impressions')} value={num(kpis.audienceImpressionsToday)} sub={t('kpi.peopleReached')} />
      <Tile label={t('kpi.revenue')} value={money(kpis.revenueToday, kpis.currency)} sub={kpis.currency} />
      <Tile label={t('kpi.activeCampaigns')} value={num(kpis.activeCampaigns)} />
      <Tile label={t('kpi.latency')} value={`${Math.round(kpis.averageLatencyMs)} ms`} />
      <Tile label={t('kpi.openIncidents')} value={num(kpis.openIncidents)} alert={kpis.openIncidents > 0} />
      <Tile label={t('kpi.delayed')} value={num(kpis.delayedDepartures)} alert={kpis.delayedDepartures > 0} />
    </div>
  );
}

function Tile({ label, value, sub, alert }: { label: string; value: string; sub?: string; alert?: boolean }) {
  return (
    <div className={`stat-tile ${alert ? 'stat-tile--alert' : ''}`}>
      <span className="stat-tile__value">{value}</span>
      <span className="stat-tile__label">{label}{sub ? ` · ${sub}` : ''}</span>
    </div>
  );
}
