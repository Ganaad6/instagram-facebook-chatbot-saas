import { Link } from 'react-router-dom';
import { ChevronRight, MessageCircle } from 'lucide-react';
import { useMe } from '../auth';
import { useResource } from '../hooks';
import { DailyChart, lastDays } from '../components/DailyChart';
import { Empty, ErrorBox, OrderStatusBadge, PageHeader, PaymentBadge, Spinner } from '../components/ui';
import { formatMoney, formatShort, formatToday, greeting } from '../format';
import type { AnalyticsSummary, ChatSummary, DailyCount, Order, Page, Product } from '../types';

const DAYS = 14;

export function OverviewPage() {
  const { business, user } = useMe();
  const base = `/api/businesses/${business.id}`;
  const days = lastDays(DAYS);
  const summary = useResource<AnalyticsSummary>(`${base}/analytics/summary`, 30000);
  const daily = useResource<DailyCount[]>(`${base}/analytics/orders-by-day?from=${days[0]}&to=${days[days.length - 1]}`, 60000);
  const recent = useResource<Page<Order>>(`${base}/orders?size=6`, 30000);
  const products = useResource<Product[]>(`${base}/products`);
  const waiting = useResource<Page<ChatSummary>>(`${base}/chats?waiting=true&size=1`, 20000);
  const waitingCount = waiting.data?.totalElements ?? 0;

  const setup = [
    { done: business.metaConnected, label: 'Facebook хуудас, Instagram холбох', to: '/settings' },
    { done: (products.data?.length ?? 0) > 0, label: 'Бараа нэмэх', to: '/products' },
    { done: business.qpayConnected, label: 'QPay холбох (заавал биш)', to: '/settings' },
  ];
  const doneCount = setup.filter((s) => s.done).length;
  const nextStep = setup.find((s) => !s.done);
  const setupLeft = products.data !== undefined && nextStep !== undefined;

  // Today against yesterday, from the same per-day series the chart uses
  const countOn = (day: string) => daily.data?.find((d) => d.day === day)?.count ?? 0;
  const change = daily.data ? countOn(days[days.length - 1]) - countOn(days[days.length - 2]) : null;

  const topMax = Math.max(1, ...(summary.data?.topProducts.map((p) => p.orderCount) ?? []));

  return (
    <>
      <PageHeader eyebrow={formatToday()} title={`${greeting()}, ${user.name || user.email}`}
                  actions={waitingCount > 0 && (
                    <Link to="/chats" className="waiting-link">
                      <MessageCircle size={18} aria-hidden="true" />
                      {waitingCount} хэрэглэгч хүн хүлээж байна
                      <ChevronRight size={16} aria-hidden="true" />
                    </Link>
                  )} />

      {setupLeft && (
        <section className="setup-bar" aria-label="Эхлүүлэх алхмууд">
          <strong>Тохиргоо {doneCount}/{setup.length}</strong>
          <div className="progress" role="progressbar" aria-valuemin={0} aria-valuemax={setup.length} aria-valuenow={doneCount}
               aria-label="Тохиргооны явц">
            <div style={{ width: `${(doneCount / setup.length) * 100}%` }} />
          </div>
          <span className="muted small">Дараагийн алхам:</span>
          <Link to={nextStep.to}><strong>{nextStep.label} →</strong></Link>
        </section>
      )}

      {summary.error && <ErrorBox message={summary.error} onRetry={summary.reload} />}
      {!summary.data && !summary.error && <Spinner />}
      {summary.data && (
        <section className="kpis" aria-label="Үзүүлэлтүүд">
          <Stat label="Өнөөдрийн захиалга" value={summary.data.todayOrders}
                hint={change ? `Өчигдрөөс ${change > 0 ? '+' : ''}${change}` : undefined} tone={change && change > 0 ? 'up' : undefined} />
          <Stat label="Шинэ, баталгаажаагүй" value={summary.data.pendingOrders} to="/orders?status=PENDING"
                hint={summary.data.pendingOrders ? 'Харах →' : undefined} tone="link" />
          <Stat label="Нийт борлуулалт" value={formatMoney(summary.data.totalRevenue)} hint="Цуцлагдсаныг оруулаагүй" />
          <Stat label="QPay-ээр төлөгдсөн" value={formatMoney(summary.data.paidRevenue)} tone="warn"
                hint={summary.data.awaitingPaymentOrders ? `${summary.data.awaitingPaymentOrders} төлбөр хүлээгдэж буй` : undefined} />
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
            <ul className="bars">
              {summary.data.topProducts.map((p) => (
                <li key={p.productName}>
                  <div className="bar-label">
                    <span className="ellipsis">{p.productName}</span>
                    <strong>{p.orderCount}</strong>
                    <span className="muted" style={{ flex: 'none' }}>({p.totalQuantity} ш)</span>
                  </div>
                  <div className="bar-track" aria-hidden="true"><div style={{ width: `${(p.orderCount / topMax) * 100}%` }} /></div>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>

      <section className="card flush">
        <div className="card-head">
          <h2>Сүүлийн захиалгууд</h2>
          <Link to="/orders"><strong>Бүгдийг харах →</strong></Link>
        </div>
        {recent.data && recent.data.content.length === 0 && (
          <Empty title="Захиалга ирээгүй байна">Хэрэглэгч Messenger эсвэл Instagram-аар бичихэд бот захиалга авна.</Empty>
        )}
        {recent.data && recent.data.content.length > 0 && (
          <div className="table-wrap">
            <table className="table orders-table">
              <tbody>
                {recent.data.content.map((o) => (
                  <tr key={o.id}>
                    <td data-label="#"><Link to={`/orders/${o.id}`}>#{o.id}</Link></td>
                    <td data-label="Бараа"><strong>{o.productName} × {o.quantity}</strong></td>
                    <td data-label="Хэрэглэгч">{o.customerName}</td>
                    <td data-label="Дүн" className="num">{formatMoney(o.totalAmount)}</td>
                    <td data-label="Төлөв"><span className="badges"><OrderStatusBadge status={o.status} />{o.paymentStatus !== 'NOT_REQUESTED' && <PaymentBadge status={o.paymentStatus} />}</span></td>
                    <td data-label="Огноо" className="muted num" style={{ fontWeight: 500 }}>{formatShort(o.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </>
  );
}

function Stat({ label, value, hint, tone, to }: {
  label: string; value: string | number; hint?: string; tone?: 'up' | 'warn' | 'link'; to?: string;
}) {
  const body = (
    <>
      <span className="stat-label">{label}</span>
      <span className="stat-value">{value}</span>
      {hint && <span className={`stat-hint${tone ? ` ${tone}` : ''}`}>{hint}</span>}
    </>
  );
  return to ? <Link to={to} className="stat card">{body}</Link> : <div className="stat card">{body}</div>;
}
