import { BRAND } from '../brand';

/** The brand mark: the configured logo image, or a generic screen-and-signal glyph in the accent colour. */
export function BrandLogo({ size = 22 }: { size?: number }) {
  if (BRAND.logoUrl) {
    return <img src={BRAND.logoUrl} alt="" width={size} height={size} className="brand-logo" />;
  }
  return (
    <svg className="brand-logo" width={size} height={size} viewBox="0 0 32 32" aria-hidden>
      <rect x="3" y="7" width="26" height="17" rx="3" fill="none" stroke="currentColor" strokeWidth="2.5" />
      <path d="M11 28h10M16 24v4" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" />
      <path d="M9 18.5l4.5-6 3.5 4 2.5-2.5 3.5 4.5" fill="none" stroke="currentColor" strokeWidth="2.2"
        strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
