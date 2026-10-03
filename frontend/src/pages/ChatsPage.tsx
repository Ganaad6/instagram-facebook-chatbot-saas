import { Fragment, useEffect, useRef, useState, type FormEvent, type UIEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, Bot, ChevronLeft, ChevronRight, Clock, PauseCircle, Send } from 'lucide-react';
import { api } from '../api';
import { useMe } from '../auth';
import { useNow, useResource } from '../hooks';
import { useToast } from '../components/Toast';
import { Avatar, Empty, ErrorBox, OrderStatusBadge, PaymentBadge, Spinner } from '../components/ui';
import { formatDateTime, formatDay, formatMoney, formatShort, formatWaiting, isFuture, PLATFORM_LABELS } from '../format';
import type { ChatSummary, Message, Order, Page } from '../types';

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
  const filter = (next: boolean) => { setWaitingOnly(next); setPage(0); };

  return (
    <div className={`chats${selectedId ? ' has-selection' : ''}`}>
      <section className="chat-list" aria-label="Чатын жагсаалт">
        <div className="chat-list-head">
          <h1>Чат</h1>
          <div className="segmented" role="group" aria-label="Шүүлтүүр">
            <button className={`segment${!waitingOnly ? ' active' : ''}`} aria-pressed={!waitingOnly} onClick={() => filter(false)}>Бүгд</button>
            <button className={`segment${waitingOnly ? ' active' : ''}`} aria-pressed={waitingOnly} onClick={() => filter(true)}>
              Хүн хүлээж буй{waitingCount > 0 && <> <span className="segment-count">{waitingCount}</span></>}
            </button>
          </div>
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
              <Link to={`/chats/${c.customerId}`}
                    className={`chat-row${c.customerId === selectedId ? ' selected' : ''}${c.handoffRequestedAt ? ' waiting' : ''}`}
                    aria-current={c.customerId === selectedId ? 'page' : undefined}>
                <Avatar id={c.customerId} name={c.displayName} platform={c.platform} />
                <div className="chat-row-body">
                  <div className="chat-row-line">
                    <strong className="ellipsis">{chatName(c)}</strong>
                    <span className="time">{formatShort(c.lastMessageAt ?? c.lastInteractionAt)}</span>
                  </div>
                  <div className="chat-row-line">
                    <span className="preview ellipsis">
                      {c.lastMessageSender && c.lastMessageSender !== 'CUSTOMER' && `${SENDER_LABELS[c.lastMessageSender]}: `}{c.lastMessage}
                    </span>
                    {c.handoffRequestedAt ? <span className="badge badge-warning">Хүн хүлээж буй</span>
                      : isFuture(c.botPausedUntil) ? <span className="badge badge-neutral">Бот зогссон</span> : null}
                  </div>
                </div>
              </Link>
            </li>
          ))}
        </ul>
        {chats.data && chats.data.totalPages > 1 && (
          <div className="pager">
            <button className="icon-btn" aria-label="Өмнөх" disabled={page === 0} onClick={() => setPage(page - 1)}><ChevronLeft size={18} /></button>
            <span className="muted">{page + 1} / {chats.data.totalPages}</span>
            <button className="icon-btn" aria-label="Дараах" disabled={page + 1 >= chats.data.totalPages} onClick={() => setPage(page + 1)}><ChevronRight size={18} /></button>
          </div>
        )}
      </section>

      {selectedId ? <ChatDetail key={selectedId} businessId={business.id} customerId={selectedId} onChanged={reloadAll} />
        : (
          <section className="chat-pane">
            <Empty title="Чат сонгоно уу">Хэрэглэгч “оператор” гэж бичвэл бот зогсоод таныг хүлээнэ.</Empty>
          </section>
        )}
    </div>
  );
}

function ChatDetail({ businessId, customerId, onChanged }: { businessId: number; customerId: number; onChanged: () => void }) {
  const chat = useResource<ChatSummary>(`/api/businesses/${businessId}/chats/${customerId}`, 10000);
  const orders = useResource<Page<Order>>(`/api/businesses/${businessId}/orders?customerId=${customerId}&size=5`, 20000);
  return (
    <>
      <section className="chat-pane" aria-label="Харилцаа">
        <Conversation businessId={businessId} customerId={customerId} chat={chat.data} latestOrder={orders.data?.content[0]}
                      onChanged={async () => { await chat.reload(); onChanged(); }} />
      </section>
      {chat.data && <CustomerPanel chat={chat.data} orders={orders.data} />}
    </>
  );
}

function Conversation({ businessId, customerId, chat: c, latestOrder, onChanged }: {
  businessId: number; customerId: number; chat: ChatSummary | undefined; latestOrder: Order | undefined; onChanged: () => Promise<void>;
}) {
  const navigate = useNavigate();
  const toast = useToast();
  const now = useNow();
  const base = `/api/businesses/${businessId}/customers/${customerId}`;
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

  const refresh = async () => { await Promise.all([messages.reload(), onChanged()]); };

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

  const paused = c && (isFuture(c.botPausedUntil) || !!c.handoffRequestedAt);

  return (
    <div className="conversation">
      <header className="conversation-head">
        <button className="icon-btn back" onClick={() => navigate('/chats')} aria-label="Буцах"><ArrowLeft size={20} /></button>
        {c && <Avatar id={c.customerId} name={c.displayName} />}
        <div className="conversation-title">
          <strong className="ellipsis">{c ? chatName(c) : '…'}</strong>
          {c && (
            <span className="muted small">
              {PLATFORM_LABELS[c.platform]}
              {paused && (isFuture(c.botPausedUntil) ? ` · Бот ${formatShort(c.botPausedUntil)} хүртэл зогссон` : ' · Бот зогссон')}
            </span>
          )}
        </div>
        {c && (paused
          ? <button className="btn btn-primary" onClick={() => void botAction('resume-bot')}><Bot size={16} aria-hidden="true" /> Ботод шилжүүлэх</button>
          : <button className="btn" onClick={() => void botAction('pause-bot')}><PauseCircle size={16} aria-hidden="true" /> Ботыг зогсоох</button>)}
      </header>
      {c?.handoffRequestedAt && (
        <div className="note warning" role="status">
          <Clock size={18} aria-hidden="true" />
          <span>
            <strong>{formatWaiting(c.handoffRequestedAt, now)} хүлээж байна.</strong>{' '}
            Хэрэглэгч {formatShort(c.handoffRequestedAt)}-д ажилтантай ярих хүсэлт гаргасан.
          </span>
        </div>
      )}
      {latestOrder && (
        <Link className="conversation-order" to={`/orders/${latestOrder.id}`}>
          <span className="ellipsis"><strong>#{latestOrder.id}</strong> {latestOrder.productName} · {formatMoney(latestOrder.totalAmount)}</span>
          <OrderStatusBadge status={latestOrder.status} />
        </Link>
      )}
      <div className="messages" onScroll={onScroll}>
        {messages.error && <ErrorBox message={messages.error} />}
        {!messages.data && !messages.error && <Spinner />}
        {messages.data?.map((m, i) => {
          const day = formatDay(m.sentAt, now);
          const newDay = i === 0 || formatDay(messages.data![i - 1].sentAt, now) !== day;
          return (
            <Fragment key={m.id}>
              {newDay && <div className="day-divider">{day}</div>}
              <div className={`bubble bubble-${m.senderType.toLowerCase()}`}>
                <div className="bubble-text">{m.content}</div>
                <div className="bubble-meta">
                  {/* The day is in the divider above, so the time alone is enough */}
                  {m.senderType !== 'CUSTOMER' && `${SENDER_LABELS[m.senderType]} · `}{formatDateTime(m.sentAt).slice(11)}
                </div>
              </div>
            </Fragment>
          );
        })}
        <div ref={bottom} />
      </div>
      <form className="composer" onSubmit={send}>
        <div className="composer-row">
          <label className="sr-only" htmlFor="reply">Хариу</label>
          <textarea id="reply" rows={2} maxLength={2000} placeholder="Хариу бичих…"
                    value={text} onChange={(e) => setText(e.target.value)}
                    onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); void send(e); } }} />
          <button className="btn btn-primary send" aria-label="Илгээх" disabled={sending || !text.trim()}>
            {sending ? <span className="spinner" aria-hidden="true" /> : <Send size={18} aria-hidden="true" />}
          </button>
        </div>
        <div className="composer-hint">Enter — илгээх · Shift+Enter — шинэ мөр · Таныг бичихэд бот энэ хэрэглэгчид түр зогсоно</div>
      </form>
    </div>
  );
}

/** Who the customer is and what they ordered, beside the conversation on wide screens. */
function CustomerPanel({ chat, orders }: { chat: ChatSummary; orders: Page<Order> | undefined }) {
  // Phone and address are only known from the order form
  const contact = orders?.content.find((o) => o.phone || o.address);
  return (
    <aside className="customer-panel" aria-label="Хэрэглэгч">
      <div className="customer-card">
        <Avatar id={chat.customerId} name={chat.displayName} large />
        <strong>{chatName(chat)}</strong>
        <span className={`platform-pill ${chat.platform}`}>{PLATFORM_LABELS[chat.platform]}</span>
      </div>
      <dl className="details">
        <dt>Утас</dt><dd>{contact?.phone ? <a href={`tel:${contact.phone}`}>{contact.phone}</a> : '—'}</dd>
        <dt>Хаяг</dt><dd>{contact?.address || '—'}</dd>
        <dt>Сүүлд</dt><dd>{formatDateTime(chat.lastMessageAt ?? chat.lastInteractionAt)}</dd>
      </dl>
      <div className="customer-orders">
        <div className="card-head">
          <h2>Захиалгууд</h2>
          {orders && <span className="muted small">{orders.totalElements}</span>}
        </div>
        {!orders && <Spinner />}
        {orders && orders.content.length === 0 && <span className="muted small">Захиалга хийгээгүй байна</span>}
        {orders?.content.map((o) => (
          <Link key={o.id} to={`/orders/${o.id}`} className="order-card">
            <strong>#{o.id} · {o.productName} × {o.quantity}</strong>
            <span className="badges">
              <span className="amount">{formatMoney(o.totalAmount)}</span>
              <OrderStatusBadge status={o.status} />
              {o.paymentStatus !== 'NOT_REQUESTED' && <PaymentBadge status={o.paymentStatus} />}
            </span>
          </Link>
        ))}
      </div>
    </aside>
  );
}
