import { useCallback, useEffect, useRef, useState } from 'react';
import {
  retailers,
  type DashboardMetrics,
  type FlowWindow,
  type LabelCount,
  type OutletSummary,
  type PassengerFlow,
} from '../server/api';
import { money } from '../format';
import { isInjectedEvent } from '../server/events';
import { useServerEvents, type ServerEvent } from '../server/stream';

/**
 * Retailer live dashboard for one company: its impression → scan → redemption funnel, attributed
 * revenue, and next-60/120-minute footfall (with passengers held by delays). Polls every few seconds
 * and refetches immediately on redemption/scan and injected events, so a QR claim or a delay shows up
 * within ~2s. Remount (via `key`) to switch company.
 */
export function RetailerDashboard({ advertiserId }: { advertiserId: number }) {
  const [metrics, setMetrics] = useState<DashboardMetrics | null>(null);
  const [flow, setFlow] = useState<PassengerFlow | null>(null);
  const [outlets, setOutlets] = useState<OutletSummary[]>([]);
  const [outletId, setOutletId] = useState<number | null>(null);
  const [flowMinutes, setFlowMinutes] = useState<FlowMinutes>(120);
  const outletRef = useRef<number | null>(null);

  const refresh = useCallback(() => {
    retailers.metrics(advertiserId).then(setMetrics).catch(() => undefined);
    retailers.flow(advertiserId, outletRef.current).then(setFlow).catch(() => undefined);
  }, [advertiserId]);

  useEffect(() => {
    outletRef.current = outletId;
    refresh();
    const timer = setInterval(refresh, 3000);
    return () => clearInterval(timer);
  }, [outletId, refresh]);

  useEffect(() => {
    retailers.outlets(advertiserId).then(setOutlets).catch(() => undefined);
  }, [advertiserId]);

  // A claim/scan anywhere, or an injected flight/screen event, should surface on this dashboard within ~2s.
  useServerEvents(
    useCallback(
      (event: ServerEvent) => {
        if (event.type === 'redemption' || event.type === 'scan' || isInjectedEvent(event)) refresh();
      },
      [refresh],
    ),
  );

  const activeFlow = flow ? (flowMinutes === 60 ? flow.next60 : flow.next120) : null;

  return (
    <section className="retail">
      <div className="kpi-row">
        <StatTile label="Impressions" value={metrics?.impressions} />
        <StatTile label="Scans" value={metrics?.scans} />
        <StatTile label="Redemptions" value={metrics?.redemptions} accent />
        <StatTile label="Conversion" value={metrics ? pct(metrics.conversionRate) : undefined} />
        <StatTile label="Attributed revenue" value={metrics ? money(metrics.attributedRevenue) : undefined} />
      </div>

      <div className="retail__cols">
        <div className="retail__panel">
          <h3>Campaigns</h3>
          <table className="retail__table">
            <thead>
              <tr><th>Campaign</th><th>Status</th><th>Impr.</th><th>Scans</th><th>Redeem</th><th>Revenue</th></tr>
            </thead>
            <tbody>
              {(metrics?.campaigns ?? []).map((c) => (
                <tr key={c.campaignId}>
                  <td>{c.name}</td>
                  <td><span className={`tag tag--${c.status.toLowerCase()}`}>{c.status}</span></td>
                  <td className="num">{c.impressions.toLocaleString()}</td>
                  <td className="num">{c.scans.toLocaleString()}</td>
                  <td className="num">{c.redemptions.toLocaleString()}</td>
                  <td className="num">{money(c.revenue)}</td>
                </tr>
              ))}
              {metrics?.campaigns.length === 0 && (
                <tr><td colSpan={6} className="retail__empty">No campaigns for this company yet.</td></tr>
              )}
            </tbody>
          </table>
        </div>

        <div className="retail__panel">
          <div className="retail__flow-head">
            <h3>Passenger flow near my stores</h3>
            <div className="retail__window" role="group" aria-label="Flow window">
              {FLOW_WINDOWS.map((m) => (
                <button
                  key={m}
                  type="button"
                  className={flowMinutes === m ? 'is-active' : ''}
                  onClick={() => setFlowMinutes(m)}
                >
                  {m}m
                </button>
              ))}
            </div>
          </div>
          <select
            className="retail__outlet"
            value={outletId ?? ''}
            onChange={(e) => setOutletId(e.target.value ? Number(e.target.value) : null)}
            aria-label="Scope passenger flow to one outlet"
          >
            <option value="">All my outlets</option>
            {outlets.map((o) => (
              <option key={o.id} value={o.id}>
                {o.name} — {o.zoneName}{o.terminalCode ? ` (${o.terminalCode})` : ''}
              </option>
            ))}
          </select>
          {activeFlow ? <Flow flow={activeFlow} /> : <p className="retail__empty">Loading…</p>}
        </div>
      </div>
    </section>
  );
}

const FLOW_WINDOWS = [60, 120] as const;
type FlowMinutes = (typeof FLOW_WINDOWS)[number];

function Flow({ flow }: { flow: FlowWindow }) {
  return (
    <div className="flow">
      <p className="flow__total">
        <strong>{flow.footfall.toLocaleString()}</strong> passengers expected in the next {flow.minutes} min
      </p>
      {flow.delayedDepartures > 0 && (
        <p className="flow__delayed">
          <strong>{flow.delayedPassengers.toLocaleString()}</strong> held by {flow.delayedDepartures} delayed
          {flow.delayedDepartures === 1 ? ' departure' : ' departures'} — extra dwell time near your stores
        </p>
      )}
      <BarList title="Top destinations" items={flow.topDestinations} />
      <BarList title="Top nationalities (by route language)" items={flow.topNationalities} />
    </div>
  );
}

function BarList({ title, items }: { title: string; items: LabelCount[] }) {
  const max = items.reduce((m, i) => Math.max(m, i.value), 0) || 1;
  return (
    <div className="barlist">
      <h4>{title}</h4>
      {items.length === 0 && <p className="retail__empty">No upcoming flights.</p>}
      {items.map((item) => (
        <div key={item.label} className="barlist__row" title={`${item.label}: ${item.value.toLocaleString()} pax`}>
          <span className="barlist__label">{item.label}</span>
          <span className="barlist__track">
            <span className="barlist__fill" style={{ width: `${(item.value / max) * 100}%` }} />
          </span>
          <span className="barlist__value">{item.value.toLocaleString()}</span>
        </div>
      ))}
    </div>
  );
}

function StatTile({ label, value, accent }: { label: string; value?: number | string; accent?: boolean }) {
  return (
    <div className={`stat-tile ${accent ? 'stat-tile--accent' : ''}`}>
      <span className="stat-tile__value">{value ?? '—'}</span>
      <span className="stat-tile__label">{label}</span>
    </div>
  );
}

function pct(fraction: number): string {
  return `${(fraction * 100).toFixed(1)}%`;
}
