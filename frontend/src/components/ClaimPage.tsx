import { useEffect, useRef, useState } from 'react';
import { useParams } from 'react-router-dom';
import { promo, type ClaimResult, type PromoInfo } from '../server/api';

/**
 * The LAN-served mobile page a passenger reaches by scanning the promo QR. It shows the offer and
 * records a redemption on claim. Deliberately standalone (no app chrome) and phone-friendly.
 */
export function ClaimPage() {
  const { code } = useParams();
  const [info, setInfo] = useState<PromoInfo | null>(null);
  const [result, setResult] = useState<ClaimResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [claiming, setClaiming] = useState(false);
  const scanned = useRef<string | null>(null);

  useEffect(() => {
    document.documentElement.dataset.theme = 'light';
    if (!code) return;
    // Opening the claim page is a QR scan — record it once for the retailer funnel (the ref guards
    // against StrictMode's double effect run inflating the scan count).
    if (scanned.current !== code) {
      scanned.current = code;
      promo.scan(code);
    }
    promo
      .info(code)
      .then(setInfo)
      .catch(() => setError('This promo code was not found.'));
  }, [code]);

  const claim = () => {
    if (!code) return;
    setClaiming(true);
    promo
      .claim(code)
      .then(setResult)
      .catch(() => setError('Something went wrong claiming this offer.'))
      .finally(() => setClaiming(false));
  };

  if (error) {
    return <ClaimShell><p className="claim__msg claim__msg--bad">{error}</p></ClaimShell>;
  }
  if (!info) {
    return <ClaimShell><p className="claim__msg">Loading offer…</p></ClaimShell>;
  }

  const done = result ?? (info.redeemed ? alreadyClaimed(info) : null);

  return (
    <ClaimShell>
      <p className="claim__brand">{info.campaignName}</p>
      <h1 className="claim__offer">{info.offerText}</h1>

      {done ? (
        <div className={`claim__outcome claim__outcome--${done.status.toLowerCase()}`}>
          <strong>{outcomeTitle(done.status)}</strong>
          <p>{done.message}</p>
          <span className="claim__code">{info.code}</span>
        </div>
      ) : info.expired ? (
        <div className="claim__outcome claim__outcome--expired">
          <strong>Offer expired</strong>
          <p>This promo is no longer valid.</p>
        </div>
      ) : (
        <>
          <p className="claim__validity">
            Valid for about {Math.max(1, Math.round(info.secondsRemaining / 60))} more minutes.
          </p>
          <button type="button" className="claim__btn" onClick={claim} disabled={claiming}>
            {claiming ? 'Claiming…' : 'Claim this offer'}
          </button>
        </>
      )}
    </ClaimShell>
  );
}

function ClaimShell({ children }: { children: React.ReactNode }) {
  return (
    <div className="claim">
      <div className="claim__card">
        <div className="claim__logo">Amberport International</div>
        {children}
      </div>
    </div>
  );
}

function alreadyClaimed(info: PromoInfo): ClaimResult {
  return {
    status: 'ALREADY_CLAIMED',
    code: info.code,
    campaignId: info.campaignId,
    campaignName: info.campaignName,
    offerText: info.offerText,
    validUntil: info.validUntil,
    redeemedAt: null,
    segment: null,
    message: 'This code has already been claimed.',
  };
}

function outcomeTitle(status: ClaimResult['status']): string {
  switch (status) {
    case 'CLAIMED':
      return 'Offer claimed 🎉';
    case 'ALREADY_CLAIMED':
      return 'Already claimed';
    case 'EXPIRED':
      return 'Offer expired';
    default:
      return 'Done';
  }
}
