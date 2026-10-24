import { useCallback, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { AppHeader } from './components/AppHeader';
import { SimControlBar } from './components/SimControlBar';
import { RoleDashboard } from './components/RoleDashboard';
import { DemoMode } from './components/DemoMode';
import { ServerStreamProvider } from './server/stream';
import { DEFAULT_ROLE, ROLES, themeForRole, type RoleId } from './roles';
import './App.css';

/** In split-screen demo the passenger screen already fills the left pane, so the right defaults to a dashboard. */
const DEMO_DEFAULT_ROLE: RoleId = 'retailer';

/**
 * The app shell. Role, demo mode and the demo's screen live in the URL (?role=…&demo=1&screen=…) so a
 * presenter can bookmark the exact split-screen setup.
 */
export default function App() {
  const [params, setParams] = useSearchParams();
  const demo = params.get('demo') === '1';
  const requested = params.get('role');
  const role: RoleId = ROLES.some((r) => r.id === requested) ? (requested as RoleId) : DEFAULT_ROLE;
  const dashboardRole: RoleId = demo && role === 'passenger' ? DEMO_DEFAULT_ROLE : role;
  const screenParam = params.get('screen');
  const demoScreenId = screenParam ? Number(screenParam) : null;
  const theme = themeForRole(dashboardRole);

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
  }, [theme]);

  const updateParams = useCallback(
    (changes: Record<string, string | null>) =>
      setParams(
        (current) => {
          const next = new URLSearchParams(current);
          Object.entries(changes).forEach(([k, v]) => (v == null ? next.delete(k) : next.set(k, v)));
          return next;
        },
        { replace: true },
      ),
    [setParams],
  );
  const setScreen = useCallback((id: number) => updateParams({ screen: String(id) }), [updateParams]);

  return (
    <ServerStreamProvider>
      <div className={`app-shell${demo ? ' app-shell--demo' : ''}`}>
        <AppHeader
          activeRole={dashboardRole}
          onRoleChange={(r) => updateParams({ role: r })}
          demo={demo}
          onDemoChange={(on) => updateParams({ demo: on ? '1' : null })}
        />
        <SimControlBar focusScreenId={demo ? demoScreenId ?? undefined : undefined} />
        <main className="app-main">
          {demo ? (
            <DemoMode role={dashboardRole} screenId={demoScreenId} onScreenChange={setScreen} />
          ) : (
            <RoleDashboard role={role} />
          )}
        </main>
      </div>
    </ServerStreamProvider>
  );
}
