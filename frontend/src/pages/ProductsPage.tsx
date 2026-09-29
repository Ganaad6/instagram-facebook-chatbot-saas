import { useRef, useState, type FormEvent } from 'react';
import { api } from '../api';
import { useMe } from '../auth';
import { useResource } from '../hooks';
import { useToast } from '../components/Toast';
import { Empty, ErrorBox, Field, Modal, PageHeader, Spinner } from '../components/ui';
import { formatMoney } from '../format';
import type { Category, Product } from '../types';

export function ProductsPage() {
  const { business } = useMe();
  const base = `/api/businesses/${business.id}`;
  const categories = useResource<Category[]>(`${base}/categories`);
  const products = useResource<Product[]>(`${base}/products`);
  const [selected, setSelected] = useState<number | 'all'>('all');
  const [editing, setEditing] = useState<Product | 'new' | null>(null);
  const [editingCategory, setEditingCategory] = useState<Category | 'new' | null>(null);

  const visible = (products.data ?? []).filter((p) => selected === 'all' || p.categoryId === selected);
  const activeCategories = (categories.data ?? []).filter((c) => c.isActive);

  if (categories.error || products.error) {
    return <ErrorBox message={categories.error || products.error || ''} onRetry={() => { void categories.reload(); void products.reload(); }} />;
  }
  if (!categories.data || !products.data) return <Spinner />;

  return (
    <>
      <PageHeader title="Бараа" actions={
        <button className="btn btn-primary" disabled={activeCategories.length === 0} onClick={() => setEditing('new')}>+ Бараа нэмэх</button>
      }>
        Бот хэрэглэгчдэд идэвхтэй ангилал, бараануудыг цэс болгон харуулна.
      </PageHeader>

      <div className="chips" role="tablist" aria-label="Ангилал">
        <button className={`chip${selected === 'all' ? ' active' : ''}`} onClick={() => setSelected('all')}>
          Бүгд <span className="chip-count">{products.data.length}</span>
        </button>
        {categories.data.map((c) => (
          <span key={c.id} className={`chip${selected === c.id ? ' active' : ''}${c.isActive ? '' : ' inactive'}`}>
            <button className="chip-main" onClick={() => setSelected(c.id)}>
              {c.name}{!c.isActive && ' (нуусан)'} <span className="chip-count">{products.data!.filter((p) => p.categoryId === c.id).length}</span>
            </button>
            <button className="chip-edit" aria-label={`${c.name} засах`} onClick={() => setEditingCategory(c)}>✎</button>
          </span>
        ))}
        <button className="chip chip-add" onClick={() => setEditingCategory('new')}>+ Ангилал</button>
      </div>

      {categories.data.length === 0 ? (
        <section className="card"><Empty title="Эхлээд ангилал нэмнэ үү">Жишээ нь: “Цэцэг”, “Бэлэг”, “Хувцас”.</Empty></section>
      ) : visible.length === 0 ? (
        <section className="card"><Empty title="Бараа алга">“+ Бараа нэмэх” товчоор эхний бараагаа нэмнэ үү.</Empty></section>
      ) : (
        <div className="product-grid">
          {visible.map((p) => (
            <button key={p.id} className={`product-card${p.isActive ? '' : ' inactive'}`} onClick={() => setEditing(p)}>
              <div className="product-image">
                {p.imageUrl ? <img src={p.imageUrl} alt="" loading="lazy" /> : <span className="muted">Зураггүй</span>}
              </div>
              <div className="product-info">
                <strong>{p.name}</strong>
                <span>{formatMoney(p.price)}</span>
                <span className="muted small">{p.categoryName}{!p.isActive && ' · Дууссан'}</span>
              </div>
            </button>
          ))}
        </div>
      )}

      {editing && (
        <ProductForm businessId={business.id} product={editing === 'new' ? null : editing}
                     categories={categories.data} defaultCategory={selected === 'all' ? activeCategories[0]?.id : selected}
                     onClose={() => setEditing(null)} onSaved={() => { setEditing(null); void products.reload(); }} />
      )}
      {editingCategory && (
        <CategoryForm businessId={business.id} category={editingCategory === 'new' ? null : editingCategory}
                      onClose={() => setEditingCategory(null)}
                      onSaved={() => { setEditingCategory(null); void categories.reload(); void products.reload(); }} />
      )}
    </>
  );
}

function ProductForm({ businessId, product, categories, defaultCategory, onClose, onSaved }: {
  businessId: number; product: Product | null; categories: Category[]; defaultCategory?: number;
  onClose: () => void; onSaved: () => void;
}) {
  const toast = useToast();
  const [name, setName] = useState(product?.name ?? '');
  const [price, setPrice] = useState(product ? String(product.price) : '');
  const [categoryId, setCategoryId] = useState<number | undefined>(product?.categoryId ?? defaultCategory);
  const [description, setDescription] = useState(product?.description ?? '');
  const [isActive, setIsActive] = useState(product?.isActive ?? true);
  const [image, setImage] = useState<{ id: string | null; url: string | null; removed: boolean }>(
    { id: null, url: product?.imageUrl ?? null, removed: false });
  const [uploading, setUploading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const fileInput = useRef<HTMLInputElement>(null);

  const upload = async (file: File) => {
    setUploading(true);
    setError(null);
    try {
      const form = new FormData();
      form.append('file', file);
      const result = await api.post<{ id: string; url: string }>(`/api/businesses/${businessId}/media`, form);
      setImage({ id: result.id, url: result.url, removed: false });
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setUploading(false);
    }
  };

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setSaving(true);
    setError(null);
    const body = {
      name: name.trim(), price: Number(price), categoryId, description: description.trim(),
      ...(image.id ? { imageFileId: image.id } : {}),
    };
    try {
      if (product) {
        await api.put(`/api/businesses/${businessId}/products/${product.id}`,
          { ...body, isActive, ...(image.removed ? { removeImage: true } : {}) });
      } else {
        await api.post(`/api/businesses/${businessId}/products`, body);
      }
      toast(product ? 'Хадгаллаа' : 'Бараа нэмэгдлээ');
      onSaved();
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal title={product ? 'Бараа засах' : 'Шинэ бараа'} onClose={onClose} footer={
      <>
        <button className="btn" type="button" onClick={onClose}>Болих</button>
        <button className="btn btn-primary" form="product-form" disabled={saving || uploading}>{saving ? 'Хадгалж байна…' : 'Хадгалах'}</button>
      </>
    }>
      <form id="product-form" className="stack" onSubmit={submit}>
        <div className="image-picker">
          <div className="product-image large">
            {image.url && !image.removed ? <img src={image.url} alt="" /> : <span className="muted">Зураггүй</span>}
          </div>
          <div className="stack-tight">
            <input ref={fileInput} type="file" accept="image/jpeg,image/png,image/webp,image/gif" hidden
                   onChange={(e) => { const f = e.target.files?.[0]; if (f) void upload(f); e.target.value = ''; }} />
            <button type="button" className="btn btn-small" disabled={uploading} onClick={() => fileInput.current?.click()}>
              {uploading ? 'Хуулж байна…' : image.url && !image.removed ? 'Зураг солих' : 'Зураг оруулах'}
            </button>
            {image.url && !image.removed && (
              <button type="button" className="btn btn-small btn-ghost" onClick={() => setImage({ id: null, url: null, removed: true })}>Зураг хасах</button>
            )}
            <span className="field-hint">JPEG, PNG, WebP · 5MB хүртэл. Бот цэсэнд зургийг илгээнэ.</span>
          </div>
        </div>
        <Field label="Нэр">
          <input required maxLength={255} value={name} onChange={(e) => setName(e.target.value)} />
        </Field>
        <div className="row-2">
          <Field label="Үнэ (₮)">
            <input required type="number" min="1" step="1" inputMode="numeric" value={price} onChange={(e) => setPrice(e.target.value)} />
          </Field>
          <Field label="Ангилал">
            <select required value={categoryId ?? ''} onChange={(e) => setCategoryId(Number(e.target.value))}>
              <option value="" disabled>Сонгох</option>
              {categories.map((c) => <option key={c.id} value={c.id}>{c.name}{c.isActive ? '' : ' (нуусан)'}</option>)}
            </select>
          </Field>
        </div>
        <Field label="Тайлбар" hint="Заавал биш">
          <textarea rows={3} value={description} onChange={(e) => setDescription(e.target.value)} />
        </Field>
        {product && (
          <label className="toggle">
            <input type="checkbox" checked={isActive} onChange={(e) => setIsActive(e.target.checked)} />
            <span>Бэлэн байгаа (унтраавал бот энэ барааг санал болгохгүй)</span>
          </label>
        )}
        {error && <div className="form-error" role="alert">{error}</div>}
      </form>
    </Modal>
  );
}

function CategoryForm({ businessId, category, onClose, onSaved }: {
  businessId: number; category: Category | null; onClose: () => void; onSaved: () => void;
}) {
  const toast = useToast();
  const [name, setName] = useState(category?.name ?? '');
  const [sortOrder, setSortOrder] = useState(String(category?.sortOrder ?? 0));
  const [isActive, setIsActive] = useState(category?.isActive ?? true);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setSaving(true);
    try {
      const body = { name: name.trim(), sortOrder: Number(sortOrder) || 0 };
      if (category) {
        await api.put(`/api/businesses/${businessId}/categories/${category.id}`, { ...body, isActive });
      } else {
        await api.post(`/api/businesses/${businessId}/categories`, body);
      }
      toast('Хадгаллаа');
      onSaved();
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal title={category ? 'Ангилал засах' : 'Шинэ ангилал'} onClose={onClose} footer={
      <>
        <button className="btn" type="button" onClick={onClose}>Болих</button>
        <button className="btn btn-primary" form="category-form" disabled={saving}>Хадгалах</button>
      </>
    }>
      <form id="category-form" className="stack" onSubmit={submit}>
        <Field label="Нэр"><input required maxLength={255} value={name} onChange={(e) => setName(e.target.value)} /></Field>
        <Field label="Эрэмбэ" hint="Бага тоотой нь цэсэнд түрүүлж харагдана">
          <input type="number" step="1" value={sortOrder} onChange={(e) => setSortOrder(e.target.value)} />
        </Field>
        {category && (
          <label className="toggle">
            <input type="checkbox" checked={isActive} onChange={(e) => setIsActive(e.target.checked)} />
            <span>Цэсэнд харуулах</span>
          </label>
        )}
        {error && <div className="form-error" role="alert">{error}</div>}
      </form>
    </Modal>
  );
}
