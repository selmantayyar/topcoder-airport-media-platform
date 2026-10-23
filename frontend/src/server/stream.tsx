import {
  createContext,
  useContext,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from 'react';

export interface ClockSnapshot {
  simTime: string;
  speed: number;
  running: boolean;
}

export type StreamStatus = 'connecting' | 'live' | 'error';

export interface ServerEvent {
  type: string;
  at: string;
  data: unknown;
}

type EventListener = (event: ServerEvent) => void;

interface StreamContextValue {
  clock: ClockSnapshot | null;
  status: StreamStatus;
  subscribe: (listener: EventListener) => () => void;
}

const StreamContext = createContext<StreamContextValue | null>(null);

/**
 * Opens one EventSource to the shared /api/stream channel for the whole app. Clock
 * ticks update context state; every other event is fanned out to subscribers, so a
 * single connection drives every live view.
 */
export function ServerStreamProvider({ children }: { children: ReactNode }) {
  const [clock, setClock] = useState<ClockSnapshot | null>(null);
  const [status, setStatus] = useState<StreamStatus>('connecting');
  const listeners = useRef(new Set<EventListener>());

  useEffect(() => {
    const source = new EventSource('/api/stream');

    source.onmessage = (message) => {
      const event = JSON.parse(message.data) as ServerEvent;
      setStatus('live');
      if (event.type === 'clock') {
        setClock(event.data as ClockSnapshot);
      } else {
        listeners.current.forEach((listener) => listener(event));
      }
    };

    source.onerror = () => setStatus('error');

    return () => source.close();
  }, []);

  const subscribe = (listener: EventListener) => {
    listeners.current.add(listener);
    return () => listeners.current.delete(listener);
  };

  return (
    <StreamContext.Provider value={{ clock, status, subscribe }}>
      {children}
    </StreamContext.Provider>
  );
}

function useStream(): StreamContextValue {
  const ctx = useContext(StreamContext);
  if (!ctx) {
    throw new Error('useStream must be used within a ServerStreamProvider');
  }
  return ctx;
}

export function useClock() {
  const { clock, status } = useStream();
  return { clock, status };
}

/** Register a callback for non-clock server events (injected events, later updates). */
export function useServerEvents(listener: EventListener) {
  const { subscribe } = useStream();
  useEffect(() => subscribe(listener), [subscribe, listener]);
}
