import {useEffect, useState} from 'react';
import {assetLabel, queryPath, hasPendingEvaluations, hasPendingReport, date, label, metricFormula, metricValue, percentage, ruleCondition, type Asset, type Evaluation, type EvaluationReport} from './api';
import {ErrorMessage, Form, InputStatus, Loading, Pagination, Status, Button, Input, Select, Table, FilterPanel} from './ui';
import {EvaluationSummary} from './QualityOverview';
import {useApiQuery} from './useApiQuery';

type Filters = {page: number; from: string; to: string; state: string};
const emptyFilters: Filters = {page: 1, from: '', to: '', state: ''};
export function Evaluations({project, asset, canManage, onIncidents, onCreate, initialSelectedId, refreshKey = 0}: {
    project: string; asset: Asset; canManage: boolean; onIncidents: (id: string) => void; onCreate: () => void; initialSelectedId?: string; refreshKey?: number
}) {
    const base = '/projects/' + project;
    const [filters, setFilters] = useState<Filters>({...emptyFilters});
    const [formRevision, setFormRevision] = useState(0);
    const [selectedId, setSelectedId] = useState(initialSelectedId);
    const [refresh, setRefresh] = useState(0);
    useEffect(() => {if (initialSelectedId) setSelectedId(initialSelectedId);}, [initialSelectedId]);
    useEffect(() => {if (refreshKey) {setFilters({...emptyFilters}); setFormRevision(v => v + 1);}}, [refreshKey]);
    const path = queryPath(base + '/quality-evaluations', {
        asset_id: asset.id, page: filters.page, date_from: filters.from, date_to: filters.to, state: filters.state
    });
    const {data: rows = [], info, error, loading} = useApiQuery<Evaluation[]>(path, {
        refreshKey: `${refresh}:${refreshKey}`, pollMs: 2000, shouldPoll: hasPendingEvaluations
    });
    const {data: report, error: reportError, loading: loadingReport} = useApiQuery<EvaluationReport>(
        selectedId ? `${base}/quality-evaluations/${selectedId}/results` : null,
        {refreshKey: refresh, pollMs: 1500, shouldPoll: hasPendingReport}
    );
    const versions = new Map(report?.rule_versions.map(v => [v.id, v]) ?? []);
    return <>
        <div className="section-heading panel-header p-4 flex flex-wrap items-center justify-between gap-4 mb-4"><div><span className="eyebrow">Historial del activo</span><h2>{assetLabel(asset)}</h2><p>Consulta ejecuciones e indicadores sin modificar las mediciones anteriores.</p></div>
            {canManage ? <Button variant="outline" onClick={onCreate}>Nueva evaluación</Button> : null}</div>
        <FilterPanel title="Filtros del historial">
            <Form key={formRevision} noValidate variant="filters" label="Aplicar filtros" submit={async data => {
                const from = String(data.get('from')); const to = String(data.get('to'));
                if (from && to && from > to) throw new Error('La fecha inicial no puede ser posterior a la final.');
                setFilters({page: 1, from, to, state: String(data.get('state'))}); setSelectedId(undefined);
            }}><div className="history-filters grid grid-cols-1 md:grid-cols-3 gap-4"><label className="field-label flex flex-col gap-2 mb-4">Desde<Input name="from" type="date" defaultValue={filters.from} aria-describedby="history-date-help"/></label>
                <label className="field-label flex flex-col gap-2 mb-4">Hasta<Input name="to" type="date" defaultValue={filters.to} aria-describedby="history-date-help"/></label>
                <label className="field-label flex flex-col gap-2 mb-4">Estado de ejecución<Select name="state" aria-label="Estado de ejecución" defaultValue={filters.state}><option value="">Todos los estados</option>{['QUEUED', 'RUNNING', 'COMPLETED', 'FAILED'].map(s => <option key={s} value={s}>{label(s)} · {s}</option>)}</Select></label></div>
                <p className="help" id="history-date-help">Fechas inclusivas en hora de Ecuador. El selector Activo de arriba limita el archivo consultado.</p></Form>
            <Button variant="link" className="text-xs p-0" onClick={() => {setFilters({...emptyFilters}); setFormRevision(v => v + 1); setSelectedId(undefined);}}>Limpiar filtros del historial</Button>
        </FilterPanel>
        <ErrorMessage error={error}/>{loading ? <Loading/> : error ? <div className="empty flex flex-col justify-center items-center text-center gap-2 p-6"><Button onClick={() => setRefresh(v => v + 1)}>Reintentar historial</Button></div> : rows.length ? <div className="table-scroll max-w-full overflow-x-auto">
            <Table className="evaluation-table"><thead><tr><th>Evaluación / activo</th><th>Fecha de solicitud</th><th>Ejecución</th><th>Cumplimiento</th><th>Origen</th><th>Detalle</th></tr></thead>
                <tbody>{rows.map(e => <tr key={e.id} className={selectedId === e.id ? 'selected-row' : ''}>
                    <td><strong className="mono">{e.id.slice(0, 8)}</strong><small className="dimension-label">{assetLabel(asset)}</small></td><td>{date(e.submitted_at)}</td>
                    <td><Status value={e.state} context="execution"/></td><td><Status value={e.quality_outcome} context="quality"/></td>
                    <td><InputStatus value={e.input_availability}/>{e.input_availability === 'EXPIRED' ? <small className="dimension-label">La copia ya no permite reproducir el análisis.</small> : null}</td>
                    <td><Button aria-pressed={selectedId === e.id} onClick={() => setSelectedId(e.id)}>Ver resultados</Button></td>
                </tr>)}</tbody></Table>
        </div> : <div className="empty flex flex-col justify-center items-center text-center gap-2 p-6">No hay evaluaciones que coincidan con los filtros de este archivo.</div>}
        <Pagination info={loading || error ? undefined : info} onPage={page => setFilters(f => ({...f, page}))}/>
        {selectedId ? <section className="evaluation-detail surface-card mt-6 overflow-hidden p-4" aria-label="Detalle de evaluación">
            <div className="section-heading panel-header p-4 flex flex-wrap items-center justify-between gap-4 mb-4"><div><span className="eyebrow">Medición histórica</span><h2>Detalle de la evaluación</h2><span className="mono">{selectedId}</span></div><Button variant="secondary" onClick={() => setSelectedId(undefined)}>Cerrar detalle</Button></div>
            <ErrorMessage error={reportError}/>{loadingReport ? <Loading/> : reportError ? <Button onClick={() => setRefresh(v => v + 1)}>Reintentar resultados</Button> : report ? <>
                <EvaluationSummary evaluation={report.evaluation}/>
                <div className="toolbar surface-card p-5 flex flex-row flex-wrap items-center justify-between gap-4 mb-4"><p>La versión de cada regla queda fijada en esta ejecución.</p>{canManage ? <Button onClick={() => onIncidents(selectedId)}>Ver incumplimientos</Button> : null}</div>
                {report.results.length ? <div className="table-scroll max-w-full overflow-x-auto"><Table className="results-table"><thead><tr><th>Dimensión / condición aplicada</th><th>Versión</th><th>Medida</th><th>Cobertura</th><th>Conteos</th><th>Umbral</th><th>Cumplimiento</th></tr></thead>
                    <tbody>{report.results.map(r => {
                        const version = versions.get(r.rule_version_id);
                        return <tr key={r.id}><td><strong>{label(version?.dimension ?? 'Indicador')}</strong><small className="dimension-label">{ruleCondition(version)}</small><small className="formula-text">{metricFormula(version?.dimension ?? '')}</small></td>
                            <td><span className="version-tag">v{version?.version_number ?? '—'}</span><small className="dimension-label">{version?.formula_version ?? 'Sin fórmula informada'}</small></td>
                            <td className="numeric"><strong>{metricValue(r)}</strong>{r.denominator === 0 ? <small className="dimension-label">Sin unidades evaluables</small> : null}</td><td className="numeric">{percentage(r.coverage_value)}</td>
                            <td className="numeric">{r.numerator} / {r.denominator}</td><td>{r.threshold == null ? 'Sin umbral' : percentage(r.threshold)}</td><td><Status value={r.compliance_state}/></td></tr>;
                    })}</tbody></Table></div> : <div className="notice notice-info my-4">{['QUEUED', 'RUNNING'].includes(report.evaluation.state) ? 'Esperando los resultados. Esta vista se actualiza automáticamente.' : 'Esta ejecución no tiene indicadores finales disponibles.'}</div>}
                <details className="technical-details"><summary>Origen, alcance y referencias</summary><dl className="context-grid grid grid-cols-1 sm:grid-cols-2 gap-4 my-4">
                    <div><dt>Entrada</dt><dd>{report.evaluation.scope?.filename ?? 'Archivo identificado'}</dd></div><div><dt>Registros de origen</dt><dd>{report.evaluation.input_row_count?.toLocaleString('es-EC') ?? 'No informado'}</dd></div>
                    <div><dt>Captura o lectura</dt><dd className="identifier">{report.evaluation.snapshot_id ?? report.evaluation.read_id ?? 'No informado'}</dd></div><div><dt>Caducidad</dt><dd>{report.evaluation.input_expires_at ? date(report.evaluation.input_expires_at) : 'Sin caducidad informada'}</dd></div></dl>
                    {report.rule_versions.map(v => <p key={v.id} className="identifier">{label(v.dimension)} · versión {v.version_number} · {v.id}</p>)}</details>
            </> : null}
        </section> : null}
    </>;
}
