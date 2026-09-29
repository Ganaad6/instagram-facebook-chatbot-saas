// Thin fetch wrapper for the backend. The session cookie is sent automatically (same origin);
// state-changing requests echo the XSRF-TOKEN cookie in the X-XSRF-TOKEN header, which the
// backend requires for cookie-authenticated writes.

export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

type Json = Record<string, unknown> | unknown[];

let onUnauthorized: () => void = () => {};

/** Called when a request finds the session gone (expired, signed out elsewhere, deactivated). */
export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler;
}

function readCookie(name: string): string | null {
  const match = document.cookie.split('; ').find((c) => c.startsWith(name + '='));
  return match ? decodeURIComponent(match.slice(name.length + 1)) : null;
}

async function ensureCsrfCookie(): Promise<string | null> {
  let token = readCookie('XSRF-TOKEN');
  if (!token) {
    await fetch('/api/auth/csrf', { credentials: 'same-origin' });
    token = readCookie('XSRF-TOKEN');
  }
  return token;
}

const FALLBACK_ERRORS: Record<number, string> = {
  400: 'Хүсэлт буруу байна',
  401: 'Нэвтрэх шаардлагатай',
  403: 'Энэ үйлдлийг хийх эрх байхгүй байна',
  404: 'Олдсонгүй',
  409: 'Үйлдлийг одоо хийх боломжгүй байна',
  429: 'Хэт олон оролдлого. Түр хүлээгээд дахин оролдоно уу',
  502: 'Гадаад үйлчилгээ хариу өгсөнгүй',
};

async function request<T>(method: string, path: string, body?: Json | FormData): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (method !== 'GET') {
    const token = await ensureCsrfCookie();
    if (token) headers['X-XSRF-TOKEN'] = token;
  }
  let payload: BodyInit | undefined;
  if (body instanceof FormData) {
    payload = body;
  } else if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    payload = JSON.stringify(body);
  }

  let response: Response;
  try {
    response = await fetch(path, { method, headers, body: payload, credentials: 'same-origin' });
  } catch {
    throw new ApiError(0, 'Сервертэй холбогдож чадсангүй. Интернэт холболтоо шалгана уу');
  }

  if (response.status === 401 && !path.startsWith('/api/auth/login') && !path.startsWith('/api/auth/links')) {
    onUnauthorized();
  }
  if (!response.ok) {
    let message = FALLBACK_ERRORS[response.status] ?? 'Алдаа гарлаа. Дахин оролдоно уу';
    try {
      const data = await response.json();
      if (typeof data?.error === 'string' && data.error && response.status !== 500) message = data.error;
    } catch {
      // Not JSON; keep the fallback
    }
    throw new ApiError(response.status, message);
  }
  if (response.status === 204) return undefined as T;
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export const api = {
  get: <T>(path: string) => request<T>('GET', path),
  post: <T>(path: string, body?: Json | FormData) => request<T>('POST', path, body),
  put: <T>(path: string, body?: Json) => request<T>('PUT', path, body),
  delete: <T>(path: string) => request<T>('DELETE', path),
};

/** Builds a query string, skipping empty values. */
export function query(params: Record<string, string | number | undefined | null>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') search.set(key, String(value));
  }
  const s = search.toString();
  return s ? '?' + s : '';
}
