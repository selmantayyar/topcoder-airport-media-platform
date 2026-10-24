import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  errorMessage,
  media,
  type AdvertiserOverviewRow,
  type ApprovalItem,
  type InventorySlot,
  type InventoryView,
} from '../server/api';
import { isInjectedEvent } from '../server/events';
import { useServerEvents, type ServerEvent } from '../server/stream';
import { day, money, num } from '../format';
import { useI18n } from '../i18n/I18nProvider';

type Tab = 'inventory' | 'yield' | 'approvals' | 'advertisers';

/**
 * Media Manager (a Peripheral Role: plain but fully functional): the inventory calendar, yield pricing and
 * forecast, the approval queue that gates what the decision pipeline may play, and the advertiser overview.
 */
export function MediaManagerView() {
  const { t } = useI18n();
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

  useServerEvents(
    useCallback((event: ServerEvent) => {
      if (event.type === 'incident' || isInjectedEvent(event)) load();
    }, [load]),
  );

  const decide = (item: ApprovalItem, approve: boolean) =>
    (approve ? media.approve(item.campaignId) : media.reject(item.campaignId))
      .then(() => {
        setNotice(t(approve ? 'mm.approved' : 'mm.rejected', { name: item.name }));
        load();
      })
      .catch((e: unknown) => setNotice(errorMessage(e, t('common.failed'))));

  const tabs: { id: Tab; label: string }[] = [
    { id: 'approvals', label: t('mm.tab.approvals', { n: approvals.length }) },
    { id: 'inventory', label: t('mm.tab.inventory') },
    { id: 'yield', label: t('mm.tab.yield') },
    { id: 'advertisers', label: t('mm.tab.advertisers') },
  ];

  return (
    <section className="mm">
      <header className="retail__head">
        <h2>{t('role.media-manager')}</h2>
        <nav className="retail__tabs" aria-label={t('mm.sections')}>
          {tabs.map((item) => (
            <button key={item.id} type="button" className={tab === item.id ? 'is-active' : ''} onClick={() => setTab(item.id)}>
              {item.label}
            </button>
          ))}
        </nav>
        <button type="button" className="mm__refresh" onClick={load}>{t('mm.refresh')}</button>
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
  const { t } = useI18n();
  return (
    <div className="retail__panel">
      <h3>{t('mm.awaiting')}</h3>
      <p className="builder__hint">{t('mm.awaitingHint')}</p>
      <table className="retail__table">
        <thead>
          <tr>
            <th>{t('common.campaign')}</th><th>{t('common.advertiser')}</th><th>{t('common.creative')}</th><th>{t('mm.qa')}</th>
            <th>{t('common.schedule')}</th><th className="num">{t('common.budget')}</th><th />
          </tr>
        </thead>
        <tbody>
          {items.map((i) => (
            <tr key={i.campaignId}>
              <td>{i.name}</td>
              <td>{i.advertiserName}</td>
              <td>
                {i.headline ?? '—'}{i.offerText && i.offerText !== i.headline ? ` · ${i.offerText}` : ''}
                {i.languages.length > 0 && <span className="mm__langs"> {i.languages.map((l) => l.toUpperCase()).join(' · ')}</span>}
              </td>
              <td>{i.creativeProblems.length === 0
                ? <span className="tag tag--running">{t('mm.pass')}</span>
                : <span className="mm__qa-fail" title={i.creativeProblems.join('; ')}>⚠ {i.creativeProblems.join('; ')}</span>}</td>
              <td className="history__dates">{day(i.scheduleStart)} → {day(i.scheduleEnd)}</td>
              <td className="num">{money(i.budget)}</td>
              <td className="history__actions">
                <button type="button" onClick={() => onDecide(i, true)}>{t('mm.approve')}</button>
                <button type="button" onClick={() => onDecide(i, false)}>{t('mm.reject')}</button>
              </td>
            </tr>
          ))}
          {items.length === 0 && <tr><td colSpan={7} className="retail__empty">{t('mm.nothing')}</td></tr>}
        </tbody>
      </table>
    </div>
  );
}

function InventoryCalendar({ inventory }: { inventory: InventoryView }) {
  const { t } = useI18n();
  const zones = useMemo(() => [...new Set(inventory.screens.map((s) => s.zoneName))], [inventory]);
  const [zone, setZone] = useState(zones[0] ?? '');
  const [showPrices, setShowPrices] = useState(false);
  const rows = inventory.screens.filter((s) => s.zoneName === zone);

  return (
    <div className="retail__panel">
      <div className="mm__bar">
        <h3>{t('mm.inventory', { day: day(inventory.day) })}</h3>
        <select value={zone} onChange={(e) => setZone(e.target.value)} aria-label={t('mm.zone')}>
          {zones.map((z) => <option key={z} value={z}>{z}</option>)}
        </select>
        <label className="mm__toggle"><input type="checkbox" checked={showPrices} onChange={(e) => setShowPrices(e.target.checked)} /> {t('mm.showPrices')}</label>
        <span className="mm__legend">
          <span className="mm__cell mm__cell--booked">●</span> {t('mm.booked')}
          <span className="mm__cell">○</span> {t('mm.available')}
          <span className="mm__cell mm__cell--premium">★</span> {t('mm.premium')}
        </span>
      </div>
      <div className="mm__scroll">
        <table className="mm__grid">
          <thead>
            <tr><th>{t('mm.screen')}</th>{inventory.hours.map((h) => <th key={h}>{String(h).padStart(2, '0')}</th>)}</tr>
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
  const { t } = useI18n();
  const state = slot.booked ? t('mm.slotBooked') : t('mm.slotAvailable');
  const cls = ['mm__cell', slot.booked ? 'mm__cell--booked' : '', slot.premium ? 'mm__cell--premium' : ''].join(' ');
  return (
    <td className={cls} title={`${state}${slot.premium ? ` · ${t('mm.slotPremium')}` : ''} · ${t('mm.slotPax', { n: num(slot.footfall) })} · ${money(slot.price)}`}>
      {showPrice ? money(slot.price) : `${slot.booked ? '●' : '○'}${slot.premium ? '★' : ''}`}
    </td>
  );
}

function Yield({ inventory }: { inventory: InventoryView }) {
  const { t } = useI18n();
  return (
    <div className="retail__panel">
      <h3>{t('mm.tab.yield')}</h3>
      <div className="kpi-row">
        <Stat label={t('mm.occupancyToday')} value={`${inventory.occupancyPct.toFixed(1)}%`} />
        <Stat label={t('mm.basePrice')} value={money(inventory.basePricePerHour)} />
        <Stat label={t('mm.premiumFrom')} value={t('common.pax', { n: num(Math.round(inventory.premiumFootfallThreshold)) })} />
        <Stat label={t('mm.forecast7')} value={money(inventory.forecastTotal)} />
      </div>
      <p className="builder__hint">{t('mm.yieldHint')}</p>
      <table className="retail__table">
        <thead><tr><th>{t('mm.day')}</th><th className="num">{t('mm.occupancy')}</th><th className="num">{t('mm.forecastRevenue')}</th></tr></thead>
        <tbody>
          {inventory.forecast.map((d) => (
            <tr key={d.date}><td>{day(d.date)}</td><td className="num">{d.occupancyPct.toFixed(1)}%</td><td className="num">{money(d.revenue)}</td></tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function Advertisers({ rows }: { rows: AdvertiserOverviewRow[] }) {
  const { t, label } = useI18n();
  return (
    <div className="retail__panel">
      <h3>{t('mm.bySpend')}</h3>
      <table className="retail__table">
        <thead>
          <tr>
            <th>#</th><th>{t('common.advertiser')}</th><th>{t('common.kind')}</th><th className="num">{t('common.spend')}</th>
            <th className="num">{t('common.impr')}</th><th className="num">{t('common.redeem')}</th>
            <th className="num">{t('mm.running')}</th><th>{t('mm.under')}</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r, i) => (
            <tr key={r.advertiserId}>
              <td>{i + 1}</td>
              <td>{r.name}</td>
              <td>{label('advertiserKind', r.kind)}</td>
              <td className="num">{money(r.spend)}</td>
              <td className="num">{num(r.impressions)}</td>
              <td className="num">{num(r.redemptions)}</td>
              <td className="num">{r.runningCampaigns}</td>
              <td>
                {r.underDelivering.length === 0 ? '—' : r.underDelivering.map((u) => (
                  <div key={u.campaignId} className="mm__flag">
                    {t('mm.pace', { name: u.name, pace: u.pacePct.toFixed(0), delivered: u.deliveredPct.toFixed(1), expected: u.expectedPct.toFixed(1) })}
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
