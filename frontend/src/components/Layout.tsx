import type { ComponentType } from 'react';
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { Bot, LayoutDashboard, LogOut, MessageCircle, Package, Settings, ShoppingBag, TriangleAlert, type LucideProps } from 'lucide-react';
import { useAuth, useMe } from '../auth';
import { useResource } from '../hooks';
import type { Business, Role } from '../types';

const NAV: { to: string; label: string; icon: ComponentType<LucideProps>; end?: boolean }[] = [
  { to: '/', label: 'Тойм', icon: LayoutDashboard, end: true },
  { to: '/orders', label: 'Захиалга', icon: ShoppingBag },
  { to: '/chats', label: 'Чат', icon: MessageCircle },
  { to: '/products', label: 'Бараа', icon: Package },
  { to: '/settings', label: 'Тохиргоо', icon: Settings },
];

export function Layout() {
  const { business, user } = useMe();
  const { logout } = useAuth();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  // Customers waiting for a person get a count on the Chat tab
  const inbox = useResource<{ handoffRequestedAt: string | null }[]>(`/api/businesses/${business.id}/inbox`, 20000);
  const waitingCount = inbox.data?.filter((c) => c.handoffRequestedAt).length ?? 0;
  // The chat screen uses the whole window for its three columns
  const fullBleed = pathname.startsWith('/chats');

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-mark" aria-hidden="true"><Bot size={20} /></span>
          <div>
            <div className="brand-name">{business.name}</div>
            <div className="brand-sub">{user.name || user.email} · {user.role === 'OWNER' ? 'Эзэмшигч' : 'Ажилтан'}</div>
          </div>
        </div>
        <nav className="nav" aria-label="Үндсэн цэс">
          {NAV.map(({ to, label, icon: Icon, end }) => (
            <NavLink key={to} to={to} end={end} className="nav-link">
              <span className="nav-icon" aria-hidden="true"><Icon size={18} /></span>
              <span className="nav-label">{label}</span>
              {to === '/chats' && waitingCount > 0 && <span className="nav-count" aria-label={`${waitingCount} хүлээж буй`}>{waitingCount}</span>}
            </NavLink>
          ))}
        </nav>
        <Connections business={business} role={user.role} />
        <button className="btn btn-ghost logout" onClick={async () => { await logout(); navigate('/login'); }}>
          <LogOut size={18} aria-hidden="true" /> Гарах
        </button>
      </aside>
      <main className={`content${fullBleed ? ' full' : ''}`}>
        {!business.metaConnected && (
          <div className="banner">
            <TriangleAlert size={18} aria-hidden="true" />
            <span>
              Facebook хуудас холбогдоогүй тул бот хариу бичихгүй байна.{' '}
              {user.role === 'OWNER' ? <NavLink to="/settings">Одоо холбох →</NavLink> : 'Дэлгүүрийн эзэмшигч холбох шаардлагатай.'}
            </span>
          </div>
        )}
        <Outlet />
      </main>
    </div>
  );
}

/** Which channels are live, so a missing Instagram or QPay link is noticed without opening settings. */
function Connections({ business, role }: { business: Business; role: Role }) {
  const items = [
    { name: 'Messenger', on: business.metaConnected && !!business.facebookPageId },
    { name: 'Instagram', on: business.metaConnected && !!business.instagramAccountId },
    { name: 'QPay', on: business.qpayConnected },
  ];
  return (
    <section className="connections" aria-label="Холболт">
      <div className="connections-title">Холболт</div>
      {items.map((c) => (
        <div key={c.name} className="connection">
          <span className={`dot${c.on ? ' on' : ''}`} aria-hidden="true" />
          <span className="connection-name">{c.name}</span>
          {c.on ? <span className="connection-state on">Идэвхтэй</span>
            : role === 'OWNER' ? <NavLink to="/settings">Холбох</NavLink>
            : <span className="connection-state">Холбоогүй</span>}
        </div>
      ))}
    </section>
  );
}
