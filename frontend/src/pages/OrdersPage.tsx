import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { ChevronLeft, ChevronRight, Download, MessageCircle, X } from 'lucide-react';
import { api, query } from '../api';
import { useMe } from '../auth';
import { useResource } from '../hooks';
import { useToast } from '../components/Toast';
import { Empty, ErrorBox, OrderStatusBadge, PageHeader, PaymentBadge, Spinner } from '../components/ui';
import { formatDateTime, formatMoney, formatShort, ORDER_STATUS_LABELS, PLATFORM_LABELS } from '../format';
import type { Order, OrderStatus, Page } from '../types';

const FILTERS: (OrderStatus | '')[] = ['', 'PENDING', 'CONFIRMED', 'DELIVERED', 'CANCELLED'];
const PAGE_SIZE = 20;

export function OrdersPage() {
  const { business } = useMe();
  const { id } = useParams();
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const status = (params.get('status') ?? '') as OrderStatus | '';
  const page = Number(params.get('page') ?? 0);
  const base = `/api/businesses/${business.id}/orders`;
  const orders = useResource<Page<Order>>(`${base}${query({ status, page, size: PAGE_SIZE })}`, 20000);

  const setFilter = (next: Record<string, string>) => {
    const merged = new URLSearchParams(params);
    for (const [k, v] of Object.entries(next)) {
      if (v) merged.set(k, v); else merged.delete(k);
    }
    setParams(merged);
  };

  const selectedId = id ? Number(id) : null;
  const open = (orderId: number) => navigate(`/orders/${orderId}${window.location.search}`);

  return (
    <div className="orders-layout">
      <div className="orders-main">
        <PageHeader title="Захиалга" actions={
          <a className="btn" href={`${base}/export?format=csv`} download><Download size={16} aria-hidden="true" /> CSV татах</a>
        } />
        <div className="pills" role="tablist" aria-label="Төлөв">
          {FILTERS.map((f) => (
            <button key={f || 'all'} role="tab" aria-selected={status === f}
                    className={`pill${status === f ? ' active' : ''}`} onClick={() => setFilter({ status: f, page: '' })}>
              {f ? ORDER_STATUS_LABELS[f] : 'Бүгд'}
            </button>
          ))}
        </div>

        <section className="card flush">
          {orders.error && <ErrorBox message={orders.error} onRetry={orders.reload} />}
          {!orders.data && !orders.error && <Spinner />}
          {orders.data && orders.data.content.length === 0 && (
            <Empty title={status ? 'Энэ төлөвтэй захиалга алга' : 'Захиалга ирээгүй байна'} />
          )}
          {orders.data && orders.data.content.length > 0 && (
            <>
              <div className="table-wrap">
                <table className="table orders-table">
                  <thead>
                    <tr><th>#</th><th>Бараа</th><th>Хэрэглэгч</th><th className="num">Дүн</th><th>Төлөв</th><th className="num">Огноо</th></tr>
                  </thead>
                  <tbody>
                    {orders.data.content.map((o) => (
                      <tr key={o.id} className={`clickable${o.id === selectedId ? ' selected' : ''}`} onClick={() => open(o.id)}>
                        <td data-label="#"><Link to={`/orders/${o.id}${window.location.search}`} onClick={(e) => e.stopPropagation()}><strong>#{o.id}</strong></Link></td>
                        <td data-label="Бараа">
                          <strong>{o.productName} × {o.quantity}</strong>
                          <div className={`platform platform-${o.platform}`}>{PLATFORM_LABELS[o.platform]}</div>
                        </td>
                        <td data-label="Хэрэглэгч">{o.customerName}<div className="sub">{o.phone}</div></td>
                        <td data-label="Дүн" className="num">{formatMoney(o.totalAmount)}</td>
                        <td data-label="Төлөв"><span className="badges"><OrderStatusBadge status={o.status} />{o.paymentStatus !== 'NOT_REQUESTED' && <PaymentBadge status={o.paymentStatus} />}</span></td>
                        <td data-label="Огноо" className="muted num" style={{ fontWeight: 500 }}>{formatShort(o.createdAt)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              {orders.data.totalPages > 1 && (
                <div className="pager">
                  <button className="btn btn-small" disabled={page === 0} onClick={() => setFilter({ page: String(page - 1) })}><ChevronLeft size={16} aria-hidden="true" /> Өмнөх</button>
                  <span className="muted">{page + 1} / {orders.data.totalPages}</span>
                  <button className="btn btn-small" disabled={page + 1 >= orders.data.totalPages} onClick={() => setFilter({ page: String(page + 1) })}>Дараах <ChevronRight size={16} aria-hidden="true" /></button>
                </div>
              )}
            </>
          )}
        </section>
      </div>

      {selectedId && <OrderDetail key={selectedId} businessId={business.id} orderId={selectedId} onChanged={orders.reload}
                                  onClose={() => navigate(`/orders${window.location.search}`)} />}
    </div>
  );
}

const NEXT_ACTIONS: Record<OrderStatus, { status: OrderStatus; label: string; primary?: boolean }[]> = {
  PENDING: [{ status: 'CONFIRMED', label: 'Баталгаажуулах', primary: true }, { status: 'CANCELLED', label: 'Цуцлах' }],
  CONFIRMED: [{ status: 'DELIVERED', label: 'Хүргэгдсэн', primary: true }, { status: 'CANCELLED', label: 'Цуцлах' }],
  DELIVERED: [],
  CANCELLED: [{ status: 'PENDING', label: 'Сэргээх' }],
};

function OrderDetail({ businessId, orderId, onClose, onChanged }: {
  businessId: number; orderId: number; onClose: () => void; onChanged: () => void;
}) {
  const toast = useToast();
  const order = useResource<Order>(`/api/businesses/${businessId}/orders/${orderId}`);
  const [busy, setBusy] = useState(false);
  const [confirmCancel, setConfirmCancel] = useState(false);

  const act = async (action: () => Promise<Order>, success: string) => {
    setBusy(true);
    try {
      await action();
      toast(success);
      await order.reload();
      onChanged();
    } catch (e) {
      toast(e instanceof Error ? e.message : String(e), 'error');
    } finally {
      setBusy(false);
      setConfirmCancel(false);
    }
  };

  const setStatus = (status: OrderStatus) => act(
    () => api.put<Order>(`/api/businesses/${businessId}/orders/${orderId}/status`, { status }),
    `Төлөв: ${ORDER_STATUS_LABELS[status]}`);

  const checkPayment = () => act(async () => {
    const updated = await api.post<Order>(`/api/businesses/${businessId}/orders/${orderId}/payment/check`);
    if (updated.paymentStatus !== 'PAID') throw new Error('Төлбөр одоогоор төлөгдөөгүй байна');
    return updated;
  }, 'Төлбөр төлөгдсөн байна');

  const panel = useRef<HTMLElement>(null);
  // Esc closes; focus moves into the panel so keyboard users land on the order they opened
  const close = useRef(onClose);
  close.current = onClose;
  useEffect(() => {
    panel.current?.focus({ preventScroll: true });
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') close.current(); };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, []);

  const o = order.data;
  const actions = o ? NEXT_ACTIONS[o.status] : [];
  const primary = actions.find((a) => a.primary);
  const cancel = actions.find((a) => a.status === 'CANCELLED');
  const others = actions.filter((a) => a !== primary && a !== cancel);

  return (
    <>
      <div className="order-panel-backdrop" onClick={onClose} aria-hidden="true" />
      <aside className="order-panel" aria-label={`Захиалга #${orderId}`} ref={panel} tabIndex={-1}>
        <div className="order-panel-head">
          <h2>Захиалга #{orderId}</h2>
          <button className="icon-btn" onClick={onClose} aria-label="Хаах"><X size={18} /></button>
        </div>
        {order.error && <ErrorBox message={order.error} />}
        {!o && !order.error && <Spinner />}
        {o && (
          <>
            <div className="badges"><OrderStatusBadge status={o.status} /><PaymentBadge status={o.paymentStatus} /></div>
            <div className="receipt">
              <div className="receipt-line"><span>{o.productName}</span><span className="muted">{formatMoney(o.unitPrice)} × {o.quantity}</span></div>
              <div className="receipt-line receipt-total"><span>Нийт</span><span>{formatMoney(o.totalAmount)}</span></div>
            </div>
            <dl className="details small">
              <dt>Нэр</dt><dd><strong>{o.customerName}</strong></dd>
              <dt>Утас</dt><dd>{o.phone ? <a href={`tel:${o.phone}`}>{o.phone}</a> : '—'}</dd>
              <dt>Хаяг</dt><dd className="pre">{o.address || '—'}</dd>
              <dt>Суваг</dt><dd>{PLATFORM_LABELS[o.platform]}</dd>
              {o.notes && <><dt>Тэмдэглэл</dt><dd className="pre">{o.notes}</dd></>}
              <dt>Огноо</dt><dd>{formatDateTime(o.createdAt)}</dd>
              {o.paidAt && <><dt>Төлсөн</dt><dd>{formatDateTime(o.paidAt)}</dd></>}
            </dl>
            {o.paymentStatus === 'PENDING' && (
              <div className="note">
                <span>
                  Хэрэглэгчид QPay-ийн холбоос илгээсэн, төлбөр хүлээгдэж байна.{' '}
                  <button className="link-btn" disabled={busy} onClick={() => void checkPayment()}>Төлбөр шалгах</button>
                </span>
              </div>
            )}
            {o.paymentStatus === 'PAID' && o.status === 'CANCELLED' && (
              <div className="note warning">Төлбөр төлөгдсөн захиалга цуцлагдсан тул QPay-ээр буцаан олголт хийнэ үү.</div>
            )}
            {confirmCancel && (
              <div className="confirm">
                <p>Захиалгыг цуцлах уу?{o.paymentStatus === 'PENDING' && ' Төлөгдөөгүй QPay нэхэмжлэх хүчингүй болно.'}</p>
                <div className="row">
                  <button className="btn btn-danger" disabled={busy} onClick={() => void setStatus('CANCELLED')}>Тийм, цуцлах</button>
                  <button className="btn" onClick={() => setConfirmCancel(false)}>Болих</button>
                </div>
              </div>
            )}
            <div className="order-actions">
              {primary && (
                <button className="btn btn-primary btn-block" disabled={busy} onClick={() => void setStatus(primary.status)}>{primary.label}</button>
              )}
              {others.map((a) => (
                <button key={a.status} className="btn btn-block" disabled={busy} onClick={() => void setStatus(a.status)}>{a.label}</button>
              ))}
              <div className="row">
                <Link className="btn" to={`/chats/${o.customerId}`}><MessageCircle size={16} aria-hidden="true" /> Чат нээх</Link>
                {cancel && (
                  <button className="btn btn-danger-ghost" disabled={busy || confirmCancel} onClick={() => setConfirmCancel(true)}>{cancel.label}</button>
                )}
              </div>
            </div>
          </>
        )}
      </aside>
    </>
  );
}
