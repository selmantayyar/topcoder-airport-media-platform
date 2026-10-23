import { useEffect, useState } from 'react';

export interface ClockTick {
  tickNumber: number;
  serverTime: string;
}

export type ClockStatus = 'connecting' | 'live' | 'error';

interface ClockState {
  tick: ClockTick | null;
  status: ClockStatus;
}

/**
 * Subscribes to the backend's Server-Sent Events clock stream and exposes the
 * latest tick plus a connection status. This is the end-to-end proof that the
 * live channel works; every later dashboard rides the same transport.
 */
const CLOCK_STREAM = '/api/clock/stream';

export function useClock(): ClockState {
  const [state, setState] = useState<ClockState>({ tick: null, status: 'connecting' });

  useEffect(() => {
    const source = new EventSource(CLOCK_STREAM);

    source.onmessage = (event) => {
      const tick = JSON.parse(event.data) as ClockTick;
      setState({ tick, status: 'live' });
    };

    source.onerror = () => {
      setState((prev) => ({ ...prev, status: 'error' }));
    };

    return () => source.close();
  }, []);

  return state;
}
