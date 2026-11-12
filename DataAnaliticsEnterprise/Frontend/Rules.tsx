import {useState} from 'react';
import {api, queryPath, label, metricFormula, percentage, ruleCondition, type Asset, type Rule} from './api';
import {useApiQuery} from './useApiQuery';
import {Dialog, ErrorMessage, Form, Loading, Pagination, Status, Button, Input, Select, Table, FilterPanel} from './ui';

const kinds = {
    REQUIRED: ['COMPLETENESS', 'Valores obligatorios'],
    UNIQUE_KEY: ['UNIQUENESS', 'Clave única'],
    EMAIL: ['VALIDITY', 'Formato de correo'],
    NUMBER_RANGE: ['VALIDITY', 'Rango numérico'],
    ALLOWED_VALUES: ['VALIDITY', 'Valores permitidos'],
    FIELD_EQUALS: ['CONSISTENCY', 'Igualdad entre campos'],
    FOREIGN_KEY: ['CONSISTENCY', 'Referencia entre activos'],
    REFERENCE_EQUALS: ['ACCURACY', 'Comparar con referencia'],
    WITHIN_AGE: ['TIMELINESS', 'Vigencia temporal']
} as const;
type Kind = keyof typeof kinds;

export function Rules({project, asset, canManage}: { project: string; asset: Asset; canManage: boolean }) {
    const base = `/projects/${project}/quality-rules`;
    const [page, setPage] = useState(1);
    const [refresh, setRefresh] = useState(0);
    const [error, setError] = useState<unknown>();
    const [open, setOpen] = useState(false);
    const [editing, setEditing] = useState<Rule>();
    const [detail, setDetail] = useState<Rule>();
    const [kind, setKind] = useState<Kind>('REQUIRED');
    const [saving, setSaving] = useState(false);
    const [notice, setNotice] = useState('');
    const [renaming, setRenaming] = useState<Rule>();
    const [filters, setFilters] = useState({search: '', enabled: ''});
    const [filterRevision, setFilterRevision] = useState(0);
    const current = editing?.versions?.[0];
    const definition = current?.definition;
    const initialFields = Array.isArray(definition?.fields) ? definition.fields : [];
    const initial = (key: string, fallback = '') => definition?.[key] == null ? fallback : String(definition[key]);
    const {data: rows = [], info, loading, error: queryError} = useApiQuery<Rule[]>(queryPath(base, {
        asset_id: asset.id, page, q: filters.search, enabled: filters.enabled
    }), {refreshKey: refresh});

    async function inspect(rule: Rule) {
        try {
            const r = await api<Rule>(`${base}/${rule.id}`);
            setDetail(r.data);
        } catch (e) {
            setError(e);
        }
    }

    async function toggle(rule: Rule) {
        setSaving(true);
        setNotice('');
        try {
            await api(`${base}/${rule.id}`, {
                method: 'PATCH',
                headers: {'Content-Type': 'application/merge-patch+json', 'If-Match': `"${rule.revision}"`},
                body: JSON.stringify({enabled: !rule.enabled})
            });
            setRefresh(v => v + 1);
            setError(undefined);
            setNotice(`Regla «${rule.name}» ${rule.enabled ? 'desactivada. No se incluirá en nuevas evaluaciones; su historial se conserva.' : 'activada.'}`);
        } catch (e) {
            setError(e);
        } finally {
            setSaving(false);
        }
    }

    async function edit(rule: Rule) {
        setSaving(true);
        try {
            const response = await api<Rule>(`${base}/${rule.id}`);
            const kind = response.data.versions?.[0]?.definition.kind;
            if (typeof kind !== 'string' || !(kind in kinds)) throw new Error('No se pudo cargar la configuración de la regla.');
            setEditing(response.data);
            setKind(kind as Kind);
            setOpen(true);
            setError(undefined);
        } catch (e) {setError(e);} finally {setSaving(false);}
    }

    return <>
        <div className="toolbar surface-card p-5 flex flex-row flex-wrap items-center justify-between gap-4 mb-4"><span>Reglas de {asset.name}</span>{canManage ? <Button variant="primary" disabled={saving} onClick={() => {
            setEditing(undefined);
            setKind('REQUIRED');
            setOpen(true);
        }}>+ Crear regla</Button> : null}</div>
        <p className="help">Cada conjunto conserva sus propias reglas. Para retirar una regla, desactívala: dejará de usarse en nuevas evaluaciones y conservará su historial.</p>
        <FilterPanel title="Filtrar reglas"><Form key={filterRevision} noValidate variant="filters" label="Buscar reglas" submit={async data => {
            setFilters({search: String(data.get('search')).trim(), enabled: String(data.get('enabled'))}); setPage(1);
        }}><div className="rule-filters grid grid-cols-1 md:grid-cols-2 gap-4"><label className="field-label flex flex-col gap-2 mb-4">Buscar por nombre<Input name="search" type="search" maxLength={160} defaultValue={filters.search}/></label>
            <label className="field-label flex flex-col gap-2 mb-4">Estado de la regla<Select name="enabled" aria-label="Estado de la regla" defaultValue={filters.enabled}><option value="">Todas las reglas</option><option value="true">Activas</option><option value="false">Inactivas</option></Select></label></div></Form>
            <Button variant="link" className="text-xs p-0" onClick={() => {setFilters({search: '', enabled: ''});setFilterRevision(v => v + 1);setPage(1);}}>Limpiar filtros de reglas</Button></FilterPanel>
        {notice ? <div className="notice notice-info my-4" role="status">{notice}</div> : null}
        <ErrorMessage error={error ?? queryError}/>{loading ? <Loading/> : rows.length ? <div className="table-scroll max-w-full overflow-x-auto">
            <Table className="rules-table">
                <thead>
                <tr>
                    <th>Regla</th>
                    <th>Condición y dimensión</th>
                    <th>Fórmula y umbral</th>
                    <th>Versión vigente</th>
                    <th>Estado</th>
                    <th>Acciones</th>
                </tr>
                </thead>
                <tbody>{rows.map(r => <tr key={r.id}>
                    <td><strong>{r.name}</strong><small className="dimension-label mono">{r.id.slice(0, 8)} · revisión {r.revision}</small></td>
                    <td>{ruleCondition(r.current_version)}<small className="dimension-label">{r.current_version ? label(r.current_version.dimension) : 'Sin dimensión'}</small></td>
                    <td><span className="formula-text">{metricFormula(r.current_version?.dimension ?? '')}</span><small className="dimension-label">Umbral: {r.current_version?.threshold == null ? 'Sin calificación' : percentage(r.current_version.threshold)}</small></td>
                    <td><span className="version-tag">v{r.current_version?.version_number ?? '—'} · inmutable</span><small className="dimension-label">{r.current_version?.formula_version ?? 'Sin fórmula informada'}</small></td>
                    <td><Status value={r.enabled ? 'ACTIVE' : 'INACTIVE'}/></td>
                    <td><div className="actions flex flex-wrap gap-2">
                        <Button onClick={() => void inspect(r)}>Ver versiones</Button>
                        {canManage ? <details className="row-actions" onKeyDown={e => {if(e.key === 'Escape'){e.currentTarget.open = false; e.currentTarget.querySelector('summary')?.focus();}}}><summary>Gestionar regla</summary><div>
                            <Button disabled={saving}
                                    onClick={() => void toggle(r)}>{r.enabled ? 'Desactivar' : 'Activar'}</Button>
                            <Button disabled={saving} onClick={() => void edit(r)}>Modificar regla</Button>
                            <Button disabled={saving} onClick={() => setRenaming(r)}>Cambiar nombre</Button>
                        </div></details> : null}</div></td>
                </tr>)}</tbody>
            </Table>
        </div> :
        <div className="empty flex flex-col justify-center items-center text-center gap-2 p-6">No hay reglas que coincidan con esta consulta. Define qué necesitas medir o limpia los filtros.</div>}<Pagination
        info={info} onPage={setPage}/>
        {detail ? <section className="result-panel surface-card p-5 mt-6">
            <div className="toolbar surface-card p-5 flex flex-row flex-wrap items-center justify-between gap-4 mb-4"><h2>{detail.name}</h2>
                <Button onClick={() => setDetail(undefined)}>Cerrar detalle</Button>
            </div>
            <p>Las versiones se conservan para reproducir las mediciones anteriores.</p>{detail.versions?.map(v => <div
            className="version" key={v.id}>
            <strong>Versión {v.version_number} · {label(v.dimension)}</strong><span>Umbral: {v.threshold == null ? 'Sin calificación' : percentage(v.threshold)} · Cobertura mínima: {percentage(v.minimum_coverage)}</span>
            <p>{ruleCondition(v)}</p><p className="formula-text">{metricFormula(v.dimension)}</p><details><summary>Parámetros de la versión</summary><pre>{JSON.stringify(v.definition, null, 2)}</pre></details>
        </div>)}</section> : null}
        {open ? <Dialog title={editing ? 'Modificar regla' : 'Crear regla'} close={() => setOpen(false)}>
            <p>{editing ? `Los resultados anteriores de ${editing.name} conservarán su versión.` : 'La regla se aplicará únicamente al activo seleccionado.'}</p>
            <Form noValidate cancel={() => setOpen(false)} label={editing ? 'Guardar nueva versión' : 'Crear regla'}
                  submit={async data => {
                      const fields = data.getAll('fields').map(String);
                      if (!fields.length) throw new Error('Selecciona al menos un campo.');
                      const definition: Record<string, unknown> = {
                          kind,
                          fields,
                          blank_is_missing: data.get('blank') === 'on'
                      };
                      if (kind === 'NUMBER_RANGE') {
                          definition.minimum = Number(data.get('minimum'));
                          definition.maximum = Number(data.get('maximum'));
                      }
                      if (kind === 'ALLOWED_VALUES') definition.allowed_values = String(data.get('values')).split(',').map(v => v.trim()).filter(Boolean);
                      if (['REFERENCE_EQUALS', 'FOREIGN_KEY'].includes(kind)) Object.assign(definition, {
                          reference_id: data.get('reference_id'),
                          reference_version: data.get('reference_version'),
                          reference_key: data.get('reference_key'),
                          reference_field: data.get('reference_field'),
                          tolerance: Number(data.get('tolerance') ?? 0)
                      });
                      if (kind === 'WITHIN_AGE') definition.max_age_seconds = Number(data.get('age'));
                      const version = {
                          dimension: kinds[kind][0],
                          definition,
                          threshold: data.get('threshold') === '' ? null : Number(data.get('threshold')),
                          minimum_coverage: 100,
                          critical: data.get('critical') === 'on'
                      };
                      const name = editing?.name ?? String(data.get('name'));
                      if (editing) await api(`${base}/${editing.id}/versions`, {
                          method: 'POST',
                          headers: {'If-Match': `"${editing.revision}"`},
                          body: JSON.stringify(version)
                      });
                      else await api(base, {
                          method: 'POST',
                          body: JSON.stringify({asset_id: asset.id, name: data.get('name'), version})
                      });
                      setOpen(false);
                      if (!editing) {setPage(1);setFilters({search: '', enabled: ''});setFilterRevision(v => v + 1);}
                      setRefresh(v => v + 1);
                      setDetail(undefined);
                      setNotice(editing ? `Nueva versión de «${name}» guardada. Las evaluaciones anteriores conservan su configuración.` : `Regla «${name}» guardada en «${asset.name}».`);
                  }}>
                {!editing ? <label className="field-label flex flex-col gap-2 mb-4">Nombre de la regla<Input name="name" required maxLength={160}/></label> : null}
                <label className="field-label flex flex-col gap-2 mb-4">Qué evaluar<Select value={kind}
                                          onChange={e => setKind(e.target.value as Kind)}>{Object.entries(kinds).map(([key, value]) =>
                    <option key={key} value={key}>{value[1]}</option>)}</Select></label>
                <fieldset className="choices">
                    <legend>Campos {kind === 'FIELD_EQUALS' ? '(selecciona dos)' : kind === 'REQUIRED' || kind === 'UNIQUE_KEY' ? '' : '(selecciona uno)'}</legend>
                    {asset.fields.map(f => <label className="check field-label flex flex-row items-center gap-2 my-2" key={f}><Input type="checkbox" name="fields"
                                                                                   value={f} defaultChecked={initialFields.includes(f)}/>{f}</label>)}</fieldset>
                {kind === 'REQUIRED' ?
                    <label className="check field-label flex flex-row items-center gap-2 my-2"><Input type="checkbox" name="blank" defaultChecked={definition?.blank_is_missing !== false}/>Considerar vacíos y
                        espacios como faltantes</label> : null}
                {kind === 'NUMBER_RANGE' ?
                    <div className="form-grid grid grid-cols-1 sm:grid-cols-2 gap-4"><label className="field-label flex flex-col gap-2 mb-4">Mínimo<Input name="minimum" type="number" step="any"
                                                                   defaultValue={initial('minimum')} required/></label><label className="field-label flex flex-col gap-2 mb-4">Máximo<Input name="maximum"
                                                                                                         type="number"
                                                                                                         step="any"
                                                                                                         defaultValue={initial('maximum')} required/></label>
                    </div> : null}
                {kind === 'ALLOWED_VALUES' ? <label className="field-label flex flex-col gap-2 mb-4">Valores permitidos, separados por coma<Input name="values" required
                                                                                                 defaultValue={Array.isArray(definition?.allowed_values) ? definition.allowed_values.join(', ') : ''} placeholder="activo, inactivo"/></label> : null}
                {['REFERENCE_EQUALS', 'FOREIGN_KEY'].includes(kind) ? <><label className="field-label flex flex-col gap-2 mb-4">Referencia confiable<Input
                    name="reference_id" required placeholder="Identificador de referencia configurada" defaultValue={initial('reference_id')}/></label><label className="field-label flex flex-col gap-2 mb-4">Versión de referencia<Input
                    name="reference_version" required defaultValue={initial('reference_version', '1')}/></label><label className="field-label flex flex-col gap-2 mb-4">Campo clave de la entrada<Select
                    name="reference_key" defaultValue={initial('reference_key', asset.fields[0])}>{asset.fields.map(f => <option key={f}>{f}</option>)}</Select></label><label className="field-label flex flex-col gap-2 mb-4">Campo
                    de referencia<Input name="reference_field" required
                                        defaultValue={initial('reference_field', kind === 'FOREIGN_KEY' ? 'id_cliente' : 'correo')}/></label><label className="field-label flex flex-col gap-2 mb-4">Tolerancia
                    numérica<Input name="tolerance" type="number" min="0" step="any"
                                   defaultValue={initial('tolerance', '0')}/></label></> : null}
                {kind === 'WITHIN_AGE' ?
                    <label className="field-label flex flex-col gap-2 mb-4">Vigencia en segundos<Input name="age" type="number" min="1" max="315360000" required
                                                      defaultValue={initial('max_age_seconds', '86400')}/></label> : null}
                <label className="field-label flex flex-col gap-2 mb-4">Umbral mínimo (%)<Input name="threshold" type="number" min="0" max="100" step="0.01" aria-describedby="rule-threshold-help"
                                               defaultValue={current?.threshold ?? ''} placeholder="Ej.: 98 para exigir 98 %"/></label><p className="help" id="rule-threshold-help">Escala de 0 a 100: 98 significa 98 %, mientras que 0,98 significa 0,98 %. Si lo dejas vacío, la medida no se calificará.</p><p className="help">El
                piloto exige cobertura del 100 %. Un formato válido no demuestra exactitud.</p><label className="check field-label flex flex-row items-center gap-2 my-2"><Input
                name="critical" type="checkbox" defaultChecked={current?.critical ?? false}/>Regla crítica</label>
            </Form></Dialog> : null}
        {renaming ? <Dialog title="Cambiar nombre de regla" close={() => setRenaming(undefined)}>
            <Form noValidate label="Guardar nombre" cancel={() => setRenaming(undefined)} submit={async data => {
                const name = String(data.get('name')).trim();
                await api(`${base}/${renaming.id}`, {
                    method: 'PATCH',
                    headers: {'Content-Type': 'application/merge-patch+json', 'If-Match': `"${renaming.revision}"`},
                    body: JSON.stringify({name})
                });
                setRenaming(undefined); setDetail(undefined); setRefresh(v => v + 1);
                setFilters({search: '', enabled: ''});setFilterRevision(v => v + 1);
                setNotice(`Nombre de regla actualizado a «${name}».`);
            }}><label className="field-label flex flex-col gap-2 mb-4">Nombre de la regla<Input name="name" required maxLength={160} defaultValue={renaming.name}/></label></Form>
        </Dialog> : null}
    </>;
}


