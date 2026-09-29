import { useEffect, useRef, type ReactNode } from 'react';
import type { OrderStatus, PaymentStatus } from '../types';
import { ORDER_STATUS_LABELS, PAYMENT_STATUS_LABELS } from '../format';

export function Spinner({ label = 'Ачаалж байна…' }: { label?: string }) {
  return (
    <div className="spinner-wrap" role="status">
      <span className="spinner" aria-hidden="true" />
      <span className="muted">{label}</span>
    </div>
  );
}

export function ErrorBox({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="error-box" role="alert">
      <span>{message}</span>
      {onRetry && <button className="btn btn-small" onClick={onRetry}>Дахин оролдох</button>}
    </div>
  );
}

export function Empty({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="empty">
      <strong>{title}</strong>
      {children && <div className="muted">{children}</div>}
    </div>
  );
}

export function PageHeader({ title, actions, children }: { title: string; actions?: ReactNode; children?: ReactNode }) {
  return (
    <header className="page-header">
      <div>
        <h1>{title}</h1>
        {children && <p className="muted">{children}</p>}
      </div>
      {actions && <div className="page-actions">{actions}</div>}
    </header>
  );
}

const ORDER_TONES: Record<OrderStatus, string> = {
  PENDING: 'info', CONFIRMED: 'accent', DELIVERED: 'success', CANCELLED: 'neutral',
};
const PAYMENT_TONES: Record<PaymentStatus, string> = {
  NOT_REQUESTED: 'neutral', PENDING: 'warning', PAID: 'success',
};

export function OrderStatusBadge({ status }: { status: OrderStatus }) {
  return <span className={`badge badge-${ORDER_TONES[status]}`}>{ORDER_STATUS_LABELS[status]}</span>;
}

export function PaymentBadge({ status }: { status: PaymentStatus }) {
  return <span className={`badge badge-${PAYMENT_TONES[status]}`}>{PAYMENT_STATUS_LABELS[status]}</span>;
}

export function Modal({ title, onClose, children, footer, wide }: {
  title: string; onClose: () => void; children: ReactNode; footer?: ReactNode; wide?: boolean;
}) {
  const dialog = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    document.addEventListener('keydown', onKey);
    // Focus the first field so typing works at once; without fields, the dialog itself
    const first = dialog.current?.querySelector<HTMLElement>('.modal-body input, .modal-body select, .modal-body textarea');
    (first ?? dialog.current)?.focus();
    return () => document.removeEventListener('keydown', onKey);
  }, [onClose]);
  return (
    <div className="modal-backdrop" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div className={`modal${wide ? ' modal-wide' : ''}`} role="dialog" aria-modal="true" aria-label={title} ref={dialog} tabIndex={-1}>
        <div className="modal-head">
          <h2>{title}</h2>
          <button className="icon-btn" onClick={onClose} aria-label="Хаах">✕</button>
        </div>
        <div className="modal-body">{children}</div>
        {footer && <div className="modal-foot">{footer}</div>}
      </div>
    </div>
  );
}

export function Field({ label, hint, children }: { label: string; hint?: ReactNode; children: ReactNode }) {
  return (
    <label className="field">
      <span className="field-label">{label}</span>
      {children}
      {hint && <span className="field-hint">{hint}</span>}
    </label>
  );
}

/** Copies text, falling back to selecting it where the clipboard API is unavailable (http). */
export async function copyText(text: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    return false;
  }
}
