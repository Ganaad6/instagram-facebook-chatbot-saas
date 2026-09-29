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
