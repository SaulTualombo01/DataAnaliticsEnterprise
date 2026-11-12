import {useState} from 'react';
import {assetLabel, date, dimensions, isPendingEvaluation, label, metricValue, type Asset, type Evaluation, type QualityOverviewData, type Result, type Version} from './api';
import {Card, CardHeader, CardContent, CardFooter, ErrorMessage, InputStatus, Loading, PanelIcon, Status, Button, MetricGauge, Reveal} from './ui';
import {useApiQuery} from './useApiQuery';

const dimensionIcons: Record<string, string> = {COMPLETENESS: 'check', ACCURACY: 'search', CONSISTENCY: 'rules', UNIQUENESS: 'database', VALIDITY: 'shield', TIMELINESS: 'clock'};

export function DimensionBoard({results, versions, sources, onHistory}: {
    results: Result[]; versions: Version[]; sources: Record<string, Evaluation>; onHistory: (id?: string) => void
}) {
    const byId = new Map(versions.map(version => [version.id, version]));
    return <div className="dimension-grid" aria-label="Indicadores de las seis dimensiones">{dimensions.map((dimension, index) => {
        const values = results.filter(result => byId.get(result.rule_version_id)?.dimension === dimension);
        const source = sources[dimension];
        const single = values.length === 1 ? values[0] : undefined;
        const gauge = single && single.denominator > 0 && single.measurement_state !== 'NOT_EVALUABLE' ? single.metric_value : null;
        const statuses = new Map<string, number>();
        values.forEach(result => statuses.set(result.compliance_state, (statuses.get(result.compliance_state) ?? 0) + 1));
        return <Reveal key={dimension} delay={index * .035} className="dimension-reveal"><Card className={'quality-dimension' + (values.length ? ' measured' : '')} aria-label={label(dimension)}>
            <CardContent>
                <div className="dimension-title"><span className="dimension-icon"><PanelIcon name={dimensionIcons[dimension]}/></span><h3>{label(dimension)}</h3><span className="dimension-index" aria-hidden="true">0{index + 1}</span></div>
                <div className="dimension-reading"><div><div className={'dimension-value' + (!values.length || single?.measurement_state === 'NOT_EVALUABLE' || single?.denominator === 0 ? ' unmeasured' : '')}>{single ? metricValue(single) : values.length ? `${values.length} reglas` : 'Sin evaluar'}</div>
                    <div className="dimension-states">{[...statuses].map(([status, count]) => <span key={status}><Status value={status}/>{values.length > 1 ? <span className="state-count">× {count}</span> : null}</span>)}</div>
                    {!values.length ? <span className="dimension-empty">Sin medición registrada</span> : null}
                </div><MetricGauge value={gauge} tone={single?.compliance_state === 'PASS' ? 'good' : single?.compliance_state === 'FAIL' ? 'bad' : 'info'}/></div>
                {source && ['EXPIRED', 'DELETED', 'UNAVAILABLE'].includes(source.input_availability ?? '') ? <InputStatus value={source.input_availability}/> : null}
            </CardContent>
            <CardFooter>{source ? <><span>{date(source.submitted_at)}</span><Button variant="link" className="min-h-8 px-0 text-xs" onClick={() => onHistory(source.id)} aria-label={`Ver evaluación de ${label(dimension)}`}>Ver detalle<PanelIcon name="arrow"/></Button></> : <span>Pendiente de evaluación</span>}</CardFooter>
        </Card></Reveal>;
    })}</div>;
}

export function EvaluationSummary({evaluation}: {evaluation: Evaluation}) {
    const expired = evaluation.input_availability === 'EXPIRED';
    return <>
        <div className="execution-summary grid gap-4 mb-4 md:grid-cols-3"><div className="min-w-0"><span className="eyebrow">Procesamiento</span><Status value={evaluation.state} context="execution"/></div>
            <div className="min-w-0"><span className="eyebrow">Cumplimiento de la evaluación</span><Status value={evaluation.quality_outcome} context="quality"/></div>
            <div className="min-w-0"><span className="eyebrow">Entrada de origen</span><InputStatus value={evaluation.input_availability}/></div>
        </div>
        {expired ? <div className="notice notice-warning my-4" role="status">La entrada de origen ha caducado. Los indicadores históricos se conservan, pero ya no se garantiza reproducir esta evaluación sobre la misma copia.</div> : null}
        {evaluation.failure_code ? <div className="notice notice-danger my-4">La ejecución falló ({evaluation.failure_code}). Los resultados parciales no aprueban la calidad del activo.</div> : null}
    </>;
}

const hasPendingOverview = (value: QualityOverviewData) => value.latest_evaluation != null && isPendingEvaluation(value.latest_evaluation);

function OverviewStat({icon, title, value, caption}: {icon: string; title: string; value: string; caption: string}) {
    return <Card className="overview-stat"><div className="stat-icon"><PanelIcon name={icon}/></div><div><span className="stat-label">{title}</span><strong>{value}</strong><span className="stat-caption">{caption}</span></div></Card>;
}

export function QualityOverview({project, asset, canManage, refreshKey, onCreate, onHistory, onRules, onIncidents}: {
    project: string; asset: Asset; canManage: boolean; refreshKey: number; onCreate: () => void; onHistory: (id?: string) => void; onRules: () => void; onIncidents: () => void
}) {
    const [refresh, setRefresh] = useState(0);
    const {data: overview, loading, error} = useApiQuery<QualityOverviewData>(`/projects/${project}/assets/${asset.id}/quality-overview`, {
        refreshKey: `${refreshKey}:${refresh}`, pollMs: 2000, shouldPoll: hasPendingOverview
    });
    const latest = overview?.latest_evaluation;
    const evaluatedDimensions = Object.keys(overview?.dimension_evaluations ?? {}).length;
    const sourceCount = new Set(Object.values(overview?.dimension_evaluations ?? {}).map(evaluation => evaluation.id)).size;
    const tools = [
        {icon: 'evaluations', title: 'Evaluaciones', description: 'Historial y resultados de tus análisis.', action: () => onHistory()},
        {icon: 'rules', title: 'Reglas de calidad', description: 'Condiciones, umbrales y versiones.', action: onRules},
        ...(canManage ? [{icon: 'incidents', title: 'Incidencias', description: 'Errores, evidencia y seguimiento.', action: onIncidents}] : [])
    ];
    const unavailable = loading || error;
    return <div className="control-dashboard">
        <section className="dashboard-hero" aria-label="Archivo seleccionado">
            <div className="hero-orbit" aria-hidden="true"><span/><span/><span/></div>
            <div className="hero-copy"><span className="hero-kicker"><span className="hero-dot"/>Espacio de análisis</span>
                <h2>{assetLabel(asset)}</h2><p>{asset.fields.length.toLocaleString('es-EC')} columnas <span>·</span> {unavailable ? 'Consultando registros…' : latest?.input_row_count != null ? `${latest.input_row_count.toLocaleString('es-EC')} registros en la última evaluación` : 'Listo para su primera evaluación'}</p>
                <div className="hero-actions">{canManage ? <Button variant="primary" onClick={onCreate}>Nueva evaluación<PanelIcon name="arrow"/></Button> : null}
                    <Button variant="outline" onClick={() => onHistory()}>Ver historial</Button><Button variant="secondary" disabled={loading} onClick={() => setRefresh(value => value + 1)}>Actualizar panel</Button></div>
            </div><div className="hero-file" aria-hidden="true"><PanelIcon name="evaluations"/><span>CSV</span></div>
        </section>
        <ErrorMessage error={error}/>{loading ? <Loading/> : error ? <div className="empty"><Button onClick={() => setRefresh(value => value + 1)}>Reintentar consulta</Button></div> : <>
            <Reveal className="overview-stats" delay={.04}>
                <OverviewStat icon="chart" title="Dimensiones con medición" value={`${evaluatedDimensions} / 6`} caption="Cobertura de dimensiones"/>
                <OverviewStat icon="rules" title="Reglas mostradas" value={(overview?.results.length ?? 0).toLocaleString('es-EC')} caption="Mediciones vigentes por dimensión"/>
                <OverviewStat icon="clock" title="Evaluaciones de origen" value={sourceCount.toLocaleString('es-EC')} caption="Ejecuciones que aportan indicadores"/>
            </Reveal>
            <section className="dimension-section" aria-label="Dimensiones de calidad"><div className="board-heading"><div><span className="eyebrow">Visión por dimensión</span><h2>Dimensiones de calidad</h2></div><span className="board-count"><PanelIcon name="chart"/>{evaluatedDimensions} con medición</span></div>
                <DimensionBoard results={overview?.results ?? []} versions={overview?.rule_versions ?? []} sources={overview?.dimension_evaluations ?? {}} onHistory={onHistory}/>
            </section>
        </>}
        <div className="dashboard-bottom">
            <Reveal><Card className="latest-group" aria-label="Última evaluación del archivo">
                <CardHeader><h2 className="panel-title"><PanelIcon name="clock"/>Última evaluación</h2>{latest ? <span className="mono text-xs text-muted-foreground">{latest.id.slice(0, 8)}</span> : null}</CardHeader>
                <CardContent>{unavailable ? <p className="mb-0">{loading ? 'Consultando evaluación…' : 'No se pudo consultar la evaluación.'}</p> : latest ? <><EvaluationSummary evaluation={latest}/>
                    <div className="context-line"><span>{date(latest.submitted_at)}</span><Button variant="link" className="min-h-8 px-0 text-xs" onClick={() => onHistory(latest.id)}>Consultar detalle<PanelIcon name="arrow"/></Button></div>
                </> : <p className="mb-0">Este archivo todavía no tiene evaluaciones. {canManage ? <Button variant="link" className="px-0 text-xs" onClick={onRules}>Configurar sus reglas</Button> : null}</p>}</CardContent>
            </Card></Reveal>
            <Reveal delay={.08}><Card aria-label="Gestión de calidad"><CardHeader><h2 className="panel-title"><PanelIcon name="panel"/>Continuar trabajando</h2></CardHeader><CardContent className="quick-links">{tools.map(tool => <Button key={tool.icon} variant="tool" onClick={tool.action}>
                <span className="tool-icon"><PanelIcon name={tool.icon}/></span><span><strong>{tool.title}</strong><small>{tool.description}</small></span><PanelIcon name="arrow"/>
            </Button>)}</CardContent></Card></Reveal>
        </div>
    </div>;
}
