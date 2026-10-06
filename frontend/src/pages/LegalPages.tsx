import { useEffect, useState, type ReactNode } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { Bot } from 'lucide-react';
import { api, ApiError } from '../api';
import { Spinner } from '../components/ui';
import { formatDateTime } from '../format';

// Public pages Meta App Review requires: privacy policy, terms and data-deletion instructions
// (plus the status page Meta's data-deletion callback links to). Meta's reviewers read English,
// shop customers Mongolian, so each page has both.

type Lang = 'en' | 'mn';

interface LegalInfo {
  operatorName: string;
  contactEmail: string;
  policyUpdated: string;
}

function useLang(): [Lang, (lang: Lang) => void] {
  const [params, setParams] = useSearchParams();
  const fromQuery = params.get('lang');
  const lang: Lang = fromQuery === 'en' || fromQuery === 'mn'
    ? fromQuery
    : navigator.language.toLowerCase().startsWith('mn') ? 'mn' : 'en';
  const setLang = (next: Lang) => {
    const updated = new URLSearchParams(params);
    updated.set('lang', next);
    setParams(updated, { replace: true });
  };
  return [lang, setLang];
}

function useLegalInfo(): LegalInfo | null {
  const [info, setInfo] = useState<LegalInfo | null>(null);
  useEffect(() => {
    api.get<LegalInfo>('/api/public/legal').then(setInfo).catch(() => setInfo({ operatorName: '', contactEmail: '', policyUpdated: '' }));
  }, []);
  return info;
}

function LegalLayout({ lang, setLang, brand, children }: { lang: Lang; setLang: (lang: Lang) => void; brand: string; children: ReactNode }) {
  const q = `?lang=${lang}`;
  return (
    <div className="legal-page">
      <header className="legal-header">
        <Link to="/" className="auth-brand"><span className="brand-mark" aria-hidden="true"><Bot size={20} /></span> {brand || (lang === 'en' ? 'Shop chatbot' : 'Дэлгүүрийн чатбот')}</Link>
        <div className="legal-lang" role="group" aria-label="Language">
          <button type="button" className={lang === 'en' ? 'active' : ''} onClick={() => setLang('en')}>English</button>
          <button type="button" className={lang === 'mn' ? 'active' : ''} onClick={() => setLang('mn')}>Монгол</button>
        </div>
      </header>
      <article className="legal-body">{children}</article>
      <footer className="legal-footer">
        <Link to={'/privacy' + q}>{lang === 'en' ? 'Privacy policy' : 'Нууцлалын бодлого'}</Link>
        <Link to={'/terms' + q}>{lang === 'en' ? 'Terms of service' : 'Үйлчилгээний нөхцөл'}</Link>
        <Link to={'/data-deletion' + q}>{lang === 'en' ? 'Data deletion' : 'Мэдээлэл устгах'}</Link>
      </footer>
    </div>
  );
}

function Operator({ info }: { info: LegalInfo }) {
  return <strong>{info.operatorName || '—'}</strong>;
}

function Contact({ info }: { info: LegalInfo }) {
  return info.contactEmail ? <a href={`mailto:${info.contactEmail}`}>{info.contactEmail}</a> : <span>—</span>;
}

export function PrivacyPage() {
  const [lang, setLang] = useLang();
  const info = useLegalInfo();
  if (!info) return <div className="center-screen"><Spinner /></div>;
  const q = `?lang=${lang}`;

  return (
    <LegalLayout lang={lang} setLang={setLang} brand={info.operatorName}>
      {lang === 'en' ? (
        <>
          <h1>Privacy policy</h1>
          <p className="muted">Last updated: {info.policyUpdated}</p>
          <p>
            This service (the "Service") lets online shops answer their customers automatically on Facebook Messenger and
            Instagram, take orders in the chat and manage them in a web dashboard. It is operated by <Operator info={info} /> ("we").
            This policy explains what information the Service handles and why.
          </p>

          <h2>Information we handle</h2>
          <h3>Shop owners and staff</h3>
          <ul>
            <li>Name, email address and password (stored only as a one-way hash) for dashboard accounts.</li>
            <li>When a shop connects Facebook: the Facebook user ID of the person who connected it (as issued to our app), the
              Facebook Page ID, the linked Instagram professional account ID, and the Page access token Meta issues. The token is
              stored encrypted.</li>
            <li>Shop settings, products and photos the shop uploads, and optional QPay merchant credentials (stored encrypted).</li>
          </ul>
          <h3>People who message a shop</h3>
          <ul>
            <li>The ID that Messenger or Instagram assigns to you for that shop's Page or account.</li>
            <li>The messages you exchange with the shop through the Service and when they were sent.</li>
            <li>Details you type in while ordering: name, phone number, delivery address, the product and quantity.</li>
            <li>The order and payment status. Payments are made on QPay; we never receive card or bank details.</li>
          </ul>
          <p>We do not request your Facebook or Instagram profile name, photo, friends or any other profile information.</p>

          <h2>How we use it</h2>
          <p>
            Only to provide the Service: replying to messages on the shop's behalf, letting the shop's staff read and answer
            conversations, recording orders, creating payment links and showing the shop its order statistics. We do not sell
            personal information, use it for advertising, or use it to train AI models.
          </p>

          <h2>Who we share it with</h2>
          <ul>
            <li><strong>The shop</strong> you message, which uses the information to fulfil your order. For customer
              conversations and orders the shop decides how they are used; we process them on its behalf.</li>
            <li><strong>Meta Platforms</strong>, to deliver the Service's replies on Messenger and Instagram.</li>
            <li><strong>QPay</strong>, when the shop has turned on payments: the order number and amount, to create an invoice.</li>
            <li>An order-notification address the shop itself configures, which receives new orders.</li>
            <li>Our hosting provider, which stores the data on our behalf, and authorities when the law requires it.</li>
          </ul>

          <h2>How long we keep it</h2>
          <p>
            Information is kept while the shop uses the Service. When the person who connected a shop removes our app in their
            Facebook settings, the Page access token and Facebook IDs are deleted immediately. When a shop closes its account, all of its data, including
            customer conversations and orders, is deleted within 30 days.
          </p>

          <h2>Security</h2>
          <p>
            All traffic uses HTTPS, access tokens and payment credentials are encrypted at rest, and each shop can only see its
            own customers and orders.
          </p>

          <h2>Your choices and deletion</h2>
          <p>
            You can ask for a copy of your information, its correction or deletion. See <Link to={'/data-deletion' + q}>data deletion</Link> for
            how. Customers may also ask the shop directly.
          </p>

          <h2>Children</h2>
          <p>The Service is not directed at children under 13.</p>

          <h2>Changes</h2>
          <p>We will post any change to this policy on this page and update the date above.</p>

          <h2>Contact</h2>
          <p><Operator info={info} /> · <Contact info={info} /></p>
        </>
      ) : (
        <>
          <h1>Нууцлалын бодлого</h1>
          <p className="muted">Сүүлд шинэчилсэн: {info.policyUpdated}</p>
          <p>
            Энэхүү үйлчилгээ ("Үйлчилгээ") нь онлайн дэлгүүрүүдэд Facebook Messenger болон Instagram дээр хэрэглэгчиддээ
            автоматаар хариулах, чат дотор захиалга авах, түүнийгээ вэб самбараас удирдах боломж олгоно. Үйлчилгээг <Operator info={info} /> ("бид")
            ажиллуулдаг. Энэхүү бодлого нь Үйлчилгээ ямар мэдээлэл боловсруулдаг, яагаад гэдгийг тайлбарлана.
          </p>

          <h2>Бидний боловсруулах мэдээлэл</h2>
          <h3>Дэлгүүрийн эзэмшигч, ажилтнууд</h3>
          <ul>
            <li>Самбарын бүртгэлийн нэр, имэйл хаяг, нууц үг (зөвхөн буцаан тайлагдахгүй хэлбэрээр хадгална).</li>
            <li>Дэлгүүр Facebook-ээ холбоход: холбосон хүний манай аппад олгогдсон Facebook хэрэглэгчийн ID, Facebook хуудасны ID,
              холбогдсон Instagram мэргэжлийн бүртгэлийн ID, Meta-гийн олгосон хуудасны хандалтын токен. Токеныг шифрлэж хадгална.</li>
            <li>Дэлгүүрийн тохиргоо, бараа, оруулсан зураг, мөн QPay мерчантын нэвтрэх мэдээлэл (шифрлэгдсэн).</li>
          </ul>
          <h3>Дэлгүүрт мессеж бичсэн хүмүүс</h3>
          <ul>
            <li>Messenger эсвэл Instagram тухайн дэлгүүрийн хувьд танд олгосон ID.</li>
            <li>Үйлчилгээгээр дамжуулан дэлгүүртэй солилцсон мессеж, илгээсэн цаг.</li>
            <li>Захиалга өгөхдөө бичсэн мэдээлэл: нэр, утас, хүргэлтийн хаяг, бараа, тоо ширхэг.</li>
            <li>Захиалга, төлбөрийн төлөв. Төлбөр QPay дээр хийгддэг бөгөөд бид карт, банкны мэдээлэл хүлээн авдаггүй.</li>
          </ul>
          <p>Бид таны Facebook, Instagram профайлын нэр, зураг, найзууд болон бусад мэдээллийг авдаггүй.</p>

          <h2>Мэдээллийг хэрхэн ашиглах</h2>
          <p>
            Зөвхөн Үйлчилгээг үзүүлэхэд: дэлгүүрийн нэрийн өмнөөс мессежид хариулах, ажилтнуудад харилцан яриаг уншиж
            хариулах боломж олгох, захиалга бүртгэх, төлбөрийн холбоос үүсгэх, дэлгүүрт захиалгын статистик харуулах. Бид
            хувийн мэдээллийг худалддаггүй, зар сурталчилгаанд болон хиймэл оюуны загвар сургахад ашигладаггүй.
          </p>

          <h2>Хэнтэй хуваалцах</h2>
          <ul>
            <li><strong>Таны мессеж бичсэн дэлгүүр</strong> — захиалгыг тань биелүүлэхэд ашиглана. Хэрэглэгчийн харилцан яриа,
              захиалгыг хэрхэн ашиглахыг дэлгүүр шийднэ; бид түүний өмнөөс боловсруулна.</li>
            <li><strong>Meta Platforms</strong> — Үйлчилгээний хариуг Messenger, Instagram-аар хүргэхэд.</li>
            <li><strong>QPay</strong> — дэлгүүр төлбөр идэвхжүүлсэн бол нэхэмжлэх үүсгэхэд захиалгын дугаар, дүн.</li>
            <li>Дэлгүүрийн өөрөө тохируулсан захиалгын мэдэгдлийн хаяг — шинэ захиалгыг хүлээн авна.</li>
            <li>Мэдээллийг бидний өмнөөс хадгалдаг хостингийн үйлчилгээ, мөн хуулиар шаардсан тохиолдолд эрх бүхий байгууллага.</li>
          </ul>

          <h2>Хадгалах хугацаа</h2>
          <p>
            Дэлгүүр Үйлчилгээг ашиглаж байх хугацаанд хадгална. Дэлгүүрийг холбосон хүн Facebook тохиргооноосоо манай аппыг устгахад
            хуудасны токен, Facebook ID-ууд шууд устана. Дэлгүүр бүртгэлээ хаахад хэрэглэгчийн харилцан яриа, захиалга зэрэг
            бүх мэдээлэл 30 хоногийн дотор устана.
          </p>

          <h2>Аюулгүй байдал</h2>
          <p>
            Бүх холболт HTTPS-ээр явагдана, хандалтын токен болон төлбөрийн нэвтрэх мэдээллийг шифрлэж хадгална, дэлгүүр бүр
            зөвхөн өөрийн хэрэглэгч, захиалгыг харна.
          </p>

          <h2>Таны эрх, мэдээлэл устгах</h2>
          <p>
            Та мэдээллийнхээ хуулбар авах, засуулах, устгуулахыг хүсэх эрхтэй. <Link to={'/data-deletion' + q}>Мэдээлэл устгах</Link> хуудаснаас
            заавар үзнэ үү. Мөн дэлгүүрт шууд хандаж болно.
          </p>

          <h2>Хүүхэд</h2>
          <p>Үйлчилгээ 13-аас доош насны хүүхдэд зориулагдаагүй.</p>

          <h2>Өөрчлөлт</h2>
          <p>Бодлогод орсон өөрчлөлтийг энэ хуудсанд нийтэлж, дээрх огноог шинэчилнэ.</p>

          <h2>Холбоо барих</h2>
          <p><Operator info={info} /> · <Contact info={info} /></p>
        </>
      )}
    </LegalLayout>
  );
}

export function TermsPage() {
  const [lang, setLang] = useLang();
  const info = useLegalInfo();
  if (!info) return <div className="center-screen"><Spinner /></div>;
  const q = `?lang=${lang}`;

  return (
    <LegalLayout lang={lang} setLang={setLang} brand={info.operatorName}>
      {lang === 'en' ? (
        <>
          <h1>Terms of service</h1>
          <p className="muted">Last updated: {info.policyUpdated}</p>
          <p>These terms apply to shops using the Service operated by <Operator info={info} />.</p>
          <h2>Using the Service</h2>
          <ul>
            <li>You may connect only Facebook Pages and Instagram accounts you are authorised to manage.</li>
            <li>You are responsible for your products, prices, the messages sent in your name and for fulfilling orders.</li>
            <li>You must follow Meta's Platform Terms, Messenger and Instagram policies, and the laws that apply to you, and
              tell your customers how you use their information.</li>
            <li>Keep your dashboard passwords private; you are responsible for activity on your accounts.</li>
          </ul>
          <h2>Payments</h2>
          <p>Customer payments go directly to your own QPay merchant account. We are not a party to sales between you and your customers.</p>
          <h2>Availability</h2>
          <p>We work to keep the Service running but cannot guarantee it is uninterrupted, or that Meta and QPay are always available.</p>
          <h2>Suspension and closing</h2>
          <p>We may suspend shops that break these terms or Meta's policies. You can remove our app from Facebook or close your account at any time; see the <Link to={'/privacy' + q}>privacy policy</Link> for what happens to the data.</p>
          <h2>Contact</h2>
          <p><Operator info={info} /> · <Contact info={info} /></p>
        </>
      ) : (
        <>
          <h1>Үйлчилгээний нөхцөл</h1>
          <p className="muted">Сүүлд шинэчилсэн: {info.policyUpdated}</p>
          <p>Энэхүү нөхцөл нь <Operator info={info} />-ийн ажиллуулдаг Үйлчилгээг ашиглаж буй дэлгүүрүүдэд хамаарна.</p>
          <h2>Үйлчилгээг ашиглах</h2>
          <ul>
            <li>Та зөвхөн удирдах эрхтэй Facebook хуудас, Instagram бүртгэлээ холбоно.</li>
            <li>Бараа, үнэ, таны нэрээр илгээгдэх мессеж, захиалгын биелэлтийг та хариуцна.</li>
            <li>Meta-гийн платформын нөхцөл, Messenger, Instagram-ын бодлого, холбогдох хуулийг дагаж, хэрэглэгчдэдээ
              мэдээллийг нь хэрхэн ашиглахаа мэдэгдэнэ.</li>
            <li>Самбарын нууц үгээ бусдад бүү өг; бүртгэл дээрх үйлдлийг та хариуцна.</li>
          </ul>
          <h2>Төлбөр</h2>
          <p>Хэрэглэгчийн төлбөр таны өөрийн QPay мерчант данс руу шууд орно. Бид таны болон хэрэглэгчийн хоорондох худалдааны тал биш.</p>
          <h2>Ажиллагаа</h2>
          <p>Бид Үйлчилгээг тасралтгүй ажиллуулахыг хичээдэг ч тасалдалгүй байх, Meta болон QPay үргэлж ажиллахыг баталж чадахгүй.</p>
          <h2>Түдгэлзүүлэх, хаах</h2>
          <p>Нөхцөл эсвэл Meta-гийн бодлогыг зөрчсөн дэлгүүрийг түдгэлзүүлж болно. Та хүссэн үедээ манай аппыг Facebook-ээс устгах, бүртгэлээ хааж болно; мэдээлэлд юу болохыг <Link to={'/privacy' + q}>нууцлалын бодлогоос</Link> үзнэ үү.</p>
          <h2>Холбоо барих</h2>
          <p><Operator info={info} /> · <Contact info={info} /></p>
        </>
      )}
    </LegalLayout>
  );
}

interface DeletionStatus {
  confirmationCode: string;
  requestedAt: string;
  completedAt: string | null;
}

function DeletionStatusBox({ code, lang }: { code: string; lang: Lang }) {
  const [status, setStatus] = useState<DeletionStatus | null | undefined>(undefined);
  useEffect(() => {
    api.get<DeletionStatus>(`/api/public/data-deletion/${encodeURIComponent(code)}`)
      .then(setStatus)
      .catch((e) => setStatus(e instanceof ApiError && e.status === 404 ? null : undefined));
  }, [code]);

  if (status === undefined) return <div className="legal-status"><Spinner /></div>;
  if (status === null) {
    return <div className="legal-status">{lang === 'en' ? `No request found with code ${code}.` : `${code} кодтой хүсэлт олдсонгүй.`}</div>;
  }
  return (
    <div className="legal-status">
      <strong>{lang === 'en' ? 'Request' : 'Хүсэлт'} {status.confirmationCode}</strong>
      <div>{lang === 'en' ? 'Received' : 'Хүлээн авсан'}: {formatDateTime(status.requestedAt)}</div>
      <div>
        {status.completedAt
          ? (lang === 'en' ? `Completed: ${formatDateTime(status.completedAt)}. Your Facebook connection data was deleted.`
            : `Дууссан: ${formatDateTime(status.completedAt)}. Таны Facebook холболтын мэдээлэл устсан.`)
          : (lang === 'en' ? 'In progress.' : 'Хийгдэж байна.')}
      </div>
    </div>
  );
}

export function DataDeletionPage() {
  const [lang, setLang] = useLang();
  const info = useLegalInfo();
  const code = useSearchParams()[0].get('code');
  if (!info) return <div className="center-screen"><Spinner /></div>;

  return (
    <LegalLayout lang={lang} setLang={setLang} brand={info.operatorName}>
      {lang === 'en' ? (
        <>
          <h1>Data deletion</h1>
          {code && <DeletionStatusBox code={code} lang={lang} />}
          <h2>Shop owners who connected Facebook</h2>
          <ol>
            <li>Open Facebook → <strong>Settings &amp; privacy</strong> → <strong>Settings</strong> → <strong>Apps and websites</strong>.</li>
            <li>Find our app and select <strong>Remove</strong>.</li>
            <li>Choose to send a data-deletion request. We delete your Facebook user ID, your Page and Instagram account IDs and
              the access token right away, and Facebook shows you a confirmation code with a link to this page to check the status.</li>
          </ol>
          <p>To delete the whole shop, including its products, orders and customer conversations, email <Contact info={info} /> from
            the shop's owner email. We delete it within 30 days and confirm by email.</p>
          <h2>Customers who messaged a shop</h2>
          <p>Ask the shop, or email <Contact info={info} /> with the shop's Facebook Page or Instagram name, the platform you used and
            roughly when you messaged. We delete your messages, orders and platform ID for that shop within 30 days and confirm by email.</p>
        </>
      ) : (
        <>
          <h1>Мэдээлэл устгах</h1>
          {code && <DeletionStatusBox code={code} lang={lang} />}
          <h2>Facebook-ээ холбосон дэлгүүрийн эзэмшигч</h2>
          <ol>
            <li>Facebook → <strong>Settings &amp; privacy</strong> → <strong>Settings</strong> → <strong>Apps and websites</strong> руу орно.</li>
            <li>Манай аппыг олоод <strong>Remove</strong> дарна.</li>
            <li>Мэдээлэл устгах хүсэлт илгээхийг сонгоно. Бид таны Facebook хэрэглэгчийн ID, хуудас, Instagram бүртгэлийн ID,
              хандалтын токеныг шууд устгах бөгөөд Facebook танд баталгаажуулах код, төлөвийг шалгах энэ хуудасны холбоосыг харуулна.</li>
          </ol>
          <p>Дэлгүүрийг бүхэлд нь (бараа, захиалга, хэрэглэгчийн харилцан яриа) устгуулах бол эзэмшигчийн имэйлээс <Contact info={info} /> руу
            бичнэ үү. 30 хоногийн дотор устгаж, имэйлээр баталгаажуулна.</p>
          <h2>Дэлгүүрт мессеж бичсэн хэрэглэгч</h2>
          <p>Дэлгүүрт хандах эсвэл <Contact info={info} /> руу дэлгүүрийн Facebook хуудас эсвэл Instagram нэр, ашигласан платформ, ойролцоогоор
            хэзээ бичсэнээ илгээнэ үү. Тухайн дэлгүүр дэх таны мессеж, захиалга, платформын ID-г 30 хоногийн дотор устгаж, имэйлээр баталгаажуулна.</p>
        </>
      )}
    </LegalLayout>
  );
}
