import { useEffect, useRef, useState } from 'react';
import type { Asset, FieldPage, Gateway } from '../../shared/contracts';
import { aborted, ApiError, message } from '../../shared/http';

type State = { phase: 'loading' } | { phase: 'ready'; asset: Asset; fields: FieldPage } | { phase: 'error'; error: unknown };
export function Catalogo({ gateway, projectId, assetId, onExpired }: { gateway: Gateway; projectId: string; assetId: string; onExpired(): void }) {
  const [state, setState] = useState<State>({ phase: 'loading' });
  const [page, setPage] = useState(1);
  const [attempt, setAttempt] = useState(0);
  const snapshot = useRef<Asset | null>(null);
  useEffect(() => {
    const controller = new AbortController();
    async function load() {
      try {
        const current = await gateway.asset(projectId, assetId, controller.signal);
        if (controller.signal.aborted) return;
        // Revalidate access but retain the initial metadata version across pages.
        const asset = snapshot.current ?? current;
        snapshot.current = asset;
        const fields = await gateway.fields(projectId, assetId, asset.metadata_version_id, page, controller.signal);
        if (!controller.signal.aborted) setState({ phase: 'ready', asset, fields });
      } catch (error) {
        if (controller.signal.aborted || aborted(error)) return;
        if (error instanceof ApiError && error.status === 401) { onExpired(); return; }
        setState({ phase: 'error', error });
      }
    }
    void load();
    return () => controller.abort();
  }, [gateway, projectId, assetId, page, attempt, onExpired]);

  if (state.phase === 'loading') return <div className="notice" role="status"><span className="spinner" aria-hidden="true" /><h2>Cargando el activo</h2><p>Consultando su estructura y diccionario.</p></div>;
  if (state.phase === 'error') return <div className="notice error" role="alert"><h2>No se pudo abrir el diccionario</h2><p>{message(state.error)}</p>{state.error instanceof ApiError && state.error.requestId ? <p className="reference">Referencia: {state.error.requestId}</p> : null}<button onClick={() => { setState({ phase: 'loading' }); setAttempt(n => n + 1); }}>Volver a intentar</button></div>;
  const { asset, fields } = state;
  const go = (next: number) => { setState({ phase: 'loading' }); setPage(next); };
  return <article className="asset">
    <div className="asset-heading"><div><p className="path">{asset.origin.namespace || 'Sin esquema'} / {asset.origin.object_path || asset.name}</p><h2>{asset.name}</h2><p>{asset.business_description || 'Este activo aún no tiene una descripción de negocio.'}</p></div><span className="status-pill">{asset.status === 'active' ? 'Activo' : asset.status === 'retired' ? 'Retirado' : asset.status}</span></div>
    <dl className="asset-context"><div><dt>Proyecto</dt><dd>{asset.project_id}</dd></div><div><dt>Versión de metadatos</dt><dd>{asset.metadata_version_id}</dd></div><div><dt>Etiquetas</dt><dd>{asset.tags.length ? asset.tags.join(', ') : 'Sin etiquetas'}</dd></div></dl>
    <section aria-labelledby="dictionary-heading"><div className="section-heading"><h3 id="dictionary-heading">Diccionario de datos</h3><span>Página {fields.meta.page_info.page}</span></div>
      {fields.data.length ? <div className="table-scroll" role="region" aria-label="Campos del activo" tabIndex={0}><table><caption className="sr-only">Campos de {asset.name} en la versión consultada</caption><thead><tr><th scope="col">Campo</th><th scope="col">Tipo de origen</th><th scope="col">Admite nulos</th><th scope="col">Descripción</th></tr></thead><tbody>{fields.data.map(field => <tr key={field.id}><th scope="row"><span className="field-name">{field.name}</span><span className="field-path">{field.path}</span></th><td>{field.native_type || field.logical_type || 'No disponible'}</td><td>{field.nullable === null ? 'No determinado' : field.nullable ? 'Sí' : 'No'}</td><td>{field.business_description || 'Sin descripción'}</td></tr>)}</tbody></table></div> : <div className="empty" role="status"><h4>No hay campos en esta página</h4><p>El activo está disponible, pero esta consulta no devuelve campos.</p></div>}
      <div className="pagination"><button className="secondary" disabled={page === 1} onClick={() => go(page - 1)}>Anterior</button><span>Se consulta una versión fija por página.</span><button className="secondary" disabled={!fields.meta.page_info.has_next} onClick={() => go(page + 1)}>Siguiente</button></div>
    </section>
  </article>;
}
