import { Link } from 'react-router-dom';
import { useMe } from '../auth';
import { useResource } from '../hooks';
import { DailyChart, lastDays } from '../components/DailyChart';
import { Empty, ErrorBox, OrderStatusBadge, PageHeader, PaymentBadge, Spinner } from '../components/ui';
import { formatMoney, formatShort } from '../format';
import type { AnalyticsSummary, DailyCount, Order, Page, Product } from '../types';

const DAYS = 14;

export function OverviewPage() {
  const { business, user } = useMe();
  const base = `/api/businesses/${business.id}`;
  const days = lastDays(DAYS);
  const summary = useResource<AnalyticsSummary>(`${base}/analytics/summary`, 30000);
  const daily = useResource<DailyCount[]>(`${base}/analytics/orders-by-day?from=${days[0]}&to=${days[days.length - 1]}`, 60000);
  const recent = useResource<Page<Order>>(`${base}/orders?size=6`, 30000);
  const products = useResource<Product[]>(`${base}/products`);

  const setup = [
    { done: business.metaConnected, label: 'Facebook хуудас, Instagram холбох', to: '/settings' },
    { done: (products.data?.length ?? 0) > 0, label: 'Бараа нэмэх', to: '/products' },
    { done: business.qpayConnected, label: 'QPay холбох (заавал биш)', to: '/settings' },
  ];
  const setupLeft = products.data !== undefined && setup.some((s) => !s.done);

  return (
    <>
      <PageHeader title="Тойм">Сайн байна уу, {user.name || user.email}</PageHeader>

      {setupLeft && (
        <section className="card setup">
          <h2>Эхлүүлэх алхмууд</h2>
          <ol className="checklist">
            {setup.map((s) => (
              <li key={s.label} className={s.done ? 'done' : ''}>
                <span className="check" aria-hidden="true">{s.done ? '✓' : ''}</span>
                {s.done ? s.label : <Link to={s.to}>{s.label}</Link>}
              </li>
            ))}
          </ol>
        </section>
      )}

      {summary.error && <ErrorBox message={summary.error} onRetry={summary.reload} />}
      {!summary.data && !summary.error && <Spinner />}
      {summary.data && (
        <section className="kpis" aria-label="Үзүүлэлтүүд">
          <Stat label="Өнөөдрийн захиалга" value={summary.data.todayOrders} />
          <Stat label="Шинэ захиалга" value={summary.data.pendingOrders} to="/orders?status=PENDING" />
          <Stat label="Нийт борлуулалт" value={formatMoney(summary.data.totalRevenue)} hint="Цуцлагдсаныг оруулаагүй" />
          <Stat label="QPay-ээр төлөгдсөн" value={formatMoney(summary.data.paidRevenue)}
                hint={summary.data.awaitingPaymentOrders ? `${summary.data.awaitingPaymentOrders} захиалга төлбөр хүлээж байна` : undefined} />
        </section>
      )}

      <div className="grid-2">
        <section className="card">
          {daily.data ? <DailyChart days={days} data={daily.data} /> : daily.error ? <ErrorBox message={daily.error} /> : <Spinner />}
        </section>
        <section className="card">
          <h2>Их захиалагдсан бараа</h2>
          {summary.data && summary.data.topProducts.length === 0 && <Empty title="Одоогоор захиалга алга" />}
          {summary.data && summary.data.topProducts.length > 0 && (
            <table className="table compact">
              <thead><tr><th>Бараа</th><th className="num">Захиалга</th><th className="num">Ширхэг</th></tr></thead>
              <tbody>
                {summary.data.topProducts.map((p) => (
                  <tr key={p.productName}><td>{p.productName}</td><td className="num">{p.orderCount}</td><td className="num">{p.totalQuantity}</td></tr>
                ))}
              </tbody>
            </table>
          )}
        </section>
      </div>

      <section className="card">
        <div className="card-head">
          <h2>Сүүлийн захиалгууд</h2>
          <Link to="/orders">Бүгдийг харах →</Link>
        </div>
        {recent.data && recent.data.content.length === 0 && (
          <Empty title="Захиалга ирээгүй байна">Хэрэглэгч Messenger эсвэл Instagram-аар бичихэд бот захиалга авна.</Empty>
        )}
        {recent.data && recent.data.content.length > 0 && (
          <ul className="list">
            {recent.data.content.map((o) => (
              <li key={o.id}>
                <Link to={`/orders/${o.id}`} className="list-row">
                  <div className="list-main">
                    <strong>#{o.id} {o.productName} × {o.quantity}</strong>
                    <span className="muted">{o.customerName} · {formatShort(o.createdAt)}</span>
                  </div>
                  <div className="list-side">
                    <span className="amount">{formatMoney(o.totalAmount)}</span>
                    <span className="badges"><OrderStatusBadge status={o.status} />{o.paymentStatus !== 'NOT_REQUESTED' && <PaymentBadge status={o.paymentStatus} />}</span>
                  </div>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>
    </>
  );
}

function Stat({ label, value, hint, to }: { label: string; value: string | number; hint?: string; to?: string }) {
  const body = (
    <>
      <span className="stat-label">{label}</span>
      <span className="stat-value">{value}</span>
      {hint && <span className="stat-hint">{hint}</span>}
    </>
  );
  return to ? <Link to={to} className="stat card">{body}</Link> : <div className="stat card">{body}</div>;
}
