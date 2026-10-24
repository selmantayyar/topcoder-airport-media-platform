import { useState, type MouseEvent } from 'react';
import { num } from '../format';
import { useI18n } from '../i18n/I18nProvider';
import type { ScreenDot, TerminalMap as Terminal, ZoneMap } from '../server/api';

const WIDTH = 1000;
const TERMINAL_GAP = 28;
const ROW_HEIGHT = 150;
const ZONE_GAP = 10;
const HEADER = 26;
const DOT_R = 5;
const DOT_STEP = 16;

const STATUSES: ScreenDot['status'][] = ['ONLINE', 'DEGRADED', 'OFFLINE'];

type HoverTarget = { kind: 'zone'; zone: ZoneMap } | { kind: 'screen'; screen: ScreenDot; zone: ZoneMap };
type Hover = HoverTarget & { x: number; y: number };

/**
 * A stylised floor plan: each terminal is a block, gate piers along the top row and landside/retail zones
 * below. Zone fill is a single-hue heat scale of live ZonePresence; each screen is a dot in the status
 * palette. Status and heat are never colour-only — the legend names them and hovering shows the values.
 */
export function TerminalMap({ terminals }: { terminals: Terminal[] }) {
  const { t, label } = useI18n();
  const [hover, setHover] = useState<Hover | null>(null);
  const layouts = layout(terminals);
  const height = layouts.reduce((h, t) => Math.max(h, t.bottom), 0) + 4;

  const track = (e: MouseEvent, target: HoverTarget) => {
    const box = (e.currentTarget as SVGElement).ownerSVGElement?.getBoundingClientRect();
    if (!box) return;
    setHover({ ...target, x: e.clientX - box.left, y: e.clientY - box.top });
  };

  return (
    <div className="tmap">
      <svg viewBox={`0 0 ${WIDTH} ${height}`} role="img" aria-label={t('map.aria')}>
        {layouts.map((box) => (
          <g key={box.terminal.id}>
            <text x={0} y={box.top + 16} className="tmap__terminal">{box.terminal.name}</text>
            {box.zones.map((z) => (
              <g key={z.zone.id}>
                <rect
                  x={z.x} y={z.y} width={z.w} height={z.h} rx={8}
                  className="tmap__zone"
                  style={{ fill: heatFill(z.zone.heat) }}
                  onMouseMove={(e) => track(e, { kind: 'zone', zone: z.zone })}
                  onMouseLeave={() => setHover(null)}
                />
                <text x={z.x + 10} y={z.y + 18} className="tmap__zone-name">{z.zone.name}</text>
                <text x={z.x + z.w - 10} y={z.y + 18} className="tmap__zone-count" textAnchor="end">
                  {t('common.pax', { n: num(z.zone.headcount) })}
                </text>
                {z.zone.screens.map((s, i) => {
                  const perRow = Math.max(1, Math.floor((z.w - 20) / DOT_STEP));
                  const cx = z.x + 10 + DOT_R + (i % perRow) * DOT_STEP;
                  const cy = z.y + 36 + DOT_R + Math.floor(i / perRow) * DOT_STEP;
                  return (
                    <circle
                      key={s.id}
                      cx={cx} cy={cy} r={DOT_R}
                      className={`tmap__dot tmap__dot--${s.status.toLowerCase()}`}
                      onMouseMove={(e) => track(e, { kind: 'screen', screen: s, zone: z.zone })}
                      onMouseLeave={() => setHover(null)}
                    />
                  );
                })}
              </g>
            ))}
          </g>
        ))}
      </svg>

      {hover && (
        <div className="tmap__tip" style={{ left: hover.x + 14, top: hover.y + 14 }}>
          {hover.kind === 'zone' ? (
            <>
              <strong>{hover.zone.name}</strong>
              <span>{t('map.paxNow', { n: num(hover.zone.headcount) })}</span>
              <span>{t('map.screens', { n: hover.zone.screens.length, offline: count(hover.zone, 'OFFLINE') })}</span>
            </>
          ) : (
            <>
              <strong>{hover.screen.code}</strong>
              <span className={`tmap__status tmap__status--${hover.screen.status.toLowerCase()}`}>
                {label('screenStatus', hover.screen.status)}
              </span>
              <span>{t('map.latency', { n: hover.screen.latencyMs })}</span>
              <span>{hover.zone.name}</span>
            </>
          )}
        </div>
      )}

      <div className="tmap__legend" aria-label={t('map.legend')}>
        {STATUSES.map((s) => (
          <span key={s} className="tmap__key">
            <svg width="12" height="12" aria-hidden><circle cx="6" cy="6" r="5" className={`tmap__dot tmap__dot--${s.toLowerCase()}`} /></svg>
            {label('screenStatus', s)}
          </span>
        ))}
        <span className="tmap__key tmap__key--heat">
          {t('map.quiet')}
          <span className="tmap__ramp" aria-hidden />
          {t('map.busiest')}
        </span>
      </div>
    </div>
  );
}

interface ZoneBox { zone: ZoneMap; x: number; y: number; w: number; h: number }
interface TerminalBox { terminal: Terminal; top: number; bottom: number; zones: ZoneBox[] }

/** Gate areas on the top row, everything else below; zones share each row's width evenly. */
function layout(terminals: Terminal[]): TerminalBox[] {
  let top = 0;
  return terminals.map((terminal) => {
    const rows = [
      terminal.zones.filter((z) => z.kind === 'GATE_AREA'),
      terminal.zones.filter((z) => z.kind !== 'GATE_AREA'),
    ].filter((r) => r.length > 0);
    const zones: ZoneBox[] = [];
    rows.forEach((row, r) => {
      const w = (WIDTH - ZONE_GAP * (row.length - 1)) / row.length;
      row.forEach((zone, i) => {
        zones.push({ zone, x: i * (w + ZONE_GAP), y: top + HEADER + r * (ROW_HEIGHT + ZONE_GAP), w, h: ROW_HEIGHT });
      });
    });
    const box = { terminal, top, bottom: top + HEADER + rows.length * (ROW_HEIGHT + ZONE_GAP), zones };
    top = box.bottom + TERMINAL_GAP;
    return box;
  });
}

/** Sequential single-hue heat: transparent when empty, saturating as the zone fills. */
function heatFill(heat: number): string {
  return `color-mix(in srgb, var(--heat) ${Math.round(8 + heat * 62)}%, transparent)`;
}

function count(zone: ZoneMap, status: ScreenDot['status']): number {
  return zone.screens.filter((s) => s.status === status).length;
}
