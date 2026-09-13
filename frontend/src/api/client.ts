import axios, { AxiosError } from 'axios';

const TOKEN_KEY = 'pragati_token';

export const api = axios.create({ baseURL: '/api', timeout: 120000 });

export function getToken(): string | null { return localStorage.getItem(TOKEN_KEY); }
export function setToken(t: string | null) {
  if (t) localStorage.setItem(TOKEN_KEY, t);
  else localStorage.removeItem(TOKEN_KEY);
}

api.interceptors.request.use((cfg) => {
  const t = getToken();
  if (t) (cfg.headers as any).Authorization = `Bearer ${t}`;
  return cfg;
});

api.interceptors.response.use(
  (r) => r,
  (err: AxiosError) => {
    if (err.response?.status === 401 && !err.config?.url?.includes('/auth/login')) {
      setToken(null);
      if (window.location.pathname !== '/login') window.location.href = '/login';
    }
    return Promise.reject(err);
  }
);

export class ApiError extends Error {
  status: number;
  requestId: string;
  fieldErrors: Record<string, string> | null;
  constructor(status: number, message: string, requestId?: string, fieldErrors?: Record<string, string>) {
    super(message);
    this.status = status;
    this.requestId = requestId ?? '';
    this.fieldErrors = fieldErrors ?? null;
  }
}

export function errMsg(e: unknown): string {
  if (e instanceof ApiError) return e.message;
  if (axios.isAxiosError(e)) {
    const d = e.response?.data as any;
    if (d?.message) return d.message;
    return 'Something went wrong. Please try again.';
  }
  return e instanceof Error ? e.message : 'Something went wrong. Please try again.';
}

export async function apiGet<T = any>(url: string, params?: any): Promise<T> {
  try {
    const r = await api.get<T>(url, { params });
    return r.data;
  } catch (e: any) {
    const d = e?.response?.data;
    throw new ApiError(e?.response?.status ?? 0, d?.message || 'Something went wrong.', d?.requestId, d?.fieldErrors);
  }
}
export async function apiPost<T = any>(url: string, body?: any, config?: any): Promise<T> {
  try {
    const r = await api.post<T>(url, body, config);
    return r.data;
  } catch (e: any) {
    const d = e?.response?.data;
    throw new ApiError(e?.response?.status ?? 0, d?.message || 'Something went wrong.', d?.requestId, d?.fieldErrors);
  }
}
export async function apiPut<T = any>(url: string, body?: any): Promise<T> {
  try {
    const r = await api.put<T>(url, body);
    return r.data;
  } catch (e: any) {
    const d = e?.response?.data;
    throw new ApiError(e?.response?.status ?? 0, d?.message || 'Something went wrong.', d?.requestId, d?.fieldErrors);
  }
}
export async function apiPatch<T = any>(url: string, body?: any): Promise<T> {
  try {
    const r = await api.patch<T>(url, body);
    return r.data;
  } catch (e: any) {
    const d = e?.response?.data;
    throw new ApiError(e?.response?.status ?? 0, d?.message || 'Something went wrong.', d?.requestId, d?.fieldErrors);
  }
}

/** Authenticated file download: fetches the file as a blob (with the JWT)
 *  and triggers a browser save. Never opens a raw URL — no JSON 401 screens. */
export async function apiDownload(url: string, filename: string): Promise<void> {
  const t = getToken();
  let resp: Response;
  try {
    resp = await fetch(url, { headers: t ? { Authorization: `Bearer ${t}` } : {} });
  } catch {
    throw new ApiError(0, 'The download could not be started. Please try again.');
  }
  if (!resp.ok) {
    let message = 'The download could not be completed. Please try again.';
    try {
      const d = await resp.json();
      if (d?.message) message = d.message;
    } catch { /* not JSON */ }
    if (resp.status === 401) {
      setToken(null);
      if (window.location.pathname !== '/login') window.location.href = '/login';
      return;
    }
    throw new ApiError(resp.status, message);
  }
  const blob = await resp.blob();
  const objectUrl = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = objectUrl;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(objectUrl), 4000);
}
