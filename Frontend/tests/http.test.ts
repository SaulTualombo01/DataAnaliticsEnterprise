import { describe, expect, it, vi } from 'vitest';
import { ApiError, createHttpGateway } from '../src/shared/http';
import examples from '../src/mocks/catalogo.examples.json';

const id = '00000000-0000-0000-0000-000000000001';
const profile = { id, email: 'empleado@example.test', display_name: 'Empleado' };
const envelope = (data: unknown) => ({ data, meta: { request_id: id } });
const response = (data: unknown, status = 200) => new Response(JSON.stringify(data), { status, headers: { 'Content-Type': 'application/json' } });
const asset = examples.asset_ok.body.data;

describe('cliente real', () => {
  it('usa cookie de mismo origen, formulario exacto y CSRF renovado al entrar', async () => {
    const fetcher = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(response(envelope({ header_name: 'X-CSRF-TOKEN', token: 'before-login' })))
      .mockResolvedValueOnce(response(envelope({ authenticated: true, user_id: id })))
      .mockResolvedValueOnce(response(envelope({ header_name: 'X-CSRF-TOKEN', token: 'after-login' })))
      .mockResolvedValueOnce(response(envelope(profile)));
    expect(await createHttpGateway(fetcher).login('empleado@example.test', 'test-password')).toEqual(profile);
    expect(fetcher.mock.calls.map(([url]) => url)).toEqual(['/api/v1/auth/csrf', '/api/v1/auth/login', '/api/v1/auth/csrf', '/api/v1/auth/me']);
    const init = fetcher.mock.calls[1][1]!;
    expect(init.credentials).toBe('same-origin'); expect(init.cache).toBe('no-store');
    expect(init.headers).toMatchObject({ 'X-CSRF-TOKEN': 'before-login', 'Content-Type': 'application/x-www-form-urlencoded' });
    expect([...((init.body as URLSearchParams).keys())]).toEqual(['email', 'password']);
  });
  it('obtiene CSRF para cerrar y admite respuesta 204', async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValueOnce(response(envelope({ header_name: 'X-CSRF-TOKEN', token: 'logout-token' }))).mockResolvedValueOnce(new Response(null, { status: 204 }));
    await createHttpGateway(fetcher).logout();
    expect(fetcher.mock.calls[1]).toMatchObject(['/api/v1/auth/logout', { method: 'POST', headers: { 'X-CSRF-TOKEN': 'logout-token' } }]);
  });
  it('no muestra mensajes arbitrarios del servidor ni cambia un fallo por datos mock', async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(response({ detail: 'SQL password=private', request_id: id, code: 'ACCESS_DENIED' }, 403));
    await expect(createHttpGateway(fetcher).asset(asset.project_id, asset.id)).rejects.toMatchObject({ status: 403, requestId: id });
    expect(new ApiError(403).message).not.toContain('SQL');
    expect(fetcher).toHaveBeenCalledTimes(1);
  });
  it('rechaza identificadores inválidos sin construir una ruta manipulada', async () => {
    const fetcher = vi.fn<typeof fetch>();
    await expect(createHttpGateway(fetcher).asset('../otra-empresa', asset.id)).rejects.toMatchObject({ status: 422 });
    expect(fetcher).not.toHaveBeenCalled();
  });
  it('rechaza un activo devuelto con otro contexto', async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(response(envelope({ ...asset, project_id: id })));
    await expect(createHttpGateway(fetcher).asset(asset.project_id, asset.id)).rejects.toMatchObject({ code: 'INVALID_CONTEXT' });
  });
  it('vincula campos a la versión y página pedidas y rechaza otra versión', async () => {
    const fields = structuredClone(examples.fields_ok.body);
    fields.meta.page_info = { page: 1, page_size: 25, returned_count: fields.data.length, has_next: false };
    const fetcher = vi.fn<typeof fetch>().mockResolvedValueOnce(response(fields)).mockResolvedValueOnce(response({ ...fields, data: fields.data.map(field => ({ ...field, metadata_version_id: id })) }));
    const api = createHttpGateway(fetcher);
    expect((await api.fields(asset.project_id, asset.id, asset.metadata_version_id, 1)).data.length).toBeGreaterThan(0);
    expect(String(fetcher.mock.calls[0][0])).toContain(`metadata_version_id=${asset.metadata_version_id}&page=1&page_size=25&sort=position%2Cid`);
    await expect(api.fields(asset.project_id, asset.id, asset.metadata_version_id, 1)).rejects.toMatchObject({ code: 'INVALID_CONTEXT' });
  });
  it('rechaza una respuesta exitosa sin el sobre común', async () => {
    const api = createHttpGateway(vi.fn<typeof fetch>().mockResolvedValue(response(profile)));
    await expect(api.profile()).rejects.toMatchObject({ code: 'INVALID_RESPONSE' });
  });
  it('conserva cancelación para descartar la consulta de un contexto anterior', async () => {
    const api = createHttpGateway(vi.fn<typeof fetch>().mockRejectedValue(new DOMException('Aborted', 'AbortError')));
    await expect(api.profile()).rejects.toMatchObject({ name: 'AbortError' });
  });
});
