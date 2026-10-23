import { ROLES, type RoleId } from '../roles';
import { useClock, type StreamStatus } from '../server/stream';

interface AppHeaderProps {
  activeRole: RoleId;
  onRoleChange: (role: RoleId) => void;
}

const STATUS_LABEL: Record<StreamStatus, string> = {
  connecting: 'Connecting…',
  live: 'Live',
  error: 'Disconnected',
};

export function AppHeader({ activeRole, onRoleChange }: AppHeaderProps) {
  const { clock, status } = useClock();

  const simTime = clock ? new Date(clock.simTime).toLocaleTimeString('en-GB', { timeZone: 'UTC' }) : '--:--:--';

  return (
    <header className="app-header">
      <div className="app-header__brand">Airport Media Platform</div>

      <nav className="role-switcher" aria-label="Role">
        {ROLES.map((role) => (
          <button
            key={role.id}
            type="button"
            className={role.id === activeRole ? 'role-switcher__btn is-active' : 'role-switcher__btn'}
            aria-pressed={role.id === activeRole}
            onClick={() => onRoleChange(role.id)}
          >
            {role.label}
          </button>
        ))}
      </nav>

      <div className={`live-clock live-clock--${status}`} aria-live="polite">
        <span className="live-clock__dot" />
        <span className="live-clock__status">{STATUS_LABEL[status]}</span>
        <span className="live-clock__value">{simTime}</span>
        {clock && <span className="live-clock__speed">{clock.running ? `${clock.speed}x` : 'paused'}</span>}
      </div>
    </header>
  );
}
