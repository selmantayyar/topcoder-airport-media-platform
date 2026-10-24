import { useEffect, useState } from 'react';
import { campaigns, errorMessage, retailers, type CampaignDraft, type CreativeSummary, type RetailerSummary } from '../server/api';
import { CampaignBuilder } from './CampaignBuilder';
import { CampaignHistory } from './CampaignHistory';
import { RetailerDashboard } from './RetailerDashboard';

type Tab = 'dashboard' | 'build' | 'history';

const TABS: { id: Tab; label: string }[] = [
  { id: 'dashboard', label: 'Dashboard' },
  { id: 'build', label: 'Build campaign' },
  { id: 'history', label: 'History & library' },
];

/** The Retailer role: one company picker shared by the live dashboard, the builder, and history/library. */
export function RetailerWorkspace() {
  const [companies, setCompanies] = useState<RetailerSummary[]>([]);
  const [selected, setSelected] = useState<number | null>(null);
  const [tab, setTab] = useState<Tab>('dashboard');
  const [libraryCreative, setLibraryCreative] = useState<CreativeSummary | null>(null);
  const [prefill, setPrefill] = useState<CampaignDraft | null>(null);
  const [builderVersion, setBuilderVersion] = useState(0);
  const [historyVersion, setHistoryVersion] = useState(0);
  const [notice, setNotice] = useState<string | null>(null);

  const openBuilder = (draft: CampaignDraft | null, creative: CreativeSummary | null) => {
    setPrefill(draft);
    setLibraryCreative(creative);
    setBuilderVersion((v) => v + 1);
    setNotice(null);
    setTab('build');
  };

  const duplicate = (campaignId: number) =>
    Promise.all([campaigns.draft(campaignId), retailers.creatives(selected!)])
      .then(([draft, library]) => openBuilder(draft, library.find((c) => c.id === draft.creativeId) ?? null))
      .catch((e: unknown) => setNotice(errorMessage(e, 'Could not duplicate this campaign')));

  useEffect(() => {
    retailers.list().then((list) => {
      setCompanies(list);
      if (list.length > 0) setSelected((cur) => cur ?? list[0].id);
    });
  }, []);

  return (
    <section className="retail">
      <header className="retail__head">
        <h2>Retailer</h2>
        <nav className="retail__tabs" aria-label="Retailer sections">
          {TABS.map((t) => (
            <button key={t.id} type="button" className={tab === t.id ? 'is-active' : ''} onClick={() => setTab(t.id)}>
              {t.label}
            </button>
          ))}
        </nav>
        <label className="retail__picker">
          <span>Company</span>
          <select
            value={selected ?? ''}
            onChange={(e) => {
              setSelected(Number(e.target.value));
              setLibraryCreative(null);
              setPrefill(null);
              setNotice(null);
            }}
            aria-label="Select retail company"
          >
            {companies.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name} ({c.kind === 'FNB' ? 'F&B' : 'Retail'} · {c.outletCount} outlet{c.outletCount === 1 ? '' : 's'})
              </option>
            ))}
          </select>
        </label>
      </header>

      {notice && <p className="history__notice">{notice}</p>}

      {selected != null && tab === 'dashboard' && <RetailerDashboard key={selected} advertiserId={selected} />}
      {selected != null && tab === 'build' && (
        <CampaignBuilder
          key={`${selected}-${builderVersion}`}
          advertiserId={selected}
          libraryCreative={libraryCreative}
          initialDraft={prefill}
          onClearCreative={() => setLibraryCreative(null)}
          onSaved={(row) => {
            setNotice(`“${row.name}” is live — the decision pipeline will consider it on its next run.`);
            setLibraryCreative(null);
            setPrefill(null);
            setHistoryVersion((v) => v + 1);
            setTab('history');
          }}
        />
      )}
      {selected != null && tab === 'history' && (
        <CampaignHistory
          key={selected}
          advertiserId={selected}
          refreshKey={historyVersion}
          onUseCreative={(c) => openBuilder(null, c)}
          onDuplicate={duplicate}
        />
      )}
    </section>
  );
}
