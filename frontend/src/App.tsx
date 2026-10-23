import { useEffect, useState } from 'react';
import { AppHeader } from './components/AppHeader';
import { SimControlBar } from './components/SimControlBar';
import { ServerStreamProvider } from './server/stream';
import { DEFAULT_ROLE, labelForRole, themeForRole, type RoleId } from './roles';
import './App.css';

export default function App() {
  const [role, setRole] = useState<RoleId>(DEFAULT_ROLE);
  const theme = themeForRole(role);
  const activeLabel = labelForRole(role);

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
  }, [theme]);

  return (
    <ServerStreamProvider>
      <div className="app-shell">
        <AppHeader activeRole={role} onRoleChange={setRole} />
        <SimControlBar />
        <main className="app-main">
          <section className="placeholder-panel">
            <h1>{activeLabel}</h1>
            <p>
              This persona's dashboard lands in a later ticket. The control bar above drives
              the shared simulation clock — play, change speed, jump, and inject events; every
              role view reacts to the same live stream.
            </p>
          </section>
        </main>
      </div>
    </ServerStreamProvider>
  );
}
