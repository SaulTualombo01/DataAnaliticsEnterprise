import { describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from '../src/shell/App';
import { createDemoGateway } from '../src/mocks/gateway';
import { ApiError } from '../src/shared/http';
import type { Asset, Gateway } from '../src/shared/contracts';
import examples from '../src/mocks/catalogo.examples.json';
import { Catalogo } from '../src/modules/catalogo/Catalogo';

describe('contenedor compartido', () => {
  it('marca el modo demo y permite navegar sin simular funciones de otros módulos', async () => {
    const user = userEvent.setup(); render(<App gateway={createDemoGateway(0)} />);
    expect(await screen.findByRole('heading', { name: 'orders' })).toBeTruthy();
    expect(screen.getByText('Demostración')).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'M2 Calidad' }));
    expect(screen.getByRole('heading', { name: 'Vista aún no integrada' })).toBeTruthy();
    expect(screen.queryByRole('table')).toBeNull();
  });
  it('cambiar de proyecto descarta la consulta pendiente y no muestra datos anteriores', async () => {
    const gateway = createDemoGateway(0);
    let resolve!: (asset: Asset) => void;
    gateway.asset = vi.fn(() => new Promise<Asset>(done => { resolve = done; }));
    const fields = vi.spyOn(gateway, 'fields');
    const user = userEvent.setup(); render(<App gateway={gateway} />);
    await screen.findByRole('heading', { name: 'Cargando el activo' });
    await user.selectOptions(screen.getByLabelText('Proyecto de demostración'), '1');
    resolve(examples.asset_ok.body.data);
    await screen.findByRole('heading', { name: 'Este proyecto aún no tiene activos' });
    expect(screen.queryByRole('heading', { name: 'orders' })).toBeNull();
    // Even if a non-cooperating adapter completes after abort, it cannot render stale data.
    await waitFor(() => expect(screen.queryByRole('table')).toBeNull());
    expect(fields).not.toHaveBeenCalled();
  });
  it('muestra vacío y denegación sin confundirlos', async () => {
    const user = userEvent.setup(); render(<App gateway={createDemoGateway(0)} />);
    await screen.findByRole('heading', { name: 'orders' });
    await user.selectOptions(screen.getByLabelText('Escenario de demostración'), 'empty');
    expect(await screen.findByRole('heading', { name: 'No hay campos en esta página' })).toBeTruthy();
    await user.selectOptions(screen.getByLabelText('Escenario de demostración'), 'denied');
    expect((await screen.findByRole('alert')).textContent).toContain('no tiene permiso');
    expect(screen.queryByRole('table')).toBeNull();
  });
  it('la expiración retira datos del activo y exige volver a entrar', async () => {
    const user = userEvent.setup(); render(<App gateway={createDemoGateway(0)} />);
    await screen.findByRole('heading', { name: 'orders' });
    await user.selectOptions(screen.getByLabelText('Escenario de demostración'), 'expired');
    expect(await screen.findByRole('button', { name: 'Entrar a la demostración' })).toBeTruthy();
    expect(screen.queryByRole('heading', { name: 'orders' })).toBeNull();
    await user.click(screen.getByRole('button', { name: 'Entrar a la demostración' }));
    expect(await screen.findByRole('heading', { name: 'orders' })).toBeTruthy();
  });
  it('un fallo real de sesión muestra error, sin inventar un perfil o contexto', async () => {
    const gateway: Gateway = { ...createDemoGateway(0), demo: undefined, profile: vi.fn().mockRejectedValue(new ApiError(503)) };
    render(<App gateway={gateway} />);
    expect(await screen.findByRole('heading', { name: 'No se pudo verificar la sesión' })).toBeTruthy();
    expect(screen.queryByText('Demostración')).toBeNull();
    expect(screen.queryByLabelText('Proyecto de demostración')).toBeNull();
  });
  it('si cerrar sesión falla, no declara que el servidor cerró la sesión', async () => {
    const gateway = createDemoGateway(0); gateway.logout = vi.fn().mockRejectedValue(new ApiError(503));
    const user = userEvent.setup(); render(<App gateway={gateway} />);
    await screen.findByRole('heading', { name: 'orders' });
    await user.click(screen.getByRole('button', { name: 'Cerrar sesión' }));
    expect((await screen.findByRole('alert')).textContent).toContain('No se pudo cerrar la sesión');
    expect(screen.getByRole('button', { name: 'Cerrar sesión' })).toBeTruthy();
  });
  it('pagina la versión inicial aunque cambie la versión actual del activo', async () => {
    const gateway = createDemoGateway(0);
    const asset = examples.asset_ok.body.data;
    const later = { ...asset, metadata_version_id: '00000000-0000-0000-0000-000000000909' };
    gateway.asset = vi.fn().mockResolvedValueOnce(asset).mockResolvedValueOnce(later);
    gateway.fields = vi.fn().mockImplementation(async (_project, _asset, _version, page) => ({
      ...examples.fields_ok.body,
      meta: { ...examples.fields_ok.body.meta, page_info: { page, page_size: 25, returned_count: examples.fields_ok.body.data.length, has_next: page === 1 } },
    }));
    const user = userEvent.setup(); render(<Catalogo gateway={gateway} projectId={asset.project_id} assetId={asset.id} onExpired={vi.fn()} />);
    await screen.findByRole('heading', { name: 'orders' });
    await user.click(screen.getByRole('button', { name: 'Siguiente' }));
    await screen.findByText('Página 2');
    expect(vi.mocked(gateway.fields).mock.calls[1][2]).toBe(asset.metadata_version_id);
    expect(screen.getByText(asset.metadata_version_id)).toBeTruthy();
  });
});
