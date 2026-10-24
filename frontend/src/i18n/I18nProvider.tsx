import { createContext, useCallback, useContext, useEffect, useMemo, type ReactNode } from 'react';
import { useSearchParams } from 'react-router-dom';
import { setFormatLocale } from '../format';
import { en, es, type MessageKey } from './messages';

export const LANGUAGES = ['en', 'es'] as const;
export type Lang = (typeof LANGUAGES)[number];

type Vars = Record<string, string | number>;

interface I18n {
  lang: Lang;
  setLang: (lang: Lang) => void;
  /** A UI string with `{name}` placeholders filled. */
  t: (key: MessageKey, vars?: Vars) => string;
  /** A UI string whose placeholders are elements (e.g. a bolded number), for word orders that differ by language. */
  rich: (key: MessageKey, parts: Record<string, ReactNode>) => ReactNode[];
  /** A server enum or label shown to people: `group.value` from the catalog, else `fallback`, else the value made readable. */
  label: (group: string, value: string, fallback?: string) => string;
}

const CATALOGS: Record<Lang, Record<MessageKey, string>> = { en, es };
const STORAGE_KEY = 'amp.lang';

const I18nContext = createContext<I18n | null>(null);

function isLang(value: string | null): value is Lang {
  return value != null && (LANGUAGES as readonly string[]).includes(value);
}

function storedLang(): Lang | null {
  try {
    const value = localStorage.getItem(STORAGE_KEY);
    return isLang(value) ? value : null;
  } catch {
    return null;
  }
}

function fill(template: string, vars?: Vars): string {
  return vars ? template.replace(/\{(\w+)\}/g, (m, k: string) => (k in vars ? String(vars[k]) : m)) : template;
}

function readable(value: string): string {
  return value ? value.charAt(0) + value.slice(1).toLowerCase().replace(/_/g, ' ') : value;
}

/**
 * The UI language. `?lang=es` in the URL wins (so a kiosk or a QR link can pin it), then the viewer's last
 * choice, then English. Switching rewrites `?lang` and remembers the choice; every role, the passenger screen
 * and the claim page read the same value.
 */
export function I18nProvider({ children }: { children: ReactNode }) {
  const [params, setParams] = useSearchParams();
  const fromUrl = params.get('lang');
  const lang: Lang = isLang(fromUrl) ? fromUrl : storedLang() ?? 'en';
  setFormatLocale(lang);

  useEffect(() => {
    document.documentElement.lang = lang;
  }, [lang]);

  const setLang = useCallback(
    (next: Lang) => {
      try {
        localStorage.setItem(STORAGE_KEY, next);
      } catch {
        // Private mode or blocked storage: the URL still carries the choice.
      }
      setParams(
        (current) => {
          const updated = new URLSearchParams(current);
          updated.set('lang', next);
          return updated;
        },
        { replace: true },
      );
    },
    [setParams],
  );

  const value = useMemo<I18n>(() => {
    const catalog = CATALOGS[lang];
    const lookup = catalog as Record<string, string>;
    return {
      lang,
      setLang,
      t: (key, vars) => fill(catalog[key], vars),
      rich: (key, parts) =>
        catalog[key].split(/(\{\w+\})/).map((piece) => {
          const name = /^\{(\w+)\}$/.exec(piece)?.[1];
          return name && name in parts ? parts[name] : piece;
        }),
      label: (group, value, fallback) => lookup[`${group}.${value}`] ?? fallback ?? readable(value),
    };
  }, [lang, setLang]);

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}

export function useI18n(): I18n {
  const ctx = useContext(I18nContext);
  if (!ctx) throw new Error('useI18n must be used inside <I18nProvider>');
  return ctx;
}

/** EN | ES toggle. Each option names its language in that language, so it's findable from either. */
export function LanguageSwitch({ className = '' }: { className?: string }) {
  const { lang, setLang, t } = useI18n();
  return (
    <div className={`lang-switch ${className}`} role="group" aria-label={t('lang.label')}>
      {LANGUAGES.map((l) => (
        <button
          key={l}
          type="button"
          lang={l}
          className={l === lang ? 'lang-switch__btn is-active' : 'lang-switch__btn'}
          aria-pressed={l === lang}
          title={l === 'en' ? 'English' : 'Español'}
          onClick={() => setLang(l)}
        >
          {l.toUpperCase()}
        </button>
      ))}
    </div>
  );
}
