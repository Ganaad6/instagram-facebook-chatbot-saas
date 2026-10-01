import { useEffect, useRef, useState, type FormEvent, type UIEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api } from '../api';
import { useMe } from '../auth';
import { useResource } from '../hooks';
import { useToast } from '../components/Toast';
import { Empty, ErrorBox, Spinner } from '../components/ui';
import { formatDateTime, formatShort, isFuture, PLATFORM_LABELS } from '../format';
import type { ChatSummary, Message, Page } from '../types';

const SENDER_LABELS = { CUSTOMER: 'Хэрэглэгч', BOT: 'Бот', AGENT: 'Ажилтан' } as const;

function chatName(chat: ChatSummary): string {
  return chat.displayName || `${PLATFORM_LABELS[chat.platform]} хэрэглэгч #${chat.customerId}`;
}

export function ChatsPage() {
  const { business } = useMe();
  const { id } = useParams();
  const selectedId = id ? Number(id) : null;
  const [waitingOnly, setWaitingOnly] = useState(false);
  const [page, setPage] = useState(0);
  const base = `/api/businesses/${business.id}/chats`;
  const chats = useResource<Page<ChatSummary>>(`${base}?page=${page}&size=30${waitingOnly ? '&waiting=true' : ''}`, 10000);
  // Counted by the server: waiting customers can be on any page of the full list
  const waiting = useResource<Page<ChatSummary>>(`${base}?waiting=true&size=1`, 10000);
  const waitingCount = waiting.data?.totalElements ?? 0;
  const list = chats.data?.content ?? [];
  const totalPages = chats.data?.totalPages;

  // Chats leave the waiting list once answered, which can empty the last page
  useEffect(() => {
    if (totalPages !== undefined && page > 0 && page >= totalPages) setPage(Math.max(totalPages - 1, 0));
  }, [page, totalPages]);

  const reloadAll = () => { void chats.reload(); void waiting.reload(); };

  return (
    <div className={`chats${selectedId ? ' has-selection' : ''}`}>
      <section className="chat-list card flush">
        <div className="chat-list-head">
          <h1>Чат</h1>
          <label className="toggle small">
            <input type="checkbox" checked={waitingOnly} onChange={(e) => { setWaitingOnly(e.target.checked); setPage(0); }} />
            <span>Хүн хүлээж буй{waitingCount > 0 && ` (${waitingCount})`}</span>
          </label>
        </div>
        {chats.error && <ErrorBox message={chats.error} onRetry={chats.reload} />}
        {!chats.data && !chats.error && <Spinner />}
        {chats.data && list.length === 0 && (
          <Empty title={waitingOnly ? 'Хүн хүлээж буй хэрэглэгч алга' : 'Чат алга'}>
            {!waitingOnly && 'Хэрэглэгч таны хуудас руу бичихэд энд харагдана.'}
          </Empty>
        )}
        <ul className="list">
          {list.map((c) => (
            <li key={c.customerId}>
              <Link to={`/chats/${c.customerId}`} className={`list-row chat-row${c.customerId === selectedId ? ' selected' : ''}`}>
                <div className="list-main">
                  <strong>{chatName(c)}</strong>
                  <span className="muted ellipsis">
                    {c.lastMessageSender && c.lastMessageSender !== 'CUSTOMER' && `${SENDER_LABELS[c.lastMessageSender]}: `}{c.lastMessage}
                  </span>
                </div>
                <div className="list-side">
                  <span className="muted small">{formatShort(c.lastMessageAt ?? c.lastInteractionAt)}</span>
                  {c.handoffRequestedAt ? <span className="badge badge-warning">Хүн хүлээж буй</span>
                    : isFuture(c.botPausedUntil) ? <span className="badge badge-neutral">Бот зогссон</span> : null}
                </div>
              </Link>
            </li>
          ))}
        </ul>
        {chats.data && chats.data.totalPages > 1 && (
          <div className="pager">
            <button className="btn btn-small" disabled={page === 0} onClick={() => setPage(page - 1)}>←</button>
            <span className="muted">{page + 1} / {chats.data.totalPages}</span>
            <button className="btn btn-small" disabled={page + 1 >= chats.data.totalPages} onClick={() => setPage(page + 1)}>→</button>
          </div>
        )}
      </section>

      <section className="chat-pane card flush">
        {selectedId ? <Conversation key={selectedId} businessId={business.id} customerId={selectedId} onChanged={reloadAll} />
          : <Empty title="Чат сонгоно уу">Хэрэглэгч “оператор” гэж бичвэл бот зогсоод таныг хүлээнэ.</Empty>}
      </section>
    </div>
  );
}

function Conversation({ businessId, customerId, onChanged }: { businessId: number; customerId: number; onChanged: () => void }) {
  const navigate = useNavigate();
  const toast = useToast();
  const base = `/api/businesses/${businessId}/customers/${customerId}`;
  const chat = useResource<ChatSummary>(`/api/businesses/${businessId}/chats/${customerId}`, 10000);
  const messages = useResource<Message[]>(`${base}/messages?limit=200`, 5000);
  const [text, setText] = useState('');
  const [sending, setSending] = useState(false);
  const bottom = useRef<HTMLDivElement>(null);
  // Follow new messages only while the view is at the bottom, so reading older ones isn't interrupted
  const atBottom = useRef(true);
  const lastId = messages.data?.[messages.data.length - 1]?.id;

  useEffect(() => { if (atBottom.current) bottom.current?.scrollIntoView({ block: 'end' }); }, [lastId]);

  const onScroll = (e: UIEvent<HTMLDivElement>) => {
    const el = e.currentTarget;
    atBottom.current = el.scrollHeight - el.scrollTop - el.clientHeight < 80;
  };

  const refresh = async () => { await Promise.all([messages.reload(), chat.reload()]); onChanged(); };

  const send = async (e: FormEvent) => {
    e.preventDefault();
    if (!text.trim()) return;
    setSending(true);
    try {
      await api.post(`${base}/messages`, { text: text.trim() });
      setText('');
      atBottom.current = true;
      await refresh();
    } catch (err) {
      toast(err instanceof Error ? err.message : String(err), 'error');
    } finally {
      setSending(false);
    }
  };

  const botAction = async (action: 'pause-bot' | 'resume-bot') => {
    try {
      await api.post(`${base}/${action}`);
      toast(action === 'pause-bot' ? 'Бот энэ хэрэглэгчид түр зогслоо' : 'Бот дахин ажиллаж эхэллээ');
      await refresh();
    } catch (err) {
      toast(err instanceof Error ? err.message : String(err), 'error');
    }
  };

  const c = chat.data;
  const paused = c && (isFuture(c.botPausedUntil) || !!c.handoffRequestedAt);

  return (
    <div className="conversation">
      <header className="conversation-head">
        <button className="icon-btn back" onClick={() => navigate('/chats')} aria-label="Буцах">←</button>
        <div className="conversation-title">
          <strong>{c ? chatName(c) : '…'}</strong>
          {c && <span className="muted small">{PLATFORM_LABELS[c.platform]}{paused && c.botPausedUntil && ` · Бот ${formatDateTime(c.botPausedUntil)} хүртэл зогссон`}</span>}
        </div>
        {c && (paused
          ? <button className="btn btn-small btn-primary" onClick={() => void botAction('resume-bot')}>Ботод шилжүүлэх</button>
          : <button className="btn btn-small" onClick={() => void botAction('pause-bot')}>Ботыг зогсоох</button>)}
      </header>
      {c?.handoffRequestedAt && (
        <div className="note warning">Хэрэглэгч {formatShort(c.handoffRequestedAt)}-д ажилтантай холбогдох хүсэлт гаргасан. Хариу бичнэ үү.</div>
      )}
      <div className="messages" onScroll={onScroll}>
        {messages.error && <ErrorBox message={messages.error} />}
        {!messages.data && !messages.error && <Spinner />}
        {messages.data?.map((m) => (
          <div key={m.id} className={`bubble bubble-${m.senderType.toLowerCase()}`}>
            <div className="bubble-text">{m.content}</div>
            <div className="bubble-meta">{SENDER_LABELS[m.senderType]} · {formatShort(m.sentAt)}</div>
          </div>
        ))}
        <div ref={bottom} />
      </div>
      <form className="composer" onSubmit={send}>
        <textarea rows={2} maxLength={2000} placeholder="Хариу бичих… (илгээхэд бот энэ хэрэглэгчид түр зогсоно)"
                  value={text} onChange={(e) => setText(e.target.value)}
                  onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); void send(e); } }} />
        <button className="btn btn-primary" disabled={sending || !text.trim()}>{sending ? '…' : 'Илгээх'}</button>
      </form>
    </div>
  );
}
