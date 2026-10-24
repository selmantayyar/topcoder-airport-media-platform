import { BRAND } from '../brand';
import { hhmmss } from '../format';
import { LanguageSwitch, useI18n } from '../i18n/I18nProvider';
import { ROLES, type RoleId } from '../roles';
import { useClock } from '../server/stream';
import { BrandLogo } from './BrandLogo';

interface AppHeaderProps {
  activeRole: RoleId;
  onRoleChange: (role: RoleId) => void;
  /** Split-screen demo: the role buttons pick the right-hand dashboard. */
  demo: boolean;
  onDemoChange: (demo: boolean) => void;
}

export function AppHeader({ activeRole, onRoleChange, demo, onDemoChange }: AppHeaderProps) {
  const { clock, status } = useClock();
  const { t } = useI18n();

  const simTime = clock ? hhmmss(clock.simTime) : '--:--:--';

  return (
    <header className="app-header">
      <div className="app-header__brand">
        <BrandLogo />
        <span>{BRAND.productName}</span>
      </div>

      <nav className="role-switcher" aria-label={t('header.roles')}>
        {ROLES.filter((role) => !demo || role.id !== 'passenger').map((role) => (
          <button
            key={role.id}
            type="button"
            className={role.id === activeRole ? 'role-switcher__btn is-active' : 'role-switcher__btn'}
            aria-pressed={role.id === activeRole}
            onClick={() => onRoleChange(role.id)}
          >
            {t(`role.${role.id}`)}
          </button>
        ))}
      </nav>

      <button
        type="button"
        className={demo ? 'demo-toggle is-active' : 'demo-toggle'}
        aria-pressed={demo}
        onClick={() => onDemoChange(!demo)}
      >
        {demo ? t('header.demoOff') : t('header.demoOn')}
      </button>

      <LanguageSwitch />

      <div className={`live-clock live-clock--${status}`} aria-live="polite">
        <span className="live-clock__dot" />
        <span className="live-clock__status">{t(`stream.${status}`)}</span>
        <span className="live-clock__value">{simTime}</span>
        {clock && <span className="live-clock__speed">{clock.running ? `${clock.speed}x` : t('clock.paused')}</span>}
      </div>
    </header>
  );
}
