import type { DemoScenario, Gateway } from '../shared/contracts';
import { ApiError } from '../shared/http';
import examples from './catalogo.examples.json';
import identity from './identidad-contexto.json';

export function createDemoGateway(latency = 250): Gateway {
  let authenticated = true;
  let scenario: DemoScenario = 'normal';
  const project = examples.asset_ok.body.data.project_id;
  async function pause(signal?: AbortSignal) {
    await new Promise<void>((resolve, reject) => {
      if (signal?.aborted) { reject(new DOMException('Aborted', 'AbortError')); return; }
      const stop = () => { clearTimeout(timer); reject(new DOMException('Aborted', 'AbortError')); };
      const timer = setTimeout(() => { signal?.removeEventListener('abort', stop); resolve(); }, latency);
      signal?.addEventListener('abort', stop, { once: true });
    });
  }
  async function check(signal?: AbortSignal) {
    await pause(signal);
    if (scenario === 'expired') authenticated = false;
    if (!authenticated) throw new ApiError(401);
    if (scenario === 'denied') throw new ApiError(403);
    if (scenario === 'unavailable') throw new ApiError(503);
  }
  return {
    demo: {
      contexts: structuredClone(identity.contexts),
      configure(value) { scenario = value; },
    },
    async profile(signal) { await pause(signal); if (!authenticated) throw new ApiError(401); return { ...identity.profile }; },
    async login() { await pause(); authenticated = true; scenario = 'normal'; return { ...identity.profile }; },
    async logout() { await pause(); authenticated = false; },
    async asset(projectId, assetId, signal) {
      await check(signal);
      if (projectId !== project || assetId !== examples.asset_ok.body.data.id) throw new ApiError(404);
      return structuredClone(examples.asset_ok.body.data);
    },
    async fields(projectId, assetId, version, page, signal) {
      await check(signal);
      if (projectId !== project || assetId !== examples.asset_ok.body.data.id || version !== examples.asset_ok.body.data.metadata_version_id) throw new ApiError(404);
      const result = structuredClone(examples.fields_ok.body);
      if (scenario === 'empty' || page > 1) result.data = [];
      result.meta.page_info = { page, page_size: 25, returned_count: result.data.length, has_next: false };
      return result;
    },
  };
}
