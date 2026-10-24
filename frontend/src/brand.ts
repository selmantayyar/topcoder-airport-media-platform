// White-label theme config: the one place the product's name, logo and colours come from. Re-skinning the
// platform for another operator means editing this file (and public/favicon.svg) only. Every name here is
// invented — no real airport, airline or brand marks.

export interface BrandConfig {
  /** Shown in the app header and the browser tab. */
  productName: string;
  /** The (fictional) host airport — passenger-facing surfaces greet travellers with it. Mirrors the backend seed. */
  airportName: string;
  /** Optional logo image URL; when null the built-in mark below is drawn in the accent colour. */
  logoUrl: string | null;
  colors: {
    /** Accent on the light business-role theme (Retailer, Media Manager, Airline, claim page). */
    accentLight: string;
    /** Accent on the dark Platform Admin control-room theme. */
    accentDark: string;
    /** Passenger-screen ad gradient, start → end. */
    screenFrom: string;
    screenTo: string;
    /** Passenger-screen advertiser line and FIDS highlights. */
    screenHighlight: string;
    /** Passenger-screen offer line. */
    screenOffer: string;
  };
}

export const BRAND: BrandConfig = {
  productName: 'Airport Media Platform',
  airportName: 'Amberport International',
  logoUrl: null,
  colors: {
    accentLight: '#2563eb',
    accentDark: '#58a6ff',
    screenFrom: '#16365c',
    screenTo: '#0b1b30',
    screenHighlight: '#ffcf6a',
    screenOffer: '#7fe6a9',
  },
};

/** Publishes the brand colours as CSS custom properties; the light/dark themes and the kiosk read them. */
export function applyBrand(root: HTMLElement = document.documentElement): void {
  const c = BRAND.colors;
  root.style.setProperty('--brand-accent-light', c.accentLight);
  root.style.setProperty('--brand-accent-dark', c.accentDark);
  root.style.setProperty('--brand-screen-from', c.screenFrom);
  root.style.setProperty('--brand-screen-to', c.screenTo);
  root.style.setProperty('--brand-screen-highlight', c.screenHighlight);
  root.style.setProperty('--brand-screen-offer', c.screenOffer);
  document.title = BRAND.productName;
}
