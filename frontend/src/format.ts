import type { OrderStatus, PaymentStatus } from './types';

const money = new Intl.NumberFormat('mn-MN', { maximumFractionDigits: 0 });

export function formatMoney(amount: number | null | undefined): string {
  return '₮' + money.format(Number(amount ?? 0));
}

/** Server timestamps are naive local times ("2026-09-29T14:03:12"); show them as-is. */
export function parseLocal(value: string): Date {
  const [date, time = '00:00:00'] = value.split('T');
  const [y, m, d] = date.split('-').map(Number);
  const [hh, mm, ss] = time.split(':').map((part) => parseInt(part, 10));
  return new Date(y, m - 1, d, hh || 0, mm || 0, ss || 0);
}

const pad = (n: number) => String(n).padStart(2, '0');

export function formatDateTime(value: string | null | undefined): string {
  if (!value) return '—';
  const d = parseLocal(value);
  return `${d.getFullYear()}.${pad(d.getMonth() + 1)}.${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/** "14:03" today, "09.27" this year, full date otherwise - for compact lists. */
export function formatShort(value: string | null | undefined, now: Date = new Date()): string {
  if (!value) return '';
  const d = parseLocal(value);
  if (d.toDateString() === now.toDateString()) return `${pad(d.getHours())}:${pad(d.getMinutes())}`;
  if (d.getFullYear() === now.getFullYear()) return `${pad(d.getMonth() + 1)}.${pad(d.getDate())}`;
  return `${d.getFullYear()}.${pad(d.getMonth() + 1)}.${pad(d.getDate())}`;
}

export const ORDER_STATUS_LABELS: Record<OrderStatus, string> = {
  PENDING: 'Шинэ',
  CONFIRMED: 'Баталгаажсан',
  DELIVERED: 'Хүргэгдсэн',
  CANCELLED: 'Цуцлагдсан',
};

export const PAYMENT_STATUS_LABELS: Record<PaymentStatus, string> = {
  NOT_REQUESTED: 'Онлайн төлбөргүй',
  PENDING: 'Төлбөр хүлээгдэж буй',
  PAID: 'Төлөгдсөн',
};

export const PLATFORM_LABELS = { FACEBOOK: 'Messenger', INSTAGRAM: 'Instagram' } as const;

export function isFuture(value: string | null | undefined, now: Date = new Date()): boolean {
  return !!value && parseLocal(value).getTime() > now.getTime();
}

/** How long since a moment, for "waiting" labels: "3 мин", "2 ц 5 мин", "3 өдөр". */
export function formatWaiting(since: string, now: Date = new Date()): string {
  const minutes = Math.max(0, Math.floor((now.getTime() - parseLocal(since).getTime()) / 60000));
  if (minutes < 60) return `${minutes} мин`;
  if (minutes < 24 * 60) {
    const rest = minutes % 60;
    return rest ? `${Math.floor(minutes / 60)} ц ${rest} мин` : `${Math.floor(minutes / 60)} ц`;
  }
  return `${Math.floor(minutes / (24 * 60))} өдөр`;
}

const WEEKDAYS = ['Ням', 'Даваа', 'Мягмар', 'Лхагва', 'Пүрэв', 'Баасан', 'Бямба'];

/** "Бямба, 10-р сарын 3" */
export function formatToday(now: Date = new Date()): string {
  return `${WEEKDAYS[now.getDay()]}, ${now.getMonth() + 1}-р сарын ${now.getDate()}`;
}

export function greeting(now: Date = new Date()): string {
  const h = now.getHours();
  if (h >= 5 && h < 12) return 'Өглөөний мэнд';
  if (h >= 12 && h < 18) return 'Өдрийн мэнд';
  return 'Оройн мэнд';
}

/** "Өнөөдөр", "Өчигдөр" or the date - separators between days in a transcript. */
export function formatDay(value: string, now: Date = new Date()): string {
  const d = parseLocal(value);
  const yesterday = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 1);
  if (d.toDateString() === now.toDateString()) return 'Өнөөдөр';
  if (d.toDateString() === yesterday.toDateString()) return 'Өчигдөр';
  return formatDateTime(value).slice(0, 10);
}

/** Up to two initials for an avatar; "#" when the customer has no name yet. */
export function initials(name: string | null | undefined): string {
  const words = (name ?? '').trim().split(/\s+/).filter(Boolean);
  if (words.length === 0) return '#';
  return words.slice(0, 2).map((w) => w[0].toUpperCase()).join('');
}
