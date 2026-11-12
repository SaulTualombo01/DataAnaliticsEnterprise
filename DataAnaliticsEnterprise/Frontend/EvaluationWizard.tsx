import {useEffect, useRef, useState} from 'react';
import {api, assetLabel, date, label, percentage, ruleCondition, type Asset, type Evaluation, type InputMetadata, type PageInfo, type Rule} from './api';
import {Dialog, ErrorMessage, Form, InputStatus, Loading, Pagination, Button, Input, Select, Table} from './ui';

export function EvaluationWizard({project, assets, initialAssetId, close, created}: {
    project: string; assets: Asset[]; initialAssetId: string; close: () => void; created: (evaluation: Evaluation) => void
}) {
    const [assetId, setAsset] = useState(initialAssetId);
    const [step, setStep] = useState(1);
    const [input, setInput] = useState<InputMetadata>();
    const [inputLoading, setInputLoading] = useState(true);
    const [inputError, setInputError] = useState<unknown>();
    const [rules, setRules] = useState<Rule[]>([]);
    const [rulePage, setRulePage] = useState({page: 1, search: ''});
    const [pageInfo, setPageInfo] = useState<PageInfo>();
    const [ruleLoading, setRuleLoading] = useState(false);
    const [ruleError, setRuleError] = useState<unknown>();
    const [selected, setSelected] = useState<Map<string, Rule>>(new Map());
    const [refresh, setRefresh] = useState(0);
    const [pending, setPending] = useState(false);
    const retry = useRef({body: '', key: ''});
    const stepsRef = useRef<HTMLOListElement>(null);
    useEffect(() => {stepsRef.current?.querySelector<HTMLButtonElement>('button[aria-current="step"]')?.focus();}, [step]);
    const asset = assets.find(a => a.id === assetId);
    const chosen = [...selected.values()];
    useEffect(() => {
        const abort = new AbortController(); setInput(undefined); setInputError(undefined); setInputLoading(true);
        api<InputMetadata>(`/projects/${project}/assets/${assetId}/input`, {signal: abort.signal}).then(r => {
            if (!abort.signal.aborted) setInput(r.data);
        }).catch(e => {if (!abort.signal.aborted) setInputError(e);}).finally(() => {if (!abort.signal.aborted) setInputLoading(false);});
        return () => abort.abort();
    }, [project, assetId, refresh]);
    useEffect(() => {
        if (step !== 2) return;
        const abort = new AbortController(); setRuleLoading(true); setRuleError(undefined);
        const params = new URLSearchParams({asset_id: assetId, enabled: 'true', page: String(rulePage.page), page_size: '10'});
        if (rulePage.search) params.set('q', rulePage.search);
        api<Rule[]>(`/projects/${project}/quality-rules?${params}`, {signal: abort.signal}).then(r => {
            if (!abort.signal.aborted) {setRules(r.data); setPageInfo(r.meta.page_info);}
        }).catch(e => {if (!abort.signal.aborted) setRuleError(e);}).finally(() => {if (!abort.signal.aborted) setRuleLoading(false);});
        return () => abort.abort();
    }, [project, assetId, step, rulePage.page, rulePage.search, refresh]);

    function choose(rule: Rule, checked: boolean) {
        setRuleError(undefined);
        if (checked && selected.size >= 20) {setRuleError(new Error('El máximo por evaluación es de 20 reglas. Desmarca una para agregar otra.')); return;}
        setSelected(previous => {const next = new Map(previous); if (checked) next.set(rule.id, rule); else next.delete(rule.id); return next;});
    }

    return <Dialog title="Nueva evaluación" variant="evaluation" close={close} busy={pending}>
        <p className="dialog-intro">Define la entrada y las reglas. Revisa el alcance antes de iniciar el análisis.</p>
        <ol ref={stepsRef} className="wizard-steps grid grid-cols-3 gap-2 list-none mb-6" aria-label="Pasos de la evaluación">{['Archivo y alcance', 'Reglas', 'Revisión'].map((name, index) => <li key={name}>
            <Button className="w-full h-full flex items-center gap-2 text-left" type="button" disabled={pending || index + 1 > step} aria-current={step === index + 1 ? 'step' : undefined} onClick={() => setStep(index + 1)}><span>{index + 1}</span>{name}</Button>
        </li>)}</ol>
        {step === 1 ? <Form noValidate label="Continuar a reglas" cancel={close} submit={async () => {
            if (!asset || inputLoading || !input) throw new Error('Espera a que se confirme la entrada o reintenta su consulta.');
            if (input.input_availability !== 'AVAILABLE') throw new Error('La entrada no está disponible. Sube un archivo vigente antes de evaluar.');
            setStep(2);
        }}>
            <label className="field-label flex flex-col gap-2 mb-4">Activo autorizado<Select aria-label="Activo de la nueva evaluación" value={assetId} aria-describedby="wizard-asset-help" onChange={e => {setAsset(e.target.value); setSelected(new Map()); setRules([]); setPageInfo(undefined); setRulePage({page: 1, search: ''});}}>
                {assets.map(a => <option value={a.id} key={a.id}>{assetLabel(a)}</option>)}
            </Select></label><p id="wizard-asset-help" className="help">Solo aparecen archivos sobre los que tienes permiso para ejecutar evaluaciones.</p>
            <ErrorMessage error={inputError}/>{inputLoading ? <Loading/> : !input ? <Button type="button" onClick={() => setRefresh(v => v + 1)}>Reintentar entrada</Button> : <>
                <fieldset className="input-choice"><legend>Entrada que se analizará</legend><label className="check field-label flex flex-row items-center gap-2 my-2"><Input type="radio" name="input" value={input.snapshot_id ?? input.read_id} defaultChecked/>{input.snapshot_id ? 'Captura conservada del archivo subido' : 'Lectura identificada del archivo'}</label>
                    <InputStatus value={input.input_availability}/><p className="help">La referencia fija el origen de esta evaluación; no modifica el CSV.</p></fieldset>
                <dl className="context-grid grid grid-cols-1 sm:grid-cols-2 gap-4 my-4"><div><dt>Alcance</dt><dd>Archivo completo</dd></div><div><dt>Registros</dt><dd>{input.input_row_count?.toLocaleString('es-EC') ?? 'No informado'}</dd></div>
                    <div><dt>Columnas</dt><dd>{asset?.fields.length ?? 0}</dd></div><div><dt>Capturado</dt><dd>{input.input_acquired_at ? date(input.input_acquired_at) : 'No informado'}</dd></div></dl>
                <details className="technical-details"><summary>Columnas, tipos y referencias de origen</summary><div className="column-tags flex flex-wrap gap-2 my-4">{asset?.fields.map(field => <span key={field}>{field}<small>{input.scope?.column_types?.[field] ?? 'TEXT'}</small></span>)}</div>
                    <p className="identifier">Metadatos: {input.metadata_version_id}</p><p className="identifier">{input.snapshot_id ? 'Captura: ' + input.snapshot_id : 'Lectura: ' + input.read_id}</p></details>
                {input.input_row_count === 0 ? <div className="notice notice-warning my-4">El archivo no tiene registros. La ejecución podrá completarse, pero sus métricas no serán evaluables.</div> : null}
            </>}
        </Form> : step === 2 ? <>
            <div className="selection-summary flex flex-wrap items-center justify-between gap-2 my-4"><strong>{selected.size} de 20 reglas seleccionadas</strong><Button type="button" onClick={() => {setSelected(new Map()); setRefresh(v => v + 1);}}>Actualizar reglas y selección</Button></div>
            <Form noValidate variant="filters" label="Buscar reglas" submit={async data => setRulePage({page: 1, search: String(data.get('search')).trim()})}>
                <label className="field-label flex flex-col gap-2 mb-4">Buscar regla por nombre<Input name="search" type="search" maxLength={160} defaultValue={rulePage.search}/></label></Form>
            <ErrorMessage error={ruleError}/>{ruleLoading ? <Loading/> : <div className="rule-options">{rules.length ? rules.map(rule => <label className="rule-option" key={rule.id}>
                <Input type="checkbox" checked={selected.has(rule.id)} onChange={e => choose(rule, e.target.checked)}/><span><strong>{rule.name}</strong><small>{ruleCondition(rule.current_version)}</small>
                    <span className="rule-option-meta"><span className="version-tag">v{rule.current_version?.version_number ?? '—'} · inmutable</span><span>{rule.current_version ? label(rule.current_version.dimension) : 'Sin versión'}</span><span>Umbral {rule.current_version?.threshold == null ? 'sin definir' : percentage(rule.current_version.threshold)}</span></span></span>
            </label>) : <div className="empty flex flex-col justify-center items-center text-center gap-2 p-6">No hay reglas activas que coincidan. Configura las reglas de este archivo antes de evaluar.</div>}</div>}
            <Pagination info={ruleLoading ? undefined : pageInfo} onPage={page => setRulePage(v => ({...v, page}))}/>
            <Form noValidate label="Revisar evaluación" cancelLabel="Volver al archivo" cancel={() => setStep(1)} submit={async () => {
                if (!selected.size) throw new Error('Selecciona al menos una regla.');
                if (chosen.some(r => !r.current_version)) throw new Error('Actualiza la selección para confirmar las versiones.');
                setStep(3);
            }}><p className="help">La selección se conserva al buscar o cambiar de página. Cada regla se aplicará con la versión revisada.</p></Form>
        </> : <Form noValidate label="Iniciar evaluación" cancelLabel="Volver a reglas" cancel={() => setStep(2)} submit={async () => {
            if (!input || !asset || !selected.size) throw new Error('Vuelve a los pasos anteriores para revisar la entrada y las reglas.');
            const ordered = [...selected.values()].sort((a, b) => a.id.localeCompare(b.id));
            const body = JSON.stringify({asset_id: asset.id, metadata_version_id: input.metadata_version_id, snapshot_id: input.snapshot_id ?? undefined,
                rule_ids: ordered.map(r => r.id), expected_rule_versions: Object.fromEntries(ordered.map(r => [r.id, r.current_version!.id]))});
            if (retry.current.body !== body) retry.current = {body, key: crypto.randomUUID()};
            setPending(true);
            try {const result = await api<Evaluation>(`/projects/${project}/quality-evaluations`, {method: 'POST', headers: {'Idempotency-Key': retry.current.key}, body}); created(result.data);} finally {setPending(false);}
        }}>
            <div className="review-heading"><span className="eyebrow">Listo para solicitar el análisis</span><h3>{asset ? assetLabel(asset) : 'Archivo seleccionado'}</h3><p>Archivo completo · {input?.input_row_count?.toLocaleString('es-EC') ?? '—'} registros · {selected.size} {selected.size === 1 ? 'regla' : 'reglas'}</p></div>
            <div className="table-scroll max-w-full overflow-x-auto"><Table><thead><tr><th>Regla y condición</th><th>Versión revisada</th><th>Umbral</th></tr></thead><tbody>{chosen.map(rule => <tr key={rule.id}>
                <td><strong>{rule.name}</strong><small className="dimension-label">{ruleCondition(rule.current_version)}</small></td><td><span className="version-tag">v{rule.current_version!.version_number}</span></td><td>{rule.current_version!.threshold == null ? 'Sin calificación' : percentage(rule.current_version!.threshold)}</td>
            </tr>)}</tbody></Table></div>
            <div className="notice notice-warning my-4">Se analizará una copia identificada del archivo. No se corregirán ni eliminarán datos del origen. Si una regla cambia desde esta revisión, deberás actualizar la selección.</div>
            {chosen.some(r => ['ACCURACY', 'CONSISTENCY'].includes(r.current_version!.dimension) && ['REFERENCE_EQUALS', 'FOREIGN_KEY'].includes(String(r.current_version!.definition.kind))) ? <p className="help">Las comparaciones requieren una referencia confiable disponible. Si falta, la regla no podrá demostrar el cumplimiento y lo informará en la medición.</p> : null}
            <p className="help">Una ejecución completada no implica que el activo cumpla los umbrales. Los resultados indicarán medida, cobertura y cumplimiento por separado.</p>
        </Form>}
    </Dialog>;
}
