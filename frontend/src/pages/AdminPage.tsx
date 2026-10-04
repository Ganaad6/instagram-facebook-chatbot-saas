import { useMemo, useState, type FormEvent } from 'react';
import { LogOut, RefreshCw, ShieldCheck } from 'lucide-react';
import { Empty, ErrorBox, Field, PageHeader, Spinner } from '../components/ui';
import { formatDateTime } from '../format';
import type { AdminUser } from '../types';

// The platform operator's view of every dashboard login. It signs in with the admin account
// (ADMIN_USERNAME / ADMIN_PASSWORD, HTTP Basic), not a shop login, so it talks to the backend
// directly instead of through api.ts: that client's 401 handling would sign the shop session out.
// The credentials stay in memory only - reloading the page asks for them again.

const ROLE_LABELS: Record<AdminUser['role'], string> = { OWNER: 'Эзэмшигч', STAFF: 'Ажилтан' };

function basicAuth(username: string, password: string): string {
  const bytes = new TextEncoder().encode(`${username}:${password}`);
  return 'Basic ' + btoa(String.fromCharCode(...bytes));
}

async function fetchUsers(authorization: string): Promise<AdminUser[]> {
  let response: Response;
  try {
    response = await fetch('/api/admin/users', {
      headers: { Accept: 'application/json', Authorization: authorization },
      credentials: 'omit',
    });
  } catch {
    throw new Error('Сервертэй холбогдож чадсангүй');
  }
  if (response.status === 401 || response.status === 403) throw new Error('Нэвтрэх нэр эсвэл нууц үг буруу байна');
  if (response.status === 429) throw new Error('Хэт олон оролдлого. Түр хүлээгээд дахин оролдоно уу');
  if (!response.ok) throw new Error('Алдаа гарлаа. Дахин оролдоно уу');
  return response.json();
}

function UserStatus({ user }: { user: AdminUser }) {
  if (user.businessStatus === 'INACTIVE') return <span className="badge badge-neutral">Дэлгүүр түр хаагдсан</span>;
  if (!user.active) return <span className="badge badge-neutral">Идэвхгүй</span>;
  if (user.locked) return <span className="badge badge-warning">Түгжигдсэн</span>;
  if (user.invitePending) return <span className="badge badge-info">Урилга хүлээгдэж буй</span>;
  return <span className="badge badge-success">Идэвхтэй</span>;
}

export function AdminPage() {
  const [authorization, setAuthorization] = useState<string | null>(null);
  const [users, setUsers] = useState<AdminUser[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [form, setForm] = useState({ username: '', password: '' });
  const [search, setSearch] = useState('');

  const load = async (auth: string) => {
    setBusy(true);
    setError(null);
    try {
      setUsers(await fetchUsers(auth));
      setAuthorization(auth);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Алдаа гарлаа');
    } finally {
      setBusy(false);
    }
  };

  const signIn = (e: FormEvent) => {
    e.preventDefault();
    void load(basicAuth(form.username, form.password));
  };

  const signOut = () => {
    setAuthorization(null);
    setUsers(null);
    setError(null);
    setForm({ username: '', password: '' });
  };

  const filtered = useMemo(() => {
    const term = search.trim().toLowerCase();
    if (!users || !term) return users ?? [];
    return users.filter((u) => [u.email, u.name, u.businessName].some((v) => v?.toLowerCase().includes(term)));
  }, [users, search]);

  if (!authorization) {
    return (
      <div className="auth-page">
        <div className="auth-card">
          <div className="auth-brand"><span className="brand-mark" aria-hidden="true"><ShieldCheck size={20} /></span> Админ</div>
          <h1>Админ нэвтрэх</h1>
          <p className="muted">Платформын админ эрхээр (ADMIN_USERNAME) нэвтэрнэ үү.</p>
          <form onSubmit={signIn} className="stack">
            <Field label="Нэвтрэх нэр">
              <input autoComplete="username" required value={form.username}
                     onChange={(e) => setForm({ ...form, username: e.target.value })} />
            </Field>
            <Field label="Нууц үг">
              <input type="password" autoComplete="current-password" required value={form.password}
                     onChange={(e) => setForm({ ...form, password: e.target.value })} />
            </Field>
            {error && <div className="form-error" role="alert">{error}</div>}
            <button className="btn btn-primary btn-block" disabled={busy}>{busy ? 'Нэвтэрч байна…' : 'Нэвтрэх'}</button>
          </form>
        </div>
      </div>
    );
  }

  const shopCount = new Set(users?.map((u) => u.businessId)).size;
  const pendingCount = users?.filter((u) => u.invitePending).length ?? 0;

  return (
    <div className="admin-page">
      <PageHeader eyebrow="Админ" title="Бүх хэрэглэгчид" actions={
        <>
          <button className="btn" disabled={busy} onClick={() => void load(authorization)}>
            <RefreshCw size={16} aria-hidden="true" /> Шинэчлэх
          </button>
          <button className="btn btn-ghost" onClick={signOut}><LogOut size={16} aria-hidden="true" /> Гарах</button>
        </>
      }>
        {users && `${users.length} хэрэглэгч · ${shopCount} дэлгүүр · ${pendingCount} урилга хүлээгдэж буй`}
      </PageHeader>

      <input className="admin-search" type="search" placeholder="Имэйл, нэр эсвэл дэлгүүрээр хайх"
             aria-label="Хайх" value={search} onChange={(e) => setSearch(e.target.value)} />

      <section className="card flush">
        {error && <ErrorBox message={error} onRetry={() => void load(authorization)} />}
        {!users && !error && <Spinner />}
        {users && filtered.length === 0 && <Empty title={search ? 'Хайлтад тохирох хэрэглэгч алга' : 'Хэрэглэгч бүртгэгдээгүй байна'} />}
        {filtered.length > 0 && (
          <div className="table-wrap">
            <table className="table admin-users-table">
              <thead>
                <tr><th>Хэрэглэгч</th><th>Дэлгүүр</th><th>Эрх</th><th>Төлөв</th><th>Сүүлд нэвтэрсэн</th><th>Бүртгүүлсэн</th></tr>
              </thead>
              <tbody>
                {filtered.map((u) => (
                  <tr key={u.id}>
                    <td data-label="Хэрэглэгч">
                      <strong>{u.email}</strong>
                      {u.name && <div className="sub">{u.name}</div>}
                    </td>
                    <td data-label="Дэлгүүр">{u.businessName}<div className="sub">#{u.businessId}</div></td>
                    <td data-label="Эрх">{ROLE_LABELS[u.role]}</td>
                    <td data-label="Төлөв"><UserStatus user={u} /></td>
                    <td data-label="Сүүлд нэвтэрсэн">{formatDateTime(u.lastLoginAt)}</td>
                    <td data-label="Бүртгүүлсэн">{formatDateTime(u.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}
