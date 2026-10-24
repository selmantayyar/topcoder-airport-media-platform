// Shared display formatting in the UI language. The simulation runs in UTC, so dates and times are shown in UTC.

let numberLocale = 'en-US';
let dateLocale = 'en-GB';

/** Called by the i18n provider as the language changes; everything below formats in that language. */
export function setFormatLocale(lang: string): void {
  numberLocale = lang === 'es' ? 'es-ES' : 'en-US';
  dateLocale = lang === 'es' ? 'es-ES' : 'en-GB';
}

export function money(amount: number, currency = 'USD'): string {
  return amount.toLocaleString(numberLocale, { style: 'currency', currency, maximumFractionDigits: 0 });
}

export function num(value: number): string {
  return value.toLocaleString(numberLocale);
}

export function day(iso: string): string {
  return new Date(iso).toLocaleDateString(dateLocale, { timeZone: 'UTC', day: '2-digit', month: 'short' });
}

export function hhmm(iso: string): string {
  return new Date(iso).toLocaleTimeString(dateLocale, { timeZone: 'UTC', hour: '2-digit', minute: '2-digit' });
}

export function hhmmss(iso: string): string {
  return new Date(iso).toLocaleTimeString(dateLocale, { timeZone: 'UTC' });
}
