import { describe, expect, it } from 'vitest';
import { formatDateTime, formatDay, formatMoney, formatShort, formatToday, formatWaiting, greeting, initials, isFuture, parseLocal } from './format';
import { lastDays, niceMax } from './components/DailyChart';

describe('format', () => {
  it('says how long a customer has waited', () => {
    const now = new Date(2026, 9, 3, 16, 40);
    expect(formatWaiting('2026-10-03T16:26:30', now)).toBe('13 мин');
    expect(formatWaiting('2026-10-03T14:35:00', now)).toBe('2 ц 5 мин');
    expect(formatWaiting('2026-10-03T14:40:00', now)).toBe('2 ц');
    expect(formatWaiting('2026-09-30T10:00:00', now)).toBe('3 өдөр');
    expect(formatWaiting('2026-10-03T16:45:00', now)).toBe('0 мин');
  });

  it('names the day and greets by time of day', () => {
    const now = new Date(2026, 9, 3, 9, 0);
    expect(formatToday(now)).toBe('Бямба, 10-р сарын 3');
    expect(greeting(now)).toBe('Өглөөний мэнд');
    expect(greeting(new Date(2026, 9, 3, 13, 0))).toBe('Өдрийн мэнд');
    expect(greeting(new Date(2026, 9, 3, 23, 0))).toBe('Оройн мэнд');
    expect(formatDay('2026-10-03T08:00:00', now)).toBe('Өнөөдөр');
    expect(formatDay('2026-10-02T08:00:00', now)).toBe('Өчигдөр');
    expect(formatDay('2026-09-20T08:00:00', now)).toBe('2026.09.20');
  });

  it('makes avatar initials', () => {
    expect(initials('Болормаа Ганбаатар')).toBe('БГ');
    expect(initials('  тэмүүлэн ')).toBe('Т');
    expect(initials(null)).toBe('#');
  });

  it('formats tugrik amounts without decimals', () => {
    expect(formatMoney(20000)).toMatch(/^₮20[\s,.  ]?000$/);
    expect(formatMoney(null)).toBe('₮0');
  });

  it('reads server timestamps as local wall-clock time', () => {
    const d = parseLocal('2026-09-29T14:03:12.123456');
    expect([d.getFullYear(), d.getMonth(), d.getDate(), d.getHours(), d.getMinutes()]).toEqual([2026, 8, 29, 14, 3]);
    expect(formatDateTime('2026-09-29T14:03:12')).toBe('2026.09.29 14:03');
    expect(formatDateTime(null)).toBe('—');
  });

  it('shortens timestamps relative to now', () => {
    const now = new Date(2026, 8, 29, 18, 0);
    expect(formatShort('2026-09-29T09:05:00', now)).toBe('09:05');
    expect(formatShort('2026-03-02T09:05:00', now)).toBe('03.02');
    expect(formatShort('2025-12-31T09:05:00', now)).toBe('2025.12.31');
  });

  it('knows whether a pause is still running', () => {
    const now = new Date(2026, 8, 29, 18, 0);
    expect(isFuture('2026-09-29T19:00:00', now)).toBe(true);
    expect(isFuture('2026-09-29T17:00:00', now)).toBe(false);
    expect(isFuture(null, now)).toBe(false);
  });
});

describe('daily chart helpers', () => {
  it('picks clean axis maxima with whole midpoints', () => {
    expect(niceMax(0)).toBe(2);
    expect(niceMax(3)).toBe(4);
    expect(niceMax(7)).toBe(10);
    expect(niceMax(13)).toBe(20);
    expect(niceMax(45)).toBe(50);
    for (const v of [1, 5, 9, 11, 37, 99, 101, 640]) {
      const top = niceMax(v);
      expect(top).toBeGreaterThanOrEqual(v);
      expect(Number.isInteger(top / 2)).toBe(true);
    }
  });

  it('lists every day of the range, oldest first, across month ends', () => {
    expect(lastDays(3, new Date(2026, 9, 1))).toEqual(['2026-09-29', '2026-09-30', '2026-10-01']);
  });
});
