import { useState } from 'react';
import type { DailyCount } from '../types';

/**
 * Orders per day as a single-series column chart. Every day in the range gets a column
 * (days without orders are zero, not skipped). Hovering or focusing a column shows its
 * value; a table view carries the same numbers for screen readers.
 */
export function DailyChart({ days, data }: { days: string[]; data: DailyCount[] }) {
  const [hover, setHover] = useState<number | null>(null);
  const [showTable, setShowTable] = useState(false);
  const counts = days.map((day) => data.find((d) => d.day === day)?.count ?? 0);
  const max = Math.max(...counts, 0);
  const top = niceMax(max);
  const ticks = [0, top / 2, top];
  const height = 160;

  return (
    <div className="chart">
      <div className="chart-head">
        <h2>Сүүлийн {days.length} хоногийн захиалга</h2>
        <button className="btn btn-small btn-ghost" onClick={() => setShowTable(!showTable)}>
          {showTable ? 'График' : 'Хүснэгт'}
        </button>
      </div>
      {showTable ? (
        <table className="table compact">
          <thead><tr><th>Өдөр</th><th className="num">Захиалга</th></tr></thead>
          <tbody>
            {days.map((day, i) => <tr key={day}><td>{day}</td><td className="num">{counts[i]}</td></tr>)}
          </tbody>
        </table>
      ) : (
        <div className="chart-plot" style={{ height: height + 28 }}>
          <div className="chart-axis" aria-hidden="true">
            {ticks.slice().reverse().map((t) => <span key={t}>{t}</span>)}
          </div>
          <div className="chart-area" style={{ height }} role="img"
               aria-label={`Сүүлийн ${days.length} хоногт нийт ${counts.reduce((a, b) => a + b, 0)} захиалга`}>
            {ticks.map((t) => (
              <div key={t} className="chart-grid" style={{ bottom: top ? (t / top) * height : 0 }} />
            ))}
            <div className="chart-columns">
              {days.map((day, i) => (
                <div key={day} className="chart-slot" tabIndex={0}
                     onMouseEnter={() => setHover(i)} onMouseLeave={() => setHover(null)}
                     onFocus={() => setHover(i)} onBlur={() => setHover(null)}
                     aria-label={`${day}: ${counts[i]} захиалга`}>
                  <div className="chart-bar" style={{ height: top ? Math.max((counts[i] / top) * height, counts[i] ? 3 : 0) : 0 }} />
                  {hover === i && (
                    <div className="chart-tooltip" style={{ bottom: (top ? (counts[i] / top) * height : 0) + 8 }}>
                      <strong>{counts[i]}</strong> захиалга<br /><span className="muted">{day}</span>
                    </div>
                  )}
                </div>
              ))}
            </div>
            <div className="chart-labels" aria-hidden="true">
              {days.map((day, i) => (
                <span key={day}>{i % 2 === (days.length - 1) % 2 ? day.slice(5).replace('-', '.') : ''}</span>
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

/** A clean axis maximum with a whole-number midpoint: 0 → 2, 3 → 4, 7 → 10, 13 → 20. */
export function niceMax(value: number): number {
  if (value <= 2) return 2;
  const magnitude = Math.pow(10, Math.floor(Math.log10(value)));
  for (const step of [1, 2, 4, 5, 10]) {
    const candidate = step * magnitude;
    if (candidate >= value && (candidate / 2) % 1 === 0) return candidate;
  }
  return 10 * magnitude;
}

/** The last n calendar days (local), oldest first, as YYYY-MM-DD. */
export function lastDays(n: number, today: Date = new Date()): string[] {
  const out: string[] = [];
  for (let i = n - 1; i >= 0; i--) {
    const d = new Date(today.getFullYear(), today.getMonth(), today.getDate() - i);
    out.push(`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`);
  }
  return out;
}
