import { useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { api, query } from '../api';
import { useMe } from '../auth';
import { useResource } from '../hooks';
import { useToast } from '../components/Toast';
import { Empty, ErrorBox, Modal, OrderStatusBadge, PageHeader, PaymentBadge, Spinner } from '../components/ui';
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

  return (
    <>
      <PageHeader title="Захиалга" actions={
        <a className="btn" href={`${base}/export?format=csv`} download>CSV татах</a>
      } />
      <div className="tabs" role="tablist">
        {FILTERS.map((f) => (
          <button key={f || 'all'} role="tab" aria-selected={status === f}
                  className={`tab${status === f ? ' active' : ''}`} onClick={() => setFilter({ status: f, page: '' })}>
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
            <table className="table orders-table">
              <thead>
                <tr><th>#</th><th>Бараа</th><th>Хэрэглэгч</th><th className="num">Дүн</th><th>Төлөв</th><th>Огноо</th></tr>
              </thead>
              <tbody>
                {orders.data.content.map((o) => (
                  <tr key={o.id} className="clickable" onClick={() => navigate(`/orders/${o.id}${window.location.search}`)}>
                    <td data-label="#"><Link to={`/orders/${o.id}${window.location.search}`} onClick={(e) => e.stopPropagation()}>#{o.id}</Link></td>
                    <td data-label="Бараа">{o.productName} × {o.quantity}</td>
                    <td data-label="Хэрэглэгч">{o.customerName}<div className="muted small">{o.phone}</div></td>
                    <td data-label="Дүн" className="num">{formatMoney(o.totalAmount)}</td>
                    <td data-label="Төлөв"><span className="badges"><OrderStatusBadge status={o.status} />{o.paymentStatus !== 'NOT_REQUESTED' && <PaymentBadge status={o.paymentStatus} />}</span></td>
                    <td data-label="Огноо" className="muted">{formatShort(o.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            {orders.data.totalPages > 1 && (
              <div className="pager">
                <button className="btn btn-small" disabled={page === 0} onClick={() => setFilter({ page: String(page - 1) })}>← Өмнөх</button>
                <span className="muted">{page + 1} / {orders.data.totalPages}</span>
                <button className="btn btn-small" disabled={page + 1 >= orders.data.totalPages} onClick={() => setFilter({ page: String(page + 1) })}>Дараах →</button>
              </div>
            )}
          </>
        )}
      </section>

      {id && <OrderDetail businessId={business.id} orderId={Number(id)} onChanged={orders.reload}
                          onClose={() => navigate(`/orders${window.location.search}`)} />}
    </>
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

  const o = order.data;
  return (
    <Modal title={`Захиалга #${orderId}`} onClose={onClose} footer={o && (
      <>
        {NEXT_ACTIONS[o.status].map((a) => (
          <button key={a.status} disabled={busy}
                  className={`btn${a.primary ? ' btn-primary' : ''}${a.status === 'CANCELLED' ? ' btn-danger-ghost' : ''}`}
                  onClick={() => a.status === 'CANCELLED' ? setConfirmCancel(true) : void setStatus(a.status)}>
            {a.label}
          </button>
        ))}
      </>
    )}>
      {order.error && <ErrorBox message={order.error} />}
      {!o && !order.error && <Spinner />}
      {o && (
        <div className="stack">
          <div className="badges"><OrderStatusBadge status={o.status} /><PaymentBadge status={o.paymentStatus} /></div>
          <dl className="details">
            <dt>Бараа</dt><dd>{o.productName} × {o.quantity} <span className="muted">({formatMoney(o.unitPrice)})</span></dd>
            <dt>Нийт дүн</dt><dd><strong>{formatMoney(o.totalAmount)}</strong></dd>
            <dt>Нэр</dt><dd>{o.customerName}</dd>
            <dt>Утас</dt><dd>{o.phone ? <a href={`tel:${o.phone}`}>{o.phone}</a> : '—'}</dd>
            <dt>Хаяг</dt><dd className="pre">{o.address}</dd>
            <dt>Суваг</dt><dd>{PLATFORM_LABELS[o.platform]} · <Link to={`/chats/${o.customerId}`}>Чат харах</Link></dd>
            <dt>Огноо</dt><dd>{formatDateTime(o.createdAt)}</dd>
            {o.paidAt && <><dt>Төлсөн</dt><dd>{formatDateTime(o.paidAt)}</dd></>}
          </dl>
          {o.paymentStatus === 'PENDING' && (
            <div className="note">
              Хэрэглэгчид QPay-ийн холбоос илгээсэн, төлбөр хүлээгдэж байна.{' '}
              <button className="btn btn-small" disabled={busy} onClick={() => void checkPayment()}>Төлбөр шалгах</button>
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
        </div>
      )}
    </Modal>
  );
}
