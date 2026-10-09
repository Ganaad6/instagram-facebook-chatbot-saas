import { useEffect, useState, type FormEvent, type ReactNode } from 'react';
import { Link, Navigate, useLocation, useNavigate, useParams } from 'react-router-dom';
import { Bot } from 'lucide-react';
import { api, ApiError } from '../api';
import { useAuth } from '../auth';
import { Field, Spinner } from '../components/ui';
import type { Me } from '../types';

function AuthCard({ title, subtitle, children, footer }: {
  title: string; subtitle?: ReactNode; children: ReactNode; footer?: ReactNode;
}) {
  return (
    <div className="auth-page">
      <div className="auth-card">
        <div className="auth-brand"><span className="brand-mark" aria-hidden="true"><Bot size={20} /></span> ChatShop</div>
        <h1>{title}</h1>
        {subtitle && <p className="muted">{subtitle}</p>}
        {children}
      </div>
      {footer && <div className="auth-footer">{footer}</div>}
      <nav className="auth-footer auth-legal" aria-label="Бодлого">
        <Link to="/privacy">Нууцлалын бодлого</Link>
        <Link to="/terms">Үйлчилгээний нөхцөл</Link>
        <Link to="/data-deletion">Мэдээлэл устгах</Link>
      </nav>
    </div>
  );
}

function useSubmit() {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const run = async (action: () => Promise<void>) => {
    setBusy(true);
    setError(null);
    try {
      await action();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Алдаа гарлаа. Дахин оролдоно уу');
    } finally {
      setBusy(false);
    }
  };
  return { busy, error, run };
}

export function LoginPage() {
  const { me, setMe } = useAuth();
  const navigate = useNavigate();
  const from = (useLocation().state as { from?: string } | null)?.from ?? '/';
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const { busy, error, run } = useSubmit();

  if (me) return <Navigate to={from} replace />;

  const submit = (e: FormEvent) => {
    e.preventDefault();
    void run(async () => {
      setMe(await api.post<Me>('/api/auth/login', { email, password }));
      navigate(from, { replace: true });
    });
  };

  return (
    <AuthCard title="Нэвтрэх" footer={<>Бүртгэлгүй юу? <Link to="/signup">Дэлгүүр бүртгүүлэх</Link></>}>
      <form onSubmit={submit} className="stack">
        <Field label="Имэйл">
          <input type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
        </Field>
        <Field label="Нууц үг">
          <input type="password" autoComplete="current-password" required value={password} onChange={(e) => setPassword(e.target.value)} />
        </Field>
        {error && <div className="form-error" role="alert">{error}</div>}
        <button className="btn btn-primary btn-block" disabled={busy}>{busy ? 'Нэвтэрч байна…' : 'Нэвтрэх'}</button>
        <p className="muted small">Нууц үгээ мартсан бол дэлгүүрийн эзэмшигчээсээ шинэ холбоос авна уу.</p>
      </form>
    </AuthCard>
  );
}

export function SignupPage() {
  const { me, setMe } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ businessName: '', name: '', email: '', password: '' });
  const { busy, error, run } = useSubmit();

  if (me) return <Navigate to="/" replace />;

  const set = (key: keyof typeof form) => (e: { target: { value: string } }) => setForm({ ...form, [key]: e.target.value });
  const submit = (e: FormEvent) => {
    e.preventDefault();
    void run(async () => {
      setMe(await api.post<Me>('/api/auth/signup', form));
      navigate('/settings', { replace: true });
    });
  };

  return (
    <AuthCard title="Дэлгүүр бүртгүүлэх" subtitle="Messenger болон Instagram-аар захиалга авдаг чатбот"
              footer={<>Бүртгэлтэй юу? <Link to="/login">Нэвтрэх</Link></>}>
      <form onSubmit={submit} className="stack">
        <Field label="Дэлгүүрийн нэр">
          <input required maxLength={255} value={form.businessName} onChange={set('businessName')} />
        </Field>
        <Field label="Таны нэр">
          <input required maxLength={255} autoComplete="name" value={form.name} onChange={set('name')} />
        </Field>
        <Field label="Имэйл">
          <input type="email" required autoComplete="email" value={form.email} onChange={set('email')} />
        </Field>
        <Field label="Нууц үг" hint="Дор хаяж 8 тэмдэгт">
          <input type="password" required minLength={8} autoComplete="new-password" value={form.password} onChange={set('password')} />
        </Field>
        {error && <div className="form-error" role="alert">{error}</div>}
        <p className="muted small">
          Бүртгүүлснээр та <Link to="/terms">Үйлчилгээний нөхцөл</Link> болон{' '}
          <Link to="/privacy">Нууцлалын бодлого</Link>-ыг зөвшөөрч байна.
        </p>
        <button className="btn btn-primary btn-block" disabled={busy}>{busy ? 'Бүртгэж байна…' : 'Бүртгүүлэх'}</button>
      </form>
    </AuthCard>
  );
}

interface LinkInfo { email: string; name: string | null; businessName: string; purpose: 'INVITE' | 'RESET' }

export function InvitePage() {
  const { token = '' } = useParams();
  const { setMe } = useAuth();
  const navigate = useNavigate();
  const [info, setInfo] = useState<LinkInfo | null | undefined>(undefined);
  const [linkError, setLinkError] = useState<string | null>(null);
  const [name, setName] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const { busy, error, run } = useSubmit();

  useEffect(() => {
    api.get<LinkInfo>(`/api/auth/links/${encodeURIComponent(token)}`)
      .then((data) => { setInfo(data); setName(data.name ?? ''); })
      .catch((e) => { setInfo(null); setLinkError(e instanceof Error ? e.message : String(e)); });
  }, [token]);

  if (info === undefined) return <AuthCard title="Холбоос шалгаж байна"><Spinner /></AuthCard>;
  if (info === null) {
    return (
      <AuthCard title="Холбоос хүчингүй" footer={<Link to="/login">Нэвтрэх хуудас</Link>}>
        <p>{linkError}</p>
      </AuthCard>
    );
  }

  const invite = info.purpose === 'INVITE';
  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (password !== confirm) return;
    void run(async () => {
      setMe(await api.post<Me>('/api/auth/links/accept', { token, name, password }));
      navigate('/', { replace: true });
    });
  };

  return (
    <AuthCard title={invite ? `${info.businessName}-д тавтай морил` : 'Шинэ нууц үг'}
              subtitle={<>{info.email} {invite ? '— нууц үгээ үүсгээд нэвтэрнэ үү.' : '— шинэ нууц үгээ оруулна уу.'}</>}>
      <form onSubmit={submit} className="stack">
        {invite && (
          <Field label="Таны нэр">
            <input required maxLength={255} autoComplete="name" value={name} onChange={(e) => setName(e.target.value)} />
          </Field>
        )}
        <Field label="Нууц үг" hint="Дор хаяж 8 тэмдэгт">
          <input type="password" required minLength={8} autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} />
        </Field>
        <Field label="Нууц үг давтах">
          <input type="password" required autoComplete="new-password" value={confirm} onChange={(e) => setConfirm(e.target.value)} />
        </Field>
        {confirm && password !== confirm && <div className="form-error">Нууц үг таарахгүй байна</div>}
        {error && <div className="form-error" role="alert">{error}</div>}
        <button className="btn btn-primary btn-block" disabled={busy || password !== confirm}>
          {busy ? 'Хадгалж байна…' : invite ? 'Нэвтрэх' : 'Нууц үг солих'}
        </button>
      </form>
    </AuthCard>
  );
}
