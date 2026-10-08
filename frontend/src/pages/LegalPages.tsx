import { useEffect, useState, type ReactNode } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { Bot } from 'lucide-react';
import { api, ApiError } from '../api';
import { Spinner } from '../components/ui';
import { formatDateTime } from '../format';

// Public pages Meta App Review links to (privacy policy, terms, data-deletion instructions and
// status). Mongolian first, then English for Meta's reviewers. Who runs the service comes from
// the server's LEGAL_* settings.

const EFFECTIVE_DATE = '2026-10-08';

interface LegalInfo { operatorName: string; contactEmail: string; address: string; messageRetentionDays: number }

function useLegalInfo(): LegalInfo | null {
  const [info, setInfo] = useState<LegalInfo | null>(null);
  useEffect(() => {
    api.get<LegalInfo>('/api/public/legal').then(setInfo).catch(() => setInfo({
      operatorName: '', contactEmail: '', address: '', messageRetentionDays: 365,
    }));
  }, []);
  return info;
}

function LegalLayout({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="legal-page">
      <header className="legal-head">
        <Link to="/" className="auth-brand"><span className="brand-mark" aria-hidden="true"><Bot size={20} /></span> Дэлгүүрийн самбар</Link>
        <nav className="legal-nav" aria-label="Бодлого">
          <Link to="/privacy">Нууцлал</Link>
          <Link to="/terms">Үйлчилгээний нөхцөл</Link>
          <Link to="/data-deletion">Мэдээлэл устгах</Link>
        </nav>
      </header>
      <article className="legal-body">
        <h1>{title}</h1>
        {children}
      </article>
    </div>
  );
}

function Operator({ info }: { info: LegalInfo }) {
  const name = info.operatorName || 'Үйлчилгээ эрхлэгч';
  return (
    <>
      <strong>{name}</strong>
      {info.address && <>, {info.address}</>}
      {info.contactEmail && <> — <a href={`mailto:${info.contactEmail}`}>{info.contactEmail}</a></>}
    </>
  );
}

function Contact({ info }: { info: LegalInfo }) {
  return info.contactEmail
    ? <a href={`mailto:${info.contactEmail}`}>{info.contactEmail}</a>
    : <>үйлчилгээ эрхлэгчийн имэйл</>;
}

function retention(days: number, mn: boolean): string {
  if (days <= 0) return mn ? 'дэлгүүрийн бүртгэл идэвхтэй байх хугацаанд' : 'for as long as the shop account is active';
  return mn ? `${days} хоног` : `${days} days`;
}

export function PrivacyPage() {
  const info = useLegalInfo();
  if (!info) return <LegalLayout title="Нууцлалын бодлого"><Spinner /></LegalLayout>;
  return (
    <LegalLayout title="Нууцлалын бодлого">
      <p className="muted">Хүчин төгөлдөр болсон: {EFFECTIVE_DATE}</p>
      <p>
        Энэхүү үйлчилгээ нь дэлгүүрүүдэд Facebook Messenger болон Instagram-аар захиалга авах чатбот, удирдлагын
        самбар олгодог. Үйлчилгээг <Operator info={info} /> эрхэлдэг.
      </p>

      <h2>1. Хэний мэдээлэл, ямар үүрэгтэй</h2>
      <p>
        Дэлгүүрийн эзэмшигч, ажилтнуудын бүртгэлийн мэдээллийг бид өөрсдөө хариуцан боловсруулна. Дэлгүүртэй чатаар
        харилцаж буй хэрэглэгчдийн мэдээллийг тухайн дэлгүүрийн нэрийн өмнөөс, түүний захиалгыг биелүүлэх зорилгоор
        л боловсруулдаг; энэ мэдээллийг хэрхэн ашиглахыг тухайн дэлгүүр хариуцна.
      </p>

      <h2>2. Цуглуулдаг мэдээлэл</h2>
      <ul>
        <li><strong>Дэлгүүрийн хэрэглэгчид:</strong> нэр, имэйл, нууц үгийн хэш (нууц үг өөрөө хадгалагддаггүй), нэвтэрсэн огноо.</li>
        <li><strong>Facebook холболт:</strong> холболт хийсэн Facebook хэрэглэгчийн аппын ID, Page болон Instagram бүртгэлийн ID,
          Page-ийн хандалтын токен (шифрлэж хадгална).</li>
        <li><strong>QPay:</strong> дэлгүүрийн QPay merchant нэвтрэх нэр, нууц үг (шифрлэсэн), нэхэмжлэхийн код.</li>
        <li><strong>Чатын хэрэглэгчид:</strong> Messenger/Instagram-ын тухайн Page-д хамаарах ID, бот болон ажилтантай
          солилцсон мессежүүд, захиалгад өгсөн нэр, утас, хүргэлтийн хаяг, захиалга ба төлбөрийн төлөв. Картын
          мэдээллийг бид авдаггүй — төлбөрийг QPay болон банкууд шууд хүлээн авна.</li>
      </ul>

      <h2>3. Ашиглах зорилго</h2>
      <p>
        Зөвхөн үйлчилгээг ажиллуулахад: мессежид хариулах, захиалга бүртгэх, төлбөрийн нэхэмжлэх үүсгэж шалгах,
        дэлгүүрийн ажилтанд харилцааг харуулах, аюулгүй байдлыг хангах. Мэдээллийг зар сурталчилгаанд ашигладаггүй,
        худалддаггүй.
      </p>

      <h2>4. Хэнтэй хуваалцдаг</h2>
      <ul>
        <li><strong>Meta</strong> (Facebook, Instagram) — мессеж илгээх, хүлээн авахад.</li>
        <li><strong>QPay</strong> — тухайн захиалгын дүнгээр нэхэмжлэх үүсгэх, төлбөр шалгахад.</li>
        <li>Дэлгүүр өөрөө тохируулсан бол тухайн дэлгүүрийн мэдэгдлийн вебхүүк (шинэ захиалга, төлбөр).</li>
        <li>Сервер байршуулалтын үйлчилгээ үзүүлэгч — зөвхөн хадгалалт, ажиллагааны хүрээнд.</li>
        <li>Хуулиар шаардсан тохиолдолд эрх бүхий байгууллага.</li>
      </ul>

      <h2>5. Хадгалах хугацаа</h2>
      <ul>
        <li>Чатын мессежийг {retention(info.messageRetentionDays, true)}-ийн дараа автоматаар устгана.</li>
        <li>Захиалгын бүртгэл дэлгүүрийн борлуулалтын баримт тул дэлгүүрийн бүртгэл идэвхтэй байх хугацаанд хадгалагдана.</li>
        <li>Дэлгүүр хэрэглэгчийн мэдээллийг хүссэн үед устгаж болно: чатын түүх устаж, захиалгаас нэр, утас, хаяг арилна.</li>
        <li>Дэлгүүрийн бүртгэлийг хаалгах хүсэлт ирэхэд түүний мэдээллийг 30 хоногийн дотор устгана (хуулиар хадгалах
          шаардлагатайгаас бусад).</li>
      </ul>

      <h2>6. Хамгаалалт</h2>
      <p>
        Бүх холболт HTTPS-ээр явна. Хандалтын токен, QPay нууц үгийг AES-GCM-ээр шифрлэж, нууц үгийг BCrypt хэшээр
        хадгална. Дэлгүүр бүр зөвхөн өөрийн мэдээллийг харна.
      </p>

      <h2>7. Таны эрх, мэдээллээ устгуулах</h2>
      <ul>
        <li><strong>Дэлгүүртэй чатласан хэрэглэгч:</strong> тухайн дэлгүүрт хандаж мэдээллээ харах, засах, устгуулах
          хүсэлт гаргана уу. Дэлгүүртэй холбогдож чадахгүй бол <Contact info={info} /> хаягаар бидэнд бичээрэй.</li>
        <li><strong>Facebook-ээр холболт хийсэн хүн:</strong> <Link to="/data-deletion">Мэдээлэл устгах</Link> хуудсанд
          заасны дагуу аппыг Facebook-ээс хасахад холболтын мэдээлэл автоматаар устна.</li>
        <li><strong>Дэлгүүрийн эзэмшигч:</strong> бүртгэлээ хаалгах, мэдээллээ авах хүсэлтээ <Contact info={info} /> хаягаар илгээнэ үү.</li>
      </ul>

      <h2>8. Өөрчлөлт</h2>
      <p>Бодлогыг өөрчилбөл энэ хуудсанд шинэ огноотой нийтэлж, томоохон өөрчлөлтийг дэлгүүрүүдэд урьдчилан мэдэгдэнэ.</p>

      <h2>9. Холбоо барих</h2>
      <p><Operator info={info} /></p>

      <hr />
      <section lang="en">
        <h2>Privacy Policy (English)</h2>
        <p className="muted">Effective: {EFFECTIVE_DATE}</p>
        <p>
          This service gives shops a chatbot and dashboard for taking orders through Facebook Messenger and Instagram.
          It is operated by <Operator info={info} />.
        </p>
        <h3>Roles</h3>
        <p>
          We are responsible for the account data of shop owners and staff. Data about people who chat with a shop is
          processed on that shop's behalf, only to handle its orders; the shop is responsible for how it uses it.
        </p>
        <h3>Data we collect</h3>
        <ul>
          <li><strong>Shop users:</strong> name, email, password hash (never the password), sign-in times.</li>
          <li><strong>Facebook connection:</strong> the app-scoped ID of the Facebook user who connected the shop, the
            Page and Instagram account IDs, and the Page access token (encrypted).</li>
          <li><strong>QPay:</strong> the shop's QPay merchant username, password (encrypted) and invoice code.</li>
          <li><strong>Chat customers:</strong> their Page-scoped Messenger/Instagram ID, messages exchanged with the bot
            and staff, the name, phone and delivery address given for orders, and order and payment status. We never
            receive card details; payments go to QPay and the banks directly.</li>
        </ul>
        <h3>How we use it</h3>
        <p>
          Only to run the service: replying to messages, recording orders, creating and checking payment invoices,
          showing conversations to the shop's staff, and keeping the service secure. We do not sell data or use it for
          advertising.
        </p>
        <h3>Who we share it with</h3>
        <p>
          Meta (to send and receive messages); QPay (to create and check invoices for an order); the shop's own
          notification webhook if the shop configured one; our hosting provider; and authorities where the law
          requires it.
        </p>
        <h3>Retention</h3>
        <p>
          Chat messages are deleted automatically after {retention(info.messageRetentionDays, false)}. Orders are the
          shop's sales records and are kept while the shop's account is active. A shop can erase a customer at any time
          (chat history deleted, name, phone and address removed from orders). When a shop asks to close its account,
          its data is deleted within 30 days, except where the law requires us to keep it.
        </p>
        <h3>Security</h3>
        <p>
          All traffic uses HTTPS. Access tokens and QPay passwords are encrypted with AES-GCM; passwords are stored as
          BCrypt hashes. Each shop can only see its own data.
        </p>
        <h3>Your rights and data deletion</h3>
        <p>
          If you chatted with a shop, ask that shop to access, correct or delete your data, or write to us at{' '}
          <Contact info={info} /> if you cannot reach it. If you connected a Facebook Page, follow the{' '}
          <Link to="/data-deletion">data deletion instructions</Link>: removing the app from Facebook deletes the
          connection data automatically. Shop owners can request account closure or a copy of their data at{' '}
          <Contact info={info} />.
        </p>
        <h3>Changes and contact</h3>
        <p>
          Changes are published on this page with a new date, and shops are notified of significant ones in advance.
          Contact: <Operator info={info} />.
        </p>
      </section>
    </LegalLayout>
  );
}

export function TermsPage() {
  const info = useLegalInfo();
  if (!info) return <LegalLayout title="Үйлчилгээний нөхцөл"><Spinner /></LegalLayout>;
  return (
    <LegalLayout title="Үйлчилгээний нөхцөл">
      <p className="muted">Хүчин төгөлдөр болсон: {EFFECTIVE_DATE}</p>
      <p>
        Эдгээр нөхцөл нь <Operator info={info} /> (цаашид «бид») болон үйлчилгээнд бүртгүүлсэн дэлгүүр (цаашид
        «дэлгүүр»)-ийн хоорондын харилцааг зохицуулна. Бүртгүүлснээр та дэлгүүрийн нэрийн өмнөөс эдгээр нөхцөлийг
        хүлээн зөвшөөрч байна.
      </p>

      <h2>1. Үйлчилгээ</h2>
      <p>
        Дэлгүүрийн Facebook Page, Instagram бүртгэлд холбогдож, бараа үзүүлэх, захиалга авах, QPay-ээр төлбөрийн
        холбоос илгээх чатбот болон удирдлагын самбар. Төлбөр дэлгүүрийн өөрийн QPay данс руу шууд ордог; бид
        мөнгийг дамжуулж, хадгалдаггүй.
      </p>

      <h2>2. Бүртгэл</h2>
      <p>
        Үнэн зөв мэдээлэл өгч, нууц үг, API түлхүүрээ нууцлах нь дэлгүүрийн үүрэг. Дэлгүүрийн бүртгэлээр хийгдсэн
        үйлдлийг дэлгүүр хариуцна. Ажилтнуудад эрх олгох, хаах нь эзэмшигчийн үүрэг.
      </p>

      <h2>3. Дэлгүүрийн үүрэг</h2>
      <ul>
        <li>Зөвхөн хууль ёсны бараа, үйлчилгээ санал болгох, үнэ болон нөхцөлийг үнэн зөв байлгах.</li>
        <li>Meta-гийн (Facebook, Instagram) болон QPay-ийн нөхцөл, бодлогыг мөрдөх.</li>
        <li>Хэрэглэгчдийн хувийн мэдээллийг зөвхөн захиалга биелүүлэхэд ашиглаж, хуулийн дагуу хамгаалах; хэрэглэгч
          мэдээллээ устгуулахыг хүсвэл самбараас устгах.</li>
        <li>Захиалгын хүргэлт, буцаалт, баталгаа, төлбөрийн буцаан олголтыг өөрөө хариуцах.</li>
        <li>Спам, залилан, бусдын эрхийг зөрчих зорилгоор үйлчилгээг ашиглахгүй байх.</li>
      </ul>

      <h2>4. Төлбөр</h2>
      <p>
        Үйлчилгээний төлбөр, хугацааг бидний тохиролцоогоор тогтооно. Төлбөр хугацаандаа төлөгдөөгүй бол бид
        урьдчилан мэдэгдээд бүртгэлийг түр зогсоож болно; зогссон үед бот хариулахгүй, самбарт нэвтрэх боломжгүй.
        Төлсний дараа шууд сэргээнэ.
      </p>

      <h2>5. Хүртээмж, хариуцлага</h2>
      <p>
        Бид үйлчилгээг тасралтгүй, найдвартай ажиллуулахыг хичээх боловч Meta, QPay зэрэг гуравдагч талын үйлчилгээ,
        интернэтийн тасалдлаас үүдэх саатлыг баталгаажуулах боломжгүй. Үйлчилгээг «байгаа байдлаар нь» олгоно.
        Хуулиар зөвшөөрөгдөх хэмжээнд бидний нийт хариуцлага сүүлийн 3 сард дэлгүүрийн төлсөн үйлчилгээний
        төлбөрөөс хэтрэхгүй; ашгийн алдагдал зэрэг шууд бус хохирлыг хариуцахгүй.
      </p>

      <h2>6. Мэдээлэл</h2>
      <p>
        Дэлгүүрийн бараа, захиалга, хэрэглэгчийн мэдээлэл дэлгүүрийнх хэвээр байна. Бид түүнийг зөвхөн{' '}
        <Link to="/privacy">Нууцлалын бодлого</Link>-д заасны дагуу үйлчилгээ үзүүлэхэд ашиглана.
      </p>

      <h2>7. Цуцлах</h2>
      <p>
        Дэлгүүр хүссэн үедээ <Contact info={info} /> хаягаар бичиж үйлчилгээг цуцалж болно. Эдгээр нөхцөлийг ноцтой
        зөрчсөн бол бид бүртгэлийг зогсоож, цуцалж болно. Цуцалсны дараа 30 хоногийн дотор дэлгүүрийн мэдээллийг
        устгана; түүнээс өмнө хүсвэл захиалгын жагсаалтаа (CSV) татаж авах боломжтой.
      </p>

      <h2>8. Өөрчлөлт, хууль</h2>
      <p>
        Нөхцөлийг өөрчлөх бол 14 хоногийн өмнө мэдэгдэнэ; үүний дараа үргэлжлүүлэн ашиглавал шинэ нөхцөлийг
        зөвшөөрсөнд тооцно. Эдгээр нөхцөлд Монгол Улсын хууль үйлчилж, маргааныг эхлээд харилцан тохиролцож,
        шийдэгдэхгүй бол Монгол Улсын шүүхээр шийдвэрлэнэ.
      </p>

      <h2>9. Холбоо барих</h2>
      <p><Operator info={info} /></p>

      <hr />
      <section lang="en">
        <h2>Terms of Service (English)</h2>
        <p className="muted">Effective: {EFFECTIVE_DATE}</p>
        <p>
          These terms govern the relationship between <Operator info={info} /> ("we") and the shop that signs up
          ("the shop"). By signing up you accept them on the shop's behalf.
        </p>
        <h3>Service</h3>
        <p>
          A chatbot and dashboard connected to the shop's Facebook Page and Instagram account that shows products, takes
          orders and sends QPay payment links. Payments go directly to the shop's own QPay account; we never handle or
          hold the money.
        </p>
        <h3>Accounts</h3>
        <p>
          The shop provides accurate information, keeps its passwords and API key secret, and is responsible for
          activity under its account, including granting and revoking staff access.
        </p>
        <h3>Shop obligations</h3>
        <p>
          Offer only lawful goods and services with accurate prices and terms; follow Meta's and QPay's terms and
          policies; use customers' personal data only to fulfil their orders, protect it as the law requires and erase
          it from the dashboard when a customer asks; handle delivery, returns, warranties and refunds itself; and not
          use the service for spam, fraud or infringing others' rights.
        </p>
        <h3>Fees</h3>
        <p>
          Fees and billing periods are agreed separately. If payment is overdue we may suspend the account after notice:
          the bot stops replying and dashboard sign-in is disabled until payment, after which it is restored at once.
        </p>
        <h3>Availability and liability</h3>
        <p>
          We work to keep the service available but cannot guarantee against outages of third parties such as Meta and
          QPay or of the internet. The service is provided "as is". To the extent the law allows, our total liability is
          limited to the fees the shop paid in the previous 3 months, and we are not liable for indirect losses such as
          lost profits.
        </p>
        <h3>Data</h3>
        <p>
          The shop's catalog, orders and customer data remain the shop's. We use them only to provide the service, as
          described in the <Link to="/privacy">Privacy Policy</Link>.
        </p>
        <h3>Termination</h3>
        <p>
          The shop may cancel at any time by writing to <Contact info={info} />. We may suspend or terminate an account
          for serious breach of these terms. After termination the shop's data is deleted within 30 days; the shop can
          export its orders (CSV) before then.
        </p>
        <h3>Changes and governing law</h3>
        <p>
          We give 14 days' notice of changes; continued use afterwards means acceptance. These terms are governed by the
          laws of Mongolia; disputes are first settled by negotiation, failing which by the courts of Mongolia.
        </p>
      </section>
    </LegalLayout>
  );
}

interface DeletionStatus { confirmationCode: string; status: string; completedAt: string }

export function DataDeletionPage() {
  const info = useLegalInfo();
  const code = useSearchParams()[0].get('code');
  const [status, setStatus] = useState<DeletionStatus | null | undefined>(code ? undefined : null);
  const [statusError, setStatusError] = useState<string | null>(null);

  useEffect(() => {
    if (!code) return;
    api.get<DeletionStatus>(`/api/public/data-deletion/${encodeURIComponent(code)}`)
      .then(setStatus)
      .catch((e) => {
        setStatus(null);
        setStatusError(e instanceof ApiError && e.status === 404 ? 'Ийм баталгаажуулах код олдсонгүй.' : 'Төлөв шалгаж чадсангүй.');
      });
  }, [code]);

  if (!info) return <LegalLayout title="Мэдээлэл устгах"><Spinner /></LegalLayout>;
  return (
    <LegalLayout title="Мэдээлэл устгах">
      {code && (
        <div className={`note ${status ? 'success' : statusError ? 'warning' : ''}`} role="status">
          {status === undefined && <Spinner />}
          {status && (
            <span>
              Хүсэлт <strong>{status.confirmationCode}</strong> {formatDateTime(status.completedAt)}-д биелсэн: таны
              Facebook холболтын мэдээлэл устсан. / Request completed: your Facebook connection data has been deleted.
            </span>
          )}
          {statusError && <span>{statusError}</span>}
        </div>
      )}

      <h2>Facebook-ээр холболт хийсэн бол</h2>
      <p>Дэлгүүрийн Facebook Page-ийг холбосон хүн аппыг Facebook-ээс хасахад холболтын мэдээлэл автоматаар устна:</p>
      <ol>
        <li>Facebook-ийн <strong>Settings &amp; privacy → Settings</strong> руу орно.</li>
        <li><strong>Apps and websites</strong> (Business integrations) хэсгийг нээнэ.</li>
        <li>Энэ аппыг сонгоод <strong>Remove</strong> дарна.</li>
      </ol>
      <p>
        Ингэхэд таны Facebook хэрэглэгчийн ID, Page болон Instagram-ын ID, хандалтын токен манай системээс устаж, бот
        тухайн Page-д хариулахаа болино. Facebook танд баталгаажуулах код өгөх ба энэ хуудаснаас төлвийг шалгаж болно.
      </p>

      <h2>Дэлгүүртэй чатласан бол</h2>
      <p>
        Тухайн дэлгүүрт хандаж мэдээллээ устгуулахыг хүснэ үү — дэлгүүр самбараасаа таны чатын түүхийг устгаж,
        захиалгаас нэр, утас, хаягийг арилгана. Дэлгүүртэй холбогдож чадахгүй бол <Contact info={info} /> хаягаар дэлгүүрийн
        нэр, өөрийн Facebook/Instagram нэрийг бичиж илгээгээрэй; 30 хоногийн дотор шийдвэрлэнэ.
      </p>

      <h2>Дэлгүүрийн бүртгэлээ устгуулах</h2>
      <p>Эзэмшигч <Contact info={info} /> хаягаар бүртгэлтэй имэйлээсээ бичнэ үү. 30 хоногийн дотор устгана.</p>

      <hr />
      <section lang="en">
        <h2>Data deletion instructions (English)</h2>
        <h3>If you connected a Facebook Page</h3>
        <ol>
          <li>In Facebook, open <strong>Settings &amp; privacy → Settings</strong>.</li>
          <li>Open <strong>Apps and websites</strong> (Business integrations).</li>
          <li>Select this app and click <strong>Remove</strong>.</li>
        </ol>
        <p>
          This deletes your Facebook user ID, the Page and Instagram IDs and the access token from our system, and the
          bot stops answering on that Page. Facebook gives you a confirmation code; you can check its status on this
          page.
        </p>
        <h3>If you chatted with a shop</h3>
        <p>
          Ask that shop to delete your data: it can erase your chat history and remove your name, phone and address from
          its orders in the dashboard. If you cannot reach the shop, email <Contact info={info} /> with the shop's name
          and your Facebook/Instagram name; we handle requests within 30 days.
        </p>
        <h3>Closing a shop account</h3>
        <p>The owner emails <Contact info={info} /> from the account's email address; we delete it within 30 days.</p>
      </section>
    </LegalLayout>
  );
}
