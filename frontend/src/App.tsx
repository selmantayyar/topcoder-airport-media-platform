import { useEffect, useState } from 'react';
import { AppHeader } from './components/AppHeader';
import { SimControlBar } from './components/SimControlBar';
import { PresencePanel } from './components/PresencePanel';
import { ScreenChooser } from './components/ScreenChooser';
import { RetailerWorkspace } from './components/RetailerWorkspace';
import { AdminConsole } from './components/AdminConsole';
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
          {role === 'passenger' ? (
            <ScreenChooser />
          ) : role === 'retailer' ? (
            <RetailerWorkspace />
          ) : role === 'admin' ? (
            <AdminConsole />
          ) : (
            <>
              <section className="placeholder-panel placeholder-panel--slim">
                <h1>{activeLabel}</h1>
                <p>
                  This persona's dashboard lands in a later ticket. Below is the live passenger-flow
                  read view: zone presence and crowd profiles computed from flights and queues,
                  moving as the shared simulation clock advances.
                </p>
              </section>
              <PresencePanel />
            </>
          )}
        </main>
      </div>
    </ServerStreamProvider>
  );
}
