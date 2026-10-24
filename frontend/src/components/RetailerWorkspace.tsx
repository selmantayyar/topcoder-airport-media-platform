import { useEffect, useState } from 'react';
import { campaigns, errorMessage, retailers, type CampaignDraft, type CreativeSummary, type RetailerSummary } from '../server/api';
import { useI18n } from '../i18n/I18nProvider';
import type { MessageKey } from '../i18n/messages';
import { CampaignBuilder } from './CampaignBuilder';
import { CampaignHistory } from './CampaignHistory';
import { RetailerDashboard } from './RetailerDashboard';

type Tab = 'dashboard' | 'build' | 'history';

const TABS: { id: Tab; label: MessageKey }[] = [
  { id: 'dashboard', label: 'retail.tab.dashboard' },
  { id: 'build', label: 'retail.tab.build' },
  { id: 'history', label: 'retail.tab.history' },
];

/** The Retailer role: one company picker shared by the live dashboard, the builder, and history/library. */
export function RetailerWorkspace() {
  const { t, label } = useI18n();
  const [companies, setCompanies] = useState<RetailerSummary[]>([]);
  const [selected, setSelected] = useState<number | null>(null);
  const [active, setTab] = useState<Tab>('dashboard');
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
      .catch((e: unknown) => setNotice(errorMessage(e, t('retail.duplicateFailed'))));

  useEffect(() => {
    retailers.list().then((list) => {
      setCompanies(list);
      if (list.length > 0) setSelected((cur) => cur ?? list[0].id);
    });
  }, []);

  return (
    <section className="retail">
      <header className="retail__head">
        <h2>{t('role.retailer')}</h2>
        <nav className="retail__tabs" aria-label={t('retail.sections')}>
          {TABS.map((tab) => (
            <button key={tab.id} type="button" className={active === tab.id ? 'is-active' : ''} onClick={() => setTab(tab.id)}>
              {t(tab.label)}
            </button>
          ))}
        </nav>
        <label className="retail__picker">
          <span>{t('retail.company')}</span>
          <select
            value={selected ?? ''}
            onChange={(e) => {
              setSelected(Number(e.target.value));
              setLibraryCreative(null);
              setPrefill(null);
              setNotice(null);
            }}
            aria-label={t('retail.selectCompany')}
          >
            {companies.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name} ({label('advertiserKind', c.kind)} · {t(c.outletCount === 1 ? 'retail.outlets.one' : 'retail.outlets.other', { n: c.outletCount })})
              </option>
            ))}
          </select>
        </label>
      </header>

      {notice && <p className="history__notice">{notice}</p>}

      {selected != null && active === 'dashboard' && <RetailerDashboard key={selected} advertiserId={selected} />}
      {selected != null && active === 'build' && (
        <CampaignBuilder
          key={`${selected}-${builderVersion}`}
          advertiserId={selected}
          libraryCreative={libraryCreative}
          initialDraft={prefill}
          onClearCreative={() => setLibraryCreative(null)}
          onSaved={(row) => {
            setNotice(t('retail.submitted', { name: row.name }));
            setLibraryCreative(null);
            setPrefill(null);
            setHistoryVersion((v) => v + 1);
            setTab('history');
          }}
        />
      )}
      {selected != null && active === 'history' && (
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
