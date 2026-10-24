// Shared display formatting. The simulation runs in UTC, so dates and times are shown in UTC.

export function money(amount: number, currency = 'USD'): string {
  return amount.toLocaleString('en-US', { style: 'currency', currency, maximumFractionDigits: 0 });
}

export function day(iso: string): string {
  return new Date(iso).toLocaleDateString('en-GB', { timeZone: 'UTC', day: '2-digit', month: 'short' });
}

export function hhmm(iso: string): string {
  return new Date(iso).toLocaleTimeString('en-GB', { timeZone: 'UTC', hour: '2-digit', minute: '2-digit' });
}

/** An advertiser kind as people say it: F&B, Retail, Airline. */
export function kindLabel(kind: string): string {
  if (kind === 'FNB') return 'F&B';
  if (kind === 'RETAILER') return 'Retail';
  return kind.charAt(0) + kind.slice(1).toLowerCase();
}
