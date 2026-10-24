import { useCallback, useEffect, useState } from 'react';
import { admin, type AdminKpis, type AdminOverview, type MasterDataSchema } from '../server/api';
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
      if (event.type === 'incident' || event.type === 'screen-offline') refresh();
    }, [refresh]),
  );

  const schema = schemas.find((s) => s.type === masterTab);

  return (
    <section className="console">
      {overview ? <KpiStrip kpis={overview.kpis} /> : <p className="console__loading">Loading control room…</p>}
      {notice && <p className="field-error">{notice}</p>}

      <div className="console__grid">
        <div className="console__panel">
          <h3>Terminal map</h3>
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
        <h3>Master data</h3>
        <nav className="console__tabs" aria-label="Master data types">
          {schemas.map((s) => (
            <button key={s.type} type="button" className={s.type === masterTab ? 'is-active' : ''} onClick={() => setMasterTab(s.type)}>
              {s.label}
            </button>
          ))}
        </nav>
        {schema && <MasterDataTable key={schema.type} schema={schema} onChanged={refresh} />}
      </div>
    </section>
  );
}

function KpiStrip({ kpis }: { kpis: AdminKpis }) {
  const money = new Intl.NumberFormat('en-US', { style: 'currency', currency: kpis.currency, maximumFractionDigits: 0 });
  return (
    <div className="kpi-row kpi-row--console">
      <Tile label="Screens online" value={`${kpis.screensOnlinePct.toFixed(1)}%`} sub={`of ${kpis.screensTotal}`} />
      <Tile label="Ad plays today" value={kpis.adPlaysToday.toLocaleString()} />
      <Tile label="Impressions today" value={kpis.audienceImpressionsToday.toLocaleString()} sub="people reached" />
      <Tile label="Revenue today" value={money.format(kpis.revenueToday)} sub={kpis.currency} />
      <Tile label="Active campaigns" value={kpis.activeCampaigns.toLocaleString()} />
      <Tile label="Avg latency" value={`${Math.round(kpis.averageLatencyMs)} ms`} />
      <Tile label="Open incidents" value={kpis.openIncidents.toLocaleString()} alert={kpis.openIncidents > 0} />
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
