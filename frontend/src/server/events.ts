import type { ServerEvent } from './stream';

/** Events the control bar injects. Every live view refetches on these, so both split-screen panes react at once. */
const INJECTED = new Set(['flight-delayed', 'gate-changed', 'screen-offline', 'screen-online']);

export function isInjectedEvent(event: ServerEvent): boolean {
  return INJECTED.has(event.type);
}
