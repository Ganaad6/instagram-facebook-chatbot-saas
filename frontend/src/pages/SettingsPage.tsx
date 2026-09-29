import { useState, type FormEvent, type ReactNode } from 'react';
import { NavLink, useParams, useSearchParams } from 'react-router-dom';
import { api } from '../api';
import { useAuth, useMe } from '../auth';
import { useResource } from '../hooks';
import { useToast } from '../components/Toast';
import { copyText, ErrorBox, Field, PageHeader, Spinner } from '../components/ui';
import { formatDateTime } from '../format';
import type { Business, IssuedLink, Role, StaffUser } from '../types';

const TABS = [
  { id: 'connections', label: 'Холболт', ownerOnly: true },
  { id: 'shop', label: 'Дэлгүүр', ownerOnly: true },
  { id: 'staff', label: 'Хэрэглэгчид', ownerOnly: true },
  { id: 'integrations', label: 'Интеграц', ownerOnly: true },
  { id: 'password', label: 'Нууц үг', ownerOnly: false },
];

export function SettingsPage() {
  const { user } = useMe();
  const owner = user.role === 'OWNER';
  const tabs = TABS.filter((t) => owner || !t.ownerOnly);
  const { tab = tabs[0].id } = useParams();

  return (
    <>
      <PageHeader title="Тохиргоо" />
      <div className="tabs" role="tablist">
        {tabs.map((t) => (
          <NavLink key={t.id} to={`/settings/${t.id}`} role="tab"
                   className={() => `tab${tab === t.id ? ' active' : ''}`}>{t.label}</NavLink>
        ))}
      </div>
      {!owner && tab !== 'password' ? <p className="muted">Энэ тохиргоог зөвхөн дэлгүүрийн эзэмшигч өөрчилнө.</p> : (
        <>
          {tab === 'connections' && <Connections />}
          {tab === 'shop' && <ShopInfo />}
          {tab === 'staff' && <Staff />}
          {tab === 'integrations' && <Integrations />}
          {tab === 'password' && <PasswordForm />}
        </>
      )}
    </>
  );
}

function Section({ title, description, children }: { title: string; description?: ReactNode; children: ReactNode }) {
  return (
    <section className="card settings-section">
      <div className="settings-intro">
        <h2>{title}</h2>
        {description && <p className="muted">{description}</p>}
      </div>
      <div className="settings-body">{children}</div>
    </section>
  );
}

function useAction() {
  const toast = useToast();
  const [busy, setBusy] = useState(false);
  const run = async (action: () => Promise<unknown>, success?: string): Promise<boolean> => {
    setBusy(true);
    try {
      await action();
      if (success) toast(success);
      return true;
    } catch (e) {
      toast(e instanceof Error ? e.message : String(e), 'error');
      return false;
    } finally {
      setBusy(false);
    }
  };
  return { busy, run };
}

const META_OUTCOMES: Record<string, { ok: boolean; text: string }> = {
  connected: { ok: true, text: 'Facebook хуудас болон Instagram амжилттай холбогдлоо.' },
  'connected-page-only': { ok: true, text: 'Facebook хуудас холбогдлоо. Хуудастай холбоотой Instagram мэргэжлийн бүртгэл олдсонгүй тул зөвхөн Messenger-т хариулна.' },
};

function Connections() {
  const { business } = useMe();
  const { refresh } = useAuth();
  const [params, setParams] = useSearchParams();
  const meta = params.get('meta');
  const outcome = meta ? META_OUTCOMES[meta] ?? { ok: false, text: params.get('message') || 'Холбож чадсангүй.' } : null;
  const { busy, run } = useAction();
  const [qpay, setQpay] = useState({ username: '', password: '', invoiceCode: '' });
  const [editingQpay, setEditingQpay] = useState(false);

  const connectMeta = () => run(async () => {
    const { authorizationUrl } = await api.get<{ authorizationUrl: string }>(`/api/auth/meta/authorize?businessId=${business.id}`);
    window.location.href = authorizationUrl;
  });

  const saveQpay = async (e: FormEvent) => {
    e.preventDefault();
    if (await run(() => api.put(`/api/businesses/${business.id}/payments/qpay`, qpay), 'QPay холбогдлоо')) {
      setQpay({ username: '', password: '', invoiceCode: '' });
      setEditingQpay(false);
      await refresh();
    }
  };

  const disconnectQpay = async () => {
    if (!window.confirm('QPay-г салгах уу? Шинэ захиалгад төлбөрийн холбоос илгээгдэхгүй.')) return;
    if (await run(() => api.delete(`/api/businesses/${business.id}/payments/qpay`), 'QPay салгагдлаа')) await refresh();
  };

  return (
    <>
      {outcome && (
        <div className={`note ${outcome.ok ? 'success' : 'warning'}`} role="status">
          {outcome.text} <button className="link-btn" onClick={() => { params.delete('meta'); params.delete('message'); setParams(params); void refresh(); }}>Хаах</button>
        </div>
      )}
      <Section title="Facebook & Instagram" description="Бот таны Facebook хуудасны Messenger болон холбоотой Instagram бүртгэлд ирсэн мессежид хариулна.">
        {business.metaConnected ? (
          <dl className="details">
            <dt>Төлөв</dt><dd><span className="badge badge-success">Холбогдсон</span></dd>
            <dt>Facebook хуудас</dt><dd className="mono">{business.facebookPageId ?? '—'}</dd>
            <dt>Instagram</dt><dd className="mono">{business.instagramAccountId ?? 'Холбогдоогүй'}</dd>
          </dl>
        ) : <p><span className="badge badge-warning">Холбогдоогүй</span></p>}
        <button className="btn btn-primary" disabled={busy} onClick={() => void connectMeta()}>
          {business.metaConnected ? 'Дахин холбох' : 'Facebook-ээр холбох'}
        </button>
        <p className="field-hint">Facebook руу шилжиж, хуудсаа сонгоод зөвшөөрөл олгоно. Instagram нь хуудастай холбоотой мэргэжлийн бүртгэл байх ёстой.</p>
      </Section>

      <Section title="QPay төлбөр" description="Холбосон үед бот захиалга бүрт QPay нэхэмжлэх үүсгэж, төлбөрийн холбоосыг хэрэглэгчид илгээнэ. Мөнгө шууд таны QPay мерчант данс руу орно.">
        {business.qpayConnected && !editingQpay ? (
          <>
            <dl className="details">
              <dt>Төлөв</dt><dd><span className="badge badge-success">Холбогдсон</span></dd>
              <dt>Мерчант</dt><dd className="mono">{business.qpayUsername}</dd>
            </dl>
            <div className="row">
              <button className="btn" onClick={() => setEditingQpay(true)}>Мэдээлэл солих</button>
              <button className="btn btn-danger-ghost" disabled={busy} onClick={() => void disconnectQpay()}>Салгах</button>
            </div>
          </>
        ) : (
          <form className="stack" onSubmit={saveQpay}>
            <Field label="Нэвтрэх нэр (username)"><input required autoComplete="off" value={qpay.username} onChange={(e) => setQpay({ ...qpay, username: e.target.value })} /></Field>
            <Field label="Нууц үг"><input required type="password" autoComplete="new-password" value={qpay.password} onChange={(e) => setQpay({ ...qpay, password: e.target.value })} /></Field>
            <Field label="Нэхэмжлэхийн код (invoice code)"><input required autoComplete="off" value={qpay.invoiceCode} onChange={(e) => setQpay({ ...qpay, invoiceCode: e.target.value })} /></Field>
            <p className="field-hint">QPay-тэй мерчант гэрээ байгуулахад эдгээр мэдээллийг өгнө. Хадгалахаас өмнө QPay-ээр шалгана.</p>
            <div className="row">
              <button className="btn btn-primary" disabled={busy}>{busy ? 'Шалгаж байна…' : 'Холбох'}</button>
              {editingQpay && <button type="button" className="btn" onClick={() => setEditingQpay(false)}>Болих</button>}
            </div>
          </form>
        )}
      </Section>
    </>
  );
}

function ShopInfo() {
  const { business } = useMe();
  const { refresh } = useAuth();
  const { busy, run } = useAction();
  const [name, setName] = useState(business.name);
  const [email, setEmail] = useState(business.email);

  const save = async (e: FormEvent) => {
    e.preventDefault();
    if (await run(() => api.put<Business>(`/api/businesses/${business.id}`, { name: name.trim(), email: email.trim() }), 'Хадгаллаа')) {
      await refresh();
    }
  };

  return (
    <Section title="Дэлгүүрийн мэдээлэл">
      <form className="stack" onSubmit={save}>
        <Field label="Дэлгүүрийн нэр"><input required maxLength={255} value={name} onChange={(e) => setName(e.target.value)} /></Field>
        <Field label="Холбоо барих имэйл"><input required type="email" value={email} onChange={(e) => setEmail(e.target.value)} /></Field>
        <div><button className="btn btn-primary" disabled={busy}>Хадгалах</button></div>
      </form>
    </Section>
  );
}

function LinkBox({ link, onDone }: { link: IssuedLink; onDone: () => void }) {
  const toast = useToast();
  return (
    <div className="note success">
      <p>Энэ холбоосыг тухайн хүнд илгээнэ үү ({formatDateTime(link.expiresAt)} хүртэл хүчинтэй, нэг удаа ашиглана):</p>
      <div className="copy-row">
        <input readOnly value={link.url} onFocus={(e) => e.target.select()} aria-label="Холбоос" />
        <button className="btn btn-small" onClick={async () => toast(await copyText(link.url) ? 'Хуулагдлаа' : 'Хуулж чадсангүй — гараар хуулна уу', 'success')}>Хуулах</button>
      </div>
      <button className="link-btn" onClick={onDone}>Хаах</button>
    </div>
  );
}

const ROLE_LABELS: Record<Role, string> = { OWNER: 'Эзэмшигч', STAFF: 'Ажилтан' };

function Staff() {
  const { business, user } = useMe();
  const base = `/api/businesses/${business.id}/staff`;
  const staff = useResource<StaffUser[]>(base);
  const { busy, run } = useAction();
  const [invite, setInvite] = useState({ email: '', name: '', role: 'STAFF' as Role });
  const [link, setLink] = useState<IssuedLink | null>(null);

  const sendInvite = async (e: FormEvent) => {
    e.preventDefault();
    await run(async () => {
      const result = await api.post<{ link: IssuedLink }>(base, invite);
      setLink(result.link);
      setInvite({ email: '', name: '', role: 'STAFF' });
      await staff.reload();
    });
  };

  const update = (u: StaffUser, body: Record<string, unknown>, message: string) =>
    run(async () => { await api.put(`${base}/${u.id}`, body); await staff.reload(); }, message);

  return (
    <>
      <Section title="Хэрэглэгч урих" description="Ажилтан захиалга, бараа, чатыг удирдана. Эзэмшигч мөн тохиргоо, холболтыг өөрчилнө.">
        {link ? <LinkBox link={link} onDone={() => setLink(null)} /> : (
          <form className="stack" onSubmit={sendInvite}>
            <div className="row-2">
              <Field label="Имэйл"><input required type="email" value={invite.email} onChange={(e) => setInvite({ ...invite, email: e.target.value })} /></Field>
              <Field label="Нэр"><input maxLength={255} value={invite.name} onChange={(e) => setInvite({ ...invite, name: e.target.value })} /></Field>
            </div>
            <Field label="Эрх">
              <select value={invite.role} onChange={(e) => setInvite({ ...invite, role: e.target.value as Role })}>
                <option value="STAFF">Ажилтан</option>
                <option value="OWNER">Эзэмшигч</option>
              </select>
            </Field>
            <div><button className="btn btn-primary" disabled={busy}>Урилгын холбоос үүсгэх</button></div>
          </form>
        )}
      </Section>
      <Section title="Хэрэглэгчид">
        {staff.error && <ErrorBox message={staff.error} />}
        {!staff.data && !staff.error && <Spinner />}
        <ul className="list">
          {staff.data?.map((u) => (
            <li key={u.id} className="list-row static">
              <div className="list-main">
                <strong>{u.name || u.email} {u.id === user.id && <span className="muted">(та)</span>}</strong>
                <span className="muted small">
                  {u.email} · {ROLE_LABELS[u.role]}
                  {u.invitePending ? ' · Урилга хүлээгдэж буй' : u.lastLoginAt ? ` · Сүүлд ${formatDateTime(u.lastLoginAt)}` : ''}
                  {!u.active && ' · Идэвхгүй'}
                </span>
              </div>
              {u.id !== user.id && (
                <div className="row wrap">
                  <button className="btn btn-small" disabled={busy}
                          onClick={() => void run(async () => setLink(await api.post<IssuedLink>(`${base}/${u.id}/password-link`)))}>
                    {u.invitePending ? 'Шинэ урилга' : 'Нууц үг сэргээх'}
                  </button>
                  <button className="btn btn-small" disabled={busy}
                          onClick={() => void update(u, { role: u.role === 'OWNER' ? 'STAFF' : 'OWNER' }, 'Эрх өөрчлөгдлөө')}>
                    {u.role === 'OWNER' ? 'Ажилтан болгох' : 'Эзэмшигч болгох'}
                  </button>
                  <button className="btn btn-small btn-danger-ghost" disabled={busy}
                          onClick={() => void update(u, { active: !u.active }, u.active ? 'Эрх хаагдлаа' : 'Эрх нээгдлээ')}>
                    {u.active ? 'Эрх хаах' : 'Эрх нээх'}
                  </button>
                </div>
              )}
            </li>
          ))}
        </ul>
      </Section>
    </>
  );
}

function Integrations() {
  const { business } = useMe();
  const { refresh } = useAuth();
  const { busy, run } = useAction();
  const toast = useToast();
  const [webhookUrl, setWebhookUrl] = useState(business.notificationWebhookUrl ?? '');
  const [apiKey, setApiKey] = useState<string | null>(null);

  const saveWebhook = async (e: FormEvent) => {
    e.preventDefault();
    if (await run(() => api.post(`/api/businesses/${business.id}/notifications/webhook-url`, { webhookUrl: webhookUrl.trim() || null }), 'Хадгаллаа')) {
      await refresh();
    }
  };

  const rotateKey = async () => {
    if (!window.confirm('Шинэ API түлхүүр үүсгэх үү? Хуучин түлхүүр шууд ажиллахаа болино.')) return;
    await run(async () => setApiKey((await api.post<{ apiKey: string }>(`/api/businesses/${business.id}/api-key`)).apiKey));
  };

  return (
    <>
      <Section title="Мэдэгдлийн webhook" description={<>Шинэ захиалга (NEW_ORDER), төлбөр (PAYMENT_RECEIVED), ажилтан хүссэн (HANDOFF_REQUESTED) үед энэ хаяг руу JSON илгээнэ. Жишээ нь өөрийн систем эсвэл Zapier/Make.</>}>
        <form className="stack" onSubmit={saveWebhook}>
          <Field label="URL"><input type="url" placeholder="https://" value={webhookUrl} onChange={(e) => setWebhookUrl(e.target.value)} /></Field>
          <div><button className="btn btn-primary" disabled={busy}>Хадгалах</button></div>
        </form>
      </Section>
      <Section title="API түлхүүр" description="Өөрийн программаас API ашиглах бол. Түлхүүр зөвхөн нэг удаа харагдана.">
        {apiKey ? (
          <div className="note success">
            <p>Шинэ түлхүүрээ одоо хадгална уу — дахин харагдахгүй:</p>
            <div className="copy-row">
              <input readOnly className="mono" value={apiKey} onFocus={(e) => e.target.select()} aria-label="API түлхүүр" />
              <button className="btn btn-small" onClick={async () => toast(await copyText(apiKey) ? 'Хуулагдлаа' : 'Гараар хуулна уу')}>Хуулах</button>
            </div>
          </div>
        ) : <button className="btn" disabled={busy} onClick={() => void rotateKey()}>Шинэ түлхүүр үүсгэх</button>}
      </Section>
    </>
  );
}

function PasswordForm() {
  const { busy, run } = useAction();
  const [form, setForm] = useState({ currentPassword: '', newPassword: '', confirm: '' });
  const mismatch = form.confirm !== '' && form.confirm !== form.newPassword;

  const save = async (e: FormEvent) => {
    e.preventDefault();
    if (mismatch) return;
    if (await run(() => api.post('/api/auth/password', { currentPassword: form.currentPassword, newPassword: form.newPassword }),
      'Нууц үг солигдлоо. Бусад төхөөрөмж дээрх нэвтрэлт хаагдлаа.')) {
      setForm({ currentPassword: '', newPassword: '', confirm: '' });
    }
  };

  return (
    <Section title="Нууц үг солих">
      <form className="stack" onSubmit={save}>
        <Field label="Одоогийн нууц үг"><input required type="password" autoComplete="current-password" value={form.currentPassword} onChange={(e) => setForm({ ...form, currentPassword: e.target.value })} /></Field>
        <Field label="Шинэ нууц үг" hint="Дор хаяж 8 тэмдэгт"><input required minLength={8} type="password" autoComplete="new-password" value={form.newPassword} onChange={(e) => setForm({ ...form, newPassword: e.target.value })} /></Field>
        <Field label="Шинэ нууц үг давтах"><input required type="password" autoComplete="new-password" value={form.confirm} onChange={(e) => setForm({ ...form, confirm: e.target.value })} /></Field>
        {mismatch && <div className="form-error">Нууц үг таарахгүй байна</div>}
        <div><button className="btn btn-primary" disabled={busy || mismatch}>Солих</button></div>
      </form>
    </Section>
  );
}
