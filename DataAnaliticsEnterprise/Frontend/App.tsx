import {useEffect, useState} from 'react';
import {m, useReducedMotion} from 'motion/react';
import {api, clearCsrf, refreshCsrf, label, assetLabel, type Me, type Asset} from './api';
import {ErrorMessage, Form, Loading, PanelIcon, Button, Input, Select, BrandMark, Reveal} from './ui';
import {Evaluations} from './Evaluations';
import {Rules} from './Rules';
import {Incidents} from './Incidents';
import {Accounts} from './Accounts';
import {CsvUpload} from './CsvUpload';
import {QualityOverview} from './QualityOverview';
import {EvaluationWizard} from './EvaluationWizard';

const tabs = ['Panel de calidad', 'Evaluaciones', 'Reglas', 'Incidencias', 'Cuentas'];
const tabIcons: Record<string, string> = {'Panel de calidad': 'panel', Evaluaciones: 'evaluations', Reglas: 'rules', Incidencias: 'incidents', Cuentas: 'accounts'};
const sectionDescriptions: Record<string, string> = {
    'Panel de calidad': 'Seis dimensiones, una entrada identificada y resultados verificables.',
    Evaluaciones: 'Historial, estado de ejecución y resultados de cada análisis.',
    Reglas: 'Condiciones, fórmulas y versiones bajo control.',
    Incidencias: 'Localiza cada incumplimiento y registra su seguimiento.',
    Cuentas: 'Administra las cuentas y los permisos del proyecto.'
};

function readPreference(key: string) {
    try { return localStorage.getItem(key); } catch { return null; }
}
function savedContext(user: string, project: string): {assetId?: string; tab?: string} {
    try {
        const value = JSON.parse(readPreference(`m2-context-v1:${user}:${project}`) ?? '{}');
        return {
            assetId: typeof value?.assetId === 'string' ? value.assetId : undefined,
            tab: tabs.includes(value?.tab) ? value.tab : undefined
        };
    } catch { return {}; }
}
function permittedTab(me: Me, project: string, tab?: string) {
    const roles = me.roles.filter(r => r.project_id === project);
    const read = roles.some(r => r.role !== 'SYSTEM_ADMIN');
    if (tab === 'Cuentas' && roles.some(r => r.role === 'SYSTEM_ADMIN')) return tab;
    if (read && (tab === 'Reglas' || tab === 'Evaluaciones' || tab === 'Panel de calidad')) return tab;
    if (tab === 'Incidencias' && roles.some(r => r.role === 'QUALITY_MANAGER')) return tab;
    return read ? 'Panel de calidad' : 'Cuentas';
}

export function App() {
    const reducedMotion = useReducedMotion();
    const [me, setMe] = useState<Me | null>();
    const [error, setError] = useState<unknown>();
    const [project, setProject] = useState('');
    const [assets, setAssets] = useState<Asset[]>([]);
    const [assetId, setAsset] = useState('');
    const [tab, setTab] = useState('Panel de calidad');
    const [recovery, setRecovery] = useState(false);
    const [notice, setNotice] = useState('');
    const [loadingAssets, setLoadingAssets] = useState(false);
    const [uploadOpen, setUploadOpen] = useState(false);
    const [uploadNotice, setUploadNotice] = useState('');
    const [incidentEvaluation, setIncidentEvaluation] = useState<string>();
    const [incidentAsset, setIncidentAsset] = useState<string>();
    const [loadedContext, setLoadedContext] = useState('');
    const [wizardOpen, setWizardOpen] = useState(false);
    const [selectedEvaluation, setSelectedEvaluation] = useState<string>();
    const [evaluationRefresh, setEvaluationRefresh] = useState(0);

    function restoreMe(value: Me) {
        const previous = readPreference(`m2-project-v1:${value.id}`);
        setMe(value);
        setProject(value.roles.some(r => r.project_id === previous) ? previous! : value.roles[0]?.project_id ?? '');
    }

    async function loadMe() {
        const response = await api<Me>('/me');
        restoreMe(response.data);
    }

    useEffect(() => {
        let active = true;
        api<Me>('/me').then(r => {
            if (active) {
                restoreMe(r.data);
            }
        }).catch(() => {
            if (active) setMe(null);
        });
        const expired = () => {
            setMe(null);
            setNotice('La sesión terminó. Inicia sesión para continuar.');
        };
        window.addEventListener('m2-session-expired', expired);
        return () => {
            active = false;
            window.removeEventListener('m2-session-expired', expired);
        };
    }, []);
    const roles = me?.roles.filter(r => r.project_id === project) ?? [];
    const admin = roles.some(r => r.role === 'SYSTEM_ADMIN');
    const canRead = roles.some(r => ['QUALITY_MANAGER', 'GOVERNANCE_MANAGER', 'BUSINESS_ANALYST', 'CATALOG_MANAGER'].includes(r.role));
    const canQuality = roles.some(r => r.role === 'QUALITY_MANAGER' && (!r.asset_scope_id || r.asset_scope_id === assetId));
    const asset = assets.find(a => a.id === assetId);
    const canImport = roles.some(r => r.role === 'QUALITY_MANAGER' && !r.asset_scope_id);
    const canIncidents = roles.some(r => r.role === 'QUALITY_MANAGER');
    const qualityAssets = assets.filter(a => roles.some(r => r.role === 'QUALITY_MANAGER' && (!r.asset_scope_id || r.asset_scope_id === a.id)));
    useEffect(() => {
        setLoadedContext('');
        setAssets([]);
        setAsset('');
        setError(undefined);
        setIncidentEvaluation(undefined);
        setIncidentAsset(undefined);
        setUploadNotice('');
        setUploadOpen(false);
        setWizardOpen(false);
        setSelectedEvaluation(undefined);
        if (!me || !project) {setLoadingAssets(false); return;}
        const saved = savedContext(me.id, project);
        setTab(permittedTab(me, project, saved.tab));
        if (!canRead) {setLoadingAssets(false); setLoadedContext(`${me.id}:${project}`); return;}
        const controller = new AbortController();
        setLoadingAssets(true);
        api<Asset[]>(`/projects/${project}/assets`, {signal: controller.signal}).then(r => {
            if (controller.signal.aborted) return;
            const selected = r.data.find(a => a.id === saved.assetId) ?? r.data[0];
            setAssets(r.data);
            setAsset(selected?.id ?? '');
            setTab(current => permittedTab(me, project, current));
            setLoadedContext(`${me.id}:${project}`);
        }).catch(e => {
            if (!controller.signal.aborted) setError(e);
        }).finally(() => {
            if (!controller.signal.aborted) setLoadingAssets(false);
        });
        return () => controller.abort();
    }, [project, canRead, me?.id]);
    useEffect(() => {
        if (!me || !project || loadingAssets || loadedContext !== `${me.id}:${project}`) return;
        // Solo preferencias de navegación; los registros se guardan en PostgreSQL.
        try {
            localStorage.setItem(`m2-project-v1:${me.id}`, project);
            localStorage.setItem(`m2-context-v1:${me.id}:${project}`, JSON.stringify({assetId, tab}));
        } catch { /* La interfaz sigue funcionando si el navegador bloquea el almacenamiento. */ }
    }, [me?.id, project, assetId, tab, loadingAssets, loadedContext]);
    useEffect(() => {
        document.title = `${me ? tab : 'Acceso'} · Calidad M2`;
    }, [me, tab]);
    useEffect(() => {
        window.scrollTo({top: 0, left: 0, behavior: 'instant'});
    }, [tab, project]);
    function openHistory(id?: string) {
        setSelectedEvaluation(id);
        setTab('Evaluaciones');
    }

    function openIncidents(evaluation?: string) {
        setIncidentEvaluation(evaluation);
        setIncidentAsset(evaluation ? asset?.id : undefined);
        setTab('Incidencias');
    }

    function renderSection() {
        if (loadingAssets && tab !== 'Cuentas') return <Loading/>;
        if (tab === 'Cuentas' && admin) return <Accounts key={project} project={project}/>;
        if (tab === 'Incidencias' && canIncidents) return <Incidents
            key={project + (incidentEvaluation ?? '') + (incidentAsset ?? '')} project={project}
            assets={qualityAssets} initialAssetId={incidentAsset} evaluationId={incidentEvaluation}
            clearFilter={() => {setIncidentEvaluation(undefined);setIncidentAsset(undefined);}}/>;
        if (!asset) return <div className="empty flex flex-col justify-center items-center text-center gap-2 p-6">{canImport
            ? 'Todavía no hay archivos cargados. Pulsa Subir CSV para agregar tus datos y configurar sus reglas.'
            : 'No hay archivos cargados disponibles para tu cuenta en este proyecto.'}</div>;
        switch (tab) {
            case 'Panel de calidad': return <QualityOverview key={project + asset.id} project={project}
                asset={asset} canManage={canQuality} refreshKey={evaluationRefresh} onCreate={() => setWizardOpen(true)}
                onHistory={openHistory} onRules={() => setTab('Reglas')} onIncidents={() => openIncidents()}/>;
            case 'Evaluaciones': return <Evaluations key={project + asset.id} project={project}
                asset={asset} canManage={canQuality} onCreate={() => setWizardOpen(true)}
                initialSelectedId={selectedEvaluation} refreshKey={evaluationRefresh} onIncidents={openIncidents}/>;
            case 'Reglas': return <Rules key={project + asset.id} project={project} asset={asset} canManage={canQuality}/>;
            default: return null;
        }
    }

    if (me === undefined) return <Loading/>;
    if (!me) return <div className="auth-layout"><aside className="auth-brand" aria-label="Gestión de calidad de datos"><div className="brand-lockup"><BrandMark/><div className="brand">Calidad <small>M2</small></div></div>
        <Reveal className="auth-presentation"><span className="auth-kicker">DATAANALITICSENTERPRISE</span><h2>Datos confiables.<br/><span>Decisiones claras.</span></h2><p>Un espacio para entender tus datos, aplicar reglas y dar seguimiento a cada hallazgo.</p>
            <div className="auth-workflow">{[{icon:'upload',title:'Carga tus datos',text:'Archivos CSV en tu espacio de trabajo'},{icon:'rules',title:'Define la calidad',text:'Reglas y versiones para cada activo'},{icon:'chart',title:'Encuentra respuestas',text:'Seis dimensiones y evidencia localizada'}].map((step,index) => <Reveal key={step.icon} delay={.12 + index * .08}><div><span><PanelIcon name={step.icon}/></span><div><strong>{step.title}</strong><small>{step.text}</small></div></div></Reveal>)}</div>
        </Reveal><span className="auth-footer"><PanelIcon name="shield"/>Gestión y evaluación de calidad de datos</span>
    </aside><main className="login"><Reveal>
        <div className="login-mark"><BrandMark/><span><strong>Calidad M2</strong><small>Bienvenido a tu espacio de calidad</small></span></div>
        <h1>{recovery ? 'Recupera tu acceso' : 'Inicia sesión'}</h1><p>Gestiona reglas, evalúa tus datos y da
        seguimiento a su calidad.</p>{notice ? <div className="notice notice-info my-4" role="status">{notice}</div> : null}
        <Form noValidate label={recovery ? 'Solicitar recuperación' : 'Iniciar sesión'} submit={async data => {
            if (recovery) {
                await api('/password-resets', {method: 'POST', body: JSON.stringify({email: data.get('email')})});
                setNotice('Si la cuenta está habilitada, recibirás instrucciones por el canal configurado.');
            } else {
                await api('/sessions', {
                    method: 'POST',
                    body: JSON.stringify({email: data.get('email'), password: data.get('password')})
                });
                clearCsrf();
                await refreshCsrf();
                await loadMe();
                setNotice('');
            }
        }}><label className="field-label flex flex-col gap-2 mb-4">Correo<Input name="email" type="email" autoComplete="username" required
                               maxLength={254}/></label>{!recovery ?
            <label className="field-label flex flex-col gap-2 mb-4">Contraseña<Input name="password" type="password" autoComplete="current-password" required
                                    maxLength={128}/></label> : null}</Form>
        <Button variant="link" className="px-0 mt-4" onClick={() => {
            setRecovery(!recovery);
            setNotice('');
        }}>{recovery ? 'Volver al acceso' : 'Olvidé mi contraseña'}</Button>
        {recovery ? <details>
            <summary>Ya tengo un código de recuperación</summary>
            <Form noValidate label="Cambiar contraseña" submit={async data => {
                await api('/password-resets/confirm', {
                    method: 'POST',
                    body: JSON.stringify({token: data.get('token'), password: data.get('password')})
                });
                setRecovery(false);
                setNotice('Contraseña actualizada. Inicia sesión.');
            }}><label className="field-label flex flex-col gap-2 mb-4">Código<Input name="token" required autoComplete="off"/></label><label className="field-label flex flex-col gap-2 mb-4">Nueva contraseña<Input
                name="password" type="password" minLength={12} maxLength={128} required
                autoComplete="new-password"/></label></Form></details> : null}
    </Reveal></main></div>;
    return <div className="shell"><a className="skip" href="#main">Ir al contenido</a>
        <aside className="app-sidebar" aria-label="Espacio de trabajo"><div className="sidebar-brand brand-lockup"><BrandMark/><div><div className="brand">Calidad <small>M2</small></div><span>DataAnaliticsEnterprise</span></div></div>
            <span className="nav-caption">Espacio de trabajo</span>
            <nav className="app-navigation" aria-label="Navegación principal">{tabs.filter(t => t === 'Cuentas' ? admin : canRead && (t !== 'Incidencias' || canIncidents)).map(t =>
                <Button variant="navigation" key={t} aria-current={tab === t ? 'page' : undefined} aria-label={t}
                        onClick={() => {setTab(t); if (t !== 'Reglas') setUploadNotice(''); if(t !== 'Evaluaciones')setSelectedEvaluation(undefined); if(t === 'Incidencias'){setIncidentEvaluation(undefined);setIncidentAsset(undefined);}}}>
                    {tab === t ? <m.span className="nav-indicator" aria-hidden="true" initial={reducedMotion ? false : {opacity:0,scale:.96}} animate={{opacity:1,scale:1}} transition={{duration:reducedMotion ? 0 : .2}}/> : null}
                    <PanelIcon name={tabIcons[t]}/><span className="nav-label">{t}</span><span className="nav-chevron" aria-hidden="true">›</span>
                </Button>)}</nav>
            <div className="sidebar-context"><span className="sidebar-context-icon"><PanelIcon name="database"/></span><strong>Tu espacio de datos</strong><span>{canRead ? `${assets.length.toLocaleString('es-EC')} archivos disponibles` : 'Administración de acceso'}</span><div><span className="sidebar-dot"/>Módulo de calidad · M2</div></div>
        </aside>
        <div className="workspace"><header className="app-topbar"><div className="workspace-location"><PanelIcon name={tabIcons[tab]}/><span>Calidad de datos</span><span aria-hidden="true">/</span><strong>{tab}</strong></div><label className="project-label field-label flex items-center gap-2 mb-0">Proyecto<Select aria-label="Proyecto" value={project}
                                                                     onChange={e => setProject(e.target.value)}>{[...new Set(me.roles.map(r => r.project_id))].map(p =>
                <option key={p} value={p}>{me.roles.find(r => r.project_id === p)?.project_name ?? p}</option>)}</Select></label>
                <Button variant="outline" className="logout-button" onClick={async () => {
                    try {
                        await api('/sessions/current', {method: 'DELETE'});
                        clearCsrf();
                        setMe(null);
                    } catch (e) {
                        setError(e);
                    }
                }}><PanelIcon name="logout"/>Cerrar sesión
                </Button>
            </header>
            <main id="main" tabIndex={-1}><Reveal key={`${project}:${tab}`} className="page-content"><ErrorMessage error={error}/>{!project ?
                <div className="empty flex flex-col justify-center items-center text-center gap-2 p-6">Tu cuenta no tiene un proyecto asignado. Contacta al administrador.</div> : <>
                    <div className="title"><span className="eyebrow">{tab === 'Panel de calidad' ? 'Tu centro de calidad' : 'Gestión y evaluación de datos'}</span><h1>{tab === 'Panel de calidad' ? 'Estado e indicadores' : tab}</h1>
                        <p>{sectionDescriptions[tab]}</p>
                    </div>
                    {tab !== 'Cuentas' ?
                        <div className="filters surface-card flex flex-wrap items-center gap-4 mb-5">{tab !== 'Incidencias' && assets.length > 0 ? <label className="asset-picker field-label flex flex-col gap-2 mb-0">Activo<Select aria-label="Activo" value={assetId} disabled={loadingAssets}
                                                                      onChange={e => {setAsset(e.target.value); setTab(permittedTab(me, project, tab)); setIncidentEvaluation(undefined); setUploadNotice('');}}>{assets.map(a =>
                            <option key={a.id} value={a.id}>{assetLabel(a)}</option>)}</Select></label> : null}<span
                            className="role-note ms-auto"><PanelIcon name="shield"/>{[...new Set(roles.map(r => label(r.role)))].join(' · ')}</span>{canImport ? <Button variant="primary" className="upload-action" onClick={() => setUploadOpen(true)}><PanelIcon name="upload"/>Subir CSV</Button> : null}
                        </div> : null}
                    {uploadNotice ? <div className="notice notice-info my-4" role="status">{uploadNotice}</div> : null}
                    {renderSection()}

                </>}</Reveal></main>
        </div>
        {wizardOpen && asset && canQuality ? <EvaluationWizard project={project} initialAssetId={asset.id}
            assets={qualityAssets}
            close={() => setWizardOpen(false)} created={evaluation => {
                setWizardOpen(false); setAsset(evaluation.asset_id); setSelectedEvaluation(evaluation.id); setEvaluationRefresh(v => v + 1); setTab('Evaluaciones');
            }}/> : null}
        {uploadOpen ? <CsvUpload project={project} close={() => setUploadOpen(false)} imported={async value => {
            const catalog=await api<Asset[]>(`/projects/${project}/assets`);
            setAssets(catalog.data); setAsset(value.id); setTab('Reglas');
            setIncidentEvaluation(undefined); setUploadOpen(false);
            setUploadNotice(value.import_reused ? `Este CSV ya estaba cargado. Se seleccionó «${value.name}» con sus reglas e historial; no se creó otro activo.` : `CSV importado: ${value.row_count ?? 0} registros. Crea las reglas para estas columnas y luego abre Evaluaciones para iniciar el análisis.`);
        }}/> : null}
    </div>;
}




