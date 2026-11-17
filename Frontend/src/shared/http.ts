import type { Asset, Envelope, FieldPage, Gateway, Profile } from './contracts';

export const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
export class ApiError extends Error {
  constructor(public status: number, public code = '', public requestId?: string) {
    super(status === 401 ? 'La sesión terminó. Inicia sesión para continuar.'
      : status === 403 ? 'Tu cuenta no tiene permiso para esta operación.'
      : status === 404 ? 'El activo no está disponible en este proyecto.'
      : status === 422 ? 'Revisa los identificadores del proyecto y del activo.'
      : status === 429 ? 'Hay demasiados intentos. Espera antes de volver a intentarlo.'
      : 'No se pudo completar la solicitud. Vuelve a intentarlo.');
  }
}
export function message(error: unknown): string { return error instanceof ApiError ? error.message : 'No se pudo conectar. Comprueba la conexión y vuelve a intentarlo.'; }
export const aborted = (error: unknown) => error instanceof DOMException && error.name === 'AbortError';

export function createHttpGateway(fetcher: typeof fetch = fetch): Gateway {
  async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
    let response: Response;
    try {
      const signal = init.signal ? AbortSignal.any([init.signal, AbortSignal.timeout(15000)]) : AbortSignal.timeout(15000);
      response = await fetcher(path, { ...init, signal, credentials: 'same-origin', cache: 'no-store', redirect: 'error', headers: { Accept: 'application/json', ...init.headers } });
    } catch (error) { if (aborted(error)) throw error; throw new ApiError(0, 'CONNECTION_FAILED'); }
    if (response.status === 204) return undefined as T;
    const body = await response.json().catch(() => null);
    if (!response.ok) {
      const requestId = typeof body?.request_id === 'string' && UUID.test(body.request_id) ? body.request_id : undefined;
      throw new ApiError(response.status, typeof body?.code === 'string' && /^[A-Z_]{1,80}$/.test(body.code) ? body.code : '', requestId);
    }
    if (!body || typeof body !== 'object' || !('data' in body) || !UUID.test(body.meta?.request_id ?? '')) throw new ApiError(502, 'INVALID_RESPONSE');
    return body as T;
  }
  async function csrf() {
    const result = await request<Envelope<{ header_name: string; token: string }>>('/api/v1/auth/csrf');
    // The existing Spring contract uses this header. Never turn arbitrary response fields into headers.
    if (result.data.header_name !== 'X-CSRF-TOKEN' || typeof result.data.token !== 'string' || !result.data.token) throw new ApiError(502, 'INVALID_RESPONSE');
    return { [result.data.header_name]: result.data.token };
  }
  function resource(project: string, asset: string) {
    if (!UUID.test(project) || !UUID.test(asset)) throw new ApiError(422, 'INVALID_IDENTIFIER');
    return `/api/v1/projects/${project}/assets/${asset}`;
  }
  const api: Gateway = {
    async profile(signal) {
      const result = await request<Envelope<Profile>>('/api/v1/auth/me', { signal });
      if (!UUID.test(result.data?.id ?? '') || typeof result.data.display_name !== 'string' || typeof result.data.email !== 'string') throw new ApiError(502, 'INVALID_RESPONSE');
      return result.data;
    },
    async login(email, password) {
      const headers = await csrf();
      await request('/api/v1/auth/login', { method: 'POST', headers: { ...headers, 'Content-Type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams({ email, password }) });
      await csrf(); // Session fixation protection changes the session and CSRF token.
      return api.profile();
    },
    async logout() { await request('/api/v1/auth/logout', { method: 'POST', headers: await csrf() }); },
    async asset(project, asset, signal) {
      const result = await request<Envelope<Asset>>(resource(project, asset), { signal });
      const data = result.data;
      if (data?.project_id !== project || data.id !== asset || !UUID.test(data.metadata_version_id) || !UUID.test(data.organization_id) || !Array.isArray(data.tags) || !data.origin || typeof data.name !== 'string') throw new ApiError(502, 'INVALID_CONTEXT');
      return data;
    },
    async fields(project, asset, version, page, signal) {
      if (!UUID.test(version) || !Number.isInteger(page) || page < 1) throw new ApiError(422);
      const query = new URLSearchParams({ metadata_version_id: version, page: String(page), page_size: '25', sort: 'position,id' });
      const result = await request<FieldPage>(`${resource(project, asset)}/fields?${query}`, { signal });
      const info = result.meta.page_info;
      if (!Array.isArray(result.data) || result.data.length > 25 || info?.page !== page || info.page_size !== 25 || info.returned_count !== result.data.length || typeof info.has_next !== 'boolean'
        || result.data.some(field => field.project_id !== project || field.asset_id !== asset || field.metadata_version_id !== version)) throw new ApiError(502, 'INVALID_CONTEXT');
      return result;
    },
  };
  return api;
}
