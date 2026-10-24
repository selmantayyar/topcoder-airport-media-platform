import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  errorMessage,
  media,
  type AdvertiserOverviewRow,
  type ApprovalItem,
  type InventorySlot,
  type InventoryView,
} from '../server/api';
import { day, kindLabel, money } from '../format';

type Tab = 'inventory' | 'yield' | 'approvals' | 'advertisers';

/**
 * Media Manager (a Peripheral Role: plain but fully functional): the inventory calendar, yield pricing and
 * forecast, the approval queue that gates what the decision pipeline may play, and the advertiser overview.
 */
export function MediaManagerView() {
  const [tab, setTab] = useState<Tab>('approvals');
  const [inventory, setInventory] = useState<InventoryView | null>(null);
  const [approvals, setApprovals] = useState<ApprovalItem[]>([]);
  const [advertisers, setAdvertisers] = useState<AdvertiserOverviewRow[]>([]);
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(() => {
    media.inventory().then(setInventory).catch(() => undefined);
    media.approvals().then(setApprovals).catch(() => undefined);
    media.advertisers().then(setAdvertisers).catch(() => undefined);
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const decide = (item: ApprovalItem, approve: boolean) =>
    (approve ? media.approve(item.campaignId) : media.reject(item.campaignId))
      .then(() => {
        setNotice(approve
          ? `Approved “${item.name}” — it is now live for the decision pipeline.`
          : `Rejected “${item.name}” — the decision pipeline will not play it.`);
        load();
      })
      .catch((e: unknown) => setNotice(errorMessage(e)));

  const tabs: { id: Tab; label: string }[] = [
    { id: 'approvals', label: `Approval queue (${approvals.length})` },
    { id: 'inventory', label: 'Inventory calendar' },
    { id: 'yield', label: 'Yield & forecast' },
    { id: 'advertisers', label: 'Advertisers' },
  ];

  return (
    <section className="mm">
      <header className="retail__head">
        <h2>Media Manager</h2>
        <nav className="retail__tabs" aria-label="Media Manager sections">
          {tabs.map((t) => (
            <button key={t.id} type="button" className={tab === t.id ? 'is-active' : ''} onClick={() => setTab(t.id)}>
              {t.label}
            </button>
          ))}
        </nav>
        <button type="button" className="mm__refresh" onClick={load}>Refresh</button>
      </header>
      {notice && <p className="history__notice">{notice}</p>}

      {tab === 'approvals' && <Approvals items={approvals} onDecide={decide} />}
      {tab === 'inventory' && inventory && <InventoryCalendar inventory={inventory} />}
      {tab === 'yield' && inventory && <Yield inventory={inventory} />}
      {tab === 'advertisers' && <Advertisers rows={advertisers} />}
    </section>
  );
}

function Approvals({ items, onDecide }: { items: ApprovalItem[]; onDecide: (i: ApprovalItem, approve: boolean) => void }) {
  return (
    <div className="retail__panel">
      <h3>Awaiting approval</h3>
      <p className="builder__hint">Only approved campaigns reach the decision pipeline; rejected and pending ones are dropped by its hard filters.</p>
      <table className="retail__table">
        <thead>
          <tr><th>Campaign</th><th>Advertiser</th><th>Creative</th><th>QA</th><th>Schedule</th><th>Budget</th><th /></tr>
        </thead>
        <tbody>
          {items.map((i) => (
            <tr key={i.campaignId}>
              <td>{i.name}</td>
              <td>{i.advertiserName}</td>
              <td>{i.headline ?? '—'}{i.offerText && i.offerText !== i.headline ? ` · ${i.offerText}` : ''}</td>
              <td>{i.creativeProblems.length === 0
                ? <span className="tag tag--running">Pass</span>
                : <span className="mm__qa-fail" title={i.creativeProblems.join('; ')}>⚠ {i.creativeProblems.join('; ')}</span>}</td>
              <td className="history__dates">{day(i.scheduleStart)} → {day(i.scheduleEnd)}</td>
              <td className="num">{money(i.budget)}</td>
              <td className="history__actions">
                <button type="button" onClick={() => onDecide(i, true)}>Approve</button>
                <button type="button" onClick={() => onDecide(i, false)}>Reject</button>
              </td>
            </tr>
          ))}
          {items.length === 0 && <tr><td colSpan={7} className="retail__empty">Nothing waiting.</td></tr>}
        </tbody>
      </table>
    </div>
  );
}

function InventoryCalendar({ inventory }: { inventory: InventoryView }) {
  const zones = useMemo(() => [...new Set(inventory.screens.map((s) => s.zoneName))], [inventory]);
  const [zone, setZone] = useState(zones[0] ?? '');
  const [showPrices, setShowPrices] = useState(false);
  const rows = inventory.screens.filter((s) => s.zoneName === zone);

  return (
    <div className="retail__panel">
      <div className="mm__bar">
        <h3>Inventory · {inventory.day}</h3>
        <select value={zone} onChange={(e) => setZone(e.target.value)} aria-label="Zone">
          {zones.map((z) => <option key={z} value={z}>{z}</option>)}
        </select>
        <label className="mm__toggle"><input type="checkbox" checked={showPrices} onChange={(e) => setShowPrices(e.target.checked)} /> Show prices</label>
        <span className="mm__legend">
          <span className="mm__cell mm__cell--booked">●</span> booked
          <span className="mm__cell">○</span> available
          <span className="mm__cell mm__cell--premium">★</span> premium (high predicted footfall)
        </span>
      </div>
      <div className="mm__scroll">
        <table className="mm__grid">
          <thead>
            <tr><th>Screen</th>{inventory.hours.map((h) => <th key={h}>{String(h).padStart(2, '0')}</th>)}</tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.screenId}>
                <th>{r.code}</th>
                {r.slots.map((s) => <SlotCell key={s.hour} slot={s} showPrice={showPrices} />)}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function SlotCell({ slot, showPrice }: { slot: InventorySlot; showPrice: boolean }) {
  const state = slot.booked ? 'Booked' : 'Available';
  const cls = ['mm__cell', slot.booked ? 'mm__cell--booked' : '', slot.premium ? 'mm__cell--premium' : ''].join(' ');
  return (
    <td className={cls} title={`${state}${slot.premium ? ' · premium' : ''} · ${slot.footfall} pax predicted · ${money(slot.price)}`}>
      {showPrice ? money(slot.price) : `${slot.booked ? '●' : '○'}${slot.premium ? '★' : ''}`}
    </td>
  );
}

function Yield({ inventory }: { inventory: InventoryView }) {
  return (
    <div className="retail__panel">
      <h3>Yield &amp; forecast</h3>
      <div className="kpi-row">
        <Stat label="Occupancy today" value={`${inventory.occupancyPct.toFixed(1)}%`} />
        <Stat label="Base price / screen-hour" value={money(inventory.basePricePerHour)} />
        <Stat label="Premium from" value={`${Math.round(inventory.premiumFootfallThreshold)} pax`} />
        <Stat label="7-day revenue forecast" value={money(inventory.forecastTotal)} />
      </div>
      <p className="builder__hint">
        Suggested price per slot = base × predicted footfall ÷ median footfall (floored at ½×, capped at 2½×).
        The forecast prices every booked slot, assuming each day repeats today's footfall pattern.
      </p>
      <table className="retail__table">
        <thead><tr><th>Day</th><th>Occupancy</th><th>Forecast revenue</th></tr></thead>
        <tbody>
          {inventory.forecast.map((d) => (
            <tr key={d.date}><td>{d.date}</td><td className="num">{d.occupancyPct.toFixed(1)}%</td><td className="num">{money(d.revenue)}</td></tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function Advertisers({ rows }: { rows: AdvertiserOverviewRow[] }) {
  return (
    <div className="retail__panel">
      <h3>Advertisers by spend</h3>
      <table className="retail__table">
        <thead>
          <tr><th>#</th><th>Advertiser</th><th>Kind</th><th>Spend</th><th>Impr.</th><th>Redeem</th><th>Running</th><th>Under-delivering</th></tr>
        </thead>
        <tbody>
          {rows.map((r, i) => (
            <tr key={r.advertiserId}>
              <td>{i + 1}</td>
              <td>{r.name}</td>
              <td>{kindLabel(r.kind)}</td>
              <td className="num">{money(r.spend)}</td>
              <td className="num">{r.impressions.toLocaleString()}</td>
              <td className="num">{r.redemptions.toLocaleString()}</td>
              <td className="num">{r.runningCampaigns}</td>
              <td>
                {r.underDelivering.length === 0 ? '—' : r.underDelivering.map((u) => (
                  <div key={u.campaignId} className="mm__flag">
                    ⚠ {u.name}: {u.pacePct.toFixed(0)}% of pace ({u.deliveredPct.toFixed(1)}% delivered vs {u.expectedPct.toFixed(1)}% expected)
                  </div>
                ))}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="stat-tile">
      <span className="stat-tile__value">{value}</span>
      <span className="stat-tile__label">{label}</span>
    </div>
  );
}
