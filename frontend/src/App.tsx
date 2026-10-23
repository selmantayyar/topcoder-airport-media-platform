import { useEffect, useState } from 'react';
import { AppHeader } from './components/AppHeader';
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
    <div className="app-shell">
      <AppHeader activeRole={role} onRoleChange={setRole} />
      <main className="app-main">
        <section className="placeholder-panel">
          <h1>{activeLabel}</h1>
          <p>
            This persona's dashboard lands in a later ticket. The header clock above is
            driven live by the backend over Server-Sent Events — the real-time channel the
            rest of the platform is built on.
          </p>
        </section>
      </main>
    </div>
  );
}
