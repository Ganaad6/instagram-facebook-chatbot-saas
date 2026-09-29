import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth, useMe } from '../auth';
import { useResource } from '../hooks';

const NAV = [
  { to: '/', label: 'Тойм', icon: '◧', end: true },
  { to: '/orders', label: 'Захиалга', icon: '▤' },
  { to: '/chats', label: 'Чат', icon: '✉' },
  { to: '/products', label: 'Бараа', icon: '▦' },
  { to: '/settings', label: 'Тохиргоо', icon: '⚙' },
];

export function Layout() {
  const { business, user } = useMe();
  const { logout } = useAuth();
  const navigate = useNavigate();
  // Customers waiting for a person get a count on the Chat tab
  const inbox = useResource<{ handoffRequestedAt: string | null }[]>(`/api/businesses/${business.id}/inbox`, 20000);
  const waitingCount = inbox.data?.filter((c) => c.handoffRequestedAt).length ?? 0;

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-mark" aria-hidden="true">✉</span>
          <div>
            <div className="brand-name">{business.name}</div>
            <div className="brand-sub">{user.name || user.email}</div>
          </div>
        </div>
        <nav className="nav">
          {NAV.map((item) => (
            <NavLink key={item.to} to={item.to} end={item.end} className="nav-link">
              <span className="nav-icon" aria-hidden="true">{item.icon}</span>
              <span className="nav-label">{item.label}</span>
              {item.to === '/chats' && waitingCount > 0 && <span className="nav-count" aria-label={`${waitingCount} хүлээж буй`}>{waitingCount}</span>}
            </NavLink>
          ))}
        </nav>
        <button className="btn btn-ghost logout" onClick={async () => { await logout(); navigate('/login'); }}>
          Гарах
        </button>
      </aside>
      <main className="content">
        {!business.metaConnected && (
          <div className="banner">
            Facebook хуудас холбогдоогүй тул бот хариу бичихгүй байна.{' '}
            {user.role === 'OWNER' ? <NavLink to="/settings">Одоо холбох →</NavLink> : 'Дэлгүүрийн эзэмшигч холбох шаардлагатай.'}
          </div>
        )}
        <Outlet />
      </main>
    </div>
  );
}
