import { useEffect, useRef, useState } from 'react';
import { useParams } from 'react-router-dom';
import { QRCodeSVG } from 'qrcode.react';
import { BRAND } from '../brand';
import { LanguageSwitch, useI18n } from '../i18n/I18nProvider';
import { errorMessage, promo, type ClaimResult, type PromoInfo, type PromoIssued } from '../server/api';
import { BrandLogo } from './BrandLogo';

type Outcome = ClaimResult['status'];

/**
 * The LAN-served mobile page a passenger reaches by scanning the promo QR. It shows the offer and
 * records a redemption on claim. Deliberately standalone (no app chrome) and phone-first; it opens in the
 * language the screen was showing (`?lang=`) and the passenger can switch it.
 */
export function ClaimPage() {
  const { code } = useParams();
  const { t, lang } = useI18n();
  const [info, setInfo] = useState<PromoInfo | null>(null);
  const [outcome, setOutcome] = useState<Outcome | null>(null);
  const [error, setError] = useState<'claim.notFound' | 'claim.failed' | null>(null);
  const [claiming, setClaiming] = useState(false);
  const scanned = useRef<string | null>(null);

  useEffect(() => {
    document.documentElement.dataset.theme = 'light';
  }, []);

  useEffect(() => {
    if (!code) return;
    // Opening the claim page is a QR scan — record it once for the retailer funnel (the ref guards
    // against StrictMode's double effect run and language switches inflating the scan count).
    if (scanned.current !== code) {
      scanned.current = code;
      promo.scan(code);
    }
  }, [code]);

  useEffect(() => {
    if (!code) return;
    promo
      .info(code, lang)
      .then(setInfo)
      .catch(() => setError('claim.notFound'));
  }, [code, lang]);

  const claim = () => {
    if (!code) return;
    setClaiming(true);
    promo
      .claim(code, lang)
      .then((r) => setOutcome(r.status))
      .catch(() => setError('claim.failed'))
      .finally(() => setClaiming(false));
  };

  if (error) {
    return <ClaimShell><p className="claim__msg claim__msg--bad">{t(error)}</p></ClaimShell>;
  }
  if (!info) {
    return <ClaimShell><p className="claim__msg">{t('claim.loading')}</p></ClaimShell>;
  }

  const done: Outcome | null = outcome ?? (info.redeemed ? 'ALREADY_CLAIMED' : null);

  return (
    <ClaimShell>
      <p className="claim__brand">{info.advertiserName}</p>
      <h1 className="claim__offer">{info.offerText}</h1>

      {done ? (
        <div className={`claim__outcome claim__outcome--${done.toLowerCase()}`}>
          <strong>{t(`claim.title.${done}`)}</strong>
          <p>{t(`claim.message.${done}`)}</p>
          <span className="claim__code">{info.code}</span>
          {done === 'CLAIMED' && code && <ShareWithFamily code={code} />}
        </div>
      ) : info.expired ? (
        <div className="claim__outcome claim__outcome--expired">
          <strong>{t('claim.title.EXPIRED')}</strong>
          <p>{t('claim.noLongerValid')}</p>
        </div>
      ) : (
        <>
          <p className="claim__validity">
            {t('claim.validity', { n: Math.max(1, Math.round(info.secondsRemaining / 60)) })}
          </p>
          <button type="button" className="claim__btn" onClick={claim} disabled={claiming}>
            {claiming ? t('claim.claiming') : t('claim.claim')}
          </button>
        </>
      )}
    </ClaimShell>
  );
}

/** One extra code a passenger can hand to a travelling companion — same offer, its own single use. */
function ShareWithFamily({ code }: { code: string }) {
  const { t, lang } = useI18n();
  const [shared, setShared] = useState<PromoIssued | null>(null);
  const [error, setError] = useState<string | null>(null);

  if (shared) {
    const link = `${window.location.origin}${shared.claimPath}?lang=${lang}`;
    return (
      <div className="claim__share">
        <p>{t('claim.familyCode')}</p>
        <QRCodeSVG value={link} size={160} includeMargin />
        <span className="claim__code">{shared.code}</span>
        <a href={link}>{link}</a>
      </div>
    );
  }
  return (
    <div className="claim__share">
      <button
        type="button"
        className="claim__btn claim__btn--secondary"
        onClick={() =>
          promo
            .share(code)
            .then(setShared)
            .catch((e: unknown) => setError(errorMessage(e, t('claim.shareFailed'))))
        }
      >
        {t('claim.share')}
      </button>
      {error && <p className="claim__msg claim__msg--bad">{error}</p>}
    </div>
  );
}

function ClaimShell({ children }: { children: React.ReactNode }) {
  return (
    <div className="claim">
      <div className="claim__card">
        <div className="claim__top">
          <div className="claim__logo"><BrandLogo size={18} /> {BRAND.airportName}</div>
          <LanguageSwitch className="lang-switch--claim" />
        </div>
        {children}
      </div>
    </div>
  );
}
