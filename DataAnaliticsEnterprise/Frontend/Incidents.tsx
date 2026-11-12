import {useState} from 'react';
import {api, queryPath, assetLabel, date, label, type Asset, type Evidence, type Incident} from './api';
import {useApiQuery} from './useApiQuery';
import {Dialog, ErrorMessage, Form, Loading, Pagination, Status, Button, Input, Select, Table, Textarea, FilterPanel} from './ui';

const transitions: Record<string, string[]> = {
    OPEN: ['IN_PROGRESS', 'DISMISSED'], IN_PROGRESS: ['RESOLVED', 'DISMISSED'],
    RESOLVED: ['OPEN'], DISMISSED: ['OPEN']
};
const dimensions = ['COMPLETENESS', 'UNIQUENESS', 'VALIDITY', 'CONSISTENCY', 'ACCURACY', 'TIMELINESS'];
type Filters = {asset: string; status: string; severity: string; dimension: string; search: string; page: number};
const emptyFilters: Filters = {asset: '', status: '', severity: '', dimension: '', search: '', page: 1};
type Event = {id: string; to_status: string; comment: string | null; created_at: string};

export function Incidents({project, assets, initialAssetId, evaluationId, clearFilter}: {
    project: string; assets: Asset[]; initialAssetId?: string; evaluationId?: string; clearFilter: () => void
}) {
    const base = `/projects/${project}/quality-incidents`;
    const [filters, setFilters] = useState<Filters>({...emptyFilters, asset: initialAssetId ?? ''});
    const [formRevision, setFormRevision] = useState(0);
    const [refresh, setRefresh] = useState(0);
    const [selected, setSelected] = useState<Incident>();
    const [notice, setNotice] = useState('');
    const [evidenceFor, setEvidenceFor] = useState<Incident>();
    const names = new Map(assets.map(a => [a.id, assetLabel(a)]));
    const {data: rows = [], info, error, loading} = useApiQuery<Incident[]>(queryPath(base, {
        page: filters.page, asset_id: filters.asset, evaluation_id: evaluationId,
        status: filters.status, severity: filters.severity, dimension: filters.dimension, q: filters.search
    }), {refreshKey: refresh});
    const {data: events = [], error: eventError, loading: loadingEvents} = useApiQuery<Event[]>(
        selected ? `${base}/${selected.id}/events` : null
    );
    const {data: evidence, error: evidenceError, loading: loadingEvidence} = useApiQuery<Evidence>(
        evidenceFor ? `${base}/${evidenceFor.id}/evidence` : null, {cache: 'no-store'}
    );

    function reset() {
        setFilters({...emptyFilters}); setFormRevision(v => v + 1); setNotice('');
        if (evaluationId) clearFilter();
    }

    return <>
        <div className="toolbar surface-card p-5 flex flex-row flex-wrap items-center justify-between gap-4 mb-4"><p>{evaluationId ? `Incidencias de la evaluación ${evaluationId.slice(0, 8)}` : 'Todas las incidencias autorizadas del proyecto'}</p>
            <Button onClick={reset}>{evaluationId ? 'Ver todas las incidencias' : 'Limpiar filtros'}</Button></div>
        <FilterPanel title="Filtrar incidencias"><Form key={formRevision} noValidate variant="filters" label="Aplicar filtros" submit={async data => {
            setFilters({asset: String(data.get('asset')), status: String(data.get('status')), severity: String(data.get('severity')),
                dimension: String(data.get('dimension')), search: String(data.get('search')).trim(), page: 1});
            setNotice('');
        }}><div className="incident-filters grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            <label className="field-label flex flex-col gap-2 mb-4">Activo<Select name="asset" aria-label="Activo" defaultValue={filters.asset}>
                <option value="">Todos los activos autorizados</option>
                {assets.map(a => <option key={a.id} value={a.id}>{assetLabel(a)}</option>)}
            </Select></label>
            <label className="field-label flex flex-col gap-2 mb-4">Estado de incidencia<Select name="status" defaultValue={filters.status}><option value="">Todos los estados</option>
                {Object.keys(transitions).map(s => <option key={s} value={s}>{label(s)}</option>)}</Select></label>
            <label className="field-label flex flex-col gap-2 mb-4">Gravedad<Select name="severity" defaultValue={filters.severity}><option value="">Todas las gravedades</option>
                {['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].map(s => <option key={s} value={s}>{label(s)}</option>)}</Select></label>
            <label className="field-label flex flex-col gap-2 mb-4">Dimensión<Select name="dimension" defaultValue={filters.dimension}><option value="">Todas las dimensiones</option>
                {dimensions.map(d => <option key={d} value={d}>{label(d)}</option>)}</Select></label>
            <label className="field-label flex flex-col gap-2 mb-4">Buscar regla, campo o fila<Input name="search" type="search" maxLength={160} defaultValue={filters.search} placeholder="Ej.: correo, identificador o 101"/></label>
        </div></Form></FilterPanel>
        {notice ? <div className="notice notice-info my-4" role="status">{notice}</div> : null}
        <ErrorMessage error={error}/>
        {loading ? <Loading/> : error ? <div className="empty flex flex-col justify-center items-center text-center gap-2 p-6"><Button onClick={() => setRefresh(v => v + 1)}>Reintentar consulta</Button></div> : rows.length ? <div className="table-scroll max-w-full overflow-x-auto">
            <Table><thead><tr><th>Activo / evaluación</th><th>Regla</th><th>Campo</th><th>Fila</th><th>Detección</th><th>Gravedad</th><th>Estado</th><th>Acción</th></tr></thead>
                <tbody>{rows.map(i => <tr key={i.id}>
                    <td>{names.get(i.asset_id) ?? i.asset_id}<small className="dimension-label">Evaluación {i.evaluation_id.slice(0, 8)} · {date(i.created_at)}</small></td>
                    <td>{i.rule_name ?? 'Regla de calidad'}<small className="dimension-label">{i.dimension ? label(i.dimension) : ''}</small></td>
                    <td className="field-name"><code>{i.field_name}</code></td><td className="row-reference">{i.record_reference}</td>
                    <td>{i.problem_code === 'MISSING_VALUE' ? 'Valor faltante' : i.problem_code === 'DUPLICATE_KEY' ? 'Clave duplicada' : i.problem_code === 'NOT_EVALUABLE' ? 'No evaluable' : 'Incumplimiento de regla'}</td>
                    <td><Status value={i.severity}/></td><td><Status value={i.status}/></td>
                    <td><div className="incident-actions"><Button className="incident-follow-up" onClick={() => setSelected(i)}>Seguimiento</Button>
                        <Button variant="link" className="text-xs p-0" onClick={() => setEvidenceFor(i)}>Ver evidencia</Button></div></td>
                </tr>)}</tbody></Table>
        </div> : <div className="empty flex flex-col justify-center items-center text-center gap-2 p-6">No hay incidencias que coincidan con estos filtros.</div>}
        <Pagination info={loading || error ? undefined : info} onPage={page => setFilters(f => ({...f, page}))}/>
        {selected ? <Dialog title="Seguimiento de incidencia" close={() => setSelected(undefined)}>
            <p><strong>{names.get(selected.asset_id) ?? selected.asset_id}</strong> · {selected.rule_name ?? 'Regla de calidad'}.</p>
            <p>Campo <strong>{selected.field_name}</strong> · fila {selected.record_reference}. Resolver esta incidencia no cambia el resultado histórico.</p>
            <ErrorMessage error={eventError}/>{loadingEvents ? <Loading/> : <ol className="timeline">{events.map(e => <li key={e.id}>
                <strong>{label(e.to_status)}</strong> · {date(e.created_at)}<p>{e.comment ?? 'Sin comentario'}</p></li>)}</ol>}
            <Form noValidate cancel={() => setSelected(undefined)} label="Registrar seguimiento" submit={async data => {
                await api(`${base}/${selected.id}/events`, {method: 'POST', headers: {'If-Match': `"${selected.revision}"`},
                    body: JSON.stringify({status: data.get('status'), comment: data.get('comment') || null})});
                setSelected(undefined); setFilters(f => ({...f, page: 1})); setRefresh(r => r + 1);
                setNotice('Seguimiento guardado. La lista refleja el estado actualizado.');
            }}><label className="field-label flex flex-col gap-2 mb-4">Nuevo estado<Select name="status">{transitions[selected.status]?.map(s => <option value={s} key={s}>{label(s)}</option>)}</Select></label>
                <label className="field-label flex flex-col gap-2 mb-4">Comentario<Textarea className="resize-none" name="comment" rows={4} maxLength={2000}/></label>
                <p className="help">Describe la acción sin copiar datos personales del origen.</p></Form>
        </Dialog> : null}
        {evidenceFor ? <Dialog title="Evidencia de la incidencia" variant="evidence" close={() => {setEvidenceFor(undefined);}}>
            <p><strong>{names.get(evidenceFor.asset_id) ?? evidenceFor.asset_id}</strong></p><p>{evidenceFor.rule_name ?? 'Regla de calidad'} · campo <code>{evidenceFor.field_name}</code> · fila <strong className="mono">{evidenceFor.record_reference}</strong></p>
            <div className="notice notice-warning my-4">Consulta confidencial. Se muestran únicamente los campos implicados en esta detección; el acceso se registra y los valores no se almacenan de forma persistente en el navegador.</div>
            <ErrorMessage error={evidenceError}/>{loadingEvidence ? <Loading/> : evidence?.available ? <>
                <dl className="evidence-values">{Object.entries(evidence.values ?? {}).map(([field, value]) => <div key={field}><dt>{field}</dt><dd><pre>{value === null ? 'Valor nulo' : value === '' ? 'Cadena vacía' : String(value)}</pre></dd></div>)}</dl>
                <p className="help">Valor utilizado por el motor de evaluación{evidence.captured_at ? ' · origen capturado ' + date(evidence.captured_at) : ''}.</p>
            </> : evidence ? <div className="notice notice-info my-4">{evidence.reason ?? 'No hay evidencia de origen disponible.'}</div> : null}
        </Dialog> : null}
    </>;
}
